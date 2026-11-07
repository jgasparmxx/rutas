//
//  MainViewModel.swift
//  RutasSendero
//
//  ViewModel central para la pantalla principal (MainScreen) y el servicio de grabación.
//  Maneja el ciclo de vida del rastreo en segundo plano, cálculo de métricas en vivo,
//  y la interacción con MapKit y SwiftData.
//

import Foundation
import CoreLocation
import SwiftUI
import Observation
import MapKit
import UIKit

public enum RecordingState: String, Sendable, CaseIterable {
    case stopped = "Detenido"
    case recording = "Grabando"
    case paused = "Pausado"
}

public enum AppRole: String, Sendable, CaseIterable {
    case registrar = "registrar" // Trazar
    case guia = "guia"           // Guía
    case visor = "visor"         // Visor
    
    public var title: String {
        switch self {
        case .registrar: return "TRAZAR"
        case .guia: return "GUÍA"
        case .visor: return "VISOR"
        }
    }
}

public enum ActiveSheet: Identifiable, Sendable {
    case senderistaRegistration
    case localRoutes
    case newRoute
    case addPoint
    case routeDetails
    case nearbyPeers
    case soundAlerts
    case quickDictate
    case cloudRoutes
    case recordingDashboard
    
    public var id: String {
        switch self {
        case .senderistaRegistration: return "senderistaRegistration"
        case .localRoutes: return "localRoutes"
        case .newRoute: return "newRoute"
        case .addPoint: return "addPoint"
        case .routeDetails: return "routeDetails"
        case .nearbyPeers: return "nearbyPeers"
        case .soundAlerts: return "soundAlerts"
        case .quickDictate: return "quickDictate"
        case .cloudRoutes: return "cloudRoutes"
        case .recordingDashboard: return "recordingDashboard"
        }
    }
}

public enum MapStyleMode: String, CaseIterable, Identifiable {
    case standard = "Estándar"
    case satellite = "Satélite"
    case hybrid = "Híbrido"
    
    public var id: String { rawValue }
}

@Observable
public final class MainViewModel: @unchecked Sendable {
    
    // MARK: - Servicios
    public let locationTracker: LocationTracker
    public let bleBroadcaster: BleBeaconBroadcaster
    public let bleScanner: BleBeaconScanner
    public var rutasRepository: SwiftDataRutasRepository?
    
    // MARK: - Estado de Grabación
    public var recordingState: RecordingState = .stopped
    public var activeRoute: Route? = nil
    public var recordedTrace: [RouteTrace] = []
    public var recordedCoordinates: [CLLocationCoordinate2D] = []
    public var recordedPoints: [RoutePoint] = []
    
    // MARK: - Métricas en Vivo
    public var elapsedSeconds: Int64 = 0
    public var totalDistanceMeters: Double = 0.0
    public var elevationStats: ElevationStats = ElevationStats()
    public var caloriesBurned: Double = 0.0
    public var hikerWeightKg: Double = 70.0
    public var minimumRouteDistanceKm: Double = 4.0
    
    // MARK: - Balizas BLE y Compañeros Cercanos
    public var nearbyPeers: [String: PeerBeaconData] = [:]
    public var nearbyPeersList: [PeerBeaconData] {
        Array(nearbyPeers.values).sorted { $0.estimatedDistanceMeters < $1.estimatedDistanceMeters }
    }
    public var peerSenderistaProfiles: [String: HikerProfile] = [:]
    public var trackingRefreshIntervalSeconds: Double = 1.5
    public var acknowledgedEmergencies: Set<String> = []
    public var currentHikerProfile: HikerProfile? = nil
    public var isSavedLocally: Bool = false
    public var saveStatusMessage: String? = nil
    
    // MARK: - Modos y Balizas
    public var activeRole: AppRole = .visor
    public var isBeaconEmitting: Bool = false
    public var isStartingBeacon: Bool = false
    public var isEmergency911Active: Bool = false
    public var mapStyle: MapStyleMode = .standard
    public var isFollowingUser: Bool = false
    public var isPointsListReversed: Bool {
        get { isVisorReversed }
        set { isVisorReversed = newValue }
    }
    
    // MARK: - Simulación de Recorrido Virtual y Rutas de Consumo (1:1 Android)
    public var isRouteConsumptionOnly: Bool = false
    public var isVirtualTourActive: Bool = false
    public var virtualTourIndex: Int = 0
    public var isVirtualTourPaused: Bool = false
    public var isVisorReversed: Bool = false
    public var showVirtualTourPhotoCard: Bool = true
    private var virtualTourTask: Task<Void, Never>? = nil
    
    public var tourPoints: [RoutePoint] {
        isVisorReversed ? recordedPoints.reversed() : recordedPoints
    }
    
    // MARK: - Detalle de Punto y Visor de Fotos en Pantalla Completa (1:1 Android)
    public var selectedPointForDetail: RoutePoint? = nil
    public var pendingRouteForOrderSelection: Route? = nil
    public var isShowingVisorOrderDialog: Bool = false
    public var fullScreenPhotoPath: String? = nil
    public var isShowingFullScreenPhoto: Bool = false
    
    // MARK: - Estado de Sincronización y Guía de Senderos
    public var isSyncingRoutes: Bool = false
    public var targetGuideWaypoint: RoutePoint? = nil
    public var isGuidingActive: Bool = false
    public var guideDirectionForward: Bool = true
    public var guidanceStatusMessage: String? = nil
    public var isVoiceGuidanceEnabled: Bool = true
    /// Distancia máxima parametrizada para redirigir a Google Maps al iniciar Guía (1:1 Android: > 2.0 km)
    public var maxDistanceToStartGuideMeters: Double = 2000.0
    public var arrivalToleranceMeters: Double = 25.0
    
    @ObservationIgnored
    private var announcedGuideAlertKeys: Set<String> = []
    @ObservationIgnored
    private var lastGuideTtsTime: Date = .distantPast
    @ObservationIgnored
    private var lastGuideOffRouteWarningTime: Date = .distantPast
    @ObservationIgnored
    private var lastGuideDistanceToTarget: Double? = nil
    
    public var distanceToTargetGuideWaypoint: Double? {
        guard let target = targetGuideWaypoint, let current = locationTracker.currentLocation else { return nil }
        let targetLoc = CLLocation(latitude: target.latitude, longitude: target.longitude)
        return current.distance(from: targetLoc)
    }
    
    /// Desnivel restante estimado hacia el siguiente punto objetivo en Modo Guía (positivo = subida, negativo = bajada).
    public var remainingElevationToTargetGuideWaypoint: Int? {
        guard let target = targetGuideWaypoint else { return nil }
        let pts = recordedPoints
        let prevPt: RoutePoint?
        if let curIdx = pts.firstIndex(where: { $0.id == target.id }) {
            let prevIdx = guideDirectionForward ? (curIdx - 1) : (curIdx + 1)
            prevPt = (prevIdx >= 0 && prevIdx < pts.count) ? pts[prevIdx] : nil
        } else {
            prevPt = nil
        }
        return RouteStatsCalculator.estimateRemainingElevationToTarget(
            prevPoint: prevPt,
            targetPoint: target,
            currentLocation: locationTracker.currentLocation,
            remainingDistanceMeters: distanceToTargetGuideWaypoint
        )
    }
    
    @MainActor
    public func stopGuiding() {
        isGuidingActive = false
        targetGuideWaypoint = nil
        guidanceStatusMessage = nil
        announcedGuideAlertKeys.removeAll()
        lastGuideDistanceToTarget = nil
    }
    
    /// Jerarquía de roles: 'G' (Guía), 'A' (Admin), 'O' (Dueño) pueden crear y mapear rutas
    public var canCreateRoutes: Bool {
        guard let role = currentHikerProfile?.role.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() else {
            return false
        }
        return role == "G" || role == "A" || role == "O"
    }
    
    public var isGuiaRole: Bool {
        guard let role = currentHikerProfile?.role.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() else {
            return false
        }
        return role == "G"
    }
    
    // MARK: - Control de Pantalla Principal y Modales
    public var showMapScreen: Bool = false
    public var activeSheet: ActiveSheet? = nil
    
