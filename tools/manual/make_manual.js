const fs = require('fs');
const { Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell, WidthType, ShadingType,
  AlignmentType, LevelFormat, TableOfContents, PageBreak, Footer, PageNumber, BorderStyle } = require('docx');

const CYAN = '1E88B8';
const p = (text, opts = {}) => new Paragraph({ spacing: { after: 120 }, ...opts, children: Array.isArray(text) ? text : [new TextRun(text)] });
const b = (t) => new TextRun({ text: t, bold: true });
const t = (s) => new TextRun(s);
const h1 = (s) => new Paragraph({ heading: HeadingLevel.HEADING_1, spacing: { before: 360, after: 160 }, children: [new TextRun(s)] });
const h2 = (s) => new Paragraph({ heading: HeadingLevel.HEADING_2, spacing: { before: 240, after: 120 }, children: [new TextRun(s)] });
const bullet = (runs) => new Paragraph({ numbering: { reference: 'bul', level: 0 }, spacing: { after: 60 }, children: Array.isArray(runs) ? runs : [t(runs)] });
const step = (runs, ref = 'num') => new Paragraph({ numbering: { reference: ref, level: 0 }, spacing: { after: 60 }, children: Array.isArray(runs) ? runs : [t(runs)] });
const tip = (s) => new Paragraph({
  spacing: { before: 80, after: 160 }, indent: { left: 240 },
  border: { left: { style: BorderStyle.SINGLE, size: 18, color: CYAN, space: 8 } },
  children: [new TextRun({ text: 'Tipp: ', bold: true, color: CYAN }), t(s)],
});

const W = 9026; // A4 Textbreite bei 2,54 cm Raendern
function table(headers, rows, widths) {
  const cell = (txt, head) => new TableCell({
    width: { size: 0, type: WidthType.DXA },
    shading: head ? { type: ShadingType.CLEAR, fill: '1E3A5F', color: 'auto' } : undefined,
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: [new Paragraph({ children: [new TextRun({ text: txt, bold: head, color: head ? 'FFFFFF' : undefined, size: 20 })] })],
  });
  const mk = (cells, head) => new TableRow({ tableHeader: head, children: cells.map((c, i) => { const x = cell(c, head); x.options; return new TableCell({
    width: { size: widths[i], type: WidthType.DXA },
    shading: head ? { type: ShadingType.CLEAR, fill: '1E3A5F', color: 'auto' } : (rowsIndex(cells) % 2 ? { type: ShadingType.CLEAR, fill: 'EEF5FA', color: 'auto' } : undefined),
    margins: { top: 60, bottom: 60, left: 100, right: 100 },
    children: [new Paragraph({ children: [new TextRun({ text: c, bold: head || i === 0, color: head ? 'FFFFFF' : undefined, size: 20 })] })],
  }); }) });
  let idx = 0; const rowsIndex = () => idx;
  const trs = [mk(headers, true)];
  rows.forEach((r, i) => { idx = i; trs.push(mk(r, false)); });
  return new Table({ width: { size: W, type: WidthType.DXA }, columnWidths: widths, rows: trs });
}

const c = [];
// Titelseite
c.push(new Paragraph({ spacing: { before: 2400, after: 200 }, alignment: AlignmentType.CENTER, children: [new TextRun({ text: 'Portiva – PowerIPTV', bold: true, size: 64, color: '1E3A5F' })] }));
c.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [new TextRun({ text: 'Bedienungsanleitung', size: 40, color: CYAN })] }));
c.push(new Paragraph({ alignment: AlignmentType.CENTER, children: [t('Für Handy, Tablet, Fire TV und Android TV')] }));
c.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 120 }, children: [new TextRun({ text: 'Stand: Version 1.1.61 · Oktober 2026', color: '666666' })] }));
c.push(new Paragraph({ children: [new PageBreak()] }));
c.push(new Paragraph({ heading: HeadingLevel.HEADING_1, children: [t('Inhalt')] }));
c.push(new TableOfContents('Inhalt', { hyperlink: true, headingStyleRange: '1-2' }));
c.push(new Paragraph({ children: [new PageBreak()] }));

