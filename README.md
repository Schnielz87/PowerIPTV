# Portiva – PowerIPTV

Android-IPTV-App (Smartphone, Tablet, Android TV / Fire TV) im Stil von IPTV Smarters –
mit **eingebautem VPN**, **Offline-Downloads** und **eigenen Favoritenlisten**.

📖 **Bedienungsanleitung (Word):** [docs/PowerIPTV-Anleitung.docx](docs/PowerIPTV-Anleitung.docx)

## Funktionen

| Bereich | Details |
|---|---|
| **Zugaenge** | Xtream Codes API (Server/Benutzer/Passwort), M3U-URL, lokale M3U-Datei · mehrere Benutzer · M3U-Link mit Zugangsdaten → Umwandlung in Xtream |
| **Bedienung** | Querformat (einstellbar), Kategorie-Spalte links mit Sprachfilter (DE, EN, ...) und Kategorie-Suche, Inhalte rechts |
| **Suche & Filter** | Titelsuche ueber alle Kategorien, globale Suche (Live + Filme + Serien), Sortierung, Mindestbewertung, Jahrzehnt, Genre |
| **Aktualisierung** | Playlist wird lokal gespeichert (schneller Start), automatische Aktualisierung alle 24 h beim Oeffnen, manuell per Button |
| **Live TV** | Kategorien, Kanalliste mit Logos, Suche, EPG (Jetzt/Danach), Kanal vor/zurueck, Direktwahl per Zifferntasten |
| **TV-Guide (EPG)** | Lueckenloses Timeline-Raster (Kanaele × Zeit), XMLTV vom Server (Xtream `xmltv.php`) oder `url-tvg`, Kanalgruppen-Filter, Jetzt-Markierung |
| **Catch-up / Timeshift** | Live pausieren und zeitversetzt weiterschauen (lokaler Puffer), vergangene Sendungen per Server-Archiv (Xtream `tv_archive`) |
| **Aufnahmen (PVR)** | Sofort-Aufnahme im Player, geplante Aufnahmen aus dem EPG, mehrere parallel, laeuft im Hintergrund, auch nach Neustart |
| **Multi-Screen** | Bis zu 4 Kanaele gleichzeitig (2er- oder 4er-Ansicht), Ton per Auswahl; zusaetzlich Bild-in-Bild |
| **Kindersicherung** | PIN, gesperrte Kategorien, automatische Sperre fuer Erwachseneninhalte, PIN-Schutz fuer Einstellungen |
| **KI-Empfehlungen** | ChatGPT-Anbindung (OpenAI-API-Schluessel): personalisierte Vorschlaege aus Verlauf + Favoriten, nur Titel aus deinem Angebot |
| **Fernbedienungen** | Android TV, Fire TV, Gamepads: deutlicher Fokus-Rahmen, CH+/CH-, Zifferntasten, INFO/GUIDE/MENU, Aufnahme-/Farbtasten, Medientasten |
| **Filme** | Poster-Raster, Detailseite (Handlung, Genre, Besetzung, Bewertung), Abspielen, Download |
| **Serien** | Staffeln & Episoden, automatische naechste Episode, Download einzelner Episoden |
| **Favoriten & Listen** | Herz-Favoriten + beliebig viele eigene Listen (anlegen, umbenennen, loeschen) |
| **Downloads** | Filme/Episoden offline speichern, Pause/Fortsetzen (Resume), Offline-Wiedergabe |
| **VPN** | WireGuard-VPN eingebaut (.conf importieren oder einfuegen), Kill-Switch, Auto-Connect, Split-Tunneling (nur PowerIPTV), externe VPN-Apps werden erkannt |
| **Player** | ExoPlayer (Media3): HLS, MPEG-TS, MP4, MKV · Untertitel/Tonspuren · Bild-in-Bild |
| **Sicherheit** | Zugangsdaten & VPN-Konfig AES-256-verschluesselt (Android Keystore) |

## APK herunterladen

Jede neue Version erscheint automatisch unter **Releases**:

1. Im Repo auf **Releases** tippen → oberster Eintrag **„PowerIPTV v1.1.x (neueste Version)“**
2. Unter **Assets** die Datei `PowerIPTV-v1.1.x.apk` herunterladen
3. Installieren („Installation aus unbekannten Quellen“ erlauben)

### Updates ohne Neuinstallation (einmalig einrichten)

