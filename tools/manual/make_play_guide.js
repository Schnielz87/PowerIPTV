// Erzeugt Android/Play-Store-Anleitung.docx – Schritt fuer Schritt in den Google Play Store + automatische Updates.
// Aufruf: node tools/manual/make_play_guide.js
const fs = require('fs');
const path = require('path');
const { Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell, WidthType, ShadingType,
  AlignmentType, LevelFormat, TableOfContents, PageBreak, Footer, PageNumber, BorderStyle, ExternalHyperlink } = require('docx');

const NAVY = '1E3A5F';
const CYAN = '1E88B8';
const RED = 'B42318';
const REPO = 'https://github.com/Schnielz87/PowerIPTV';
const BRANCH = 'claude/happy-mccarthy-fhkx19';

const t = (s) => new TextRun(s);
const b = (s) => new TextRun({ text: s, bold: true });
const code = (s) => new TextRun({ text: s, font: 'Consolas', size: 20, color: '0B4F6C' });
const link = (text, url) => new ExternalHyperlink({ link: url, children: [new TextRun({ text, style: 'Hyperlink', color: '0563C1', underline: {} })] });
const p = (runs, opts = {}) => new Paragraph({ spacing: { after: 120 }, ...opts, children: Array.isArray(runs) ? runs : [t(runs)] });
const h1 = (s) => new Paragraph({ heading: HeadingLevel.HEADING_1, spacing: { before: 360, after: 160 }, children: [new TextRun(s)] });
const h2 = (s) => new Paragraph({ heading: HeadingLevel.HEADING_2, spacing: { before: 240, after: 120 }, children: [new TextRun(s)] });
const bullet = (runs) => new Paragraph({ numbering: { reference: 'bul', level: 0 }, spacing: { after: 60 }, children: Array.isArray(runs) ? runs : [t(runs)] });
let listNo = 0;
const REFS = [];
/** Nummerierte Schritte – jede Liste beginnt wieder bei 1. */
function steps(items) {
  const ref = 's' + (++listNo);
  REFS.push(ref);
  return items.map((runs) => new Paragraph({ numbering: { reference: ref, level: 0 }, spacing: { after: 80 }, children: Array.isArray(runs) ? runs : [t(runs)] }));
}
const box = (label, color, runs) => new Paragraph({
  spacing: { before: 80, after: 160 }, indent: { left: 240 },
  border: { left: { style: BorderStyle.SINGLE, size: 18, color, space: 8 } },
  children: [new TextRun({ text: label + ' ', bold: true, color }), ...(Array.isArray(runs) ? runs : [t(runs)])],
});
const tip = (runs) => box('Tipp:', CYAN, runs);
const warn = (runs) => box('Wichtig:', RED, runs);

const W = 9026;
function table(headers, rows, widths) {
  const cell = (txt, i, head, shade) => new TableCell({
    width: { size: widths[i], type: WidthType.DXA },
    shading: head ? { type: ShadingType.CLEAR, fill: NAVY, color: 'auto' } : (shade ? { type: ShadingType.CLEAR, fill: 'EEF5FA', color: 'auto' } : undefined),
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: [new Paragraph({ children: [new TextRun({ text: txt, bold: head || i === 0, color: head ? 'FFFFFF' : undefined, size: 20 })] })],
  });
  const trs = [new TableRow({ tableHeader: true, children: headers.map((h, i) => cell(h, i, true)) })];
  rows.forEach((r, ri) => trs.push(new TableRow({ children: r.map((c, i) => cell(c, i, false, ri % 2 === 1)) })));
  return new Table({ width: { size: W, type: WidthType.DXA }, columnWidths: widths, rows: trs });
}

const c = [];
// ---------- Titel ----------
c.push(new Paragraph({ spacing: { before: 2200, after: 200 }, alignment: AlignmentType.CENTER, children: [new TextRun({ text: 'PowerIPTV by Portiva', bold: true, size: 60, color: NAVY })] }));
c.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 400 }, children: [new TextRun({ text: 'In den Google Play Store – Schritt für Schritt', size: 36, color: CYAN })] }));
c.push(new Paragraph({ alignment: AlignmentType.CENTER, children: [t('Konto anlegen · App einreichen · Test mit 12 Personen · Veröffentlichen · Automatische Updates von GitHub')] }));
c.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 200 }, children: [new TextRun({ text: 'Stand: Oktober 2026 (Version 1.1.111)', color: '666666' })] }));
c.push(new Paragraph({ children: [new PageBreak()] }));
c.push(new TableOfContents('Inhalt', { hyperlink: true, headingStyleRange: '1-2' }));
c.push(new Paragraph({ children: [new PageBreak()] }));