    public var isShowingSenderistaRegistration: Bool {
        get { activeSheet == .senderistaRegistration }
        set { activeSheet = newValue ? .senderistaRegistration : (activeSheet == .senderistaRegistration ? nil : activeSheet) }
    }
    public var localRoutesInitialTab: Int = 0
    public var isShowingLocalRoutesSheet: Bool {
        get { activeSheet == .localRoutes }
        set { activeSheet = newValue ? .localRoutes : (activeSheet == .localRoutes ? nil : activeSheet) }
    }
    public var isShowingNewRouteSheet: Bool {
        get { activeSheet == .newRoute }
        set { activeSheet = newValue ? .newRoute : (activeSheet == .newRoute ? nil : activeSheet) }
    }
    public var isShowingAddPointSheet: Bool {
        get { activeSheet == .addPoint }
        set { activeSheet = newValue ? .addPoint : (activeSheet == .addPoint ? nil : activeSheet) }
    }
    public var isShowingRouteDetailsSheet: Bool {
        get { activeSheet == .routeDetails }
        set { activeSheet = newValue ? .routeDetails : (activeSheet == .routeDetails ? nil : activeSheet) }
    }
    public var isShowingNearbyPeersSheet: Bool {
        get { activeSheet == .nearbyPeers }
        set { activeSheet = newValue ? .nearbyPeers : (activeSheet == .nearbyPeers ? nil : activeSheet) }
    }
    public var isShowingSoundAlertsSheet: Bool {
        get { activeSheet == .soundAlerts }
        set { activeSheet = newValue ? .soundAlerts : (activeSheet == .soundAlerts ? nil : activeSheet) }
    }
    public var isShowingDictateSheet: Bool {
        get { activeSheet == .quickDictate }
        set { activeSheet = newValue ? .quickDictate : (activeSheet == .quickDictate ? nil : activeSheet) }
    }
    public var isShowingCloudRoutesSheet: Bool {
        get { activeSheet == .cloudRoutes }
        set { activeSheet = newValue ? .cloudRoutes : (activeSheet == .cloudRoutes ? nil : activeSheet) }
    }
    public var isShowingRecordingDashboard: Bool {
        get { activeSheet == .recordingDashboard }
        set { activeSheet = newValue ? .recordingDashboard : (activeSheet == .recordingDashboard ? nil : activeSheet) }
    }
    
    public var agrupacionLogoImage: UIImage? = nil
    public var agrupacionLogoData: Data? = nil
    
    public var isRegistrationMandatory: Bool = false
    public var isShowingFinishRouteConfirm: Bool = false
    public var isShowingRouteTooShortAlert: Bool = false
    public var isShowingPointsListSheet: Bool = false
    public var isPocketModeActive: Bool = false
    public var isShowingAboutSheet: Bool = false
    public var availableLocalRoutes: [Route] = []
    
    // MARK: - Timer Privado
    @ObservationIgnored
    private var timer: Timer? = nil
    
    // MARK: - Inicialización
    public init(
        locationTracker: LocationTracker = LocationTracker(),
        bleBroadcaster: BleBeaconBroadcaster = BleBeaconBroadcaster(),
        bleScanner: BleBeaconScanner = BleBeaconScanner(),
        rutasRepository: SwiftDataRutasRepository? = nil
    ) {
        self.locationTracker = locationTracker
        self.bleBroadcaster = bleBroadcaster
        self.bleScanner = bleScanner
        self.rutasRepository = rutasRepository
        
        // Si Bluetooth se apaga mientras el Faro está activo, reflejar estado real
        self.bleBroadcaster.onBroadcastInterrupted = { [weak self] reason in
            Task { @MainActor in
                guard let self = self else { return }
                self.isBeaconEmitting = false
                self.isEmergency911Active = false
                self.saveStatusMessage = reason
            }
        }
        
        // Iniciar rastreo básico para visualización en mapa
        self.startLocationUpdates()
        
        // Iniciar escaneo BLE de balizas de compañeros
        self.startBleScanning()
        
        // Sincronizar/descargar logo de la agrupación por defecto ("MH" 1:1 Android)
        self.syncAgrupacionLogo(idAgrupacion: "MH")
    }
    
    deinit {
        timer?.invalidate()
        bleBroadcaster.stopBroadcasting()
        bleScanner.stopScanning()
    }
    
    // MARK: - Manejo de Ubicación y GPS
    
    public func startLocationUpdates() {
        locationTracker.startTracking(distanceFilterMeters: 4.0) { [weak self] lat, lon, alt in
            guard let self = self else { return }
            Task { @MainActor in
                self.processLiveCoordinate(latitude: lat, longitude: lon, altitude: alt)
            }
        }
    }
    
