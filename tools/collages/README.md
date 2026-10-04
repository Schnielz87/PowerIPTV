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

**Aktuell verwendet (alle Varianten: Android, Windows, Samsung/Fire TV Vega):** die selbst erzeugte, rein stilisierte
Version aus `collage.html` – komplett ohne Text, ohne Senderlogos/-namen, ohne Film-/Serientitel und ohne Personen
(nur Farben, Formen und neutrale Symbole). Damit gibt es keine Marken- oder Urheberrechtsprobleme.
Bitte keine echten Logos, Plakate oder Screenshots einsetzen.

**Neu erzeugen:**
```
cd tools/collages
NODE_PATH=$(npm root -g) node generate.js   # rendert collage.html per Chromium/Playwright
python3 generate_webp.py                   # -> WebP in Android/, Windows/ und Tizen Samsung/
```
