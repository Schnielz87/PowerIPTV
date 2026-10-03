# Portiva – PowerIPTV für Samsung Smart TV (Tizen)

Eigene Web-App für Samsung-Fernseher ab Baujahr 2018 (Tizen 4.0, Chromium M56), getestet für den **UE49NU8009**.
Sie ist kein umgewandeltes APK, sondern eine echte Tizen-App mit Samsung-Videoplayer (AVPlay, Hardware-Decoder des Fernsehers).

## Funktionen

Die App kann dasselbe wie Android und Windows, soweit es auf dem Fernseher technisch möglich ist:

- Zugänge mit Xtream Codes oder M3U-Link, mehrere Benutzer und Schnellwechsel über das Männchen oben rechts.
- Live TV, Filme und Serien mit Kategorien:
  - Sprachauswahl (DE, EN …), Favoriten, Zuletzt gesehen, Alle.
  - Kategorien anheften oder ausblenden; Sortierung.
- Detailseiten mit FSK, Fortsetzen, Staffeln und Folgen sowie gesehen/ungesehen.
- Player (AVPlay):
  - Buffer einstellbar, automatisches Neuverbinden und Hänger-Wächter; Spulen wird gesammelt.
  - Tonspur und Untertitel wählbar; AC3 und E-AC3 dekodiert der Fernseher selbst.
  - Bildformat, Sleep-Timer.
  - Bei Serien: nächste Folge mit Countdown, „Intro überspringen“ (lernt aus deinem Vorspulen).
- Live TV im Player:
  - Programmtasten CH+/CH−, Pfeil ↑/↓ und Zifferntasten zum Umschalten.
  - Senderliste (Taste CH LIST bzw. ←) und Jetzt/Danach.
  - Catch-up (Archiv) direkt aus dem Programm.
- TV-Guide mit Programm pro Sender, Catch-up und Erinnerungen.
- Suche, Favoriten & Verlauf, Weiterschauen.
- Kindersicherung mit PIN: Erwachsenen-Kategorien, gesperrte Kategorien, Einstellungen geschützt.
- KI-Empfehlungen (ChatGPT-API-Schlüssel) und Update-Prüfung über GitHub.

Nicht enthalten, weil der Fernseher das nicht kann:

- Aufnahmen und Downloads: kein frei beschreibbarer Speicher für Apps.
- VPN: Apps dürfen den Netzwerkverkehr des Fernsehers nicht umleiten.
- Multi-Screen: AVPlay spielt nur ein Video gleichzeitig.

## Fernbedienung

| Taste | Funktion |
|---|---|
| Pfeile / OK | Bedienen |
| Zurück | eine Ebene zurück (Startseite: Beenden-Frage) |
| ▶❚❚ / ■ / ⏪ ⏩ | Pause/Weiter, Stopp, ±30 s |
| ← / → (Leiste ausgeblendet) | Film/Serie ±10 s |
| CH+ / CH− / ↑ / ↓ | Sender umschalten |
| 0–9 | Sendernummer direkt |
| Rot | Favorit |
| Grün | Tonspur (Player) / TV-Guide bzw. Sortieren |
| Gelb | Untertitel (Player) / Kategorie-Menü (anheften, ausblenden, sperren) |
| Blau | Bildformat |
| INFO | Infoleiste ein/aus |
| GUIDE | Programm des Senders |

## Bauen

```bash
cd "Tizen Samsung"
npm ci
node build.js        # -> build/ (index.html, app.js, app.css, config.xml, icon.png, assets/)
```

`build.js` übersetzt das moderne JavaScript mit esbuild für Chromium 56 (Ziel `chrome56`, ein einziges Skript, keine Module).
Das Layout nutzt nur Flexbox, kein CSS-Grid.

Jeder Push baut automatisch `PowerIPTV-Tizen-v1.1.X.zip` (= Inhalt von `build/`) in die GitHub-Releases.

## Installation auf dem Fernseher (UE49NU8009)

Samsung erlaubt Apps außerhalb des Stores nur mit **eigenem Samsung-Zertifikat**. Das Zertifikat ist an die Geräte-ID (DUID) deines Fernsehers gebunden.
Deshalb wird die App einmalig an deinem PC signiert.

### 1. Tizen Studio installieren (PC, Windows/Mac/Linux)

- Download: <https://developer.tizen.org/development/tizen-studio/download> (Variante „Tizen Studio … with IDE installer“)
- Samsung-Anleitung: <https://developer.samsung.com/smarttv/develop/getting-started/setting-up-sdk/installing-tv-sdk.html>
- Nach der Installation öffnet sich der **Package Manager**. Unter „Extension SDK“ installieren:
  - **TV Extensions-4.0** (oder neuer)
  - **Samsung Certificate Extension**

