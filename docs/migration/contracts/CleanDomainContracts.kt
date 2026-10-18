package docs.migration.contracts

/**
 * Contratos limpios de dominio y casos de uso en Kotlin puro.
 * REGLA DE ARQUITECTURA: Cero dependencias de paquetes 'android.*'.
 * Este código sirve como especificación agnóstica para implementar en Swift / Kotlin Multiplatform.
 */

// =====================================================================
// 1. MODELOS DE DOMINIO LIMPIOS
// =====================================================================

data class RoutePoint(
    val id: Int,
    val routeId: String,
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val description: String,
    val photoUrl: String?,
    val isRouteEnd: Boolean,
    val hasConnectivity: Boolean
)

data class RouteTrace(
    val id: Int,
    val routeId: String,
    val order: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double
)

data class Route(
    val id: String,
    val name: String,
    val date: String,
    val originLatitude: Double,
    val originLongitude: Double,
    val description: String,
    val gpsDistanceMeters: Double,
    val beaconDistanceMeters: Double,
    val startTime: String?,
    val endTime: String?,
    val durationSeconds: Long,
    val elevationGain: Double,
    val elevationLoss: Double,
    val climbsCount: Int,
    val descentsCount: Int,
    val isPublic: Boolean,
    val syncTimestamp: Long
)

data class HikerProfile(
    val email: String,
    val name: String,
    val phone: String,
    val role: String,
    val beaconId: String?,
    val experienceLevel: String,
    val bloodType: String?,
    val emergencyPhone1: String,
    val emergencyPhone2: String?
)

data class PeerBeaconData(
    val beaconId: String,
    val latitude: Double,
    val longitude: Double,
    val rssi: Int,
    val txPower: Int,
    val estimatedDistanceMeters: Double,
    val lastDetectedTimestamp: Long
)

// =====================================================================
// 2. CONTRATOS DE REPOSITORIO (PUERTOS)
// =====================================================================

interface RutasRepositoryContract {
    suspend fun getAllRoutes(): List<Route>
    suspend fun getRouteById(id: String): Route?
    suspend fun getRoutePoints(routeId: String): List<RoutePoint>
    suspend fun getRouteTrace(routeId: String): List<RouteTrace>
    suspend fun saveRoute(route: Route)
    suspend fun savePoint(point: RoutePoint)
    suspend fun saveTraceBatch(traces: List<RouteTrace>)
    suspend fun deleteRoute(routeId: String)
}

interface HikerProfileRepositoryContract {
    suspend fun getProfile(): HikerProfile?
    suspend fun saveProfile(profile: HikerProfile)
    suspend fun updateBeaconId(beaconId: String)
}

interface PeerTrackingRepositoryContract {
    suspend fun getActivePeers(): List<PeerBeaconData>
    suspend fun updatePeerLocation(peer: PeerBeaconData)
    suspend fun pruneInactivePeers(maxAgeMillis: Long)
}

// =====================================================================
// 3. CONTRATOS DE SERVICIOS DE HARDWARE Y NUBE
// =====================================================================

interface BleBeaconBroadcasterContract {
    suspend fun startBroadcasting(beaconId: String, latitude: Double, longitude: Double): Boolean
    fun stopBroadcasting()
    fun isBroadcasting(): Boolean
}

interface BleBeaconScannerContract {
    fun startScanning(onPeerDetected: (PeerBeaconData) -> Unit): Boolean
    fun stopScanning()
    fun isScanning(): Boolean
}

interface LocationTrackerContract {
    fun startTracking(minDistanceMeters: Float, onLocationUpdate: (lat: Double, lon: Double, alt: Double) -> Unit)
    fun stopTracking()
    fun isTracking(): Boolean
}

interface CloudSyncContract {
    suspend fun pushLocalChanges(): Result<Unit>
    suspend fun pullRemoteChanges(): Result<Unit>
    suspend fun uploadPhoto(localFilePath: String): Result<String>
}
