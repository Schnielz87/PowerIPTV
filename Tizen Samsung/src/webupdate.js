// Selbst-Aktualisierung der TV-Oberflaeche (Samsung Tizen, Fire TV Vega) ohne PC:
// Das Release enthaelt "portiva-tv-web.json" (JS + CSS der Oberflaeche, Ed25519-signiert im GitHub-Build).
// Nur mit gueltiger Unterschrift wird es gespeichert; die Startrampe (loader.js) startet es beim naechsten Mal.
import nacl from 'tweetnacl';
import { getText } from './net';

const KEY = 'portiva.web';
/** Oeffentlicher Schluessel (der geheime liegt nur als GitHub-Secret TV_WEB_SIGNING_KEY vor). */
const PUBLIC_KEY = 'UG4s/L5iwP6PIB1VugHW+SG7y5qLiuIRE66B2IWBK40=';
export const WEB_ASSET = 'portiva-tv-web.json';

/** Angaben der Startrampe (alte Installationen ohne Startrampe: shell 0). */
export function shellInfo() {
  return window.__PORTIVA_SHELL || { shell: 0, source: 'builtin' };
}

function b64(s) {
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function utf8(s) {
  if (typeof TextEncoder !== 'undefined') return new TextEncoder().encode(s);
  const bin = unescape(encodeURIComponent(s));
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

/** Gleiche Nachricht wie beim Signieren (scripts/sign-web.js). */
export function signedMessage(b) { return `portiva-web|${b.v}|${b.shell}|${b.js}|${b.css}`; }

export function verify(b) {
  try { return nacl.sign.detached.verify(utf8(signedMessage(b)), b64(b.sig), b64(PUBLIC_KEY)); } catch (e) { return false; }
}

/**
 * Neue Oberflaeche laden, pruefen und fuer den naechsten Start speichern.
 * Ergebnis: { v } = bereit; { v, needsPc: true } = diese Version braucht eine neue Installation vom PC.
 */
export async function installWeb(url) {
  const b = JSON.parse(await getText(url, 90000));
  if (!b || !b.v || !b.js || !b.css || !b.sig) throw new Error('Update-Datei unvollständig');
  if (shellInfo().shell < 1 || (b.shell || 1) > shellInfo().shell) return { v: b.v, needsPc: true };
  if (!verify(b)) throw new Error('Unterschrift ungültig – Update verworfen');
  localStorage.setItem(KEY, JSON.stringify({ v: b.v, shell: b.shell, js: b.js, css: b.css }));
  localStorage.removeItem(`${KEY}.fails`);
  return { v: b.v };
}

/** Nach erfolgreichem Start: Absturz-Zaehler der Startrampe zuruecksetzen. */
export function markStarted() {
  setTimeout(() => { try { localStorage.removeItem(`${KEY}.fails`); } catch (e) { /* egal */ } }, 6000);
}