### 2. Entwicklermodus am Fernseher einschalten

1. PC und Fernseher im selben Heimnetz. Die IP-Adresse des PCs notieren (Windows: `ipconfig` → IPv4-Adresse).
2. Am Fernseher die Taste **Home** drücken und **Apps** öffnen.
3. Im Apps-Bildschirm auf der Fernbedienung nacheinander **1 2 3 4 5** eingeben (bei der Smart-Remote über die Zifferntasten auf dem Bildschirm).
4. Im Fenster „Developer mode“ auf **On** stellen, die **IP-Adresse des PCs** eintragen und **OK** wählen.
5. Den Fernseher **neu starten**: Ein/Aus-Taste gedrückt halten, bis er neu startet, oder kurz vom Strom nehmen.
6. Die IP-Adresse des Fernsehers notieren: Einstellungen → Allgemein → Netzwerk → Netzwerkstatus → IP-Einstellungen.

### 3. Fernseher mit dem PC verbinden

Tizen Studio → **Tools → Device Manager** → „Remote Device Manager“ → **+** → IP des Fernsehers → Verbindung **ON**.
Alternativ in der Kommandozeile:

```bash
sdb connect 192.168.178.50      # IP des Fernsehers
sdb devices                     # zeigt z.B. "192.168.178.50:26101   device   UE49NU8009"
```

### 4. Samsung-Zertifikat erstellen (einmalig)

Tizen Studio → **Tools → Certificate Manager** → **+**:

1. **Samsung** → **TV** auswählen.
2. Profilname: **PortivaTV**.
3. Author-Zertifikat: „Create a new author certificate“, Name und Passwort vergeben.
4. Mit dem Samsung-Konto anmelden (kostenlos).
5. Distributor-Zertifikat: „Create a new distributor certificate“, Privilege-Level **Public**.
6. Die **DUID** des verbundenen Fernsehers wird automatisch eingetragen.
7. Fertigstellen.

Die Dateien liegen danach unter `~/SamsungCertificate/PortivaTV/` (`author.p12`, `distributor.p12`).
**Nie ins Repository hochladen.**

### 5. Signieren und installieren

`PowerIPTV-Tizen-v1.1.X.zip` aus den GitHub-Releases herunterladen und entpacken (oder selbst bauen).
Danach im Ordner `tizen-studio/tools/ide/bin` bzw. mit `tizen` im PATH:

```bash
cd PowerIPTV-Tizen                       # entpackter Ordner (oder "Tizen Samsung/build")
tizen package -t wgt -s PortivaTV        # erzeugt Portiva.wgt (signiert mit deinem Zertifikat)
tizen install -n Portiva.wgt -s 192.168.178.50:26101
```

Die App erscheint danach unter **Apps** als **Portiva**. Updates werden genauso installiert. Zugänge, Favoriten und Verlauf bleiben erhalten, solange dasselbe Zertifikat verwendet wird.

### Optional: fertig signierte .wgt direkt aus GitHub

Wenn du deine Zertifikate als GitHub-Secrets hinterlegst, baut jeder Push zusätzlich eine signierte `PowerIPTV.wgt`. Dann entfällt Schritt 5, und du installierst nur noch mit `tizen install`.

| Secret | Inhalt |
|---|---|
| `TIZEN_AUTHOR_P12_BASE64` | `base64 -w0 author.p12` |
| `TIZEN_AUTHOR_PASSWORD` | Passwort des Author-Zertifikats |
| `TIZEN_DISTRIBUTOR_P12_BASE64` | `base64 -w0 distributor.p12` |
| `TIZEN_DISTRIBUTOR_PASSWORD` | Passwort des Distributor-Zertifikats |

## Fehlerhilfe

- **„install failed … certificate“**: Das Distributor-Zertifikat enthält die DUID eines anderen Fernsehers. Zertifikat mit verbundenem Fernseher neu erstellen.
- **`sdb connect` klappt nicht**:
  - Im Entwicklermodus muss die IP des PCs eingetragen sein; danach den Fernseher neu starten.
  - Die Firewall darf Port 26101 nicht blockieren.
- **Film ohne Ton**: Samsung hat DTS ab den 2018er-Modellen abgeschafft. Mit Grün eine andere Tonspur wählen (z. B. AC3). Hat der Film nur DTS-Ton, gibt es ihn auf diesem Fernseher nur über eine DTS-fähige Soundbar oder einen AV-Receiver (HDMI-ARC, Ton-Ausgabe „Bitstream/Durchleiten“).
- **Live-Sender bleibt schwarz**: Einstellungen → Live-TV-Format auf „HLS (m3u8)“ stellen, Puffer „Groß“.
