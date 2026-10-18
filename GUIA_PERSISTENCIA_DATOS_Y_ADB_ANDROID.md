# Guía de Persistencia de Datos, Auto-Backup y Comandos ADB en Android

Esta guía recopila todo lo relacionado con el problema de datos residuales (bases de datos y variables que no se borran al desinstalar una app) y el funcionamiento de la terminal de comandos en Android.

---

## 1. ¿Por qué las apps conservan datos tras ser desinstaladas?

Muchos desarrolladores asumen que al desinstalar una aplicación de Android, todo su contenido se elimina para siempre. Sin embargo, al reinstalarla, la app a veces arranca con la base de datos anterior o variables de sesión intactas.

### La Causa Principal: Android Auto Backup (Google Cloud)
Desde Android 6.0 (API 23), el sistema operativo incluye por defecto la función **Auto Backup**:
* Si en el archivo `AndroidManifest.xml` tienes `android:allowBackup="true"` (o no lo especificas), el servicio de Google Play Services **sube una copia de seguridad periódica y silenciosa a la cuenta de Google Drive del usuario**.
* Esta copia incluye:
  * Bases de datos SQLite / Room (`/data/data/com.tu.paquete/databases/`)
  * Preferencias (`SharedPreferences` en `/data/data/com.tu.paquete/shared_prefs/`)
  * Archivos creados en el almacenamiento interno privado.
* **Al desinstalar la app:** Los datos se borran físicamente del teléfono.
* **Al reinstalar la app:** Antes de abrir la app por primera vez, Android descarga automáticamente el respaldo de Google Drive y restaura la base de datos y preferencias sin avisar.

### ¿Por qué no encontraste archivos en el explorador del teléfono?
* El almacenamiento interno de cada app vive en `/data/user/0/<tu.paquete>/`. 
* Este directorio está protegido por permisos del kernel de Linux; ningún explorador de archivos común puede verlo (a menos que el teléfono esté rooteado). 
* Lo que ves en exploradores de archivos es el almacenamiento público compartido (`/sdcard/` o `/storage/emulated/0/`), donde tu base de datos Room nunca se guardó. La restauración venía directamente de los servidores de Google.

---

## 2. Solución Definitiva en el Código

Para que tu aplicación **nunca** guarde ni restaure datos automáticamente en la nube ni al transferir de un teléfono a otro, se aplican dos configuraciones:

### A. En `AndroidManifest.xml`
Dentro de la etiqueta `<application>`:

```xml
<application
    android:name=".BachesApplication"
    android:allowBackup="false"
    android:fullBackupContent="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    ...>
```

### B. En `res/xml/data_extraction_rules.xml` (Para Android 12 en adelante)
Este archivo excluye todo el almacenamiento privado tanto de la nube como de transferencias cable/Wi-Fi directas entre dispositivos:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <!-- Excluye todo el almacenamiento de la copia de seguridad de Google -->
        <exclude path="." />
    </cloud-backup>
    <device-transfer>
        <!-- Excluye todo el almacenamiento de transferencias entre teléfonos -->
        <exclude path="." />
    </device-transfer>
</data-extraction-rules>
```

---

## 3. ¿Cómo dar comandos en Android?

A diferencia de Windows o Linux, **los teléfonos Android no tienen una aplicación de consola o terminal por defecto** para el usuario. Existen dos formas de interactuar con el sistema a bajo nivel o hacer limpiezas totales:

---

### Método 1: Limpieza Manual desde la Pantalla (Sin comandos)

No necesitas usar una terminal para resetear la aplicación por completo:

1. Ve a **Ajustes** (o Configuración) en el teléfono.
2. Selecciona **Aplicaciones** (o *Gestionar aplicaciones*).
3. Busca tu app (**Senderismo con Amigos** / `com.gaspar.rutassendero`).
4. Entra a **Almacenamiento y caché**.
5. Pulsa en **"Borrar almacenamiento"** o **"Borrar todos los datos"** (⚠️ *No* es lo mismo que "Borrar caché"; borrar datos destruye la base de datos Room y los archivos SharedPreferences al instante).
6. Si deseas desinstalarla, desinstálala inmediatamente después de borrar los datos.

---

### Método 2: Usar Comandos desde la PC hacia el Teléfono (ADB)

Los comandos para Android se envían desde la computadora (Windows) al teléfono a través de la herramienta **ADB** (*Android Debug Bridge*), incluida en el Android SDK.

#### Paso A: Activar el modo desarrollador en el teléfono
1. Ve a **Ajustes > Información del teléfono** (o *Acerca del dispositivo*).
2. Busca la opción **Número de compilación** y pulsa sobre ella **7 veces seguidas** rápidamente.
3. El sistema te pedirá tu PIN/patrón y mostrará el mensaje: *"¡Ya eres desarrollador!"*.
4. Regresa a **Ajustes > Sistema > Opciones para desarrolladores**.
5. Activa la casilla **Depuración por USB**.
6. Conecta el teléfono a tu PC con el cable USB.
7. En la pantalla del móvil aparecerá una ventana: *"¿Permitir depuración por USB desde este equipo?"* -> Marca **"Permitir siempre"** y pulsa **Permitir**.

#### Paso B: Ejecutar los comandos en tu PC (Windows)
Abre la pestaña **Terminal** integrada en Android Studio (o abre PowerShell / CMD en Windows):

1. **Comprobar que el teléfono está conectado y detectado:**
   ```powershell
   adb devices
   ```
   *Respuesta esperada:* Una serie alfanumérica seguida de la palabra `device`.

2. **Limpiar todos los datos de la app (Equivalente instantáneo a "Borrar datos"):**
   ```powershell
   adb shell pm clear com.gaspar.rutassendero
   ```
   *Respuesta:* `Success`. (La base de datos queda vacía sin necesidad de desinstalar la app).

3. **Desinstalar la aplicación por completo:**
   ```powershell
   adb uninstall com.gaspar.rutassendero
   ```
   *(Nota: Nunca uses el parámetro `-k`, ya que `adb uninstall -k` le indica al teléfono que conserve la base de datos).*

4. **Ver las bases de datos de tu app (Modo interactivo shell):**
   ```powershell
   adb shell run-as com.gaspar.rutassendero ls -la databases/
   ```

---

## 4. Otros Casos Extraños que Pueden Dejar Residuos

Si alguna vez notas que un teléfono retiene datos a pesar de todo:

1. **Perfiles de Trabajo / Carpeta Segura / Dual Messenger (Samsung / Xiaomi / Huawei):**
   * Teléfonos con funciones como "Carpeta Segura", "Dual Messenger" o "Segundo Espacio" mantienen dos instancias separadas de la app. Si no la desinstalas de ambos perfiles, los datos pueden permanecer.
2. **Archivos en Carpetas Públicas:**
   * Si en tu código alguna vez guardas fotos, exportaciones GPX o JSONs en carpetas como `/sdcard/Download/` o `/sdcard/Documents/`, Android **nunca** las borrará al desinstalar. Solo se eliminan automáticamente los archivos dentro de `context.getExternalFilesDir(...)` o el almacenamiento interno.
3. **Copia de seguridad en Google Drive (Manual):**
   * Puedes comprobar qué copias tiene tu cuenta abriendo la aplicación **Google Drive** en tu móvil > Menú lateral (las tres líneas) > **Copias de seguridad**. Si ves una copia del dispositivo, puedes eliminarla manualmente desde allí.
