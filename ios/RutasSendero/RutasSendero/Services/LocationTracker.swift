//
//  LocationTracker.swift
//  RutasSendero
//
//  Servicio de seguimiento y geolocalización continua en primer y segundo plano.
//  Implementa LocationTrackerProtocol según BACKGROUND_GPS_TRACKING_SPEC.md.
//

import Foundation
import CoreLocation
import Observation

@Observable
public final class LocationTracker: NSObject, CLLocationManagerDelegate, LocationTrackerProtocol, @unchecked Sendable {
    
    // MARK: - Estado Observable
    public var isTracking: Bool = false
    public var currentLocation: CLLocation? = nil
    public var currentSpeedKmh: Double = 0.0
    public var currentAltitude: Double = 0.0
    public var currentCourse: Double = -1.0
    public var horizontalAccuracy: Double = -1.0
    public var authorizationStatus: CLAuthorizationStatus = .notDetermined
    public var isGpsReady: Bool = false
    public var lastErrorMessage: String? = nil
    
    // MARK: - Dependencias Privadas
    @ObservationIgnored
    private let locationManager = CLLocationManager()
    
    @ObservationIgnored
    private var onUpdateCallback: (@Sendable (Double, Double, Double) -> Void)?
    
    /// Callback opcional para notificar cualquier fix válido (incluso en interiores por Wi-Fi/Red <= 200m) para el Faro BLE
    @ObservationIgnored
    public var onBeaconLocationFix: (@Sendable (Double, Double) -> Void)?
    
    // MARK: - Inicialización
    public override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = 4.0 // Umbral de 4 metros idéntico a Android
        
        #if os(iOS)
        locationManager.activityType = .fitness
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.showsBackgroundLocationIndicator = true
        locationManager.pausesLocationUpdatesAutomatically = false
        #endif
        
        self.authorizationStatus = locationManager.authorizationStatus
        #if os(iOS)
        self.isGpsReady = (authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways)
        #else
        self.isGpsReady = (authorizationStatus == .authorizedAlways)
        #endif
    }
    
    // MARK: - LocationTrackerProtocol
    
    public func startTracking(
        distanceFilterMeters: Double = 4.0,
        onUpdate: @escaping @Sendable (Double, Double, Double) -> Void
    ) {
        self.onUpdateCallback = onUpdate
        locationManager.distanceFilter = distanceFilterMeters
        
        // Manejo de permisos
        if locationManager.authorizationStatus == .notDetermined {
            #if os(iOS)
            locationManager.requestAlwaysAuthorization()
            #else
            locationManager.requestWhenInUseAuthorization()
            #endif
        }
        
        #if os(iOS)
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.showsBackgroundLocationIndicator = true
        locationManager.pausesLocationUpdatesAutomatically = false
        locationManager.startUpdatingHeading()
        #endif
        
        locationManager.startUpdatingLocation()
        
        Task { @MainActor in
            self.isTracking = true
            self.lastErrorMessage = nil
        }
    }
    
    public func stopTracking() {
        locationManager.stopUpdatingLocation()
        #if os(iOS)
        locationManager.stopUpdatingHeading()
        #endif
        
        Task { @MainActor in
            self.isTracking = false
            self.onUpdateCallback = nil
        }
    }
    
    public func requestPermissions() {
        #if os(iOS)
        locationManager.requestAlwaysAuthorization()
        #else
        locationManager.requestWhenInUseAuthorization()
        #endif
    }
    
    // MARK: - CLLocationManagerDelegate
    
    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        guard location.horizontalAccuracy >= 0 else { return }
        
        let lat = location.coordinate.latitude
        let lon = location.coordinate.longitude
        let alt = location.altitude
        let speedMps = max(0.0, location.speed)
        let speedKmh = speedMps * 3.6
        let course = location.course
        let accuracy = location.horizontalAccuracy
        
        // Permitir ubicación general y de Faro BLE incluso en interiores (<= 200m),
        // pero exigir precisión fina (<= 25m) para grabar trazas de sendero sin ruido GPS.
        if accuracy <= 200.0 {
            Task { @MainActor in
                if self.currentLocation == nil || accuracy <= 35.0 || accuracy <= (self.currentLocation?.horizontalAccuracy ?? 200.0) {
                    self.currentLocation = location
                    self.currentAltitude = alt
                    self.currentSpeedKmh = speedKmh
                    self.currentCourse = course
                }
                self.horizontalAccuracy = accuracy
                self.isGpsReady = true
            }
            onBeaconLocationFix?(lat, lon)
        } else {
            Task { @MainActor in
                self.horizontalAccuracy = accuracy
            }
        }
        
        guard accuracy <= 25.0 else { return }
        
        // Invocar el callback de traza / guiado fino cuando la precisión horizontal es óptima
        onUpdateCallback?(lat, lon, alt)
    }
    
    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            self.authorizationStatus = status
            #if os(iOS)
            self.isGpsReady = (status == .authorizedWhenInUse || status == .authorizedAlways)
            if status == .authorizedWhenInUse && isTracking {
                // Solicitar Always si ya tenemos WhenInUse para garantizar segundo plano
                manager.requestAlwaysAuthorization()
            }
            #else
            self.isGpsReady = (status == .authorizedAlways)
            #endif
        }
    }
    
    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        let message = error.localizedDescription
        Task { @MainActor in
            self.lastErrorMessage = message
        }
        print("⚠️ [LocationTracker] Error en CoreLocation: \(message)")
    }
    
    // MARK: - Simulación de Ubicación (Testing / Simulator)
    
    @MainActor
    public func simulateLocation(latitude: Double, longitude: Double, altitude: Double = 500.0) {
        let loc = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            altitude: altitude,
            horizontalAccuracy: 5.0,
            verticalAccuracy: 5.0,
            course: 0.0,
            speed: 0.0,
            timestamp: Date()
        )
        self.currentLocation = loc
        self.currentAltitude = altitude
        self.horizontalAccuracy = 5.0
        self.isGpsReady = true
        onUpdateCallback?(latitude, longitude, altitude)
    }
}