// ---------- 1. Ueberblick ----------
c.push(h1('1. Kurz zusammengefasst'));
c.push(p([b('Ja – nach einer einmaligen Einrichtung geht jede Änderung, die du bei mir (Claude) anstößt, automatisch von GitHub an Google Play. '),
  t('Google prüft jedes Update noch kurz (meist wenige Stunden, manchmal 1–3 Tage), danach bekommen alle Nutzer es automatisch über den Play Store.')]));
c.push(p('Der Weg dorthin besteht aus drei Teilen:'));
c.push(table(['Teil', 'Was passiert', 'Dauer'], [
  ['A. Einmalig: Konto & App', 'Entwicklerkonto (25 US-Dollar einmalig), Ausweis-Prüfung, App mit Texten/Bildern/Datenschutz anlegen', '1–3 Tage'],
  ['B. Pflicht-Test', 'Geschlossener Test mit mindestens 12 Personen, 14 Tage am Stück (gilt für private Konten)', 'mind. 14 Tage'],
  ['C. Veröffentlichen + Automatik', 'Produktion beantragen, Google prüft, App geht live. Danach Upload-Schlüssel bei GitHub hinterlegen → Updates laufen automatisch', 'ca. 1 Woche'],
], [2600, 4826, 1600]));
c.push(p(''));
c.push(h2('Was ich (Claude) schon für dich vorbereitet habe'));
c.push(bullet([b('Eigene Play-Store-Variante der App: '), t('GitHub baut jetzt bei jeder Änderung zusätzlich ein „App-Bundle“ (.aab) – das Format, das Google verlangt.')]));
c.push(bullet([b('Technische Pflichten erfüllt: '), t('Android 16 (API 36) als Ziel, 16-KB-Speicherseiten (neue VLC- und WireGuard-Bibliotheken).')]));
c.push(bullet([b('Play-Regeln beachtet: '), t('In der Play-Version gibt es keine eigene Update-Funktion (Updates kommen vom Play Store) und kein VPN (Google erlaubt geräteweite VPNs nur in reinen VPN-Apps). Die GitHub-Version behält beides.')]));
c.push(bullet([b('Fertige Unterlagen im Ordner „Android/play“: '), t('Datenschutzerklärung, Store-Texte, App-Symbol (512×512) und Werbegrafik (1024×500).')]));
c.push(bullet([b('Automatischer Upload: '), t('Ein GitHub-Schritt („play“) schickt jedes neue App-Bundle an Google Play – er ist schon eingebaut und wird aktiv, sobald du in Teil C den Schlüssel hinterlegst.')]));

c.push(h2('Wichtig vorab'));
c.push(warn([t('Google ist bei IPTV-Apps streng. Die App darf '), b('keine'), t(' Sender oder Playlists mitbringen und nicht mit Sendern, Sport-Events oder Logos von Sendern werben. Das ist bei PowerIPTV so umgesetzt – bitte auch Screenshots ohne bekannte Senderlogos/Pay-TV-Inhalte machen (siehe Schritt 5).')]));
c.push(bullet([b('Gleicher Paketname: '), t('Play-Version und GitHub-Version heißen intern beide „com.poweriptv.app“. Wer von der GitHub-APK zur Play-Version wechseln will, muss die App einmal deinstallieren (Zugänge vorher per Backup/QR sichern).')]));
c.push(bullet([b('GitHub-Version bleibt: '), t('Fire TV, Samsung, Windows, iOS, Enigma2 und die Android-APK laufen weiter wie bisher über GitHub.')]));
c.push(bullet([b('Entwickler-Verifizierung: '), t('Google verlangt ab 2026/2027 auch für APKs außerhalb des Play Stores einen bestätigten Entwickler. Mit deinem Play-Konto bist du bestätigt – so funktioniert auch die GitHub-APK weiterhin problemlos.')]));

