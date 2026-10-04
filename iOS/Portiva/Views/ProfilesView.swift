import SwiftUI

/** "Wer schaut?" – Zugaenge mit QR, Stift und Muelleimer (wie Android). */
struct ProfilesView: View {
    @EnvironmentObject var app: AppState
    @Environment(\.goHome) private var goHome
    let first: Bool
    @State private var qrFor: Profile?
    @State private var edit: Profile?
    @State private var adding = false
    @State private var showChooser = false
    @State private var scanning = false
    @State private var receiving = false
    @State private var deleteAsk: Profile?

    var body: some View {
        List {
            if app.profiles.isEmpty {
                VStack(spacing: 10) {
                    Image("Logo").resizable().scaledToFit().frame(width: 90, height: 90)
                    Text("Willkommen bei Portiva – PowerIPTV").font(.title3.bold())
                    Text("Füge deinen ersten Zugang hinzu – per Xtream Codes, M3U-Link oder ganz bequem per QR-Code von deinem anderen Gerät.")
                        .font(.subheadline).foregroundColor(.secondary).multilineTextAlignment(.center)
                }.frame(maxWidth: .infinity).listRowBackground(Color.clear)
            }
            ForEach(app.profiles) { p in
                let active = p.id == app.profile?.id
                HStack(spacing: 12) {
                    Image(systemName: "person.fill").foregroundColor(active ? .black : Brand.accent)
                        .frame(width: 40, height: 40).background(Circle().fill(active ? Brand.cyan : Brand.accent.opacity(0.2)))
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 8) {
                            Text(p.name).font(.headline).foregroundColor(active ? Brand.cyan : .white).lineLimit(1)
                            if active { Text("AKTIV").font(.caption2.bold()).foregroundColor(.black).padding(.horizontal, 8).padding(.vertical, 2).background(Capsule().fill(Brand.cyan)) }
                        }
                        Text(p.type == .XTREAM ? "Xtream Codes · \(p.username)" : "M3U-Link").font(.caption).foregroundColor(.secondary)
                    }
                    Spacer()
                    Button { qrFor = p } label: { Image(systemName: "qrcode") }.buttonStyle(.borderless)
                    Button { edit = p } label: { Image(systemName: "pencil") }.buttonStyle(.borderless)
                    Button { deleteAsk = p } label: { Image(systemName: "trash") }.buttonStyle(.borderless)
                }
                .contentShape(Rectangle())
                .onTapGesture { app.activate(p); goHome() }
                .listRowBackground(active ? Brand.accent.opacity(0.22) : Brand.surface)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Brand.background.ignoresSafeArea())
        .navigationTitle("Wer schaut?")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { showChooser = true } label: { Label("Neuer Zugang", systemImage: "plus") }
            }
        }
        .confirmationDialog("Neuer Zugang", isPresented: $showChooser, titleVisibility: .visible) {
            Button("Manuell eingeben") { adding = true }
            Button("QR-Code scannen") { scanning = true }
            Button("Vom anderen Gerät empfangen") { receiving = true }
        } message: { Text("Übertragen wird immer nur der eine Zugang, den du am anderen Gerät auswählst.") }
        .sheet(item: $qrFor) { p in AccountQRSheet(profile: p).environmentObject(app) }
        .sheet(item: $edit) { p in NavigationStack { AddProfileView(existing: p) }.environmentObject(app) }
        .sheet(isPresented: $adding) { NavigationStack { AddProfileView(existing: nil) }.environmentObject(app) }
        .sheet(isPresented: $receiving) { ReceiveAccountSheet().environmentObject(app) }
        .sheet(isPresented: $scanning) {
            ScanSheet { text in
                scanning = false
                if let a = LinkCodes.parseAccount(text) {
                    let p = app.importAccount(a)
                    app.activate(p); goHome()
                    app.show("Zugang „\(p.name)“ übernommen")
                } else if LinkCodes.parsePair(text) != nil {
                    app.show("Das ist ein Empfangs-Code – zum Senden beim Zugang auf das QR-Symbol tippen")
                } else { app.show("Kein Portiva-QR-Code erkannt") }
            }
        }
        .alert("Zugang löschen?", isPresented: Binding(get: { deleteAsk != nil }, set: { if !$0 { deleteAsk = nil } })) {
            Button("Löschen", role: .destructive) { if let p = deleteAsk { app.delete(p) }; deleteAsk = nil }
            Button("Abbrechen", role: .cancel) { deleteAsk = nil }
        } message: { Text("„\(deleteAsk?.name ?? "")“ wird mit Favoriten entfernt.") }
    }
}

/** Kamera-Scanner als Blatt. */
struct ScanSheet: View {
    @Environment(\.dismiss) private var dismiss
    let onCode: (String) -> Void
    var body: some View {
        NavigationStack {
            QRScannerView(onCode: onCode).ignoresSafeArea()
                .navigationTitle("Portiva-QR-Code scannen").navigationBarTitleDisplayMode(.inline)
                .toolbar { Button("Abbrechen") { dismiss() } }
        }
    }
}

