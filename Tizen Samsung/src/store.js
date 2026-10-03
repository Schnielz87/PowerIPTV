// Gespeicherte Daten auf dem Fernseher (localStorage) – gleiche Inhalte wie in Android/Windows.
import { storage, uid } from './util';
import { isAdult } from './rules';

// ---------- Einstellungen ----------
const defaults = {
  lastProfileId: null,
  introSound: true,
  liveFormat: 'ts',        // Xtream Live: "ts" oder "m3u8"
  aspect: 'auto',          // auto | fill | stretch
  autoNext: true,
  categoryLanguage: '',
  buffer: 'normal',        // normal | gross | sehr_gross
  subtitlesOn: false,
};

export const settings = Object.assign({}, defaults, storage.get('settings', {}));
export function saveSettings(patch) {
  Object.assign(settings, patch);
  storage.set('settings', settings);
}

// ---------- Zugaenge ----------
export const profiles = {
  list() { return storage.get('profiles', []); },
  save(p) {
    const l = this.list().filter((x) => x.id !== p.id);
    l.push(p);
    storage.set('profiles', l);
  },
  remove(id) {
    storage.set('profiles', this.list().filter((x) => x.id !== id));
    ['fav', 'hist', 'pos', 'watched', 'intro', 'lists'].forEach((k) => storage.remove(`${k}.${id}`));
  },
  newId: uid,
};

// ---------- Bibliothek je Zugang ----------
export class Library {
  constructor(profileId) {
    this.pid = profileId;
    this.favorites = storage.get(`fav.${profileId}`, []);
    this.history = storage.get(`hist.${profileId}`, []);   // [{item, episode?, position, duration, updated}]
    this.positions = storage.get(`pos.${profileId}`, {});
    this.watched = storage.get(`watched.${profileId}`, {});
    this.intros = storage.get(`intro.${profileId}`, {});
  }

  key(item) { return `${item.type}:${item.id}`; }
  static episodeKey(id) { return `EPISODE:${id}`; }

  isFavorite(item) { return this.favorites.some((f) => this.key(f) === this.key(item)); }
  toggleFavorite(item) {
    const k = this.key(item);
    if (this.isFavorite(item)) this.favorites = this.favorites.filter((f) => this.key(f) !== k);
    else this.favorites.unshift(slim(item));
    storage.set(`fav.${this.pid}`, this.favorites);
    return this.isFavorite(item);
  }

  addHistory(entry) {
    const k = this.key(entry.item);
    this.history = [Object.assign({}, entry, { item: slim(entry.item), updated: Date.now() })]
      .concat(this.history.filter((h) => this.key(h.item) !== k)).slice(0, 150);
    storage.set(`hist.${this.pid}`, this.history);
  }
  removeHistory(item) {
    const k = this.key(item);
    this.history = this.history.filter((h) => this.key(h.item) !== k);
    storage.set(`hist.${this.pid}`, this.history);
  }

  position(key) { return this.positions[key] || 0; }

  /** Wie Android: kurz vor Schluss = gesehen, sonst Position merken. */
  savePosition(key, pos, dur, entry) {
    if (!dur || dur <= 0) return;
    const nearEnd = dur - pos < 90000 || pos / dur > 0.95;
    if (nearEnd) { delete this.positions[key]; this.watched[key] = 1; storage.set(`watched.${this.pid}`, this.watched); }
    else if (pos > 10000) this.positions[key] = pos;
    storage.set(`pos.${this.pid}`, this.positions);
    if (entry) this.addHistory(Object.assign({}, entry, { position: nearEnd ? dur : pos, duration: dur }));
  }

  isWatched(key) { return !!this.watched[key]; }
  setWatched(key, v) {
    if (v) { this.watched[key] = 1; delete this.positions[key]; } else delete this.watched[key];
    storage.set(`watched.${this.pid}`, this.watched);
    storage.set(`pos.${this.pid}`, this.positions);
  }

  continueWatching() {
    return this.history.filter((h) => h.item.type !== 'LIVE' && h.duration > 0 && h.position > 10000 && h.position / h.duration < 0.95);
  }

  setIntro(seriesKey, start, end) { this.intros[seriesKey] = [start, end]; storage.set(`intro.${this.pid}`, this.intros); }
  intro(seriesKey) { return this.intros[seriesKey] || null; }
}

