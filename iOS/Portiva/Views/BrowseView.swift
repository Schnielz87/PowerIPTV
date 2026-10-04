import SwiftUI

private let CAT_FAV = "__fav__", CAT_RECENT = "__recent__", CAT_ALL = "__all__"

/** Stoebern wie Android: Sprache (DE/EN …), Kategorien, Favoriten/Zuletzt/Alle, Raster. */
struct BrowseView: View {
    @EnvironmentObject var app: AppState
    let type: ContentType
    @State private var cats: [Category] = []
    @State private var selected: String?
    @State private var items: [ContentItem] = []
    @State private var loading = false
    @State private var error: String?
    @State private var query = ""
    @State private var sort = "DEFAULT"
    @State private var epgNow: [String: String] = [:]

    private var langs: [String] { Rules.detectLanguages(cats) }
    @State private var lang = ""

    private var visibleCats: [Category] {
        guard !lang.isEmpty else { return cats }
        return cats.filter { Rules.categoryLanguage($0.name) == lang || Rules.categoryLanguage($0.name) == nil }
    }

    private var shown: [ContentItem] {
        var l = query.isEmpty ? items : items.filter { Rules.matches($0.name, query) }
        switch sort {
        case "NAME_ASC": l.sort { $0.name.localizedCompare($1.name) == .orderedAscending }
        case "NAME_DESC": l.sort { $0.name.localizedCompare($1.name) == .orderedDescending }
        case "NEWEST": l.sort { ($0.added ?? 0) > ($1.added ?? 0) }
        case "RATING": l.sort { ($0.rating ?? 0) > ($1.rating ?? 0) }
        case "YEAR_DESC": l.sort { ($0.year ?? 0) > ($1.year ?? 0) }
        default: break
        }
        return l
    }

    var body: some View {
        VStack(spacing: 0) {
            // Sprachen + Kategorien (waagerecht – passt auf iPhone und iPad)
            if langs.count >= 2 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        chip("Alle", lang.isEmpty) { lang = ""; app.settings.categoryLanguage = "" }
                        ForEach(langs.prefix(12), id: \.self) { l in chip(l, lang == l) { lang = l; app.settings.categoryLanguage = l } }
                    }.padding(.horizontal, 12)
                }.padding(.top, 8)
            }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    chip("❤ Favoriten", selected == CAT_FAV) { select(CAT_FAV) }
                    chip("🕘 Zuletzt", selected == CAT_RECENT) { select(CAT_RECENT) }
                    chip(type == .LIVE ? "Alle Sender" : "Alle", selected == CAT_ALL) { select(CAT_ALL) }
                    ForEach(visibleCats) { c in chip(lang.isEmpty ? c.name : Rules.stripLanguage(c.name), selected == c.id) { select(c.id) } }
                }.padding(.horizontal, 12)
            }.padding(.vertical, 8)

            if loading { Spacer(); ProgressView(); Spacer() }
            else if let error { Spacer(); Text(error).foregroundColor(.secondary).multilineTextAlignment(.center).padding(); Spacer() }
            else if shown.isEmpty { Spacer(); Text(selected == CAT_FAV ? "Noch keine Favoriten – lange auf einen Eintrag drücken" : "Keine Einträge").foregroundColor(.secondary); Spacer() }
            else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: type == .LIVE ? 150 : 110), spacing: 12)], spacing: 14) {
                        ForEach(shown) { i in
                            Group {
                                if i.type == .LIVE || app.source?.supportsDetails == false {
                                    Button { open(i) } label: { card(i) }
                                } else {
                                    NavigationLink(value: Route.detail(i)) { card(i) }
                                }
                            }
                            .contextMenu {
                                Button { app.library?.toggleFavorite(i); app.objectWillChange.send() } label: {
                                    Label(app.library?.isFavorite(i) == true ? "Aus Favoriten entfernen" : "Zu Favoriten", systemImage: "heart")
                                }
                            }
                            .onAppear { if type == .LIVE { loadEpg(i) } }
                        }
                    }.padding(12)
                }
            }
        }
        .background(Brand.background.ignoresSafeArea())
        .searchable(text: $query, prompt: "In dieser Kategorie suchen")
        .portivaToolbar(type.title)
        .toolbar {
            if type != .LIVE {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Picker("Sortieren", selection: $sort) {
                            Text("Standard").tag("DEFAULT"); Text("A – Z").tag("NAME_ASC"); Text("Z – A").tag("NAME_DESC")
                            Text("Neu hinzugefügt").tag("NEWEST"); Text("Beste Bewertung").tag("RATING"); Text("Neueste Jahre").tag("YEAR_DESC")
                        }
                    } label: { Image(systemName: "arrow.up.arrow.down") }
                }
            }
        }
        .task { lang = app.settings.categoryLanguage; await loadCategories() }
    }

    private func card(_ i: ContentItem) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            ItemCard(item: i)
            if type == .LIVE, let e = epgNow[i.id], !e.isEmpty { Text(e).font(.caption2).foregroundColor(.secondary).lineLimit(1) }
        }
    }

    private func chip(_ text: String, _ active: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(.subheadline).lineLimit(1).padding(.horizontal, 14).padding(.vertical, 8)
                .background(Capsule().fill(active ? Brand.navy : Brand.surface))
                .foregroundColor(active ? Brand.cyan : .white)
        }
    }

    private func loadCategories() async {
        guard let src = app.source, cats.isEmpty else { return }
        loading = true
        do { cats = try await src.categories(type); AdultContent.register(src.profile.id, type, cats) } catch { self.error = "Kategorien konnten nicht geladen werden: \(error.localizedDescription)" }
        loading = false
        if selected == nil { select(type == .LIVE ? (visibleCats.first?.id ?? CAT_ALL) : CAT_ALL) }
    }

    private func select(_ id: String) {
        selected = id
        Task { await loadItems(id) }
    }

    private func loadItems(_ id: String) async {
        guard let src = app.source, let lib = app.library else { return }
        loading = true; error = nil
        switch id {
        case CAT_FAV: items = lib.favorites.filter { $0.type == type }
        case CAT_RECENT: items = lib.history.map { $0.item }.filter { $0.type == type }
        default:
            do { items = try await src.items(type, category: id == CAT_ALL ? nil : id) }
            catch { self.error = "Laden fehlgeschlagen: \(error.localizedDescription)"; items = [] }
        }
        if selected == id { loading = false }
    }

    private func loadEpg(_ i: ContentItem) {
        guard epgNow[i.id] == nil, let src = app.source else { return }
        epgNow[i.id] = ""
        Task {
            let l = await src.epg(i, full: false)
            let now = Date().timeIntervalSince1970 * 1000
            if let cur = l.first(where: { $0.start <= now && $0.end > now }) { epgNow[i.id] = "\(Rules.clock(cur.start))  \(cur.title)" }
        }
    }

    private func open(_ i: ContentItem) {
        if i.type == .LIVE {
            let list = shown
            app.play(list.map { app.entry(for: $0) }, index: list.firstIndex(of: i) ?? 0)
        } else {
            app.play([app.entry(for: i)])
        }
    }
}
