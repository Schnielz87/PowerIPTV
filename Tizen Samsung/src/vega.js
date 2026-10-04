// Fire TV mit Vega OS (neue Amazon-Sticks): die Oberflaeche laeuft in der WebView der Vega-App
// ("Fire TV Vega/"). Netzwerk und Video gehen ueber die App (React Native), weil die WebView
// fremde IPTV-Server wegen CORS/HTTP nicht direkt erreichen darf. Auf Samsung/Browser inaktiv.

export const isVega = () => !!(window.ReactNativeWebView && window.__PORTIVA_VEGA);

let seq = 0;
const pending = {};
const videoListeners = {};
const videoState = { currentTime: 0, duration: NaN, paused: true, audio: [] };

function post(msg) { window.ReactNativeWebView.postMessage(JSON.stringify(msg)); }

// Antworten der App (wird von der App per injectJavaScript aufgerufen)
window.__vega = function (msg) {
  if (!msg) return;
  if (msg.t === 'http') {
    const p = pending[msg.id];
    if (!p) return;
    delete pending[msg.id];
    p(msg.ok ? { status: msg.status, text: msg.text } : null);
  } else if (msg.t === 'v') {
    if (msg.currentTime != null) videoState.currentTime = msg.currentTime;
    if (msg.duration != null) videoState.duration = msg.duration;
    if (msg.paused != null) videoState.paused = msg.paused;
    if (msg.audio) videoState.audio = msg.audio;
    (videoListeners[msg.ev] || []).forEach((f) => { try { f(); } catch (e) { /* UI-Fehler */ } });
  }
};

/** HTTP ueber die App. Liefert {status, text} oder null (keine Verbindung/Zeitueberschreitung). */
export function request(method, url, body, headers, timeoutMs) {
  return new Promise((resolve) => {
    const id = ++seq;
    pending[id] = resolve;
    post({ t: 'http', id, method, url, body: body == null ? null : String(body), headers: headers || {}, timeout: timeoutMs || 30000 });
    setTimeout(() => { if (pending[id]) { delete pending[id]; resolve(null); } }, (timeoutMs || 30000) + 2000);
  });
}

/**
 * Nachbildung eines <video>-Elements: das Bild kommt vom Hardware-Player der App (liegt unter der
 * durchsichtigen WebView, wie AVPlay beim Samsung-Fernseher).
 */
export class VegaVideo {
  constructor() { this.style = {}; this._src = ''; this._rate = 1; }
  addEventListener(ev, fn) { (videoListeners[ev] = videoListeners[ev] || []).push(fn); }
  get src() { return this._src; }
  set src(url) { this._src = url; videoState.currentTime = 0; videoState.duration = NaN; post({ t: 'v', cmd: 'open', url, fit: this.fit() }); }
  removeAttribute(name) { if (name === 'src') { this._src = ''; post({ t: 'v', cmd: 'stop' }); } }
  load() { /* Stopp passiert bereits in removeAttribute */ }
  play() { post({ t: 'v', cmd: 'play' }); return Promise.resolve(); }
  pause() { post({ t: 'v', cmd: 'pause' }); }
  get paused() { return videoState.paused; }
  get currentTime() { return videoState.currentTime; }
  set currentTime(s) { videoState.currentTime = s; post({ t: 'v', cmd: 'seek', time: s }); }
  get duration() { return videoState.duration; }
  get playbackRate() { return this._rate; }
  set playbackRate(r) { this._rate = r; post({ t: 'v', cmd: 'rate', rate: r }); }
  get audioTracks() { return videoState.audio; }
  selectAudioTrack(i) { post({ t: 'v', cmd: 'audio', index: i }); }
  fit() { return this.style.objectFit || 'contain'; }
  applyFit() { post({ t: 'v', cmd: 'fit', fit: this.fit() }); }
}

/** Name dieser Variante fuer Anzeigen (Einstellungen, Update, Portiva Link). */
export const platformName = () => (isVega() ? 'Fire TV (Vega OS)' : 'Samsung Smart TV (Tizen)');
