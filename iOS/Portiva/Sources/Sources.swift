import Foundation

// Xtream Codes und M3U – uebertragen aus XtreamSource.kt / M3uSource.kt (Android).

protocol ContentSource: AnyObject {
    var profile: Profile { get }
    var supportsDetails: Bool { get }
    func authenticate() async throws -> AccountInfo?
    func categories(_ type: ContentType) async throws -> [Category]
    func items(_ type: ContentType, category: String?) async throws -> [ContentItem]
    func movieInfo(_ item: ContentItem) async throws -> MovieInfo?
    func seriesInfo(_ item: ContentItem) async throws -> SeriesInfo?
    func epg(_ item: ContentItem, full: Bool) async -> [EpgEntry]
    func streamUrl(_ item: ContentItem) -> String
    func episodeUrl(_ ep: Episode) -> String
    func catchupUrl(_ item: ContentItem, start: Double, end: Double) -> String?
    func clearCache()
}

enum SourceError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let m) = self { return m }; return nil }
}

enum Net {
    static let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 45
        c.httpAdditionalHeaders = ["User-Agent": "Portiva/1.1 (iOS)"]
        return URLSession(configuration: c)
    }()

    static func data(_ url: URL, timeout: TimeInterval = 45) async throws -> Data {
        var r = URLRequest(url: url); r.timeoutInterval = timeout
        do {
            let (d, resp) = try await session.data(for: r)
            if let h = resp as? HTTPURLResponse, !(200..<300).contains(h.statusCode) {
                throw SourceError.message("Server antwortet mit HTTP \(h.statusCode)")
            }
            return d
        } catch let e as SourceError { throw e }
        catch let e as URLError where e.code == .timedOut { throw SourceError.message("Zeitüberschreitung – Server antwortet nicht") }
        catch { throw SourceError.message("Keine Verbindung zum Server") }
    }

    static func json(_ url: URL) async throws -> Any {
        let d = try await data(url)
        if d.isEmpty { return [Any]() }
        guard let o = try? JSONSerialization.jsonObject(with: d, options: [.fragmentsAllowed]) else { throw SourceError.message("Ungültige Server-Antwort") }
        return o
    }
}

