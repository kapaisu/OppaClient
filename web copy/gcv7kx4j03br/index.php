<?php
require dirname(__DIR__) . '/api/lib.php';

ini_set('session.use_strict_mode', '1');
session_name('oppa_admin');
$https = ($_SERVER['HTTPS'] ?? '') === 'on' || ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https';
session_set_cookie_params(['lifetime' => 0, 'path' => dirname($_SERVER['SCRIPT_NAME']) . '/', 'secure' => $https, 'httponly' => true, 'samesite' => 'Lax']);
session_start();
header('X-Frame-Options: DENY');
header('Referrer-Policy: no-referrer');
header('X-Content-Type-Options: nosniff');
header('Cache-Control: no-store');

$ip = $_SERVER['REMOTE_ADDR'] ?? '';
$authed = !empty($_SESSION['admin']);
$_SESSION['csrf'] ??= bin2hex(random_bytes(16));
$error = '';

function back(string $flash = ''): never {
    if ($flash) $_SESSION['flash'] = $flash;
    header('Location: ./');
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $do = $_POST['do'] ?? '';
    if (!hash_equals($_SESSION['csrf'], (string)($_POST['csrf'] ?? ''))) {
        $error = 'Your browser blocked the login cookie. Reload the page and try again.';
        $do = '';
    }

    if ($do === 'login') {
        try {
            migrate();
        } catch (Throwable $e) {
            $error = db_problem($e);
            $do = '';
        }
    }

    if ($do === 'login') {
        $db = db();
        $db->prepare('DELETE FROM logins WHERE at < ?')->execute([time() - 900]);
        $q = $db->prepare('SELECT COUNT(*) FROM logins WHERE ip = ?');
        $q->execute([$ip]);
        $admin = config()['admin'];
        if ($q->fetchColumn() >= 8) {
            $error = 'Too many tries. Wait 15 minutes.';
        } elseif (hash_equals($admin['user'], (string)($_POST['user'] ?? '')) && password_verify((string)($_POST['pass'] ?? ''), $admin['hash'])) {
            session_regenerate_id(true);
            $_SESSION['admin'] = true;
            back();
        } else {
            $db->prepare('INSERT INTO logins (ip, at) VALUES (?, ?)')->execute([$ip, time()]);
            $error = 'Wrong username or password.';
        }
    } elseif ($authed) {
        if ($do === 'logout') {
            $_SESSION = [];
            session_destroy();
            back();
        }
        if ($do === 'release') {
            $version = trim($_POST['version'] ?? '');
            if (!preg_match('/^\d+\.\d+\.\d+$/', $version)) back('Version must look like 1.2.3.');
            try {
                db()->prepare('INSERT INTO releases (version, notes, created) VALUES (?, ?, ?)')
                    ->execute([$version, trim($_POST['notes'] ?? ''), time()]);
            } catch (PDOException) {
                back("$version is already published.");
            }
            back("Published $version.");
        }
        if ($do === 'unrelease') {
            db()->prepare('DELETE FROM releases WHERE id = ?')->execute([(int)($_POST['id'] ?? 0)]);
            back('Release removed.');
        }
        if ($do === 'cape') {
            $state = ($_POST['verdict'] ?? '') === 'approve' ? 'approved' : 'rejected';
            $q = db()->prepare('SELECT hash FROM capes WHERE id = ?');
            $q->execute([(int)($_POST['id'] ?? 0)]);
            $hash = $q->fetchColumn();
            if ($hash) db()->prepare('UPDATE capes SET state = ? WHERE hash = ?')->execute([$state, $hash]);
            back($state === 'approved' ? 'Cape approved.' : 'Cape rejected.');
        }
        if ($do === 'unshot') {
            $q = db()->prepare('SELECT id, type FROM shots WHERE id = ?');
            $q->execute([(string)($_POST['id'] ?? '')]);
            if ($s = $q->fetch()) {
                @unlink(storage('shots') . "/{$s['id']}.{$s['type']}");
                db()->prepare('DELETE FROM shots WHERE id = ?')->execute([$s['id']]);
            }
            back('Screenshot removed.');
        }
        if ($do === 'notice') {
            setting('notice', mb_substr(trim($_POST['notice'] ?? ''), 0, 300));
            back('Notice saved.');
        }
    }
}

