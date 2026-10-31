# Manual de Distribución y Gestión de Testers en TestFlight
**Proyecto:** RutasSendero (iOS)  
**App ID de Apple:** `6829071855`  
**Bundle ID:** `com.gaspar.rutassendero.RutasSendero`  

---

## 1. ¿Cómo funciona tu flujo de trabajo (CI/CD)?

Tu sistema de desarrollo ahora es completamente profesional y automático:

```
[Tu Mac Intel con Sonoma]
      │
      │  (Programas en Xcode y haces: git push origin master)
      ▼
[GitHub Actions en la Nube (Apple Silicon - macOS 26 / Xcode 26)]
      │
      │  1. Desbloquea tu certificado "Apple Distribution"
      │  2. Descarga tu perfil oficial de App Store
      │  3. Compila y empaqueta en modo Release (.ipa)
      │  4. Sube el binario a TestFlight vía App Store Connect API
      ▼
[App Store Connect / TestFlight]
      │
      ▼
[iPhones de tus Testers (Notificación automática de actualización)]
```

> **A futuro:** Cada vez que hagas cambios en tu código y hagas `git push origin master`, la nube compilará y subirá una nueva versión a TestFlight sin que tengas que configurar nada más.

---

## 2. Cómo gestionar e invitar a tus Testers

Entra a la consola oficial de tu aplicación:
👉 **[App Store Connect - TestFlight RutasSendero](https://appstoreconnect.apple.com/apps/6829071855/testflight/ios)**

Apple ofrece dos modalidades para compartir la app con usuarios:

### Opción A: Pruebas Internas (Tú y tu equipo cercano)
- **Capacidad:** Hasta 100 personas.
- **Revisión de Apple:** **Ninguna**. En cuanto Apple termina de procesar el archivo (5 a 15 minutos), la versión queda disponible de inmediato.
- **Pasos para activar:**
  1. En el menú lateral izquierdo de TestFlight, haz clic en **Pruebas internas** > botón **`+`** (Crear grupo).
  2. Ponle un nombre (ej. `Equipo Interno`).
  3. Haz clic en **Testers** > botón **`+`** y añade los correos de tus usuarios (deben ser usuarios vinculados a un Apple ID).
  4. En la pestaña **Compilaciones**, asocia la versión que acabamos de subir.

### Opción B: Pruebas Externas (Senderistas y público en general con Enlace Público)
- **Capacidad:** Hasta 10,000 personas.
- **Revisión de Apple:** Requiere una revisión inicial rápida de Apple (suele tardar unas horas o un día hábil).
- **Pasos para activar:**
  1. En el menú lateral izquierdo, haz clic en **Pruebas externas** > botón **`+`** (Nuevo grupo).
  2. Ponle nombre al grupo (ej. `Senderistas Beta`).
  3. En la sección **Compilaciones**, haz clic en el botón **`+`** y selecciona la compilación.
  4. En la sección **Testers**, haz clic en **Habilitar enlace público** (Enable Public Link).
  5. Apple te generará un enlace único con este formato:  
     `https://testflight.apple.com/join/XXXXXXXX`
  6. **¡Listo!** Ese enlace es el que puedes compartir por WhatsApp, grupos de Facebook o correo.

---

## 3. Mensaje listo para enviar a tus Testers (WhatsApp / Correo)

Copia y pega este mensaje para enviárselo a las personas que van a probar tu app:

---
> 🌲 **¡Prueba la nueva app de Rutas Sendero para iPhone!** 🎒📱  
> 
> Te invitamos a probar de forma anticipada la aplicación oficial de **Rutas Sendero**. 
> La instalación se realiza de forma 100% segura mediante **TestFlight** (la plataforma oficial de pruebas de Apple).
> 
> **Pasos para instalarla en tu iPhone:**
> 
> 1. Si aún no tienes la app gratuita **TestFlight** en tu iPhone, descárgala desde la App Store oficial:  
>    👉 https://apps.apple.com/app/testflight/id899247664
> 
> 2. Toca este enlace de invitación en tu iPhone:  
>    👉 **[PEGA_AQUÍ_TU_ENLACE_DE_TESTFLIGHT]**
> 
> 3. Se abrirá TestFlight mostrándote **Rutas Sendero**. Pulsa en **"Aceptar"** y luego en **"Instalar"**.
> 
> *(La app se instalará en la pantalla de inicio de tu iPhone como cualquier otra app. Si detectas cualquier detalle en ruta, solo toma una captura de pantalla y el iPhone te dará la opción de enviarnos tus comentarios).*
---

---

## 4. ¿Qué experimenta el Tester al abrir la app?

1. En la pantalla de su iPhone verá el icono oficial de **Rutas Sendero** con un pequeño punto naranja junto al nombre (distintivo de Apple que indica que es una app beta).
2. Podrá utilizar el mapa, registrar tracks GPS, guardar fotos y consultar puntos de interés con normalidad.
3. Si la app se cierra inesperadamente o el tester toma una captura de pantalla y presiona "Compartir comentarios", el reporte técnico detallado te llegará a ti en tu panel de App Store Connect.

---

## 5. Resumen de comandos útiles para el futuro

Cuando hagas mejoras en el código Swift desde tu Mac:

```bash
# 1. Ver qué archivos modificaste
git status

# 2. Guardar tus cambios
git add .
git commit -m "Descripción de lo que mejoraste"

# 3. Mandar a compilar y publicar a TestFlight
git push origin master
```

Y podrás monitorear la compilación automática en vivo en:  
👉 **https://github.com/jgasparmxx/rutas/actions**
