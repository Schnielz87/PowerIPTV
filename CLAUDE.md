# PowerIPTV – Hinweise fuer Claude

- **Bedienungsanleitung immer mitpflegen:** Nach jeder Aenderung an Funktionen oder Bedienung
  `tools/manual/make_manual.js` anpassen und mit `node tools/manual/make_manual.js` die Datei
  `docs/PowerIPTV-Anleitung.docx` neu erzeugen (Versionsnummer auf der Titelseite aktualisieren).
- Releases entstehen automatisch per GitHub Actions bei jedem Push (Version 1.1.<Run-Nummer>).
- Signierschluessel liegt nur in den GitHub-Secrets – niemals ins Repo committen.
- **Alle Varianten gleich halten:** Jede Aenderung (Funktion, Optik, Bedienung) immer in ALLEN
  Software-Varianten umsetzen – Android-App (`app/`, Handy/Tablet/TV), Windows-App (`Windows/`)
  UND Samsung-TV-App (`Tizen Samsung/`, JavaScript, Chromium 56: nur Flexbox, Build mit esbuild ->
  `node build.js`; Funktionen, die der Fernseher nicht kann – Aufnahmen, Downloads, VPN, Multi-Screen – entfallen dort)
  UND iOS-App (`iOS/`, SwiftUI + MobileVLCKit, Projekt via XcodeGen `project.yml` + `Podfile`, Build nur im CI auf macOS).
- Gemeinsamer Player: VLC (Android, Windows, iOS); Samsung TV nutzt zwangsweise AVPlay.
  Gemeinsame Logik moeglichst als geteilte Datei ablegen (siehe `include(...)` in `Windows/build.gradle.kts`).
- Windows-App lokal pruefen: `cd Windows && gradle compileKotlin` (Starten geht hier nicht, Google-Maven ist gesperrt).
- Tizen-App lokal pruefen: `cd "Tizen Samsung" && npm ci && node build.js`, dann `build/` im Browser (1920x1080) testen.
