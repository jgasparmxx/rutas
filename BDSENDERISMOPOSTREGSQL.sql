-- ==============================================================================
-- BASE DE DATOS: Rutas Sendero (PostgreSQL / Neon)
-- Esquema DDL Consolidado y Actualizado
-- Compatible con: Entidades Android Room (v30) y PostgREST API
-- ==============================================================================

-- 1. ESQUEMAS
CREATE SCHEMA IF NOT EXISTS "public";
CREATE SCHEMA IF NOT EXISTS "auth";
CREATE SCHEMA IF NOT EXISTS "neon_auth";
CREATE SCHEMA IF NOT EXISTS "pgrst";

-- ==============================================================================
-- 2. TABLAS DEL ESQUEMA PÚBLICO (DATOS DE LA APP)
-- ==============================================================================

-- 2.1 Tabla: agrupaciones
CREATE TABLE IF NOT EXISTS "agrupaciones" (
    "id_agrupacion" VARCHAR(50) PRIMARY KEY,
    "nombre" VARCHAR(150) NOT NULL,
    "responsable" VARCHAR(150),
    "telefono" VARCHAR(30),
    "email" VARCHAR(150),
    "fecha_creacion" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "clave" VARCHAR(255)
);

-- 2.2 Tabla: frases_dificultad
CREATE TABLE IF NOT EXISTS "frases_dificultad" (
    "id" VARCHAR(50) PRIMARY KEY,
    "categoria" VARCHAR(50) NOT NULL,
    "frase" TEXT NOT NULL
);

-- 2.3 Tabla: requisitos_senderismo
CREATE TABLE IF NOT EXISTS "requisitos_senderismo" (
    "id" SERIAL PRIMARY KEY,
    "nivel_codigo" VARCHAR(50) NOT NULL UNIQUE,
    "nivel_nombre" VARCHAR(100) NOT NULL,
    "descripcion_terreno" TEXT NOT NULL,
    "equipo_requerido" TEXT NOT NULL
);

-- 2.4 Tabla: senderistas (Sincronizado con SenderistaEntity Room v30)
CREATE TABLE IF NOT EXISTS "senderistas" (
    "email" VARCHAR(150) PRIMARY KEY,
    "rol" VARCHAR(5) NOT NULL DEFAULT 'S',
    "telefono" VARCHAR(30) NOT NULL,
    "nombre" VARCHAR(150) NOT NULL,
    "sexo" VARCHAR(5) NOT NULL,
    "fecha_nacimiento" DATE NOT NULL,
    "id_agrupacion" VARCHAR(50) REFERENCES "agrupaciones"("id_agrupacion") ON DELETE SET NULL,
    "a_quienes_avisar" TEXT NOT NULL,
    "a_quien_avisar_telefono1" VARCHAR(30) NOT NULL,
    "a_quien_avisar_telefono2" VARCHAR(30),
    "tiene_enfermedad_cronica" VARCHAR(2) NOT NULL DEFAULT 'N',
    "enfermedad_cronica1" TEXT,
    "enfermedad_cronica2" TEXT,
    "tiene_auto" VARCHAR(2) DEFAULT 'N',
    "direccion" TEXT,
    "lleva_mascota" VARCHAR(2) DEFAULT 'N',
    "alergias" TEXT,
    "tipo_sangre" VARCHAR(10),
    "seguro_medico" TEXT,
    "nivel_senderista" VARCHAR(30) DEFAULT 'principiante',
    "fupdate" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "id_beacon" VARCHAR(12),
    "minimo_longitud" DOUBLE PRECISION NOT NULL DEFAULT 4.0,
    "bloqueado" BOOLEAN DEFAULT FALSE,
    "consentimiento_terminos" BOOLEAN DEFAULT FALSE,
    "consentimiento_ficha_medica" BOOLEAN DEFAULT FALSE,
    "consentimiento_gps_multimedia" BOOLEAN DEFAULT FALSE,
    "timestamp_consentimiento_utc" BIGINT,
    "codigo_confianza" VARCHAR(6),
    "peso" DOUBLE PRECISION,
    "max_privadas" INTEGER DEFAULT 3
);

