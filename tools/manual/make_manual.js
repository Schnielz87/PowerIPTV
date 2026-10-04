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
c.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 120 }, children: [new TextRun({ text: 'Stand: Version 1.1.102 · Oktober 2026', color: '666666' })] }));
c.push(new Paragraph({ children: [new PageBreak()] }));
c.push(new Paragraph({ heading: HeadingLevel.HEADING_1, children: [t('Inhalt')] }));
c.push(new TableOfContents('Inhalt', { hyperlink: true, headingStyleRange: '1-2' }));
c.push(new Paragraph({ children: [new PageBreak()] }));

// 1
c.push(h1('1. Was die App kann'));
c.push(p('PowerIPTV ist ein IPTV-Player für Live-TV, Filme und Serien deines Anbieters (Xtream Codes oder M3U). Eine einzige App-Datei läuft auf Handy, Tablet, Fire TV und Android TV und passt die Oberfläche automatisch an. Zusätzlich gibt es eine eigene Version für Windows-PCs (Kapitel 14) und für Samsung Smart TVs ab 2018 (Kapitel 15) sowie für iPhone & iPad (Kapitel 17).'));
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
c.push(p('Alle Dateien liegen im Release bereit. Der Quellcode ist auf GitHub nach Geräten getrennt: Ordner „Android“, „iOS“, „Tizen Samsung“, „Windows“ und „Fire TV Vega“ (neue Amazon-Sticks mit Vega OS).'));
c.push(h2('Handy und Tablet'));
c.push(step('Auf GitHub unter „Releases“ die neueste Version öffnen.', 'n1'));
c.push(step('Die APK-Datei herunterladen und antippen.', 'n1'));
c.push(step('Falls gefragt: „Installation aus unbekannten Quellen“ für den Browser erlauben.', 'n1'));
c.push(h2('Fire TV / Android TV'));
c.push(tip('Gilt für alle Fire TV mit Fire OS (Android): Fire TV Stick (Lite/3. Gen.), Fire TV Stick 4K, 4K Max, 4K Plus, Fire TV Cube. Die NEUEN Sticks mit Vega OS (Fire TV Stick 4K Select, Fire TV Stick HD 2026, Fire TV Stick 4K 2026) können keine APK installieren – dafür gibt es die eigene Fire-TV-Vega-Version (Kapitel 18). Welcher Stick? Einstellungen → Mein Fire TV → Info.'));
c.push(step('App „Downloader“ aus dem Amazon App Store installieren.', 'n2'));
c.push(step('Einstellungen → Mein Fire TV → Entwickleroptionen → „Apps unbekannter Herkunft“ für Downloader aktivieren.', 'n2'));
c.push(step('In Downloader den Link zur APK aus den Releases eingeben, laden und installieren.', 'n2'));
c.push(tip('Updates werden einfach über die vorhandene App installiert – Profile und Einstellungen bleiben erhalten.'));
c.push(h2('Updates direkt in der App'));
c.push(p('Portiva prüft alle 24 Stunden selbst bei GitHub, ob es eine neue Version gibt (ohne Play Store, ohne GitHub-Konto). Gibt es eine, erscheint ein Hinweis auf der Startseite und die Kachel „Update verfügbar!“.'));
c.push(step('Kachel „Update“ (unten neben „Einstellungen“) antippen – Portiva sucht sofort nach der neuesten Version, lädt sie herunter und startet die Installation automatisch.', 'n5'));
c.push(step('Beim ersten Mal fragt Android, ob Portiva Apps installieren darf → erlauben, zurück und erneut antippen.', 'n5'));
c.push(step('Den Android-Installer mit „Aktualisieren“ bestätigen – Zugänge, Favoriten und Einstellungen bleiben erhalten.', 'n5'));
c.push(p('Unter „Update“ lässt sich die automatische Prüfung abschalten und jederzeit manuell „Nach Updates suchen“. Unter Windows lädt Portiva das neue Setup, schließt sich kurz und der Installer aktualisiert die App.'));
c.push(p('Playlist und TV-Guide werden ebenfalls automatisch alle 24 Stunden aktualisiert – auch wenn die App länger geöffnet bleibt. Sofort geht es über die zwei Pfeile oben.'));
c.push(tip('Auf jeder Seite steht oben links das Portiva-„P“ – ein Tipp (am Fernseher: Pfeil hoch, OK) führt direkt zur Startseite.'));
c.push(tip('Im Player: links Helligkeit, rechts Lautstärke (senkrechte Regler zum Ziehen oder Antippen; am Fernseher regelt das die Fernbedienung). Bei Filmen und Serien unten unter dem Zeitstrahl: Seitenverhältnis – Geschwindigkeit – Untertitel (ein/aus). Bei Live TV erscheint unten die Sender-Infoleiste – bei jedem Umschalten und beim Antippen: Senderlogo, „Jetzt“ mit Uhrzeit und Fortschrittsbalken, „Weiter“ (nächste Sendung) und darunter Mehrfachbildschirm – Seitenverhältnis – Senderliste (Mehrfachbildschirm nur Android und Windows). Die Senderliste öffnet sich rechts im Bild. Oben in der Liste: ⟳ „EPG aktualisieren“ (holt das Programm sofort neu vom Anbieter – wie im TV-Guide) und ✕ zum Schließen. Am Griff nach rechts ziehen schiebt die Liste ganz zu. Am Griff links an der Liste lässt sie sich breiter nach links ziehen (Handy, Tablet, iPhone, Windows): Dann steht bei jedem Sender, was gerade läuft (mit Uhrzeit und Fortschrittsbalken) und was danach kommt; zurück nach rechts ziehen macht sie wieder schmal. Solange die Liste offen ist, sind Helligkeit und Lautstärke ausgeblendet, damit das Blättern in der Liste nichts verstellt. Das Zahnrad oben öffnet rechts die Einstellungsleiste: Videospuren, Audiospuren (mit Codec, kb/s, Hz), Untertitelspuren, Untertitel-Einstellungen (Schriftgröße, Hintergrund) und Sleep-Timer.'));
c.push(h2('Windows-PC'));
c.push(p('Siehe Kapitel 14 – dort steht die Installation der Windows-Version.'));
c.push(h2('Samsung Smart TV'));
c.push(p('Siehe Kapitel 15 – Installation über den Entwicklermodus des Fernsehers (einmalig mit dem PC).'));

