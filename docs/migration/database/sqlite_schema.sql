-- =====================================================================
-- ESQUEMA SQLITE LOCAL (ROOM V34 EQUIVALENTE)
-- Base de datos: RutasSendero
-- Compatible con: SQLite 3, GRDB.swift, FMDB, CoreData / SwiftData
-- =====================================================================

-- 1. TABLA: rutas
CREATE TABLE IF NOT EXISTS rutas (
    id TEXT PRIMARY KEY NOT NULL,
    nombre TEXT NOT NULL,
    fecha TEXT NOT NULL,
    latitud_orig REAL NOT NULL,
    longitud_orig REAL NOT NULL,
    descripcion TEXT NOT NULL DEFAULT '',
    distancia_gps REAL NOT NULL DEFAULT 0.0,
    distancia_beacons REAL NOT NULL DEFAULT 0.0,
    fecha_hora_inicio TEXT,
    fecha_hora_fin TEXT,
    duracion_segundos INTEGER NOT NULL DEFAULT 0,
    desnivel_positivo REAL NOT NULL DEFAULT 0.0,
    desnivel_negativo REAL NOT NULL DEFAULT 0.0,
    contador_subidas INTEGER NOT NULL DEFAULT 0,
    contador_bajadas INTEGER NOT NULL DEFAULT 0,
    telefono TEXT NOT NULL DEFAULT '',
    datos_encriptados TEXT,
    conectividad REAL NOT NULL DEFAULT 0.0,
    codigo TEXT NOT NULL DEFAULT '',
    publico INTEGER NOT NULL DEFAULT 1,
    fecha_importacion INTEGER,
    fupdate INTEGER NOT NULL DEFAULT 0
);

