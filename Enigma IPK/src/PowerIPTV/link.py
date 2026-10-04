# -*- coding: utf-8 -*-
"""
Portiva Link auf dem Receiver – gleiches Protokoll wie Android/Windows/Samsung/iOS:
  GET  /portiva/hello   -> wer bin ich        POST /portiva/play -> hier weiterschauen
  POST /portiva/pair    -> Zugang uebernehmen (nur mit dem gerade angezeigten Code)
UDP-Rundruf auf Port 47801 ("PORTIVA_DISCOVER"). Der Dienst laeuft, solange Enigma2 laeuft.
"""
import base64
import json
import random
import socket
import threading

LINK_PORT = 47800
UDP_PORT = 47801
DISCOVER = b"PORTIVA_DISCOVER"

_state = {"session": None, "pair_code": None, "on_pair": None, "port": 0, "started": False}


def local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("10.255.255.255", 1))  # sendet nichts, ermittelt nur die eigene Adresse
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return None


def hello():
    from . import store
    return {"app": "portiva", "name": store.settings().get("device_name") or "Enigma2-Receiver", "platform": "enigma2",
            "port": _state["port"] or LINK_PORT, "receive": True}


def new_code():
    return str(random.SystemRandom().randint(100000, 999999))


def pair_qr(code):
    return "PORTIVA-PAIR:%s:%d:%s" % (local_ip() or "0.0.0.0", _state["port"] or LINK_PORT, code)


# ---------- Zugang als QR (fuer Handy/Tablet) ----------
def _b64url(data):
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


def account_qr(profile):
    a = {"name": profile.get("name", ""), "type": "M3U_URL" if profile.get("type") == "m3u" else "XTREAM",
         "serverUrl": profile.get("server", ""), "username": profile.get("username", ""), "password": profile.get("password", ""),
         "m3uUrl": profile.get("m3u", ""), "epgUrl": ""}
    return "PORTIVA1:" + _b64url(json.dumps(a, separators=(",", ":")).encode("utf-8"))


def account_to_profile(a):
    m3u = a.get("type") == "M3U_URL" or (a.get("m3uUrl") and not a.get("username"))
    return {"name": a.get("name") or "Zugang", "type": "m3u" if m3u else "xtream", "server": a.get("serverUrl", ""),
            "username": a.get("username", ""), "password": a.get("password", ""), "m3u": a.get("m3uUrl", "")}


# ---------- Empfangen ----------
def start(session):
    """Beim Start von Enigma2: HTTP-Dienst + UDP-Antwort (Fehler werden ignoriert, z.B. Port belegt)."""
    _state["session"] = session
    if _state["started"]:
        return
    _state["started"] = True
    try:
        from twisted.internet import reactor
        from twisted.web import server, resource

        class Res(resource.Resource):
            isLeaf = True

            def _send(self, request, code, obj):
                request.setResponseCode(code)
                request.setHeader(b"Content-Type", b"application/json; charset=utf-8")
                request.setHeader(b"Access-Control-Allow-Origin", b"*")
                request.setHeader(b"Access-Control-Allow-Methods", b"GET, POST, OPTIONS")
                request.setHeader(b"Access-Control-Allow-Headers", b"Content-Type")
                return json.dumps(obj).encode("utf-8")

            def render_OPTIONS(self, request):
                return self._send(request, 204, {})

            def render_GET(self, request):
                path = request.path.decode() if isinstance(request.path, bytes) else request.path
                if path == "/portiva/hello":
                    return self._send(request, 200, hello())
                return self._send(request, 404, {"ok": False})

            def render_POST(self, request):
                path = request.path.decode() if isinstance(request.path, bytes) else request.path
                try:
                    body = json.loads(request.content.read().decode("utf-8") or "{}")
                except Exception:
                    return self._send(request, 400, {"ok": False})
                if path == "/portiva/play":
                    return self._send(request, 200 if on_play(body) else 409, {"ok": True})
                if path == "/portiva/pair":
                    ok = on_pair(body)
                    return self._send(request, 200 if ok else 403, {"ok": ok} if ok else {"ok": False, "error": "Code passt nicht"})
                return self._send(request, 404, {"ok": False})

        for p in range(LINK_PORT, LINK_PORT + 6):
            if p == UDP_PORT:
                continue
            try:
                reactor.listenTCP(p, server.Site(Res()))
                _state["port"] = p
                break
            except Exception:
                continue

        from twisted.internet import protocol

        class Disc(protocol.DatagramProtocol):
            def datagramReceived(self, data, addr):
                if data.strip() == DISCOVER and _state["port"]:
                    self.transport.write(json.dumps(hello()).encode("utf-8"), addr)
        try:
            reactor.listenUDP(UDP_PORT, Disc())
        except Exception:
            pass
    except Exception:
        pass


