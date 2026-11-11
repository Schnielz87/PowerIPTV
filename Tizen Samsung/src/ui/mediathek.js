// Mediatheken von ARD, ZDF, Arte & Co. (wie Android/Windows/iPhone) – ueber die freie MediathekView-Datenbank,
// unabhaengig vom IPTV-Zugang. Links die Sender, rechts Suche und Sendungen (neueste zuerst).
import { h, clear, debounce } from '../util';
import { setFocus, focusable } from '../focus';
import { go } from '../app';
import { T } from '../sources';
import { postJson } from '../net';
import { topbar, field, loading, empty, button } from './common';

const CHANNELS = [
  ['Alle Sender', ''], ['ARD', 'ARD'], ['ZDF', 'ZDF'], ['Arte', 'ARTE.DE'], ['3sat', '3Sat'], ['ZDFneo', 'ZDFneo'],
  ['ZDFinfo', 'ZDFinfo'], ['Phoenix', 'PHOENIX'], ['KiKA', 'KiKA'], ['ZDFtivi', 'ZDF-tivi'], ['BR', 'BR'], ['WDR', 'WDR'],
  ['NDR', 'NDR'], ['SWR', 'SWR'], ['MDR', 'MDR'], ['HR', 'HR'], ['RBB', 'RBB'], ['SR', 'SR'], ['Radio Bremen', 'Radio Bremen TV'],
  ['ORF', 'ORF'], ['SRF', 'SRF'], ['DW', 'DW'],
];
const SPECIAL = /Audiodeskription|Gebärdensprache|Hörfassung|\(mit Untertitel\)|klare Sprache/i;
const PAGE = 60;
const WD = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];

async function search(query, channel, offset) {
  const queries = [];
  if (channel) queries.push({ fields: ['channel'], query: channel });
  if (query) queries.push({ fields: ['title', 'topic'], query });
  const r = await postJson('https://mediathekviewweb.de/api/query', {
    queries, sortBy: 'timestamp', sortOrder: 'desc', future: false, offset, size: PAGE, duration_min: 180,
  }, {}, 20000);
  if (r.status !== 200) throw new Error('Mediathek nicht erreichbar (' + r.status + ')');
  const res = ((JSON.parse(r.body) || {}).result || {}).results || [];
  const seen = {};
  return res.filter((o) => o && o.url_video && !SPECIAL.test(o.title || '')).filter((o) => {
    const k = `${o.channel}|${o.title}|${Math.floor((o.duration || 0) / 60)}`;
    if (seen[k]) return false;
    seen[k] = 1;
    return true;
  });
}

function pad(n) { return (n < 10 ? '0' : '') + n; }
function when(ts) {
  if (!ts) return '';
  const d = new Date(ts * 1000);
  return `${WD[d.getDay()]} ${pad(d.getDate())}.${pad(d.getMonth() + 1)}. ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export default function mediathek() {
  let channel = '';
  let results = [];
  let token = 0;
  const q = field(null, '', { placeholder: 'Sendung oder Thema suchen (OK öffnet die Tastatur)', onDone: () => load() });
  q.input.addEventListener('input', debounce(() => load(), 800));
  const catList = h('div.cat-list.scroll-y');
  const sidebar = h('div.sidebar', null, catList);
  const list = h('div.list.scroll-y', { style: { padding: '0 0 60px' } });
  const content = h('div.content', null, h('div', { style: { padding: '0 40px 10px 0' } }, q.el), list);
  const el = h('div.page', null, topbar('Mediathek – ARD, ZDF, Arte & Co.'), h('div.browse', null, sidebar, content));

  function renderChannels() {
    clear(catList);
    CHANNELS.forEach(([label, query]) => {
      const c = focusable(h('div.cat' + (query === channel ? '.active' : ''), null, h('div.name', null, label)), () => {
        channel = query; renderChannels(); load();
        const a = catList.querySelector('.cat.active'); if (a) setFocus(a);
      });
      catList.appendChild(c);
    });
  }

  function play(o) {
    const title = o.topic && (o.title || '').toLowerCase().indexOf(o.topic.toLowerCase()) < 0 ? `${o.topic}: ${o.title}` : o.title;
    const url = o.url_video_hd || o.url_video;
    go('player', { item: { id: 'mediathek:' + (o.id || url), name: title, type: T.MOVIE, url, mediathek: true } });
  }

  function row(o) {
    const meta = [when(o.timestamp), o.duration ? Math.ceil(o.duration / 60) + ' Min.' : '', o.url_video_hd ? 'HD' : ''].filter(Boolean).join('  ·  ');
    return focusable(h('div.mt-row', null,
      h('div.mt-head', null, h('span.mt-ch', null, o.channel || ''), h('span.mt-topic', null, o.topic || '')),
      h('div.mt-title', null, o.title || ''),
      h('div.mt-meta', null, meta),
      o.description ? h('div.mt-desc', null, o.description) : null), () => play(o));
  }

  function render(more) {
    clear(list);
    if (!results.length) { list.appendChild(empty(q.input.value.trim() ? `Keine Treffer für „${q.input.value.trim()}“` : 'Keine Sendungen gefunden')); return; }
    results.forEach((o) => list.appendChild(row(o)));
    if (more) list.appendChild(button('Weitere Sendungen laden', loadMore, '.mt-more'));
  }

  async function load() {
    const my = ++token;
    clear(list);
    list.appendChild(loading('Mediathek wird geladen …'));
    try {
      const r = await search(q.input.value.trim(), channel, 0);
      if (my !== token) return;
      results = r;
      render(r.length >= PAGE / 2);
    } catch (e) {
      if (my !== token) return;
      clear(list);
      list.appendChild(empty((e && e.message) || 'Mediathek nicht erreichbar'));
    }
  }

  async function loadMore() {
    const my = token;
    try {
      const r = await search(q.input.value.trim(), channel, results.length);
      if (my !== token) return;
      const known = {};
      results.forEach((o) => { known[o.id] = 1; });
      const add = r.filter((o) => !known[o.id]);
      const first = results.length;
      results = results.concat(add);
      render(add.length > 0);
      const rows = list.querySelectorAll('.mt-row');
      if (rows[first]) setFocus(rows[first]);
    } catch (e) { /* bleibt wie es ist */ }
  }

  renderChannels();
  load();
  return {
    el,
    onShow(fresh) { if (fresh) { const a = catList.querySelector('.cat.active'); if (a) setFocus(a); } },
  };
}
