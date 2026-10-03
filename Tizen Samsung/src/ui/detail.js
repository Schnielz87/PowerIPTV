// Detailseite fuer Filme und Serien: Cover, Handlung, FSK, Fortsetzen, Staffeln + Folgen.
import { h, clear, toast, formatTime } from '../util';
import { focusable, setFocus, getFocus } from '../focus';
import { KEY } from '../keys';
import { app, go, choose } from '../app';
import { Library } from '../store';
import { T } from '../sources';
import { fskOf } from '../rules';
import { topbar, button, loading, empty, imgDiv } from './common';

export default function detail({ item }) {
  const lib = app.library;
  const isSeries = item.type === T.SERIES;
  const backdrop = imgDiv('backdrop', item.logo);
  const cover = imgDiv('detail-cover', item.logo);
  const meta = h('div.meta');
  const plot = h('div.plot', null, '');
  const buttons = h('div.buttons');
  const seasonsBox = h('div.seasons.scroll-x');
  const episodesBox = h('div.episodes.scroll-y');
  const info = h('div.detail-info', null, h('h2', null, item.name), meta, plot, buttons);
  const body = h('div.inner', null, topbar(isSeries ? 'Serie' : 'Film'), h('div.detail-top', null, cover, info), isSeries ? seasonsBox : null, isSeries ? episodesBox : null);
  const el = h('div.page.detail', null, backdrop, h('div.shade'), body);
  let data = null;
  let season = null;
  let favBtn = null;

  buttons.appendChild(loading());

  function renderMeta(d) {
    clear(meta);
    const fsk = fskOf(d && d.age);
    const year = item.year || (d && d.releaseDate && d.releaseDate.slice(0, 4));
    if (fsk != null) meta.appendChild(h('span.fsk', null, 'FSK ' + fsk));
    if (year) meta.appendChild(h('span', null, String(year)));
    const rating = (d && d.rating) || item.rating;
    if (rating) meta.appendChild(h('span', null, '★ ' + Math.round(rating * 10) / 10));
    if (d && d.duration) meta.appendChild(h('span', null, d.duration));
    if (d && d.genre) meta.appendChild(h('span', null, d.genre));
    if (isSeries && d && d.seasons) meta.appendChild(h('span', null, d.seasons.length + (d.seasons.length === 1 ? ' Staffel' : ' Staffeln')));
    plot.textContent = (d && d.plot) || '';
    if (d && d.cast) plot.appendChild(h('div.muted', { style: { marginTop: '10px' } }, 'Mit: ' + d.cast));
    if (d && d.cover) cover.style.backgroundImage = `url("${d.cover}")`;
    if (d && (d.backdrop || d.cover)) backdrop.style.backgroundImage = `url("${d.backdrop || d.cover}")`;
  }

  function favLabel() { return lib.isFavorite(item) ? '❤ Favorit' : '♡ Zu Favoriten'; }

  function renderButtons() {
    clear(buttons);
    favBtn = button(favLabel(), () => {
      const f = lib.toggleFavorite(item);
      favBtn.textContent = favLabel();
      toast(f ? 'Zu Favoriten hinzugefügt' : 'Aus Favoriten entfernt');
    });
    if (!isSeries) {
      const key = lib.key(item);
      const pos = lib.position(key);
      const ext = data && data.ext;
      const playItem = ext ? Object.assign({}, item, { ext }) : item;
      if (pos > 0) {
        buttons.appendChild(button(`▶ Fortsetzen (${formatTime(pos)})`, () => go('player', { item: playItem, startAt: pos }), '.primary.autofocus'));
        buttons.appendChild(button('Von vorne', () => go('player', { item: playItem, startAt: 0 })));
      } else {
        buttons.appendChild(button('▶ Abspielen', () => go('player', { item: playItem }), '.primary.autofocus'));
      }
      buttons.appendChild(favBtn);
      const watched = lib.isWatched(key);
      buttons.appendChild(button(watched ? 'Als ungesehen markieren' : 'Als gesehen markieren', () => { lib.setWatched(key, !watched); renderButtons(); }));
    } else {
      const next = nextEpisode();
      if (next) {
        const pos = lib.position(Library.episodeKey(next.id));
        buttons.appendChild(button(pos > 0 ? `▶ Fortsetzen S${next.season} E${next.episodeNum} (${formatTime(pos)})` : `▶ S${next.season} E${next.episodeNum} abspielen`,
          () => playEpisode(next, pos), '.primary.autofocus'));
      }
      buttons.appendChild(favBtn);
    }
    const f = buttons.querySelector('.autofocus');
    const g = getFocus();
    if (f && (!g || !document.body.contains(g) || g.closest('.buttons') || g.closest('.topbar'))) setFocus(f);
  }

  /** Erste nicht gesehene Folge (bzw. die angefangene). */
  function nextEpisode() {
    if (!data || !data.seasons || !data.seasons.length) return null;
    const all = [];
    data.seasons.forEach((s) => data.episodes[s].forEach((e) => all.push(e)));
    const started = lib.history.find((x) => x.item.type === T.SERIES && x.item.id === item.id && x.episode);
    if (started) {
      const e = all.find((x) => x.id === started.episode.id);
      if (e && !lib.isWatched(Library.episodeKey(e.id))) return e;
      if (e) { const i = all.indexOf(e); if (all[i + 1]) return all[i + 1]; }
    }
    return all.find((e) => !lib.isWatched(Library.episodeKey(e.id))) || all[0];
  }

  function renderSeasons() {
    clear(seasonsBox);
    data.seasons.forEach((s) => {
      const chip = focusable(h('div.chip' + (s === season ? '.active' : ''), null, `Staffel ${s}`), () => { season = s; renderSeasons(); renderEpisodes(); });
      chip._season = s;
      seasonsBox.appendChild(chip);
    });
  }

  function renderEpisodes() {
    clear(episodesBox);
    const eps = data.episodes[season] || [];
    if (!eps.length) { episodesBox.appendChild(empty('Keine Folgen')); return; }
    eps.forEach((e) => {
      const key = Library.episodeKey(e.id);
      const pos = lib.position(key);
      const img = imgDiv('img', e.image);
      if (lib.isWatched(key)) img.appendChild(h('div.tag.seen', null, '✓'));
      else if (pos > 0) img.appendChild(h('div.progress', { style: { width: '40%' } }));
      const card = focusable(h('div.episode', null, img, h('div.txt', null,
        h('div.t', null, `${e.episodeNum}. ${e.title}`),
        h('div.p', null, [e.duration, e.plot].filter(Boolean).join(' · ')))), () => playEpisode(e, pos), { _episode: e });
      episodesBox.appendChild(card);
    });
  }

  function playEpisode(e, pos) {
    go('player', { item, episode: e, startAt: pos || 0, series: data });
  }

  function episodeMenu(card) {
    const e = card._episode;
    const key = Library.episodeKey(e.id);
    const watched = lib.isWatched(key);
    choose(e.title, [
      { label: '▶ Von vorne abspielen', onSelect: () => playEpisode(e, 0) },
      { label: watched ? 'Als ungesehen markieren' : 'Als gesehen markieren', onSelect: () => { lib.setWatched(key, !watched); renderEpisodes(); renderButtons(); } },
      { label: 'Ganze Staffel als gesehen markieren', onSelect: () => { (data.episodes[season] || []).forEach((x) => lib.setWatched(Library.episodeKey(x.id), true)); renderEpisodes(); renderButtons(); } },
    ]);
  }

  async function load() {
    try {
      data = isSeries ? await app.source.seriesInfo(item) : await app.source.movieInfo(item);
    } catch (e) {
      data = null;
      toast('Details konnten nicht geladen werden');
    }
    renderMeta(data);
    if (isSeries) {
      if (!data || !data.seasons || !data.seasons.length) {
        clear(buttons);
        buttons.appendChild(button(lib.isFavorite(item) ? '❤ Favorit' : '♡ Zu Favoriten', () => { lib.toggleFavorite(item); }));
        episodesBox.appendChild(empty('Für diese Serie liefert der Anbieter keine Folgen.'));
        setFocus(buttons.querySelector('.focusable'));
        return;
      }
      const next = nextEpisode();
      season = next ? next.season : data.seasons[0];
      renderSeasons();
      renderEpisodes();
    }
    renderButtons();
  }
  load();

  return {
    el,
    onShow(fresh) {
      if (!fresh && data) {
        const f = getFocus();
        const epId = f && f._episode && f._episode.id;
        renderButtons();
        if (isSeries) {
          renderEpisodes();
          const again = epId && Array.prototype.find.call(episodesBox.querySelectorAll('.episode'), (c) => c._episode.id === epId);
          if (again) setFocus(again);
        }
      }
    },
    onKey(code) {
      const f = getFocus();
      if ((code === KEY.YELLOW || code === KEY.INFO) && f && f._episode) { episodeMenu(f); return true; }
      if (code === KEY.RED) { if (favBtn) favBtn._onEnter(); return true; }
      return false;
    },
  };
}