// 1
c.push(h1('1. Was die App kann'));
c.push(p('PowerIPTV ist ein IPTV-Player für Live-TV, Filme und Serien deines Anbieters (Xtream Codes oder M3U). Eine einzige App-Datei läuft auf Handy, Tablet, Fire TV und Android TV und passt die Oberfläche automatisch an.'));
table; c.push(table(['Bereich', 'Funktionen'], [
  ['Anmeldung', 'Xtream Codes, M3U-Link oder M3U-Datei; mehrere Profile'],
  ['Live-TV', 'Senderliste im Bild, letzter Sender, Timeshift (Pause), Aufnahmen, Catch-up, Multi-View'],
  ['Filme & Serien', 'Weiterschauen ab Stopp, nächste Folge automatisch, Intro überspringen, FSK, Vorschaubilder beim Spulen'],
  ['Programmführer', 'EPG-Zeitraster, Jetzt/Danach im Player, Erinnerungen, Aufnahme planen'],
  ['Player', 'Audiospur, Untertitel (inkl. Größe), Bildformat, Geschwindigkeit, Sleep-Timer, VLC-Kompatibilitätsmodus'],
  ['Organisation', 'Favoriten, eigene Listen, Zuletzt gesehen, Kategorien anheften/ausblenden, Suche, Filter'],
  ['Weitere', 'Offline-Downloads, VPN mit Kill-Switch, Kindersicherung, KI-Empfehlungen, Google Cast/Smart View, Backup'],
], [2400, 6626]));

// 2
c.push(h1('2. Installation & Updates'));
c.push(h2('Handy und Tablet'));
c.push(step('Auf GitHub unter „Releases“ die neueste Version öffnen.', 'n1'));
c.push(step('Die APK-Datei herunterladen und antippen.', 'n1'));
c.push(step('Falls gefragt: „Installation aus unbekannten Quellen“ für den Browser erlauben.', 'n1'));
c.push(h2('Fire TV / Android TV'));
c.push(step('App „Downloader“ aus dem Amazon App Store installieren.', 'n2'));
c.push(step('Einstellungen → Mein Fire TV → Entwickleroptionen → „Apps unbekannter Herkunft“ für Downloader aktivieren.', 'n2'));
c.push(step('In Downloader den Link zur APK aus den Releases eingeben, laden und installieren.', 'n2'));
c.push(tip('Updates werden einfach über die vorhandene App installiert – Profile und Einstellungen bleiben erhalten.'));

// 3
c.push(h1('3. Erste Einrichtung'));
c.push(step('App öffnen → „Profil hinzufügen“.', 'n3'));
c.push(step([t('Typ wählen: '), b('Xtream Codes'), t(' (Server, Benutzername, Passwort), '), b('M3U-Link'), t(' oder '), b('M3U-Datei'), t('.')], 'n3'));
c.push(step('Speichern – die Senderlisten werden geladen und zwischengespeichert.', 'n3'));
c.push(p('Die Listen werden automatisch alle 24 Stunden aktualisiert; über das Aktualisieren-Symbol geht es jederzeit manuell.'));

// 4
c.push(h1('4. Startseite'));
c.push(bullet([b('Große Kacheln: '), t('Live TV, Filme, Serien.')]));
c.push(bullet([b('Kleine Kacheln: '), t('Suche, Programmführer, Favoriten, Aufnahmen, Downloads, Empfehlungen, Multi-View, VPN, Einstellungen.')]));
c.push(bullet([b('Weiterschauen: '), t('angefangene Filme mit Fortschritt und Restzeit sowie die zuletzt gesehene Folge jeder Serie.')]));
c.push(bullet([b('Zuletzt gesehen: '), t('getrennt nach Live TV, Filme und Serien. Ein Tipp auf „Alle anzeigen ›“ öffnet die große Übersicht mit Reitern; lange drücken entfernt einen Eintrag.')]));

