//
//  CloudSyncService.swift
//  RutasSendero
//
//  Servicio de sincronización con la nube (PostgREST en Render).
//  Descarga el catálogo de rutas públicas y comunitarias,
//  descifra el payload cifrado con AES-256 GCM usando CryptoKit
//  e importa la ruta, puntos e hitos directamente a SwiftData local.
//

import Foundation
import CryptoKit

public struct CloudRouteSummary: Identifiable, Sendable {
    public let id: String
    public let name: String
    public let date: String
    public let creatorName: String
    public let creatorEmail: String
    public let creatorPhone: String
    public let distanceGpsMeters: Double
    public let descriptionText: String
    public let originLatitude: Double
    public let originLongitude: Double
    public let isPublic: Bool
    public let code: String
    
    public var formattedDistanceKm: String {
        String(format: "%.2f km", distanceGpsMeters / 1000.0)
    }
}

public final class CloudSyncService: @unchecked Sendable {
    public static let shared = CloudSyncService()
    
    private let baseUrl = "https://postgrest-latest-09dh.onrender.com"
    
    // Clave AES-256 precompartida (32 bytes) 1:1 con Android CryptoUtils
    private let secretKeyBytes: [UInt8] = [
        0x5F, 0x2A, 0x1E, 0x7D, 0x4C, 0x3B, 0x0A, 0x7E,
        0x12, 0x5D, 0x4E, 0x3F, 0x2C, 0x1B, 0x0A, 0x6E,
        0x78, 0x69, 0x5A, 0x4B, 0x3C, 0x2D, 0x1E, 0x0F,
        0x7E, 0x6F, 0x5D, 0x4C, 0x3B, 0x2A, 0x19, 0x08
    ]
    
    // MARK: - Autenticación JWT (HS256) 1:1 con Android JwtManager
    private let jwtSecret = "mi_clave_secreta_super_segura_rutas_sendero_2026"
    
    public func generateJwtToken(
        email: String = "senderista@rutassendero.com",
        telefono: String = "",
        rol: String = "S",
        idAgrupacion: String? = nil,
        validityDays: Int = 365
    ) -> String {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let cleanTelefono = telefono.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanRol = rol.trimmingCharacters(in: .whitespacesAndNewlines).uppercased().isEmpty ? "S" : rol.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        
        let headerObj: [String: Any] = [
            "alg": "HS256",
            "typ": "JWT"
        ]
        
        let nowSeconds = Int64(Date().timeIntervalSince1970)
        let expSeconds = nowSeconds + Int64(validityDays * 24 * 60 * 60)
        
        var payloadObj: [String: Any] = [
            "role": "senderista",
            "rol": cleanRol,
            "email": cleanEmail,
            "iat": nowSeconds,
            "exp": expSeconds
        ]
        if !cleanTelefono.isEmpty {
            payloadObj["telefono"] = cleanTelefono
        }
        if let idAgru = idAgrupacion?.trimmingCharacters(in: .whitespacesAndNewlines), !idAgru.isEmpty, idAgru != "@&" {
            payloadObj["id_agrupacion"] = idAgru
        }
        
        guard let headerData = try? JSONSerialization.data(withJSONObject: headerObj),
              let payloadData = try? JSONSerialization.data(withJSONObject: payloadObj) else {
            return ""
        }
        
        let headerBase64 = base64UrlEncode(headerData)
        let payloadBase64 = base64UrlEncode(payloadData)
        let signingInput = "\(headerBase64).\(payloadBase64)"
        
        let symmetricKey = SymmetricKey(data: Data(jwtSecret.utf8))
        let signature = HMAC<SHA256>.authenticationCode(for: Data(signingInput.utf8), using: symmetricKey)
        let signatureBase64 = base64UrlEncode(Data(signature))
        
        return "\(signingInput).\(signatureBase64)"
    }
    
    private func base64UrlEncode(_ data: Data) -> String {
        return data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .trimmingCharacters(in: CharacterSet(charactersIn: "="))
    }
    
    // MARK: - Ejecución con Degradación Transparente a Modo Anónimo (1:1 Android)
    private let lock = NSLock()
    private var _isJwtSupportedByServer = false
    private var _lastJwtCheckTime: TimeInterval = 0
    
