// Signiert die gebaute TV-Oberflaeche (build/app.js + app.css) fuer die Selbst-Aktualisierung.
// Aufruf im GitHub-Build: TV_WEB_SIGNING_KEY=<Base64-Seed> node scripts/sign-web.js <Ausgabe.json>
// Ohne Schluessel wird nichts erzeugt (dann gibt es fuer diese Version nur das Update per PC).
const fs = require('fs');
const path = require('path');
const nacl = require('tweetnacl');

const seedB64 = (process.env.TV_WEB_SIGNING_KEY || '').trim();
const out = process.argv[2];
if (!seedB64) { console.log('Kein TV_WEB_SIGNING_KEY – keine Selbst-Aktualisierung fuer diese Version.'); process.exit(0); }
const seed = Buffer.from(seedB64, 'base64');
if (seed.length !== 32) { console.error('TV_WEB_SIGNING_KEY muss 32 Bytes (Base64) sein'); process.exit(1); }
const kp = nacl.sign.keyPair.fromSeed(new Uint8Array(seed));
const dir = path.join(__dirname, '..', 'build');
const meta = JSON.parse(fs.readFileSync(path.join(dir, 'web-meta.json'), 'utf8'));
const b = { v: meta.version, shell: meta.shell, js: fs.readFileSync(path.join(dir, 'app.js'), 'utf8'), css: fs.readFileSync(path.join(dir, 'app.css'), 'utf8') };
const msg = Buffer.from(`portiva-web|${b.v}|${b.shell}|${b.js}|${b.css}`, 'utf8');
b.sig = Buffer.from(nacl.sign.detached(new Uint8Array(msg), kp.secretKey)).toString('base64');
fs.writeFileSync(out, JSON.stringify(b));
console.log(`Signiert: ${out} (${b.v}, Startrampe ${b.shell}, oeffentlicher Schluessel ${Buffer.from(kp.publicKey).toString('base64')})`);
