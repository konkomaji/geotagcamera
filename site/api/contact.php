<?php
/**
 * Website forms: "Join the Google Play test" and "Write to us".
 * Delivered to the project's inbox (hello@konkomaji.com by default; set
 * contact_to = "..." in geotag-report-data/smtp.ini to change it).
 *
 * Works with and without JavaScript: the page's script posts with
 * fetch + X-GeoTag-Client and gets JSON; a plain form post gets redirected
 * back to the page with ?sent=1 or ?error=... in the URL.
 *
 * Abuse limits: honeypot field, a minimum time on the page, per-IP (IPv6 by
 * /64, salted hash) and global rate limits, link and spam-phrase scoring with
 * silent quarantine, size caps, and one tester signup per address.
 *
 * Privacy: tester addresses are kept in geotag-report-data/testers.json
 * (outside the web root) only to send the Google Play test invitation; the
 * privacy policy says so, and any address is deleted on request.
 */

declare(strict_types=1);
ini_set('display_errors', '0');
require __DIR__ . '/mailer.php';

const FROM_EMAIL   = 'hello@konkomaji.com';
const PER_IP_HOUR  = 4;
const PER_IP_DAY   = 10;
const GLOBAL_DAY   = 150;
const MIN_SECONDS  = 3;
const ALLOWED_BACK = ['support/', 'download/', ''];

$wantsJson = ($_SERVER['HTTP_X_GEOTAG_CLIENT'] ?? '') === 'web';

function finish(bool $ok, string $msg, string $kind, bool $json): void {
    if ($json) {
        header('Content-Type: application/json; charset=utf-8');
        http_response_code($ok ? 200 : 400);
        echo json_encode(['ok' => $ok, 'msg' => $msg]);
        exit;
    }
    $back = (string)($_POST['back'] ?? 'support/');
    if (!in_array($back, ALLOWED_BACK, true)) $back = 'support/';
    $anchor = $kind === 'tester' ? '#beta' : '#contact';
    header('Location: /geotagcamera/' . $back . ($ok ? '?sent=' . $kind : '?error=' . rawurlencode($msg)) . $anchor, true, 303);
    exit;
}

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') { http_response_code(405); exit; }

$kind = ($_POST['kind'] ?? '') === 'tester' ? 'tester' : 'message';
$clean = function (string $k, int $max): string {
    $v = $_POST[$k] ?? '';
    if (!is_string($v)) return '';
    $v = preg_replace('/[^\P{C}\t\n]/u', '', $v) ?? '';
    return mb_substr(trim($v), 0, $max);
};
$email   = $clean('email', 200);
$name    = trim(preg_replace('/[\r\n]+/', ' ', $clean('name', 80)) ?? '');
$message = $clean('message', 3000);
$device  = trim(preg_replace('/[\r\n]+/', ' ', $clean('device', 120)) ?? '');
$started = (int)($_POST['t'] ?? 0);

// Honeypot: bots get the same success message and nothing happens.
if (($_POST['website'] ?? '') !== '') finish(true, 'Thanks!', $kind, $wantsJson);
if (!filter_var($email, FILTER_VALIDATE_EMAIL)) finish(false, 'Please enter a valid email address.', $kind, $wantsJson);
if ($kind === 'message' && mb_strlen($message) < 10) finish(false, 'Please write a little more (at least 10 characters).', $kind, $wantsJson);
if ($started > 0 && time() - $started < MIN_SECONDS) finish(true, 'Thanks!', $kind, $wantsJson);

// ---- storage + limits (outside the web root) --------------------------------------
$data = dirname(__DIR__, 3) . '/geotag-report-data';
if (!is_dir($data)) @mkdir($data, 0700, true);
@mkdir("$data/rate", 0700);
$saltFile = "$data/salt";
if (!is_file($saltFile)) @file_put_contents($saltFile, bin2hex(random_bytes(16)));
$salt = (string)@file_get_contents($saltFile);

