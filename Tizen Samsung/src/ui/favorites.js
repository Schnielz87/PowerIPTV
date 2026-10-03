// Favoriten und Verlauf (alle Bereiche). Rote Taste entfernt den Eintrag.
import { h, clear, toast } from '../util';
import { getFocus, setFocus } from '../focus';
import { KEY } from '../keys';
import { app, go } from '../app';
import { parental } from '../store';
import { T } from '../sources';
import { topbar, itemCard, empty, legend } from './common';

const LABEL = { LIVE: 'Sender', MOVIE: 'Filme', SERIES: 'Serien' };

export default function favorites() {
  const body = h('div.list.scroll-y', { style: { padding: '0 60px 80px' } });
  const el = h('div.page', null, topbar('Favoriten & Verlauf'), body, legend([['r', 'Entfernen']]));

  function render() {
    clear(body);
    const lib = app.library;
    const pid = app.profile.id;
    const favs = parental.visible(pid, lib.favorites);
    let any = false;
    [T.LIVE, T.MOVIE, T.SERIES].forEach((t) => {
      const l = favs.filter((f) => f.type === t);
      if (!l.length) return;
      any = true;
      body.appendChild(h('div.section-title', null, `❤ ${LABEL[t]}`));
      const g = h('div.grid-inner');
      l.forEach((i) => g.appendChild(itemCard(i, () => open(i, l), { extra: { _item: i, _kind: 'fav' } })));
      body.appendChild(g);
    });
    const hist = lib.history.filter((x) => !parental.isItemBlocked(pid, x.item)).slice(0, 40);
    if (hist.length) {
      any = true;
      body.appendChild(h('div.section-title', null, '🕘 Zuletzt gesehen'));
      const g = h('div.grid-inner');
      hist.forEach((x) => g.appendChild(itemCard(x.item, () => {
        if (x.item.type === T.LIVE) go('player', { item: x.item });
        else if (x.episode) go('player', { item: x.item, episode: x.episode, startAt: x.position });
        else go('detail', { item: x.item });
      }, { progress: x.duration > 0 && x.item.type !== T.LIVE ? Math.min(1, x.position / x.duration) : null, extra: { _item: x.item, _kind: 'hist' } })));
      body.appendChild(g);
    }
    if (!any) body.appendChild(empty('Noch keine Favoriten. Rote Taste auf einem Sender, Film oder einer Serie fügt ihn hinzu.'));
  }

  function open(i, list) {
    if (i.type === T.LIVE) go('player', { item: i, list });
    else go('detail', { item: i });
  }

  render();
  return {
    el,
    onShow(fresh) { if (!fresh) render(); },
    onKey(code) {
      const f = getFocus();
      if (code === KEY.RED && f && f._item) {
        if (f._kind === 'fav') { app.library.toggleFavorite(f._item); toast('Aus Favoriten entfernt'); } else { app.library.removeHistory(f._item); toast('Aus dem Verlauf entfernt'); }
        const next = f.nextElementSibling || f.previousElementSibling;
        const idx = next && next._item;
        render();
        const again = idx && Array.prototype.find.call(body.querySelectorAll('.focusable'), (c) => c._item === idx);
        const first = body.querySelector('.focusable');
        if (again || first) setFocus(again || first);
        return true;
      }
      return false;
    },
  };
}
