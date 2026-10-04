package com.gaspar.rutassendero.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.room.withTransaction
import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import com.gaspar.rutassendero.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object CloudSyncManager {
    private const val TAG = "CloudSyncManager"

    // Formateadores de fecha de la aplicación
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    // Cliente HTTP para PostgREST
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(35, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun baseUrl(): String = SecureCredentialsManager.getPostgrestBaseUrl().trimEnd('/')

    @Volatile
    private var cachedJwtToken: String? = null
    @Volatile
    private var cachedJwtEmail: String? = null

    /**
     * Establece o actualiza el contexto de autenticación JWT para las peticiones a PostgREST.
     */
    fun setAuthContext(
        email: String, 
        telefono: String = "",
        rol: String = "S",
        idAgrupacion: String? = null
    ) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isNotEmpty()) {
            cachedJwtEmail = cleanEmail
            cachedJwtToken = JwtManager.generateToken(cleanEmail, telefono, rol, idAgrupacion)
            Log.d(TAG, "Contexto de autenticación JWT actualizado para: $cleanEmail con rol: $rol")
        }
    }

    /**
     * Limpia el token JWT actual (por ejemplo al cerrar sesión).
     */
    fun clearAuthContext() {
        cachedJwtToken = null
        cachedJwtEmail = null
    }

    /**
     * Obtiene el token JWT actual en memoria o lo resuelve desde la base de datos local.
     */
    fun getActiveJwtToken(): String? {
        cachedJwtToken?.let { return it }
        // Intentar autogenerar desde la sesión local en Room
        return try {
            val app = com.gaspar.rutassendero.BachesApplication.instance
            val db = app.database
            val ownerEmail = app.preferencesHelper.ownerEmail
            val senderista = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                if (!ownerEmail.isNullOrBlank()) {
                    db.rutasDao().getSenderistaByEmail(ownerEmail)
                } else {
                    db.rutasDao().getFirstSenderista()
                }
            }
            if (senderista != null && senderista.email.isNotBlank()) {
                val token = JwtManager.generateToken(senderista.email, senderista.telefono, senderista.rol, senderista.idAgrupacion)
                cachedJwtEmail = senderista.email.trim().lowercase()
                cachedJwtToken = token
                Log.d(TAG, "JWT autogenerado desde BD local para: ${senderista.email} (rol=${senderista.rol})")
                token
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo autogenerar JWT: ${e.message}")
            null
        }
    }

    @Volatile
    private var isJwtSupportedByServer: Boolean = true
    @Volatile
    private var lastJwtCheckTime: Long = 0L

    private fun shouldAttemptJwt(): Boolean {
        if (isJwtSupportedByServer) return true
        val now = System.currentTimeMillis()
        if (now - lastJwtCheckTime > 5 * 60 * 1000L) {
            return true
        }
        return false
    }

    private fun isJwtSecretMissingError(code: Int, body: String): Boolean {
        return (code == 500 || code == 401) && (
            body.contains("Server lacks JWT secret", ignoreCase = true) ||
            body.contains("PGRST300", ignoreCase = true) ||
            body.contains("JWT secret not found", ignoreCase = true) ||
            body.contains("secret not found", ignoreCase = true)
        )
    }

    private fun executeGet(endpointAndQuery: String, overrideToken: String? = null): String {
        val url = "${baseUrl()}/$endpointAndQuery"

        fun buildRequest(useToken: Boolean): Request {
            val builder = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
            if (useToken) {
                val token = overrideToken ?: getActiveJwtToken()
                if (!token.isNullOrEmpty()) {
                    builder.header("Authorization", "Bearer $token")
                }
            }
            return builder.build()
        }

        val tryJwt = shouldAttemptJwt() && (overrideToken != null || getActiveJwtToken() != null)
        val initialRequest = buildRequest(useToken = tryJwt)

        client.newCall(initialRequest).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (tryJwt && isJwtSecretMissingError(response.code, bodyStr)) {
                Log.w(TAG, "PostgREST aún no tiene configurada la variable PGRST_JWT_SECRET en el servidor. Degradando de forma transparente y segura a modo anónimo...")
                isJwtSupportedByServer = false
                lastJwtCheckTime = System.currentTimeMillis()
                client.newCall(buildRequest(useToken = false)).execute().use { retryResp ->
                    val retryBody = retryResp.body?.string() ?: ""
                    if (!retryResp.isSuccessful) {
                        throw Exception("HTTP ${retryResp.code} en GET $endpointAndQuery: $retryBody")
                    }
                    return retryBody
                }
            }

            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} en GET $endpointAndQuery: $bodyStr")
            }
            if (tryJwt) {
                isJwtSupportedByServer = true
            }
            return bodyStr
        }
    }

    private fun executePostUpsert(table: String, jsonPayload: String, overrideToken: String? = null): String {
        val url = "${baseUrl()}/$table"

        fun buildRequest(useToken: Boolean): Request {
            val builder = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Prefer", "resolution=merge-duplicates,return=representation")
                .header("Accept", "application/json")
            if (useToken) {
                val token = overrideToken ?: getActiveJwtToken()
                if (!token.isNullOrEmpty()) {
                    builder.header("Authorization", "Bearer $token")
                }
            }
            return builder.build()
        }

        val tryJwt = shouldAttemptJwt() && (overrideToken != null || getActiveJwtToken() != null)
        val initialRequest = buildRequest(useToken = tryJwt)

        client.newCall(initialRequest).execute().use { response ->
            val bodyStr = response.body?.string() ?: ""
            if (tryJwt && isJwtSecretMissingError(response.code, bodyStr)) {
                Log.w(TAG, "PostgREST aún no tiene configurada la variable PGRST_JWT_SECRET en el servidor. Reintentando POST $table anónimamente...")
                isJwtSupportedByServer = false
                lastJwtCheckTime = System.currentTimeMillis()
                client.newCall(buildRequest(useToken = false)).execute().use { retryResp ->
                    val retryBody = retryResp.body?.string() ?: ""
                    if (!retryResp.isSuccessful) {
                        throw Exception("HTTP ${retryResp.code} en POST $table: $retryBody")
                    }
                    return retryBody
                }
            }

            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} en POST $table: $bodyStr")
            }
            if (tryJwt) {
                isJwtSupportedByServer = true
            }
            return bodyStr
        }
    }

    private fun executeDelete(endpointAndQuery: String, overrideToken: String? = null): Int {
        val url = "${baseUrl()}/$endpointAndQuery"

        fun buildRequest(useToken: Boolean): Request {
            val builder = Request.Builder()
                .url(url)
                .delete()
                .header("Prefer", "count=exact")
            if (useToken) {
                val token = overrideToken ?: getActiveJwtToken()
                if (!token.isNullOrEmpty()) {
                    builder.header("Authorization", "Bearer $token")
                }
            }
            return builder.build()
        }

        val tryJwt = shouldAttemptJwt() && (overrideToken != null || getActiveJwtToken() != null)
        val initialRequest = buildRequest(useToken = tryJwt)

        client.newCall(initialRequest).execute().use { response ->
            if (tryJwt && (response.code == 500 || response.code == 401)) {
                val bodyStr = response.body?.string() ?: ""
                if (isJwtSecretMissingError(response.code, bodyStr)) {
                    Log.w(TAG, "PostgREST aún no tiene PGRST_JWT_SECRET. Reintentando DELETE $endpointAndQuery anónimamente...")
                    isJwtSupportedByServer = false
                    lastJwtCheckTime = System.currentTimeMillis()
                    client.newCall(buildRequest(useToken = false)).execute().use { retryResp ->
                        if (!retryResp.isSuccessful) {
                            val retryBody = retryResp.body?.string() ?: ""
                            throw Exception("HTTP ${retryResp.code} en DELETE $endpointAndQuery: $retryBody")
                        }
                        val contentRange = retryResp.header("Content-Range")
                        return contentRange?.substringAfterLast('/')?.toIntOrNull() ?: if (retryResp.code == 204) 1 else 0
                    }
                } else {
                    throw Exception("HTTP ${response.code} en DELETE $endpointAndQuery: $bodyStr")
                }
            }

            if (!response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                throw Exception("HTTP ${response.code} en DELETE $endpointAndQuery: $bodyStr")
            }
            if (tryJwt) {
                isJwtSupportedByServer = true
            }
            val contentRange = response.header("Content-Range")
            return contentRange?.substringAfterLast('/')?.toIntOrNull() ?: if (response.code == 204) 1 else 0
        }
    }

    private fun formatToIso(dateTimeStr: String): String {
        return try {
            val parsed = dateTimeFormat.parse(dateTimeStr)
            if (parsed != null) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(parsed)
            } else {
                dateTimeStr
            }
        } catch (e: Exception) {
            try {
                val parsed2 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(dateTimeStr)
                if (parsed2 != null) {
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(parsed2)
                } else {
                    dateTimeStr
                }
            } catch (e2: Exception) {
                dateTimeStr
            }
        }
    }

    private fun formatIsoToDisplay(isoStr: String, targetFormat: SimpleDateFormat): String {
        if (isoStr.isEmpty()) return ""
        return try {
            val cleaned = isoStr.replace("Z", "+00:00")
            val instant = java.time.OffsetDateTime.parse(cleaned)
            val date = Date.from(instant.toInstant())
            targetFormat.format(date)
        } catch (e: Throwable) {
            try {
                val p = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(isoStr)
                if (p != null) targetFormat.format(p) else isoStr
            } catch (e2: Throwable) {
                isoStr
            }
        }
    }

    /**
     * Verifica la disponibilidad de la API PostgREST en Render.
     * En arquitectura REST, el esquema DDL es administrado en PostgreSQL/Render.
     */
    fun initDatabaseSchema() {
        try {
            val response = executeGet("agrupaciones?limit=1")
            Log.d(TAG, "API PostgREST en Render verificada correctamente: $response")
        } catch (e: Throwable) {
            Log.w(TAG, "Advertencia al verificar API PostgREST: ${e.message}")
        }
    }

    /**
     * Comprime y redimensiona una imagen local para optimizar el tamaño.
     * Limita la dimensión máxima a 1080px y comprime al 80% de calidad JPEG.
     */
    private fun compressImage(context: Context, originalPath: String): File? {
        val file = File(originalPath)
        if (!file.exists()) return null

        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
            }
            val bitmap = BitmapFactory.decodeFile(originalPath, options) ?: return null

            val width = bitmap.width
            val height = bitmap.height
            val maxDim = 1080

            val scaledBitmap = if (width > maxDim || height > maxDim) {
                val ratio = width.toFloat() / height.toFloat()
                val newWidth = if (width > height) maxDim else (maxDim * ratio).toInt()
                val newHeight = if (height > width) maxDim else (maxDim / ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }

            val tempFile = File.createTempFile("optimized_photo_", ".jpg", context.cacheDir)
            FileOutputStream(tempFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }

            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()

            Log.d(TAG, "Imagen optimizada. Tamaño original: ${file.length()} bytes, optimizado: ${tempFile.length()} bytes.")
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error comprimiendo imagen: ${e.message}", e)
            null
        }
    }

    /**
     * Sube un archivo a Cloudinary usando el API blocking core.
     */
    private fun uploadToCloudinary(file: File, publicId: String): String {
        val config = SecureCredentialsManager.getCloudinaryConfig()
        val cloudinary = Cloudinary(config)
        val options = ObjectUtils.asMap(
            "folder", "rutas_senderismo",
            "public_id", publicId,
            "overwrite", true,
            "resource_type", "image"
        )
        val uploadResult = cloudinary.uploader().upload(file, options)
        return uploadResult["secure_url"] as String
    }

    /**
     * Descarga las agrupaciones disponibles desde PostgREST.
     */
    suspend fun descargarAgrupaciones(): List<AgrupacionEntity> = withContext(Dispatchers.IO) {
        val agrupaciones = mutableListOf<AgrupacionEntity>()
        try {
            val jsonStr = executeGet("agrupaciones?select=id_agrupacion,nombre&order=nombre.asc")
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id_agrupacion", "").trim()
                val nombre = obj.optString("nombre", "").trim()
                if (id.isNotEmpty()) {
                    agrupaciones.add(
                        AgrupacionEntity(
                            idAgrupacion = id,
                            nombre = if (nombre.isNotEmpty()) nombre else id
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando agrupaciones: ${e.message}", e)
        }
        if (agrupaciones.isEmpty()) {
            AgrupacionEntity.SEED_AGRUPACIONES
        } else {
            agrupaciones
        }
    }

    /**
     * Sincroniza el perfil de un senderista con PostgREST mediante Upsert.
     */
    suspend fun syncSenderista(senderista: SenderistaEntity): Unit = withContext(Dispatchers.IO) {
        setAuthContext(senderista.email, senderista.telefono, senderista.rol, senderista.idAgrupacion)
        initDatabaseSchema()

        val parsedNac = try {
            dateFormat.parse(senderista.fechaNacimiento)
        } catch (e: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(senderista.fechaNacimiento)
            } catch (e2: Exception) {
                null
            }
        }
        val formattedDob = if (parsedNac != null) {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsedNac)
        } else {
            "2000-01-01"
        }

        val fupdateIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(senderista.fupdate))

        val cleanTipoSangre = senderista.tipoSangre?.trim()?.takeIf {
            it.isNotEmpty() && it != "No agregó el tipo de sangre" && it != "No especificado"
        }

        val payload = JSONObject().apply {
            put("email", senderista.email)
            put("rol", senderista.rol)
            put("telefono", senderista.telefono)
            put("nombre", senderista.nombre)
            put("sexo", senderista.sexo)
            put("fecha_nacimiento", formattedDob)
            put("a_quienes_avisar", senderista.aQuienesAvisar)
            put("a_quien_avisar_telefono1", senderista.aQuienAvisarTelefono1)
            put("a_quien_avisar_telefono2", senderista.aQuienAvisarTelefono2 ?: JSONObject.NULL)
            put("tiene_enfermedad_cronica", senderista.tieneEnfermedadCronica)
            put("enfermedad_cronica1", senderista.enfermedadCronica1 ?: JSONObject.NULL)
            put("enfermedad_cronica2", senderista.enfermedadCronica2 ?: JSONObject.NULL)
            put("tiene_auto", senderista.tieneAuto ?: "N")
            put("direccion", senderista.direccion ?: JSONObject.NULL)
            put("lleva_mascota", senderista.llevaMascota ?: "N")
            put("alergias", senderista.alergias ?: JSONObject.NULL)
            put("tipo_sangre", cleanTipoSangre ?: JSONObject.NULL)
            put("seguro_medico", senderista.seguroMedico ?: JSONObject.NULL)
            put("nivel_senderista", senderista.nivelSenderista)
            put("fupdate", fupdateIso)
            put("id_beacon", senderista.idBeacon ?: JSONObject.NULL)
            put("minimo_longitud", senderista.minimoLongitud)
            put("bloqueado", senderista.bloqueado)
            put("consentimiento_terminos", senderista.consentimientoTerminos)
            put("consentimiento_ficha_medica", senderista.consentimientoFichaMedica)
            put("consentimiento_gps_multimedia", senderista.consentimientoGpsMultimedia)
            put("timestamp_consentimiento_utc", senderista.timestampConsentimientoUtc ?: JSONObject.NULL)
            put("codigo_confianza", senderista.codigoConfianza ?: JSONObject.NULL)
            put("peso", senderista.peso ?: JSONObject.NULL)
            put("id_agrupacion", senderista.idAgrupacion ?: JSONObject.NULL)
            put("max_privadas", senderista.maxPrivadas)
        }

        executePostUpsert("senderistas", payload.toString())
        Log.d(TAG, "Senderista ${senderista.email} sincronizado con PostgREST.")
    }

    /**
     * Genera un id_beacon único de 6 bytes (12 caracteres hex) y verifica que no se repita en PostgREST.
     */
    suspend fun generateUniqueBeaconId(): String = withContext(Dispatchers.IO) {
        val random = java.security.SecureRandom()
        val bytes = ByteArray(6)
        var attempts = 0
        var uniqueId = ""
        var isUnique = false

        while (!isUnique && attempts < 10) {
            random.nextBytes(bytes)
            uniqueId = bytes.joinToString("") { "%02x".format(it) }
            attempts++
            try {
                val jsonStr = executeGet("senderistas?id_beacon=eq.$uniqueId&select=id_beacon")
                val array = JSONArray(jsonStr)
                if (array.length() == 0) {
                    isUnique = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error al verificar unicidad de id_beacon en PostgREST, usando generado: ${e.message}")
                isUnique = true
            }
        }
        uniqueId
    }

    /**
     * Busca un senderista en PostgREST por su id_beacon.
     */
    suspend fun fetchSenderistaByBeaconId(idBeacon: String): SenderistaEntity? = withContext(Dispatchers.IO) {
        try {
            val jsonStr = executeGet("senderistas?id_beacon=eq.$idBeacon&limit=1")
            val array = JSONArray(jsonStr)
            if (array.length() > 0) {
                mapJsonToSenderista(array.getJSONObject(0))
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al buscar senderista por id_beacon en PostgREST: ${e.message}", e)
            null
        }
    }

    /**
     * Busca un senderista en PostgREST por su email.
     */
    suspend fun fetchSenderistaByEmail(email: String): SenderistaEntity? = withContext(Dispatchers.IO) {
        try {
            val encodedEmail = URLEncoder.encode(email, "UTF-8")
            val jsonStr = executeGet("senderistas?email=eq.$encodedEmail&limit=1")
            val array = JSONArray(jsonStr)
            if (array.length() > 0) {
                mapJsonToSenderista(array.getJSONObject(0))
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al buscar senderista por email en PostgREST: ${e.message}", e)
            null
        }
    }

    private fun mapJsonToSenderista(obj: JSONObject): SenderistaEntity {
        fun optStr(key: String): String? = if (obj.isNull(key)) null else obj.optString(key)
        fun optDbl(key: String): Double? = if (obj.isNull(key)) null else obj.optDouble(key)
        fun optLng(key: String): Long? = if (obj.isNull(key)) null else obj.optLong(key)
        fun optBool(key: String, default: Boolean = false): Boolean = if (obj.isNull(key)) default else obj.optBoolean(key, default)

        val rawDob = optStr("fecha_nacimiento") ?: "2000-01-01"
        val formattedDob = try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(rawDob)
            if (parsed != null) dateFormat.format(parsed) else rawDob
        } catch (e: Exception) {
            rawDob
        }

        val fupdateRaw = optStr("fupdate")
        val fupdateMillis = try {
            if (!fupdateRaw.isNullOrEmpty()) {
                val cleaned = fupdateRaw.replace("Z", "+00:00")
                java.time.OffsetDateTime.parse(cleaned).toInstant().toEpochMilli()
            } else {
                System.currentTimeMillis()
            }
        } catch (e: Throwable) {
            System.currentTimeMillis()
        }
        val rawRol = obj.optString("rol", "S")
        val normalizedRol = if (rawRol.equals("G", ignoreCase = true)) "G" else rawRol.uppercase()

        return SenderistaEntity(
            email = obj.getString("email"),
            rol = normalizedRol,
            telefono = obj.optString("telefono", ""),
            nombre = obj.optString("nombre", ""),
            sexo = obj.optString("sexo", ""),
            fechaNacimiento = formattedDob,
            aQuienesAvisar = obj.optString("a_quienes_avisar", ""),
            aQuienAvisarTelefono1 = obj.optString("a_quien_avisar_telefono1", ""),
            aQuienAvisarTelefono2 = optStr("a_quien_avisar_telefono2"),
            tieneEnfermedadCronica = obj.optString("tiene_enfermedad_cronica", "N"),
            enfermedadCronica1 = optStr("enfermedad_cronica1"),
            enfermedadCronica2 = optStr("enfermedad_cronica2"),
            tieneAuto = obj.optString("tiene_auto", "N"),
            direccion = optStr("direccion"),
            llevaMascota = obj.optString("lleva_mascota", "N"),
            alergias = optStr("alergias"),
            tipoSangre = optStr("tipo_sangre")?.takeIf { it.isNotBlank() },
            seguroMedico = optStr("seguro_medico"),
            nivelSenderista = obj.optString("nivel_senderista", "principiante"),
            idBeacon = optStr("id_beacon"),
            minimoLongitud = optDbl("minimo_longitud") ?: 4.0,
            bloqueado = optBool("bloqueado"),
            fupdate = fupdateMillis,
            codigoConfianza = optStr("codigo_confianza"),
            consentimientoTerminos = optBool("consentimiento_terminos"),
            consentimientoFichaMedica = optBool("consentimiento_ficha_medica"),
            consentimientoGpsMultimedia = optBool("consentimiento_gps_multimedia"),
            timestampConsentimientoUtc = optLng("timestamp_consentimiento_utc"),
            peso = optDbl("peso"),
            idAgrupacion = optStr("id_agrupacion"),
            maxPrivadas = if (obj.isNull("max_privadas")) 3 else obj.optInt("max_privadas", 3)
        )
    }

    /**
     * Sube y sincroniza una ruta terminada, optimizando fotos y guardándola en PostgREST.
     */
    suspend fun syncRoute(
        context: Context,
        db: RutasDatabase,
        routeId: String,
        onProgress: (String) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        onProgress("Conectando con la base de datos en la nube...")
        initDatabaseSchema()

        // 1. Obtener perfil de senderista local
        onProgress("Verificando perfil del senderista...")
        val senderista = db.rutasDao().getSenderistaFlow().first() 
            ?: throw Exception("Por favor, registra el perfil del senderista primero.")

        // Asegurar que el senderista creador esté en la nube
        onProgress("Sincronizando perfil con la nube...")
        syncSenderista(senderista)

        // 2. Obtener datos de la ruta local
        onProgress("Obteniendo datos de la ruta...")
        val ruta = db.rutasDao().getRutaById(routeId)
            ?: throw Exception("No se encontró la ruta seleccionada localmente.")

        val isOwn = ruta.telefono.isEmpty() || com.gaspar.rutassendero.ui.components.matchPhones(ruta.telefono, senderista.telefono)
        if (!isOwn) {
            Log.w(TAG, "No se sincroniza la ruta '${ruta.nombre}' (${ruta.id}) a la nube porque pertenece a otro senderista (${ruta.telefono}).")
            return@withContext
        }

        val puntos = db.rutasDao().getPuntosForRuta(routeId)
        val traza = db.rutasDao().getTrazaForRuta(routeId)

        // 3. Procesar y optimizar fotos, subiéndolas a Cloudinary
        val puntosSincronizados = puntos.mapIndexed { idx, punto ->
            if (!punto.fotoPath.isNullOrEmpty() && !punto.fotoPath.startsWith("http")) {
                onProgress("Optimizando y subiendo foto del punto ${idx + 1}...")
                val rawFile = File(punto.fotoPath)
                if (rawFile.exists()) {
                    val compressed = compressImage(context, punto.fotoPath)
                    if (compressed != null) {
                        try {
                            val publicId = "${routeId}_${punto.id}"
                            val cloudUrl = uploadToCloudinary(compressed, publicId)
                            
                            // Asegurar que exista una copia local con el nombre del archivo de la nube en filesDir
                            val cloudFileName = cloudUrl.substringAfterLast("/")
                            val localCanonicalFile = File(context.filesDir, cloudFileName)
                            if (!localCanonicalFile.exists() && rawFile.exists()) {
                                rawFile.copyTo(localCanonicalFile, overwrite = true)
                            }
                            compressed.delete() // Limpiar temporal

                            // Actualizar localmente para no volver a subirla
                            val updatedPunto = punto.copy(fotoPath = cloudUrl)
                            db.rutasDao().insertPuntoRuta(updatedPunto)
                            updatedPunto
                        } catch (e: Exception) {
                            Log.e(TAG, "Error subiendo a Cloudinary: ${e.message}", e)
                            throw Exception("Fallo al subir foto del punto ${idx + 1}. Asegúrate de tener buena conexión. Detalles: ${e.message}")
                        }
                    } else {
                        throw Exception("No se pudo comprimir la foto del punto ${idx + 1}.")
                    }
                } else {
                    // Si el archivo en disco no está en la ruta guardada, verificar si está con su nombre en filesDir
                    val altFile = File(context.filesDir, File(punto.fotoPath).name)
                    if (altFile.exists() && altFile.length() > 0) {
                        punto.copy(fotoPath = altFile.absolutePath)
                    } else {
                        punto
                    }
                }
            } else {
                punto
            }
        }

        // 4. Empaquetar a JSON
        onProgress("Empaquetando datos...")
        val jsonPayload = JSONObject().apply {
            // Serializar ruta
            put("ruta", JSONObject().apply {
                put("id", ruta.id)
                put("nombre", ruta.nombre)
                put("fecha", ruta.fecha)
                put("latitud_orig", ruta.latitudOrig)
                put("longitud_orig", ruta.longitudOrig)
                put("descripcion", ruta.descripcion)
                put("distancia_gps", ruta.distanciaGps)
                put("distancia_beacons", ruta.distanciaBeacons)
                put("fecha_hora_inicio", ruta.fechaHoraInicio)
                put("fecha_hora_fin", ruta.fechaHoraFin)
                put("duracion_segundos", ruta.duracionSegundos)
                put("desnivel_positivo", ruta.desnivelPositivo)
                put("desnivel_negativo", ruta.desnivelNegativo)
                put("contador_subidas", ruta.contadorSubidas)
                put("contador_bajadas", ruta.contadorBajadas)
                put("telefono", ruta.telefono)
                put("conectividad", ruta.conectividad)
                put("codigo", ruta.codigo)
                put("publico", ruta.publico)
            })

            // Serializar puntos
            val puntosArray = JSONArray()
            for (p in puntosSincronizados) {
                puntosArray.put(JSONObject().apply {
                    put("id", p.id)
                    put("id_ruta", p.idRuta)
                    put("fecha_hora", p.fechaHora)
                    put("latitud", p.latitud)
                    put("longitud", p.longitud)
                    put("altitud", p.altitud)
                    put("descripcion", p.descripcion)
                    put("foto_path", p.fotoPath)
                    put("es_fin_ruta", p.esFinRuta)
                    put("tiene_conectividad", p.tieneConectividad)
                    put("estado", p.estado)
                })
            }
            put("puntos", puntosArray)

            // Serializar traza
            val trazaArray = JSONArray()
            for (t in traza) {
                trazaArray.put(JSONObject().apply {
                    put("id", t.id)
                    put("id_ruta", t.idRuta)
                    put("orden", t.orden)
                    put("latitud", t.latitud)
                    put("longitud", t.longitud)
                    put("altitud", t.altitud)
                })
            }
            put("traza", trazaArray)
        }

        // 5. Encriptar payload JSON y codificar a Base64
        onProgress("Encriptando datos de ruta...")
        val encryptedBytes = CryptoUtils.encrypt(jsonPayload.toString())
        val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)

        // 6. Subir a PostgREST
        onProgress("Guardando ruta en la nube vía API REST...")

        val parsedDate = try { dateFormat.parse(ruta.fecha) } catch (e: Exception) { null }
        val formattedDate = if (parsedDate != null) SimpleDateFormat("yyyy-MM-dd", Locale.US).format(parsedDate) else ruta.fecha

        val rutaPayload = JSONObject().apply {
            put("id", ruta.id)
            put("nombre", ruta.nombre)
            put("fecha", formattedDate)
            put("latitud_orig", ruta.latitudOrig)
            put("longitud_orig", ruta.longitudOrig)
            put("telefono_creador", ruta.telefono.ifEmpty { senderista.telefono })
            put("descripcion", ruta.descripcion)
            put("distancia_gps", ruta.distanciaGps)
            put("distancia_beacons", ruta.distanciaBeacons)
            put("fecha_hora_inicio", ruta.fechaHoraInicio?.let { formatToIso(it) } ?: JSONObject.NULL)
            put("fecha_hora_fin", ruta.fechaHoraFin?.let { formatToIso(it) } ?: JSONObject.NULL)
            put("duracion_segundos", ruta.duracionSegundos)
            put("desnivel_positivo", ruta.desnivelPositivo)
            put("desnivel_negativo", ruta.desnivelNegativo)
            put("contador_subidas", ruta.contadorSubidas)
            put("contador_bajadas", ruta.contadorBajadas)
            put("datos_encriptados", encryptedBase64)
            put("email_creador", senderista.email)
            put("conectividad", ruta.conectividad)
            put("codigo", ruta.codigo)
            put("publico", ruta.publico)
            put("agrupacion", senderista.idAgrupacion ?: "MH")
        }

        executePostUpsert("rutas", rutaPayload.toString())
        onProgress("¡Ruta sincronizada con éxito!")
    }

    /**
     * Consulta las rutas registradas en la nube mediante PostgREST.
     */
    suspend fun fetchCloudRoutes(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        initDatabaseSchema()
        val routesList = mutableListOf<Map<String, Any>>()
        try {
            val query = "rutas?select=id,nombre,fecha,latitud_orig,longitud_orig,descripcion,distancia_gps,email_creador,telefono_creador,publico,codigo,senderistas(nombre)&order=fecha_registro_nube.desc"
            val jsonStr = executeGet(query)
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val senderistaObj = obj.optJSONObject("senderistas")
                val senderistaNombre = senderistaObj?.optString("nombre") ?: "Senderista Anónimo"

                val rawFecha = obj.optString("fecha", "")
                val displayFecha = try {
                    val p = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(rawFecha)
                    if (p != null) dateFormat.format(p) else rawFecha
                } catch (e: Exception) {
                    rawFecha
                }

                routesList.add(mapOf(
                    "id" to obj.optString("id", ""),
                    "nombre" to obj.optString("nombre", ""),
                    "fecha" to displayFecha,
                    "latitud_orig" to obj.optDouble("latitud_orig", 0.0),
                    "longitud_orig" to obj.optDouble("longitud_orig", 0.0),
                    "descripcion" to obj.optString("descripcion", ""),
                    "senderista_nombre" to senderistaNombre,
                    "distancia_gps" to obj.optDouble("distancia_gps", 0.0),
                    "email_creador" to obj.optString("email_creador", ""),
                    "telefono_creador" to obj.optString("telefono_creador", ""),
                    "publico" to obj.optBoolean("publico", false),
                    "codigo" to obj.optString("codigo", "")
                ))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error consultando rutas en PostgREST: ${e.message}", e)
        }
        routesList
    }

    /**
     * Elimina una ruta de la base de datos PostgreSQL en la nube vía PostgREST.
     */
    suspend fun deleteRouteFromCloud(routeId: String, creatorEmail: String, creatorPhone: String = "") = withContext(Dispatchers.IO) {
        try {
            val token = JwtManager.generateToken(creatorEmail, creatorPhone)
            val encodedId = URLEncoder.encode(routeId, "UTF-8")
            val encodedEmail = URLEncoder.encode(creatorEmail, "UTF-8")
            val query = if (creatorPhone.isNotBlank()) {
                val encodedPhone = URLEncoder.encode(creatorPhone, "UTF-8")
                "rutas?id=eq.$encodedId&or=(email_creador.eq.$encodedEmail,telefono_creador.eq.$encodedPhone)"
            } else {
                "rutas?id=eq.$encodedId&email_creador=eq.$encodedEmail"
            }
            val affectedRows = executeDelete(query, overrideToken = token)
            Log.d(TAG, "deleteRouteFromCloud: Filas eliminadas = $affectedRows para ruta = $routeId")
        } catch (e: Throwable) {
            Log.e(TAG, "Error al eliminar ruta de la nube: ${e.message}", e)
            throw e
        }
    }

    /**
     * Descarga, descifra e inserta en Room una ruta desde PostgREST.
     */
    suspend fun downloadRoute(
        context: Context,
        db: RutasDatabase,
        routeId: String,
        targetPhone: String? = null,
        onProgress: (String) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        onProgress("Comprobando existencia local...")
        val existing = db.rutasDao().getRutaById(routeId)

        onProgress("Descargando ruta desde la nube...")
        val encodedId = URLEncoder.encode(routeId, "UTF-8")
        val jsonStr = executeGet("rutas?id=eq.$encodedId&select=datos_encriptados,telefono_creador&limit=1")
        val array = JSONArray(jsonStr)
        if (array.length() == 0) {
            throw Exception("No se encontró la ruta en la nube.")
        }
        val routeObj = array.getJSONObject(0)
        val encryptedBase64 = if (routeObj.isNull("datos_encriptados")) null else routeObj.getString("datos_encriptados")
        val telefonoCreador = if (routeObj.isNull("telefono_creador")) null else routeObj.getString("telefono_creador")

        if (encryptedBase64.isNullOrEmpty()) {
            throw Exception("Los datos encriptados de la ruta no se encontraron en la nube.")
        }

        // Descifrar payload
        onProgress("Descifrando datos...")
        val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        val decryptedJson = CryptoUtils.decrypt(encryptedBytes)

        // Parsear JSON
        onProgress("Importando a base de datos local...")
        val root = JSONObject(decryptedJson)
        val rJson = root.getJSONObject("ruta")

        val finalPhone = targetPhone?.takeIf { it.isNotBlank() }
            ?: (telefonoCreador ?: rJson.optString("telefono", "")).ifEmpty { rJson.optString("telefono", "") }

        val rutaEntity = RutaEntity(
            id = rJson.getString("id"),
            nombre = rJson.getString("nombre"),
            fecha = rJson.getString("fecha"),
            latitudOrig = rJson.getDouble("latitud_orig"),
            longitudOrig = rJson.getDouble("longitud_orig"),
            descripcion = rJson.getString("descripcion"),
            distanciaGps = rJson.optDouble("distancia_gps", 0.0),
            distanciaBeacons = rJson.optDouble("distancia_beacons", 0.0),
            fechaHoraInicio = if (rJson.isNull("fecha_hora_inicio")) null else rJson.getString("fecha_hora_inicio"),
            fechaHoraFin = if (rJson.isNull("fecha_hora_fin")) null else rJson.getString("fecha_hora_fin"),
            duracionSegundos = rJson.optLong("duracion_segundos", 0L),
            desnivelPositivo = rJson.optDouble("desnivel_positivo", 0.0),
            desnivelNegativo = rJson.optDouble("desnivel_negativo", 0.0),
            contadorSubidas = rJson.optInt("contador_subidas", 0),
            contadorBajadas = rJson.optInt("contador_bajadas", 0),
            telefono = finalPhone,
            conectividad = rJson.optDouble("conectividad", 0.0).toFloat(),
            codigo = rJson.optString("codigo", ""),
            publico = rJson.optBoolean("publico", false),
            fechaImportacion = if (!targetPhone.isNullOrBlank()) null else System.currentTimeMillis()
        )

        val pArray = root.getJSONArray("puntos")

        data class TempPunto(val fechaHoraMillis: Long, val originalId: Int, val pObj: JSONObject)
        val tempPuntos = mutableListOf<TempPunto>()
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        for (i in 0 until pArray.length()) {
            val pObj = pArray.getJSONObject(i)
            val desc = pObj.optString("descripcion", "")

            if (desc == "Pausado por vehículo" || desc == "Reanudando a pie") {
                continue
            }

            val originalId = pObj.optInt("id", i)
            val fechaHoraStr = pObj.optString("fecha_hora", "")
            val millis = try {
                if (fechaHoraStr.isNotEmpty()) sdf.parse(fechaHoraStr)?.time ?: 0L else 0L
            } catch (e: Exception) {
                0L
            }
            tempPuntos.add(TempPunto(millis, originalId, pObj))
        }

        tempPuntos.sortWith(compareBy({ it.fechaHoraMillis }, { it.originalId }))

        val uniquePuntos = tempPuntos.distinctBy { 
            "${it.fechaHoraMillis}_${it.pObj.optDouble("latitud", 0.0)}_${it.pObj.optDouble("longitud", 0.0)}"
        }

        val existingLocalPoints = if (existing != null) db.rutasDao().getPuntosForRuta(routeId) else emptyList()
        val puntosList = mutableListOf<PuntoRutaEntity>()
        for ((idx, temp) in uniquePuntos.withIndex()) {
            val pObj = temp.pObj
            val cloudPath = if (pObj.isNull("foto_path")) null else pObj.getString("foto_path")
            var localPath: String? = null
            if (!cloudPath.isNullOrEmpty()) {
                if (cloudPath.startsWith("http")) {
                    val fileName = cloudPath.substringAfterLast("/")
                    val localFile = File(context.filesDir, fileName)
                    if (localFile.exists() && localFile.length() > 0) {
                        // 1. La foto ya existe físicamente en el teléfono
                        localPath = localFile.absolutePath
                    } else {
                        // 2. Intentar descargarla de la nube
                        try {
                            val pct = ((idx + 1).toFloat() / tempPuntos.size * 100).toInt()
                            onProgress("bajando $pct%..")
                            java.net.URL(cloudPath).openStream().use { input ->
                                FileOutputStream(localFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (localFile.exists() && localFile.length() > 0) {
                                localPath = localFile.absolutePath
                            } else {
                                localPath = cloudPath
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error al descargar foto de la nube ($cloudPath): ${e.message}", e)
                            // Si falla la red, preservar la foto local previa si existía, o mantener la URL
                            val matchedExisting = existingLocalPoints.getOrNull(idx) ?: existingLocalPoints.find { it.descripcion == pObj.optString("descripcion") }
                            localPath = matchedExisting?.fotoPath ?: cloudPath
                        }
                    }
                } else {
                    localPath = cloudPath
                }
            } else {
                // Si la nube no tenía foto, pero localmente ya teníamos una foto válida, ¡conservarla!
                val matchedExisting = existingLocalPoints.getOrNull(idx) ?: existingLocalPoints.find { it.descripcion == pObj.optString("descripcion") }
                if (matchedExisting != null && !matchedExisting.fotoPath.isNullOrEmpty()) {
                    localPath = matchedExisting.fotoPath
                }
            }

            puntosList.add(PuntoRutaEntity(
                id = 0,
                idRuta = pObj.optString("id_ruta", routeId),
                fechaHora = pObj.optString("fecha_hora", ""),
                latitud = pObj.optDouble("latitud", 0.0),
                longitud = pObj.optDouble("longitud", 0.0),
                altitud = pObj.optDouble("altitud", 0.0),
                descripcion = pObj.optString("descripcion", ""),
                fotoPath = localPath,
                esFinRuta = pObj.optBoolean("es_fin_ruta", false),
                tieneConectividad = pObj.optBoolean("tiene_conectividad", false),
                estado = pObj.optInt("estado", 1)
            ))
        }

        val trazaList = mutableListOf<TrazaRutaEntity>()
        val tArray = root.getJSONArray("traza")
        for (i in 0 until tArray.length()) {
            val tObj = tArray.getJSONObject(i)
            trazaList.add(TrazaRutaEntity(
                id = 0,
                idRuta = tObj.optString("id_ruta", routeId),
                orden = tObj.optInt("orden", i),
                latitud = tObj.optDouble("latitud", 0.0),
                longitud = tObj.optDouble("longitud", 0.0),
                altitud = tObj.optDouble("altitud", 0.0)
            ))
        }

        // Guardar transaccionalmente en Room
        db.withTransaction {
            if (existing != null) {
                db.rutasDao().deletePuntosForRuta(routeId)
                db.rutasDao().deleteTrazaForRuta(routeId)
            }
            db.rutasDao().insertRuta(rutaEntity)
            for (p in puntosList) {
                db.rutasDao().insertPuntoRuta(p)
            }
            for (t in trazaList) {
                db.rutasDao().insertTrazaRuta(t)
            }
        }

        onProgress("¡Ruta importada con éxito!")
    }

    /**
     * Verifica si una ruta local está descargada correctamente, incluyendo sus fotos.
     */
    suspend fun verifyRouteDownloadedCorrectly(context: Context, db: RutasDatabase, routeId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val ruta = db.rutasDao().getRutaById(routeId) ?: return@withContext false
            val puntos = db.rutasDao().getPuntosForRuta(routeId)

            if (puntos.isEmpty()) {
                return@withContext false
            }

            for (punto in puntos) {
                val path = punto.fotoPath
                if (!path.isNullOrEmpty()) {
                    if (path.startsWith("http")) {
                        val fileName = path.substringAfterLast("/")
                        val localFile = File(context.filesDir, fileName)
                        if (!localFile.exists() || localFile.length() == 0L) {
                            return@withContext false
                        }
                    } else {
                        val file = File(path)
                        if (!file.exists() || file.length() == 0L) {
                            return@withContext false
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error al verificar integridad de la ruta local: ${e.message}", e)
            false
        }
    }

    /**
     * Recupera y descarga la foto de un punto de ruta específico desde PostgREST.
     */
    suspend fun recoverWaypointPhoto(
        context: Context,
        routeId: String,
        latitud: Double,
        longitud: Double,
        fechaHora: String
    ): PhotoRecoveryDetails = withContext(Dispatchers.IO) {
        var cloudPhotoPath: String? = null
        try {
            val encodedId = URLEncoder.encode(routeId, "UTF-8")
            val jsonStr = executeGet("rutas?id=eq.$encodedId&select=datos_encriptados&limit=1")
            val array = JSONArray(jsonStr)
            if (array.length() == 0) return@withContext PhotoRecoveryDetails(null, null)

            val encryptedBase64 = array.getJSONObject(0).optString("datos_encriptados", "")
            if (encryptedBase64.isEmpty()) return@withContext PhotoRecoveryDetails(null, null)

            val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val decryptedJson = CryptoUtils.decrypt(encryptedBytes)
            val root = JSONObject(decryptedJson)
            val pArray = root.getJSONArray("puntos")

            for (i in 0 until pArray.length()) {
                val pObj = pArray.getJSONObject(i)
                val pLat = pObj.getDouble("latitud")
                val pLon = pObj.getDouble("longitud")
                val pTime = pObj.getString("fecha_hora")

                if (Math.abs(pLat - latitud) < 0.000001 && Math.abs(pLon - longitud) < 0.000001 && pTime == fechaHora) {
                    cloudPhotoPath = if (pObj.isNull("foto_path")) null else pObj.getString("foto_path")
                    break
                }
            }

            if (cloudPhotoPath.isNullOrEmpty()) {
                return@withContext PhotoRecoveryDetails(null, null)
            }

            if (!cloudPhotoPath.startsWith("http")) {
                return@withContext PhotoRecoveryDetails(null, cloudPhotoPath)
            }

            val fileName = cloudPhotoPath.substringAfterLast("/")
            val localFile = File(context.filesDir, fileName)
            java.net.URL(cloudPhotoPath).openStream().use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
            PhotoRecoveryDetails(localFile.absolutePath, cloudPhotoPath)
        } catch (e: Exception) {
            Log.e(TAG, "Error recovering photo: ${e.message}", e)
            PhotoRecoveryDetails(null, cloudPhotoPath)
        }
    }

    data class PhotoRecoveryDetails(
        val localPath: String?,
        val cloudUrl: String?
    )

    /**
     * Obtiene las invitaciones a salir desde PostgREST disponibles para el senderista.
     */
    suspend fun fetchAvailableInvitations(diasRango: Int, userEmail: String): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Map<String, Any>>()
        val nowMs = System.currentTimeMillis()
        val endMs = nowMs + (diasRango * 24L * 60L * 60L * 1000L)
        val sdfIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val startIso = sdfIso.format(Date(nowMs))
        val endIso = sdfIso.format(Date(endMs))

        val query = "invitaciones_ruta?fecha_hora_salida=gte.$startIso&fecha_hora_salida=lte.$endIso&select=id_invitacion,id_ruta,telefono,fecha_hora_salida,punto_reunion,link_google_maps,cooperacion_minima,fecha_limite_registro,nivel,instrucciones,latitud,longitud,tipo_ruta,comentario,rutas(nombre,conectividad,distancia_gps,desnivel_positivo,desnivel_negativo,contador_subidas,contador_bajadas,duracion_segundos)&order=fecha_hora_salida.asc"

        val sdfDisplay = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        try {
            // 1. Fetch invitaciones_ruta
            val jsonStr = executeGet(query)
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val inv = array.getJSONObject(i)

                // Filtrar si el usuario ya está registrado en los participantes
                val participantesArray = inv.optJSONArray("participantes_invitacion")
                var yaRegistrado = false
                if (participantesArray != null) {
                    for (pIdx in 0 until participantesArray.length()) {
                        val pEmail = participantesArray.getJSONObject(pIdx).optString("email_senderista", "")
                        if (pEmail.equals(userEmail, ignoreCase = true)) {
                            yaRegistrado = true
                            break
                        }
                    }
                }
                if (yaRegistrado) continue

                val rutaObj = inv.optJSONObject("rutas")

                val map = mutableMapOf<String, Any>()
                map["id_invitacion"] = inv.optInt("id_invitacion", 0)
                map["id_ruta"] = inv.optString("id_ruta", "")
                map["telefono"] = inv.optString("telefono", "")

                val salidaRaw = inv.optString("fecha_hora_salida", "")
                map["fecha_hora_salida"] = formatIsoToDisplay(salidaRaw, sdfDisplay)

                map["punto_reunion"] = inv.optString("punto_reunion", "")
                map["link_google_maps"] = inv.optString("link_google_maps", "")
                map["cooperacion_minima"] = inv.optDouble("cooperacion_minima", 0.0)

                val limiteRaw = inv.optString("fecha_limite_registro", "")
                map["fecha_limite_registro"] = formatIsoToDisplay(limiteRaw, sdfDisplay)

                map["nivel"] = inv.optString("nivel", "")
                map["instrucciones"] = inv.optString("instrucciones", "")
                map["latitud"] = inv.optDouble("latitud", 0.0)
                map["longitud"] = inv.optDouble("longitud", 0.0)
                map["tipo_ruta"] = inv.optString("tipo_ruta", "S")
                map["comentario"] = inv.optString("comentario", "")
                map["ruta_nombre"] = rutaObj?.optString("nombre") ?: "Ruta sin nombre"

                map["conectividad"] = rutaObj?.optDouble("conectividad", 0.0)?.toFloat() ?: 0.0f
                map["distancia_gps"] = rutaObj?.optDouble("distancia_gps", 0.0) ?: 0.0
                map["desnivel_positivo"] = rutaObj?.optDouble("desnivel_positivo", 0.0) ?: 0.0
                map["desnivel_negativo"] = rutaObj?.optDouble("desnivel_negativo", 0.0) ?: 0.0
                map["contador_subidas"] = rutaObj?.optInt("contador_subidas", 0) ?: 0
                map["contador_bajadas"] = rutaObj?.optInt("contador_bajadas", 0) ?: 0
                map["duracion_segundos"] = rutaObj?.optLong("duracion_segundos", 0L) ?: 0L

                list.add(map)
            }

            // 2. Fetch eventos con manejo de errores específico
            var jsonStrEv = ""
            try {
                val queryEventos = "eventos?select=*" // Remove varchar date filters, filter locally later if needed
                jsonStrEv = executeGet(queryEventos)
                
                val arrayEv = JSONArray(jsonStrEv)
                for (i in 0 until arrayEv.length()) {
                    val ev = arrayEv.getJSONObject(i)
                    val map = mutableMapOf<String, Any>()
                    val idEv = ev.optInt("id_invitacion", ev.optInt("id_evento", ev.optInt("id", 0)))
                    if (idEv == 0) continue
                    map["id_invitacion"] = -idEv
                    map["id_ruta"] = "EVENTO"
                    map["telefono"] = ev.optString("telefono", ev.optString("telefono_creador", ""))

                    val salidaRaw = ev.optString("fecha_hora_salida", "")
                    map["fecha_hora_salida"] = formatIsoToDisplay(salidaRaw, sdfDisplay)

                    map["punto_reunion"] = ev.optString("punto_reunion", "")
                    map["link_google_maps"] = ev.optString("link_google_maps", "")
                    map["cooperacion_minima"] = ev.optDouble("cooperacion_minima", 0.0)

                    val limiteRaw = ev.optString("fecha_limite_registro", "")
                    map["fecha_limite_registro"] = formatIsoToDisplay(limiteRaw, sdfDisplay)

                    map["nivel"] = "Evento"
                    map["instrucciones"] = ev.optString("instrucciones", "")
                    map["latitud"] = ev.optDouble("latitud", 0.0)
                    map["longitud"] = ev.optDouble("longitud", 0.0)
                    map["tipo_ruta"] = "E"
                    map["comentario"] = ev.optString("comentario", "")
                    map["ruta_nombre"] = ev.optString("id_ruta", ev.optString("nombre_agrupacion", "Evento"))

                    map["conectividad"] = 0.0f
                    map["distancia_gps"] = 0.0
                    map["desnivel_positivo"] = 0.0
                    map["desnivel_negativo"] = 0.0
                    map["contador_subidas"] = 0
                    map["contador_bajadas"] = 0
                    map["duracion_segundos"] = 0L

                    list.add(map)
                }
            } catch (e: Exception) {
                val errorMsg = "Error parseando evento: ${e.message}\nTrace:\n${android.util.Log.getStackTraceString(e)}\nResponse: $jsonStrEv"
                Log.e(TAG, errorMsg)
                try {
                    val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val file = java.io.File(dir, "error_evento_api.txt")
                    file.writeText(errorMsg)
                } catch (ioe: Exception) {
                    Log.e(TAG, "No se pudo guardar el txt", ioe)
                }
            }
            
            // Re-ordenar por fecha combinada
            list.sortBy { it["fecha_hora_salida"].toString() }

        } catch (e: Throwable) {
            Log.e(TAG, "Error consultando invitaciones y eventos disponibles: ${e.message}", e)
        }
        list
    }

    /**
     * Registra o actualiza la participación del senderista en una invitación específica en PostgREST.
     */
    suspend fun registerParticipationInCloud(
        idInvitacion: Int, 
        emailSenderista: String, 
        telefonoSenderista: String, 
        cooperacion: Double?, 
        fechaCooperacionStr: String?,
        comprobanteBase64: String? = null,
        tipoRuta: String = "S"
    ): Unit = withContext(Dispatchers.IO) {
        val realId = if (idInvitacion < 0) -idInvitacion else idInvitacion
        val payload = JSONObject().apply {
            put("id_invitacion", realId)
            put("email_senderista", emailSenderista)
            put("telefono_senderista", telefonoSenderista)
            put("cooperacion", cooperacion ?: JSONObject.NULL)
            put("fecha_cooperacion", if (!fechaCooperacionStr.isNullOrEmpty()) formatToIso(fechaCooperacionStr) else JSONObject.NULL)
            put("comprobante_pago", comprobanteBase64 ?: JSONObject.NULL)
            put("fecha_subida_comprobante", if (!comprobanteBase64.isNullOrEmpty() && !fechaCooperacionStr.isNullOrEmpty()) formatToIso(fechaCooperacionStr) else JSONObject.NULL)
            put("tipo_ruta", tipoRuta)
        }
        executePostUpsert("participantes_invitacion", payload.toString())
    }

    /**
     * Elimina el registro de participación del senderista en PostgREST.
     */
    suspend fun deleteParticipationFromCloud(idInvitacion: Int, emailSenderista: String, tipoRuta: String = "S"): Unit = withContext(Dispatchers.IO) {
        val encodedEmail = URLEncoder.encode(emailSenderista, "UTF-8")
        val realId = if (idInvitacion < 0) -idInvitacion else idInvitacion
        executeDelete("participantes_invitacion?id_invitacion=eq.$realId&email_senderista=eq.$encodedEmail&tipo_ruta=eq.$tipoRuta")
    }

    /**
     * Elimina el registro de la invitación en PostgREST.
     */
    suspend fun deleteInvitationFromCloud(idInvitacion: Int): Unit = withContext(Dispatchers.IO) {
        executeDelete("invitaciones_ruta?id_invitacion=eq.$idInvitacion")
    }

    /**
     * Consulta si la participación del senderista sigue registrada en PostgREST.
     */
    suspend fun isParticipantRegisteredInCloud(idInvitacion: Int, emailSenderista: String, tipoRuta: String = "S"): Boolean = withContext(Dispatchers.IO) {
        try {
            val encodedEmail = URLEncoder.encode(emailSenderista, "UTF-8")
            val realId = if (idInvitacion < 0) -idInvitacion else idInvitacion
            val jsonStr = executeGet("participantes_invitacion?id_invitacion=eq.$realId&email_senderista=eq.$encodedEmail&select=id_invitacion")
            val array = JSONArray(jsonStr)
            return@withContext array.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking participation in cloud: ${e.message}", e)
            return@withContext true
        }
    }

    /**
     * Elimina una imagen de Cloudinary dado su URL seguro.
     */
    suspend fun deleteFromCloudinary(urlStr: String): Unit = withContext(Dispatchers.IO) {
        try {
            val uri = java.net.URI(urlStr)
            val path = uri.path
            val parts = path.split("/")
            val folderIndex = parts.indexOf("rutas_senderismo")
            if (folderIndex != -1 && folderIndex < parts.size - 1) {
                val filename = parts.subList(folderIndex, parts.size).joinToString("/")
                val publicId = filename.substringBeforeLast(".")

                val config = SecureCredentialsManager.getCloudinaryConfig()
                val cloudinary = Cloudinary(config)
                cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap())
                Log.d(TAG, "Imagen de Cloudinary eliminada: $publicId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar imagen de Cloudinary ($urlStr): ${e.message}", e)
        }
    }

    /**
     * Verifica si una invitación o evento existe en la base de datos vía PostgREST.
     */
    suspend fun doesInvitationExistInCloud(idInvitacion: Int, tipoRuta: String = "S"): Boolean = withContext(Dispatchers.IO) {
        val realId = if (idInvitacion < 0) -idInvitacion else idInvitacion
        try {
            val jsonStr = if (tipoRuta == "E") {
                executeGet("eventos?id_evento=eq.$realId&select=id_evento")
            } else {
                executeGet("invitaciones_ruta?id_invitacion=eq.$realId&select=id_invitacion")
            }
            val array = JSONArray(jsonStr)
            return@withContext array.length() > 0
        } catch (e: Exception) {
            // Si la columna id_evento no existe y arroja error, intentamos con id o id_invitacion
            if (tipoRuta == "E") {
                try {
                    val fallbackStr = executeGet("eventos?id=eq.$realId&select=id")
                    return@withContext JSONArray(fallbackStr).length() > 0
                } catch (e2: Exception) {
                    try {
                        val fallbackStr2 = executeGet("eventos?id_invitacion=eq.$realId&select=id_invitacion")
                        return@withContext JSONArray(fallbackStr2).length() > 0
                    } catch (e3: Exception) {
                        Log.e(TAG, "Error checking event existence in cloud: ${e3.message}", e3)
                        return@withContext true
                    }
                }
            }
            Log.e(TAG, "Error checking invitation existence in cloud: ${e.message}", e)
            return@withContext true
        }
    }

    /**
     * Obtiene el listado de participantes de una invitación desde PostgREST.
     */
    suspend fun fetchOutingParticipants(idInvitacion: Int): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Map<String, Any>>()
        try {
            val query = "participantes_invitacion?id_invitacion=eq.$idInvitacion&select=telefono_senderista,senderistas(nombre,id_beacon)"
            val jsonStr = executeGet(query)
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val sObj = obj.optJSONObject("senderistas")
                list.add(mapOf(
                    "telefono_senderista" to obj.optString("telefono_senderista", ""),
                    "nombre" to (sObj?.optString("nombre") ?: ""),
                    "id_beacon" to (sObj?.optString("id_beacon") ?: "")
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando participantes de la invitacion: ${e.message}", e)
        }
        list
    }

    /**
     * Elimina todos los datos de un senderista de la base de datos vía PostgREST.
     * Debido a la integridad referencial (ON DELETE CASCADE), se eliminan sus rutas y participaciones.
     */
    suspend fun deleteAccountFromCloud(email: String): Unit = withContext(Dispatchers.IO) {
        try {
            val token = JwtManager.generateToken(email)
            val encodedEmail = URLEncoder.encode(email, "UTF-8")
            val affectedRows = executeDelete("senderistas?email=eq.$encodedEmail", overrideToken = token)
            Log.d(TAG, "Cuenta eliminada de la nube ($email). Filas afectadas: $affectedRows")
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar la cuenta de la nube: ${e.message}", e)
            throw e
        }
    }

    /**
     * Sincroniza en background las tablas principales desde la nube hacia la base de datos local
     * consultando solo los registros donde fupdate (nube) > fupdate (local).
     */
    suspend fun syncDeltaFromCloud(context: Context, db: RutasDatabase) = withContext(Dispatchers.IO) {
        val sdfIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
        
        // 1. Sincronizar Frases Dificultad
        try {
            val cursor = db.openHelper.readableDatabase.query("SELECT MAX(fupdate) FROM frases_dificultad")
            var maxFupdate = 0L
            if (cursor.moveToFirst()) maxFupdate = cursor.getLong(0)
            cursor.close()
            
            val fupdateFilter = if (maxFupdate > 0) "?fupdate=gt.${sdfIso.format(java.util.Date(maxFupdate))}" else ""
            val jsonStr = executeGet("frases_dificultad$fupdateFilter")
            if (jsonStr.isNotBlank() && jsonStr != "[]") {
                val array = JSONArray(jsonStr)
                val frasesList = mutableListOf<FraseDificultadEntity>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val fupdateStr = obj.optString("fupdate", "")
                    val fupdateMillis = parseFupdate(fupdateStr)
                    frasesList.add(FraseDificultadEntity(
                        id = obj.getString("id"),
                        categoria = obj.getString("categoria"),
                        frase = obj.getString("frase"),
                        fupdate = fupdateMillis
                    ))
                }
                if (frasesList.isNotEmpty()) db.rutasDao().insertFrases(frasesList)
                Log.d(TAG, "Sincronizadas ${frasesList.size} frases de dificultad.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing frases: ${e.message}")
        }
        
        // 2. Sincronizar Agrupaciones
        try {
            val cursor = db.openHelper.readableDatabase.query("SELECT MAX(fupdate) FROM agrupaciones")
            var maxFupdate = 0L
            if (cursor.moveToFirst()) maxFupdate = cursor.getLong(0)
            cursor.close()
            
            val fupdateFilter = if (maxFupdate > 0) "?fupdate=gt.${sdfIso.format(java.util.Date(maxFupdate))}" else ""
            val jsonStr = executeGet("agrupaciones$fupdateFilter")
            if (jsonStr.isNotBlank() && jsonStr != "[]") {
                val array = JSONArray(jsonStr)
                val agrupacionesList = mutableListOf<AgrupacionEntity>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val fupdateStr = obj.optString("fupdate", "")
                    val fupdateMillis = parseFupdate(fupdateStr)
                    agrupacionesList.add(AgrupacionEntity(
                        idAgrupacion = obj.getString("id_agrupacion"),
                        nombre = obj.getString("nombre"),
                        fupdate = fupdateMillis
                    ))
                }
                if (agrupacionesList.isNotEmpty()) db.rutasDao().insertAgrupaciones(agrupacionesList)
                Log.d(TAG, "Sincronizadas ${agrupacionesList.size} agrupaciones.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing agrupaciones: ${e.message}")
        }
        
        // 2.5 Sincronizar Eventos
        try {
            // Usar un filtro similar para la fecha, calculando startIso localmente
            val nowMs = System.currentTimeMillis()
            val sdfIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val startIso = sdfIso.format(Date(nowMs))
            val jsonStr = executeGet("eventos?select=*")
            if (jsonStr.isNotBlank() && jsonStr != "[]") {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val fupdateStr = obj.optString("fupdate", "")
                    val fupdateMillis = if (fupdateStr.isNotBlank()) parseFupdate(fupdateStr) else System.currentTimeMillis()
                    val idEv = obj.optInt("id_invitacion", obj.optInt("id_evento", obj.optInt("id", 0)))
                    if (idEv == 0) continue // Skip if no ID is found
                    val evento = EventoEntity(
                        idEvento = idEv,
                        nombreEvento = obj.optString("id_ruta", obj.optString("nombre_agrupacion", "Evento")),
                        telefonoCreador = obj.optString("telefono", obj.optString("telefono_creador", "")),
                        fechaHoraSalida = obj.optString("fecha_hora_salida", ""),
                        puntoReunion = obj.optString("punto_reunion", ""),
                        linkGoogleMaps = obj.optString("link_google_maps").takeIf { it.isNotBlank() },
                        cooperacionMinima = obj.optDouble("cooperacion_minima", 0.0),
                        fechaLimiteRegistro = obj.optString("fecha_limite_registro").takeIf { it.isNotBlank() },
                        instrucciones = obj.optString("instrucciones").takeIf { it.isNotBlank() },
                        latitud = obj.optDouble("latitud").takeIf { !it.isNaN() },
                        longitud = obj.optDouble("longitud").takeIf { !it.isNaN() },
                        cooperacionRealizada = null,
                        fechaCooperacionRealizada = null,
                        sincronizadoConNube = true,
                        fupdate = fupdateMillis,
                        comentario = obj.optString("comentario").takeIf { it.isNotBlank() }
                    )
                    db.rutasDao().insertEvento(evento)
                }
                Log.d(TAG, "Sincronizados eventos delta.")
            }
        } catch (e: Exception) {
            val errorMsg = "Error leyendo tabla evento: ${e.message}\nTrace:\n${android.util.Log.getStackTraceString(e)}"
            Log.e(TAG, errorMsg)
            try {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(dir, "error_evento_api.txt")
                file.appendText("\n\n--- SYNC DELTA ---\n$errorMsg")
            } catch (ioe: Exception) {
                Log.e(TAG, "No se pudo guardar el txt", ioe)
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Error API Eventos: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        
        // 3. Sincronizar Rutas (esto automáticamente actualiza puntos y traza porque están en datos_encriptados)
        try {
            val cursor = db.openHelper.readableDatabase.query("SELECT MAX(fupdate) FROM rutas")
            var maxFupdate = 0L
            if (cursor.moveToFirst()) maxFupdate = cursor.getLong(0)
            cursor.close()
            
            val fupdateFilter = if (maxFupdate > 0) "?fupdate=gt.${sdfIso.format(java.util.Date(maxFupdate))}" else ""
            val jsonStr = executeGet("rutas$fupdateFilter&select=id")
            if (jsonStr.isNotBlank() && jsonStr != "[]") {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val routeId = obj.getString("id")
                    // Download full route which handles decrypting points and trace
                    downloadRoute(context, db, routeId, null) { progress ->
                        Log.d(TAG, "Delta sync route $routeId progress: $progress")
                    }
                    Log.d(TAG, "Ruta actualizada desde delta sync: $routeId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing rutas: ${e.message}")
        }
        
        // 4. Sincronizar Senderista actual
        try {
            val senderistaLocal = db.rutasDao().getFirstSenderista()
            if (senderistaLocal != null) {
                val encodedEmail = URLEncoder.encode(senderistaLocal.email, "UTF-8")
                val filter = "?email=eq.$encodedEmail&fupdate=gt.${sdfIso.format(java.util.Date(senderistaLocal.fupdate))}"
                val jsonStr = executeGet("senderistas$filter")
                if (jsonStr.isNotBlank() && jsonStr != "[]") {
                    val array = JSONArray(jsonStr)
                    if (array.length() > 0) {
                        val obj = array.getJSONObject(0)
                        val fupdateStr = obj.optString("fupdate", "")
                        val fupdateMillis = parseFupdate(fupdateStr)
                        
                        // Solo actualizamos atributos relevantes que pudieron cambiar del lado admin
                        val updatedSenderista = senderistaLocal.copy(
                            bloqueado = obj.optBoolean("bloqueado", senderistaLocal.bloqueado),
                            nombre = obj.optString("nombre", senderistaLocal.nombre),
                            rol = obj.optString("rol", senderistaLocal.rol),
                            nivelSenderista = obj.optString("nivel_senderista", senderistaLocal.nivelSenderista),
                            maxPrivadas = obj.optInt("max_privadas", senderistaLocal.maxPrivadas),
                            fupdate = fupdateMillis
                        )
                        db.rutasDao().insertSenderista(updatedSenderista)
                        Log.d(TAG, "Senderista local actualizado desde delta sync.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing senderista: ${e.message}")
        }
    }
    
    private fun parseFupdate(fupdateStr: String): Long {
        return try {
            if (fupdateStr.isNotEmpty()) {
                val cleaned = fupdateStr.replace("Z", "+00:00")
                java.time.OffsetDateTime.parse(cleaned).toInstant().toEpochMilli()
            } else {
                System.currentTimeMillis()
            }
        } catch (e: Throwable) {
            System.currentTimeMillis()
        }
    }
}
