// Regeln – uebertragen aus der Android-App (CategoryRules.kt, ContentFilter.kt, ParentalControl.kt, XtreamSource.kt).

export const CAT_FAV = '__fav__';
export const CAT_RECENT = '__recent__';
export const CAT_ALL = '__all__';

const langRegex = /^\W*([A-Za-z]{2,4})\s*[|:\-–]/;

/** Sprach-/Laender-Praefix einer Kategorie, z.B. "EN | MOVIES LATEST" -> "EN". */
export function categoryLanguage(name) {
  const m = langRegex.exec(name || '');
  return m ? m[1].toUpperCase() : null;
}

/** Name ohne Sprach-Praefix. */
export function stripLanguage(name) {
  const m = langRegex.exec(name || '');
  if (!m) return name;
  return name.substring(m.index + m[0].length).trim().replace(/^[|:\-–\s]+/, '').trim();
}

/** Alle Praefixe, die mindestens zweimal vorkommen (sonst kein Sprachschema). */
export function detectLanguages(categories) {
  const counts = {};
  for (const c of categories) {
    const l = categoryLanguage(c.name);
    if (l) counts[l] = (counts[l] || 0) + 1;
  }
  return Object.keys(counts).filter((k) => counts[k] >= 2).sort((a, b) => counts[b] - counts[a]);
}

/** Alle Woerter der Suche muessen im Namen vorkommen. */
export function matchesQuery(name, q) {
  const words = (q || '').trim().toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return true;
  const n = (name || '').toLowerCase();
  return words.every((w) => n.indexOf(w) >= 0);
}

const adultRegex = /(xxx|adult|erotic|erotik|porn|\b18\s*\+|\+\s*18\b|for adults|nur für erwachsene)/i;
export const isAdult = (name) => adultRegex.test(name || '');

const yearRegex = /\b(19[3-9]\d|20[0-4]\d)\b/;
/** Jahr aus Datum/Jahr-Feld oder aus dem Titel, z.B. "Film (2019)". */
export function yearOf(field, name) {
  const a = field && yearRegex.exec(field);
  if (a) return +a[1];
  const b = name && /\((19[3-9]\d|20[0-4]\d)\)/.exec(name);
  return b ? +b[1] : null;
}

export const SORTS = [
  ['DEFAULT', 'Standard'], ['NAME_ASC', 'A – Z'], ['NAME_DESC', 'Z – A'],
  ['NEWEST', 'Neu hinzugefügt'], ['RATING', 'Beste Bewertung'], ['YEAR_DESC', 'Neueste Jahre'],
];

export function applySort(list, sort) {
  const l = list.slice();
  switch (sort) {
    case 'NAME_ASC': return l.sort((a, b) => a.name.localeCompare(b.name));
    case 'NAME_DESC': return l.sort((a, b) => b.name.localeCompare(a.name));
    case 'NEWEST': return l.sort((a, b) => (b.added || 0) - (a.added || 0));
    case 'RATING': return l.sort((a, b) => (b.rating || 0) - (a.rating || 0));
    case 'YEAR_DESC': return l.sort((a, b) => (b.year || 0) - (a.year || 0));
    default: return l;
  }
}

/** Serien-Komfort (wie Android EpisodeFlow). */
export const EPISODE = {
  NEXT_BEFORE_END: 40000,
  INTRO_SHOW_MS: 7000,
  INTRO_SKIP: 85000,
  INTRO_WINDOW_START: 5000,
  LEARN_WITHIN: 600000, LEARN_MIN: 20000, LEARN_MAX: 300000,
};

/** FSK aus Anbieter-Angaben ("16", "FSK 12", "PG-13", "R" ...). */
export function fskOf(age) {
  const a = (age || '').trim().toUpperCase();
  if (!a) return null;
  const m = /\b(0|6|12|16|18)\b/.exec(a);
  if (m) return +m[1];
  const us = { G: 0, PG: 6, 'PG-13': 12, R: 16, 'NC-17': 18 };
  return us[a] != null ? us[a] : null;
}
