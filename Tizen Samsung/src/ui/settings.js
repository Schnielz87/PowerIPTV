// Einstellungen wie Android (soweit auf dem Fernseher sinnvoll) + Kindersicherung.
import { platformName } from '../vega';
import { h, toast, storage } from '../util';
import { app, go, dialog, askPin } from '../app';
import { settings, saveSettings, parental, categoryPrefs } from '../store';
import { VERSION, updates } from '../update';
import { topbar, row, toggleRow, selectRow, field } from './common';
import { openProfileManager } from './profiles';

export default function settingsScreen() {
  const inner = h('div.list-inner');
  const el = h('div.page', null, topbar('Einstellungen'), h('div.list.scroll-y', null, inner));
  const g = (t) => inner.appendChild(h('div.group-title', null, t));
  const add = (r) => { inner.appendChild(r); return r; };

  g('Wiedergabe');
  add(selectRow('Live-TV-Format (Xtream)', 'TS ist am schnellsten; bei Problemen HLS probieren',
    [['ts', 'MPEG-TS (.ts)'], ['m3u8', 'HLS (.m3u8)']], () => settings.liveFormat, (v) => saveSettings({ liveFormat: v })));
  add(selectRow('Puffer', 'Größer = weniger Stocken, dafür etwas späterer Start',
    [['normal', 'Normal'], ['gross', 'Groß'], ['sehr_gross', 'Sehr groß']], () => settings.buffer,
    (v) => { saveSettings({ buffer: v }); app.player.buffer = v; }));
  add(selectRow('Stabil-Modus (gegen Stocken)', 'Automatisch: nach erkanntem Stocken 24 Stunden lang mit größerem Puffer',
    [['auto', 'Automatisch'], ['on', 'Immer an'], ['off', 'Aus']], () => settings.stableMode,
    (v) => saveSettings({ stableMode: v })));
  add(selectRow('Bildformat', 'Im Player auch mit der blauen Taste',
    [['auto', 'Original'], ['fill', 'Zoom (ausfüllen)'], ['stretch', 'Strecken']], () => settings.aspect,
    (v) => { saveSettings({ aspect: v }); app.player.aspect = v; }));
  add(toggleRow('Untertitel automatisch einschalten', 'Wenn der Film Untertitel hat', () => settings.subtitlesOn, (v) => saveSettings({ subtitlesOn: v })));
  add(toggleRow('Nächste Folge automatisch', '40 Sekunden vor Schluss mit Countdown', () => settings.autoNext, (v) => saveSettings({ autoNext: v })));
  add(toggleRow('Start-Klang', 'Kurzer Kino-Klang beim App-Start', () => settings.introSound, (v) => saveSettings({ introSound: v })));

  g('Inhalte');
  add(row('Kategorie-Sprache zurücksetzen', 'Zeigt wieder alle Sprachen (DE, EN …)', settings.categoryLanguage || 'Alle', (r) => {
    saveSettings({ categoryLanguage: '' }); r._value.textContent = 'Alle'; toast('Alle Sprachen werden angezeigt');
  }));
  add(row('Ausgeblendete Kategorien wieder zeigen', 'Für alle Bereiche dieses Zugangs', '', () => {
    ['LIVE', 'MOVIE', 'SERIES'].forEach((t) => { categoryPrefs.data.hidden[`${app.profile.id}|${t}`] = []; });
    storage.set('catprefs', categoryPrefs.data);
    toast('Alle Kategorien sind wieder sichtbar');
  }));
  add(row('Playlist neu laden', 'Sender, Filme und Serien frisch vom Anbieter holen', '', () => { app.source.clearCache(); storage.set('playlist.refreshed', Date.now()); toast('Wird beim nächsten Öffnen neu geladen'); }));
  add(row('Verlauf löschen', 'Zuletzt gesehen + Weiterschauen dieses Zugangs', '', () => dialog({
    title: 'Verlauf löschen?', buttons: [{ label: 'Löschen', primary: true, onClick: () => {
      const l = app.library; l.history = []; l.positions = {}; storage.set(`hist.${l.pid}`, []); storage.set(`pos.${l.pid}`, {}); toast('Verlauf gelöscht');
    } }, { label: 'Abbrechen' }],
  })));

  g('Zugänge & Schutz');
  add(row('Zugänge verwalten', 'Hinzufügen, bearbeiten, löschen', app.profile ? app.profile.name : '', () => openProfileManager()));
  add(row('Kindersicherung', 'PIN, gesperrte Kategorien, Erwachsenen-Inhalte', parental.isOn() ? 'Ein' : 'Aus', () => go('parental')));
  add(row('KI-Empfehlungen', 'ChatGPT-API-Schlüssel', storage.get('ai.key', '') ? 'eingerichtet' : 'nicht eingerichtet', () => editAiKey()));

  g('Über Portiva');
  add(row('Version', platformName(), VERSION, null));
  add(row('Update', updates.available ? `Neue Version ${updates.available.tag} verfügbar` : 'Nach neuer Version suchen', '', () => go('update')));

  return { el };
}

