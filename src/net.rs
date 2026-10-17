use std::future::Future;
use std::path::{Path, PathBuf};
use std::sync::OnceLock;
use std::sync::atomic::{AtomicU64, Ordering};

use anyhow::{Context, bail};
use futures_util::{StreamExt, TryStreamExt, stream};
use sha1::{Digest, Sha1};
use tokio::io::AsyncWriteExt;

pub fn rt() -> &'static tokio::runtime::Runtime {
    static RT: OnceLock<tokio::runtime::Runtime> = OnceLock::new();
    RT.get_or_init(|| {
        tokio::runtime::Builder::new_multi_thread()
            .worker_threads(4)
            .enable_all()
            .build()
            .expect("tokio runtime")
    })
}

pub fn http() -> &'static reqwest::Client {
    static CLIENT: OnceLock<reqwest::Client> = OnceLock::new();
    CLIENT.get_or_init(|| {
        reqwest::Client::builder()
            .user_agent(concat!("oppa-launcher/", env!("CARGO_PKG_VERSION"), " (arch linux)"))
            .connect_timeout(std::time::Duration::from_secs(10))
            .build()
            .expect("http client")
    })
}

pub async fn bg<T, F>(fut: F) -> T
where
    F: Future<Output = T> + Send + 'static,
    T: Send + 'static,
{
    rt().spawn(fut).await.expect("background task panicked")
}

pub enum Activity {
    Begin { id: u64, label: String },
    Progress { id: u64, done: u64, total: u64 },
    Relabel { id: u64, label: String },
    End { id: u64 },
}

fn activity_channel() -> &'static (async_channel::Sender<Activity>, async_channel::Receiver<Activity>) {
    static CH: OnceLock<(async_channel::Sender<Activity>, async_channel::Receiver<Activity>)> = OnceLock::new();
    CH.get_or_init(async_channel::unbounded)
}

pub fn activity_feed() -> async_channel::Receiver<Activity> {
    activity_channel().1.clone()
}

pub struct Job {
    id: u64,
}

impl Job {
    pub fn begin(label: impl Into<String>) -> Self {
        static NEXT: AtomicU64 = AtomicU64::new(1);
        let id = NEXT.fetch_add(1, Ordering::Relaxed);
        let _ = activity_channel().0.try_send(Activity::Begin { id, label: label.into() });
        Job { id }
    }

    pub fn progress(&self, done: u64, total: u64) {
        let _ = activity_channel().0.try_send(Activity::Progress { id: self.id, done, total });
    }

    pub fn relabel(&self, label: impl Into<String>) {
        let _ = activity_channel().0.try_send(Activity::Relabel { id: self.id, label: label.into() });
    }
}

impl Drop for Job {
    fn drop(&mut self) {
        let _ = activity_channel().0.try_send(Activity::End { id: self.id });
    }
}

pub async fn get_json<T: serde::de::DeserializeOwned>(url: &str) -> anyhow::Result<T> {
    let resp = http().get(url).send().await?;
    if !resp.status().is_success() {
        bail!("{} returned {}", short_host(url), resp.status());
    }
    Ok(resp.json().await?)
}

pub async fn get_bytes(url: &str) -> anyhow::Result<bytes::Bytes> {
    let resp = http().get(url).send().await?;
    if !resp.status().is_success() {
        bail!("{} returned {}", short_host(url), resp.status());
    }
    Ok(resp.bytes().await?)
}

fn short_host(url: &str) -> &str {
    url.split("//").nth(1).and_then(|s| s.split('/').next()).unwrap_or(url)
}

pub fn sha1_file(path: &Path) -> std::io::Result<String> {
    use std::io::Read;
    let mut f = std::fs::File::open(path)?;
    let mut h = Sha1::new();
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf)?;
        if n == 0 {
            break;
        }
        h.update(&buf[..n]);
    }
    Ok(hex::encode(h.finalize()))
}

#[derive(Clone, Debug)]
pub struct Fetch {
    pub url: String,
    pub dest: PathBuf,
    pub sha1: Option<String>,
    pub size: u64,
}

fn already_have(f: &Fetch) -> bool {
    let Ok(meta) = std::fs::metadata(&f.dest) else { return false };
    if f.size > 0 && meta.len() != f.size {
        return false;
    }
    match &f.sha1 {
        Some(_) if f.size > 0 && f.dest.components().any(|c| c.as_os_str() == "objects") => true,
        Some(want) => sha1_file(&f.dest).map(|got| &got == want).unwrap_or(false),
        None => meta.len() > 0,
    }
}

async fn fetch_one(f: &Fetch) -> anyhow::Result<()> {
    if let Some(dir) = f.dest.parent() {
        tokio::fs::create_dir_all(dir).await?;
    }
    let mut part = f.dest.clone().into_os_string();
    part.push(".part");
    let part = PathBuf::from(part);
    let mut last_err = None;
    for attempt in 0..3 {
        if attempt > 0 {
            tokio::time::sleep(std::time::Duration::from_millis(400 * attempt)).await;
        }
        match stream_to(&f.url, &part).await {
            Ok(hash) => {
                if let Some(want) = &f.sha1 {
                    if &hash != want {
                        last_err = Some(anyhow::anyhow!("checksum mismatch for {}", f.url));
                        continue;
                    }
                }
                tokio::fs::rename(&part, &f.dest).await?;
                return Ok(());
            }
            Err(e) => last_err = Some(e),
        }
    }
    let _ = tokio::fs::remove_file(&part).await;
    Err(last_err.unwrap())
}

async fn stream_to(url: &str, path: &Path) -> anyhow::Result<String> {
    let resp = http().get(url).send().await?;
    if !resp.status().is_success() {
        bail!("{} returned {}", short_host(url), resp.status());
    }
    let mut file = tokio::fs::File::create(path).await?;
    let mut hasher = Sha1::new();
    let mut body = resp.bytes_stream();
    while let Some(chunk) = body.try_next().await? {
        hasher.update(&chunk);
        file.write_all(&chunk).await?;
    }
    file.flush().await?;
    Ok(hex::encode(hasher.finalize()))
}

pub async fn fetch(f: Fetch) -> anyhow::Result<()> {
    if already_have(&f) {
        return Ok(());
    }
    fetch_one(&f).await
}

pub async fn fetch_all(files: Vec<Fetch>, job: &Job) -> anyhow::Result<()> {
    let missing: Vec<Fetch> = tokio::task::spawn_blocking(move || {
        files.into_iter().filter(|f| !already_have(f)).collect()
    })
    .await?;

    let total = missing.len() as u64;
    if total == 0 {
        return Ok(());
    }
    job.progress(0, total);
    let done = AtomicU64::new(0);
    stream::iter(missing)
        .map(|f| {
            let done = &done;
            async move {
                fetch_one(&f).await.with_context(|| format!("downloading {}", f.url))?;
                let n = done.fetch_add(1, Ordering::Relaxed) + 1;
                job.progress(n, total);
                anyhow::Ok(())
            }
        })
        .buffer_unordered(24)
        .try_collect::<Vec<()>>()
        .await?;
    Ok(())
}
