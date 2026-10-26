import Foundation
import Network

/**
 * Wiedergabe-Feinabstimmung wie Android: Mobile-Daten-Modus (erkennt das Mobilfunknetz selbst, im WLAN volle
 * Qualitaet) und Bildschaerfe.
 */
enum Tuning {
    private static let monitor: NWPathMonitor = {
        let m = NWPathMonitor()
        m.start(queue: DispatchQueue(label: "portiva.net"))
        return m
    }()

    /** Laeuft das Geraet gerade ueber Mobilfunk (nicht WLAN/LAN)? */
    static var isCellular: Bool {
        let p = monitor.currentPath
        return p.usesInterfaceType(.cellular) && !p.usesInterfaceType(.wifi) && !p.usesInterfaceType(.wiredEthernet)
    }

    /** Netz-Erkennung frueh starten (der erste Pfad steht sonst evtl. noch nicht fest). */
    static func warmUp() { _ = monitor }

    private static let tag = try! NSRegularExpression(
        pattern: #"(?i)(\b|_)(f?hd|uhd|4k|8k|hevc|h\.?265|1080[pi]?|720p|2160p|50fps)(\b|_)|[\s*+]*\b(raw|backup)\b"#)
    private static let high = try! NSRegularExpression(pattern: #"(?i)\b(f?hd|uhd|4k|8k|hevc|h\.?265|1080|2160)\b"#)

    static func base(_ name: String) -> String {
        let r = NSRange(name.startIndex..., in: name)
        let s = tag.stringByReplacingMatches(in: name, range: r, withTemplate: " ")
        return s.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }.joined(separator: " ").lowercased()
    }

    private static func isHigh(_ name: String) -> Bool {
        high.firstMatch(in: name, range: NSRange(name.startIndex..., in: name)) != nil
    }

    /** Xtream-Live-Adresse im jeweils anderen Format (.ts <-> .m3u8), sonst nil. */
    static func altFormatUrl(_ url: String) -> String? {
        guard let re = try? NSRegularExpression(pattern: #"(?i)(/live/[^/]+/[^/]+/[^/.?]+)\.(ts|m3u8)(\?.*)?$"#),
              let m = re.firstMatch(in: url, range: NSRange(url.startIndex..., in: url)),
              let whole = Range(m.range, in: url), let head = Range(m.range(at: 1), in: url), let ext = Range(m.range(at: 2), in: url) else { return nil }
        let query = Range(m.range(at: 3), in: url).map { String(url[$0]) } ?? ""
        let other = url[ext].lowercased() == "ts" ? "m3u8" : "ts"
        return url.replacingCharacters(in: whole, with: String(url[head]) + "." + other + query)
    }

    /** Ausweich-Sender gleichen Namens (z.B. "Sky Sport HD" -> "Sky Sport HD RAW"), der noch nicht probiert wurde. */
    static func backup(_ e: PlayEntry, in queue: [PlayEntry], tried: Set<String>) -> PlayEntry? {
        let key = base(e.title)
        guard e.live, !key.isEmpty else { return nil }
        return queue.first { $0.live && $0.url != e.url && !tried.contains($0.url) && base($0.title) == key }
    }

    /** SD-Version desselben Senders (z.B. "RTL" statt "RTL FHD") – nil, wenn es keine gibt. */
    static func sdVariant(_ e: PlayEntry, in queue: [PlayEntry]) -> PlayEntry? {
        guard e.live, isHigh(e.title) else { return nil }
        let key = base(e.title)
        guard !key.isEmpty else { return nil }
        return queue.first { $0.live && $0.url != e.url && !isHigh($0.title) && base($0.title) == key }
    }
}
