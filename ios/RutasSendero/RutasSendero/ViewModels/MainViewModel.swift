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
    public var currentHikerProfile: HikerProfile? = nil
    public var isSavedLocally: Bool = false
    public var saveStatusMessage: String? = nil
    
    // MARK: - Modos y Balizas
    public var activeRole: AppRole = .visor
    public var isBeaconEmitting: Bool = false
    public var isEmergency911Active: Bool = false
    public var mapStyle: MapStyleMode = .standard
    public var isFollowingUser: Bool = true
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
    
    public var distanceToTargetGuideWaypoint: Double? {
        guard let target = targetGuideWaypoint, let current = locationTracker.currentLocation else { return nil }
        let targetLoc = CLLocation(latitude: target.latitude, longitude: target.longitude)
        return current.distance(from: targetLoc)
    }
    
    @MainActor
    public func stopGuiding() {
        isGuidingActive = false
        targetGuideWaypoint = nil
        guidanceStatusMessage = nil
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
                let previousEmergency = self.nearbyPeers[peer.beaconId]?.isEmergency ?? false
                self.nearbyPeers[peer.beaconId] = peer
                
                // Si el compañero activó 911, disparar alerta inmediata (1:1 Android)
                if peer.isEmergency && !previousEmergency {
                    TextToSpeechService.shared.speak("¡Alerta! Emergencia 911 detectada de compañero \(peer.beaconId) a \(Int(peer.estimatedDistanceMeters)) metros.")
                    self.saveStatusMessage = "¡EMERGENCIA 911 DE \(peer.beaconId)!"
                }
            }
        }
    }
    
    @MainActor
    private func processLiveCoordinate(latitude: Double, longitude: Double, altitude: Double) {
        // Si la baliza está transmitiendo, actualizar coordenadas emitidas
        if isBeaconEmitting {
            bleBroadcaster.updateCoordinates(latitude: latitude, longitude: longitude)
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
    public func toggleBeacon() {
        isBeaconEmitting.toggle()
        let bId = currentHikerProfile?.beaconId ?? "010203040506"
        let lat = locationTracker.currentLocation?.coordinate.latitude ?? 19.2458
        let lon = locationTracker.currentLocation?.coordinate.longitude ?? -103.7251
        
        if isBeaconEmitting {
            Task {
                _ = await bleBroadcaster.startBroadcasting(
                    beaconId: bId,
                    latitude: lat,
                    longitude: lon,
                    isEmergency: isEmergency911Active
                )
            }
        } else {
            bleBroadcaster.stopBroadcasting()
        }
    }
    
    @MainActor
    public func toggleEmergency911() {
        isEmergency911Active.toggle()
        let bId = currentHikerProfile?.beaconId ?? "010203040506"
        let lat = locationTracker.currentLocation?.coordinate.latitude ?? 19.2458
        let lon = locationTracker.currentLocation?.coordinate.longitude ?? -103.7251
        
        if isEmergency911Active {
            isBeaconEmitting = true
            saveStatusMessage = "¡EMERGENCIA 911 ACTIVADA!"
            TextToSpeechService.shared.speak("¡Emergencia 911 activada! Transmitiendo señal de auxilio por baliza a compañeros cercanos.")
            Task {
                _ = await bleBroadcaster.startBroadcasting(
                    beaconId: bId,
                    latitude: lat,
                    longitude: lon,
                    isEmergency: true
                )
            }
        } else {
            saveStatusMessage = "Emergencia cancelada"
            TextToSpeechService.shared.speak("Emergencia cancelada.")
            if isBeaconEmitting {
                Task {
                    _ = await bleBroadcaster.startBroadcasting(
                        beaconId: bId,
                        latitude: lat,
                        longitude: lon,
                        isEmergency: false
                    )
                }
            } else {
                bleBroadcaster.stopBroadcasting()
            }
        }
    }
    
    /// Simula la detección de un compañero cercano (ideal para pruebas y validación visual)
    public func simulatePeerDetected(beaconId: String = "C0QU1M4TL4N1", distanceMeters: Double = 18.5) {
        let curLat = locationTracker.currentLocation?.coordinate.latitude ?? 19.2458
        let curLon = locationTracker.currentLocation?.coordinate.longitude ?? -103.7251
        let peer = PeerBeaconData(
            beaconId: beaconId,
            latitude: curLat + 0.0003,
            longitude: curLon + 0.0004,
            rssi: -62,
            txPower: -59,
            estimatedDistanceMeters: distanceMeters,
            lastDetectedTimestamp: Date()
        )
        nearbyPeers[beaconId] = peer
        resolvePeerProfile(for: beaconId)
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
            if updatedProfile.beaconId == nil || updatedProfile.beaconId?.isEmpty == true {
                updatedProfile.beaconId = String(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(12)).lowercased()
            }
            
            try await repo.save(profile: updatedProfile)
            await MainActor.run {
                self.currentHikerProfile = updatedProfile
                self.hikerWeightKg = updatedProfile.weightKg
                self.activeRole = (updatedProfile.role.uppercased() == "G") ? .guia : .visor
                self.isShowingSenderistaRegistration = false
                self.isRegistrationMandatory = false
                self.syncAgrupacionLogo(idAgrupacion: updatedProfile.idAgrupacion)
            }
            print("✅ [SwiftData] Perfil de senderista registrado exitosamente: \(updatedProfile.name) (Rol: \(updatedProfile.role))")
            
            // Sincronización en segundo plano con PostgREST en la nube
            Task.detached(priority: .background) {
                do {
                    try await CloudSyncService.shared.syncSenderista(profile: updatedProfile)
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
        
        let orderedPts = toStart ? pts.reversed() : pts
        
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
        
        // Distancia mínima a cualquiera de los puntos del sendero para orientar al senderista
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
        
        // Siempre se carga la ruta y se mantiene activo el rol y modo Guía (1:1 Android)
        self.activeRoute = route
        let userPhone = self.currentHikerProfile?.phone ?? ""
        self.isRouteConsumptionOnly = !route.isOwnRoute(userPhone: userPhone)
        self.activeRole = .guia // Respeta estrictamente el estado y atributo de Guía
        self.isVisorReversed = toStart
        self.guideDirectionForward = !toStart
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
        
        // Si el usuario está cerca, guiar al punto más cercano; si está lejos, al inicio del sentido seleccionado
        self.targetGuideWaypoint = (minDistanceMeters <= 1000.0 && closestPoint != nil) ? closestPoint : orderedPts.first
        self.isGuidingActive = true
        self.isShowingVisorOrderDialog = false
        self.pendingRouteForOrderSelection = nil
        self.showMapScreen = true
        
        let destText = toStart ? "el inicio (retorno)" : "el final (ida)"
        
        // REGLA 1: Si el usuario está a más de 1 km (> 1000m) de toda la ruta (ningún punto ni inicio a <= 1km):
        // Mostrarle en Google Maps cómo llegar al inicio de la ruta, conservando la ruta cargada en modo guía
        if minDistanceMeters > 1000.0 && currentLoc != nil {
            let distKm = minDistanceMeters / 1000.0
            let distStr = String(format: "%.1f", distKm)
            
            self.guidanceStatusMessage = "Estás a \(distStr) km del sendero. Redirigiendo a Google Maps para llegar al inicio..."
            self.saveStatusMessage = self.guidanceStatusMessage
            
            openGoogleMapsDirections(lat: startLat, lon: startLon, label: "Inicio de \(route.name)")
        } else if let cp = closestPoint, minDistanceMeters > 50.0 {
            // REGLA 2: Si está cerca (<= 1 km / 1000m):
            // NUNCA abrir Google Maps. Mostrar solo la ruta en el mapa y orientar al usuario al hito más cercano
            let pointDistStr = minDistanceMeters >= 1000 ? String(format: "%.1f km", minDistanceMeters / 1000.0) : "\(Int(minDistanceMeters)) m"
            self.guidanceStatusMessage = "Estás a \(pointDistStr) de \(cp.name). Dirígete hacia él para iniciar la ruta hacia \(destText)."
            self.saveStatusMessage = self.guidanceStatusMessage
        } else {
            self.guidanceStatusMessage = "Iniciando guía hacia \(destText) del sendero."
            self.saveStatusMessage = self.guidanceStatusMessage
        }
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
        isVirtualTourActive = true
        isVirtualTourPaused = false
        virtualTourIndex = 0
        showVirtualTourPhotoCard = true
        showMapScreen = true // Mostrar el mapa para la simulación
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
        
        // Calcular distancia al siguiente punto (1:1 Android)
        let nextDistText: String?
        if let next = nextPoint {
            let loc1 = CLLocation(latitude: currentPoint.latitude, longitude: currentPoint.longitude)
            let loc2 = CLLocation(latitude: next.latitude, longitude: next.longitude)
            let d = loc1.distance(from: loc2)
            if d >= 1000 {
                nextDistText = String(format: "%.1f kilómetros", d / 1000.0)
            } else {
                nextDistText = "\(Int(d)) metros"
            }
        } else {
            nextDistText = nil
        }
        
        // Construir locución descriptiva 1:1 con Android MainScreen.kt líneas 492-508
        var speech = "Punto \(virtualTourIndex + 1) de \(pts.count). "
        if currentPoint.altitude > 0 {
            speech += "Altitud: \(Int(currentPoint.altitude)) metros. "
        }
        if !currentPoint.descriptionText.isEmpty {
            speech += "Descripción: \(currentPoint.descriptionText). "
        }
        if let dist = nextDistText {
            speech += "Distancia a caminar al siguiente punto: \(dist)."
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
