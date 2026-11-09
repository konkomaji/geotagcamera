<?php
/**
 * GeoTag Camera in-app report endpoint.
 *
 * The app's "Send report" button POSTs multipart/form-data here: a "report"
 * JSON part plus optional "attachment[]" files (screenshots, one screen
 * recording). This emails the report to the developer and keeps a copy on
 * disk outside public_html, in case mail fails. Nothing is sent by the app
 * unless the user presses Send.
 *
 * Deploy: uploaded with the website to public_html/geotagcamera/api/report.php.
 * Needs PHP 8.0+, mail(), fileinfo; GD is used when present.
 *
 * Security:
 * - The app is open source, so a shared secret would not stay secret. Abuse
 *   limits instead: size caps, a honeypot field, per-IP and global rate limits.
 *   IPs are kept only as salted hashes, for the rate limit.
 * - Attachments: type decided from the file's bytes (finfo), never from the
 *   name or the client's Content-Type. Only JPEG/PNG/WebP images and MP4/WebM
 *   video. Images are re-encoded through GD (drops metadata and any payload
 *   hidden in the file). Files are stored with random names outside the web
 *   root and never served by URL; they only travel as email attachments.
 * - All text is stripped of control characters and length-capped; the
 *   subject and headers are single-line, so no header injection.
 */

declare(strict_types=1);

const TO_EMAIL        = 'konkomaji@gmail.com';
const FROM_EMAIL      = 'noreply@konkomaji.com';   // must be on the site's own domain
const MAX_JSON        = 300000;                    // bytes of report JSON
const MAX_IMAGES      = 3;
const MAX_VIDEOS      = 1;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const MAX_VIDEO_BYTES = 20 * 1024 * 1024;
const MAX_MAIL_ATTACH = 18 * 1024 * 1024;          // Gmail takes 25 MB including base64 overhead
const PER_IP_HOUR     = 5;
const GLOBAL_DAY      = 200;

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');
header('Cache-Control: no-store');

function reply(int $code, array $body): void {
    http_response_code($code);
    echo json_encode($body, JSON_UNESCAPED_SLASHES);
    exit;
}

function field(array $in, string $key, int $max): string {
    $v = $in[$key] ?? '';
    if (!is_string($v)) return '';
    // Strip control characters except tab and newline; cap length.
    $v = preg_replace('/[^\P{C}\t\n]/u', '', $v) ?? '';
    return mb_substr(trim($v), 0, $max);
}

function one_line(string $s): string {
    return trim(preg_replace('/[\r\n]+/', ' ', $s) ?? '');
}

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') reply(405, ['ok' => false, 'error' => 'POST only']);
// PHP drops the whole body when post_max_size is exceeded.
if (empty($_POST) && (int)($_SERVER['CONTENT_LENGTH'] ?? 0) > 0) reply(413, ['ok' => false, 'error' => 'Report too large']);

$raw = $_POST['report'] ?? '';
if (!is_string($raw) || $raw === '') reply(400, ['ok' => false, 'error' => 'Bad request']);
if (strlen($raw) > MAX_JSON) reply(413, ['ok' => false, 'error' => 'Report too large']);
$in = json_decode($raw, true);
if (!is_array($in)) reply(400, ['ok' => false, 'error' => 'Bad request']);

// Honeypot: the app always sends it empty. Bots that fill every field get a fake success.
if (($in['website'] ?? '') !== '') reply(200, ['ok' => true, 'id' => 'ok']);

$kind    = in_array($in['kind'] ?? '', ['problem', 'idea', 'question'], true) ? $in['kind'] : 'problem';
$message = field($in, 'message', 4000);
$app     = one_line(field($in, 'app', 200));
$device  = field($in, 'device', 2000);
$crash   = field($in, 'crash', 20000);
$log     = field($in, 'log', 200000);
$contact = one_line(field($in, 'contact', 200));
if ($contact !== '' && !filter_var($contact, FILTER_VALIDATE_EMAIL)) $contact = '';

if (mb_strlen($message) < 5) reply(422, ['ok' => false, 'error' => 'Message too short']);

// ---- storage + rate limits (outside the web root) ----------------------------------
$data = dirname(__DIR__, 3) . '/geotag-report-data';
if (!is_dir($data) && !@mkdir($data, 0700, true)) {
    $data = sys_get_temp_dir() . '/geotag-report-data';
    @mkdir($data, 0700, true);
}
$saltFile = "$data/salt";
if (!is_file($saltFile)) @file_put_contents($saltFile, bin2hex(random_bytes(16)));
$salt = (string)@file_get_contents($saltFile);

function hit(string $file, int $window, int $limit): bool {
    $now = time();
    $fh = @fopen($file, 'c+');
    if (!$fh) return true; // never block reports because the limiter can't write
    flock($fh, LOCK_EX);
    $times = array_filter(array_map('intval', explode(',', (string)stream_get_contents($fh))), fn($t) => $t > $now - $window);
    $ok = count($times) < $limit;
    if ($ok) $times[] = $now;
    ftruncate($fh, 0); rewind($fh); fwrite($fh, implode(',', $times));
    flock($fh, LOCK_UN); fclose($fh);
    return $ok;
}

$ipHash = substr(hash('sha256', $salt . ($_SERVER['REMOTE_ADDR'] ?? '')), 0, 24);
@mkdir("$data/rate", 0700);
if (!hit("$data/rate/$ipHash", 3600, PER_IP_HOUR)) reply(429, ['ok' => false, 'error' => 'Too many reports, try again in an hour']);
if (!hit("$data/rate/_global", 86400, GLOBAL_DAY)) reply(429, ['ok' => false, 'error' => 'Report service busy, try again tomorrow']);

