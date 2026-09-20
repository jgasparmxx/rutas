CREATE SCHEMA "public";
CREATE SCHEMA "auth";
CREATE SCHEMA "neon_auth";
CREATE SCHEMA "pgrst";
CREATE TABLE "agrupaciones" (
	"id_agrupacion" varchar(50) PRIMARY KEY,
	"nombre" varchar(150) NOT NULL,
	"responsable" varchar(150) NOT NULL,
	"telefono" varchar(30) NOT NULL,
	"email" varchar(150) NOT NULL,
	"fecha_creacion" timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
	"clave" varchar(255)
);
CREATE TABLE "frases_dificultad" (
	"id" varchar(50) PRIMARY KEY,
	"categoria" varchar(50) NOT NULL,
	"frase" text NOT NULL
);
CREATE TABLE "invitaciones_ruta" (
	"id_invitacion" serial PRIMARY KEY,
	"id_ruta" varchar(255),
	"telefono" varchar(50) NOT NULL,
	"fecha_hora_salida" timestamp NOT NULL,
	"punto_reunion" varchar(500) NOT NULL,
	"link_google_maps" varchar(1000),
	"cooperacion_minima" double precision DEFAULT 0.0,
	"fecha_limite_registro" timestamp,
	"fecha_creacion" timestamp DEFAULT CURRENT_TIMESTAMP,
	"nivel" varchar(50),
	"instrucciones" text,
	"latitud" double precision,
	"longitud" double precision,
	"nombre_agrupacion" varchar(255)
);
CREATE TABLE "participantes_invitacion" (
	"id_invitacion" integer,
	"email_senderista" varchar(255),
	"telefono_senderista" varchar(50) NOT NULL,
	"cooperacion" double precision DEFAULT 0.0,
	"fecha_cooperacion" timestamp,
	"fecha_registro" timestamp DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT "participantes_invitacion_pkey" PRIMARY KEY("id_invitacion","email_senderista")
);
CREATE TABLE "requisitos_senderismo" (
	"id" serial PRIMARY KEY,
	"nivel_codigo" varchar(50) NOT NULL CONSTRAINT "requisitos_senderismo_nivel_codigo_key" UNIQUE,
	"nivel_nombre" varchar(100) NOT NULL,
	"descripcion_terreno" text NOT NULL,
	"equipo_requerido" text NOT NULL
);
CREATE TABLE "rutas" (
	"id" varchar(10) PRIMARY KEY,
	"nombre" varchar(150) NOT NULL,
	"fecha" date NOT NULL,
	"agrupacion" varchar(100) NOT NULL,
	"latitud_orig" double precision NOT NULL,
	"longitud_orig" double precision NOT NULL,
	"telefono_creador" varchar(30) NOT NULL,
	"descripcion" text NOT NULL,
	"distancia_gps" double precision DEFAULT 0.0 NOT NULL,
	"distancia_beacons" double precision DEFAULT 0.0 NOT NULL,
	"fecha_hora_inicio" timestamp with time zone,
	"fecha_hora_fin" timestamp with time zone,
	"duracion_segundos" bigint DEFAULT 0 NOT NULL,
	"desnivel_positivo" double precision DEFAULT 0.0 NOT NULL,
	"desnivel_negativo" double precision DEFAULT 0.0 NOT NULL,
	"contador_subidas" integer DEFAULT 0 NOT NULL,
	"contador_bajadas" integer DEFAULT 0 NOT NULL,
	"datos_encriptados" text,
	"fecha_registro_nube" timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
	"email_creador" varchar(150),
	"conectividad" real DEFAULT 0.0 NOT NULL,
	"codigo" varchar(5) DEFAULT '' NOT NULL,
	"publico" boolean DEFAULT false NOT NULL
);
CREATE TABLE "senderistas" (
	"email" varchar(150) PRIMARY KEY,
	"rol" varchar(5) DEFAULT 'S' NOT NULL,
	"telefono" varchar(30) NOT NULL,
	"nombre" varchar(150) NOT NULL,
	"sexo" varchar(5) NOT NULL,
	"id_agrupacion" varchar(50),
	"fecha_nacimiento" date NOT NULL,
	"a_quienes_avisar" text NOT NULL,
	"a_quien_avisar_telefono1" varchar(30) NOT NULL,
	"a_quien_avisar_telefono2" varchar(30),
	"tiene_enfermedad_cronica" varchar(2) DEFAULT 'N' NOT NULL,
	"enfermedad_cronica1" text,
	"enfermedad_cronica2" text,
	"tiene_auto" varchar(2) DEFAULT 'N',
	"direccion" text,
	"lleva_mascota" varchar(2) DEFAULT 'N',
	"alergias" text,
	"tipo_sangre" varchar(10),
	"seguro_medico" text,
	"nivel_senderista" varchar(30) DEFAULT 'principiante',
	"fupdate" timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
	"id_beacon" varchar(12),
	"minimo_longitud" double precision DEFAULT 4.0 NOT NULL,
	"bloqueado" boolean DEFAULT false,
	"consentimiento_terminos" boolean DEFAULT false,
	"consentimiento_ficha_medica" boolean DEFAULT false,
	"consentimiento_gps_multimedia" boolean DEFAULT false,
	"timestamp_consentimiento_utc" bigint
);
CREATE TABLE "neon_auth"."account" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"accountId" text NOT NULL,
	"providerId" text NOT NULL,
	"userId" uuid NOT NULL,
	"accessToken" text,
	"refreshToken" text,
	"idToken" text,
	"accessTokenExpiresAt" timestamp with time zone,
	"refreshTokenExpiresAt" timestamp with time zone,
	"scope" text,
	"password" text,
	"createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
	"updatedAt" timestamp with time zone NOT NULL
);
CREATE TABLE "neon_auth"."invitation" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"organizationId" uuid NOT NULL,
	"email" text NOT NULL,
	"role" text,
	"status" text NOT NULL,
	"expiresAt" timestamp with time zone NOT NULL,
	"createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
	"inviterId" uuid NOT NULL
);
CREATE TABLE "neon_auth"."jwks" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"publicKey" text NOT NULL,
	"privateKey" text NOT NULL,
	"createdAt" timestamp with time zone NOT NULL,
	"expiresAt" timestamp with time zone
);
CREATE TABLE "neon_auth"."member" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"organizationId" uuid NOT NULL,
	"userId" uuid NOT NULL,
	"role" text NOT NULL,
	"createdAt" timestamp with time zone NOT NULL
);
CREATE TABLE "neon_auth"."organization" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"name" text NOT NULL,
	"slug" text NOT NULL CONSTRAINT "organization_slug_key" UNIQUE,
	"logo" text,
	"createdAt" timestamp with time zone NOT NULL,
	"metadata" text
);
CREATE TABLE "neon_auth"."project_config" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"name" text NOT NULL,
	"endpoint_id" text NOT NULL CONSTRAINT "project_config_endpoint_id_key" UNIQUE,
	"created_at" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
	"updated_at" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
	"trusted_origins" jsonb NOT NULL,
	"social_providers" jsonb NOT NULL,
	"email_provider" jsonb,
	"email_and_password" jsonb,
	"allow_localhost" boolean NOT NULL,
	"plugin_configs" jsonb,
	"webhook_config" jsonb
);
CREATE TABLE "neon_auth"."session" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"expiresAt" timestamp with time zone NOT NULL,
	"token" text NOT NULL CONSTRAINT "session_token_key" UNIQUE,
	"createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
	"updatedAt" timestamp with time zone NOT NULL,
	"ipAddress" text,
	"userAgent" text,
	"userId" uuid NOT NULL,
	"impersonatedBy" text,
	"activeOrganizationId" text
);
CREATE TABLE "neon_auth"."user" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"name" text NOT NULL,
	"email" text NOT NULL CONSTRAINT "user_email_key" UNIQUE,
	"emailVerified" boolean NOT NULL,
	"image" text,
	"createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
	"updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
	"role" text,
	"banned" boolean,
	"banReason" text,
	"banExpires" timestamp with time zone
);
CREATE TABLE "neon_auth"."verification" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	"identifier" text NOT NULL,
	"value" text NOT NULL,
	"expiresAt" timestamp with time zone NOT NULL,
	"createdAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
	"updatedAt" timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE UNIQUE INDEX "agrupaciones_pkey" ON "agrupaciones" ("id_agrupacion");
