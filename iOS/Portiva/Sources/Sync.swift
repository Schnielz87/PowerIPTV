import CryptoKit
import Foundation

/*
 * Synchronisieren (Favoriten, eigene Listen, Weiterschauen) zwischen Geraeten mit demselben Zugang –
 * ueber Portiva Link im Heimnetz, ohne Cloud. Gleiches Format wie Android/Windows (SyncCore.kt),
 * Samsung-TV/Fire TV (sync.js) und Enigma2 (sync.py).
 * Jede Aenderung hat einen Zeitstempel (ms); pro Eintrag gewinnt die neuere Aenderung.
 * Geloeschtes bleibt 60 Tage als "del" erhalten, damit es nicht zurueckkommt.
 */

struct SyncItem: Codable, Hashable {
    var id: String
    var name: String
    var type: String
    var logo: String?
    var ext: String?
    var year: Int?
    var rating: String?
    var cat: String?
    var url: String?
    var epg: String?
    var key: String { "\(type):\(id)" }

    init(_ i: ContentItem) {
        id = i.id; name = i.name; type = i.type.rawValue; logo = i.logo; ext = i.ext; year = i.year
        rating = i.rating.map { String($0) }; cat = i.categoryId.isEmpty ? nil : i.categoryId; url = i.url
    }

    var content: ContentItem {
        var c = ContentItem(id: id, name: name, type: ContentType(rawValue: type) ?? .MOVIE)
        c.categoryId = cat ?? ""; c.logo = logo; c.url = url; c.ext = ext; c.year = year
        c.rating = rating.flatMap { Double($0.replacingOccurrences(of: ",", with: ".")) }
        return c
    }
}

struct SyncEntry: Codable {
    var k: String
    var ts: Int64 = 0
    var del: Bool = false
    var item: SyncItem?

    init(k: String, ts: Int64, del: Bool, item: SyncItem?) { self.k = k; self.ts = ts; self.del = del; self.item = item }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        k = try c.decode(String.self, forKey: .k)
        ts = try c.decodeIfPresent(Int64.self, forKey: .ts) ?? 0
        del = try c.decodeIfPresent(Bool.self, forKey: .del) ?? false
        item = try c.decodeIfPresent(SyncItem.self, forKey: .item)
    }
}

struct SyncList: Codable {
    var id: String
    var name: String = ""
    var ts: Int64 = 0
    var del: Bool = false
    var items: [SyncEntry] = []

    init(id: String, name: String, ts: Int64, del: Bool, items: [SyncEntry]) { self.id = id; self.name = name; self.ts = ts; self.del = del; self.items = items }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        ts = try c.decodeIfPresent(Int64.self, forKey: .ts) ?? 0
        del = try c.decodeIfPresent(Bool.self, forKey: .del) ?? false
        items = try c.decodeIfPresent([SyncEntry].self, forKey: .items) ?? []
    }
}

struct SyncEpisode: Codable {
    var id: String
    var season: Int = 0
    var num: Int = 0
    var title: String = ""
    var ext: String?

    init(id: String, season: Int, num: Int, title: String, ext: String?) { self.id = id; self.season = season; self.num = num; self.title = title; self.ext = ext }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        season = try c.decodeIfPresent(Int.self, forKey: .season) ?? 0
        num = try c.decodeIfPresent(Int.self, forKey: .num) ?? 0
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        ext = try c.decodeIfPresent(String.self, forKey: .ext)
    }
}

struct SyncResume: Codable {
    var k: String
    var pos: Int64 = 0
    var dur: Int64 = 0
    var ts: Int64 = 0
    var done: Bool = false
    var item: SyncItem?
    var ep: SyncEpisode?

