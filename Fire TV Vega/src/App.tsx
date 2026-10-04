/**
 * Portiva – PowerIPTV fuer Fire TV mit Vega OS.
 *
 * Aufbau (wie beim Samsung-Fernseher mit AVPlay):
 *  - unten: Hardware-Video-Player von Vega (W3C Media + KeplerVideoSurfaceView)
 *  - oben:  durchsichtige WebView mit der Portiva-TV-Oberflaeche (gleicher Code wie "Tizen Samsung/")
 * Die Oberflaeche schickt Netzwerk-Anfragen und Video-Befehle per postMessage hierher,
 * weil die WebView fremde IPTV-Server (CORS/HTTP) nicht direkt abfragen darf.
 */
import React, {useCallback, useEffect, useRef, useState} from 'react';
import {BackHandler, StyleSheet, View} from 'react-native';
import {WebView} from '@amazon-devices/webview';
import {KeplerVideoSurfaceView, VideoPlayer} from '@amazon-devices/react-native-w3cmedia';

const WEB_UI = 'file:///pkg/assets/web/index.html';
const VIDEO_EVENTS = ['waiting', 'playing', 'pause', 'timeupdate', 'durationchange', 'ended', 'error', 'loadedmetadata'];
type Fit = 'fit' | 'fill' | 'strech';
const FIT: Record<string, Fit> = {contain: 'fit', cover: 'fill', fill: 'strech'};

export default function App() {
  const web = useRef<any>(null);
  const player = useRef<VideoPlayer | null>(null);
  const surface = useRef<string | null>(null);
  const queue = useRef<Promise<void>>(Promise.resolve());
  const [fit, setFit] = useState<Fit>('fit');

  /** Antwort an die Oberflaeche (window.__vega in "Tizen Samsung/src/vega.js"). */
  const send = useCallback((msg: object) => {
    web.current?.injectJavaScript(`window.__vega && window.__vega(${JSON.stringify(msg)}); true;`);
  }, []);

  const videoEvent = useCallback((ev: string) => {
    const p: any = player.current;
    if (!p) return;
    const audio: object[] = [];
    try {
      const list = p.audioTracks;
      for (let i = 0; list && i < list.length; i++) {
        const a = list[i];
        audio.push({label: a.label || '', language: a.language || '', enabled: !!a.enabled});
      }
    } catch (e) { /* noch keine Spuren */ }
    const duration = Number(p.duration);
    send({t: 'v', ev, currentTime: Number(p.currentTime) || 0, duration: isFinite(duration) ? duration : null, paused: !!p.paused, audio});
  }, [send]);

  const release = async () => {
    const p = player.current;
    player.current = null;
    if (!p) return;
    try { p.pause(); } catch (e) { /* egal */ }
    try { p.clearSurfaceHandle(surface.current ?? ''); } catch (e) { /* egal */ }
    try { await p.deinitialize(); } catch (e) { /* egal */ }
  };

  /** Video-Befehle nacheinander ausfuehren (Oeffnen ist asynchron). */
  const video = useCallback((msg: any) => {
    queue.current = queue.current.then(async () => {
      switch (msg.cmd) {
        case 'open': {
          await release();
          const p = new VideoPlayer();
          await p.initialize();
          VIDEO_EVENTS.forEach((ev) => p.addEventListener(ev, () => videoEvent(ev)));
          player.current = p;
          if (msg.fit) setFit(FIT[msg.fit] || 'fit');
          if (surface.current) p.setSurfaceHandle(surface.current);
          p.autoplay = true;
          p.src = msg.url;
          break;
        }
        case 'play': try { await player.current?.play(); } catch (e) { videoEvent('error'); } break;
        case 'pause': player.current?.pause(); break;
        case 'seek': if (player.current) player.current.currentTime = msg.time; break;
        case 'rate': if (player.current) player.current.playbackRate = msg.rate; break;
        case 'fit': setFit(FIT[msg.fit] || 'fit'); break;
        case 'audio': {
          const list: any = player.current?.audioTracks;
          for (let i = 0; list && i < list.length; i++) list[i].enabled = i === msg.index;
          videoEvent('timeupdate');
          break;
        }
        case 'stop': await release(); break;
      }
    }).catch(() => videoEvent('error'));
  }, [videoEvent]);

  /** HTTP fuer die Oberflaeche (IPTV-Anbieter, EPG, Portiva Link, GitHub-Update). */
  const http = useCallback(async (msg: any) => {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), msg.timeout || 30000);
    try {
      const res = await fetch(msg.url, {method: msg.method || 'GET', headers: msg.headers || {}, body: msg.body ?? undefined, signal: ctrl.signal});
      const text = await res.text();
      send({t: 'http', id: msg.id, ok: true, status: res.status, text});
    } catch (e) {
      send({t: 'http', id: msg.id, ok: false});
    } finally {
      clearTimeout(timer);
    }
  }, [send]);

  const onMessage = useCallback((event: any) => {
    let msg: any;
    try { msg = JSON.parse(event.nativeEvent.data); } catch (e) { return; }
    if (msg.t === 'http') http(msg);
    else if (msg.t === 'v') video(msg);
    else if (msg.t === 'exit') { release(); BackHandler.exitApp(); }
  }, [http, video]);

  useEffect(() => () => { release(); }, []);

  return (
    <View style={styles.root}>
      <KeplerVideoSurfaceView
        style={styles.layer}
        scalingmode={fit}
        onSurfaceViewCreated={(handle: string) => { surface.current = handle; player.current?.setSurfaceHandle(handle); }}
        onSurfaceViewDestroyed={(handle: string) => { surface.current = null; try { player.current?.clearSurfaceHandle(handle); } catch (e) { /* egal */ } }}
      />
      <WebView
        ref={web}
        style={[styles.layer, styles.web]}
        hasTVPreferredFocus
        source={{uri: WEB_UI}}
        javaScriptEnabled
        domStorageEnabled
        allowFileAccess
        allowSystemKeyEvents
        allowsDefaultMediaControl={false}
        mediaPlaybackRequiresUserAction={false}
        mixedContentMode="always"
        injectedJavaScriptBeforeContentLoaded={'window.__PORTIVA_VEGA = true; true;'}
        onMessage={onMessage}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {flex: 1, backgroundColor: '#000000'},
  layer: {position: 'absolute', left: 0, top: 0, right: 0, bottom: 0},
  web: {backgroundColor: 'transparent'},
});
