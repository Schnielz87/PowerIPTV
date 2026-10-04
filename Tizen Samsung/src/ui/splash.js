// Startbild mit Portiva-Logo und Start-Sound (abschaltbar in den Einstellungen).
import { h } from '../util';
import { settings } from '../store';

export default function splash({ done }) {
  const el = h('div.page.splash', null,
    h('img', { src: 'assets/logo.png' }),
    h('div.name', null, h('span.power', null, 'Power'), h('span', null, 'IPTV')),
    h('div.sub', null, 'by Portiva', h('sup', null, '©')),
  );
  let finished = false;
  const finish = () => { if (!finished) { finished = true; done(); } };
  return {
    el,
    onShow() {
      if (settings.introSound) {
        try {
          const a = new Audio('assets/intro.ogg');
          a.volume = 0.8;
          const p = a.play();
          if (p && p.catch) p.catch(() => {});
        } catch (e) { /* kein Ton */ }
      }
      setTimeout(finish, 2200);
    },
    onKey() { finish(); return true; },
  };
}
