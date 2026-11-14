// Synchronisieren (Favoriten, eigene Listen, Weiterschauen) mit anderen Portiva-Geraeten im Heimnetz,
// auf denen derselbe Zugang eingerichtet ist. Gleiches Format wie Android/Windows (SyncCore.kt),
// iOS (Sync.swift) und Enigma2 (sync.py). Der Fernseher fragt die anderen Geraete an (er selbst kann
// keine Anfragen annehmen) und uebernimmt den zusammengefuehrten Stand.
// Jede Aenderung hat einen Zeitstempel (ms); pro Eintrag gewinnt die neuere. Geloeschtes bleibt 60 Tage als "del".
import { storage } from './util';
import { discover, LINK_PORT } from './link';
import { isVega, request } from './vega';

export const DEFAULT_LIST = 'default';
const TOMBSTONE_MS = 60 * 86400000;

// ---------- SHA-256 (eigene Umsetzung – WebCrypto gibt es in der TV-App nicht immer) ----------
const K = [
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5, 0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
  0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174, 0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
  0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85, 0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3, 0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
  0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
];

export function sha256hex(text) {
  const s = unescape(encodeURIComponent(text));
  const bytes = [];
  for (let i = 0; i < s.length; i++) bytes.push(s.charCodeAt(i));
  const bitLen = bytes.length * 8;
  bytes.push(0x80);
  while (bytes.length % 64 !== 56) bytes.push(0);
  for (let i = 7; i >= 0; i--) bytes.push(i >= 4 ? 0 : (bitLen >>> (i * 8)) & 0xff);
  const H = [0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19];
  const w = new Array(64);
  const rotr = (x, n) => (x >>> n) | (x << (32 - n));
  for (let off = 0; off < bytes.length; off += 64) {
    for (let t = 0; t < 16; t++) {
      const j = off + t * 4;
      w[t] = ((bytes[j] << 24) | (bytes[j + 1] << 16) | (bytes[j + 2] << 8) | bytes[j + 3]) | 0;
    }
    for (let t = 16; t < 64; t++) {
      const s0 = rotr(w[t - 15], 7) ^ rotr(w[t - 15], 18) ^ (w[t - 15] >>> 3);
      const s1 = rotr(w[t - 2], 17) ^ rotr(w[t - 2], 19) ^ (w[t - 2] >>> 10);
      w[t] = (w[t - 16] + s0 + w[t - 7] + s1) | 0;
    }
    let [a, b, c, d, e, f, g, h] = H;
    for (let t = 0; t < 64; t++) {
      const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
      const ch = (e & f) ^ (~e & g);
      const t1 = (h + S1 + ch + K[t] + w[t]) | 0;
      const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const t2 = (S0 + maj) | 0;
      h = g; g = f; f = e; e = (d + t1) | 0; d = c; c = b; b = a; a = (t1 + t2) | 0;
    }
    H[0] = (H[0] + a) | 0; H[1] = (H[1] + b) | 0; H[2] = (H[2] + c) | 0; H[3] = (H[3] + d) | 0;
    H[4] = (H[4] + e) | 0; H[5] = (H[5] + f) | 0; H[6] = (H[6] + g) | 0; H[7] = (H[7] + h) | 0;
  }
  return H.map((x) => (x >>> 0).toString(16).padStart(8, '0')).join('');
}

const norm = (u) => String(u || '').trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/+$/, '');

export function canSync(p) { return !!p && !!((p.username || '').trim() || (p.m3uUrl || '').trim()); }

const host = (u) => norm(u).split('/')[0].split('?')[0].split('#')[0].replace(/:(80|443)$/, '');

function query(u) {
  const s = String(u || '');
  const i = s.indexOf('?');
  const out = {};
  if (i < 0) return out;
  s.slice(i + 1).split('#')[0].split('&').forEach((kv) => {
    const j = kv.indexOf('=');
    const k = (j < 0 ? kv : kv.slice(0, j)).toLowerCase();
    if (!k) return;
    const v = j < 0 ? '' : kv.slice(j + 1).replace(/\+/g, ' ');
    try { out[k] = decodeURIComponent(v); } catch (e) { out[k] = v; }
  });
  return out;
}

