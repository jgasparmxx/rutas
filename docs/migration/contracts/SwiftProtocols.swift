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
    public var isPublic: Bool
    public var syncTimestamp: Int64
}

public struct RoutePoint: Identifiable, Codable, Sendable {
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
}

public struct RouteTrace: Identifiable, Codable, Sendable {
    public let id: Int
    public let routeId: String
    public let order: Int
    public let latitude: Double
    public let longitude: Double
    public let altitude: Double
}

public struct HikerProfile: Identifiable, Codable, Sendable {
    public var id: String { email }
    public let email: String
    public var name: String
    public var phone: String
    public var role: String
    public var beaconId: String?
    public var experienceLevel: String
    public var bloodType: String?
    public var emergencyPhone1: String
    public var emergencyPhone2: String?
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
