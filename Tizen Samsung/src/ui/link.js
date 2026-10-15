// Portiva Link auf dem Fernseher: Zugang als QR zeigen, Zugang vom Handy empfangen, Wiedergabe an ein Geraet senden.
import { isVega } from '../vega';
import { h, toast } from '../util';
import { dialog, choose, go, activateProfile } from '../app';
import { profiles } from '../store';
import { accountQr, pairQr, newCode, qrImage, localIp, discover, fetchOffer, fromAccount, sendPlay, probe, saveHost, sendPair, toAccount } from '../link';
import { field } from './common';

/** Grosser QR-Code eines Zugangs (Handy/Tablet scannt ihn unter „Neuer Zugang → QR-Code scannen“). */
export function showAccountQr(p) {
  dialog({
    title: p.name,
    content: h('div', { style: { textAlign: 'center' } },
      h('img', { src: qrImage(accountQr(p), 9), style: { width: '460px', height: '460px', borderRadius: '16px', imageRendering: 'pixelated' } }),
      h('p.dialog-text.small', { style: { marginTop: '18px' } },
        'Am Handy/Tablet in Portiva: Benutzer wechseln → Neuer Zugang → „QR-Code scannen“.\nNur dir selbst zeigen – der Code enthält die Zugangsdaten.')),
    buttons: [{ label: '✕ Schließen', primary: true }, { label: 'Auf Receiver übertragen (Code eingeben)', onClick: () => sendToReceiver(p) }],
  });
}

/** Zugang auf einen Enigma2-Receiver uebertragen: dort "Zugang vom Handy empfangen" oeffnen, IP-Adresse + Code eintragen. */
function sendToReceiver(p) {
  const ipF = field('IP-Adresse des Receivers', '', { placeholder: 'z.B. 192.168.178.40' });
  const codeF = field('Code (6 Ziffern)', '', { placeholder: '123456' });
  const msg = h('p.dialog-error', null, '');
  const close = dialog({
    title: `„${p.name}“ auf Receiver übertragen`,
    text: 'Am Receiver: PowerIPTV → Benutzer wechseln → „Zugang vom Handy empfangen“. Die dort angezeigte IP-Adresse und den Code eintragen.',
    content: h('div', null, ipF.el, codeF.el, msg),
    buttons: [{ label: 'Übertragen', primary: true, onClick: () => {} }, { label: 'Abbrechen' }],
  });
  const okBtn = ipF.el.parentNode.parentNode.querySelector('.btn.primary');
  okBtn._onEnter = async () => {
    const a = ipF.input.value.trim();
    const code = codeF.input.value.replace(/\D/g, '');
    if (!a || code.length !== 6) { msg.textContent = 'Bitte IP-Adresse und 6-stelligen Code eingeben'; return; }
    msg.textContent = 'Wird übertragen …';
    const host = a.split(':')[0];
    const port = parseInt(a.split(':')[1], 10) || 47800;
    const err = await sendPair(host, port, code, toAccount(p), isVega() ? 'Fire TV' : 'Samsung TV');
    if (err) { msg.textContent = err; return; }
    close();
    toast(`✓ „${p.name}“ wurde auf den Receiver übertragen`);
  };
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
  if (!list.length) toast('Kein Gerät gefunden – Portiva dort öffnen (gleiches Heimnetz, WLAN oder LAN-Kabel) oder IP-Adresse eingeben', 5000);
  const send = async (d) => {
    const err = await sendPlay(d, Object.assign({ from: isVega() ? 'Fire TV' : 'Samsung TV' }, play));
    if (err) toast(err); else { toast(`Läuft jetzt auf „${d.name}“`); onSent(d.name); }
  };
  choose('An Gerät senden', list.map((d) => ({ label: d.name, onSelect: () => send(d) })).concat([{
    label: '＋ Gerät per IP-Adresse …',
    onSelect: () => askIp(async (ip) => {
      toast(`Verbinde mit ${ip} …`);
      const d = await probe(ip);
      if (!d) { toast(`Unter ${ip} antwortet kein Portiva-Gerät`); return; }
      saveHost(ip);
      send(d);
    }),
  }]));
}

/** IP-Adresse eingeben (steht beim Enigma2-Receiver unten auf der Portiva-Startseite). */
function askIp(onOk) {
  const f = field('IP-Adresse des Geräts', '', { placeholder: 'z.B. 192.168.178.40' });
  const close = dialog({
    title: 'Gerät per IP-Adresse',
    content: f.el,
    buttons: [{ label: 'Verbinden', primary: true, onClick: () => {} }, { label: 'Abbrechen' }],
  });
  const okBtn = f.el.parentNode.parentNode.querySelector('.btn.primary');
  okBtn._onEnter = () => { const v = f.input.value.trim(); if (!v) return; close(); onOk(v); };
  f.input._onDone = () => okBtn._onEnter();
}
