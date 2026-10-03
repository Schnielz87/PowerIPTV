// Startseite wie Android: grosse Kacheln (Live TV, Filme, Serien), Weiterschauen, zuletzt gesehene Sender,
// kleine Kacheln fuer alle weiteren Bereiche, Benutzer-Maennchen zum schnellen Wechseln.
import { h, clear, dateDE, toast, storage } from '../util';
import { focusable } from '../focus';
import { app, go, choose, activateProfile, askPin } from '../app';
import { profiles, parental } from '../store';
import { T } from '../sources';
import { updates } from '../update';
import { topbar, iconButton, imgDiv, ICON } from './common';

export default function home() {
  const accountLine = h('span.account-line');
  const userBtn = iconButton(ICON.user, app.profile ? app.profile.name : '', quickSwitch);
  const bar = topbar('', [
    iconButton(ICON.search, null, () => go('search')),
    iconButton(ICON.sync, null, refreshPlaylist),
    userBtn,
  ], { noBack: true });
  bar.insertBefore(accountLine, bar.querySelector('.spacer'));

  const big = h('div.big-tiles', null,
    bigTile('LIVE TV', 'Sender & TV-Guide', 'assets/live_tv_collage.webp', T.LIVE),
    bigTile('FILME', 'Filme & Neuheiten', 'assets/movies_collage.webp', T.MOVIE),
    bigTile('SERIEN', 'Serien & Staffeln', 'assets/series_collage.webp', T.SERIES),
  );
  const rows = h('div');
  const updateTile = smallTile(ICON.update, 'Update', () => go('update'));
  const tiles = h('div.small-tiles', null,
    smallTile(ICON.search, 'Suche', () => go('search')),
    smallTile(ICON.sync, 'Playlist aktualisieren', refreshPlaylist),
    smallTile(ICON.epg, 'TV-Guide (EPG)', () => go('epg')),
    smallTile(ICON.ai, 'KI-Empfehlungen', () => go('ai')),
    smallTile(ICON.fav, 'Favoriten & Verlauf', () => go('favorites')),
    smallTile(ICON.user, 'Benutzer wechseln', () => go('profiles')),
    smallTile(ICON.settings, 'Einstellungen', openSettings),
    updateTile,
  );
  const body = h('div.home-body.scroll-y.grow', null, big, rows, h('div.section-title', null, 'Mehr'), tiles);
  const el = h('div.page', null, bar, body);

  function bigTile(title, sub, image, type) {
    const t = h('div.big-tile' + (type === T.LIVE ? '.autofocus' : ''), { style: { backgroundImage: `url("${image}")` } }, h('div.t', null, title), h('div.s', null, sub));
    return focusable(t, () => go('browse', { type }));
  }

  function renderRows() {
    clear(rows);
    const lib = app.library;
    if (!lib) return;
    const cont = lib.continueWatching().filter((x) => !parental.isItemBlocked(app.profile.id, x.item)).slice(0, 15);
    if (cont.length) {
      rows.appendChild(h('div.section-title', null, 'Weiterschauen'));
      rows.appendChild(h('div.hrow.scroll-x', null, cont.map((e) => wideCard(e))));
    }
    const live = lib.history.filter((x) => x.item.type === T.LIVE && !parental.isItemBlocked(app.profile.id, x.item)).slice(0, 15);
    if (live.length) {
      rows.appendChild(h('div.section-title', null, 'Zuletzt gesehene Sender'));
      rows.appendChild(h('div.hrow.scroll-x', null, live.map((e) => wideCard(e))));
    }
  }

  function wideCard(e) {
    const img = imgDiv('img', e.episode && e.episode.image ? e.episode.image : e.item.logo);
    if (e.item.type === T.LIVE) img.style.backgroundSize = 'contain';
    if (e.duration > 0 && e.item.type !== T.LIVE) img.appendChild(h('div.bar', { style: { width: Math.round(Math.min(1, e.position / e.duration) * 100) + '%' } }));
    const title = e.episode ? `${e.item.name} · S${e.episode.season} E${e.episode.episodeNum}` : e.item.name;
    return focusable(h('div.wide-card', null, img, h('div.n.ellipsis', null, title)), () => {
      if (e.item.type === T.LIVE) go('player', { item: e.item });
      else if (e.episode) go('player', { item: e.item, episode: e.episode, startAt: e.position });
      else go('player', { item: e.item, startAt: e.position });
    });
  }

  function refreshAccount() {
    const a = app.account;
    if (!a) { accountLine.textContent = ''; return; }
    const parts = [];
    if (a.expiresAt) parts.push('Gültig bis ' + dateDE(a.expiresAt));
    if (a.maxConnections) parts.push(`${a.maxConnections} ${a.maxConnections === '1' ? 'Verbindung' : 'Verbindungen'}`);
    accountLine.textContent = parts.join(' · ');
  }

  function refreshUpdate() {
    const av = updates.available;
    updateTile.querySelector('.l').textContent = av ? 'Update verfügbar!' : 'Update';
    updateTile.classList.toggle('highlight', !!av);
  }

  function quickSwitch() {
    const all = profiles.list();
    choose('Benutzer wechseln', all.map((p) => ({
      label: p.name, active: app.profile && app.profile.id === p.id,
      onSelect: () => {
        if (app.profile && app.profile.id === p.id) return;
        activateProfile(p);
        parental.unlocked = false;
        go('home', {}, { replace: true });
        toast(`Benutzer: ${p.name}`);
      },
    })).concat([{ label: '＋ Zugang hinzufügen', onSelect: () => go('profileEdit', {}) }]));
  }

  return {
    el,
    refresh() { refreshAccount(); },
    onShow() {
      userBtn.querySelector('span:last-child').textContent = app.profile ? app.profile.name : '';
      renderRows();
      refreshAccount();
      refreshUpdate();
      autoRefreshPlaylist();
      updates.autoCheck().then(refreshUpdate);
    },
  };
}

function smallTile(icon, label, onEnter) {
  return focusable(h('div.small-tile', null, h('div.ico', null, icon), h('div.l', null, label)), onEnter);
}

export function openSettings() {
  if (parental.settingsNeedPin()) askPin(() => go('settings'));
  else go('settings');
}

function refreshPlaylist() {
  if (!app.source) return;
  app.source.clearCache();
  storage.set('playlist.refreshed', Date.now());
  toast('Playlist wird neu geladen …');
  ['LIVE', 'MOVIE', 'SERIES'].forEach((t) => app.source.categories(t).catch(() => {}));
  app.source.items(T.LIVE).then((l) => toast(`Playlist aktualisiert · ${l.length} Sender`)).catch((e) => toast('Aktualisieren fehlgeschlagen: ' + e.message));
}

/** Wie Android: Playlist alle 24 Stunden automatisch frisch laden. */
function autoRefreshPlaylist() {
  const last = storage.get('playlist.refreshed', 0);
  if (Date.now() - last > 24 * 3600000) {
    storage.set('playlist.refreshed', Date.now());
    if (last && app.source) app.source.clearCache();
  }
}