/** Gleicher Zugang = gleiche Kennung, egal ob als Xtream-Login oder M3U-Link (mit username/password) eingetragen. */
export function accountKey(p) {
  const username = (p.username || '').trim();
  const q = query(p.m3uUrl);
  const user = username || (q.username || '').trim();
  let raw;
  if (user) {
    const pass = (username ? (p.password || '') : (q.password || '')).trim();
    const server = username && (p.serverUrl || '').trim() ? p.serverUrl : p.m3uUrl;
    raw = `portiva-sync-v2|${host(server)}|${user}|${pass}`;
  } else raw = `portiva-sync-v2|m3u|${norm(p.m3uUrl)}`;
  return sha256hex(raw);
}

// ---------- Zusammenfuehren ----------
const newer = (a, aDel, b, bDel) => a > b || (a === b && !aDel && bDel);

function mergeEntries(a, b) {
  const out = new Map();
  a.concat(b).forEach((e) => {
    const cur = out.get(e.k);
    if (!cur) out.set(e.k, e);
    else if (newer(e.ts || 0, !!e.del, cur.ts || 0, !!cur.del)) out.set(e.k, Object.assign({}, e, { item: e.item || cur.item }));
    else out.set(e.k, Object.assign({}, cur, { item: cur.item || e.item }));
  });
  return Array.from(out.values());
}

export function merge(a, b) {
  const cutoff = Date.now() - TOMBSTONE_MS;
  const lists = new Map();
  (a.lists || []).concat(b.lists || []).forEach((l) => {
    const cur = lists.get(l.id);
    if (!cur) lists.set(l.id, l);
    else {
      const head = newer(l.ts || 0, !!l.del, cur.ts || 0, !!cur.del) ? l : cur;
      lists.set(l.id, Object.assign({}, head, { items: mergeEntries(cur.items || [], l.items || []) }));
    }
  });
  const resume = new Map();
  (a.resume || []).concat(b.resume || []).forEach((r) => {
    const cur = resume.get(r.k);
    if (!cur) resume.set(r.k, r);
    else if ((r.ts || 0) > (cur.ts || 0)) resume.set(r.k, Object.assign({}, r, { item: r.item || cur.item, ep: r.ep || cur.ep }));
    else resume.set(r.k, Object.assign({}, cur, { item: cur.item || r.item, ep: cur.ep || r.ep }));
  });
  const old = (x) => x.del && x.ts > 0 && x.ts < cutoff;
  return {
    v: 1,
    lists: Array.from(lists.values()).filter((l) => !old(l)).map((l) => Object.assign({}, l, { items: (l.items || []).filter((e) => !old(e)) })),
    resume: Array.from(resume.values()),
  };
}

export function changes(before, after) {
  const present = (d) => {
    const s = new Set();
    d.lists.filter((l) => !l.del).forEach((l) => (l.items || []).filter((e) => !e.del).forEach((e) => s.add(`${l.id}|${e.k}`)));
    return s;
  };
  const b = present(before);
  const a = present(after);
  let added = 0;
  let removed = 0;
  a.forEach((k) => { if (!b.has(k)) added++; });
  b.forEach((k) => { if (!a.has(k)) removed++; });
  after.resume.forEach((r) => { if (!before.resume.some((x) => x.k === r.k && x.ts >= r.ts)) added++; });
  return [added, removed];
}

// ---------- Zeitstempel der lokalen Aenderungen (pro Profil; negativ = geloescht) ----------
export const meta = {
  all(pid) { return storage.get(`syncmeta.${pid}`, {}); },
  ts(pid, k) { return Math.abs(this.all(pid)[k] || 0); },
  touch(pid, k, deleted) {
    const m = this.all(pid);
    m[k] = deleted ? -Date.now() : Date.now();
    storage.set(`syncmeta.${pid}`, m);
  },
  setAll(pid, values) {
    const m = Object.assign(this.all(pid), values);
    const cutoff = Date.now() - TOMBSTONE_MS;
    Object.keys(m).forEach((k) => { if (m[k] < 0 && -m[k] < cutoff) delete m[k]; });
    storage.set(`syncmeta.${pid}`, m);
  },
};

