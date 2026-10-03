// Stoebern wie Android: links Kategorien (mit Sprachauswahl DE/EN/…, Favoriten, Zuletzt, Alle,
// Anheften/Ausblenden, Kindersicherung), rechts Sender bzw. Poster mit Nachladen.
import { h, clear, toast, debounce, clock } from '../util';
import { focusable, setFocus, getFocus } from '../focus';
import { KEY } from '../keys';
import { app, go, choose, askPin } from '../app';
import { settings, saveSettings, categoryPrefs, parental } from '../store';
import { T } from '../sources';
import { CAT_FAV, CAT_RECENT, CAT_ALL, categoryLanguage, stripLanguage, detectLanguages, SORTS, applySort } from '../rules';
import { topbar, iconButton, itemCard, lazyGrid, loading, empty, legend, ICON } from './common';

const TITLES = { LIVE: 'Live TV', MOVIE: 'Filme', SERIES: 'Serien' };
const sortKey = (type) => 'sort.' + type;

export default function browse(params) {
  const type = params.type;
  const scope = `${app.profile.id}|${type}`;
  let cats = [];
  let langs = [];
  let lang = settings.categoryLanguage || '';
  let active = params.category || (type === T.LIVE ? null : CAT_ALL);
  let activeEl = null;
  let items = [];
  let grid = null;
  let sort = (app.library && localStore(sortKey(type))) || 'DEFAULT';
  let loadToken = 0;

  const langBox = h('div.langs');
  const catList = h('div.cat-list.scroll-y');
  const sidebar = h('div.sidebar', null, langBox, catList);
  const headTitle = h('div.title');
  const sortBtn = type === T.LIVE ? null : iconButton('⇅', label(sort), pickSort);
  const head = h('div.content-head', null, headTitle, sortBtn);
  const gridBox = h('div.grid.scroll-y');
  const content = h('div.content', null, head, gridBox);
  const el = h('div.page', null,
    topbar(TITLES[type], [iconButton(ICON.search, null, () => go('search', { type }))].concat(type === T.LIVE ? [iconButton(ICON.epg, 'TV-Guide', () => go('epg'))] : [])),
    h('div.browse', null, sidebar, content),
    legend([['r', 'Favorit'], ['g', type === T.LIVE ? 'TV-Guide' : 'Sortieren'], ['y', 'Kategorie-Menü']]),
  );

  function label(s) { const o = SORTS.find((x) => x[0] === s); return o ? o[1] : ''; }

  // ---------------- Kategorien ----------------

  async function loadCategories() {
    clear(catList).appendChild(loading());
    try {
      cats = await app.source.categories(type);
    } catch (e) {
      clear(catList).appendChild(empty('Kategorien konnten nicht geladen werden: ' + e.message));
      return;
    }
    parental.register(app.profile.id, type, cats);
    langs = detectLanguages(cats);
    if (lang && langs.indexOf(lang) < 0) lang = '';
    renderLangs();
    renderCats();
    if (!active) {
      const first = visibleCats()[0];
      active = first ? first.id : CAT_ALL;
    }
    selectCategory(active, false);
    const target = catList.querySelector('.cat.active') || catList.querySelector('.cat');
    if (target) setFocus(target);
  }

  function renderLangs() {
    clear(langBox);
    if (langs.length < 2) return;
    const chip = (code, text) => {
      const c = focusable(h('div.chip' + (lang === code ? '.active' : ''), null, text), () => {
        lang = code;
        saveSettings({ categoryLanguage: code });
        renderLangs();
        renderCats();
        const b = langBox.querySelector('.chip.active');
        if (b) setFocus(b);
      });
      return c;
    };
    langBox.appendChild(chip('', 'Alle'));
    langs.slice(0, 11).forEach((l) => langBox.appendChild(chip(l, l)));
  }

  function visibleCats() {
    const hidden = categoryPrefs.hidden(scope);
    let l = cats.filter((c) => hidden.indexOf(c.id) < 0);
    if (lang) l = l.filter((c) => categoryLanguage(c.name) === lang || !categoryLanguage(c.name));
    return categoryPrefs.arrange(scope, l);
  }

  function renderCats() {
    clear(catList);
    const locked = parental.lockedIds(app.profile.id, type, cats);
    const pins = categoryPrefs.pinned(scope);
    const add = (id, name, badge) => {
      const c = focusable(h('div.cat' + (id === active ? '.active' : ''), null, h('div.name', null, name), badge ? h('div.badge', null, badge) : null),
        () => selectCategory(id, true), { navRight: () => (grid && grid.first()) || null });
      c._catId = id;
      if (id === active) activeEl = c;
      catList.appendChild(c);
    };
    add(CAT_FAV, '❤ Favoriten');
    add(CAT_RECENT, '🕘 Zuletzt gesehen');
    add(CAT_ALL, type === T.LIVE ? 'Alle Sender' : type === T.MOVIE ? 'Alle Filme' : 'Alle Serien');
    visibleCats().forEach((c) => add(c.id, lang ? stripLanguage(c.name) : c.name,
      [locked[c.id] ? ICON.lock : '', pins.indexOf(c.id) >= 0 ? ICON.pin : ''].join('')));
    const hiddenCount = categoryPrefs.hidden(scope).length;
    if (hiddenCount) {
      catList.appendChild(focusable(h('div.cat', null, h('div.name.muted', null, `Ausgeblendete (${hiddenCount})`)), showHidden));
    }
  }

  function selectCategory(id, moveFocus) {
    const cat = cats.find((c) => c.id === id);
    if (cat && parental.requiresPin(app.profile.id, type, cat)) {
      askPin(() => { renderCats(); selectCategory(id, moveFocus); });
      return;
    }
    active = id;
    Array.prototype.forEach.call(catList.querySelectorAll('.cat'), (c) => {
      c.classList.toggle('active', c._catId === id);
      if (c._catId === id) activeEl = c;
    });
    headTitle.textContent = id === CAT_FAV ? 'Favoriten' : id === CAT_RECENT ? 'Zuletzt gesehen' : id === CAT_ALL ? TITLES[type] : (cat ? (lang ? stripLanguage(cat.name) : cat.name) : '');
    loadItems(moveFocus);
  }

  async function loadItems(moveFocus) {
    const token = ++loadToken;
    clear(gridBox).appendChild(loading());
    grid = null;
    let list;
    try {
      const lib = app.library;
      if (active === CAT_FAV) list = lib.favorites.filter((i) => i.type === type);
      else if (active === CAT_RECENT) list = lib.history.filter((x) => x.item.type === type).map((x) => x.item);
      else list = await app.source.items(type, active === CAT_ALL ? null : active);
    } catch (e) {
      if (token === loadToken) clear(gridBox).appendChild(empty('Laden fehlgeschlagen: ' + e.message));
      return;
    }
    if (token !== loadToken) return;
    list = parental.visible(app.profile.id, list);
    if (active === CAT_ALL || active === CAT_FAV || active === CAT_RECENT) {
      const hidden = categoryPrefs.hidden(scope);
      if (hidden.length && active === CAT_ALL) list = list.filter((i) => hidden.indexOf(i.categoryId) < 0);
    }
    if (type !== T.LIVE && active !== CAT_RECENT) list = applySort(list, sort);
    items = list;
    if (!list.length) {
      clear(gridBox).appendChild(empty(active === CAT_FAV ? 'Noch keine Favoriten – rote Taste auf einem Eintrag fügt ihn hinzu.' : 'Keine Einträge'));
      return;
    }
    headTitle.textContent = headTitle.textContent.replace(/ · \d+$/, '') + ' · ' + list.length;
    grid = lazyGrid(gridBox, list, makeCard, { chunk: type === T.LIVE ? 45 : 50 });
    if (moveFocus && grid.first()) setFocus(grid.first());
  }

  const epgCache = {};
  const loadEpg = debounce((item, card) => {
    if (!app.source || !card._epgLine) return;
    const show = (l) => {
      const now = Date.now();
      const cur = l.find((p) => p.start <= now && p.end > now);
      card._epgLine.textContent = cur ? `${clock(cur.start)}  ${cur.title}` : '';
    };
    if (epgCache[item.id]) { show(epgCache[item.id]); return; }
    app.source.epg(item, false).then((l) => { epgCache[item.id] = l; show(l); });
  }, 350);

  function makeCard(item) {
    const card = itemCard(item, () => open(item), {
      extra: {
        navLeft: () => (isFirstColumn(card) ? activeEl || 'stop' : null),
        _item: item,
      },
    });
    if (type === T.LIVE) card._onFocus = () => loadEpg(item, card);
    return card;
  }

  function isFirstColumn(card) {
    const r = card.getBoundingClientRect();
    const g = gridBox.getBoundingClientRect();
    return r.left - g.left < 120;
  }

  function open(item) {
    if (type === T.LIVE) go('player', { item, list: items });
    else if (type === T.SERIES && !app.source.supportsDetails) go('player', { item, list: items });
    else go('detail', { item });
  }

  // ---------------- Menues ----------------

  function pickSort() {
    if (type === T.LIVE) return;
    choose('Sortieren', SORTS.map(([k, t]) => ({ label: t, active: k === sort, onSelect: () => {
      sort = k;
      localStore(sortKey(type), k);
      if (sortBtn) sortBtn.querySelector('span:last-child').textContent = label(k);
      loadItems(false);
    } })));
  }

  function categoryMenu(catEl) {
    const id = catEl._catId;
    const cat = cats.find((c) => c.id === id);
    if (!cat) { toast('Für diese Kategorie gibt es kein Menü'); return; }
    const pinned = categoryPrefs.pinned(scope).indexOf(id) >= 0;
    const locked = parental.data.locked.indexOf(parental.key(app.profile.id, type, id)) >= 0;
    choose(cat.name, [
      { label: pinned ? 'Nicht mehr oben anheften' : 'Oben anheften', onSelect: () => { categoryPrefs.setPinned(scope, id, !pinned); renderCats(); refocusCat(id); } },
      { label: 'Kategorie ausblenden', onSelect: () => { categoryPrefs.setHidden(scope, id, true); renderCats(); focusFirstCat(); } },
      { label: locked ? 'Kindersicherung aufheben' : 'Mit Kindersicherung sperren', onSelect: () => {
        const run = () => { parental.setLocked(app.profile.id, type, id, !locked); renderCats(); refocusCat(id); toast(locked ? 'Sperre aufgehoben' : 'Kategorie gesperrt'); };
        if (!parental.hasPin()) { toast('Bitte zuerst in den Einstellungen eine PIN festlegen'); return; }
        if (parental.unlocked || !parental.isOn()) run(); else askPin(run);
      } },
    ]);
  }

  function showHidden() {
    const hidden = categoryPrefs.hidden(scope);
    choose('Ausgeblendete Kategorien (OK = wieder anzeigen)', hidden.map((id) => {
      const c = cats.find((x) => x.id === id);
      return { label: c ? c.name : id, onSelect: () => { categoryPrefs.setHidden(scope, id, false); renderCats(); refocusCat(id); } };
    }));
  }

  function refocusCat(id) {
    const c = Array.prototype.find.call(catList.querySelectorAll('.cat'), (x) => x._catId === id);
    if (c) setFocus(c); else focusFirstCat();
  }
  function focusFirstCat() { const c = catList.querySelector('.cat'); if (c) setFocus(c); }

  function toggleFavorite(card) {
    const item = card._item;
    if (!item) return;
    const fav = app.library.toggleFavorite(item);
    toast(fav ? `„${item.name}“ zu Favoriten hinzugefügt` : `„${item.name}“ aus Favoriten entfernt`);
    if (item.type === T.LIVE) {
      const n = card.querySelector('.n');
      const dot = n.querySelector('.fav-dot');
      if (fav && !dot) n.appendChild(h('span.fav-dot', null, ' ❤'));
      if (!fav && dot) n.removeChild(dot);
    } else {
      const img = card.querySelector('.img');
      const tag = img.querySelector('.tag.fav');
      if (fav && !tag) img.appendChild(h('div.tag.fav', null, '❤'));
      if (!fav && tag) img.removeChild(tag);
    }
    if (active === CAT_FAV && !fav) loadItems(false);
  }

  loadCategories();

  return {
    el,
    onShow(fresh) {
      if (!fresh && (active === CAT_FAV || active === CAT_RECENT)) loadItems(false);
    },
    onKey(code) {
      const f = getFocus();
      if (code === KEY.RED && f && f._item) { toggleFavorite(f); return true; }
      if (code === KEY.YELLOW && f && f._catId) { categoryMenu(f); return true; }
      if (code === KEY.YELLOW) { const a = catList.querySelector('.cat.active'); if (a) { setFocus(a); categoryMenu(a); } return true; }
      if (code === KEY.GREEN) { if (type === T.LIVE) go('epg'); else pickSort(); return true; }
      return false;
    },
  };
}

function localStore(k, v) {
  try {
    if (v === undefined) return localStorage.getItem('portiva.' + k);
    localStorage.setItem('portiva.' + k, v);
  } catch (e) { /* egal */ }
  return null;
}