/** Grosser QR-Code eines Zugangs + "An TV-Stick / Fernseher senden". */
struct AccountQRSheet: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    let profile: Profile
    @State private var scanning = false
    @State private var status: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    QRCodeImage(text: LinkCodes.accountQr(profile)).frame(maxWidth: 300)
                    Text("Auf dem anderen Handy/Tablet: Benutzer wechseln → Neuer Zugang → „QR-Code scannen“. Nur dir selbst zeigen – der Code enthält die Zugangsdaten.")
                        .font(.footnote).foregroundColor(.secondary).multilineTextAlignment(.center)
                    Button { scanning = true } label: { Label("An TV-Stick / Fernseher senden", systemImage: "tv") .frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent)
                    Text("Am TV: Benutzer wechseln → Neuer Zugang → „Vom anderen Gerät empfangen“ und den dort gezeigten Code hier scannen.")
                        .font(.footnote).foregroundColor(.secondary).multilineTextAlignment(.center)
                    if let status { Text(status).foregroundColor(status.hasPrefix("✓") ? Brand.cyan : .white) }
                }.padding(20)
            }
            .navigationTitle(profile.name).navigationBarTitleDisplayMode(.inline)
            .toolbar { Button { dismiss() } label: { Image(systemName: "xmark.circle.fill") } }
            .sheet(isPresented: $scanning) {
                ScanSheet { text in
                    scanning = false
                    guard let target = LinkCodes.parsePair(text) else { status = "Das ist kein Empfangs-Code."; return }
                    status = "Wird übertragen …"
                    if target.port == 0 { app.link.onOfferTaken = { a in status = "✓ „\(a.name)“ wurde auf den Fernseher übertragen" } }
                    Task {
                        let err = await app.link.sendAccount(target, profile)
                        if target.port > 0 { status = err ?? "✓ „\(profile.name)“ wurde übertragen" }
                        else if err == nil { status = "Der Fernseher holt den Zugang jetzt ab … Portiva hier geöffnet lassen." }
                    }
                }
            }
        }
        .presentationDetents([.large])
    }
}

/** Dieses Geraet zeigt einen Empfangs-Code; das andere Handy scannt ihn und schickt den Zugang. */
struct ReceiveAccountSheet: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    @State private var code = LinkCodes.newCode()
    private let ip = LinkCodes.localIPv4()

    var body: some View {
        NavigationStack {
            VStack(spacing: 18) {
                if let ip, app.link.port > 0 {
                    QRCodeImage(text: LinkCodes.pairQr(ip: ip, port: app.link.port, code: code)).frame(maxWidth: 300)
                    Text("Am anderen Gerät in Portiva: Benutzer wechseln → beim gewünschten Zugang auf das QR-Symbol tippen → „An TV-Stick / Fernseher senden“ → diesen Code scannen.")
                        .font(.footnote).foregroundColor(.secondary).multilineTextAlignment(.center)
                    HStack { ProgressView(); Text("Warte auf Zugang …  Code \(code)").bold() }
                } else {
                    Text("Kein WLAN verbunden – beide Geräte müssen im selben Heimnetz sein.").multilineTextAlignment(.center)
                }
            }
            .padding(20)
            .navigationTitle("Zugang empfangen").navigationBarTitleDisplayMode(.inline)
            .toolbar { Button { dismiss() } label: { Image(systemName: "xmark.circle.fill") } }
        }
        .onAppear { app.link.pairCode = code }
        .onDisappear { if app.link.pairCode == code { app.link.pairCode = nil } }
        .onReceive(app.link.$received) { if $0 != nil { dismiss() } }
    }
}

/** Zugang anlegen/bearbeiten – Speichern prueft die Verbindung. */
struct AddProfileView: View {
    @EnvironmentObject var app: AppState
    @Environment(\.dismiss) private var dismiss
    let existing: Profile?
    @State private var type: ProfileType = .XTREAM
    @State private var name = ""
    @State private var server = ""
    @State private var user = ""
    @State private var pass = ""
    @State private var m3u = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        Form {
            Picker("Art", selection: $type) {
                Text("Xtream Codes").tag(ProfileType.XTREAM)
                Text("M3U-Link").tag(ProfileType.M3U_URL)
            }.pickerStyle(.segmented)
            TextField("Name (beliebig)", text: $name)
            if type == .XTREAM {
                TextField("Server-URL (z.B. http://server.com:8080)", text: $server).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                TextField("Benutzername", text: $user).textInputAutocapitalization(.never).autocorrectionDisabled()
                SecureField("Passwort", text: $pass)
            } else {
                TextField("M3U-Link", text: $m3u).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
            }
            if let error { Text(error).foregroundColor(.red) }
            Button { Task { await save() } } label: {
                HStack { if busy { ProgressView() }; Text("Prüfen & speichern") }
            }.disabled(busy)
        }
        .navigationTitle(existing == nil ? "Zugang hinzufügen" : "Zugang bearbeiten")
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Abbrechen") { dismiss() } } }
        .onAppear {
            if let e = existing { type = e.type; name = e.name; server = e.serverUrl; user = e.username; pass = e.password; m3u = e.m3uUrl }
        }
    }

    private func save() async {
        error = nil
        if type == .XTREAM && (server.isEmpty || user.isEmpty || pass.isEmpty) { error = "Bitte Server, Benutzername und Passwort eingeben"; return }
        if type == .M3U_URL && !m3u.lowercased().hasPrefix("http") { error = "Bitte einen gültigen M3U-Link eingeben"; return }
        let p = Profile(id: existing?.id ?? UUID().uuidString,
                        name: name.isEmpty ? (type == .XTREAM ? (URL(string: normalizeServer(server))?.host ?? "Mein Zugang") : "M3U-Playlist") : name,
                        type: type, serverUrl: type == .XTREAM ? normalizeServer(server) : "", username: user.trimmingCharacters(in: .whitespaces),
                        password: pass, m3uUrl: m3u.trimmingCharacters(in: .whitespaces))
        busy = true
        defer { busy = false }
        do {
            _ = try await makeSource(p) { "ts" }.authenticate()
            app.save(p)
            app.activate(p)
            dismiss()
        } catch { self.error = "Verbindung fehlgeschlagen: \(error.localizedDescription)" }
    }
}
