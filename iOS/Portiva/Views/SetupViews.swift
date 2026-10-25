import SwiftUI
import UIKit

// Einrichtungs-Code (wie Android/Windows): Zugang verschluesselt weitergeben – PIN, Geraetebindung, Ablaufdatum.

private let validityOptions: [(String, Int64)] = [("24 Std.", 86_400_000), ("7 Tage", 7 * 86_400_000), ("30 Tage", 30 * 86_400_000), ("Unbegrenzt", 0)]

private func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }

/** Code fuer einen Zugang erstellen und teilen. */
struct CreateSetupSheet: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    let profile: Profile
    @State private var pin = ""
    @State private var device = ""
    @State private var validity: Int64 = 7 * 86_400_000
    @State private var busy = false
    @State private var code: String?

    private var pinOk: Bool { pin.isEmpty || pin.count >= 4 }
    private var deviceOk: Bool { device.isEmpty || SetupCode.normalizeId(device).count == 8 }

    var body: some View {
        NavigationStack {
            Form {
                if let code {
                    Section {
                        QRCodeImage(text: code).frame(maxWidth: 300).frame(maxWidth: .infinity)
                        Text(pin.isEmpty ? "Ohne PIN – jeder mit diesem Code kann den Zugang einrichten." : "PIN: \(pin) – die PIN NICHT im selben Chat schicken (z.B. anrufen oder SMS).")
                            .font(.footnote)
                        if !device.isEmpty { Text("Nur für Gerät \(device.uppercased()).").font(.footnote) }
                        ShareLink(item: "Dein Portiva-Einrichtungs-Code:\n\n\(code)\n\nSo geht's: Portiva öffnen → Benutzer wechseln → Neuer Zugang → „Einrichtungs-Code“ → Code einfügen. Die PIN bekommst du getrennt.") {
                            Label("Teilen", systemImage: "square.and.arrow.up")
                        }
                        Button { UIPasteboard.general.string = code; app.show("Code kopiert") } label: { Label("Kopieren", systemImage: "doc.on.doc") }
                    }
                } else {
                    Section(footer: Text("Ein verschlüsseltes Paket mit diesem Zugang. Der Empfänger scannt oder fügt es ein – fertig, ohne Abtippen.")) {
                        SecureField("PIN (4–8 Ziffern, empfohlen)", text: $pin).keyboardType(.numberPad)
                            .onChange(of: pin) { v in pin = String(v.filter(\.isNumber).prefix(8)) }
                        TextField("Gerätenummer des Empfängers (optional)", text: $device).textInputAutocapitalization(.characters).autocorrectionDisabled()
                    }
                    Section("Gültig") {
                        Picker("Gültig", selection: $validity) {
                            ForEach(0..<validityOptions.count, id: \.self) { i in Text(validityOptions[i].0).tag(validityOptions[i].1) }
                        }.pickerStyle(.segmented)
                    }
                    if !pinOk { Text("Die PIN braucht mindestens 4 Ziffern.").foregroundColor(.red) }
                    if !deviceOk { Text("Die Gerätenummer hat 8 Zeichen (z.B. 7KQ4-M2XD).").foregroundColor(.red) }
                    Button {
                        busy = true
                        let payload = SetupPayload(account: LinkAccount(profile), exp: validity > 0 ? nowMs() + validity : 0, created: nowMs(), from: app.link.deviceName)
                        let p = pin, d = device
                        Task.detached {
                            let c = SetupCode.create(payload, pin: p, deviceId: d)
                            await MainActor.run { code = c; busy = false; if c == nil { app.show("Code konnte nicht erstellt werden") } }
                        }
                    } label: { if busy { ProgressView() } else { Text("Code erstellen") } }
                    .disabled(busy || !pinOk || !deviceOk)
                    Text("VPN mitgeben geht in der Android- und Windows-App.").font(.footnote).foregroundColor(.secondary)
                }
            }
            .navigationTitle("Einrichtungs-Code").navigationBarTitleDisplayMode(.inline)
            .toolbar { Button(code == nil ? "Abbrechen" : "Fertig") { dismiss() } }
        }
    }
}

