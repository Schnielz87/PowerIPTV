import SwiftUI

/** Startseite wie Android: grosse Kacheln, Weiterschauen, kleine Kacheln, Benutzer-Maennchen. */
struct HomeView: View {
    @EnvironmentObject var app: AppState
    @State private var showSwitch = false

    private let columns = [GridItem(.adaptive(minimum: 150), spacing: 12)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                // Kopfzeile
                HStack(spacing: 12) {
                    Image("Logo").resizable().scaledToFit().frame(width: 40, height: 40).clipShape(RoundedRectangle(cornerRadius: 8))
                    if let a = app.account {
                        Text([a.expiresAt.map { "Gültig bis \($0.formatted(date: .numeric, time: .omitted))" },
                              a.maxConnections.map { "\($0) \($0 == 1 ? "Verbindung" : "Verbindungen")" }].compactMap { $0 }.joined(separator: " · "))
                            .font(.caption).foregroundColor(.secondary)
                    }
                    Spacer()
                    NavigationLink(value: Route.search) { Image(systemName: "magnifyingglass").font(.title3) }
                    Button { showSwitch = true } label: {
                        HStack(spacing: 6) { Image(systemName: "person.fill"); Text(app.profile?.name ?? "").lineLimit(1) }
                            .font(.subheadline).padding(.horizontal, 12).padding(.vertical, 8).background(Capsule().fill(Brand.surface))
                    }
                }
                // Grosse Kacheln
                HStack(spacing: 12) {
                    bigTile("LIVE TV", "Sender & TV-Guide", .LIVE, [Color(red: 0.12, green: 0.42, blue: 1), Color(red: 0.04, green: 0.15, blue: 0.28)])
                    bigTile("FILME", "Filme & Neuheiten", .MOVIE, [Color(red: 0.48, green: 0.24, blue: 1), Color(red: 0.11, green: 0.08, blue: 0.31)])
                    bigTile("SERIEN", "Serien & Staffeln", .SERIES, [Color(red: 0.05, green: 0.54, blue: 0.54), Color(red: 0.04, green: 0.15, blue: 0.28)])
                }
                .frame(height: 150)

                if let lib = app.library {
                    let cont = Array(lib.continueWatching.prefix(15))
                    if !cont.isEmpty {
                        Text("Weiterschauen").font(.headline)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(cont) { h in
                                    Button { resume(h) } label: {
                                        ItemCard(item: h.item, progress: min(1, h.position / max(h.duration, 1))).frame(width: 120)
                                    }
                                }
                            }
                        }
                    }
                    let live = Array(lib.history.filter { $0.item.type == .LIVE }.prefix(15))
                    if !live.isEmpty {
                        Text("Zuletzt gesehene Sender").font(.headline)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(live) { h in
                                    Button { app.play([app.entry(for: h.item)]) } label: { ItemCard(item: h.item).frame(width: 140) }
                                }
                            }
                        }
                    }
                }

                // Kleine Kacheln
                LazyVGrid(columns: columns, spacing: 12) {
                    smallTile("magnifyingglass", "Suche", .search)
                    Button { app.source?.clearCache(); app.show("Playlist wird neu geladen …") } label: { tileLabel("arrow.clockwise", "Playlist aktualisieren") }
                    smallTile("calendar", "TV-Guide (EPG)", .epg)
                    smallTile("heart.fill", "Favoriten & Verlauf", .favorites)
                    smallTile("person.2.fill", "Benutzer wechseln", .profiles)
                    smallTile("gearshape.fill", "Einstellungen", .settings)
                    smallTile("arrow.down.circle.fill", "Update", .update)
                }
            }
            .padding(16)
        }
        .background(Brand.background.ignoresSafeArea())
        .navigationBarHidden(true)
        .confirmationDialog("Benutzer wechseln", isPresented: $showSwitch) {
            ForEach(app.profiles) { p in
                Button(p.id == app.profile?.id ? "✓ \(p.name)" : p.name) { app.activate(p) }
            }
        }
    }

    private func resume(_ h: HistoryEntry) {
        if let ep = h.episode { app.play([app.entry(for: ep, series: h.item)]) } else { app.play([app.entry(for: h.item)]) }
    }

    private func bigTile(_ title: String, _ sub: String, _ type: ContentType, _ colors: [Color]) -> some View {
        NavigationLink(value: Route.browse(type)) {
            ZStack(alignment: .bottomLeading) {
                LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.title2.weight(.heavy)).foregroundColor(.white)
                    Text(sub).font(.caption).foregroundColor(.white.opacity(0.85))
                }.padding(14)
            }
            .clipShape(RoundedRectangle(cornerRadius: 18))
        }
    }

    private func smallTile(_ icon: String, _ label: String, _ route: Route) -> some View {
        NavigationLink(value: route) { tileLabel(icon, label) }
    }

    private func tileLabel(_ icon: String, _ label: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: icon).font(.title2).foregroundColor(Brand.cyan)
            Text(label).font(.subheadline).foregroundColor(.white).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 90)
        .background(RoundedRectangle(cornerRadius: 16).fill(Brand.surface))
    }
}
