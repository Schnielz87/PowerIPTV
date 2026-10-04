# Portiva – PowerIPTV für Enigma2 (.ipk)

Plugin für Enigma2-Receiver (VU+, Dreambox, Gigablue, Octagon, Zgemma … mit OpenATV, OpenPLi, VTi u. a.).

## Funktionen

Gedacht für die Bedienung am Fernseher mit der Receiver-Fernbedienung (HD- und FHD-Skins).

- **Startseite mit Kacheln**: Live TV, Filme, Serien (große Bild-Kacheln) + Weiterschauen, Suche, TV-Guide, Favoriten, Zuletzt gesehen, Benutzer wechseln, Vom Handy empfangen, Einstellungen, Update, Playlist aktualisieren
- Zugänge: **Xtream Codes** oder **M3U-Link**, mehrere Benutzer, Ablaufdatum/Verbindungen
- Kategorien mit **♥ Favoriten / Zuletzt gesehen / Alle**, **Sprachfilter** (gelb), **Kindersicherung** mit PIN (rot, Erwachseneninhalte automatisch gesperrt), **Bouquet-Export** (grün)
- Listen: **Sortieren** (rot), **Favorit** (gelb), **Filtern** (blau), MENU = **Download** auf HDD/USB, INFO = Programm
- **Neues Design** (Grafiken aus `tools/make_skin.py`, HD + Full-HD): Glas-Panels, Bild-Kacheln, Leucht-Fokus, Symbole
- **Live-Vorschau** in der Senderliste (Bild-im-Bild über `session.VideoPicture`/Pig), abschaltbar in den Einstellungen; Filme/Serien mit Cover
- **Zoom-Effekt** (`zoom.py`): Vorschau zieht per /proc/stb/vmpeg/0/dst_* zum Vollbild auf und beim Verlassen wieder zurück
- **Verbindungsschutz** (`StreamSwitch` in `common.py`): nie zwei Streams gleichzeitig, Pause zwischen Schliessen/Oeffnen, Zapp-Puffer, Vorschau max. alle 3 s, Pause wenn alle Verbindungen des Zugangs belegt sind
- **Live TV Info-Leiste**: Logo, Name, Uhr, Jetzt mit Fortschritt, Weiter; ▲▼/CH = umschalten, ROT = letzter Sender
- **Senderliste** im Player (OK) mit **EPG aktualisieren** (grün)
- **Programm / Catch-up** (INFO): ganzes EPG des Senders, Archiv-Sendungen (►) nachträglich abspielen
- **Weiterschauen** für Filme/Folgen, nächste Folge automatisch
- **Portiva Link**: Receiver ist im Heimnetz als Gerät sichtbar (TCP 47800, UDP 47801) – Handy/PC können Filme/Sender hierher senden; BLAU im Player sendet an ein anderes Portiva-Gerät
- **QR**: Zugang vom Handy empfangen (Kopplungs-Code) bzw. eigenen Zugang als QR anzeigen (nur selbst ansehen – enthält Zugangsdaten)
- **Update** direkt von GitHub (opkg, danach GUI-Neustart), automatische Prüfung 1×/Tag
- Player wählbar: GStreamer (4097) oder – mit ServiceApp – exteplayer3 (5002) bzw. GStreamer über ServiceApp (5001)

Nicht enthalten (passt nicht zur Receiver-Hardware): Multi-Screen, Bild-in-Bild, VPN, Spul-Vorschaubilder, KI-Empfehlungen.

## Bauen

```
python3 build.py 1.1.123   # -> out/enigma2-plugin-extensions-poweriptv_1.1.123_all.ipk
```
Im CI automatisch (Job `enigma2`) → Release-Datei `PowerIPTV-Enigma2.ipk`.

## Installieren

1. `PowerIPTV-Enigma2.ipk` per FTP (z. B. FileZilla, Benutzer `root`) nach `/tmp` auf den Receiver kopieren.
2. Installieren: Menü → Plugins → (grün/blau) „Lokale Erweiterungen installieren“ **oder** per Telnet/SSH:
   `opkg install /tmp/PowerIPTV-Enigma2.ipk`
3. GUI neu starten (Menü → Standby/Neustart → GUI neu starten).
4. Menü → Plugins → **PowerIPTV** → Zugang eintragen.

Daten liegen unter `/etc/enigma2/PowerIPTV/` (Zugangsdaten nur für root lesbar).
