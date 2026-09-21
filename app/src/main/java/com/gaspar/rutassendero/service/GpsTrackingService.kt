package com.gaspar.rutassendero.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.gaspar.rutassendero.bluetooth.BeaconEmitterManager
import com.gaspar.rutassendero.bluetooth.BeaconScannerManager
import com.gaspar.rutassendero.bluetooth.BleSupportStatus
import com.gaspar.rutassendero.data.ParticipanteEntity
import com.gaspar.rutassendero.data.SenderistaEntity
import kotlinx.coroutines.withContext
import com.gaspar.rutassendero.BachesApplication
import com.gaspar.rutassendero.MainActivity
import com.gaspar.rutassendero.data.RutaEntity
import com.gaspar.rutassendero.data.PuntoRutaEntity
import com.gaspar.rutassendero.data.TrazaRutaEntity
import com.gaspar.rutassendero.data.PreferencesHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale


class GpsTrackingService : Service(), TextToSpeech.OnInitListener, android.hardware.SensorEventListener {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var prefs: PreferencesHelper
    private lateinit var db: com.gaspar.rutassendero.data.RutasDatabase

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    // --- Sensores (Step Counter) ---
    private lateinit var sensorManager: android.hardware.SensorManager
    private var stepSensor: android.hardware.Sensor? = null
    private var lastStepTime = 0L
    private var stepsSincePause = 0f
    private var initialStepCount = -1f



    // --- Motor de Voz (TTS) ---
    private var ttsEngine: TextToSpeech? = null
    private var isTtsReady = false

    // --- Generador de Tonos (Beeps) ---
    private var toneGenerator: ToneGenerator? = null
    private var beepJob: Job? = null
    private var emergencyJob: Job? = null
    @Volatile
    private var currentBeepIntervalMs: Long = -1 // -1 significa sin pitido Doppler

    private var lastTtsTime = 0L

