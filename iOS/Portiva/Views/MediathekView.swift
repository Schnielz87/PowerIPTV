import SwiftUI

// Mediatheken von ARD, ZDF, Arte & Co. (wie Android/Windows) – ueber die freie MediathekView-Datenbank,
// unabhaengig vom IPTV-Zugang. Sender als Leiste, Suche, neueste Sendungen zuerst.

struct MediathekItem: Identifiable, Hashable {
    let id: String
    let channel: String
    let topic: String
    let title: String
    let description: String?
    let timestamp: Double
    let duration: Double
    let url: String
    let urlHd: String?

    var bestUrl: String { (urlHd?.isEmpty == false ? urlHd : nil) ?? url }
}

struct MediathekChannel: Hashable {
    let label: String
    let query: String
}

enum Mediathek {
    static let channels: [MediathekChannel] = [
        ("Alle Sender", ""), ("ARD", "ARD"), ("ZDF", "ZDF"), ("Arte", "ARTE.DE"), ("3sat", "3Sat"), ("ZDFneo", "ZDFneo"),
        ("ZDFinfo", "ZDFinfo"), ("Phoenix", "PHOENIX"), ("KiKA", "KiKA"), ("ZDFtivi", "ZDF-tivi"), ("BR", "BR"), ("WDR", "WDR"),
        ("NDR", "NDR"), ("SWR", "SWR"), ("MDR", "MDR"), ("HR", "HR"), ("RBB", "RBB"), ("SR", "SR"), ("Radio Bremen", "Radio Bremen TV"),
        ("ORF", "ORF"), ("SRF", "SRF"), ("DW", "DW"),
    ].map { MediathekChannel(label: $0.0, query: $0.1) }
    private static let special = try? NSRegularExpression(pattern: "Audiodeskription|Gebärdensprache|Hörfassung|\\(mit Untertitel\\)|klare Sprache", options: .caseInsensitive)