$flash = $_SESSION['flash'] ?? '';
unset($_SESSION['flash']);
$csrf = $_SESSION['csrf'];

function e(?string $s): string {
    return htmlspecialchars((string)$s, ENT_QUOTES);
}

function ago(int $t): string {
    $d = time() - $t;
    if ($d < 90) return 'just now';
    if ($d < 3600) return intdiv($d, 60) . ' min ago';
    if ($d < 86400) return intdiv($d, 3600) . ' h ago';
    return date('j M Y', $t);
}

if ($authed) {
    migrate();
    $db = db();
    $cut = time() - ONLINE_FOR;
    $playing = (int)$db->query("SELECT COUNT(*) FROM players WHERE playing = 1 AND last_seen > $cut")->fetchColumn();
    $today = (int)$db->query('SELECT COUNT(*) FROM players WHERE last_seen > ' . (time() - 86400))->fetchColumn();
    $total = (int)$db->query('SELECT COUNT(*) FROM players')->fetchColumn();
    $releases = $db->query('SELECT id, version, notes, created FROM releases ORDER BY created DESC, id DESC LIMIT 20')->fetchAll();
    $find = trim($_GET['q'] ?? '');
    if ($find !== '') {
        $q = $db->prepare('SELECT * FROM players WHERE name LIKE ? ORDER BY last_seen DESC LIMIT 100');
        $q->execute(['%' . addcslashes($find, '%_\\') . '%']);
    } else {
        $q = $db->query('SELECT * FROM players ORDER BY last_seen DESC LIMIT 50');
    }
    $players = $q->fetchAll();
    $notice = setting('notice');
    $pending = $db->query("SELECT MIN(c.id) id, c.hash, MIN(c.created) created, MIN(p.name) name FROM capes c JOIN players p ON p.uuid = c.uuid WHERE c.state = 'pending' GROUP BY c.hash ORDER BY created LIMIT 30")->fetchAll();
    $shots = $db->query('SELECT s.id, s.created, p.name FROM shots s JOIN players p ON p.uuid = s.uuid ORDER BY s.created DESC LIMIT 24')->fetchAll();
}

function cape_data(string $hash): string {
    $file = storage('capes') . "/$hash.png";
    return is_file($file) ? 'data:image/png;base64,' . base64_encode(file_get_contents($file)) : '';
}
?>
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>Oppa admin</title>
<link rel="icon" href="../assets/img/icon-64.png">
<link rel="stylesheet" href="admin.css">
<script src="admin.js" defer></script>
</head>
<body>
<?php if (!$authed): ?>
<form class="login" method="post">
  <img src="../assets/img/logo.webp" alt="Oppa" width="140" height="42">
  <input type="hidden" name="csrf" value="<?= e($csrf) ?>">
  <input type="hidden" name="do" value="login">
  <input name="user" placeholder="Username" autocomplete="username" required autofocus>
  <input name="pass" type="password" placeholder="Password" autocomplete="current-password" required>
  <?php if ($error): ?><p class="err"><?= e($error) ?></p><?php endif ?>
  <button class="solid">Sign in</button>
</form>
<?php else: ?>
<header>
  <img src="../assets/img/logo.webp" alt="Oppa" width="100" height="30">
  <form method="post">
    <input type="hidden" name="csrf" value="<?= e($csrf) ?>">
    <button name="do" value="logout">Sign out</button>
  </form>
</header>

