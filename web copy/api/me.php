<?php
require __DIR__ . '/lib.php';

post_only();
$me = me();
$db = db();
$cut = time() - ONLINE_FOR;

$q = $db->prepare('SELECT p.uuid, p.name, p.status, p.server, p.game, p.last_seen FROM friends f JOIN players p ON p.uuid = f.b WHERE f.a = ? ORDER BY p.name');
$q->execute([$me['uuid']]);
$friends = [];
foreach ($q->fetchAll() as $f) {
    $online = $f['last_seen'] > $cut && $f['status'] !== 'offline';
    $friends[] = [
        'uuid' => $f['uuid'],
        'name' => $f['name'],
        'status' => $online ? ($f['status'] ?: 'menu') : 'offline',
        'server' => $online ? $f['server'] : '',
        'game' => $online ? $f['game'] : '',
        'seen' => (int)$f['last_seen'],
    ];
}

$q = $db->prepare('SELECT p.uuid, p.name FROM requests r JOIN players p ON p.uuid = r.sender WHERE r.target = ? ORDER BY r.created DESC LIMIT 50');
$q->execute([$me['uuid']]);
$incoming = $q->fetchAll();

$q = $db->prepare('SELECT p.uuid, p.name FROM requests r JOIN players p ON p.uuid = r.target WHERE r.sender = ? ORDER BY r.created DESC LIMIT 50');
$q->execute([$me['uuid']]);
$outgoing = $q->fetchAll();

$db->prepare('DELETE FROM invites WHERE created < ?')->execute([time() - 600]);
$q = $db->prepare('SELECT i.id, p.uuid, p.name, i.server, i.game, i.created FROM invites i JOIN players p ON p.uuid = i.sender WHERE i.target = ? ORDER BY i.created DESC LIMIT 10');
$q->execute([$me['uuid']]);
$invites = array_map(fn($i) => ['id' => (int)$i['id'], 'uuid' => $i['uuid'], 'name' => $i['name'], 'server' => $i['server'], 'game' => $i['game'], 'created' => (int)$i['created']], $q->fetchAll());

$q = $db->prepare('SELECT cape, hat, wings, aura FROM looks WHERE uuid = ?');
$q->execute([$me['uuid']]);
$look = $q->fetch() ?: ['cape' => null, 'hat' => '', 'wings' => '', 'aura' => ''];

$q = $db->prepare('SELECT id, hash, state, created FROM capes WHERE uuid = ? AND state <> \'deleted\' ORDER BY created DESC LIMIT 30');
$q->execute([$me['uuid']]);
$capes = array_map(fn($c) => ['id' => (int)$c['id'], 'hash' => $c['hash'], 'state' => $c['state'], 'url' => cape_url($c['hash'])], $q->fetchAll());

reply([
    'uuid' => $me['uuid'],
    'name' => $me['name'],
    'friends' => $friends,
    'incoming' => $incoming,
    'outgoing' => $outgoing,
    'invites' => $invites,
    'look' => ['cape' => $look['cape'] === null ? null : (int)$look['cape'], 'hat' => $look['hat'], 'wings' => $look['wings'], 'aura' => $look['aura']],
    'capes' => $capes,
]);
