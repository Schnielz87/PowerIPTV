import Foundation
import Security
import SwiftUI

/** Zugaenge sicher im iOS-Schluesselbund (enthalten Passwoerter). */
enum Keychain {
    private static let account = "portiva.profiles"

    static func load() -> Data? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: account,
                                kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        return SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess ? out as? Data : nil
    }

    static func save(_ data: Data) {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: account]
        SecItemDelete(q as CFDictionary)
        var add = q
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(add as CFDictionary, nil)
    }
}

/** Einstellungen (wie Android). */
final class Settings: ObservableObject {
    private let d = UserDefaults.standard
    @Published var liveFormat: String { didSet { d.set(liveFormat, forKey: "liveFormat") } }
    @Published var categoryLanguage: String { didSet { d.set(categoryLanguage, forKey: "categoryLanguage") } }
    @Published var autoNext: Bool { didSet { d.set(autoNext, forKey: "autoNext") } }
    @Published var subtitleSize: String { didSet { d.set(subtitleSize, forKey: "subtitleSize") } }
    @Published var aspect: String { didSet { d.set(aspect, forKey: "aspect") } }
    @Published var lastProfileId: String? { didSet { d.set(lastProfileId, forKey: "lastProfileId") } }

    init() {
        liveFormat = d.string(forKey: "liveFormat") ?? "ts"
        categoryLanguage = d.string(forKey: "categoryLanguage") ?? ""
        autoNext = d.object(forKey: "autoNext") as? Bool ?? true
        subtitleSize = d.string(forKey: "subtitleSize") ?? "NORMAL"
        aspect = d.string(forKey: "aspect") ?? "FIT"
        lastProfileId = d.string(forKey: "lastProfileId")
    }
}

/** Erwachseneninhalte erkennen (wie Android ParentalControl.isAdult) – die landen nie in "Zuletzt gesehen". */
enum AdultContent {
    private static let regex = try! NSRegularExpression(pattern: #"(xxx|adult|erotic|erotik|porn|\b18\s*\+|\+\s*18\b|for adults|nur für erwachsene)"#, options: [.caseInsensitive])
    private static let lock = NSLock()
    private static var cats: Set<String> = []

    static func isAdult(_ name: String) -> Bool {
        regex.firstMatch(in: name, range: NSRange(name.startIndex..., in: name)) != nil
    }
    static func register(_ pid: String, _ type: ContentType, _ list: [Category]) {
        lock.lock(); defer { lock.unlock() }
        for c in list where isAdult(c.name) { cats.insert("\(pid)|\(type.rawValue)|\(c.id)") }
    }
    static func isAdultItem(_ pid: String, _ item: ContentItem) -> Bool {
        if isAdult(item.name) { return true }
        lock.lock(); defer { lock.unlock() }
        return cats.contains("\(pid)|\(item.type.rawValue)|\(item.categoryId)")
    }
}

/** Favoriten, Verlauf und Weiterschauen – je Zugang. */
final class Library: ObservableObject {
    let pid: String
    @Published private(set) var favorites: [ContentItem] = []
    @Published private(set) var history: [HistoryEntry] = []
    private var positions: [String: Double] = [:]
    private var watched: Set<String> = []
    private let d = UserDefaults.standard

    init(profileId: String) {
        pid = profileId
        favorites = load("fav") ?? []
        history = load("hist") ?? []
        positions = load("pos") ?? [:]
        watched = Set(load("watched") ?? [String]())
        purgeHistory()
    }

    /** Erwachseneninhalte aus "Zuletzt gesehen" entfernen. */
    func purgeHistory() {
        let list = history.filter { !AdultContent.isAdultItem(pid, $0.item) }
        if list.count != history.count { history = list; store("hist", history) }
    }

