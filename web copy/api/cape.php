<?php
require __DIR__ . '/lib.php';

const CAPE_WIDTHS = [64, 128, 256, 512, 1024];

if ($_SERVER['REQUEST_METHOD'] === 'GET') {
    $hash = (string)($_GET['h'] ?? '');
    if (!preg_match('/^[0-9a-f]{40}$/', $hash)) fail('bad cape', 404);
    $q = db()->prepare('SELECT uuid, state FROM capes WHERE hash = ?');
    $q->execute([$hash]);
    $rows = $q->fetchAll();
    $public = (bool)array_filter($rows, fn($r) => $r['state'] === 'approved');
    if (!$public) {
        $owner = isset($_SERVER['HTTP_X_OPPA_TOKEN']) ? me()['uuid'] : null;
        if (!$owner || !array_filter($rows, fn($r) => $r['uuid'] === $owner)) fail('not found', 404);
    }
    $file = storage('capes') . "/$hash.png";
    if (!is_file($file)) fail('not found', 404);
    header('Content-Type: image/png');
    header('Content-Length: ' . filesize($file));
    header('Cache-Control: ' . ($public ? 'public, max-age=31536000, immutable' : 'private, no-store'));
    header("Content-Security-Policy: default-src 'none'");
    readfile($file);
    exit;
}

post_only();
$me = me();
$db = db();

if (str_starts_with($_SERVER['CONTENT_TYPE'] ?? '', 'application/json')) {
    $in = body();
    $id = (int)($in['id'] ?? 0);
    if (($in['action'] ?? '') !== 'delete') fail('bad request');
    $q = $db->prepare('SELECT hash FROM capes WHERE id = ? AND uuid = ?');
    $q->execute([$id, $me['uuid']]);
    $hash = $q->fetchColumn();
    if (!$hash) fail('not yours', 404);
    $db->prepare('DELETE FROM capes WHERE id = ?')->execute([$id]);
    $db->prepare('UPDATE looks SET cape = NULL WHERE uuid = ? AND cape = ?')->execute([$me['uuid'], $id]);
    $q = $db->prepare('SELECT 1 FROM capes WHERE hash = ?');
    $q->execute([$hash]);
    if (!$q->fetchColumn()) @unlink(storage('capes') . "/$hash.png");
    reply(['ok' => true]);
}

limit('cape:' . $me['uuid'], 20, 86400);
$data = raw_body(1024 * 1024);
$info = getimagesizefromstring($data);
if (!$info || $info[2] !== IMAGETYPE_PNG || !in_array($info[0], CAPE_WIDTHS, true) || $info[1] * 2 !== $info[0]) fail('capes must be a PNG twice as wide as tall');

$q = $db->prepare('SELECT COUNT(*) FROM capes WHERE uuid = ?');
$q->execute([$me['uuid']]);
if ($q->fetchColumn() >= 30) fail('delete an old cape first');

$hash = sha1($data);
$file = storage('capes') . "/$hash.png";
if (!is_file($file) && file_put_contents($file, $data) === false) fail('could not save', 500);

$q = $db->prepare('SELECT state FROM capes WHERE hash = ? AND state IN (\'approved\', \'rejected\') LIMIT 1');
$q->execute([$hash]);
$state = $q->fetchColumn() ?: 'pending';
$db->prepare('INSERT INTO capes (uuid, hash, state, created) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE created = VALUES(created)')
    ->execute([$me['uuid'], $hash, $state, time()]);
$q = $db->prepare('SELECT id, state FROM capes WHERE uuid = ? AND hash = ?');
$q->execute([$me['uuid'], $hash]);
$cape = $q->fetch();
$db->prepare('INSERT INTO looks (uuid, cape, updated) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE cape = VALUES(cape), updated = VALUES(updated)')
    ->execute([$me['uuid'], $cape['id'], time()]);

reply(['id' => (int)$cape['id'], 'hash' => $hash, 'state' => $cape['state'], 'url' => cape_url($hash)]);