/** Code einloesen: hier einrichten oder an Receiver/TV weitergeben. */
struct RedeemSetupSheet: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    @Environment(\.goHome) private var goHome
    @State var code: String
    @State private var pin = ""
    @State private var busy = false
    @State private var error: String?
    @State private var done: String?
    @State private var scanning = false
    @State private var scanningTv = false
    @State private var tvIp = ""
    @State private var tvCode = ""
    @State private var tvDevice = ""

    private var info: SetupCode.Info? { SetupCode.info(code) }
    private var pinOk: Bool { info != nil && (!(info?.needsPin ?? false) || pin.count >= 4) }

    @MainActor private func redeemHere() {
        busy = true; error = nil
        let c = code, p = pin, d = app.deviceId
        Task.detached {
            let r = SetupCode.open(c, pin: p, deviceId: d)
            await MainActor.run {
                busy = false
                switch r {
                case .failure(let e): error = e.text
                case .success(let s):
                    let prof = app.importAccount(s.account)
                    app.activate(prof)
                    done = "✓ Zugang „\(prof.name)“ ist eingerichtet und aktiv." + (s.vpn != nil ? "\nDas mitgeschickte VPN gibt es nur in der Android-/Windows-App – am iPhone ein VPN in den Einstellungen nutzen." : "")
                }
            }
        }
    }

    @MainActor private func redeemTo(_ target: LinkCodes.PairTarget) {
        busy = true; error = nil
        let c = code, p = pin, bound = info?.boundToDevice ?? false, manual = tvDevice
        Task {
            var dev = target.deviceId
            if dev.isEmpty && target.port > 0 { dev = await LinkService.deviceId(host: target.host, port: target.port) }
            if dev.isEmpty { dev = manual }
            if bound && dev.isEmpty {
                error = "Dieser Code ist an ein Gerät gebunden – bitte die Gerätenummer des Receivers/TV eintragen."; busy = false; return
            }
            let d = dev
            let r = await Task.detached { SetupCode.open(c, pin: p, deviceId: d) }.value
            switch r {
            case .failure(let e): error = e.text; busy = false
            case .success(let s):
                if target.port == 0 {
                    app.link.onOfferTaken = { a in done = "✓ „\(a.name)“ wurde auf den Fernseher übertragen"; busy = false }
                }
                let err = await app.link.sendSetup(target, s)
                if target.port > 0 { busy = false; if let err { error = err } else { done = "✓ „\(s.account.name)“ wurde auf das Gerät übertragen." } }
            }
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                if let done {
                    Text(done).foregroundColor(Brand.cyan)
                    Button("Fertig") { dismiss(); goHome() }
                } else {
                    Section(footer: Text("Gerätenummer dieses iPhones: \(app.deviceId)").font(.footnote.monospaced())) {
                        TextField("Einrichtungs-Code einfügen", text: $code, axis: .vertical).lineLimit(1...3).autocorrectionDisabled().textInputAutocapitalization(.never)
                        Button { if let s = UIPasteboard.general.string { code = s.trimmingCharacters(in: .whitespacesAndNewlines) } } label: { Label("Einfügen", systemImage: "doc.on.clipboard") }
                        Button { scanning = true } label: { Label("Code scannen", systemImage: "qrcode.viewfinder") }
                        if !code.isEmpty && info == nil { Text("Kein gültiger Einrichtungs-Code (vollständig kopiert?).").foregroundColor(.red) }
                        if info?.needsPin == true {
                            SecureField("PIN", text: $pin).keyboardType(.numberPad)
                        }
                    }
                    if info != nil {
                        Section {
                            Button("Auf diesem iPhone einrichten", action: redeemHere).disabled(busy || !pinOk)
                        }
                        Section(header: Text("Auf Receiver / TV einrichten"),
                                footer: Text("Am Receiver/TV „Zugang vom Handy empfangen“ öffnen, dann den QR-Code scannen oder IP-Adresse und Code eintragen.")) {
                            Button { scanningTv = true } label: { Label("QR am Receiver/TV scannen", systemImage: "tv") }.disabled(busy || !pinOk)
                            TextField("IP-Adresse", text: $tvIp).keyboardType(.decimalPad)
                            TextField("Code (6 Ziffern)", text: $tvCode).keyboardType(.numberPad)
                            if info?.boundToDevice == true { TextField("Gerätenummer (falls nicht automatisch)", text: $tvDevice).textInputAutocapitalization(.characters) }
                            Button("Übertragen") {
                                let parts = tvIp.trimmingCharacters(in: .whitespaces).split(separator: ":")
                                let host = parts.first.map(String.init) ?? ""
                                let port = parts.count > 1 ? (Int(parts[1]) ?? Int(linkPort)) : Int(linkPort)
                                redeemTo(LinkCodes.PairTarget(host: host, port: port, code: tvCode.trimmingCharacters(in: .whitespaces)))
                            }.disabled(busy || !pinOk || tvIp.isEmpty || tvCode.count != 6)
                        }
                    }
                    if busy { HStack { ProgressView(); Text("Bitte warten …") } }
                    if let error { Text(error).foregroundColor(.red) }
                }
            }
            .navigationTitle("Einrichtungs-Code").navigationBarTitleDisplayMode(.inline)
            .toolbar { Button(done == nil ? "Abbrechen" : "Schließen") { dismiss() } }
            .sheet(isPresented: $scanning) {
                ScanSheet { text in
                    scanning = false
                    if SetupCode.isSetupCode(text) { code = text.trimmingCharacters(in: .whitespacesAndNewlines) } else { error = "Das ist kein Einrichtungs-Code." }
                }
            }
            .sheet(isPresented: $scanningTv) {
                ScanSheet { text in
                    scanningTv = false
                    if let t = LinkCodes.parsePair(text) { redeemTo(t) } else { error = "Das ist kein Empfangs-Code. Am Receiver/TV „Zugang vom Handy empfangen“ öffnen." }
                }
            }
        }
        .onDisappear { app.link.onOfferTaken = nil }
    }
}
