// Video-Wiedergabe ueber Samsung AVPlay (Hardware-Decoder des Fernsehers: TS, HLS, MP4, MKV, AC3/EAC3 ...).
// Im Browser (zum Testen) automatisch HTML5-Video. Gleiche Schutzmassnahmen wie in Android/Windows:
// grosser Puffer, automatisches Neuverbinden, Haenger-Waechter, gesammeltes Spulen.

import { isVega, VegaVideo } from './vega';
import { settings, saveSettings } from './store';

const hasAvPlay = () => !!(window.webapis && window.webapis.avplay);

const BUFFER_SECONDS = { normal: [3, 6], gross: [5, 10], sehr_gross: [8, 15] };

export class Player {
  constructor() {
    this.listeners = {};
    this.state = { playing: false, buffering: false, time: 0, duration: 0, error: null };
    this.url = null;
    this.live = false;
    this.userPaused = false;
    this.pendingSeek = null;
    this.seekTimer = null;
    this.retries = 0;
    this.lastTick = -1;
    this.stallSince = 0;
    this.recoveries = 0;
    this.lastRecoveryAt = 0;
    this.buffer = 'normal';
    this.aspect = 'auto';
    this.watchdogTimer = null;
    this.av = hasAvPlay() ? window.webapis.avplay : null;
    // Fire TV (Vega OS): Hardware-Player der App unter der durchsichtigen Oberflaeche
    this.video = isVega() ? new VegaVideo() : document.getElementById('html-player');
    if (!this.av) {
      // Kein Samsung-Fernseher (z.B. Test im Browser): AVPlay-Ebene ausblenden, HTML5-Video nutzen
      const o = document.getElementById('av-player');
      if (o) o.style.display = 'none';
      this.bindHtml5();
    } else if (this.video) this.video.style.display = 'none';
    document.addEventListener('visibilitychange', () => this.onVisibility());
  }

  on(ev, fn) { (this.listeners[ev] = this.listeners[ev] || []).push(fn); }
  off(ev) { delete this.listeners[ev]; }
  emit(ev, data) { (this.listeners[ev] || []).forEach((f) => { try { f(data); } catch (e) { /* UI-Fehler nicht weiterreichen */ } }); }
  set(patch) { Object.assign(this.state, patch); this.emit('state', this.state); }

  // ---------------- Abspielen ----------------

  play(url, opts = {}) {
    this.stopInternal();
    this.url = url;
    this.live = !!opts.live;
    this.userPaused = false;
    this.pendingSeek = null;
    this.retries = opts.keepRetries ? this.retries : 0;
    this.lastTick = -1; this.stallSince = 0;
    this.set({ playing: false, buffering: true, time: opts.startAt || 0, duration: 0, error: null });
    if (this.av) this.openAvPlay(url, opts.startAt || 0);
    else this.openHtml5(url, opts.startAt || 0);
    this.startWatchdog();
  }

  openAvPlay(url, startAt) {
    const av = this.av;
    try {
      av.open(url);
      av.setDisplayRect(0, 0, 1920, 1080);
      this.applyAspect();
      // Stabil-Modus (fest an oder automatisch nach erkanntem Stocken): mindestens "gross"
      const key = this.stableActive() && this.buffer === 'normal' ? 'gross' : this.buffer;
      const b = BUFFER_SECONDS[key] || BUFFER_SECONDS.normal;
      // Erst mit Vorrat starten; nach einem Aussetzer mit mehr Vorrat weiter (verhindert Dauer-Stocken)
      try {
        av.setBufferingParam('PLAYER_BUFFER_FOR_PLAY', 'PLAYER_BUFFER_SIZE_IN_SECOND', this.live ? b[0] + 1 : b[0]);
        av.setBufferingParam('PLAYER_BUFFER_FOR_RESUME', 'PLAYER_BUFFER_SIZE_IN_SECOND', b[1]);
      } catch (e) { /* aeltere Firmware */ }
      try { av.setTimeoutForBuffering(25); } catch (e) { /* optional */ }
      av.setListener({
        onbufferingstart: () => { this.noteStutter(); this.set({ buffering: true }); },
        onbufferingprogress: () => {},
        onbufferingcomplete: () => this.set({ buffering: false }),
        oncurrentplaytime: (ms) => {
          if (this.pendingSeek == null) this.set({ time: ms, buffering: false });
          this.emit('time', ms);
        },
        onstreamcompleted: () => { this.set({ playing: false }); this.emit('ended'); },
        onevent: () => {},
        onerror: (type) => this.handleError(String(type)),
        onsubtitlechange: (duration, text) => this.emit('subtitle', { text: text || '', duration: +duration || 0 }),
        ondrmevent: () => {},
      });
      av.prepareAsync(() => {
        let dur = 0;
        try { dur = av.getDuration(); } catch (e) { /* live */ }
        this.set({ duration: this.live ? 0 : dur });
        const go = () => {
          try { av.play(); } catch (e) { this.handleError('PLAY_FAILED'); return; }
          this.set({ playing: true, buffering: false });
          this.emit('tracks');
        };
        if (startAt > 0 && !this.live) av.seekTo(startAt, go, go); else go();
      }, (err) => this.handleError(String(err && err.name ? err.name : err)));
    } catch (e) {
      this.handleError(e && e.name ? e.name : String(e));
    }
  }

