<?php
/**
 * Buy Me a Coffee webhook receiver: keeps the website's supporters wall
 * current without polling BMC's API.
 *
 * BMC dashboard > Integrations > Webhooks:
 *   Endpoint URL: https://konkomaji.com/geotagcamera/api/bmc-webhook.php
 *   Events: Support created, Support refunded, Monthly support started,
 *           Monthly support cancelled, Membership started, Membership cancelled
 * After creating it, copy the webhook secret into
 *   geotag-report-data/bmc.ini   ->   webhook_secret = "..."
 * (outside public_html, never in the repository).
 *
 * Security: every delivery must carry a valid HMAC-SHA256 of the raw body
 * made with that secret (header X-Signature-Sha256, or X-BMC-Signature;
 * hex or base64). Anything else is refused and only its header names are
 * logged, never its body. Test deliveries (live_mode false) are stored
 * separately and never shown on the site.
 *
 * Privacy: the store keeps only what the wall needs: event id, kind, the
 * display name the supporter chose, coffee count, a public note (only when
 * the supporter made it public), date and refund state. No emails, no
 * amounts, no countries.
 */

declare(strict_types=1);
ini_set('display_errors', '0');
header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');

function done(int $code, string $msg): void { http_response_code($code); echo json_encode(['ok' => $code < 300, 'msg' => $msg]); exit; }

if (($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') done(405, 'POST only');

$data = dirname(__DIR__, 3) . '/geotag-report-data';
if (!is_dir($data)) @mkdir($data, 0700, true);
$raw = (string)file_get_contents('php://input', false, null, 0, 200001);
if (strlen($raw) > 200000) done(413, 'too large');

// ---- signature ---------------------------------------------------------------------------
$cfg = @parse_ini_file("$data/bmc.ini");
$secret = is_array($cfg) ? (string)($cfg['webhook_secret'] ?? '') : '';
// BMC's docs name the header differently in different places, so accept any
// header whose name contains SIGNATURE, as hex or base64, optional "sha256=".
$hmac = $secret !== '' ? hash_hmac('sha256', $raw, $secret, true) : '';
$sig = ''; $valid = false;
foreach ($_SERVER as $h => $v) {
    if (!is_string($v) || !str_starts_with($h, 'HTTP_') || !str_contains($h, 'SIGNATURE')) continue;
    $cand = preg_replace('/^sha256=/i', '', trim($v)) ?? '';
    $sig = $cand;
    if ($secret !== '' && $cand !== '' &&
        (hash_equals(bin2hex($hmac), strtolower($cand)) || hash_equals(base64_encode($hmac), $cand))) { $valid = true; break; }
}

if (!$valid) {
    // Header names only, so a misconfiguration can be diagnosed without storing the payload.
    $names = array_values(array_filter(array_keys($_SERVER), fn($k) => str_starts_with($k, 'HTTP_')));
    @file_put_contents("$data/bmc-webhook-rejected.log",
        date(DATE_ATOM) . ' ' . ($secret === '' ? 'no webhook_secret in bmc.ini' : ($sig === '' ? 'no signature header' : 'signature mismatch'))
        . ' headers=' . implode(',', $names) . "\n", FILE_APPEND | LOCK_EX);
    done($secret === '' ? 503 : 401, 'not verified');
}

// ---- event -------------------------------------------------------------------------------
$e = json_decode($raw, true);
if (!is_array($e)) done(400, 'bad json');
$type = strtolower((string)($e['type'] ?? $e['event'] ?? ''));
$d = is_array($e['data'] ?? null) ? $e['data'] : [];
$live = !array_key_exists('live_mode', $e) || !empty($e['live_mode']);
$id = (string)($d['id'] ?? $d['support_id'] ?? $d['membership_id'] ?? $e['event_id'] ?? '');
if ($id === '') $id = substr(hash('sha256', $raw), 0, 16);

function clean(string $s, int $max): string {
    $s = preg_replace('/[^\P{C}]/u', '', $s) ?? '';
    return mb_substr(trim(strip_tags($s)), 0, $max);
}

$store = $live ? "$data/bmc-supporters.json" : "$data/bmc-supporters-test.json";
$fh = fopen($store, 'c+');
if (!$fh) done(500, 'store');
flock($fh, LOCK_EX);
$all = json_decode((string)stream_get_contents($fh), true);
if (!is_array($all)) $all = [];

$isNew = preg_match('/(created|started)$/', $type) === 1;
$isGone = preg_match('/(refunded|cancelled|canceled)$/', $type) === 1;

if ($isNew) {
    $name = clean((string)($d['supporter_name'] ?? $d['payer_name'] ?? $d['name'] ?? ''), 40);
    $coffees = (int)($d['coffee_count'] ?? $d['support_coffees'] ?? $d['number_of_coffees'] ?? $d['quantity'] ?? 1);
    // Notes are published only when the payload says plainly that they are public.
    $vis = $d['support_visibility'] ?? $d['is_public'] ?? $d['note_visibility'] ?? null;
    $public = $vis === 1 || $vis === '1' || $vis === true || $vis === 'public';
    if (array_key_exists('is_private', $d)) $public = $public && empty($d['is_private']);
    $note = $public ? clean((string)($d['support_note'] ?? $d['message'] ?? $d['note'] ?? ''), 160) : '';
    $all[$id] = [
        'kind' => str_contains($type, 'membership') || str_contains($type, 'recurring') || str_contains($type, 'monthly') ? 'member' : 'coffee',
        'name' => $name !== '' ? $name : 'Someone',
        'coffees' => max(1, $coffees),
        'note' => $note,
        'date' => gmdate('Y-m-d', is_numeric($e['created'] ?? null) ? (int)$e['created'] : time()),
        'gone' => false,
    ];
} elseif ($isGone && isset($all[$id])) {
    $all[$id]['gone'] = true;
}
// Field names seen, for checking the mapping above (names only, no values).
@file_put_contents("$data/bmc-webhook-fields.log", date(DATE_ATOM) . " $type live=" . ($live ? 1 : 0) . ' top=' . implode(',', array_keys($e)) . ' data=' . implode(',', array_keys($d)) . "\n", FILE_APPEND | LOCK_EX);

ftruncate($fh, 0); rewind($fh);
fwrite($fh, json_encode($all, JSON_UNESCAPED_UNICODE | JSON_PRETTY_PRINT));
flock($fh, LOCK_UN); fclose($fh);
@unlink("$data/stats-cache.json"); // show it on the site right away
done(200, 'stored');