    // Variables de seguimiento para la guía inteligente
    private var lastEvaluatedTargetId: Int? = null
    private var minDistanceToTarget = Float.MAX_VALUE
    private var lastWarningTime = 0L
    private var lastProximityAnnouncementTime = 0L
    private var lastProximityAnnouncementDistance = Float.MAX_VALUE

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "GpsTrackingService: onCreate")

        val app = application as BachesApplication
        prefs = app.preferencesHelper
        db = app.database
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Inicializar Sensores
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        stepSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER)

        // Inicializar TTS
        ttsEngine = TextToSpeech(this, this)
        beaconManager = BeaconEmitterManager(this)
        beaconScanner = BeaconScannerManager(this)
        
        serviceScope.launch {
            beaconScanner.isScanning.collect { _isBleScanning.value = it }
        }

        // Inicializar ToneGenerator con el volumen al máximo para exteriores
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear ToneGenerator: ${e.message}")
        }

        // Bucle de pitidos para el efecto Doppler
        startBeepLoop()

        // Escuchar cambios reactivos en la ruta seleccionada y actualizar puntos
        serviceScope.launch {
            selectedRoute.collectLatest { ruta ->
                if (ruta != null) {
                    db.rutasDao().getPuntosForRutaFlow(ruta.id).collect { puntos ->
                        _selectedRoutePoints.value = puntos
                        // Si está guiando y no hay un destino fijado, asignar por defecto (el más cercano)
                        if (_isGuiding.value && _targetWaypoint.value == null && puntos.isNotEmpty()) {
                            val userLoc = _currentLocation.value
                            _targetWaypoint.value = if (userLoc != null) {
                                puntos.minByOrNull { pt ->
                                    val ptLoc = Location("").apply {
                                        latitude = pt.latitud
                                        longitude = pt.longitud
                                    }
                                    userLoc.distanceTo(ptLoc)
                                }
                            } else {
                                if (_guideDirectionForward.value) puntos.first() else puntos.last()
                            }
                        }
                    }
                } else {
                    _selectedRoutePoints.value = emptyList()
                    _targetWaypoint.value = null
                    _isGuiding.value = false
                }
            }
        }

        // Escuchar cambios reactivos en la ruta seleccionada y actualizar traza
        serviceScope.launch {
            selectedRoute.collectLatest { ruta ->
                if (ruta != null) {
                    db.rutasDao().getTrazaForRutaFlow(ruta.id).collect { traza ->
                        _selectedRouteTrace.value = traza
                    }
                } else {
                    _selectedRouteTrace.value = emptyList()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "GpsTrackingService: onStartCommand")

        intent?.action?.let { action ->
            when (action) {
                ACTION_STOP_SERVICE -> {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }

        startForeground(NOTIFICATION_SERVICE_ID, createForegroundNotification("Esperando ruta..."))
        startLocationUpdates()
        _gpsRunningState.value = true
        _recordingState.value = RecordingState.RECORDING

        stepSensor?.let {
            sensorManager.registerListener(this, it, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "GpsTrackingService: onDestroy")
        instance = null

        stopLocationUpdates()
        sensorManager.unregisterListener(this)
        _recordingState.value = RecordingState.STOPPED
        beepJob?.cancel()
        toneGenerator?.release()
        toneGenerator = null

        ttsEngine?.stop()
        ttsEngine?.shutdown()
        ttsEngine = null
        isTtsReady = false

        _gpsRunningState.value = false
        _currentLocation.value = null
        _selectedRoute.value = null
        _selectedRoutePoints.value = emptyList()
        _selectedRouteTrace.value = emptyList()
        _targetWaypoint.value = null
        _isGuiding.value = false
        _distanceToTarget.value = null
        _relativeBearing.value = null
        _relativeDirection.value = null

        Toast.makeText(this, "Servicio GPS Apagado", Toast.LENGTH_SHORT).show()
    }

    // --- MANEJO DEL GPS ---

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            if (location == null) {
                Log.d(TAG, "locationCallback: onLocationResult - lastLocation es nulo")
                return
            }
            
            Log.d(TAG, "Ubicación GPS recibida: lat=${location.latitude}, lng=${location.longitude}, precisión=${location.accuracy}m")
            
            if (location.hasAccuracy() && location.accuracy > 60f) {
                Log.w(TAG, "Ubicación ignorada por baja precisión: ${location.accuracy}m > 60m")
                return
            }

            _currentLocation.value = location

            // Máquina de estados: Detección de vehículo y reanudación
            evaluateVehicleState(location)

            // Evaluar guía acústica hacia el siguiente waypoint
            evaluateHikingGuidance(location)

            // Actualizar notificación del sistema
            updateForegroundNotificationDynamic(location)

            // Registrar punto de traza continua solo si estamos grabando
            if (_recordingState.value == RecordingState.RECORDING) {
                recordTracePointIfNeeded(location)
            }
        }
    }

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        if (event?.sensor?.type == android.hardware.Sensor.TYPE_STEP_COUNTER) {
            val currentSteps = event.values[0]
            if (initialStepCount == -1f) {
                initialStepCount = currentSteps
            }
            if (_recordingState.value == RecordingState.PAUSED_VEHICLE) {
                stepsSincePause += (currentSteps - initialStepCount)
            } else {
                stepsSincePause = 0f
            }
            initialStepCount = currentSteps
            lastStepTime = System.currentTimeMillis()
        }
    }

    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}

    private fun evaluateVehicleState(location: Location) {
        // Solo evaluar la velocidad si hay una ruta seleccionada y que esté en proceso (no finalizada)
        val ruta = _selectedRoute.value
        if (ruta == null || ruta.fechaHoraFin != null) {
            return
        }

        // Solo evaluar si ya se ha registrado al menos un punto (se inició la ruta verdaderamente)
        if (_selectedRoutePoints.value.isEmpty()) {
            return
        }

        val now = System.currentTimeMillis()
        val timeSinceLastStep = now - lastStepTime

        when (_recordingState.value) {
            RecordingState.RECORDING -> {
                // Si la velocidad supera 30 km/h (8.33 m/s) con precisión aceptable, y no hay pasos en 60s
                if (location.hasSpeed() && location.speed > 8.33f && location.accuracy < 15f && timeSinceLastStep > 60_000) {
                    _recordingState.value = RecordingState.PAUSED_VEHICLE
                    stepsSincePause = 0f
                    
                    speakTts("Pausamos la grabación: detectamos velocidad de transporte. Reanuda cuando estés a pie")
                    
                    // Insertar faro (PuntoRutaEntity) indicando pausa
                    serviceScope.launch {
                        val faro = PuntoRutaEntity(
                            idRuta = ruta.id,
                            latitud = location.latitude,
                            longitud = location.longitude,
                            altitud = location.altitude,
                            descripcion = "Pausado por vehículo",
                            fotoPath = "res://ic_aviso_vehiculo",
                            estado = 8 // Vehículo
                        )
                        db.rutasDao().insertPuntoRuta(faro)
                    }
                }
            }
            RecordingState.PAUSED_VEHICLE -> {
                // Si la velocidad baja de 8 km/h (2.22 m/s) y ha dado más de 50 pasos
                if (location.hasSpeed() && location.speed < 2.22f && stepsSincePause > 50) {
                    _recordingState.value = RecordingState.RECORDING
                    stepsSincePause = 0f
                    
                    speakTts("Reanudando grabación a pie")
                    
                    // Insertar faro indicando reanudación
                    serviceScope.launch {
                        val faro = PuntoRutaEntity(
                            idRuta = ruta.id,
                            latitud = location.latitude,
                            longitud = location.longitude,
                            altitud = location.altitude,
                            descripcion = "Reanudando a pie",
                            fotoPath = "res://ic_caminando",
                            estado = 1 // Normal
                        )
                        db.rutasDao().insertPuntoRuta(faro)
                    }
                }
            }
            else -> {}
        }
    }

    private fun recordTracePointIfNeeded(location: Location) {
        val route = _selectedRoute.value ?: return
        serviceScope.launch {
            try {
                // Si la ruta ya tiene registrado su punto final, no registrar más traza
                if (db.rutasDao().isRutaFinalized(route.id)) {
                    Log.d(TAG, "recordTracePointIfNeeded: La ruta ${route.id} ya está finalizada. No se registrará más traza.")
                    return@launch
                }

                val trazaList = db.rutasDao().getTrazaForRuta(route.id)
                val lastPt = trazaList.lastOrNull()
                
                val distance = if (lastPt != null) {
                    val lastLoc = Location("").apply {
                        latitude = lastPt.latitud
                        longitude = lastPt.longitud
                    }
                    location.distanceTo(lastLoc)
                } else {
                    Float.MAX_VALUE // Si es el primer punto, se inserta inmediatamente
                }
                
                // Mínimo 10-15 metros entre puntos (usaremos 12 metros)
                if (distance >= 12.0f) {
                    val nextOrder = (lastPt?.orden ?: 0) + 1
                    val newTracePoint = TrazaRutaEntity(
                        idRuta = route.id,
                        orden = nextOrder,
                        latitud = location.latitude,
                        longitud = location.longitude,
                        altitud = location.altitude
                    )
                    db.rutasDao().insertTrazaRuta(newTracePoint)
                    Log.d(TAG, "Trace point recorded: route=${route.id}, order=$nextOrder, lat=${location.latitude}, lng=${location.longitude}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording trace point: ${e.message}", e)
            }
        }
    }

    private fun startLocationUpdates() {
        Log.d(TAG, "startLocationUpdates: Solicitando actualizaciones de ubicación")
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setMinUpdateDistanceMeters(0f)
        }.build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d(TAG, "fusedLocationClient.requestLocationUpdates ejecutado exitosamente")
        } else {
            Log.w(TAG, "Error al iniciar localización: Permiso ACCESS_FINE_LOCATION denegado")
            Toast.makeText(this, "Error: Permiso de ubicación no concedido", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // --- EVALUACIÃ“N DE GUÍA Y DOPPLER ---

    private fun evaluateHikingGuidance(userLoc: Location) {
        if (!_isGuiding.value) {
            currentBeepIntervalMs = -1
            _distanceToTarget.value = null
            _relativeBearing.value = null
            _relativeDirection.value = null
            lastEvaluatedTargetId = null
            minDistanceToTarget = Float.MAX_VALUE
            lastProximityAnnouncementDistance = Float.MAX_VALUE
            return
        }

        val target = _targetWaypoint.value
        if (target == null) {
            currentBeepIntervalMs = -1
            _distanceToTarget.value = null
            _relativeBearing.value = null
            _relativeDirection.value = null
            lastEvaluatedTargetId = null
            minDistanceToTarget = Float.MAX_VALUE
            lastProximityAnnouncementDistance = Float.MAX_VALUE
            return
        }

        val puntos = _selectedRoutePoints.value
        val forward = _guideDirectionForward.value
        val currentIndex = puntos.indexOfFirst { it.id == target.id }
        
        if (currentIndex == -1) return

        val tolerance = prefs.arrivalTolerance.toFloat()
        val limitDistance = prefs.alertDistance.toFloat()
        // Radio de peligro para la alarma de "estás perdido", puede ser 100m o el doble de limitDistance
        val radioPeligroMetros = 100f.coerceAtLeast(limitDistance * 1.5f)
        val now = System.currentTimeMillis()

        // Si el waypoint de destino cambia, reiniciamos las variables de seguimiento
        if (lastEvaluatedTargetId != target.id) {
            lastEvaluatedTargetId = target.id
            minDistanceToTarget = Float.MAX_VALUE
            lastProximityAnnouncementDistance = Float.MAX_VALUE
            lastWarningTime = 0L
            lastProximityAnnouncementTime = 0L
        }

        // --- Comportamiento Normal de Seguimiento hacia el target actual ---
        
        val targetLoc = Location("").apply {
            latitude = target.latitud
            longitude = target.longitud
        }

        val distanceToCurrentTarget = userLoc.distanceTo(targetLoc)
        _distanceToTarget.value = distanceToCurrentTarget

        // Actualizar la distancia mínima alcanzada para este punto
        if (distanceToCurrentTarget < minDistanceToTarget) {
            minDistanceToTarget = distanceToCurrentTarget
        }
        
        val isMovingAway = distanceToCurrentTarget > minDistanceToTarget + 10f

        // 1. EVALUAR ATAJOS Y AVANCE (Windowed Proximity)
        var shortcutDetected = false
        var nextTargetIndex = currentIndex
        var minDistanceToAnyRemaining = Float.MAX_VALUE
        
        val sequenceToCheck = if (forward) {
            currentIndex until puntos.size
        } else {
            currentIndex downTo 0
        }
        
        var closestPointAheadIndex = -1
        var minDistanceToPointAhead = Float.MAX_VALUE
        
        for (i in sequenceToCheck) {
            val ptLoc = Location("").apply {
                latitude = puntos[i].latitud
                longitude = puntos[i].longitud
            }
            val dist = userLoc.distanceTo(ptLoc)
            
            if (dist < minDistanceToAnyRemaining) {
                minDistanceToAnyRemaining = dist
            }
            
            val isAhead = if (forward) i > currentIndex else i < currentIndex
            if (isAhead) {
                if (dist < minDistanceToPointAhead) {
                    minDistanceToPointAhead = dist
                    closestPointAheadIndex = i
                }
            }
            
            // Si nos acercamos a un punto de la ruta...
            if (dist <= tolerance) {
                if (isAhead) {
                    shortcutDetected = true
                    nextTargetIndex = i
                    break
                }
            }
        }

        // Si detectamos que brincó a un punto futuro (atajo)
        if (shortcutDetected) {
             val missingPointsCount = Math.abs(nextTargetIndex - currentIndex)
             speakTts("Vas bien hacia el destino, pero no pasaste por $missingPointsCount puntos.")
             
             val reachedPoint = puntos[nextTargetIndex]
             currentBeepIntervalMs = -1
             serviceScope.launch(Dispatchers.Main) {
                 handleWaypointReached(reachedPoint)
             }
             return
        } else if (isMovingAway && closestPointAheadIndex != -1 && minDistanceToPointAhead < distanceToCurrentTarget - 20f) {
             // Adaptive tracking: User skipped the current point and is heading to a point ahead
             val missingPointsCount = Math.abs(closestPointAheadIndex - currentIndex)
             val nextPoint = puntos[closestPointAheadIndex]
             speakTts("Parece que tomaste un atajo o desvío. Redirigiendo al punto: ${nextPoint.descripcion}.")
             _targetWaypoint.value = nextPoint
             return // Re-evaluate on next location update
        }

        val distance = distanceToCurrentTarget

        // --- AVISOS DE PUNTOS ESPECIALES (50m y 20m) ---
        if (target.estado != 1) {
            val thresholds = listOf(50f, 20f)
            for (t in thresholds) {
                if (distance <= t) {
                    val alertKey = "${target.id}_${t.toInt()}"
                    if (!announcedAlertsThresholds.contains(alertKey)) {
                        announcedAlertsThresholds.add(alertKey)
                        val stateName = when(target.estado) {
                            2 -> "sitio de interés"
                            3 -> "bifurcación"
                            4 -> "suelo flojo"
                            5 -> "cuidado"
                            6 -> "peligro"
                            8 -> "vehículo"
                            9 -> "refugio"
                            10 -> "comida"
                            else -> "punto especial"
                        }
                        val descText = if (target.descripcion.isNotBlank()) ". A continuación: ${target.descripcion}" else ""
                        if (t == 50f) {
                            speakTts("Atención a 50 metros, $stateName$descText", force = true)
                        } else {
                            speakTts("Alerta, estás llegando a $stateName$descText", force = true)
                        }
                        
                        if (target.estado == 6) {
                            playSoundPattern(listOf(100 to 50, 400 to 100, 100 to 50, 400 to 100))
                        } else if (target.estado in listOf(4, 5)) {
                            playSoundPattern(listOf(200 to 100, 200 to 100))
                        } else {
                            playSoundPattern(listOf(150 to 100))
                        }
                        break
                    }
                }
            }
        }

        // 2. ALARMA DE DESVÍO GLOBAL (Estás perdido)
        // En lugar de alertar si se aleja del punto actual (que daba falsas alarmas en tramos largos),
        // calculamos un margen dinámico basado en la distancia entre el punto anterior y el actual.
        val prevIndex = if (forward) currentIndex - 1 else currentIndex + 1
        val distPrevToNext = if (prevIndex in puntos.indices) {
            val prevPoint = puntos[prevIndex]
            val prevLoc = Location("").apply { latitude = prevPoint.latitud; longitude = prevPoint.longitud }
            prevLoc.distanceTo(targetLoc)
        } else {
            0f
        }
        val maxAllowedDistance = radioPeligroMetros.coerceAtLeast(distPrevToNext + 50f)

        if (minDistanceToAnyRemaining > maxAllowedDistance) {
            if (now - lastWarningTime > 30000) { // Limitar a una vez cada 30 segundos
                lastWarningTime = now
                speakTts("Alarma. Te estás alejando peligrosamente de la ruta. Verifica el mapa.")
                // Sonido de alerta de desvío (corto-largo)
                playSoundPattern(listOf(100 to 50, 400 to 100))
            }
        }

        // 3. Anuncio de dirección relativo y vibración
        val targetBearing = userLoc.bearingTo(targetLoc)
        if (userLoc.hasBearing() && userLoc.speed > 0.5f) {
            val userBearing = userLoc.bearing
            var relative = targetBearing - userBearing
            while (relative < -180) relative += 360
            while (relative > 180) relative -= 360
            _relativeBearing.value = relative

            val dirText = when {
                relative in -22.5..22.5 -> "adelante"
                relative in 22.5..67.5 -> "gira a la derecha"
                relative in 67.5..112.5 -> "gira a la derecha"
                relative in 112.5..157.5 -> "da la vuelta a la derecha"
                relative in -67.5..-22.5 -> "gira a la izquierda"
                relative in -112.5..-67.5 -> "gira a la izquierda"
                relative in -157.5..-112.5 -> "da la vuelta a la izquierda"
                else -> "da la vuelta"
            }
            _relativeDirection.value = dirText

            // Solo avisar por voz si se está alejando
            if (isMovingAway && now - lastTtsTime > 15000 && distance > tolerance) {
                lastTtsTime = now
                speakTts("A ${distance.toInt()} metros, $dirText")
            } else if (!isMovingAway && now - lastTtsTime > 15000 && distance > tolerance) {
                lastTtsTime = now
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator?.vibrate(300)
                }
            }
        } else {
            _relativeBearing.value = null
            _relativeDirection.value = null

            // Solo avisar por voz si se está alejando
            if (isMovingAway && now - lastTtsTime > 20000 && distance > tolerance) {
                lastTtsTime = now
                speakTts("Punto a ${distance.toInt()} metros.")
            } else if (!isMovingAway && now - lastTtsTime > 20000 && distance > tolerance) {
                lastTtsTime = now
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator?.vibrate(300)
                }
            }
        }

        // 4. Efecto Doppler y Llegada normal al objetivo actual
        if (distance <= tolerance) {
            // Llegamos al punto de ruta actual
            currentBeepIntervalMs = -1
            serviceScope.launch(Dispatchers.Main) {
                handleWaypointReached(target)
            }
        } else if (distance <= limitDistance) {
            // Doppler: la frecuencia de pitido aumenta
            // Solo si beeps está habilitado Y la guía por voz está apagada (para no encimar sonidos molestos)
            if (prefs.enableBeeps && !_isVoiceGuidanceEnabled.value) {
                val ratio = (limitDistance - distance) / (limitDistance - tolerance)
                val ratioClamped = ratio.coerceIn(0f, 1f)
                currentBeepIntervalMs = (1500 - (ratioClamped * 1350)).toLong().coerceAtLeast(150L)
            } else {
                currentBeepIntervalMs = -1
            }
        } else {
            currentBeepIntervalMs = -1
        }
    }

    private suspend fun handleWaypointReached(punto: PuntoRutaEntity) {
        // Avanzar o retroceder de forma automática
        val puntos = _selectedRoutePoints.value
        val index = puntos.indexOfFirst { it.id == punto.id }
        if (index != -1) {
            val nextIndex = if (_guideDirectionForward.value) index + 1 else index - 1
            if (nextIndex in puntos.indices) {
                // Llegó a un punto intermedio
                val isNormal = punto.estado == 1
                if (isNormal) {
                    speakTts("Llegaste a punto de ruta: ${punto.descripcion}")
                } else {
                    val stateName = when(punto.estado) {
                        2 -> "sitio de interés"
                        3 -> "bifurcación"
                        4 -> "suelo flojo"
                        5 -> "cuidado"
                        6 -> "peligro"
                        8 -> "vehículo"
                        9 -> "refugio"
                        10 -> "comida"
                        else -> "punto especial"
                    }
                    speakTts("$stateName. ${punto.descripcion}", force = true)
                }
                
                // Sonido de llegada normal
                playSoundPattern(listOf(150 to 50, 150 to 50, 300 to 100))
                
                val nextPunto = puntos[nextIndex]
                _targetWaypoint.value = nextPunto
                speakTts("Siguiente destino a ${nextPunto.descripcion}")
            } else {
                // LLEGADA AL DESTINO FINAL
                speakTts("Felicidades. Has llegado a tu destino final, completando la ruta: ${punto.descripcion}")
                // Sonido de llegada triunfal / festivo
                playSoundPattern(listOf(150 to 50, 150 to 50, 150 to 50, 400 to 100, 200 to 50, 600 to 200))
                
                _isGuiding.value = false
                _targetWaypoint.value = null
            }
        }
    }

    // --- REPRODUCCIÃ“N DE SONIDOS A ALTO VOLUMEN ---

    private fun maximizeVolume() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)
            
            val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusicVol, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error maximizando volumen: ${e.message}")
        }
    }

    private fun startBeepLoop() {
        beepJob = serviceScope.launch {
            while (true) {
                val interval = currentBeepIntervalMs
                if (interval > 0) {
                    try {
                        // TONE_CDMA_PIP es un pulso muy breve y agradable para el efecto Doppler
                        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_PIP, 50)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error tocando pitido Doppler: ${e.message}")
                    }
                    delay(interval)
                } else {
                    delay(300)
                }
            }
        }
    }

    /**
     * Reproduce un patrón de pitidos pausando el Doppler temporalmente.
     * Utiliza TONE_CDMA_ALERT_CALL_GUARD para un silbido muy agudo, fuerte y escandaloso.
     */
    private fun playSoundPatternInternal(pattern: List<Pair<Int, Int>>) {
        stopEmergencyPatternInternal()
        serviceScope.launch {
            val prevInterval = currentBeepIntervalMs
            currentBeepIntervalMs = -1
            delay(150) // Pausa de transición

            for (step in pattern) {
                try {
                    maximizeVolume()
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, step.first)
                } catch (e: Exception) {
                    Log.e(TAG, "Error tocando pitido de patrón: ${e.message}")
                }
                delay(step.first + step.second.toLong())
            }

            currentBeepIntervalMs = prevInterval
        }
    }

    private fun startEmergencyPatternInternal(pattern: List<Pair<Int, Int>>) {
        emergencyJob?.cancel()
        emergencyJob = serviceScope.launch {
            val prevInterval = currentBeepIntervalMs
            currentBeepIntervalMs = -1
            delay(150) // Pausa de transición

            try {
                while (true) {
                    for (step in pattern) {
                        try {
                            maximizeVolume()
                            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, step.first)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error tocando pitido de patrón de emergencia: ${e.message}")
                        }
                        delay(step.first + step.second.toLong())
                    }
                }
            } finally {
                currentBeepIntervalMs = prevInterval
            }
        }
    }

    private fun stopEmergencyPatternInternal() {
        emergencyJob?.cancel()
        emergencyJob = null
    }



    // --- NOTIFICACIÃ“N DING ---

    private fun createForegroundNotification(contentText: String): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val openAppIntent = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java), flags
        )
        val stopSelfIntent = PendingIntent.getService(
            this, 11,
            Intent(this, GpsTrackingService::class.java).apply { action = ACTION_STOP_SERVICE },
            flags
        )

        return NotificationCompat.Builder(this, BachesApplication.CHANNEL_SERVICE_ID)
            .setContentTitle("Senderismo con Amigos Activo")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Apagar GPS", stopSelfIntent)
            .build()
    }

    private fun updateForegroundNotificationDynamic(location: Location) {
        val route = _selectedRoute.value
        val content = when {
            _isGuiding.value && _targetWaypoint.value != null -> {
                val target = _targetWaypoint.value!!
                val dist = _distanceToTarget.value ?: 0f
                val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
                val speedStr = String.format(Locale.US, "%.1f km/h", speedKmh)
                "Guía a: ${target.descripcion} (${dist.toInt()}m) · Vel: $speedStr"
            }
            route != null -> {
                val puntos = _selectedRoutePoints.value.size
                "Ruta: ${route.nombre} | ${puntos} pts | GPS Â±${String.format("%.1f", location.accuracy)}m"
            }
            else -> {
                "GPS Conectado | Esperando selección de ruta..."
            }
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_SERVICE_ID, createForegroundNotification(content))
    }

    // --- TTS MOTOR ---

    private fun speakTts(msg: String, force: Boolean = false) {
        if (!_isVoiceGuidanceEnabled.value && !force) return
        if (isTtsReady) {
            ttsEngine?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "SenderoAlert")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = ttsEngine?.setLanguage(Locale("es", "MX"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsEngine?.setLanguage(Locale("es", "ES"))
            }
            isTtsReady = true
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    enum class RecordingState { RECORDING, PAUSED_VEHICLE, STOPPED }

    private val acknowledgedEmergencies = mutableMapOf<String, Long>()
    private lateinit var beaconManager: BeaconEmitterManager
    
    

    
    

    fun checkBleSupport(): BleSupportStatus {
        return beaconManager.checkBleHardwareSupport()
    }

    private var beaconEmissionJob: Job? = null

    fun startBeaconEmissionInternal(idBeacon: String, isEmergency: Boolean = false, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        val lastLoc = currentLocation.value
        beaconManager.startEmitting(
            idBeacon = idBeacon,
            latitude = lastLoc?.latitude?.toFloat(),
            longitude = lastLoc?.longitude?.toFloat(),
            minor = if (isEmergency) 911 else 1,
            onSuccess = {
                _isBeaconEmitting.value = true
                _isBeaconEmergencyEmitting.value = isEmergency
                
                // Monitor location changes and update advertising with current GPS
                beaconEmissionJob?.cancel()
                beaconEmissionJob = serviceScope.launch {
                    var lastAdvertisedLoc: Location? = lastLoc
                    var lastAdvertiseTime = System.currentTimeMillis()
                    currentLocation.collect { newLoc ->
                        if (newLoc != null) {
                            val now = System.currentTimeMillis()
                            val dist = lastAdvertisedLoc?.distanceTo(newLoc) ?: Float.MAX_VALUE
                            
                            // Only restart advertising if moved > 10 meters AND at least 20 seconds have passed
                            if ((dist > 10.0f && now - lastAdvertiseTime > 20000L) || lastAdvertisedLoc == null) {
                                lastAdvertisedLoc = newLoc
                                lastAdvertiseTime = now
                                beaconManager.startEmitting(
                                    idBeacon = idBeacon,
                                    latitude = newLoc.latitude.toFloat(),
                                    longitude = newLoc.longitude.toFloat(),
                                    minor = if (isEmergency) 911 else 1,
                                    onSuccess = {},
                                    onFailure = {}
                                )
                            }
                        }
                    }
                }
                
                onSuccess()
            },
            onFailure = { errorCode ->
                _isBeaconEmitting.value = false
                onFailure(errorCode)
            }
        )
    }

    fun stopBeaconEmissionInternal() {
        beaconEmissionJob?.cancel()
        beaconEmissionJob = null
        beaconManager.stopEmitting()
        _isBeaconEmitting.value = false
        _isBeaconEmergencyEmitting.value = false
    }

    // --- ESCANEO DE BEACONS Y SEGUIMIENTO DE SENDERISTAS (MODO ABIERTO Y DINAMICO) ---
    private lateinit var beaconScanner: BeaconScannerManager

    private val participantStates = mutableMapOf<String, ParticipantState>()
    private val stateEmergencies = mutableMapOf<String, Long>() // idBeacon to Last Time Alarm triggered
    
    private var bleTimeoutJob: Job? = null
    private var blePendingChanges = false
    val trackingRefreshIntervalMs = MutableStateFlow(1500L)
    private val pendingFetches = mutableSetOf<String>()
    private val failedFetches = mutableMapOf<String, Long>()

    private data class ParticipantState(
        val telefonoSenderista: String,
        val nombreSenderista: String,
        val idBeacon: String?,
        var latitud: Double?,
        var longitud: Double?,
        var distance: Double = Double.MAX_VALUE,
        var lastRssi: Int? = null,
        var lastDetection: Long? = null,
        val fechaIngreso: Long,
        val aQuienesAvisar: String?,
        val aQuienAvisarTelefono1: String?,
        val aQuienAvisarTelefono2: String?,
        val tipoSangre: String?,
        val fechaNacimiento: String?,
        val alergias: String?,
        val enfermedadesCronicas: String?,
        var codigoConfianza: String? = null,
        val sexo: String?
    )

    private fun tryToIdentifyBeacon(idBeacon: String, state: ParticipantState, dist: Double, rssi: Int) {
        serviceScope.launch {
            val now = System.currentTimeMillis()
            synchronized(pendingFetches) {
                if (pendingFetches.contains(idBeacon)) return@launch
                val lastFailedTime = failedFetches[idBeacon]
                if (lastFailedTime != null && now - lastFailedTime < 180000L) { // 3 minutes lockout
                    return@launch
                }
                pendingFetches.add(idBeacon)
            }
            
            // A. Check local cache (Room SQLite) first
            val cachedSenderista = withContext(Dispatchers.IO) {
                db.rutasDao().getSenderistaByBeaconId(idBeacon)
            }
            
            if (cachedSenderista != null) {
                // If found locally in Room, upgrade participant info immediately
                upgradeParticipant(state, cachedSenderista, dist, rssi)

                synchronized(pendingFetches) {
                    pendingFetches.remove(idBeacon)
                }
                return@launch
            }
            
            // B. If not found locally, launch background network fetch
            launch(Dispatchers.IO) {
                try {
                    val senderistaObj = com.gaspar.rutassendero.sync.CloudSyncManager.fetchSenderistaByBeaconId(idBeacon)
                    if (senderistaObj != null) {
                        // DO NOT cache profile in senderista table to avoid contaminating the owner's profile.
                        // Switch to main thread to update UI and state structures
                        withContext(Dispatchers.Main) {
                            upgradeParticipant(state, senderistaObj, dist, rssi)
                        }
                        
                        synchronized(pendingFetches) {
                            pendingFetches.remove(idBeacon)
                            failedFetches.remove(idBeacon)
                        }
                    } else {
                        // Network query returned null (user not found or no internet)
                        synchronized(pendingFetches) {
                            pendingFetches.remove(idBeacon)
                            failedFetches[idBeacon] = System.currentTimeMillis()
                        }
                    }
                } catch (e: Exception) {
                    Log.w("GpsTrackingService", "Error fetching profile for beacon $idBeacon: ")
                    synchronized(pendingFetches) {
                        pendingFetches.remove(idBeacon)
                        failedFetches[idBeacon] = System.currentTimeMillis()
                    }
                }
            }
        }
    }

    fun forceFetchParticipantInternal(idBeacon: String, onResult: (ParticipanteEntity?) -> Unit) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Remove from blacklist so it doesn't get blocked
                synchronized(pendingFetches) {
                    failedFetches.remove(idBeacon)
                }

                // Fetch directly from the cloud
                val senderistaObj = com.gaspar.rutassendero.sync.CloudSyncManager.fetchSenderistaByBeaconId(idBeacon)
                if (senderistaObj != null) {
                    // DO NOT cache profile in senderista table to avoid contaminating the owner's profile.
                    
                    // Update the participant in the active list
                    val existingState = participantStates.values.find { it.idBeacon == idBeacon }
                    if (existingState != null) {
                        withContext(Dispatchers.Main) {
                            upgradeParticipant(existingState, senderistaObj, existingState.distance, existingState.lastRssi ?: -100)
                        }
                        
                        // Find the updated participant entity to pass back to the UI
                        val updatedEntity = db.rutasDao().getParticipanteByTelefono(senderistaObj.telefono)
                        withContext(Dispatchers.Main) {
                            onResult(updatedEntity)
                            android.widget.Toast.makeText(applicationContext, "Datos actualizados desde la nube", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onResult(null)
                            android.widget.Toast.makeText(applicationContext, "Beacon no detectado localmente", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(null)
                        android.widget.Toast.makeText(applicationContext, "No se encontraron datos en la nube para este Beacon", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(null)
                    android.widget.Toast.makeText(applicationContext, "Error de red al actualizar: ", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private suspend fun removeParticipant(state: ParticipantState) {
        withContext(Dispatchers.IO) {
            db.rutasDao().deleteParticipante(state.telefonoSenderista)
        }
        synchronized(participantStates) {
            participantStates.remove(state.telefonoSenderista)
        }
        blePendingChanges = true
        emitParticipantsState()
    }

    private suspend fun upgradeParticipant(
        state: ParticipantState,
        senderistaObj: SenderistaEntity,
        dist: Double,
        rssi: Int
    ) {
        // Delete temporary entry
        withContext(Dispatchers.IO) {
            db.rutasDao().deleteParticipante(state.telefonoSenderista)
        }

            val displayDistance = if (dist == Double.MAX_VALUE) "Fuera de alcance" else String.format(Locale.US, "%.1f m", dist)
        val enfermedadesStr = if (senderistaObj.tieneEnfermedadCronica == "S") {
            listOfNotNull(senderistaObj.enfermedadCronica1, senderistaObj.enfermedadCronica2).joinToString(", ").takeIf { it.isNotBlank() }
        } else null

        val newParticipant = ParticipanteEntity(
            telefonoSenderista = senderistaObj.telefono,
            nombreSenderista = senderistaObj.nombre,
            idBeacon = senderistaObj.idBeacon,
            latitud = state.latitud,
            longitud = state.longitud,
            distanciaEstimada = displayDistance,
            ultimoRssi = rssi,
            ultimaDeteccion = System.currentTimeMillis(),
            fechaIngreso = state.fechaIngreso,
            aQuienesAvisar = senderistaObj.aQuienesAvisar,
            aQuienAvisarTelefono1 = senderistaObj.aQuienAvisarTelefono1,
            aQuienAvisarTelefono2 = senderistaObj.aQuienAvisarTelefono2,
            tipoSangre = senderistaObj.tipoSangre,
            fechaNacimiento = senderistaObj.fechaNacimiento,
            alergias = senderistaObj.alergias,
            enfermedadesCronicas = enfermedadesStr,
            codigoConfianza = senderistaObj.codigoConfianza,
            sexo = senderistaObj.sexo
        )
        withContext(Dispatchers.IO) {
            db.rutasDao().insertParticipante(newParticipant)
        }

        synchronized(participantStates) {
            participantStates.remove(state.telefonoSenderista)
            participantStates[senderistaObj.telefono] = ParticipantState(
                telefonoSenderista = senderistaObj.telefono,
                nombreSenderista = senderistaObj.nombre,
                idBeacon = senderistaObj.idBeacon,
                latitud = newParticipant.latitud,
                longitud = newParticipant.longitud,
                distance = dist,
                lastRssi = rssi,
                lastDetection = newParticipant.ultimaDeteccion,
                fechaIngreso = newParticipant.fechaIngreso,
                aQuienesAvisar = senderistaObj.aQuienesAvisar,
                aQuienAvisarTelefono1 = senderistaObj.aQuienAvisarTelefono1,
                aQuienAvisarTelefono2 = senderistaObj.aQuienAvisarTelefono2,
                tipoSangre = senderistaObj.tipoSangre,
                fechaNacimiento = senderistaObj.fechaNacimiento,
                alergias = senderistaObj.alergias,
                enfermedadesCronicas = enfermedadesStr,
                codigoConfianza = senderistaObj.codigoConfianza,
                sexo = senderistaObj.sexo
            )
        }
        blePendingChanges = true
        emitParticipantsState()
    }

        fun startBleTrackingInternal() {
        serviceScope.launch {
            try {
                // 1. Purge expired participants (5 hours limit = 18,000,000 ms)
                val fiveHoursAgo = System.currentTimeMillis() - 18000000L
                db.rutasDao().pruneExpiredParticipantes(fiveHoursAgo)

                // 2. Load remaining active participants from Room
                val currentList = db.rutasDao().getAllParticipantes()
                participantStates.clear()
                currentList.forEach { p ->
                    participantStates[p.telefonoSenderista] = ParticipantState(
                        telefonoSenderista = p.telefonoSenderista,
                        nombreSenderista = p.nombreSenderista,
                        idBeacon = p.idBeacon,
                        latitud = p.latitud,
                        longitud = p.longitud,
                        distance = if (p.distanciaEstimada == "Fuera de alcance" || p.distanciaEstimada == null) Double.MAX_VALUE else {
                            try {
                                p.distanciaEstimada.replace(" m", "").toDouble()
                            } catch (e: Exception) {
                                Double.MAX_VALUE
                            }
                        },
                        lastRssi = p.ultimoRssi,
                        lastDetection = p.ultimaDeteccion,
                        fechaIngreso = p.fechaIngreso,
                        aQuienesAvisar = p.aQuienesAvisar,
                        aQuienAvisarTelefono1 = p.aQuienAvisarTelefono1,
                        aQuienAvisarTelefono2 = p.aQuienAvisarTelefono2,
                        tipoSangre = p.tipoSangre,
                        fechaNacimiento = p.fechaNacimiento,
                        alergias = p.alergias,
                        enfermedadesCronicas = p.enfermedadesCronicas,
                        codigoConfianza = p.codigoConfianza,
                        sexo = p.sexo
                    )
                }
                emitParticipantsState()

                // 3. Setup BLE scan listener
                beaconScanner.onBeaconDetected = { idBeacon, lat, lon, rssi, txPower, minor ->
                    serviceScope.launch {
                        val existingState = participantStates.values.find { it.idBeacon == idBeacon }
                        val dist = calculateDistance(txPower, rssi)
                        
                        // Check for emergency beacon
                        if (minor == 911) {
                            val lastAlarm = stateEmergencies[idBeacon] ?: 0L
                            stateEmergencies[idBeacon] = System.currentTimeMillis()
                            if (acknowledgedEmergencies.containsKey(idBeacon)) {
                                acknowledgedEmergencies[idBeacon] = System.currentTimeMillis()
                            } else {
                                if (!_activeEmergencies.value.contains(idBeacon)) {
                                    _activeEmergencies.value = _activeEmergencies.value + idBeacon
                                }
                                if (System.currentTimeMillis() - lastAlarm > 30000) { // debounce 30s
                                    startEmergencyPatternInternal(listOf(100 to 50, 100 to 50, 400 to 100))
                                    // You might want to update some state here to show UI indicator
                                    android.widget.Toast.makeText(applicationContext, "¡EMERGENCIA 911 DE ${existingState?.nombreSenderista ?: idBeacon}!", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }

                        if (existingState != null) {
                            // Update existing participant
                            existingState.distance = dist
                            existingState.lastRssi = rssi
                            existingState.lastDetection = System.currentTimeMillis()
                            if (lat != 0.0 || lon != 0.0) {
                                val hasMoved = if (existingState.latitud != null && existingState.longitud != null) {
                                    val oldLoc = Location("").apply {
                                        latitude = existingState.latitud!!
                                        longitude = existingState.longitud!!
                                    }
                                    val newLoc = Location("").apply {
                                        latitude = lat
                                        longitude = lon
                                    }
                                    oldLoc.distanceTo(newLoc) >= 5.0f
                                } else {
                                    true
                                }

                                if (hasMoved) {
                                    existingState.latitud = lat
                                    existingState.longitud = lon
                                }
                            }
                            blePendingChanges = true
                            
                            // If still a temporary participant (unidentified), try to identify
                            if (existingState.telefonoSenderista == existingState.idBeacon) {
                                tryToIdentifyBeacon(idBeacon, existingState, dist, rssi)
                            }
                        } else {
                            // New participant discovered: register DIRECTLY as raw beacon ID
                            val tempPhone = idBeacon
                            val tempName = "ID: $idBeacon - Hablar a soporte"
                            val displayDistance = if (dist == Double.MAX_VALUE) "Fuera de alcance" else String.format(Locale.US, "%.1f m", dist)
                            
                            val newParticipant = ParticipanteEntity(
                                telefonoSenderista = tempPhone,
                                nombreSenderista = tempName,
                                idBeacon = idBeacon,
                                latitud = if (lat != 0.0) lat else null,
                                longitud = if (lon != 0.0) lon else null,
                                distanciaEstimada = displayDistance,
                                ultimoRssi = rssi,
                                ultimaDeteccion = System.currentTimeMillis(),
                                fechaIngreso = System.currentTimeMillis(),
                                aQuienesAvisar = null,
                                aQuienAvisarTelefono1 = null,
                                aQuienAvisarTelefono2 = null,
                                alergias = null,
                                enfermedadesCronicas = null,
                                codigoConfianza = null,
                                sexo = null
                            )
                            withContext(Dispatchers.IO) {
                                db.rutasDao().insertParticipante(newParticipant)
                            }
                            
                            val newState = ParticipantState(
                                telefonoSenderista = tempPhone,
                                nombreSenderista = tempName,
                                idBeacon = idBeacon,
                                latitud = newParticipant.latitud,
                                longitud = newParticipant.longitud,
                                distance = dist,
                                lastRssi = rssi,
                                lastDetection = newParticipant.ultimaDeteccion,
                                fechaIngreso = newParticipant.fechaIngreso,
                                aQuienesAvisar = null,
                                aQuienAvisarTelefono1 = null,
                                aQuienAvisarTelefono2 = null,
                                tipoSangre = null,
                                fechaNacimiento = null,
                                alergias = null,
                                enfermedadesCronicas = null,
                                codigoConfianza = null,
                                sexo = null
                            )
                            participantStates[tempPhone] = newState
                            blePendingChanges = true
                            emitParticipantsState()
                            
                            // Fetch profile details (name, emergency contacts)
                            tryToIdentifyBeacon(idBeacon, newState, dist, rssi)
                        }
                    }
                }

                // 4. Start scan
                val started = beaconScanner.startScan()
                if (started) {
                    bleTimeoutJob?.cancel()
                    bleTimeoutJob = serviceScope.launch {
                        var lastSaveTime = System.currentTimeMillis()
                        while (true) {
                            val interval = trackingRefreshIntervalMs.value
                            delay(interval)
                            val now = System.currentTimeMillis()
                            participantStates.values.forEach { state ->
                                val lastDet = state.lastDetection ?: 0L
                                if (state.distance != Double.MAX_VALUE && now - lastDet > 10000L) {
                                    state.distance = Double.MAX_VALUE
                                    blePendingChanges = true
                                }
                            }
                            
                            val timeoutMs = prefs.emergencyTimeoutMinutes * 60 * 1000L
                            val expiredEmergencies = stateEmergencies.filter { now - it.value > timeoutMs }.keys
                            if (expiredEmergencies.isNotEmpty()) {
                                expiredEmergencies.forEach { stateEmergencies.remove(it) }
                                _activeEmergencies.value = _activeEmergencies.value - expiredEmergencies
                            }
                            
                            val expiredAcknowledged = acknowledgedEmergencies.filter { now - it.value > timeoutMs }.keys
                            if (expiredAcknowledged.isNotEmpty()) {
                                expiredAcknowledged.forEach { acknowledgedEmergencies.remove(it) }
                            }
                            
                            if (blePendingChanges) {
                                emitParticipantsState()
                                blePendingChanges = false
                            }

                            // Periodic persist to Room (every ~15 seconds)
                            if (now - lastSaveTime >= 15000L) {
                                lastSaveTime = now
                                val entities = participantStates.values.map { state ->
                                    val displayDistance = if (state.distance == Double.MAX_VALUE) {
                                        "Fuera de alcance"
                                    } else {
                                        String.format(Locale.US, "%.1f m", state.distance)
                                    }
                                    ParticipanteEntity(
                                        telefonoSenderista = state.telefonoSenderista,
                                        nombreSenderista = state.nombreSenderista,
                                        idBeacon = state.idBeacon,
                                        latitud = state.latitud,
                                        longitud = state.longitud,
                                        distanciaEstimada = displayDistance,
                                        ultimoRssi = state.lastRssi,
                                        ultimaDeteccion = state.lastDetection,
                                        fechaIngreso = state.fechaIngreso,
                                        aQuienesAvisar = state.aQuienesAvisar,
                                        aQuienAvisarTelefono1 = state.aQuienAvisarTelefono1,
                                        aQuienAvisarTelefono2 = state.aQuienAvisarTelefono2,
                                        tipoSangre = state.tipoSangre,
                                        fechaNacimiento = state.fechaNacimiento,
                                        alergias = state.alergias,
                                        enfermedadesCronicas = state.enfermedadesCronicas,
                                        codigoConfianza = state.codigoConfianza,
                                        sexo = state.sexo
                                    )
                                }
                                if (entities.isNotEmpty()) {
                                    withContext(Dispatchers.IO) {
                                        db.rutasDao().insertParticipantes(entities)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error en startBleTracking: ${e.message}", e)
            }
        }
    }

    fun stopBleTrackingInternal() {
        beaconScanner.stopScan()
        bleTimeoutJob?.cancel()
        bleTimeoutJob = null

        // Persist final states
        if (participantStates.isNotEmpty()) {
            serviceScope.launch {
                val entities = participantStates.values.map { state ->
                    val displayDistance = if (state.distance == Double.MAX_VALUE) {
                        "Fuera de alcance"
                    } else {
                        String.format(Locale.US, "%.1f m", state.distance)
                    }
                    ParticipanteEntity(
                        telefonoSenderista = state.telefonoSenderista,
                        nombreSenderista = state.nombreSenderista,
                        idBeacon = state.idBeacon,
                        latitud = state.latitud,
                        longitud = state.longitud,
                        distanciaEstimada = displayDistance,
                        ultimoRssi = state.lastRssi,
                        ultimaDeteccion = state.lastDetection,
                        fechaIngreso = state.fechaIngreso,
                        aQuienesAvisar = state.aQuienesAvisar,
                        aQuienAvisarTelefono1 = state.aQuienAvisarTelefono1,
                        aQuienAvisarTelefono2 = state.aQuienAvisarTelefono2,
                        tipoSangre = state.tipoSangre,
                        fechaNacimiento = state.fechaNacimiento,
                        alergias = state.alergias,
                        enfermedadesCronicas = state.enfermedadesCronicas,
                        codigoConfianza = state.codigoConfianza,
                        sexo = state.sexo
                    )
                }
                if (entities.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        db.rutasDao().insertParticipantes(entities)
                    }
                }
            }
        }
    }


    private fun acknowledgeEmergencyInternal(idBeacon: String) {
        stopEmergencyPatternInternal()
        _activeEmergencies.value = _activeEmergencies.value - idBeacon
        acknowledgedEmergencies[idBeacon] = System.currentTimeMillis()
    }

    private fun emitParticipantsState() {
        val sortedList = participantStates.values.sortedBy { it.distance }
        val entities = sortedList.map { state ->
            val displayDistance = if (state.distance == Double.MAX_VALUE) {
                "Fuera de alcance"
            } else {
                String.format(Locale.US, "%.1f m", state.distance)
            }
            ParticipanteEntity(
                telefonoSenderista = state.telefonoSenderista,
                nombreSenderista = state.nombreSenderista,
                idBeacon = state.idBeacon,
                latitud = state.latitud,
                longitud = state.longitud,
                distanciaEstimada = displayDistance,
                ultimoRssi = state.lastRssi,
                ultimaDeteccion = state.lastDetection,
                fechaIngreso = state.fechaIngreso,
                aQuienesAvisar = state.aQuienesAvisar,
                aQuienAvisarTelefono1 = state.aQuienAvisarTelefono1,
                aQuienAvisarTelefono2 = state.aQuienAvisarTelefono2,
                tipoSangre = state.tipoSangre,
                fechaNacimiento = state.fechaNacimiento,
                alergias = state.alergias,
                enfermedadesCronicas = state.enfermedadesCronicas,
                codigoConfianza = state.codigoConfianza,
                sexo = state.sexo
            )
        }
        _activeOutingParticipants.value = entities
    }

    private fun calculateDistance(txPower: Int, rssi: Int): Double {
        if (rssi == 0) return Double.MAX_VALUE
        val actualTxPower = if (txPower in -100..-30) txPower else -59
        val n = 2.2 // environmental attenuation factor
        val ratio = (actualTxPower - rssi) / (10.0 * n)
        return Math.pow(10.0, ratio)
    }


    companion object {
        private const val TAG = "Sendero_GpsService"
        private const val NOTIFICATION_SERVICE_ID = 1

        const val ACTION_STOP_SERVICE = "com.gaspar.rutassendero.action.STOP_SERVICE"

        // --- FLUJOS DE ESTADO GLOBALES ---
                private val _isBeaconEmitting = MutableStateFlow(false)
        val isBeaconEmitting = _isBeaconEmitting.asStateFlow()

        private val _isBeaconEmergencyEmitting = MutableStateFlow(false)
        val isBeaconEmergencyEmitting = _isBeaconEmergencyEmitting.asStateFlow()

        private val _activeOutingParticipants = MutableStateFlow<List<ParticipanteEntity>>(emptyList())
        val activeOutingParticipants = _activeOutingParticipants.asStateFlow()

        private val _activeEmergencies = MutableStateFlow<Set<String>>(emptySet())
        val activeEmergencies = _activeEmergencies.asStateFlow()

        private val _isBleScanning = MutableStateFlow(false)
        val isBleScanning = _isBleScanning.asStateFlow()

        fun acknowledgeEmergency(idBeacon: String) {
            instance?.acknowledgeEmergencyInternal(idBeacon)
        }
        
        val trackingRefreshIntervalMs = MutableStateFlow(1500L)

        fun checkBleSupport(context: Context): BleSupportStatus {
            return BeaconEmitterManager(context).checkBleHardwareSupport()
        }

        fun startBeaconEmission(idBeacon: String, isEmergency: Boolean = false, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
            instance?.startBeaconEmissionInternal(idBeacon, isEmergency, onSuccess, onFailure) ?: onFailure(-1)
        }

        fun stopBeaconEmission() {
            instance?.stopBeaconEmissionInternal()
        }

        fun startBleTracking() {
            instance?.startBleTrackingInternal()
        }

        fun stopBleTracking() {
            instance?.stopBleTrackingInternal()
        }

        fun forceFetchParticipant(idBeacon: String, onResult: (ParticipanteEntity?) -> Unit) {
            instance?.forceFetchParticipantInternal(idBeacon, onResult) ?: onResult(null)
        }

        private val _gpsRunningState = MutableStateFlow(false)
        val gpsRunningState = _gpsRunningState.asStateFlow()

        private val _recordingState = MutableStateFlow(RecordingState.STOPPED)
        val recordingState = _recordingState.asStateFlow()



        private val _currentLocation = MutableStateFlow<Location?>(null)
        val currentLocation = _currentLocation.asStateFlow()

        private val _selectedRoute = MutableStateFlow<RutaEntity?>(null)
        val selectedRoute = _selectedRoute.asStateFlow()

        private val _selectedRoutePoints = MutableStateFlow<List<PuntoRutaEntity>>(emptyList())
        val selectedRoutePoints = _selectedRoutePoints.asStateFlow()

        private val _selectedRouteTrace = MutableStateFlow<List<TrazaRutaEntity>>(emptyList())
        val selectedRouteTrace = _selectedRouteTrace.asStateFlow()

        private val _targetWaypoint = MutableStateFlow<PuntoRutaEntity?>(null)
        val targetWaypoint = _targetWaypoint.asStateFlow()

        private val _distanceToTarget = MutableStateFlow<Float?>(null)
        val distanceToTarget = _distanceToTarget.asStateFlow()

        private val _relativeBearing = MutableStateFlow<Float?>(null)
        val relativeBearing = _relativeBearing.asStateFlow()

        private val _relativeDirection = MutableStateFlow<String?>(null)
        val relativeDirection = _relativeDirection.asStateFlow()

        private val _isGuiding = MutableStateFlow(false)
        val isGuiding = _isGuiding.asStateFlow()

        private val _isVoiceGuidanceEnabled = MutableStateFlow(false)
        val isVoiceGuidanceEnabled = _isVoiceGuidanceEnabled.asStateFlow()

        fun setVoiceGuidanceEnabled(enabled: Boolean) {
            _isVoiceGuidanceEnabled.value = enabled
        }

        private val _guideDirectionForward = MutableStateFlow(true)
        val guideDirectionForward = _guideDirectionForward.asStateFlow()

        // --- MÃ‰TODOS DE ACCIÃ“N ---
        @Volatile
        private var instance: GpsTrackingService? = null

        private val announcedAlertsThresholds = mutableSetOf<String>()



        fun playSoundPattern(pattern: List<Pair<Int, Int>>) {
            instance?.playSoundPatternInternal(pattern)
        }

        fun startEmergencyPattern(pattern: List<Pair<Int, Int>>) {
            instance?.startEmergencyPatternInternal(pattern)
        }

        fun stopEmergencyPattern() {
            instance?.stopEmergencyPatternInternal()
        }

        fun setSelectedRoute(ruta: RutaEntity?) {
            _selectedRoute.value = ruta
            if (ruta == null) {
                _isGuiding.value = false
                _targetWaypoint.value = null
                _selectedRouteTrace.value = emptyList()
                announcedAlertsThresholds.clear()
                instance?.prefs?.activeRouteId = null
            }
        }

        fun startGuiding(target: PuntoRutaEntity?, forward: Boolean) {
            _guideDirectionForward.value = forward
            _targetWaypoint.value = target
            _isGuiding.value = true
            
            val rutaId = _selectedRoute.value?.id
            if (rutaId != null) {
                instance?.prefs?.activeRouteId = rutaId
                instance?.prefs?.activeRouteForward = forward
            }
        }

        fun startGuidingToDestination(toStart: Boolean) {
            val points = _selectedRoutePoints.value
            if (points.isEmpty()) return

            val userLoc = _currentLocation.value
            val target = if (userLoc != null) {
                // Encontrar el punto más cercano para iniciar inteligentemente
                points.minByOrNull { pt ->
                    val ptLoc = Location("").apply {
                        latitude = pt.latitud
                        longitude = pt.longitud
                    }
                    userLoc.distanceTo(ptLoc)
                }
            } else {
                if (toStart) points.lastOrNull() else points.firstOrNull()
            }

            _guideDirectionForward.value = !toStart
            _targetWaypoint.value = target
            _isGuiding.value = true
            announcedAlertsThresholds.clear()
            
            val rutaId = _selectedRoute.value?.id
            if (rutaId != null) {
                instance?.prefs?.activeRouteId = rutaId
                instance?.prefs?.activeRouteForward = !toStart
            }

            val destText = if (toStart) "el inicio" else "el final"
            instance?.speakTts("Iniciando guía hacia $destText del sendero. Avance al punto: ${target?.descripcion ?: ""}")
        }

        fun stopGuiding() {
            _isGuiding.value = false
            _targetWaypoint.value = null
            announcedAlertsThresholds.clear()
            instance?.prefs?.activeRouteId = null
        }

        fun setTargetWaypoint(target: PuntoRutaEntity?) {
            _targetWaypoint.value = target
        }

        fun advanceWaypointManually() {
            val service = instance ?: return
            val currentTarget = _targetWaypoint.value ?: return
            service.serviceScope.launch {
                val puntos = _selectedRoutePoints.value
                val index = puntos.indexOfFirst { it.id == currentTarget.id }
                if (index != -1) {
                    val nextIndex = if (_guideDirectionForward.value) index + 1 else index - 1
                    if (nextIndex in puntos.indices) {
                        service.speakTts("Punto ${currentTarget.descripcion} localizado manualmente.")
                        service.playSoundPatternInternal(listOf(150 to 50, 150 to 50, 300 to 100))
                        delay(1200) // Delay to let the sound finish before speaking next target
                        val nextPunto = puntos[nextIndex]
                        _targetWaypoint.value = nextPunto
                        service.speakTts("Siguiente destino a ${nextPunto.descripcion}")
                    } else {
                        service.speakTts("Punto final ${currentTarget.descripcion} localizado manualmente. Guía finalizada.")
                        service.playSoundPatternInternal(listOf(150 to 50, 150 to 50, 150 to 50, 400 to 100, 200 to 50, 600 to 200))
                        _isGuiding.value = false
                        _targetWaypoint.value = null
                    }
                }
            }
        }
    }
}

