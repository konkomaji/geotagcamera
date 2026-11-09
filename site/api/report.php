<?php
/**
 * GeoTag Camera in-app report endpoint.
 *
 * The app's "Send report" button POSTs multipart/form-data here: a "report"
 * JSON part plus optional "attachment[]" files (up to 3 screenshots, 1 screen
 * recording). Genuine reports are emailed to the developer and a copy is kept
 * outside public_html; spam is quarantined and never emailed. Nothing is sent
 * by the app unless the user presses Send.
 *
 * Deploy: uploaded with the website to public_html/geotagcamera/api/report.php.
 * Needs PHP 8.0+ with mbstring and fileinfo; GD is used when present.
 *
 * The app is open source, so no secret in it would stay secret. Defences are
 * layered instead, each cheap for one real user and costly for a spammer:
 *  1. Client checks: app User-Agent and an X-GeoTag-Client header. The custom
 *     header forces a CORS preflight that this endpoint never approves, so web
 *     pages cannot make visitors' browsers post reports.
 *  2. Proof of work: the app hashes for about a second to find a nonce that
 *     binds the report's own content and a timestamp (header X-GeoTag-PoW).
 *     Checking it costs one hash. Stale or replayed proofs are refused.
 *  3. Rate limits per IP (IPv6 by /64) per hour and per day, and globally.
 *  4. Spam score: links, known spam phrases, an app version that doesn't look
 *     like ours, a form filled in too fast, a repeat of a recent message.
 *     High scores are quarantined silently (the sender sees success); middling
 *     ones are emailed tagged [possible spam].
 *  5. Storage caps: old reports deleted after 30 days; new ones refused while
 *     the store is over its size cap, so the site's disk can't be filled.
 *
 * Attachments: type decided from the file's bytes (finfo), never the name or
 * the client's Content-Type. JPEG/PNG/WebP images only up to 40 megapixels
 * (no decompression bombs), re-encoded through GD, which drops metadata and
 * anything hidden in the file. MP4/WebM video is kept on the server only and
 * never emailed (memory). Files get fixed names in a random folder outside
 * the web root and are never served by URL.
 */

declare(strict_types=1);

ini_set('display_errors', '0');
error_reporting(E_ALL);

const TO_EMAIL        = 'konkomaji@gmail.com';
const FROM_EMAIL      = 'noreply@konkomaji.com';   // must be on the site's own domain
const MAX_JSON        = 300000;                    // bytes of report JSON
const MAX_IMAGES      = 3;
const MAX_VIDEOS      = 1;
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const MAX_PIXELS      = 40000000;
const MAX_VIDEO_BYTES = 20 * 1024 * 1024;
const PER_IP_HOUR     = 5;
const PER_IP_DAY      = 15;
const GLOBAL_DAY      = 200;
const POW_BITS        = 20;                        // about 1M hashes on average: ~1 s on a phone
const POW_MAX_AGE     = 900;                       // seconds
const KEEP_DAYS       = 30;
const STORE_CAP       = 1024 * 1024 * 1024;        // 1 GB of stored reports
const SPAM_QUARANTINE = 5;
const SPAM_FLAG       = 3;

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

function dir_size(string $dir): int {
    $total = 0;
    if (!is_dir($dir)) return 0;
    $it = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($dir, FilesystemIterator::SKIP_DOTS));
    foreach ($it as $f) $total += $f->getSize();
    return $total;
}

function rm_tree(string $dir): void {
    if (!is_dir($dir)) return;
    $it = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($dir, FilesystemIterator::SKIP_DOTS), RecursiveIteratorIterator::CHILD_FIRST);
    foreach ($it as $f) $f->isDir() ? @rmdir($f->getPathname()) : @unlink($f->getPathname());
    @rmdir($dir);
}

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

/** True when sha256($input) starts with $bits zero bits. */
function leading_zero_bits(string $input, int $bits): bool {
    $h = hash('sha256', $input, true);
    $full = intdiv($bits, 8);
    for ($i = 0; $i < $full; $i++) if ($h[$i] !== "\0") return false;
    $rest = $bits % 8;
    return $rest === 0 || (ord($h[$full]) >> (8 - $rest)) === 0;
}

