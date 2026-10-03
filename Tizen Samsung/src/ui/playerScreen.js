// Player-Bildschirm fuer die Fernbedienung (AVPlay): Infoleiste mit EPG, Spulen, Tonspur/Untertitel/Bildformat,
// Senderliste + Umschalten (CH+/CH-, Zahlen), naechste Folge, Intro ueberspringen, Sleep-Timer.
import { h, clear, toast, formatTime, clock } from '../util';
import { focusable, setFocus, getFocus, pushScope, popScope, focusFirst } from '../focus';
import { KEY, isBack, isDigit } from '../keys';
import { app, back, go, choose, dialog } from '../app';
import { settings, saveSettings, Library } from '../store';
import { T } from '../sources';
import { EPISODE } from '../rules';
import { button, imgDiv } from './common';
import { sendToDevice } from './link';

const HIDE_AFTER = 5000;

export default function playerScreen(params) {
  const p = app.player;
  const lib = app.library;
  let cur = { item: params.item, episode: params.episode || null, catchup: params.catchup || null };
  let list = params.list || null;          // Senderliste zum Umschalten
  let series = params.series || null;      // Staffeln/Folgen fuer "naechste Folge"
  let overlayVisible = true;
  let hideTimer = null;
  let epgTimer = null;
  let saveTimer = null;
  let subTimer = null;
  let digits = '';
  let digitTimer = null;
  let panel = null;
  let nextShownFor = null;
  let nextCancelled = false;
  let countdownTimer = null;
  let introDone = false;
  let seekOrigin = null;
  let seekLearnTimer = null;
  let sleepTimer = null;
  let started = false;

  // ---------------- Aufbau ----------------
  const titleEl = h('div.p-title');
  const subEl = h('div.p-sub');
  const clockEl = h('div.p-clock', null, clock(Date.now()));
  const epgEl = h('div.p-epg');
  const fill = h('div.fill');
  const knob = h('div.knob');
  const bar = h('div.p-bar', null, fill, knob);
  const tNow = h('span');
  const tEnd = h('span');
  const times = h('div.p-times', null, tNow, h('div.spacer'), tEnd);
  const playBtn = button('⏸', () => { p.togglePause(); poke(); }, '.autofocus');
  const btnRow = h('div.p-buttons');
  const bottom = h('div.p-bottom', null, epgEl, bar, times, btnRow);
  const center = h('div.p-center');
  const subtitle = h('div.p-subtitle');
  const corner = h('div.p-corner');
  const numberEl = h('div.p-number');
  const el = h('div.player', null,
    h('div.shade-top'), h('div.shade-bottom'),
    subtitle,
    h('div.hud', null, titleEl, subEl, clockEl, bottom),
    center, corner, numberEl,
  );

  const isLive = () => cur.item.type === T.LIVE && !cur.catchup;
  const isEpisode = () => !!cur.episode;
  const posKey = () => (cur.episode ? Library.episodeKey(cur.episode.id) : lib.key(cur.item));

  function buildButtons() {
    clear(btnRow);
    if (!isLive()) btnRow.appendChild(button('⏪ 10 s', () => { seek(-10000); poke(); }));
    btnRow.appendChild(playBtn);
    if (!isLive()) btnRow.appendChild(button('10 s ⏩', () => { seek(10000); poke(); }));
    if (isLive() || cur.catchup) btnRow.appendChild(button('☰ Sender', openChannelList));
    if (cur.item.type === T.LIVE) btnRow.appendChild(button('📅 Programm', openProgramme));
    if (isEpisode()) {
      btnRow.appendChild(button('Nächste Folge ⏭', () => playNextEpisode(true)));
      btnRow.appendChild(button('Folgen', openEpisodes));
    }
    btnRow.appendChild(button('🔊 Ton', pickAudio));
    btnRow.appendChild(button('💬 Untertitel', pickSubtitle));
    btnRow.appendChild(button('▭ Bild', pickAspect));
    btnRow.appendChild(button(lib.isFavorite(cur.item) ? '❤' : '♡', toggleFavorite));
    btnRow.appendChild(button('⏾', pickSleep));
    // Portiva Link: auf TV-Stick, Tablet oder PC an derselben Stelle weiterschauen
    btnRow.appendChild(button('📲 Senden', () => sendToDevice({
      title: titleEl.textContent, url: urlFor(), live: isLive(),
      positionMs: isLive() ? 0 : Math.floor(p.state.time || 0), durationMs: Math.floor(p.state.duration || 0), logo: cur.item.logo || null,
    }, () => { saveNow(); back(); })));
  }

  // ---------------- Abspielen ----------------
  function urlFor() {
    if (cur.catchup) return cur.catchup.url;
    if (cur.episode) return app.source.episodeUrl(cur.episode);
    return app.source.streamUrl(cur.item);
  }

  function start(startAt) {
    saveNow();
    nextShownFor = null; nextCancelled = false; introDone = false; seekOrigin = null;
    clearInterval(countdownTimer); clear(corner);
    subtitle.textContent = '';
    titleEl.textContent = cur.catchup ? `${cur.item.name} · ${cur.catchup.title}`
      : cur.episode ? `${cur.item.name} · S${cur.episode.season} E${cur.episode.episodeNum}` : ((cur.item.number ? cur.item.number + '  ' : '') + cur.item.name);
    subEl.textContent = cur.episode ? cur.episode.title : cur.catchup ? `Sendung vom ${clock(cur.catchup.start)} (Catch-up)` : '';
    epgEl.innerHTML = '';
    buildButtons();
    let at = startAt;
    if (at == null && !isLive()) at = lib.position(posKey()) || 0;
    p.play(urlFor(), { startAt: at || 0, live: isLive() });
    if (cur.item.type === T.LIVE && !cur.catchup) lib.addHistory({ item: cur.item });
    if (isLive()) loadEpg();
    started = true;
    showOverlay(true);
  }

  // ---------------- Ereignisse vom Player ----------------
  function onState(s) {
    playBtn.textContent = s.playing ? '⏸' : '▶';
    clear(center);
    if (s.error) {
      center.appendChild(h('div.msg', null, s.error + '\nOK = erneut versuchen'));
      if (isLive() && app.profile.type === 'XTREAM' && settings.liveFormat === 'ts') {
        center.appendChild(h('div.msg.small', null, 'Tipp: Einstellungen → Live-Format „HLS (m3u8)“ probieren'));
      }
      showOverlay(true);
    } else if (s.buffering) {
      center.appendChild(h('div.spinner'));
    }
    updateProgress();
  }

  function onTime() {
    updateProgress();
    episodeComfort();
  }

  function updateProgress() {
    const s = p.state;
    if (isLive()) {
      bar.style.visibility = 'hidden'; times.style.visibility = 'hidden';
      return;
    }
    bar.style.visibility = ''; times.style.visibility = '';
    const dur = s.duration || 0;
    const frac = dur > 0 ? Math.min(1, s.time / dur) : 0;
    fill.style.width = (frac * 100) + '%';
    knob.style.left = (frac * 100) + '%';
    tNow.textContent = formatTime(s.time);
    tEnd.textContent = dur > 0 ? `${formatTime(dur)}  ·  Ende ${clock(Date.now() + dur - s.time)}` : '';
  }

  function onSubtitle(ev) {
    clearTimeout(subTimer);
    subtitle.textContent = (ev.text || '').replace(/<[^>]+>/g, '');
    if (ev.duration > 0) subTimer = setTimeout(() => { subtitle.textContent = ''; }, ev.duration);
  }

  function onTracks() {
    if (!settings.subtitlesOn) return;
    setTimeout(() => {
      const t = p.tracks();
      if (t.text.length) p.selectText(t.text[0].index);
    }, 800);
  }

  function onEnded() {
    saveNow(true);
    if (isEpisode() && settings.autoNext && !nextCancelled) { playNextEpisode(false); return; }
    if (!isLive()) back();
  }

  // ---------------- EPG ----------------
  function loadEpg() {
    clearInterval(epgTimer);
    const item = cur.item;
    const show = () => app.source.epg(item, false).then((l) => {
      if (cur.item !== item) return;
      const now = Date.now();
      const nowP = l.find((x) => x.start <= now && x.end > now);
      const next = l.find((x) => x.start >= now);
      clear(epgEl);
      if (nowP) {
        const frac = Math.min(1, (now - nowP.start) / (nowP.end - nowP.start));
        epgEl.appendChild(h('div', null, `${clock(nowP.start)} – ${clock(nowP.end)}  ${nowP.title}`));
        epgEl.appendChild(h('div.p-bar', { style: { margin: '10px 0 0' } }, h('div.fill', { style: { width: (frac * 100) + '%' } })));
      }
      if (next) epgEl.appendChild(h('div.next', null, `Danach ${clock(next.start)}  ${next.title}`));
    });
    show();
    epgTimer = setInterval(show, 60000);
  }

  // ---------------- Overlay ----------------
  function showOverlay(focusPlay) {
    overlayVisible = true;
    el.classList.remove('hidden');
    if (focusPlay || !getFocus() || !el.contains(getFocus())) setFocus(playBtn, { noScroll: true });
    poke();
  }

  function hideOverlay() {
    if (p.state.error || panel) return;
    overlayVisible = false;
    el.classList.add('hidden');
  }

  function poke() {
    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => { if (p.state.playing) hideOverlay(); else poke(); }, HIDE_AFTER);
  }

  // ---------------- Spulen + Intro lernen ----------------
  function seek(delta) {
    if (isLive()) return;
    const before = p.pendingSeek != null ? p.pendingSeek : p.state.time;
    if (seekOrigin == null) seekOrigin = before;
    const t = p.seekBy(delta);
    if (t == null) return;
    toast(`${delta > 0 ? '⏩ +' : '⏪ −'}${formatTime(Math.abs(delta))}  ·  ${formatTime(t)} / ${formatTime(p.state.duration)}`, 1500);
    clearTimeout(seekLearnTimer);
    seekLearnTimer = setTimeout(() => learnIntro(seekOrigin, t), 2500);
  }

  /** Wie Android: wer am Anfang einer Folge gezielt vorspult, ueberspringt das Intro -> fuer die Serie merken. */
  function learnIntro(from, to) {
    seekOrigin = null;
    if (!isEpisode() || from == null) return;
    const jump = to - from;
    if (from < EPISODE.LEARN_WITHIN && jump >= EPISODE.LEARN_MIN && jump <= EPISODE.LEARN_MAX) {
      lib.setIntro(lib.key(cur.item), from, to);
      introDone = true;
      clear(corner);
    }
  }

  // ---------------- Serien-Komfort ----------------
  function episodeComfort() {
    if (!isEpisode()) return;
    const s = p.state;
    const t = s.time, dur = s.duration;
    // Intro ueberspringen
    const intro = lib.intro(lib.key(cur.item));
    const iStart = intro ? intro[0] : EPISODE.INTRO_WINDOW_START;
    const iEnd = intro ? intro[1] : EPISODE.INTRO_WINDOW_START + EPISODE.INTRO_SHOW_MS;
    const inIntro = !introDone && t >= iStart && t < iEnd && t < EPISODE.LEARN_WITHIN;
    const skipBtn = corner.querySelector('.skip-intro');
    if (inIntro && !skipBtn && !corner.querySelector('.next-ep')) {
      const b = button('Intro überspringen ⏭', () => {
        introDone = true;
        p.seekTo(intro ? intro[1] : t + EPISODE.INTRO_SKIP);
        clear(corner);
        showOverlay(true);
      }, '.skip-intro');
      corner.appendChild(b);
      pushCornerFocus(b);
    } else if (!inIntro && skipBtn) {
      clear(corner);
      if (getFocus() === skipBtn) setFocus(playBtn, { noScroll: true });
    }
    // Naechste Folge (40 s vor Schluss)
    if (dur > 0 && dur - t < EPISODE.NEXT_BEFORE_END && settings.autoNext && !nextCancelled && nextShownFor !== cur.episode.id && nextEpisode()) {
      nextShownFor = cur.episode.id;
      let left = Math.max(1, Math.round((dur - t) / 1000));
      clear(corner);
      const nb = button(`Nächste Folge in ${left} s ⏭`, () => playNextEpisode(true), '.primary.next-ep');
      const cancel = button('Abbrechen', () => { nextCancelled = true; clearInterval(countdownTimer); clear(corner); setFocus(playBtn, { noScroll: true }); });
      corner.appendChild(nb);
      corner.appendChild(cancel);
      pushCornerFocus(nb);
      clearInterval(countdownTimer);
      countdownTimer = setInterval(() => {
        left = Math.max(0, Math.round((p.state.duration - p.state.time) / 1000));
        nb.textContent = `Nächste Folge in ${left} s ⏭`;
      }, 1000);
    }
  }

  function pushCornerFocus(btn) {
    // Bei ausgeblendeter Leiste direkt mit OK bedienbar
    if (!overlayVisible || !getFocus() || getFocus() === playBtn) setFocus(btn, { noScroll: true });
  }

  function allEpisodes() {
    if (!series || !series.seasons) return [];
    const out = [];
    series.seasons.forEach((s) => series.episodes[s].forEach((e) => out.push(e)));
    return out;
  }

  function nextEpisode() {
    const all = allEpisodes();
    const i = all.findIndex((e) => e.id === cur.episode.id);
    return i >= 0 ? all[i + 1] || null : null;
  }

  function playNextEpisode(manual) {
    const n = nextEpisode();
    clearInterval(countdownTimer);
    if (!n) { if (!manual) back(); else toast('Das war die letzte Folge'); return; }
    saveNow(true);
    cur = { item: cur.item, episode: n, catchup: null };
    start(lib.position(Library.episodeKey(n.id)) || 0);
  }

  function openEpisodes() {
    const all = allEpisodes();
    if (!all.length) { toast('Folgen werden noch geladen …'); return; }
    choose('Folge wählen', all.map((e) => ({
      label: `S${e.season} E${e.episodeNum} · ${e.title}`, active: e.id === cur.episode.id,
      onSelect: () => { saveNow(); cur = { item: cur.item, episode: e, catchup: null }; start(lib.position(Library.episodeKey(e.id)) || 0); },
    })));
  }

  // ---------------- Position merken ----------------
  function saveNow(ended) {
    if (!started || !cur || isLive()) return;
    const s = p.state;
    const dur = s.duration;
    if (!dur) return;
    const pos = ended ? dur : (p.pendingSeek != null ? p.pendingSeek : s.time);
    const entry = cur.episode
      ? { item: cur.item, episode: { id: cur.episode.id, season: cur.episode.season, episodeNum: cur.episode.episodeNum, title: cur.episode.title, ext: cur.episode.ext, image: cur.episode.image } }
      : (cur.catchup ? null : { item: cur.item });
    if (cur.catchup) return;
    lib.savePosition(posKey(), pos, dur, entry);
  }

  // ---------------- Sender ----------------
  async function ensureList() {
    if (list && list.length) return list;
    try { list = await app.source.items(T.LIVE, cur.item.categoryId || null); } catch (e) { list = []; }
    return list;
  }

  async function zap(dir) {
    const l = await ensureList();
    if (!l.length) return;
    const i = l.findIndex((x) => x.id === cur.item.id);
    const n = l[(i + dir + l.length) % l.length];
    cur = { item: n, episode: null, catchup: null };
    start();
  }

  async function zapToNumber(num) {
    let target = null;
    const l = await ensureList();
    target = l.find((x) => x.number === num);
    if (!target) {
      try { target = (await app.source.items(T.LIVE)).find((x) => x.number === num); } catch (e) { /* egal */ }
    }
    if (!target) { toast(`Kein Sender mit Nummer ${num}`); return; }
    cur = { item: target, episode: null, catchup: null };
    start();
  }

  function onDigit(code) {
    if (cur.item.type !== T.LIVE) return;
    digits = (digits + (code - 48)).slice(-4);
    numberEl.textContent = digits;
    clearTimeout(digitTimer);
    digitTimer = setTimeout(() => {
      const n = parseInt(digits, 10);
      digits = ''; numberEl.textContent = '';
      if (n > 0) zapToNumber(n);
    }, 1600);
  }

  async function openChannelList() {
    const l = await ensureList();
    if (!l.length) { toast('Keine Senderliste'); return; }
    closePanel();
    const box = h('div.scroll-y');
    let active = null;
    const epgCache = {};
    l.forEach((it) => {
      const nowEl = h('div.now');
      const row = focusable(h('div.epg-ch' + (it.id === cur.item.id ? '.active' : ''), null,
        imgDiv('logo', it.logo),
        h('div', { style: { flex: '1 1 auto', minWidth: 0 } }, h('div.n', null, (it.number ? it.number + '  ' : '') + it.name), nowEl)),
      () => { closePanel(); cur = { item: it, episode: null, catchup: null }; start(); });
      row._onFocus = () => {
        if (epgCache[it.id] !== undefined) return;
        epgCache[it.id] = null;
        app.source.epg(it, false).then((pl) => {
          const now = Date.now();
          const c = pl.find((x) => x.start <= now && x.end > now);
          epgCache[it.id] = c;
          if (c) nowEl.textContent = `${clock(c.start)} ${c.title}`;
        });
      };
      if (it.id === cur.item.id) { active = row; row.classList.add('autofocus'); }
      box.appendChild(row);
    });
    panel = h('div.p-chlist', null, h('h3', null, 'Sender'), box);
    el.appendChild(panel);
    showOverlay(false);
    pushScope(panel);
    if (active) setFocus(active); else focusFirst();
  }

  async function openProgramme() {
    const item = cur.item;
    const l = await app.source.epg(item, true);
    if (!l.length) { toast('Für diesen Sender gibt es kein Programm'); return; }
    const now = Date.now();
    const items = l.filter((x) => x.end > now - (item.archiveDays || 0) * 86400000).map((x) => {
      const live = x.start <= now && x.end > now;
      const past = x.end <= now;
      const canCatch = (past || live) && x.archive !== false && app.source.catchupUrl(item, x.start, x.end);
      return {
        label: `${clock(x.start)}  ${x.title}${live ? '  ● LIVE' : ''}${canCatch && past ? '  ↺' : ''}`,
        active: live,
        onSelect: () => {
          if (live && !canCatch) { cur = { item, episode: null, catchup: null }; start(); return; }
          if (live) {
            choose(x.title, [
              { label: '● Live ansehen', onSelect: () => { cur = { item, episode: null, catchup: null }; start(); } },
              { label: '↺ Von Beginn an', onSelect: () => playCatchup(item, x) },
            ]);
            return;
          }
          if (canCatch) playCatchup(item, x); else toast(past ? 'Für diese Sendung gibt es keine Aufzeichnung' : 'Die Sendung hat noch nicht begonnen');
        },
      };
    });
    choose(`Programm · ${item.name}`, items);
  }

  function playCatchup(item, prog) {
    const url = app.source.catchupUrl(item, prog.start, prog.end);
    if (!url) { toast('Catch-up nicht verfügbar'); return; }
    cur = { item, episode: null, catchup: { url, title: prog.title, start: prog.start } };
    start(0);
  }

  function closePanel() {
    if (!panel) return;
    panel.parentNode && panel.parentNode.removeChild(panel);
    panel = null;
    popScope();
    poke();
  }

  // ---------------- Menues ----------------
  function pickAudio() {
    const t = p.tracks().audio;
    if (!t.length) { toast('Keine weiteren Tonspuren'); return; }
    choose('Tonspur', t.map((a) => ({ label: a.label, active: a.active, onSelect: () => { p.selectAudio(a.index); toast('Ton: ' + a.label); } })));
  }

  function pickSubtitle() {
    const t = p.tracks().text;
    if (!t.length) { toast('Keine Untertitel vorhanden'); return; }
    choose('Untertitel', [{ label: 'Aus', active: false, onSelect: () => { p.selectText(-1); saveSettings({ subtitlesOn: false }); } }]
      .concat(t.map((s) => ({ label: s.label, active: s.active, onSelect: () => { p.selectText(s.index); saveSettings({ subtitlesOn: true }); toast('Untertitel: ' + s.label); } }))));
  }

  const ASPECTS = [['auto', 'Original'], ['fill', 'Zoom (ausfüllen)'], ['stretch', 'Strecken']];
  function pickAspect() {
    choose('Bildformat', ASPECTS.map(([k, l]) => ({ label: l, active: p.aspect === k, onSelect: () => { p.setAspect(k); saveSettings({ aspect: k }); toast('Bild: ' + l); } })));
  }
  function cycleAspect() {
    const i = ASPECTS.findIndex((a) => a[0] === p.aspect);
    const n = ASPECTS[(i + 1) % ASPECTS.length];
    p.setAspect(n[0]); saveSettings({ aspect: n[0] }); toast('Bild: ' + n[1]);
  }

  function toggleFavorite() {
    const f = lib.toggleFavorite(cur.item);
    toast(f ? 'Zu Favoriten hinzugefügt' : 'Aus Favoriten entfernt');
    buildButtons();
    setFocus(btnRow.lastChild.previousSibling, { noScroll: true });
  }

  function pickSleep() {
    choose('Sleep-Timer', [[0, 'Aus'], [15, '15 Minuten'], [30, '30 Minuten'], [60, '60 Minuten'], [90, '90 Minuten'], [120, '2 Stunden']].map(([m, l]) => ({
      label: l, active: false,
      onSelect: () => {
        clearTimeout(sleepTimer);
        if (!m) { toast('Sleep-Timer aus'); return; }
        toast(`Wiedergabe stoppt in ${l}`);
        sleepTimer = setTimeout(() => {
          saveNow();
          p.stop();
          dialog({ title: 'Sleep-Timer', text: 'Die Wiedergabe wurde beendet.', buttons: [{ label: 'OK', primary: true, onClick: () => back() }] });
        }, m * 60000);
      },
    })));
  }

  // ---------------- Lebenszyklus ----------------
  function attach() {
    p.on('state', onState);
    p.on('time', onTime);
    p.on('ended', onEnded);
    p.on('subtitle', onSubtitle);
    p.on('tracks', onTracks);
    p.on('info', (m) => toast(m));
    p.on('error', () => showOverlay(true));
  }
  function detach() { ['state', 'time', 'ended', 'subtitle', 'tracks', 'info', 'error'].forEach((e) => p.off(e)); }

  const clockTimer = setInterval(() => { clockEl.textContent = clock(Date.now()); }, 15000);

  return {
    el,
    video: true,
    onShow(fresh) {
      attach();
      if (fresh) {
        start(params.startAt != null ? params.startAt : null);
        if (cur.episode && !series) {
          app.source.seriesInfo(cur.item).then((d) => { series = d; }).catch(() => {});
        }
      } else {
        start(isLive() ? 0 : (lib.position(posKey()) || null));
      }
      clearInterval(saveTimer);
      saveTimer = setInterval(() => saveNow(), 15000);
    },
    onHide() {
      saveNow();
      clearInterval(saveTimer); clearInterval(epgTimer); clearInterval(countdownTimer);
      clearTimeout(hideTimer); clearTimeout(sleepTimer);
      detach();
      p.stop();
      started = false;
    },
    onDestroy() { clearInterval(clockTimer); },
    onKey(code) {
      // Medientasten gelten immer
      switch (code) {
        case KEY.PLAY_PAUSE: p.togglePause(); showOverlay(false); return true;
        case KEY.PLAY: p.resume(); showOverlay(false); return true;
        case KEY.PAUSE: p.pause(); showOverlay(false); return true;
        case KEY.STOP: back(); return true;
        case KEY.FF: seek(30000); showOverlay(false); return true;
        case KEY.RW: seek(-30000); showOverlay(false); return true;
        case KEY.NEXT: if (isEpisode()) playNextEpisode(true); else if (cur.item.type === T.LIVE) zap(1); return true;
        case KEY.PREV: if (cur.item.type === T.LIVE) zap(-1); else p.seekTo(0); return true;
        case KEY.CH_UP: case KEY.PAGE_UP: if (cur.item.type === T.LIVE) zap(1); return true;
        case KEY.CH_DOWN: case KEY.PAGE_DOWN: if (cur.item.type === T.LIVE) zap(-1); return true;
        case KEY.RED: toggleFavorite(); return true;
        case KEY.GREEN: pickAudio(); return true;
        case KEY.YELLOW: pickSubtitle(); return true;
        case KEY.BLUE: cycleAspect(); return true;
        case KEY.INFO: if (overlayVisible) hideOverlay(); else showOverlay(true); return true;
        case KEY.GUIDE: if (cur.item.type === T.LIVE) openProgramme(); else go('epg'); return true;
        case KEY.CH_LIST: openChannelList(); return true;
        default: break;
      }
      if (isDigit(code)) { onDigit(code); return true; }
      if (panel) {
        if (isBack(code)) { closePanel(); setFocus(playBtn, { noScroll: true }); return true; }
        return false; // Pfeile/OK in der Senderliste
      }
      if (isBack(code)) {
        if (overlayVisible && !p.state.error) { hideOverlay(); return true; }
        return false; // zurueck zur vorherigen Seite
      }
      if (!overlayVisible) {
        // Leiste ausgeblendet: Pfeile direkt fuer Spulen/Umschalten, OK zeigt die Leiste
        const cornerBtn = corner.querySelector('.focusable');
        if (code === KEY.ENTER) {
          if (cornerBtn && getFocus() === cornerBtn) { cornerBtn._onEnter(); return true; }
          if (p.state.error) { p.togglePause(); return true; }
          showOverlay(true); return true;
        }
        if (code === KEY.LEFT && !isLive()) { seek(-10000); return true; }
        if (code === KEY.RIGHT && !isLive()) { seek(10000); return true; }
        if (code === KEY.UP && cur.item.type === T.LIVE && !cur.catchup) { zap(1); return true; }
        if (code === KEY.DOWN && cur.item.type === T.LIVE && !cur.catchup) { zap(-1); return true; }
        if (code === KEY.LEFT && isLive()) { openChannelList(); return true; }
        showOverlay(false);
        return true;
      }
      if (code === KEY.ENTER && p.state.error && getFocus() === playBtn) { p.togglePause(); return true; }
      poke();
      return false; // Pfeile/OK in der Leiste
    },
  };
}
