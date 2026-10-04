import SwiftUI
import UIKit

/** Vollbild-Player wie Android: links Helligkeit, rechts Lautstaerke, unten Seitenverhaeltnis – Geschwindigkeit – Untertitel, Zahnrad = Einstellungsleiste. */
struct PlayerScreen: View {
    @EnvironmentObject var app: AppState
    @StateObject private var ctl = PlayerController()
    @State private var entry: PlayEntry
    @State private var overlay = true
    @State private var lastTouch = Date()
    @State private var showSettings = false
    @State private var showSend = false
    @State private var dragging: Double?
    @State private var brightness = Double(UIScreen.main.brightness)
    @State private var nextCountdown: Int?
    @State private var sleepUntil: Date?
    @State private var viewSize: CGSize = .zero
    @State private var epg: [EpgEntry] = []
    @State private var showChannels = false
    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    init(entry: PlayEntry) { _entry = State(initialValue: entry) }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                Color.black.ignoresSafeArea()
                VLCVideoView(controller: ctl).ignoresSafeArea()
                    .onTapGesture(count: 2) { location in seekByDoubleTap(location.x > geo.size.width / 2) }
                    .onTapGesture { withAnimation { overlay.toggle() }; lastTouch = Date() }

                if ctl.buffering && ctl.error == nil { ProgressView().tint(.white).scaleEffect(1.6) }
                if let err = ctl.error { errorBox(err) }

