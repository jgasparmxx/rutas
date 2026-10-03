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

## 3. Matriz de Roles y Políticas RLS en PostgreSQL

### A. Definición de Jerarquía de Roles del Ecosistema
El atributo de rol en la tabla `senderistas` y en el token JWT se llama **`rol`** y sigue un orden estricto de herencia descendente:

```
['O' (Dueño)] ──hereda──> ['A' (Admin Agrupación)] ──hereda──> ['G' (Guía)] ──hereda──> ['S' (Senderista)]
```

| Nivel | Código `rol` | Nombre de Rol | Aplicación / Plataforma | Permisos, Herencia y Capacidades |
| :---: | :---: | :--- | :--- | :--- |
| **4** | **`'O'`** | **Dueño (Owner)** | App Gestión Central (C# Windows/Linux) y Móvil | **El más alto. Puede hacer TODO (O, A, G, S):** Superadministrador, cambia roles de usuarios, modera fotos/rutas, gestiona agrupaciones, publica salidas, crea/sube rutas y las consume. |
| **3** | **`'A'`** | **Admin Agrupación** | App Administración (Android / Windows) | **Hereda A, G y S:** Publica y gestiona invitaciones a salir de su agrupación. Puede mapear, crear y subir rutas a la nube (como Guía) y consumir rutas (como Senderista). |
| **2** | **`'G'`** | **Guía** | Móvil Android (Rutas Sendero) | **Hereda G y S:** Mapea senderos, **crea rutas y las sube a la nube**. Modifica/elimina sus propias rutas. Consume rutas (como Senderista). |
| **1** | **`'S'`** | **Senderista** | Móvil Android (Rutas Sendero) | **Nivel base (solo S):** **Consume rutas** (las descarga, visualiza y navega). Participa en salidas/invitaciones. **NO puede crear rutas en la nube ni gestionar salidas.** |

---

### B. Rol de Base de Datos para PostgREST
PostgREST se conecta con el rol `senderista`. Los permisos granulares se controlan leyendo los claims del JWT (`rol`, `email`, `id_agrupacion`):
```sql
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'senderista') THEN
    CREATE ROLE senderista NOLOGIN;
  END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO senderista;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO senderista;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO senderista;
```

---

### C. Políticas RLS para la tabla `rutas`
```sql
ALTER TABLE rutas ENABLE ROW LEVEL SECURITY;

-- 1. SELECT: Públicas (todos), privadas (creador, admin 'A' de su agrupación, o dueño 'O')
DROP POLICY IF EXISTS rutas_select_policy ON rutas;
CREATE POLICY rutas_select_policy ON rutas FOR SELECT
USING (
    publico = true 
    OR email_creador = (current_setting('request.jwt.claims', true)::jsonb ->> 'email')
    OR (
        (current_setting('request.jwt.claims', true)::jsonb ->> 'rol') = 'A'
        AND agrupacion = (current_setting('request.jwt.claims', true)::jsonb ->> 'id_agrupacion')
    )
    OR (current_setting('request.jwt.claims', true)::jsonb ->> 'rol') = 'O'
);

-- 2. INSERT: Guías ('G'), Administradores ('A') o Dueño ('O') pueden subir rutas a la nube
DROP POLICY IF EXISTS rutas_insert_policy ON rutas;
CREATE POLICY rutas_insert_policy ON rutas FOR INSERT
WITH CHECK (
    (current_setting('request.jwt.claims', true)::jsonb ->> 'rol') IN ('G', 'A', 'O')
    AND email_creador = (current_setting('request.jwt.claims', true)::jsonb ->> 'email')
);

-- 3. UPDATE: Creador de la ruta o Dueño ('O')
DROP POLICY IF EXISTS rutas_update_policy ON rutas;
CREATE POLICY rutas_update_policy ON rutas FOR UPDATE
USING (
    email_creador = (current_setting('request.jwt.claims', true)::jsonb ->> 'email')
    OR (current_setting('request.jwt.claims', true)::jsonb ->> 'rol') = 'O'
);

-- 4. DELETE: Creador de la ruta o Dueño ('O')
DROP POLICY IF EXISTS rutas_delete_policy ON rutas;
CREATE POLICY rutas_delete_policy ON rutas FOR DELETE
USING (
    email_creador = (current_setting('request.jwt.claims', true)::jsonb ->> 'email')
    OR (current_setting('request.jwt.claims', true)::jsonb ->> 'rol') = 'O'
);
```

---

### D. Políticas RLS para la tabla `invitaciones_ruta`
```sql
ALTER TABLE invitaciones_ruta ENABLE ROW LEVEL SECURITY;

-- 1. SELECT: Todos los senderistas autenticados pueden ver invitaciones
DROP POLICY IF EXISTS inv_select_policy ON invitaciones_ruta;
CREATE POLICY inv_select_policy ON invitaciones_ruta FOR SELECT
USING (true);

-- 2. INSERT/UPDATE/DELETE: Solo Administradores ('A') o Dueño ('O')
DROP POLICY IF EXISTS inv_modify_policy ON invitaciones_ruta;
CREATE POLICY inv_modify_policy ON invitaciones_ruta FOR ALL
USING (
    (current_setting('request.jwt.claims', true)::jsonb ->> 'rol') IN ('A', 'O')
);
```

---

### E. Políticas RLS para la tabla `senderistas`
```sql
ALTER TABLE senderistas ENABLE ROW LEVEL SECURITY;

-- 1. SELECT: Lectura permitida para autenticados (ver nombres en invitaciones, etc.)
DROP POLICY IF EXISTS senderistas_select_policy ON senderistas;
CREATE POLICY senderistas_select_policy ON senderistas FOR SELECT
USING (true);

-- 2. INSERT / UPDATE: Cada senderista gestiona su perfil; Dueño ('O') puede editar cualquiera y cambiar roles
DROP POLICY IF EXISTS senderistas_modify_policy ON senderistas;
CREATE POLICY senderistas_modify_policy ON senderistas FOR ALL
USING (
    email = (current_setting('request.jwt.claims', true)::jsonb ->> 'email')
    OR (current_setting('request.jwt.claims', true)::jsonb ->> 'rol') = 'O'
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

### C. Ajuste en la App Android (¡COMPLETADO el 23 de Septiembre de 2026!)
* Se creó [`JwtManager.kt`](file:///c:/GASPAR/baches/rutas/app/src/main/java/com/gaspar/rutassendero/sync/JwtManager.kt):
  * Genera tokens **HS256** válidos y compatibles con el RFC 7519 y PostgREST.
  * Incluye `role: "senderista"`, `email`, `telefono`, `iat` y `exp` (1 año de vigencia renovable).
  * Firmado con la clave secreta compartida en [`SecureCredentialsManager.kt`](file:///c:/GASPAR/baches/rutas/app/src/main/java/com/gaspar/rutassendero/sync/SecureCredentialsManager.kt).
* Se integró en [`CloudSyncManager.kt`](file:///c:/GASPAR/baches/rutas/app/src/main/java/com/gaspar/rutassendero/sync/CloudSyncManager.kt):
  * Inyecta automáticamente el encabezado `Authorization: Bearer <token>` en todas las peticiones `GET`, `POST` (Upsert) y `DELETE`.
  * **Degradación Transparente Automática (Fallback Seguro):** Si el servidor PostgREST aún no tiene configurada la variable `PGRST_JWT_SECRET` (responde con error `PGRST300: Server lacks JWT secret` o 401), la app automáticamente y de forma instantánea reintenta la petición en modo anónimo (sin el encabezado Bearer). De esta forma, la app NUNCA se bloquea, las agrupaciones se descargan, las rutas de otros senderistas se consultan normalmente y las búsquedas funcionan al 100%.
  * Tan pronto como se configure `PGRST_JWT_SECRET` en Render, la app detectará automáticamente el soporte y activará el uso de JWT sin requerir actualización adicional.
* Se blindó [`RutasViewModel.kt`](file:///c:/GASPAR/baches/rutas/app/src/main/java/com/gaspar/rutassendero/ui/RutasViewModel.kt):
  * `fetchAgrupaciones()` solo borra la tabla local si la respuesta de la nube trae registros válidos (`cloudData.isNotEmpty()`), evitando vaciar el caché offline.
  * Sincroniza y restaura `idAgrupacion` del senderista desde la nube al iniciar si estaba en blanco o difería localmente.
* Se verificó con prueba unitaria automatizada exitosa ([`JwtManagerTest.kt`](file:///c:/GASPAR/baches/rutas/app/src/test/java/com/gaspar/rutassendero/JwtManagerTest.kt)).

---

## 5. Instrucción para el Asistente de IA y Pasos Finales
1. **Paso 1 (App Android - Ya Resuelto):** La app incluye degradación transparente automática. Funciona tanto si Render tiene o no tiene `PGRST_JWT_SECRET`. La nueva versión compilada es `tksenderismo.apk`.
2. **Paso 2 (Render):** Cuando el administrador desee activar la seguridad RLS en el servidor, solo debe agregar la variable de entorno `PGRST_JWT_SECRET` en Render con el valor:
   `mi_clave_secreta_super_segura_rutas_sendero_2026`.
3. **Paso 3 (PostgreSQL):** Ejecutar el script SQL de la Sección 3 para activar las políticas RLS definitivas.
