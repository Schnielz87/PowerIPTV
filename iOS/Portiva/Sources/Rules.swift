import Foundation

// Regeln – wie CategoryRules.kt / rules.js (Android, Windows, Samsung).

enum Rules {
    private static let langRegex = try! NSRegularExpression(pattern: "^\\W*([A-Za-z]{2,4})\\s*[|:\\-–]")

    /** Sprach-/Laender-Praefix einer Kategorie, z.B. "EN | MOVIES" -> "EN". */
    static func categoryLanguage(_ name: String) -> String? {
        let ns = name as NSString
        guard let m = langRegex.firstMatch(in: name, range: NSRange(location: 0, length: ns.length)) else { return nil }
        return ns.substring(with: m.range(at: 1)).uppercased()
    }

    static func stripLanguage(_ name: String) -> String {
        let ns = name as NSString
        guard let m = langRegex.firstMatch(in: name, range: NSRange(location: 0, length: ns.length)) else { return name }
        var rest = ns.substring(from: m.range.location + m.range.length)
        rest = rest.trimmingCharacters(in: CharacterSet(charactersIn: "|:-– ").union(.whitespaces))
        return rest.isEmpty ? name : rest
    }

    /** Alle Praefixe, die mindestens zweimal vorkommen. */
    static func detectLanguages(_ cats: [Category]) -> [String] {
        var counts: [String: Int] = [:]
        for c in cats { if let l = categoryLanguage(c.name) { counts[l, default: 0] += 1 } }
        return counts.filter { $0.value >= 2 }.sorted { $0.value > $1.value }.map { $0.key }
    }

    /** Alle Woerter der Suche muessen vorkommen. */
    static func matches(_ name: String, _ query: String) -> Bool {
        let words = query.lowercased().split(separator: " ").map(String.init).filter { !$0.isEmpty }
        if words.isEmpty { return true }
        let n = name.lowercased()
        return words.allSatisfy { n.contains($0) }
    }

    private static let yearRegex = try! NSRegularExpression(pattern: "\\b(19[3-9]\\d|20[0-4]\\d)\\b")

    static func year(_ field: String?, _ name: String) -> Int? {
        for s in [field ?? "", name] {
            let ns = s as NSString
            if let m = yearRegex.firstMatch(in: s, range: NSRange(location: 0, length: ns.length)) {
                return Int(ns.substring(with: m.range(at: 1)))
            }
        }
        return nil
    }

    /** FSK aus Anbieter-Angaben ("16", "FSK 12", "PG-13" …). */
    static func fsk(_ age: String?) -> Int? {
        guard let a = age?.trimmingCharacters(in: .whitespaces).uppercased(), !a.isEmpty else { return nil }
        for v in ["18", "16", "12", "6", "0"] where a.range(of: "\\b\(v)\\b", options: .regularExpression) != nil { return Int(v) }
        switch a { case "G": return 0; case "PG": return 6; case "PG-13": return 12; case "R": return 16; case "NC-17": return 18; default: return nil }
    }

    static func time(_ ms: Double) -> String {
        let s = max(0, Int(ms / 1000))
        let h = s / 3600, m = (s % 3600) / 60, sec = s % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, sec) : String(format: "%d:%02d", m, sec)
    }

    static func clock(_ ms: Double) -> String {
        let f = DateFormatter(); f.dateFormat = "HH:mm"
        return f.string(from: Date(timeIntervalSince1970: ms / 1000))
    }
}
