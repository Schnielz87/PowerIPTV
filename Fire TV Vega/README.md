# Portiva – Fire TV mit Vega OS (neue Amazon-Sticks)

🆕 **Nur fuer die NEUEN Amazon Fire TV Sticks ohne Android:** Fire TV Stick 4K Select, Fire TV Stick HD (2026),
Fire TV Stick 4K (2026) und alle kommenden Sticks. Fire TV mit **Fire OS** (Fire TV Stick Lite/3. Gen., 4K, 4K Max,
4K Plus, Cube) nutzen weiterhin die normale APK aus `Android/`.
Welcher Stick? *Einstellungen → Mein Fire TV → Info*.

## Aufbau

- Hardware-Video-Player von Vega (`@amazon-devices/react-native-w3cmedia`) liegt unten,
  darueber eine durchsichtige WebView mit der Portiva-TV-Oberflaeche – derselbe Code wie `../Tizen Samsung/`
  (`node "../Tizen Samsung/build.js" --vega assets/web`).
- Die Oberflaeche schickt Netzwerk-Anfragen und Video-Befehle per Bruecke (`Tizen Samsung/src/vega.js`) an
  `src/App.tsx`, weil die WebView fremde IPTV-Server nicht direkt abfragen darf.

## Bauen

Im CI automatisch (Job `firetv-vega`) → Release-Datei `Portiva-FireTV-Vega-v1.1.x.vpkg`.
Lokal (Linux/Mac/WSL mit Vega SDK): `source ~/vega/env && npm install && npm run build:app`.

## Installieren

Nur ueber den Amazon-Entwicklermodus (kostenloses Amazon-Entwicklerkonto):

```sh
vega devmode login
vega devmode enable-device --code <Code vom Fernseher>
vega device list
vega device -d <Seriennummer> install-app --packagePath Portiva-FireTV-Vega-v1.1.x.vpkg
vega device -d <Seriennummer> launch-app --appName app.portiva.firetv.main
```

Genaue Schritte: Bedienungsanleitung, Kapitel 18.
