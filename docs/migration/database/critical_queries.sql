-- =====================================================================
-- CONSULTAS CRÍTICAS DE LOS DAOS (KOTLIN ROOM -> SQL PURO / SWIFT)
-- Ubicación: /docs/migration/database/critical_queries.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. RUTAS DAO
-- ---------------------------------------------------------------------

-- Obtener todas las rutas ordenadas cronológicamente
SELECT * FROM rutas ORDER BY fecha DESC, id DESC;

-- Obtener una ruta por su ID único
SELECT * FROM rutas WHERE id = :idRuta LIMIT 1;

-- Obtener puntos de una ruta ordenados por ID incremental
SELECT * FROM puntos_ruta WHERE id_ruta = :idRuta ORDER BY id ASC;

-- Obtener la traza GPS continua de una ruta
SELECT * FROM traza_ruta WHERE id_ruta = :idRuta ORDER BY orden ASC;

-- Obtener rutas no sincronizadas o modificadas recientemente
SELECT * FROM rutas WHERE fupdate > :lastSyncTimestamp;

-- Obtener puntos no sincronizados
SELECT * FROM puntos_ruta WHERE fupdate > :lastSyncTimestamp;

-- Contar rutas públicas del usuario
SELECT COUNT(*) FROM rutas WHERE telefono = :telefono AND publico = 1;

-- Contar rutas privadas del usuario
SELECT COUNT(*) FROM rutas WHERE telefono = :telefono AND publico = 0;

-- ---------------------------------------------------------------------
-- 2. SENDERISTA DAO
-- ---------------------------------------------------------------------

-- Obtener el perfil activo
SELECT * FROM senderista LIMIT 1;

-- Obtener el senderista por correo electrónico
SELECT * FROM senderista WHERE email = :email LIMIT 1;

-- Actualizar coordenadas actuales del senderista
UPDATE senderista 
SET fupdate = :timestamp 
WHERE email = :email;

-- ---------------------------------------------------------------------
-- 3. PARTICIPANTES DAO (Balizas BLE y Compañeros)
-- ---------------------------------------------------------------------

-- Obtener todos los participantes activos detectados
SELECT * FROM participantes ORDER BY ultima_deteccion DESC;

-- Actualizar o insertar participante por telemetría BLE
INSERT INTO participantes (
    telefono_senderista, 
    nombre_senderista, 
    id_beacon, 
    latitud, 
    longitud, 
    ultimo_rssi, 
    ultima_deteccion, 
    distancia_estimada,
    fupdate
) VALUES (
    :telefono, 
    :nombre, 
    :idBeacon, 
    :latitud, 
    :longitud, 
    :rssi, 
    :timestamp, 
    :distancia,
    :timestamp
)
ON CONFLICT(telefono_senderista) DO UPDATE SET
    latitud = excluded.latitud,
    longitud = excluded.longitud,
    ultimo_rssi = excluded.ultimo_rssi,
    ultima_deteccion = excluded.ultima_deteccion,
    distancia_estimada = excluded.distancia_estimada,
    fupdate = excluded.fupdate;

-- Limpiar participantes inactivos (hace más de 1 hora)
DELETE FROM participantes WHERE ultima_deteccion < :haceUnaHora;

-- ---------------------------------------------------------------------
-- 4. EVENTOS E INVITACIONES DAO
-- ---------------------------------------------------------------------

-- Obtener todas las invitaciones aceptadas
SELECT * FROM invitaciones_aceptadas ORDER BY fecha_hora_salida ASC;

-- Obtener eventos pendientes de subir comprobante
SELECT * FROM eventos WHERE comprobante_pago IS NOT NULL AND sincronizado_con_nube = 0;

-- ---------------------------------------------------------------------
-- 5. FRASES DE DIFICULTAD
-- ---------------------------------------------------------------------

-- Obtener frases por categoría (Principiante, Intermedio, Experto)
SELECT frase FROM frases_dificultad WHERE categoria = :categoria ORDER BY RANDOM() LIMIT 1;
