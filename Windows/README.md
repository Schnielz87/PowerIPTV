# Portiva – PowerIPTV für Windows

Windows-Version der App (Kotlin + Compose Desktop, Wiedergabe mit VLC).

## Herunterladen & installieren

Jede neue Version wird automatisch gebaut und unter **Releases** veröffentlicht:

- **Installer (empfohlen):** [Portiva-Windows-Setup.exe](https://github.com/Schnielz87/PowerIPTV/releases/latest/download/Portiva-Windows-Setup.exe)
- Alternativ `Portiva-Windows-…msi` oder ohne Installation `Portiva-Windows-Portable-…zip` (entpacken, `Portiva.exe` starten)

Die Installationsdateien sind über 100 MB groß (Java-Laufzeit + VLC sind enthalten). Deshalb liegen sie
nicht direkt in diesem Ordner, sondern unter *Releases*. GitHub erlaubt im Code-Ordner nur Dateien bis 100 MB.

Beim ersten Start warnt Windows evtl. („Der Computer wurde durch Windows geschützt“):
**Weitere Informationen → Trotzdem ausführen**.

## Aufbau

| Pfad | Inhalt |
|---|---|
| `src/main/kotlin/com/poweriptv/desktop/` | Windows-Oberfläche, Player, Speicher |
| `../app/src/main/java/com/poweriptv/app/data/` | Xtream-/M3U-Logik, geteilt mit der Android-App |
| `src/main/resources/` | Logo, Start-Klang, Kachelbilder |
| `icon/` | App-Symbol (`make_icon.py` erzeugt es aus dem Android-Logo) |

Bauen (Windows, JDK 17): `gradlew.bat -p Windows packageExe` – VLC (`libvlc.dll`, `libvlccore.dll`, `plugins/`)
gehört nach `Windows/build/vlc-resources/windows/vlc/` (macht der GitHub-Build automatisch).
Daten (Zugänge, Favoriten, Verlauf) liegen unter `%APPDATA%\Portiva`, Passwörter sind mit Windows-DPAPI verschlüsselt.
