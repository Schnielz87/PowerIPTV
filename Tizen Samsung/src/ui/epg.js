// TV-Guide: links Sender (Kategorie waehlbar), rechts das Programm des Senders.
// OK auf Sendung: laeuft -> einschalten, vorbei + Archiv -> Catch-up, Zukunft -> Erinnerung.
import { h, clear, toast, clock, weekday, dateDE, debounce } from '../util';
import { focusable, setFocus } from '../focus';
import { KEY } from '../keys';
import { app, go, choose } from '../app';
import { settings, categoryPrefs, parental } from '../store';
import { T } from '../sources';
import { CAT_FAV, CAT_ALL, categoryLanguage, stripLanguage } from '../rules';
import { reminders } from '../reminders';
import { topbar, iconButton, lazyGrid, loading, empty, imgDiv, legend } from './common';

export default function epg() {
  let cats = [];
  let catId = null;
  let channels = [];
  let selected = null;
  let grid = null;
  let progToken = 0;
  const catBtn = iconButton('☰', 'Kategorie', pickCategory);
  const chBox = h('div.epg-channels.scroll-y');
  const progBox = h('div.epg-progs.scroll-y');
  const el = h('div.page', null,
    topbar('TV-Guide', [catBtn]),
    h('div.epg', null, chBox, progBox),
    legend([['r', 'Favorit'], ['y', 'Kategorie']]),
  );

  async function init() {
    clear(chBox).appendChild(loading());
    try { cats = await app.source.categories(T.LIVE); } catch (e) { cats = []; }
    parental.register(app.profile.id, T.LIVE, cats);
    const scope = `${app.profile.id}|LIVE`;
    const hidden = categoryPrefs.hidden(scope);
    const locked = parental.lockedIds(app.profile.id, T.LIVE, cats);
    cats = categoryPrefs.arrange(scope, cats.filter((c) => hidden.indexOf(c.id) < 0 && !locked[c.id]));
    if (settings.categoryLanguage) {
      const l = cats.filter((c) => categoryLanguage(c.name) === settings.categoryLanguage);
      if (l.length) cats = l;
    }
    const fav = app.library.favorites.some((f) => f.type === T.LIVE);
    selectCategory(fav ? CAT_FAV : (cats[0] ? cats[0].id : CAT_ALL));
  }

  function catName(id) {
    if (id === CAT_FAV) return 'Favoriten';
    if (id === CAT_ALL) return 'Alle Sender';
    const c = cats.find((x) => x.id === id);
    return c ? stripLanguage(c.name) : '';
  }

  function pickCategory() {
    const opts = [{ id: CAT_FAV, name: '❤ Favoriten' }, { id: CAT_ALL, name: 'Alle Sender' }].concat(cats.map((c) => ({ id: c.id, name: c.name })));
    choose('Kategorie', opts.map((c) => ({ label: c.name, active: c.id === catId, onSelect: () => selectCategory(c.id) })));
  }

  async function selectCategory(id) {
    catId = id;
    catBtn.querySelector('span:last-child').textContent = catName(id);
    clear(chBox).appendChild(loading());
    clear(progBox);
    try {
      channels = id === CAT_FAV ? app.library.favorites.filter((f) => f.type === T.LIVE) : await app.source.items(T.LIVE, id === CAT_ALL ? null : id);
    } catch (e) { channels = []; }
    channels = parental.visible(app.profile.id, channels);
    if (!channels.length) { clear(chBox).appendChild(empty('Keine Sender')); return; }
    grid = lazyGrid(chBox, channels, (it) => {
      const row = focusable(h('div.epg-ch', null, imgDiv('logo', it.logo), h('div.n', null, (it.number ? it.number + '  ' : '') + it.name + (it.archiveDays ? '  ↺' : ''))),
        () => go('player', { item: it, list: channels }), { _item: it, navRight: () => progBox.querySelector('.prog.now') || progBox.querySelector('.prog') || 'stop' });
      row.style.width = '540px';
      row._onFocus = () => showChannel(it, row);
      return row;
    }, { chunk: 40 });
    if (grid.first()) setFocus(grid.first());
  }

  const showChannel = debounce((it, row) => {
    if (selected === it) return;
    selected = it;
    Array.prototype.forEach.call(chBox.querySelectorAll('.epg-ch.active'), (r) => r.classList.remove('active'));
    row.classList.add('active');
    loadProgramme(it, row);
  }, 300);

  async function loadProgramme(it, row) {
    const token = ++progToken;
    clear(progBox).appendChild(loading());
    const list = await app.source.epg(it, true);
    if (token !== progToken) return;
    clear(progBox);
    if (!list.length) { progBox.appendChild(empty('Kein Programm für diesen Sender')); return; }
    const now = Date.now();
    const from = now - (it.archiveDays ? it.archiveDays * 86400000 : 3 * 3600000);
    let lastDay = null;
    let nowEl = null;
    list.filter((p) => p.end > from).forEach((p) => {
      const day = dateDE(p.start);
      if (day !== lastDay) { lastDay = day; progBox.appendChild(h('div.day-sep', null, `${weekday(p.start)}, ${day}`)); }
      const live = p.start <= now && p.end > now;
      const past = p.end <= now;
      const catchup = (past || live) && p.archive !== false && app.source.catchupUrl(it, p.start, p.end);
      const flag = live ? h('span.flag.danger', null, '● LIVE')
        : past ? (catchup ? h('span.flag.cyan', null, '↺ Catch-up') : null)
        : (reminders.has(it, p.start) ? h('span.flag.success', null, '🔔') : null);
      const r = focusable(h('div.prog' + (live ? '.now' : past ? '.past' : ''), null,
        h('div.time', null, `${clock(p.start)} – ${clock(p.end)}`),
        h('div.main', null, h('div.t', null, p.title, flag), p.description ? h('div.d', null, p.description) : null)),
      () => onProgramme(it, p, live, past, catchup), { navLeft: () => row });
      if (live) nowEl = r;
      progBox.appendChild(r);
    });
    progBox.scrollTop = 0;
    if (nowEl) progBox.scrollTop = Math.max(0, nowEl.offsetTop - 120);
  }

  function onProgramme(it, p, live, past, catchup) {
    if (live) {
      if (catchup) {
        choose(p.title, [
          { label: '● Live ansehen', onSelect: () => go('player', { item: it, list: channels }) },
          { label: '↺ Von Beginn an', onSelect: () => go('player', { item: it, catchup: { url: catchup, title: p.title, start: p.start } }) },
        ]);
      } else go('player', { item: it, list: channels });
      return;
    }
    if (past) {
      if (catchup) go('player', { item: it, catchup: { url: catchup, title: p.title, start: p.start } });
      else toast('Für diese Sendung gibt es keine Aufzeichnung');
      return;
    }
    const on = reminders.toggle(app.profile.id, it, p.title, p.start);
    toast(on ? `🔔 Erinnerung um ${clock(p.start)} (Portiva muss geöffnet sein)` : 'Erinnerung gelöscht');
    const s = selected; selected = null;
    const row = Array.prototype.find.call(chBox.querySelectorAll('.epg-ch'), (r) => r._item === s);
    if (row) { selected = s; loadProgramme(s, row).then(() => {
      const again = Array.prototype.find.call(progBox.querySelectorAll('.prog'), (x) => x.textContent.indexOf(clock(p.start)) === 0);
      if (again) setFocus(again);
    }); }
  }

  init();

  return {
    el,
    onKey(code) {
      if (code === KEY.YELLOW) { pickCategory(); return true; }
      if (code === KEY.RED && selected) {
        const f = app.library.toggleFavorite(selected);
        toast(f ? `„${selected.name}“ zu Favoriten hinzugefügt` : `„${selected.name}“ aus Favoriten entfernt`);
        return true;
      }
      return false;
    },
  };
}
