<?php
require __DIR__ . '/lib.php';

post_only();
$me = me();
$in = body();
$them = $in['uuid'] ?? null;
$action = $in['action'] ?? '';
if (!valid_uuid($them) || $them === $me['uuid']) fail('bad player');
$db = db();
$now = time();

$q = $db->prepare('SELECT 1 FROM players WHERE uuid = ?');
$q->execute([$them]);
if (!$q->fetchColumn()) fail('not an Oppa player', 404);

function befriend(PDO $db, string $a, string $b, int $now): void {
    $db->prepare('INSERT IGNORE INTO friends (a, b, created) VALUES (?, ?, ?), (?, ?, ?)')->execute([$a, $b, $now, $b, $a, $now]);
    $db->prepare('DELETE FROM requests WHERE (sender = ? AND target = ?) OR (sender = ? AND target = ?)')->execute([$a, $b, $b, $a]);
}

switch ($action) {
    case 'add':
        limit('friend:' . $me['uuid'], 30, 3600);
        if (friends($me['uuid'], $them)) reply(['state' => 'friends']);
        $q = $db->prepare('SELECT 1 FROM requests WHERE sender = ? AND target = ?');
        $q->execute([$them, $me['uuid']]);
        if ($q->fetchColumn()) {
            befriend($db, $me['uuid'], $them, $now);
            reply(['state' => 'friends']);
        }
        $q = $db->prepare('SELECT COUNT(*) FROM friends WHERE a = ?');
        $q->execute([$me['uuid']]);
        if ($q->fetchColumn() >= 300) fail('you have too many friends');
        $db->prepare('INSERT IGNORE INTO requests (sender, target, created) VALUES (?, ?, ?)')->execute([$me['uuid'], $them, $now]);
        reply(['state' => 'requested']);
    case 'accept':
        $q = $db->prepare('SELECT 1 FROM requests WHERE sender = ? AND target = ?');
        $q->execute([$them, $me['uuid']]);
        if (!$q->fetchColumn()) fail('no request from them', 404);
        befriend($db, $me['uuid'], $them, $now);
        reply(['state' => 'friends']);
    case 'decline':
    case 'cancel':
        $db->prepare('DELETE FROM requests WHERE (sender = ? AND target = ?) OR (sender = ? AND target = ?)')->execute([$them, $me['uuid'], $me['uuid'], $them]);
        reply(['state' => 'none']);
    case 'remove':
        $db->prepare('DELETE FROM friends WHERE (a = ? AND b = ?) OR (a = ? AND b = ?)')->execute([$me['uuid'], $them, $them, $me['uuid']]);
        reply(['state' => 'none']);
}
fail('bad action');