    public func startBleScanning() {
        _ = bleScanner.startScanning { [weak self] peer in
            guard let self = self else { return }
            Task { @MainActor in
                let normalizedId = peer.beaconId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                guard !normalizedId.isEmpty else { return }
                
                // Ignorar nuestra propia baliza si el radio local recibe eco
                let ownId = (self.currentHikerProfile?.beaconId ?? "")
                    .replacingOccurrences(of: ":", with: "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .lowercased()
                if !ownId.isEmpty && normalizedId == ownId {
                    return
                }
                
                let previousEmergency = self.nearbyPeers[normalizedId]?.isEmergency ?? false
                let normalizedPeer = PeerBeaconData(
                    beaconId: normalizedId,
                    latitude: peer.latitude,
                    longitude: peer.longitude,
                    rssi: peer.rssi,
                    txPower: peer.txPower,
                    estimatedDistanceMeters: peer.estimatedDistanceMeters,
                    lastDetectedTimestamp: peer.lastDetectedTimestamp,
                    isEmergency: peer.isEmergency
                )
                self.nearbyPeers[normalizedId] = normalizedPeer
                self.resolvePeerProfile(for: normalizedId)
                
                if !peer.isEmergency {
                    self.acknowledgedEmergencies.remove(normalizedId)
                }
                
                // Si el compañero activó 911 y no ha sido silenciado, disparar alerta inmediata (1:1 Android)
                if peer.isEmergency && !previousEmergency && !self.acknowledgedEmergencies.contains(normalizedId) {
                    let displayName = self.peerSenderistaProfiles[normalizedId]?.name ?? normalizedId.uppercased()
                    TextToSpeechService.shared.speak("¡Alerta! Emergencia 911 detectada de compañero \(displayName) a \(Int(peer.estimatedDistanceMeters)) metros.")
                    self.saveStatusMessage = "¡EMERGENCIA 911 DE \(displayName)!"
                }
            }
        }
    }
    
    @MainActor
    public func refreshBleScanning() {
        bleScanner.refreshScan()
    }
    
    @MainActor
    public func acknowledgePeerEmergency(beaconId: String) {
        let clean = beaconId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        acknowledgedEmergencies.insert(clean)
        let name = peerSenderistaProfiles[clean]?.name ?? clean.uppercased()
        saveStatusMessage = "Alarma silenciada para \(name)"
    }
    
    @MainActor
    private func processLiveCoordinate(latitude: Double, longitude: Double, altitude: Double) {
        // Si la baliza está transmitiendo, actualizar coordenadas emitidas
        if isBeaconEmitting {
            bleBroadcaster.updateCoordinates(latitude: latitude, longitude: longitude)
        }
        
        // Si el Modo Guía está activo, evaluar avance, alertas y distancia + subida/bajada al siguiente punto
        if isGuidingActive && activeRole == .guia {
            evaluateGuideProgress(latitude: latitude, longitude: longitude, altitude: altitude)
        }
        
        guard recordingState == .recording else { return }
        
        let newCoord = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        
        // Filtrar jitter mínimo respecto al último punto de la traza (< 1.5 metros)
        if let lastCoord = recordedCoordinates.last {
            let loc1 = CLLocation(latitude: lastCoord.latitude, longitude: lastCoord.longitude)
            let loc2 = CLLocation(latitude: latitude, longitude: longitude)
            if loc1.distance(from: loc2) < 1.5 {
                return
            }
        }
        
        let newTrace = RouteTrace(
            id: recordedTrace.count + 1,
            routeId: activeRoute?.id ?? "temp",
            order: recordedTrace.count + 1,
            latitude: latitude,
            longitude: longitude,
            altitude: altitude
        )
        
        // Actualizar distancia euclidiana 3D
        if let lastPoint = recordedTrace.last {
            let segDist = RouteStatsCalculator.distance3D(
                from: lastPoint.latitude, lon1: lastPoint.longitude, alt1: lastPoint.altitude,
                to: latitude, lon2: longitude, alt2: altitude
            )
            totalDistanceMeters += segDist
        }
        
        recordedTrace.append(newTrace)
        recordedCoordinates.append(newCoord)
        
        // Actualizar estadísticas de elevación y calorías
        elevationStats = RouteStatsCalculator.calculateElevationStats(tracePoints: recordedTrace)
        caloriesBurned = RouteStatsCalculator.calculateCalories(weightKg: hikerWeightKg, distanceMeters: totalDistanceMeters)
    }
    
    // MARK: - Ciclo de Vida de Grabación
    
    @MainActor
    public func startRecording(name: String, description: String = "") {
        let routeId = UUID().uuidString
        let dateString = ISO8601DateFormatter().string(from: Date())
        let loc = locationTracker.currentLocation ?? CLLocation(latitude: 19.4326, longitude: -99.1332)
        
        let route = Route(
            id: routeId,
            name: name.isEmpty ? "Sendero \(Date().formatted(date: .abbreviated, time: .shortened))" : name,
            date: dateString,
            originLatitude: loc.coordinate.latitude,
            originLongitude: loc.coordinate.longitude,
            descriptionText: description,
            gpsDistanceMeters: 0.0,
            beaconDistanceMeters: 0.0,
            startTime: dateString,
            endTime: nil,
            durationSeconds: 0,
            elevationGain: 0.0,
            elevationLoss: 0.0,
            climbsCount: 0,
            descentsCount: 0,
            isPublic: true,
            syncTimestamp: Int64(Date().timeIntervalSince1970 * 1000),
            creatorPhone: currentHikerProfile?.phone ?? "",
            importDate: nil
        )
        
        self.activeRoute = route
        self.activeRole = .registrar
        self.isRouteConsumptionOnly = false
        self.isVirtualTourActive = false
        self.showMapScreen = false // Jamás abrir el mapa automáticamente (1:1 Android)
        self.recordedTrace = []
        self.recordedCoordinates = []
        self.recordedPoints = []
        self.totalDistanceMeters = 0.0
        self.elevationStats = ElevationStats()
        self.caloriesBurned = 0.0
        self.elapsedSeconds = 0
        self.recordingState = .recording
        
        // 1:1 Android: Registrar primer punto automáticamente ("Punto Inicial")
        let startPoint = RoutePoint(
            id: 1,
            routeId: routeId,
            timestamp: dateString,
            latitude: loc.coordinate.latitude,
            longitude: loc.coordinate.longitude,
            altitude: loc.altitude,
            descriptionText: "Punto Inicial: Inicio del sendero",
            photoUrl: nil,
            isRouteEnd: false,
            hasConnectivity: true
        )
        self.recordedPoints.append(startPoint)
        
        let firstTrace = RouteTrace(
            id: 1,
            routeId: routeId,
            order: 1,
            latitude: loc.coordinate.latitude,
            longitude: loc.coordinate.longitude,
            altitude: loc.altitude
        )
        self.recordedTrace.append(firstTrace)
        self.recordedCoordinates.append(loc.coordinate)
        
        // Auto-guardar en SwiftData
        if let repo = rutasRepository {
            let r = route
            let pt = startPoint
            let tr = firstTrace
            Task {
                try? await repo.save(route: r)
                try? await repo.save(point: pt)
                try? await repo.save(traceBatch: [tr])
            }
        }
        
        startTimer()
    }
    
    @MainActor
    public func pauseRecording() {
        recordingState = .paused
        stopTimer()
    }
    
    @MainActor
    public func resumeRecording() {
        recordingState = .recording
        startTimer()
    }
    
    @MainActor
    public func addPoint(description: String, photoUrl: String? = nil, isFinRuta: Bool = false, estado: Int = 1) {
        guard !isRouteConsumptionOnly else {
            print("⚠️ [RutasSendero] Ruta de solo lectura / consumo: no se pueden agregar puntos.")
            return
        }
        let loc = locationTracker.currentLocation ?? CLLocation(latitude: 19.4326, longitude: -99.1332)
        let dateString = ISO8601DateFormatter().string(from: Date())
        let routeId = activeRoute?.id ?? "temp"
        let pointNumber = recordedPoints.count + 1
        
        let pointDesc = description.isEmpty ? (isFinRuta ? "Fin de la Ruta" : "Punto de control #\(pointNumber)") : description
        
        let newPoint = RoutePoint(
            id: pointNumber,
            routeId: routeId,
            timestamp: dateString,
            latitude: loc.coordinate.latitude,
            longitude: loc.coordinate.longitude,
            altitude: loc.altitude,
            descriptionText: pointDesc,
            photoUrl: photoUrl,
            isRouteEnd: isFinRuta,
            hasConnectivity: true,
            estado: estado
        )
        
        recordedPoints.append(newPoint)
        
        // Auto-guardar punto en SwiftData
        if let repo = rutasRepository {
            let pt = newPoint
            Task {
                try? await repo.save(point: pt)
            }
        }
        
        if isFinRuta {
            finishRoute(force: true)
        }
    }
    
    @MainActor
    public func appendVoiceToLastPoint(text: String) {
        guard !recordedPoints.isEmpty else { return }
        let lastIndex = recordedPoints.count - 1
        var lastPoint = recordedPoints[lastIndex]
        if lastPoint.descriptionText.isEmpty {
            lastPoint.descriptionText = text
        } else {
            lastPoint.descriptionText += " - " + text
        }
        recordedPoints[lastIndex] = lastPoint
        
        if let repo = rutasRepository {
            let pt = lastPoint
            Task {
                try? await repo.save(point: pt)
            }
        }
    }
    
    @MainActor
    public func requestFinishRoute() {
        let currentKm = totalDistanceMeters / 1000.0
        if currentKm < minimumRouteDistanceKm {
            isShowingRouteTooShortAlert = true
        } else {
            isShowingFinishRouteConfirm = true
        }
    }
    
    @MainActor
    public func finishRoute(force: Bool = false) {
        stopTimer()
        recordingState = .stopped
        self.activeRole = .visor
        
        if var route = activeRoute {
            let endTimeString = ISO8601DateFormatter().string(from: Date())
            route.endTime = endTimeString
            route.durationSeconds = elapsedSeconds
            route.gpsDistanceMeters = totalDistanceMeters
            route.beaconDistanceMeters = RouteStatsCalculator.calculatePointsDistance(points: recordedPoints)
            route.elevationGain = elevationStats.elevationGain
            route.elevationLoss = elevationStats.elevationLoss
            route.climbsCount = elevationStats.climbsCount
            route.descentsCount = elevationStats.descentsCount
            self.activeRoute = route
            
            // Persistir ruta completa, puntos y traza en SwiftData
            if let repo = rutasRepository {
                let savedRoute = route
                let pts = self.recordedPoints
                let trs = self.recordedTrace
                Task {
                    do {
                        try await repo.save(route: savedRoute)
                        for p in pts {
                            try await repo.save(point: p)
                        }
                        try await repo.save(traceBatch: trs)
                        await MainActor.run {
                            self.isSavedLocally = true
                            self.saveStatusMessage = "Ruta guardada localmente en SwiftData"
                        }
                        print("✅ [SwiftData] Ruta '\(savedRoute.name)' persistida exitosamente (\(pts.count) puntos, \(trs.count) trazas)")
                        
                        // Sincronización automática a la nube si el perfil está disponible (1:1 Android)
                        if let profile = self.currentHikerProfile {
                            Task.detached(priority: .background) {
                                do {
                                    try await CloudSyncService.shared.uploadRouteToCloud(
                                        routeId: savedRoute.id,
                                        from: repo,
                                        creator: profile
                                    )
                                    print("☁️ [CloudSync] Ruta '\(savedRoute.name)' sincronizada automáticamente con la nube.")
                                } catch {
                                    print("⚠️ [CloudSync] Error sincronizando ruta con la nube: \(error.localizedDescription)")
                                }
                            }
                        }
                    } catch {
                        await MainActor.run {
                            self.saveStatusMessage = "Error al guardar: \(error.localizedDescription)"
                        }
                        print("❌ [SwiftData] Error al guardar ruta: \(error)")
                    }
                }
            }
        }
    }
    
    @MainActor
    public func cancelRecording() {
        stopTimer()
        recordingState = .stopped
        self.activeRole = .visor
        activeRoute = nil
        recordedTrace = []
        recordedCoordinates = []
        recordedPoints = []
        totalDistanceMeters = 0.0
        elapsedSeconds = 0
    }
    
    // MARK: - Manejo del Temporizador
    
    private func startTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            Task { @MainActor in
                if self.recordingState == .recording {
                    self.elapsedSeconds += 1
                }
            }
        }
    }
    
    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }
    
    // MARK: - Baliza y Emergencia
    
    @MainActor
    private func ensureValidBeaconId() async -> String? {
        guard var profile = currentHikerProfile else { return nil }
        let existing = (profile.beaconId ?? "")
            .replacingOccurrences(of: ":", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .filter { $0.isHexDigit }
        
        if existing.count >= 12 {
            return String(existing.prefix(12))
        }
        
        let generated = await CloudSyncService.shared.generateUniqueBeaconId()
        profile.beaconId = generated
        self.currentHikerProfile = profile
        
        if let repo = rutasRepository {
            try? await repo.updateBeaconId(generated)
        }
        let syncedProfile = profile
        Task.detached(priority: .background) {
            try? await CloudSyncService.shared.syncSenderista(profile: syncedProfile)
        }
        return generated
    }
    
    private func beaconFailureMessage(for code: Int) -> String {
        switch code {
        case -1:
            return "Bluetooth apagado o hardware BLE no disponible. Activa Bluetooth en el iPhone."
        case -2:
            return "No se pudo inicializar el paquete del Faro BLE."
        case -3:
            return "Permiso de Bluetooth denegado. Actívalo en Ajustes > RutasSendero."
        default:
            return bleBroadcaster.lastBroadcastError ?? "Error al iniciar emisión de Faro (Código \(code))."
        }
    }
    
    @MainActor
    public func toggleBeacon() {
        guard !isStartingBeacon else { return }
        
        if isBeaconEmitting {
            bleBroadcaster.stopBroadcasting()
            isBeaconEmitting = false
            if isEmergency911Active {
                isEmergency911Active = false
            }
            bleScanner.refreshScan()
            saveStatusMessage = "Emisión de Faro detenida"
            return
        }
        
        guard currentHikerProfile != nil else {
            saveStatusMessage = "Por favor, registra el perfil del senderista primero."
            isShowingSenderistaRegistration = true
            return
        }
        
        isStartingBeacon = true
        Task { @MainActor in
            defer { self.isStartingBeacon = false }
            
            guard let bId = await ensureValidBeaconId() else {
                self.isBeaconEmitting = false
                self.saveStatusMessage = "El senderista no tiene ID de Faro disponible."
                return
            }
            
            let realLoc = self.locationTracker.currentLocation
            let lat = realLoc?.coordinate.latitude ?? 19.2458
            let lon = realLoc?.coordinate.longitude ?? -103.7251
            
            let code = await self.bleBroadcaster.startBroadcastingWithCode(
                beaconId: bId,
                latitude: lat,
                longitude: lon,
                isEmergency: self.isEmergency911Active,
                hasRealLocation: realLoc != nil
            )
            
            if code == 0 {
                self.isBeaconEmitting = true
                self.bleScanner.refreshScan()
                self.saveStatusMessage = "Faro ENCENDIDO (ID: \(bId.uppercased()))"
            } else {
                self.isBeaconEmitting = false
                self.saveStatusMessage = self.beaconFailureMessage(for: code)
            }
        }
    }
    
    @MainActor
    public func toggleEmergency911() {
        guard !isStartingBeacon else { return }
        
        guard currentHikerProfile != nil else {
            saveStatusMessage = "No se puede emitir 911 sin perfil de senderista."
            isShowingSenderistaRegistration = true
            return
        }
        
        let activatingEmergency = !isEmergency911Active
        isStartingBeacon = true
        
        Task { @MainActor in
            defer { self.isStartingBeacon = false }
            
            guard let bId = await ensureValidBeaconId() else {
                self.saveStatusMessage = "No se puede emitir 911 sin ID de Faro."
                return
            }
            
            let realLoc = self.locationTracker.currentLocation
            let lat = realLoc?.coordinate.latitude ?? 19.2458
            let lon = realLoc?.coordinate.longitude ?? -103.7251
            
            if activatingEmergency {
                let code = await self.bleBroadcaster.startBroadcastingWithCode(
                    beaconId: bId,
                    latitude: lat,
                    longitude: lon,
                    isEmergency: true,
                    hasRealLocation: realLoc != nil
                )
                if code == 0 {
                    self.isEmergency911Active = true
                    self.isBeaconEmitting = true
                    self.bleScanner.refreshScan()
                    self.saveStatusMessage = "¡EMERGENCIA 911 ACTIVADA!"
                    TextToSpeechService.shared.speak("¡Emergencia 911 activada! Transmitiendo señal de auxilio por baliza a compañeros cercanos.")
                } else {
                    self.isEmergency911Active = false
                    self.saveStatusMessage = self.beaconFailureMessage(for: code)
                }
            } else {
                self.isEmergency911Active = false
                self.saveStatusMessage = "Emergencia cancelada"
                TextToSpeechService.shared.speak("Emergencia cancelada.")
                if self.isBeaconEmitting {
                    let code = await self.bleBroadcaster.startBroadcastingWithCode(
                        beaconId: bId,
                        latitude: lat,
                        longitude: lon,
                        isEmergency: false,
                        hasRealLocation: realLoc != nil
                    )
                    if code != 0 {
                        self.isBeaconEmitting = false
                    }
                    self.bleScanner.refreshScan()
                } else {
                    self.bleBroadcaster.stopBroadcasting()
                    self.bleScanner.refreshScan()
                }
            }
        }
    }
    
    /// Simula la detección de un compañero cercano (ideal para pruebas y validación visual)
    public func simulatePeerDetected(beaconId: String = "C0QU1M4TL4N1", distanceMeters: Double = 18.5) {
        let cleanId = beaconId.lowercased()
        let curLat = locationTracker.currentLocation?.coordinate.latitude ?? 19.2458
        let curLon = locationTracker.currentLocation?.coordinate.longitude ?? -103.7251
        let peer = PeerBeaconData(
            beaconId: cleanId,
            latitude: curLat + 0.0003,
            longitude: curLon + 0.0004,
            rssi: -62,
            txPower: -59,
            estimatedDistanceMeters: distanceMeters,
            lastDetectedTimestamp: Date()
        )
        nearbyPeers[cleanId] = peer
        resolvePeerProfile(for: cleanId)
    }
    
    /// Resuelve el nombre y datos de un compañero senderista desde PostgREST en la nube por su baliza BLE
    public func resolvePeerProfile(for beaconId: String) {
        let clean = beaconId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !clean.isEmpty, peerSenderistaProfiles[clean] == nil else { return }
        Task {
            if let profile = await CloudSyncService.shared.fetchSenderistaByBeaconId(idBeacon: clean) {
                await MainActor.run {
                    self.peerSenderistaProfiles[clean] = profile
                    print("☁️ [BLE] Identidad de baliza \(clean) resuelta desde la nube: \(profile.name)")
                }
            }
        }
    }
    
    /// Sincroniza y descarga el logo de la agrupación desde PostgREST en la nube (1:1 Android)
    public func syncAgrupacionLogo(idAgrupacion: String? = nil) {
        let rawId = idAgrupacion ?? currentHikerProfile?.idAgrupacion ?? "MH"
        let id = rawId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty, id != "@&" else {
            self.agrupacionLogoImage = nil
            self.agrupacionLogoData = nil
            return
        }
        
        let fileManager = FileManager.default
        let cacheDir = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first
        let localFile = cacheDir?.appendingPathComponent("logo_agrupacion_\(id.lowercased()).png")
        
        // 1. Revisar si ya está en caché local
        if let path = localFile?.path, fileManager.fileExists(atPath: path),
           let data = try? Data(contentsOf: localFile!),
           let img = UIImage(data: data) {
            self.agrupacionLogoData = data
            self.agrupacionLogoImage = img
            return
        }
        
        // 2. Descargar de PostgREST
        Task {
            if let data = await CloudSyncService.shared.fetchAgrupacionLogo(idAgrupacion: id) {
                if let fileUrl = localFile {
                    try? data.write(to: fileUrl)
                }
                if let img = UIImage(data: data) {
                    await MainActor.run {
                        self.agrupacionLogoData = data
                        self.agrupacionLogoImage = img
                        print("☁️ [Agrupación] Logo descargado y guardado para '\(id)'")
                    }
                }
            }
        }
    }
    
    // MARK: - Gestión de Perfil de Senderista (Onboarding Obligatorio)
    
    public func checkSenderistaRegistration() async {
        guard let repo = rutasRepository else {
            print("⚠️ [checkSenderistaRegistration] rutasRepository es nil, asignando activeSheet")
            await MainActor.run {
                self.isRegistrationMandatory = true
                self.activeSheet = .senderistaRegistration
            }
            return
        }
        do {
            if let profile = try await repo.getProfile() {
                await MainActor.run {
                    self.currentHikerProfile = profile
                    self.hikerWeightKg = profile.weightKg
                    self.activeRole = (profile.role.uppercased() == "G") ? .guia : .visor
                    self.activeSheet = nil
                    self.isRegistrationMandatory = false
                    self.syncAgrupacionLogo(idAgrupacion: profile.idAgrupacion)
                }
                // 1:1 Android (RutasViewModel.kt:448): Si falta id_beacon válido, generarlo y sincronizarlo
                let cleanBeacon = (profile.beaconId ?? "").replacingOccurrences(of: ":", with: "").trimmingCharacters(in: .whitespacesAndNewlines).filter { $0.isHexDigit }
                if cleanBeacon.count < 12 {
                    _ = await self.ensureValidBeaconId()
                }
                await self.loadLocalRoutes()
                print("✅ [checkSenderistaRegistration] Perfil existente: \(profile.name) (Rol: \(profile.role))")
            } else {
                // Primera vez: No existe perfil local en SwiftData -> Forzar Registro de Senderista
                await MainActor.run {
                    self.isRegistrationMandatory = true
                    self.activeSheet = .senderistaRegistration
                }
                print("ℹ️ [checkSenderistaRegistration] Sin perfil local, presentando registro obligatorio")
            }
        } catch {
            print("❌ [SwiftData] Error verificando perfil de senderista: \(error)")
            await MainActor.run {
                self.isRegistrationMandatory = true
                self.activeSheet = .senderistaRegistration
            }
        }
    }
    
    public func saveSenderistaProfile(_ profile: HikerProfile) async {
        guard let repo = rutasRepository else { return }
        do {
            var updatedProfile = profile
            // Para pruebas y según requerimiento, el rol por defecto se asigna 'G' (Guía con permisos completos)
            if updatedProfile.role.isEmpty || updatedProfile.role == "S" {
                updatedProfile.role = "G"
            }
            let cleanBeacon = updatedProfile.beaconId?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if cleanBeacon.count < 12 {
                updatedProfile.beaconId = await CloudSyncService.shared.generateUniqueBeaconId()
            }
            
            let savedProfile = updatedProfile
            try await repo.save(profile: savedProfile)
            await MainActor.run {
                self.currentHikerProfile = savedProfile
                self.hikerWeightKg = savedProfile.weightKg
                self.activeRole = (savedProfile.role.uppercased() == "G") ? .guia : .visor
                self.isShowingSenderistaRegistration = false
                self.isRegistrationMandatory = false
                self.syncAgrupacionLogo(idAgrupacion: savedProfile.idAgrupacion)
            }
            print("✅ [SwiftData] Perfil de senderista registrado exitosamente: \(savedProfile.name) (Rol: \(savedProfile.role))")
            
            // Sincronización en segundo plano con PostgREST en la nube
            Task.detached(priority: .background) {
                do {
                    try await CloudSyncService.shared.syncSenderista(profile: savedProfile)
                } catch {
                    print("⚠️ [CloudSync] Error sincronizando perfil con la nube: \(error.localizedDescription)")
                }
            }
        } catch {
            print("❌ [SwiftData] Error guardando perfil: \(error)")
        }
    }
    
    // MARK: - Subir Ruta Local Cifrada a la Nube (PostgREST)
    public func uploadRouteToCloud(_ route: Route) async -> Bool {
        guard let repo = rutasRepository, let profile = currentHikerProfile else { return false }
        do {
            try await CloudSyncService.shared.uploadRouteToCloud(
                routeId: route.id,
                from: repo,
                creator: profile
            )
            await MainActor.run {
                self.saveStatusMessage = "¡Ruta '\(route.name)' subida a la nube exitosamente!"
            }
            return true
        } catch {
            print("❌ [CloudSync] Error subiendo ruta a la nube: \(error.localizedDescription)")
            await MainActor.run {
                self.saveStatusMessage = "Error al subir a la nube: \(error.localizedDescription)"
            }
            return false
        }
    }
    
    // MARK: - Gestión y Selección de Rutas Locales
    
    public func loadLocalRoutes() async {
        guard let repo = rutasRepository else { return }
        do {
            let routes = try await repo.getAllRoutes()
            await MainActor.run {
                self.availableLocalRoutes = routes
            }
        } catch {
            print("❌ [SwiftData] Error cargando rutas locales: \(error)")
        }
    }
    
    public func selectRouteForTracking(_ route: Route, reversed: Bool = false, showMap: Bool = false) async {
        let pts = (try? await rutasRepository?.getPoints(forRouteId: route.id)) ?? []
        let trs = (try? await rutasRepository?.getTrace(forRouteId: route.id)) ?? []
        await MainActor.run {
            self.activeRoute = route
            let userPhone = self.currentHikerProfile?.phone ?? ""
            let isOwn = route.isOwnRoute(userPhone: userPhone)
            // Si la ruta no pertenece al senderista dueño, es de solo consumo (no se puede modificar)
            self.isRouteConsumptionOnly = !isOwn
            // Por omisión se activa el modo VISOR para ver la ruta (1:1 Android)
            self.activeRole = .visor
            self.isGuidingActive = false
            self.isFollowingUser = false
            self.isVisorReversed = reversed
            self.isVirtualTourActive = false
            self.virtualTourIndex = 0
            self.isVirtualTourPaused = false
            self.recordedPoints = pts
            self.recordedTrace = trs
            self.recordedCoordinates = trs.isEmpty
                ? pts.map { CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) }
                : trs.map { CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) }
            if trs.isEmpty {
                self.elevationStats = ElevationStats(
                    elevationGain: route.elevationGain,
                    elevationLoss: route.elevationLoss,
                    climbsCount: route.climbsCount,
                    descentsCount: route.descentsCount
                )
            } else {
                self.elevationStats = RouteStatsCalculator.calculateElevationStats(tracePoints: trs)
            }
            self.recordingState = .stopped
            self.showMapScreen = showMap
            self.isShowingLocalRoutesSheet = false
            self.isShowingVisorOrderDialog = false
            self.pendingRouteForOrderSelection = nil
        }
    }
    
    // MARK: - Iniciar Guía de Ruta con Verificación de Distancia (1:1 Android vs Especificación)
    
    @MainActor
    public func openGoogleMapsDirections(lat: Double, lon: Double, label: String = "Inicio") {
        let googleMapsAppUrl = URL(string: "comgooglemaps://?daddr=\(lat),\(lon)&directionsmode=driving")!
        let googleMapsWebUrl = URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(lat),\(lon)")!
        let appleMapsUrl = URL(string: "maps://?daddr=\(lat),\(lon)")!
        
        if UIApplication.shared.canOpenURL(googleMapsAppUrl) {
            UIApplication.shared.open(googleMapsAppUrl)
        } else if UIApplication.shared.canOpenURL(googleMapsWebUrl) {
            UIApplication.shared.open(googleMapsWebUrl)
        } else {
            UIApplication.shared.open(appleMapsUrl)
        }
    }
    
    @MainActor
    public func startGuideRoute(_ route: Route, toStart: Bool) async {
        let pts = (try? await rutasRepository?.getPoints(forRouteId: route.id)) ?? []
        let trs = (try? await rutasRepository?.getTrace(forRouteId: route.id)) ?? []
        
        let orderedPts = toStart ? Array(pts.reversed()) : pts
        
        // Coordenada del punto de inicio según el sentido seleccionado (Ida vs Retorno)
        let startLat = orderedPts.first?.latitude ?? route.originLatitude
        let startLon = orderedPts.first?.longitude ?? route.originLongitude
        
        #if targetEnvironment(simulator)
        // En el simulador de iOS, si la ubicación del simulador no se configuró y está por omisión
        // en otro país (como San Francisco / Cupertino a > 50km de la ruta en México),
        // simular la ubicación en el inicio de la ruta para permitir la prueba fluida de la guía
        if let userLoc = locationTracker.currentLocation {
            let distFromRoute = userLoc.distance(from: CLLocation(latitude: startLat, longitude: startLon))
            if distFromRoute > 50_000 {
                locationTracker.simulateLocation(latitude: startLat, longitude: startLon)
            }
        } else {
            locationTracker.simulateLocation(latitude: startLat, longitude: startLon)
        }
        #endif
        
        let currentLoc = locationTracker.currentLocation
        
        // Distancia al punto de inicio objetivo
        let distToStartMeters: Double
        if let userLoc = currentLoc {
            distToStartMeters = userLoc.distance(from: CLLocation(latitude: startLat, longitude: startLon))
        } else {
            distToStartMeters = 0.0
        }
        
        // Distancia mínima a cualquiera de los puntos del sendero para orientar al senderista (1:1 Android MainScreen.kt:2704)
        var minDistanceMeters = distToStartMeters
        var closestPoint: RoutePoint? = orderedPts.first
        if let userLoc = currentLoc {
            for pt in pts {
                let ptLoc = CLLocation(latitude: pt.latitude, longitude: pt.longitude)
                let d = userLoc.distance(from: ptLoc)
                if d < minDistanceMeters {
                    minDistanceMeters = d
                    closestPoint = pt
                }
            }
        }
        
        // Siempre se carga la ruta y se posiciona el mapa de la aplicación en el punto donde inicia
        self.activeRoute = route
        let userPhone = self.currentHikerProfile?.phone ?? ""
        self.isRouteConsumptionOnly = !route.isOwnRoute(userPhone: userPhone)
        self.activeRole = .guia
        self.isFollowingUser = false
        self.isVisorReversed = toStart
        self.guideDirectionForward = !toStart
        self.announcedGuideAlertKeys.removeAll()
        self.lastGuideDistanceToTarget = nil
        self.lastGuideTtsTime = Date()
        self.recordedPoints = pts
        self.recordedTrace = trs
        self.recordedCoordinates = trs.isEmpty
            ? pts.map { CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) }
            : trs.map { CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) }
        if trs.isEmpty {
            self.elevationStats = ElevationStats(
                elevationGain: route.elevationGain,
                elevationLoss: route.elevationLoss,
                climbsCount: route.climbsCount,
                descentsCount: route.descentsCount
            )
        } else {
            self.elevationStats = RouteStatsCalculator.calculateElevationStats(tracePoints: trs)
        }
        
        let isVeryFarFromTrail = (minDistanceMeters > maxDistanceToStartGuideMeters && currentLoc != nil)
        let selectedInitialTarget: RoutePoint? = isVeryFarFromTrail ? orderedPts.first : (closestPoint ?? orderedPts.first)
        
        self.targetGuideWaypoint = selectedInitialTarget
        self.isGuidingActive = true
        self.isShowingVisorOrderDialog = false
        self.pendingRouteForOrderSelection = nil
        self.showMapScreen = true
        
        let destText = toStart ? "el inicio (retorno)" : "el final (ida)"
        let destSpeech = toStart ? "el inicio" : "el final"
        
        // REGLA 1 (1:1 Android MainScreen.kt:2717): Si la distancia es muy grande (> 2.0 km parametrizados):
        // Redirigir a Google Maps para cómo llegar al inicio, dejando el mapa de la app colocado en el punto donde inicia.
        if isVeryFarFromTrail {
            let distKm = minDistanceMeters / 1000.0
            let distStr = String(format: "%.1f", distKm)
            
            self.guidanceStatusMessage = "Está muy lejos del sendero (a \(distStr) km). Acérquese para iniciar. Redirigiendo a Google Maps..."
            self.saveStatusMessage = self.guidanceStatusMessage
            
            openGoogleMapsDirections(lat: startLat, lon: startLon, label: "Inicio de \(route.name)")
        } else {
            if let cp = closestPoint, minDistanceMeters > 50.0 {
                let pointDistStr = RouteStatsCalculator.formatDistanceShort(minDistanceMeters)
                self.guidanceStatusMessage = "Estás a \(pointDistStr) de \(cp.name). Dirígete hacia él para seguir la ruta hacia \(destText)."
                self.saveStatusMessage = self.guidanceStatusMessage
            } else {
                self.guidanceStatusMessage = "Iniciando guía hacia \(destText) del sendero."
                self.saveStatusMessage = self.guidanceStatusMessage
            }
            
            // Locución inicial de Modo Guía con distancia y subida/bajada (1:1 Android GpsTrackingService.kt:1864-1881)
            if isVoiceGuidanceEnabled, let target = selectedInitialTarget {
                let initialTargetMsg: String
                if let userLoc = currentLoc {
                    let tLoc = CLLocation(latitude: target.latitude, longitude: target.longitude)
                    let d = userLoc.distance(from: tLoc)
                    let prevPt = previousPointInGuideOrder(for: target)
                    let delta = RouteStatsCalculator.estimateRemainingElevationToTarget(
                        prevPoint: prevPt,
                        targetPoint: target,
                        currentLocation: userLoc,
                        remainingDistanceMeters: d
                    )
                    let dSpoken = RouteStatsCalculator.formatDistanceSpoken(d)
                    let eSuffix = RouteStatsCalculator.formatElevationSpeechSuffix(delta)
                    initialTargetMsg = "Avance al punto: \(target.name), distancia a caminar \(dSpoken)\(eSuffix)."
                } else {
                    initialTargetMsg = "Avance al punto: \(target.name)."
                }
                TextToSpeechService.shared.speak("Iniciando guía hacia \(destSpeech) del sendero. \(initialTargetMsg)")
            }
        }
    }
    
    private func previousPointInGuideOrder(for target: RoutePoint) -> RoutePoint? {
        guard let curIdx = recordedPoints.firstIndex(where: { $0.id == target.id }) else { return nil }
        let prevIdx = guideDirectionForward ? (curIdx - 1) : (curIdx + 1)
        return (prevIdx >= 0 && prevIdx < recordedPoints.count) ? recordedPoints[prevIdx] : nil
    }
    
    @MainActor
    public func advanceGuideToNextWaypoint() {
        guard let currentTarget = targetGuideWaypoint else { return }
        guard let index = recordedPoints.firstIndex(where: { $0.id == currentTarget.id }) else { return }
        let nextIndex = guideDirectionForward ? (index + 1) : (index - 1)
        
        if nextIndex >= 0 && nextIndex < recordedPoints.count {
            let nextPunto = recordedPoints[nextIndex]
            targetGuideWaypoint = nextPunto
            lastGuideDistanceToTarget = nil
            lastGuideTtsTime = Date()
            
            let fromLoc = locationTracker.currentLocation ?? CLLocation(latitude: currentTarget.latitude, longitude: currentTarget.longitude)
            let nextLoc = CLLocation(latitude: nextPunto.latitude, longitude: nextPunto.longitude)
            let segDist = fromLoc.distance(from: nextLoc)
            let segDeltaAlt = RouteStatsCalculator.calculateElevationDeltaMeters(fromAlt: currentTarget.altitude, toAlt: nextPunto.altitude)
                ?? RouteStatsCalculator.estimateRemainingElevationToTarget(
                    prevPoint: currentTarget,
                    targetPoint: nextPunto,
                    currentLocation: locationTracker.currentLocation,
                    remainingDistanceMeters: segDist
                )
            let nextDistSpoken = RouteStatsCalculator.formatDistanceSpoken(segDist)
            let nextElevSuffix = RouteStatsCalculator.formatElevationSpeechSuffix(segDeltaAlt)
            if isVoiceGuidanceEnabled {
                TextToSpeechService.shared.speak("Punto \(currentTarget.name) localizado manualmente. Siguiente destino: \(nextPunto.name), distancia a caminar \(nextDistSpoken)\(nextElevSuffix).")
            }
        } else {
            if isVoiceGuidanceEnabled {
                TextToSpeechService.shared.speak("Felicidades. Has llegado a tu destino final, completando la ruta: \(currentTarget.name).")
            }
            stopGuiding()
        }
    }
    
    /// Motor en tiempo real de Modo Guía con avisos de distancia + subida/bajada (1:1 Android GpsTrackingService.kt:607-840)
    @MainActor
    private func evaluateGuideProgress(latitude: Double, longitude: Double, altitude: Double) {
        guard isGuidingActive, let target = targetGuideWaypoint, !recordedPoints.isEmpty else { return }
        guard let currentIndex = recordedPoints.firstIndex(where: { $0.id == target.id }) else { return }
        
        let userLoc = locationTracker.currentLocation ?? CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            altitude: altitude,
            horizontalAccuracy: 5.0,
            verticalAccuracy: RouteStatsCalculator.hasValidAltitude(altitude) ? 10.0 : -1.0,
            timestamp: Date()
        )
        let targetLoc = CLLocation(latitude: target.latitude, longitude: target.longitude)
        let distance = userLoc.distance(from: targetLoc)
        let isMovingAway: Bool = {
            if let prevDist = lastGuideDistanceToTarget {
                return distance > prevDist + 3.0
            }
            return false
        }()
        lastGuideDistanceToTarget = distance
        
        // Detectar si el senderista tomó un atajo hacia un punto más adelante en el sentido de la guía
        let aheadIndices: [Int] = guideDirectionForward
            ? Array((currentIndex + 1)..<recordedPoints.count)
            : Array((0..<currentIndex).reversed())
        
        var closestAheadIdx: Int? = nil
        var minAheadDist = Double.greatestFiniteMagnitude
        var minRemainingDist = distance
        for idx in aheadIndices {
            let pt = recordedPoints[idx]
            let d = userLoc.distance(from: CLLocation(latitude: pt.latitude, longitude: pt.longitude))
            if d < minAheadDist {
                minAheadDist = d
                closestAheadIdx = idx
            }
            if d < minRemainingDist {
                minRemainingDist = d
            }
        }
        
        if isMovingAway, let aheadIdx = closestAheadIdx, minAheadDist < distance - 20.0 {
            let nextPoint = recordedPoints[aheadIdx]
            let prevAheadPt = previousPointInGuideOrder(for: nextPoint)
            let nextDeltaAlt = RouteStatsCalculator.estimateRemainingElevationToTarget(
                prevPoint: prevAheadPt,
                targetPoint: nextPoint,
                currentLocation: userLoc,
                remainingDistanceMeters: minAheadDist
            )
            let nextDistSpoken = RouteStatsCalculator.formatDistanceSpoken(minAheadDist)
            let nextElevSuffix = RouteStatsCalculator.formatElevationSpeechSuffix(nextDeltaAlt)
            targetGuideWaypoint = nextPoint
            lastGuideDistanceToTarget = minAheadDist
            if isVoiceGuidanceEnabled {
                TextToSpeechService.shared.speak("Parece que tomaste un atajo o desvío. Redirigiendo al punto: \(nextPoint.name), a \(nextDistSpoken)\(nextElevSuffix).")
            }
            return
        }
        
        let prevPt = previousPointInGuideOrder(for: target)
        let remDeltaAlt = RouteStatsCalculator.estimateRemainingElevationToTarget(
            prevPoint: prevPt,
            targetPoint: target,
            currentLocation: userLoc,
            remainingDistanceMeters: distance
        )
        let remElevSuffix = RouteStatsCalculator.formatElevationSpeechSuffix(remDeltaAlt)
        
        // 1. Avisos anticipados de puntos especiales (50m y 20m) con desnivel (1:1 Android GpsTrackingService.kt:638)
        if target.estado != 1 {
            let stateInfo = PointStateInfo.forPoint(estado: target.estado, isRouteEnd: target.isRouteEnd, description: target.descriptionText)
            for threshold in [50.0, 20.0] {
                if distance <= threshold {
                    let key = "\(target.id)_\(Int(threshold))"
                    if !announcedGuideAlertKeys.contains(key) {
                        announcedGuideAlertKeys.insert(key)
                        let descPart = target.descriptionText.isEmpty ? "" : ". A continuación: \(target.descriptionText)"
                        if threshold == 50.0 {
                            TextToSpeechService.shared.speak("Atención a 50 metros\(remElevSuffix), \(stateInfo.name.lowercased())\(descPart).")
                        } else {
                            TextToSpeechService.shared.speak("Alerta, estás llegando a \(stateInfo.name.lowercased())\(descPart).")
                        }
                        let generator = UINotificationFeedbackGenerator()
                        generator.notificationOccurred(.warning)
                        break
                    }
                }
            }
        }
        
        // 2. Llegada al punto objetivo actual (<= tolerancia 25m)
        if distance <= arrivalToleranceMeters {
            let nextIndex = guideDirectionForward ? (currentIndex + 1) : (currentIndex - 1)
            let generator = UINotificationFeedbackGenerator()
            generator.notificationOccurred(.success)
            
            if nextIndex >= 0 && nextIndex < recordedPoints.count {
                let nextPunto = recordedPoints[nextIndex]
                let nextLoc = CLLocation(latitude: nextPunto.latitude, longitude: nextPunto.longitude)
                let segDist = userLoc.distance(from: nextLoc)
                let segDeltaAlt = RouteStatsCalculator.calculateElevationDeltaMeters(fromAlt: target.altitude, toAlt: nextPunto.altitude)
                    ?? RouteStatsCalculator.estimateRemainingElevationToTarget(
                        prevPoint: target,
                        targetPoint: nextPunto,
                        currentLocation: userLoc,
                        remainingDistanceMeters: segDist
                    )
                let nextDistSpoken = RouteStatsCalculator.formatDistanceSpoken(segDist)
                let nextElevSuffix = RouteStatsCalculator.formatElevationSpeechSuffix(segDeltaAlt)
                let nextTargetMsg = "Siguiente destino: \(nextPunto.name), distancia a caminar \(nextDistSpoken)\(nextElevSuffix)."
                
                if target.estado == 1 {
                    if isVoiceGuidanceEnabled {
                        TextToSpeechService.shared.speak("Llegaste a punto de ruta: \(target.name). \(nextTargetMsg)")
                    }
                } else {
                    let stateInfo = PointStateInfo.forPoint(estado: target.estado, isRouteEnd: target.isRouteEnd, description: target.descriptionText)
                    let suffix = isVoiceGuidanceEnabled ? " \(nextTargetMsg)" : ""
                    TextToSpeechService.shared.speak("\(stateInfo.name). \(target.name).\(suffix)")
                }
                
                targetGuideWaypoint = nextPunto
                lastGuideDistanceToTarget = segDist
                lastGuideTtsTime = Date()
            } else {
                TextToSpeechService.shared.speak("Felicidades. Has llegado a tu destino final, completando la ruta: \(target.name).")
                saveStatusMessage = "¡Ruta completada! Destino final alcanzado."
                stopGuiding()
            }
            return
        }
        
        // 3. Alarma de desvío peligroso (1:1 Android GpsTrackingService.kt:679-696)
        let distPrevToNext: Double = {
            if let p = prevPt {
                return CLLocation(latitude: p.latitude, longitude: p.longitude).distance(from: targetLoc)
            }
            return 0.0
        }()
        let maxAllowedDistance = max(150.0, distPrevToNext + 50.0)
        let now = Date()
        if minRemainingDist > maxAllowedDistance && now.timeIntervalSince(lastGuideOffRouteWarningTime) > 30.0 {
            lastGuideOffRouteWarningTime = now
            TextToSpeechService.shared.speak("Alarma. Te estás alejando peligrosamente de la ruta. Verifica el mapa.")
            return
        }
        
        // 4. Aviso periódico cuando se aleja del punto objetivo incluyendo subida/bajada (1:1 Android líneas 725-745)
        if isVoiceGuidanceEnabled && isMovingAway && now.timeIntervalSince(lastGuideTtsTime) > 20.0 && distance > arrivalToleranceMeters {
            lastGuideTtsTime = now
            if userLoc.course >= 0 && userLoc.speed > 0.5 {
                let targetBearing = Self.bearingDegrees(from: userLoc.coordinate, to: targetLoc.coordinate)
                var relative = targetBearing - userLoc.course
                while relative < -180 { relative += 360 }
                while relative > 180 { relative -= 360 }
                let dirText: String
                if relative >= -22.5 && relative <= 22.5 {
                    dirText = "adelante"
                } else if relative > 22.5 && relative <= 112.5 {
                    dirText = "gira a la derecha"
                } else if relative > 112.5 && relative <= 157.5 {
                    dirText = "da la vuelta a la derecha"
                } else if relative >= -112.5 && relative < -22.5 {
                    dirText = "gira a la izquierda"
                } else if relative >= -157.5 && relative < -112.5 {
                    dirText = "da la vuelta a la izquierda"
                } else {
                    dirText = "da la vuelta"
                }
                TextToSpeechService.shared.speak("A \(Int(distance)) metros\(remElevSuffix), \(dirText).")
            } else {
                TextToSpeechService.shared.speak("Punto a \(Int(distance)) metros\(remElevSuffix).")
            }
        }
    }
    
    private static func bearingDegrees(from start: CLLocationCoordinate2D, to end: CLLocationCoordinate2D) -> Double {
        let lat1 = start.latitude * .pi / 180.0
        let lon1 = start.longitude * .pi / 180.0
        let lat2 = end.latitude * .pi / 180.0
        let lon2 = end.longitude * .pi / 180.0
        let dLon = lon2 - lon1
        let y = sin(dLon) * cos(lat2)
        let x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var brng = atan2(y, x) * 180.0 / .pi
        if brng < 0 { brng += 360.0 }
        return brng
    }
    
    // MARK: - Sincronización Manual con la Nube (1:1 Android)
    
    @MainActor
    public func triggerManualCloudSync() async {
        isSyncingRoutes = true
        defer { isSyncingRoutes = false }
        
        await loadLocalRoutes()
        
        let userPhone = currentHikerProfile?.phone ?? ""
        var syncedAny = false
        
        for route in availableLocalRoutes where route.isOwnRoute(userPhone: userPhone) {
            let ok = await uploadRouteToCloud(route)
            if ok { syncedAny = true }
        }
        
        if let profile = currentHikerProfile {
            try? await CloudSyncService.shared.syncSenderista(profile: profile)
            syncAgrupacionLogo(idAgrupacion: profile.idAgrupacion)
        }
        
        await loadLocalRoutes()
        
        self.saveStatusMessage = syncedAny
            ? "¡Rutas sincronizadas exitosamente con la nube!"
            : "Tus rutas locales ya están actualizadas con la nube."
    }
    
    @MainActor
    public func selectPointForDetail(_ point: RoutePoint) {
        self.selectedPointForDetail = point
    }
    
    @MainActor
    public func nextPointDetail() {
        guard let current = selectedPointForDetail else { return }
        let pts = tourPoints
        if let idx = pts.firstIndex(where: { $0.id == current.id }), idx < pts.count - 1 {
            selectedPointForDetail = pts[idx + 1]
        }
    }
    
    @MainActor
    public func previousPointDetail() {
        guard let current = selectedPointForDetail else { return }
        let pts = tourPoints
        if let idx = pts.firstIndex(where: { $0.id == current.id }), idx > 0 {
            selectedPointForDetail = pts[idx - 1]
        }
    }
    
    @MainActor
    public func clearActiveRoute() {
        stopVirtualTour()
        stopGuiding()
        self.activeRoute = nil
        self.isRouteConsumptionOnly = false
        if self.activeRole == .registrar {
            self.activeRole = .visor
        }
        self.recordedPoints = []
        self.recordedTrace = []
        self.recordedCoordinates = []
        self.totalDistanceMeters = 0.0
        self.elevationStats = ElevationStats()
        self.recordingState = .stopped
    }
    
    // MARK: - Motor de Recorrido Virtual / Simulación (1:1 Android)
    
    @MainActor
    public func startVirtualTour() {
        guard activeRole == .visor else {
            print("⚠️ [VirtualTour] La simulación de recorrido (Play) es exclusiva de modo visor. Rol actual: \(activeRole)")
            return
        }
        guard !recordedPoints.isEmpty else { return }
        virtualTourTask?.cancel()
        isFollowingUser = false
        isVirtualTourActive = true
        isVirtualTourPaused = false
        virtualTourIndex = 0
        showVirtualTourPhotoCard = true
        showMapScreen = true // Mostrar el mapa posicionado en el punto de inicio de la simulación
        stepVirtualTour()
    }
    
    @MainActor
    public func togglePauseVirtualTour() {
        guard activeRole == .visor else { return }
        if !isVirtualTourActive {
            startVirtualTour()
            return
        }
        isVirtualTourPaused.toggle()
        if isVirtualTourPaused {
            virtualTourTask?.cancel()
            TextToSpeechService.shared.stop()
        } else {
            stepVirtualTour()
        }
    }
    
    @MainActor
    public func stopVirtualTour() {
        virtualTourTask?.cancel()
        virtualTourTask = nil
        isVirtualTourActive = false
        isVirtualTourPaused = false
        virtualTourIndex = 0
        TextToSpeechService.shared.stop()
    }
    
    @MainActor
    public func nextVirtualTourPoint() {
        virtualTourTask?.cancel()
        TextToSpeechService.shared.stop()
        let pts = tourPoints
        if virtualTourIndex < pts.count - 1 {
            virtualTourIndex += 1
            stepVirtualTour()
        } else {
            TextToSpeechService.shared.speak("Has completado el recorrido virtual.") { [weak self] in
                self?.stopVirtualTour()
            }
        }
    }
    
    @MainActor
    public func previousVirtualTourPoint() {
        virtualTourTask?.cancel()
        TextToSpeechService.shared.stop()
        if virtualTourIndex > 0 {
            virtualTourIndex -= 1
            stepVirtualTour()
        }
    }
    
    @MainActor
    public func stepVirtualTour() {
        guard isVirtualTourActive, !isVirtualTourPaused else { return }
        let pts = tourPoints
        guard virtualTourIndex < pts.count else {
            TextToSpeechService.shared.speak("Recorrido virtual completado. Has llegado al final de la ruta.") { [weak self] in
                self?.stopVirtualTour()
            }
            return
        }
        
        let currentPoint = pts[virtualTourIndex]
        let nextPoint = (virtualTourIndex < pts.count - 1) ? pts[virtualTourIndex + 1] : nil
        
        // Calcular distancia y subida/bajada respecto al punto actual hacia el siguiente (1:1 Android MainScreen.kt:499-523)
        let nextStepSpeech: String?
        if let next = nextPoint {
            let loc1 = CLLocation(latitude: currentPoint.latitude, longitude: currentPoint.longitude)
            let loc2 = CLLocation(latitude: next.latitude, longitude: next.longitude)
            let d = loc1.distance(from: loc2)
            let deltaAlt = RouteStatsCalculator.calculateElevationDeltaMeters(fromAlt: currentPoint.altitude, toAlt: next.altitude)
            nextStepSpeech = RouteStatsCalculator.formatNextPointSimulationSpeech(distanceMeters: d, deltaMeters: deltaAlt)
        } else {
            nextStepSpeech = nil
        }
        
        let stateInfo = PointStateInfo.forPoint(estado: currentPoint.estado, isRouteEnd: currentPoint.isRouteEnd, description: currentPoint.descriptionText)
        let isNormalPoint = (currentPoint.estado == 1 || stateInfo.name.lowercased() == "punto normal") && !currentPoint.isRouteEnd
        
        // Construir locución descriptiva SIN decir la altitud absoluta del punto, solo lo que sube o baja al siguiente punto
        var speech = "Punto \(virtualTourIndex + 1) de \(pts.count). "
        if !isNormalPoint {
            speech += "Tipo: \(stateInfo.name). "
        }
        if !currentPoint.descriptionText.isEmpty {
            speech += "Descripción: \(currentPoint.descriptionText). "
        }
        if let nextStepSpeech = nextStepSpeech {
            speech += nextStepSpeech
        } else {
            speech += "Este es el último punto del recorrido."
        }
        
        // Locución en español
        TextToSpeechService.shared.speak(speech) { [weak self] in
            guard let self = self, self.isVirtualTourActive, !self.isVirtualTourPaused else { return }
            // Pausa de cortesía para visualizar el mapa y la foto antes de avanzar al siguiente punto
            self.virtualTourTask = Task {
                try? await Task.sleep(nanoseconds: 3_500_000_000)
                guard !Task.isCancelled, self.isVirtualTourActive, !self.isVirtualTourPaused else { return }
                await MainActor.run {
                    if self.virtualTourIndex < pts.count - 1 {
                        self.virtualTourIndex += 1
                        self.stepVirtualTour()
                    } else {
                        self.stopVirtualTour()
                    }
                }
            }
        }
    }
}