// 3
c.push(h1('3. Erste Einrichtung'));
c.push(step('App öffnen → „Profil hinzufügen“.', 'n3'));
c.push(step([t('Typ wählen: '), b('Xtream Codes'), t(' (Server, Benutzername, Passwort), '), b('M3U-Link'), t(' oder '), b('M3U-Datei'), t('.')], 'n3'));
c.push(step('Speichern – die Senderlisten werden geladen und zwischengespeichert.', 'n3'));
c.push(p('Die Listen werden automatisch alle 24 Stunden aktualisiert; über das Aktualisieren-Symbol geht es jederzeit manuell.'));

// 4
c.push(h1('4. Startseite'));
c.push(bullet([b('Kopfzeile: '), t('Logo, Benutzer-Männchen, Cast, Suche, Aktualisieren und VPN-Status – gleich groß und gleichmäßig verteilt.')]));
c.push(bullet([b('Benutzer-Männchen (oben neben dem Logo): '), t('antippen → Liste aller Zugänge; der aktive ist blau mit Haken markiert. Ein Tipp auf einen anderen Zugang wechselt sofort. Bearbeiten und Löschen gibt es nur unter „Benutzer wechseln“.')]));
c.push(bullet([b('Große Kacheln: '), t('Live TV, Filme, Serien.')]));
c.push(bullet([b('Kleine Kacheln: '), t('Suche, Programmführer, Favoriten, Aufnahmen, Downloads, Empfehlungen, Multi-View, VPN, Einstellungen.')]));
c.push(bullet([b('Weiterschauen: '), t('angefangene Filme mit Fortschritt und Restzeit sowie die zuletzt gesehene Folge jeder Serie.')]));
c.push(bullet([b('Zuletzt gesehen: '), t('getrennt nach Live TV, Filme und Serien. Ein Tipp auf „Alle anzeigen ›“ öffnet die große Übersicht mit Reitern; lange drücken entfernt einen Eintrag.')]));
c.push(bullet([b('Benutzer wechseln (Kachel): '), t('zeigt alle Zugänge zum Bearbeiten, Löschen und Neu-Anlegen. Der aktive Zugang ist blau umrandet und mit „AKTIV“ markiert. Mit der Zurück-Taste (oder dem Pfeil oben) geht es ohne Wechsel zurück zur Startseite.')]));

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
  ['10 s spulen', 'Doppelt tippen rechts/links', '→ / ← (mehrmals drücken = Sprünge addieren sich)'],
  ['Beliebige Stelle', 'Zeitstrahl ziehen (mit Vorschaubild)', 'Zeitstrahl anwählen, ←/→'],
  ['Pause / Weiter', 'Mittlerer Knopf', 'Play/Pause-Taste oder OK bei Leiste'],
  ['Einstellungen', 'Zahnrad oben rechts', 'Menü-Taste (≡) oder ↑ zum Zahnrad'],
  ['Sender wechseln (Live)', 'Pfeile ⏮ / ⏭ in der Mitte', '↑ / ↓'],
  ['Nächste Folge / Intro', 'Knopf antippen', 'OK, solange der Knopf sichtbar ist'],
  ['Senderliste (Live)', '„Senderliste“ unten in der Infoleiste (öffnet rechts)', '→'],
  ['Letzter Sender (Live)', 'Pfeil-Symbol oben', '←'],
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
c.push(bullet([b('Gesehen-Markierung: '), t('Fertig geschaute Filme und Folgen bekommen einen grünen Haken (✓), angefangene einen Fortschrittsbalken – in der Übersicht, in der Suche und bei den Folgen. Von Hand umschalten: beim Film über „Gesehen/Ungesehen“ auf der Detailseite, bei Folgen durch langes Drücken (TV: OK gedrückt halten).')]));
c.push(bullet([b('Detailseite: '), t('Großes Hintergrundbild, FSK-Kennzeichen, Beschreibung, Besetzung, Download und Liste. Bei Serien ist die zuletzt gesehene Folge markiert, angefangene Folgen zeigen einen Fortschrittsbalken.')]));
c.push(bullet([b('Vorschaubilder beim Spulen: '), t('Beim Ziehen über den Zeitstrahl. Erlaubt dein Zugang nur 1 Verbindung, hält der Film beim Ziehen kurz an und läuft danach an der Zielstelle mit derselben Tonspur weiter – so sind nie zwei Verbindungen gleichzeitig offen. „Immer parallel“ (Einstellungen → Player) lässt den Film weiterlaufen, braucht aber eine 2. Verbindung.')]));
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
c.push(bullet([b('Sender wechseln: '), t('Die Pfeile ⏮ / ⏭ in der Bildmitte schalten zum vorherigen bzw. nächsten Sender der Liste (wie CH+/CH- auf der Fernbedienung).')]));

// 9
c.push(h1('9. Programmführer (EPG) & Erinnerungen'));
c.push(p('Die Kachel „Programm“ zeigt ein Zeitraster aller Sender. Über „🌐 Sprache“ ganz links lassen sich die Kategorien nach Sprache filtern (gleiche Einstellung wie bei Live TV, Filmen und Serien). Einen Sender lange drücken = Favorit. Eine Sendung antippen öffnet die Optionen:'));
c.push(bullet('Live ansehen bzw. von Beginn an (Timeshift)'));
c.push(bullet('Nachträglich ansehen (Catch-up)'));
c.push(bullet([b('Erinnern: '), t('5 Minuten vor Beginn kommt eine Benachrichtigung; „Jetzt ansehen“ startet den Sender direkt.')]));
c.push(bullet('Aufnahme planen'));