// ---------- Umrechnung Titel <-> Abgleich-Format ----------
function toSync(i) {
  const o = { id: String(i.id), name: i.name || '', type: i.type };
  if (i.logo) o.logo = i.logo;
  if (i.ext) o.ext = i.ext;
  if (i.year) o.year = i.year;
  if (i.rating != null && i.rating !== '') o.rating = String(i.rating);
  if (i.categoryId) o.cat = String(i.categoryId);
  if (i.url) o.url = i.url;
  if (i.epgChannelId) o.epg = i.epgChannelId;
  return o;
}

function fromSync(s) {
  const o = { id: s.id, name: s.name, type: s.type };
  if (s.logo) o.logo = s.logo;
  if (s.ext) o.ext = s.ext;
  if (s.year) o.year = s.year;
  if (s.rating) o.rating = s.rating;
  if (s.cat) o.categoryId = s.cat;
  if (s.url) o.url = s.url;
  if (s.epg) o.epgChannelId = s.epg;
  return o;
}

const itemKey = (i) => `${i.type}:${i.id}`;

export function exportData(lib) {
  const pid = lib.pid;
  const m = meta.all(pid);
  const local = [{ id: DEFAULT_LIST, name: 'Favoriten', items: lib.favorites }].concat(lib.lists);
  const lists = local.map((l) => {
    const keys = new Set(l.items.map(itemKey));
    const pre = `I|${l.id}|`;
    const items = l.items.map((i) => ({ k: itemKey(i), ts: Math.abs(m[pre + itemKey(i)] || 0), del: false, item: toSync(i) }))
      .concat(Object.keys(m).filter((k) => m[k] < 0 && k.indexOf(pre) === 0 && !keys.has(k.slice(pre.length)))
        .map((k) => ({ k: k.slice(pre.length), ts: -m[k], del: true })));
    return { id: l.id, name: l.name, ts: Math.abs(m[`L|${l.id}`] || 0), del: false, items };
  });
  const ids = new Set(local.map((l) => l.id));
  Object.keys(m).filter((k) => m[k] < 0 && k.indexOf('L|') === 0 && !ids.has(k.slice(2)))
    .forEach((k) => lists.push({ id: k.slice(2), name: '', ts: -m[k], del: true, items: [] }));
  const resume = lib.history.filter((h) => h.item.type !== 'LIVE').map((h) => {
    const key = h.episode ? `EPISODE:${h.episode.id}` : itemKey(h.item);
    const r = { k: itemKey(h.item), pos: Math.round(h.position || 0), dur: Math.round(h.duration || 0), ts: h.updated || 0, done: lib.isWatched(key), item: toSync(h.item) };
    if (h.episode) r.ep = { id: String(h.episode.id), season: h.episode.season || 0, num: h.episode.episodeNum || 0, title: h.episode.title || '', ext: h.episode.ext || null };
    return r;
  });
  return { v: 1, lists, resume };
}