<main>
  <?php if ($flash): ?><p class="flash"><?= e($flash) ?></p><?php endif ?>

  <section class="nums">
    <div><b><?= $playing ?></b><span>Playing now</span></div>
    <div><b><?= $today ?></b><span>Last 24 hours</span></div>
    <div><b><?= $total ?></b><span>All players</span></div>
  </section>

  <section>
    <h2>Release</h2>
    <form method="post" class="stack">
      <input type="hidden" name="csrf" value="<?= e($csrf) ?>">
      <input type="hidden" name="do" value="release">
      <input name="version" placeholder="Version, like 0.2.0" required pattern="\d+\.\d+\.\d+">
      <textarea name="notes" rows="4" placeholder="What changed"></textarea>
      <button class="solid">Publish</button>
    </form>
    <?php foreach ($releases as $r): ?>
    <div class="row">
      <div><b><?= e($r['version']) ?></b> <span class="dim"><?= date('j M Y', $r['created']) ?></span>
        <?php if ($r['notes'] !== ''): ?><p class="dim"><?= nl2br(e($r['notes'])) ?></p><?php endif ?></div>
      <form method="post" data-confirm="Remove <?= e($r['version']) ?>?">
        <input type="hidden" name="csrf" value="<?= e($csrf) ?>">
        <input type="hidden" name="id" value="<?= (int)$r['id'] ?>">
        <button name="do" value="unrelease">Remove</button>
      </form>
    </div>
    <?php endforeach ?>
  </section>

  <section>
    <h2>Capes to review</h2>
    <?php if (!$pending): ?><p class="dim">Nothing waiting.</p><?php endif ?>
    <div class="capes">
    <?php foreach ($pending as $c): ?>
      <form method="post" class="cape">
        <img src="<?= cape_data($c['hash']) ?>" alt="">
        <p><b><?= e($c['name']) ?></b> <span class="dim"><?= ago((int)$c['created']) ?></span></p>
        <input type="hidden" name="csrf" value="<?= e($csrf) ?>">
        <input type="hidden" name="do" value="cape">
        <input type="hidden" name="id" value="<?= (int)$c['id'] ?>">
        <div><button class="solid" name="verdict" value="approve">Approve</button><button name="verdict" value="reject">Reject</button></div>
      </form>
    <?php endforeach ?>
    </div>
  </section>

  <section>
    <h2>Screenshots</h2>
    <?php if (!$shots): ?><p class="dim">None yet.</p><?php endif ?>
    <?php foreach ($shots as $s): ?>
    <div class="row">
      <div><a href="/s/<?= e($s['id']) ?>" target="_blank" rel="noopener"><?= e($s['id']) ?></a> <span class="dim"><?= e($s['name']) ?> | <?= ago((int)$s['created']) ?></span></div>
      <form method="post" data-confirm="Remove this screenshot?">
        <input type="hidden" name="csrf" value="<?= e($csrf) ?>">
        <input type="hidden" name="id" value="<?= e($s['id']) ?>">
        <button name="do" value="unshot">Remove</button>
      </form>
    </div>
    <?php endforeach ?>
  </section>

  <section>
    <h2>Notice</h2>
    <form method="post" class="stack">
      <input type="hidden" name="csrf" value="<?= e($csrf) ?>">
      <input type="hidden" name="do" value="notice">
      <textarea name="notice" rows="2" maxlength="300" placeholder="Shown at the top of the launcher. Leave empty for none."><?= e($notice) ?></textarea>
      <button class="solid">Save</button>
    </form>
  </section>

  <section>
    <h2>Players</h2>
    <form method="get" class="find">
      <input name="q" value="<?= e($find) ?>" placeholder="Search by name">
    </form>
    <table>
      <tr><th>Name</th><th>System</th><th>Launcher</th><th>Status</th></tr>
      <?php foreach ($players as $p): ?>
      <tr>
        <td><?= e($p['name']) ?></td>
        <td class="dim"><?= e($p['os']) ?></td>
        <td class="dim"><?= e($p['version']) ?></td>
        <td class="dim"><?= $p['last_seen'] > $cut ? e(match ($p['status']) { 'server' => 'On ' . $p['server'], 'launcher' => 'In the launcher', 'singleplayer' => 'Singleplayer', default => 'Playing' }) : ago((int)$p['last_seen']) ?></td>
      </tr>
      <?php endforeach ?>
    </table>
  </section>
</main>
<?php endif ?>
</body>
</html>