// 10
c.push(h1('10. Favoriten, Listen, Downloads'));
c.push(bullet([b('Favoriten: '), t('Überall möglich: Herz auf der Detailseite und im Player (oben), oder lange drücken (TV: OK halten) auf ein Poster, einen Sender, im Programmführer, in der Suche, bei „Weiterschauen“/„Zuletzt gesehen“. Favoriten tragen ein rotes Herz.')]));
c.push(bullet([b('Menü bei langem Drücken: '), t('Zu Favoriten hinzufügen/entfernen, Teilen (WhatsApp & Co. – geteilt wird nur der Titel, nie der Stream-Link mit deinen Zugangsdaten) und bei „Weiterschauen“/„Zuletzt gesehen“ zusätzlich „Aus Liste entfernen“ (der Titel selbst bleibt erhalten).')]));
c.push(bullet([b('Eigene Listen: '), t('„Liste“ auf der Detailseite, z.B. „Filmabend“ oder „Kinder“.')]));
c.push(bullet([b('Downloads: '), t('„Offline“ lädt Filme und Folgen herunter (mehrere Verbindungen parallel, wenn der Zugang es erlaubt).')]));

// 11
c.push(h1('11. Auf den Fernseher bringen'));
c.push(bullet([b('Google Cast: '), t('Cast-Symbol → Chromecast, Google TV oder Fernseher mit „Chromecast built-in“ im gleichen WLAN.')]));
c.push(bullet([b('Smart View / Bildschirm spiegeln: '), t('Für Samsung-Fernseher und Fire TV (dort „Display-Mirroring“ einschalten). Wird das Handy abgelehnt: am Samsung-TV unter Allgemein → Externe Geräteverwaltung → Geräteverbindungs-Manager → Geräteliste freigeben.')]));
c.push(bullet([b('Multi-View: '), t('Bis zu 4 Sender gleichzeitig (Kachel „Multi-View“). Leeres Fenster antippen = Sender wählen; Fenster antippen = dessen Ton hören (blauer Rahmen); lange drücken = Sender wechseln. Die Bedienknöpfe blenden sich nach 4 Sekunden aus und kommen mit einem Tipp zurück. Unten: Umschalten zwischen 2 und 4 Fenstern. Die App erkennt, wie viele Streams dein Zugang gleichzeitig erlaubt (Anzeige „Streams: x / y“): Reicht das Limit nicht für alle Fenster, laufen nur die zuletzt angetippten – die übrigen zeigen „Pausiert (Limit des Zugangs)“ mit dem letzten Bild und starten beim Antippen. Laufende Aufnahmen zählen mit. Hinweis: 4 Sender in Full-HD brauchen eine schnelle Internetleitung (ca. 40 MBit/s).')]));
c.push(bullet([b('Bild-in-Bild: '), t('Auf dem Handy läuft der Sender beim Verlassen der App im kleinen Fenster weiter.')]));

// 12
c.push(h1('12. Einstellungen'));
c.push(table(['Bereich', 'Was man einstellen kann'], [
  ['Player', 'Start-Klang an/aus (kurzer Kino-Klang beim App-Start, das Logo erscheint auf den Schlag), Automatisch / Standard / VLC, VLC-Leistung (Automatisch / Schnell / Qualität), Bildwiederholrate (AFR), Bildformat, Vorschaubilder beim Spulen'],
  ['Live-Format & User-Agent', 'Stream-Format (TS/HLS) und Kennung gegenüber dem Anbieter'],
  ['VPN', 'WireGuard-Konfiguration, Auto-Verbinden, Kill-Switch („nur mit VPN abspielen“)'],
  ['Kindersicherung', 'Erst mit festgelegter PIN aktiv (grüner Hinweis „AKTIV“ oben). Sperrt Kategorien, Erwachseneninhalte automatisch und gilt überall – auch in Verlauf, Weiterschauen, Favoriten, Suche und Programmführer.'],
  ['KI-Empfehlungen', 'ChatGPT-Schlüssel und Modell'],
  ['Altersfreigaben (FSK)', 'TMDB-Schlüssel für die offizielle FSK'],
  ['Sichern & Wiederherstellen', 'Alles als Datei sichern (optional mit Zugangsdaten und Passwort) und auf einem anderen Gerät laden'],
  ['Darstellung', 'Ausrichtung auf Handy/Tablet'],
], [2800, 6226]));
c.push(tip('Beim Umzug vom Handy auf den Fire TV: Sicherung mit Passwort erstellen, Datei z.B. per Cloud auf das andere Gerät bringen und dort unter „Sicherung laden“ wiederherstellen.'));

