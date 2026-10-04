// Build fuer Samsung Tizen (Chromium M56): JS/CSS buendeln und auf chrome56 uebersetzen,
// statische Dateien nach build/ kopieren, Version in config.xml setzen.
// Aufruf: node build.js            -> build/ (unsigniert, fertig zum Signieren mit Tizen Studio)
//         node build.js --vega DIR -> Oberflaeche fuer die Fire-TV-App (Vega OS), siehe "Fire TV Vega/"
const fs = require('fs');
const path = require('path');
const esbuild = require('esbuild');

const vegaIdx = process.argv.indexOf('--vega');
const vega = vegaIdx >= 0;
const out = vega ? path.resolve(process.argv[vegaIdx + 1] || path.join(__dirname, 'build-vega')) : path.join(__dirname, 'build');
const build = process.env.BUILD_NUMBER || '0';
const version = `1.1.${build}`;

fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(path.join(out, 'assets'), { recursive: true });

// JavaScript: modernes JS -> ES2016 fuer Chromium 56 (kein ?., ??, Objekt-Spread, Module ...)
esbuild.buildSync({
  entryPoints: [path.join(__dirname, 'src', 'main.js')],
  bundle: true,
  format: 'iife',
  target: ['chrome56'],
  minify: true,
  sourcemap: false,
  legalComments: 'none',
  define: { __APP_VERSION__: JSON.stringify(version) },
  outfile: path.join(out, 'app.js'),
});

// CSS: ebenfalls fuer Chromium 56 (kein Grid verwendet, Flexbox only)
esbuild.buildSync({
  entryPoints: [path.join(__dirname, 'src', 'app.css')],
  bundle: true,
  target: ['chrome56'],
  minify: true,
  outfile: path.join(out, 'app.css'),
});

if (vega) {
  // Fire TV (Vega OS): keine Samsung-Schnittstellen; Video kommt vom Player der App unter der WebView
  const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8')
    .replace(/\s*<!-- Samsung-Schnittstellen[\s\S]*?webapis\.js"><\/script>/, '\n    <script>window.__PORTIVA_VEGA = true;</script>')
    .replace(/\s*<!-- Video-Ebene von AVPlay[^\n]*\n\s*<object[^>]*><\/object>/, '')
    .replace(/\s*<video id="html-player"[^>]*><\/video>/, '');
  fs.writeFileSync(path.join(out, 'index.html'), html);
  fs.copyFileSync(path.join(__dirname, 'icon.png'), path.join(out, 'icon.png'));
} else {
  for (const f of ['index.html', 'icon.png']) fs.copyFileSync(path.join(__dirname, f), path.join(out, f));
}
for (const f of fs.readdirSync(path.join(__dirname, 'assets'))) {
  fs.copyFileSync(path.join(__dirname, 'assets', f), path.join(out, 'assets', f));
}
if (vega) { console.log(`Fire-TV-Oberflaeche ${version} -> ${out}`); process.exit(0); }
const config = fs.readFileSync(path.join(__dirname, 'config.xml'), 'utf8')
  .replace(/(<widget[^>]*\sversion=")[^"]*(")/, `$1${version}$2`);
fs.writeFileSync(path.join(out, 'config.xml'), config);

console.log(`Tizen-Build ${version} -> ${out}`);
