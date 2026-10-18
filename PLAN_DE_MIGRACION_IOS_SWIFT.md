# 📱 PLAN MAESTRO DE MIGRACIÓN: KOTLIN (ANDROID) ➔ SWIFT (iOS)
**Proyecto:** RutasSendero  
**Fecha de Preparación:** Octubre 2026 (Listo para iniciar el Lunes)  
**Repositorio Oficial en la Nube:** [https://github.com/jgasparmxx/rutas.git](https://github.com/jgasparmxx/rutas.git)  
**Ramas Relevantes:**
* `master` (Rama activa con la estructura multi-plataforma y entregables)
* `antes-de-crear-ios` (Rama congelada de respaldo 100% Android)

---

## 🧭 1. Resumen Ejecutivo de lo Realizado en Windows

Antes de pasar a la Mac, todo el código y la arquitectura fueron reorganizados y asegurados:
1. **Respaldo Local y Nube:** Se creó una copia física en disco (`c:\GASPAR\baches\rutas_antes_de_crear_ios`), un archivo comprimido `.zip` y se subió el commit/tag a GitHub.
2. **Estructura Multiplataforma:** Se reubicó el proyecto Android dentro de `/android/` (preservando el 100% del historial de Git) y se reservó la carpeta `/ios/` para el proyecto de Xcode.
3. **Entregables Técnicos Generados:** Se preparó la carpeta `/docs/migration/` con todos los contratos, especificaciones de hardware BLE, esquemas de bases de datos y modelos nativos en Swift.

---

## 🗂️ 2. Estructura del Repositorio

```text
rutas/
├── PLAN_DE_MIGRACION_IOS_SWIFT.md   <-- Este documento maestro
├── android/                         <-- Proyecto nativo Android (Gradle, Kotlin, Compose)
├── ios/                             <-- Carpeta donde se creará el proyecto de Xcode en Mac
│   └── README.md                    <-- Guía rápida de inicialización
└── docs/
    └── migration/                   <-- Entregables listos para que los consuma Antigravity en Mac
        ├── architecture_mapping.md  <-- Guía de equivalencias Compose -> SwiftUI, Room -> SwiftData
        ├── contracts/
        │   ├── CleanDomainContracts.kt  <-- Interfaces Kotlin limpias sin 'android.*'
        │   └── SwiftProtocols.swift     <-- Protocolos nativos de Swift listos para compilar
        ├── database/
        │   ├── sqlite_schema.sql            <-- DDL SQLite de 9 tablas (Room v34)
        │   ├── postgresql_remote_schema.sql <-- Esquema de PostgreSQL en Neon
        │   ├── critical_queries.sql         <-- Consultas SQL críticas de los DAOs
        │   └── SwiftDataModels.swift        <-- Clases @Model de SwiftData listas para usar
        └── specs/
            ├── BLE_HARDWARE_PROTOCOL.md     <-- Protocolo iBeacon (Bytes, UUID, 0xBEAC, RSSI)
            ├── REST_API_POSTGREST_SPEC.md   <-- Endpoints, JWT client-side y Cloudinary
            └── BACKGROUND_GPS_TRACKING_SPEC.md <-- CoreLocation, segundo plano y filtros
```

---

## 🚀 3. Paso a Paso para el LUNES al Sentarte en la Mac

No necesitas memorias USB ni cables. Al abrir tu Mac, sigue estos 6 pasos:

### Paso 1: Clonar el proyecto desde la Terminal de Mac
Abre la aplicación **Terminal** en macOS y ejecuta:
```bash
# 1. Ve a la carpeta donde quieras tener el proyecto (ej. Documents o Developer)
cd ~/Documents

# 2. Clona el repositorio desde GitHub
git clone https://github.com/jgasparmxx/rutas.git

# 3. Entra a la carpeta del proyecto
cd rutas
```

---

### Paso 2: Abrir Antigravity en la Mac
Abre la carpeta raíz `rutas` en **Antigravity IDE** (o VSCode / Cursor con Antigravity en macOS).
> **¿Por qué abrir la raíz?**  
> Porque así el agente inteligente tendrá acceso simultáneo a `/docs/migration/` (para leer qué construir), a `/android/` (por si necesita comparar cómo funcionaba en Kotlin) y a `/ios/` (donde escribirá el código de Swift).

---

### Paso 3: Crear el proyecto nativo en Xcode
1. Abre **Xcode** en tu Mac.
2. Selecciona **Create New Project** -> pestaña **iOS** -> plantilla **App** -> botón **Next**.
3. Configura los campos de la siguiente manera:
   * **Product Name:** `RutasSendero`
   * **Team:** Selecciona tu cuenta de Apple Developer (o tu Apple ID gratuito).
   * **Organization Identifier:** `com.gaspar.rutassendero`
   * **Bundle Identifier:** `com.gaspar.rutassendero.RutasSendero` (se genera automático).
   * **Interface:** **SwiftUI**
   * **Language:** **Swift**
   * **Storage:** **SwiftData**
4. Haz clic en **Next**.
5. ⚠️ **Ubicación de guardado:** Navega hasta la carpeta que acabas de clonar (`rutas`) y selecciona la carpeta **`ios`** como destino.

---

### Paso 4: Añadir los archivos Swift ya preparados
En el navegador de archivos de Xcode (panel izquierdo):
1. Haz clic derecho sobre el grupo `RutasSendero` y selecciona **Add Files to "RutasSendero"...**
2. Navega hasta `docs/migration/`:
   * Selecciona `docs/migration/contracts/SwiftProtocols.swift`
   * Selecciona `docs/migration/database/SwiftDataModels.swift`
3. Asegúrate de que esté marcada la casilla **Copy items if needed** y marcado el target **RutasSendero**.
4. Pulsa **Add**. *(¡Ya tienes tus modelos de datos y contratos listos sin haber escrito una sola línea manual!)*.

---

### Paso 5: Configurar Capacidades y Permisos en Xcode (`Info.plist`)
Selecciona el proyecto en el árbol de Xcode (ícono azul superior) y ve a la pestaña **Signing & Capabilities**:
1. Pulsa el botón **`+ Capability`** y añade **Background Modes**.
2. Marca las casillas:
   * ☑️ **Location updates** (Permite registrar coordenadas con la pantalla bloqueada).
   * ☑️ **Uses Bluetooth LE accessories** (Permite escanear balizas de compañeros).
   * ☑️ **Acts as a Bluetooth LE accessory** (Permite que el iPhone emita su baliza iBeacon).

Luego, en la pestaña **Info** (o en tu archivo `Info.plist`), añade las siguientes claves de privacidad:
* **`Privacy - Location When In Use Usage Description`**  
  *Valor:* `RutasSendero necesita tu ubicación para registrar el trayecto en el mapa.`
* **`Privacy - Location Always and When In Use Usage Description`**  
  *Valor:* `RutasSendero registra tu posición en segundo plano para alertarte de desvíos y peligros con la pantalla bloqueada.`
* **`Privacy - Bluetooth Always Usage Description`**  
  *Valor:* `RutasSendero utiliza Bluetooth para comunicar tu baliza con compañeros de expedición cercanos.`

---

### Paso 6: Instrucción (Prompt) para Antigravity en la Mac
Una vez hecho lo anterior, abre el chat con Antigravity en tu Mac y envíale este mensaje:

```text
Hola Antigravity. Ya tenemos inicializado el proyecto en /ios y configurado en Xcode con SwiftUI y SwiftData.
En el repositorio tenemos la carpeta /docs/migration con toda la arquitectura lista:
1. Lee /docs/migration/architecture_mapping.md
2. Lee /docs/migration/specs/BLE_HARDWARE_PROTOCOL.md
3. Lee /docs/migration/specs/BACKGROUND_GPS_TRACKING_SPEC.md
4. Ya tenemos incluidos SwiftProtocols.swift y SwiftDataModels.swift.

Por favor, comencemos implementando el servicio de geolocalización en segundo plano (LocationTracker) y la pantalla principal (MainScreen) en SwiftUI mostrando el mapa y las métricas de grabación.
```

---

## ⚡ 4. Hoja de Equivalencias Rápidas (Cheat Sheet)

| Función en Android (Kotlin) | Equivalente en iOS (Swift) | Detalle Clave |
| :--- | :--- | :--- |
| `StateFlow<T>` en ViewModel | `@Observable class RutasViewModel` | En iOS 17+, las propiedades estándar de una clase `@Observable` actualizan la vista SwiftUI automáticamente. |
| Room Database (SQLite) | SwiftData (`@Model`) | Los modelos ya están redactados en `SwiftDataModels.swift`. |
| `ForegroundService` + WakeLock | `CLLocationManager` con `allowsBackgroundLocationUpdates = true` | iOS no necesita foreground services; la pastilla azul en el Dynamic Island / Status Bar se activa sola. |
| iBeacon Tx (`BluetoothLeAdvertiser`) | `CBPeripheralManager` | Se emite con Apple Company ID `0x004C` y firma mágica `0xBEAC` en los bytes 14-15 del UUID. |
| iBeacon Rx (`BluetoothLeScanner`) | `CBCentralManager` / `CLBeaconRegion` | Parseo binario directo de los 23 bytes del payload iBeacon. |
| PostgREST + JWT HMAC-SHA256 | `URLSession` + `CryptoKit` (`HMAC<SHA256>`) | Firma instantánea por hardware en iOS sin librerías externas pesadas. |
| Subida de Fotos Cloudinary | `URLSessionUploadTask` multipart/form-data | Subida asíncrona directa a la API REST de Cloudinary. |
| Síntesis de Voz (TTS Alertas) | `AVSpeechSynthesizer` (`AVFoundation`) | `AVSpeechUtterance(string:)` con locale `"es-MX"`. |

---

## 📅 5. Cronograma Recomendado de la Semana

* **Lunes:** Clonar en Mac, setup de Xcode, validar compilación de `SwiftDataModels.swift` y `SwiftProtocols.swift`.
* **Martes:** Servicio de localización en segundo plano (`LocationTracker`) y pantalla del mapa (`MapKit` en SwiftUI).
* **Miércoles:** Emisión y escaneo de balizas BLE iBeacon con firma `0xBEAC` (`CoreBluetooth`).
* **Jueves:** Sincronización REST con PostgREST en Render, firma de tokens JWT y subida de fotos a Cloudinary.
* **Viernes:** Pruebas en iPhone físico en campo, modo bolsillo/bloqueo de pantalla y alertas de voz.

---

> 💡 **Nota:** Si en algún momento necesitas consultar el código original tal y como estaba antes de cualquier cambio, puedes cambiarte a la rama de respaldo en cualquier momento ejecutando: `git checkout antes-de-crear-ios`.
