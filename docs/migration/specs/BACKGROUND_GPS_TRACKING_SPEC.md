# Especificación Técnica: Rastreo GPS en Segundo Plano (CoreLocation vs Android Service)

Esta especificación detalla la arquitectura de seguimiento de posición en tiempo real cuando la pantalla del iPhone está bloqueada o la aplicación se encuentra en segundo plano.

---

## 1. Comparativa de Plataformas

| Característica | Android (`GpsTrackingService.kt`) | iOS (`CLLocationManager`) |
| :--- | :--- | :--- |
| **Mecanismo Base** | `ForegroundService` + Notificación persistente | `CLLocationManager` con `UIBackgroundModes = ["location"]` |
| **Bloqueo de Suspensión** | `PowerManager.WakeLock` (`PARTIAL_WAKE_LOCK`) | Manejado automáticamente por el subsistema GPS de iOS |
| **Indicador Visual** | Notificación con botones en barra de estado | Indicador de pastilla azul en Status Bar / Dynamic Island |
| **Filtro de Distancia** | `LocationRequest.setMinUpdateDistanceMeters(4f)` | `locationManager.distanceFilter = 4.0` |
| **Precisión Deseada** | `PRIORITY_HIGH_ACCURACY` | `kCLLocationAccuracyBestForNavigation` / `kCLLocationAccuracyBest` |
| **Pausa Automática** | No disponible nativamente | Se debe desactivar: `pausesLocationUpdatesAutomatically = false` |

---

## 2. Configuración Requerida en `Info.plist` (iOS)

Para que iOS permita la ejecución y actualizaciones de geolocalización continua en segundo plano, el archivo `Info.plist` del proyecto Xcode debe incluir:

```xml
<!-- Permisos de Ubicación -->
<key>NSLocationWhenInUseUsageDescription</key>
<string>RutasSendero necesita tu ubicación para registrar la ruta y mostrar tu posición en el mapa durante la caminata.</string>

<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>RutasSendero necesita tu ubicación en segundo plano para continuar registrando el trayecto y alertarte de peligros con la pantalla bloqueada.</string>

<!-- Modos de Segundo Plano -->
<key>UIBackgroundModes</key>
<array>
    <string>location</string>
    <string>bluetooth-central</string>
    <string>bluetooth-peripheral</string>
</array>
```

---

## 3. Implementación del LocationTracker en Swift

```swift
import Foundation
import CoreLocation

public final class LocationTracker: NSObject, CLLocationManagerDelegate, LocationTrackerProtocol {
    private let locationManager = CLLocationManager()
    private var onUpdateCallback: (@Sendable (Double, Double, Double) -> Void)?
    
    public private(set) var isTracking: Bool = false

    public override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.activityType = .fitness
        locationManager.distanceFilter = 4.0 // Umbral de 4 metros igual que en Android
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.showsBackgroundLocationIndicator = true
        locationManager.pausesLocationUpdatesAutomatically = false
    }

    public func startTracking(
        distanceFilterMeters: Double = 4.0, 
        onUpdate: @escaping @Sendable (Double, Double, Double) -> Void
    ) {
        self.onUpdateCallback = onUpdate
        locationManager.distanceFilter = distanceFilterMeters
        
        // Solicitar permisos Always si no se tienen
        if locationManager.authorizationStatus == .notDetermined {
            locationManager.requestAlwaysAuthorization()
        }
        
        locationManager.startUpdatingLocation()
        isTracking = true
    }

    public func stopTracking() {
        locationManager.stopUpdatingLocation()
        isTracking = false
        onUpdateCallback = nil
    }

    // MARK: - CLLocationManagerDelegate

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        
        // Filtrar ubicaciones con mala precisión horizontal (> 20 metros)
        guard location.horizontalAccuracy >= 0 && location.horizontalAccuracy <= 20.0 else { return }

        let lat = location.coordinate.latitude
        let lon = location.coordinate.longitude
        let alt = location.altitude

        onUpdateCallback?(lat, lon, alt)
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Error en CoreLocation: \(error.localizedDescription)")
    }
}
```

---

## 4. Cálculo de Desnivel Acumulado y Filtro de Ruido Barométrico/GPS

Al igual que en el algoritmo de `RouteStatsCalculator.kt`:
1. **Umbral de elevación mínima:** Solo se computa como ascenso o descenso si la diferencia altimétrica entre el punto actual y el último hito supera `± 3.0` metros. Esto previene que el jitter o ruido de la señal GPS infle falsamente el desnivel acumulado.
2. **Contador de subidas y bajadas:** Se incrementa cuando la tendencia vertical cambia de sentido tras recorrer al menos un desnivel de 5 metros.
