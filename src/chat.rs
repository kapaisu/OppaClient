use std::collections::HashMap;
use std::sync::{Arc, OnceLock};
use std::time::Duration;

use rustls::client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier};
use rustls::crypto::{CryptoProvider, verify_tls12_signature, verify_tls13_signature};
use rustls::pki_types::{CertificateDer, ServerName, UnixTime};
use rustls::{DigitallySignedStruct, SignatureScheme};
use serde::Deserialize;
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::net::TcpStream;
use tokio::sync::mpsc;

use crate::net;

const PIN: &str = "c834003bd0436b8eeb64d8aadc552ecef1c9c7859022de9bdbccbd6fd81166d0";

#[derive(Clone, Debug, Deserialize)]
pub struct Message {
    pub id: i64,
    pub from: String,
    #[serde(default)]
    pub to: String,
    pub text: String,
    #[serde(default)]
    pub name: String,
}

pub enum Event {
    Online(bool),
    Message(Message),
    History(String, Vec<Message>),
    Unread(HashMap<String, i64>),
    Refused(String),
}

struct Link {
    out: mpsc::UnboundedSender<Value>,
    events: async_channel::Receiver<Event>,
    me: std::sync::Mutex<String>,
}

fn link() -> &'static Link {
    static L: OnceLock<Link> = OnceLock::new();
    L.get_or_init(|| {
        let (out, rx) = mpsc::unbounded_channel();
        let (tx, events) = async_channel::unbounded();
        net::rt().spawn(run(rx, tx));
        Link { out, events, me: std::sync::Mutex::new(String::new()) }
    })
}

pub fn events() -> async_channel::Receiver<Event> {
    link().events.clone()
}

pub fn me() -> String {
    link().me.lock().unwrap().clone()
}

pub fn plain(uuid: &str) -> String {
    uuid.replace('-', "").to_lowercase()
}

pub fn send(to: &str, text: &str) {
    let text: String = text.trim().chars().take(500).collect();
    if !text.is_empty() {
        let _ = link().out.send(json!({"op": "send", "to": plain(to), "text": text}));
    }
}

pub fn open(with: &str) {
    let _ = link().out.send(json!({"op": "history", "with": plain(with)}));
    let _ = link().out.send(json!({"op": "read", "with": plain(with)}));
}

pub fn read(with: &str) {
    let _ = link().out.send(json!({"op": "read", "with": plain(with)}));
}

#[derive(Debug)]
struct Pinned {
    pin: String,
    provider: Arc<CryptoProvider>,
}

impl ServerCertVerifier for Pinned {
    fn verify_server_cert(
        &self,
        end: &CertificateDer<'_>,
        _: &[CertificateDer<'_>],
        _: &ServerName<'_>,
        _: &[u8],
        _: UnixTime,
    ) -> Result<ServerCertVerified, rustls::Error> {
        if self.pin.is_empty() || hex::encode(Sha256::digest(end.as_ref())) != self.pin {
            return Err(rustls::Error::General("Oppa relay certificate doesn't match".into()));
        }
        Ok(ServerCertVerified::assertion())
    }

    fn verify_tls12_signature(&self, msg: &[u8], cert: &CertificateDer<'_>, dss: &DigitallySignedStruct) -> Result<HandshakeSignatureValid, rustls::Error> {
        verify_tls12_signature(msg, cert, dss, &self.provider.signature_verification_algorithms)
    }

    fn verify_tls13_signature(&self, msg: &[u8], cert: &CertificateDer<'_>, dss: &DigitallySignedStruct) -> Result<HandshakeSignatureValid, rustls::Error> {
        verify_tls13_signature(msg, cert, dss, &self.provider.signature_verification_algorithms)
    }

    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        self.provider.signature_verification_algorithms.supported_schemes()
    }
}

async fn target() -> anyhow::Result<(String, u16, String)> {
    if cfg!(debug_assertions)
        && let Some(port) = std::env::var("OPPA_RELAY").ok().and_then(|r| r.strip_prefix("127.0.0.1:").and_then(|p| p.parse().ok()))
    {
        return Ok(("127.0.0.1".into(), port, std::env::var("OPPA_RELAY_PIN").unwrap_or_default()));
    }
    static WHERE: tokio::sync::OnceCell<(String, u16)> = tokio::sync::OnceCell::const_new();
    let (host, port) = WHERE.get_or_try_init(crate::site::relay).await?.clone();
    Ok((host, port, PIN.into()))
}

