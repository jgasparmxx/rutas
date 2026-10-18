# Especificación Técnica: API REST y Sincronización en la Nube (PostgREST & Cloudinary)

Esta especificación detalla la interacción cliente-servidor para la sincronización bidireccional entre la base de datos local del dispositivo (SQLite / SwiftData) y el backend remoto (PostgreSQL en Neon a través de PostgREST en Render).

---

## 1. Configuración del Servidor

* **Base URL de PostgREST:** `https://postgrest-latest-09dh.onrender.com`
* **Content-Type / Accept:** `application/json; charset=utf-8`
* **Base de Datos Remota:** PostgreSQL (Neon Serverless)
* **Gestión de Seguridad:** Row Level Security (RLS) basado en Claims del token JWT.

---

## 2. Generación y Manejo de JWT (Client-Side)

PostgREST utiliza tokens JWT para autorizar peticiones y poblar las variables de sesión de PostgreSQL (`current_setting('request.jwt.claims', true)`).

### Algoritmo:
* **Tipo:** `HS256` (HMAC con SHA-256)
* **Header:**
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

### Payload (Claims requeridos):
```json
{
  "role": "senderista",        // o "administrador", "guia"
  "email": "usuario@ejemplo.com",
  "telefono": "3121234567",
  "id_agrupacion": "MH",       // o "CL1", etc. (nullable)
  "iat": 1729270000,           // Timestamp emitido (segundos)
  "exp": 1731862000            // Timestamp expiración (30 días)
}
```

### Cabecera HTTP requerida en cada petición:
```http
Authorization: Bearer <TOKEN_JWT_FIRMADO>
```

---

## 3. Endpoints Principales y Operaciones

### 3.1. Rutas (`/rutas`)
* **GET `/rutas?select=*&order=fecha.desc`**: Descarga todas las rutas públicas o permitidas por RLS.
* **POST `/rutas`**: Inserción / Upsert de una ruta.
  * Headers requeridos para Upsert:
    * `Prefer: resolution=merge-duplicates`
* **GET `/rutas?id=eq.{idRuta}`**: Consulta de una ruta específica.

### 3.2. Puntos de Ruta (`/puntos_ruta`)
* **GET `/puntos_ruta?id_ruta=eq.{idRuta}&order=id.asc`**: Obtiene los puntos de control e hitos de una ruta.
* **POST `/puntos_ruta`**: Inserción por lotes (Batch Insert):
  * Payload: Array JSON de objetos `PuntoRuta`.
  * Headers: `Prefer: return=representation`

### 3.3. Trazas GPS (`/traza_ruta`)
* **GET `/traza_ruta?id_ruta=eq.{idRuta}&order=orden.asc`**: Obtiene la polilínea completa de coordenadas GPS.
* **POST `/traza_ruta`**: Inserción por lotes de puntos de la traza (latitud, longitud, altitud, orden).

### 3.4. Invitaciones y Eventos (`/invitaciones_aceptadas`, `/eventos`)
* **GET `/eventos?select=*&fecha_hora_salida=gte.{hoy}`**: Obtiene eventos futuros disponibles.
* **POST `/invitaciones_aceptadas`**: Registra la inscripción de un senderista a un evento.

---

## 4. Almacenamiento de Multimedia (Cloudinary)

Las fotos capturadas en puntos de ruta se almacenan en Cloudinary para optimizar el peso de la base de datos:

* **Cloud Name:** Obtenido de `SecureCredentialsManager`
* **Carpeta de subida:** `rutas_senderismo/fotos_puntos`
* **Formato de URL almacenada en base de datos:**
  `https://res.cloudinary.com/{cloud_name}/image/upload/v{timestamp}/rutas_senderismo/fotos_puntos/{id_punto}.jpg`
* **Estrategia offline:**
  1. La imagen se guarda en el directorio local de documentos del sandbox de iOS (`FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)`).
  2. El registro local en la base de datos almacena la ruta relativa del archivo local.
  3. Cuando hay conectividad a Internet (`NWPathMonitor`), el gestor de sincronización sube la imagen y actualiza la URL en SQLite/SwiftData y en PostgREST.

---

## 5. Algoritmo de Sincronización Incremental (Sync Engine)

1. **Detección de Cambios Locales:**
   * Cada entidad contiene un campo `fupdate: Int64` (timestamp en milisegundos).
   * La app compara el timestamp de la última sincronización guardado en `UserDefaults` (`last_successful_sync_timestamp`).
2. **Paso 1 - Subida (Push):**
   * Filtrar registros donde `fupdate > last_successful_sync_timestamp`.
   * Enviar a PostgREST con `resolution=merge-duplicates`.
3. **Paso 2 - Descarga (Pull):**
   * Solicitar a PostgREST: `GET /{tabla}?fupdate=gt.{last_successful_sync_timestamp}`.
   * Actualizar o insertar los registros entrantes en la base local.
4. **Paso 3 - Cierre:**
   * Actualizar `last_successful_sync_timestamp = System.currentTimeMillis()`.
