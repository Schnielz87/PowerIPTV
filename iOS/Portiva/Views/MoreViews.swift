import SwiftUI

/** Detailseite fuer Filme und Serien (Staffeln, Folgen, Fortsetzen). */
struct DetailView: View {
    @EnvironmentObject var app: AppState
    let item: ContentItem
    @State private var movie: MovieInfo?
    @State private var series: SeriesInfo?
    @State private var season: Int?
    @State private var loading = true
    @State private var tick = 0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top, spacing: 16) {
                    NetImage(url: movie?.cover ?? series?.cover ?? item.logo).frame(width: 130, height: 195).clipShape(RoundedRectangle(cornerRadius: 12))
                    VStack(alignment: .leading, spacing: 8) {
                        Text(item.name).font(.title2.bold())
                        HStack(spacing: 10) {
                            if let f = Rules.fsk(movie?.age ?? series?.age) {
                                Text("FSK \(f)").font(.caption.bold()).foregroundColor(.black).padding(.horizontal, 8).padding(.vertical, 2).background(RoundedRectangle(cornerRadius: 6).fill(Color.yellow))
                            }
                            if let y = item.year { Text(String(y)) }
                            if let r = movie?.rating ?? series?.rating ?? item.rating, r > 0 { Text("★ \(String(format: "%.1f", r))") }
                        }.font(.subheadline).foregroundColor(.secondary)
                        if let g = movie?.genre ?? series?.genre { Text(g).font(.subheadline).foregroundColor(.secondary) }
                        if let d = movie?.duration { Text(d).font(.subheadline).foregroundColor(.secondary) }
                        buttons
                    }
                }
                if let plot = movie?.plot ?? series?.plot { Text(plot).font(.body) }
                if let cast = movie?.cast ?? series?.cast { Text("Mit: \(cast)").font(.footnote).foregroundColor(.secondary) }
                if loading { ProgressView().frame(maxWidth: .infinity) }
                if let s = series, !s.seasons.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(s.seasons, id: \.self) { n in
                                Button("Staffel \(n)") { season = n }
                                    .padding(.horizontal, 14).padding(.vertical, 8)
                                    .background(Capsule().fill(season == n ? Brand.navy : Brand.surface))
                                    .foregroundColor(season == n ? Brand.cyan : .white)
                            }
                        }
                    }
                    let _ = tick
                    ForEach(s.episodes[season ?? s.seasons[0]] ?? []) { ep in
                        Button { playEpisode(ep) } label: { episodeRow(ep) }
                    }
                }
            }.padding(16)
        }
        .background(Brand.background.ignoresSafeArea())
        .portivaToolbar(item.type == .SERIES ? "Serie" : "Film")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { app.library?.toggleFavorite(item); tick += 1 } label: {
                    Image(systemName: app.library?.isFavorite(item) == true ? "heart.fill" : "heart")
                }
            }
        }
        .task { await load() }
        .onAppear { tick += 1 }
    }

    @ViewBuilder private var buttons: some View {
        let _ = tick
        if item.type == .MOVIE {
            let pos = app.library?.position(item.key) ?? 0
            Button { play(startAt: pos) } label: {
                Label(pos > 0 ? "Fortsetzen (\(Rules.time(pos)))" : "Abspielen", systemImage: "play.fill").frame(maxWidth: .infinity)
            }.buttonStyle(.borderedProminent)
            if pos > 0 { Button("Von vorne") { play(startAt: 0) }.buttonStyle(.bordered) }
        } else if let s = series, let next = nextEpisode(s) {
            let pos = app.library?.position("EPISODE:\(next.id)") ?? 0
            Button { playEpisode(next) } label: {
                Label(pos > 0 ? "Fortsetzen S\(next.season) E\(next.episodeNum)" : "S\(next.season) E\(next.episodeNum) abspielen", systemImage: "play.fill").frame(maxWidth: .infinity)
            }.buttonStyle(.borderedProminent)
        }
    }

    private func episodeRow(_ ep: Episode) -> some View {
        let key = "EPISODE:\(ep.id)"
        let watched = app.library?.isWatched(key) == true
        let pos = app.library?.position(key) ?? 0
        return HStack(spacing: 12) {
            NetImage(url: ep.image).frame(width: 140, height: 80).clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(alignment: .bottomLeading) { if watched { Image(systemName: "checkmark.circle.fill").foregroundColor(.green).padding(4) } else if pos > 0 { Rectangle().fill(Brand.cyan).frame(height: 4) } }
            VStack(alignment: .leading, spacing: 4) {
                Text("\(ep.episodeNum). \(ep.title)").font(.subheadline.bold()).foregroundColor(.white).lineLimit(2)
                Text([ep.duration, ep.plot].compactMap { $0 }.joined(separator: " · ")).font(.caption).foregroundColor(.secondary).lineLimit(2)
            }
            Spacer()
        }
        .padding(8).background(RoundedRectangle(cornerRadius: 12).fill(Brand.surface))
    }

    private func load() async {
        guard let src = app.source, movie == nil, series == nil else { return }
        if item.type == .SERIES {
            series = try? await src.seriesInfo(item)
            if let s = series { season = nextEpisode(s)?.season ?? s.seasons.first }
        } else { movie = try? await src.movieInfo(item) }
        loading = false
    }

    private func allEpisodes(_ s: SeriesInfo) -> [Episode] { s.seasons.flatMap { s.episodes[$0] ?? [] } }

    private func nextEpisode(_ s: SeriesInfo) -> Episode? {
        let all = allEpisodes(s)
        guard let lib = app.library else { return all.first }
        if let h = lib.history.first(where: { $0.item.key == item.key }), let e = h.episode, let i = all.firstIndex(where: { $0.id == e.id }) {
            if !lib.isWatched("EPISODE:\(e.id)") { return all[i] }
            if i + 1 < all.count { return all[i + 1] }
        }
        return all.first { !lib.isWatched("EPISODE:\($0.id)") } ?? all.first
    }

    private func play(startAt: Double) {
        var it = item
        if let ext = movie?.ext { it.ext = ext }
        var e = app.entry(for: it)
        e.startAt = startAt
        app.play([e])
    }

    private func playEpisode(_ ep: Episode) {
        guard let s = series else { return }
        let all = allEpisodes(s)
        let entries = all.map { app.entry(for: $0, series: item) }
        app.play(entries, index: all.firstIndex(of: ep) ?? 0)
    }
}

