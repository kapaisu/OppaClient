<?php

ini_set('display_errors', '0');

const ONLINE_FOR = 150;

function config(): array {
    static $cfg;
    if ($cfg) return $cfg;
    $places = array_unique([dirname(__DIR__, 2), dirname($_SERVER['DOCUMENT_ROOT']), getenv('HOME') ?: '']);
    foreach ($places as $dir) {
        if ($dir !== '' && is_file("$dir/oppa-config.php")) return $cfg = require "$dir/oppa-config.php";
    }
    throw new RuntimeException('oppa-config.php is missing. Put it in ' . dirname(__DIR__, 2) . ', next to public_html.');
}

function db_problem(Throwable $e): string {
    if ($e instanceof RuntimeException && !$e instanceof PDOException) return $e->getMessage();
    error_log('oppa: ' . $e->getMessage());
    $code = $e instanceof PDOException ? (int)($e->errorInfo[1] ?? 0) : 0;
    return match (true) {
        str_contains($e->getMessage(), 'could not find driver') => 'PHP can\'t talk to MySQL. Turn on pdo_mysql in cPanel, Select PHP Version, Extensions.',
        $code === 1045 => 'The database user or password in oppa-config.php is wrong.',
        $code === 1049 => 'The database name in oppa-config.php is wrong.',
        $code === 1044 => 'Either the database name in oppa-config.php is wrong, or the user isn\'t added to it.',
        default => 'Can\'t connect to the database. Check oppa-config.php.',
    };
}

