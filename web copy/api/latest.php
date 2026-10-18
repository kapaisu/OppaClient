<?php
require __DIR__ . '/lib.php';

$r = db()->query('SELECT version, notes FROM releases ORDER BY created DESC, id DESC LIMIT 1')->fetch();

header('Cache-Control: public, max-age=60');
header('Content-Type: application/json');
echo json_encode([
    'version' => $r['version'] ?? null,
    'notes' => $r['notes'] ?? '',
    'notice' => setting('notice'),
], JSON_UNESCAPED_SLASHES);
