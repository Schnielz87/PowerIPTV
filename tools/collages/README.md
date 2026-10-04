# Startseiten-Collagen (Live TV / Filme / Serien)

Die drei Hintergrundbilder der grossen Startseiten-Kacheln liegen in:

```
Android/app/src/main/res/drawable-nodpi/live_tv_collage.webp
Android/app/src/main/res/drawable-nodpi/movies_collage.webp
Android/app/src/main/res/drawable-nodpi/series_collage.webp
```

**Austauschen:** Eigenes Bild mit exakt demselben Dateinamen dort ablegen (alte Datei ersetzen), committen – fertig.
Android-Ressourcennamen duerfen nur Kleinbuchstaben, Ziffern und `_` enthalten (keine Bindestriche).

**Empfehlung:** WebP, 1200 × 600 px (2:1), Qualitaet ~80, max. ca. 150 KB.
Wichtiges Motiv eher am Rand – die Mitte wird fuer Icon und Schrift abgedunkelt und je nach
Bildschirm (Handy hoch/quer, TV) links/rechts oder oben/unten etwas beschnitten.
Keine Icons oder Schrift ins Bild einbauen – die kommen von der App.

**Aktuell verwendet (alle Varianten: Android, Windows, Samsung/Fire TV Vega, iOS):** die drei Kacheln aus
`vorlage_kacheln.png` (vom Projektinhaber bereitgestellt; keine echten Senderlogos, Plakate oder Titel).
Das eingebaute Symbol und die Schrift wurden herausretuschiert, weil die App beides selbst darueberlegt.
iOS: `iOS/Portiva/Assets.xcassets/*_collage.imageset` (JPEG).

Bitte keine echten Senderlogos, Filmplakate oder Fotos echter Personen verwenden.

`collage.html` + `generate.js` + `generate_webp.py` erzeugen eine rein gezeichnete Ersatzversion (wird derzeit nicht verwendet).
