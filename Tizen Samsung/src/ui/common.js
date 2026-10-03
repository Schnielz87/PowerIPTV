// Gemeinsame Bausteine der Bildschirme: Kopfzeile, Karten, Raster mit Nachladen, Einstellungszeilen.
import { h, clear, clock } from '../util';
import { focusable, getFocus } from '../focus';
import { app, back, choose, go } from '../app';
import { T } from '../sources';

export const ICON = {
  live: '📺', movie: '🎬', series: '🎞', search: '🔍', epg: '📅', fav: '❤', ai: '✨', sync: '⟳',
  user: '👤', settings: '⚙', update: '⬇', back: '←', lock: '🔒', pin: '📌', play: '▶', pause: '⏸',
};

/** Kopfzeile mit Zurueck-Knopf, Titel, rechts eigene Knoepfe und Uhr. */
export function topbar(title, actions = [], opts = {}) {
  const clk = h('div.clock', null, clock(Date.now()));
  const bar = h('div.topbar', null,
    opts.noBack ? h('img.logo', { src: 'assets/logo.png' }) : focusable(h('div.icon-btn', null, h('span.ico', null, ICON.back)), () => back()),
    // Portiva-"P": von jeder Seite direkt zur Startseite (wie Android/Windows)
    !opts.noBack && app.profile ? focusable(h('div.logo-btn', null, h('img', { src: 'assets/logo.png' })), () => go('home', {}, { replace: true })) : null,
    h('h1.ellipsis', null, title),
    h('div.spacer'),
    actions,
    clk,
  );
  const t = setInterval(() => { if (!document.body.contains(clk)) clearInterval(t); else clk.textContent = clock(Date.now()); }, 15000);
  return bar;
}

export function iconButton(icon, label, onEnter) {
  return focusable(h('div.icon-btn', null, h('span.ico', null, icon), label ? h('span', null, label) : null), onEnter);
}

export function button(label, onEnter, cls = '') {
  return focusable(h('div.btn' + cls, null, label), onEnter);
}

export function loading(text) {
  return h('div.loading', null, h('div.spinner'), text ? h('p.muted', { style: { marginTop: '20px' } }, text) : null);
}

export function empty(text) {
  return h('div.empty', null, text);
}

const bg = (url) => (url ? `url("${String(url).replace(/"/g, '%22')}")` : '');

/** Bild erst laden, wenn die Karte gebaut wird (Raster baut ohnehin nur sichtbare Bloecke). */
export function imgDiv(cls, url) {
  const d = h('div.' + cls);
  if (url) d.style.backgroundImage = bg(url);
  return d;
}

/** Karte fuer ein Element (Sender = breite Zeile mit Logo, Film/Serie = Poster). */
export function itemCard(item, onEnter, opts = {}) {
  const lib = app.library;
  if (item.type === T.LIVE) {
    const epgLine = h('div.e', null, opts.sub || '');
    const el = h('div.channel', null,
      imgDiv('logo', item.logo),
      h('div.txt', null,
        h('div.n', null, (item.number ? item.number + '  ' : '') + item.name, lib && lib.isFavorite(item) ? h('span.fav-dot', null, ' ❤') : null),
        epgLine),
    );
    el._epgLine = epgLine;
    return focusable(el, onEnter, opts.extra || {});
  }
  const img = imgDiv('img', item.logo);
  if (lib && lib.isFavorite(item)) img.appendChild(h('div.tag.fav', null, '❤'));
  if (item.rating) img.appendChild(h('div.tag.rating', null, '★ ' + (Math.round(item.rating * 10) / 10)));
  const key = lib && lib.key(item);
  if (lib && item.type === T.MOVIE) {
    if (lib.isWatched(key)) img.appendChild(h('div.tag.seen', null, '✓ Gesehen'));
    else if (opts.progress != null) img.appendChild(h('div.progress', { style: { width: Math.round(opts.progress * 100) + '%' } }));
  }
  if (opts.progress != null && item.type !== T.MOVIE) img.appendChild(h('div.progress', { style: { width: Math.round(opts.progress * 100) + '%' } }));
  return focusable(h('div.poster', null, img, h('div.n', null, item.name + (item.year && item.name.indexOf(String(item.year)) < 0 ? ` (${item.year})` : ''))), onEnter, opts.extra || {});
}

/**
 * Raster fuer grosse Listen (tausende Sender): baut Karten in Bloecken und laedt nach,
 * sobald der Fokus in die Naehe des Endes kommt. Haelt den Fernseher fluessig.
 */
export function lazyGrid(container, items, makeCard, opts = {}) {
  const inner = h('div.grid-inner');
  clear(container);
  container.appendChild(inner);
  container.scrollTop = 0;
  const chunk = opts.chunk || 60;
  let built = 0;
  const cards = [];
  function more() {
    const end = Math.min(items.length, built + chunk);
    const frag = document.createDocumentFragment();
    for (let i = built; i < end; i++) {
      const c = makeCard(items[i], i);
      c._index = i;
      const prev = c._onFocus;
      c._onFocus = () => { if (prev) prev(); if (i > built - chunk / 2) more(); if (opts.onFocusItem) opts.onFocusItem(items[i], c); };
      cards.push(c);
      frag.appendChild(c);
    }
    built = end;
    inner.appendChild(frag);
  }
  more();
  return { inner, cards, first: () => cards[0] || null, more, built: () => built };
}

/** Einstellungszeile: Titel, Untertitel, Wert rechts. */
export function row(title, sub, value, onEnter, extra) {
  const v = h('div.v', null, value == null ? '' : value);
  const s = h('div.s', null, sub || '');
  const el = focusable(h('div.item-row', null, h('div.main', null, h('div.t', null, title), sub != null ? s : null), v), onEnter && (() => onEnter(el)), extra);
  el._value = v;
  el._sub = s;
  return el;
}

/** Ein/Aus-Zeile. */
export function toggleRow(title, sub, get, set) {
  const sw = h('div.switch' + (get() ? '.on' : ''));
  const el = focusable(h('div.item-row', null, h('div.main', null, h('div.t', null, title), sub ? h('div.s', null, sub) : null), sw), () => {
    set(!get());
    sw.classList.toggle('on', !!get());
  });
  el._refresh = () => sw.classList.toggle('on', !!get());
  return el;
}

/** Auswahlzeile (oeffnet Liste). options: [[wert, text]] */
export function selectRow(title, sub, options, get, set) {
  const label = () => { const o = options.find((x) => x[0] === get()); return o ? o[1] : ''; };
  const el = row(title, sub, label(), () => {
    choose(title, options.map((o) => ({ label: o[1], active: o[0] === get(), onSelect: () => { set(o[0]); el._value.textContent = label(); } })));
  });
  return el;
}

/** Texteingabe mit Beschriftung. OK oeffnet die Bildschirmtastatur des Fernsehers. */
export function field(label, value, opts = {}) {
  const input = h('input', { type: opts.type || 'text', value: value || '', placeholder: opts.placeholder || '', autocomplete: 'off', spellcheck: false });
  if (opts.onDone) input._onDone = opts.onDone;
  focusable(input);
  return { el: h('div.field', null, label ? h('label', null, label) : null, input), input };
}

/** Kurze Beschreibung eines Elements fuer Untertitel (Typ). */
export const typeName = (t) => ({ LIVE: 'Live TV', MOVIE: 'Film', SERIES: 'Serie' }[t] || '');

export function focused() { return getFocus(); }

/** Farbtasten-Legende unten links. */
export function legend(items) {
  return h('div.legend', null, ...items.map(([c, t]) => [h('b.' + c), t]));
}
