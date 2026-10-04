import SwiftUI
import CoreImage.CIFilterBuiltins
import AVFoundation

// Farben wie Android (Portiva-Logo: Hellblau -> Blau -> Navy auf Nachtblau)
enum Brand {
    static let background = Color(red: 0.024, green: 0.043, blue: 0.086)
    static let surface = Color(red: 0.055, green: 0.09, blue: 0.15)
    static let surfaceHigh = Color(red: 0.09, green: 0.14, blue: 0.22)
    static let cyan = Color(red: 0.37, green: 0.77, blue: 0.95)
    static let accent = Color(red: 0.23, green: 0.61, blue: 0.86)
    static let navy = Color(red: 0.11, green: 0.31, blue: 0.54)
}

/** Portiva-"P" oben links: ein Tipp fuehrt von jeder Seite zur Startseite. */
struct HomeLogoButton: View {
    @Environment(\.goHome) private var goHome
    var body: some View {
        Button { goHome() } label: {
            Image("Logo").resizable().scaledToFit().frame(width: 32, height: 32).clipShape(RoundedRectangle(cornerRadius: 7))
        }
        .accessibilityLabel("Startseite")
    }
}

private struct GoHomeKey: EnvironmentKey { static let defaultValue: () -> Void = {} }
extension EnvironmentValues {
    var goHome: () -> Void {
        get { self[GoHomeKey.self] }
        set { self[GoHomeKey.self] = newValue }
    }
}

/** Standard-Kopfzeile: Zurueck (vom System) + Portiva-P. */
extension View {
    func portivaToolbar(_ title: String) -> some View {
        self.navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .navigationBarTrailing) { HomeLogoButton() } }
    }
}

/** Poster/Logo aus dem Netz. */
struct NetImage: View {
    let url: String?
    var contentMode: ContentMode = .fill
    var body: some View {
        if let u = url, let url = URL(string: u) {
            AsyncImage(url: url) { phase in
                if let img = phase.image { img.resizable().aspectRatio(contentMode: contentMode) }
                else { Brand.surfaceHigh }
            }
        } else { Brand.surfaceHigh }
    }
}

/** Karte fuer Film/Serie (Poster) bzw. Sender (Logo). */
struct ItemCard: View {
    @EnvironmentObject var app: AppState
    let item: ContentItem
    var progress: Double? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .topLeading) {
                NetImage(url: item.logo, contentMode: item.type == .LIVE ? .fit : .fill)
                    .frame(maxWidth: .infinity)
                    .aspectRatio(item.type == .LIVE ? 16 / 10 : 2 / 3, contentMode: .fit)
                    .background(Brand.surfaceHigh)
                    .clipped()
                if app.library?.isFavorite(item) == true {
                    Image(systemName: "heart.fill").foregroundColor(.pink).padding(6).background(Circle().fill(.black.opacity(0.6))).padding(6)
                }
                if let r = item.rating, r > 0 {
                    Text("★ \(String(format: "%.1f", r))").font(.caption2.bold()).foregroundColor(.yellow)
                        .padding(.horizontal, 6).padding(.vertical, 2).background(Capsule().fill(.black.opacity(0.7)))
                        .frame(maxWidth: .infinity, alignment: .trailing).padding(6)
                }
            }
            .overlay(alignment: .bottomLeading) {
                if let p = progress { GeometryReader { g in Rectangle().fill(Brand.cyan).frame(width: g.size.width * p, height: 4).frame(maxHeight: .infinity, alignment: .bottom) } }
            }
            .clipShape(RoundedRectangle(cornerRadius: 10))
            Text((item.number.map { "\($0)  " } ?? "") + item.name).font(.caption).foregroundColor(.white).lineLimit(2)
        }
    }
}

/** QR-Code als Bild. */
struct QRCodeImage: View {
    let text: String
    var body: some View {
        let f = CIFilter.qrCodeGenerator()
        f.message = Data(text.utf8)
        f.correctionLevel = "M"
        let ctx = CIContext()
        if let out = f.outputImage?.transformed(by: CGAffineTransform(scaleX: 10, y: 10)), let cg = ctx.createCGImage(out, from: out.extent) {
            return AnyView(Image(decorative: cg, scale: 1).interpolation(.none).resizable().scaledToFit().padding(14).background(Color.white).cornerRadius(12))
        }
        return AnyView(Color.white)
    }
}

/** QR-Scanner mit der Kamera. */
struct QRScannerView: UIViewControllerRepresentable {
    let onCode: (String) -> Void

    func makeUIViewController(context: Context) -> ScannerVC {
        let vc = ScannerVC()
        vc.onCode = onCode
        return vc
    }
    func updateUIViewController(_ vc: ScannerVC, context: Context) {}

    final class ScannerVC: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
        var onCode: ((String) -> Void)?
        private let session = AVCaptureSession()
        private var done = false

        override func viewDidLoad() {
            super.viewDidLoad()
            view.backgroundColor = .black
            guard let dev = AVCaptureDevice.default(for: .video), let input = try? AVCaptureDeviceInput(device: dev), session.canAddInput(input) else {
                let l = UILabel(); l.text = "Kamera nicht verfügbar"; l.textColor = .white; l.frame = view.bounds; l.textAlignment = .center
                view.addSubview(l); return
            }
            session.addInput(input)
            let out = AVCaptureMetadataOutput()
            if session.canAddOutput(out) {
                session.addOutput(out)
                out.setMetadataObjectsDelegate(self, queue: .main)
                out.metadataObjectTypes = [.qr]
            }
            let preview = AVCaptureVideoPreviewLayer(session: session)
            preview.videoGravity = .resizeAspectFill
            preview.frame = view.layer.bounds
            view.layer.addSublayer(preview)
            DispatchQueue.global(qos: .userInitiated).async { self.session.startRunning() }
        }

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            view.layer.sublayers?.compactMap { $0 as? AVCaptureVideoPreviewLayer }.forEach { $0.frame = view.layer.bounds }
        }

        override func viewWillDisappear(_ animated: Bool) {
            super.viewWillDisappear(animated)
            if session.isRunning { session.stopRunning() }
        }

        func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput objects: [AVMetadataObject], from connection: AVCaptureConnection) {
            guard !done, let o = objects.first as? AVMetadataMachineReadableCodeObject, let s = o.stringValue else { return }
            done = true
            onCode?(s)
        }
    }
}
