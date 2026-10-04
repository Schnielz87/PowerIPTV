// Seitenverwaltung: Stapel von Seiten, Zurueck-Taste, Dialoge, gemeinsamer Zustand.
import { h, clear, toast } from './util';
import { pushScope, popScope, replaceScope, focusFirst, getFocus, setFocus, move, activate, focusable } from './focus';
import { KEY, isBack, VEGA_KEYS } from './keys';
import { isVega } from './vega';
import { settings, saveSettings, profiles, Library, parental } from './store';
import { createSource } from './sources';
import { Player } from './player';

export const app = {
  root: null,
  stack: [],          // [{ name, render, state, el }]
  profile: null,
  source: null,
  library: null,
  player: null,
  maxConnections: null,
  account: null,
  screens: {},        // name -> render(params) => { el, onKey?, onShow?, onHide? }
  dialogOpen: 0,
};

export function init(root) {
  app.root = root;
  app.player = new Player();
  app.player.buffer = settings.buffer;
  app.player.aspect = settings.aspect;
  document.addEventListener('keydown', onKey, true);
}

export function activateProfile(p) {
  app.profile = p;
  app.source = createSource(p, () => settings.liveFormat);
  app.library = new Library(p.id);
  app.maxConnections = null;
  app.account = null;
  saveSettings({ lastProfileId: p.id });
  // Konto-Infos + Kategorien fuer die Kindersicherung im Hintergrund
  app.source.accountInfo().then((acc) => {
    app.account = acc;
    app.maxConnections = acc && acc.maxConnections ? parseInt(acc.maxConnections, 10) || null : null;
    const cur = top();
    if (cur && cur.name === 'home' && cur.refresh) cur.refresh();
  });
  ['LIVE', 'MOVIE', 'SERIES'].forEach((t) => app.source.categories(t).then((c) => parental.register(p.id, t, c)).catch(() => {}));
}

export function firstProfile() {
  const list = profiles.list();
  return list.find((p) => p.id === settings.lastProfileId) || list[0] || null;
}

function top() { return app.stack[app.stack.length - 1]; }

/** Seite oeffnen. replace = Verlauf leeren (z.B. Startseite). */
export function go(name, params = {}, opts = {}) {
  const cur = top();
  if (cur) cur.lastFocus = getFocus();
  if (cur && cur.onHide) cur.onHide();
  if (opts.replace) {
    app.stack.forEach((s) => s.onDestroy && s.onDestroy());
    app.stack = [];
  }
  const page = app.screens[name](params) || {};
  page.name = name;
  page.params = params;
  app.stack.push(page);
  show(page, true);
}

function show(page, fresh) {
  clear(app.root);
  app.root.appendChild(page.el);
  document.body.classList.toggle('video-mode', !!page.video);
  replaceScope(page.el, { onEdge: page.onEdge });
  if (fresh || !page.lastFocus || !document.body.contains(page.lastFocus)) focusFirst();
  else setFocus(page.lastFocus);
  if (page.onShow) page.onShow(fresh);
}

export function back() {
  if (app.stack.length <= 1) { confirmExit(); return; }
  const cur = app.stack.pop();
  if (cur.onHide) cur.onHide();
  if (cur.onDestroy) cur.onDestroy();
  const prev = top();
  show(prev, false);
}

export function current() { return top(); }

// ---------------- Dialoge ----------------

/** Dialog mit Titel, Text und Knoepfen / eigenem Inhalt. Liefert close(). */
export function dialog({ title, text, content, buttons = [], wide, onClose }) {
  const cur = top();
  if (cur) cur.lastFocus = getFocus();
  const box = h('div.dialog' + (wide ? '.wide' : ''),
    null,
    title ? h('h2', null, title) : null,
    text ? h('p.dialog-text', null, text) : null,
    content || null,
    buttons.length ? h('div.dialog-buttons', null, buttons.map((b) => focusable(h('div.btn' + (b.primary ? '.primary' : ''), null, b.label), () => { close(); if (b.onClick) b.onClick(); }))) : null,
  );
  const overlay = h('div.overlay', null, box);
  document.body.appendChild(overlay);
  app.dialogOpen++;
  pushScope(box);
  focusFirst();
  let closed = false;
  function close() {
    if (closed) return;
    closed = true;
    overlay.parentNode && overlay.parentNode.removeChild(overlay);
    app.dialogOpen--;
    popScope();
    if (onClose) onClose();
  }
  box._close = close;
  return close;
}

