// Zugaenge (Benutzer): Liste zum schnellen Wechseln + Anlegen/Bearbeiten (Xtream Codes oder M3U-Link).
import { h } from '../util';
import { app, go, back, dialog, activateProfile, askPin } from '../app';
import { profiles, parental } from '../store';
import { createSource, normalizeServer } from '../sources';
import { topbar, row, field, button } from './common';

/** Liste der Zugaenge. Aktiver Zugang ist markiert; OK wechselt, Gelb/Rot bearbeitet. */
export default function profilesScreen(params = {}) {
  const list = h('div.list.scroll-y');
  const inner = h('div.list-inner');
  list.appendChild(inner);
  const el = h('div.page', null,
    topbar(params.manage ? 'Zugänge verwalten' : 'Benutzer wechseln'),
    list,
  );

  function render() {
    inner.innerHTML = '';
    const all = profiles.list();
    all.forEach((p) => {
      const active = app.profile && app.profile.id === p.id;
      const r = row(p.name, p.type === 'XTREAM' ? `Xtream Codes · ${hostOf(p.serverUrl)}` : 'M3U-Playlist', '', () => select(p));
      r.querySelector('.t').appendChild(active ? h('span.aktiv', null, 'AKTIV') : document.createTextNode(''));
      if (active) r.classList.add('active', 'autofocus');
      if (params.manage) {
        r._value.appendChild(h('span', null, 'OK = Bearbeiten'));
      }
      inner.appendChild(r);
    });
    inner.appendChild(row('＋ Zugang hinzufügen', 'Xtream Codes oder M3U-Link', '', () => go('profileEdit', {})));
  }

  function select(p) {
    if (params.manage) { go('profileEdit', { profile: p }); return; }
    if (!(app.profile && app.profile.id === p.id)) {
      activateProfile(p);
      parental.unlocked = false;
    }
    go('home', {}, { replace: true });
  }

  render();
  return { el, onShow(fresh) { if (!fresh) render(); } };
}

function hostOf(url) {
  const m = /^https?:\/\/([^/:]+)/i.exec(normalizeServer(url || ''));
  return m ? m[1] : '';
}

/** Zugang anlegen oder bearbeiten. Speichern prueft die Verbindung (wie Android). */
export function profileEdit(params = {}) {
  const p = params.profile || { id: profiles.newId(), type: 'XTREAM', name: '', serverUrl: '', username: '', password: '', m3uUrl: '' };
  let type = p.type;
  const name = field('Name (z. B. „Wohnzimmer“)', p.name);
  const server = field('Server-Adresse (z. B. http://anbieter.tv:8080)', p.serverUrl, { placeholder: 'http://' });
  const user = field('Benutzername', p.username);
  const pass = field('Passwort', p.password, { type: 'password' });
  const m3u = field('M3U-Link', p.m3uUrl, { placeholder: 'http://…/get.php?…' });
  const status = h('p.dialog-error');
  const xtBtn = button('Xtream Codes', () => setType('XTREAM'), '.chip');
  const m3uBtn = button('M3U-Link', () => setType('M3U'), '.chip');
  xtBtn.className = 'chip focusable'; m3uBtn.className = 'chip focusable';
  const xtFields = h('div', null, server.el, user.el, pass.el);
  const m3uFields = h('div', null, m3u.el);
  const saveBtn = button('Verbindung testen & speichern', save, '.primary');
  const buttons = h('div.buttons', null, saveBtn,
    params.profile ? button('Löschen', remove, '.danger-btn') : null,
    params.first ? null : button('Abbrechen', () => back()));
  const list = h('div.list.scroll-y', null,
    h('div.form', { style: { padding: 0 } },
      params.first ? h('p.dialog-text', null, 'Willkommen bei Portiva! Bitte richte deinen IPTV-Zugang ein.\nOK auf einem Feld öffnet die Tastatur des Fernsehers.') : null,
      h('div.type-toggle', null, xtBtn, m3uBtn),
      name.el, xtFields, m3uFields, status, buttons));
  const el = h('div.page', null, topbar(params.profile ? 'Zugang bearbeiten' : 'Zugang hinzufügen', [], { noBack: params.first }), list);

  function setType(t) {
    type = t;
    xtBtn.classList.toggle('active', t === 'XTREAM');
    m3uBtn.classList.toggle('active', t === 'M3U');
    xtFields.style.display = t === 'XTREAM' ? '' : 'none';
    m3uFields.style.display = t === 'M3U' ? '' : 'none';
  }
  setType(type);
  (params.profile ? saveBtn : xtBtn).classList.add('autofocus');

  let busy = false;
  async function save() {
    if (busy) return;
    const np = Object.assign({}, p, {
      type, name: name.input.value.trim(),
      serverUrl: server.input.value.trim(), username: user.input.value.trim(), password: pass.input.value,
      m3uUrl: m3u.input.value.trim(),
    });
    if (type === 'XTREAM' && (!np.serverUrl || !np.username || !np.password)) { status.textContent = 'Bitte Server, Benutzername und Passwort eingeben'; return; }
    if (type === 'M3U' && !/^https?:\/\//i.test(np.m3uUrl)) { status.textContent = 'Bitte einen gültigen M3U-Link eingeben (http…)'; return; }
    if (!np.name) np.name = type === 'XTREAM' ? (hostOf(np.serverUrl) || 'Mein Zugang') : 'M3U-Playlist';
    busy = true;
    status.className = 'dialog-error cyan';
    status.textContent = 'Verbindung wird geprüft …';
    try {
      const acc = await createSource(np, () => 'ts').authenticate();
      if (acc && acc.status && !/active/i.test(acc.status)) throw new Error(`Zugang ist nicht aktiv (${acc.status})`);
      profiles.save(np);
      if (params.first || !app.profile || app.profile.id === np.id) {
        activateProfile(np);
        go('home', {}, { replace: true });
      } else {
        dialog({ title: 'Gespeichert', text: `Zum Zugang „${np.name}“ wechseln?`, buttons: [
          { label: 'Wechseln', primary: true, onClick: () => { activateProfile(np); go('home', {}, { replace: true }); } },
          { label: 'Später', onClick: () => back() },
        ] });
      }
    } catch (e) {
      status.className = 'dialog-error';
      status.textContent = 'Verbindung fehlgeschlagen: ' + e.message;
    } finally { busy = false; }
  }

  function remove() {
    dialog({ title: 'Zugang löschen?', text: `„${p.name}“ mit Favoriten und Verlauf löschen?`, buttons: [
      { label: 'Löschen', primary: true, onClick: () => {
        profiles.remove(p.id);
        const rest = profiles.list();
        if (app.profile && app.profile.id === p.id) {
          if (rest.length) { activateProfile(rest[0]); go('home', {}, { replace: true }); } else go('profileEdit', { first: true }, { replace: true });
        } else back();
      } },
      { label: 'Abbrechen' },
    ] });
  }

  return { el };
}

/** Zugangsverwaltung ist durch die Kindersicherung geschuetzt (wie Einstellungen). */
export function openProfileManager() {
  if (parental.settingsNeedPin()) askPin(() => go('profiles', { manage: true }));
  else go('profiles', { manage: true });
}

