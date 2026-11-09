<?php
/**
 * Live numbers for the website, cached on this server:
 *  - total APK downloads, the latest version and its date, and the star count
 *    (GitHub's public API, no token needed)
 *  - Buy Me a Coffee supporters for the shout-out wall, from the store that
 *    api/bmc-webhook.php keeps current (or, failing that, BMC's API with a
 *    personal access token in geotag-report-data/bmc.ini: token = "...")
 *
 * Visitors' browsers only ever talk to this site: GitHub and Buy Me a Coffee
 * never see who is looking, and the BMC token never leaves the server.
 * Refreshed at most every CACHE_SECONDS; on any upstream failure the last
 * good copy is served.
 *
 * Supporter privacy: only the name a supporter chose to show is published
 * (Buy Me a Coffee lets them pick "Someone"), plus their message only when
 * they made it public. Emails, countries and amounts in money are never
 * output.
 */

declare(strict_types=1);
ini_set('display_errors', '0');

const REPO          = 'konkomaji/geotagcamera';
const BMC_API       = 'https://developers.buymeacoffee.com/api/v1/supporters';
const CACHE_SECONDS = 1800;
const MAX_SHOWN     = 24;

header('Content-Type: application/json; charset=utf-8');
header('X-Content-Type-Options: nosniff');
header('Cache-Control: public, max-age=600');

$data = dirname(__DIR__, 3) . '/geotag-report-data';
if (!is_dir($data)) @mkdir($data, 0700, true);
$cacheFile = "$data/stats-cache.json";

$cached = is_file($cacheFile) ? json_decode((string)@file_get_contents($cacheFile), true) : null;
if (is_array($cached) && ($cached['updated'] ?? 0) > time() - CACHE_SECONDS) {
    echo json_encode($cached, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    exit;
}

function get_json(string $url, array $headers = []): ?array {
    $ctx = stream_context_create(['http' => [
        'method' => 'GET', 'timeout' => 8, 'ignore_errors' => true,
        'header' => implode("\r\n", array_merge(['User-Agent: GeoTagCamera-site', 'Accept: application/json'], $headers)),
    ]]);
    $body = @file_get_contents($url, false, $ctx);
    if ($body === false) return null;
    $status = 0;
    foreach ($http_response_header ?? [] as $h) if (preg_match('~^HTTP/\S+ (\d{3})~', $h, $m)) $status = (int)$m[1];
    if ($status < 200 || $status >= 300) return null;
    $j = json_decode($body, true);
    return is_array($j) ? $j : null;
}

function clean(string $s, int $max): string {
    $s = preg_replace('/[^\P{C}]/u', '', $s) ?? '';
    return mb_substr(trim(strip_tags($s)), 0, $max);
}

$out = is_array($cached) ? $cached : ['downloads' => null, 'version' => null, 'released' => null, 'stars' => null, 'supporters' => null];

// ---- GitHub ------------------------------------------------------------------------------
$releases = get_json('https://api.github.com/repos/' . REPO . '/releases?per_page=100');
if ($releases !== null) {
    $downloads = 0;
    foreach ($releases as $r) {
        foreach ($r['assets'] ?? [] as $a) {
            if (str_ends_with((string)($a['name'] ?? ''), '.apk')) $downloads += (int)($a['download_count'] ?? 0);
        }
    }
    $latest = null;
    foreach ($releases as $r) if (empty($r['draft']) && empty($r['prerelease'])) { $latest = $r; break; }
    $out['downloads'] = $downloads;
    if ($latest) {
        $out['version'] = ltrim((string)$latest['tag_name'], 'v');
        $out['released'] = substr((string)$latest['published_at'], 0, 10);
    }
}
$repo = get_json('https://api.github.com/repos/' . REPO);
if ($repo !== null) $out['stars'] = (int)($repo['stargazers_count'] ?? 0);

// ---- Buy Me a Coffee ------------------------------------------------------------------------
// Preferred: the store kept up to date by api/bmc-webhook.php. Fallback: BMC's API with a token.
$bmc = @parse_ini_file("$data/bmc.ini");
$hook = is_file("$data/bmc-supporters.json") ? json_decode((string)@file_get_contents("$data/bmc-supporters.json"), true) : null;
if (is_array($hook)) {
    $rows = array_values(array_filter($hook, fn($r) => is_array($r) && empty($r['gone'])));
    usort($rows, fn($a, $b) => strcmp((string)$b['date'], (string)$a['date']));
    $out['supporters'] = [
        'count' => count($rows),
        'coffees' => array_sum(array_map(fn($r) => (int)$r['coffees'], array_filter($rows, fn($r) => $r['kind'] === 'coffee'))),
        'members' => count(array_filter($rows, fn($r) => $r['kind'] === 'member')),
        // Only supporters who left a public message are listed; everyone counts.
        'messages' => array_map(fn($r) => ['name' => $r['name'], 'note' => $r['note']],
                                array_slice(array_values(array_filter($rows, fn($r) => $r['note'] !== '')), 0, MAX_SHOWN)),
    ];
} elseif (is_array($bmc) && !empty($bmc['token'])) {
    $list = []; $coffees = 0; $count = 0; $ok = false;
    for ($page = 1; $page <= 5; $page++) {
        $j = get_json(BMC_API . '?page=' . $page, ['Authorization: Bearer ' . $bmc['token']]);
        if ($j === null) break;
        $ok = true;
        foreach ($j['data'] ?? [] as $s) {
            if (!empty($s['is_refunded'])) continue;
            $n = (int)($s['support_coffees'] ?? 1);
            $coffees += max(1, $n); $count++;
            if (count($list) >= MAX_SHOWN) continue;
            $name = clean((string)($s['supporter_name'] ?? ''), 40);
            // A note is only shown when the supporter made it public.
            $public = (string)($s['support_visibility'] ?? '0') === '1';
            $note = $public ? clean((string)($s['support_note'] ?? ''), 160) : '';
            $list[] = ['name' => $name !== '' ? $name : 'Someone', 'coffees' => max(1, $n), 'note' => $note,
                       'date' => substr((string)($s['support_created_on'] ?? ''), 0, 10)];
        }
        if (empty($j['next_page_url'])) break;
    }
    if ($ok) $out['supporters'] = ['count' => $count, 'coffees' => $coffees,
        'messages' => array_values(array_map(fn($r) => ['name' => $r['name'], 'note' => $r['note']], array_filter($list, fn($r) => $r['note'] !== '')))];
} else {
    $out['supporters'] = null;
}

$out['updated'] = time();
@file_put_contents($cacheFile, json_encode($out, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));
echo json_encode($out, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
