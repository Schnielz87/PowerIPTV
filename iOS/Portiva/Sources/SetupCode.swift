import Foundation
import CryptoKit
import CommonCrypto

// Einrichtungs-Code – gleiches Format wie Android/Windows (SetupCode.kt):
//   "PORTIVA-SETUP1:" + base64url( Version(1) | Flags(1) | Salt(16) | Nonce(12) | Geheimtext + Tag(16) )
//   Flags: Bit 0 = PIN, Bit 1 = Geraetenummer. Schluessel = PBKDF2-HMAC-SHA256("PORTIVA-SETUP|PIN|GERAET", Salt, 100000, 32)
//   AES-256-GCM, Version+Flags als zusaetzliche Daten abgesichert.

struct SetupPayload: Codable {
    var v: Int = 1
    var account: LinkAccount
    var vpn: String? = nil
    var vpnRequired: Bool = false
    var exp: Int64 = 0
    var created: Int64 = 0
    var from: String = ""
}

enum SetupCode {
    static let prefix = "PORTIVA-SETUP1:"
    private static let iterations: UInt32 = 100_000
    private static let idChars = Array("23456789ABCDEFGHJKMNPQRSTUVWXYZ")

    static func newDeviceId() -> String {
        let s = String((0..<8).map { _ in idChars[Int.random(in: 0..<idChars.count)] })
        return String(s.prefix(4)) + "-" + String(s.suffix(4))
    }

    static func normalizeId(_ id: String?) -> String {
        String((id ?? "").uppercased().unicodeScalars.filter { ("A"..."Z").contains($0) || ("0"..."9").contains($0) }.map(Character.init))
    }

    static func isSetupCode(_ text: String?) -> Bool { text?.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix(prefix) == true }

    struct Info { let needsPin: Bool; let boundToDevice: Bool }

    static func info(_ text: String) -> Info? {
        guard let b = bytes(text) else { return nil }
        return Info(needsPin: b[1] & 1 != 0, boundToDevice: b[1] & 2 != 0)
    }

    static func create(_ payload: SetupPayload, pin: String, deviceId: String) -> String? {
        let p = pin.trimmingCharacters(in: .whitespaces)
        let d = normalizeId(deviceId)
        let flags: UInt8 = (p.isEmpty ? 0 : 1) | (d.isEmpty ? 0 : 2)
        var salt = [UInt8](repeating: 0, count: 16)
        guard SecRandomCopyBytes(kSecRandomDefault, 16, &salt) == errSecSuccess,
              let key = key(p, d, salt), let plain = try? JSONEncoder().encode(payload) else { return nil }
        let header: [UInt8] = [1, flags]
        guard let box = try? AES.GCM.seal(plain, using: key, nonce: AES.GCM.Nonce(), authenticating: Data(header)) else { return nil }
        let nonceData = box.nonce.withUnsafeBytes { Data($0) }
        let all = Data(header) + Data(salt) + nonceData + box.ciphertext + box.tag
        return prefix + all.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }

    /** Liefert den Inhalt oder eine Fehlermeldung. */
    static func open(_ text: String, pin: String, deviceId: String, now: Date = Date()) -> Result<SetupPayload, SetupError> {
        guard let b = bytes(text) else { return .failure(.message("Kein gültiger Einrichtungs-Code (unvollständig kopiert?).")) }
        let needsPin = b[1] & 1 != 0, bound = b[1] & 2 != 0
        let p = pin.trimmingCharacters(in: .whitespaces)
        if needsPin && p.isEmpty { return .failure(.message("Für diesen Code wird die PIN benötigt.")) }
        let d = bound ? normalizeId(deviceId) : ""
        let wrong: String = bound && needsPin ? "PIN falsch oder der Code ist für ein anderes Gerät bestimmt."
            : bound ? "Dieser Code ist für ein anderes Gerät bestimmt (Gerätenummer passt nicht)."
            : needsPin ? "Die PIN ist falsch." : "Der Code ist beschädigt."
        guard let key = key(needsPin ? p : "", d, Array(b[2..<18])),
              let nonce = try? AES.GCM.Nonce(data: Data(b[18..<30])),
              let box = try? AES.GCM.SealedBox(nonce: nonce, ciphertext: Data(b[30..<(b.count - 16)]), tag: Data(b[(b.count - 16)...])),
              let plain = try? AES.GCM.open(box, using: key, authenticating: Data(b[0..<2])) else { return .failure(.message(wrong)) }
        guard let payload = try? JSONDecoder().decode(SetupPayload.self, from: plain) else { return .failure(.message("Der Code ist beschädigt.")) }
        if payload.exp > 0 && Double(payload.exp) / 1000 < now.timeIntervalSince1970 {
            return .failure(.message("Dieser Einrichtungs-Code ist abgelaufen – bitte einen neuen anfordern."))
        }
        return .success(payload)
    }

    enum SetupError: Error { case message(String)
        var text: String { if case let .message(m) = self { return m }; return "" }
    }

    private static func bytes(_ text: String) -> [UInt8]? {
        let t = text.components(separatedBy: .whitespacesAndNewlines).joined()
        guard t.hasPrefix(prefix) else { return nil }
        var s = String(t.dropFirst(prefix.count)).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while s.count % 4 != 0 { s += "=" }
        guard let d = Data(base64Encoded: s), d.count >= 46 else { return nil }
        let b = [UInt8](d)
        return b[0] == 1 ? b : nil
    }

    private static func key(_ pin: String, _ device: String, _ salt: [UInt8]) -> SymmetricKey? {
        let password = "PORTIVA-SETUP|\(pin)|\(device)"
        var out = [UInt8](repeating: 0, count: 32)
        let count = out.count
        let rc: Int32 = password.withCString { pw in
            out.withUnsafeMutableBufferPointer { o in
                CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), pw, strlen(pw), salt, salt.count,
                                     CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), iterations, o.baseAddress, count)
            }
        }
        return Int(rc) == kCCSuccess ? SymmetricKey(data: out) : nil
    }
}
