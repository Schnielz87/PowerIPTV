// Optional: deutsche Folgen-Beschreibungen und Folgenbilder von TMDB (themoviedb.org), wenn der Anbieter keine liefert.
// Braucht einen kostenlosen TMDB-API-Schluessel (Einstellungen → „Folgen-Beschreibungen (TMDB)“), wie am Handy.
import { getJson } from './net';
import { storage } from './util';

export const tmdbKey = {
  get() { return storage.get('tmdb.key', ''); },
  set(v) { storage.set('tmdb.key', String(v || '').trim()); },
};

const cache = {};

/** Seriennamen fuer die Suche bereinigen: "Breaking Bad # DE", "DE: Dark (2017)" -> "Breaking Bad", "Dark". */
export function cleanName(n) {
  return String(n || '')
    .replace(/^\s*\[?[A-Z]{2,3}\]?\s*[|:\-–]\s*/, '')
    .replace(/#.*$/, '')
    .replace(/\((19|20)\d{2}\)/, '')
    .replace(/\b(HD|FHD|UHD|4K|GER|GERMAN|DEUTSCH|MULTI)\b/gi, '')
    .replace(/\s+/g, ' ')
    .trim();
}

async function seriesId(name, tmdbId, key) {
  if (tmdbId && /^\d+$/.test(tmdbId)) return tmdbId;
  const q = cleanName(name);
  if (!q) return null;
  const ck = `id|${q}`;
  if (cache[ck] !== undefined) return cache[ck];
  const r = await getJson(`https://api.themoviedb.org/3/search/tv?api_key=${encodeURIComponent(key)}&language=de-DE&query=${encodeURIComponent(q)}`, 15000);
  const hit = r && r.results && r.results[0];
  cache[ck] = hit ? String(hit.id) : null;
  return cache[ck];
}

/** Folgen einer Staffel: { [Folgennummer]: { plot, still } } oder null. */
export async function tmdbSeason(name, tmdbId, season) {
  const key = tmdbKey.get();
  if (!key) return null;
  const id = await seriesId(name, tmdbId, key);
  if (!id) return null;
  const ck = `s|${id}|${season}`;
  if (cache[ck]) return cache[ck];
  const r = await getJson(`https://api.themoviedb.org/3/tv/${id}/season/${season}?api_key=${encodeURIComponent(key)}&language=de-DE`, 15000);
  const out = {};
  ((r && r.episodes) || []).forEach((e) => {
    out[e.episode_number] = {
      plot: (e.overview || '').trim() || null,
      still: e.still_path ? `https://image.tmdb.org/t/p/w300${e.still_path}` : null,
      name: e.name || null,
    };
  });
  cache[ck] = out;
  return out;
}