// 13
c.push(h1('13. Hilfe bei Problemen'));
c.push(table(['Problem', 'Lösung'], [
  ['Bild bleibt beim Spulen stehen', 'Behoben ab 1.1.79: Bei Zugängen mit nur 1 Stream öffnet die Vorschau keine 2. Verbindung mehr. Ab 1.1.80 gibt es die Vorschaubilder dort trotzdem wieder: Der Film hält beim Ziehen kurz an. Ab 1.1.84 spult der Player schneller: mehrere Tipps auf ⏪10/10⏩ werden zu einem Sprung zusammengefasst und es wird zum nächsten Schlüsselbild gesprungen (kein langes Nachladen bis zur exakten Sekunde). Hängt das Bild länger als 10 Sekunden, lädt der Player den Stream automatisch an derselben Stelle neu.'],
  ['Bild-in-Bild (Mini-Fenster)', 'Läuft ein Film oder Sender und du drückst die Home-Taste bzw. wischst nach Hause („Kreis“), spielt das Video in einem kleinen Fenster weiter – in beiden Playern (Standard und VLC). Das Fenster lässt sich verschieben; antippen und vergrößern bringt dich zurück, ✕ beendet die Wiedergabe. Nur Handy/Tablet (Fire TV unterstützt es für fremde Apps nicht).'],
  ['Live TV: nur Ton, kein Bild (oft SD-Sender)', 'Behoben ab 1.1.97: Viele SD-Sender senden im älteren MPEG-2-Format, das der Standard-Player auf Handys/Tablets nicht anzeigen kann. Läuft der Ton 6 Sekunden ohne Bild, wechselt Portiva automatisch zu VLC und merkt sich das für diesen Sender.'],
  ['Film läuft ohne Ton', 'Behoben ab 1.1.80: Viele Filme haben DTS- oder TrueHD-Ton, den der Standard-Player auf den meisten Handys/Tablets nicht abspielen kann. Portiva erkennt das und wechselt automatisch auf VLC (spielt DTS ab). Im Player-Zahnrad unter „Audiospur“ wählst du die Sprache (z. B. Deutsch / Englisch); Spuren mit „(mit VLC)“ wechseln beim Antippen auf VLC.'],
  ['Live TV stockt', 'Beide Player puffern Live TV jetzt größer (Standard-Player bis 60 s, nach einem Aussetzer erst mit 6 s Vorrat weiter; VLC 4 s) und verbinden sich nach Unterbrechungen automatisch neu. Unter Windows: Einstellungen → Player → Puffer „Groß“.'],
  ['Wann wird VLC benutzt?', 'Im Modus „Automatisch“ startet immer zuerst der Standard-Player. Nur wenn er einen bestimmten Stream nicht abspielen kann (z.B. seltene Ton- oder Bildformate), wechselt die App für genau diesen Titel zu VLC.'],
  ['VLC ruckelt oder hängt', 'Einstellungen → Player → VLC-Leistung auf „Schnell“ stellen (bei TV-Sticks automatisch).'],
  ['Nur Ton, kein Bild', 'Der Sender nutzt ein altes Format – die App wechselt automatisch zu VLC. Sonst Einstellungen → Player → VLC.'],
  ['Bild bleibt bei Aufnahme stehen', 'Der Zugang erlaubt nur eine Verbindung. Den aufgenommenen Sender schauen oder die Aufnahme stoppen.'],
  ['Update lässt sich nicht installieren', 'Immer die APK aus den Releases verwenden (gleiche Signatur).'],
  ['Cast findet den Fernseher nicht', 'Samsung- und Fire-TV-Geräte können kein Google Cast – „Bildschirm spiegeln“ nutzen.'],
  ['Senderliste veraltet', 'Aktualisieren-Symbol in der Kategorie-Ansicht antippen.'],
], [3000, 6026]));

// 14
c.push(h1('14. PowerIPTV für Windows'));
c.push(p('Für den Windows-PC gibt es eine eigene Version mit gleicher Optik – angepasst an Maus und Tastatur. Der Player (VLC) ist bereits enthalten, es muss nichts zusätzlich installiert werden.'));
c.push(h2('Installieren'));
c.push(step('Auf GitHub unter „Releases“ die neueste Version öffnen (fester Link: …/releases/latest/download/Portiva-Windows-Setup.exe).', 'n4'));
c.push(step('„Portiva-Windows-Setup-v1.1.X.exe“ herunterladen und doppelklicken.', 'n4'));
c.push(step('Warnt Windows („Der Computer wurde durch Windows geschützt“): „Weitere Informationen“ → „Trotzdem ausführen“.', 'n4'));
c.push(step('Danach startet Portiva über das Startmenü oder die Desktop-Verknüpfung.', 'n4'));
c.push(p('Ohne Installation: „Portiva-Windows-Portable-…zip“ entpacken und „Portiva.exe“ starten. Updates: einfach die neue Setup-Datei ausführen – Zugänge, Favoriten und Verlauf bleiben erhalten.'));
c.push(h2('Bedienung'));
c.push(bullet([b('Linke Leiste: '), t('Start, Live TV, Filme, Serien, Favoriten, Suche, Einstellungen und der aktive Zugang.')]));
c.push(bullet([b('Startseite: '), t('wie am Handy: große Kacheln, darunter die kleinen Kacheln (Suche, Playlist aktualisieren, Favoriten, Benutzer wechseln, Einstellungen …), Weiterschauen und „Zuletzt gesehen“ für Live TV, Filme und Serien. Oben rechts das Benutzer-Männchen zum schnellen Wechseln. Mit „bald“ markierte Kacheln folgen mit den nächsten Updates.')]));
c.push(bullet([b('Kategorien: '), t('genau wie in der Android-App – Sprache (Globus, z.B. DE/EN), Kategorie-Filter (Trichter), Favoriten, Zuletzt gesehen, Alle. Rechtsklick auf eine Kategorie: oben anheften oder ausblenden. Rechts daneben: Suche („Nur hier“/„Überall“), Filter & Sortierung (Bewertung, Jahr, Genre).')]));
c.push(bullet([b('Vollbild: '), t('F, F11 oder Doppelklick – deckt den ganzen Bildschirm ab (ohne Titel- und Taskleiste). Esc beendet das Vollbild. Kino-Filme haben oben und unten schwarze Balken; Taste Z (Zoomen) füllt den Bildschirm.')]));
c.push(bullet([b('Rechtsklick '), t('auf Sender, Film, Serie oder Folge: Favorit, gesehen markieren, aus „Weiterschauen“ entfernen.')]));