function editAiKey() {
  const f = field('API-Schlüssel (beginnt mit sk-…)', storage.get('ai.key', ''), { type: 'password' });
  dialog({
    title: 'KI-Empfehlungen', text: 'Schlüssel von platform.openai.com → API keys. Es werden nur Titel übertragen, keine Zugangsdaten.',
    content: f.el,
    buttons: [
      { label: 'Speichern', primary: true, onClick: () => { storage.set('ai.key', f.input.value.trim()); toast(f.input.value.trim() ? 'Schlüssel gespeichert' : 'Schlüssel entfernt'); } },
      { label: 'Abbrechen' },
    ],
  });
}

/** Kindersicherung wie Android: PIN, automatisch Erwachsenen-Kategorien, Einstellungen schuetzen, gesperrte Kategorien. */
export function parentalScreen() {
  const inner = h('div.list-inner');
  const el = h('div.page', null, topbar('Kindersicherung'), h('div.list.scroll-y', null, inner));

  function render() {
    inner.innerHTML = '';
    inner.appendChild(h('p.dialog-text', null, 'Gesperrte Kategorien und Titel werden ausgeblendet, bis die PIN eingegeben wird. Einzelne Kategorien sperrst du im Bereich Live TV / Filme / Serien mit der gelben Taste.'));
    inner.appendChild(row(parental.hasPin() ? 'PIN ändern' : 'PIN festlegen', '4 bis 8 Ziffern', parental.hasPin() ? '••••' : 'keine', () => {
      if (parental.hasPin() && !parental.unlocked) askPin(setPin); else setPin();
    }));
    if (parental.hasPin()) {
      inner.appendChild(toggleRow('Kindersicherung aktiv', null, () => parental.data.enabled, (v) => guarded(() => { parental.setEnabled(v); render(); })));
      inner.appendChild(toggleRow('Erwachsenen-Kategorien automatisch sperren', 'XXX, Adult, 18+ …', () => parental.data.autoAdult, (v) => guarded(() => { parental.data.autoAdult = v; parental.save(); render(); })));
      inner.appendChild(toggleRow('Einstellungen mit PIN schützen', null, () => parental.data.protectSettings, (v) => guarded(() => { parental.data.protectSettings = v; parental.save(); render(); })));
      inner.appendChild(row('Gesperrte Kategorien entsperren', `${parental.data.locked.length} manuell gesperrt`, '', () => guarded(() => { parental.data.locked = []; parental.save(); toast('Alle manuellen Sperren aufgehoben'); render(); })));
      inner.appendChild(row('Kindersicherung zurücksetzen', 'Löscht PIN und alle Sperren', '', () => guarded(() => dialog({
        title: 'Zurücksetzen?', buttons: [{ label: 'Zurücksetzen', primary: true, onClick: () => { parental.reset(); render(); } }, { label: 'Abbrechen' }],
      }))));
    }
  }

  function guarded(fn) { if (parental.isOn() && !parental.unlocked) askPin(fn); else fn(); }

  function setPin() {
    const a = field('Neue PIN', '', { type: 'password' });
    const b = field('PIN wiederholen', '', { type: 'password' });
    const err = h('p.dialog-error');
    const close = dialog({
      title: 'PIN festlegen', content: h('div', null, a.el, b.el, err),
      buttons: [{ label: 'Speichern', primary: true }, { label: 'Abbrechen' }],
    });
    const ok = err.parentNode.parentNode.querySelector('.btn.primary');
    ok._onEnter = async () => {
      const p1 = a.input.value.trim();
      if (!/^\d{4,8}$/.test(p1)) { err.textContent = 'Bitte 4 bis 8 Ziffern'; return; }
      if (p1 !== b.input.value.trim()) { err.textContent = 'Die PINs stimmen nicht überein'; return; }
      await parental.setPin(p1);
      parental.unlocked = true;
      parental.data.enabled = true;
      parental.save();
      close();
      toast('PIN gespeichert – Kindersicherung aktiv');
      render();
    };
  }

  render();
  return { el };
}

