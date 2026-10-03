package com.gaspar.rutassendero.ui

// REGULA LIMITACIÃ“N DE RUTAS PRIVADAS: Por regla el usuario sólo puede tener un máximo de 3 rutas no públicas (privadas). Esta regla cambiará cuando coloquemos la app en la tienda (Play Store).

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gaspar.rutassendero.BachesApplication
import com.gaspar.rutassendero.data.RutaEntity
import com.gaspar.rutassendero.data.PuntoRutaEntity
import com.gaspar.rutassendero.data.SenderistaEntity
import com.gaspar.rutassendero.data.TrazaRutaEntity
import com.gaspar.rutassendero.data.FraseDificultadEntity
import com.gaspar.rutassendero.data.RutasDatabase
import com.gaspar.rutassendero.data.PreferencesHelper
import com.gaspar.rutassendero.data.InvitacionAceptadaEntity
import com.gaspar.rutassendero.data.AgrupacionEntity
import com.gaspar.rutassendero.service.GpsTrackingService
import com.gaspar.rutassendero.sync.GoogleDriveSyncManager
import com.gaspar.rutassendero.sync.CryptoUtils
import android.accounts.Account
import android.content.Context
import android.widget.Toast
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import com.gaspar.rutassendero.bluetooth.BeaconEmitterManager
import com.gaspar.rutassendero.bluetooth.BleSupportStatus
import com.gaspar.rutassendero.bluetooth.BeaconScannerManager
import com.gaspar.rutassendero.ui.components.matchPhones
import com.gaspar.rutassendero.data.ParticipanteEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

data class PuntoRutaUIModel(
    val entity: PuntoRutaEntity,
    val distanceMeters: Float? // Distance to waypoint
)

data class CalendarEventData(
    val title: String,
    val description: String,
    val location: String,
    val beginTimeMs: Long,
    val endTimeMs: Long
)


data class SenderistaUiState(
    val isLoaded: Boolean = false,
    val senderista: SenderistaEntity? = null
)


class RutasViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as BachesApplication
    val db: RutasDatabase = app.database
    val prefs: PreferencesHelper = app.preferencesHelper

    val networkErrorMessage = MutableStateFlow<String?>(null)

    fun dismissNetworkError() {
        networkErrorMessage.value = null
    }

    val isBeaconEmitting: StateFlow<Boolean> = GpsTrackingService.isBeaconEmitting
    val isBeaconEmergencyEmitting: StateFlow<Boolean> = GpsTrackingService.isBeaconEmergencyEmitting

    fun checkBleSupport(): BleSupportStatus {
        return GpsTrackingService.checkBleSupport(app)
    }

    fun startBeaconEmission(idBeacon: String, isEmergency: Boolean = false, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        GpsTrackingService.startBeaconEmission(idBeacon, isEmergency, onSuccess, onFailure)
    }

    fun stopBeaconEmission() {
        GpsTrackingService.stopBeaconEmission()
    }

    val activeOutingParticipants: StateFlow<List<ParticipanteEntity>> = GpsTrackingService.activeOutingParticipants
    val activeEmergencies: StateFlow<Set<String>> = GpsTrackingService.activeEmergencies
    val isBleScanning: StateFlow<Boolean> = GpsTrackingService.isBleScanning
    val trackingRefreshIntervalMs = GpsTrackingService.trackingRefreshIntervalMs

    fun startBleTracking() {
        GpsTrackingService.startBleTracking()
    }

    fun stopBleTracking() {
        GpsTrackingService.stopBleTracking()
    }

    fun forceFetchParticipant(idBeacon: String, onResult: (ParticipanteEntity?) -> Unit) {
        GpsTrackingService.forceFetchParticipant(idBeacon, onResult)
    }
    val isGpsActive: StateFlow<Boolean> = GpsTrackingService.gpsRunningState
    val currentLocation: StateFlow<Location?> = GpsTrackingService.currentLocation

    private val _uiLocation = MutableStateFlow<Location?>(null)
    val uiLocation: StateFlow<Location?> = _uiLocation.asStateFlow()

    fun refreshUiLocation() {
        _uiLocation.value = GpsTrackingService.currentLocation.value
    }

    val selectedRoute: StateFlow<RutaEntity?> = GpsTrackingService.selectedRoute
    val selectedRoutePoints: StateFlow<List<PuntoRutaEntity>> = GpsTrackingService.selectedRoutePoints
    val selectedRouteTrace: StateFlow<List<TrazaRutaEntity>> = GpsTrackingService.selectedRouteTrace

    val targetWaypoint: StateFlow<PuntoRutaEntity?> = GpsTrackingService.targetWaypoint
    val distanceToTarget: StateFlow<Float?> = GpsTrackingService.distanceToTarget
    val relativeBearing: StateFlow<Float?> = GpsTrackingService.relativeBearing
    val relativeDirection: StateFlow<String?> = GpsTrackingService.relativeDirection
    val isGuiding: StateFlow<Boolean> = GpsTrackingService.isGuiding
    val guideDirectionForward: StateFlow<Boolean> = GpsTrackingService.guideDirectionForward
    
    val isVoiceGuidanceEnabled: StateFlow<Boolean> = GpsTrackingService.isVoiceGuidanceEnabled
    fun toggleVoiceGuidance() {
        GpsTrackingService.setVoiceGuidanceEnabled(!isVoiceGuidanceEnabled.value)
    }

    // Foto temporal capturada por la cámara
    val capturedPhotoPath = MutableStateFlow<String?>(null)
    val photoCaptureCancelledEvent = MutableSharedFlow<Unit>(replay = 0)

    // Indica si el orden de los puntos en la lista debe ser invertido (final a inicio)
    val isPointsListReversed = MutableStateFlow(false)

    // --- STATE FOR OUTINGS (SALIDAS) ---
    val invitacionesAceptadas: StateFlow<List<InvitacionAceptadaEntity>> = db.rutasDao().getInvitacionesAceptadasFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
    )

    private val _availableOutings = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val availableOutings: StateFlow<List<Map<String, Any>>> = _availableOutings.asStateFlow()

    private val _loadingAvailableOutings = MutableStateFlow(false)
    val loadingAvailableOutings: StateFlow<Boolean> = _loadingAvailableOutings.asStateFlow()

    val calendarEventFlow = MutableSharedFlow<CalendarEventData>(replay = 0)

    private val _outingsProgress = MutableStateFlow<String?>(null)
    val outingsProgress: StateFlow<String?> = _outingsProgress.asStateFlow()
    private val _senderistaUiState = MutableStateFlow(SenderistaUiState())
    val senderistaUiState: StateFlow<SenderistaUiState> = _senderistaUiState.asStateFlow()

    private val _senderistaLoaded = MutableStateFlow(false)
    val senderistaLoaded: StateFlow<Boolean> = _senderistaLoaded.asStateFlow()

    private val _ownerEmailFlow = MutableStateFlow(prefs.ownerEmail)

    val agrupacionesList: StateFlow<List<AgrupacionEntity>> = db.rutasDao().getAgrupacionesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AgrupacionEntity.SEED_AGRUPACIONES
        )


    fun fetchAgrupaciones() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cloudData = com.gaspar.rutassendero.sync.CloudSyncManager.descargarAgrupaciones()
                if (cloudData.isNotEmpty()) {
                    db.rutasDao().deleteAllAgrupaciones()
                    db.rutasDao().insertAgrupaciones(cloudData)
                    Log.d("RutasViewModel", "Agrupaciones actualizadas desde la nube: ${cloudData.size}")
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error sincronizando agrupaciones con la nube: ${e.message}")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val senderistaState: StateFlow<SenderistaEntity?> = _ownerEmailFlow.flatMapLatest { email ->
        if (email.isNullOrBlank()) {
            db.rutasDao().getSenderistaFlow()
        } else {
            db.rutasDao().getSenderistaByEmailFlow(email)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    init {
        deleteExpiredDownloadedRoutes()

        // 1. Auto-detect and initialize ownerEmail from database if null (upgrade transition / unswapping)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Try Google Account first (extremely reliable for unswapping cached identities)
                var googleEmail: String? = null
                
                // Method A: AccountManager (Gets the phone's primary Google account directly if permitted)
                if (androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.GET_ACCOUNTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val accountManager = android.accounts.AccountManager.get(app)
                    val accounts = accountManager.getAccountsByType("com.google")
                    if (accounts.isNotEmpty()) {
                        googleEmail = accounts[0].name
                        Log.d("RutasViewModel", "Obtained Google email via AccountManager: $googleEmail")
                    }
                }
                
                // Method B: Fallback to Google SignIn SDK
                if (googleEmail.isNullOrBlank()) {
                    googleEmail = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(app)?.email
                }

                var resolvedEmail: String? = null
                if (!googleEmail.isNullOrBlank()) {
                    val matchingSenderista = db.rutasDao().getSenderistaByEmailFlow(googleEmail).first()
                    if (matchingSenderista != null) {
                        resolvedEmail = matchingSenderista.email
                    } else {
                        // Query PostgreSQL in the cloud to restore profile if not present locally
                        val cloudSenderista = com.gaspar.rutassendero.sync.CloudSyncManager.fetchSenderistaByEmail(googleEmail)
                        if (cloudSenderista != null) {
                            db.rutasDao().insertSenderista(cloudSenderista)
                            resolvedEmail = cloudSenderista.email
                        }
                    }
                }

                // If Google Account was not found/signed-in, fallback to the local DB
                if (resolvedEmail == null) {
                    val allSenderistas = db.rutasDao().getAllSenderistas()
                    if (allSenderistas.size == 1) {
                        // Safe: Only one profile exists, must be the owner
                        resolvedEmail = allSenderistas[0].email
                    } else if (allSenderistas.size > 1) {
                        val ownerEmailPref = prefs.ownerEmail
                        if (!ownerEmailPref.isNullOrBlank()) {
                            val ownerProfile = allSenderistas.find { it.email == ownerEmailPref }
                            if (ownerProfile != null) {
                                resolvedEmail = ownerProfile.email
                                // Clean up the contaminated entries!
                                db.rutasDao().clearCachedSenderistas(ownerEmailPref)
                                Log.i("RutasViewModel", "Recovered owner profile from preferences and cleaned up contamination.")
                            } else {
                                db.rutasDao().deleteAllSenderistas()
                                Log.w("RutasViewModel", "Owner profile not found in DB despite preferences. Cleared DB.")
                            }
                        } else {
                            // DANGER: Multiple profiles exist, and we don't know which is the owner.
                            // We MUST NOT pick randomly because it swaps identities!
                            // Clear the table to force the user to enter their profile (which resets it correctly).
                            db.rutasDao().deleteAllSenderistas()
                            Log.w("RutasViewModel", "Multiple profiles found but owner unknown. Cleared DB to prevent identity swap.")
                        }
                    }
                }

                if (resolvedEmail != null) {
                    prefs.ownerEmail = resolvedEmail
                    _ownerEmailFlow.value = resolvedEmail
                    Log.d("RutasViewModel", "Resolved ownerEmail to: $resolvedEmail")

                    // Background update for rol and bloqueado from server
                    try {
                        val cloudSenderista = com.gaspar.rutassendero.sync.CloudSyncManager.fetchSenderistaByEmail(resolvedEmail)
                        if (cloudSenderista != null) {
                            val local = db.rutasDao().getSenderistaByEmailFlow(resolvedEmail).first()
                            if (local != null) {
                                val normalizedServerRol = if (cloudSenderista.rol.equals("G", ignoreCase = true)) "G" else cloudSenderista.rol.uppercase()
                                val cloudAgrupacion = cloudSenderista.idAgrupacion?.takeIf { it.isNotBlank() }
                                val shouldUpdate = local.rol != normalizedServerRol || 
                                    local.bloqueado != cloudSenderista.bloqueado || 
                                    local.minimoLongitud != cloudSenderista.minimoLongitud ||
                                    (cloudAgrupacion != null && local.idAgrupacion != cloudAgrupacion)
                                if (shouldUpdate) {
                                    val updatedLocal = local.copy(
                                        rol = normalizedServerRol,
                                        bloqueado = cloudSenderista.bloqueado,
                                        minimoLongitud = cloudSenderista.minimoLongitud,
                                        idAgrupacion = cloudAgrupacion ?: local.idAgrupacion
                                    )
                                    db.rutasDao().insertSenderista(updatedLocal)
                                    Log.d("RutasViewModel", "Updated local senderista with server rol=$normalizedServerRol, agrupacion=${updatedLocal.idAgrupacion}, bloqueado=${cloudSenderista.bloqueado}, minimoLongitud=${cloudSenderista.minimoLongitud}")
                                }
                            }
                            syncOwnRoutesFromCloud(cloudSenderista)
                        }
                    } catch (e: Exception) {
                        Log.e("RutasViewModel", "Failed to sync senderista role on startup: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error resolving ownerEmail on startup: ${e.message}")
            }
        }

        // 2. Collect senderista profile avoiding coroutine race condition
        viewModelScope.launch {
            _ownerEmailFlow.flatMapLatest { email ->
                if (email.isNullOrBlank()) {
                    db.rutasDao().getSenderistaFlow()
                } else {
                    db.rutasDao().getSenderistaByEmailFlow(email)
                }
            }.collect { senderista ->
                _senderistaUiState.value = SenderistaUiState(isLoaded = true, senderista = senderista)
                _senderistaLoaded.value = true
                if (senderista != null) {
                    com.gaspar.rutassendero.sync.CloudSyncManager.setAuthContext(
                        senderista.email, 
                        senderista.telefono, 
                        senderista.rol, 
                        senderista.idAgrupacion
                    )
                    if (prefs.ownerEmail.isNullOrBlank()) {
                        prefs.ownerEmail = senderista.email
                        _ownerEmailFlow.value = senderista.email
                    }
                    cleanupPastOutings(senderista.telefono)
                    syncOwnRoutesFromCloud(senderista)
                    
                    // Sincronizar en background todas las tablas según su fupdate
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            com.gaspar.rutassendero.sync.CloudSyncManager.syncDeltaFromCloud(app, db)
                        } catch (e: Exception) {
                            Log.e("RutasViewModel", "Error al sincronizar datos delta desde la nube: \${e.message}", e)
                        }
                    }

                    if (senderista.idBeacon.isNullOrBlank()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                Log.d("RutasViewModel", "El campo idBeacon del senderista es nulo o vacio. Generando uno nuevo...")
                                val uniqueId = com.gaspar.rutassendero.sync.CloudSyncManager.generateUniqueBeaconId()
                                val updatedSenderista = senderista.copy(idBeacon = uniqueId)
                                db.rutasDao().insertSenderista(updatedSenderista)
                                com.gaspar.rutassendero.sync.CloudSyncManager.syncSenderista(updatedSenderista)
                                Log.d("RutasViewModel", "idBeacon generado y sincronizado con exito: $uniqueId")
                            } catch (e: Exception) {
                                Log.e("RutasViewModel", "Error al generar/sincronizar idBeacon: ${e.message}", e)
                            }
                        }
                    }
                }
            }
        }

        // Suscribirse a la ubicación para actualizar uiLocation en tiempo real
        viewModelScope.launch {
            GpsTrackingService.currentLocation.collect { location ->
                _uiLocation.value = location
            }
        }
        // Siembra de frases simpáticas en la base de datos local
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (db.rutasDao().getFraseCount() == 0) {
                    db.rutasDao().insertFrases(FraseDificultadEntity.SEED_FRASES)
                    Log.d("RutasViewModel", "Se sembraron las frases de dificultad en SQLite local.")
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al sembrar frases en SQLite: ${e.message}", e)
            }
        }

        // Siembra de agrupaciones por omisión en SQLite local si está vacía
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (db.rutasDao().getAgrupaciones().isEmpty()) {
                    db.rutasDao().insertAgrupaciones(AgrupacionEntity.SEED_AGRUPACIONES)
                    Log.d("RutasViewModel", "Se sembraron las agrupaciones por omisión en SQLite local.")
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al sembrar agrupaciones en SQLite: ${e.message}", e)
            }
        }

        // Limpieza automática en segundo plano de cualquier punto duplicado de traza acumulado anteriormente
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.openHelper.writableDatabase.execSQL(
                    "DELETE FROM traza_ruta WHERE id NOT IN (SELECT MIN(id) FROM traza_ruta GROUP BY id_ruta, orden)"
                )
                val rutas = db.rutasDao().getAllRutas()
                for (ruta in rutas) {
                    val puntos = db.rutasDao().getPuntosForRuta(ruta.id)
                    if (puntos.size == 1) {
                        val singlePt = puntos.first()
                        if (singlePt.esFinRuta) {
                            val updatedSingle = singlePt.copy(esFinRuta = false)
                            db.rutasDao().insertPuntoRuta(updatedSingle)
                            Log.d("RutasViewModel", "Reparada ruta '${ruta.nombre}' (${ruta.id}): Se desmarcó el único punto como fin de ruta para desbloquearla.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al reparar rutas de 1 punto: ${e.message}", e)
            }
        }

        // Registrar callback de red Wi-Fi para sincronización automática
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (cm != null) {
            val request = android.net.NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            try {
                cm.registerNetworkCallback(request, object : android.net.ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: android.net.Network) {
                        Log.d("RutasViewModel", "Wi-Fi detected! Triggering automatic cloud sync...")
                        triggerAutomaticCloudSync()
                    }
                })
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error registering network callback: ${e.message}")
            }
        }

        // Ejecutar sincronización al iniciar ("cuando lo prenda")
        triggerAutomaticCloudSync()
        fetchAgrupaciones()
    }


    fun saveSenderista(profile: SenderistaEntity) {
        if (!com.gaspar.rutassendero.utils.NetworkUtils.isNetworkAvailable(app)) {
            networkErrorMessage.value = "Para registrar o actualizar sus datos, el dispositivo necesita tener conexión a Internet (Datos o WiFi)."
            return
        }

        if (!profile.idAgrupacion.isNullOrBlank() && profile.idAgrupacion != "@&") {
            val agrupacionValida = profile.idAgrupacion.equals("MH", ignoreCase = true) ||
                AgrupacionEntity.SEED_AGRUPACIONES.any { it.idAgrupacion.equals(profile.idAgrupacion, ignoreCase = true) } ||
                agrupacionesList.value.any { it.idAgrupacion.equals(profile.idAgrupacion, ignoreCase = true) }
            if (!agrupacionValida) {
                networkErrorMessage.value = "El ID de Agrupación ingresado no es válido."
                return
            }
        }

        viewModelScope.launch {
            var finalProfileToSave = profile
            var isCloudRestored = false
            try {
                // Verificar en la nube si existe usando el email
                val cloudSenderista = withContext(Dispatchers.IO) {
                    com.gaspar.rutassendero.sync.CloudSyncManager.fetchSenderistaByEmail(profile.email.trim())
                }

                fun matchPhones(p1: String, p2: String): Boolean {
                    val clean1 = p1.filter { it.isDigit() }
                    val clean2 = p2.filter { it.isDigit() }
                    if (clean1.isEmpty() || clean2.isEmpty()) return false
                    return if (clean1.length >= 10 && clean2.length >= 10) {
                        clean1.takeLast(10) == clean2.takeLast(10)
                    } else {
                        clean1 == clean2
                    }
                }

                // Verifica que exista y que coincida el teléfono
                if (cloudSenderista != null && matchPhones(cloudSenderista.telefono, profile.telefono)) {
                    if (cloudSenderista.bloqueado) {
                        networkErrorMessage.value = "Este usuario se encuentra bloqueado. Actualización cancelada."
                        return@launch
                    }

                    val rolToUse = if (cloudSenderista.rol.equals("G", ignoreCase = true)) "G" else cloudSenderista.rol.uppercase()
                    val existingLocal = db.rutasDao().getSenderistaByEmailFlow(profile.email.trim()).first()

                    if (existingLocal == null) {
                        // REGLA: Al instalar la aplicación o primer registro, si el senderista ya existía en PostgreSQL,
                        // se conservan y recuperan todos sus datos de PostgreSQL (rol 'G', idBeacon, nombre, etc.)
                        finalProfileToSave = cloudSenderista.copy(
                            rol = rolToUse,
                            consentimientoTerminos = profile.consentimientoTerminos,
                            consentimientoFichaMedica = profile.consentimientoFichaMedica,
                            consentimientoGpsMultimedia = profile.consentimientoGpsMultimedia,
                            timestampConsentimientoUtc = profile.timestampConsentimientoUtc,
                            fupdate = System.currentTimeMillis()
                        )
                        isCloudRestored = true
                        Log.i("RutasViewModel", "Datos conservados y recuperados de PostgreSQL para ${cloudSenderista.email}, rol=$rolToUse")
                    } else {
                        // Ya existía localmente y está editando sus datos ("ya después lo puede cambiar")
                        finalProfileToSave = profile.copy(
                            rol = rolToUse,
                            idBeacon = if (profile.idBeacon.isNullOrBlank()) cloudSenderista.idBeacon else profile.idBeacon,
                            bloqueado = cloudSenderista.bloqueado,
                            minimoLongitud = cloudSenderista.minimoLongitud,
                            maxPrivadas = cloudSenderista.maxPrivadas,
                            fupdate = System.currentTimeMillis()
                        )
                        Log.i("RutasViewModel", "Actualización de perfil para ${profile.email}, conservando rol=$rolToUse")
                    }
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al verificar senderista en la nube: ${e.message}")
            }

            prefs.ownerEmail = finalProfileToSave.email
            _ownerEmailFlow.value = finalProfileToSave.email
            val existing = db.rutasDao().getSenderistaByTelefono(finalProfileToSave.telefono)
            val updatedProfile = if (finalProfileToSave.idBeacon.isNullOrBlank() && !existing?.idBeacon.isNullOrBlank()) {
                finalProfileToSave.copy(idBeacon = existing.idBeacon, fupdate = System.currentTimeMillis())
            } else {
                finalProfileToSave.copy(fupdate = System.currentTimeMillis())
            }
            db.rutasDao().insertSenderista(updatedProfile)
            if (isCloudRestored) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Se recuperaron tus datos existentes de la nube (Rol: ${updatedProfile.rol}).", Toast.LENGTH_LONG).show()
                }
            }
            try {
                com.gaspar.rutassendero.sync.CloudSyncManager.syncSenderista(updatedProfile)
                Log.d("RutasViewModel", "Sincronización automática de perfil completada con éxito.")
            } catch (e: Throwable) {
                Log.e("RutasViewModel", "Error al sincronizar perfil con PostgreSQL en la nube: ${e.message}", e)
                saveCrashLogToDownloads(app, e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Error de sincronización: ${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            // Sincronizar de forma transparente las rutas del senderista que están en la nube
            syncOwnRoutesFromCloud(updatedProfile)
        }
    }

    /**
     * Sincroniza las rutas pertenecientes al senderista (por email o teléfono) desde la nube:
     * - Si la ruta no existe localmente, la descarga y le asigna el teléfono actual del dueño.
     * - Si la ruta ya existe localmente con un teléfono anterior o vacío, la actualiza con el teléfono actual del dueño
     *   para que siempre aparezca en "Mis Rutas".
     */
    fun syncOwnRoutesFromCloud(ownerProfile: SenderistaEntity? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = ownerProfile ?: db.rutasDao().getSenderistaFlow().first() ?: return@launch
                val ownerEmail = profile.email.trim()
                val ownerPhone = profile.telefono.trim()

                fun matchPhones(p1: String, p2: String): Boolean {
                    val clean1 = p1.filter { it.isDigit() }
                    val clean2 = p2.filter { it.isDigit() }
                    if (clean1.isEmpty() || clean2.isEmpty()) return false
                    return if (clean1.length >= 10 && clean2.length >= 10) {
                        clean1.takeLast(10) == clean2.takeLast(10)
                    } else {
                        clean1 == clean2
                    }
                }

                // 1. Asegurar que las rutas de otros senderistas en la base local conserven su teléfono de creador y fechaImportacion
                val marcosRoutes = setOf("FECHI", "VILAC", "PRUEB", "VILLA", "JALAAUPKZJ", "RODRO", "PICACSA217", "CAMPA", "LACUMXP2KD", "RÍOSAN2J98", "COMALWZP79", "COLOMXPZFZ")
                val miboroRoutes = setOf("LACAM", "RANCH6Z45D")
                val lourdesRoutes = setOf("CASCADE4XL", "GASXXOPVK1", "RÍOSA8BHZH")

                val allLocal = db.rutasDao().getAllRutas()
                for (lr in allLocal) {
                    if (marcosRoutes.contains(lr.id) && (lr.telefono != "3121220253" || lr.fechaImportacion == null)) {
                        db.rutasDao().insertRuta(lr.copy(telefono = "3121220253", fechaImportacion = lr.fechaImportacion ?: System.currentTimeMillis()))
                    } else if (miboroRoutes.contains(lr.id) && (lr.telefono != "3122440776" || lr.fechaImportacion == null)) {
                        db.rutasDao().insertRuta(lr.copy(telefono = "3122440776", fechaImportacion = lr.fechaImportacion ?: System.currentTimeMillis()))
                    } else if (lourdesRoutes.contains(lr.id) && (lr.telefono != "3122716544" || lr.fechaImportacion == null)) {
                        db.rutasDao().insertRuta(lr.copy(telefono = "3122716544", fechaImportacion = lr.fechaImportacion ?: System.currentTimeMillis()))
                    }
                }

                // 2. Obtener rutas propias desde la nube (filtradas estrictamente por el teléfono del senderista actual)
                val cloudRoutes = com.gaspar.rutassendero.sync.CloudSyncManager.fetchCloudRoutes()
                val ownCloudRoutes = cloudRoutes.filter { routeMap ->
                    val phone = routeMap["telefono_creador"] as? String ?: ""
                    phone.isNotEmpty() && matchPhones(phone, ownerPhone)
                }

                var downloadedCount = 0
                for (routeMap in ownCloudRoutes) {
                    val routeId = routeMap["id"] as? String ?: continue
                    try {
                        val localRoute = db.rutasDao().getRutaById(routeId)
                        if (localRoute == null) {
                            Log.d("RutasViewModel", "Descargando de forma transparente ruta propia '${routeMap["nombre"]}'...")
                            com.gaspar.rutassendero.sync.CloudSyncManager.downloadRoute(
                                context = app,
                                db = db,
                                routeId = routeId,
                                targetPhone = ownerPhone
                            ) { progress ->
                                Log.d("RutasViewModel", "Descarga propia: $progress")
                            }
                            prefs.markRouteSyncedToCloud(routeId)
                            downloadedCount++
                        } else {
                            if (localRoute.telefono != ownerPhone) {
                                val updatedRoute = localRoute.copy(telefono = ownerPhone)
                                db.rutasDao().insertRuta(updatedRoute)
                                Log.d("RutasViewModel", "Actualizado teléfono de ruta local propia '${localRoute.nombre}' a '$ownerPhone'")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("RutasViewModel", "Error al sincronizar ruta propia individual '$routeId': ${e.message}", e)
                    }
                }
                if (downloadedCount > 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(app, "Sincronización de rutas: Se descargaron automáticamente $downloadedCount rutas tuyas desde la nube.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error general al descargar/sincronizar rutas propias de la nube: ${e.message}", e)
            }
        }
    }

    private fun saveCrashLogToDownloads(context: Context, t: Throwable) {
        viewModelScope.launch(Dispatchers.IO) {
            val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val logFileName = "tekins_sync_error_$dateStr.txt"
            val stackTraceString = Log.getStackTraceString(t)
            
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, logFileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            out.write(stackTraceString.toByteArray())
                        }
                    }
                } else {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val logFile = File(downloadsDir, logFileName)
                    logFile.writeText(stackTraceString)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Log de error guardado en Descargas: $logFileName", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al escribir log de crash", e)
            }
        }
    }

    var isCapturingPhotoForPoint = false
    var isFinRutaPending = false
    var locationSnapshotForPoint: Location? = null

    fun onPhotoCaptured(path: String) {
        capturedPhotoPath.value = path
    }

    fun clearCapturedPhoto() {
        capturedPhotoPath.value = null
    }

    fun onPhotoCaptureCancelled() {
        viewModelScope.launch {
            photoCaptureCancelledEvent.emit(Unit)
        }
    }

    // Flow for all routes in database
    val rutasState: StateFlow<List<RutaEntity>> = db.rutasDao().getAllRutasFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Combined Flow for points of the selected route with sequential segment distances (from previous point)
    val puntosListState: StateFlow<List<PuntoRutaUIModel>> = combine(
        selectedRoutePoints,
        uiLocation,
        isGuiding,
        targetWaypoint,
        isPointsListReversed
    ) { puntos, location, guiding, target, reversed ->
        val uiList = puntos.mapIndexed { index, punto ->
            val distance = if (index == 0) {
                0.0f // El primer punto es el inicio de la ruta, distancia 0
            } else {
                val prevPunto = puntos[index - 1]
                val loc1 = Location("").apply {
                    latitude = prevPunto.latitud
                    longitude = prevPunto.longitud
                }
                val loc2 = Location("").apply {
                    latitude = punto.latitud
                    longitude = punto.longitud
                }
                loc1.distanceTo(loc2)
            }
            PuntoRutaUIModel(punto, distance)
        }

        // Orden de la lista:
        // Por defecto, en modo guía el orden es inicial-final (reversed = false), en visor/trazar es final-inicial (reversed = true).
        // Las flechas arriba/abajo permiten al usuario cambiar este orden explícitamente.
        if (reversed) {
            uiList.reversed()
        } else {
            uiList
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addRuta(nombre: String, descripcion: String, lat: Double, lng: Double, publico: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = RutaEntity.generateRouteId(nombre)
            val senderista = db.rutasDao().getSenderistaFlow().first()
            val creatorPhone = senderista?.telefono ?: ""
            val codigo = if (!publico) {
                val allowedChars = ('A'..'Z') + ('0'..'9')
                (1..5).map { allowedChars.random() }.joinToString("")
            } else {
                ""
            }
            val nuevaRuta = RutaEntity(
                id = id,
                nombre = nombre,
                latitudOrig = lat,
                longitudOrig = lng,
                descripcion = descripcion,
                telefono = creatorPhone,
                publico = publico,
                codigo = codigo
            )
            db.rutasDao().insertRuta(nuevaRuta)
            // Auto-select the newly added route
            withContext(Dispatchers.Main) {
                GpsTrackingService.setSelectedRoute(nuevaRuta)
            }
        }
    }

    suspend fun hasActiveRoute(): Boolean {
        return db.rutasDao().getUnfinalizedRoutesCount() > 0
    }

    suspend fun getActiveRoute(): RutaEntity? {
        return db.rutasDao().getAllRutas().firstOrNull { r ->
            !db.rutasDao().isRutaFinalized(r.id)
        }
    }

    suspend fun isRutaFinalized(idRuta: String): Boolean {
        return db.rutasDao().isRutaFinalized(idRuta)
    }

    fun updateRuta(ruta: RutaEntity) {
        viewModelScope.launch {
            db.rutasDao().insertRuta(ruta)
            // If the updated route is currently selected, update the selected route state flow
            if (GpsTrackingService.selectedRoute.value?.id == ruta.id) {
                GpsTrackingService.setSelectedRoute(ruta)
            }
        }
    }

    fun selectRuta(ruta: RutaEntity?) {
        GpsTrackingService.setSelectedRoute(ruta)
        isPointsListReversed.value = (prefs.appRole != "guia")
        if (ruta == null) {
            triggerAutomaticCloudSync()
        } else {
            // Verificar si faltan fotos/puntos de la ruta y traerlos silenciosamente
            viewModelScope.launch {
                try {
                    val isCorrect = com.gaspar.rutassendero.sync.CloudSyncManager.verifyRouteDownloadedCorrectly(app, db, ruta.id)
                    val isSynced = prefs.isRouteSyncedToCloud(ruta.id)
                    if (!isCorrect && isSynced) {
                        Log.d("RutasViewModel", "selectRuta: Faltan fotos o puntos para la ruta ${ruta.id}. Descargando silenciosamente...")
                        com.gaspar.rutassendero.sync.CloudSyncManager.downloadRoute(
                            context = app,
                            db = db,
                            routeId = ruta.id,
                            onProgress = {}
                        )
                        // Modificar el teléfono de la ruta descargada por si acaso
                        val downloadedRoute = db.rutasDao().getRutaById(ruta.id)
                        if (downloadedRoute != null && downloadedRoute.telefono.isEmpty()) {
                            val invitation = db.rutasDao().getInvitacionesAceptadas().firstOrNull { it.idRuta == ruta.id }
                            if (invitation != null) {
                                db.rutasDao().insertRuta(downloadedRoute.copy(telefono = invitation.telefonoCreador))
                            }
                        }
                        
                        // Forzar refresco de puntos y traza en el servicio
                        if (GpsTrackingService.selectedRoute.value?.id == ruta.id) {
                            GpsTrackingService.setSelectedRoute(db.rutasDao().getRutaById(ruta.id))
                        }
                        Log.d("RutasViewModel", "selectRuta: Descarga silenciosa completada con éxito para la ruta ${ruta.id}")
                    }
                } catch (e: Exception) {
                    Log.e("RutasViewModel", "Error en descarga silenciosa selectRuta: ${e.message}", e)
                    val senderista = senderistaState.value ?: db.rutasDao().getSenderistaFlow().first()
                    val isOwnRoute = senderista != null && (ruta.telefono == senderista.telefono || ruta.telefono.isEmpty())
                    if (!isOwnRoute) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(app, "Error al descargar fotos de la ruta: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    fun addPuntoRuta(idRuta: String, desc: String, lat: Double, lng: Double, alt: Double, fotoPath: String?, esFinRuta: Boolean = false, estado: Int = 1) {
        viewModelScope.launch {
            val trimmedDesc = desc.trim()
            val isAutoDesc = trimmedDesc.isEmpty()
            
            val currentPoints = db.rutasDao().getPuntosForRuta(idRuta)
            val pointNumber = currentPoints.size + 1
            
            val initialDesc = if (isAutoDesc) {
                if (pointNumber == 1) {
                    "Inicio de Ruta"
                } else if (esFinRuta) {
                    "Fin de Ruta"
                } else {
                    "Faro #$pointNumber"
                }
            } else {
                trimmedDesc
            }
            
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val systemHasInternet = if (cm != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val network = cm.activeNetwork
                    val capabilities = cm.getNetworkCapabilities(network)
                    capabilities != null && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                             capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI))
                } else {
                    @Suppress("DEPRECATION")
                    val info = cm.activeNetworkInfo
                    @Suppress("DEPRECATION")
                    info != null && info.isConnected && 
                            (info.type == android.net.ConnectivityManager.TYPE_MOBILE || info.type == android.net.ConnectivityManager.TYPE_WIFI)
                }
            } else {
                false
            }

            val tieneConectividad = if (systemHasInternet) {
                withContext(Dispatchers.IO) {
                    try {
                        val socket = java.net.Socket()
                        val sockaddr = java.net.InetSocketAddress("8.8.8.8", 53)
                        socket.connect(sockaddr, 1500) // 1.5 segundos de timeout
                        socket.close()
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
            } else {
                false
            }

            val nuevoPunto = PuntoRutaEntity(
                idRuta = idRuta,
                descripcion = initialDesc,
                latitud = lat,
                longitud = lng,
                altitud = alt,
                fotoPath = fotoPath,
                esFinRuta = esFinRuta,
                tieneConectividad = tieneConectividad,
                estado = estado
            )
            db.rutasDao().insertPuntoRuta(nuevoPunto)
            
            // Si es el primer punto, también actualizar las coordenadas de origen de la ruta
            if (pointNumber == 1) {
                db.rutasDao().getRutaById(idRuta)?.let { ruta ->
                    val updatedRuta = ruta.copy(latitudOrig = lat, longitudOrig = lng)
                    db.rutasDao().insertRuta(updatedRuta)
                    // Forzar actualización reactiva en el servicio si está seleccionada
                    if (GpsTrackingService.selectedRoute.value?.id == idRuta) {
                        GpsTrackingService.setSelectedRoute(updatedRuta)
                    }
                }
                
                // Asegurar que el punto de inicio de la ruta esté registrado en la traza si ésta se encuentra vacía
                val currentTraza = db.rutasDao().getTrazaForRuta(idRuta)
                if (currentTraza.isEmpty()) {
                    val startTracePoint = TrazaRutaEntity(
                        idRuta = idRuta,
                        orden = 0,
                        latitud = lat,
                        longitud = lng,
                        altitud = alt
                    )
                    db.rutasDao().insertTrazaRuta(startTracePoint)
                    Log.d("RutasViewModel", "Se insertó el punto inicial en la traza: orden=0, lat=$lat, lng=$lng")
                }
            }
            
            if (esFinRuta) {
                // Asegurar que el punto final esté en la traza con el orden correcto
                val currentTraza = db.rutasDao().getTrazaForRuta(idRuta)
                val nextOrder = (currentTraza.lastOrNull()?.orden ?: -1) + 1
                val finalTracePoint = TrazaRutaEntity(
                    idRuta = idRuta,
                    orden = nextOrder,
                    latitud = lat,
                    longitud = lng,
                    altitud = alt
                )
                db.rutasDao().insertTrazaRuta(finalTracePoint)
                Log.d("RutasViewModel", "Se insertó el punto final en la traza: orden=$nextOrder, lat=$lat, lng=$lng")
                
                calculateAndSaveRouteStats(idRuta)
                triggerAutomaticCloudSync()
            }
            
            withContext(Dispatchers.Main) {
                if (esFinRuta) {
                    Toast.makeText(app, "Ruta finalizada con éxito.", Toast.LENGTH_SHORT).show()
                } else if (pointNumber == 1) {
                    Toast.makeText(app, "¡Punto inicial registrado! Por favor, registra el siguiente punto.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(app, "Punto #${pointNumber} registrado con éxito.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun calculateAndSaveRouteStats(idRuta: String) = withContext(Dispatchers.IO) {
        try {
            val ruta = db.rutasDao().getRutaById(idRuta) ?: return@withContext
            val puntos = db.rutasDao().getPuntosForRuta(idRuta)
            val traza = db.rutasDao().getTrazaForRuta(idRuta)

            // 1. Calcular distancias
            val beaconDist = RouteStatsCalculator.calculateBeaconDistance(puntos)
            val traceDist = RouteStatsCalculator.calculateTraceDistance(traza)

            // 2. Calcular tiempos
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            val firstPt = puntos.firstOrNull()
            val lastPt = puntos.find { it.esFinRuta } ?: puntos.lastOrNull()

            val inicioStr = firstPt?.fechaHora
            val finStr = lastPt?.fechaHora

            var duracion: Long = 0L
            if (inicioStr != null && finStr != null) {
                try {
                    val dateInicio = format.parse(inicioStr)
                    val dateFin = format.parse(finStr)
                    if (dateInicio != null && dateFin != null) {
                        duracion = (dateFin.time - dateInicio.time) / 1000L // en segundos
                    }
                } catch (e: Exception) {
                    Log.e("RutasViewModel", "Error al parsear fechas: ${e.message}")
                }
            }

            // 3. Calcular elevaciones
            val elevationStats = RouteStatsCalculator.calculateElevationStats(traza)

            // 3.5. Calcular conectividad
            val totalPuntos = puntos.size
            val puntosConConectividad = puntos.count { it.tieneConectividad }
            val porcentajeConectividad = if (totalPuntos > 0) {
                val rawPct = (puntosConConectividad.toFloat() / totalPuntos.toFloat()) * 100f
                kotlin.math.round(rawPct * 1000f) / 1000f
            } else {
                0.0f
            }

            // 4. Actualizar ruta en la base de datos con descripción formateada
            val senderistaName = db.rutasDao().getSenderistaFlow().first()?.nombre ?: ""
            val dateParts = ruta.fecha.split("/")
            val dateFormatted = if (dateParts.size == 3) {
                val day = dateParts[0]
                val month = dateParts[1]
                val year = dateParts[2]
                val shortYear = if (year.length == 4) year.substring(2) else year
                "$day/$month/$shortYear"
            } else {
                ruta.fecha
            }
            val distKm = traceDist.toDouble() / 1000.0
            val distStr = String.format(java.util.Locale("es", "ES"), "%.2f", distKm)
            val statusStr = "Finalizada"
            val newDescription = "autor ; $senderistaName\n$dateFormatted , $distStr Kms , $statusStr"

            val shareCode = if (ruta.codigo.isBlank()) {
                val allowedChars = ('A'..'Z') + ('0'..'9')
                (1..6).map { allowedChars.random() }.joinToString("")
            } else {
                ruta.codigo
            }

            val rutaActualizada = ruta.copy(
                descripcion = newDescription,
                distanciaGps = traceDist.toDouble(),
                distanciaBeacons = beaconDist.toDouble(),
                fechaHoraInicio = inicioStr,
                fechaHoraFin = finStr,
                duracionSegundos = duracion,
                desnivelPositivo = elevationStats.desnivelPositivo,
                desnivelNegativo = elevationStats.desnivelNegativo,
                contadorSubidas = elevationStats.contadorSubidas,
                contadorBajadas = elevationStats.contadorBajadas,
                conectividad = porcentajeConectividad,
                codigo = shareCode
            )
            db.rutasDao().insertRuta(rutaActualizada)
            
            // Forzar actualización reactiva en el servicio
            if (GpsTrackingService.selectedRoute.value?.id == ruta.id) {
                GpsTrackingService.setSelectedRoute(rutaActualizada)
            }
            
            Log.d("RutasViewModel", "Estadísticas de ruta actualizadas exitosamente: $rutaActualizada")
        } catch (e: Exception) {
            Log.e("RutasViewModel", "Error al calcular estadísticas de ruta: ${e.message}", e)
        }
    }

    fun updatePuntoRuta(punto: PuntoRutaEntity) {
        viewModelScope.launch {
            if (punto.esFinRuta) {
                // Desmarcar cualquier otro punto que estuviera marcado como fin de ruta
                val puntos = db.rutasDao().getPuntosForRuta(punto.idRuta)
                for (p in puntos) {
                    if (p.id != punto.id && p.esFinRuta) {
                        db.rutasDao().insertPuntoRuta(p.copy(esFinRuta = false))
                    }
                }
            }
            db.rutasDao().insertPuntoRuta(punto)
            // Si es fin de ruta, recalculamos estadísticas de la ruta
            if (punto.esFinRuta) {
                calculateAndSaveRouteStats(punto.idRuta)
                triggerAutomaticCloudSync()
            }
        }
    }

    fun appendDescriptionToLastPoint(idRuta: String, textToAdd: String) {
        viewModelScope.launch {
            val puntos = db.rutasDao().getPuntosForRuta(idRuta)
            val lastPoint = puntos.lastOrNull()
            if (lastPoint != null) {
                val currentDesc = lastPoint.descripcion.trim()
                val newDesc = if (currentDesc.isEmpty() || currentDesc.equals("Punto inicial", ignoreCase = true) || currentDesc.matches(Regex("Punto \\d+"))) {
                    textToAdd
                } else {
                    "$currentDesc. $textToAdd"
                }
                db.rutasDao().insertPuntoRuta(lastPoint.copy(descripcion = newDesc))
            }
        }
    }

    fun deleteRuta(ruta: RutaEntity) {
        viewModelScope.launch {
            val senderista = withContext(Dispatchers.IO) {
                db.rutasDao().getSenderistaFlow().first()
            }
            val userPhone = senderista?.telefono ?: ""
            val isOwnRoute = if (senderista != null) {
                ruta.fechaImportacion == null || matchPhones(ruta.telefono, userPhone) || ruta.telefono.isEmpty()
            } else {
                ruta.fechaImportacion == null || ruta.telefono.isEmpty()
            }

            val isSynced = prefs.isRouteSyncedToCloud(ruta.id)
            val hasNetwork = com.gaspar.rutassendero.utils.NetworkUtils.isNetworkAvailable(app)

            if (isOwnRoute && isSynced && !hasNetwork) {
                networkErrorMessage.value = "Para eliminar tu propia ruta de la nube, el dispositivo necesita tener conexión a Internet (Datos o WiFi)."
                return@launch
            }

            // 1. Obtener puntos antes de borrarlos para saber las rutas de las fotos
            val puntos = withContext(Dispatchers.IO) {
                db.rutasDao().getPuntosForRuta(ruta.id)
            }

            withContext(Dispatchers.IO) {
                db.rutasDao().deleteRuta(ruta)
                db.rutasDao().deletePuntosForRuta(ruta.id)
                db.rutasDao().deleteTrazaForRuta(ruta.id)
            }
            if (selectedRoute.value?.id == ruta.id) {
                GpsTrackingService.setSelectedRoute(null)
            }

            // También borrar la Invitación Aceptada si existe (y cancelar participación en nube si hay red)
            try {
                val invitaciones = withContext(Dispatchers.IO) { db.rutasDao().getInvitacionesAceptadas() }
                val matchingInv = invitaciones.find { it.idRuta == ruta.id }
                if (matchingInv != null) {
                    withContext(Dispatchers.IO) { db.rutasDao().deleteInvitacionAceptada(matchingInv) }
                    val currentSenderista = withContext(Dispatchers.IO) { db.rutasDao().getSenderistaFlow().first() }
                    if (currentSenderista != null && hasNetwork) {
                        withContext(Dispatchers.IO) {
                            com.gaspar.rutassendero.sync.CloudSyncManager.deleteParticipationFromCloud(
                                idInvitacion = matchingInv.idInvitacion,
                                emailSenderista = currentSenderista.email
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("RutasViewModel", "No se pudo cancelar la participacion de la invitacion al borrar la ruta: ${e.message}")
            }
            
            // Borrar de Neon Postgres, Cloudinary y script de Drive solo si es la propia ruta del senderista
            try {
                if (isOwnRoute && senderista != null && hasNetwork) {
                    // 1. Borrar en PostgreSQL (en la nube)
                    withContext(Dispatchers.IO) {
                        com.gaspar.rutassendero.sync.CloudSyncManager.deleteRouteFromCloud(ruta.id, senderista.email, senderista.telefono)
                    }
                    Log.d("RutasViewModel", "Ruta ${ruta.id} eliminada también de la nube PostgreSQL.")
                    
                    // 2. Borrar las fotos de la nube (Cloudinary)
                    withContext(Dispatchers.IO) {
                        for (punto in puntos) {
                            if (!punto.fotoPath.isNullOrEmpty() && punto.fotoPath.startsWith("http")) {
                                com.gaspar.rutassendero.sync.CloudSyncManager.deleteFromCloudinary(punto.fotoPath)
                            }
                        }
                    }
                    Log.d("RutasViewModel", "Fotos eliminadas de la nube Cloudinary.")
                }

                // 3. Borrar las fotos físicas en el teléfono y recuerdos (I/O en Dispatchers.IO) - Para todos los casos
                withContext(Dispatchers.IO) {
                    for (punto in puntos) {
                        if (!punto.fotoPath.isNullOrEmpty()) {
                            // Intento 1: Ruta local directa
                            val file = File(punto.fotoPath)
                            if (file.exists()) {
                                file.delete()
                            }
                            // Intento 2: Si era URL, buscar en internal filesDir
                            if (punto.fotoPath.startsWith("http")) {
                                val fileName = punto.fotoPath.substringAfterLast("/")
                                val internalFile = File(app.filesDir, fileName)
                                if (internalFile.exists()) {
                                    internalFile.delete()
                                }
                            }
                        }
                    }
                    val rootDir = app.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                    val safeRouteName = ruta.nombre.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val souvenirDir = File(rootDir, "TekinsRutas/$safeRouteName")
                    if (souvenirDir.exists()) {
                        souvenirDir.deleteRecursively()
                    }
                }
                Log.d("RutasViewModel", "Fotos locales eliminadas de la ruta ${ruta.id}.")

                if (isOwnRoute && senderista != null && hasNetwork) {
                    // 4. Borrar únicamente el script SQL en Google Drive (conservando fotos)
                    val gAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(app)
                    val account = gAccount?.account
                    if (account != null) {
                        try {
                            val token = GoogleDriveSyncManager.getAccessToken(app, account)
                            val routeFolder = GoogleDriveSyncManager.findFile(token, ruta.nombre, "application/vnd.google-apps.folder")
                            if (routeFolder != null) {
                                val routeFiles = GoogleDriveSyncManager.listFilesInFolder(token, routeFolder.id)
                                val sqlFiles = routeFiles.filter { it.name.endsWith(".sql") }
                                for (sqlFile in sqlFiles) {
                                    GoogleDriveSyncManager.deleteFile(token, sqlFile.id)
                                    Log.d("RutasViewModel", "Script SQL ${sqlFile.name} eliminado de Google Drive.")
                                }
                            } else {
                                Log.d("RutasViewModel", "No se encontró la carpeta de la ruta en Google Drive para eliminar el script.")
                            }
                        } catch (e: Exception) {
                            Log.w("RutasViewModel", "No se pudo eliminar el script de Google Drive: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("RutasViewModel", "No se pudo eliminar la ruta de la nube: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                val label = if (isOwnRoute) "Tu ruta" else "Ruta descargada"
                Toast.makeText(app, "$label '${ruta.nombre}' ha sido eliminada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun deleteRutaLocalOnly(ruta: RutaEntity) {
        withContext(Dispatchers.IO) {
            val puntos = db.rutasDao().getPuntosForRuta(ruta.id)
            db.rutasDao().deleteRuta(ruta)
            db.rutasDao().deletePuntosForRuta(ruta.id)
            db.rutasDao().deleteTrazaForRuta(ruta.id)
            if (selectedRoute.value?.id == ruta.id) {
                withContext(Dispatchers.Main) {
                    GpsTrackingService.setSelectedRoute(null)
                }
            }
            for (punto in puntos) {
                if (!punto.fotoPath.isNullOrEmpty()) {
                    val file = File(punto.fotoPath)
                    if (file.exists()) file.delete()
                    if (punto.fotoPath.startsWith("http")) {
                        val fileName = punto.fotoPath.substringAfterLast("/")
                        val internalFile = File(app.filesDir, fileName)
                        if (internalFile.exists()) internalFile.delete()
                    }
                }
            }
            val rootDir = app.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val safeRouteName = ruta.nombre.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val souvenirDir = File(rootDir, "TekinsRutas/$safeRouteName")
            if (souvenirDir.exists()) souvenirDir.deleteRecursively()
        }
    }

    fun checkAndCleanOtherRoutes() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Obtener todas las rutas en la nube (PostgreSQL)
                val cloudRoutes = com.gaspar.rutassendero.sync.CloudSyncManager.fetchCloudRoutes()
                val cloudRouteIds = cloudRoutes.mapNotNull { it["id"] as? String }.toSet()

                // 2. Obtener senderista para saber cuáles son las "otras rutas"
                val senderista = db.rutasDao().getSenderistaFlow().first()
                val userPhone = senderista?.telefono ?: ""

                // 3. Obtener todas las rutas locales
                val localRutas = db.rutasDao().getAllRutas()

                // 4. Filtrar las que son estrictamente "otras rutas" importadas (fechaImportacion != null)
                // JAMÁS considerar como ajena una ruta local creada en este dispositivo (fechaImportacion == null)
                val otherRutas = localRutas.filter {
                    val isOwn = it.fechaImportacion == null || matchPhones(it.telefono, userPhone) || it.telefono.isEmpty()
                    !isOwn && it.fechaImportacion != null
                }

                // 5. Borrar las que no estén en la nube
                for (ruta in otherRutas) {
                    if (!cloudRouteIds.contains(ruta.id)) {
                        Log.d("RutasViewModel", "Ruta ajena huérfana detectada: ${ruta.nombre} (ID: ${ruta.id}). Borrando localmente...")
                        deleteRutaLocalOnly(ruta)
                    }
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al verificar/limpiar otras rutas no existentes en la nube: ${e.message}", e)
            }
        }
    }

    private fun cleanupPastOutings(userPhone: String) {
        if (userPhone.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val acceptedList = db.rutasDao().getInvitacionesAceptadas()
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val now = System.currentTimeMillis()
                val threeDaysInMillis = 3L * 24L * 60L * 60L * 1000L

                for (invitation in acceptedList) {
                    val dateStr = invitation.fechaHoraSalida
                    if (dateStr.isNotEmpty()) {
                        val date = sdf.parse(dateStr)
                        if (date != null && (now - date.time) >= threeDaysInMillis) {
                            val isOwnRoute = invitation.telefonoCreador == userPhone
                            if (!isOwnRoute) {
                                db.rutasDao().deleteInvitacionAceptada(invitation)
                                Log.d("RutasViewModel", "Cleanup: Deleted invitation accepted ${invitation.idInvitacion} (older than 3 days)")

                                val localRoute = db.rutasDao().getRutaById(invitation.idRuta)
                                val isLocalRouteOwn = localRoute?.let { it.fechaImportacion == null || matchPhones(it.telefono, userPhone) || it.telefono.isEmpty() } ?: true
                                if (localRoute != null && !isLocalRouteOwn && localRoute.fechaImportacion != null) {
                                    db.rutasDao().deleteRuta(localRoute)
                                    db.rutasDao().deletePuntosForRuta(localRoute.id)
                                    db.rutasDao().deleteTrazaForRuta(localRoute.id)
                                    Log.d("RutasViewModel", "Cleanup: Deleted local route ${localRoute.id} associated with past outing")
                                    
                                    if (selectedRoute.value?.id == localRoute.id) {
                                        GpsTrackingService.setSelectedRoute(null)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error doing automatic cleanup of outings: ${e.message}", e)
            }
        }
    }

    fun deleteExpiredDownloadedRoutes() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val senderista = db.rutasDao().getSenderistaFlow().first()
                val userPhone = senderista?.telefono ?: ""
                val allLocalRoutes = db.rutasDao().getAllRutas()
                val threeDaysInMillis = 3 * 24 * 3600 * 1000L
                val fortyEightHoursInMillis = 48 * 3600 * 1000L
                val currentTime = System.currentTimeMillis()
                val sdfSalida = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                
                val invitaciones = db.rutasDao().getInvitacionesAceptadas()
                val rutasDeSalidaMap = invitaciones.associateBy { it.idRuta }
                
                for (ruta in allLocalRoutes) {
                    val isOwnRoute = ruta.fechaImportacion == null || (if (userPhone.isNotEmpty()) {
                        matchPhones(ruta.telefono, userPhone) || ruta.telefono.isEmpty()
                    } else {
                        ruta.telefono.isEmpty()
                    })
                    
                    if (!isOwnRoute && ruta.fechaImportacion != null) {
                        var shouldDelete = false
                        val invitacion = rutasDeSalidaMap[ruta.id]
                        
                        if (invitacion != null) {
                            try {
                                val fechaSalida = sdfSalida.parse(invitacion.fechaHoraSalida)
                                if (fechaSalida != null && (currentTime - fechaSalida.time) > fortyEightHoursInMillis) {
                                    shouldDelete = true
                                }
                            } catch (e: Exception) {
                                if (ruta.fechaImportacion != null && (currentTime - ruta.fechaImportacion) > threeDaysInMillis) {
                                    shouldDelete = true
                                }
                            }
                        } else {
                            if (ruta.fechaImportacion != null && (currentTime - ruta.fechaImportacion) > threeDaysInMillis) {
                                shouldDelete = true
                            }
                        }
                        
                        if (shouldDelete) {
                            Log.d("RutasViewModel", "Eliminando ruta expirada de otro senderista: ${ruta.nombre} (${ruta.id})")
                        
                        // 1. Obtener puntos antes de borrar
                        val puntos = db.rutasDao().getPuntosForRuta(ruta.id)
                        
                        // 2. Borrar localmente de Room
                        db.rutasDao().deleteRuta(ruta)
                        db.rutasDao().deletePuntosForRuta(ruta.id)
                        db.rutasDao().deleteTrazaForRuta(ruta.id)
                        if (selectedRoute.value?.id == ruta.id) {
                            withContext(Dispatchers.Main) {
                                GpsTrackingService.setSelectedRoute(null)
                            }
                        }
                        
                        // 3. Borrar fotos locales
                        for (punto in puntos) {
                            if (!punto.fotoPath.isNullOrEmpty()) {
                                val file = java.io.File(punto.fotoPath)
                                if (file.exists()) {
                                    file.delete()
                                }
                                if (punto.fotoPath.startsWith("http")) {
                                    val fileName = punto.fotoPath.substringAfterLast("/")
                                    val internalFile = java.io.File(app.filesDir, fileName)
                                    if (internalFile.exists()) {
                                        internalFile.delete()
                                    }
                                }
                            }
                        }
                        val rootDir = app.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                        val safeRouteName = ruta.nombre.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val souvenirDir = java.io.File(rootDir, "TekinsRutas/$safeRouteName")
                        if (souvenirDir.exists()) {
                            souvenirDir.deleteRecursively()
                        }
                        
                        // 4. Borrar únicamente el script SQL en Google Drive (conservando fotos)
                        val gAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(app)
                        val account = gAccount?.account
                        if (account != null) {
                            try {
                                val token = GoogleDriveSyncManager.getAccessToken(app, account)
                                val routeFolder = GoogleDriveSyncManager.findFile(token, ruta.nombre, "application/vnd.google-apps.folder")
                                if (routeFolder != null) {
                                    val routeFiles = GoogleDriveSyncManager.listFilesInFolder(token, routeFolder.id)
                                    val sqlFiles = routeFiles.filter { it.name.endsWith(".sql") }
                                    for (sqlFile in sqlFiles) {
                                        GoogleDriveSyncManager.deleteFile(token, sqlFile.id)
                                        Log.d("RutasViewModel", "Script SQL ${sqlFile.name} eliminado de Google Drive para ruta expirada.")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("RutasViewModel", "No se pudo eliminar el script de Google Drive para la ruta expirada: ${e.message}")
                            }
                        }
                    }
                }
                } // closes for (ruta in allLocalRoutes)
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error en deleteExpiredDownloadedRoutes: ${e.message}", e)
            }
        }
    }

    fun deletePuntoRuta(punto: PuntoRutaEntity) {
        if (!com.gaspar.rutassendero.utils.NetworkUtils.isNetworkAvailable(app)) {
            networkErrorMessage.value = "Para eliminar un punto, el dispositivo necesita tener conexión a Internet (Datos o WiFi)."
            return
        }
        viewModelScope.launch {
            db.rutasDao().deletePuntoRuta(punto)
        }
    }

    suspend fun getPuntosForRuta(idRuta: String): List<PuntoRutaEntity> {
        return db.rutasDao().getPuntosForRuta(idRuta)
    }

    suspend fun isLocationNearRoute(idRuta: String, lat: Double, lng: Double, thresholdMeters: Double = 100.0): Boolean {
        val puntos = getPuntosForRuta(idRuta)
        val userLoc = Location("").apply {
            latitude = lat
            longitude = lng
        }
        for (pt in puntos) {
            val ptLoc = Location("").apply {
                latitude = pt.latitud
                longitude = pt.longitud
            }
            if (userLoc.distanceTo(ptLoc) <= thresholdMeters) {
                return true
            }
        }
        return false
    }

    suspend fun resumeActiveRoute(idRuta: String, forward: Boolean) {
        val ruta = db.rutasDao().getRutaById(idRuta)
        if (ruta != null) {
            prefs.appRole = "guia"
            GpsTrackingService.setSelectedRoute(ruta)
            // startGuidingToDestination needs `toStart` boolean, which is the opposite of forward
            GpsTrackingService.startGuidingToDestination(toStart = !forward)
        }
    }

    fun startGuiding(target: PuntoRutaEntity?, forward: Boolean) {
        GpsTrackingService.startGuiding(target, forward)
    }

    fun startGuidingToDestination(toStart: Boolean) {
        GpsTrackingService.startGuidingToDestination(toStart)
    }

    fun stopGuiding() {
        GpsTrackingService.stopGuiding()
    }

    fun setTargetWaypoint(target: PuntoRutaEntity?) {
        GpsTrackingService.setTargetWaypoint(target)
    }

    fun advanceWaypointManually() {
        GpsTrackingService.advanceWaypointManually()
    }

    fun updateSettings(distance: Int, showInactive: Boolean, preferredMap: String, enableBeeps: Boolean, arrivalTolerance: Int, tapDistanceLimit: Int, appRole: String, emergencyTimeoutMinutes: Int) {
        prefs.alertDistance = distance
        prefs.showInactive = showInactive
        prefs.preferredMap = preferredMap
        prefs.enableBeeps = enableBeeps
        prefs.arrivalTolerance = arrivalTolerance
        prefs.tapDistanceLimit = tapDistanceLimit
        prefs.emergencyTimeoutMinutes = emergencyTimeoutMinutes
        prefs.appRole = appRole
    }

    // --- PLAY SOUND PATTERNS FOR COMMUNICATION & EMERGENCY ---

    fun playShortBeep() {
        GpsTrackingService.playSoundPattern(listOf(200 to 100))
    }

    fun playLongBeep() {
        GpsTrackingService.playSoundPattern(listOf(2000 to 100))
    }

    fun playTwoShortBeeps() {
        GpsTrackingService.playSoundPattern(listOf(200 to 100, 200 to 100))
    }

    fun playEmergencyHelp() {
        GpsTrackingService.startEmergencyPattern(listOf(800 to 400, 800 to 400, 800 to 400, 800 to 400, 800 to 400))
    }

    fun playEmergencyAlert() {
        GpsTrackingService.startEmergencyPattern(listOf(200 to 200, 200 to 200, 200 to 200, 200 to 200, 200 to 200, 200 to 200, 200 to 200, 200 to 200, 200 to 200, 200 to 200))
    }

    fun playSosSignal() {
        GpsTrackingService.startEmergencyPattern(listOf(
            200 to 100, 200 to 100, 200 to 200,
            600 to 100, 600 to 100, 600 to 200,
            200 to 100, 200 to 100, 200 to 100
        ))
    }

    fun stopEmergencySound() {
        GpsTrackingService.stopEmergencyPattern()
    }
    
    fun acknowledgeEmergency(idBeacon: String) {
        GpsTrackingService.acknowledgeEmergency(idBeacon)
    }

    // --- DICTADO POR VOZ COMPARTIDO PARA DIÃ LOGOS ---
    val dictatedText = MutableStateFlow<String?>(null)

    fun onTextDictated(text: String) {
        dictatedText.value = text
    }

    fun clearDictatedText() {
        dictatedText.value = null
    }

    // --- ESTADOS Y MÃ‰TODOS DE CLOUD SYNC (PostgreSQL + Cloudinary) ---
    private val _cloudSyncState = MutableStateFlow<CloudSyncState>(CloudSyncState.Idle)
    val cloudSyncState = _cloudSyncState.asStateFlow()

    fun resetCloudSyncState() {
        _cloudSyncState.value = CloudSyncState.Idle
    }



    private val _cloudRoutes = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val cloudRoutes = _cloudRoutes.asStateFlow()

    private val _loadingCloudRoutes = MutableStateFlow(false)
    val loadingCloudRoutes = _loadingCloudRoutes.asStateFlow()

    fun fetchCloudRoutes() {
        viewModelScope.launch {
            _loadingCloudRoutes.value = true
            try {
                val routes = com.gaspar.rutassendero.sync.CloudSyncManager.fetchCloudRoutes()
                _cloudRoutes.value = routes
            } catch (e: Throwable) {
                Log.e("RutasViewModel", "Error al consultar rutas en la nube: ${e.message}", e)
                saveCrashLogToDownloads(app, e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Error al obtener rutas: ${e.javaClass.simpleName}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                _loadingCloudRoutes.value = false
            }
        }
    }

    fun downloadRouteFromCloud(context: Context, routeId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            _cloudSyncState.value = CloudSyncState.CheckingConnection
            try {
                com.gaspar.rutassendero.sync.CloudSyncManager.downloadRoute(context, db, routeId) { progress ->
                    _cloudSyncState.value = CloudSyncState.Syncing(progress)
                }
                _cloudSyncState.value = CloudSyncState.Success
                onSuccess()
            } catch (e: Throwable) {
                val errMsg = "${e.javaClass.simpleName}: ${e.message ?: "Error al descargar la ruta"}"
                _cloudSyncState.value = CloudSyncState.Error(errMsg)
                onError(errMsg)
                Log.e("RutasViewModel", "Error al descargar ruta: ${e.message}", e)
                saveCrashLogToDownloads(context, e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error de descarga: $errMsg", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun recoverPhotoForWaypoint(
        context: Context,
        punto: PuntoRutaEntity,
        onFinished: (Boolean, String?, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val details = com.gaspar.rutassendero.sync.CloudSyncManager.recoverWaypointPhoto(
                    context = context,
                    routeId = punto.idRuta,
                    latitud = punto.latitud,
                    longitud = punto.longitud,
                    fechaHora = punto.fechaHora
                )
                if (details.localPath != null) {
                    val updatedPunto = punto.copy(fotoPath = details.localPath)
                    withContext(Dispatchers.IO) {
                        db.rutasDao().insertPuntoRuta(updatedPunto)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Foto recuperada con éxito de la nube.", Toast.LENGTH_SHORT).show()
                        onFinished(true, null, details.localPath)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        val cloudUrl = details.cloudUrl
                        val msg = if (cloudUrl.isNullOrEmpty()) {
                            "La foto no está registrada en la nube para este punto (fotoPath en la BD es nula o vacía)."
                        } else if (!cloudUrl.startsWith("http")) {
                            "La foto no se subió a Cloudinary durante la sincronización inicial.\nEn la base de datos se guardó la ruta local del creador:\n\n$cloudUrl"
                        } else {
                            "No se pudo descargar el archivo desde Cloudinary. Posiblemente fue eliminada de la nube o hay un problema de red.\n\nURL en BD:\n$cloudUrl"
                        }
                        onFinished(false, msg, null)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onFinished(false, "Error al recuperar la foto: ${e.message}", null)
                }
            }
        }
    }

    /**
     * Escanea el almacenamiento interno de la app buscando fotos huérfanas tomadas durante las rutas
     * y las vuelve a vincular a los puntos de ruta correspondientes si su fotoPath es nula, vacía o inexistente.
     */
    suspend fun repairLocalRoutePhotos(context: Context, targetRouteId: String? = null): Int = withContext(Dispatchers.IO) {
        var repairedCount = 0
        try {
            val routesToCheck = if (targetRouteId != null) {
                listOfNotNull(db.rutasDao().getRutaById(targetRouteId))
            } else {
                db.rutasDao().getAllRutas()
            }

            val filesDir = context.filesDir ?: return@withContext 0
            val allPhotos = filesDir.listFiles { file ->
                file.isFile && (file.extension.equals("jpg", true) || file.extension.equals("jpeg", true)) && file.length() > 0
            } ?: emptyArray()

            if (allPhotos.isEmpty()) return@withContext 0

            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

            data class PhotoCandidate(val file: File, val timestamp: Long)
            val candidates = allPhotos.map { file ->
                val timeFromName = if (file.name.startsWith("foto_")) {
                    file.name.removePrefix("foto_").substringBefore(".").toLongOrNull()
                } else null
                PhotoCandidate(file, timeFromName ?: file.lastModified())
            }.sortedBy { it.timestamp }

            for (ruta in routesToCheck) {
                val puntos = db.rutasDao().getPuntosForRuta(ruta.id)
                var routeNeedsResync = false

                for (punto in puntos) {
                    val currentPath = punto.fotoPath
                    val fileExists = !currentPath.isNullOrEmpty() && (
                        File(currentPath).exists() ||
                        File(context.filesDir, currentPath.substringAfterLast("/")).exists()
                    )

                    if (!fileExists) {
                        // 1. Buscar coincidencia exacta por ID canónico: <routeId>_<puntoId>.jpg
                        val exactNameMatch = allPhotos.find { it.name.startsWith("${ruta.id}_${punto.id}") }
                        if (exactNameMatch != null) {
                            db.rutasDao().insertPuntoRuta(punto.copy(fotoPath = exactNameMatch.absolutePath))
                            repairedCount++
                            routeNeedsResync = true
                            continue
                        }

                        // 2. Si el punto tiene fechaHora, buscar la foto más cercana en el tiempo
                        val puntoMillis = try {
                            if (punto.fechaHora.isNotEmpty()) sdf.parse(punto.fechaHora)?.time ?: 0L else 0L
                        } catch (e: Exception) {
                            0L
                        }

                        if (puntoMillis > 0L) {
                            // Buscar foto con diferencia menor a 30 minutos
                            val bestMatch = candidates.minByOrNull { kotlin.math.abs(it.timestamp - puntoMillis) }
                            if (bestMatch != null && kotlin.math.abs(bestMatch.timestamp - puntoMillis) < 1800000L) {
                                db.rutasDao().insertPuntoRuta(punto.copy(fotoPath = bestMatch.file.absolutePath))
                                repairedCount++
                                routeNeedsResync = true
                                continue
                            }
                        }
                    }
                }

                if (routeNeedsResync && prefs.isRouteSyncedToCloud(ruta.id)) {
                    prefs.clearRouteSyncedToCloud(ruta.id)
                }
            }

            if (repairedCount > 0) {
                Log.d("RutasViewModel", "repairLocalRoutePhotos: Se repararon y vincularon $repairedCount fotos locales.")
            }
        } catch (e: Exception) {
            Log.e("RutasViewModel", "Error al reparar fotos locales: ${e.message}", e)
        }
        repairedCount
    }

    fun checkAndResolveRoute(
        context: Context,
        routeId: String,
        onAlreadyCorrect: () -> Unit,
        onResolveNeeded: () -> Unit
    ) {
        viewModelScope.launch {
            // Reparar fotos huérfanas en disco antes de verificar
            repairLocalRoutePhotos(context, routeId)
            val isCorrect = com.gaspar.rutassendero.sync.CloudSyncManager.verifyRouteDownloadedCorrectly(context, db, routeId)
            val isSynced = prefs.isRouteSyncedToCloud(routeId)
            if (isCorrect || !isSynced) {
                withContext(Dispatchers.Main) {
                    onAlreadyCorrect()
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResolveNeeded()
                }
            }
        }
    }

    fun triggerAutomaticCloudSync() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = app.applicationContext
            repairLocalRoutePhotos(context)
            if (!com.gaspar.rutassendero.utils.NetworkUtils.isNetworkAvailable(context) ||
                !com.gaspar.rutassendero.utils.NetworkUtils.hasRealInternetConnection()) {
                Log.d("RutasViewModel", "AutoSync: No real network connection available. Skipping sync.")
                return@launch
            }

            try {
                val senderista = db.rutasDao().getSenderistaFlow().first()
                val userPhone = senderista?.telefono ?: ""

                // 1. Sincronizar rutas finalizadas no respaldadas (solo rutas propias)
                val allRoutes = db.rutasDao().getAllRutas()
                for (ruta in allRoutes) {
                    val isFinalized = db.rutasDao().isRutaFinalized(ruta.id)
                    val isSynced = prefs.isRouteSyncedToCloud(ruta.id)
                    val isOwn = matchPhones(ruta.telefono, userPhone) || (ruta.fechaImportacion == null && ruta.telefono.isEmpty())
                    if (isFinalized && !isSynced && isOwn) {
                        Log.d("RutasViewModel", "AutoSync: Syncing finalized route '${ruta.nombre}' (${ruta.id}) to cloud...")
                        com.gaspar.rutassendero.sync.CloudSyncManager.syncRoute(context, db, ruta.id) { progress ->
                            Log.d("RutasViewModel", "AutoSync Progress for '${ruta.nombre}': $progress")
                        }
                        prefs.markRouteSyncedToCloud(ruta.id)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Se sincronizó automáticamente la ruta '${ruta.nombre}' y sus fotos con la nube.", Toast.LENGTH_LONG).show()
                        }
                    }
                }

                // 2. Sincronizar participaciones de salidas pendientes
                if (senderista != null) {
                    val pendingOutings = db.rutasDao().getInvitacionesAceptadas().filter { !it.sincronizadoConNube }
                    for (outing in pendingOutings) {
                        try {
                            com.gaspar.rutassendero.sync.CloudSyncManager.registerParticipationInCloud(
                                idInvitacion = outing.idInvitacion,
                                emailSenderista = senderista.email,
                                telefonoSenderista = senderista.telefono,
                                cooperacion = outing.cooperacionRealizada,
                                fechaCooperacionStr = outing.fechaCooperacionRealizada,
                                tipoRuta = outing.tipoRuta
                            )
                            db.rutasDao().insertInvitacionAceptada(outing.copy(sincronizadoConNube = true))
                            Log.d("RutasViewModel", "AutoSync: Sincronizada participación de salida ${outing.idInvitacion} con éxito.")
                        } catch (ex: Exception) {
                            Log.w("RutasViewModel", "AutoSync: No se pudo sincronizar participación de salida ${outing.idInvitacion}: ${ex.message}")
                        }
                    }
                }

                // 3. Verificar si el senderista sigue registrado en participantes_invitacion
                if (senderista != null) {
                    verifyLocalOutingsWithCloud(senderista)
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "AutoSync failed: ${e.message}", e)
            }
        }
    }

    fun triggerManualCloudSync(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 0. Reparar y revincular fotos huérfanas antes de la sincronización
                val repaired = repairLocalRoutePhotos(context)
                if (repaired > 0) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Se recuperaron y vincularon $repaired fotos locales.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                // Comprobar si hay conexión real a Internet
                if (!com.gaspar.rutassendero.utils.NetworkUtils.isNetworkAvailable(context) ||
                    !com.gaspar.rutassendero.utils.NetworkUtils.hasRealInternetConnection()) {
                    withContext(Dispatchers.Main) {
                        val msg = if (repaired > 0) {
                            "Fotos locales recuperadas ($repaired). Conéctate a Internet para sincronizar con la nube."
                        } else {
                            "Sin conexión a Internet. Conéctate a una red con datos para sincronizar."
                        }
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }

                val senderista = db.rutasDao().getSenderistaFlow().first()
                val userPhone = senderista?.telefono ?: ""

                // 1. Sincronizar rutas finalizadas no respaldadas (solo rutas propias)
                val allRoutes = db.rutasDao().getAllRutas()
                var syncedAny = false
                
                for (ruta in allRoutes) {
                    val isFinalized = db.rutasDao().isRutaFinalized(ruta.id)
                    val isSynced = prefs.isRouteSyncedToCloud(ruta.id)
                    val isOwn = matchPhones(ruta.telefono, userPhone) || (ruta.fechaImportacion == null && ruta.telefono.isEmpty())
                    if (isFinalized && !isSynced && isOwn) {
                        Log.d("RutasViewModel", "ManualSync: Syncing finalized route '${ruta.nombre}' (${ruta.id}) to cloud...")
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Sincronizando ruta '${ruta.nombre}'...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        com.gaspar.rutassendero.sync.CloudSyncManager.syncRoute(context, db, ruta.id) { progress ->
                            Log.d("RutasViewModel", "ManualSync Progress for '${ruta.nombre}': $progress")
                        }
                        prefs.markRouteSyncedToCloud(ruta.id)
                        syncedAny = true
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "¡Ruta '${ruta.nombre}' sincronizada con éxito!", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }

                if (!syncedAny) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Todas tus rutas finalizadas ya están sincronizadas.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                // 2. Sincronizar participaciones de salidas pendientes
                if (senderista != null) {
                    val pendingOutings = db.rutasDao().getInvitacionesAceptadas().filter { !it.sincronizadoConNube }
                    for (outing in pendingOutings) {
                        try {
                            com.gaspar.rutassendero.sync.CloudSyncManager.registerParticipationInCloud(
                                idInvitacion = outing.idInvitacion,
                                emailSenderista = senderista.email,
                                telefonoSenderista = senderista.telefono,
                                cooperacion = outing.cooperacionRealizada,
                                fechaCooperacionStr = outing.fechaCooperacionRealizada,
                                tipoRuta = outing.tipoRuta
                            )
                            db.rutasDao().insertInvitacionAceptada(outing.copy(sincronizadoConNube = true))
                        } catch (ex: Exception) {
                            Log.w("RutasViewModel", "ManualSync: No se pudo sincronizar participación de salida ${outing.idInvitacion}: ${ex.message}")
                        }
                    }
                    verifyLocalOutingsWithCloud(senderista)
                }
            } catch (e: Exception) {
                val fullMsg = if (e.cause != null) "${e.message}\n\nCausa:\n${e.cause?.message}" else e.message ?: "Error desconocido"
                Log.e("RutasViewModel", "ManualSync failed: $fullMsg", e)
                try {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val errorFile = java.io.File(downloadsDir, "Error_Sincronizacion_Rutas.txt")
                    errorFile.writeText(fullMsg)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Error guardado en descargas: Error_Sincronizacion_Rutas.txt", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (ioe: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Error al sincronizar (y al guardar txt): $fullMsg", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // --- ESTADOS Y MÃ‰TODOS DE GOOGLE DRIVE SYNC ---

    private val _driveSyncState = MutableStateFlow<DriveSyncState>(DriveSyncState.Idle)
    val driveSyncState = _driveSyncState.asStateFlow()

    fun resetSyncState() {
        _driveSyncState.value = DriveSyncState.Idle
    }

    fun clearLocalPoints(route: RutaEntity) {
        viewModelScope.launch {
            db.rutasDao().deletePuntosForRuta(route.id)
            // Forzar actualización reactiva en el servicio
            if (GpsTrackingService.selectedRoute.value?.id == route.id) {
                GpsTrackingService.setSelectedRoute(route)
            }
            Toast.makeText(app, "Se liberó espacio: puntos de ruta locales eliminados.", Toast.LENGTH_LONG).show()
        }
    }

    fun clearLocalTrace(route: RutaEntity) {
        viewModelScope.launch {
            db.rutasDao().deleteTrazaForRuta(route.id)
            // Forzar actualización reactiva en el servicio
            if (GpsTrackingService.selectedRoute.value?.id == route.id) {
                GpsTrackingService.setSelectedRoute(route)
            }
            Toast.makeText(app, "Se liberó espacio: traza de ruta local eliminada.", Toast.LENGTH_LONG).show()
        }
    }

    fun clearLocalPhotosAndPointsAndTrace(route: RutaEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Obtener puntos antes de borrarlos para saber las rutas de las fotos
                val puntos = db.rutasDao().getPuntosForRuta(route.id)
                
                // 2. Eliminar los archivos de las fotos de los puntos
                for (punto in puntos) {
                    if (!punto.fotoPath.isNullOrEmpty()) {
                        val file = File(punto.fotoPath)
                        if (file.exists()) {
                            file.delete()
                        }
                    }
                }
                
                // 3. Eliminar fotos de recuerdo (souvenirs) de la galería/directorio local
                val rootDir = app.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                val safeRouteName = route.nombre.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val souvenirDir = File(rootDir, "TekinsRutas/$safeRouteName")
                if (souvenirDir.exists()) {
                    souvenirDir.deleteRecursively()
                }
                
                // 4. Eliminar puntos y trazas de la base de datos
                db.rutasDao().deletePuntosForRuta(route.id)
                db.rutasDao().deleteTrazaForRuta(route.id)
                
                // 5. Forzar actualización reactiva en el servicio
                withContext(Dispatchers.Main) {
                    if (GpsTrackingService.selectedRoute.value?.id == route.id) {
                        GpsTrackingService.setSelectedRoute(route)
                    }
                    Toast.makeText(app, "Se liberó espacio: fotos, puntos y trazas eliminados.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al liberar espacio: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, "Error al liberar espacio: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun uploadRouteToDrive(context: Context, route: RutaEntity, account: Account) {
        viewModelScope.launch {
            _driveSyncState.value = DriveSyncState.CheckingWifi
            if (!GoogleDriveSyncManager.isWifiConnected(context)) {
                _driveSyncState.value = DriveSyncState.Error("Se requiere una conexión Wi-Fi para realizar la copia de seguridad.")
                return@launch
            }

            _driveSyncState.value = DriveSyncState.Authenticating
            try {
                val token = GoogleDriveSyncManager.getAccessToken(context, account)
                
                _driveSyncState.value = DriveSyncState.Syncing("Buscando carpeta de la ruta en Google Drive...")
                var routeFolder = GoogleDriveSyncManager.findFile(token, route.nombre, "application/vnd.google-apps.folder")
                val routeFolderId = if (routeFolder != null) {
                    routeFolder.id
                } else {
                    _driveSyncState.value = DriveSyncState.Syncing("Creando carpeta de la ruta...")
                    GoogleDriveSyncManager.createFolder(token, route.nombre)
                }

                _driveSyncState.value = DriveSyncState.Syncing("Creando carpeta de la fecha...")
                val dateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                var dateFolder = GoogleDriveSyncManager.findFile(token, dateStr, "application/vnd.google-apps.folder", routeFolderId)
                val dateFolderId = if (dateFolder != null) {
                    dateFolder.id
                } else {
                    GoogleDriveSyncManager.createFolder(token, dateStr, routeFolderId)
                }

                // Generar y subir script SQL cifrado solo si la ruta pertenece al senderista
                val puntos = db.rutasDao().getPuntosForRuta(route.id)
                val senderista = senderistaState.value ?: db.rutasDao().getSenderistaFlow().first()
                val isOwnRoute = senderista != null && (route.telefono == senderista.telefono || route.telefono.isEmpty())
                if (isOwnRoute) {
                    _driveSyncState.value = DriveSyncState.Syncing("Generando script SQL...")
                    val traza = db.rutasDao().getTrazaForRuta(route.id)
                    val sqlContent = GoogleDriveSyncManager.generateSqlScript(route, puntos, traza)
                    val sqlFileName = "${route.id}_$dateStr.sql"
                    
                    _driveSyncState.value = DriveSyncState.Syncing("Cifrando y subiendo script SQL...")
                    val encryptedSqlBytes = CryptoUtils.encrypt(sqlContent)
                    GoogleDriveSyncManager.uploadMultipartFile(
                        token = token,
                        name = sqlFileName,
                        mimeType = "application/octet-stream",
                        data = encryptedSqlBytes,
                        parentId = routeFolderId
                    )
                }

                // Subir fotos de los puntos de ruta
                val pointsWithPhotos = puntos.filter { !it.fotoPath.isNullOrEmpty() }
                pointsWithPhotos.forEachIndexed { index, punto ->
                    val file = File(punto.fotoPath!!)
                    if (file.exists()) {
                        _driveSyncState.value = DriveSyncState.Syncing("Subiendo foto de punto (${index + 1}/${pointsWithPhotos.size}): ${punto.descripcion}...")
                        val data = file.readBytes()
                        GoogleDriveSyncManager.uploadMultipartFile(
                            token = token,
                            name = file.name,
                            mimeType = "image/jpeg",
                            data = data,
                            parentId = dateFolderId
                        )
                    }
                }

                // Subir fotos de recuerdo (souvenirs) de la galería local de hoy
                val rootDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                val safeRouteName = route.nombre.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val souvenirDir = File(rootDir, "TekinsRutas/$safeRouteName/$dateStr")
                if (souvenirDir.exists()) {
                    val souvenirs = souvenirDir.listFiles { f -> f.extension.lowercase() == "jpg" || f.extension.lowercase() == "jpeg" }
                    souvenirs?.forEachIndexed { index, file ->
                        _driveSyncState.value = DriveSyncState.Syncing("Subiendo foto de recuerdo (${index + 1}/${souvenirs.size}): ${file.name}...")
                        val data = file.readBytes()
                        GoogleDriveSyncManager.uploadMultipartFile(
                            token = token,
                            name = file.name,
                            mimeType = "image/jpeg",
                            data = data,
                            parentId = dateFolderId
                        )
                    }
                }

                _driveSyncState.value = DriveSyncState.Success
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al subir a Drive", e)
                _driveSyncState.value = DriveSyncState.Error(e.localizedMessage ?: "Error en la conexión con Google Drive.")
            }
        }
    }

    fun downloadRouteFromDrive(context: Context, route: RutaEntity, account: Account) {
        viewModelScope.launch {
            _driveSyncState.value = DriveSyncState.CheckingWifi
            if (!GoogleDriveSyncManager.isWifiConnected(context)) {
                _driveSyncState.value = DriveSyncState.Error("Se requiere una conexión Wi-Fi para descargar los puntos.")
                return@launch
            }

            _driveSyncState.value = DriveSyncState.Authenticating
            try {
                val token = GoogleDriveSyncManager.getAccessToken(context, account)

                _driveSyncState.value = DriveSyncState.Syncing("Buscando carpeta de la ruta en Google Drive...")
                val routeFolder = GoogleDriveSyncManager.findFile(token, route.nombre, "application/vnd.google-apps.folder")
                if (routeFolder == null) {
                    _driveSyncState.value = DriveSyncState.Error("No se encontró copia de seguridad de esta ruta en Google Drive.")
                    return@launch
                }

                _driveSyncState.value = DriveSyncState.Syncing("Buscando script SQL en la carpeta de la ruta...")
                val routeFiles = GoogleDriveSyncManager.listFilesInFolder(token, routeFolder.id)
                val sqlFile = routeFiles.filter { it.name.endsWith(".sql") }
                    .sortedByDescending { it.name }
                    .firstOrNull()
                if (sqlFile == null) {
                    _driveSyncState.value = DriveSyncState.Error("No se encontró el script SQL de restauración en la carpeta de la ruta.")
                    return@launch
                }

                _driveSyncState.value = DriveSyncState.Syncing("Descargando script SQL...")
                val sqlBytes = GoogleDriveSyncManager.downloadFile(token, sqlFile.id)
                _driveSyncState.value = DriveSyncState.Syncing("Descifrando script SQL...")
                val sqlContent = CryptoUtils.decrypt(sqlBytes)

                _driveSyncState.value = DriveSyncState.Syncing("Restaurando puntos en base de datos...")
                withContext(Dispatchers.IO) {
                    val statements = sqlContent.split(";")
                    for (stmt in statements) {
                        val trimmed = stmt.trim()
                        if (trimmed.isNotEmpty()) {
                            db.openHelper.writableDatabase.execSQL(trimmed)
                        }
                    }
                }

                // Recuperar y descargar únicamente las fotos de los puntos de ruta restaurados
                val restoredPoints = db.rutasDao().getPuntosForRuta(route.id)
                val pointsWithPhotos = restoredPoints.filter { !it.fotoPath.isNullOrEmpty() }
                
                if (pointsWithPhotos.isNotEmpty()) {
                    _driveSyncState.value = DriveSyncState.Syncing("Buscando copias de seguridad de fechas...")
                    val dateFolders = GoogleDriveSyncManager.listSubfoldersInFolder(token, routeFolder.id)
                    if (dateFolders.isNotEmpty()) {
                        val targetFolder = dateFolders.sortedByDescending { it.name }.first()
                        _driveSyncState.value = DriveSyncState.Syncing("Listando fotos de la copia del ${targetFolder.name}...")
                        val driveFiles = GoogleDriveSyncManager.listFilesInFolder(token, targetFolder.id)
                        
                        pointsWithPhotos.forEachIndexed { index, punto ->
                            val fileName = File(punto.fotoPath!!).name
                            val drivePhoto = driveFiles.find { it.name == fileName }
                            if (drivePhoto != null) {
                                val pct = ((index + 1).toFloat() / pointsWithPhotos.size * 100).toInt()
                                _driveSyncState.value = DriveSyncState.Syncing("bajando $pct%..")
                                try {
                                    val photoBytes = GoogleDriveSyncManager.downloadFile(token, drivePhoto.id)
                                    val localFile = File(context.filesDir, fileName)
                                    localFile.writeBytes(photoBytes)
                                    
                                    // Actualizar la fotoPath en la base de datos con la ruta absoluta local correcta
                                    db.rutasDao().insertPuntoRuta(punto.copy(fotoPath = localFile.absolutePath))
                                } catch (e: Exception) {
                                    Log.e("RutasViewModel", "Error al descargar foto '${fileName}': ${e.message}")
                                }
                            }
                        }
                    }
                }

                // Forzar actualización reactiva en el servicio para recargar los puntos en la UI
                if (GpsTrackingService.selectedRoute.value?.id == route.id) {
                    GpsTrackingService.setSelectedRoute(route)
                }

                _driveSyncState.value = DriveSyncState.Success
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al descargar de Drive", e)
                _driveSyncState.value = DriveSyncState.Error(e.localizedMessage ?: "Error en la conexión con Google Drive.")
            }
        }
    }

    fun exportDatabaseToDownloads(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val dbFile = context.getDatabasePath("rutas.db")
            if (!dbFile.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: Base de datos no encontrada", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            
            val filesToZip = mutableListOf<File>()
            filesToZip.add(dbFile)
            
            val walFile = File(dbFile.path + "-wal")
            if (walFile.exists()) filesToZip.add(walFile)
            
            val shmFile = File(dbFile.path + "-shm")
            if (shmFile.exists()) filesToZip.add(shmFile)
            
            val tempZip = File(context.cacheDir, "rutas_db_temp.zip")
            try {
                if (tempZip.exists()) tempZip.delete()
                
                java.util.zip.ZipOutputStream(java.io.FileOutputStream(tempZip)).use { zipOut ->
                    for (file in filesToZip) {
                        java.io.FileInputStream(file).use { fileIn ->
                            val zipEntry = java.util.zip.ZipEntry(file.name)
                            zipOut.putNextEntry(zipEntry)
                            fileIn.copyTo(zipOut)
                            zipOut.closeEntry()
                        }
                    }
                }
                
                val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val zipFileName = "rutas_db_$dateStr.zip"
                var success = false
                var savedPathMessage = ""
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, zipFileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri).use { outStream ->
                            if (outStream != null) {
                                tempZip.inputStream().use { inStream ->
                                    inStream.copyTo(outStream)
                                }
                                success = true
                                savedPathMessage = "carpeta Downloads"
                            }
                        }
                    }
                } else {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (downloadsDir != null) {
                        if (!downloadsDir.exists()) {
                            downloadsDir.mkdirs()
                        }
                        val destFile = File(downloadsDir, zipFileName)
                        tempZip.inputStream().use { inStream ->
                            destFile.outputStream().use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        success = true
                        savedPathMessage = destFile.absolutePath
                    }
                }
                
                if (!success) {
                    val fallbackDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (fallbackDir != null) {
                        val destFile = File(fallbackDir, zipFileName)
                        tempZip.inputStream().use { inStream ->
                            destFile.outputStream().use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        success = true
                        savedPathMessage = "carpeta privada de la App"
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, "Base de datos exportada con éxito:\n$zipFileName en $savedPathMessage", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Error al guardar el archivo zip", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al exportar base de datos", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                if (tempZip.exists()) {
                    tempZip.delete()
                }
            }
        }
    }

    fun exportLocalRoutesToCsv(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val senderista = db.rutasDao().getSenderistaFlow().first()
                val userPhone = senderista?.telefono ?: ""
                
                if (userPhone.isNotEmpty()) {
                    val currentRutas = db.rutasDao().getAllRutas()
                    for (ruta in currentRutas) {
                        if (ruta.telefono.isEmpty()) {
                            val updatedRoute = ruta.copy(telefono = userPhone)
                            db.rutasDao().insertRuta(updatedRoute)
                            Log.d("RutasViewModel", "ExportCSV: Actualizado teléfono vacío de la ruta '${ruta.nombre}' a '$userPhone'")
                            
                            // Si la ruta está finalizada, la sincronizamos con Neon PostgreSQL
                            val isFinalized = db.rutasDao().isRutaFinalized(ruta.id)
                            if (isFinalized) {
                                try {
                                    Log.d("RutasViewModel", "ExportCSV: Sincronizando ruta '${ruta.nombre}' a Neon Postgres...")
                                    com.gaspar.rutassendero.sync.CloudSyncManager.syncRoute(context, db, ruta.id) { _ -> }
                                    prefs.markRouteSyncedToCloud(ruta.id)
                                } catch (e: Exception) {
                                    Log.e("RutasViewModel", "ExportCSV: Error al sincronizar ruta '${ruta.nombre}': ${e.message}")
                                }
                            }
                        }
                    }
                }

                val rutas = db.rutasDao().getAllRutas()
                if (rutas.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No hay rutas locales para exportar.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val csvContent = StringBuilder()
                csvContent.append("id,nombre,descripcion,telefono\n")
                for (ruta in rutas) {
                    val cleanDesc = ruta.descripcion.replace("\n", " ").replace("\"", "\"\"")
                    csvContent.append("${ruta.id},\"${ruta.nombre}\",\"$cleanDesc\",\"${ruta.telefono}\"\n")
                }

                val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val csvFileName = "rutas_locales_$dateStr.csv"
                var success = false
                var savedPathMessage = ""

                val tempFile = File(context.cacheDir, "temp_rutas.csv")
                tempFile.writeText(csvContent.toString(), Charsets.UTF_8)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, csvFileName)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri).use { outStream ->
                            if (outStream != null) {
                                tempFile.inputStream().use { inStream ->
                                    inStream.copyTo(outStream)
                                }
                                success = true
                                savedPathMessage = "carpeta Downloads"
                            }
                        }
                    }
                } else {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (downloadsDir != null) {
                        if (!downloadsDir.exists()) {
                            downloadsDir.mkdirs()
                        }
                        val destFile = File(downloadsDir, csvFileName)
                        tempFile.inputStream().use { inStream ->
                            destFile.outputStream().use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        success = true
                        savedPathMessage = destFile.absolutePath
                    }
                }

                if (!success) {
                    val fallbackDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    if (fallbackDir != null) {
                        val destFile = File(fallbackDir, csvFileName)
                        tempFile.inputStream().use { inStream ->
                            destFile.outputStream().use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        success = true
                        savedPathMessage = "carpeta privada de la App"
                    }
                }

                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, "Rutas exportadas con éxito:\n$csvFileName en $savedPathMessage", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Error al guardar el archivo CSV", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al exportar rutas a CSV", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al exportar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- OPERACIONES DE SALIDAS (OUTINGS) ---


    fun fetchAvailableOutings() {
        viewModelScope.launch {
            _loadingAvailableOutings.value = true
            try {
                val senderista = senderistaState.value ?: db.rutasDao().getSenderistaFlow().first()
                val email = senderista?.email ?: ""
                
                if (senderista != null) {
                    verifyLocalOutingsWithCloud(senderista)
                }

                val rawList = com.gaspar.rutassendero.sync.CloudSyncManager.fetchAvailableInvitations(
                    prefs.rangoDiasBusquedaSalidas,
                    email
                )
                
                val acceptedInvitations = db.rutasDao().getInvitacionesAceptadas().map { it.idInvitacion }.toSet()
                
                val currentLoc = uiLocation.value ?: currentLocation.value
                val maxKm = prefs.rangoKmBusquedaSalidas.toDouble()
                
                val filtered = rawList.map { it.toMutableMap() }.filter { map ->
                    val tipoRuta = map["tipo_ruta"] as? String ?: "S"
                    val idInvRaw = map["id_invitacion"] as? Int ?: 0
                    
                    if (acceptedInvitations.contains(idInvRaw)) return@filter false
                    
                    val rawLat = map["latitud"] as? Double ?: 0.0
                    val rawLng = map["longitud"] as? Double ?: 0.0
                    val lat = if (rawLat.isNaN()) 0.0 else rawLat
                    val lng = if (rawLng.isNaN()) 0.0 else rawLng
                    
                    if (tipoRuta == "E" || (lat == 0.0 && lng == 0.0)) {
                        map["distancia_gps_val"] = -1.0
                        true
                    } else if (currentLoc != null) {
                        val outingLoc = Location("").apply {
                            latitude = lat
                            longitude = lng
                        }
                        val distMeters = currentLoc.distanceTo(outingLoc)
                        val distKm = distMeters / 1000.0
                        map["distancia_gps_val"] = distKm
                        distKm <= maxKm
                    } else {
                        map["distancia_gps_val"] = -1.0
                        true
                    }
                }
                _availableOutings.value = filtered
                
                // Buscar cuántos eventos llegaron crudos y cuántos pasaron los filtros
                val rawEventosEncontrados = rawList.count { it["tipo_ruta"] == "E" }
                val eventosEncontrados = filtered.count { it["tipo_ruta"] == "E" }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        getApplication<Application>(),
                        "Nube: $rawEventosEncontrados eventos. Mostrando: $eventosEncontrados tras filtros",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al buscar salidas en PostgreSQL", e)
            } finally {
                _loadingAvailableOutings.value = false
            }
        }
    }

    fun acceptOuting(
        context: Context,
        outing: Map<String, Any>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _outingsProgress.value = "Aceptando salida..."
            try {
                val idInvitacion = outing["id_invitacion"] as Int
                val idRuta = outing["id_ruta"] as String
                val rutaNombre = outing["ruta_nombre"] as String
                val telefonoCreador = outing["telefono"] as String
                val fechaHoraSalida = outing["fecha_hora_salida"] as String
                val puntoReunion = outing["punto_reunion"] as String
                val linkGoogleMaps = outing["link_google_maps"] as? String
                val cooperacionMinima = outing["cooperacion_minima"] as Double
                val fechaLimiteRegistro = outing["fecha_limite_registro"] as? String
                val nivel = outing["nivel"] as? String
                val instrucciones = outing["instrucciones"] as? String
                val latitud = outing["latitud"] as? Double
                val longitud = outing["longitud"] as? Double

                val conectividad = (outing["conectividad"] as? Number)?.toFloat()
                val distanciaGps = (outing["distancia_gps"] as? Number)?.toDouble()
                val desnivelPositivo = (outing["desnivel_positivo"] as? Number)?.toDouble()
                val desnivelNegativo = (outing["desnivel_negativo"] as? Number)?.toDouble()
                val contadorSubidas = (outing["contador_subidas"] as? Number)?.toInt()
                val contadorBajadas = (outing["contador_bajadas"] as? Number)?.toInt()
                val duracionSegundos = (outing["duracion_segundos"] as? Number)?.toLong()

                val tipoRuta = outing["tipo_ruta"] as? String ?: "S"
                val comentario = outing["comentario"] as? String

                val senderista = senderistaState.value ?: db.rutasDao().getSenderistaFlow().first()
                    ?: throw Exception("Registra tu perfil antes de participar.")

                // 1. Guardar localmente
                val localEntity = InvitacionAceptadaEntity(
                    idInvitacion = idInvitacion,
                    idRuta = idRuta,
                    nombreRuta = rutaNombre,
                    telefonoCreador = telefonoCreador,
                    fechaHoraSalida = fechaHoraSalida,
                    puntoReunion = puntoReunion,
                    linkGoogleMaps = linkGoogleMaps,
                    cooperacionMinima = cooperacionMinima,
                    fechaLimiteRegistro = fechaLimiteRegistro,
                    nivel = nivel,
                    instrucciones = instrucciones,
                    latitud = latitud,
                    longitud = longitud,
                    cooperacionRealizada = null,
                    fechaCooperacionRealizada = null,
                    sincronizadoConNube = false,
                    conectividad = conectividad,
                    distanciaGps = distanciaGps,
                    desnivelPositivo = desnivelPositivo,
                    desnivelNegativo = desnivelNegativo,
                    contadorSubidas = contadorSubidas,
                    contadorBajadas = contadorBajadas,
                    duracionSegundos = duracionSegundos,
                    tipoRuta = tipoRuta,
                    comentario = comentario
                )
                db.rutasDao().insertInvitacionAceptada(localEntity)

                // 2. Intentar registrar en Postgres
                try {
                    com.gaspar.rutassendero.sync.CloudSyncManager.registerParticipationInCloud(
                        idInvitacion = idInvitacion,
                        emailSenderista = senderista.email,
                        telefonoSenderista = senderista.telefono,
                        cooperacion = null,
                        fechaCooperacionStr = null,
                        tipoRuta = tipoRuta
                    )
                    db.rutasDao().insertInvitacionAceptada(localEntity.copy(sincronizadoConNube = true))
                } catch (e: Exception) {
                    Log.w("RutasViewModel", "No se pudo sincronizar participación en la nube inmediatamente: ${e.message}")
                }

                // 3. Descargar la ruta si no existe localmente (solo para tipo_ruta = 'S')
                if (tipoRuta == "S") {
                    _outingsProgress.value = "Verificando ruta local..."
                    val existingRoute = db.rutasDao().getRutaById(idRuta)
                    if (existingRoute == null) {
                        _outingsProgress.value = "Descargando ruta desde la nube..."
                        com.gaspar.rutassendero.sync.CloudSyncManager.downloadRoute(
                            context,
                            db,
                            idRuta,
                            onProgress = { _outingsProgress.value = it }
                        )
                        
                        // Modificar el teléfono de la ruta descargada para que pertenezca al creador
                        // y así el usuario actual no pueda borrarla en la nube (solo localmente)
                        val downloadedRoute = db.rutasDao().getRutaById(idRuta)
                        if (downloadedRoute != null && downloadedRoute.telefono.isEmpty()) {
                            db.rutasDao().insertRuta(downloadedRoute.copy(telefono = telefonoCreador))
                        }
                    }
                }

                // 4. Agendar en calendario
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    val parsedDate = sdf.parse(fechaHoraSalida)
                    if (parsedDate != null) {
                        val beginTime = parsedDate.time
                        val endTime = beginTime + (4 * 60 * 60 * 1000L) // 4 horas por defecto
                        val desc = buildString {
                            append("Salida de senderismo para la ruta: $rutaNombre.\n")
                            if (!instrucciones.isNullOrEmpty()) {
                                append("Instrucciones: $instrucciones\n")
                            }
                            if (cooperacionMinima > 0.0) {
                                append("Cooperación requerida: $$cooperacionMinima\n")
                            }
                            if (!linkGoogleMaps.isNullOrEmpty()) {
                                append("Punto de encuentro maps: $linkGoogleMaps\n")
                            }
                        }
                        
                        calendarEventFlow.emit(CalendarEventData(
                            title = "Salida: $rutaNombre",
                            description = desc,
                            location = puntoReunion,
                            beginTimeMs = beginTime,
                            endTimeMs = endTime
                        ))
                    }
                } catch (e: Exception) {
                    Log.e("RutasViewModel", "Error al procesar fecha para calendario", e)
                }

                // Refrescar disponibles
                fetchAvailableOutings()

                // If in guide mode, set as active outing
                if (prefs.appRole == "guia") {
                    prefs.activeInvitacionId = idInvitacion
                }

                onSuccess()
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al aceptar la salida", e)
                onError(e.message ?: "Error desconocido")
            } finally {
                _outingsProgress.value = null
            }
        }
    }

    fun registrarCooperacion(
        idInvitacion: Int,
        monto: Double,
        comprobanteBytes: ByteArray? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _outingsProgress.value = "Registrando cooperación..."
            try {
                val inv = db.rutasDao().getInvitacionAceptadaById(idInvitacion)
                    ?: throw Exception("No se encontró la invitación localmente.")

                val senderista = senderistaState.value ?: db.rutasDao().getSenderistaFlow().first()
                    ?: throw Exception("Perfil no encontrado.")
                val fechaCoop = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

                // 1. Guardar localmente
                val updated = inv.copy(
                    cooperacionRealizada = monto,
                    fechaCooperacionRealizada = fechaCoop,
                    comprobantePago = comprobanteBytes,
                    fechaSubidaComprobante = if (comprobanteBytes != null) fechaCoop else null,
                    sincronizadoConNube = false
                )
                db.rutasDao().insertInvitacionAceptada(updated)

                // 2. Intentar registrar en Postgres
                try {
                    val base64Comprobante = comprobanteBytes?.let {
                        android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                    }
                    com.gaspar.rutassendero.sync.CloudSyncManager.registerParticipationInCloud(
                        idInvitacion = idInvitacion,
                        emailSenderista = senderista.email,
                        telefonoSenderista = senderista.telefono,
                        cooperacion = monto,
                        fechaCooperacionStr = fechaCoop,
                        comprobanteBase64 = base64Comprobante,
                        tipoRuta = inv.tipoRuta
                    )
                    db.rutasDao().insertInvitacionAceptada(updated.copy(sincronizadoConNube = true))
                } catch (e: Exception) {
                    Log.w("RutasViewModel", "No se pudo sincronizar el pago en la nube: ${e.message}")
                }

                onSuccess()
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al registrar cooperación", e)
                onError(e.message ?: "Error desconocido")
            } finally {
                _outingsProgress.value = null
            }
        }
    }

    fun reintentarSincronizarCooperacion(
        idInvitacion: Int,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val inv = db.rutasDao().getInvitacionAceptadaById(idInvitacion)
                if (inv == null) return@launch
                
                val senderista = senderistaState.value ?: db.rutasDao().getSenderistaFlow().first()
                if (senderista == null) return@launch

                val base64Comprobante = inv.comprobantePago?.let {
                    android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)
                }

                com.gaspar.rutassendero.sync.CloudSyncManager.registerParticipationInCloud(
                    idInvitacion = inv.idInvitacion,
                    emailSenderista = senderista.email,
                    telefonoSenderista = senderista.telefono,
                    cooperacion = inv.cooperacionRealizada,
                    fechaCooperacionStr = inv.fechaCooperacionRealizada,
                    comprobanteBase64 = base64Comprobante,
                    tipoRuta = inv.tipoRuta
                )
                db.rutasDao().insertInvitacionAceptada(inv.copy(sincronizadoConNube = true))
                Log.d("RutasViewModel", "Sincronización reintentada con éxito")
                onSuccess()
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al reintentar sincronizar", e)
                try {
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val logFile = java.io.File(downloadsDir, "error_sync_cooperacion.txt")
                    logFile.appendText("Error: ${e.message}\nStacktrace:\n${android.util.Log.getStackTraceString(e)}\n\n")
                } catch (ioe: Exception) {
                    Log.e("RutasViewModel", "No se pudo escribir log en Download", ioe)
                }
                onError(e.message ?: "Error desconocido")
            }
        }
    }

    fun cancelarAsistencia(
        idInvitacion: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _outingsProgress.value = "Cancelando participación..."
            try {
                val inv = db.rutasDao().getInvitacionAceptadaById(idInvitacion)
                    ?: throw Exception("No se encontró la invitación localmente.")

                val senderista = senderistaState.value ?: db.rutasDao().getSenderistaFlow().first()

                // 1. Intentar borrar en Postgres la participación y la invitación
                try {
                    if (senderista != null) {
                        com.gaspar.rutassendero.sync.CloudSyncManager.deleteParticipationFromCloud(
                            idInvitacion = idInvitacion,
                            emailSenderista = senderista.email
                        )
                    }
                    com.gaspar.rutassendero.sync.CloudSyncManager.deleteInvitationFromCloud(idInvitacion)
                } catch (e: Exception) {
                    Log.w("RutasViewModel", "No se pudo eliminar de la nube (participación o invitación): ${e.message}")
                }

                // 2. Borrar localmente la invitación
                db.rutasDao().deleteInvitacionAceptada(inv)
                if (prefs.activeInvitacionId == idInvitacion) {
                    prefs.activeInvitacionId = -1
                    db.rutasDao().clearParticipantes()
                }

                // 3. Borrar la ruta localmente si NO pertenece al senderista
                if (senderista != null) {
                    val isOwnRoute = senderista.telefono == inv.telefonoCreador
                    if (!isOwnRoute) {
                        val routeId = inv.idRuta
                        val localRoute = db.rutasDao().getRutaById(routeId)
                        if (localRoute != null) {
                            db.rutasDao().deleteRuta(localRoute)
                            db.rutasDao().deletePuntosForRuta(routeId)
                            db.rutasDao().deleteTrazaForRuta(routeId)
                            if (selectedRoute.value?.id == routeId) {
                                GpsTrackingService.setSelectedRoute(null)
                            }
                            Log.d("RutasViewModel", "cancelarAsistencia: Borrada ruta local $routeId (no pertenece al senderista)")
                        }
                    }
                }

                // Refrescar disponibles
                fetchAvailableOutings()
                onSuccess()
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al cancelar participación", e)
                onError(e.message ?: "Error desconocido")
            } finally {
                _outingsProgress.value = null
            }
        }
    }

    private suspend fun verifyLocalOutingsWithCloud(senderista: SenderistaEntity) = withContext(Dispatchers.IO) {
        try {
            val acceptedList = db.rutasDao().getInvitacionesAceptadas()
            for (inv in acceptedList) {
                // No eliminar salidas que aún no se han subido a la nube
                if (!inv.sincronizadoConNube) continue
                
                val isRegistered = com.gaspar.rutassendero.sync.CloudSyncManager.isParticipantRegisteredInCloud(
                    idInvitacion = inv.idInvitacion,
                    emailSenderista = senderista.email,
                    tipoRuta = inv.tipoRuta
                )
                if (!isRegistered) {
                    Log.d("RutasViewModel", "Outing ${inv.idInvitacion} is no longer active for user in PostgreSQL. Cleaning up locally.")
                    db.rutasDao().deleteInvitacionAceptada(inv)
                    
                    val isOwnRoute = inv.telefonoCreador == senderista.telefono
                    if (!isOwnRoute) {
                        val routeId = inv.idRuta
                        val localRoute = db.rutasDao().getRutaById(routeId)
                        if (localRoute != null) {
                            db.rutasDao().deleteRuta(localRoute)
                            db.rutasDao().deletePuntosForRuta(localRoute.id)
                            db.rutasDao().deleteTrazaForRuta(localRoute.id)
                            if (selectedRoute.value?.id == localRoute.id) {
                                withContext(Dispatchers.Main) {
                                    GpsTrackingService.setSelectedRoute(null)
                                }
                            }
                            Log.d("RutasViewModel", "Deleted local route ${localRoute.id} as it doesn't belong to the hiker.")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RutasViewModel", "Error in verifyLocalOutingsWithCloud", e)
        }
    }

    fun verificarExistenciaInvitacion(
        idInvitacion: Int,
        onDeleted: () -> Unit = {},
        onExists: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val inv = db.rutasDao().getInvitacionAceptadaById(idInvitacion)
                if (inv != null) {
                    val exists = com.gaspar.rutassendero.sync.CloudSyncManager.doesInvitationExistInCloud(idInvitacion)
                    if (!exists) {
                        Log.d("RutasViewModel", "Invitación $idInvitacion no existe en el servidor PostgreSQL. Borrando localmente...")
                        
                        // 1. Borrar localmente la invitación
                        db.rutasDao().deleteInvitacionAceptada(inv)
                        
                        // 2. Borrar la ruta localmente si NO pertenece al senderista
                        val senderista = db.rutasDao().getSenderistaFlow().first()
                        if (senderista != null) {
                            val isOwnRoute = senderista.telefono == inv.telefonoCreador
                            if (!isOwnRoute) {
                                val routeId = inv.idRuta
                                val localRoute = db.rutasDao().getRutaById(routeId)
                                if (localRoute != null) {
                                    db.rutasDao().deleteRuta(localRoute)
                                    db.rutasDao().deletePuntosForRuta(routeId)
                                    db.rutasDao().deleteTrazaForRuta(routeId)
                                    if (selectedRoute.value?.id == routeId) {
                                        GpsTrackingService.setSelectedRoute(null)
                                    }
                                    Log.d("RutasViewModel", "verificarExistenciaInvitacion: Borrada ruta local $routeId (no pertenece al senderista)")
                                }
                            }
                        }
                        onDeleted()
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.w("RutasViewModel", "Error al verificar existencia de la invitación en la nube: ${e.message}")
            }
            onExists()
        }
    }

    fun checkUpcomingOutings(onShowAlert: (InvitacionAceptadaEntity) -> Unit) {
        viewModelScope.launch {
            try {
                val list = db.rutasDao().getInvitacionesAceptadas()
                val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val futureOutings = list.filter { it.fechaHoraSalida >= nowStr }
                if (futureOutings.isNotEmpty()) {
                    // La lista ya está ordenada por fecha_hora_salida ASC en el Dao
                    val closest = futureOutings.first()
                    onShowAlert(closest)
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al verificar salidas futuras", e)
            }
        }
    }

    /**
     * Elimina completamente la cuenta del usuario tanto en la nube (PostgreSQL) como localmente.
     */
    fun deleteAccount(onComplete: (Boolean, String?) -> Unit) {
        if (!com.gaspar.rutassendero.utils.NetworkUtils.isNetworkAvailable(app)) {
            networkErrorMessage.value = "Para eliminar tu cuenta, el dispositivo necesita tener conexión a Internet (Datos o WiFi)."
            onComplete(false, "Sin conexión a Internet")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val email = _ownerEmailFlow.value ?: prefs.ownerEmail
                if (email.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        onComplete(false, "No hay una cuenta activa para eliminar.")
                    }
                    return@launch
                }

                // 1. Eliminar de la nube (PostgreSQL)
                com.gaspar.rutassendero.sync.CloudSyncManager.deleteAccountFromCloud(email)

                // 2. Limpiar Base de Datos Local
                db.clearAllTables() 

                // 3. Limpiar SharedPreferences
                prefs.clearAll()
                prefs.accountDeleted = true
                _ownerEmailFlow.value = null
                _senderistaUiState.value = SenderistaUiState(isLoaded = true, senderista = null)

                withContext(Dispatchers.Main) {
                    onComplete(true, null)
                }
            } catch (e: Exception) {
                Log.e("RutasViewModel", "Error al eliminar la cuenta: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onComplete(false, e.message)
                }
            }
        }
    }
}


sealed class DriveSyncState {
    object Idle : DriveSyncState()
    object CheckingWifi : DriveSyncState()
    object Authenticating : DriveSyncState()
    data class Syncing(val progress: String) : DriveSyncState()
    object Success : DriveSyncState()
    data class Error(val message: String) : DriveSyncState()
}

sealed class CloudSyncState {
    object Idle : CloudSyncState()
    object CheckingConnection : CloudSyncState()
    data class Syncing(val progress: String) : CloudSyncState()
    object Success : CloudSyncState()
    data class Error(val message: String) : CloudSyncState()
}