function hit(string $file, int $window, int $limit): bool {
    $now = time();
    $fh = @fopen($file, 'c+');
    if (!$fh) return true;
    flock($fh, LOCK_EX);
    $times = array_filter(array_map('intval', explode(',', (string)stream_get_contents($fh))), fn($t) => $t > $now - $window);
    $ok = count($times) < $limit;
    if ($ok) $times[] = $now;
    ftruncate($fh, 0); rewind($fh); fwrite($fh, implode(',', $times));
    flock($fh, LOCK_UN); fclose($fh);
    return $ok;
}
$ip = $_SERVER['REMOTE_ADDR'] ?? '';
if (filter_var($ip, FILTER_VALIDATE_IP, FILTER_FLAG_IPV6)) $ip = implode(':', array_slice(explode(':', inet_ntop(inet_pton($ip))), 0, 4)) . '::/64';
$ipHash = substr(hash('sha256', $salt . $ip), 0, 24);
if (!hit("$data/rate/web-$ipHash-h", 3600, PER_IP_HOUR) || !hit("$data/rate/web-$ipHash-d", 86400, PER_IP_DAY)) {
    finish(false, 'Too many messages from your connection. Please try again later, or email ' . FROM_EMAIL . '.', $kind, $wantsJson);
}
if (!hit("$data/rate/_web_global", 86400, GLOBAL_DAY)) finish(false, 'The form is busy. Please email ' . FROM_EMAIL . '.', $kind, $wantsJson);

$smtp = @parse_ini_file("$data/smtp.ini");
if (!is_array($smtp)) $smtp = null;
$to = (is_array($smtp) && !empty($smtp['contact_to']) && filter_var($smtp['contact_to'], FILTER_VALIDATE_EMAIL)) ? $smtp['contact_to'] : FROM_EMAIL;

// ---- tester signup -----------------------------------------------------------------------
if ($kind === 'tester') {
    $file = "$data/testers.json";
    $fh = fopen($file, 'c+');
    if (!$fh) finish(false, 'Something went wrong. Please email ' . FROM_EMAIL . '.', $kind, $wantsJson);
    flock($fh, LOCK_EX);
    $list = json_decode((string)stream_get_contents($fh), true);
    if (!is_array($list)) $list = [];
    $key = strtolower($email);
    $already = isset($list[$key]);
    if (!$already) {
        $list[$key] = ['email' => $email, 'name' => $name, 'device' => $device, 'date' => gmdate('Y-m-d')];
        ftruncate($fh, 0); rewind($fh);
        fwrite($fh, json_encode($list, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES));
    }
    flock($fh, LOCK_UN); fclose($fh);
    if ($already) finish(true, "You're already on the list. We'll email you when the test opens.", $kind, $wantsJson);
    $n = count($list);
    send_plain($smtp, FROM_EMAIL, $to, "[GeoTag Camera] New Play Store tester ($n)",
        "New tester signup for the Google Play closed test.\n\nEmail: $email\nName: " . ($name ?: '-') . "\nPhone: " . ($device ?: '-') .
        "\n\nTesters so far: $n (list: geotag-report-data/testers.json)\n", $email);
    finish(true, "You're on the list. We'll email you an invitation when the Google Play test opens.", $kind, $wantsJson);
}

// ---- message -------------------------------------------------------------------------------
$score = 0;
$links = preg_match_all('~(https?://|www\.)\S+~i', $message);
if ($links > 2) $score += 2 * ($links - 2);
if (preg_match('~\b(casino|viagra|crypto(currency)?|bitcoin|forex|loan|escort|porn|seo services?|backlinks?|guest post|web ?design services|rank (your|on) google|whatsapp me|telegram me|investment opportunity|airdrop)\b~i', $message)) $score += 3;
if ($started > 0 && time() - $started < 8) $score += 1;
if ($score >= 4) {
    @mkdir("$data/spam", 0700);
    @file_put_contents("$data/spam/web-" . gmdate('Ymd-His') . '-' . bin2hex(random_bytes(3)) . '.json',
        json_encode(compact('email', 'name', 'message', 'score'), JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE));
    finish(true, "Thanks, your message was sent. We'll reply to $email.", $kind, $wantsJson);
}
$first = mb_substr(trim(strtok($message, "\n") ?: $message), 0, 60);
$sent = send_plain($smtp, FROM_EMAIL, $to, ($score >= 2 ? '[possible spam] ' : '') . "[GeoTag Camera] Website message: $first",
    "$message\n\n-- From the website contact form\nName: " . ($name ?: '-') . "\nEmail: $email (reply to this message to answer)\n", $email);
if (!$sent) finish(false, 'Your message could not be sent right now. Please email ' . FROM_EMAIL . '.', $kind, $wantsJson);
finish(true, "Thanks, your message was sent. We'll reply to $email.", $kind, $wantsJson);