/** Nur die noetigen Felder speichern (localStorage ist auf dem Fernseher knapp). */
function slim(i) {
  const o = {};
  ['id', 'name', 'type', 'categoryId', 'logo', 'url', 'ext', 'rating', 'year', 'number', 'archiveDays', 'epgChannelId'].forEach((k) => {
    if (i[k] != null) o[k] = i[k];
  });
  return o;
}

// ---------- Kategorien anheften / ausblenden ----------
export const categoryPrefs = {
  data: storage.get('catprefs', { hidden: {}, pinned: {} }),
  hidden(scope) { return this.data.hidden[scope] || []; },
  pinned(scope) { return this.data.pinned[scope] || []; },
  setHidden(scope, id, v) {
    const s = this.hidden(scope).filter((x) => x !== id);
    if (v) s.push(id);
    this.data.hidden[scope] = s;
    if (v) this.data.pinned[scope] = this.pinned(scope).filter((x) => x !== id);
    storage.set('catprefs', this.data);
  },
  setPinned(scope, id, v) {
    const s = this.pinned(scope).filter((x) => x !== id);
    if (v) s.push(id);
    this.data.pinned[scope] = s;
    storage.set('catprefs', this.data);
  },
  arrange(scope, cats) {
    const pins = this.pinned(scope);
    if (!pins.length) return cats;
    const top = pins.map((id) => cats.find((c) => c.id === id)).filter(Boolean);
    return top.concat(cats.filter((c) => pins.indexOf(c.id) < 0));
  },
};

// ---------- Kindersicherung (wie ParentalControl.kt) ----------
export const parental = {
  data: storage.get('parental', { enabled: false, autoAdult: true, protectSettings: true, locked: [], salt: null, hash: null }),
  unlocked: false,
  adultCats: {},
  save() { storage.set('parental', this.data); },
  hasPin() { return !!this.data.hash; },
  async setPin(pin) {
    this.data.salt = uid();
    this.data.hash = await sha256(`${this.data.salt}:${pin}`);
    this.save();
  },
  async verify(pin) {
    if (!this.data.hash) return false;
    const ok = (await sha256(`${this.data.salt}:${pin}`)) === this.data.hash;
    if (ok) this.unlocked = true;
    return ok;
  },
  isOn() { return this.data.enabled && this.hasPin(); },
  setEnabled(v) { this.data.enabled = v && this.hasPin(); this.save(); },
  reset() { this.data = { enabled: false, autoAdult: true, protectSettings: true, locked: [], salt: null, hash: null }; this.unlocked = false; this.save(); },
  key(pid, type, catId) { return `${pid}|${type}|${catId}`; },
  register(pid, type, cats) { cats.forEach((c) => { if (isAdult(c.name)) this.adultCats[this.key(pid, type, c.id)] = 1; }); },
  configuredLocked(pid, type, cat) {
    return this.data.locked.indexOf(this.key(pid, type, cat.id)) >= 0 || (this.data.autoAdult && isAdult(cat.name));
  },
  requiresPin(pid, type, cat) { return this.isOn() && !this.unlocked && this.configuredLocked(pid, type, cat); },
  lockedIds(pid, type, cats) {
    this.register(pid, type, cats);
    if (!this.isOn() || this.unlocked) return {};
    const o = {};
    cats.forEach((c) => { if (this.configuredLocked(pid, type, c)) o[c.id] = 1; });
    return o;
  },
  isItemBlocked(pid, item) {
    if (!this.isOn() || this.unlocked) return false;
    const k = this.key(pid, item.type, item.categoryId);
    return this.data.locked.indexOf(k) >= 0 || (this.data.autoAdult && (this.adultCats[k] || isAdult(item.name)));
  },
  visible(pid, items) { return this.isOn() && !this.unlocked ? items.filter((i) => !this.isItemBlocked(pid, i)) : items; },
  setLocked(pid, type, catId, v) {
    const k = this.key(pid, type, catId);
    this.data.locked = this.data.locked.filter((x) => x !== k);
    if (v) this.data.locked.push(k);
    this.save();
  },
  settingsNeedPin() { return this.isOn() && this.data.protectSettings && !this.unlocked; },
};

/** SHA-256 (WebCrypto ist in Chromium 56 vorhanden; Fallback falls nicht). */
async function sha256(text) {
  try {
    const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
    return Array.prototype.map.call(new Uint8Array(buf), (b) => b.toString(16).padStart(2, '0')).join('');
  } catch (e) {
    let h = 0;
    for (let i = 0; i < text.length; i++) h = (h * 31 + text.charCodeAt(i)) | 0;
    return 'x' + h;
  }
}
