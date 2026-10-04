# Portiva – PowerIPTV für Enigma2 (.ipk)

Plugin für Enigma2-Receiver (VU+, Dreambox, Gigablue, Octagon, Zgemma … mit OpenATV, OpenPLi, VTi u. a.).

## Funktionen

- Zugänge: **Xtream Codes** (Server, Benutzer, Passwort) oder **M3U-Link**, mehrere Zugänge, Verbindungstest
- **Live TV**, **Filme**, **Serien** (Staffeln/Folgen) nach Kategorien, Filtern (blau), Suche über alles
- Sender-Info mit **Jetzt/Danach** (EPG des Anbieters), Film-Infos (Handlung, Genre, Bewertung)
- **Favoriten** (gelb), **Weiterschauen** bei Filmen/Folgen, nächste Folge automatisch
- Im Player: Hoch/Runter bzw. CH+/CH- = Sender wechseln, INFO = Jetzt/Danach, Tonspur/Untertitel wie gewohnt
- **Live-Kategorie als Bouquet** (grün) – dann auch in der normalen Senderliste des Receivers
- Player wählbar: GStreamer (4097) oder – mit installiertem ServiceApp – exteplayer3 (5002, mehr Formate)

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