  /** Stabil-Modus aktiv? (fest an oder nach erkanntem Stocken fuer 24 Stunden) */
  stableActive() {
    return settings.stableMode === 'on' || (settings.stableMode === 'auto' && Date.now() - (settings.stutterAt || 0) < 24 * 3600 * 1000);
  }

  /** Puffern mitten in der Wiedergabe (nicht nach dem Spulen) -> 2x in 5 Minuten schaltet die Automatik zu. */
  noteStutter() {
    const now = Date.now();
    if (!this.state.playing || (this.state.time || 0) < 5000 || this.pendingSeek != null || now - (this.lastSeekAt || 0) < 3000) return;
    this.stutters = (this.stutters || []).filter((t) => now - t < 5 * 60 * 1000).concat(now);
    if (this.stutters.length >= 2 && settings.stableMode === 'auto' && !this.stableActive()) {
      this.stutters = [];
      saveSettings({ stutterAt: now });
      this.emit('info', 'Stocken erkannt – Stabil-Modus mit größerem Puffer ist ab dem nächsten Sender aktiv');
    }
  }

  handleError(type) {
    // Netzwerk-Aussetzer: automatisch neu verbinden (Live: neu starten, Filme: gleiche Stelle)
    if (this.retries < 5 && this.url) {
      this.retries++;
      const pos = this.live ? 0 : (this.state.time || 0);
      this.emit('info', `Verbindung unterbrochen – verbinde neu (${this.retries}/5) …`);
      const url = this.url;
      setTimeout(() => { if (this.url === url) this.play(url, { startAt: pos, live: this.live, keepRetries: true }); }, 1500);
      return;
    }
    this.set({ playing: false, buffering: false, error: errorText(type) });
    this.emit('error', type);
  }

  togglePause() {
    if (this.state.error && this.url) { this.play(this.url, { startAt: this.state.time, live: this.live }); return; }
    if (this.state.playing) this.pause(); else this.resume();
  }

  pause() {
    this.userPaused = true;
    try { if (this.av) this.av.pause(); else this.video.pause(); } catch (e) { /* egal */ }
    this.set({ playing: false });
  }

  resume() {
    this.userPaused = false;
    try { if (this.av) this.av.play(); else this.video.play(); } catch (e) { /* egal */ }
    this.set({ playing: true });
  }

  /** Mehrfaches Spulen sammeln und erst nach kurzer Pause ausfuehren (fluessiger, wie Android). */
  seekBy(deltaMs) {
    if (this.live || !this.state.duration) return null;
    const base = this.pendingSeek != null ? this.pendingSeek : this.state.time;
    return this.seekTo(base + deltaMs);
  }

  seekTo(target) {
    if (this.live || !this.state.duration) return null;
    const t = Math.max(0, Math.min(target, this.state.duration - 2000));
    this.pendingSeek = t;
    this.lastSeekAt = Date.now();
    this.set({ time: t });
    clearTimeout(this.seekTimer);
    this.seekTimer = setTimeout(() => {
      const to = this.pendingSeek;
      const done = () => { this.pendingSeek = null; this.stallSince = 0; };
      try {
        if (this.av) this.av.seekTo(to, done, done);
        else { this.video.currentTime = to / 1000; done(); }
      } catch (e) { done(); }
    }, 600);
    return t;
  }

