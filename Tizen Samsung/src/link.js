// Portiva Link (gleiches Protokoll wie Android/Windows, siehe app/.../link/PortivaLink.kt).
import { isVega, request } from './vega';
// Der Fernseher darf selbst keinen Netzwerkdienst anbieten. Deshalb:
//  - Zugang empfangen: der Fernseher zeigt einen Code (Port 0) und holt den Zugang beim Handy ab.
//  - Wiedergabe senden: der Fernseher sucht Portiva-Geraete im Heimnetz und schickt dorthin.
import qrcode from 'qrcode-generator';
import { uid } from './util';

export const LINK_PORT = 47800;
const ACCOUNT_PREFIX = 'PORTIVA1:';
const PAIR_PREFIX = 'PORTIVA-PAIR:';

function b64url(text) {
  return btoa(unescape(encodeURIComponent(text))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Profil (Tizen) -> Zugangsdaten im gemeinsamen Format. */
export function toAccount(p) {
  return {
    name: p.name || '', type: p.type === 'XTREAM' ? 'XTREAM' : 'M3U_URL',
    serverUrl: p.serverUrl || '', username: p.username || '', password: p.password || '', m3uUrl: p.m3uUrl || '', epgUrl: '',
  };
}

export function fromAccount(a, id) {
  return {
    id: id || uid(), name: a.name || 'Zugang', type: a.type === 'XTREAM' ? 'XTREAM' : 'M3U',
    serverUrl: a.serverUrl || '', username: a.username || '', password: a.password || '', m3uUrl: a.m3uUrl || '',
  };
}

export const accountQr = (p) => ACCOUNT_PREFIX + b64url(JSON.stringify(toAccount(p)));
export const pairQr = (ip, code) => `${PAIR_PREFIX}${ip}:0:${code}`;
export const newCode = () => String(100000 + Math.floor(Math.random() * 900000));

/** QR-Code als Bild (data:-URL), schwarz auf weiss. */
export function qrImage(text, cell = 8) {
  const qr = qrcode(0, 'M');
  qr.addData(text);
  qr.make();
  return qr.createDataURL(cell, 4);
}

/** Eigene IP-Adresse im Heimnetz (Samsung-API, sonst ueber WebRTC). */
export function localIp() {
  return new Promise((resolve) => {
    try {
      const ip = window.webapis && window.webapis.network && window.webapis.network.getIp();
      if (ip && /^\d+\.\d+\.\d+\.\d+$/.test(ip)) { resolve(ip); return; }
    } catch (e) { /* keine Berechtigung / kein Fernseher */ }
    try {
      const pc = new RTCPeerConnection({ iceServers: [] });
      let done = false;
      const finish = (v) => { if (!done) { done = true; try { pc.close(); } catch (e) { /* egal */ } resolve(v); } };
      pc.createDataChannel('x');
      pc.onicecandidate = (e) => {
        if (!e || !e.candidate) return;
        const m = /(\d+\.\d+\.\d+\.\d+)/.exec(e.candidate.candidate);
        if (m && /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(m[1])) finish(m[1]);
      };
      pc.createOffer().then((o) => pc.setLocalDescription(o)).catch(() => finish(null));
      setTimeout(() => finish(null), 2500);
    } catch (e) { resolve(null); }
  });
}

function xhr(method, url, body, timeout) {
  if (isVega()) return request(method, url, body ? JSON.stringify(body) : null, body ? { 'Content-Type': 'application/json' } : {}, timeout);
  return new Promise((resolve) => {
    const x = new XMLHttpRequest();
    x.open(method, url, true);
    x.timeout = timeout;
    if (body) x.setRequestHeader('Content-Type', 'application/json');
    x.onload = () => resolve({ status: x.status, text: x.responseText });
    x.onerror = () => resolve(null);
    x.ontimeout = () => resolve(null);
    x.send(body ? JSON.stringify(body) : null);
  });
}

/** Portiva-Geraete im eigenen /24-Netz (je Geraet {name, platform, host, port}). */
export async function discover(ip) {
  const own = ip || await localIp();
  if (!own) return [];
  const prefix = own.slice(0, own.lastIndexOf('.'));
  const hosts = [];
  for (let i = 1; i < 255; i++) if (`${prefix}.${i}` !== own) hosts.push(`${prefix}.${i}`);
  const found = [];
  let next = 0;
  async function worker() {
    while (next < hosts.length) {
      const h = hosts[next++];
      const r = await xhr('GET', `http://${h}:${LINK_PORT}/portiva/hello`, null, 900);
      if (!r || r.status !== 200) continue;
      try {
        const d = JSON.parse(r.text);
        if (d.app === 'portiva') found.push({ name: d.name, platform: d.platform, host: h, port: d.port || LINK_PORT });
      } catch (e) { /* kein Portiva */ }
    }
  }
  const workers = [];
  for (let w = 0; w < 32; w++) workers.push(worker());
  await Promise.all(workers);
  return found.sort((a, b) => a.name.localeCompare(b.name));
}

/** Zugang beim Handy abholen (das Handy hat ihn nach dem Scannen unter dem Code bereitgestellt). */
export async function fetchOffer(devices, code) {
  for (const d of devices) {
    const r = await xhr('GET', `http://${d.host}:${d.port}/portiva/offer/${code}`, null, 2500);
    if (r && r.status === 200) {
      try { return JSON.parse(r.text); } catch (e) { /* weiter */ }
    }
  }
  return null;
}

/** Wiedergabe an ein Geraet senden. Liefert null bei Erfolg, sonst Fehlertext. */
export async function sendPlay(d, play) {
  const r = await xhr('POST', `http://${d.host}:${d.port}/portiva/play`, play, 8000);
  if (!r) return 'Gerät nicht erreichbar';
  if (r.status >= 200 && r.status < 300) return null;
  if (r.status === 409) return 'Portiva ist auf dem anderen Gerät nicht geöffnet';
  return `Fehler ${r.status}`;
}