                if overlay { controls(geo.size).transition(.opacity) }
                if let n = nextCountdown { nextCard(n) }
                if showSettings { settingsPanel.transition(.move(edge: .trailing)) }
                if let t = app.toast { toastView(t) }
            }
            .onAppear { viewSize = geo.size }
            .onChange(of: geo.size) { viewSize = $0; ctl.setAspect(ctl.aspect, viewSize: $0) }
        }
        .statusBarHidden(true)
        .persistentSystemOverlays(.hidden)
        .onAppear {
            ctl.subtitleScale = ["KLEIN": 75, "NORMAL": 100, "GROSS": 135, "SEHR_GROSS": 170][app.settings.subtitleSize] ?? 100
            ctl.play(entry)
            ctl.setAspect(AspectMode(rawValue: app.settings.aspect) ?? .FIT, viewSize: viewSize)
            UIApplication.shared.isIdleTimerDisabled = true
        }
        .onDisappear { savePosition(); ctl.stop(); UIApplication.shared.isIdleTimerDisabled = false }
        .onReceive(tick) { _ in everySecond() }
        .task(id: entry.url) { await loadEpg() }
        .sheet(isPresented: $showChannels) { channelSheet }
        .onChange(of: ctl.ended) { if $0 { onEnded() } }
        .sheet(isPresented: $showSend) { SendToDeviceSheet(entry: entry, position: ctl.time, duration: ctl.length) { name in
            showSend = false; savePosition(); app.show("Läuft jetzt auf „\(name)“"); close()
        } }
    }

    // ---------------- Steuerung ----------------

    @ViewBuilder
    private func controls(_ size: CGSize) -> some View {
        ZStack {
            LinearGradient(colors: [.black.opacity(0.7), .clear, .clear, .black.opacity(0.8)], startPoint: .top, endPoint: .bottom).ignoresSafeArea().allowsHitTesting(false)
            VStack(spacing: 0) {
                // oben
                HStack(spacing: 16) {
                    circleButton("arrow.left") { close() }
                    Text(entry.title).font(.headline).foregroundColor(.white).lineLimit(1)
                    Spacer()
                    if let item = entry.item, let lib = app.library {
                        Button { lib.toggleFavorite(item); touch() } label: {
                            Image(systemName: lib.isFavorite(item) ? "heart.fill" : "heart").font(.title3).foregroundColor(lib.isFavorite(item) ? .pink : .white)
                        }
                    }
                    if entry.url.hasPrefix("http") {
                        Button { showSend = true } label: { Image(systemName: "tv.and.mediabox").font(.title3).foregroundColor(.white) }
                    }
                    circleButton("gearshape.fill") { withAnimation { showSettings = true } }
                }
                .padding(.horizontal, 20).padding(.top, 14)
                Spacer()
                // Mitte: Helligkeit | -10 | Pause | +10 | Lautstaerke
                HStack {
                    VerticalLevel(icon: "sun.max", value: brightness) { v in brightness = v; UIScreen.main.brightness = CGFloat(v); touch() }
                    Spacer()
                    if entry.live {
                        if app.playQueue.count > 1 { roundIcon("backward.end.fill") { zap(-1) } }
                    } else { roundIcon("gobackward.10") { ctl.seekBy(-10_000); touch() } }
                    Spacer().frame(width: 40)
                    Button { ctl.togglePause(); touch() } label: {
                        Image(systemName: ctl.playing ? "pause.fill" : "play.fill").font(.system(size: 44)).foregroundColor(.white).frame(width: 84, height: 84)
                    }
                    Spacer().frame(width: 40)
                    if entry.live {
                        if app.playQueue.count > 1 { roundIcon("forward.end.fill") { zap(1) } }
                    } else { roundIcon("goforward.10") { ctl.seekBy(10_000); touch() } }
                    Spacer()
                    VerticalLevel(icon: ctl.volume < 0.01 ? "speaker.slash.fill" : "speaker.wave.2.fill", value: Double(ctl.volume)) { v in ctl.setVolume(Float(v)); touch() }
                }
                .padding(.horizontal, 28)
                Spacer()
                // unten – Live-TV: Sender-Infos + Senderliste/Seitenverhaeltnis; sonst Zeitstrahl + Knopfleiste
                if entry.live { liveInfo } else {
                VStack(spacing: 8) {
                    if !entry.live && ctl.length > 0 {
                        HStack {
                            Text(Rules.time(dragging ?? ctl.time)).font(.caption).monospacedDigit().foregroundColor(.white)
                            Slider(value: Binding(get: { (dragging ?? ctl.time) / max(ctl.length, 1) }, set: { dragging = $0 * ctl.length; touch() }),
                                   onEditingChanged: { editing in if !editing, let d = dragging { ctl.seekTo(d); dragging = nil } })
                                .tint(Color(red: 0.37, green: 0.77, blue: 0.95))
                            Text(Rules.time(ctl.length)).font(.caption).monospacedDigit().foregroundColor(.white)
                        }
                    }
                    HStack(spacing: 12) {
                        Menu {
                            ForEach(AspectMode.allCases) { a in
                                Button { ctl.setAspect(a, viewSize: viewSize); app.settings.aspect = a.rawValue; app.show("Seitenverhältnis: \(a.label)") } label: {
                                    if ctl.aspect == a { Label(a.label, systemImage: "checkmark") } else { Text(a.label) }
                                }
                            }
                        } label: { chip("aspectratio", "Seitenverhältnis", ctl.aspect.label) }
                        if !entry.live {
                            Menu {
                                ForEach([0.5, 0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { r in
                                    Button { ctl.setRate(Float(r)) } label: {
                                        let l = r == 1 ? "Normal (1×)" : "\(r.formatted())×"
                                        if abs(Double(ctl.rate) - r) < 0.01 { Label(l, systemImage: "checkmark") } else { Text(l) }
                                    }
                                }
                            } label: { chip("speedometer", "Geschwindigkeit", "\(Double(ctl.rate).formatted())×") }
                        }
                        Button { app.show(ctl.toggleSubtitles()); touch() } label: {
                            chip("captions.bubble", "Untertitel", ctl.subtitlesOn ? "An" : "Aus")
                        }
                    }
                }
                .padding(.horizontal, 20).padding(.bottom, 12)
                }
            }
        }
    }

    private var aspectMenu: some View {
        Menu {
            ForEach(AspectMode.allCases) { a in
                Button { ctl.setAspect(a, viewSize: viewSize); app.settings.aspect = a.rawValue; app.show("Seitenverhältnis: \(a.label)") } label: {
                    if ctl.aspect == a { Label(a.label, systemImage: "checkmark") } else { Text(a.label) }
                }
            }
        } label: { chip("aspectratio", "Seitenverhältnis", ctl.aspect.label) }
    }

    /** Live-TV-Infoleiste (wie Android): Senderlogo, Jetzt mit Fortschritt, Weiter; darunter Senderliste – Seitenverhaeltnis. */
    private var liveInfo: some View {
        let now = Date().timeIntervalSince1970 * 1000
        let cur = epg.first { $0.start <= now && $0.end > now }
        let next = epg.first { $0.start >= (cur?.end ?? now) }
        func line(_ e: EpgEntry?) -> String { e.map { "\(Rules.clock($0.start)) – \(Rules.clock($0.end))  \($0.title)" } ?? "Kein Programm gefunden" }
        let progress = cur.map { min(1, max(0, (now - $0.start) / max(1, $0.end - $0.start))) } ?? 0
        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8).fill(Color(red: 0.1, green: 0.12, blue: 0.16))
                    if let logo = entry.item?.logo, !logo.isEmpty { NetImage(url: logo, contentMode: .fit).padding(4) }
                    else { Image(systemName: "tv").foregroundColor(.white) }
                }
                .frame(width: 72, height: 48).clipShape(RoundedRectangle(cornerRadius: 8))
                VStack(alignment: .leading, spacing: 6) {
                    Text("Jetzt: " + line(cur)).foregroundColor(.white).lineLimit(1)
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            Capsule().fill(Color.white.opacity(0.3))
                            Capsule().fill(Brand.cyan).frame(width: g.size.width * progress)
                        }
                    }.frame(height: 4)
                    Text("Weiter: " + line(next)).foregroundColor(.white.opacity(0.8)).lineLimit(1)
                }
            }
            HStack(spacing: 12) {
                Spacer()
                if app.playQueue.count > 1 {
                    Button { showChannels = true; touch() } label: { chip("list.bullet.rectangle", "Senderliste", "") }
                }
                aspectMenu
                Spacer()
            }
        }
        .font(.subheadline)
        .padding(.horizontal, 20).padding(.bottom, 12)
    }

    /** Senderliste zum Umschalten (aktuelle Liste des Players). */
    private var channelSheet: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                List(app.playQueue) { e in
                    Button {
                        showChannels = false
                        if e.url != entry.url { entry = e; ctl.play(e); withAnimation { overlay = true }; touch() }
                    } label: {
                        HStack(spacing: 12) {
                            NetImage(url: e.item?.logo, contentMode: .fit).frame(width: 56, height: 36)
                            Text(e.title).foregroundColor(e.url == entry.url ? Brand.cyan : .white).lineLimit(1)
                        }
                    }
                    .id(e.url)
                }
                .onAppear { proxy.scrollTo(entry.url, anchor: .center) }
            }
            .navigationTitle("Senderliste").navigationBarTitleDisplayMode(.inline)
            .toolbar { Button { showChannels = false } label: { Image(systemName: "xmark.circle.fill") } }
        }
        .presentationDetents([.medium, .large])
    }

    private func loadEpg() async {
        epg = []
        guard entry.live, let item = entry.item, let src = app.source else { return }
        let url = entry.url
        let l = await src.epg(item, full: false)
        if entry.url == url { epg = l }
    }

    private var settingsPanel: some View {
        HStack(spacing: 0) {
            Color.black.opacity(0.001).onTapGesture { withAnimation { showSettings = false } }
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 14) {
                    circleButton("arrow.left") { withAnimation { showSettings = false } }
                    Text("Einstellungen").font(.title3.weight(.semibold)).foregroundColor(.white)
                }.padding(16)
                Divider().background(Color.white.opacity(0.4))
                ScrollView {
                    let _ = ctl.tracksTick
                    VStack(alignment: .leading, spacing: 4) {
                        section("film", "Videospuren")
                        radios(ctl.videoTracks) { ctl.selectVideo($0.id) }
                        section("music.note", "Audiospuren")
                        radios(ctl.audioTracks) { ctl.selectAudio($0.id) }
                        section("captions.bubble", "Untertitelspuren")
                        if ctl.subtitleTracks.filter({ $0.id >= 0 }).isEmpty { Text("Keine Untertitel im Stream").foregroundColor(.gray).padding(.leading, 26) }
                        else { radios(ctl.subtitleTracks) { ctl.selectSubtitle($0.id) } }
                        section("textformat.size", "Untertiteleinstellungen")
                        ForEach([("KLEIN", "Klein"), ("NORMAL", "Normal"), ("GROSS", "Groß"), ("SEHR_GROSS", "Sehr groß")], id: \.0) { pair in
                            let k = pair.0, l = pair.1
                            radioRow(l, app.settings.subtitleSize == k) {
                                app.settings.subtitleSize = k
                                ctl.subtitleScale = ["KLEIN": 75, "NORMAL": 100, "GROSS": 135, "SEHR_GROSS": 170][k] ?? 100
                                ctl.play(entry, startAt: ctl.time) // Schriftgroesse gilt ab Neustart des Streams
                            }
                        }
                        section("moon.zzz", sleepUntil.map { "Sleep-Timer (noch \(max(1, Int($0.timeIntervalSinceNow / 60) + 1)) Min.)" } ?? "Sleep-Timer")
                        ForEach([0, 15, 30, 60, 90, 120], id: \.self) { m in
                            radioRow(m == 0 ? "Aus" : (m == 120 ? "2 Stunden" : "\(m) Minuten"), m == 0 && sleepUntil == nil) {
                                sleepUntil = m == 0 ? nil : Date().addingTimeInterval(Double(m) * 60)
                                app.show(m == 0 ? "Sleep-Timer aus" : "Wiedergabe stoppt in \(m) Minuten")
                            }
                        }
                    }.padding(.bottom, 30)
                }
            }
            .frame(width: min(440, max(320, viewSize.width * 0.42)))
            .background(Color(red: 0.03, green: 0.05, blue: 0.08).opacity(0.95))
        }
        .ignoresSafeArea(edges: .vertical)
    }

    // ---------------- Bausteine ----------------

    private func section(_ icon: String, _ title: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon).font(.title3).foregroundColor(.white)
            Text(title).font(.title3.bold()).foregroundColor(.white)
        }.padding(.leading, 18).padding(.top, 18).padding(.bottom, 4)
    }

    private func radios(_ opts: [TrackOption], _ select: @escaping (TrackOption) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            if opts.isEmpty { Text("Keine Angaben").foregroundColor(.gray).padding(.leading, 26) }
            ForEach(opts) { o in radioRow(o.label, o.selected) { select(o) } }
        }
    }

    private func radioRow(_ label: String, _ selected: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle").font(.title3).foregroundColor(.white)
                Text(label).foregroundColor(.white).multilineTextAlignment(.leading)
                Spacer()
            }.padding(.horizontal, 22).padding(.vertical, 6)
        }
    }

    private func chip(_ icon: String, _ label: String, _ value: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
            Text(label).lineLimit(1)
            Text(value).foregroundColor(.white.opacity(0.65)).font(.caption).lineLimit(1)
        }
        .font(.subheadline).foregroundColor(.white)
        .padding(.horizontal, 14).padding(.vertical, 8)
        .background(Capsule().fill(Color.black.opacity(0.45)))
    }

    private func circleButton(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.title3.weight(.semibold)).foregroundColor(.white)
                .frame(width: 44, height: 44).background(Circle().fill(Color(red: 0.11, green: 0.31, blue: 0.54)))
        }
    }

    private func roundIcon(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { Image(systemName: icon).font(.system(size: 34)).foregroundColor(.white).frame(width: 64, height: 64) }
    }

    private func errorBox(_ text: String) -> some View {
        VStack(spacing: 14) {
            Text(text).foregroundColor(.white).multilineTextAlignment(.center)
            Button("Erneut versuchen") { ctl.togglePause() }.buttonStyle(.borderedProminent)
        }.padding(24).background(RoundedRectangle(cornerRadius: 16).fill(Color.black.opacity(0.75)))
    }

    private func nextCard(_ n: Int) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Nächste Folge in \(n) s").font(.headline).foregroundColor(.white)
            HStack {
                Button("▶ Jetzt abspielen") { playNext() }.buttonStyle(.borderedProminent)
                Button("Abbrechen") { nextCountdown = -1 }.buttonStyle(.bordered).tint(.white)
            }
        }
        .padding(16).background(RoundedRectangle(cornerRadius: 14).fill(Color(red: 0.06, green: 0.09, blue: 0.13).opacity(0.95)))
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing).padding(24)
        .opacity(n >= 0 ? 1 : 0)
    }

    private func toastView(_ t: String) -> some View {
        Text(t).foregroundColor(.white).padding(.horizontal, 18).padding(.vertical, 10)
            .background(Capsule().fill(Color.black.opacity(0.8)))
            .frame(maxHeight: .infinity, alignment: .top).padding(.top, 70)
    }

    // ---------------- Ablauf ----------------

    private func touch() { lastTouch = Date() }

    private func seekByDoubleTap(_ forward: Bool) {
        guard !entry.live else { return }
        ctl.seekBy(forward ? 10_000 : -10_000)
        app.show(forward ? "⏩ +10 s" : "⏪ −10 s")
    }

    private func everySecond() {
        // Leiste nach 5 s ausblenden (nicht bei Pause, Fehler, Einstellungen)
        if overlay && !showSettings && ctl.playing && dragging == nil && Date().timeIntervalSince(lastTouch) > 5 { withAnimation { overlay = false } }
        if let s = sleepUntil, s < Date() { sleepUntil = nil; savePosition(); close(); return }
        if Int(Date().timeIntervalSince1970) % 15 == 0 { savePosition() }
        // Naechste Folge: 40 s vor Schluss
        if entry.episode != nil, app.settings.autoNext, ctl.length > 0, nextIndex() != nil {
            let left = Int((ctl.length - ctl.time) / 1000)
            if left <= 40 && left > 0 && nextCountdown != -1 { nextCountdown = left }
        }
    }

    private func nextIndex() -> Int? {
        guard let i = app.playQueue.firstIndex(where: { $0.url == entry.url }), i + 1 < app.playQueue.count else { return nil }
        return i + 1
    }

    private func onEnded() {
        savePosition(ended: true)
        if entry.episode != nil, app.settings.autoNext, nextCountdown != -1, nextIndex() != nil { playNext() } else { close() }
    }

    private func playNext() {
        guard let i = nextIndex() else { return }
        savePosition()
        var n = app.playQueue[i]
        if let lib = app.library { n.startAt = lib.position(Library.positionKey(n)) }
        entry = n
        nextCountdown = nil
        ctl.play(n)
    }

    private func zap(_ d: Int) {
        let q = app.playQueue
        guard let i = q.firstIndex(where: { $0.url == entry.url }), q.count > 1 else { return }
        entry = q[(i + d + q.count) % q.count]
        ctl.play(entry)
        withAnimation { overlay = true } // beim Umschalten immer die Sender-Infos zeigen
        touch()
    }

    private func savePosition(ended: Bool = false) {
        guard let lib = app.library else { return }
        lib.save(entry, position: ended ? ctl.length : ctl.time, duration: ctl.length)
    }

    private func close() { app.playing = nil }
}

