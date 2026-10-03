// Portiva Link auf dem Fernseher: Zugang als QR zeigen, Zugang vom Handy empfangen, Wiedergabe an ein Geraet senden.
import { h, toast } from '../util';
import { dialog, choose, go, activateProfile } from '../app';
import { profiles } from '../store';
import { accountQr, pairQr, newCode, qrImage, localIp, discover, fetchOffer, fromAccount, sendPlay } from '../link';

/** Grosser QR-Code eines Zugangs (Handy/Tablet scannt ihn unter „Neuer Zugang → QR-Code scannen“). */
export function showAccountQr(p) {
  dialog({
    title: p.name,
    content: h('div', { style: { textAlign: 'center' } },
      h('img', { src: qrImage(accountQr(p), 9), style: { width: '460px', height: '460px', borderRadius: '16px', imageRendering: 'pixelated' } }),
      h('p.dialog-text.small', { style: { marginTop: '18px' } },
        'Am Handy/Tablet in Portiva: Benutzer wechseln → Neuer Zugang → „QR-Code scannen“.\nNur dir selbst zeigen – der Code enthält die Zugangsdaten.')),
    buttons: [{ label: '✕ Schließen', primary: true }],
  });
}

/** Zugang vom Handy empfangen: Fernseher zeigt Code, Handy scannt und stellt den Zugang bereit, der Fernseher holt ihn ab. */
export async function receiveAccount() {
  const ip = await localIp();
  if (!ip) { toast('Keine Netzwerkverbindung gefunden'); return; }
  const code = newCode();
  const status = h('p.dialog-text.cyan', null, 'Warte auf das Handy …');
  let open = true;
  dialog({
    title: 'Zugang vom Handy empfangen', wide: true,
    content: h('div.row', { style: { alignItems: 'flex-start' } },
      h('img', { src: qrImage(pairQr(ip, code), 8), style: { width: '420px', height: '420px', borderRadius: '16px', marginRight: '40px', imageRendering: 'pixelated' } }),
      h('div', { style: { flex: '1 1 auto' } },
        h('p.dialog-text', null,
          '1. Am Handy Portiva öffnen → Benutzer wechseln.\n' +
          '2. Beim gewünschten Zugang auf das QR-Symbol tippen.\n' +
          '3. „An TV-Stick / Fernseher senden“ → diesen Code scannen.\n\n' +
          `Code ${code} · Handy und Fernseher im selben WLAN.`),
        status)),
    buttons: [{ label: 'Abbrechen' }],
    onClose: () => { open = false; },
  });
  let devices = [];
  let round = 0;
  while (open) {
    if (round % 3 === 0 || !devices.length) {
      status.textContent = 'Suche das Handy im Heimnetz …';
      devices = await discover(ip);
      if (!open) return;
      status.textContent = devices.length ? `Warte auf das Handy … (${devices.map((d) => d.name).join(', ')})` : 'Noch kein Portiva-Gerät gefunden – Portiva am Handy geöffnet?';
    }
    const a = devices.length ? await fetchOffer(devices, code) : null;
    if (!open) return;
    if (a) {
      const existing = profiles.list().find((p) => p.serverUrl === a.serverUrl && p.username === a.username && (p.m3uUrl || '') === (a.m3uUrl || ''));
      const p = fromAccount(a, existing && existing.id);
      profiles.save(p);
      const boxes = document.querySelectorAll('.overlay .dialog');
      const box = boxes[boxes.length - 1];
      if (box && box._close) box._close();
      toast(`Zugang „${p.name}“ übernommen`);
      activateProfile(p);
      go('home', {}, { replace: true });
      return;
    }
    round++;
    await new Promise((r) => setTimeout(r, 3000));
  }
}

/** Laufende Wiedergabe an ein anderes Portiva-Geraet (TV-Stick, Tablet, PC) geben. onSent(name) stoppt hier. */
export async function sendToDevice(play, onSent) {
  toast('Suche Portiva-Geräte im Heimnetz …', 6000);
  const list = await discover();
  if (!list.length) { toast('Kein Gerät gefunden – Portiva dort öffnen (gleiches WLAN)'); return; }
  choose('An Gerät senden', list.map((d) => ({
    label: d.name,
    onSelect: async () => {
      const err = await sendPlay(d, Object.assign({ from: 'Samsung TV' }, play));
      if (err) toast(err); else { toast(`Läuft jetzt auf „${d.name}“`); onSent(d.name); }
    },
  })));
}
