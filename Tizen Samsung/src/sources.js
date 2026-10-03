// Xtream Codes und M3U – uebertragen aus XtreamSource.kt / M3uSource.kt der Android-App.
import { getJson, getText, query } from './net';
import { yearOf } from './rules';

export const T = { LIVE: 'LIVE', MOVIE: 'MOVIE', SERIES: 'SERIES' };

const str = (v) => (v == null || v === false || v === 'null' || String(v).trim() === '' ? null : String(v));
const num = (v) => { const n = parseFloat(v); return isNaN(n) ? null : n; };
const arr = (v) => (Array.isArray(v) ? v : (v && typeof v === 'object' ? Object.keys(v).map((k) => v[k]) : []));

function b64(s) {
  if (!s) return s;
  try { return decodeURIComponent(escape(atob(s))); } catch (e) { return s; }
}

function img(v) {
  const s = str(v);
  if (!s || /^(null|n\/a)$/i.test(s)) return null;
  if (s.indexOf('http') === 0) return s;
  if (s[0] === '/') return 'https://image.tmdb.org/t/p/w300' + s;
  return null;
}

export function normalizeServer(url) {
  let u = (url || '').trim().replace(/\/+$/, '');
  if (!/^https?:\/\//i.test(u)) u = 'http://' + u;
  return u.replace(/\/player_api\.php$/i, '').replace(/\/get\.php$/i, '').replace(/\/+$/, '');
}

const enc = (s) => encodeURIComponent(s);

export class XtreamSource {
  constructor(profile, liveExt) {
    this.profile = profile;
    this.liveExt = liveExt || (() => 'ts');
    this.base = normalizeServer(profile.serverUrl);
    this.cache = {};
    this.timezone = null;
    this.supportsDetails = true;
  }

  call(action, params = {}) {
    return getJson(`${this.base}/player_api.php?` + query(Object.assign({ username: this.profile.username, password: this.profile.password, action }, params)), 45000);
  }

  async authenticate() {
    const root = await this.call(undefined);
    const info = root && root.user_info;
    if (!info) throw new Error('Ungültige Server-Antwort');
    if (+info.auth !== 1) throw new Error('Benutzername oder Passwort falsch');
    if (root.server_info && root.server_info.timezone) this.timezone = root.server_info.timezone;
    return {
      status: str(info.status),
      expiresAt: num(info.exp_date) ? num(info.exp_date) * 1000 : null,
      maxConnections: str(info.max_connections),
      activeConnections: str(info.active_cons),
    };
  }

  accountInfo() { return this.authenticate().catch(() => null); }

  async categories(type) {
    const key = 'cat' + type;
    if (this.cache[key]) return this.cache[key];
    const action = { LIVE: 'get_live_categories', MOVIE: 'get_vod_categories', SERIES: 'get_series_categories' }[type];
    const list = arr(await this.call(action)).filter((o) => str(o.category_id))
      .map((o) => ({ id: String(o.category_id), name: str(o.category_name) || 'Unbenannt' }));
    this.cache[key] = list;
    return list;
  }

  async items(type, categoryId) {
    const key = `it${type}:${categoryId || '*'}`;
    if (this.cache[key]) return this.cache[key];
    const action = { LIVE: 'get_live_streams', MOVIE: 'get_vod_streams', SERIES: 'get_series' }[type];
    let raw = arr(await this.call(action, categoryId ? { category_id: categoryId } : {}));
    if (!categoryId && raw.length === 0) {
      // Manche Anbieter liefern ohne category_id nichts -> Kategorie fuer Kategorie
      const cats = await this.categories(type);
      raw = [];
      for (const c of cats) {
        try { raw = raw.concat(arr(await this.call(action, { category_id: c.id }))); } catch (e) { /* weiter */ }
      }
    }
    const seen = {};
    const list = [];
    for (const o of raw) {
      const id = str(type === T.SERIES ? o.series_id : o.stream_id);
      if (!id || seen[id]) continue;
      seen[id] = true;
      const name = str(o.name) || '';
      if (type === T.LIVE) {
        list.push({
          id, name, type, categoryId: str(o.category_id) || '', logo: img(o.stream_icon),
          epgChannelId: str(o.epg_channel_id), number: num(o.num),
          archiveDays: +o.tv_archive === 1 ? (num(o.tv_archive_duration) || 1) : 0,
        });
      } else if (type === T.MOVIE) {
        list.push({
          id, name, type, categoryId: str(o.category_id) || '', logo: img(o.stream_icon),
          ext: str(o.container_extension), rating: num(o.rating), added: num(o.added) ? num(o.added) * 1000 : null,
          year: yearOf(str(o.year) || str(o.release_date), name),
        });
      } else {
        list.push({
          id, name, type, categoryId: str(o.category_id) || '', logo: img(o.cover), rating: num(o.rating),
          added: num(o.last_modified) ? num(o.last_modified) * 1000 : null,
          year: yearOf(str(o.releaseDate) || str(o.release_date) || str(o.year), name), genre: str(o.genre),
        });
      }
    }
    this.cache[key] = list;
    return list;
  }

  async movieInfo(item) {
    const root = await this.call('get_vod_info', { vod_id: item.id });
    const info = (root && root.info) || {};
    const movie = (root && root.movie_data) || {};
    const bd = Array.isArray(info.backdrop_path) ? info.backdrop_path[0] : info.backdrop_path;
    return {
      plot: str(info.plot) || str(info.description), genre: str(info.genre), director: str(info.director),
      cast: str(info.cast) || str(info.actors), releaseDate: str(info.releasedate) || str(info.release_date),
      duration: str(info.duration), rating: num(info.rating), cover: img(info.movie_image) || img(info.cover_big) || item.logo,
      backdrop: img(bd), ext: str(movie.container_extension) || item.ext,
      age: str(info.age) || str(info.mpaa_rating) || str(info.certification),
    };
  }

  async seriesInfo(item) {
    const root = await this.call('get_series_info', { series_id: item.id });
    const info = (root && root.info) || {};
    const bd = Array.isArray(info.backdrop_path) ? info.backdrop_path[0] : info.backdrop_path;
    const seriesBackdrop = img(bd);
    const seasonCovers = {};
    for (const s of arr(root && root.seasons)) {
      const nr = num(s.season_number) || num(s.season_num);
      const c = img(s.cover_big) || img(s.cover) || img(s.cover_tmdb);
      if (nr != null && c) seasonCovers[nr] = c;
    }
    const episodes = {};
    const add = (o, fallbackSeason) => {
      const ei = o.info || {};
      const season = num(o.season) || fallbackSeason;
      const id = str(o.id);
      if (!id) return;
      const ep = {
        id, season, episodeNum: num(o.episode_num) || 0,
        title: str(o.title) || `Episode ${o.episode_num || ''}`,
        ext: str(o.container_extension),
        plot: (str(ei.plot) || '').replace(/^(n\/a|null)$/i, '') || null,
        duration: str(ei.duration),
        image: img(ei.movie_image) || img(ei.cover_big) || img(ei.still_path) || img(o.cover) || seasonCovers[season] || seriesBackdrop || item.logo,
      };
      (episodes[season] = episodes[season] || []).push(ep);
    };
    const eps = root && root.episodes;
    if (Array.isArray(eps)) eps.forEach((a, i) => arr(a).forEach((o) => add(o, i + 1)));
    else if (eps && typeof eps === 'object') Object.keys(eps).forEach((s) => arr(eps[s]).forEach((o) => add(o, +s || 0)));
    const seasons = Object.keys(episodes).map(Number).sort((a, b) => a - b);
    seasons.forEach((s) => episodes[s].sort((a, b) => a.episodeNum - b.episodeNum));
    return {
      plot: str(info.plot), genre: str(info.genre), cast: str(info.cast), releaseDate: str(info.releaseDate) || str(info.release_date),
      rating: num(info.rating), cover: img(info.cover) || item.logo, backdrop: seriesBackdrop, episodes, seasons,
      age: str(info.age) || str(info.mpaa_rating) || str(info.certification),
    };
  }

  /** Programm eines Senders (heute + folgende Tage, mit Archiv-Kennzeichen). */
  async epg(item, full) {
    try {
      const root = await this.call(full ? 'get_simple_data_table' : 'get_short_epg', full ? { stream_id: item.id } : { stream_id: item.id, limit: 4 });
      return arr(root && root.epg_listings).map((o) => ({
        title: b64(str(o.title)) || '',
        description: b64(str(o.description)),
        start: num(o.start_timestamp) ? num(o.start_timestamp) * 1000 : Date.parse((o.start || '').replace(' ', 'T') + 'Z'),
        end: num(o.stop_timestamp) ? num(o.stop_timestamp) * 1000 : Date.parse((o.end || '').replace(' ', 'T') + 'Z'),
        archive: +o.has_archive === 1,
      })).filter((p) => p.end > p.start).sort((a, b) => a.start - b.start);
    } catch (e) { return []; }
  }

  streamUrl(item) {
    const u = enc(this.profile.username), p = enc(this.profile.password);
    if (item.type === T.LIVE) return `${this.base}/live/${u}/${p}/${item.id}.${this.liveExt()}`;
    return `${this.base}/movie/${u}/${p}/${item.id}.${item.ext || 'mp4'}`;
  }

  episodeUrl(ep) {
    return `${this.base}/series/${enc(this.profile.username)}/${enc(this.profile.password)}/${ep.id}.${ep.ext || 'mp4'}`;
  }

  /** Xtream-Timeshift: /timeshift/{user}/{pass}/{dauer_min}/{yyyy-MM-dd:HH-mm}/{stream_id}.ts (Zeit in der Server-Zeitzone). */
  catchupUrl(item, start, end) {
    if (item.type !== T.LIVE || !item.archiveDays) return null;
    if (start < Date.now() - item.archiveDays * 86400000 || start > Date.now()) return null;
    const minutes = Math.max(1, Math.round((end - start) / 60000));
    return `${this.base}/timeshift/${enc(this.profile.username)}/${enc(this.profile.password)}/${minutes}/${formatInZone(start, this.timezone)}/${item.id}.ts`;
  }

  clearCache() { this.cache = {}; }
}

/** yyyy-MM-dd:HH-mm in der Zeitzone des Servers (Intl, in Chromium 56 vorhanden). */
function formatInZone(ms, tz) {
  const pad = (n) => String(n).padStart(2, '0');
  try {
    if (tz) {
      const s = new Intl.DateTimeFormat('en-GB', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(ms));
      const m = /(\d{2})\/(\d{2})\/(\d{4}),?\s+(\d{2}):(\d{2})/.exec(s);
      if (m) return `${m[3]}-${m[2]}-${m[1]}:${m[4] === '24' ? '00' : m[4]}-${m[5]}`;
    }
  } catch (e) { /* unbekannte Zeitzone */ }
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}:${pad(d.getHours())}-${pad(d.getMinutes())}`;
}

export class M3uSource {
  constructor(profile) {
    this.profile = profile;
    this.parsed = null;
    this.supportsDetails = false;
  }

  async data() {
    if (this.parsed) return this.parsed;
    const text = await getText(this.profile.m3uUrl.trim(), 120000);
    const out = parseM3u(text);
    if (!out.LIVE.length && !out.MOVIE.length && !out.SERIES.length) throw new Error('Keine Kanäle in der Playlist gefunden');
    this.parsed = out;
    return out;
  }

  async authenticate() { await this.data(); return null; }
  accountInfo() { return Promise.resolve(null); }

  async categories(type) {
    const seen = {};
    const out = [];
    for (const i of (await this.data())[type]) if (!seen[i.categoryId]) { seen[i.categoryId] = 1; out.push({ id: i.categoryId, name: i.categoryId }); }
    return out;
  }

  async items(type, categoryId) {
    const all = (await this.data())[type];
    return categoryId ? all.filter((i) => i.categoryId === categoryId) : all;
  }

  movieInfo() { return Promise.resolve(null); }
  seriesInfo() { return Promise.resolve(null); }
  epg() { return Promise.resolve([]); }
  streamUrl(item) { return item.url; }
  episodeUrl(ep) { return ep.url; }
  catchupUrl() { return null; }
  clearCache() { this.parsed = null; }
}

const attrRegex = /([\w-]+)="([^"]*)"/g;

export function parseM3u(text) {
  const out = { LIVE: [], MOVIE: [], SERIES: [] };
  let attrs = {}, title = null, group = null, counter = 0;
  const lines = text.split(/\r?\n/);
  for (let raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    if (/^#EXTINF/i.test(line)) {
      attrs = {};
      const comma = lastCommaOutsideQuotes(line);
      const header = comma >= 0 ? line.slice(0, comma) : line;
      let m;
      attrRegex.lastIndex = 0;
      while ((m = attrRegex.exec(header))) attrs[m[1].toLowerCase()] = m[2];
      title = comma >= 0 ? line.slice(comma + 1).trim() : '';
    } else if (/^#EXTGRP/i.test(line)) {
      group = line.slice(line.indexOf(':') + 1).trim();
    } else if (line[0] === '#') {
      continue;
    } else {
      const url = line;
      const name = title || attrs['tvg-name'] || url.slice(url.lastIndexOf('/') + 1);
      const cat = attrs['group-title'] || group || 'Ohne Kategorie';
      const type = classify(url, cat);
      counter++;
      out[type].push({
        id: 'm' + counter, name, type, categoryId: cat, logo: attrs['tvg-logo'] || null, url,
        epgChannelId: attrs['tvg-id'] || null, number: num(attrs['tvg-chno']),
        archiveDays: num(attrs['catchup-days']) || 0, year: type === T.LIVE ? null : yearOf(null, name),
      });
      attrs = {}; title = null; group = null;
    }
  }
  return out;
}

function lastCommaOutsideQuotes(line) {
  let q = false, idx = -1;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (c === '"') q = !q;
    if (c === ',' && !q) idx = i;
  }
  return idx;
}

function classify(url, group) {
  const u = url.toLowerCase(), g = group.toLowerCase();
  if (u.indexOf('/series/') >= 0 || g.indexOf('serie') >= 0) return T.SERIES;
  if (u.indexOf('/movie/') >= 0 || g.indexOf('vod') >= 0 || g.indexOf('film') >= 0 || g.indexOf('movie') >= 0) return T.MOVIE;
  if (/\.(mp4|mkv|avi)$/.test(u)) return T.MOVIE;
  return T.LIVE;
}

export function createSource(profile, liveExt) {
  return profile.type === 'XTREAM' ? new XtreamSource(profile, liveExt) : new M3uSource(profile);
}