c.push(bullet([b('Player: '), t('Klick = Pause, Doppelklick = Vollbild, Mausrad = Lautstärke. Zahnrad: Bildformat, Tonspur, Untertitel, Geschwindigkeit. Bei Serien „Nächste Folge“ 40 Sekunden vor Schluss.')]));
c.push(table(['Taste', 'Funktion'], [
  ['Leertaste / K', 'Pause / Weiter'],
  ['← / →', '10 Sekunden zurück / vor (mit Umschalt: 1 Minute)'],
  ['↑ / ↓', 'Lautstärke; bei Live TV: Sender wechseln'],
  ['Bild ↑ / Bild ↓', 'Sender wechseln'],
  ['M', 'Ton aus / an'],
  ['Z', 'Zoomen an / aus (schwarze Balken weg)'],
  ['R', 'Aufnahme starten (Live TV)'],
  ['B', 'Zurück zum vorherigen Sender'],
  ['Enter', 'Intro überspringen (wenn eingeblendet)'],
  ['F, F11, Doppelklick', 'Vollbild an / aus'],
  ['L', 'Senderliste (Live TV)'],
  ['N', 'Nächste Folge'],
  ['Esc', 'Vollbild verlassen bzw. Player schließen'],
], [3000, 6026]));
c.push(tip('Dein Passwort wird mit dem Windows-Datenschutz (DPAPI) verschlüsselt gespeichert – nur dein Windows-Konto auf diesem PC kann es lesen. Alle Daten liegen unter %APPDATA%\\Portiva.'));
c.push(bullet([b('TV-Guide (EPG): '), t('linke Leiste „TV-Guide“ oder Kachel – Zeitraster wie am Handy (Sprache, Favoriten, Gruppen). Sendung anklicken: Live ansehen, von Beginn an/Catch-up (bei Archiv-Sendern), 🔔 Erinnern. Die Erinnerung erscheint 5 Minuten vorher als Windows-Benachrichtigung und als Hinweis mit „Jetzt ansehen“ (Portiva muss dafür geöffnet sein). In der Senderliste und im Player steht, was gerade läuft und was danach kommt.')]));
c.push(h2('Alle Funktionen wie am Handy'));
c.push(p('Die Windows-Version kann dasselbe wie die Android-App – vieles läuft sogar mit demselben Programmcode (Kindersicherung, FSK, KI-Empfehlungen, Aufnahme-Mitschnitt, Downloads, TV-Guide, Kategorien, Filter).'));
c.push(bullet([b('Aufnahmen: '), t('im TV-Guide „Aufnahme planen“ bzw. „Jetzt aufnehmen“ oder im Live-Player mit dem roten Punkt (Taste R) – Dauer wählen oder bis Sendungsende. Kachel „Aufnahmen“: abspielen, stoppen, löschen, Ordner öffnen. Portiva muss zur Aufnahmezeit geöffnet sein.')]));
c.push(bullet([b('Downloads: '), t('Detailseite „Herunterladen“ (Filme) bzw. Download-Symbol bei jeder Folge. Kachel „Downloads“: Fortschritt, Geschwindigkeit, pausieren/fortsetzen, offline abspielen. Ordner unter Einstellungen → Downloads & Aufnahmen.')]));
c.push(bullet([b('Multi-Screen: '), t('2 oder 4 Sender gleichzeitig. Fenster anklicken = dessen Ton; „Wechseln“ = anderer Sender. Das Stream-Limit des Zugangs wird beachtet wie am Handy („Pausiert (Limit des Zugangs)“, „Streams: x / y“).')]));
c.push(bullet([b('KI-Empfehlungen: '), t('OpenAI-Schlüssel unter Einstellungen → KI-Empfehlungen eintragen, dann Kachel „KI-Empfehlungen“.')]));
c.push(bullet([b('Kindersicherung: '), t('PIN festlegen, Erwachseneninhalte automatisch sperren, einzelne Kategorien sperren, Einstellungen schützen – gesperrte Inhalte sind überall ausgeblendet (Listen, Suche, Verlauf, Favoriten, TV-Guide) und nur mit PIN erreichbar.')]));
c.push(bullet([b('VPN & Sicherheit: '), t('WireGuard-Konfiguration (.conf) importieren und verbinden (einmalig „WireGuard für Windows“ installieren; Windows fragt beim Verbinden nach Administrator-Rechten). Kill-Switch blockiert ohne VPN Login, Listen, Bilder und Streams. Externe VPNs (NordVPN, Surfshark …) werden erkannt. Startseite oben: „VPN aktiv“ / „Kein VPN“.')]));
c.push(bullet([b('Favoriten & Listen: '), t('eigene Listen anlegen, umbenennen, löschen. Rechtsklick auf jeden Titel: Favorit, „Zu Liste hinzufügen …“, gesehen markieren, per WhatsApp teilen (nur der Titel, nie der Stream-Link).')]));
c.push(bullet([b('Player-Extras: '), t('Favorit-Herz, Aufnahme (R), zurück zum vorherigen Sender (B), Sleep-Timer und Untertitel im Zahnrad-Menü, „Intro überspringen“ (lernt wie am Handy aus deinem Vorspulen), Vorschaubilder beim Spulen, Auf Fernseher übertragen (Chromecast / Android TV im Heimnetz).')]));
c.push(bullet([b('Sichern & Wiederherstellen: '), t('Einstellungen → Sichern & Wiederherstellen. Gleiches Dateiformat wie die Handy-App: eine Handy-Sicherung kann am PC eingespielt werden (Zugänge, Favoriten & Listen, Verlauf, Weiterschauen, Kindersicherung, Kategorien, Schlüssel) – und umgekehrt.')]));
c.push(bullet([b('FSK: '), t('mit TMDB-Schlüssel (Einstellungen → Altersfreigabe) die offizielle deutsche Freigabe, sonst die Angabe des Anbieters.')]));
c.push(bullet([b('Zugänge: '), t('bearbeiten (Stift) und löschen wie am Handy; Passwörter mit dem Windows-Datenschutz verschlüsselt.')]));
c.push(bullet([b('Update: '), t('Kachel „Update“ neben „Einstellungen“ – ein Klick: sucht bei GitHub, lädt das neue Setup und installiert es (Portiva schließt sich dafür kurz). Zusätzlich automatische Prüfung alle 24 Stunden.')]));