    init(k: String, pos: Int64, dur: Int64, ts: Int64, done: Bool, item: SyncItem?, ep: SyncEpisode?) {
        self.k = k; self.pos = pos; self.dur = dur; self.ts = ts; self.done = done; self.item = item; self.ep = ep
    }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        k = try c.decode(String.self, forKey: .k)
        pos = try c.decodeIfPresent(Int64.self, forKey: .pos) ?? 0
        dur = try c.decodeIfPresent(Int64.self, forKey: .dur) ?? 0
        ts = try c.decodeIfPresent(Int64.self, forKey: .ts) ?? 0
        done = try c.decodeIfPresent(Bool.self, forKey: .done) ?? false
        item = try c.decodeIfPresent(SyncItem.self, forKey: .item)
        ep = try c.decodeIfPresent(SyncEpisode.self, forKey: .ep)
    }
}

struct SyncData: Codable {
    var v: Int = 1
    var lists: [SyncList] = []
    var resume: [SyncResume] = []

    init(lists: [SyncList], resume: [SyncResume]) { self.lists = lists; self.resume = resume }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        v = try c.decodeIfPresent(Int.self, forKey: .v) ?? 1
        lists = try c.decodeIfPresent([SyncList].self, forKey: .lists) ?? []
        resume = try c.decodeIfPresent([SyncResume].self, forKey: .resume) ?? []
    }
}

struct SyncRequest: Codable { var account: String; var data: SyncData; var from: String = "" }

struct SyncReply: Codable {
    var ok: Bool
    var data: SyncData?
    var name: String = ""
    var error: String?
    var unknown: Bool = false

    init(ok: Bool, data: SyncData? = nil, name: String = "", error: String? = nil, unknown: Bool = false) {
        self.ok = ok; self.data = data; self.name = name; self.error = error; self.unknown = unknown
    }
    init(from d: Decoder) throws {
        let c = try d.container(keyedBy: CodingKeys.self)
        ok = try c.decodeIfPresent(Bool.self, forKey: .ok) ?? false
        data = try c.decodeIfPresent(SyncData.self, forKey: .data)
        name = try c.decodeIfPresent(String.self, forKey: .name) ?? ""
        error = try c.decodeIfPresent(String.self, forKey: .error)
        unknown = try c.decodeIfPresent(Bool.self, forKey: .unknown) ?? false
    }
}

enum SyncCore {
    static let defaultList = "default"
    static let tombstoneMs: Int64 = 60 * 86_400_000

    static func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

    static func norm(_ u: String) -> String {
        var s = u.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        for p in ["http://", "https://"] where s.hasPrefix(p) { s = String(s.dropFirst(p.count)) }
        while s.hasSuffix("/") { s.removeLast() }
        return s
    }

    static func canSync(_ p: Profile) -> Bool { !p.username.isEmpty || !p.m3uUrl.isEmpty }

    /** Nur Rechner[:Port] (ohne Pfad, ohne Standard-Port 80/443). */
    static func host(_ u: String) -> String {
        var h = norm(u)
        for sep in ["/", "?", "#"] { h = h.components(separatedBy: sep)[0] }
        for port in [":80", ":443"] where h.hasSuffix(port) { h = String(h.dropLast(port.count)) }
        return h
    }

    static func query(_ u: String) -> [String: String] {
        guard let i = u.firstIndex(of: "?") else { return [:] }
        let q = String(u[u.index(after: i)...]).components(separatedBy: "#")[0]
        var out: [String: String] = [:]
        for kv in q.components(separatedBy: "&") {
            let parts = kv.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            guard let k = parts.first, !k.isEmpty else { continue }
            let v = parts.count > 1 ? String(parts[1]).replacingOccurrences(of: "+", with: " ") : ""
            out[k.lowercased()] = v.removingPercentEncoding ?? v
        }
        return out
    }

