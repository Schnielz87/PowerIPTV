import Foundation
import MobileVLCKit
import SwiftUI
import UIKit

/** Eine waehlbare Spur (Video, Audio, Untertitel). id < 0 = aus. */
struct TrackOption: Identifiable, Hashable {
    let id: Int32
    let label: String
    let selected: Bool
}

/** Bildformate wie Android/Windows. */
enum AspectMode: String, CaseIterable, Identifiable {
    case FIT, FILL, STRETCH, R16_9, R4_3, R21_9
    var id: String { rawValue }
    var label: String {
        switch self {
        case .FIT: return "Original"
        case .FILL: return "Zoom (ausfüllen)"
        case .STRETCH: return "Strecken"
        case .R16_9: return "16:9"
        case .R4_3: return "4:3"
        case .R21_9: return "21:9"
        }
    }
}

/**
 * VLC-Player (MobileVLCKit) – gleicher Player wie Android und Windows.
 * Grosser Puffer, Neuverbinden, Haenger-Waechter, gesammeltes Spulen.
 */
@MainActor
final class PlayerController: NSObject, ObservableObject, VLCMediaPlayerDelegate {
    let player = VLCMediaPlayer()
    @Published var playing = false
    @Published var buffering = true
    @Published var time: Double = 0          // ms
    @Published var length: Double = 0        // ms
    @Published var error: String?
    @Published var rate: Float = 1
    @Published var aspect: AspectMode = .FIT
    @Published var volume: Float = 0.8       // 0..1 (VLC 0..125)
    @Published var tracksTick = 0
    @Published var ended = false

    private(set) var entry: PlayEntry?
    private var pendingSeek: Double?
    private var seekWork: DispatchWorkItem?
    private var retries = 0
    private var lastTick: Double = -1
    private var stallSince: Date?
    private var watchdog: Timer?
    private var userPaused = false
    private var startAt: Double = 0
    var subtitleScale = 100

    override init() {
        super.init()
        player.delegate = self
    }

    // ---------------- Abspielen ----------------

    func play(_ e: PlayEntry, startAt: Double? = nil) {
        entry = e
        error = nil; ended = false; buffering = true; playing = false
        time = startAt ?? e.startAt; length = 0
        self.startAt = startAt ?? e.startAt
        pendingSeek = nil; userPaused = false; lastTick = -1; stallSince = nil
        guard let url = URL(string: e.url) else { error = "Ungültige Stream-Adresse"; return }
        let m = VLCMedia(url: url)
        if e.live {
            m.addOption(":network-caching=4000")
            m.addOption(":live-caching=4000")
        } else {
            m.addOption(":network-caching=1500") // kuerzer vorpuffern -> Filme starten schneller
            m.addOption(":input-fast-seek")
            if self.startAt > 1000 { m.addOption(":start-time=\(Int(self.startAt / 1000))") }
        }
        m.addOption(":http-reconnect")
        m.addOption(":http-user-agent=Portiva/1.1 (iOS)")
        m.addOption(":sub-text-scale=\(subtitleScale)")
        player.media = m
        player.play()
        player.rate = rate
        applyVolume()
        startWatchdog()
    }

    func stop() {
        watchdog?.invalidate()
        seekWork?.cancel()
        player.stop()
        playing = false
    }

    func togglePause() {
        if error != nil, let e = entry { retries = 0; play(e, startAt: time); return }
        if player.isPlaying { userPaused = true; player.pause(); playing = false } else { userPaused = false; player.play(); playing = true }
    }

    // ---------------- Spulen (gesammelt, zum Schluesselbild) ----------------

    func seekBy(_ deltaMs: Double) {
        guard let e = entry, !e.live, length > 0 else { return }
        seekTo((pendingSeek ?? time) + deltaMs)
    }