// ---------- 2. Konto ----------
c.push(h1('2. Schritt 1: Google-Play-Entwicklerkonto anlegen'));
c.push(...steps([
  [t('Am PC öffnen: '), link('play.google.com/console', 'https://play.google.com/console'), t(' und mit deinem Google-Konto anmelden (am besten ein eigenes Konto nur für die App).')],
  [t('Kontotyp wählen: '), b('„Für mich selbst“ (privat)'), t('. Ein Organisationskonto braucht eine D-U-N-S-Nummer (Firma) – dafür entfiele allerdings der 14-Tage-Test.')],
  [t('Entwicklername eingeben: '), b('Portiva'), t(' (wird im Store unter dem App-Namen angezeigt).')],
  'Kontaktdaten, Telefonnummer und E-Mail eingeben und bestätigen.',
  [t('Registrierungsgebühr '), b('25 US-Dollar'), t(' (einmalig, ca. 23 €) per Karte bezahlen.')],
  [t('Identität bestätigen: Ausweis hochladen. Die Prüfung dauert meist 1–2 Tage. Zusätzlich verlangt Google, dass du die '), b('Play Console App'), t(' auf deinem Android-Handy installierst und dich dort einmal anmeldest (Gerätebestätigung).')],
]));
c.push(tip('Bei privaten Konten zeigt Google deinen Namen und dein Land im Store an. Eine Adresse wird nur angezeigt, wenn du Geld mit der App verdienst (In-App-Käufe/Bezahl-App) – das ist bei PowerIPTV nicht der Fall.'));

// ---------- 3. Datenschutz ----------
c.push(h1('3. Schritt 2: Datenschutzerklärung fertig machen'));
c.push(p('Google verlangt einen Link zu einer Datenschutzerklärung. Die Erklärung ist fertig geschrieben – es fehlen nur deine Kontaktdaten (Pflicht nach DSGVO).'));
c.push(...steps([
  [t('Öffne: '), link('Android/play/DATENSCHUTZ.md auf GitHub', `${REPO}/blob/${BRANCH}/Android/play/DATENSCHUTZ.md`)],
  [t('Oben rechts auf den Stift (Bearbeiten) klicken und die Platzhalter in eckigen Klammern ersetzen: '), b('Name, Anschrift, E-Mail-Adresse'), t('. Alternativ schreibst du mir die Daten, dann trage ich sie ein.')],
  'Unten auf „Commit changes“ klicken.',
  [t('Diesen Link brauchst du später in der Play Console: '), code(`${REPO}/blob/${BRANCH}/Android/play/DATENSCHUTZ.md`)],
]));
c.push(tip('Die E-Mail-Adresse wird öffentlich sichtbar. Am besten eine eigene Adresse nur für die App anlegen (z. B. portiva.app@…).'));

// ---------- 4. App anlegen ----------
c.push(h1('4. Schritt 3: App in der Play Console anlegen'));
c.push(...steps([
  'In der Play Console auf „App erstellen“ klicken.',
  [t('App-Name: '), b('PowerIPTV – IPTV Player'), t(' · Standardsprache: '), b('Deutsch – de-DE'), t(' · App oder Spiel: '), b('App'), t(' · Kostenlos oder kostenpflichtig: '), b('Kostenlos'), t('.')],
  'Die Erklärungen (Richtlinien, US-Exportgesetze) bestätigen und „App erstellen“ klicken.',
]));
c.push(p('Danach zeigt dir die Play Console im „Dashboard“ eine Aufgabenliste. Die folgenden Schritte arbeiten diese Liste ab.'));

// ---------- 5. App-Bundle ----------
c.push(h1('5. Schritt 4: Das App-Bundle (.aab) von GitHub holen'));
c.push(p('Das erste Hochladen muss einmal von Hand passieren – erst danach darf GitHub automatisch hochladen.'));
c.push(...steps([
  [t('Öffne '), link('github.com/Schnielz87/PowerIPTV/actions', `${REPO}/actions`), t(' (du musst bei GitHub angemeldet sein).')],
  'Den obersten Eintrag mit grünem Haken anklicken.',
  [t('Ganz unten bei „Artifacts“ auf '), b('PowerIPTV-Play-Bundle'), t(' klicken – es lädt eine ZIP-Datei herunter.')],
  [t('ZIP entpacken. Darin liegt '), code('PowerIPTV-Play-v1.1.XXX.aab'), t('.')],
]));
c.push(tip('Artefakte hebt GitHub 90 Tage auf. Brauchst du ein frisches, sag mir einfach Bescheid – jede kleine Änderung erzeugt ein neues.'));

