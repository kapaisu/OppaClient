<?php
require __DIR__ . '/lib.php';

post_only();
$in = body();
$me = me();
$status = $in['status'] ?? null;
$statuses = ['launcher', 'menu', 'singleplayer', 'multiplayer', 'server', 'offline'];
if (!in_array($status, $statuses, true)) $status = empty($in['left']) ? 'menu' : 'launcher';
$playing = !in_array($status, ['launcher', 'offline'], true) && empty($in['left']) ? 1 : 0;
$server = $status === 'server' && valid_server($in['server'] ?? null) ? $in['server'] : '';
$game = is_string($in['game'] ?? null) && preg_match('/^[0-9a-z.\-]{1,20}$/', $in['game']) ? $in['game'] : '';

db()->prepare('UPDATE players SET playing = ?, status = ?, server = ?, game = ?, last_seen = ? WHERE uuid = ?')
    ->execute([$playing, $status, $server, $game, time(), $me['uuid']]);

reply(['ok' => true]);
