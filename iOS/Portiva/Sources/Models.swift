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
