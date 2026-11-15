// Update-Pruefung ueber GitHub (wie Android/Windows). Installiert werden kann eine Tizen-App nur vom PC aus.
import { getJson } from './net';
import { storage } from './util';
import { isVega } from './vega';
import { installWeb, shellInfo, WEB_ASSET } from './webupdate';

export const REPO = 'Schnielz87/PowerIPTV';
export const VERSION = typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '1.1.0';

const state = { available: storage.get('update.available', null), lastCheck: storage.get('update.last', 0), installed: null, webError: null };
export const updates = {
  get available() { return state.available && newer(state.available.tag, VERSION) ? state.available : null; },
  get lastCheck() { return state.lastCheck; },
  /** Version, die geladen ist und beim naechsten Start laeuft (null = keine). */
  get installed() { return state.installed; },
  get webError() { return state.webError; },
  async check() {
    const r = await getJson(`https://api.github.com/repos/${REPO}/releases/latest`, 20000);
    state.lastCheck = Date.now();
    storage.set('update.last', state.lastCheck);
    const tag = (r && r.tag_name) || '';
    const asset = isVega() ? (r.assets || []).find((a) => /\.vpkg$/i.test(a.name))
      : (r.assets || []).find((a) => /\.wgt$/i.test(a.name)) || (r.assets || []).find((a) => /tizen/i.test(a.name));
    // Nur melden, wenn das Release auch eine Datei fuer dieses Geraet enthaelt (GitHub-Build kann einzeln ausfallen)
    const web = (r.assets || []).find((a) => a.name === WEB_ASSET);
    state.available = (asset || web) && newer(tag, VERSION)
      ? { tag, notes: r.body || '', pageUrl: r.html_url, asset: asset ? asset.browser_download_url : null, web: web ? web.browser_download_url : null } : null;
    storage.set('update.available', state.available);
    // Selbst-Aktualisierung: signierte Oberflaeche laden – beim naechsten Start aktiv (kein PC noetig)
    state.webError = null;
    if (state.available && state.available.web && shellInfo().shell >= 1 && state.installed !== tag.replace(/^v/i, '')) {
      try {
        const res = await installWeb(state.available.web);
        if (res.needsPc) state.available.needsPc = true; else state.installed = res.v;
      } catch (e) { state.webError = e.message; }
    }
    return state.available;
  },
  /** Hoechstens alle 6 Stunden automatisch (neue Oberflaechen laedt die App dabei gleich selbst). */
  autoCheck() {
    if (storage.get('update.auto', true) === false) return Promise.resolve(null);
    if (Date.now() - state.lastCheck < 6 * 3600000) return Promise.resolve(this.available);
    return this.check().catch(() => null);
  },
  get auto() { return storage.get('update.auto', true) !== false; },
  set auto(v) { storage.set('update.auto', !!v); },
};

function parts(v) { return String(v || '').replace(/^v/i, '').split('.').map((x) => parseInt(x, 10) || 0); }
export function newer(a, b) {
  const x = parts(a), y = parts(b);
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) > (y[i] || 0);
  }
  return false;
}
