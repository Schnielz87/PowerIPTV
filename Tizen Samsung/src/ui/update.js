// Update-Seite: neue Version auf GitHub pruefen. Tizen-Apps koennen sich nicht selbst installieren –
// die neue PowerIPTV.wgt wird wie bei der Erstinstallation vom PC aus aufgespielt (Daten bleiben erhalten).
import { h, clear, dateDE, clock } from '../util';
import { VERSION, updates, REPO } from '../update';
import { topbar, row, toggleRow, loading } from './common';
import { isVega, platformName } from '../vega';
import { shellInfo } from '../webupdate';
import { button } from './common';

export default function update() {
  const status = h('div');
  const inner = h('div.list-inner', null,
    row('Installiert', 'Portiva für ' + platformName() + (shellInfo().source === 'update' ? ` · automatisch aktualisiert (Grundversion ${shellInfo().builtIn})` : ''), VERSION, null),
    row('Nach Updates suchen', updates.lastCheck ? `Zuletzt geprüft: ${dateDE(updates.lastCheck)} ${clock(updates.lastCheck)}` : 'Noch nie geprüft', '', () => check()),
    toggleRow('Automatisch prüfen (alle 6 Stunden)', 'Neue Versionen werden automatisch geladen und beim nächsten Start aktiv', () => updates.auto, (v) => { updates.auto = v; }),
    status,
  );
  const el = h('div.page', null, topbar('Update'), h('div.list.scroll-y', null, inner));

  function show() {
    clear(status);
    const a = updates.available;
    if (!a) { if (updates.lastCheck) status.appendChild(h('p.dialog-text.success', null, '✓ Du hast die neueste Version.')); return; }
    status.appendChild(h('div.group-title', null, `Neue Version ${a.tag} verfügbar`));
    if (updates.installed) {
      // Selbst-Aktualisierung: schon geladen und geprueft
      status.appendChild(h('p.dialog-text.success', null, `✓ Version ${updates.installed} ist geladen und geprüft. Sie startet beim nächsten Öffnen von Portiva – oder gleich hier:`));
      const b = button('Jetzt neu starten', () => { window.location.reload(); }, '.primary');
      status.appendChild(b);
      if (a.notes) status.appendChild(h('p.dialog-text.small', null, a.notes.replace(/[*`#]/g, '').slice(0, 1500)));
      return;
    }
    if (updates.webError) status.appendChild(h('p.dialog-error', null, `Automatisches Update fehlgeschlagen: ${updates.webError}`));
    if (shellInfo().shell < 1 || a.needsPc) status.appendChild(h('p.dialog-text', null,
      shellInfo().shell < 1
        ? 'Einmalig nötig: Diese Version vom PC aufspielen. Danach aktualisiert sich Portiva selbst – ohne PC.'
        : 'Diese Version ändert die App-Grundlage und muss einmal vom PC aufgespielt werden.'));
    if (isVega()) status.appendChild(h('p.dialog-text', null,
      'Amazon erlaubt Apps außerhalb des Appstores nicht, sich selbst zu aktualisieren. So geht das Update (Zugänge, Favoriten und Verlauf bleiben erhalten):\n' +
      `1. Am PC github.com/${REPO}/releases/latest öffnen und „Portiva-FireTV-Vega…vpkg“ herunterladen.\n` +
      '2. Wie bei der Erstinstallation aufspielen:\n' +
      '    vega device -d <Seriennummer> install-app --packagePath Portiva-FireTV-Vega.vpkg\n' +
      '(Genaue Schritte: Bedienungsanleitung, Kapitel „Fire TV mit Vega OS“.)'));
    else status.appendChild(h('p.dialog-text', null,
      'Samsung erlaubt Apps außerhalb des Samsung-Stores nicht, sich selbst zu aktualisieren. So geht das Update (Zugänge, Favoriten und Verlauf bleiben erhalten):\n' +
      `1. Am PC github.com/${REPO}/releases/latest öffnen und „PowerIPTV-Tizen…“ herunterladen.\n` +
      '2. Wie bei der Erstinstallation mit dem eigenen Zertifikat signieren und installieren:\n' +
      '    tizen package -t wgt -s PortivaTV -- build\n' +
      '    tizen install -n Portiva.wgt -t <Fernseher>\n' +
      '(Genaue Schritte: Bedienungsanleitung, Kapitel „Samsung Smart TV“.)'));
    if (a.notes) status.appendChild(h('p.dialog-text.small', null, a.notes.replace(/[*`#]/g, '').slice(0, 1500)));
  }

  async function check() {
    clear(status).appendChild(loading('Prüfe GitHub …'));
    try { await updates.check(); show(); } catch (e) { clear(status).appendChild(h('p.dialog-error', null, 'Prüfung fehlgeschlagen: ' + e.message)); }
  }

  show();
  return { el };
}