    static func search(query: String, channel: String, offset: Int = 0, size: Int = 60) async throws -> [MediathekItem] {
        var queries: [[String: Any]] = []
        if !channel.isEmpty { queries.append(["fields": ["channel"], "query": channel]) }
        let q = query.trimmingCharacters(in: .whitespaces)
        if !q.isEmpty { queries.append(["fields": ["title", "topic"], "query": q]) }
        let body: [String: Any] = ["queries": queries, "sortBy": "timestamp", "sortOrder": "desc", "future": false,
                                   "offset": offset, "size": size, "duration_min": 180]
        var r = URLRequest(url: URL(string: "https://mediathekviewweb.de/api/query")!)
        r.httpMethod = "POST"
        r.setValue("text/plain", forHTTPHeaderField: "Content-Type")
        r.httpBody = try JSONSerialization.data(withJSONObject: body)
        r.timeoutInterval = 20
        let (data, resp) = try await URLSession.shared.data(for: r)
        guard (resp as? HTTPURLResponse)?.statusCode == 200,
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let results = (root["result"] as? [String: Any])?["results"] as? [[String: Any]] else {
            throw NSError(domain: "Mediathek", code: 1, userInfo: [NSLocalizedDescriptionKey: "Mediathek nicht erreichbar"])
        }
        var seen = Set<String>()
        return results.compactMap { o -> MediathekItem? in
            guard let url = o["url_video"] as? String, url.hasPrefix("http") else { return nil }
            let title = o["title"] as? String ?? ""
            let range = NSRange(title.startIndex..., in: title)
            if special?.firstMatch(in: title, range: range) != nil { return nil }
            let channel = o["channel"] as? String ?? ""
            let duration = (o["duration"] as? NSNumber)?.doubleValue ?? Double(o["duration"] as? String ?? "") ?? 0
            let key = "\(channel)|\(title)|\(Int(duration / 60))"
            guard seen.insert(key).inserted else { return nil }
            let hd = o["url_video_hd"] as? String
            return MediathekItem(
                id: o["id"] as? String ?? url, channel: channel, topic: o["topic"] as? String ?? "", title: title,
                description: (o["description"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
                timestamp: (o["timestamp"] as? NSNumber)?.doubleValue ?? 0, duration: duration,
                url: url, urlHd: (hd?.hasPrefix("http") == true) ? hd : nil)
        }
    }
}

struct MediathekView: View {
    @EnvironmentObject var app: AppState
    @State private var channel = ""
    @State private var query = ""
    @State private var items: [MediathekItem]?
    @State private var error: String?
    @State private var loadingMore = false
    @State private var hasMore = true
    @State private var reload = 0

    private static let fmt: DateFormatter = {
        let f = DateFormatter(); f.locale = Locale(identifier: "de_DE"); f.dateFormat = "EE dd.MM. HH:mm"; return f
    }()

    private var loadKey: String { "\(channel)|\(query)|\(reload)" }

    var body: some View {
        VStack(spacing: 8) {
            TextField("Sendung oder Thema suchen (z.B. Tatort)", text: $query)
                .textFieldStyle(.roundedBorder).autocorrectionDisabled().padding(.horizontal)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(Mediathek.channels, id: \.query) { c in
                        Button { channel = c.query } label: {
                            Text(c.label).font(.subheadline).padding(.horizontal, 12).padding(.vertical, 6)
                                .background(Capsule().fill(channel == c.query ? Brand.cyan : Brand.surface))
                                .foregroundColor(channel == c.query ? .black : .white)
                        }.buttonStyle(.plain)
                    }
                }.padding(.horizontal)
            }
            Group {
                if let error {
                    VStack(spacing: 10) { Text(error).foregroundColor(.secondary); Button("Erneut versuchen") { reload += 1 } }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let items {
                    if items.isEmpty {
                        Text(query.isEmpty ? "Keine Sendungen gefunden" : "Keine Treffer für „\(query)“").foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        List {
                            ForEach(items) { item in
                                Button { play(item) } label: { row(item) }.listRowBackground(Brand.surface)
                            }
                            if hasMore {
                                Button(loadingMore ? "Lädt …" : "Weitere Sendungen laden") { loadMore() }
                                    .disabled(loadingMore).listRowBackground(Color.clear)
                            }
                        }
                        .listStyle(.plain).scrollContentBackground(.hidden)
                    }
                } else {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
        }
        .padding(.top, 8)
        .background(Brand.background.ignoresSafeArea())
        .portivaToolbar("Mediathek")
        .task(id: loadKey) {
            items = nil; error = nil; hasMore = true
            if !query.isEmpty { try? await Task.sleep(nanoseconds: 500_000_000) }
            if Task.isCancelled { return }
            do {
                let r = try await Mediathek.search(query: query, channel: channel)
                if Task.isCancelled { return }
                items = r; hasMore = r.count >= 30
            } catch { if !Task.isCancelled { self.error = error.localizedDescription } }
        }
    }

    private func row(_ item: MediathekItem) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 8) {
                Text(item.channel).font(.caption2.bold()).foregroundColor(.black)
                    .padding(.horizontal, 6).padding(.vertical, 1).background(RoundedRectangle(cornerRadius: 4).fill(Brand.cyan))
                Text(item.topic).font(.caption).foregroundColor(.secondary).lineLimit(1)
            }
            Text(item.title).font(.subheadline.bold()).foregroundColor(.white).lineLimit(2)
            Text([item.timestamp > 0 ? Self.fmt.string(from: Date(timeIntervalSince1970: item.timestamp)) : nil,
                  item.duration > 0 ? "\(Int((item.duration + 59) / 60)) Min." : nil,
                  item.urlHd != nil ? "HD" : nil].compactMap { $0 }.joined(separator: "  ·  "))
                .font(.caption).foregroundColor(.secondary)
        }
        .padding(.vertical, 4)
    }

    private func play(_ item: MediathekItem) {
        let title = (!item.topic.isEmpty && !item.title.localizedCaseInsensitiveContains(item.topic)) ? "\(item.topic): \(item.title)" : item.title
        let content = ContentItem(id: "mediathek:" + item.id, name: title, type: .MOVIE, url: item.bestUrl)
        app.play([PlayEntry(title: title, url: item.bestUrl, live: false, item: content)])
    }

    private func loadMore() {
        guard let cur = items else { return }
        loadingMore = true
        Task {
            if let more = try? await Mediathek.search(query: query, channel: channel, offset: cur.count) {
                let known = Set(cur.map(\.id))
                let add = more.filter { !known.contains($0.id) }
                items = cur + add; hasMore = !add.isEmpty
            }
            loadingMore = false
        }
    }
}
