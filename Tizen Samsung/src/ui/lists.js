// Eigene Listen (wie Android/Windows): Menue beim langen OK-Druecken bzw. blauer Taste auf einem
// Sender, Film oder einer Serie – Favorit, "Zu Liste hinzufuegen" (neue Liste anlegen oder in vorhandene legen).
import { h, toast } from '../util';
import { app, choose, dialog } from '../app';
import { setFocus } from '../focus';
import { field } from './common';

/** Menue fuer einen Titel. onChange wird nach jeder Aenderung aufgerufen (Seite neu zeichnen). */
export function itemMenu(item, onChange) {
  const lib = app.library;
  if (!lib || !item) return;
  const fav = lib.isFavorite(item);
  choose(item.name, [
    { label: fav ? '❤ Aus Favoriten entfernen' : '❤ Zu Favoriten hinzufügen', onSelect: () => {
      const f = lib.toggleFavorite(item);
      toast(f ? `„${item.name}“ zu Favoriten hinzugefügt` : `„${item.name}“ aus Favoriten entfernt`);
      if (onChange) onChange();
    } },
    { label: '☰ Zu Liste hinzufügen …', onSelect: () => listPicker(item, onChange) },
  ]);
}

/** Auswahl der eigenen Listen: OK legt hinein bzw. nimmt heraus, oben "Neue Liste anlegen". */
export function listPicker(item, onChange, focusIndex) {
  const lib = app.library;
  if (!lib) return;
  const rows = [{ label: '＋ Neue Liste anlegen …', onSelect: () => askName('Neue Liste', '', (name) => {
    const id = lib.createList(name);
    lib.toggleInList(id, item);
    toast(`„${item.name}“ zur neuen Liste „${name}“ hinzugefügt`);
    if (onChange) onChange();
  }) }];
  lib.lists.forEach((l, i) => {
    const inside = lib.isInList(l.id, item);
    rows.push({ label: `${inside ? '✓' : '○'}  ${l.name} (${l.items.length})`, onSelect: () => {
      const added = lib.toggleInList(l.id, item);
      toast(added ? `Zu „${l.name}“ hinzugefügt` : `Aus „${l.name}“ entfernt`);
      if (onChange) onChange();
      listPicker(item, onChange, i + 1); // offen lassen, damit mehrere Listen gewaehlt werden koennen
    } });
  });
  choose(`Zu Liste hinzufügen: ${item.name}`, rows);
  if (focusIndex) {
    const all = document.querySelectorAll('.overlay .choose-row');
    if (all[focusIndex]) setFocus(all[focusIndex]);
  }
}

/** Namen eingeben (neue Liste / umbenennen). */
export function askName(title, value, onOk) {
  const f = field('Name der Liste', value, { placeholder: 'z.B. Sport, Kinder, Nachrichten' });
  const msg = h('p.dialog-error', null, '');
  const close = dialog({
    title, content: h('div', null, f.el, msg),
    buttons: [
      { label: 'Speichern', primary: true, onClick: () => {} },
      { label: 'Abbrechen' },
    ],
  });
  const okBtn = f.el.parentNode.parentNode.querySelector('.btn.primary');
  okBtn._onEnter = () => {
    const name = f.input.value.trim();
    if (!name) { msg.textContent = 'Bitte einen Namen eingeben'; return; }
    close();
    onOk(name);
  };
  f.input._onDone = () => okBtn._onEnter();
}