/** Auswahlliste (z.B. Tonspur). items: [{label, active, onSelect}] */
export function choose(title, items) {
  const list = h('div.choose-list.scroll-y', null);
  let close = null;
  items.forEach((it) => {
    const row = focusable(h('div.choose-row' + (it.active ? '.active' : ''), null, (it.active ? '✓ ' : '') + it.label), () => { close(); it.onSelect(); });
    if (it.active) row.classList.add('autofocus');
    list.appendChild(row);
  });
  close = dialog({ title, content: list });
  return close;
}

/** PIN-Abfrage (Kindersicherung). */
export function askPin(onOk, title = 'Kindersicherung') {
  const input = h('input.pin-input', { type: 'password', inputmode: 'numeric', maxLength: 8, placeholder: 'PIN' });
  focusable(input);
  const msg = h('p.dialog-error', null, '');
  const close = dialog({
    title, text: 'Bitte PIN eingeben', content: h('div', null, input, msg),
    buttons: [
      { label: 'OK', primary: true, onClick: () => {} },
      { label: 'Abbrechen' },
    ],
  });
  // OK-Knopf selbst pruefen (Dialog schliesst sonst sofort)
  const okBtn = input.parentNode.parentNode.querySelector('.btn.primary');
  okBtn._onEnter = async () => {
    if (await parental.verify(input.value)) { close(); onOk(); } else { msg.textContent = 'Falsche PIN'; input.value = ''; }
  };
  input.addEventListener('keydown', (e) => { if (e.keyCode === KEY.ENTER || e.keyCode === KEY.IME_DONE) { input.blur(); okBtn._onEnter(); } });
}

function confirmExit() {
  dialog({
    title: 'Portiva beenden?',
    buttons: [
      { label: 'Beenden', primary: true, onClick: exitApp },
      { label: 'Abbrechen' },
    ],
  });
}

export function exitApp() {
  try { app.player.stop(); } catch (e) { /* egal */ }
  if (isVega()) { window.ReactNativeWebView.postMessage(JSON.stringify({ t: 'exit' })); return; }
  try { window.tizen.application.getCurrentApplication().exit(); } catch (e) { window.close(); }
}

// ---------------- Tasten ----------------

function onKey(e) {
  // Fire TV (Vega OS): Medientasten auf die Samsung-Codes abbilden
  const code = VEGA_KEYS[e.keyCode] ? KEY[VEGA_KEYS[e.keyCode]] : e.keyCode;
  const active = document.activeElement;
  // Texteingabe: Bildschirmtastatur offen -> nur Fertig/Abbrechen/Zurueck auswerten
  if (active && active.tagName === 'INPUT') {
    if (code === KEY.IME_DONE || code === KEY.IME_CANCEL || code === KEY.BACK || code === KEY.ESC || code === KEY.ENTER || code === KEY.DOWN || code === KEY.UP) {
      active.blur();
      if (active._onDone && code !== KEY.IME_CANCEL && code !== KEY.BACK && code !== KEY.ESC) active._onDone();
      if (code === KEY.DOWN) move('down');
      if (code === KEY.UP) move('up');
      e.preventDefault();
    }
    return;
  }
  e.preventDefault();
  // Dialog offen: nur Navigation + Zurueck (schliesst Dialog)
  if (app.dialogOpen > 0) {
    if (isBack(code)) {
      const boxes = document.querySelectorAll('.overlay .dialog');
      const box = boxes[boxes.length - 1];
      if (box && box._close) box._close();
      return;
    }
    navKey(code);
    return;
  }
  const page = top();
  if (page && page.onKey && page.onKey(code, e) === true) return;
  if (isBack(code)) { back(); return; }
  if (code === KEY.EXIT) { exitApp(); return; }
  navKey(code);
}

function navKey(code) {
  switch (code) {
    case KEY.LEFT: move('left'); break;
    case KEY.RIGHT: move('right'); break;
    case KEY.UP: move('up'); break;
    case KEY.DOWN: move('down'); break;
    case KEY.ENTER: activate(); break;
    default: break;
  }
}

export { toast };
