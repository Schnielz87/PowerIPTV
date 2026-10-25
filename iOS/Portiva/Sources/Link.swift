import Foundation
import Network
import UIKit

// Portiva Link – gleiches Protokoll wie Android/Windows (PortivaLink.kt) und Samsung (link.js):
//   GET  /portiva/hello · POST /portiva/play · POST /portiva/pair · GET /portiva/offer/<code>  (TCP 47800)

let linkPort: UInt16 = 47800

struct LinkAccount: Codable {
    var name: String
    var type: String
    var serverUrl: String = ""
    var username: String = ""
    var password: String = ""
    var m3uUrl: String = ""
    var epgUrl: String = ""

    init(_ p: Profile) {
        name = p.name; type = p.type == .XTREAM ? "XTREAM" : "M3U_URL"
        serverUrl = p.serverUrl; username = p.username; password = p.password; m3uUrl = p.m3uUrl; epgUrl = p.epgUrl
    }
}

struct LinkHello: Codable { var app = "portiva"; var name: String; var platform: String; var port: Int; var receive = true }
struct LinkPlay: Codable { var title: String; var url: String; var live: Bool; var positionMs: Int64 = 0; var durationMs: Int64 = 0; var logo: String? = nil; var from: String = "" }
struct LinkPair: Codable { var code: String; var account: LinkAccount; var from: String = "" }
struct LinkDevice: Identifiable, Hashable { var id: String { host }; let name: String; let platform: String; let host: String; let port: Int }

enum LinkCodes {
    static let accountPrefix = "PORTIVA1:"
    static let pairPrefix = "PORTIVA-PAIR:"

    static func accountQr(_ p: Profile) -> String {
        let data = (try? JSONEncoder().encode(LinkAccount(p))) ?? Data()
        return accountPrefix + data.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }

    static func parseAccount(_ text: String) -> LinkAccount? {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard t.hasPrefix(accountPrefix) else { return nil }
        var b = String(t.dropFirst(accountPrefix.count)).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while b.count % 4 != 0 { b += "=" }
        guard let d = Data(base64Encoded: b) else { return nil }
        return try? JSONDecoder().decode(LinkAccount.self, from: d)
    }

    struct PairTarget { let host: String; let port: Int; let code: String }

    static func parsePair(_ text: String) -> PairTarget? {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard t.hasPrefix(pairPrefix) else { return nil }
        let parts = t.dropFirst(pairPrefix.count).split(separator: ":").map(String.init)
        guard parts.count == 3 || parts.count == 4, let port = Int(parts[1]) else { return nil } // 4. Teil (1.1.143) ignorieren
        return PairTarget(host: parts[0], port: port, code: parts[2])
    }

    static func pairQr(ip: String, port: Int, code: String) -> String { "\(pairPrefix)\(ip):\(port):\(code)" }
    static func newCode() -> String { String(Int.random(in: 100000...999999)) }

    /** IPv4 im WLAN (en0) bzw. erstes privates Netz. */
    static func localIPv4() -> String? {
        var addr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&addr) == 0, let first = addr else { return nil }
        defer { freeifaddrs(addr) }
        var best: String?
        var p: UnsafeMutablePointer<ifaddrs>? = first
        while let cur = p {
            let ifa = cur.pointee
            if ifa.ifa_addr.pointee.sa_family == UInt8(AF_INET) {
                var host = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                getnameinfo(ifa.ifa_addr, socklen_t(ifa.ifa_addr.pointee.sa_len), &host, socklen_t(host.count), nil, 0, NI_NUMERICHOST)
                let ip = String(cString: host)
                let name = String(cString: ifa.ifa_name)
                if ip.hasPrefix("10.") || ip.hasPrefix("192.168.") || ip.range(of: "^172\\.(1[6-9]|2\\d|3[01])\\.", options: .regularExpression) != nil {
                    if name == "en0" { return ip }
                    if best == nil { best = ip }
                }
            }
            p = ifa.ifa_next
        }
        return best
    }
}

