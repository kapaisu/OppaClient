<?php
require __DIR__ . '/lib.php';

post_only();
$me = me();
$in = body();
$them = $in['uuid'] ?? null;
if (!valid_uuid($them)) fail('bad player');
if (!friends($me['uuid'], $them)) fail('you can only invite friends', 403);

$q = db()->prepare('SELECT server, game, status FROM players WHERE uuid = ?');
$q->execute([$me['uuid']]);
$self = $q->fetch();
$server = valid_server($in['server'] ?? null) ? $in['server'] : $self['server'];
if ($server === '') fail('join a server first');
limit('invite:' . $me['uuid'] . ':' . $them, 1, 15);
limit('invite:' . $me['uuid'], 30, 3600);

db()->prepare('INSERT INTO invites (sender, target, server, game, created) VALUES (?, ?, ?, ?, ?)')
    ->execute([$me['uuid'], $them, $server, $self['game'], time()]);
reply(['ok' => true]);