// ---------- 6. Store-Eintrag ----------
c.push(h1('6. Schritt 5: Store-Eintrag (Texte & Bilder)'));
c.push(p([t('Play Console → '), b('Wachstum → Store-Präsenz → Haupteintrag im Play Store'), t('. Alle Texte stehen zum Kopieren in '), link('Android/play/STORE-TEXTE.md', `${REPO}/blob/${BRANCH}/Android/play/STORE-TEXTE.md`), t('.')]));
c.push(table(['Feld', 'Was eintragen'], [
  ['App-Name', 'PowerIPTV – IPTV Player'],
  ['Kurzbeschreibung', 'aus STORE-TEXTE.md (max. 80 Zeichen)'],
  ['Vollständige Beschreibung', 'aus STORE-TEXTE.md'],
  ['App-Symbol', 'Android/play/icon-512.png'],
  ['Vorstellungsgrafik', 'Android/play/feature-graphic-1024x500.png'],
  ['Smartphone-Screenshots', 'mind. 2, besser 4–8 (selbst am Handy machen: Power + Leiser)'],
  ['Tablet-Screenshots (optional)', 'für 7"- und 10"-Tablets – erhöht die Sichtbarkeit'],
  ['Kategorie', 'Video-Player & -Editoren'],
  ['Kontakt-E-Mail', 'deine App-E-Mail-Adresse'],
], [3000, 6026]));
c.push(p(''));
c.push(warn('Screenshots am besten mit einem Test-Zugang oder freien Sendern machen. Keine Bilder von Pay-TV, Sport-Events, Kinofilmen oder großen Senderlogos – das ist der häufigste Ablehnungsgrund bei IPTV-Apps.'));

// ---------- 7. App-Inhalte ----------
c.push(h1('7. Schritt 6: „App-Inhalte“ ausfüllen (Richtlinien)'));
c.push(p([t('Play Console → '), b('Richtlinien → App-Inhalte'), t('. Hier meine Empfehlungen für jede Frage:')]));
c.push(table(['Bereich', 'Antwort / Empfehlung'], [
  ['Datenschutzerklärung', 'Link aus Schritt 2 einfügen'],
  ['Werbung', 'Nein, die App enthält keine Werbung'],
  ['App-Zugriff', '„Alle oder einige Funktionen sind eingeschränkt“ → Anleitung + Test-Zugang für Google-Prüfer angeben (siehe unten)'],
  ['Einstufung des Inhalts', 'Fragebogen: Kategorie „Alle anderen App-Typen“. Gewalt/Sex/Sprache: Nein. Nutzer können Inhalte austauschen: Nein. Die App spielt nur Inhalte ab, die der Nutzer selbst einträgt.'],
  ['Zielgruppe', '18 Jahre und älter (vermeidet die strengen Familien-Regeln; PowerIPTV hat zwar eine Kindersicherung, richtet sich aber an Erwachsene)'],
  ['Nachrichten-App', 'Nein'],
  ['Datensicherheit', 'Siehe Abschnitt unten'],
  ['Behörden-/Finanz-/Gesundheits-Apps', 'Nein'],
  ['Vordergrunddienste', 'Typ „Datensynchronisierung“: für Downloads und Aufnahmen. Google verlangt eine kurze Beschreibung + ein Video-Link (z. B. Bildschirmaufnahme eines Downloads, als „nicht gelistet“ auf YouTube)'],
  ['Exakte Wecker (Alarme)', 'Falls gefragt: für Erinnerungen an Sendungen im TV-Guide, vom Nutzer selbst gesetzt'],
], [2600, 6426]));
c.push(p(''));
c.push(h2('Test-Zugang für die Google-Prüfer'));
c.push(p('Google muss die App ausprobieren können. Ohne funktionierenden Zugang wird sie abgelehnt. Trage unter „App-Zugriff“ ein:'));
c.push(bullet('Eine kurze Anleitung: „App öffnen → Zugang hinzufügen → Manuell → M3U-Link einfügen → Speichern.“'));
c.push(bullet('Einen legalen Test-Zugang, z. B. eine M3U-Liste mit frei empfangbaren öffentlich-rechtlichen Streams oder einen Test-Zugang deines Anbieters, der nur legale Inhalte enthält.'));
c.push(warn('Niemals einen Zugang mit Pay-TV-, Sky-, DAZN- oder ähnlichen Inhalten angeben – das führt sicher zur Sperre des Kontos.'));

