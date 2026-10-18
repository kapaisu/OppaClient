<?php
require __DIR__ . '/lib.php';

post_only();
$me = me();
$in = body();
limit('look:' . $me['uuid'], 120, 3600);
$cape = $in['cape'] ?? null;
if ($cape !== null) {
    $q = db()->prepare('SELECT id FROM capes WHERE id = ? AND uuid = ?');
    $q->execute([(int)$cape, $me['uuid']]);
    $cape = $q->fetchColumn() ?: null;
}
foreach (['hat', 'wings', 'aura'] as $k) {
    if (!valid_look($in[$k] ?? '')) fail("bad $k");
}
db()->prepare('INSERT INTO looks (uuid, cape, hat, wings, aura, updated) VALUES (?, ?, ?, ?, ?, ?)
    ON DUPLICATE KEY UPDATE cape = VALUES(cape), hat = VALUES(hat), wings = VALUES(wings), aura = VALUES(aura), updated = VALUES(updated)')
    ->execute([$me['uuid'], $cape, $in['hat'] ?? '', $in['wings'] ?? '', $in['aura'] ?? '', time()]);
reply(['ok' => true, 'cape' => $cape === null ? null : (int)$cape]);