-- 2.5 Tabla: rutas (Sincronizado con RutaEntity Room v30)
CREATE TABLE IF NOT EXISTS "rutas" (
    "id" VARCHAR(10) PRIMARY KEY,
    "nombre" VARCHAR(150) NOT NULL,
    "fecha" DATE NOT NULL,
    "agrupacion" VARCHAR(100) NOT NULL DEFAULT 'MH',
    "latitud_orig" DOUBLE PRECISION NOT NULL,
    "longitud_orig" DOUBLE PRECISION NOT NULL,
    "telefono_creador" VARCHAR(30) NOT NULL,
    "descripcion" TEXT NOT NULL,
    "distancia_gps" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "distancia_beacons" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "fecha_hora_inicio" TIMESTAMP WITH TIME ZONE,
    "fecha_hora_fin" TIMESTAMP WITH TIME ZONE,
    "duracion_segundos" BIGINT NOT NULL DEFAULT 0,
    "desnivel_positivo" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "desnivel_negativo" DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    "contador_subidas" INTEGER NOT NULL DEFAULT 0,
    "contador_bajadas" INTEGER NOT NULL DEFAULT 0,
    "datos_encriptados" TEXT,
    "fecha_registro_nube" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    "email_creador" VARCHAR(150) REFERENCES "senderistas"("email") ON DELETE CASCADE,
    "conectividad" REAL NOT NULL DEFAULT 0.0,
    "codigo" VARCHAR(10) NOT NULL DEFAULT '',
    "publico" BOOLEAN NOT NULL DEFAULT FALSE
);

-- 2.6 Tabla: invitaciones_ruta
CREATE TABLE IF NOT EXISTS "invitaciones_ruta" (
    "id_invitacion" SERIAL PRIMARY KEY,
    "id_ruta" VARCHAR(10) REFERENCES "rutas"("id") ON DELETE CASCADE,
    "telefono" VARCHAR(50) NOT NULL,
    "fecha_hora_salida" TIMESTAMP NOT NULL,
    "punto_reunion" VARCHAR(500) NOT NULL,
    "link_google_maps" VARCHAR(1000),
    "cooperacion_minima" DOUBLE PRECISION DEFAULT 0.0,
    "fecha_limite_registro" TIMESTAMP,
    "fecha_creacion" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "nivel" VARCHAR(50),
    "instrucciones" TEXT,
    "latitud" DOUBLE PRECISION,
    "longitud" DOUBLE PRECISION,
    "nombre_agrupacion" VARCHAR(255)
);

-- 2.7 Tabla: participantes_invitacion
CREATE TABLE IF NOT EXISTS "participantes_invitacion" (
    "id_invitacion" INTEGER NOT NULL REFERENCES "invitaciones_ruta"("id_invitacion") ON DELETE CASCADE,
    "email_senderista" VARCHAR(255) NOT NULL REFERENCES "senderistas"("email") ON DELETE CASCADE,
    "telefono_senderista" VARCHAR(50) NOT NULL,
    "cooperacion" DOUBLE PRECISION DEFAULT 0.0,
    "fecha_cooperacion" TIMESTAMP,
    "fecha_registro" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "participantes_invitacion_pkey" PRIMARY KEY ("id_invitacion", "email_senderista")
);

-- ==============================================================================
-- 3. ESQUEMA NEON AUTH (AUTENTICACIÓN INTEGRADA DE NEON)
-- ==============================================================================

