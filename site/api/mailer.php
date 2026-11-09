<?php
/**
 * Shared mail sending for report.php and contact.php. Defines functions only;
 * requesting this file directly returns 404.
 */
declare(strict_types=1);
if (realpath($_SERVER['SCRIPT_FILENAME'] ?? '') === __FILE__) { http_response_code(404); exit; }

/**
 * Minimal authenticated SMTP over TLS, so mail goes out through the domain's
 * own mailbox (DKIM-signed and SPF-aligned by the mail provider) instead of
 * the web server's mail(). Settings live in geotag-report-data/smtp.ini
 * outside the web root, never in the public repository:
 *   host = "smtp.hostinger.com"
 *   port = 465
 *   user = "hello@konkomaji.com"
 *   pass = "..."
 *   to   = "konkomaji@gmail.com"   (optional: where reports go)
 */
function smtp_send(array $cfg, string $to, string $subject, string $body, array $headers): bool {
    $host = (string)$cfg['host']; $port = (int)($cfg['port'] ?? 465);
    $ctx = stream_context_create(['ssl' => ['verify_peer' => true, 'verify_peer_name' => true, 'peer_name' => $host]]);
    $fp = @stream_socket_client("ssl://$host:$port", $errno, $errstr, 15, STREAM_CLIENT_CONNECT, $ctx);
    if (!$fp) return false;
    stream_set_timeout($fp, 30);
    $read = function () use ($fp): string {
        $out = '';
        while (($line = fgets($fp, 1024)) !== false) { $out .= $line; if (strlen($line) < 4 || $line[3] === ' ') break; }
        return $out;
    };
    $cmd = function (string $c, string $expect) use ($fp, $read): bool {
        if ($c !== '') fwrite($fp, $c . "\r\n");
        return str_starts_with($read(), $expect);
    };
    $user = (string)$cfg['user'];
    $ok = $cmd('', '220')
        && $cmd('EHLO ' . substr(strrchr($user, '@'), 1), '250')
        && $cmd('AUTH LOGIN', '334')
        && $cmd(base64_encode($user), '334')
        && $cmd(base64_encode((string)$cfg['pass']), '235')
        && $cmd("MAIL FROM:<$user>", '250')
        && $cmd("RCPT TO:<$to>", '25')
        && $cmd('DATA', '354');
    if ($ok) {
        $msg = implode("\r\n", array_merge(["To: <$to>", "Subject: $subject"], $headers)) . "\r\n\r\n" . $body;
        // Dot-stuffing: a line starting with "." must be doubled.
        $msg = preg_replace('/^\./m', '..', str_replace(["\r\n", "\n"], ["\n", "\r\n"], $msg));
        fwrite($fp, $msg . "\r\n");
        $ok = $cmd('.', '250');
    }
    @fwrite($fp, "QUIT\r\n");
    fclose($fp);
    return $ok;
}


/**
 * One plain-text email from the project's address, through SMTP when
 * smtp.ini is configured, else mail(). Subject and Reply-To are single-line.
 */
function send_plain(?array $smtp, string $from, string $to, string $subject, string $text, string $replyTo = ''): bool {
    $subject = '=?UTF-8?B?' . base64_encode(trim(preg_replace('/[\r\n]+/', ' ', $subject) ?? '')) . '?=';
    $replyTo = filter_var($replyTo, FILTER_VALIDATE_EMAIL) ? $replyTo : $from;
    $headers = [
        'Date: ' . date(DATE_RFC2822),
        'Message-ID: <' . bin2hex(random_bytes(8)) . '@' . substr(strrchr($from, '@'), 1) . '>',
        'From: GeoTag Camera website <' . $from . '>',
        'Reply-To: ' . $replyTo,
        'MIME-Version: 1.0',
        'Content-Type: text/plain; charset=UTF-8',
        'Content-Transfer-Encoding: base64',
    ];
    $body = chunk_split(base64_encode($text));
    if (is_array($smtp) && !empty($smtp['host']) && !empty($smtp['user']) && isset($smtp['pass'])) {
        if (smtp_send($smtp, $to, $subject, $body, $headers)) return true;
    }
    return @mail($to, $subject, $body, implode("\r\n", $headers), '-f' . $from);
}