// ---- 1. request shape and client checks ----------------------------------------------
if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') reply(405, ['ok' => false, 'error' => 'POST only']);
if (($_SERVER['HTTP_X_GEOTAG_CLIENT'] ?? '') !== '1' || !str_starts_with($_SERVER['HTTP_USER_AGENT'] ?? '', 'GeoTagCamera/')) {
    reply(403, ['ok' => false, 'error' => 'Reports are sent from the GeoTag Camera app']);
}
// PHP drops the whole body when post_max_size is exceeded.
if (empty($_POST) && (int)($_SERVER['CONTENT_LENGTH'] ?? 0) > 0) reply(413, ['ok' => false, 'error' => 'Report too large']);

$raw = $_POST['report'] ?? '';
if (!is_string($raw) || $raw === '') reply(400, ['ok' => false, 'error' => 'Bad request']);
if (strlen($raw) > MAX_JSON) reply(413, ['ok' => false, 'error' => 'Report too large']);
$in = json_decode($raw, true);
if (!is_array($in)) reply(400, ['ok' => false, 'error' => 'Bad request']);

// Honeypot: the app always sends it empty. Bots that fill every field get a fake success.
if (($in['website'] ?? '') !== '') reply(200, ['ok' => true, 'id' => 'ok']);

// ---- 2. proof of work --------------------------------------------------------------------
$contentHash = hash('sha256', $raw);
$pow = explode(':', (string)($_SERVER['HTTP_X_GEOTAG_POW'] ?? ''));
if (count($pow) !== 2 || !ctype_digit($pow[0]) || !ctype_digit($pow[1])) reply(400, ['ok' => false, 'error' => 'Please update the app and try again']);
[$powTs, $powNonce] = $pow;
if (abs(time() - (int)$powTs) > POW_MAX_AGE) reply(400, ['ok' => false, 'error' => "Your phone's clock looks wrong. Check the date and time, then try again"]);
if (!leading_zero_bits("geotag-report-v1:$powTs:$contentHash:$powNonce", POW_BITS)) reply(400, ['ok' => false, 'error' => 'Please update the app and try again']);

// ---- storage (outside the web root) --------------------------------------------------
$data = dirname(__DIR__, 3) . '/geotag-report-data';
if (!is_dir($data) && !@mkdir($data, 0700, true)) {
    $data = sys_get_temp_dir() . '/geotag-report-data';
    @mkdir($data, 0700, true);
}
foreach (['rate', 'seen', 'reports', 'spam'] as $d) @mkdir("$data/$d", 0700);
$saltFile = "$data/salt";
if (!is_file($saltFile)) @file_put_contents($saltFile, bin2hex(random_bytes(16)));
$salt = (string)@file_get_contents($saltFile);

// Replay: a proof (and so a report body) is accepted once.
$seenFile = "$data/seen/" . substr($contentHash, 0, 32);
if (is_file($seenFile)) reply(200, ['ok' => true, 'id' => trim((string)@file_get_contents($seenFile)) ?: 'ok']);

// Housekeeping, at most once an hour: expire old reports, rate and replay files.
$sweep = "$data/last_sweep";
if (!is_file($sweep) || filemtime($sweep) < time() - 3600) {
    @touch($sweep);
    foreach (['reports', 'spam'] as $d) {
        foreach (glob("$data/$d/*", GLOB_ONLYDIR) ?: [] as $r) if (filemtime($r) < time() - KEEP_DAYS * 86400) rm_tree($r);
    }
    foreach (glob("$data/rate/*") ?: [] as $f) if (filemtime($f) < time() - 86400) @unlink($f);
    foreach (glob("$data/seen/*") ?: [] as $f) if (filemtime($f) < time() - 86400) @unlink($f);
}
if (dir_size("$data/reports") + dir_size("$data/spam") > STORE_CAP) {
    reply(503, ['ok' => false, 'error' => 'Report service is full right now. Please email ' . TO_EMAIL]);
}

// ---- 3. rate limits ----------------------------------------------------------------------
$ip = $_SERVER['REMOTE_ADDR'] ?? '';
if (filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_IPV6)) {
    // One person can rotate through a whole /64; count it as one address.
    $ip = implode(':', array_slice(explode(':', inet_ntop(inet_pton($ip))), 0, 4)) . '::/64';
}
$ipHash = substr(hash('sha256', $salt . $ip), 0, 24);
if (!hit("$data/rate/$ipHash-h", 3600, PER_IP_HOUR)) reply(429, ['ok' => false, 'error' => 'Too many reports, try again in an hour']);
if (!hit("$data/rate/$ipHash-d", 86400, PER_IP_DAY)) reply(429, ['ok' => false, 'error' => 'Too many reports today, try again tomorrow']);
if (!hit("$data/rate/_global", 86400, GLOBAL_DAY)) reply(429, ['ok' => false, 'error' => 'Report service busy, try again tomorrow']);

