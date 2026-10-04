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
        guard parts.count == 3, let port = Int(parts[1]) else { return nil }
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
        c.start(queue: .main)
        var buffer = Data()
        func receive() {
            c.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, done, err in
                Task { @MainActor in
                    if let data { buffer.append(data) }
                    if let req = Self.parse(buffer) {
                        let (code, body) = self?.route(req.method, req.path, req.body) ?? (500, "{}")
                        Self.respond(c, code, body)
                    } else if done || err != nil || buffer.count > 200_000 {
                        c.cancel()
                    } else { receive() }
                }
            }
        }
        receive()
    }

    private struct Request { let method: String; let path: String; let body: Data }

    private static func parse(_ d: Data) -> Request? {
        guard let headEnd = d.range(of: Data("\r\n\r\n".utf8)) else { return nil }
        let head = String(decoding: d[..<headEnd.lowerBound], as: UTF8.self)
        let lines = head.components(separatedBy: "\r\n")
        let first = lines.first?.split(separator: " ") ?? []
        guard first.count >= 2 else { return nil }
        let length = lines.dropFirst().first { $0.lowercased().hasPrefix("content-length:") }
            .flatMap { Int($0.split(separator: ":").last?.trimmingCharacters(in: .whitespaces) ?? "") } ?? 0
        let body = d[headEnd.upperBound...]
        if body.count < length { return nil }
        return Request(method: String(first[0]).uppercased(), path: String(first[1]).components(separatedBy: "?")[0], body: Data(body.prefix(length)))
    }

    private static func respond(_ c: NWConnection, _ code: Int, _ body: String) {
        let b = Data(body.utf8)
        let head = "HTTP/1.1 \(code) \(code < 300 ? "OK" : "Error")\r\nContent-Type: application/json; charset=utf-8\r\nAccess-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type\r\nContent-Length: \(b.count)\r\nConnection: close\r\n\r\n"
        c.send(content: Data(head.utf8) + b, completion: .contentProcessed { _ in c.cancel() })
    }

    private func route(_ method: String, _ path: String, _ body: Data) -> (Int, String) {
        let dec = JSONDecoder()
        if method == "OPTIONS" { return (204, "") }
        if method == "GET" && path == "/portiva/hello" {
            let h = LinkHello(name: deviceName, platform: UIDevice.current.userInterfaceIdiom == .pad ? "ipad" : "iphone", port: port)
            return (200, String(decoding: (try? JSONEncoder().encode(h)) ?? Data(), as: UTF8.self))
        }
        if method == "POST" && path == "/portiva/pair" {
            guard let p = try? dec.decode(LinkPair.self, from: body) else { return (400, #"{"ok":false}"#) }
            guard let code = pairCode, code == p.code, let app else { return (403, #"{"ok":false,"error":"Code passt nicht"}"#) }
            pairCode = nil
            received = app.importAccount(p.account)
            return (200, #"{"ok":true}"#)
        }
        if method == "POST" && path == "/portiva/play" {
            guard let p = try? dec.decode(LinkPlay.self, from: body), let app else { return (400, #"{"ok":false}"#) }
            let item = ContentItem(id: "link:\(p.url.hashValue)", name: p.title, type: p.live ? .LIVE : .MOVIE, logo: p.logo)
            app.playing = nil
            app.play([PlayEntry(title: p.title, url: p.url, live: p.live, item: item, startAt: p.live ? 0 : Double(p.positionMs))])
            app.show(p.from.isEmpty ? "Wiedergabe übernommen" : "Von „\(p.from)“ übernommen")
            return (200, #"{"ok":true}"#)
        }
        if method == "GET" && path.hasPrefix("/portiva/offer/") {
            let code = String(path.dropFirst("/portiva/offer/".count))
            guard let o = offers[code], o.1 > Date() else { return (404, #"{"ok":false}"#) }
            offers[code] = nil
            onOfferTaken?(o.0)
            return (200, String(decoding: (try? JSONEncoder().encode(o.0)) ?? Data(), as: UTF8.self))
        }
        return (404, #"{"ok":false}"#)
    }

    // ---------- Senden ----------

    /** Portiva-Geraete im eigenen /24-Netz suchen. */
    nonisolated static func discover() async -> [LinkDevice] {
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