    func seekTo(_ target: Double) {
        guard length > 0 else { return }
        let t = min(max(0, target), length - 2000)
        pendingSeek = t
        time = t
        seekWork?.cancel()
        let w = DispatchWorkItem { [weak self] in
            guard let self, let to = self.pendingSeek else { return }
            self.player.time = VLCTime(int: Int32(to))
            self.pendingSeek = nil
            self.stallSince = nil
        }
        seekWork = w
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5, execute: w)
    }

    // ---------------- VLC-Ereignisse ----------------

    nonisolated func mediaPlayerStateChanged(_ aNotification: Notification) {
        Task { @MainActor in self.stateChanged() }
    }

    nonisolated func mediaPlayerTimeChanged(_ aNotification: Notification) {
        Task { @MainActor in
            if self.pendingSeek == nil { self.time = Double(self.player.time.intValue) }
            let len = Double(self.player.media?.length.intValue ?? 0)
            if len > 0 && !(self.entry?.live ?? true) { self.length = len }
            self.buffering = false
            self.retries = 0
        }
    }

    private func stateChanged() {
        switch player.state {
        case .buffering, .opening: buffering = true
        case .playing: playing = true; buffering = false; error = nil; tracksTick += 1
        case .paused: playing = false
        case .esAdded: tracksTick += 1
        case .ended:
            playing = false
            if entry?.live == true { reconnect() } else { ended = true }
        case .error: reconnect()
        case .stopped: playing = false
        @unknown default: break
        }
    }

    /** Verbindung weg -> automatisch neu verbinden (Live: neu starten, Filme: gleiche Stelle). */
    private func reconnect() {
        guard let e = entry else { return }
        if retries < 5 {
            retries += 1
            error = nil
            let pos = e.live ? 0 : time
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { [weak self] in
                guard let self, self.entry?.url == e.url else { return }
                let r = self.retries
                self.play(e, startAt: pos)
                self.retries = r
            }
        } else {
            error = "Keine Verbindung zum Stream (Anbieter erreichbar? Stream-Limit?)"
            buffering = false
        }
    }

    /** Haenger-Waechter: steht das Bild >10 s ohne Fehler, an gleicher Stelle neu laden. */
    private func startWatchdog() {
        watchdog?.invalidate()
        watchdog = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self, let e = self.entry, !self.userPaused, self.error == nil, self.pendingSeek == nil else { self?.stallSince = nil; return }
                let t = Double(self.player.time.intValue)
                if t != self.lastTick && t > 0 { self.lastTick = t; self.stallSince = nil; return }
                if self.stallSince == nil { self.stallSince = Date(); return }
                if Date().timeIntervalSince(self.stallSince!) < (e.live ? 12 : 10) { return }
                self.stallSince = nil
                self.play(e, startAt: e.live ? 0 : self.time)
            }
        }
    }

    // ---------------- Spuren ----------------

    private func options(_ names: [Any]?, _ indexes: [Any]?, current: Int32, kind: String) -> [TrackOption] {
        let n = (names ?? []).map { "\($0)" }
        let ids = (indexes ?? []).compactMap { ($0 as? NSNumber)?.int32Value }
        var out: [TrackOption] = []
        for (i, id) in ids.enumerated() {
            let label = i < n.count ? n[i] : "\(kind) \(i + 1)"
            if id < 0 { out.insert(TrackOption(id: -1, label: "Aus", selected: current < 0), at: 0) }
            else { out.append(TrackOption(id: id, label: label.replacingOccurrences(of: "Track", with: "Spur"), selected: id == current)) }
        }
        if !out.contains(where: { $0.id < 0 }) && !out.isEmpty { out.insert(TrackOption(id: -1, label: "Aus", selected: current < 0), at: 0) }
        return out
    }

    var videoTracks: [TrackOption] { options(player.videoTrackNames, player.videoTrackIndexes, current: player.currentVideoTrackIndex, kind: "Video") }
    var audioTracks: [TrackOption] { options(player.audioTrackNames, player.audioTrackIndexes, current: player.currentAudioTrackIndex, kind: "Ton") }
    var subtitleTracks: [TrackOption] { options(player.videoSubTitlesNames, player.videoSubTitlesIndexes, current: player.currentVideoSubTitleIndex, kind: "Untertitel") }

    func selectVideo(_ id: Int32) { player.currentVideoTrackIndex = id; tracksTick += 1 }
    func selectAudio(_ id: Int32) { player.currentAudioTrackIndex = id; tracksTick += 1 }
    func selectSubtitle(_ id: Int32) { player.currentVideoSubTitleIndex = id; tracksTick += 1 }

    private var lastSubtitle: Int32 = -1
    /** Untertitel-Knopf: nur ein/aus. */
    func toggleSubtitles() -> String {
        let cur = player.currentVideoSubTitleIndex
        if cur >= 0 { lastSubtitle = cur; selectSubtitle(-1); return "Untertitel aus" }
        let t = subtitleTracks.filter { $0.id >= 0 }
        guard !t.isEmpty else { return "Dieser Stream hat keine Untertitel" }
        let pick = t.first { $0.id == lastSubtitle } ?? t.first { $0.label.localizedCaseInsensitiveContains("deutsch") || $0.label.localizedCaseInsensitiveContains("german") } ?? t[0]
        selectSubtitle(pick.id)
        return "Untertitel: \(pick.label)"
    }

    var subtitlesOn: Bool { _ = tracksTick; return player.currentVideoSubTitleIndex >= 0 }

    // ---------------- Bild, Tempo, Lautstaerke ----------------

    func setAspect(_ a: AspectMode, viewSize: CGSize) {
        aspect = a
        func cstr(_ s: String?) -> UnsafeMutablePointer<CChar>? { s.flatMap { strdup($0) } }
        let screen = viewSize.width > 0 && viewSize.height > 0 ? "\(Int(viewSize.width)):\(Int(viewSize.height))" : "16:9"
        switch a {
        case .FIT: player.videoAspectRatio = nil; player.videoCropGeometry = nil
        case .FILL: player.videoAspectRatio = nil; player.videoCropGeometry = cstr(screen)
        case .STRETCH: player.videoCropGeometry = nil; player.videoAspectRatio = cstr(screen)
        case .R16_9: player.videoCropGeometry = nil; player.videoAspectRatio = cstr("16:9")
        case .R4_3: player.videoCropGeometry = nil; player.videoAspectRatio = cstr("4:3")
        case .R21_9: player.videoCropGeometry = nil; player.videoAspectRatio = cstr("21:9")
        }
    }

    func setRate(_ r: Float) { rate = r; player.rate = r }

    func setVolume(_ v: Float) { volume = min(max(v, 0), 1); applyVolume() }
    private func applyVolume() { (player.audio as VLCAudio?)?.volume = Int32(volume * 125) }
}

/** Zeichenflaeche fuer VLC. */
struct VLCVideoView: UIViewRepresentable {
    let controller: PlayerController
    func makeUIView(context: Context) -> UIView {
        let v = UIView()
        v.backgroundColor = .black
        controller.player.drawable = v
        return v
    }
    func updateUIView(_ uiView: UIView, context: Context) {}
}
