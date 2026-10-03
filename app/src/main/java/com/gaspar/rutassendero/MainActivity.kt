package com.gaspar.rutassendero

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import android.os.Environment
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gaspar.rutassendero.service.GpsTrackingService
import com.gaspar.rutassendero.ui.RutasViewModel
import com.gaspar.rutassendero.ui.screens.MainScreen
import java.util.Locale
import java.io.File
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
/**
 * EXPLICACIÓN DE MAINACTIVITY:
 *
 * La Activity (actividad) representa la ventana visual donde se dibuja la interfaz.
 * En Kotlin moderno, en lugar de inflar layouts XML heredados, heredamos de `ComponentActivity`
 * y usamos `setContent { ... }` para montar nuestra interfaz declarativa de Jetpack Compose.
 *
 * Esta clase también actúa como el "Controlador Principal" de los eventos del sistema:
 * 1. Petición de permisos en tiempo de ejecución (GPS y Notificaciones en Android 13+).
 * 2. Arranque y parada del servicio GPS (`GpsTrackingService`).
 * 3. Ejecución de la ventana nativa de dictado por voz (Speech to Text) para registrar baches por voz.
 * 4. Control de minimizado (`moveTaskToBack`) y lanzamiento de navegación en Google Maps/Waze.
 */
class MainActivity : FragmentActivity() {

    // Inicializar el ViewModel usando el delegado de Kotlin 'viewModels()'
    private val viewModel: RutasViewModel by viewModels()

    private val cameraHelper = com.gaspar.rutassendero.utils.CameraHelper(this) { success, file ->
        try {
            if (success && file != null) {
                viewModel.onPhotoCaptured(file.absolutePath)
            } else {
                viewModel.onPhotoCaptureCancelled()
            }
        } catch (e: Exception) {
            logErrorToFile(e, "cameraHelper_result")
            Toast.makeText(this, "Error al procesar la foto: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val voiceHelper = com.gaspar.rutassendero.utils.VoiceHelper(this) { text ->
        if (!text.isNullOrEmpty()) {
            viewModel.onTextDictated(text)
        } else {
            Toast.makeText(this, "La descripción dictada está vacía.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        cameraHelper.photoFile?.absolutePath?.let {
            outState.putString("PHOTO_FILE_PATH", it)
        }
        outState.putBoolean("IS_CAPTURING_PHOTO", viewModel.isCapturingPhotoForPoint)
        outState.putBoolean("IS_FIN_RUTA", viewModel.isFinRutaPending)
        viewModel.locationSnapshotForPoint?.let { loc ->
            outState.putDouble("LOC_LAT", loc.latitude)
            outState.putDouble("LOC_LNG", loc.longitude)
            if (loc.hasAltitude()) {
                outState.putDouble("LOC_ALT", loc.altitude)
            }
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedInstanceState.getString("PHOTO_FILE_PATH")?.let {
            cameraHelper.photoFile = File(it)
        }
        viewModel.isCapturingPhotoForPoint = savedInstanceState.getBoolean("IS_CAPTURING_PHOTO", false)
        viewModel.isFinRutaPending = savedInstanceState.getBoolean("IS_FIN_RUTA", false)
        if (savedInstanceState.containsKey("LOC_LAT")) {
            val loc = android.location.Location("restored").apply {
                latitude = savedInstanceState.getDouble("LOC_LAT")
                longitude = savedInstanceState.getDouble("LOC_LNG")
                if (savedInstanceState.containsKey("LOC_ALT")) {
                    altitude = savedInstanceState.getDouble("LOC_ALT")
                }
            }
            viewModel.locationSnapshotForPoint = loc
        }
    }

    private fun logErrorToFile(e: Exception, contextMsg: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null) {
                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val fileName = "problemafotos_${timeStamp}.txt"
                    val file = File(downloadsDir, fileName)
                    
                    val stackTrace = e.stackTraceToString()
                    val logMessage = "Error in $contextMsg\nTime: $timeStamp\nMessage: ${e.message}\n\nStackTrace:\n$stackTrace"
                    
                    file.writeText(logMessage)
                    Log.e("MainActivity", "Error logged to ${file.absolutePath}")
                }
            } catch (logEx: Exception) {
                Log.e("MainActivity", "Failed to write error log to Downloads", logEx)
            }
        }
    }



    // Lanzador para solicitar permisos múltiples en tiempo de ejecución (Moderno)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val notificationGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: true // true por defecto si es < Android 13

        if (fineGranted) {
            Toast.makeText(this, "Permiso de ubicación concedido.", Toast.LENGTH_SHORT).show()
            checkBackgroundLocationAndBattery()
            startGpsService()
        } else {
            Toast.makeText(this, "Se requiere permiso de ubicación para el radar.", Toast.LENGTH_LONG).show()
        }
    }