// 15 Samsung Smart TV (Tizen)
c.push(new Paragraph({ children: [new PageBreak()] }));
c.push(h1('15. PowerIPTV für Samsung Smart TV (Tizen)'));
c.push(p('Für Samsung-Fernseher ab Baujahr 2018 (Tizen 4.0, z. B. UE49NU8009) gibt es eine eigene App mit dem Samsung-Videoplayer des Fernsehers. Sie wird einmalig vom PC aus installiert. Samsung erlaubt Apps außerhalb des Samsung-Stores nur mit einem eigenen, kostenlosen Samsung-Zertifikat, das an deinen Fernseher gebunden ist.'));
c.push(h2('Was die TV-Version kann'));
c.push(p('Zugänge (Xtream/M3U, mehrere Benutzer), Live TV, Filme und Serien mit Kategorien und Sprachauswahl, Favoriten, Verlauf, Weiterschauen, TV-Guide mit Catch-up und Erinnerungen, Suche, Kindersicherung, KI-Empfehlungen, Tonspur, Untertitel, Bildformat, Sleep-Timer, nächste Folge und „Intro überspringen“. Nicht möglich auf dem Fernseher: Aufnahmen, Downloads, VPN und Multi-Screen.'));
c.push(tip('Ton: Samsung-Fernseher ab 2018 können kein DTS mehr abspielen. Hat ein Film mehrere Tonspuren, mit der grünen Taste eine andere wählen (z. B. AC3).'));
c.push(h2('1. Software am PC installieren'));
c.push(step([b('Tizen Studio herunterladen: '), t('https://developer.tizen.org/development/tizen-studio/download (Variante „with IDE installer“ für Windows, Mac oder Linux) und installieren.')], 'n6'));
c.push(step([b('Samsung-Anleitung (falls nötig): '), t('https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/installing-tv-sdk.html')], 'n6'));
c.push(step('Im Package Manager unter „Extension SDK“ installieren: „TV Extensions-4.0“ (oder neuer) und „Samsung Certificate Extension“.', 'n6'));
c.push(h2('2. Entwicklermodus am Fernseher'));
c.push(step('PC und Fernseher ins selbe Heimnetz. IP-Adresse des PCs notieren (Windows: Eingabeaufforderung → ipconfig → IPv4-Adresse).', 'n7'));
c.push(step('Am Fernseher Home drücken → „Apps“ öffnen → auf der Fernbedienung 1 2 3 4 5 eingeben.', 'n7'));
c.push(step('„Developer mode“ auf „On“ stellen, die IP-Adresse des PCs eintragen, OK.', 'n7'));
c.push(step('Fernseher neu starten (Ein/Aus-Taste gedrückt halten oder kurz vom Strom trennen).', 'n7'));
c.push(step('IP-Adresse des Fernsehers notieren: Einstellungen → Allgemein → Netzwerk → Netzwerkstatus → IP-Einstellungen.', 'n7'));
c.push(h2('3. Verbinden und Zertifikat erstellen (einmalig)'));
c.push(step('Tizen Studio → Tools → Device Manager → Remote Device Manager → „+“ → IP des Fernsehers eintragen → Verbindung einschalten.', 'n8'));
c.push(step('Tools → Certificate Manager → „+“ → Samsung → TV → Profilname „PortivaTV“.', 'n8'));
c.push(step('Neues Author-Zertifikat anlegen (Name, Passwort) und mit dem kostenlosen Samsung-Konto anmelden.', 'n8'));
c.push(step('Neues Distributor-Zertifikat, Stufe „Public“ – die DUID des verbundenen Fernsehers wird automatisch eingetragen → Fertig.', 'n8'));
c.push(h2('4. App signieren und installieren'));
c.push(step('Auf GitHub unter „Releases“ die neueste Version öffnen und „PowerIPTV-Tizen-v1.1.X.zip“ herunterladen und entpacken.', 'n9'));
c.push(step('Eingabeaufforderung im entpackten Ordner „PowerIPTV-Tizen“ öffnen (das Programm „tizen“ liegt in tizen-studio\\tools\\ide\\bin).', 'n9'));
c.push(step([b('Signieren: '), t('tizen package -t wgt -s PortivaTV')], 'n9'));
c.push(step([b('Installieren: '), t('tizen install -n Portiva.wgt -s <IP-des-Fernsehers>:26101')], 'n9'));
c.push(step('Fertig – „Portiva“ erscheint am Fernseher unter „Apps“. Beim ersten Start den Zugang einrichten (OK auf einem Feld öffnet die Fernseher-Tastatur).', 'n9'));
c.push(h2('Fernbedienung'));
c.push(table(['Taste', 'Funktion'], [
  ['Pfeile / OK / Zurück', 'Bedienen; auf der Startseite fragt Zurück, ob Portiva beendet werden soll'],
  ['▶❚❚, ■, ⏪ ⏩', 'Pause/Weiter, Stopp, 30 Sekunden zurück/vor'],
  ['← / → (Leiste aus)', 'Film/Serie 10 Sekunden zurück/vor'],
  ['CH+ / CH− oder ↑ / ↓', 'Sender umschalten; 0–9 = Sendernummer direkt'],
  ['Rot / Grün / Gelb / Blau', 'Favorit / Tonspur (TV-Guide, Sortieren) / Untertitel (Kategorie-Menü) / Bildformat'],
  ['CH LIST, INFO, GUIDE', 'Senderliste, Infoleiste, Programm des Senders'],
], [3000, 6000]));
c.push(h2('Updates'));
c.push(p('Die Kachel „Update“ prüft alle 24 Stunden bei GitHub und leuchtet bei einer neuen Version. Installiert wird wie oben (Schritt 4) mit derselben Zertifikat-Datei – Zugänge, Favoriten und Verlauf bleiben erhalten.'));
c.push(tip('Die Zertifikat-Dateien (author.p12, distributor.p12) gut aufheben und nie weitergeben. Mit einem neuen Zertifikat muss die App vorher deinstalliert werden.'));

