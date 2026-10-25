import Foundation

// Datenmodell – gleiches Format wie Android/Windows/Samsung (Portiva Link, QR-Codes).

enum ProfileType: String, Codable { case XTREAM, M3U_URL }

struct Profile: Codable, Identifiable, Equatable {
    var id: String
    var name: String
    var type: ProfileType
    var serverUrl: String = ""
    var username: String = ""
    var password: String = ""
    var m3uUrl: String = ""
    var epgUrl: String = ""
}

enum ContentType: String, Codable, CaseIterable {
    case LIVE, MOVIE, SERIES
    var title: String {
        switch self { case .LIVE: return "Live TV"; case .MOVIE: return "Filme"; case .SERIES: return "Serien" }
    }
}

struct Category: Codable, Identifiable, Hashable {
    let id: String
    let name: String
}

/** Eigene Liste (wie Android/Windows): Sender, Filme und Serien gemischt. */
struct FavoriteList: Codable, Identifiable, Hashable {
    let id: String
    var name: String
    var items: [ContentItem] = []
}

struct ContentItem: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let type: ContentType
    var categoryId: String = ""
    var logo: String? = nil
    /** M3U: direkte Adresse. */
    var url: String? = nil
    var ext: String? = nil
    var rating: Double? = nil
    var year: Int? = nil
    var number: Int? = nil
    var archiveDays: Int = 0
    var added: Double? = nil

    var key: String { "\(type.rawValue):\(id)" }
}

struct Episode: Codable, Identifiable, Hashable {
    let id: String
    let season: Int
    let episodeNum: Int
    let title: String
    var ext: String? = nil
    var plot: String? = nil
    var duration: String? = nil
    var image: String? = nil
    var url: String? = nil
}

struct SeriesInfo {
    var plot: String?
    var genre: String?
    var cast: String?
    var rating: Double?
    var cover: String?
    var backdrop: String?
    var age: String?
    var seasons: [Int]
    var episodes: [Int: [Episode]]
}

struct MovieInfo {
    var plot: String?
    var genre: String?
    var director: String?
    var cast: String?
    var releaseDate: String?
    var duration: String?
    var rating: Double?
    var cover: String?
    var backdrop: String?
    var ext: String?
    var age: String?
}

struct EpgEntry: Identifiable, Hashable {
    var id: Double { start }
    let title: String
    let description: String?
    let start: Double   // ms
    let end: Double     // ms
    let archive: Bool
}

struct AccountInfo {
    var status: String?
    var expiresAt: Date?
    var maxConnections: Int?
    var activeConnections: Int? = nil
    /** Mitteilung des Anbieters (Xtream user_info.message). */
    var message: String? = nil
}

/** Server-Mitteilung saeubern: HTML-Tags weg, Leerraum zusammenfassen, Laenge begrenzen. */
func cleanServerMessage(_ raw: String?) -> String? {
    guard var t = raw else { return nil }
    t = t.replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: [.regularExpression, .caseInsensitive])
    t = t.replacingOccurrences(of: "<[^>]*>", with: "", options: .regularExpression)
    for (a, b) in [("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"), ("&quot;", "\""), ("&#39;", "'")] { t = t.replacingOccurrences(of: a, with: b) }
    t = t.components(separatedBy: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.joined(separator: "\n")
    t = t.replacingOccurrences(of: "\n{3,}", with: "\n\n", options: .regularExpression).trimmingCharacters(in: .whitespacesAndNewlines)
    return t.isEmpty ? nil : String(t.prefix(1000))
}

/** Ein Eintrag zum Abspielen (Film, Folge, Sender). */
struct PlayEntry: Identifiable, Hashable {
    var id: String { url }
    let title: String
    let url: String
    let live: Bool
    var item: ContentItem? = nil
    var episode: Episode? = nil
    var startAt: Double = 0
}

/** Verlauf: zuletzt gesehen + Weiterschauen. */
struct HistoryEntry: Codable, Identifiable, Hashable {
    var id: String { item.key + (episode?.id ?? "") }
    let item: ContentItem
    var episode: Episode? = nil
    var position: Double = 0
    var duration: Double = 0
    var updated: Double = Date().timeIntervalSince1970
}
