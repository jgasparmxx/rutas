//
//  SwiftProtocols.swift
//  RutasSendero (iOS Migration)
//
//  Contratos nativos en Swift (Protocol-Oriented Programming).
//  Equivalente 1:1 de los contratos y casos de uso de Clean Architecture.
//

import Foundation

// MARK: - Modelos de Dominio en Swift

public struct Route: Identifiable, Codable, Sendable {
    public let id: String
    public var name: String
    public var date: String
    public var originLatitude: Double
    public var originLongitude: Double
    public var descriptionText: String
    public var gpsDistanceMeters: Double
    public var beaconDistanceMeters: Double
    public var startTime: String?
    public var endTime: String?
    public var durationSeconds: Int64
    public var elevationGain: Double
    public var elevationLoss: Double
    public var climbsCount: Int
    public var descentsCount: Int
    public var connectivity: Double
    public var accessCode: String
    public var isPublic: Bool
    public var syncTimestamp: Int64
    public var creatorPhone: String
    public var importDate: Int64?
    
    public init(
        id: String,
        name: String,
        date: String,
        originLatitude: Double,
        originLongitude: Double,
        descriptionText: String = "",
        gpsDistanceMeters: Double = 0.0,
        beaconDistanceMeters: Double = 0.0,
        startTime: String? = nil,
        endTime: String? = nil,
        durationSeconds: Int64 = 0,
        elevationGain: Double = 0.0,
        elevationLoss: Double = 0.0,
        climbsCount: Int = 0,
        descentsCount: Int = 0,
        connectivity: Double = 0.0,
        accessCode: String = "",
        isPublic: Bool = true,
        syncTimestamp: Int64 = 0,
        creatorPhone: String = "",
        importDate: Int64? = nil
    ) {
        self.id = id
        self.name = name
        self.date = date
        self.originLatitude = originLatitude
        self.originLongitude = originLongitude
        self.descriptionText = descriptionText
        self.gpsDistanceMeters = gpsDistanceMeters
        self.beaconDistanceMeters = beaconDistanceMeters
        self.startTime = startTime
        self.endTime = endTime
        self.durationSeconds = durationSeconds
        self.elevationGain = elevationGain
        self.elevationLoss = elevationLoss
        self.climbsCount = climbsCount
        self.descentsCount = descentsCount
        self.connectivity = connectivity
        self.accessCode = accessCode
        self.isPublic = isPublic
        self.syncTimestamp = syncTimestamp
        self.creatorPhone = creatorPhone
        self.importDate = importDate
    }
    
    /// Regla 1:1 con Android Kotlin:
    /// Una ruta pertenece al dueño si coincide el teléfono o si fue creada localmente (fechaImportacion == nil).
    /// Si fue importada de la nube y el teléfono no coincide, es de solo consumo y no puede modificarse.
    public func isOwnRoute(userPhone: String) -> Bool {
        let cleanUser = userPhone.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanCreator = creatorPhone.trimmingCharacters(in: .whitespacesAndNewlines)
        
        if cleanUser.isEmpty {
            return cleanCreator.isEmpty && importDate == nil
        }
        
        if importDate == nil && cleanCreator.isEmpty {
            return true
        }
        
        if cleanCreator == cleanUser {
            return true
        }
        
        let userDigits = cleanUser.filter { $0.isNumber }
        let creatorDigits = cleanCreator.filter { $0.isNumber }
        if userDigits.count >= 10 && creatorDigits.count >= 10 {
            return userDigits.suffix(10) == creatorDigits.suffix(10)
        }
        return false
    }
    
    public var latitudOrig: Double { originLatitude }
    public var longitudOrig: Double { originLongitude }
    public var nombre: String { name }
}

public struct RoutePoint: Identifiable, Codable, Sendable, Equatable, Hashable {
    public let id: Int
    public let routeId: String
    public let timestamp: String
    public var latitude: Double
    public var longitude: Double
    public var altitude: Double
    public var descriptionText: String
    public var photoUrl: String?
    public var isRouteEnd: Bool
    public var hasConnectivity: Bool
    public var estado: Int
    