/** Schutz des Empfangs-Dienstes (wie LinkSecurity in Android/Windows): nur Heimnetz, keine Webseiten,
 *  Sperre nach 5 falschen Codes fuer 10 Minuten, nur gueltige Adressen und begrenzte Feldlaengen. */
@MainActor
enum LinkSecurity {
    private static var fails: [String: [Date]] = [:]
    private static var total: [Date] = []
    private static let window: TimeInterval = 600

    static func isLocal(_ endpoint: NWEndpoint) -> Bool {
        guard case let .hostPort(host, _) = endpoint else { return false }
        switch host {
        case .ipv4(let a): return isLocalV4([UInt8](a.rawValue))
        case .ipv6(let a):
            if let v4 = a.asIPv4 { return isLocalV4([UInt8](v4.rawValue)) }
            let b = [UInt8](a.rawValue)
            if a.isLoopback || a.isLinkLocal { return true }
            return b.first.map { $0 & 0xfe == 0xfc } ?? false // fc00::/7
        default: return false
        }
    }

    private static func isLocalV4(_ b: [UInt8]) -> Bool {
        guard b.count == 4 else { return false }
        return b[0] == 10 || b[0] == 127 || (b[0] == 172 && (16...31).contains(b[1])) ||
            (b[0] == 192 && b[1] == 168) || (b[0] == 169 && b[1] == 254)
    }

    static func peer(_ endpoint: NWEndpoint) -> String {
        if case let .hostPort(host, _) = endpoint { return "\(host)" }
        return "?"
    }

    static func isWebsiteOrigin(_ origin: String?) -> Bool {
        guard let o = origin?.trimmingCharacters(in: .whitespaces).lowercased() else { return false }
        return o.hasPrefix("http://") || o.hasPrefix("https://")
    }

    static func isLocalHostHeader(_ host: String?) -> Bool {
        guard var h = host?.trimmingCharacters(in: .whitespaces).lowercased(), !h.isEmpty else { return true }
        if h.hasPrefix("[") { return true }
        h = h.components(separatedBy: ":")[0]
        if h.range(of: #"^\d{1,3}(\.\d{1,3}){3}$"#, options: .regularExpression) != nil { return true }
        if !h.contains(".") { return true }
        return [".local", ".lan", ".home", ".home.arpa", ".fritz.box", ".box", ".internal"].contains { h.hasSuffix($0) }
    }

    private static func prune(_ l: [Date]) -> [Date] { l.filter { $0 > Date().addingTimeInterval(-window) } }

    static func blocked(_ peer: String) -> Bool {
        total = prune(total)
        let mine = prune(fails[peer] ?? [])
        fails[peer] = mine
        return mine.count >= 5 || total.count >= 25
    }

    static func fail(_ peer: String) { fails[peer, default: []].append(Date()); total.append(Date()) }
    static func success(_ peer: String) { fails[peer] = nil }

    private static func ok(_ u: String?, _ max: Int = 4096, _ schemes: [String] = ["http://", "https://"]) -> Bool {
        guard let u, !u.isEmpty else { return true }
        if u.count > max || u.unicodeScalars.contains(where: { $0.value < 0x20 }) { return false }
        let l = u.lowercased()
        return schemes.contains { l.hasPrefix($0) }
    }

    private static func okServer(_ u: String, _ max: Int = 4096) -> Bool {
        if u.isEmpty { return true }
        if u.count > max || u.unicodeScalars.contains(where: { $0.value < 0x20 }) { return false }
        let l = u.lowercased()
        if l.range(of: #"^[a-z][a-z0-9+.-]*:(?![0-9])"#, options: .regularExpression) != nil {
            return l.hasPrefix("http://") || l.hasPrefix("https://")
        }
        return true
    }

    static func valid(_ p: LinkPlay) -> Bool {
        !p.url.isEmpty && ok(p.url, 4096, ["http://", "https://", "rtsp://", "rtmp://"]) && ok(p.logo, 2048) &&
            p.title.count <= 300 && p.from.count <= 100 && p.positionMs >= 0 && p.durationMs >= 0
    }

    static func valid(_ a: LinkAccount) -> Bool {
        a.name.count <= 100 && a.username.count <= 256 && a.password.count <= 256 && a.type.count <= 20 &&
            okServer(a.serverUrl, 2048) && okServer(a.m3uUrl) && okServer(a.epgUrl)
    }

    /** Codes in gleichbleibender Zeit vergleichen. */
    static func sameCode(_ a: String?, _ b: String?) -> Bool {
        guard let a, let b else { return false }
        let x = Array(a.utf8), y = Array(b.utf8)
        guard x.count == y.count else { return false }
        var d: UInt8 = 0
        for i in 0..<x.count { d |= x[i] ^ y[i] }
        return d == 0
    }
}

/** Empfangs-Dienst + Senden (Zugang/Wiedergabe). */
@MainActor
final class LinkService: ObservableObject {
    private weak var app: AppState?
    private var listener: NWListener?
    private(set) var port: Int = 0
    /** Gerade angezeigter Empfangs-Code. */
    @Published var pairCode: String?
    /** Zugang wurde empfangen. */
    @Published var received: Profile?
    /** Zugang (fuer Samsung-TV) zum Abholen: Code -> (Zugang, gueltig bis). */
    private var offers: [String: (LinkAccount, Date)] = [:]
    var onOfferTaken: ((LinkAccount) -> Void)?