function db(): PDO {
    static $pdo;
    if ($pdo) return $pdo;
    $c = config()['db'];
    $pdo = new PDO("mysql:host={$c['host']};dbname={$c['name']};charset=utf8mb4", $c['user'], $c['pass'], [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
    return $pdo;
}

function migrate(): void {
    $db = db();
    $db->exec('CREATE TABLE IF NOT EXISTS players (
        uuid CHAR(32) NOT NULL PRIMARY KEY,
        name VARCHAR(16) NOT NULL,
        token CHAR(64) NULL,
        os VARCHAR(16) NOT NULL DEFAULT \'\',
        version VARCHAR(20) NOT NULL DEFAULT \'\',
        playing TINYINT(1) NOT NULL DEFAULT 0,
        first_seen INT UNSIGNED NOT NULL,
        last_seen INT UNSIGNED NOT NULL,
        KEY token (token),
        KEY last_seen (last_seen)
    ) DEFAULT CHARSET=utf8mb4');
    $cols = $db->query("SHOW TABLES LIKE 'nonces'")->fetch() ? $db->query('SHOW COLUMNS FROM nonces')->fetchAll(PDO::FETCH_COLUMN) : [];
    if ($cols && !in_array('ip', $cols, true)) $db->exec('DROP TABLE nonces');
    $db->exec('CREATE TABLE IF NOT EXISTS nonces (
        nonce CHAR(32) NOT NULL PRIMARY KEY,
        name VARCHAR(16) NOT NULL,
        ip VARCHAR(45) NOT NULL,
        created INT UNSIGNED NOT NULL,
        KEY created (created),
        KEY ip (ip)
    ) DEFAULT CHARSET=utf8mb4');
    $db->exec('CREATE TABLE IF NOT EXISTS releases (
        id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
        version VARCHAR(20) NOT NULL UNIQUE,
        notes TEXT NOT NULL,
        created INT UNSIGNED NOT NULL
    ) DEFAULT CHARSET=utf8mb4');
    $db->exec('CREATE TABLE IF NOT EXISTS settings (
        k VARCHAR(32) NOT NULL PRIMARY KEY,
        v TEXT NOT NULL
    ) DEFAULT CHARSET=utf8mb4');
    $have = $db->query('SHOW COLUMNS FROM players')->fetchAll(PDO::FETCH_COLUMN);
    foreach (['status' => "VARCHAR(16) NOT NULL DEFAULT ''", 'server' => "VARCHAR(255) NOT NULL DEFAULT ''", 'game' => "VARCHAR(20) NOT NULL DEFAULT ''"] as $col => $type) {
        if (!in_array($col, $have, true)) $db->exec("ALTER TABLE players ADD COLUMN $col $type");
    }
    $db->exec('CREATE TABLE IF NOT EXISTS sessions (
        token CHAR(64) NOT NULL PRIMARY KEY,
        uuid CHAR(32) NOT NULL,
        created INT UNSIGNED NOT NULL,
        used INT UNSIGNED NOT NULL,
        KEY uuid (uuid)
    ) DEFAULT CHARSET=utf8mb4');
    $db->exec('CREATE TABLE IF NOT EXISTS friends (
        a CHAR(32) NOT NULL,
        b CHAR(32) NOT NULL,
        created INT UNSIGNED NOT NULL,
        PRIMARY KEY (a, b)
    ) DEFAULT CHARSET=utf8mb4');
    $db->exec('CREATE TABLE IF NOT EXISTS requests (
        sender CHAR(32) NOT NULL,
        target CHAR(32) NOT NULL,
        created INT UNSIGNED NOT NULL,
        PRIMARY KEY (sender, target),
        KEY target (target)
    ) DEFAULT CHARSET=utf8mb4');
    $db->exec('CREATE TABLE IF NOT EXISTS invites (
        id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
        sender CHAR(32) NOT NULL,
        target CHAR(32) NOT NULL,
        server VARCHAR(255) NOT NULL,
        game VARCHAR(20) NOT NULL,
        created INT UNSIGNED NOT NULL,
        KEY target (target, created)
    ) DEFAULT CHARSET=utf8mb4');
    $db->exec('CREATE TABLE IF NOT EXISTS shots (
        id CHAR(10) NOT NULL PRIMARY KEY,
        uuid CHAR(32) NOT NULL,
        type VARCHAR(4) NOT NULL,
        width SMALLINT UNSIGNED NOT NULL,
        height SMALLINT UNSIGNED NOT NULL,
        bytes INT UNSIGNED NOT NULL,
        created INT UNSIGNED NOT NULL,
        KEY uuid (uuid, created)
    ) DEFAULT CHARSET=utf8mb4');
    $db->exec('CREATE TABLE IF NOT EXISTS capes (
        id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
        uuid CHAR(32) NOT NULL,
        hash CHAR(40) NOT NULL,
        state VARCHAR(8) NOT NULL DEFAULT \'pending\',
        created INT UNSIGNED NOT NULL,
        UNIQUE KEY mine (uuid, hash),
        KEY state (state)
    ) DEFAULT CHARSET=utf8mb4');
    $db->exec('CREATE TABLE IF NOT EXISTS looks (
        uuid CHAR(32) NOT NULL PRIMARY KEY,
        cape INT UNSIGNED NULL,
        hat VARCHAR(40) NOT NULL DEFAULT \'\',
        wings VARCHAR(40) NOT NULL DEFAULT \'\',
        aura VARCHAR(40) NOT NULL DEFAULT \'\',
        updated INT UNSIGNED NOT NULL
    ) DEFAULT CHARSET=utf8mb4');
    $db->exec('CREATE TABLE IF NOT EXISTS hits (
        k VARCHAR(80) NOT NULL,
        at INT UNSIGNED NOT NULL,
        KEY k_at (k, at)
    ) DEFAULT CHARSET=utf8mb4');
    $db->exec('CREATE TABLE IF NOT EXISTS logins (
        ip VARCHAR(45) NOT NULL,
        at INT UNSIGNED NOT NULL,
        KEY ip_at (ip, at)
    ) DEFAULT CHARSET=utf8mb4');
}

function storage(string $sub): string {
    $dir = (config()['storage'] ?? dirname(__DIR__, 2) . '/oppa-files') . "/$sub";
    if (!is_dir($dir) && !mkdir($dir, 0750, true) && !is_dir($dir)) throw new RuntimeException("Can't create $dir");
    return $dir;
}

function me(): array {
    $token = $_SERVER['HTTP_X_OPPA_TOKEN'] ?? body()['token'] ?? null;
    if (!is_string($token) || !preg_match('/^[0-9a-f]{64}$/', $token)) fail('sign in with the Oppa launcher', 401);
    $hash = hash('sha256', $token);
    $db = db();
    $q = $db->prepare('SELECT p.uuid, p.name FROM sessions s JOIN players p ON p.uuid = s.uuid WHERE s.token = ?');
    $q->execute([$hash]);
    $row = $q->fetch();
    if (!$row) {
        $q = $db->prepare('SELECT uuid, name FROM players WHERE token = ?');
        $q->execute([$hash]);
        $row = $q->fetch();
    } else {
        $db->prepare('UPDATE sessions SET used = ? WHERE token = ?')->execute([time(), $hash]);
    }
    if (!$row) fail('session expired, restart the launcher', 401);
    return $row;
}

function limit(string $key, int $max, int $window): void {
    $db = db();
    $now = time();
    if (random_int(0, 50) === 0) $db->prepare('DELETE FROM hits WHERE at < ?')->execute([$now - 86400]);
    $q = $db->prepare('SELECT COUNT(*) FROM hits WHERE k = ? AND at > ?');
    $q->execute([$key, $now - $window]);
    if ($q->fetchColumn() >= $max) fail('slow down', 429);
    $db->prepare('INSERT INTO hits (k, at) VALUES (?, ?)')->execute([$key, $now]);
}

function friends(string $a, string $b): bool {
    $q = db()->prepare('SELECT 1 FROM friends WHERE a = ? AND b = ?');
    $q->execute([$a, $b]);
    return (bool)$q->fetchColumn();
}

function valid_server(mixed $s): bool {
    return is_string($s) && strlen($s) <= 255 && preg_match('/^[A-Za-z0-9.\-:\[\]_]+$/', $s);
}

function valid_look(mixed $s): bool {
    return is_string($s) && ($s === '' || preg_match('/^[a-z0-9_]{1,16}(:[0-9a-f]{6}){0,2}$/', $s));
}

function site_url(): string {
    $host = preg_match('/^[a-z0-9.\-]+(:\d+)?$/i', $_SERVER['HTTP_HOST'] ?? '') ? $_SERVER['HTTP_HOST'] : 'oppaclient.com';
    $local = str_starts_with($host, '127.0.0.1') || str_starts_with($host, 'localhost');
    return ($local ? 'http' : 'https') . "://$host";
}

function cape_url(string $hash): string {
    return site_url() . "/api/cape.php?h=$hash";
}

function raw_body(int $max): string {
    $data = file_get_contents('php://input', false, null, 0, $max + 1);
    if ($data === false || $data === '') fail('empty upload');
    if (strlen($data) > $max) fail('file too big', 413);
    return $data;
}

function setting(string $k, ?string $v = null): string {
    if ($v === null) {
        $q = db()->prepare('SELECT v FROM settings WHERE k = ?');
        $q->execute([$k]);
        return (string)$q->fetchColumn();
    }
    db()->prepare('REPLACE INTO settings (k, v) VALUES (?, ?)')->execute([$k, $v]);
    return $v;
}

function body(): array {
    $data = json_decode(file_get_contents('php://input'), true);
    return is_array($data) ? $data : [];
}

function reply(array $data, int $status = 200): never {
    http_response_code($status);
    header('Content-Type: application/json');
    header('Cache-Control: no-store');
    echo json_encode($data, JSON_UNESCAPED_SLASHES);
    exit;
}

function fail(string $msg, int $status = 400): never {
    reply(['error' => $msg], $status);
}

function post_only(): void {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') fail('use POST', 405);
}

function valid_name(mixed $name): bool {
    return is_string($name) && preg_match('/^[A-Za-z0-9_]{1,16}$/', $name);
}

function valid_uuid(mixed $id): bool {
    return is_string($id) && preg_match('/^[0-9a-f]{32}$/', $id);
}

function short(mixed $s, int $max): string {
    return is_string($s) ? mb_substr(trim($s), 0, $max) : '';
}
