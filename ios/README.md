# Proyecto Nativo iOS (Swift / SwiftUI)

Este directorio está reservado para el proyecto nativo de Xcode para iOS.

## Inicialización en macOS con Xcode:
1. Abrir Xcode en la Mac.
2. Seleccionar **Create New Project** -> **iOS** -> **App**.
3. Configuración recomendada:
   - **Product Name:** `RutasSendero` (o `Rutas`)
   - **Organization Identifier:** `com.gaspar.rutassendero`
   - **Interface:** SwiftUI
   - **Language:** Swift
   - **Storage:** SwiftData (o None si se usa GRDB/SQLite directo)
4. Guardar el proyecto directamente dentro de este directorio (`/ios`).
5. Consultar las especificaciones y contratos en `../docs/migration/` para conectar:
   - Protocolo Bluetooth BLE iBeacon (CoreBluetooth)
   - Base de datos local (SwiftData / GRDB)
   - Sincronización REST con PostgREST en Render
   - Servicio de rastreo GPS en segundo plano (CoreLocation)