export function applyData(lib, merged, before) {
  const pid = lib.pid;
  const stamps = {};
  const local = [];
  merged.lists.forEach((l) => {
    stamps[`L|${l.id}`] = l.del ? -l.ts : l.ts;
    (l.items || []).forEach((e) => { stamps[`I|${l.id}|${e.k}`] = e.del ? -e.ts : e.ts; });
    if (l.del && l.id !== DEFAULT_LIST) return;
    const items = (l.items || []).map((e, i) => ({ e, i })).filter((x) => !x.e.del && x.e.item)
      .sort((x, y) => (y.e.ts || 0) - (x.e.ts || 0) || x.i - y.i).map((x) => fromSync(x.e.item));
    local.push({ id: l.id, name: l.name || (l.id === DEFAULT_LIST ? 'Favoriten' : 'Liste'), items });
  });
  Object.keys(stamps).forEach((k) => { if (!stamps[k]) delete stamps[k]; });
  lib.favorites = (local.find((l) => l.id === DEFAULT_LIST) || { items: [] }).items;
  lib.lists = local.filter((l) => l.id !== DEFAULT_LIST);
  storage.set(`fav.${pid}`, lib.favorites);
  lib.saveLists();
  meta.setAll(pid, stamps);

  const localTs = {};
  before.resume.forEach((r) => { localTs[r.k] = r.ts; });
  const incoming = merged.resume.filter((r) => r.item && (r.ts || 0) > (localTs[r.k] || 0));
  if (!incoming.length) return;
  const entries = incoming.map((r) => {
    const ep = r.ep ? { id: r.ep.id, season: r.ep.season, episodeNum: r.ep.num, title: r.ep.title, ext: r.ep.ext || undefined } : null;
    const key = ep ? `EPISODE:${ep.id}` : r.k;
    if (r.done) { delete lib.positions[key]; lib.watched[key] = 1; } else if (r.pos > 10000) lib.positions[key] = r.pos; else delete lib.positions[key];
    const h = { item: fromSync(r.item), position: r.done ? r.dur : r.pos, duration: r.dur, updated: r.ts };
    if (ep) h.episode = ep;
    return h;
  });
  const keys = new Set(entries.map((h) => itemKey(h.item)));
  lib.history = entries.concat(lib.history.filter((h) => !keys.has(itemKey(h.item))))
    .filter((h) => !lib.isExcluded(h.item)).sort((x, y) => (y.updated || 0) - (x.updated || 0)).slice(0, 150);
  storage.set(`hist.${pid}`, lib.history);
  storage.set(`pos.${pid}`, lib.positions);
  storage.set(`watched.${pid}`, lib.watched);
}

function post(d, body) {
  const url = `http://${d.host}:${d.port || LINK_PORT}/portiva/sync`;
  if (isVega()) return request('POST', url, JSON.stringify(body), { 'Content-Type': 'application/json' }, 20000);
  return new Promise((resolve) => {
    const x = new XMLHttpRequest();
    x.open('POST', url, true);
    x.timeout = 20000;
    x.setRequestHeader('Content-Type', 'application/json');
    x.onload = () => resolve({ status: x.status, text: x.responseText });
    x.onerror = () => resolve(null);
    x.ontimeout = () => resolve(null);
    x.send(JSON.stringify(body));
  });
}

/** Mit allen Geraeten im Heimnetz abgleichen. progress(text) zeigt den Zwischenstand. Liefert die Meldung. */
export async function syncAll(profile, lib, progress) {
  if (!canSync(profile)) return 'Für diesen Zugang ist kein Abgleich möglich.';
  progress('Suche Geräte im Heimnetz …');
  const devices = await discover();
  if (!devices.length) return 'Kein anderes Gerät mit Portiva gefunden.\nIm gleichen WLAN? Portiva dort geöffnet?';
  const account = accountKey(profile);
  const from = isVega() ? 'Fire TV' : 'Samsung TV';
  const done = [];
  const failed = [];
  let added = 0;
  let removed = 0;
  for (const d of devices) {
    progress(`Gleiche ab mit „${d.name}“ …`);
    const before = exportData(lib);
    const r = await post(d, { account, data: before, from });
    if (!r) { failed.push(`„${d.name}“ nicht erreichbar`); continue; }
    if (r.status === 404) { failed.push(`Portiva auf „${d.name}“ ist zu alt – bitte dort aktualisieren`); continue; }
    let reply = null;
    try { reply = JSON.parse(r.text); } catch (e) { reply = null; }
    if (reply && reply.ok && reply.data) {
      const merged = merge(before, reply.data);
      applyData(lib, merged, before);
      const c = changes(before, merged);
      added += c[0]; removed += c[1];
      done.push(d.name);
    } else if (!(reply && reply.unknown)) failed.push((reply && reply.error) || `Fehler ${r.status}`);
  }
  let msg = done.length
    ? `Synchronisiert mit ${done.join(', ')}.\n${added || removed ? `${added} neu oder aktualisiert, ${removed} entfernt.` : 'Alles war schon aktuell.'}`
    : 'Kein Gerät mit diesem Benutzer gefunden.\nAuf dem anderen Gerät muss derselbe Zugang eingerichtet und Portiva geöffnet sein.';
  if (failed.length) msg += `\n\nNicht geklappt: ${failed.join('; ')}`;
  return msg;
}