  stop() {
    this.stopInternal();
    this.url = null;
    clearInterval(this.watchdogTimer);
    this.set({ playing: false, buffering: false, time: 0, duration: 0, error: null });
  }

  stopInternal() {
    clearTimeout(this.seekTimer);
    if (this.av) {
      try { this.av.stop(); } catch (e) { /* nicht offen */ }
      try { this.av.close(); } catch (e) { /* nicht offen */ }
    } else if (this.video) {
      this.video.pause();
      this.video.removeAttribute('src');
      try { this.video.load(); } catch (e) { /* egal */ }
    }
  }

  // ---------------- Haenger-Waechter ----------------
  // Bleibt das Bild stehen, ohne dass ein Fehler gemeldet wird, wird der Stream an derselben Stelle neu geladen.
  startWatchdog() {
    clearInterval(this.watchdogTimer);
    this.watchdogTimer = setInterval(() => {
      if (!this.url || this.userPaused || this.state.error) { this.stallSince = 0; return; }
      if (!this.state.playing && !this.state.buffering) { this.stallSince = 0; return; }
      const t = this.currentTime();
      const now = Date.now();
      if (t !== this.lastTick && t > 0) { this.lastTick = t; this.stallSince = 0; return; }
      if (!this.stallSince) { this.stallSince = now; return; }
      if (now - this.stallSince < (this.live ? 15000 : 12000)) return;
      this.stallSince = 0;
      if (now - this.lastRecoveryAt > 120000) this.recoveries = 0;
      if (++this.recoveries > 3) { this.set({ error: 'Der Stream hängt – bitte später erneut versuchen', buffering: false }); return; }
      this.lastRecoveryAt = now;
      const pos = this.live ? 0 : (this.pendingSeek != null ? this.pendingSeek : this.lastTick);
      this.emit('info', 'Verbindung hing – wird neu geladen …');
      this.play(this.url, { startAt: Math.max(0, pos), live: this.live });
    }, 1000);
  }

  currentTime() {
    try { return this.av ? this.av.getCurrentTime() : Math.floor(this.video.currentTime * 1000); } catch (e) { return this.state.time; }
  }

  // ---------------- Tonspuren / Untertitel / Bildformat ----------------

  tracks() {
    const out = { audio: [], text: [] };
    if (this.av) {
      let info = [];
      try { info = this.av.getTotalTrackInfo() || []; } catch (e) { return out; }
      let cur = {};
      try { cur = this.av.getCurrentStreamInfo().reduce((m, s) => { m[s.type] = s.index; return m; }, {}); } catch (e) { /* optional */ }
      for (const t of info) {
        let extra = {};
        try { extra = JSON.parse(t.extra_info || '{}'); } catch (e) { /* kein JSON */ }
        const lang = extra.language || extra.track_lang || '';
        if (t.type === 'AUDIO') out.audio.push({ index: t.index, label: langName(lang) || `Tonspur ${out.audio.length + 1}`, active: cur.AUDIO === t.index });
        if (t.type === 'TEXT') out.text.push({ index: t.index, label: langName(lang) || `Untertitel ${out.text.length + 1}`, active: false });
      }
    } else if (this.video && this.video.audioTracks) {
      for (let i = 0; i < this.video.audioTracks.length; i++) {
        const a = this.video.audioTracks[i];
        out.audio.push({ index: i, label: langName(a.language) || a.label || `Tonspur ${i + 1}`, active: a.enabled });
      }
    }
    return out;
  }

  selectAudio(index) {
    try {
      if (this.av) this.av.setSelectTrack('AUDIO', index);
      else if (this.video && this.video.selectAudioTrack) this.video.selectAudioTrack(index);
    } catch (e) { /* nicht moeglich */ }
  }

  /** index < 0 = Untertitel aus. */
  selectText(index) {
    try {
      if (!this.av) return;
      if (index < 0) { this.av.setSilentSubtitle(true); this.emit('subtitle', { text: '' }); return; }
      this.av.setSelectTrack('TEXT', index);
      this.av.setSilentSubtitle(false);
    } catch (e) { /* nicht moeglich */ }
  }

  setAspect(mode) { this.aspect = mode; this.applyAspect(); }