CREATE UNIQUE INDEX "frases_dificultad_pkey" ON "frases_dificultad" ("id");
CREATE UNIQUE INDEX "invitaciones_ruta_pkey" ON "invitaciones_ruta" ("id_invitacion");
CREATE UNIQUE INDEX "participantes_invitacion_pkey" ON "participantes_invitacion" ("id_invitacion","email_senderista");
CREATE UNIQUE INDEX "requisitos_senderismo_nivel_codigo_key" ON "requisitos_senderismo" ("nivel_codigo");
CREATE UNIQUE INDEX "requisitos_senderismo_pkey" ON "requisitos_senderismo" ("id");
CREATE UNIQUE INDEX "rutas_pkey" ON "rutas" ("id");
CREATE UNIQUE INDEX "senderistas_pkey" ON "senderistas" ("email");
CREATE UNIQUE INDEX "account_pkey" ON "neon_auth"."account" ("id");
CREATE INDEX "account_userId_idx" ON "neon_auth"."account" ("userId");
CREATE INDEX "invitation_email_idx" ON "neon_auth"."invitation" ("email");
CREATE INDEX "invitation_organizationId_idx" ON "neon_auth"."invitation" ("organizationId");
CREATE UNIQUE INDEX "invitation_pkey" ON "neon_auth"."invitation" ("id");
CREATE UNIQUE INDEX "jwks_pkey" ON "neon_auth"."jwks" ("id");
CREATE INDEX "member_organizationId_idx" ON "neon_auth"."member" ("organizationId");
CREATE UNIQUE INDEX "member_pkey" ON "neon_auth"."member" ("id");
CREATE INDEX "member_userId_idx" ON "neon_auth"."member" ("userId");
CREATE UNIQUE INDEX "organization_pkey" ON "neon_auth"."organization" ("id");
CREATE UNIQUE INDEX "organization_slug_key" ON "neon_auth"."organization" ("slug");
CREATE UNIQUE INDEX "organization_slug_uidx" ON "neon_auth"."organization" ("slug");
CREATE UNIQUE INDEX "project_config_endpoint_id_key" ON "neon_auth"."project_config" ("endpoint_id");
CREATE UNIQUE INDEX "project_config_pkey" ON "neon_auth"."project_config" ("id");
CREATE UNIQUE INDEX "session_pkey" ON "neon_auth"."session" ("id");
CREATE UNIQUE INDEX "session_token_key" ON "neon_auth"."session" ("token");
CREATE INDEX "session_userId_idx" ON "neon_auth"."session" ("userId");
CREATE UNIQUE INDEX "user_email_key" ON "neon_auth"."user" ("email");
CREATE UNIQUE INDEX "user_pkey" ON "neon_auth"."user" ("id");
CREATE INDEX "verification_identifier_idx" ON "neon_auth"."verification" ("identifier");
CREATE UNIQUE INDEX "verification_pkey" ON "neon_auth"."verification" ("id");
ALTER TABLE "invitaciones_ruta" ADD CONSTRAINT "invitaciones_ruta_id_ruta_fkey" FOREIGN KEY ("id_ruta") REFERENCES "rutas"("id") ON DELETE CASCADE;
ALTER TABLE "participantes_invitacion" ADD CONSTRAINT "participantes_invitacion_email_senderista_fkey" FOREIGN KEY ("email_senderista") REFERENCES "senderistas"("email") ON DELETE CASCADE;
ALTER TABLE "participantes_invitacion" ADD CONSTRAINT "participantes_invitacion_id_invitacion_fkey" FOREIGN KEY ("id_invitacion") REFERENCES "invitaciones_ruta"("id_invitacion") ON DELETE CASCADE;
ALTER TABLE "rutas" ADD CONSTRAINT "rutas_email_creador_fkey" FOREIGN KEY ("email_creador") REFERENCES "senderistas"("email") ON DELETE CASCADE;
ALTER TABLE "neon_auth"."account" ADD CONSTRAINT "account_userId_fkey" FOREIGN KEY ("userId") REFERENCES "neon_auth"."user"("id") ON DELETE CASCADE;
ALTER TABLE "neon_auth"."invitation" ADD CONSTRAINT "invitation_inviterId_fkey" FOREIGN KEY ("inviterId") REFERENCES "neon_auth"."user"("id") ON DELETE CASCADE;
ALTER TABLE "neon_auth"."invitation" ADD CONSTRAINT "invitation_organizationId_fkey" FOREIGN KEY ("organizationId") REFERENCES "neon_auth"."organization"("id") ON DELETE CASCADE;
ALTER TABLE "neon_auth"."member" ADD CONSTRAINT "member_organizationId_fkey" FOREIGN KEY ("organizationId") REFERENCES "neon_auth"."organization"("id") ON DELETE CASCADE;
ALTER TABLE "neon_auth"."member" ADD CONSTRAINT "member_userId_fkey" FOREIGN KEY ("userId") REFERENCES "neon_auth"."user"("id") ON DELETE CASCADE;
ALTER TABLE "neon_auth"."session" ADD CONSTRAINT "session_userId_fkey" FOREIGN KEY ("userId") REFERENCES "neon_auth"."user"("id") ON DELETE CASCADE;