// ---- fields ----------------------------------------------------------------------------
$kind     = in_array($in['kind'] ?? '', ['problem', 'idea', 'question'], true) ? $in['kind'] : 'problem';
$message  = field($in, 'message', 4000);
$app      = one_line(field($in, 'app', 200));
$device   = field($in, 'device', 2000);
$crash    = field($in, 'crash', 20000);
$log      = field($in, 'log', 200000);
$contact  = one_line(field($in, 'contact', 200));
$elapsed  = is_int($in['elapsed_ms'] ?? null) ? $in['elapsed_ms'] : -1;
if ($contact !== '' && !filter_var($contact, FILTER_VALIDATE_EMAIL)) $contact = '';
if (mb_strlen($message) < 5) reply(422, ['ok' => false, 'error' => 'Message too short']);

// ---- 4. spam score -----------------------------------------------------------------------
$score = 0; $why = [];
$links = preg_match_all('~(https?://|www\.)\S+~i', $message);
if ($links > 2) { $score += 2 * ($links - 2); $why[] = "$links links"; }
$spamWords = '~\b(casino|viagra|cialis|crypto(currency)?|bitcoin|forex|loan|escort|porn|seo services?|backlinks?|guest post|'
    . 'web ?design services|rank (your|on) google|whatsapp me|telegram me|earn \$|work from home|investment opportunity|airdrop)\b~i';
if (preg_match_all($spamWords, $message, $m)) { $score += 3; $why[] = 'spam phrases: ' . implode(', ', array_unique(array_map('strtolower', $m[0]))); }
if (!preg_match('~^\d+\.\d+\.\d+ \(\d+, (foss|play)\)$~', $app)) { $score += 3; $why[] = 'app version not from the app'; }
if ($elapsed >= 0 && $elapsed < 4000) { $score += 2; $why[] = "form filled in {$elapsed} ms"; }
$letters = preg_match_all('/\p{L}/u', $message);
if ($letters < mb_strlen($message) * 0.4) { $score += 1; $why[] = 'mostly non-letters'; }
$msgKey = "$data/seen/m-" . substr(hash('sha256', $salt . mb_strtolower(preg_replace('/\s+/', ' ', $message))), 0, 32);
if (is_file($msgKey)) { $score += 3; $why[] = 'same message in the last 24 h'; }
@touch($msgKey);

$id = gmdate('Ymd-His') . '-' . bin2hex(random_bytes(3));
$quarantine = $score >= SPAM_QUARANTINE;
$dir = "$data/" . ($quarantine ? 'spam' : 'reports') . "/$id";
@mkdir($dir, 0700, true);
@file_put_contents($seenFile, $id);