Android installiert Updates nur, wenn jede Version mit demselben Schluessel signiert ist.
Dafuer einmalig einen Schluessel erzeugen und als GitHub-Secret hinterlegen:

```bash
keytool -genkeypair -keystore release.jks -alias poweriptv -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks > release.jks.b64
```

Im Repo unter *Settings → Secrets and variables → Actions* anlegen:
`SIGNING_KEYSTORE_BASE64` (Inhalt von `release.jks.b64`), `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS` (`poweriptv`), `SIGNING_KEY_PASSWORD`.
Ohne diese Secrets wird mit einem wechselnden Debug-Schluessel signiert (dann vor einem Update die alte Version deinstallieren).

## Fire TV / Android TV

Eine APK fuer alles: Die App erkennt Fernseher und TV-Sticks automatisch (Fire TV, Android TV, Google TV)
und schaltet in den TV-Modus: immer Querformat, Vollbild, Sicherheitsrand gegen Overscan,
Bedienung komplett per Fernbedienung, Suchfelder oeffnen die Tastatur erst nach „OK“.

**Bildformate:** Videos werden automatisch im richtigen Seitenverhaeltnis angezeigt (Auto / Zoom / Strecken,
im Player per blauer Taste umschaltbar). **AFR:** Die Bildwiederholrate des Fernsehers wird an das Video
angepasst (z.B. 50 Hz fuer TV, 24 Hz fuer Filme). Auf Fire TV zusaetzlich *Einstellungen → Display & Ton →
Display → Originalbildfrequenz anpassen* aktivieren.

**Installation auf dem Fire TV Stick:**
1. Fire TV: *Einstellungen → Mein Fire TV → Entwickleroptionen → Apps unbekannter Herkunft* fuer die App „Downloader“ erlauben
   (Entwickleroptionen erscheinen nach 7× Klick auf *Info → Fire TV Stick*)
2. App **Downloader** (Amazon Appstore) installieren
3. Im Downloader den APK-Link eingeben (siehe unten) → herunterladen → installieren

Hinweis: Solange dieses Repository **privat** ist, kann der Fire TV die Datei nicht direkt von GitHub laden.
Alternativen: App „Send Files to TV“ (Handy → Fire TV), oder die APK auf einen eigenen Speicher/Link legen.

## KI-Empfehlungen (ChatGPT) einrichten

1. Auf https://platform.openai.com → *API keys* einen Schluessel erstellen (ein ChatGPT-Plus-Abo enthaelt **keine** API-Nutzung; die API wird separat nach Verbrauch abgerechnet)
2. In der App: **Einstellungen → KI-Empfehlungen** → Schluessel einfuegen → *Speichern & testen*
3. Startseite → **KI-Empfehlungen**

Modell und API-Adresse sind einstellbar (auch OpenAI-kompatible Anbieter).

## VPN einrichten

1. Beim VPN-Anbieter (z.B. Mullvad, ProtonVPN, Surfshark, NordVPN, IVPN, AirVPN – oder eigener Server)
   eine **WireGuard-Konfiguration (.conf)** erzeugen und herunterladen
2. In der App: **VPN & Sicherheit** → **.conf importieren**
3. **VPN verbinden** – Android fragt einmalig nach der VPN-Berechtigung
4. Optional: **Kill-Switch** aktivieren → ohne VPN laedt die App gar nichts (auch keine Downloads)

## Logo austauschen

Das Logo (Portiva-P in der IPTV-Variante: Play-Button + Signalwellen) liegt als Vektor in `app/src/main/res/drawable/portiva_logo.xml`.
Zum Ersetzen die Datei loeschen und das Original als `portiva_logo.png` in denselben Ordner legen.
Das App-Icon befindet sich in `ic_launcher_background.xml` / `ic_launcher_foreground.xml`.

## Selbst bauen

Android Studio (Koala oder neuer) oeffnen → Projekt laden → *Run*.
Oder per Kommandozeile: `./gradlew assembleRelease` (JDK 17, Android SDK 34).

## Technik

Kotlin · Jetpack Compose (Material 3) · Media3 ExoPlayer · OkHttp · kotlinx.serialization · Coil ·
WireGuard Tunnel-Library (`com.wireguard.android:tunnel`)

> Hinweis: Die App enthaelt keine Inhalte. Nutze nur IPTV-Dienste, fuer die du die Rechte besitzt.
