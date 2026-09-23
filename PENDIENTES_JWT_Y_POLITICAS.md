# Resumen del Sistema, Rutas y Plan de Implementación JWT / RLS

Este documento sirve como registro y memoria técnica para el equipo y el asistente de IA (Antigravity) para dar continuidad a las tareas de seguridad, sincronización y políticas de base de datos.

---

## 1. Contexto y Cambios Realizados (22 de Septiembre de 2026)

### A. Corrección de Autoría y Separación de Rutas
* **Identificación de Autores Reales:** Se normalizó la base de datos en PostgreSQL (Neon) y la app para distinguir correctamente las rutas:
  * **Jorge Gaspar Ramírez** (`jgasparmxx@gmail.com` / Tel: `9616603835`): Sus rutas creadas localmente o en la nube se muestran en la pestaña **"Mis Rutas"**.
  * **Marcos Arroyo Cruz** (`colimotzinemo@gmail.com` / Tel: `3121220253`): Autor de *fe chical*, *colomitos*, *vila comala*, *prueba la cumbre total*, *jala-alcomun*, *rodro*, *campana*, *la cumbre 2*, *río San palmar*, *comala*, *picachos*. Se muestran en **"Otras Rutas"**.
  * **Miboro Bonilla** (`miboro.007@gmail.com` / Tel: `3122440776`): Autor de *La Campana*, *Rancho de Villa*. Se muestran en **"Otras Rutas"**.
  * **María de Lourdes** (`loudi1502@hotmail.com` / Tel: `3122716544`): Autora de *Cascadas Alas de Angel*, *gas*, *Río San Palmar*. Se muestran en **"Otras Rutas"**.
* **Blindaje contra borrado de rutas locales:** Se eliminó el error en `checkAndCleanOtherRoutes()` que borraba rutas locales cuando no estaban en la nube o no coincidía el teléfono. Ahora cualquier ruta creada en el teléfono (`fechaImportacion == null`) jamás es borrada automáticamente.
* **Sincronización en campo (Datos Móviles + Wi-Fi):** Se retiró la restricción que limitaba `triggerAutomaticCloudSync()` a Wi-Fi. Ahora sincroniza tanto en datos móviles como en Wi-Fi.
* **Borrado de "Otras Rutas" sin conexión:** Se retiró el requisito de internet para eliminar rutas ajenas descargadas en el teléfono, y se añadió `key(ruta.id)` en la lista de Compose para refresco visual inmediato.

---

## 2. Arquitectura de Seguridad Actual

1. **API:** PostgREST desplegado en Render (`https://postgrest-latest-09dh.onrender.com`).
2. **Conexión:** HTTPS / TLS.
3. **Rol en Base de Datos:** PostgREST se ejecuta actualmente con el rol anónimo `web_anon`.
4. **Cifrado de Puntos y Traza:** Cifrado autenticado cliente-a-cliente con **AES-256-GCM** (`CryptoUtils.kt`) almacenado en `datos_encriptados`.

---

## 3. Guía de Implementación de Políticas RLS (Row-Level Security) en PostgreSQL

Para garantizar que nadie pueda manipular rutas ajenas directamente en la base de datos, se deben aplicar las siguientes políticas en PostgreSQL:

### A. Crear Roles en PostgreSQL
```sql
-- Rol para usuarios autenticados mediante JWT
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'senderista') THEN
    CREATE ROLE senderista NOLOGIN;
  END IF;
END
$$;

-- Permisos básicos para el rol senderista
GRANT USAGE ON SCHEMA public TO senderista;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO senderista;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO senderista;
```

### B. Políticas para la tabla `rutas`
```sql
-- 1. Habilitar RLS
ALTER TABLE rutas ENABLE ROW LEVEL SECURITY;

-- 2. SELECT: Todos pueden ver rutas públicas; las privadas solo su creador autenticado
CREATE POLICY rutas_select_policy ON rutas
FOR SELECT
USING (
    publico = true 
    OR email_creador = (current_setting('request.jwt.claims', true)::jsonb ->> 'email')
);

-- 3. INSERT: El senderista solo puede registrar rutas con su propio email
CREATE POLICY rutas_insert_policy ON rutas
FOR INSERT
WITH CHECK (
    email_creador = (current_setting('request.jwt.claims', true)::jsonb ->> 'email')
);

-- 4. UPDATE: Solo el creador puede modificar su ruta
CREATE POLICY rutas_update_policy ON rutas
FOR UPDATE
USING (
    email_creador = (current_setting('request.jwt.claims', true)::jsonb ->> 'email')
);

-- 5. DELETE: Solo el creador puede eliminar su ruta
CREATE POLICY rutas_delete_policy ON rutas
FOR DELETE
USING (
    email_creador = (current_setting('request.jwt.claims', true)::jsonb ->> 'email')
);
```

---

## 4. Configuración del JWT para PostgREST

### A. Variable de Entorno en Render (Servicio PostgREST)
Configurar la variable:
* `PGRST_JWT_SECRET`: Clave secreta segura de al menos 32 caracteres (ejemplo: `mi_clave_secreta_super_segura_rutas_sendero_2026`).

### B. Estructura del Payload del Token JWT
```json
{
  "role": "senderista",
  "email": "jgasparmxx@gmail.com",
  "telefono": "9616603835",
  "exp": 1790000000
}
```

### C. Ajuste pendiente en la App Android (cuando se active el JWT)
En `CloudSyncManager.kt`, incluir el token en el encabezado de las peticiones HTTP de OkHttp:
```kotlin
val request = Request.Builder()
    .url(url)
    .header("Authorization", "Bearer $jwtToken")
    ...
```

---

## 5. Instrucción para el Asistente de IA
Cuando el usuario pida continuar o recordar este trabajo, lee este archivo para:
1. Conocer la autoría real de las rutas y respetar la regla de teléfonos.
2. Ayudar al usuario a desplegar las políticas RLS en PostgreSQL o integrar la generación/envío de JWT en `CloudSyncManager.kt`.