def on_play(p):
    """Film/Sender von Handy, Tablet oder PC hier weiterschauen."""
    session = _state["session"]
    if not session or not p.get("url"):
        return False
    from .player import PortivaPlayer, DirectSource
    item = {"kind": "live" if p.get("live") else "movie", "id": p["url"], "name": p.get("title") or "Portiva", "url": p["url"], "logo": p.get("logo")}
    start_at = int((p.get("positionMs") or 0) / 1000)
    try:
        from Tools.Notifications import AddPopup
        AddPopup("PowerIPTV: „%s“ von %s" % (item["name"], p.get("from") or "anderem Gerät"), 1, 5, "PowerIPTVLink")
    except Exception:
        pass
    session.open(PortivaPlayer, DirectSource(), [item], 0, bool(p.get("live")), start_at)
    return True


def on_pair(body):
    code = _state.get("pair_code")
    if not code or body.get("code") != code or not isinstance(body.get("account"), dict):
        return False
    from . import store
    prof = store.save_profile(account_to_profile(body["account"]))
    _state["pair_code"] = None
    cb = _state.get("on_pair")
    if cb:
        try:
            cb(prof)
        except Exception:
            pass
    return True


def set_pairing(code, callback):
    _state["pair_code"] = code
    _state["on_pair"] = callback


def port():
    return _state["port"]


# ---------- Senden (blockierend: im Hintergrund aufrufen) ----------
def _http(method, host, port_, path, body=None, timeout=1.5):
    try:
        from urllib.request import urlopen, Request
    except ImportError:
        from urllib2 import urlopen, Request
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = Request("http://%s:%d%s" % (host, port_, path), data=data, headers={"Content-Type": "application/json; charset=utf-8"})
    if method == "POST" and data is None:
        req.data = b"{}"
    r = urlopen(req, timeout=timeout)
    return r.getcode(), r.read().decode("utf-8", "ignore")


def discover(timeout=1.8):
    """Andere Portiva-Geraete im Heimnetz: UDP-Rundruf + direkte Suche im eigenen /24-Netz."""
    found = {}
    me = local_ip()
    lock = threading.Lock()

    def udp():
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            s.settimeout(0.3)
            for _ in range(2):
                s.sendto(DISCOVER, ("255.255.255.255", UDP_PORT))
            import time
            end = time.time() + timeout
            while time.time() < end:
                try:
                    data, addr = s.recvfrom(1024)
                except Exception:
                    continue
                try:
                    h = json.loads(data.decode("utf-8"))
                except Exception:
                    continue
                if addr[0] != me and h.get("app") == "portiva":
                    with lock:
                        found[addr[0]] = {"name": h.get("name"), "platform": h.get("platform"), "host": addr[0], "port": h.get("port") or LINK_PORT}
            s.close()
        except Exception:
            pass

    hosts = []
    if me:
        prefix = me.rsplit(".", 1)[0]
        hosts = ["%s.%d" % (prefix, i) for i in range(1, 255) if "%s.%d" % (prefix, i) != me]

    def scan(chunk):
        for h in chunk:
            try:
                code, text = _http("GET", h, LINK_PORT, "/portiva/hello", timeout=0.6)
                d = json.loads(text)
                if code == 200 and d.get("app") == "portiva":
                    with lock:
                        found.setdefault(h, {"name": d.get("name"), "platform": d.get("platform"), "host": h, "port": d.get("port") or LINK_PORT})
            except Exception:
                pass
    threads = [threading.Thread(target=udp)]
    for i in range(32):
        threads.append(threading.Thread(target=scan, args=(hosts[i::32],)))
    for t in threads:
        t.daemon = True
        t.start()
    for t in threads:
        t.join(timeout + 8)
    return sorted(found.values(), key=lambda d: (d.get("name") or "").lower())


def send_play(dev, title, url, live, position_s=0, duration_s=0, logo=None):
    """Liefert None bei Erfolg, sonst eine Fehlermeldung."""
    from . import store
    body = {"title": title, "url": url, "live": bool(live), "positionMs": int(position_s * 1000), "durationMs": int(duration_s * 1000),
            "logo": logo, "from": store.settings().get("device_name") or "Enigma2-Receiver"}
    try:
        code, _ = _http("POST", dev["host"], int(dev.get("port") or LINK_PORT), "/portiva/play", body, timeout=8)
        return None if code < 300 else "Fehler %d" % code
    except Exception as e:
        if "409" in str(e):
            return "Portiva ist auf dem anderen Gerät nicht geöffnet"
        return "Gerät nicht erreichbar"