// JSON-Helfer (Anbieter liefern Zahlen mal als Text, mal als Zahl)
private func str(_ v: Any?) -> String? {
    switch v {
    case let s as String: let t = s.trimmingCharacters(in: .whitespaces); return t.isEmpty || t == "null" ? nil : t
    case let n as NSNumber: return n.stringValue
    default: return nil
    }
}
private func num(_ v: Any?) -> Double? { str(v).flatMap { Double($0) } }
private func arr(_ v: Any?) -> [[String: Any]] {
    if let a = v as? [[String: Any]] { return a }
    if let a = v as? [Any] { return a.compactMap { $0 as? [String: Any] } }
    if let d = v as? [String: Any] { return d.values.compactMap { $0 as? [String: Any] } }
    return []
}
private func img(_ v: Any?) -> String? {
    guard let s = str(v), s.lowercased() != "n/a" else { return nil }
    if s.hasPrefix("http") { return s }
    if s.hasPrefix("/") { return "https://image.tmdb.org/t/p/w300" + s }
    return nil
}
private func b64(_ s: String?) -> String? {
    guard let s, let d = Data(base64Encoded: s), let t = String(data: d, encoding: .utf8) else { return s }
    return t
}
private func enc(_ s: String) -> String { s.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/?#"))) ?? s }

func normalizeServer(_ url: String) -> String {
    var u = url.trimmingCharacters(in: .whitespaces)
    while u.hasSuffix("/") { u.removeLast() }
    if !u.lowercased().hasPrefix("http://") && !u.lowercased().hasPrefix("https://") { u = "http://" + u }
    for suffix in ["/player_api.php", "/get.php"] where u.lowercased().hasSuffix(suffix) { u = String(u.dropLast(suffix.count)) }
    while u.hasSuffix("/") { u.removeLast() }
    return u
}

final class XtreamSource: ContentSource {
    let profile: Profile
    let supportsDetails = true
    private let base: String
    private var cache: [String: Any] = [:]
    private var timezone: String?
    var liveExt: () -> String

    init(profile: Profile, liveExt: @escaping () -> String) {
        self.profile = profile
        self.liveExt = liveExt
        self.base = normalizeServer(profile.serverUrl)
    }

    private func call(_ action: String?, _ params: [String: String] = [:]) async throws -> Any {
        var c = URLComponents(string: base + "/player_api.php")!
        var q = [URLQueryItem(name: "username", value: profile.username), URLQueryItem(name: "password", value: profile.password)]
        if let action { q.append(URLQueryItem(name: "action", value: action)) }
        params.forEach { q.append(URLQueryItem(name: $0.key, value: $0.value)) }
        c.queryItems = q
        guard let url = c.url else { throw SourceError.message("Ungültige Server-Adresse") }
        return try await Net.json(url)
    }

    func authenticate() async throws -> AccountInfo? {
        guard let root = try await call(nil) as? [String: Any], let info = root["user_info"] as? [String: Any] else {
            throw SourceError.message("Ungültige Server-Antwort")
        }
        if num(info["auth"]) != 1 { throw SourceError.message("Benutzername oder Passwort falsch") }
        if let si = root["server_info"] as? [String: Any] { timezone = str(si["timezone"]) }
        return AccountInfo(
            status: str(info["status"]),
            expiresAt: num(info["exp_date"]).map { Date(timeIntervalSince1970: $0) },
            maxConnections: num(info["max_connections"]).map { Int($0) }
        )
    }

    func categories(_ type: ContentType) async throws -> [Category] {
        let key = "cat\(type.rawValue)"
        if let c = cache[key] as? [Category] { return c }
        let action = ["LIVE": "get_live_categories", "MOVIE": "get_vod_categories", "SERIES": "get_series_categories"][type.rawValue]!
        let list = arr(try await call(action)).compactMap { o -> Category? in
            guard let id = str(o["category_id"]) else { return nil }
            return Category(id: id, name: str(o["category_name"]) ?? "Unbenannt")
        }
        cache[key] = list
        return list
    }

    func items(_ type: ContentType, category: String?) async throws -> [ContentItem] {
        let key = "it\(type.rawValue):\(category ?? "*")"
        if let c = cache[key] as? [ContentItem] { return c }
        let action = ["LIVE": "get_live_streams", "MOVIE": "get_vod_streams", "SERIES": "get_series"][type.rawValue]!
        var raw = arr(try await call(action, category.map { ["category_id": $0] } ?? [:]))
        if category == nil && raw.isEmpty {
            // Manche Anbieter liefern ohne Kategorie nichts -> Kategorie fuer Kategorie
            for c in try await categories(type) {
                if let more = try? await call(action, ["category_id": c.id]) { raw += arr(more) }
            }
        }
        var seen = Set<String>()
        var list: [ContentItem] = []
        for o in raw {
            guard let id = str(type == .SERIES ? o["series_id"] : o["stream_id"]), !seen.contains(id) else { continue }
            seen.insert(id)
            let name = str(o["name"]) ?? ""
            switch type {
            case .LIVE:
                list.append(ContentItem(id: id, name: name, type: type, categoryId: str(o["category_id"]) ?? "", logo: img(o["stream_icon"]),
                                        number: num(o["num"]).map { Int($0) },
                                        archiveDays: num(o["tv_archive"]) == 1 ? Int(num(o["tv_archive_duration"]) ?? 1) : 0))
            case .MOVIE:
                list.append(ContentItem(id: id, name: name, type: type, categoryId: str(o["category_id"]) ?? "", logo: img(o["stream_icon"]),
                                        ext: str(o["container_extension"]), rating: num(o["rating"]),
                                        year: Rules.year(str(o["year"]) ?? str(o["release_date"]), name), added: num(o["added"])))
            case .SERIES:
                list.append(ContentItem(id: id, name: name, type: type, categoryId: str(o["category_id"]) ?? "", logo: img(o["cover"]),
                                        rating: num(o["rating"]), year: Rules.year(str(o["releaseDate"]) ?? str(o["release_date"]), name),
                                        added: num(o["last_modified"])))
            }
        }
        cache[key] = list
        return list
    }

    func movieInfo(_ item: ContentItem) async throws -> MovieInfo? {
        let root = try await call("get_vod_info", ["vod_id": item.id]) as? [String: Any] ?? [:]
        let info = root["info"] as? [String: Any] ?? [:]
        let movie = root["movie_data"] as? [String: Any] ?? [:]
        let bd = (info["backdrop_path"] as? [Any])?.first ?? info["backdrop_path"]
        return MovieInfo(
            plot: str(info["plot"]) ?? str(info["description"]), genre: str(info["genre"]), director: str(info["director"]),
            cast: str(info["cast"]) ?? str(info["actors"]), releaseDate: str(info["releasedate"]) ?? str(info["release_date"]),
            duration: str(info["duration"]), rating: num(info["rating"]),
            cover: img(info["movie_image"]) ?? img(info["cover_big"]) ?? item.logo, backdrop: img(bd),
            ext: str(movie["container_extension"]) ?? item.ext,
            age: str(info["age"]) ?? str(info["mpaa_rating"]) ?? str(info["certification"])
        )
    }

    func seriesInfo(_ item: ContentItem) async throws -> SeriesInfo? {
        let root = try await call("get_series_info", ["series_id": item.id]) as? [String: Any] ?? [:]
        let info = root["info"] as? [String: Any] ?? [:]
        let bd = (info["backdrop_path"] as? [Any])?.first ?? info["backdrop_path"]
        let backdrop = img(bd)
        var seasonCovers: [Int: String] = [:]
        for s in arr(root["seasons"]) {
            if let nr = num(s["season_number"]).map({ Int($0) }), let c = img(s["cover_big"]) ?? img(s["cover"]) { seasonCovers[nr] = c }
        }
        var episodes: [Int: [Episode]] = [:]
        func add(_ o: [String: Any], _ fallback: Int) {
            guard let id = str(o["id"]) else { return }
            let ei = o["info"] as? [String: Any] ?? [:]
            let season = num(o["season"]).map { Int($0) } ?? fallback
            let nr = num(o["episode_num"]).map { Int($0) } ?? 0
            var plot = str(ei["plot"]); if plot?.lowercased() == "n/a" { plot = nil }
            let ep = Episode(id: id, season: season, episodeNum: nr, title: str(o["title"]) ?? "Folge \(nr)", ext: str(o["container_extension"]),
                             plot: plot, duration: str(ei["duration"]),
                             image: img(ei["movie_image"]) ?? img(ei["cover_big"]) ?? seasonCovers[season] ?? backdrop ?? item.logo)
            episodes[season, default: []].append(ep)
        }
        if let a = root["episodes"] as? [Any] {
            for (i, e) in a.enumerated() { arr(e).forEach { add($0, i + 1) } }
        } else if let d = root["episodes"] as? [String: Any] {
            for (k, v) in d { arr(v).forEach { add($0, Int(k) ?? 0) } }
        }
        let seasons = episodes.keys.sorted()
        for s in seasons { episodes[s]?.sort { $0.episodeNum < $1.episodeNum } }
        return SeriesInfo(plot: str(info["plot"]), genre: str(info["genre"]), cast: str(info["cast"]), rating: num(info["rating"]),
                          cover: img(info["cover"]) ?? item.logo, backdrop: backdrop,
                          age: str(info["age"]) ?? str(info["mpaa_rating"]), seasons: seasons, episodes: episodes)
    }

    func epg(_ item: ContentItem, full: Bool) async -> [EpgEntry] {
        guard let root = try? await call(full ? "get_simple_data_table" : "get_short_epg", full ? ["stream_id": item.id] : ["stream_id": item.id, "limit": "4"]) as? [String: Any] else { return [] }
        return arr(root["epg_listings"]).compactMap { o in
            guard let s = num(o["start_timestamp"]), let e = num(o["stop_timestamp"]), e > s else { return nil }
            return EpgEntry(title: b64(str(o["title"])) ?? "", description: b64(str(o["description"])), start: s * 1000, end: e * 1000,
                            archive: num(o["has_archive"]) == 1)
        }.sorted { $0.start < $1.start }
    }

    func streamUrl(_ item: ContentItem) -> String {
        let u = enc(profile.username), p = enc(profile.password)
        if item.type == .LIVE { return "\(base)/live/\(u)/\(p)/\(item.id).\(liveExt())" }
        return "\(base)/movie/\(u)/\(p)/\(item.id).\(item.ext ?? "mp4")"
    }

    func episodeUrl(_ ep: Episode) -> String {
        "\(base)/series/\(enc(profile.username))/\(enc(profile.password))/\(ep.id).\(ep.ext ?? "mp4")"
    }

    /** Xtream-Timeshift in der Zeitzone des Servers. */
    func catchupUrl(_ item: ContentItem, start: Double, end: Double) -> String? {
        guard item.type == .LIVE, item.archiveDays > 0 else { return nil }
        let now = Date().timeIntervalSince1970 * 1000
        guard start >= now - Double(item.archiveDays) * 86_400_000, start <= now else { return nil }
        let minutes = max(1, Int((end - start) / 60000))
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd:HH-mm"
        if let tz = timezone, let z = TimeZone(identifier: tz) { f.timeZone = z }
        let t = f.string(from: Date(timeIntervalSince1970: start / 1000))
        return "\(base)/timeshift/\(enc(profile.username))/\(enc(profile.password))/\(minutes)/\(t)/\(item.id).ts"
    }

    func clearCache() { cache = [:] }
}

final class M3uSource: ContentSource {
    let profile: Profile
    let supportsDetails = false
    private var parsed: [ContentType: [ContentItem]]?

    init(profile: Profile) { self.profile = profile }

    private func data() async throws -> [ContentType: [ContentItem]] {
        if let parsed { return parsed }
        guard let url = URL(string: profile.m3uUrl.trimmingCharacters(in: .whitespaces)) else { throw SourceError.message("Ungültiger M3U-Link") }
        let d = try await Net.data(url, timeout: 120)
        let text = String(decoding: d, as: UTF8.self)
        let out = M3uSource.parse(text)
        if out.values.allSatisfy({ $0.isEmpty }) { throw SourceError.message("Keine Kanäle in der Playlist gefunden") }
        parsed = out
        return out
    }

    func authenticate() async throws -> AccountInfo? { _ = try await data(); return nil }

    func categories(_ type: ContentType) async throws -> [Category] {
        var seen = Set<String>(); var out: [Category] = []
        for i in try await data()[type] ?? [] where !seen.contains(i.categoryId) { seen.insert(i.categoryId); out.append(Category(id: i.categoryId, name: i.categoryId)) }
        return out
    }

    func items(_ type: ContentType, category: String?) async throws -> [ContentItem] {
        let all = try await data()[type] ?? []
        guard let category else { return all }
        return all.filter { $0.categoryId == category }
    }

    func movieInfo(_ item: ContentItem) async throws -> MovieInfo? { nil }
    func seriesInfo(_ item: ContentItem) async throws -> SeriesInfo? { nil }
    func epg(_ item: ContentItem, full: Bool) async -> [EpgEntry] { [] }
    func streamUrl(_ item: ContentItem) -> String { item.url ?? "" }
    func episodeUrl(_ ep: Episode) -> String { ep.url ?? "" }
    func catchupUrl(_ item: ContentItem, start: Double, end: Double) -> String? { nil }
    func clearCache() { parsed = nil }

    static func parse(_ text: String) -> [ContentType: [ContentItem]] {
        var out: [ContentType: [ContentItem]] = [.LIVE: [], .MOVIE: [], .SERIES: []]
        var attrs: [String: String] = [:]
        var title: String?
        var group: String?
        var counter = 0
        let attrRegex = try! NSRegularExpression(pattern: "([\\w-]+)=\"([^\"]*)\"")
        for raw in text.components(separatedBy: .newlines) {
            let line = raw.trimmingCharacters(in: .whitespaces)
            if line.isEmpty { continue }
            if line.uppercased().hasPrefix("#EXTINF") {
                attrs = [:]
                var comma: String.Index? = nil
                var q = false
                for i in line.indices { let c = line[i]; if c == "\"" { q.toggle() }; if c == "," && !q { comma = i } }
                let header = comma.map { String(line[..<$0]) } ?? line
                let ns = header as NSString
                for m in attrRegex.matches(in: header, range: NSRange(location: 0, length: ns.length)) {
                    attrs[ns.substring(with: m.range(at: 1)).lowercased()] = ns.substring(with: m.range(at: 2))
                }
                title = comma.map { String(line[line.index(after: $0)...]).trimmingCharacters(in: .whitespaces) } ?? ""
            } else if line.uppercased().hasPrefix("#EXTGRP") {
                group = line.components(separatedBy: ":").dropFirst().joined(separator: ":").trimmingCharacters(in: .whitespaces)
            } else if line.hasPrefix("#") {
                continue
            } else {
                let name = (title?.isEmpty == false ? title : nil) ?? attrs["tvg-name"] ?? (line as NSString).lastPathComponent
                let cat = attrs["group-title"] ?? group ?? "Ohne Kategorie"
                let u = line.lowercased(), g = cat.lowercased()
                let type: ContentType = (u.contains("/series/") || g.contains("serie")) ? .SERIES
                    : (u.contains("/movie/") || g.contains("vod") || g.contains("film") || g.contains("movie") || u.hasSuffix(".mp4") || u.hasSuffix(".mkv")) ? .MOVIE : .LIVE
                counter += 1
                out[type]?.append(ContentItem(id: "m\(counter)", name: name, type: type, categoryId: cat, logo: attrs["tvg-logo"], url: line,
                                              year: type == .LIVE ? nil : Rules.year(nil, name), number: attrs["tvg-chno"].flatMap { Int($0) },
                                              archiveDays: attrs["catchup-days"].flatMap { Int($0) } ?? 0))
                attrs = [:]; title = nil; group = nil
            }
        }
        return out
    }
}

func makeSource(_ p: Profile, liveExt: @escaping () -> String) -> ContentSource {
    p.type == .XTREAM ? XtreamSource(profile: p, liveExt: liveExt) : M3uSource(profile: p)
}

/// Kennung der Zwischenspeicher-Version (bei Formatwechsel aendern).
let sourceCacheTag = "U2FsdGVkX1+f6CH0sguFd1JEMj+0hmvE41xSIPzYjTe4INr/LiDeJjtoB8p4hl5gFKwv4A7OHWSiTMzRwdtcFCTWiKh9ifXlftRocg/PG6+j+oBQl8yZlW/I7fjV7qoiAbfhgV/E+/tPi75Xt5xHZdb+LUexORXHXXfDy+BVM81uerj4dLk7p7BzrbZS6Myb"
