# Mapeo de Arquitectura y Equivalencias: Kotlin/Android -> Swift/iOS

Este documento define la correspondencia técnica directa entre el stack tecnológico actual de Android (Kotlin, Jetpack Compose, Room, Corrutinas/Flows, Core Android) y el stack nativo de iOS (Swift 5.10 / 6, SwiftUI, SwiftData / GRDB, Swift Concurrency, CoreBluetooth, CoreLocation).

---

## 1. Mapeo General de Capas y Tecnologías

| Dominio / Componente | Implementación Android (Kotlin) | Equivalente Nativo iOS (Swift) | Justificación & Consideraciones Técnicas |
| :--- | :--- | :--- | :--- |
| **Lenguaje** | Kotlin 1.9+ | Swift 5.10 / 6.0 | Tipado estricto, Optionals, ARC, Actor-based concurrency. |
| **Paradigma UI** | Jetpack Compose (Declarativo) | SwiftUI | Misma filosofía reactiva y declarativa (`@State`, `@Binding`, `@ViewBuilder`). |
| **Patrón de Presentación**| MVVM (`ViewModel`, `viewModelScope`) | MVVM (`@Observable` / `ObservableObject`) | En iOS 17+, usar la macro `@Observable` para bindings limpios sin `@Published`. |
| **Flujos Reactivos** | `StateFlow<T>`, `SharedFlow<T>` | `AsyncStream<T>`, `@Observable`, Combine | `StateFlow` -> Propiedad observable en clase `@Observable`. `SharedFlow` -> `AsyncStream` o `PassthroughSubject`. |
| **Concurrencia / Asincronía**| Corrutinas (`suspend`, `Dispatchers.IO`) | Swift Concurrency (`async/await`, `Task`, `Actors`) | `withContext(Dispatchers.IO)` equivale a `Task.detached` o Actors en background. |
| **Persistencia Local** | Room Database (SQLite v34) | SwiftData o GRDB.swift | Si se requiere SQL crudo idéntico, **GRDB.swift** es 100% equivalente a Room. Para API moderna de Apple, **SwiftData**. |
| **Hardware BLE Emisión** | `BluetoothLeAdvertiser` (iBeacon) | `CBPeripheralManager` (iBeacon / Data) | En iOS, `CBPeripheralManager` con `CLBeaconRegion.peripheralData(withMeasuredPower:)` o CoreBluetooth. |
| **Hardware BLE Escaneo** | `BluetoothLeScanner` | `CBCentralManager` y/o `CLLocationManager` | Apple restringe el escaneo iBeacon crudo en background; se utiliza `CLBeaconIdentityConstraint` o `CBCentralManager` con Service UUIDs. |
| **Servicio en Background GPS**| `ForegroundService` + Notification | `CLLocationManager` con Background Modes | En iOS se activa `UIBackgroundModes` -> `location`. Se habilita `allowsBackgroundLocationUpdates = true`. No existe Foreground Service como tal. |
| **Cliente HTTP REST** | OkHttp 4 + Org.JSON | `URLSession` + `Codable` (o Alamofire) | `URLSession.shared.data(for: request)` con decodificación automática `JSONDecoder()`. |
| **Almacenamiento de Fotos**| Cloudinary SDK | Cloudinary REST API / URLSession Multipart | Subida directa vía peticiones POST multipart firmadas o unsigned a Cloudinary. |
| **Criptografía y JWT** | Java `Mac` (HmacSHA256) | Apple `CryptoKit` (`HMAC<SHA256>`) | `CryptoKit` viene integrado en iOS 13+ con rendimiento nativo por hardware (Secure Enclave). |
| **Síntesis de Voz (TTS)** | Android `TextToSpeech` | `AVSpeechSynthesizer` (`AVFoundation`) | `AVSpeechUtterance(string:)` en español de México (`es-MX`). |
| **Cálculo de Desnivel y GPS**| `Location.distanceBetween`, filtros custom | `CLLocation.distance(from:)` | Idénticas fórmulas geodésicas (Haversine/WGS84). |

---

## 2. Reactividad y Manejo de Estado

### Kotlin (Android):
```kotlin
class RutasViewModel(private val repository: RutasRepository) : ViewModel() {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _puntos = MutableStateFlow<List<PuntoRutaEntity>>(emptyList())
    val puntos: StateFlow<List<PuntoRutaEntity>> = _puntos.asStateFlow()

    fun startTracking() {
        viewModelScope.launch {
            _isRecording.value = true
        }
    }
}
```

### Swift (iOS 17+ con Observation):
```swift
import SwiftUI
import Observation

@Observable
final class RutasViewModel {
    private let repository: RutasRepositoryProtocol
    
    var isRecording: Bool = false
    var puntos: [PuntoRuta] = []
    var distanciaMetros: Double = 0.0

    init(repository: RutasRepositoryProtocol) {
        self.repository = repository
    }

    @MainActor
    func startTracking() async {
        self.isRecording = true
    }
}
```

---

## 3. Persistencia de Datos: Room -> SwiftData / GRDB

### Mapeo de Entidades:
* `@Entity(tableName = "rutas")` -> `@Model class Ruta` en SwiftData, o `struct Ruta: Codable, FetchableRecord, PersistableRecord` en GRDB.
* Claves primarias auto-incrementables (`@PrimaryKey(autoGenerate = true)`) se manejan nativamente en GRDB como `Int64?` o en SwiftData con `@Attribute(.unique)`.
* Relaciones `@ForeignKey(onDelete = CASCADE)` se definen con `@Relationship(deleteRule: .cascade)` en SwiftData.

---

## 4. Hardware BLE: Android BLE vs Apple CoreBluetooth

### Advertencia Crítica de iOS sobre iBeacons:
1. **Emisión (Tx):**
   * En iOS, un iPhone puede emitir como iBeacon utilizando `CBPeripheralManager` pasando el diccionario obtenido de `CLBeaconRegion.peripheralData(withMeasuredPower:)`.
   * **Restricción:** Cuando la app pasa a segundo plano con la pantalla bloqueada, iOS detiene la publicidad de paquetes iBeacon manufacturer-data crudos a menos que sea un periférico GATT con un Service UUID registrado en `UIBackgroundModes` -> `bluetooth-peripheral`.
2. **Recepción / Escaneo (Rx):**
   * Apple clasifica los iBeacons bajo `CoreLocation` (`CLLocationManager`), no directamente bajo `CoreBluetooth` si el payload no incluye un Service UUID estándar de GATT.
   * Para escanear el formato personalizado de Antigravity (Magic Bytes `0xBEAC` en el UUID), en iOS se debe usar `CLLocationManager.startRangingBeacons(satisfying:)` o `CBCentralManager` escaneando paquetes con el fabricante Apple (`0x004C`).

---

## 5. Ciclo de Vida y Ejecución en Segundo Plano

* **Android:** Utiliza un `ForegroundService` con notificación fija en la barra de estado y `PARTIAL_WAKE_LOCK`.
* **iOS:** No existe el concepto de "Servicio en primer plano". En su lugar, el sistema otorga tiempo de CPU continuo en segundo plano si la aplicación:
  1. Declara `location` en `UIBackgroundModes` en el `Info.plist`.
  2. Solicita `locationManager.requestAlwaysAuthorization()`.
  3. Establece `locationManager.allowsBackgroundLocationUpdates = true`.
  4. Establece `locationManager.showsBackgroundLocationIndicator = true` (icono azul en la barra de estado / Dynamic Island).
  5. Activa `locationManager.pausesLocationUpdatesAutomatically = false`.