    init(app: AppState) { self.app = app }

    var deviceName: String { UIDevice.current.name }

    func start() {
        for p in [linkPort, linkPort + 2, linkPort + 3] {
            guard let l = try? NWListener(using: .tcp, on: NWEndpoint.Port(rawValue: p)!) else { continue }
            l.newConnectionHandler = { [weak self] c in Task { @MainActor in self?.handle(c) } }
            l.start(queue: .main)
            listener = l
            port = Int(p)
            break
        }
    }

    func offer(_ code: String, _ a: LinkAccount) { offers[code] = (a, Date().addingTimeInterval(300)) }

    // ---------- kleiner HTTP-Dienst ----------
    private func handle(_ c: NWConnection) {
        // Nur Geraete aus dem eigenen Heimnetz
        guard LinkSecurity.isLocal(c.endpoint) else { c.cancel(); return }
        let peer = LinkSecurity.peer(c.endpoint)
        c.start(queue: .main)
        var buffer = Data()
        func receive() {
            c.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, done, err in
                Task { @MainActor in
                    if let data { buffer.append(data) }
                    if let req = Self.parse(buffer) {
                        let result: (Int, String)
                        if LinkSecurity.isWebsiteOrigin(req.origin) || !LinkSecurity.isLocalHostHeader(req.host) {
                            result = (403, #"{"ok":false,"error":"Nicht erlaubt"}"#)
                        } else {
                            result = self?.route(req.method, req.path, req.body, peer) ?? (500, "{}")
                        }
                        Self.respond(c, result.0, result.1)
                    } else if done || err != nil || buffer.count > 64_000 {
                        c.cancel()
                    } else { receive() }
                }
            }
        }
        receive()
    }

    private struct Request { let method: String; let path: String; let body: Data; let origin: String?; let host: String? }

    private static func parse(_ d: Data) -> Request? {
        guard let headEnd = d.range(of: Data("\r\n\r\n".utf8)) else { return nil }
        let head = String(decoding: d[..<headEnd.lowerBound], as: UTF8.self)
        let lines = head.components(separatedBy: "\r\n")
        let first = lines.first?.split(separator: " ") ?? []
        guard first.count >= 2 else { return nil }
        func header(_ name: String) -> String? {
            guard let line = lines.dropFirst().first(where: { $0.lowercased().hasPrefix(name + ":") }) else { return nil }
            return String(line.dropFirst(name.count + 1)).trimmingCharacters(in: .whitespaces)
        }
        let length = min(Int(header("content-length") ?? "") ?? 0, 64_000)
        let body = d[headEnd.upperBound...]
        if body.count < length { return nil }
        return Request(method: String(first[0]).uppercased(), path: String(first[1]).components(separatedBy: "?")[0],
                       body: Data(body.prefix(length)), origin: header("origin"), host: header("host"))
    }

