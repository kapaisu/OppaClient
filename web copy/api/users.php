<?php
require __DIR__ . '/lib.php';

post_only();
$ids = array_values(array_unique(array_filter((array)(body()['uuids'] ?? []), 'valid_uuid')));
if (!$ids) reply(['online' => [], 'looks' => new stdClass()]);
$ids = array_slice($ids, 0, 100);

$marks = implode(',', array_fill(0, count($ids), '?'));
$q = db()->prepare("SELECT uuid FROM players WHERE playing = 1 AND last_seen > ? AND uuid IN ($marks)");
$q->execute([time() - ONLINE_FOR, ...$ids]);
$online = $q->fetchAll(PDO::FETCH_COLUMN);

$looks = new stdClass();
if ($online) {
    $marks = implode(',', array_fill(0, count($online), '?'));
    $q = db()->prepare("SELECT l.uuid, l.hat, l.wings, l.aura, c.hash, c.state FROM looks l LEFT JOIN capes c ON c.id = l.cape WHERE l.uuid IN ($marks)");
    $q->execute($online);
    foreach ($q->fetchAll() as $l) {
        $looks->{$l['uuid']} = [
            'cape' => $l['state'] === 'approved' ? cape_url($l['hash']) : null,
            'hat' => $l['hat'],
            'wings' => $l['wings'],
            'aura' => $l['aura'],
        ];
    }
}

reply(['online' => $online, 'looks' => $looks]);