// 5
c.push(h1('5. Live TV, Filme & Serien durchsuchen'));
c.push(bullet([b('Kategorie-Spalte links: '), t('Favoriten, Zuletzt gesehen, Alle und die Kategorien des Anbieters.')]));
c.push(bullet([b('Sprache (Globus): '), t('zeigt nur Kategorien einer Sprache, z.B. DE.')]));
c.push(bullet([b('Filter (Trichter): '), t('Sortierung, Genre, Bewertung, Jahr – bleibt gespeichert.')]));
c.push(bullet([b('Suche: '), t('sucht in der gewählten Kategorie („Nur hier“) oder überall.')]));
c.push(bullet([b('Kategorien anheften/ausblenden: '), t('Kategorie lange drücken (TV: OK gedrückt halten oder Menü-Taste) → „Oben anheften“ bzw. „Ausblenden“. Ausgeblendete lassen sich unten in der Liste wieder anzeigen.')]));
c.push(tip('Am TV: Im Suchfeld mit OK die Tastatur öffnen, mit ↓ oder „Suchen“ zu den Treffern springen, mit Zurück nur das Suchfeld verlassen.'));

// 6
c.push(h1('6. Den Player bedienen'));
c.push(p('Beide Player (Standard und VLC) sehen gleich aus: oben Titel und Optionen, in der Mitte die Steuerung, unten der Zeitstrahl. Die Leisten verschwinden 5 Sekunden nach der letzten Bedienung.'));
c.push(table(['Inhalt', 'Pfeile in der Mitte'], [
  ['Live-TV', '⏮ voriger Sender · ⏸ · ⏭ nächster Sender (kein Spulen bei Live)'],
  ['Serie', '⏮ vorige Folge · ⟲ 10 s · ⏸ · ⟳ 10 s · ⏭ nächste Folge'],
  ['Film', '⟲ 10 s zurück · ⏸ · ⟳ 10 s vor'],
], [2200, 6826]));
c.push(table(['Aktion', 'Handy / Tablet', 'Fernbedienung (Fire TV)'], [
  ['Leiste ein/aus', 'Einmal tippen', 'OK (Zurück blendet aus)'],
  ['10 s spulen', 'Doppelt tippen rechts/links', '→ / ←'],
  ['Beliebige Stelle', 'Zeitstrahl ziehen (mit Vorschaubild)', 'Zeitstrahl anwählen, ←/→'],
  ['Pause / Weiter', 'Mittlerer Knopf', 'Play/Pause-Taste oder OK bei Leiste'],
  ['Einstellungen', 'Zahnrad oben rechts', 'Menü-Taste (≡) oder ↑ zum Zahnrad'],
  ['Sender wechseln (Live)', 'Pfeile ⏮ / ⏭ in der Mitte', '↑ / ↓'],
  ['Nächste Folge / Intro', 'Knopf antippen', 'OK, solange der Knopf sichtbar ist'],
  ['Senderliste (Live)', 'Listen-Symbol oben', '←'],
  ['Letzter Sender (Live)', 'Pfeil-Symbol oben', '→'],
  ['Bildformat schnell', 'Zahnrad → Bildformat', 'Blaue Taste'],
  ['Player schließen', 'Zurück-Pfeil', 'Zurück (bei ausgeblendeter Leiste)'],
], [2600, 3200, 3226]));
c.push(h2('Das Zahnrad-Menü'));
c.push(bullet([b('Audiospur: '), t('z.B. Deutsch 5.1 oder Englisch Stereo.')]));
c.push(bullet([b('Untertitel: '), t('Aus oder Sprache wählen; Größe (Klein bis Sehr groß) und dunkler Hintergrund.')]));
c.push(bullet([b('Geschwindigkeit: '), t('0,5× bis 2× (Filme und Serien).')]));
c.push(bullet([b('Bildformat: '), t('Original, Zoom, Strecken, 16:9, 4:3, 21:9, 1,85:1 – das aktive Format wird kurz eingeblendet.')]));
c.push(bullet([b('Sleep-Timer: '), t('15 Minuten bis 2 Stunden; eine Minute vorher kommt ein Hinweis.')]));

