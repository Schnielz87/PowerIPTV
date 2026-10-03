// Sendungs-Erinnerungen (wie Android/Windows): solange Portiva laeuft, erscheint zum Sendungsbeginn ein Hinweis.
import { storage, clock } from './util';
import { app, dialog, go } from './app';

let list = storage.get('reminders', []); // [{profileId, item, title, start}]

export const reminders = {
  all() { return list.filter((r) => r.start > Date.now() - 60000); },
  has(item, start) { return list.some((r) => r.item.id === item.id && r.start === start); },
  toggle(profileId, item, title, start) {
    if (this.has(item, start)) list = list.filter((r) => !(r.item.id === item.id && r.start === start));
    else list.push({ profileId, item: { id: item.id, name: item.name, type: item.type, logo: item.logo, categoryId: item.categoryId, number: item.number, archiveDays: item.archiveDays }, title, start });
    storage.set('reminders', list);
    return this.has(item, start);
  },
};

export function startReminders() {
  setInterval(() => {
    const now = Date.now();
    const due = list.filter((r) => r.start <= now + 30000 && r.start > now - 120000);
    if (!due.length) {
      if (list.some((r) => r.start <= now - 120000)) { list = list.filter((r) => r.start > now - 120000); storage.set('reminders', list); }
      return;
    }
    list = list.filter((r) => due.indexOf(r) < 0);
    storage.set('reminders', list);
    const r = due[0];
    if (!app.profile || app.profile.id !== r.profileId) return;
    dialog({
      title: 'Erinnerung',
      text: `${clock(r.start)}  ${r.title}\nauf ${r.item.name} beginnt jetzt.`,
      buttons: [
        { label: 'Einschalten', primary: true, onClick: () => go('player', { item: r.item }) },
        { label: 'Schließen' },
      ],
    });
  }, 20000);
}
