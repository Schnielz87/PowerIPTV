# PowerIPTV – Hinweise fuer Claude

- **Bedienungsanleitung immer mitpflegen:** Nach jeder Aenderung an Funktionen oder Bedienung
  `tools/manual/make_manual.js` anpassen und mit `node tools/manual/make_manual.js` die Datei
  `docs/PowerIPTV-Anleitung.docx` neu erzeugen (Versionsnummer auf der Titelseite aktualisieren).
- Releases entstehen automatisch per GitHub Actions bei jedem Push (Version 1.1.<Run-Nummer>).
- Signierschluessel liegt nur in den GitHub-Secrets – niemals ins Repo committen.
