# Portiva – PowerIPTV

Android-IPTV-App (Smartphone, Tablet, Android TV / Fire TV) im Stil von IPTV Smarters –
mit **eingebautem VPN**, **Offline-Downloads** und **eigenen Favoritenlisten**.

## Funktionen

| Bereich | Details |
|---|---|
| **Zugaenge** | Xtream Codes API (Server/Benutzer/Passwort), M3U-URL, lokale M3U-Datei · mehrere Benutzer · M3U-Link mit Zugangsdaten → Umwandlung in Xtream |
| **Live TV** | Kategorien, Kanalliste mit Logos, Suche, EPG (Jetzt/Danach), Kanal vor/zurueck (auch per Fernbedienung CH+/CH-) |
| **Filme** | Poster-Raster, Detailseite (Handlung, Genre, Besetzung, Bewertung), Abspielen, Download |
| **Serien** | Staffeln & Episoden, automatische naechste Episode, Download einzelner Episoden |
| **Favoriten & Listen** | Herz-Favoriten + beliebig viele eigene Listen (anlegen, umbenennen, loeschen) |
| **Downloads** | Filme/Episoden offline speichern, Pause/Fortsetzen (Resume), Offline-Wiedergabe |
| **VPN** | WireGuard-VPN eingebaut (.conf importieren oder einfuegen), Kill-Switch, Auto-Connect, Split-Tunneling (nur PowerIPTV), externe VPN-Apps werden erkannt |
| **Player** | ExoPlayer (Media3): HLS, MPEG-TS, MP4, MKV · Untertitel/Tonspuren · Bild-in-Bild |
| **Sicherheit** | Zugangsdaten & VPN-Konfig AES-256-verschluesselt (Android Keystore) |

## APK herunterladen

Jeder Push baut automatisch die APK (GitHub Actions):

1. Im Repo auf **Actions** → letzter Lauf von **„APK bauen“** klicken
2. Unten bei **Artifacts** → **PowerIPTV-APK** herunterladen und entpacken
3. `PowerIPTV.apk` aufs Handy/TV kopieren und installieren
   (Installation aus „unbekannten Quellen“ erlauben)

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