// 16 Portiva Link
c.push(new Paragraph({ children: [new PageBreak()] }));
c.push(h1('16. Portiva Link: Zugang übertragen & an Gerät senden'));
c.push(p('Portiva-Geräte im selben WLAN arbeiten zusammen – ohne Konto, ohne Cloud, ohne Chromecast. Übertragen wird immer nur der eine Zugang, den du auswählst.'));
c.push(h2('Zugang auf Handy/Tablet übertragen (mit Kamera)'));
c.push(step('Am Gerät mit dem Zugang: Benutzer wechseln („Wer schaut?“) → beim gewünschten Zugang auf das QR-Symbol (links neben Stift und Mülleimer) tippen. Der Code erscheint groß; schließen mit dem Kreuz oder durch Tippen daneben.', 'n10'));
c.push(step('Am neuen Handy/Tablet: Benutzer wechseln → „Neuer Zugang“ → „QR-Code scannen“ → Code scannen. Fertig – der Zugang ist eingerichtet und aktiv.', 'n10'));
c.push(h2('Zugang auf TV-Stick, Fernseher oder PC übertragen (ohne Kamera)'));
c.push(step('Am TV-Stick / Android TV: Benutzer wechseln → „Neuer Zugang“ → „Vom anderen Gerät empfangen“. Samsung-TV: Benutzer wechseln → „Vom Handy empfangen“. Windows: Zugänge → „Vom Handy empfangen“. Es erscheint ein QR-Code.', 'n11'));
c.push(step('Am Handy: Benutzer wechseln → beim gewünschten Zugang auf das QR-Symbol tippen → „An TV-Stick / Fernseher senden“ → den Code am TV scannen.', 'n11'));
c.push(step('Der Zugang erscheint sofort auf dem TV und wird aktiviert. Beim Samsung-TV dauert es einige Sekunden (der Fernseher holt den Zugang beim Handy ab – Portiva am Handy so lange geöffnet lassen).', 'n11'));
c.push(tip('Der QR-Code eines Zugangs enthält die Zugangsdaten – nur dir selbst zeigen. Lokale M3U-Dateien lassen sich nicht übertragen (dort fehlt das QR-Symbol).'));
c.push(h2('Wiedergabe an ein anderes Gerät senden'));
c.push(step('Im Player oben auf das Übertragen-Symbol (wie bei Chromecast) oder das Fernseher-Symbol „An Gerät senden“ tippen (Samsung-TV: Knopf „📲 Senden“). Das Fenster bleibt offen, bis du es schließt – auch wenn die Suche etwas dauert.', 'n12'));
c.push(step('Unter „Portiva-Geräte“ erscheinen alle Geräte mit geöffnetem Portiva im Heimnetz (TV-Stick, Tablet, Handy, Windows-PC) → Gerät wählen. Darüber stehen wie bisher Chromecast-Geräte.', 'n12'));
c.push(step('Der Film läuft dort an derselben Stelle weiter (Live TV: derselbe Sender), hier wird gestoppt.', 'n12'));
c.push(tip('Portiva muss auf dem Zielgerät geöffnet sein (Android ab Version 10 erlaubt Apps im Hintergrund nicht, sich selbst zu öffnen). Samsung-Fernseher können senden, aber nichts empfangen. Windows fragt beim ersten Mal nach der Firewall – „Zulassen“ wählen.'));

// 17 iPhone & iPad
c.push(new Paragraph({ children: [new PageBreak()] }));
c.push(h1('17. PowerIPTV für iPhone & iPad'));
c.push(p('Die iOS-Version nutzt denselben Player wie Android und Windows: VLC. Damit laufen auch DTS-, AC3- und MKV-Filme. Sie kann Zugänge (auch per QR-Code), Live TV, Filme und Serien mit Sprachauswahl, Weiterschauen, Favoriten, Suche, TV-Guide mit Catch-up, Player mit Helligkeit/Lautstärke, Seitenverhältnis, Geschwindigkeit, Untertiteln und Einstellungsleiste sowie „An Gerät senden“.'));
c.push(tip('Apple erlaubt Apps außerhalb des App Stores nur mit einer Apple-ID-Signatur. Mit einer kostenlosen Apple-ID läuft die App 7 Tage und muss dann neu aufgespielt werden (Zugänge bleiben erhalten). Mit einem Apple-Entwicklerkonto (99 €/Jahr) gilt sie 1 Jahr.'));
c.push(h2('Installation mit Sideloadly (Windows oder Mac)'));
c.push(step('Am PC: Sideloadly von https://sideloadly.io herunterladen und installieren. Unter Windows zusätzlich iTunes und iCloud von der Apple-Webseite (nicht aus dem Microsoft Store).', 'n13'));
c.push(step('Auf GitHub unter „Releases“ die neueste Version öffnen und „Portiva-iOS-v1.1.X.ipa“ herunterladen.', 'n13'));
c.push(step('iPhone/iPad per Kabel anschließen, entsperren und „Vertrauen“ bestätigen.', 'n13'));
c.push(step('Sideloadly öffnen, die .ipa hineinziehen, deine Apple-ID eintragen und „Start“ klicken.', 'n13'));
c.push(step('Am iPhone: Einstellungen → Datenschutz & Sicherheit → Entwicklermodus einschalten (iOS 16+, Neustart). Danach Einstellungen → Allgemein → VPN & Geräteverwaltung → deine Apple-ID → „Vertrauen“.', 'n13'));
c.push(step('Portiva öffnen. Beim ersten Start fragt iOS nach „Lokales Netzwerk“ – erlauben (für QR-Übertragung und „An Gerät senden“).', 'n13'));
c.push(p('Zugang am schnellsten: Benutzer wechseln → „+“ → „QR-Code scannen“ und am anderen Gerät beim Zugang auf das QR-Symbol tippen.'));

