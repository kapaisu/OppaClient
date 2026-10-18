<?php
require __DIR__ . '/lib.php';

post_only();
$name = body()['name'] ?? null;
if (!valid_name($name)) fail('bad name');

$ip = $_SERVER['REMOTE_ADDR'] ?? '';
$db = db();
$db->prepare('DELETE FROM nonces WHERE created < ?')->execute([time() - 300]);
$q = $db->prepare('SELECT COUNT(*) FROM nonces WHERE ip = ?');
$q->execute([$ip]);
if ($q->fetchColumn() >= 20) fail('slow down', 429);
$nonce = bin2hex(random_bytes(16));
$db->prepare('INSERT INTO nonces (nonce, name, ip, created) VALUES (?, ?, ?, ?)')->execute([$nonce, $name, $ip, time()]);

reply(['nonce' => $nonce]);