    private func shouldAttemptJwt() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if _isJwtSupportedByServer { return true }
        let now = Date().timeIntervalSince1970
        return (now - _lastJwtCheckTime > 300)
    }
    
    private func markJwtFailed() {
        lock.lock()
        _isJwtSupportedByServer = false
        _lastJwtCheckTime = Date().timeIntervalSince1970
        lock.unlock()
    }
    
    private func markJwtSuccess() {
        lock.lock()
        _isJwtSupportedByServer = true
        lock.unlock()
    }
    
    private func isJwtSecretMissingError(statusCode: Int, body: String) -> Bool {
        return (statusCode == 500 || statusCode == 401) && (
            body.localizedCaseInsensitiveContains("Server lacks JWT secret") ||
            body.localizedCaseInsensitiveContains("PGRST300") ||
            body.localizedCaseInsensitiveContains("JWT secret not found") ||
            body.localizedCaseInsensitiveContains("secret not found")
        )
    }
    
    public func executeGet(url: URL, token: String? = nil) async throws -> (Data, HTTPURLResponse) {
        func buildRequest(useToken: Bool) -> URLRequest {
            var req = URLRequest(url: url)
            req.timeoutInterval = 20
            req.setValue("application/json", forHTTPHeaderField: "Accept")
            if useToken {
                let jwt = token ?? generateJwtToken()
                req.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")
            }
            return req
        }
        
        let tryJwt = shouldAttemptJwt()
        let initialReq = buildRequest(useToken: tryJwt)
        let (data, response) = try await URLSession.shared.data(for: initialReq)
        let httpResponse = response as? HTTPURLResponse ?? HTTPURLResponse()
        let bodyStr = String(data: data, encoding: .utf8) ?? ""
        
        if tryJwt && isJwtSecretMissingError(statusCode: httpResponse.statusCode, body: bodyStr) {
            print("⚠️ [CloudSync] Servidor PostgREST sin PGRST_JWT_SECRET. Degradando de forma transparente a modo anónimo (1:1 Android)...")
            markJwtFailed()
            let (anonData, anonResponse) = try await URLSession.shared.data(for: buildRequest(useToken: false))
            guard let anonHttp = anonResponse as? HTTPURLResponse else {
                throw URLError(.badServerResponse)
            }
            return (anonData, anonHttp)
        }
        
        if tryJwt && (200...299).contains(httpResponse.statusCode) {
            markJwtSuccess()
        }
        return (data, httpResponse)
    }
    
    public func executePost(url: URL, body: Data, prefer: String? = nil, token: String? = nil) async throws -> (Data, HTTPURLResponse) {
        func buildRequest(useToken: Bool) -> URLRequest {
            var req = URLRequest(url: url)
            req.httpMethod = "POST"
            req.timeoutInterval = 25
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            if let p = prefer {
                req.setValue(p, forHTTPHeaderField: "Prefer")
            }
            if useToken {
                let jwt = token ?? generateJwtToken()
                req.setValue("Bearer \(jwt)", forHTTPHeaderField: "Authorization")
            }
            req.httpBody = body
            return req
        }
        
        let tryJwt = shouldAttemptJwt()
        let initialReq = buildRequest(useToken: tryJwt)
        let (data, response) = try await URLSession.shared.data(for: initialReq)
        let httpResponse = response as? HTTPURLResponse ?? HTTPURLResponse()
        let bodyStr = String(data: data, encoding: .utf8) ?? ""
        
        if tryJwt && isJwtSecretMissingError(statusCode: httpResponse.statusCode, body: bodyStr) {
            print("⚠️ [CloudSync] Servidor PostgREST sin PGRST_JWT_SECRET. Reintentando POST en modo anónimo (1:1 Android)...")
            markJwtFailed()
            let (anonData, anonResponse) = try await URLSession.shared.data(for: buildRequest(useToken: false))
            guard let anonHttp = anonResponse as? HTTPURLResponse else {
                throw URLError(.badServerResponse)
            }
            return (anonData, anonHttp)
        }
        
        if tryJwt && (200...299).contains(httpResponse.statusCode) {
            markJwtSuccess()
        }
        return (data, httpResponse)
    }

    // MARK: - Descargar Agrupaciones de PostgREST
    public func fetchAgrupaciones() async -> [Agrupacion] {
        let endpoint = "\(baseUrl)/agrupaciones?select=id_agrupacion,nombre&order=nombre.asc"
        guard let url = URL(string: endpoint) else {
            return Agrupacion.defaultAgrupaciones
        }
        
        do {
            let (data, httpResponse) = try await executeGet(url: url)
            guard (200...299).contains(httpResponse.statusCode) else {
                return Agrupacion.defaultAgrupaciones
            }
            guard let list = try JSONSerialization.jsonObject(with: data) as? [[String: Any]], !list.isEmpty else {
                return Agrupacion.defaultAgrupaciones
            }
            
            var agrupaciones: [Agrupacion] = []
            for item in list {
                let id = (item["id_agrupacion"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                let nombre = (item["nombre"] as? String ?? id).trimmingCharacters(in: .whitespacesAndNewlines)
                if !id.isEmpty {
                    agrupaciones.append(Agrupacion(idAgrupacion: id, nombre: nombre))
                }
            }
            return agrupaciones.isEmpty ? Agrupacion.defaultAgrupaciones : agrupaciones
        } catch {
            print("⚠️ [CloudSync] Error descargando agrupaciones: \(error.localizedDescription). Usando predeterminadas.")
            return Agrupacion.defaultAgrupaciones
        }
    }
    
    // MARK: - Descargar Logo de Agrupación de PostgREST
    public func fetchAgrupacionLogo(idAgrupacion: String) async -> Data? {
        let cleanId = idAgrupacion.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty, cleanId != "@&",
              let encodedId = cleanId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "\(baseUrl)/agrupaciones?select=id_agrupacion,logo&id_agrupacion=eq.\(encodedId)") else {
            return nil
        }
        
        do {
            let token = generateJwtToken(idAgrupacion: cleanId)
            let (data, httpResponse) = try await executeGet(url: url, token: token)
            guard (200...299).contains(httpResponse.statusCode) else {
                return nil
            }
            guard let list = try JSONSerialization.jsonObject(with: data) as? [[String: Any]],
                  let first = list.first,
                  let rawLogo = first["logo"] as? String else {
                return nil
            }
            
            return parseLogoBytes(rawLogo)
        } catch {
            print("⚠️ [CloudSync] Error descargando logo para agrupación \(cleanId): \(error.localizedDescription)")
            return nil
        }
    }
    
    private func parseLogoBytes(_ raw: String) -> Data? {
        var clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty || clean.lowercased() == "null" { return nil }
        
        if clean.hasPrefix("\"") && clean.hasSuffix("\"") && clean.count >= 2 {
            clean = String(clean.dropFirst().dropLast()).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        
        // Formato PostgreSQL hex: \x... o 0x...
        if clean.hasPrefix("\\x") || clean.hasPrefix("\\X") {
            let hexStr = String(clean.dropFirst(2))
            return decodeHex(hexStr)
        } else if clean.hasPrefix("0x") || clean.hasPrefix("0X") {
            let hexStr = String(clean.dropFirst(2))
            return decodeHex(hexStr)
        } else if let b64Data = Data(base64Encoded: clean) {
            return b64Data
        }
        return nil
    }
    
    private func decodeHex(_ hex: String) -> Data? {
        let utf8 = Array(hex.utf8)
        guard utf8.count >= 2 else { return nil }
        var data = Data(capacity: utf8.count / 2)
        
        func hexVal(_ byte: UInt8) -> UInt8? {
            switch byte {
            case UInt8(ascii: "0")...UInt8(ascii: "9"):
                return byte - UInt8(ascii: "0")
            case UInt8(ascii: "a")...UInt8(ascii: "f"):
                return byte - UInt8(ascii: "a") + 10
            case UInt8(ascii: "A")...UInt8(ascii: "F"):
                return byte - UInt8(ascii: "A") + 10
            default:
                return nil
            }
        }
        
        var i = 0
        if utf8.count % 2 != 0 {
            if let v = hexVal(utf8[0]) {
                data.append(v)
            }
            i = 1
        }
        
        while i + 1 < utf8.count {
            guard let high = hexVal(utf8[i]), let low = hexVal(utf8[i + 1]) else {
                return nil
            }
            data.append((high << 4) | low)
            i += 2
        }
        return data.isEmpty ? nil : data
    }
    
    // MARK: - Auto-Recuperación de Senderista por Email
    public func fetchSenderistaByEmail(email: String) async throws -> HikerProfile? {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard let encodedEmail = cleanEmail.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "\(baseUrl)/senderistas?email=eq.\(encodedEmail)&limit=1") else {
            return nil
        }
        
        let token = generateJwtToken(email: cleanEmail)
        let (data, httpResponse) = try await executeGet(url: url, token: token)
        guard (200...299).contains(httpResponse.statusCode) else {
            return nil
        }
        guard let array = try JSONSerialization.jsonObject(with: data) as? [[String: Any]],
              let obj = array.first else {
            return nil
        }
        
        return mapJsonToProfile(obj)
    }
    
    // MARK: - Generación de id_beacon Único (1:1 Android CloudSyncManager.generateUniqueBeaconId)
    public func generateUniqueBeaconId() async -> String {
        for _ in 0..<10 {
            var bytes = [UInt8](repeating: 0, count: 6)
            for i in 0..<6 {
                bytes[i] = UInt8.random(in: 0...255)
            }
            let candidate = bytes.map { String(format: "%02x", $0) }.joined()
            if await fetchSenderistaByBeaconId(idBeacon: candidate) == nil {
                return candidate
            }
        }
        return String(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(12)).lowercased()
    }
    
    // MARK: - Consulta de Senderista por Baliza BLE
    public func fetchSenderistaByBeaconId(idBeacon: String) async -> HikerProfile? {
        let cleanBeacon = idBeacon.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanBeacon.isEmpty,
              let encoded = cleanBeacon.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "\(baseUrl)/senderistas?id_beacon=eq.\(encoded)&limit=1") else {
            return nil
        }
        
        do {
            let (data, httpResponse) = try await executeGet(url: url)
            guard (200...299).contains(httpResponse.statusCode) else {
                return nil
            }
            guard let array = try JSONSerialization.jsonObject(with: data) as? [[String: Any]],
                  let obj = array.first else {
                return nil
            }
            return mapJsonToProfile(obj)
        } catch {
            print("⚠️ [CloudSync] Error al buscar senderista por baliza BLE \(cleanBeacon): \(error.localizedDescription)")
            return nil
        }
    }
    
    private func mapJsonToProfile(_ obj: [String: Any]) -> HikerProfile {
        let email = obj["email"] as? String ?? ""
        let rolRaw = obj["rol"] as? String ?? "S"
        let rol = rolRaw.uppercased() == "G" ? "G" : rolRaw.uppercased()
        let telefono = obj["telefono"] as? String ?? ""
        let nombre = obj["nombre"] as? String ?? ""
        let sexo = obj["sexo"] as? String ?? "M"
        
        let rawDob = obj["fecha_nacimiento"] as? String ?? "2000-01-01"
        var formattedDob = rawDob
        let dfIn = DateFormatter()
        dfIn.dateFormat = "yyyy-MM-dd"
        dfIn.locale = Locale(identifier: "en_US_POSIX")
        if let d = dfIn.date(from: rawDob) {
            let dfOut = DateFormatter()
            dfOut.dateFormat = "dd/MM/yyyy"
            dfOut.locale = Locale(identifier: "en_US_POSIX")
            formattedDob = dfOut.string(from: d)
        }
        
        let aQuienes = obj["a_quienes_avisar"] as? String ?? "Familia"
        let tel1 = obj["a_quien_avisar_telefono1"] as? String ?? ""
        let tel2 = obj["a_quien_avisar_telefono2"] as? String
        
        let tieneEnf = (obj["tiene_enfermedad_cronica"] as? String) == "S"
        let enf1 = obj["enfermedad_cronica1"] as? String
        let enf2 = obj["enfermedad_cronica2"] as? String
        let tieneAuto = (obj["tiene_auto"] as? String) == "S"
        let direccion = obj["direccion"] as? String
        let llevaMascota = (obj["lleva_mascota"] as? String) == "S"
        let alergias = obj["alergias"] as? String
        let tipoSangre = obj["tipo_sangre"] as? String
        let seguro = obj["seguro_medico"] as? String
        let nivel = obj["nivel_senderista"] as? String ?? "principiante"
        let beacon = obj["id_beacon"] as? String
        let minLong = obj["minimo_longitud"] as? Double ?? 4.0
        let codigoConfianza = obj["codigo_confianza"] as? String
        let peso = obj["peso"] as? Double ?? 70.0
        let idAgrupacion = obj["id_agrupacion"] as? String
        let maxPriv = obj["max_privadas"] as? Int ?? 3
        let consTerminos = obj["consentimiento_terminos"] as? Bool ?? true
        let consFicha = obj["consentimiento_ficha_medica"] as? Bool ?? true
        let consGps = obj["consentimiento_gps_multimedia"] as? Bool ?? true
        let timeUtc = (obj["timestamp_consentimiento_utc"] as? NSNumber)?.int64Value
        
        return HikerProfile(
            email: email,
            name: nombre,
            phone: telefono,
            role: rol,
            sexo: sexo,
            fechaNacimiento: formattedDob,
            idAgrupacion: idAgrupacion,
            codigoConfianza: codigoConfianza,
            beaconId: beacon,
            experienceLevel: nivel,
            bloodType: tipoSangre,
            contactName: aQuienes,
            emergencyPhone1: tel1,
            emergencyPhone2: tel2,
            weightKg: peso,
            tieneEnfermedadCronica: tieneEnf,
            enfermedadCronica1: enf1,
            enfermedadCronica2: enf2,
            tieneAuto: tieneAuto,
            direccion: direccion,
            llevaMascota: llevaMascota,
            alergias: alergias,
            seguroMedico: seguro,
            minimoLongitud: minLong,
            maxPrivadas: maxPriv,
            consentimientoTerminos: consTerminos,
            consentimientoFichaMedica: consFicha,
            consentimientoGpsMultimedia: consGps,
            timestampConsentimientoUtc: timeUtc
        )
    }
    
    // MARK: - Upsert de Senderista en PostgREST
    public func syncSenderista(profile: HikerProfile) async throws {
        let endpoint = "\(baseUrl)/senderistas"
        guard let url = URL(string: endpoint) else {
            throw URLError(.badURL)
        }
        
        let token = generateJwtToken(
            email: profile.email,
            telefono: profile.phone,
            rol: profile.role,
            idAgrupacion: profile.idAgrupacion
        )
        
        var formattedDob = "2000-01-01"
        let cleanDob = profile.fechaNacimiento.trimmingCharacters(in: .whitespacesAndNewlines)
        for fmt in ["dd/MM/yyyy", "yyyy-MM-dd", "yyyy/MM/dd"] {
            let df = DateFormatter()
            df.dateFormat = fmt
            df.locale = Locale(identifier: "en_US_POSIX")
            if let d = df.date(from: cleanDob) {
                let dfOut = DateFormatter()
                dfOut.dateFormat = "yyyy-MM-dd"
                dfOut.locale = Locale(identifier: "en_US_POSIX")
                formattedDob = dfOut.string(from: d)
                break
            }
        }
        
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let fupdateIso = isoFormatter.string(from: Date())
        
        var payload: [String: Any] = [
            "email": profile.email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(),
            "rol": profile.role.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(),
            "telefono": profile.phone.trimmingCharacters(in: .whitespacesAndNewlines),
            "nombre": profile.name.trimmingCharacters(in: .whitespacesAndNewlines),
            "sexo": profile.sexo,
            "fecha_nacimiento": formattedDob,
            "a_quienes_avisar": profile.contactName.trimmingCharacters(in: .whitespacesAndNewlines),
            "a_quien_avisar_telefono1": profile.emergencyPhone1.trimmingCharacters(in: .whitespacesAndNewlines),
            "tiene_enfermedad_cronica": profile.tieneEnfermedadCronica ? "S" : "N",
            "tiene_auto": profile.tieneAuto ? "S" : "N",
            "lleva_mascota": profile.llevaMascota ? "S" : "N",
            "nivel_senderista": profile.experienceLevel,
            "fupdate": fupdateIso,
            "minimo_longitud": profile.minimoLongitud,
            "bloqueado": false,
            "consentimiento_terminos": profile.consentimientoTerminos,
            "consentimiento_ficha_medica": profile.consentimientoFichaMedica,
            "consentimiento_gps_multimedia": profile.consentimientoGpsMultimedia,
            "max_privadas": profile.maxPrivadas,
            "peso": profile.weightKg
        ]
        
        if let p2 = profile.emergencyPhone2?.trimmingCharacters(in: .whitespacesAndNewlines), !p2.isEmpty {
            payload["a_quien_avisar_telefono2"] = p2
        } else {
            payload["a_quien_avisar_telefono2"] = NSNull()
        }
        
        if profile.tieneEnfermedadCronica, let e1 = profile.enfermedadCronica1?.trimmingCharacters(in: .whitespacesAndNewlines), !e1.isEmpty {
            payload["enfermedad_cronica1"] = e1
        } else {
            payload["enfermedad_cronica1"] = NSNull()
        }
        
        if profile.tieneEnfermedadCronica, let e2 = profile.enfermedadCronica2?.trimmingCharacters(in: .whitespacesAndNewlines), !e2.isEmpty {
            payload["enfermedad_cronica2"] = e2
        } else {
            payload["enfermedad_cronica2"] = NSNull()
        }
        
        if let dir = profile.direccion?.trimmingCharacters(in: .whitespacesAndNewlines), !dir.isEmpty {
            payload["direccion"] = dir
        } else {
            payload["direccion"] = NSNull()
        }
        
        if let alg = profile.alergias?.trimmingCharacters(in: .whitespacesAndNewlines), !alg.isEmpty {
            payload["alergias"] = alg
        } else {
            payload["alergias"] = NSNull()
        }
        
        if let ts = profile.bloodType?.trimmingCharacters(in: .whitespacesAndNewlines), !ts.isEmpty, ts != "No especificado" {
            payload["tipo_sangre"] = ts
        } else {
            payload["tipo_sangre"] = NSNull()
        }
        
        if let seg = profile.seguroMedico?.trimmingCharacters(in: .whitespacesAndNewlines), !seg.isEmpty {
            payload["seguro_medico"] = seg
        } else {
            payload["seguro_medico"] = NSNull()
        }
        
        if let bid = profile.beaconId?.trimmingCharacters(in: .whitespacesAndNewlines), !bid.isEmpty {
            payload["id_beacon"] = bid
        } else {
            payload["id_beacon"] = NSNull()
        }
        
        if let agru = profile.idAgrupacion?.trimmingCharacters(in: .whitespacesAndNewlines), !agru.isEmpty, agru != "@&" {
            payload["id_agrupacion"] = agru
        } else {
            payload["id_agrupacion"] = NSNull()
        }
        
        if let cod = profile.codigoConfianza?.trimmingCharacters(in: .whitespacesAndNewlines), !cod.isEmpty {
            payload["codigo_confianza"] = cod
        } else {
            payload["codigo_confianza"] = NSNull()
        }
        
        if let timeUtc = profile.timestampConsentimientoUtc {
            payload["timestamp_consentimiento_utc"] = timeUtc
        } else {
            payload["timestamp_consentimiento_utc"] = Int64(Date().timeIntervalSince1970 * 1000)
        }
        
        let bodyData = try JSONSerialization.data(withJSONObject: payload)
        let (_, httpResponse) = try await executePost(url: url, body: bodyData, prefer: "resolution=merge-duplicates", token: token)
        guard (200...299).contains(httpResponse.statusCode) else {
            print("⚠️ [CloudSync] Error sincronizando senderista en PostgREST (HTTP \(httpResponse.statusCode))")
            throw URLError(.badServerResponse)
        }
        
        print("☁️ [CloudSync] Senderista \(profile.email) sincronizado exitosamente con PostgREST en la nube.")
    }

    // MARK: - 1. Consultar Catálogo de Rutas en la Nube
    
    public func fetchCloudRoutes() async throws -> [CloudRouteSummary] {
        let endpoint = "\(baseUrl)/rutas?select=id,nombre,fecha,latitud_orig,longitud_orig,descripcion,distancia_gps,email_creador,telefono_creador,publico,codigo,senderistas(nombre)&order=fecha_registro_nube.desc"
        
        guard let url = URL(string: endpoint) else {
            throw URLError(.badURL)
        }
        
        let (data, httpResponse) = try await executeGet(url: url)
        
        guard (200...299).contains(httpResponse.statusCode) else {
            throw URLError(.badServerResponse)
        }
        
        guard let jsonArray = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }
        
        var summaries: [CloudRouteSummary] = []
        
        for item in jsonArray {
            let id = item["id"] as? String ?? ""
            guard !id.isEmpty else { continue }
            
            let nombre = item["nombre"] as? String ?? "Sin nombre"
            let fecha = item["fecha"] as? String ?? ""
            let lat = item["latitud_orig"] as? Double ?? 0.0
            let lon = item["longitud_orig"] as? Double ?? 0.0
            let desc = item["descripcion"] as? String ?? ""
            let distancia = item["distancia_gps"] as? Double ?? 0.0
            let email = item["email_creador"] as? String ?? ""
            let tel = item["telefono_creador"] as? String ?? ""
            let pub = item["publico"] as? Bool ?? false
            let cod = item["codigo"] as? String ?? ""
            
            var senderistaName = "Senderista"
            if let sendObj = item["senderistas"] as? [String: Any], let sName = sendObj["nombre"] as? String {
                senderistaName = sName
            }
            
            summaries.append(
                CloudRouteSummary(
                    id: id,
                    name: nombre,
                    date: fecha,
                    creatorName: senderistaName,
                    creatorEmail: email,
                    creatorPhone: tel,
                    distanceGpsMeters: distancia,
                    descriptionText: desc,
                    originLatitude: lat,
                    originLongitude: lon,
                    isPublic: pub,
                    code: cod
                )
            )
        }
        
        return summaries
    }
    
    // MARK: - 2. Descargar, Descifrar (AES-256 GCM) y Guardar en SQLite Local
    
    public func downloadAndSaveRoute(
        routeId: String,
        into repository: RutasRepositoryProtocol
    ) async throws -> Route {
        guard let encodedId = routeId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
            throw URLError(.badURL)
        }
        
        let endpoint = "\(baseUrl)/rutas?id=eq.\(encodedId)&select=datos_encriptados,telefono_creador&limit=1"
        guard let url = URL(string: endpoint) else {
            throw URLError(.badURL)
        }
        
        var request = URLRequest(url: url)
        request.timeoutInterval = 35
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw URLError(.badServerResponse)
        }
        
        guard let jsonArray = try JSONSerialization.jsonObject(with: data) as? [[String: Any]],
              let firstItem = jsonArray.first,
              let encryptedBase64 = firstItem["datos_encriptados"] as? String,
              let encryptedData = Data(base64Encoded: encryptedBase64) else {
            throw NSError(domain: "CloudSync", code: 404, userInfo: [NSLocalizedDescriptionKey: "No se encontraron los datos cifrados de la ruta."])
        }
        
        // Descifrado mediante CryptoKit (AES-256 GCM con IV de 12 bytes al inicio)
        let symmetricKey = SymmetricKey(data: secretKeyBytes)
        let sealedBox = try AES.GCM.SealedBox(combined: encryptedData)
        let decryptedData = try AES.GCM.open(sealedBox, using: symmetricKey)
        
        guard let root = try JSONSerialization.jsonObject(with: decryptedData) as? [String: Any],
              let rJson = root["ruta"] as? [String: Any] else {
            throw NSError(domain: "CloudSync", code: 500, userInfo: [NSLocalizedDescriptionKey: "El contenido de la ruta no tiene el formato esperado."])
        }
        
        // Mapear Ruta
        let id = rJson["id"] as? String ?? routeId
        let nombre = rJson["nombre"] as? String ?? "Ruta Importada"
        let fecha = rJson["fecha"] as? String ?? ""
        let latOrig = rJson["latitud_orig"] as? Double ?? 0.0
        let lonOrig = rJson["longitud_orig"] as? Double ?? 0.0
        let desc = rJson["descripcion"] as? String ?? ""
        let distGps = rJson["distancia_gps"] as? Double ?? 0.0
        let distBeacons = rJson["distancia_beacons"] as? Double ?? 0.0
        let fInicio = rJson["fecha_hora_inicio"] as? String
        let fFin = rJson["fecha_hora_fin"] as? String
        let duracion = Int64(rJson["duracion_segundos"] as? Int ?? 0)
        let desPos = rJson["desnivel_positivo"] as? Double ?? 0.0
        let desNeg = rJson["desnivel_negativo"] as? Double ?? 0.0
        let subidas = rJson["contador_subidas"] as? Int ?? 0
        let bajadas = rJson["contador_bajadas"] as? Int ?? 0
        let esPublico = rJson["publico"] as? Bool ?? true
        let tel = rJson["telefono"] as? String ?? (rJson["telefono_creador"] as? String ?? "")
        let importDate = Int64(Date().timeIntervalSince1970 * 1000)
        
        let route = Route(
            id: id,
            name: nombre,
            date: fecha,
            originLatitude: latOrig,
            originLongitude: lonOrig,
            descriptionText: desc,
            gpsDistanceMeters: distGps,
            beaconDistanceMeters: distBeacons,
            startTime: fInicio,
            endTime: fFin,
            durationSeconds: duracion,
            elevationGain: desPos,
            elevationLoss: desNeg,
            climbsCount: subidas,
            descentsCount: bajadas,
            isPublic: esPublico,
            syncTimestamp: Int64(Date().timeIntervalSince1970 * 1000),
            creatorPhone: tel,
            importDate: importDate
        )
        
        // Guardar Ruta en SwiftData
        try await repository.save(route: route)
        
        // Mapear y Guardar Puntos
        if let pArray = root["puntos"] as? [[String: Any]] {
            for (idx, pObj) in pArray.enumerated() {
                let pId = pObj["id"] as? Int ?? (idx + 1)
                let pTimestamp = pObj["fecha_hora"] as? String ?? ""
                let pLat = pObj["latitud"] as? Double ?? 0.0
                let pLon = pObj["longitud"] as? Double ?? 0.0
                let pAlt = pObj["altitud"] as? Double ?? 0.0
                let pDesc = pObj["descripcion"] as? String ?? ""
                let pFoto = pObj["foto_path"] as? String
                let pFin = (pObj["es_fin_ruta"] as? Int ?? 0) != 0 || (pObj["es_fin_ruta"] as? Bool ?? false)
                let pCon = (pObj["tiene_conectividad"] as? Int ?? 1) != 0
                var pEstado = (pObj["estado"] as? NSNumber)?.intValue ?? (pObj["estado"] as? Int ?? (Int(pObj["estado"] as? String ?? "") ?? 1))
                if pEstado == 1 && !pDesc.isEmpty {
                    let lower = pDesc.lowercased()
                    if lower.contains("bifurcac") || lower.contains("unión de dos ríos") || lower.contains("union de dos rios") {
                        pEstado = 3
                    } else if lower.contains("suelo flojo") || lower.contains("resbalos") {
                        pEstado = 4
                    } else if lower.contains("cuidado") || lower.contains("precauci") {
                        pEstado = 5
                    } else if lower.contains("alerta") || lower.contains("peligro") {
                        pEstado = 6
                    } else if lower.contains("vehiculo") || lower.contains("vehículo") || lower.contains("carro") {
                        pEstado = 8
                    } else if lower.contains("refugio") || lower.contains("campamento") {
                        pEstado = 9
                    } else if lower.contains("comida") || lower.contains("restaurante") {
                        pEstado = 10
                    } else if lower.contains("interes") || lower.contains("interés") || lower.contains("mirador") || lower.contains("arroyo") || lower.contains("ranchito") {
                        pEstado = 2
                    }
                }
                
                let point = RoutePoint(
                    id: pId,
                    routeId: id,
                    timestamp: pTimestamp,
                    latitude: pLat,
                    longitude: pLon,
                    altitude: pAlt,
                    descriptionText: pDesc,
                    photoUrl: pFoto,
                    isRouteEnd: pFin,
                    hasConnectivity: pCon,
                    estado: pEstado
                )
                try await repository.save(point: point)
            }
        }
        
        // Mapear y Guardar Traza
        if let tArray = root["traza"] as? [[String: Any]] {
            var traceBatch: [RouteTrace] = []
            for (idx, tObj) in tArray.enumerated() {
                let tId = tObj["id"] as? Int ?? (idx + 1)
                let tOrder = tObj["orden"] as? Int ?? (idx + 1)
                let tLat = tObj["latitud"] as? Double ?? 0.0
                let tLon = tObj["longitud"] as? Double ?? 0.0
                let tAlt = tObj["altitud"] as? Double ?? 0.0
                
                traceBatch.append(
                    RouteTrace(
                        id: tId,
                        routeId: id,
                        order: tOrder,
                        latitude: tLat,
                        longitude: tLon,
                        altitude: tAlt
                    )
                )
            }
            if !traceBatch.isEmpty {
                try await repository.save(traceBatch: traceBatch)
            }
        }
        
        return route
    }
    
    // MARK: - 3. Subir Ruta Local Cifrada a la Nube (PostgREST)
    public func uploadRouteToCloud(
        routeId: String,
        from repository: RutasRepositoryProtocol,
        creator: HikerProfile
    ) async throws {
        guard let route = try await repository.getRoute(byId: routeId) else {
            throw NSError(domain: "CloudSync", code: 404, userInfo: [NSLocalizedDescriptionKey: "No se encontró la ruta en el almacenamiento local."])
        }
        
        let points = try await repository.getPoints(forRouteId: routeId)
        let trace = try await repository.getTrace(forRouteId: routeId)
        
        // 1. Construir JSON idéntico a Android
        var routeDict: [String: Any] = [
            "id": route.id,
            "nombre": route.name,
            "fecha": route.date,
            "latitud_orig": route.originLatitude,
            "longitud_orig": route.originLongitude,
            "descripcion": route.descriptionText,
            "distancia_gps": route.gpsDistanceMeters,
            "distancia_beacons": route.beaconDistanceMeters,
            "duracion_segundos": route.durationSeconds,
            "desnivel_positivo": route.elevationGain,
            "desnivel_negativo": route.elevationLoss,
            "contador_subidas": route.climbsCount,
            "contador_bajadas": route.descentsCount,
            "telefono": route.creatorPhone.isEmpty ? creator.phone : route.creatorPhone,
            "conectividad": true,
            "codigo": route.id,
            "publico": route.isPublic
        ]
        if let start = route.startTime { routeDict["fecha_hora_inicio"] = start }
        if let end = route.endTime { routeDict["fecha_hora_fin"] = end }
        
        var pointsArray: [[String: Any]] = []
        for p in points {
            var pDict: [String: Any] = [
                "id": p.id,
                "id_ruta": p.routeId,
                "fecha_hora": p.timestamp,
                "latitud": p.latitude,
                "longitud": p.longitude,
                "altitud": p.altitude,
                "descripcion": p.descriptionText,
                "es_fin_ruta": p.isRouteEnd ? 1 : 0,
                "tiene_conectividad": p.hasConnectivity ? 1 : 0,
                "estado": p.estado
            ]
            if let f = p.photoUrl { pDict["foto_path"] = f }
            pointsArray.append(pDict)
        }
        
        var traceArray: [[String: Any]] = []
        for t in trace {
            let tDict: [String: Any] = [
                "id": t.id,
                "id_ruta": t.routeId,
                "orden": t.order,
                "latitud": t.latitude,
                "longitud": t.longitude,
                "altitud": t.altitude
            ]
            traceArray.append(tDict)
        }
        
        let rootPayload: [String: Any] = [
            "ruta": routeDict,
            "puntos": pointsArray,
            "traza": traceArray
        ]
        
        let jsonData = try JSONSerialization.data(withJSONObject: rootPayload)
        
        // 2. Cifrar con AES-256 GCM (CryptoKit) y codificar a Base64
        let symmetricKey = SymmetricKey(data: secretKeyBytes)
        let sealedBox = try AES.GCM.seal(jsonData, using: symmetricKey)
        guard let combinedData = sealedBox.combined else {
            throw NSError(domain: "CloudSync", code: 500, userInfo: [NSLocalizedDescriptionKey: "Error cifrando los datos de la ruta."])
        }
        let encryptedBase64 = combinedData.base64EncodedString()
        
        // 3. Formatear fecha para PostgreSQL (yyyy-MM-dd)
        var formattedDate = route.date
        for fmt in ["dd/MM/yyyy", "yyyy-MM-dd", "yyyy/MM/dd"] {
            let dfIn = DateFormatter()
            dfIn.dateFormat = fmt
            dfIn.locale = Locale(identifier: "en_US_POSIX")
            if let d = dfIn.date(from: route.date) {
                let dfOut = DateFormatter()
                dfOut.dateFormat = "yyyy-MM-dd"
                dfOut.locale = Locale(identifier: "en_US_POSIX")
                formattedDate = dfOut.string(from: d)
                break
            }
        }
        
        let rutaPayload: [String: Any] = [
            "id": route.id,
            "nombre": route.name,
            "fecha": formattedDate,
            "latitud_orig": route.originLatitude,
            "longitud_orig": route.originLongitude,
            "telefono_creador": route.creatorPhone.isEmpty ? creator.phone : route.creatorPhone,
            "descripcion": route.descriptionText,
            "distancia_gps": route.gpsDistanceMeters,
            "distancia_beacons": route.beaconDistanceMeters,
            "duracion_segundos": route.durationSeconds,
            "desnivel_positivo": route.elevationGain,
            "desnivel_negativo": route.elevationLoss,
            "contador_subidas": route.climbsCount,
            "contador_bajadas": route.descentsCount,
            "datos_encriptados": encryptedBase64,
            "email_creador": creator.email,
            "conectividad": true,
            "codigo": route.id,
            "publico": route.isPublic,
            "agrupacion": creator.idAgrupacion ?? "MH"
        ]
        
        guard let url = URL(string: "\(baseUrl)/rutas") else {
            throw URLError(.badURL)
        }
        
        let token = generateJwtToken(email: creator.email, telefono: creator.phone, rol: creator.role, idAgrupacion: creator.idAgrupacion)
        let bodyData = try JSONSerialization.data(withJSONObject: rutaPayload)
        let (_, httpResponse) = try await executePost(url: url, body: bodyData, prefer: "resolution=merge-duplicates", token: token)
        guard (200...299).contains(httpResponse.statusCode) else {
            print("⚠️ [CloudSync] Error subiendo ruta a PostgREST (HTTP \(httpResponse.statusCode))")
            throw URLError(.badServerResponse)
        }
        
        print("☁️ [CloudSync] Ruta '\(route.name)' (\(route.id)) subida exitosamente a la nube.")
    }
}