/** Suche ueber Sender, Filme und Serien. */
struct SearchView: View {
    @EnvironmentObject var app: AppState
    @State private var query = ""
    @State private var results: [ContentType: [ContentItem]] = [:]
    @State private var loading = false

    var body: some View {
        List {
            if loading { ProgressView() }
            ForEach(ContentType.allCases, id: \.self) { t in
                if let r = results[t], !r.isEmpty {
                    Section("\(t.title) (\(r.count))") {
                        ForEach(r) { i in
                            if i.type == .LIVE || app.source?.supportsDetails == false {
                                Button { app.play([app.entry(for: i)]) } label: { row(i) }
                            } else {
                                NavigationLink(value: Route.detail(i)) { row(i) }
                            }
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Brand.background.ignoresSafeArea())
        .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Sender, Filme, Serien")
        .onSubmit(of: .search) { Task { await run() } }
        .portivaToolbar("Suche")
    }

    private func row(_ i: ContentItem) -> some View {
        HStack { NetImage(url: i.logo, contentMode: .fit).frame(width: 44, height: 44).clipShape(RoundedRectangle(cornerRadius: 6)); Text(i.name).foregroundColor(.white) }
    }

    private func run() async {
        guard query.count >= 2, let src = app.source else { return }
        loading = true
        var out: [ContentType: [ContentItem]] = [:]
        for t in ContentType.allCases {
            let all = (try? await src.items(t, category: nil)) ?? []
            out[t] = Array(all.filter { Rules.matches($0.name, query) }.prefix(60))
        }
        results = out
        loading = false
    }
}

/** Favoriten & Verlauf. */
struct FavoritesView: View {
    @EnvironmentObject var app: AppState
    @State private var tick = 0

    var body: some View {
        List {
            let _ = tick
            if let lib = app.library {
                ForEach(ContentType.allCases, id: \.self) { t in
                    let favs = lib.favorites.filter { $0.type == t }
                    if !favs.isEmpty {
                        Section("❤ \(t.title)") {
                            ForEach(favs) { i in entryRow(i) }
                                .onDelete { idx in idx.map { favs[$0] }.forEach { lib.toggleFavorite($0) }; tick += 1 }
                        }
                    }
                }
                if !lib.history.isEmpty {
                    Section("🕘 Zuletzt gesehen") {
                        ForEach(lib.history.prefix(40)) { h in
                            Button {
                                if let ep = h.episode { app.play([app.entry(for: ep, series: h.item)]) } else { app.play([app.entry(for: h.item)]) }
                            } label: { HStack { NetImage(url: h.item.logo, contentMode: .fit).frame(width: 44, height: 44); Text(h.item.name).foregroundColor(.white) } }
                        }
                    }
                }
                if lib.favorites.isEmpty && lib.history.isEmpty { Text("Noch keine Favoriten – lange auf einen Eintrag drücken.").foregroundColor(.secondary) }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Brand.background.ignoresSafeArea())
        .portivaToolbar("Favoriten & Verlauf")
    }

    @ViewBuilder private func entryRow(_ i: ContentItem) -> some View {
        let label = HStack { NetImage(url: i.logo, contentMode: .fit).frame(width: 44, height: 44); Text(i.name).foregroundColor(.white) }
        if i.type == .LIVE || app.source?.supportsDetails == false { Button { app.play([app.entry(for: i)]) } label: { label } }
        else { NavigationLink(value: Route.detail(i)) { label } }
    }
}

/** TV-Guide: Sender und ihr Programm (Catch-up bei Archiv-Sendern). */
struct EpgView: View {
    @EnvironmentObject var app: AppState
    @State private var channels: [ContentItem] = []
    @State private var selected: ContentItem?
    @State private var programme: [EpgEntry] = []

    var body: some View {
        HStack(spacing: 0) {
            List(channels, selection: Binding(get: { selected?.id }, set: { id in selected = channels.first { $0.id == id }; Task { await loadProgramme() } })) { c in
                Text((c.number.map { "\($0)  " } ?? "") + c.name + (c.archiveDays > 0 ? "  ↺" : "")).tag(c.id)
            }
            .frame(maxWidth: 280)
            List(programme) { p in
                let now = Date().timeIntervalSince1970 * 1000
                let live = p.start <= now && p.end > now
                let catchup = selected.flatMap { app.source?.catchupUrl($0, start: p.start, end: p.end) }
                Button {
                    guard let c = selected else { return }
                    if live { app.play([app.entry(for: c)]) }
                    else if p.end <= now, let u = catchup { app.play([PlayEntry(title: "\(c.name) · \(p.title)", url: u, live: false, item: c)]) }
                    else { app.show(p.end <= now ? "Für diese Sendung gibt es keine Aufzeichnung" : "Die Sendung hat noch nicht begonnen") }
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text("\(Rules.clock(p.start)) – \(Rules.clock(p.end))").font(.caption).foregroundColor(.secondary)
                            if live { Text("● LIVE").font(.caption.bold()).foregroundColor(.red) }
                            else if p.end <= now && catchup != nil { Text("↺ Catch-up").font(.caption.bold()).foregroundColor(Brand.cyan) }
                        }
                        Text(p.title).foregroundColor(.white)
                        if let d = p.description { Text(d).font(.caption).foregroundColor(.secondary).lineLimit(2) }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Brand.background.ignoresSafeArea())
        .portivaToolbar("TV-Guide")
        .task {
            guard channels.isEmpty, let src = app.source else { return }
            let favs = app.library?.favorites.filter { $0.type == .LIVE } ?? []
            channels = favs.isEmpty ? Array(((try? await src.items(.LIVE, category: nil)) ?? []).prefix(400)) : favs
            selected = channels.first
            await loadProgramme()
        }
    }

    private func loadProgramme() async {
        guard let c = selected, let src = app.source else { return }
        programme = await src.epg(c, full: true).filter { $0.end > Date().timeIntervalSince1970 * 1000 - Double(max(c.archiveDays, 0)) * 86_400_000 - 3 * 3_600_000 }
    }
}

/** Einstellungen. */
struct SettingsView: View {
    @EnvironmentObject var app: AppState
    @State private var liveFormat = "ts"
    @State private var autoNext = true
    @State private var dataSaver = "AUTO"
    @State private var stableMode = "AUTO"
    @State private var sharpen = "OFF"

    var body: some View {
        Form {
            Section("Wiedergabe") {
                Picker("Live-TV-Format (Xtream)", selection: $liveFormat) { Text("MPEG-TS (.ts)").tag("ts"); Text("HLS (.m3u8)").tag("m3u8") }
                    .onChange(of: liveFormat) { app.settings.liveFormat = $0 }
                Toggle("Nächste Folge automatisch", isOn: $autoNext).onChange(of: autoNext) { app.settings.autoNext = $0 }
                Text("Player: VLC – gleicher Player wie Android und Windows (spielt DTS, AC3, MKV …)").font(.footnote).foregroundColor(.secondary)
            }
            Section("Bild & Verbindung") {
                Picker("Mobile-Daten-Modus", selection: $dataSaver) {
                    Text("Automatisch (nur Mobilfunk)").tag("AUTO"); Text("Immer an").tag("ON"); Text("Aus").tag("OFF")
                }.onChange(of: dataSaver) { app.settings.dataSaver = $0 }
                Picker("Stabil-Modus (gegen Stocken)", selection: $stableMode) {
                    Text("Automatisch").tag("AUTO"); Text("Immer an").tag("ON"); Text("Aus").tag("OFF")
                }.onChange(of: stableMode) { app.settings.stableMode = $0 }
                Picker("Bildschärfe", selection: $sharpen) {
                    Text("Aus").tag("OFF"); Text("Leicht").tag("LIGHT"); Text("Stark").tag("STRONG")
                }.onChange(of: sharpen) { app.settings.sharpen = $0 }
                Text("Mobile Daten: nimmt unterwegs die SD-Version eines Senders, im WLAN bleibt die volle Qualität. Stabil-Modus: größerer Puffer – schaltet sich bei Mobilfunk oder erkanntem Stocken selbst zu.")
                    .font(.footnote).foregroundColor(.secondary)
            }
            Section("Inhalte") {
                Button("Kategorie-Sprache zurücksetzen (alle Sprachen)") { app.settings.categoryLanguage = ""; app.show("Alle Sprachen werden angezeigt") }
                Button("Playlist neu laden") { app.source?.clearCache(); app.show("Wird beim nächsten Öffnen neu geladen") }
            }
            Section("Zugänge") { NavigationLink("Zugänge verwalten", value: Route.profiles) }
            Section("Über Portiva") {
                HStack { Text("Version"); Spacer(); Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "-").foregroundColor(.secondary) }
                NavigationLink("Update", value: Route.update)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Brand.background.ignoresSafeArea())
        .portivaToolbar("Einstellungen")
        .onAppear {
            liveFormat = app.settings.liveFormat; autoNext = app.settings.autoNext
            dataSaver = app.settings.dataSaver; stableMode = app.settings.stableMode; sharpen = app.settings.sharpen
        }
    }
}

/** Update: neue Version auf GitHub pruefen. */
struct UpdateView: View {
    @State private var latest: String?
    @State private var checking = false
    private let current = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.1.0"

    var body: some View {
        Form {
            HStack { Text("Installiert"); Spacer(); Text(current).foregroundColor(.secondary) }
            Button { Task { await check() } } label: { HStack { Text("Nach Updates suchen"); if checking { Spacer(); ProgressView() } } }
            if let l = latest {
                if newer(l, current) {
                    Text("Neue Version \(l) verfügbar.").foregroundColor(Brand.cyan)
                    Text("iPhone/iPad: In den GitHub-Releases „Portiva-iOS-….ipa“ laden und wie bei der Erstinstallation (z.B. mit Sideloadly oder AltStore) aufspielen. Zugänge und Favoriten bleiben erhalten.").font(.footnote)
                    Link("Releases öffnen", destination: URL(string: "https://github.com/Schnielz87/PowerIPTV/releases/latest")!)
                } else { Text("✓ Du hast die neueste Version.").foregroundColor(.green) }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Brand.background.ignoresSafeArea())
        .portivaToolbar("Update")
    }

    private func check() async {
        checking = true
        defer { checking = false }
        guard let url = URL(string: "https://api.github.com/repos/Schnielz87/PowerIPTV/releases/latest"),
              let d = try? await Net.data(url, timeout: 20),
              let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any], let tag = o["tag_name"] as? String else { latest = "?"; return }
        // Nur melden, wenn das Release auch eine iPhone-Datei enthaelt (GitHub-Build kann einzeln ausfallen)
        let assets = (o["assets"] as? [[String: Any]]) ?? []
        let hasIpa = assets.contains { (($0["name"] as? String) ?? "").lowercased().hasSuffix(".ipa") }
        latest = hasIpa ? tag.replacingOccurrences(of: "v", with: "") : current
    }

    private func newer(_ a: String, _ b: String) -> Bool {
        let x = a.split(separator: ".").map { Int($0) ?? 0 }, y = b.split(separator: ".").map { Int($0) ?? 0 }
        for i in 0..<max(x.count, y.count) {
            let p = i < x.count ? x[i] : 0, q = i < y.count ? y[i] : 0
            if p != q { return p > q }
        }
        return false
    }
}