// 7
c.push(h1('7. Filme & Serien'));
c.push(bullet([b('Weiterschauen: '), t('Beim Abspielen eines angefangenen Titels fragt die App „Weiterschauen ab …“ oder „Von vorne beginnen“.')]));
c.push(bullet([b('Nächste Folge: '), t('40 Sekunden vor Ende erscheint „Nächste Folge in … s“. OK/„Jetzt abspielen“ startet sofort, Zurück/„Abbrechen“ bleibt bei der Folge.')]));
c.push(bullet([b('Intro überspringen: '), t('Der Knopf erscheint für 7 Sekunden; OK auf der Fernbedienung löst ihn aus. Die App lernt den Vorspann jeder Serie selbst (siehe unten).')]));
c.push(bullet([b('Detailseite: '), t('Großes Hintergrundbild, FSK-Kennzeichen, Beschreibung, Besetzung, Download und Liste. Bei Serien ist die zuletzt gesehene Folge markiert, angefangene Folgen zeigen einen Fortschrittsbalken.')]));
c.push(bullet([b('Vorschaubilder beim Spulen: '), t('Beim Ziehen über den Zeitstrahl. Erlaubt der Anbieter keine zweite Verbindung, schaltet sich die Funktion automatisch ab.')]));
c.push(tip('Für die offizielle FSK bei fast allen Titeln einen kostenlosen TMDB-Schlüssel unter Einstellungen → Altersfreigaben eintragen.'));
c.push(h2('Wie die App den Vorspann lernt'));
c.push(p('Es gibt zwei Verfahren, die sich ergänzen:'));
c.push(bullet([b('Ton-Erkennung (automatisch, Standard-Player): '), t('Die App hört in den ersten 10 Minuten jeder Folge mit und bildet einen Ton-Fingerabdruck. Nach zwei Folgen kennt sie die Vorspann-Musik. Ab der dritten Folge erkennt sie den Vorspann live, egal wo er beginnt, und springt genau an sein Ende. Erkennt sie ihn einmal nicht, lernt sie neu (z.B. neue Staffel).')]));
c.push(bullet([b('Lernen durch Vorspulen (beide Player): '), t('Spulst du in den ersten 10 Minuten über den Vorspann (20 s bis 5 Min.), merkt sich die App Beginn und Ende. In den nächsten Folgen erscheint der Knopf genau dort.')]));
c.push(bullet([b('Zusammenspiel: '), t('Per Ton erkannte Vorspann-Zeiten werden zusätzlich gespeichert – so profitiert auch der VLC-Player davon.')]));
c.push(p('Grenzen: Serien ohne wiederkehrende Vorspann-Musik werden nicht automatisch erkannt. Bei Dolby-Durchleitung an einen AV-Receiver hört die App den Ton nicht mit – dann gilt das Lernen durch Vorspulen.'));

// 8
c.push(h1('8. Live-TV-Funktionen'));
c.push(bullet([b('Timeshift: '), t('Pause bei Live-TV puffert die Sendung; Play spielt zeitversetzt weiter, „LIVE“ springt zurück.')]));
c.push(bullet([b('Aufnahme: '), t('Roter Punkt (oder rote Taste) → bis Sendungsende oder 30–180 Minuten. Während der Aufnahme kommt das Bild direkt aus der Aufnahme – so reicht eine Verbindung. Stoppen: roter Punkt → „Aufnahme stoppen“ oder über die Benachrichtigung.')]));
c.push(bullet([b('Catch-up: '), t('Verpasste Sendungen im Programmführer nachträglich ansehen (wenn der Anbieter es unterstützt).')]));
c.push(bullet([b('VLC-Modus: '), t('Sender mit älterem Videoformat (z.B. RTL, ProSieben, VOX) wechseln automatisch in den VLC-Player.')]));

// 9
c.push(h1('9. Programmführer (EPG) & Erinnerungen'));
c.push(p('Die Kachel „Programm“ zeigt ein Zeitraster aller Sender. Eine Sendung antippen öffnet die Optionen:'));
c.push(bullet('Live ansehen bzw. von Beginn an (Timeshift)'));
c.push(bullet('Nachträglich ansehen (Catch-up)'));
c.push(bullet([b('Erinnern: '), t('5 Minuten vor Beginn kommt eine Benachrichtigung; „Jetzt ansehen“ startet den Sender direkt.')]));
c.push(bullet('Aufnahme planen'));

// 10
c.push(h1('10. Favoriten, Listen, Downloads'));
c.push(bullet([b('Favoriten: '), t('Herz-Symbol auf der Detailseite oder im Player.')]));
c.push(bullet([b('Eigene Listen: '), t('„Liste“ auf der Detailseite, z.B. „Filmabend“ oder „Kinder“.')]));
c.push(bullet([b('Downloads: '), t('„Offline“ lädt Filme und Folgen herunter (mehrere Verbindungen parallel, wenn der Zugang es erlaubt).')]));

