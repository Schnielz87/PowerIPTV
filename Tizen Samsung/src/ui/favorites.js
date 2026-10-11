// Favoriten, eigene Listen und Verlauf (alle Bereiche). Rote Taste entfernt den Eintrag.
import { h, clear, toast } from '../util';
import { getFocus, setFocus } from '../focus';
import { KEY } from '../keys';
import { app, go, choose, dialog, openItemMenu } from '../app';
import { parental } from '../store';
import { T } from '../sources';
import { topbar, itemCard, empty, legend } from './common';
import { askName } from './lists';

const LABEL = { LIVE: 'Sender', MOVIE: 'Filme', SERIES: 'Serien' };

export default function favorites() {
  const body = h('div.list.scroll-y', { style: { padding: '0 60px 80px' } });
  const el = h('div.page', null, topbar('Favoriten & Listen'), body,
    legend([['r', 'Entfernen'], ['g', 'Neue Liste'], ['y', 'Liste umbenennen/löschen'], ['b', 'Zu Liste (oder OK lange drücken)']]));

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
    // Eigene Listen (Sender, Filme und Serien gemischt – Sender zuerst)
    lib.lists.forEach((list) => {
      any = true;
      const items = parental.visible(pid, list.items);
      body.appendChild(h('div.section-title', null, `☰ ${list.name} (${items.length})`));
      if (!items.length) {
        body.appendChild(h('p.muted', { style: { margin: '0 0 20px 6px' } }, 'Noch leer – OK auf einem Sender, Film oder einer Serie lange drücken (oder blaue Taste) → „Zu Liste hinzufügen“.'));
        return;
      }
      [T.LIVE, T.MOVIE, T.SERIES].forEach((t) => {
        const l = items.filter((i) => i.type === t);
        if (!l.length) return;
        const g = h('div.grid-inner');
        l.forEach((i) => g.appendChild(itemCard(i, () => open(i, l), { extra: { _item: i, _kind: 'list', _listId: list.id } })));
        body.appendChild(g);
      });
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
    if (!any) body.appendChild(empty('Noch keine Favoriten. Rote Taste auf einem Sender, Film oder einer Serie fügt ihn hinzu – grüne Taste legt eine eigene Liste an.'));
  }

  function open(i, list) {
    if (i.type === T.LIVE) go('player', { item: i, list });
    else go('detail', { item: i });
  }

  /** Neu zeichnen und den Fokus moeglichst auf dem gleichen (oder naechsten) Eintrag halten. */
  function rerender(item) {
    const f = getFocus();
    const lid = f && f._listId;
    const next = f && (f.nextElementSibling || f.previousElementSibling);
    render();
    const cards = body.querySelectorAll('.focusable');
    const key = (it) => app.library.key(it);
    const find = (it, l) => it && Array.prototype.find.call(cards, (c) => c._item && key(c._item) === key(it) && (!l || c._listId === l));
    const target = find(item, lid) || (next && find(next._item, next._listId)) || cards[0];
    if (target) setFocus(target);
  }

  function listMenu(listId) {
    const lib = app.library;
    const list = lib.lists.find((l) => l.id === listId);
    if (!list) return;
    choose(`Liste „${list.name}“`, [
      { label: 'Umbenennen', onSelect: () => askName('Liste umbenennen', list.name, (name) => { lib.renameList(list.id, name); rerender(); }) },
      { label: 'Liste löschen', onSelect: () => dialog({
        title: 'Liste löschen?',
        text: `„${list.name}“ wird gelöscht. Die Sender, Filme und Serien selbst bleiben erhalten.`,
        buttons: [
          { label: 'Löschen', primary: true, onClick: () => { lib.deleteList(list.id); toast('Liste gelöscht'); rerender(); } },
          { label: 'Abbrechen' },
        ],
      }) },
    ]);
  }

  render();
  return {
    el,
    onShow(fresh) { if (!fresh) render(); },
    onItemChanged(item) { rerender(item); },
    onKey(code) {
      const f = getFocus();
      if (code === KEY.GREEN) {
        askName('Neue Liste', '', (name) => { app.library.createList(name); toast(`Liste „${name}“ angelegt`); rerender(); });
        return true;
      }
      if (code === KEY.YELLOW) {
        if (f && f._listId) listMenu(f._listId);
        else if (app.library.lists.length) {
          choose('Welche Liste?', app.library.lists.map((l) => ({ label: l.name, onSelect: () => listMenu(l.id) })));
        } else toast('Noch keine eigene Liste – grüne Taste legt eine an');
        return true;
      }
      if (code === KEY.BLUE && f && f._item) { openItemMenu(f); return true; }
      if (code === KEY.RED && f && f._item) {
        if (f._kind === 'fav') { app.library.toggleFavorite(f._item); toast('Aus Favoriten entfernt'); } else if (f._kind === 'list') {
          app.library.toggleInList(f._listId, f._item); toast('Aus der Liste entfernt');
        } else { app.library.removeHistory(f._item); toast('Aus dem Verlauf entfernt'); }
        rerender(null);
        return true;
      }
      return false;
    },
  };
}
