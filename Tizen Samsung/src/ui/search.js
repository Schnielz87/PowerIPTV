// Suche ueber Sender, Filme und Serien (alle Woerter muessen vorkommen – wie Android).
import { h, clear, debounce } from '../util';
import { setFocus } from '../focus';
import { app, go } from '../app';
import { parental } from '../store';
import { T } from '../sources';
import { matchesQuery } from '../rules';
import { topbar, field, itemCard, loading, empty, button } from './common';

const LABEL = { LIVE: 'Sender', MOVIE: 'Filme', SERIES: 'Serien' };

export default function search(params = {}) {
  let only = params.type || null;
  const q = field(null, '', { placeholder: 'Suchbegriff eingeben (OK öffnet die Tastatur)', onDone: () => run() });
  const filters = h('div.type-toggle');
  const results = h('div.list.scroll-y', { style: { padding: '0 60px 60px' } });
  const el = h('div.page', null, topbar('Suche'), h('div', { style: { padding: '0 60px' } }, q.el, filters), results);
  q.input.classList.add('autofocus');
  q.input.addEventListener('input', debounce(() => run(), 700));

  function renderFilters() {
    clear(filters);
    [[null, 'Alles'], [T.LIVE, 'Sender'], [T.MOVIE, 'Filme'], [T.SERIES, 'Serien']].forEach(([t, l]) => {
      const b = button(l, () => { only = t; renderFilters(); run(); setFocus(filters.querySelector('.active') || b); });
      b.className = 'chip focusable' + (only === t ? ' active' : '');
      filters.appendChild(b);
    });
  }

  let token = 0;
  async function run() {
    const text = q.input.value.trim();
    const my = ++token;
    clear(results);
    if (text.length < 2) { results.appendChild(empty('Mindestens 2 Zeichen eingeben')); return; }
    results.appendChild(loading('Suche läuft … (beim ersten Mal wird der Katalog geladen)'));
    const types = only ? [only] : [T.LIVE, T.MOVIE, T.SERIES];
    const found = {};
    for (const t of types) {
      try {
        const all = await app.source.items(t);
        found[t] = parental.visible(app.profile.id, all.filter((i) => matchesQuery(i.name, text))).slice(0, 60);
      } catch (e) { found[t] = []; }
      if (my !== token) return;
    }
    clear(results);
    let any = false;
    types.forEach((t) => {
      if (!found[t].length) return;
      any = true;
      results.appendChild(h('div.section-title', null, `${LABEL[t]} (${found[t].length}${found[t].length === 60 ? '+' : ''})`));
      const g = h('div.grid-inner');
      found[t].forEach((i) => g.appendChild(itemCard(i, () => open(i, found[t]))));
      results.appendChild(g);
    });
    if (!any) results.appendChild(empty(`Nichts gefunden für „${text}“`));
  }

  function open(i, list) {
    if (i.type === T.LIVE) go('player', { item: i, list });
    else if (i.type === T.SERIES && !app.source.supportsDetails) go('player', { item: i });
    else go('detail', { item: i });
  }

  renderFilters();
  return { el };
}