  /** Wiedergabe-Geschwindigkeit (AVPlay: ganze Stufen, HTML5: beliebig). */
  setRate(r) {
    this.rate = r;
    try { if (this.av) this.av.setSpeed(r); else this.video.playbackRate = r; } catch (e) { /* nicht moeglich */ }
  }

  applyAspect() {
    const m = this.aspect;
    if (this.av) {
      const method = m === 'stretch' ? 'PLAYER_DISPLAY_MODE_FULL_SCREEN' : m === 'fill' ? 'PLAYER_DISPLAY_MODE_CROPPED_FULL' : 'PLAYER_DISPLAY_MODE_LETTER_BOX';
      try { this.av.setDisplayMethod(method); } catch (e) {
        try { this.av.setDisplayMethod(m === 'stretch' ? 'PLAYER_DISPLAY_MODE_FULL_SCREEN' : 'PLAYER_DISPLAY_MODE_LETTER_BOX'); } catch (er) { /* egal */ }
      }
    } else if (this.video) {
      this.video.style.objectFit = m === 'stretch' ? 'fill' : m === 'fill' ? 'cover' : 'contain';
      if (this.video.applyFit) this.video.applyFit();
    }
  }

  /** App in den Hintergrund (z.B. Smart Hub) -> pausieren, zurueck -> fortsetzen. */
  onVisibility() {
    if (!this.url) return;
    if (document.hidden) {
      try { if (this.av) this.av.suspend(); else this.video.pause(); } catch (e) { /* egal */ }
    } else {
      try {
        if (this.av) this.av.restore(this.url, this.live ? 0 : this.state.time, true);
        else if (!this.userPaused) this.video.play();
      } catch (e) { this.play(this.url, { startAt: this.state.time, live: this.live }); }
    }
  }

  // ---------------- HTML5 (nur Browser-Test) ----------------

  bindHtml5() {
    const v = this.video;
    v.addEventListener('waiting', () => this.set({ buffering: true }));
    v.addEventListener('playing', () => this.set({ playing: true, buffering: false }));
    v.addEventListener('pause', () => this.set({ playing: false }));
    v.addEventListener('timeupdate', () => { if (this.pendingSeek == null) this.set({ time: Math.floor(v.currentTime * 1000) }); this.emit('time', v.currentTime * 1000); });
    v.addEventListener('durationchange', () => this.set({ duration: isFinite(v.duration) && !this.live ? Math.floor(v.duration * 1000) : 0 }));
    v.addEventListener('ended', () => this.emit('ended'));
    v.addEventListener('error', () => { if (this.url) this.handleError('HTML5_ERROR'); });
  }

  openHtml5(url, startAt) {
    const v = this.video;
    v.src = url;
    if (startAt > 0) v.currentTime = startAt / 1000;
    this.applyAspect();
    const p = v.play();
    if (p && p.catch) p.catch(() => {});
    this.emit('tracks');
  }
}

function errorText(type) {
  if (/CONNECTION|NETWORK|TIMEOUT/i.test(type)) return 'Keine Verbindung zum Stream (Anbieter erreichbar? Stream-Limit?)';
  if (/UNSUPPORTED|NOT_SUPPORTED|CODEC/i.test(type)) return 'Dieses Format kann der Fernseher nicht abspielen';
  if (/INVALID_URI|URI/i.test(type)) return 'Ungültige Stream-Adresse';
  return `Wiedergabe fehlgeschlagen (${type})`;
}

const LANGS = { de: 'Deutsch', ger: 'Deutsch', deu: 'Deutsch', en: 'Englisch', eng: 'Englisch', fr: 'Französisch', fre: 'Französisch', fra: 'Französisch',
  es: 'Spanisch', spa: 'Spanisch', it: 'Italienisch', ita: 'Italienisch', tr: 'Türkisch', tur: 'Türkisch', pl: 'Polnisch', pol: 'Polnisch',
  ru: 'Russisch', rus: 'Russisch', nl: 'Niederländisch', dut: 'Niederländisch', nld: 'Niederländisch', ar: 'Arabisch', ara: 'Arabisch' };

function langName(code) {
  if (!code) return '';
  const c = String(code).toLowerCase();
  return LANGS[c] || code.toUpperCase();
}
