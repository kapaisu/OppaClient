<?php
require __DIR__ . '/lib.php';

const SHOT_TYPES = [IMAGETYPE_PNG => 'png', IMAGETYPE_JPEG => 'jpg'];

function shot(string $id): array {
    if (!preg_match('/^[A-Za-z0-9]{10}$/', $id)) not_found();
    $q = db()->prepare('SELECT s.*, p.name FROM shots s JOIN players p ON p.uuid = s.uuid WHERE s.id = ?');
    $q->execute([$id]);
    return $q->fetch() ?: not_found();
}

function not_found(): never {
    http_response_code(404);
    header('Content-Type: text/plain');
    exit('Not found');
}

if (isset($_GET['raw'])) {
    $s = shot((string)$_GET['raw']);
    $file = storage('shots') . "/{$s['id']}.{$s['type']}";
    if (!is_file($file)) not_found();
    header('Content-Type: ' . ($s['type'] === 'png' ? 'image/png' : 'image/jpeg'));
    header('Content-Length: ' . filesize($file));
    header('Cache-Control: public, max-age=31536000, immutable');
    header("Content-Security-Policy: default-src 'none'");
    readfile($file);
    exit;
}

if (isset($_GET['view'])) {
    $s = shot((string)$_GET['view']);
    $name = htmlspecialchars($s['name'], ENT_QUOTES);
    $img = "https://oppaclient.com/s/{$s['id']}.{$s['type']}";
    $date = date('j F Y', (int)$s['created']);
    header('Content-Type: text/html; charset=utf-8');
    header('Cache-Control: public, max-age=300');
    echo <<<HTML
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Screenshot by $name</title>
<meta property="og:title" content="Screenshot by $name">
<meta property="og:site_name" content="Oppa">
<meta property="og:image" content="$img">
<meta property="og:image:width" content="{$s['width']}">
<meta property="og:image:height" content="{$s['height']}">
<meta name="twitter:card" content="summary_large_image">
<meta name="theme-color" content="#0b0b0c">
<link rel="icon" href="/assets/img/icon-64.png">
<link rel="stylesheet" href="/assets/shot.css">
</head>
<body>
<main>
  <a class="frame" href="$img"><img src="$img" width="{$s['width']}" height="{$s['height']}" alt="Screenshot by $name"></a>
  <p><b>$name</b> <span>$date</span> <a href="/">Oppa</a></p>
</main>
</body>
</html>
HTML;
    exit;
}

post_only();
$me = me();
$db = db();

if (str_starts_with($_SERVER['CONTENT_TYPE'] ?? '', 'application/json')) {
    $in = body();
    if (($in['action'] ?? '') !== 'delete' || !is_string($in['id'] ?? null)) fail('bad request');
    $q = $db->prepare('SELECT id, type FROM shots WHERE id = ? AND uuid = ?');
    $q->execute([$in['id'], $me['uuid']]);
    $s = $q->fetch();
    if (!$s) fail('not yours', 404);
    @unlink(storage('shots') . "/{$s['id']}.{$s['type']}");
    $db->prepare('DELETE FROM shots WHERE id = ?')->execute([$s['id']]);
    reply(['ok' => true]);
}

limit('shot:' . $me['uuid'], 60, 86400);
$data = raw_body(12 * 1024 * 1024);
$info = getimagesizefromstring($data);
if (!$info || !isset(SHOT_TYPES[$info[2]]) || $info[0] < 16 || $info[1] < 16 || $info[0] > 8192 || $info[1] > 8192) fail('not a PNG or JPEG');
$type = SHOT_TYPES[$info[2]];

$alphabet = 'abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789';
do {
    $id = '';
    for ($i = 0; $i < 10; $i++) $id .= $alphabet[random_int(0, strlen($alphabet) - 1)];
    $q = $db->prepare('SELECT 1 FROM shots WHERE id = ?');
    $q->execute([$id]);
} while ($q->fetchColumn());

if (file_put_contents(storage('shots') . "/$id.$type", $data) === false) fail('could not save', 500);
$db->prepare('INSERT INTO shots (id, uuid, type, width, height, bytes, created) VALUES (?, ?, ?, ?, ?, ?, ?)')
    ->execute([$id, $me['uuid'], $type, $info[0], $info[1], strlen($data), time()]);

reply(['id' => $id, 'url' => "https://oppaclient.com/s/$id"]);