    private static func respond(_ c: NWConnection, _ code: Int, _ body: String) {
        let b = Data(body.utf8)
        let head = "HTTP/1.1 \(code) \(code < 300 ? "OK" : "Error")\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type\r\nContent-Length: \(b.count)\r\nConnection: close\r\n\r\n"
        c.send(content: Data(head.utf8) + b, completion: .contentProcessed { _ in c.cancel() })
    }

    private func route(_ method: String, _ path: String, _ body: Data, _ peer: String) -> (Int, String) {
        let dec = JSONDecoder()
        if method == "OPTIONS" { return (204, "") }
        if (path == "/portiva/pair" || path.hasPrefix("/portiva/offer/")) && LinkSecurity.blocked(peer) {
            return (429, #"{"ok":false,"error":"Zu viele falsche Codes – bitte 10 Minuten warten"}"#)
        }
        if method == "GET" && path == "/portiva/hello" {
            let h = LinkHello(name: deviceName, platform: UIDevice.current.userInterfaceIdiom == .pad ? "ipad" : "iphone", port: port)
            return (200, String(decoding: (try? JSONEncoder().encode(h)) ?? Data(), as: UTF8.self))
        }
        if method == "POST" && path == "/portiva/pair" {
            guard let p = try? dec.decode(LinkPair.self, from: body), LinkSecurity.valid(p.account) else {
                LinkSecurity.fail(peer); return (400, #"{"ok":false}"#)
            }
            guard LinkSecurity.sameCode(pairCode, p.code), let app else {
                LinkSecurity.fail(peer); return (403, #"{"ok":false,"error":"Code passt nicht"}"#)
            }
            LinkSecurity.success(peer)
            pairCode = nil
            received = app.importAccount(p.account)
            return (200, #"{"ok":true}"#)
        }
        if method == "POST" && path == "/portiva/play" {
            guard let p = try? dec.decode(LinkPlay.self, from: body), LinkSecurity.valid(p), let app else {
                return (400, #"{"ok":false,"error":"Ungueltige Adresse"}"#)
            }
            let item = ContentItem(id: "link:\(p.url.hashValue)", name: p.title, type: p.live ? .LIVE : .MOVIE, logo: p.logo)
            app.playing = nil
            app.play([PlayEntry(title: p.title, url: p.url, live: p.live, item: item, startAt: p.live ? 0 : Double(p.positionMs))])
            app.show(p.from.isEmpty ? "Wiedergabe übernommen" : "Von „\(p.from)“ übernommen")
            return (200, #"{"ok":true}"#)
        }
        if method == "GET" && path.hasPrefix("/portiva/offer/") {
            let code = String(path.dropFirst("/portiva/offer/".count))
            // Abgelaufene Angebote entfernen; liegt nichts bereit, ist die Nachfrage des Samsung-TV kein Fehlversuch
            offers = offers.filter { $0.value.1 > Date() }
            if offers.isEmpty { return (404, #"{"ok":false}"#) }
            guard let key = offers.keys.first(where: { LinkSecurity.sameCode($0, code) }), let o = offers[key], o.1 > Date() else {
                LinkSecurity.fail(peer); return (404, #"{"ok":false}"#)
            }
            offers[key] = nil
            LinkSecurity.success(peer)
            onOfferTaken?(o.0)
            return (200, String(decoding: (try? JSONEncoder().encode(o.0)) ?? Data(), as: UTF8.self))
        }
        return (404, #"{"ok":false}"#)
    }

    // ---------- Senden ----------

    /** Portiva-Geraete im eigenen /24-Netz suchen. */
    /** Per IP-Adresse eingetragene Geraete (z.B. Enigma2-Receiver am LAN-Kabel in anderem Netzbereich). */
    static var savedHosts: [String] {
        get { UserDefaults.standard.stringArray(forKey: "linkHosts") ?? [] }
        set { UserDefaults.standard.set(Array(newValue.prefix(6)), forKey: "linkHosts") }
    }

    /** Geraet unter "IP" oder "IP:Port" pruefen. */
    nonisolated static func probe(_ address: String) async -> LinkDevice? {
        let a = address.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "http://", with: "")
        let parts = a.split(separator: ":")
        guard let first = parts.first else { return nil }
        let host = String(first)
        let port = parts.count > 1 ? (Int(parts[1]) ?? Int(linkPort)) : Int(linkPort)
        guard let url = URL(string: "http://\(host):\(port)/portiva/hello") else { return nil }
        var r = URLRequest(url: url); r.timeoutInterval = 3
        guard let res = try? await URLSession.shared.data(for: r), (res.1 as? HTTPURLResponse)?.statusCode == 200,
              let h = try? JSONDecoder().decode(LinkHello.self, from: res.0), h.app == "portiva" else { return nil }
        return LinkDevice(name: h.name, platform: h.platform, host: host, port: h.port > 0 ? h.port : port)
    }

    nonisolated static func discover() async -> [LinkDevice] {
        let saved = await MainActor.run { savedHosts }
        var manual: [LinkDevice] = []
        for a in saved { if let d = await probe(a) { manual.append(d) } }
        let scanned = await scan()
        let extra = manual.filter { m in !scanned.contains { $0.host == m.host } }
        return (scanned + extra).sorted { $0.name.lowercased() < $1.name.lowercased() }
    }

    nonisolated static func scan() async -> [LinkDevice] {
        guard let own = LinkCodes.localIPv4() else { return [] }
        let prefix = own.split(separator: ".").dropLast().joined(separator: ".")
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 0.9
        let session = URLSession(configuration: config)
        return await withTaskGroup(of: LinkDevice?.self) { g in
            for i in 1...254 {
                let host = "\(prefix).\(i)"
                if host == own { continue }
                g.addTask {
                    guard let url = URL(string: "http://\(host):\(linkPort)/portiva/hello"),
                          let res = try? await session.data(from: url), (res.1 as? HTTPURLResponse)?.statusCode == 200,
                          let h = try? JSONDecoder().decode(LinkHello.self, from: res.0), h.app == "portiva" else { return nil }
                    return LinkDevice(name: h.name, platform: h.platform, host: host, port: h.port)
                }
            }
            var out: [LinkDevice] = []
            for await d in g { if let d { out.append(d) } }
            return out.sorted { $0.name.lowercased() < $1.name.lowercased() }
        }
    }

    /** null = Erfolg, sonst Fehlertext. */
    nonisolated static func post(host: String, port: Int, path: String, body: Data) async -> String? {
        guard let url = URL(string: "http://\(host):\(port)\(path)") else { return "Ungültige Adresse" }
        var r = URLRequest(url: url); r.httpMethod = "POST"; r.httpBody = body; r.timeoutInterval = 8
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        guard let res = try? await URLSession.shared.data(for: r), let code = (res.1 as? HTTPURLResponse)?.statusCode else {
            return "Gerät nicht erreichbar (gleiches WLAN? Portiva dort geöffnet?)"
        }
        switch code {
        case 200..<300: return nil
        case 403: return "Der Code passt nicht mehr – bitte am anderen Gerät neu anzeigen lassen"
        case 409: return "Portiva ist auf dem anderen Gerät nicht geöffnet"
        default: return "Fehler \(code)"
        }
    }

    /** Zugang an das Geraet schicken, dessen Empfangs-Code gescannt wurde (Port 0 = Samsung holt ab). */
    func sendAccount(_ target: LinkCodes.PairTarget, _ p: Profile) async -> String? {
        if target.port == 0 { offer(target.code, LinkAccount(p)); return nil }
        let body = (try? JSONEncoder().encode(LinkPair(code: target.code, account: LinkAccount(p), from: deviceName))) ?? Data()
        return await Self.post(host: target.host, port: target.port, path: "/portiva/pair", body: body)
    }

    func sendPlay(_ d: LinkDevice, _ e: PlayEntry, position: Double, duration: Double) async -> String? {
        let play = LinkPlay(title: e.title, url: e.url, live: e.live, positionMs: Int64(e.live ? 0 : position), durationMs: Int64(max(0, duration)),
                            logo: e.item?.logo, from: deviceName)
        let body = (try? JSONEncoder().encode(play)) ?? Data()
        return await Self.post(host: d.host, port: d.port, path: "/portiva/play", body: body)
    }
}