    /** Gleicher Zugang = gleiche Kennung, egal ob als Xtream-Login oder M3U-Link (mit username/password) eingetragen. */
    static func accountKey(_ p: Profile) -> String {
        let ws = CharacterSet.whitespacesAndNewlines
        let username = p.username.trimmingCharacters(in: ws)
        let q = query(p.m3uUrl)
        let user = username.isEmpty ? (q["username"] ?? "").trimmingCharacters(in: ws) : username
        let raw: String
        if !user.isEmpty {
            let pass = (username.isEmpty ? (q["password"] ?? "") : p.password).trimmingCharacters(in: ws)
            let server = !username.isEmpty && !p.serverUrl.trimmingCharacters(in: ws).isEmpty ? p.serverUrl : p.m3uUrl
            raw = "portiva-sync-v2|\(host(server))|\(user)|\(pass)"
        } else {
            raw = "portiva-sync-v2|m3u|\(norm(p.m3uUrl))"
        }
        return SHA256.hash(data: Data(raw.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    private static func newer(_ a: Int64, _ aDel: Bool, _ b: Int64, _ bDel: Bool) -> Bool { a > b || (a == b && !aDel && bDel) }

    private static func mergeEntries(_ a: [SyncEntry], _ b: [SyncEntry]) -> [SyncEntry] {
        var order: [String] = []
        var out: [String: SyncEntry] = [:]
        for e in a + b {
            if let cur = out[e.k] {
                if newer(e.ts, e.del, cur.ts, cur.del) { var n = e; n.item = e.item ?? cur.item; out[e.k] = n }
                else { var n = cur; n.item = cur.item ?? e.item; out[e.k] = n }
            } else { out[e.k] = e; order.append(e.k) }
        }
        return order.compactMap { out[$0] }
    }

    static func merge(_ a: SyncData, _ b: SyncData) -> SyncData {
        let cutoff = nowMs() - tombstoneMs
        var order: [String] = []
        var lists: [String: SyncList] = [:]
        for l in a.lists + b.lists {
            if let cur = lists[l.id] {
                var head = newer(l.ts, l.del, cur.ts, cur.del) ? l : cur
                head.items = mergeEntries(cur.items, l.items)
                lists[l.id] = head
            } else { lists[l.id] = l; order.append(l.id) }
        }
        var rOrder: [String] = []
        var resume: [String: SyncResume] = [:]
        for r in a.resume + b.resume {
            if let cur = resume[r.k] {
                if r.ts > cur.ts { var n = r; n.item = r.item ?? cur.item; n.ep = r.ep ?? cur.ep; resume[r.k] = n }
                else { var n = cur; n.item = cur.item ?? r.item; n.ep = cur.ep ?? r.ep; resume[r.k] = n }
            } else { resume[r.k] = r; rOrder.append(r.k) }
        }
        let outLists = order.compactMap { lists[$0] }
            .filter { !($0.del && $0.ts > 0 && $0.ts < cutoff) }
            .map { l -> SyncList in var n = l; n.items = l.items.filter { !($0.del && $0.ts > 0 && $0.ts < cutoff) }; return n }
        return SyncData(lists: outLists, resume: rOrder.compactMap { resume[$0] })
    }

    static func changes(_ before: SyncData, _ after: SyncData) -> (Int, Int) {
        func present(_ d: SyncData) -> Set<String> {
            Set(d.lists.filter { !$0.del }.flatMap { l in l.items.filter { !$0.del }.map { "\(l.id)|\($0.k)" } })
        }
        let b = present(before), a = present(after)
        let res = after.resume.filter { r in !before.resume.contains { $0.k == r.k && $0.ts >= r.ts } }.count
        return (a.subtracting(b).count + res, b.subtracting(a).count)
    }
}

/** Zeitstempel der lokalen Aenderungen eines Profils (negativ = geloescht). */
struct SyncMeta {
    let pid: String
    private var key: String { "syncmeta.\(pid)" }

    var all: [String: Int64] {
        get {
            guard let d = UserDefaults.standard.data(forKey: key) else { return [:] }
            return (try? JSONDecoder().decode([String: Int64].self, from: d)) ?? [:]
        }
        nonmutating set { if let d = try? JSONEncoder().encode(newValue) { UserDefaults.standard.set(d, forKey: key) } }
    }

    func ts(_ k: String) -> Int64 { abs(all[k] ?? 0) }

    func touch(_ k: String, deleted: Bool = false) {
        var m = all
        let now = SyncCore.nowMs()
        m[k] = deleted ? -now : now
        all = m
    }

    func setAll(_ values: [String: Int64]) {
        var m = all
        for (k, v) in values { m[k] = v }
        let cutoff = SyncCore.nowMs() - SyncCore.tombstoneMs
        m = m.filter { !($0.value < 0 && -$0.value < cutoff) }
        all = m
    }
}

/** Umrechnung "lokale Listen <-> Abgleich-Format" (wie SyncLists in Kotlin). */
enum SyncLists {
    struct Local { let id: String; let name: String; let items: [SyncItem] }

    static func export(_ lists: [Local], _ meta: SyncMeta) -> [SyncList] {
        let m = meta.all
        var out = lists.map { l -> SyncList in
            let present = l.items.map { SyncEntry(k: $0.key, ts: meta.ts("I|\(l.id)|\($0.key)"), del: false, item: $0) }
            let keys = Set(l.items.map { $0.key })
            let prefix = "I|\(l.id)|"
            let gone = m.filter { $0.value < 0 && $0.key.hasPrefix(prefix) && !keys.contains(String($0.key.dropFirst(prefix.count))) }
                .map { SyncEntry(k: String($0.key.dropFirst(prefix.count)), ts: -$0.value, del: true, item: nil) }
            return SyncList(id: l.id, name: l.name, ts: meta.ts("L|\(l.id)"), del: false, items: present + gone)
        }
        let ids = Set(lists.map { $0.id })
        out += m.filter { $0.value < 0 && $0.key.hasPrefix("L|") && !ids.contains(String($0.key.dropFirst(2))) }
            .map { SyncList(id: String($0.key.dropFirst(2)), name: "", ts: -$0.value, del: true, items: []) }
        return out
    }

    static func apply(_ merged: [SyncList]) -> ([Local], [String: Int64]) {
        var meta: [String: Int64] = [:]
        var local: [Local] = []
        for l in merged {
            meta["L|\(l.id)"] = l.del ? -l.ts : l.ts
            for e in l.items { meta["I|\(l.id)|\(e.k)"] = e.del ? -e.ts : e.ts }
            if l.del && l.id != SyncCore.defaultList { continue }
            let items = l.items.enumerated().filter { !$0.element.del && $0.element.item != nil }
                .sorted { $0.element.ts != $1.element.ts ? $0.element.ts > $1.element.ts : $0.offset < $1.offset }
                .compactMap { $0.element.item }
            local.append(Local(id: l.id, name: l.name.isEmpty ? (l.id == SyncCore.defaultList ? "Favoriten" : "Liste") : l.name, items: items))
        }
        return (local, meta.filter { $0.value != 0 })
    }
}

extension Library {
    var syncMeta: SyncMeta { SyncMeta(pid: pid) }

    func exportSync() -> SyncData {
        let local = [SyncLists.Local(id: SyncCore.defaultList, name: "Favoriten", items: favorites.map { SyncItem($0) })] +
            lists.map { SyncLists.Local(id: $0.id, name: $0.name, items: $0.items.map { SyncItem($0) }) }
        let resume = history.filter { $0.item.type != .LIVE }.map { h -> SyncResume in
            let key = h.episode.map { "EPISODE:\($0.id)" } ?? h.item.key
            return SyncResume(k: h.item.key, pos: Int64(h.position), dur: Int64(h.duration), ts: Int64(h.updated * 1000),
                              done: isWatched(key), item: SyncItem(h.item),
                              ep: h.episode.map { SyncEpisode(id: $0.id, season: $0.season, num: $0.episodeNum, title: $0.title, ext: $0.ext) })
        }
        return SyncData(lists: SyncLists.export(local, syncMeta), resume: resume)
    }
}

extension AppState {
    private func libraryFor(_ p: Profile) -> Library { (profile?.id == p.id ? library : nil) ?? Library(profileId: p.id) }

    /** Server-Seite (/portiva/sync). nil = Benutzer hier nicht eingerichtet. */
    func handleSync(_ req: SyncRequest) -> SyncReply? {
        guard let p = profiles.first(where: { SyncCore.canSync($0) && SyncCore.accountKey($0) == req.account }) else { return nil }
        let lib = libraryFor(p)
        let before = lib.exportSync()
        let merged = SyncCore.merge(before, req.data)
        lib.applySync(merged, before: before)
        if !req.from.isEmpty { show("Favoriten & Weiterschauen mit „\(req.from)“ synchronisiert") }
        return SyncReply(ok: true, data: merged, name: link.deviceName)
    }

    /** Mit allen Geraeten im Heimnetz abgleichen. Liefert die Meldung. */
    func syncAll(progress: @escaping (String) -> Void) async -> String {
        guard let p = profile else { return "Kein Zugang ausgewählt" }
        guard SyncCore.canSync(p) else { return "Mit einer lokalen M3U-Datei ist kein Abgleich möglich." }
        progress("Suche Geräte im Heimnetz …")
        let devices = await LinkService.discover()
        if devices.isEmpty { return "Kein anderes Gerät mit Portiva gefunden.\nIm gleichen WLAN? Portiva dort geöffnet?" }
        var done: [String] = [], failed: [String] = []
        var added = 0, removed = 0
        for d in devices {
            progress("Gleiche ab mit „\(d.name)“ …")
            let lib = libraryFor(p)
            let before = lib.exportSync()
            let reply = await LinkService.sync(d, SyncRequest(account: SyncCore.accountKey(p), data: before, from: link.deviceName))
            if reply.ok, let data = reply.data {
                let merged = SyncCore.merge(before, data)
                lib.applySync(merged, before: before)
                let c = SyncCore.changes(before, merged)
                added += c.0; removed += c.1
                done.append(d.name)
            } else if !reply.unknown {
                failed.append(reply.error ?? d.name)
            }
        }
        var msg = done.isEmpty
            ? "Kein Gerät mit diesem Benutzer gefunden.\nAuf dem anderen Gerät muss derselbe Zugang eingerichtet und Portiva geöffnet sein."
            : "Synchronisiert mit \(done.joined(separator: ", ")).\n" + (added == 0 && removed == 0 ? "Alles war schon aktuell." : "\(added) neu oder aktualisiert, \(removed) entfernt.")
        if !failed.isEmpty { msg += "\n\nNicht geklappt: \(failed.joined(separator: "; "))" }
        return msg
    }
}

extension LinkService {
    nonisolated static func sync(_ d: LinkDevice, _ req: SyncRequest) async -> SyncReply {
        guard let url = URL(string: "http://\(d.host):\(d.port)/portiva/sync"), let body = try? JSONEncoder().encode(req) else {
            return SyncReply(ok: false, name: d.name, error: "Ungültige Adresse")
        }
        var r = URLRequest(url: url); r.httpMethod = "POST"; r.httpBody = body; r.timeoutInterval = 20
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        guard let res = try? await URLSession.shared.data(for: r), let code = (res.1 as? HTTPURLResponse)?.statusCode else {
            return SyncReply(ok: false, name: d.name, error: "„\(d.name)“ nicht erreichbar")
        }
        if code == 404 { return SyncReply(ok: false, name: d.name, error: "Portiva auf „\(d.name)“ ist zu alt – bitte dort aktualisieren") }
        return (try? JSONDecoder().decode(SyncReply.self, from: res.0)) ?? SyncReply(ok: false, name: d.name, error: "Fehler \(code)")
    }
}