CREATE TABLE IF NOT EXISTS "neon_auth"."user" (
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "name" TEXT NOT NULL,
    "email" TEXT NOT NULL UNIQUE,
    "emailVerified" BOOLEAN NOT NULL,
    "image" TEXT,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "role" TEXT,
    "banned" BOOLEAN,
    "banReason" TEXT,
    "banExpires" TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS "neon_auth"."session" (
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "expiresAt" TIMESTAMP WITH TIME ZONE NOT NULL,
    "token" TEXT NOT NULL UNIQUE,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL,
    "ipAddress" TEXT,
    "userAgent" TEXT,
    "userId" UUID NOT NULL REFERENCES "neon_auth"."user"("id") ON DELETE CASCADE,
    "impersonatedBy" TEXT,
    "activeOrganizationId" TEXT
);

CREATE TABLE IF NOT EXISTS "neon_auth"."account" (
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "accountId" TEXT NOT NULL,
    "providerId" TEXT NOT NULL,
    "userId" UUID NOT NULL REFERENCES "neon_auth"."user"("id") ON DELETE CASCADE,
    "accessToken" TEXT,
    "refreshToken" TEXT,
    "idToken" TEXT,
    "accessTokenExpiresAt" TIMESTAMP WITH TIME ZONE,
    "refreshTokenExpiresAt" TIMESTAMP WITH TIME ZONE,
    "scope" TEXT,
    "password" TEXT,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS "neon_auth"."organization" (
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "name" TEXT NOT NULL,
    "slug" TEXT NOT NULL UNIQUE,
    "logo" TEXT,
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL,
    "metadata" TEXT
);

CREATE TABLE IF NOT EXISTS "neon_auth"."member" (
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "organizationId" UUID NOT NULL REFERENCES "neon_auth"."organization"("id") ON DELETE CASCADE,
    "userId" UUID NOT NULL REFERENCES "neon_auth"."user"("id") ON DELETE CASCADE,
    "role" TEXT NOT NULL,
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS "neon_auth"."invitation" (
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "organizationId" UUID NOT NULL REFERENCES "neon_auth"."organization"("id") ON DELETE CASCADE,
    "email" TEXT NOT NULL,
    "role" TEXT,
    "status" TEXT NOT NULL,
    "expiresAt" TIMESTAMP WITH TIME ZONE NOT NULL,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "inviterId" UUID NOT NULL REFERENCES "neon_auth"."user"("id") ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS "neon_auth"."jwks" (
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "publicKey" TEXT NOT NULL,
    "privateKey" TEXT NOT NULL,
    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL,
    "expiresAt" TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS "neon_auth"."project_config" (
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "name" TEXT NOT NULL,
    "endpoint_id" TEXT NOT NULL UNIQUE,
    "created_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updated_at" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "trusted_origins" JSONB NOT NULL,
    "social_providers" JSONB NOT NULL,
    "email_provider" JSONB,
    "email_and_password" JSONB,
    "allow_localhost" BOOLEAN NOT NULL,
    "plugin_configs" JSONB,
    "webhook_config" JSONB
);

CREATE TABLE IF NOT EXISTS "neon_auth"."verification" (
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "identifier" TEXT NOT NULL,
    "value" TEXT NOT NULL,
    "expiresAt" TIMESTAMP WITH TIME ZONE NOT NULL,
    "createdAt" TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL
);

-- ==============================================================================
-- 4. ÍNDICES DE RENDIMIENTO
-- ==============================================================================

CREATE INDEX IF NOT EXISTS "idx_senderistas_telefono" ON "senderistas" ("telefono");
CREATE INDEX IF NOT EXISTS "idx_senderistas_id_beacon" ON "senderistas" ("id_beacon");
CREATE INDEX IF NOT EXISTS "idx_rutas_telefono_creador" ON "rutas" ("telefono_creador");
CREATE INDEX IF NOT EXISTS "idx_rutas_email_creador" ON "rutas" ("email_creador");
CREATE INDEX IF NOT EXISTS "idx_rutas_fecha_registro_nube" ON "rutas" ("fecha_registro_nube" DESC);
CREATE INDEX IF NOT EXISTS "idx_invitaciones_fecha_salida" ON "invitaciones_ruta" ("fecha_hora_salida");
CREATE INDEX IF NOT EXISTS "idx_participantes_email" ON "participantes_invitacion" ("email_senderista");
CREATE INDEX IF NOT EXISTS "idx_neon_auth_session_user" ON "neon_auth"."session" ("userId");
CREATE INDEX IF NOT EXISTS "idx_neon_auth_account_user" ON "neon_auth"."account" ("userId");

-- ==============================================================================
-- 5. CONFIGURACIÓN DE SEGURIDAD Y ROLES PARA POSTGREST
-- ==============================================================================

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'web_anon') THEN
    CREATE ROLE web_anon NOLOGIN;
  END IF;
END
$$;

-- Permisos de lectura/escritura en el esquema public para la API REST
GRANT USAGE ON SCHEMA public TO web_anon;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO web_anon;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO web_anon;

-- Permitir que el usuario de conexión (neondb_owner) asuma el rol web_anon en PostgREST
GRANT web_anon TO CURRENT_USER;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO web_anon;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO web_anon;

-- Vista para estadísticas de rutas consumible directamente por PostgREST
CREATE OR REPLACE VIEW vista_estadisticas_rutas AS
SELECT r.nombre, COUNT(p.email_senderista)::integer as total_participantes
FROM rutas r
LEFT JOIN invitaciones_ruta i ON r.id = i.id_ruta
LEFT JOIN participantes_invitacion p ON i.id_invitacion = p.id_invitacion
GROUP BY r.nombre;

GRANT SELECT ON vista_estadisticas_rutas TO web_anon;

-- Bloqueo de seguridad: la API no debe exponer las tablas internas de auth
REVOKE ALL ON SCHEMA neon_auth FROM web_anon;
REVOKE ALL ON SCHEMA auth FROM web_anon;
REVOKE ALL ON SCHEMA pgrst FROM web_anon;