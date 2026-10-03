// KI-Empfehlungen ueber die ChatGPT-API (gleiches Verfahren wie Android/Windows: nur Titel, keine Zugangsdaten).
import { h, clear, storage } from '../util';
import { focusable } from '../focus';
import { app, go } from '../app';
import { parental } from '../store';
import { T } from '../sources';
import { postJson } from '../net';
import { topbar, field, button, loading, empty } from './common';

const SYSTEM = 'Du bist ein persoenlicher Film- und Serien-Berater in einer IPTV-App.\n' +
  'Empfiehl 10 Titel, die zum Geschmack des Nutzers passen. Waehle AUSSCHLIESSLICH Titel aus dem Katalog\n' +
  'und uebernimm den Titel exakt. Begruende jede Empfehlung kurz auf Deutsch (max. 20 Woerter).\n' +
  'Antworte nur als JSON: {"recommendations":[{"title":"...","type":"movie|series","reason":"..."}]}';

const norm = (s) => String(s || '').toLowerCase().replace(/[^a-z0-9äöüßéèáàíóú]/g, '');

export default function ai() {
  const wish = field('Worauf hast du Lust? (optional, z. B. „lustig, Familie“)', '', {});
  const out = h('div.list.scroll-y', { style: { padding: '0 60px 60px' } });
  const go1 = button('✨ Empfehlungen holen', run, '.primary.autofocus');
  const el = h('div.page', null, topbar('KI-Empfehlungen'), h('div', { style: { padding: '0 60px', width: '1300px' } }, wish.el, h('div.buttons', null, go1)), out);

  async function run() {
    const key = storage.get('ai.key', '');
    clear(out);
    if (!key) { out.appendChild(empty('Bitte zuerst unter Einstellungen → KI-Empfehlungen einen ChatGPT-API-Schlüssel eintragen.')); return; }
    out.appendChild(loading('Katalog wird zusammengestellt und an die KI geschickt …'));
    try {
      const catalog = [];
      for (const t of [T.MOVIE, T.SERIES]) {
        const cats = await app.source.categories(t).catch(() => []);
        const locked = parental.lockedIds(app.profile.id, t, cats);
        const items = await app.source.items(t).catch(() => []);
        items.forEach((i) => { if (!locked[i.categoryId]) catalog.push(i); });
      }
      if (!catalog.length) throw new Error('Keine Filme oder Serien im Katalog gefunden.');
      const lib = app.library;
      const seen = {};
      lib.history.forEach((x) => { seen[lib.key(x.item)] = 1; });
      const sample = catalog.filter((i) => !seen[lib.key(i)]).sort(() => Math.random() - 0.5).slice(0, 350);
      const label = (i) => `${i.name} [${i.type === T.SERIES ? 'Serie' : i.type === T.LIVE ? 'Live' : 'Film'}]`;
      const user = [
        'Zuletzt gesehen: ' + (lib.history.slice(0, 25).map((x) => label(x.item)).join('; ') || 'nichts'),
        'Favoriten: ' + (lib.favorites.slice(0, 25).map(label).join('; ') || 'keine'),
        wish.input.value.trim() ? 'Aktueller Wunsch des Nutzers: ' + wish.input.value.trim() : '',
        '', 'Verfuegbarer Katalog (nur daraus waehlen):',
      ].concat(sample.map((i) => '- ' + label(i))).join('\n');
      const res = await postJson(storage.get('ai.url', 'https://api.openai.com/v1') + '/chat/completions', {
        model: storage.get('ai.model', 'gpt-4o-mini'), temperature: 0.7, response_format: { type: 'json_object' },
        messages: [{ role: 'system', content: SYSTEM }, { role: 'user', content: user }],
      }, { Authorization: 'Bearer ' + key });
      if (res.status === 401) throw new Error('API-Schlüssel ungültig');
      if (res.status === 429) throw new Error('Limit erreicht oder kein Guthaben');
      if (res.status < 200 || res.status >= 300) throw new Error('KI-Anfrage fehlgeschlagen (HTTP ' + res.status + ')');
      const content = JSON.parse(res.body).choices[0].message.content;
      const recs = (JSON.parse(content).recommendations || []);
      const byName = {};
      catalog.forEach((i) => { byName[norm(i.name)] = i; });
      clear(out);
      recs.forEach((r) => {
        const n = norm(String(r.title || '').replace(/\s*\[(Film|Serie|Live)]\s*$/, ''));
        const match = byName[n] || catalog.find((i) => norm(i.name).indexOf(n) >= 0 || n.indexOf(norm(i.name)) >= 0);
        const card = focusable(h('div.item-row.rec-row', null, h('div.main', null, h('div.t', null, r.title), h('div.s', null, r.reason || '')),
          h('div.v', null, match ? '▶ Öffnen' : 'nicht im Katalog')), () => { if (match) go('detail', { item: match }); });
        out.appendChild(card);
      });
      if (!recs.length) out.appendChild(empty('Keine Empfehlungen erhalten'));
    } catch (e) {
      clear(out).appendChild(empty('Fehler: ' + e.message));
    }
  }

  return { el };
}
