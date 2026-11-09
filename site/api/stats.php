<?php
/**
 * Live numbers for the website, cached on this server:
 *  - total APK downloads, the latest version and its date, and the star count
 *    (GitHub's public API, no token needed)
 *  - Buy Me a Coffee supporters for the shout-out wall (needs a personal
 *    access token in geotag-report-data/bmc.ini: token = "...")
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
$bmc = @parse_ini_file("$data/bmc.ini");
if (is_array($bmc) && !empty($bmc['token'])) {
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
    if ($ok) $out['supporters'] = ['count' => $count, 'coffees' => $coffees, 'recent' => $list];
} else {
    $out['supporters'] = null;
}

$out['updated'] = time();
@file_put_contents($cacheFile, json_encode($out, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE));
echo json_encode($out, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