-- 2. TABLA: puntos_ruta
CREATE TABLE IF NOT EXISTS puntos_ruta (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    id_ruta TEXT NOT NULL,
    fecha_hora TEXT NOT NULL,
    latitud REAL NOT NULL,
    longitud REAL NOT NULL,
    altitud REAL NOT NULL,
    descripcion TEXT NOT NULL,
    foto_path TEXT,
    es_fin_ruta INTEGER NOT NULL DEFAULT 0,
    tiene_conectividad INTEGER NOT NULL DEFAULT 0,
    estado INTEGER NOT NULL DEFAULT 1,
    fupdate INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(id_ruta) REFERENCES rutas(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_puntos_ruta_id_ruta ON puntos_ruta(id_ruta);

-- 3. TABLA: traza_ruta (Polilínea de alta densidad GPS)
CREATE TABLE IF NOT EXISTS traza_ruta (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    id_ruta TEXT NOT NULL,
    orden INTEGER NOT NULL,
    latitud REAL NOT NULL,
    longitud REAL NOT NULL,
    altitud REAL NOT NULL,
    fupdate INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(id_ruta) REFERENCES rutas(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_traza_ruta_id_ruta ON traza_ruta(id_ruta);

-- 4. TABLA: senderista (Perfil de usuario local)
CREATE TABLE IF NOT EXISTS senderista (
    email TEXT PRIMARY KEY NOT NULL,
    rol TEXT NOT NULL DEFAULT 'S',
    telefono TEXT NOT NULL,
    nombre TEXT NOT NULL,
    sexo TEXT NOT NULL,
    fecha_nacimiento TEXT NOT NULL,
    a_quienes_avisar TEXT NOT NULL,
    a_quien_avisar_telefono1 TEXT NOT NULL,
    a_quien_avisar_telefono2 TEXT,
    tiene_enfermedad_cronica TEXT NOT NULL,
    enfermedad_cronica1 TEXT,
    enfermedad_cronica2 TEXT,
    tiene_auto TEXT,
    direccion TEXT,
    lleva_mascota TEXT,
    alergias TEXT,
    tipo_sangre TEXT,
    seguro_medico TEXT,
    nivel_senderista TEXT NOT NULL DEFAULT 'principiante',
    id_beacon TEXT,
    minimo_longitud REAL NOT NULL DEFAULT 4.0,
    id_agrupacion TEXT,
    fupdate INTEGER NOT NULL DEFAULT 0,
    bloqueado INTEGER NOT NULL DEFAULT 0,
    consentimiento_terminos INTEGER NOT NULL DEFAULT 0,
    consentimiento_ficha_medica INTEGER NOT NULL DEFAULT 0,
    consentimiento_gps_multimedia INTEGER NOT NULL DEFAULT 0,
    timestamp_consentimiento_utc INTEGER,
    codigo_confianza TEXT,
    peso REAL,
    max_privadas INTEGER NOT NULL DEFAULT 3
);

-- 5. TABLA: agrupaciones
CREATE TABLE IF NOT EXISTS agrupaciones (
    id_agrupacion TEXT PRIMARY KEY NOT NULL,
    nombre TEXT NOT NULL,
    fupdate INTEGER NOT NULL DEFAULT 0
);

-- 6. TABLA: participantes (Compañeros de expedición detectados por BLE o sincronizados)
CREATE TABLE IF NOT EXISTS participantes (
    telefono_senderista TEXT PRIMARY KEY NOT NULL,
    nombre_senderista TEXT NOT NULL,
    id_beacon TEXT,
    latitud REAL,
    longitud REAL,
    distancia_estimada TEXT,
    ultimo_rssi INTEGER,
    ultima_deteccion INTEGER,
    fecha_ingreso INTEGER NOT NULL DEFAULT 0,
    a_quienes_avisar TEXT,
    a_quien_avisar_telefono1 TEXT,
    a_quien_avisar_telefono2 TEXT,
    tipo_sangre TEXT,
    fecha_nacimiento TEXT,
    alergias TEXT,
    enfermedades_cronicas TEXT,
    codigo_confianza TEXT,
    sexo TEXT,
    fupdate INTEGER NOT NULL DEFAULT 0
);

-- 7. TABLA: eventos
CREATE TABLE IF NOT EXISTS eventos (
    id_evento INTEGER PRIMARY KEY NOT NULL,
    nombre_evento TEXT NOT NULL,
    telefono_creador TEXT NOT NULL,
    fecha_hora_salida TEXT NOT NULL,
    punto_reunion TEXT NOT NULL,
    link_google_maps TEXT,
    cooperacion_minima REAL NOT NULL,
    fecha_limite_registro TEXT,
    instrucciones TEXT,
    latitud REAL,
    longitud REAL,
    cooperacion_realizada REAL,
    fecha_cooperacion_realizada TEXT,
    sincronizado_con_nube INTEGER NOT NULL DEFAULT 0,
    comprobante_pago BLOB,
    fecha_subida_comprobante TEXT,
    fupdate INTEGER NOT NULL DEFAULT 0,
    comentario TEXT
);

-- 8. TABLA: invitaciones_aceptadas
CREATE TABLE IF NOT EXISTS invitaciones_aceptadas (
    id_invitacion INTEGER PRIMARY KEY NOT NULL,
    id_ruta TEXT NOT NULL,
    nombre_ruta TEXT NOT NULL,
    telefono_creador TEXT NOT NULL,
    fecha_hora_salida TEXT NOT NULL,
    punto_reunion TEXT NOT NULL,
    link_google_maps TEXT,
    cooperacion_minima REAL NOT NULL,
    fecha_limite_registro TEXT,
    nivel TEXT,
    instrucciones TEXT,
    latitud REAL,
    longitud REAL,
    cooperacion_realizada REAL,
    fecha_cooperacion_realizada TEXT,
    sincronizado_con_nube INTEGER NOT NULL DEFAULT 0,
    conectividad REAL,
    distancia_gps REAL,
    desnivel_positivo REAL,
    desnivel_negativo REAL,
    contador_subidas INTEGER,
    contador_bajadas INTEGER,
    duracion_segundos INTEGER,
    comprobante_pago BLOB,
    fecha_subida_comprobante TEXT,
    fupdate INTEGER NOT NULL DEFAULT 0,
    tipo_ruta TEXT NOT NULL DEFAULT 'S',
    comentario TEXT
);

-- 9. TABLA: frases_dificultad
CREATE TABLE IF NOT EXISTS frases_dificultad (
    id TEXT PRIMARY KEY NOT NULL,
    categoria TEXT NOT NULL,
    frase TEXT NOT NULL,
    fupdate INTEGER NOT NULL DEFAULT 0
);