// 11
c.push(h1('11. Auf den Fernseher bringen'));
c.push(bullet([b('Google Cast: '), t('Cast-Symbol → Chromecast, Google TV oder Fernseher mit „Chromecast built-in“ im gleichen WLAN.')]));
c.push(bullet([b('Smart View / Bildschirm spiegeln: '), t('Für Samsung-Fernseher und Fire TV (dort „Display-Mirroring“ einschalten). Wird das Handy abgelehnt: am Samsung-TV unter Allgemein → Externe Geräteverwaltung → Geräteverbindungs-Manager → Geräteliste freigeben.')]));
c.push(bullet([b('Multi-View: '), t('Mehrere Sender gleichzeitig; Bild-in-Bild auf dem Handy beim Verlassen der App.')]));

// 12
c.push(h1('12. Einstellungen'));
c.push(table(['Bereich', 'Was man einstellen kann'], [
  ['Player', 'Automatisch / Standard / VLC, Bildwiederholrate (AFR), Bildformat, Vorschaubilder beim Spulen'],
  ['Live-Format & User-Agent', 'Stream-Format (TS/HLS) und Kennung gegenüber dem Anbieter'],
  ['VPN', 'WireGuard-Konfiguration, Auto-Verbinden, Kill-Switch („nur mit VPN abspielen“)'],
  ['Kindersicherung', 'PIN, gesperrte Kategorien, Erwachseneninhalte ausblenden'],
  ['KI-Empfehlungen', 'ChatGPT-Schlüssel und Modell'],
  ['Altersfreigaben (FSK)', 'TMDB-Schlüssel für die offizielle FSK'],
  ['Sichern & Wiederherstellen', 'Alles als Datei sichern (optional mit Zugangsdaten und Passwort) und auf einem anderen Gerät laden'],
  ['Darstellung', 'Ausrichtung auf Handy/Tablet'],
], [2800, 6226]));
c.push(tip('Beim Umzug vom Handy auf den Fire TV: Sicherung mit Passwort erstellen, Datei z.B. per Cloud auf das andere Gerät bringen und dort unter „Sicherung laden“ wiederherstellen.'));

// 13
c.push(h1('13. Hilfe bei Problemen'));
c.push(table(['Problem', 'Lösung'], [
  ['Nur Ton, kein Bild', 'Der Sender nutzt ein altes Format – die App wechselt automatisch zu VLC. Sonst Einstellungen → Player → VLC.'],
  ['Bild bleibt bei Aufnahme stehen', 'Der Zugang erlaubt nur eine Verbindung. Den aufgenommenen Sender schauen oder die Aufnahme stoppen.'],
  ['Update lässt sich nicht installieren', 'Immer die APK aus den Releases verwenden (gleiche Signatur).'],
  ['Cast findet den Fernseher nicht', 'Samsung- und Fire-TV-Geräte können kein Google Cast – „Bildschirm spiegeln“ nutzen.'],
  ['Senderliste veraltet', 'Aktualisieren-Symbol in der Kategorie-Ansicht antippen.'],
], [3000, 6026]));

const doc = new Document({
  creator: 'Portiva', title: 'PowerIPTV – Bedienungsanleitung',
  styles: {
    default: { document: { run: { font: 'Calibri', size: 22 } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', run: { size: 32, bold: true, color: '1E3A5F' } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', run: { size: 26, bold: true, color: CYAN } },
    ],
  },
  numbering: { config: [
    { reference: 'bul', levels: [{ level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } }] },
    ...['n1', 'n2', 'n3', 'num'].map(r => ({ reference: r, levels: [{ level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } }] })),
  ] },
  features: { updateFields: true },
  sections: [{
    properties: { page: { margin: { top: 1440, bottom: 1440, left: 1440, right: 1440 } } },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: 'PowerIPTV – Bedienungsanleitung · Seite ', color: '888888', size: 18 }), new TextRun({ children: [PageNumber.CURRENT], color: '888888', size: 18 })] })] }) },
    children: c,
  }],
});
Packer.toBuffer(doc).then(buf => { fs.writeFileSync(require('path').join(__dirname, '../../docs/PowerIPTV-Anleitung.docx'), buf); console.log('ok'); });
