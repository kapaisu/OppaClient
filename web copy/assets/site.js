const REPO = "kapaisu/OppaClient";

function remember(key, value) {
  try {
    sessionStorage.setItem(key, JSON.stringify({ at: Date.now(), value }));
  } catch {}
}

function recall(key, maxAge) {
  try {
    const hit = JSON.parse(sessionStorage.getItem(key));
    if (hit && Date.now() - hit.at < maxAge) return hit.value;
  } catch {}
  return null;
}

async function latestRelease() {
  const cached = recall("oppa-release", 10 * 60 * 1000);
  if (cached) return cached;
  const res = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
    headers: { Accept: "application/vnd.github+json" },
  });
  if (!res.ok) throw new Error(res.status);
  const r = await res.json();
  const release = {
    version: r.tag_name.replace(/^v/, ""),
    date: r.published_at,
    assets: Object.fromEntries(r.assets.map((a) => [a.name, a.size])),
  };
  remember("oppa-release", release);
  return release;
}

function set(id, text) {
  const el = document.getElementById(id);
  if (el) el.textContent = text;
}

function megabytes(bytes) {
  return `${Math.round(bytes / 1_000_000)} MB`;
}

function niceDate(iso) {
  return new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" });
}

latestRelease()
  .then((r) => {
    set("ver", `Version ${r.version}, released ${niceDate(r.date)}`);
    if (r.assets["Oppa-Setup.exe"]) set("exe-size", megabytes(r.assets["Oppa-Setup.exe"]));
  })
  .catch(() => {});

const list = document.querySelector(".os-list");
if (list) {
  const ua = (navigator.userAgentData && navigator.userAgentData.platform) || navigator.userAgent;
  const mine = /win/i.test(ua) ? "win" : /linux|x11/i.test(ua) ? "arch" : null;
  if (mine) list.prepend(document.getElementById(mine));
}

for (const button of document.querySelectorAll(".cmd button")) {
  button.addEventListener("click", async () => {
    const text = button.parentElement.querySelector("code").textContent;
    try {
      await navigator.clipboard.writeText(text);
      button.textContent = "Copied";
      button.classList.add("done");
      setTimeout(() => {
        button.textContent = "Copy";
        button.classList.remove("done");
      }, 1400);
    } catch {
      button.textContent = "Select and copy";
    }
  });
}