    public init(
        id: Int,
        routeId: String,
        timestamp: String,
        latitude: Double,
        longitude: Double,
        altitude: Double,
        descriptionText: String,
        photoUrl: String? = nil,
        isRouteEnd: Bool = false,
        hasConnectivity: Bool = true,
        estado: Int = 1
    ) {
        self.id = id
        self.routeId = routeId
        self.timestamp = timestamp
        self.latitude = latitude
        self.longitude = longitude
        self.altitude = altitude
        self.descriptionText = descriptionText
        self.photoUrl = photoUrl
        self.isRouteEnd = isRouteEnd
        self.hasConnectivity = hasConnectivity
        self.estado = estado
    }
    
    public var name: String {
        descriptionText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Punto \(id)" : descriptionText
    }
}

public struct RouteTrace: Identifiable, Codable, Sendable {
    public let id: Int
    public let routeId: String
    public let order: Int
    public let latitude: Double
    public let longitude: Double
    public let altitude: Double
}

public struct Agrupacion: Identifiable, Codable, Sendable, Hashable {
    public var id: String { idAgrupacion }
    public let idAgrupacion: String
    public let nombre: String
    
    public init(idAgrupacion: String, nombre: String) {
        self.idAgrupacion = idAgrupacion
        self.nombre = nombre
    }
    
    public static let defaultAgrupaciones: [Agrupacion] = [
        Agrupacion(idAgrupacion: "MH", nombre: "Miguel Hidalgo"),
        Agrupacion(idAgrupacion: "CL1", nombre: "COLIBRI")
    ]
}

public struct HikerProfile: Identifiable, Codable, Sendable {
    public var id: String { email }
    public var email: String
    public var name: String
    public var phone: String
    public var role: String
    public var sexo: String // "M" para masculino, "F" para femenino
    public var fechaNacimiento: String // Formato "dd/MM/yyyy"
    public var idAgrupacion: String? // "MH", "CL1", etc., o nil para "Sin Agrupación"
    public var codigoConfianza: String?
    public var beaconId: String?
    public var experienceLevel: String
    public var bloodType: String?
    public var contactName: String
    public var emergencyPhone1: String
    public var emergencyPhone2: String?
    public var weightKg: Double
    
    // Ficha médica y estilo de vida
    public var tieneEnfermedadCronica: Bool
    public var enfermedadCronica1: String?
    public var enfermedadCronica2: String?
    public var tieneAuto: Bool
    public var direccion: String?
    public var llevaMascota: Bool
    public var alergias: String?
    public var seguroMedico: String?
    public var minimoLongitud: Double
    public var maxPrivadas: Int
    
    // Consentimientos legales
    public var consentimientoTerminos: Bool
    public var consentimientoFichaMedica: Bool
    public var consentimientoGpsMultimedia: Bool
    public var timestampConsentimientoUtc: Int64?
    
    // MARK: - Cálculo Dinámico de Edad
    public var edad: Int? {
        let clean = fechaNacimiento.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return nil }
        let formats = ["dd/MM/yyyy", "yyyy-MM-dd", "yyyy/MM/dd"]
        for fmt in formats {
            let df = DateFormatter()
            df.dateFormat = fmt
            df.locale = Locale(identifier: "en_US_POSIX")
            if let birthDate = df.date(from: clean) {
                let calendar = Calendar.current
                let ageComponents = calendar.dateComponents([.year], from: birthDate, to: Date())
                return ageComponents.year
            }
        }
        return nil
    }
    
    public var isUnderage: Bool {
        guard let a = edad else { return false }
        return a <= 18
    }
    
    public init(
        email: String,
        name: String,
        phone: String,
        role: String = "G",
        sexo: String = "M",
        fechaNacimiento: String = "01/01/2000",
        idAgrupacion: String? = "MH",
        codigoConfianza: String? = nil,
        beaconId: String? = nil,
        experienceLevel: String = "principiante",
        bloodType: String? = "O+",
        contactName: String = "Familia",
        emergencyPhone1: String,
        emergencyPhone2: String? = nil,
        weightKg: Double = 70.0,
        tieneEnfermedadCronica: Bool = false,
        enfermedadCronica1: String? = nil,
        enfermedadCronica2: String? = nil,
        tieneAuto: Bool = false,
        direccion: String? = nil,
        llevaMascota: Bool = false,
        alergias: String? = nil,
        seguroMedico: String? = nil,
        minimoLongitud: Double = 4.0,
        maxPrivadas: Int = 3,
        consentimientoTerminos: Bool = true,
        consentimientoFichaMedica: Bool = true,
        consentimientoGpsMultimedia: Bool = true,
        timestampConsentimientoUtc: Int64? = nil
    ) {
        self.email = email
        self.name = name
        self.phone = phone
        self.role = role
        self.sexo = sexo
        self.fechaNacimiento = fechaNacimiento
        self.idAgrupacion = idAgrupacion
        self.codigoConfianza = codigoConfianza
        self.beaconId = beaconId
        self.experienceLevel = experienceLevel
        self.bloodType = bloodType
        self.contactName = contactName
        self.emergencyPhone1 = emergencyPhone1
        self.emergencyPhone2 = emergencyPhone2
        self.weightKg = weightKg
        self.tieneEnfermedadCronica = tieneEnfermedadCronica
        self.enfermedadCronica1 = enfermedadCronica1
        self.enfermedadCronica2 = enfermedadCronica2
        self.tieneAuto = tieneAuto
        self.direccion = direccion
        self.llevaMascota = llevaMascota
        self.alergias = alergias
        self.seguroMedico = seguroMedico
        self.minimoLongitud = minimoLongitud
        self.maxPrivadas = maxPrivadas
        self.consentimientoTerminos = consentimientoTerminos
        self.consentimientoFichaMedica = consentimientoFichaMedica
        self.consentimientoGpsMultimedia = consentimientoGpsMultimedia
        self.timestampConsentimientoUtc = timestampConsentimientoUtc
    }
}

