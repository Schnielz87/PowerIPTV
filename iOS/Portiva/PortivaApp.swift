import SwiftUI

@main
struct PortivaApp: App {
    @StateObject private var app = AppState()
    @State private var splash = true

    var body: some Scene {
        WindowGroup {
            ZStack {
                RootView().environmentObject(app)
                if splash { SplashView().transition(.opacity) }
            }
            .preferredColorScheme(.dark)
            .tint(Brand.cyan)
            .task { try? await Task.sleep(nanoseconds: 1_600_000_000); withAnimation { splash = false } }
        }
    }
}

struct SplashView: View {
    @State private var scale = 0.7
    var body: some View {
        ZStack {
            RadialGradient(colors: [Color(red: 0.06, green: 0.16, blue: 0.3), Brand.background], center: .center, startRadius: 10, endRadius: 600).ignoresSafeArea()
            VStack(spacing: 18) {
                Image("Logo").resizable().scaledToFit().frame(width: 150, height: 150).scaleEffect(scale)
                // Gross und leuchtend "PowerIPTV" (zweifarbig), darunter klein "by Portiva©"
                HStack(spacing: 0) {
                    Text("Power").foregroundColor(Brand.cyan)
                    Text("IPTV").foregroundColor(.white)
                }
                .font(.system(size: 40, weight: .black)).tracking(2)
                .shadow(color: Brand.cyan.opacity(0.75), radius: 12)
                HStack(alignment: .top, spacing: 1) {
                    Text("by Portiva").font(.title3.weight(.semibold))
                    Text("©").font(.footnote.weight(.semibold))
                }
                .foregroundColor(.white.opacity(0.85))
            }
        }
        .onAppear { withAnimation(.spring(response: 0.6, dampingFraction: 0.6)) { scale = 1 } }
    }
}

/** Startseite oder Zugangsauswahl; Player als Vollbild darueber. */
struct RootView: View {
    @EnvironmentObject var app: AppState
    @State private var path = NavigationPath()

    var body: some View {
        ZStack {
            NavigationStack(path: $path) {
                Group {
                    if app.profile == nil { ProfilesView(first: true) } else { HomeView() }
                }
                .navigationDestination(for: Route.self) { r in r.view }
            }
            .environment(\.goHome) { path = NavigationPath() }
            .fullScreenCover(item: $app.playing) { e in PlayerScreen(entry: e).environmentObject(app) }
            .onReceive(app.link.$received) { r in
                // Zugang vom Handy empfangen -> aktivieren und zur Startseite
                if let p = r { app.activate(p); app.link.received = nil; path = NavigationPath(); app.show("Zugang „\(p.name)“ übernommen") }
            }
            if let t = app.toast, app.playing == nil {
                Text(t).foregroundColor(.white).padding(.horizontal, 18).padding(.vertical, 10)
                    .background(Capsule().fill(Brand.surface.opacity(0.97)).overlay(Capsule().stroke(Brand.accent)))
                    .frame(maxHeight: .infinity, alignment: .bottom).padding(.bottom, 40)
            }
        }
        .background(Brand.background.ignoresSafeArea())
    }
}

/** Navigationsziele. */
enum Route: Hashable {
    case browse(ContentType)
    case detail(ContentItem)
    case search
    case favorites
    case epg
    case profiles
    case settings
    case update

    @ViewBuilder var view: some View {
        switch self {
        case .browse(let t): BrowseView(type: t)
        case .detail(let i): DetailView(item: i)
        case .search: SearchView()
        case .favorites: FavoritesView()
        case .epg: EpgView()
        case .profiles: ProfilesView(first: false)
        case .settings: SettingsView()
        case .update: UpdateView()
        }
    }
}