    private val requestBackgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d(TAG, "Permiso de ubicación en segundo plano concedido.")
        } else {
            Log.w(TAG, "Permiso de ubicación en segundo plano no concedido.")
        }
        requestBatteryOptimizationExemption()
    }

    private var onBluetoothPermissionResult: ((Boolean) -> Unit)? = null

    private val requestBluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val advertiseGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_ADVERTISE] == true &&
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true &&
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        } else {
            true
        }
        onBluetoothPermissionResult?.invoke(advertiseGranted)
    }

    private fun requestBluetoothPermission(onResult: (Boolean) -> Unit) {
        onBluetoothPermissionResult = onResult
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
            onResult(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity: onCreate")

        val appInstance = application as BachesApplication
        val prefs = appInstance.preferencesHelper

        // 1. Obtener la versión de la app actual
        val currentVersionCode = try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }

        // Incrementar el contador de inicios
        prefs.launchCount += 1

        // Detectar si es una versión diferente/actualizada
        val isDifferentVersion = prefs.lastRunVersionCode != currentVersionCode
        if (isDifferentVersion) {
            prefs.installationCount += 1
            prefs.lastRunVersionCode = currentVersionCode

            // Si ya tiene una fecha de caducidad previa (por ejemplo, restaurada por un backup),
            // la actualizamos/restablecemos a la fecha actual para esta nueva versión.
            if (prefs.firstLaunchTime != 0L) {
                prefs.firstLaunchTime = System.currentTimeMillis()
            }
            // Restablecer la advertencia de prueba para que se muestre en esta versión
            prefs.trialWarningShown = false
        }

        // Inicializar primer inicio si aún no está guardado
        if (prefs.firstLaunchTime == 0L) {
            prefs.firstLaunchTime = System.currentTimeMillis()
        }

        // 2. Verificar expiración (por ejecuciones o por días)
        val isExpired = if (prefs.useLaunchLimit) {
            prefs.launchCount > prefs.maxLaunchLimit
        } else {
            val limitMs = prefs.trialDaysLimit * 24 * 60 * 60 * 1000L
            val elapsedMs = System.currentTimeMillis() - prefs.firstLaunchTime
            elapsedMs > limitMs
        }

        // Comprobar y solicitar permisos se hará después de autenticarse

        // 3. Cargar la UI
        setContent {
            val senderista by viewModel.senderistaState.collectAsStateWithLifecycle(initialValue = null)
            val isBanned = senderista?.bloqueado == true

            // Validar que el senderista sea mayor de 18 años
            val isUnderage = remember(senderista?.fechaNacimiento) {
                val dobStr = senderista?.fechaNacimiento
                if (dobStr.isNullOrEmpty()) {
                    false
                } else {
                    try {
                        val parts = dobStr.split("/")
                        if (parts.size == 3) {
                            val day = parts[0].toInt()
                            val month = parts[1].toInt() - 1
                            val year = parts[2].toInt()
                            
                            val dob = java.util.Calendar.getInstance().apply {
                                set(year, month, day)
                            }
                            val today = java.util.Calendar.getInstance()
                            var age = today.get(java.util.Calendar.YEAR) - dob.get(java.util.Calendar.YEAR)
                            if (today.get(java.util.Calendar.DAY_OF_YEAR) < dob.get(java.util.Calendar.DAY_OF_YEAR)) {
                                age--
                            }
                            // Si la edad no es mayor de 18 (es decir <= 18), no puede usarla
                            age <= 18
                        } else false
                    } catch (e: Exception) {
                        false
                    }
                }
            }

            var isAuthenticated by rememberSaveable { mutableStateOf(false) }
            var isAuthenticating by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(isBanned) {
                if (isBanned) {
                    wipeLocalData()
                }
            }

            LaunchedEffect(isAuthenticated, isBanned) {
                if (!isBanned && !isAuthenticated && !isAuthenticating) {
                    isAuthenticating = true
                    
                    val activeRouteId = prefs.activeRouteId
                    var shouldSkipAuth = false
                    
                    if (activeRouteId != null) {
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            try {
                                val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this@MainActivity)
                                val locationTask = fusedLocationClient.lastLocation
                                val location = locationTask.await()
                                if (location != null) {
                                    val isNear = viewModel.isLocationNearRoute(activeRouteId, location.latitude, location.longitude, 100.0)
                                    if (isNear) {
                                        shouldSkipAuth = true
                                        isAuthenticated = true
                                        isAuthenticating = false
                                        viewModel.resumeActiveRoute(activeRouteId, prefs.activeRouteForward)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error getting location for auth skip", e)
                            }
                        }
                    }

                    if (!shouldSkipAuth) {
                        val biometricManager = BiometricManager.from(this@MainActivity)
                        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
                            showBiometricPrompt(
                                onSuccess = { 
                                    isAuthenticated = true
                                    isAuthenticating = false 
                                },
                                onError = { 
                                    isAuthenticating = false
                                    finish() 
                                }
                            )
                        } else {
                            // Permite entrar si no tiene bloqueo configurado
                            isAuthenticated = true
                            isAuthenticating = false
                        }
                    }
                }
            }

            LaunchedEffect(isAuthenticated, isExpired, isBanned, isUnderage) {
                if (isAuthenticated && !isExpired && !isBanned && !isUnderage) {
                    checkAndRequestPermissions()
                }
            }

            val networkError by viewModel.networkErrorMessage.collectAsStateWithLifecycle()

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFFFAF9F6)
            ) {
                networkError?.let { errorMessage ->
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissNetworkError() },
                        title = { Text("Se Requiere Conexión", fontWeight = FontWeight.Bold) },
                        text = { Text(errorMessage) },
                        confirmButton = {
                            Button(onClick = { viewModel.dismissNetworkError() }) {
                                Text("Entendido")
                            }
                        }
                    )
                }

                if (isBanned) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Red), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("ACCESO DENEGADO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            Text("Esta cuenta ha sido bloqueada por uso indebido y se han borrado los datos locales por seguridad.", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                        }
                    }
                } else if (isUnderage) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Red), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("ACCESO DENEGADO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            Text("Debe ser mayor de 18 años para utilizar esta aplicación.", color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { stopGpsService() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Red)
                            ) {
                                Text("Cerrar Aplicación", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (!isAuthenticated) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFD97706))
                    }
                } else if (isExpired) {
                    TrialExpiredScreen(
                        useLaunchLimit = prefs.useLaunchLimit,
                        daysLimit = prefs.trialDaysLimit,
                        maxLaunches = prefs.maxLaunchLimit,
                        onExitApp = { stopGpsService() }
                    )
                } else {
                    var showTrialDialog by remember { mutableStateOf(!prefs.trialWarningShown) }

                    if (showTrialDialog) {
                        TrialWarningDialog(
                            daysLimit = prefs.trialDaysLimit,
                            useLaunchLimit = prefs.useLaunchLimit,
                            maxLaunches = prefs.maxLaunchLimit,
                            onDismiss = {
                                prefs.trialWarningShown = true
                                showTrialDialog = false
                            }
                        )
                    }

                    MainScreen(
                        viewModel = viewModel,
                        onLaunchVoiceRecognition = { voiceHelper.launch() },
                        onOpenExternalMap = { lat, lng, mapType -> openMapApp(lat, lng, mapType) },
                        onOpenCamera = { file -> cameraHelper.launch(file) },
                        onMinimizeApp = { minimizeApp() },
                        onExitApp = { stopGpsService() },
                        onRequestBluetoothPermission = { onResult ->
                            requestBluetoothPermission(onResult)
                        }
                    )
                }
            }
        }
    }

    // --- AUTENTICACIÓN Y BLOQUEO ---

    private fun showBiometricPrompt(onSuccess: () -> Unit, onError: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Autenticación requerida: $errString", Toast.LENGTH_SHORT).show()
                    onError()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Android maneja los fallos menores mostrando un mensaje en el prompt
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación Requerida")
            .setSubtitle("Verifique que es el dueño del teléfono para continuar")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun wipeLocalData() {
        try {
            val appInstance = application as BachesApplication
            appInstance.preferencesHelper.clearAll()
            applicationContext.deleteDatabase("rutas_database")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- ACCIONES Y CONTROLES DEL RADAR ---

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )

        // Android 13 (API 33) requiere permiso para mostrar notificaciones
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        }


        val missingPermissions = permissionsNeeded.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            // Si ya tenemos permisos básicos, asegurar segundo plano, batería y arranque del GPS
            checkBackgroundLocationAndBattery()
            startGpsService()
        }
    }

    private fun checkBackgroundLocationAndBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBackground = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasBackground) {
                requestBackgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                requestBatteryOptimizationExemption()
            }
        } else {
            requestBatteryOptimizationExemption()
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    Log.d(TAG, "Diálogo de exclusión de optimización de batería solicitado.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo solicitar la exclusión de optimización de batería: ${e.message}")
            }
        }
    }

    /**
     * Arranca el servicio en segundo plano (Foreground Service).
     */
    private fun startGpsService() {
        val serviceIntent = Intent(this, GpsTrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    /**
     * Detiene el servicio en segundo plano y cierra la aplicación.
     */
    private fun stopGpsService() {
        try {
            if (viewModel.isBeaconEmitting.value) {
                viewModel.stopBeaconEmission()
            }
            if (viewModel.isBleScanning.value) {
                viewModel.stopBleTracking()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping beacon or scanner: ${e.message}", e)
        }
        val serviceIntent = Intent(this, GpsTrackingService::class.java)
        stopService(serviceIntent)
        finishAffinity() // Cierra completamente todas las ventanas de la app y sale
    }

    /**
     * Minimiza la ventana de la aplicación enviándola al fondo.
     * Es el equivalente nativo en Kotlin a moveTaskToBack(true) de B4A.
     */
    private fun minimizeApp() {
        moveTaskToBack(true)
        Toast.makeText(this, "Radar activo en segundo plano.", Toast.LENGTH_SHORT).show()
    }



    /**
     * Abre Google Maps o Waze y minimiza la app para guiar al conductor.
     */
    private fun openMapApp(lat: Double, lng: Double, mapType: String) {
        try {
            val uri = if (mapType == "waze") {
                Uri.parse("waze://?ll=$lat,$lng&navigate=yes")
            } else {
                Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
            }
            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
            
            // Forzar paquete si corresponde
            if (mapType == "waze") {
                mapIntent.setPackage("com.waze")
            } else {
                mapIntent.setPackage("com.google.android.apps.maps")
            }

            startActivity(mapIntent)
            minimizeApp() // Minimizar la app para que el mapa quede al frente
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir la aplicación de mapas seleccionada.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        try {
            if (viewModel.isBeaconEmitting.value) {
                viewModel.stopBeaconEmission()
            }
            if (viewModel.isBleScanning.value) {
                viewModel.stopBleTracking()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy cleanup: ${e.message}", e)
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Baches_MainActivity"
    }
}

@Composable
fun TrialExpiredScreen(
    useLaunchLimit: Boolean,
    daysLimit: Int,
    maxLaunches: Int,
    onExitApp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)), // Slate-900 / ColorTekinsDarkBg
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)), // Slate-800 / ColorTekinsCardBg
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icono elegante con candado de alerta
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Licencia Expirada",
                    tint = Color(0xFFF43F5E), // Rose-500 / ColorTekinsAlert
                    modifier = Modifier
                        .size(80.dp)
                        .padding(bottom = 16.dp)
                )

                Text(
                    text = "Período de Prueba Finalizado",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val expiredMessage = if (useLaunchLimit) {
                    "Esta aplicación es una versión de evaluación con un límite de $maxLaunches inicios/ejecuciones. El período de prueba ha expirado.\n\nPor favor, póngase en contacto con el administrador para adquirir una licencia o renovar el acceso."
                } else {
                    val durationText = if (daysLimit % 30 == 0) "$daysLimit días (${daysLimit / 30} meses)" else "$daysLimit días"
                    "Esta aplicación es una versión de evaluación con un límite de $durationText. El período de prueba ha expirado.\n\nPor favor, póngase en contacto con el administrador para adquirir una licencia o renovar el acceso."
                }

                Text(
                    text = expiredMessage,
                    color = Color(0xFF94A3B8), // Slate-400 / ColorTextoSecundario
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Button(
                    onClick = onExitApp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF43F5E), // ColorTekinsAlert
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Cerrar Aplicación",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TrialWarningDialog(
    daysLimit: Int,
    useLaunchLimit: Boolean,
    maxLaunches: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {}, // Impedir cerrar haciendo click afuera
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F766E), // Teal-700 / ColorTekinsPrimary
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Entendido",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Versión de Prueba",
                    tint = Color(0xFF2DD4BF), // Menta brillante / ColorTekinsAccent
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Versión de Prueba",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            val durationText = if (daysLimit % 30 == 0) "$daysLimit días (${daysLimit / 30} meses)" else "$daysLimit días"
            val warningMessage = if (useLaunchLimit) {
                "Esta es una versión de evaluación de la aplicación. Cuenta con un límite de $maxLaunches inicios/ejecuciones.\n\nUna vez cumplido el plazo, se requerirá una licencia para continuar utilizándola."
            } else {
                "Esta es una versión de evaluación de la aplicación. Cuenta con un límite de uso de $durationText a partir de este momento.\n\nUna vez cumplido el plazo, se requerirá una licencia para continuar utilizándola."
            }
            Text(
                text = warningMessage,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        },
        containerColor = Color(0xFF1E293B), // Slate-800 / ColorTekinsCardBg
        titleContentColor = Color.White,
        textContentColor = Color(0xFF94A3B8), // Slate-400
        shape = RoundedCornerShape(20.dp)
    )
}