async fn connect(token: &str) -> anyhow::Result<(tokio_rustls::client::TlsStream<TcpStream>, String)> {
    let (host, port, pin) = target().await?;
    let provider = Arc::new(rustls::crypto::ring::default_provider());
    let config = rustls::ClientConfig::builder_with_provider(provider.clone())
        .with_safe_default_protocol_versions()?
        .dangerous()
        .with_custom_certificate_verifier(Arc::new(Pinned { pin, provider }))
        .with_no_client_auth();
    let tcp = tokio::time::timeout(Duration::from_secs(8), TcpStream::connect((host.as_str(), port))).await??;
    tcp.set_nodelay(true)?;
    let name = ServerName::try_from(host.clone())?;
    let mut tls = tokio_rustls::TlsConnector::from(Arc::new(config)).connect(name, tcp).await?;
    let mut hello = json!({"op": "chat", "token": token}).to_string();
    hello.push('\n');
    tls.write_all(hello.as_bytes()).await?;
    tls.flush().await?;
    let mut reader = BufReader::new(&mut tls);
    let mut line = String::new();
    tokio::time::timeout(Duration::from_secs(10), reader.read_line(&mut line)).await??;
    let reply: Value = serde_json::from_str(&line)?;
    if reply["ok"] != true {
        anyhow::bail!("{}", reply["error"].as_str().unwrap_or("Oppa relay said no"));
    }
    Ok((tls, plain(reply["uuid"].as_str().unwrap_or(""))))
}

async fn run(mut out: mpsc::UnboundedReceiver<Value>, events: async_channel::Sender<Event>) {
    let mut wait = 2;
    let mut tokens = crate::site::token_watch();
    loop {
        let Some(token) = crate::site::token() else {
            let _ = tokens.changed().await;
            continue;
        };
        if let Ok((tls, me)) = connect(&token).await {
            *link().me.lock().unwrap() = me;
            wait = 2;
            let _ = events.send(Event::Online(true)).await;
            let _ = serve(tls, &mut out, &events, &mut tokens).await;
            let _ = events.send(Event::Online(false)).await;
        }
        tokio::select! {
            _ = tokio::time::sleep(Duration::from_secs(wait)) => {}
            _ = tokens.changed() => {}
        }
        wait = (wait * 2).min(60);
    }
}

async fn serve(
    tls: tokio_rustls::client::TlsStream<TcpStream>,
    out: &mut mpsc::UnboundedReceiver<Value>,
    events: &async_channel::Sender<Event>,
    tokens: &mut tokio::sync::watch::Receiver<Option<String>>,
) -> anyhow::Result<()> {
    let (rd, mut wr) = tokio::io::split(tls);
    let mut lines = BufReader::new(rd).lines();
    let mut ask = json!({"op": "unread"}).to_string();
    ask.push('\n');
    wr.write_all(ask.as_bytes()).await?;
    while out.try_recv().is_ok() {}
    loop {
        tokio::select! {
            line = tokio::time::timeout(Duration::from_secs(90), lines.next_line()) => {
                let Some(line) = line?? else { return Ok(()) };
                let v: Value = serde_json::from_str(&line)?;
                let event = match v["op"].as_str().unwrap_or("") {
                    "ping" => {
                        wr.write_all(b"{\"op\":\"pong\"}\n").await?;
                        None
                    }
                    "msg" => serde_json::from_value(v).ok().map(Event::Message),
                    "history" => Some(Event::History(
                        plain(v["with"].as_str().unwrap_or("")),
                        serde_json::from_value(v["messages"].clone()).unwrap_or_default(),
                    )),
                    "unread" => Some(Event::Unread(serde_json::from_value(v["counts"].clone()).unwrap_or_default())),
                    "error" => Some(Event::Refused(v["error"].as_str().unwrap_or("Message not sent").to_string())),
                    _ => None,
                };
                if let Some(e) = event {
                    let _ = events.send(e).await;
                }
            }
            msg = out.recv() => {
                let Some(msg) = msg else { return Ok(()) };
                let mut s = msg.to_string();
                s.push('\n');
                wr.write_all(s.as_bytes()).await?;
                wr.flush().await?;
            }
            _ = tokens.changed() => return Ok(()),
        }
    }
}
