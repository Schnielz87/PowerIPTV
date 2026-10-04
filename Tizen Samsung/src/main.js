// Einstieg der Tizen-App: Polyfills, Fernbedienung, Bildschirme registrieren, Startbild.
import './polyfills';
import { registerKeys } from './keys';
import { isVega } from './vega';
import { app, init, go, activateProfile, firstProfile } from './app';
import { startReminders } from './reminders';
import splash from './ui/splash';
import profiles, { profileEdit } from './ui/profiles';
import home from './ui/home';
import browse from './ui/browse';
import detail from './ui/detail';
import playerScreen from './ui/playerScreen';
import epg from './ui/epg';
import search from './ui/search';
import favorites from './ui/favorites';
import settingsScreen, { parentalScreen } from './ui/settings';
import ai from './ui/ai';
import update from './ui/update';

Object.assign(app.screens, {
  splash, profiles, profileEdit, home, browse, detail, player: playerScreen, epg, search, favorites,
  settings: settingsScreen, parental: parentalScreen, ai, update,
});

function start() {
  registerKeys();
  // Fire TV (Vega OS): feste 1920x1080-Oberflaeche auf die echte Bildschirmgroesse skalieren
  if (isVega() && window.innerWidth && window.innerWidth !== 1920) document.documentElement.style.zoom = String(window.innerWidth / 1920);
  init(document.getElementById('app'));
  go('splash', {
    done: () => {
      const p = firstProfile();
      if (p) { activateProfile(p); go('home', {}, { replace: true }); } else go('profileEdit', { first: true }, { replace: true });
      startReminders();
    },
  });
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