    private func load<T: Decodable>(_ k: String) -> T? {
        guard let data = d.data(forKey: "\(k).\(pid)") else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
    private func store<T: Encodable>(_ k: String, _ v: T) {
        if let data = try? JSONEncoder().encode(v) { d.set(data, forKey: "\(k).\(pid)") }
    }

    func isFavorite(_ i: ContentItem) -> Bool { favorites.contains { $0.key == i.key } }

    @discardableResult
    func toggleFavorite(_ i: ContentItem) -> Bool {
        if isFavorite(i) { favorites.removeAll { $0.key == i.key } } else { favorites.insert(i, at: 0) }
        store("fav", favorites)
        return isFavorite(i)
    }

    static func positionKey(_ e: PlayEntry) -> String { e.episode.map { "EPISODE:\($0.id)" } ?? e.item?.key ?? e.url }

    func position(_ key: String) -> Double { positions[key] ?? 0 }
    func isWatched(_ key: String) -> Bool { watched.contains(key) }

    func setWatched(_ key: String, _ v: Bool) {
        if v { watched.insert(key); positions[key] = nil } else { watched.remove(key) }
        store("watched", Array(watched)); store("pos", positions)
        objectWillChange.send()
    }

    /** Wie Android: kurz vor Schluss = gesehen, sonst Position merken. */
    func save(_ e: PlayEntry, position: Double, duration: Double) {
        let key = Library.positionKey(e)
        if e.live {
            if let item = e.item { addHistory(HistoryEntry(item: item)) }
            return
        }
        guard duration > 0 else { return }
        let nearEnd = duration - position < 90_000 || position / duration > 0.95
        if nearEnd { positions[key] = nil; watched.insert(key); store("watched", Array(watched)) }
        else if position > 10_000 { positions[key] = position }
        store("pos", positions)
        if let item = e.item { addHistory(HistoryEntry(item: item, episode: e.episode, position: nearEnd ? duration : position, duration: duration)) }
    }

    func addHistory(_ h: HistoryEntry) {
        if AdultContent.isAdultItem(pid, h.item) { purgeHistory(); return }  // nie in "Zuletzt gesehen"
        history.removeAll { $0.item.key == h.item.key || AdultContent.isAdultItem(pid, $0.item) }
        var entry = h
        entry.updated = Date().timeIntervalSince1970
        history.insert(entry, at: 0)
        if history.count > 150 { history = Array(history.prefix(150)) }
        store("hist", history)
    }

    func removeHistory(_ i: ContentItem) {
        history.removeAll { $0.item.key == i.key }
        store("hist", history)
    }

    var continueWatching: [HistoryEntry] {
        history.filter { $0.item.type != .LIVE && $0.duration > 0 && $0.position > 10_000 && $0.position / $0.duration < 0.95 }
    }
}

/** Zentraler Zustand der App (wie AppContainer in Android). */
@MainActor
final class AppState: ObservableObject {
    @Published var profiles: [Profile] = []
    @Published var profile: Profile?
    @Published var account: AccountInfo?
    @Published var library: Library?
    @Published var playing: PlayEntry?
    @Published var playQueue: [PlayEntry] = []
    @Published var toast: String?
    let settings = Settings()
    private(set) var source: ContentSource?
    lazy var link: LinkService = LinkService(app: self)

    init() {
        if let data = Keychain.load(), let list = try? JSONDecoder().decode([Profile].self, from: data) { profiles = list }
        if let p = profiles.first(where: { $0.id == settings.lastProfileId }) ?? profiles.first { activate(p) }
        link.start()
    }

    func saveProfiles() {
        if let data = try? JSONEncoder().encode(profiles) { Keychain.save(data) }
    }

    func save(_ p: Profile) {
        if let i = profiles.firstIndex(where: { $0.id == p.id }) { profiles[i] = p } else { profiles.append(p) }
        saveProfiles()
    }

    func delete(_ p: Profile) {
        profiles.removeAll { $0.id == p.id }
        saveProfiles()
        if profile?.id == p.id { if let f = profiles.first { activate(f) } else { profile = nil; source = nil; library = nil } }
    }

    func activate(_ p: Profile) {
        profile = p
        settings.lastProfileId = p.id
        source = makeSource(p) { [weak self] in self?.settings.liveFormat ?? "ts" }
        let lib = Library(profileId: p.id)
        library = lib
        account = nil
        let src = source
        Task { [weak self] in
            let acc = try? await src?.authenticate()
            await MainActor.run { self?.account = acc }
            // Erwachsenen-Kategorien kennen, damit auch Einzeltitel daraus nicht in "Zuletzt gesehen" landen
            for t in [ContentType.LIVE, .MOVIE, .SERIES] {
                if let cats = try? await src?.categories(t) { AdultContent.register(p.id, t, cats) }
            }
            await MainActor.run { lib.purgeHistory() }
        }
    }

    /** Zugang aus QR-Code / anderem Geraet uebernehmen (gleicher Zugang wird aktualisiert). */
    @discardableResult
    func importAccount(_ a: LinkAccount) -> Profile {
        let existing = profiles.first { $0.type.rawValue == a.type && $0.serverUrl == a.serverUrl && $0.username == a.username && $0.m3uUrl == a.m3uUrl }
        let p = Profile(id: existing?.id ?? UUID().uuidString, name: a.name.isEmpty ? "Zugang" : a.name,
                        type: a.type == "XTREAM" ? .XTREAM : .M3U_URL, serverUrl: a.serverUrl, username: a.username,
                        password: a.password, m3uUrl: a.m3uUrl, epgUrl: a.epgUrl)
        save(p)
        return p
    }

    func play(_ entries: [PlayEntry], index: Int = 0) {
        guard entries.indices.contains(index) else { return }
        playQueue = entries
        var e = entries[index]
        if !e.live && e.startAt == 0, let lib = library { e.startAt = lib.position(Library.positionKey(e)) }
        playing = e
    }

    func entry(for item: ContentItem) -> PlayEntry {
        PlayEntry(title: item.name, url: source?.streamUrl(item) ?? "", live: item.type == .LIVE, item: item)
    }

    func entry(for ep: Episode, series: ContentItem) -> PlayEntry {
        PlayEntry(title: "\(series.name) – S\(ep.season) E\(ep.episodeNum) \(ep.title)", url: source?.episodeUrl(ep) ?? "", live: false, item: series, episode: ep)
    }

    func show(_ message: String) {
        toast = message
        Task { try? await Task.sleep(nanoseconds: 2_600_000_000); await MainActor.run { if self.toast == message { self.toast = nil } } }
    }
}
