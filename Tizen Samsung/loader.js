/*
 * Startrampe der TV-App (Samsung Tizen und Fire TV Vega).
 * Startet die zuletzt geladene, digital signierte Oberflaeche aus dem Speicher – oder die eingebaute.
 * Neue Oberflaechen laedt und prueft die App selbst (src/webupdate.js); hier wird nur gewaehlt.
 * Stuerzt eine geladene Version zweimal hintereinander beim Start ab, gilt wieder die eingebaute.
 * __SHELL__ / __VERSION__ setzt build.js. Bewusst ES5 (Chromium 56).
 */
(function () {
  var SHELL = __SHELL__;
  var BUILT = '__VERSION__';
  var KEY = 'portiva.web';
  function parts(v) { return String(v || '').replace(/^v/i, '').split('.').map(function (x) { return parseInt(x, 10) || 0; }); }
  function newer(a, b) {
    var x = parts(a), y = parts(b);
    for (var i = 0; i < Math.max(x.length, y.length); i++) {
      if ((x[i] || 0) !== (y[i] || 0)) return (x[i] || 0) > (y[i] || 0);
    }
    return false;
  }
  var info = { shell: SHELL, builtIn: BUILT, running: BUILT, source: 'builtin' };
  window.__PORTIVA_SHELL = info;
  var b = null;
  try {
    var fails = parseInt(localStorage.getItem(KEY + '.fails') || '0', 10) || 0;
    var raw = localStorage.getItem(KEY);
    if (raw) {
      b = JSON.parse(raw);
      var usable = b && b.js && b.css && (b.shell || 1) <= SHELL && newer(b.v, BUILT);
      if (!usable || fails >= 2) {
        // veraltet (eingebaute Version ist neuer) oder zweimal beim Start abgestuerzt -> verwerfen
        localStorage.removeItem(KEY);
        localStorage.removeItem(KEY + '.fails');
        if (usable) info.discarded = b.v;
        b = null;
      } else {
        localStorage.setItem(KEY + '.fails', String(fails + 1)); // setzt die App nach erfolgreichem Start zurueck
      }
    }
  } catch (e) { b = null; }
  if (b) {
    info.running = b.v;
    info.source = 'update';
    var st = document.createElement('style');
    st.textContent = b.css;
    document.head.appendChild(st);
    var sc = document.createElement('script');
    sc.textContent = b.js;
    document.body.appendChild(sc);
  } else {
    var l = document.createElement('link');
    l.rel = 'stylesheet';
    l.href = 'app.css';
    document.head.appendChild(l);
    var s = document.createElement('script');
    s.src = 'app.js';
    document.body.appendChild(s);
  }
}());
