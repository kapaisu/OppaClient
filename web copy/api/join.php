<?php
require __DIR__ . '/lib.php';

post_only();
$in = body();
$name = $in['name'] ?? null;
$nonce = $in['nonce'] ?? null;
if (!valid_name($name) || !is_string($nonce) || !preg_match('/^[0-9a-f]{32}$/', $nonce)) fail('bad request');

$db = db();
$q = $db->prepare('DELETE FROM nonces WHERE nonce = ? AND name = ? AND created > ?');
$q->execute([$nonce, $name, time() - 300]);
if ($q->rowCount() !== 1) fail('unknown nonce', 403);

$ch = curl_init('https://sessionserver.mojang.com/session/minecraft/hasJoined?' . http_build_query(['username' => $name, 'serverId' => $nonce]));
curl_setopt_array($ch, [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 10]);
$res = curl_exec($ch);
$code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);
$profile = $code === 200 ? json_decode($res, true) : null;
if (!is_array($profile) || !valid_uuid($profile['id'] ?? null)) fail('not verified', 403);

$token = bin2hex(random_bytes(32));
$hash = hash('sha256', $token);
$now = time();
$launcher = !empty($in['launcher']);
$db->prepare('INSERT INTO players (uuid, name, token, os, version, playing, status, first_seen, last_seen)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON DUPLICATE KEY UPDATE name = VALUES(name), token = VALUES(token), os = VALUES(os),
        version = VALUES(version), playing = VALUES(playing), status = VALUES(status), last_seen = VALUES(last_seen)')
    ->execute([$profile['id'], $profile['name'], $hash, short($in['os'] ?? '', 16), short($in['version'] ?? '', 20),
        $launcher ? 0 : 1, $launcher ? 'launcher' : 'menu', $now, $now]);
$db->prepare('DELETE FROM sessions WHERE uuid = ? AND used < ?')->execute([$profile['id'], $now - 30 * 86400]);
$db->prepare('INSERT INTO sessions (token, uuid, created, used) VALUES (?, ?, ?, ?)')->execute([$hash, $profile['id'], $now, $now]);

reply(['token' => $token, 'uuid' => $profile['id'], 'name' => $profile['name']]);