$id = gmdate('Ymd-His') . '-' . bin2hex(random_bytes(3));
$dir = "$data/reports/$id";
@mkdir($dir, 0700, true);

// ---- attachments -------------------------------------------------------------------
$files = [];   // [name, mime, path]
$skipped = [];
if (isset($_FILES['attachment']) && is_array($_FILES['attachment']['tmp_name'] ?? null)) {
    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $images = 0; $videos = 0;
    foreach ($_FILES['attachment']['tmp_name'] as $i => $tmp) {
        $err = $_FILES['attachment']['error'][$i] ?? UPLOAD_ERR_NO_FILE;
        if ($err !== UPLOAD_ERR_OK || !is_uploaded_file($tmp)) { $skipped[] = "file $i (upload error $err)"; continue; }
        $size = filesize($tmp) ?: 0;
        $mime = $finfo->file($tmp) ?: '';
        $isImage = in_array($mime, ['image/jpeg', 'image/png', 'image/webp'], true);
        $isVideo = in_array($mime, ['video/mp4', 'video/webm'], true);
        if ($isImage && $images < MAX_IMAGES && $size <= MAX_IMAGE_BYTES) {
            $images++;
            $dest = "$dir/screenshot-$images.jpg";
            $ok = false; $outMime = 'image/jpeg';
            if (function_exists('imagecreatefromstring')) {
                $img = @imagecreatefromstring((string)file_get_contents($tmp));
                if ($img !== false) { $ok = imagejpeg($img, $dest, 85); imagedestroy($img); }
            }
            if (!$ok) {
                // No GD, or GD can't read it: keep the original bytes, still typed by content.
                $ext = ['image/jpeg' => 'jpg', 'image/png' => 'png', 'image/webp' => 'webp'][$mime];
                $dest = "$dir/screenshot-$images.$ext";
                $ok = move_uploaded_file($tmp, $dest);
                $outMime = $mime;
            }
            if ($ok) $files[] = [basename($dest), $outMime, $dest];
        } elseif ($isVideo && $videos < MAX_VIDEOS && $size <= MAX_VIDEO_BYTES) {
            $videos++;
            $dest = "$dir/recording-$videos." . ($mime === 'video/webm' ? 'webm' : 'mp4');
            if (move_uploaded_file($tmp, $dest)) $files[] = [basename($dest), $mime, $dest];
        } else {
            $skipped[] = "file $i ($mime, " . round($size / 1048576, 1) . ' MB, not accepted)';
        }
    }
}

// ---- keep a copy, then mail --------------------------------------------------------
$saved = @file_put_contents("$dir/report.json", json_encode(
    ['id' => $id, 'kind' => $kind, 'message' => $message, 'app' => $app, 'device' => $device,
     'crash' => $crash, 'log' => $log, 'contact' => $contact,
     'attachments' => array_map(fn($f) => $f[0], $files), 'skipped' => $skipped],
    JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE
)) !== false;

$label   = ['problem' => 'Problem', 'idea' => 'Idea', 'question' => 'Question'][$kind];
$first   = one_line(mb_substr(strtok($message, "\n") ?: $message, 0, 70));
$subject = "[GeoTag Camera] $label: $first" . ($crash !== '' ? ' [crash]' : '') . ($files ? ' [' . count($files) . ' file' . (count($files) > 1 ? 's' : '') . ']' : '');

$text  = "$message\n\n";
$text .= "-- Report $id\n";
if ($app !== '')     $text .= "App: $app\n";
if ($contact !== '') $text .= "Reply to: $contact\n";
if ($device !== '')  $text .= "\n-- Device --\n$device\n";
if ($crash !== '')   $text .= "\n-- Crash details --\n$crash\n";
if ($log !== '')     $text .= "\n-- App log --\n$log\n";

// Attach what fits in one email; the rest stays on the server (hPanel File Manager).
$attach = []; $total = 0; $left = [];
foreach ($files as $f) {
    $sz = filesize($f[2]) ?: 0;
    if ($total + $sz <= MAX_MAIL_ATTACH) { $attach[] = $f; $total += $sz; } else { $left[] = $f[0]; }
}
if ($left)    $text .= "\n-- Too large to email, kept on the server in geotag-report-data/reports/$id/ --\n" . implode("\n", $left) . "\n";
if ($skipped) $text .= "\n-- Refused attachments --\n" . implode("\n", $skipped) . "\n";

$boundary = 'b' . bin2hex(random_bytes(12));
$headers = [
    'From: GeoTag Camera reports <' . FROM_EMAIL . '>',
    'Reply-To: ' . ($contact !== '' ? $contact : FROM_EMAIL),
    'MIME-Version: 1.0',
    "Content-Type: multipart/mixed; boundary=\"$boundary\"",
    'X-GeoTag-Report: ' . $id,
];
$body  = "--$boundary\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n";
$body .= chunk_split(base64_encode($text)) . "\r\n";
foreach ($attach as [$name, $mime, $path]) {
    $body .= "--$boundary\r\nContent-Type: $mime; name=\"$name\"\r\nContent-Transfer-Encoding: base64\r\n";
    $body .= "Content-Disposition: attachment; filename=\"$name\"\r\n\r\n";
    $body .= chunk_split(base64_encode((string)file_get_contents($path))) . "\r\n";
}
$body .= "--$boundary--\r\n";

$encodedSubject = '=?UTF-8?B?' . base64_encode($subject) . '?=';
$mailed = @mail(TO_EMAIL, $encodedSubject, $body, implode("\r\n", $headers), '-f' . FROM_EMAIL);

if (!$mailed && !$saved) reply(502, ['ok' => false, 'error' => 'Could not deliver the report']);
reply(200, ['ok' => true, 'id' => $id]);
