<?php
require __DIR__ . '/lib.php';

post_only();
$me = me();
$name = body()['name'] ?? null;
if (!valid_name($name)) fail('bad name');
limit('lookup:' . $me['uuid'], 60, 60);

$q = db()->prepare('SELECT uuid, name FROM players WHERE name = ? ORDER BY last_seen DESC LIMIT 1');
$q->execute([$name]);
$p = $q->fetch();
if (!$p) reply(['oppa' => false, 'name' => $name]);

$q = db()->prepare('SELECT 1 FROM requests WHERE sender = ? AND target = ?');
$q->execute([$me['uuid'], $p['uuid']]);
$requested = (bool)$q->fetchColumn();
$q->execute([$p['uuid'], $me['uuid']]);
$wants = (bool)$q->fetchColumn();

reply([
    'oppa' => true,
    'uuid' => $p['uuid'],
    'name' => $p['name'],
    'me' => $p['uuid'] === $me['uuid'],
    'friend' => friends($me['uuid'], $p['uuid']),
    'requested' => $requested,
    'wants' => $wants,
]);