/** Senkrechter Regler (0..1) mit Symbol oben – Ziehen oder Antippen. */
struct VerticalLevel: View {
    let icon: String
    let value: Double
    let onChange: (Double) -> Void
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: icon).font(.title2).foregroundColor(.white)
            GeometryReader { g in
                ZStack(alignment: .bottom) {
                    Capsule().fill(Color.white.opacity(0.3)).frame(width: 8)
                    Capsule().fill(Color.white).frame(width: 8, height: g.size.height * CGFloat(min(max(value, 0), 1)))
                }
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0).onChanged { v in onChange(Double(1 - min(max(v.location.y / g.size.height, 0), 1))) })
            }
            .frame(width: 44, height: 170)
            Text("\(Int(value * 100))%").font(.caption2).foregroundColor(.white.opacity(0.8))
        }
    }
}

/** "An Gerät senden" (Portiva Link). */
struct SendToDeviceSheet: View {
    @EnvironmentObject var app: AppState
    let entry: PlayEntry
    let position: Double
    let duration: Double
    let onSent: (String) -> Void
    @State private var devices: [LinkDevice]?
    @State private var status: String?

    var body: some View {
        NavigationStack {
            List {
                if let d = devices {
                    if d.isEmpty { Text("Kein Gerät gefunden. Portiva muss auf dem anderen Gerät geöffnet sein (gleiches WLAN).").foregroundColor(.secondary) }
                    ForEach(d) { dev in
                        Button {
                            status = "Sende an \(dev.name) …"
                            Task {
                                let err = await app.link.sendPlay(dev, entry, position: position, duration: duration)
                                if let err { status = err } else { onSent(dev.name) }
                            }
                        } label: { Label(dev.name, systemImage: dev.platform.contains("tv") ? "tv" : dev.platform == "windows" ? "desktopcomputer" : "iphone") }
                    }
                } else { HStack { ProgressView(); Text("Suche Portiva-Geräte im Heimnetz …") } }
                if let status { Text(status).foregroundColor(.accentColor) }
            }
            .navigationTitle("An Gerät senden")
            .toolbar { Button("Neu suchen") { devices = nil; Task { devices = await LinkService.discover() } } }
            .task { devices = await LinkService.discover() }
        }
        .presentationDetents([.medium, .large])
    }
}
