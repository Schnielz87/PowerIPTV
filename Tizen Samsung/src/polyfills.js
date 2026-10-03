// Laufzeit-Ergaenzungen fuer Chromium 56 (Tizen 4.0). Syntax uebersetzt esbuild, APIs fehlen aber teils.
if (!String.prototype.padStart) {
  String.prototype.padStart = function (len, fill) {
    let s = String(this);
    const f = fill === undefined ? ' ' : String(fill);
    while (s.length < len) s = f + s;
    return s.slice(s.length - Math.max(len, String(this).length));
  };
}
if (!Array.prototype.flat) {
  Array.prototype.flat = function () { return [].concat.apply([], this); };
}
if (!Object.fromEntries) {
  Object.fromEntries = function (entries) {
    const o = {};
    for (const [k, v] of entries) o[k] = v;
    return o;
  };
}
if (typeof Promise !== 'undefined' && !Promise.prototype.finally) {
  Promise.prototype.finally = function (fn) {
    return this.then((v) => Promise.resolve(fn()).then(() => v), (e) => Promise.resolve(fn()).then(() => { throw e; }));
  };
}