public struct PeerBeaconData: Identifiable, Codable, Sendable {
    public var id: String { beaconId }
    public let beaconId: String
    public var latitude: Double
    public var longitude: Double
    public var rssi: Int
    public var txPower: Int
    public var estimatedDistanceMeters: Double
    public var lastDetectedTimestamp: Date
    public var isEmergency: Bool
    
    public init(
        beaconId: String,
        latitude: Double,
        longitude: Double,
        rssi: Int,
        txPower: Int,
        estimatedDistanceMeters: Double,
        lastDetectedTimestamp: Date,
        isEmergency: Bool = false
    ) {
        self.beaconId = beaconId
        self.latitude = latitude
        self.longitude = longitude
        self.rssi = rssi
        self.txPower = txPower
        self.estimatedDistanceMeters = estimatedDistanceMeters
        self.lastDetectedTimestamp = lastDetectedTimestamp
        self.isEmergency = isEmergency
    }
}

// MARK: - Protocolos de Repositorio (Capa de Datos)

public protocol RutasRepositoryProtocol: Sendable {
    func getAllRoutes() async throws -> [Route]
    func getRoute(byId id: String) async throws -> Route?
    func getPoints(forRouteId routeId: String) async throws -> [RoutePoint]
    func getTrace(forRouteId routeId: String) async throws -> [RouteTrace]
    func save(route: Route) async throws
    func save(point: RoutePoint) async throws
    func save(traceBatch: [RouteTrace]) async throws
    func deletePointsAndTrace(forRouteId routeId: String) async throws
    func deleteRoute(byId id: String) async throws
}

public protocol HikerProfileRepositoryProtocol: Sendable {
    func getProfile() async throws -> HikerProfile?
    func save(profile: HikerProfile) async throws
    func updateBeaconId(_ beaconId: String) async throws
}

public protocol PeerTrackingRepositoryProtocol: Sendable {
    func getActivePeers() async throws -> [PeerBeaconData]
    func update(peer: PeerBeaconData) async throws
    func pruneInactivePeers(olderThan date: Date) async throws
}

// MARK: - Protocolos de Servicios de Hardware (CoreBluetooth y CoreLocation)

public protocol BleBeaconBroadcasterProtocol: Sendable {
    func startBroadcasting(beaconId: String, latitude: Double, longitude: Double) async -> Bool
    func stopBroadcasting()
    var isBroadcasting: Bool { get }
}

public protocol BleBeaconScannerProtocol: Sendable {
    func startScanning(onPeerDetected: @escaping @Sendable (PeerBeaconData) -> Void) -> Bool
    func stopScanning()
    var isScanning: Bool { get }
}

public protocol LocationTrackerProtocol: Sendable {
    func startTracking(distanceFilterMeters: Double, onUpdate: @escaping @Sendable (Double, Double, Double) -> Void)
    func stopTracking()
    var isTracking: Bool { get }
}

// MARK: - Protocolo de Sincronización en la Nube

public protocol CloudSyncProtocol: Sendable {
    func pushLocalChanges() async throws
    func pullRemoteChanges() async throws
    func uploadPhoto(fromFileURL url: URL) async throws -> String
}