c.push(h2('Datensicherheit (Formular)'));
c.push(p('PowerIPTV schickt dir als Entwickler keinerlei Daten, hat keine Werbung und kein Tracking. Zugangsdaten bleiben verschlüsselt auf dem Gerät und gehen nur an den Server, den der Nutzer selbst einträgt. Meine Empfehlung für das Formular:'));
c.push(bullet([b('Erhebt oder teilt die App Nutzerdaten? '), t('Nein. (Verbindungen gehen nur an vom Nutzer selbst gewählte Dienste.)')]));
c.push(bullet([b('Verschlüsselung bei der Übertragung: '), t('ehrlich beantworten – die Verbindung zum IPTV-Anbieter ist nur verschlüsselt, wenn dessen Server „https“ nutzt.')]));
c.push(bullet([b('Löschen von Daten: '), t('„Nutzer können Daten in der App bzw. durch Deinstallation löschen.“')]));
c.push(tip('Fragt Google nach, schreib mir die Rückfrage – ich formuliere dir eine passende Antwort.'));

// ---------- 8. Test ----------
c.push(h1('8. Schritt 7: Geschlossener Test mit 12 Personen (14 Tage)'));
c.push(p('Bei privaten Konten (angelegt nach November 2023) verlangt Google vor der Veröffentlichung einen Test: mindestens 12 Personen müssen 14 Tage am Stück dabei sein.'));
c.push(...steps([
  [t('Play Console → '), b('Testen und veröffentlichen → Tests → Geschlossener Test'), t(' → Track „Alpha“ → „Release erstellen“.')],
  [t('Google Play App-Signatur: '), b('„Weiter“ / Standard übernehmen'), t(' (Google verwaltet den App-Schlüssel sicher).')],
  'Das .aab aus Schritt 4 hochladen. Versionsname und Notizen füllt die Console selbst aus; kurz „Erste Version“ eintragen.',
  [t('Tab '), b('„Tester“'), t(': E-Mail-Liste anlegen mit mindestens '), b('12 Google-Konten'), t(' (Familie, Freunde – am besten 15, falls jemand abspringt).')],
  'Release speichern → „Zur Überprüfung senden“. Google prüft (1–3 Tage).',
  [t('Danach den '), b('Link zur Teilnahme'), t(' (unter „Tester“) an alle schicken. Jede Person muss den Link öffnen, „Tester werden“ tippen und die App aus dem Play Store installieren.')],
  [b('14 Tage warten'), t(' – alle bleiben angemeldet und öffnen die App ab und zu. Sinkt die Zahl unter 12, beginnt die Frist neu.')],
]));
c.push(tip('Schon während des Tests funktioniert die Automatik (Teil C). Jede Änderung landet dann direkt bei deinen Testern – praktisch zum Ausprobieren.'));

// ---------- 9. Produktion ----------
c.push(h1('9. Schritt 8: Für alle veröffentlichen'));
c.push(...steps([
  [t('Nach den 14 Tagen erscheint im Dashboard '), b('„Zugriff auf Produktion beantragen“'), t('. Ein paar Fragen zum Test beantworten (wie viele Tester, was wurde verbessert – ich helfe gern beim Formulieren).')],
  'Google entscheidet meist innerhalb einer Woche.',
  [t('Dann: '), b('Produktion → Release erstellen'), t(' → „Release aus der Mediathek hinzufügen“ (das neueste .aab wählen) → Länder: alle oder nur Deutschland/Österreich/Schweiz → „Zur Überprüfung senden“.')],
  'Nach der Prüfung ist PowerIPTV im Play Store für alle zu finden.',
]));

