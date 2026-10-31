# Guía de Despliegue a TestFlight con GitHub Actions (Mac Intel)
**Proyecto:** RutasSendero (iOS)  
**Fecha:** Octubre 2026  
**Objetivo:** Distribuir la aplicación a testers mediante TestFlight superando la limitación de hardware de la Mac Intel.

---

## 1. Resumen y Diagnóstico Técnico

### El escenario inicial:
1. Te registraste en el **Apple Developer Program** y querías compartir tu app con testers mediante **TestFlight**.
2. Tu entorno de desarrollo es una **Mac con procesador Intel** corriendo **macOS Sonoma**.
3. En esta configuración de hardware/SO, la versión máxima de Xcode soportada e instalada es **Xcode 16.2** (con SDK de iOS 18.2).

### Los pasos automatizados con éxito en tu proyecto:
Durante nuestra sesión conectamos directamente con los servicios de compilación y empaquetado de Apple y resolvimos los siguientes puntos:
1. **Generación automática de iconos:**
   - Apple rechazó la subida inicial porque faltaban los iconos reglamentarios para iPhone y iPad.
   - Procesamos tu logo oficial (`logo_senderismo.png`) y generamos la suite completa (1024x1024, 180x180, 152x152, 120x120, etc.) en `AppIcon.appiconset` en formato RGB opaco sin canal alfa.
2. **Correcciones en `Info.plist`:**
   - Agregamos la clave obligatoria `CFBundleIconName` asociada a `AppIcon`.
   - Agregamos `ITSAppUsesNonExemptEncryption` con valor `false` para que TestFlight no bloquee cada versión pidiendo cuestionarios de criptografía.
3. **Validación de compilación:**
   - La compilación en modo Release y el archivo (`.xcarchive`) finalizaron con éxito (`ARCHIVE SUCCEEDED`).
   - Se creó el archivo de opciones de exportación `ExportOptions.plist`.

### El bloqueo de Apple en la subida directa:
Al intentar subir el binario a App Store Connect:
```text
SDK version issue. This app was built with the iOS 18.2 SDK. 
All iOS and iPadOS apps must be built with the iOS 26 SDK or later, 
included in Xcode 26 or later, in order to be uploaded to App Store Connect or submitted for distribution.
```
- **Causa:** Apple exige que toda aplicación subida a TestFlight esté construida con la versión más reciente de Xcode. Xcode 26 requiere arquitectura Apple Silicon y no puede instalarse de forma nativa en tu Mac Intel.
- **Aclaración de términos:** Aclaramos que tu uso de la palabra *"ad-hoc"* se refería a una versión de Xcode adaptada/a la medida para tu Mac Intel, y no al método de instalación directa.
- **Decisión acordada:** **Solución 1: Compilación y despliegue a TestFlight mediante GitHub Actions en la nube.**

---

## 2. La Solución: ¿Cómo funciona GitHub Actions?

No necesitas comprar otra Mac. El flujo de trabajo funciona así:
1. Trabajas y programas normalmente en tu Mac Intel con Xcode 16.2.
2. Guardas tus cambios y haces un `git push` a tu repositorio en GitHub.
3. Un **servidor gratuito en la nube de GitHub** (con procesador Apple Silicon y la versión más reciente de Xcode que Apple exige) toma tu código, lo compila, lo firma y lo sube directamente a TestFlight.
4. En cuestión de minutos, tus testers reciben la notificación en la app TestFlight de sus iPhones para probar la última versión.

---

## 3. Hoja de Ruta para cuando retomemos

Cuando quieras continuar, seguiremos estos sencillos pasos:

### Paso 1: Generar una App Store Connect API Key (5 minutos)
Para que GitHub tenga permiso de subir compilaciones a tu cuenta sin pedirte contraseña cada vez:
1. Entra a [appstoreconnect.apple.com/access/api](https://appstoreconnect.apple.com/access/api).
2. Haz clic en **Integraciones** > **Claves de API de App Store Connect** > botón **`+`**.
3. Nombre: `GitHub Actions TestFlight`.
4. Acceso: `App Manager` o `Developer`.
5. Haz clic en **Generar**.
6. Guarda estos 3 datos:
   - **Issuer ID** (Aparece arriba en texto pequeño, es un UUID).
   - **Key ID** (El identificador alfanumérico de la clave).
   - **Descargar clave privada (`.p8`)**: Apple solo te deja descargarla **una sola vez**. Guárdala en un lugar seguro.

### Paso 2: Crear el certificado y perfil para CI/CD
Exportaremos tu certificado de distribución con su clave privada en formato `.p12` para que GitHub Actions pueda firmar la app en la nube. (Te guiaré paso a paso con los comandos exactos).

### Paso 3: Configurar los Secretos en GitHub
En tu repositorio de GitHub:
- Iremos a **Settings > Secrets and variables > Actions > New repository secret**.
- Añadiremos:
  - `APP_STORE_KEY_ID`
  - `APP_STORE_ISSUER_ID`
  - `APP_STORE_PRIVATE_KEY` (el contenido del archivo `.p8`)
  - `BUILD_CERTIFICATE_BASE64`
  - `P12_PASSWORD`
  - `PROVISIONING_PROFILE_BASE64`

### Paso 4: Crear el Workflow de Automatización
Crearemos en el proyecto el archivo `.github/workflows/testflight.yml`:
- Cada vez que hagas `git tag` (ej. `v1.0.1`) o pulses el botón "Run workflow", compilará automáticamente con el Xcode más reciente y lo enviará a TestFlight.

---

## 4. Archivos Clave del Proyecto Modificados en esta Sesión
- `ios/RutasSendero/RutasSendero/Info.plist`: Configuración de icono y bypass de cuestionario de criptografía.
- `ios/RutasSendero/RutasSendero/Assets.xcassets/AppIcon.appiconset/`: Catálogo de iconos completo generado para iPhone/iPad/App Store.
- `ios/RutasSendero/ExportOptions.plist`: Parámetros de exportación para App Store Connect.

---
*Cuando estés listo para continuar, solo dime: **"Continuemos con la configuración de GitHub Actions"** y empezaremos desde el Paso 1.*
