// Fernbedienung (Samsung Tizen). Pfeile, OK und Zurueck sind immer da; Media-, Farb-, Zahlen- und
// Programmtasten muessen ueber die Tizen-API angemeldet werden.

export const KEY = {
  LEFT: 37, UP: 38, RIGHT: 39, DOWN: 40, ENTER: 13,
  BACK: 10009, BACKSPACE: 8, ESC: 27, EXIT: 10182,
  PLAY_PAUSE: 10252, PLAY: 415, PAUSE: 19, STOP: 413, FF: 417, RW: 412,
  PREV: 10232, NEXT: 10233,
  CH_UP: 427, CH_DOWN: 428, PAGE_UP: 33, PAGE_DOWN: 34,
  RED: 403, GREEN: 404, YELLOW: 405, BLUE: 406,
  INFO: 457, GUIDE: 458, CH_LIST: 10073,
  // Tastatur (Bildschirm-Tastatur des Fernsehers)
  IME_DONE: 65376, IME_CANCEL: 65385,
};

const OPTIONAL_KEYS = [
  'MediaPlayPause', 'MediaPlay', 'MediaPause', 'MediaStop', 'MediaFastForward', 'MediaRewind',
  'MediaTrackPrevious', 'MediaTrackNext', 'ChannelUp', 'ChannelDown',
  'ColorF0Red', 'ColorF1Green', 'ColorF2Yellow', 'ColorF3Blue',
  'Info', 'Guide', 'ChannelList',
  '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
];

/** Tasten anmelden und echte Codes vom Geraet uebernehmen (unterscheiden sich je nach Modell nicht, aber sicher ist sicher). */
export function registerKeys() {
  const ti = window.tizen && window.tizen.tvinputdevice;
  if (!ti) return;
  const supported = {};
  try { ti.getSupportedKeys().forEach((k) => { supported[k.name] = k.code; }); } catch (e) { /* aeltere Firmware */ }
  const names = OPTIONAL_KEYS.filter((n) => supported[n] !== undefined);
  try {
    if (ti.registerKeyBatch) ti.registerKeyBatch(names);
    else names.forEach((n) => ti.registerKey(n));
  } catch (e) {
    names.forEach((n) => { try { ti.registerKey(n); } catch (er) { /* Taste fehlt */ } });
  }
  const map = {
    MediaPlayPause: 'PLAY_PAUSE', MediaPlay: 'PLAY', MediaPause: 'PAUSE', MediaStop: 'STOP',
    MediaFastForward: 'FF', MediaRewind: 'RW', MediaTrackPrevious: 'PREV', MediaTrackNext: 'NEXT',
    ChannelUp: 'CH_UP', ChannelDown: 'CH_DOWN', ColorF0Red: 'RED', ColorF1Green: 'GREEN',
    ColorF2Yellow: 'YELLOW', ColorF3Blue: 'BLUE', Info: 'INFO', Guide: 'GUIDE', ChannelList: 'CH_LIST',
  };
  for (const n of Object.keys(map)) if (supported[n] !== undefined) KEY[map[n]] = supported[n];
}

/** Fire TV-Fernbedienung (Vega OS): Medientasten haben eigene Codes. */
export const VEGA_KEYS = { 179: 'PLAY_PAUSE', 227: 'RW', 228: 'FF' };

export const isBack = (c) => c === KEY.BACK || c === KEY.ESC || c === KEY.BACKSPACE;
export const isDigit = (c) => c >= 48 && c <= 57;