// ---------- 10. Automatik ----------
c.push(h1('10. Schritt 9: Automatische Updates von GitHub einrichten'));
c.push(p('Damit GitHub neue Versionen selbst an Google Play schicken darf, bekommt es einen eigenen „Dienstkonto“-Schlüssel. Das machst du einmal:'));
c.push(h2('a) Dienstkonto bei Google Cloud anlegen'));
c.push(...steps([
  [t('Öffne '), link('console.cloud.google.com', 'https://console.cloud.google.com'), t(' (gleiches Google-Konto) und lege ein Projekt an, z. B. „PowerIPTV“.')],
  [t('Menü → '), b('APIs & Dienste → Bibliothek'), t(' → „Google Play Android Developer API“ suchen → '), b('Aktivieren'), t('.')],
  [t('Menü → '), b('IAM & Verwaltung → Dienstkonten'), t(' → „Dienstkonto erstellen“ → Name „github-upload“ → Fertig (keine Rollen nötig).')],
  [t('Das neue Dienstkonto anklicken → Tab '), b('Schlüssel'), t(' → „Schlüssel hinzufügen“ → „Neuen Schlüssel erstellen“ → '), b('JSON'), t('. Eine .json-Datei wird heruntergeladen.')],
  'Die E-Mail-Adresse des Dienstkontos kopieren (sieht aus wie github-upload@poweriptv-12345.iam.gserviceaccount.com).',
]));
c.push(warn('Die JSON-Datei ist ein Generalschlüssel für deine App. Niemals per WhatsApp/E-Mail verschicken, nicht in den Chat mit mir kopieren und nicht ins GitHub-Repository hochladen – nur wie unten beschrieben als GitHub-Secret eintragen.'));
c.push(h2('b) Dienstkonto in der Play Console freigeben'));
c.push(...steps([
  [t('Play Console → '), b('Nutzer und Berechtigungen'), t(' → „Neue Nutzer einladen“.')],
  'Die E-Mail-Adresse des Dienstkontos eintragen.',
  [t('Tab „App-Berechtigungen“ → PowerIPTV hinzufügen → Häkchen bei '), b('„Releases für Test-Tracks verwalten“'), t(' und '), b('„Releases in der Produktion veröffentlichen …“'), t('.')],
  '„Nutzer einladen“ klicken. (Es kann bis zu 24 Stunden dauern, bis die Freigabe wirkt.)',
]));
c.push(h2('c) Schlüssel bei GitHub hinterlegen'));
c.push(...steps([
  [t('Öffne '), link('github.com/Schnielz87/PowerIPTV/settings/secrets/actions', `${REPO}/settings/secrets/actions`), t('.')],
  [t('„New repository secret“ → Name: '), code('PLAY_SERVICE_ACCOUNT_JSON'), t(' → in „Secret“ den '), b('kompletten Inhalt'), t(' der JSON-Datei einfügen (mit Editor öffnen, alles markieren, kopieren) → „Add secret“.')],
  [t('Oben auf den Reiter '), b('„Variables“'), t(' wechseln und zwei Variablen anlegen („New repository variable“):')],
]));
c.push(table(['Variable', 'Wert', 'Bedeutung'], [
  ['PLAY_TRACK', 'alpha', 'Während des 14-Tage-Tests: Updates gehen an deine Tester'],
  ['PLAY_TRACK', 'production', 'Nach der Freigabe: Updates gehen an alle Nutzer'],
  ['PLAY_STATUS', 'completed', 'Update wird sofort zur Prüfung eingereicht und danach automatisch veröffentlicht'],
  ['PLAY_STATUS', 'draft', 'Update landet nur als Entwurf in der Play Console – du klickst selbst auf „Veröffentlichen“'],
], [2000, 1700, 5326]));
c.push(p(''));
c.push(tip('Solange die App noch nie veröffentlicht wurde, akzeptiert Google nur „draft“. Lass PLAY_STATUS also bis zum ersten Release auf „draft“ (oder lege die Variable noch gar nicht an – „draft“ und „internal“ sind die Standardwerte).'));
c.push(tip('Willst du die Automatik pausieren, lösche einfach das Secret oder setze PLAY_STATUS auf „draft“.'));