// 18
c.push(h1('18. PowerIPTV für die neuen Fire TV Sticks (Vega OS)'));
c.push(p('Amazon hat bei den neuen Sticks Android durch das eigene Betriebssystem „Vega OS“ ersetzt. Betroffen sind der Fire TV Stick 4K Select, der Fire TV Stick HD (2026), der Fire TV Stick 4K (2026) und alle kommenden Sticks. Dort laufen keine APKs und es gibt kein „Apps unbekannter Herkunft“ mehr. Deshalb gibt es eine eigene Version: „Portiva-FireTV-Vega-v1.1.X.vpkg“ (auf GitHub im Ordner „Fire TV Vega“, im Release mit „🆕 NEUE Amazon Fire TV Sticks“ gekennzeichnet).'));
c.push(tip('Welcher Stick? Einstellungen → Mein Fire TV → Info. Steht dort „Fire OS“, nimm die normale APK (Kapitel 2). Steht dort „Vega OS“, nimm diese Version.'));
c.push(p('Die Oberfläche ist dieselbe wie beim Samsung-Fernseher (Fernbedienung, Live-TV-Infoleiste, EPG, Favoriten, Portiva Link). Das Bild kommt vom Hardware-Player des Sticks. Aufnahmen, Downloads, VPN und Mehrfachbildschirm gibt es dort nicht.'));
c.push(h2('Einmalig: Amazon-Entwicklerkonto & Vega-Werkzeug'));
c.push(step('Kostenloses Amazon-Entwicklerkonto auf developer.amazon.com anlegen und das Appstore-Entwicklerprofil vollständig ausfüllen (ohne Bank-/Steuerdaten). Ohne vollständiges Profil klappt der Entwicklermodus nicht.', 'n14'));
c.push(step('Am PC (Windows mit WSL/Ubuntu, Linux oder Mac) das Vega-Werkzeug installieren: curl -fsSL https://sdk-installer.vega.labcollab.net/get_vvm.sh | bash und danach source ~/vega/env.', 'n14'));
c.push(step('Anmelden: vega devmode login → den angezeigten Code auf amazon.com/code bestätigen.', 'n14'));
c.push(h2('Entwicklermodus am Stick einschalten'));
c.push(step('Am Stick: Einstellungen → Mein Fire TV → Info → 7-mal auf den Gerätenamen drücken („Du bist jetzt Entwickler“).', 'n15'));
c.push(step('Zurück → Entwickleroptionen → Entwicklermodus → Weiter. Der Fernseher zeigt einen 6-stelligen Code (gilt ca. 5 Minuten).', 'n15'));
c.push(step('Am PC: vega devmode enable-device --code <Code>. Der Stick startet neu.', 'n15'));
c.push(step('Entwickleroptionen → Verbindungsart „Netzwerk“ wählen (oder per USB-Kabel verbinden). Prüfen mit: vega device list (zeigt die Seriennummer).', 'n15'));
c.push(h2('Portiva installieren & aktualisieren'));
c.push(step('Auf GitHub unter „Releases“ die Datei „Portiva-FireTV-Vega-v1.1.X.vpkg“ herunterladen.', 'n16'));
c.push(step('Installieren: vega device -d <Seriennummer> install-app --packagePath Portiva-FireTV-Vega-v1.1.X.vpkg', 'n16'));
c.push(step('Starten: über die Kachel „Portiva“ bei den Apps (ggf. nach einem Neustart) oder vega device -d <Seriennummer> launch-app --appName app.portiva.firetv.main', 'n16'));
c.push(step('Zugang am bequemsten per „Vom Handy empfangen“ einrichten (Kapitel 16).', 'n16'));
c.push(tip('Updates genauso installieren – Zugänge und Favoriten bleiben erhalten. Die App zeigt unter „Update“, wenn es eine neue Version gibt.'));
c.push(tip('Hinweis: Diese Version ist neu und noch nicht auf jedem Stick erprobt. Klappt ein Sender oder Film nicht, im Anbieter-Portal bzw. in den Einstellungen das Live-Format „HLS (m3u8)“ wählen; MKV-Filme mit DTS-Ton spielen die neuen Sticks evtl. ohne Ton ab.'));

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
    ...['n1', 'n2', 'n3', 'n4', 'n5', 'n6', 'n7', 'n8', 'n9', 'n10', 'n11', 'n12', 'n13', 'n14', 'n15', 'n16', 'num'].map(r => ({ reference: r, levels: [{ level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } }] })),
  ] },
  features: { updateFields: true },
  sections: [{
    properties: { page: { margin: { top: 1440, bottom: 1440, left: 1440, right: 1440 } } },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: 'PowerIPTV – Bedienungsanleitung · Seite ', color: '888888', size: 18 }), new TextRun({ children: [PageNumber.CURRENT], color: '888888', size: 18 })] })] }) },
    children: c,
  }],
});
Packer.toBuffer(doc).then(buf => { fs.writeFileSync(require('path').join(__dirname, '../../docs/PowerIPTV-Anleitung.docx'), buf); console.log('ok'); });