// ---- attachments -------------------------------------------------------------------
$images = []; $videos = []; $skipped = [];
if (isset($_FILES['attachment']) && is_array($_FILES['attachment']['tmp_name'] ?? null)) {
    $finfo = new finfo(FILEINFO_MIME_TYPE);
    foreach ($_FILES['attachment']['tmp_name'] as $i => $tmp) {
        $err = $_FILES['attachment']['error'][$i] ?? UPLOAD_ERR_NO_FILE;
        if ($err !== UPLOAD_ERR_OK || !is_uploaded_file($tmp)) { $skipped[] = "file $i (upload error $err)"; continue; }
        $size = filesize($tmp) ?: 0;
        $mime = $finfo->file($tmp) ?: '';
        if (in_array($mime, ['image/jpeg', 'image/png', 'image/webp'], true)) {
            $dims = @getimagesize($tmp);
            if (count($images) >= MAX_IMAGES || $size > MAX_IMAGE_BYTES || !$dims || $dims[0] * $dims[1] > MAX_PIXELS) {
                $skipped[] = "image $i ($mime, " . round($size / 1048576, 1) . ' MB, ' . ($dims ? "{$dims[0]}x{$dims[1]}" : 'unreadable') . ', not accepted)';
                continue;
            }
            $n = count($images) + 1;
            $dest = "$dir/screenshot-$n.jpg"; $outMime = 'image/jpeg'; $ok = false;
            if (function_exists('imagecreatefromstring')) {
                $img = @imagecreatefromstring((string)file_get_contents($tmp));
                if ($img !== false) { $ok = imagejpeg($img, $dest, 85); imagedestroy($img); }
            }
            if (!$ok) {
                // No GD, or GD can't read it: keep the original bytes, still typed by content.
                $dest = "$dir/screenshot-$n." . ['image/jpeg' => 'jpg', 'image/png' => 'png', 'image/webp' => 'webp'][$mime];
                $ok = move_uploaded_file($tmp, $dest); $outMime = $mime;
            }
            if ($ok) $images[] = [basename($dest), $outMime, $dest];
        } elseif (in_array($mime, ['video/mp4', 'video/webm'], true) && count($videos) < MAX_VIDEOS && $size <= MAX_VIDEO_BYTES) {
            $dest = "$dir/recording-1." . ($mime === 'video/webm' ? 'webm' : 'mp4');
            if (move_uploaded_file($tmp, $dest)) $videos[] = basename($dest);
        } else {
            $skipped[] = "file $i ($mime, " . round($size / 1048576, 1) . ' MB, not accepted)';
        }
    }
}

// ---- keep a copy -----------------------------------------------------------------------
$saved = @file_put_contents("$dir/report.json", json_encode(
    ['id' => $id, 'kind' => $kind, 'message' => $message, 'app' => $app, 'device' => $device,
     'crash' => $crash, 'log' => $log, 'contact' => $contact, 'spam_score' => $score, 'spam_reasons' => $why,
     'images' => array_map(fn($f) => $f[0], $images), 'videos' => $videos, 'skipped' => $skipped],
    JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE
)) !== false;

// Quarantined: never emailed. The sender sees the same success as anyone else.
if ($quarantine) reply(200, ['ok' => true, 'id' => $id]);

// ---- mail ------------------------------------------------------------------------------
$label   = ['problem' => 'Problem', 'idea' => 'Idea', 'question' => 'Question'][$kind];
$first   = one_line(mb_substr(strtok($message, "\n") ?: $message, 0, 70));
$nFiles  = count($images) + count($videos);
$subject = ($score >= SPAM_FLAG ? '[possible spam] ' : '') . "[GeoTag Camera] $label: $first"
    . ($crash !== '' ? ' [crash]' : '') . ($nFiles ? " [$nFiles file" . ($nFiles > 1 ? 's' : '') . ']' : '');

$text  = "$message\n\n";
$text .= "-- Report $id\n";
if ($app !== '')     $text .= "App: $app\n";
if ($contact !== '') $text .= "Reply to: $contact\n";
if ($score > 0)      $text .= "Spam score $score: " . implode('; ', $why) . "\n";
if ($device !== '')  $text .= "\n-- Device --\n$device\n";
if ($crash !== '')   $text .= "\n-- Crash details --\n$crash\n";
if ($log !== '')     $text .= "\n-- App log --\n$log\n";
if ($videos)         $text .= "\n-- Screen recording kept on the server (hPanel > File Manager) --\ngeotag-report-data/reports/$id/" . implode(', ', $videos) . "\n";
if ($skipped)        $text .= "\n-- Refused attachments --\n" . implode("\n", $skipped) . "\n";

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
foreach ($images as [$name, $mime, $path]) {
    $body .= "--$boundary\r\nContent-Type: $mime; name=\"$name\"\r\nContent-Transfer-Encoding: base64\r\n";
    $body .= "Content-Disposition: attachment; filename=\"$name\"\r\n\r\n";
    $body .= chunk_split(base64_encode((string)file_get_contents($path))) . "\r\n";
}
$body .= "--$boundary--\r\n";

$encodedSubject = '=?UTF-8?B?' . base64_encode($subject) . '?=';
$mailed = @mail(TO_EMAIL, $encodedSubject, $body, implode("\r\n", $headers), '-f' . FROM_EMAIL);

if (!$mailed && !$saved) reply(502, ['ok' => false, 'error' => 'Could not deliver the report']);
reply(200, ['ok' => true, 'id' => $id]);