// ---------- 11. Ablauf ----------
c.push(h1('11. So läuft es danach – jedes Update'));
c.push(...steps([
  [b('Du'), t(' sagst mir, was geändert werden soll (wie bisher).')],
  [b('Ich'), t(' ändere den Code für alle Varianten und lade ihn zu GitHub hoch.')],
  [b('GitHub'), t(' baut automatisch alles: APK, Windows, Samsung, Fire TV, iOS, Enigma2 – und zusätzlich das Play-App-Bundle. Die Versionsnummer zählt automatisch hoch (z. B. 1.1.111 → 1.1.112).')],
  [b('GitHub'), t(' veröffentlicht wie gewohnt das Release mit allen Downloads '), b('und'), t(' schickt das App-Bundle an Google Play (Schritt „play“).')],
  [b('Google'), t(' prüft das Update (meist wenige Stunden, selten bis 3 Tage).')],
  [b('Nutzer'), t(' bekommen das Update automatisch über den Play Store – genau wie bei jeder anderen App.')],
]));
c.push(table(['Variante', 'Wie kommen Updates zum Nutzer?'], [
  ['Android aus dem Play Store', 'Automatisch über Google Play (nach Google-Prüfung)'],
  ['Android-APK von GitHub', 'App meldet sich selbst („Update verfügbar“) – wie bisher'],
  ['Windows, Samsung, Fire TV, iOS, Enigma2', 'Unverändert über GitHub-Releases bzw. die eingebaute Update-Funktion'],
], [3600, 5426]));
c.push(p(''));
c.push(warn([t('Jede Änderung, die ich hochlade, wird zu einem Play-Update. Bei vielen kleinen Änderungen an einem Tag prüft Google jede einzeln. Wenn du lieber sammeln willst: '), b('PLAY_STATUS = draft'), t(' – dann veröffentlichst du in der Play Console selbst, wann es dir passt (es zählt immer der neueste Entwurf).')]));

// ---------- 12. Probleme ----------
c.push(h1('12. Häufige Probleme'));
c.push(table(['Meldung / Problem', 'Lösung'], [
  ['App abgelehnt: „Urheberrechte / Inhalte Dritter“', 'Screenshots und Beschreibung prüfen: keine Senderlogos, keine Pay-TV-/Sport-Inhalte. Einspruch mit Hinweis „reiner Player, keine Inhalte“. Schick mir die Mail von Google.'],
  ['App abgelehnt: „App-Zugriff / keine Anmeldedaten“', 'Funktionierenden, legalen Test-Zugang unter „App-Zugriff“ eintragen.'],
  ['GitHub-Schritt „play“ rot: „Only releases with status draft may be created on draft app“', 'PLAY_STATUS auf „draft“ lassen, bis die App einmal veröffentlicht wurde.'],
  ['GitHub-Schritt „play“ rot: „The caller does not have permission“', 'Freigabe des Dienstkontos (Schritt 9b) prüfen; bis zu 24 h warten.'],
  ['GitHub-Schritt „play“ rot: „Package not found“', 'Das erste App-Bundle muss einmal von Hand hochgeladen werden (Schritt 7).'],
  ['„Version code already used“', 'Tritt nur auf, wenn ein Bundle doppelt hochgeladen wird – einfach ignorieren, das nächste Update hat eine neue Nummer.'],
  ['Tester sehen die App nicht', 'Sie müssen den Teilnahme-Link mit genau dem Google-Konto öffnen, das auf der Liste steht.'],
], [3600, 5426]));
c.push(p(''));
c.push(p([b('Bei jedem Schritt gilt: '), t('Mach einen Screenshot und schick ihn mir – ich sage dir, was zu tun ist.')]));

const doc = new Document({
  creator: 'Portiva', title: 'PowerIPTV – Google Play Store Anleitung',
  styles: {
    default: { document: { run: { font: 'Calibri', size: 22 } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 32, bold: true, color: NAVY }, paragraph: { outlineLevel: 0 } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 26, bold: true, color: CYAN }, paragraph: { outlineLevel: 1 } },
    ],
  },
  numbering: { config: [
    { reference: 'bul', levels: [{ level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } }] },
    ...REFS.map((r) => ({ reference: r, levels: [{ level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 300 } } } }] })),
  ] },
  features: { updateFields: true },
  sections: [{
    properties: { page: { margin: { top: 1440, bottom: 1440, left: 1440, right: 1440 } } },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: 'PowerIPTV – Google Play Store Anleitung · Seite ', color: '888888', size: 18 }), new TextRun({ children: [PageNumber.CURRENT], color: '888888', size: 18 })] })] }) },
    children: c,
  }],
});

const out = path.join(__dirname, '..', '..', 'Android', 'Play-Store-Anleitung.docx');
Packer.toBuffer(doc).then((buf) => { fs.writeFileSync(out, buf); console.log('ok', out); });
