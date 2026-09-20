package com.gaspar.rutassendero.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.util.Base64
import android.util.Log
import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import androidx.room.withTransaction
import com.gaspar.rutassendero.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.text.SimpleDateFormat
import java.util.Locale

object CloudSyncManager {
    private const val TAG = "CloudSyncManager"

    // Formateadores de fecha para PostgreSQL
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    /**
     * Obtiene una conexión JDBC activa con la base de datos PostgreSQL de Neon.
     */
    private fun getConnection(): Connection {
        Class.forName("org.postgresql.Driver")
        val uri = SecureCredentialsManager.getPostgresUri()
        val user = SecureCredentialsManager.getPostgresUser()
        val password = SecureCredentialsManager.getPostgresPassword()
        return DriverManager.getConnection(uri, user, password)
    }

    /**
     * Inicializa las tablas en la nube si no existen.
     * Esto asegura que la base de datos de Neon esté lista para usarse.
     */
    fun initDatabaseSchema() {
        try {
            getConnection().use { conn ->
                val stmt = conn.createStatement()



                // 2. Tabla senderistas
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS senderistas (
                        email VARCHAR(150) PRIMARY KEY,
                        rol VARCHAR(5) NOT NULL DEFAULT 'S',
                        telefono VARCHAR(30) NOT NULL,
                        nombre VARCHAR(150) NOT NULL,
                        sexo VARCHAR(5) NOT NULL,
                        fecha_nacimiento DATE NOT NULL,
                        a_quienes_avisar TEXT NOT NULL,
                        a_quien_avisar_telefono1 VARCHAR(30) NOT NULL,
                        a_quien_avisar_telefono2 VARCHAR(30),
                        tiene_enfermedad_cronica VARCHAR(2) NOT NULL DEFAULT 'N',
                        enfermedad_cronica1 TEXT,
                        enfermedad_cronica2 TEXT,
                        tiene_auto VARCHAR(2) DEFAULT 'N',
                        direccion TEXT,
                        lleva_mascota VARCHAR(2) DEFAULT 'N',
                        alergias TEXT,
                        tipo_sangre VARCHAR(10),
                        seguro_medico TEXT,
                        nivel_senderista VARCHAR(30) DEFAULT 'principiante',
                        fupdate TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        id_beacon VARCHAR(12),
                        minimo_longitud DOUBLE PRECISION NOT NULL DEFAULT 4.0,
                        bloqueado BOOLEAN DEFAULT FALSE,
                        consentimiento_terminos BOOLEAN DEFAULT FALSE,
                        consentimiento_ficha_medica BOOLEAN DEFAULT FALSE,
                        consentimiento_gps_multimedia BOOLEAN DEFAULT FALSE,
                        timestamp_consentimiento_utc BIGINT,
                        max_privadas INT DEFAULT 3
                    );
                """.trimIndent())

                // Modificar estructura de senderistas existentes si es necesario
                val columnsToEnsure = listOf(
                    "id_beacon VARCHAR(12)",
                    "minimo_longitud DOUBLE PRECISION NOT NULL DEFAULT 4.0",
                    "bloqueado BOOLEAN DEFAULT FALSE",
                    "consentimiento_terminos BOOLEAN DEFAULT FALSE",
                    "consentimiento_ficha_medica BOOLEAN DEFAULT FALSE",
                    "consentimiento_gps_multimedia BOOLEAN DEFAULT FALSE",
                    "timestamp_consentimiento_utc BIGINT",
                    "codigo_confianza VARCHAR(6)",
                    "peso DOUBLE PRECISION",
                    "max_privadas INT DEFAULT 3"
                )
                
                columnsToEnsure.forEach { col ->
                    try {
                        stmt.execute("ALTER TABLE senderistas ADD COLUMN IF NOT EXISTS $col")
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo agregar columna '$col' a tabla senderistas: ${e.message}")
                    }
                }
                
                try {
                    stmt.execute("ALTER TABLE senderistas ALTER COLUMN tipo_sangre DROP NOT NULL")
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo alterar tipo_sangre DROP NOT NULL: ${e.message}")
                }



                // 3. Tabla rutas
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS rutas (
                        id VARCHAR(10) PRIMARY KEY,
                        nombre VARCHAR(150) NOT NULL,
                        fecha DATE NOT NULL,
                        latitud_orig DOUBLE PRECISION NOT NULL,
                        longitud_orig DOUBLE PRECISION NOT NULL,
                        telefono_creador VARCHAR(30) NOT NULL,
                        descripcion TEXT NOT NULL,
                        distancia_gps DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                        distancia_beacons DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                        fecha_hora_inicio TIMESTAMP WITH TIME ZONE,
                        fecha_hora_fin TIMESTAMP WITH TIME ZONE,
                        duracion_segundos BIGINT NOT NULL DEFAULT 0,
                        desnivel_positivo DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                        desnivel_negativo DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                        contador_subidas INTEGER NOT NULL DEFAULT 0,
                        contador_bajadas INTEGER NOT NULL DEFAULT 0,
                        datos_encriptados TEXT,
                        fecha_registro_nube TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                        email_creador VARCHAR(150) REFERENCES senderistas(email) ON DELETE CASCADE,
                        conectividad REAL NOT NULL DEFAULT 0.0,
                        codigo VARCHAR(5) NOT NULL DEFAULT '',
                        publico BOOLEAN NOT NULL DEFAULT FALSE
                    );
                """.trimIndent())

                // Asegurar columna conectividad en rutas existentes
                try {
                    stmt.execute("ALTER TABLE rutas ADD COLUMN IF NOT EXISTS conectividad REAL NOT NULL DEFAULT 0.0")
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo agregar columna conectividad a tabla rutas en Postgres: ${e.message}")
                }

                // Asegurar que datos_encriptados sea TEXT y no VARCHAR(255)
                try {
                    stmt.execute("ALTER TABLE rutas ALTER COLUMN datos_encriptados TYPE TEXT")
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo alterar el tipo de datos_encriptados a TEXT: ${e.message}")
                }

                // Asegurar que descripcion sea TEXT y no VARCHAR(255)
                try {
                    stmt.execute("ALTER TABLE rutas ALTER COLUMN descripcion TYPE TEXT")
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo alterar el tipo de descripcion a TEXT: ${e.message}")
                }

                try {
                    stmt.execute("ALTER TABLE rutas ADD COLUMN IF NOT EXISTS codigo VARCHAR(10) NOT NULL DEFAULT ''")
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo agregar columna codigo a tabla rutas en Postgres: ${e.message}")
                }
                
                try {
                    stmt.execute("ALTER TABLE rutas ALTER COLUMN codigo TYPE VARCHAR(10)")
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo alterar codigo a VARCHAR(10): ${e.message}")
                }

                try {
                    stmt.execute("ALTER TABLE rutas ADD COLUMN IF NOT EXISTS publico BOOLEAN NOT NULL DEFAULT FALSE")
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo agregar columna publico a tabla rutas en Postgres: ${e.message}")
                }

                // 4. Tabla frases_dificultad en PostgreSQL
                try {
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS frases_dificultad (
                            id VARCHAR(50) PRIMARY KEY,
                            categoria VARCHAR(50) NOT NULL,
                            frase TEXT NOT NULL
                        );
                    """.trimIndent())

                    val countRs = stmt.executeQuery("SELECT COUNT(*) FROM frases_dificultad")
                    var pgFraseCount = 0
                    if (countRs.next()) {
                        pgFraseCount = countRs.getInt(1)
                    }
                    countRs.close()

                    if (pgFraseCount == 0) {
                        conn.prepareStatement("INSERT INTO frases_dificultad (id, categoria, frase) VALUES (?, ?, ?)").use { insertStmt ->
                            for (frase in FraseDificultadEntity.SEED_FRASES) {
                                insertStmt.setString(1, frase.id)
                                insertStmt.setString(2, frase.categoria)
                                insertStmt.setString(3, frase.frase)
                                insertStmt.addBatch()
                            }
                            insertStmt.executeBatch()
                        }
                        Log.d(TAG, "Se sembró la tabla frases_dificultad en PostgreSQL con ${FraseDificultadEntity.SEED_FRASES.size} registros.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al crear/sembrar tabla frases_dificultad en PostgreSQL: ${e.message}", e)
                }
 
                Log.d(TAG, "Esquema de base de datos verificado/creado con éxito en la nube.")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error al inicializar el esquema de BD en la nube: ${e.message}", e)
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
     * Descarga las agrupaciones disponibles desde PostgreSQL en la nube.
     */
    suspend fun descargarEstadisticasRutas(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        val stats = mutableListOf<Map<String, Any>>()
        val sql = """
            SELECT r.nombre, COUNT(p.email_senderista) as total_participantes
            FROM rutas r
            LEFT JOIN invitaciones_ruta i ON r.id = i.id_ruta
            LEFT JOIN participantes_invitacion p ON i.id_invitacion = p.id_invitacion
            GROUP BY r.nombre
        """.trimIndent()
        try {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(sql)
                    while (rs.next()) {
                        val map = mapOf<String, Any>(
                            "nombre" to (rs.getString("nombre") ?: "Desconocida"),
                            "total_participantes" to rs.getInt("total_participantes")
                        )
                        stats.add(map)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading route statistics: ${e.message}", e)
        }
        stats
    }

    suspend fun descargarAgrupaciones(): List<AgrupacionEntity> = withContext(Dispatchers.IO) {
        val agrupaciones = mutableListOf<AgrupacionEntity>()
        val sql = "SELECT id_agrupacion, nombre FROM agrupaciones"
        getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                while (rs.next()) {
                    agrupaciones.add(
                        AgrupacionEntity(
                            idAgrupacion = rs.getString("id_agrupacion"),
                            nombre = rs.getString("nombre")
                        )
                    )
                }
            }
        }
        agrupaciones
    }

    /**
     * Sincroniza el perfil de un senderista con PostgreSQL en la nube.
     */
    suspend fun syncSenderista(senderista: SenderistaEntity): Unit = withContext(Dispatchers.IO) {
        initDatabaseSchema() // Aseguramos tablas creadas

        val sql = """
            INSERT INTO senderistas (
                email, rol, telefono, nombre, sexo, fecha_nacimiento, 
                a_quienes_avisar, a_quien_avisar_telefono1, a_quien_avisar_telefono2, 
                tiene_enfermedad_cronica, enfermedad_cronica1, enfermedad_cronica2, 
                tiene_auto, direccion, lleva_mascota, alergias, tipo_sangre, 
                seguro_medico, nivel_senderista, fupdate, id_beacon, minimo_longitud, bloqueado,
                consentimiento_terminos, consentimiento_ficha_medica, consentimiento_gps_multimedia, timestamp_consentimiento_utc, codigo_confianza, peso, id_agrupacion, max_privadas
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (email) DO UPDATE SET
                nombre = EXCLUDED.nombre,
                sexo = EXCLUDED.sexo,
                fecha_nacimiento = EXCLUDED.fecha_nacimiento,
                a_quienes_avisar = EXCLUDED.a_quienes_avisar,
                a_quien_avisar_telefono1 = EXCLUDED.a_quien_avisar_telefono1,
                a_quien_avisar_telefono2 = EXCLUDED.a_quien_avisar_telefono2,
                tiene_enfermedad_cronica = EXCLUDED.tiene_enfermedad_cronica,
                enfermedad_cronica1 = EXCLUDED.enfermedad_cronica1,
                enfermedad_cronica2 = EXCLUDED.enfermedad_cronica2,
                tiene_auto = EXCLUDED.tiene_auto,
                direccion = EXCLUDED.direccion,
                lleva_mascota = EXCLUDED.lleva_mascota,
                alergias = EXCLUDED.alergias,
                tipo_sangre = EXCLUDED.tipo_sangre,
                seguro_medico = EXCLUDED.seguro_medico,
                nivel_senderista = EXCLUDED.nivel_senderista,
                fupdate = EXCLUDED.fupdate,
                id_beacon = EXCLUDED.id_beacon,
                minimo_longitud = EXCLUDED.minimo_longitud,
                consentimiento_terminos = EXCLUDED.consentimiento_terminos,
                consentimiento_ficha_medica = EXCLUDED.consentimiento_ficha_medica,
                consentimiento_gps_multimedia = EXCLUDED.consentimiento_gps_multimedia,
                timestamp_consentimiento_utc = EXCLUDED.timestamp_consentimiento_utc,
                codigo_confianza = EXCLUDED.codigo_confianza,
                peso = EXCLUDED.peso,
                id_agrupacion = EXCLUDED.id_agrupacion,
                max_privadas = EXCLUDED.max_privadas;
        """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, senderista.email)
                pstmt.setString(2, senderista.rol)
                pstmt.setString(3, senderista.telefono)
                pstmt.setString(4, senderista.nombre)
                pstmt.setString(5, senderista.sexo)

                val parsedNac = try {
                    dateFormat.parse(senderista.fechaNacimiento)
                } catch (e: Exception) {
                    try {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(senderista.fechaNacimiento)
                    } catch (e2: Exception) {
                        try {
                            SimpleDateFormat("yyyy/MM/dd", Locale.US).parse(senderista.fechaNacimiento)
                        } catch (ex: Exception) {
                            null
                        }
                    }
                }
                if (parsedNac != null) {
                    pstmt.setDate(6, java.sql.Date(parsedNac.time))
                } else {
                    pstmt.setNull(6, java.sql.Types.DATE)
                }

                pstmt.setString(7, senderista.aQuienesAvisar)
                pstmt.setString(8, senderista.aQuienAvisarTelefono1)
                pstmt.setString(9, senderista.aQuienAvisarTelefono2)
                pstmt.setString(10, senderista.tieneEnfermedadCronica)
                pstmt.setString(11, senderista.enfermedadCronica1)
                pstmt.setString(12, senderista.enfermedadCronica2)
                pstmt.setString(13, senderista.tieneAuto ?: "N")
                pstmt.setString(14, senderista.direccion)
                pstmt.setString(15, senderista.llevaMascota ?: "N")
                pstmt.setString(16, senderista.alergias)
                val cleanTipoSangre = senderista.tipoSangre?.trim()?.takeIf {
                    it.isNotEmpty() && it != "No agregó el tipo de sangre" && it != "No especificado"
                }
                pstmt.setString(17, cleanTipoSangre)
                pstmt.setString(18, senderista.seguroMedico)
                pstmt.setString(19, senderista.nivelSenderista)
                pstmt.setTimestamp(20, java.sql.Timestamp(senderista.fupdate))
                pstmt.setString(21, senderista.idBeacon)
                pstmt.setDouble(22, senderista.minimoLongitud)
                pstmt.setBoolean(23, senderista.bloqueado)
                pstmt.setBoolean(24, senderista.consentimientoTerminos)
                pstmt.setBoolean(25, senderista.consentimientoFichaMedica)
                pstmt.setBoolean(26, senderista.consentimientoGpsMultimedia)
                
                if (senderista.timestampConsentimientoUtc != null) {
                    pstmt.setLong(27, senderista.timestampConsentimientoUtc)
                } else {
                    pstmt.setNull(27, java.sql.Types.BIGINT)
                }
                pstmt.setString(28, senderista.codigoConfianza)

                if (senderista.peso != null) {
                    pstmt.setDouble(29, senderista.peso)
                } else {
                    pstmt.setNull(29, java.sql.Types.DOUBLE)
                }
                
                if (senderista.idAgrupacion != null) {
                    pstmt.setString(30, senderista.idAgrupacion)
                } else {
                    pstmt.setNull(30, java.sql.Types.VARCHAR)
                }
                
                pstmt.setInt(31, senderista.maxPrivadas)

                pstmt.executeUpdate()
                Log.d(TAG, "Senderista ${senderista.email} sincronizado con PostgreSQL.")
            }
        }
    }

    /**
     * Genera un id_beacon único de 6 bytes (12 caracteres hex) y verifica que no se repita en Postgres.
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
                getConnection().use { conn ->
                    val sql = "SELECT COUNT(*) FROM senderistas WHERE id_beacon = ?"
                    conn.prepareStatement(sql).use { pstmt ->
                        pstmt.setString(1, uniqueId)
                        pstmt.executeQuery().use { rs ->
                            if (rs.next() && rs.getInt(1) == 0) {
                                isUnique = true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Si falla la verificación por red, asumimos que es único (la probabilidad de colisión es de 1 en 2.8*10^14)
                Log.w(TAG, "Error al verificar unicidad de id_beacon en la nube, usando generado: ${e.message}")
                isUnique = true
            }
        }
        uniqueId
    }

    /**
     * Busca un senderista en PostgreSQL por su id_beacon.
     */
    suspend fun fetchSenderistaByBeaconId(idBeacon: String): SenderistaEntity? = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = "SELECT * FROM senderistas WHERE id_beacon = ? LIMIT 1"
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, idBeacon)
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            mapRowToSenderista(rs)
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al buscar senderista por id_beacon en PostgreSQL: ${e.message}", e)
            null
        }
    }

    /**
     * Busca un senderista en PostgreSQL por su email.
     */
    suspend fun fetchSenderistaByEmail(email: String): SenderistaEntity? = withContext(Dispatchers.IO) {
        try {
            getConnection().use { conn ->
                val sql = "SELECT * FROM senderistas WHERE email = ? LIMIT 1"
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, email)
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            mapRowToSenderista(rs)
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al buscar senderista por email en PostgreSQL: ${e.message}", e)
            null
        }
    }

    private fun mapRowToSenderista(rs: java.sql.ResultSet): SenderistaEntity {
        val dobDate = try { rs.getDate("fecha_nacimiento") } catch (e: Exception) { null }
        val dobStr = if (dobDate != null) dateFormat.format(dobDate) else (try { rs.getString("fecha_nacimiento") } catch (e: Exception) { null } ?: "01/01/2000")
        val fupdateVal = try { rs.getTimestamp("fupdate")?.time } catch (e: Exception) { null } ?: System.currentTimeMillis()

        fun getOptDouble(col: String): Double? = try {
            val v = rs.getDouble(col)
            if (rs.wasNull()) null else v
        } catch (e: Exception) { null }

        fun getOptLong(col: String): Long? = try {
            val v = rs.getLong(col)
            if (rs.wasNull()) null else v
        } catch (e: Exception) { null }

        fun getOptBoolean(col: String): Boolean = try {
            rs.getBoolean(col)
        } catch (e: Exception) { false }

        fun getOptString(col: String): String? = try {
            rs.getString(col)
        } catch (e: Exception) { null }

        return SenderistaEntity(
            email = rs.getString("email"),
            rol = rs.getString("rol") ?: "S",
            telefono = rs.getString("telefono"),
            nombre = rs.getString("nombre"),
            sexo = rs.getString("sexo"),
            fechaNacimiento = dobStr,
            aQuienesAvisar = rs.getString("a_quienes_avisar"),
            aQuienAvisarTelefono1 = rs.getString("a_quien_avisar_telefono1"),
            aQuienAvisarTelefono2 = getOptString("a_quien_avisar_telefono2"),
            tieneEnfermedadCronica = rs.getString("tiene_enfermedad_cronica") ?: "N",
            enfermedadCronica1 = getOptString("enfermedad_cronica1"),
            enfermedadCronica2 = getOptString("enfermedad_cronica2"),
            tieneAuto = rs.getString("tiene_auto") ?: "N",
            direccion = getOptString("direccion"),
            llevaMascota = rs.getString("lleva_mascota") ?: "N",
            alergias = getOptString("alergias"),
            tipoSangre = getOptString("tipo_sangre")?.takeIf { it.isNotBlank() },
            seguroMedico = getOptString("seguro_medico"),
            nivelSenderista = rs.getString("nivel_senderista") ?: "principiante",
            idBeacon = getOptString("id_beacon"),
            minimoLongitud = getOptDouble("minimo_longitud") ?: 4.0,
            bloqueado = getOptBoolean("bloqueado"),
            fupdate = fupdateVal,
            codigoConfianza = getOptString("codigo_confianza"),
            consentimientoTerminos = getOptBoolean("consentimiento_terminos"),
            consentimientoFichaMedica = getOptBoolean("consentimiento_ficha_medica"),
            consentimientoGpsMultimedia = getOptBoolean("consentimiento_gps_multimedia"),
            timestampConsentimientoUtc = getOptLong("timestamp_consentimiento_utc"),
            peso = getOptDouble("peso"),
            idAgrupacion = getOptString("id_agrupacion")
        )
    }

    /**
     * Sube y sincroniza una ruta terminada, optimizando fotos y encriptando los datos.
     */
    suspend fun syncRoute(
        context: Context,
        db: RutasDatabase,
        routeId: String,
        onProgress: (String) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        onProgress("Conectando con la base de datos...")
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
                            compressed.delete() // Limpiar temporal

                            // Actualizar localmente para no volver a subirla
                            val updatedPunto = punto.copy(fotoPath = cloudUrl)
                            db.rutasDao().insertPuntoRuta(updatedPunto)
                            updatedPunto
                        } catch (e: Exception) {
                            Log.e(TAG, "Error subiendo a Cloudinary: ${e.message}", e)
                            // En lugar de ignorar la foto, detenemos la sincronización para no perderla
                            throw Exception("Fallo al subir foto del punto ${idx + 1}. Asegúrate de tener buena conexión. Detalles: ${e.message}")
                        }
                    } else {
                        throw Exception("No se pudo comprimir la foto del punto ${idx + 1}.")
                    }
                } else {
                    // Si el archivo físico no existe localmente, entonces sí la mandamos como null para no romper la ruta
                    punto.copy(fotoPath = null)
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

        // 6. Subir a Postgres
        onProgress("Guardando en Neon PostgreSQL...")
        val sql = """
            INSERT INTO rutas (
                id, nombre, fecha, latitud_orig, longitud_orig, telefono_creador, 
                descripcion, distancia_gps, distancia_beacons, fecha_hora_inicio, fecha_hora_fin, 
                duracion_segundos, desnivel_positivo, desnivel_negativo, contador_subidas, 
                contador_bajadas, datos_encriptados, email_creador, conectividad, codigo, publico, agrupacion
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                nombre = EXCLUDED.nombre,
                fecha = EXCLUDED.fecha,
                latitud_orig = EXCLUDED.latitud_orig,
                longitud_orig = EXCLUDED.longitud_orig,
                telefono_creador = EXCLUDED.telefono_creador,
                descripcion = EXCLUDED.descripcion,
                distancia_gps = EXCLUDED.distancia_gps,
                distancia_beacons = EXCLUDED.distancia_beacons,
                fecha_hora_inicio = EXCLUDED.fecha_hora_inicio,
                fecha_hora_fin = EXCLUDED.fecha_hora_fin,
                duracion_segundos = EXCLUDED.duracion_segundos,
                desnivel_positivo = EXCLUDED.desnivel_positivo,
                desnivel_negativo = EXCLUDED.desnivel_negativo,
                contador_subidas = EXCLUDED.contador_subidas,
                contador_bajadas = EXCLUDED.contador_bajadas,
                datos_encriptados = EXCLUDED.datos_encriptados,
                email_creador = EXCLUDED.email_creador,
                conectividad = EXCLUDED.conectividad,
                codigo = EXCLUDED.codigo,
                publico = EXCLUDED.publico,
                agrupacion = EXCLUDED.agrupacion;
        """.trimIndent()

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, ruta.id)
                pstmt.setString(2, ruta.nombre)

                val parsedDate = dateFormat.parse(ruta.fecha)
                pstmt.setDate(3, java.sql.Date(parsedDate?.time ?: System.currentTimeMillis()))
                pstmt.setDouble(4, ruta.latitudOrig)
                pstmt.setDouble(5, ruta.longitudOrig)
                pstmt.setString(6, ruta.telefono.ifEmpty { senderista.telefono })
                pstmt.setString(7, ruta.descripcion)
                pstmt.setDouble(8, ruta.distanciaGps)
                pstmt.setDouble(9, ruta.distanciaBeacons)

                val parsedInicio = ruta.fechaHoraInicio?.let { dateTimeFormat.parse(it) }
                pstmt.setTimestamp(10, parsedInicio?.let { java.sql.Timestamp(it.time) })

                val parsedFin = ruta.fechaHoraFin?.let { dateTimeFormat.parse(it) }
                pstmt.setTimestamp(11, parsedFin?.let { java.sql.Timestamp(it.time) })

                pstmt.setLong(12, ruta.duracionSegundos)
                pstmt.setDouble(13, ruta.desnivelPositivo)
                pstmt.setDouble(14, ruta.desnivelNegativo)
                pstmt.setInt(15, ruta.contadorSubidas)
                pstmt.setInt(16, ruta.contadorBajadas)
                pstmt.setString(17, encryptedBase64)
                pstmt.setString(18, senderista.email)
                pstmt.setFloat(19, ruta.conectividad)
                pstmt.setString(20, ruta.codigo)
                pstmt.setBoolean(21, ruta.publico)
                pstmt.setString(22, senderista.idAgrupacion ?: "MH")

                pstmt.executeUpdate()
            }
        }
        onProgress("¡Ruta sincronizada con éxito!")
    }

    suspend fun fetchCloudRoutes(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        initDatabaseSchema()
        val routesList = mutableListOf<Map<String, Any>>()
        val sql = """
            SELECT r.id, r.nombre, r.fecha, r.latitud_orig, r.longitud_orig, 
                   r.descripcion, r.distancia_gps, r.email_creador, r.telefono_creador, s.nombre AS senderista_nombre,
                   r.publico, r.codigo
            FROM rutas r 
            LEFT JOIN senderistas s ON r.telefono_creador = s.telefono 
            ORDER BY r.fecha_registro_nube DESC
        """.trimIndent()

        try {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        while (rs.next()) {
                            routesList.add(mapOf(
                                "id" to rs.getString("id"),
                                "nombre" to rs.getString("nombre"),
                                "fecha" to dateFormat.format(rs.getDate("fecha")),

                                "latitud_orig" to rs.getDouble("latitud_orig"),
                                "longitud_orig" to rs.getDouble("longitud_orig"),
                                "descripcion" to rs.getString("descripcion"),
                                "senderista_nombre" to (rs.getString("senderista_nombre") ?: "Senderista Anónimo"),
                                "distancia_gps" to rs.getDouble("distancia_gps"),
                                "email_creador" to (rs.getString("email_creador") ?: ""),
                                "telefono_creador" to (rs.getString("telefono_creador") ?: ""),
                                "publico" to rs.getBoolean("publico"),
                                "codigo" to (rs.getString("codigo") ?: "")
                            ))
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error consultando rutas en la nube: ${e.message}", e)
        }
        routesList
    }

    /**
     * Elimina una ruta de la base de datos PostgreSQL de Neon en la nube.
     */
    suspend fun deleteRouteFromCloud(routeId: String, creatorEmail: String, creatorPhone: String = "") = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM rutas WHERE id = ? AND (email_creador = ? OR telefono_creador = ?)"
        try {
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, routeId)
                    pstmt.setString(2, creatorEmail)
                    pstmt.setString(3, creatorPhone)
                    val affectedRows = pstmt.executeUpdate()
                    Log.d(TAG, "deleteRouteFromCloud: Filas eliminadas = $affectedRows para ruta = $routeId")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error al eliminar ruta de la nube: ${e.message}", e)
            throw e
        }
    }


    /**
     * Descarga, descifra e inserta en Room una ruta desde PostgreSQL.
     * Si ya existe localmente, interrumpe el proceso.
     */
    suspend fun downloadRoute(
        context: Context,
        db: RutasDatabase,
        routeId: String,
        onProgress: (String) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        onProgress("Comprobando existencia local...")
        val existing = db.rutasDao().getRutaById(routeId)


        onProgress("Descargando ruta desde PostgreSQL...")
        var encryptedBase64: String? = null
        var telefonoCreador: String? = null
        val sql = "SELECT datos_encriptados, telefono_creador FROM rutas WHERE id = ?"

        getConnection().use { conn ->
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setString(1, routeId)
                pstmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        encryptedBase64 = rs.getString("datos_encriptados")
                        telefonoCreador = rs.getString("telefono_creador")
                    }
                }
            }
        }

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
            telefono = (telefonoCreador ?: rJson.optString("telefono", "")).ifEmpty { rJson.optString("telefono", "") },
            conectividad = rJson.optDouble("conectividad", 0.0).toFloat(),
            codigo = rJson.optString("codigo", ""),
            publico = rJson.optBoolean("publico", false),
            fechaImportacion = System.currentTimeMillis()
        )

        val pArray = root.getJSONArray("puntos")

        // 1. Extraer y filtrar a una lista temporal para poder ordenar
        data class TempPunto(val fechaHoraMillis: Long, val originalId: Int, val pObj: JSONObject)
        val tempPuntos = mutableListOf<TempPunto>()
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
        for (i in 0 until pArray.length()) {
            val pObj = pArray.getJSONObject(i)
            val desc = pObj.optString("descripcion", "")
            
            // Filtrar los puntos espurios generados automáticamente por vehículo (glitch de versiones viejas)
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
        
        // 2. Ordenar cronológicamente usando fecha_hora, y si falla (mismos millis o nulos), usar el ID original
        tempPuntos.sortWith(compareBy({ it.fechaHoraMillis }, { it.originalId }))
        
        // 3. Eliminar duplicados exactos (misma hora y mismas coordenadas) producto de resincronizaciones erróneas
        val uniquePuntos = tempPuntos.distinctBy { 
            "${it.fechaHoraMillis}_${it.pObj.optDouble("latitud", 0.0)}_${it.pObj.optDouble("longitud", 0.0)}"
        }
        
        // 4. Procesar los puntos ya ordenados y filtrados
        val puntosList = mutableListOf<PuntoRutaEntity>()
        for ((idx, temp) in uniquePuntos.withIndex()) {
            val pObj = temp.pObj
            val cloudPath = if (pObj.isNull("foto_path")) null else pObj.getString("foto_path")
            var localPath: String? = null
            if (!cloudPath.isNullOrEmpty()) {
                if (cloudPath.startsWith("http")) {
                    try {
                        val fileName = cloudPath.substringAfterLast("/")
                        val pct = ((idx + 1).toFloat() / tempPuntos.size * 100).toInt()
                        onProgress("bajando $pct%..")
                        val localFile = File(context.filesDir, fileName)
                        java.net.URL(cloudPath).openStream().use { input ->
                            java.io.FileOutputStream(localFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        localPath = localFile.absolutePath
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al descargar foto de la nube ($cloudPath): ${e.message}", e)
                        // No interrumpir la descarga de la ruta por un fallo en una foto
                        localPath = null
                    }
                } else {
                    localPath = cloudPath
                }
            }

            puntosList.add(PuntoRutaEntity(
                id = 0, // Room generará una nueva PK secuencial (y en el orden correcto)
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
                id = 0, // Room generará una nueva PK
                idRuta = tObj.optString("id_ruta", routeId),
                orden = tObj.optInt("orden", i),
                latitud = tObj.optDouble("latitud", 0.0),
                longitud = tObj.optDouble("longitud", 0.0),
                altitud = tObj.optDouble("altitud", 0.0)
            ))
        }

        // Guardar transaccionalmente en Room
        db.withTransaction {
            if (existing == null) {
                db.rutasDao().insertRuta(rutaEntity)
            } else {
                // Si la ruta ya existía localmente, limpiamos sus puntos y trazas previos antes de re-importar los de la nube
                db.rutasDao().deletePuntosForRuta(routeId)
                db.rutasDao().deleteTrazaForRuta(routeId)
            }
            for (p in puntosList) {
                // Insertar los puntos de ruta importados
                db.rutasDao().insertPuntoRuta(p)
            }
            for (t in trazaList) {
                // Insertar la traza de ruta importada
                db.rutasDao().insertTrazaRuta(t)
            }
        }
        
        onProgress("¡Ruta importada con éxito!")
    }

    /**
     * Verifica si una ruta local está descargada correctamente, incluyendo sus fotos.
     * Retorna true si todo está bien, false si faltan fotos, puntos, o si la ruta no existe.
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
                        return@withContext false
                    }
                    val file = File(path)
                    if (!file.exists() || file.length() == 0L) {
                        return@withContext false
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
     * Recupera y descarga la foto de un punto de ruta específico desde Neon PostgreSQL.
     */
    suspend fun recoverWaypointPhoto(
        context: Context,
        routeId: String,
        latitud: Double,
        longitud: Double,
        fechaHora: String
    ): PhotoRecoveryDetails = withContext(Dispatchers.IO) {
        var encryptedBase64: String? = null
        var cloudPhotoPath: String? = null
        val sql = "SELECT datos_encriptados FROM rutas WHERE id = ?"
        try {
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, routeId)
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            encryptedBase64 = rs.getString("datos_encriptados")
                        }
                    }
                }
            }
            if (encryptedBase64.isNullOrEmpty()) return@withContext PhotoRecoveryDetails(null, null)

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
                java.io.FileOutputStream(localFile).use { output ->
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
     * Obtiene las invitaciones a salir de PostgreSQL disponibles para el senderista.
     * Rango de fechas de 'diasRango' partiendo de la fecha actual.
     * Excluye las invitaciones en las que el usuario ya está registrado como participante.
     */
    suspend fun fetchAvailableInvitations(diasRango: Int, userEmail: String): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Map<String, Any>>()
        val nowMs = System.currentTimeMillis()
        val endMs = nowMs + (diasRango * 24L * 60L * 60L * 1000L)
        val nowTS = java.sql.Timestamp(nowMs)
        val endTS = java.sql.Timestamp(endMs)
        
        val sql = """
            SELECT i.id_invitacion, i.id_ruta, i.telefono, i.fecha_hora_salida, i.punto_reunion, 
                   i.link_google_maps, i.cooperacion_minima, i.fecha_limite_registro, i.nivel, 
                   i.instrucciones, i.latitud, i.longitud, r.nombre AS ruta_nombre, 
                   r.conectividad, r.distancia_gps, r.desnivel_positivo, r.desnivel_negativo,
                   r.contador_subidas, r.contador_bajadas, r.duracion_segundos
            FROM invitaciones_ruta i
            LEFT JOIN rutas r ON i.id_ruta = r.id
            LEFT JOIN senderistas s ON i.telefono = s.telefono
            WHERE i.fecha_hora_salida >= ? AND i.fecha_hora_salida <= ?
              AND i.id_invitacion NOT IN (
                  SELECT p.id_invitacion 
                  FROM participantes_invitacion p 
                  WHERE p.email_senderista = ?
              )
            ORDER BY i.fecha_hora_salida ASC
        """.trimIndent()
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        
        try {
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setTimestamp(1, nowTS)
                    pstmt.setTimestamp(2, endTS)
                    pstmt.setString(3, userEmail)
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val map = mutableMapOf<String, Any>()
                            map["id_invitacion"] = rs.getInt("id_invitacion")
                            map["id_ruta"] = rs.getString("id_ruta") ?: ""
                            map["telefono"] = rs.getString("telefono") ?: ""
                            
                            val salidaTS = rs.getTimestamp("fecha_hora_salida")
                            map["fecha_hora_salida"] = if (salidaTS != null) sdf.format(salidaTS) else ""
                            
                            map["punto_reunion"] = rs.getString("punto_reunion") ?: ""
                            map["link_google_maps"] = rs.getString("link_google_maps") ?: ""
                            map["cooperacion_minima"] = rs.getDouble("cooperacion_minima")
                            
                            val limiteTS = rs.getTimestamp("fecha_limite_registro")
                            map["fecha_limite_registro"] = if (limiteTS != null) sdf.format(limiteTS) else ""
                            
                            map["nivel"] = rs.getString("nivel") ?: ""
                            map["instrucciones"] = rs.getString("instrucciones") ?: ""
                            map["latitud"] = rs.getDouble("latitud")
                            map["longitud"] = rs.getDouble("longitud")
                            map["ruta_nombre"] = rs.getString("ruta_nombre") ?: "Ruta sin nombre"
                            
                            map["conectividad"] = rs.getFloat("conectividad")
                            map["distancia_gps"] = rs.getDouble("distancia_gps")
                            map["desnivel_positivo"] = rs.getDouble("desnivel_positivo")
                            map["desnivel_negativo"] = rs.getDouble("desnivel_negativo")
                            map["contador_subidas"] = rs.getInt("contador_subidas")
                            map["contador_bajadas"] = rs.getInt("contador_bajadas")
                            map["duracion_segundos"] = rs.getLong("duracion_segundos")
                            
                            list.add(map)
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error consultando invitaciones disponibles: ${e.message}", e)
        }
        list
    }

    /**
     * Registra o actualiza la participación del senderista en una invitación específica en PostgreSQL.
     */
    suspend fun registerParticipationInCloud(
        idInvitacion: Int, 
        emailSenderista: String, 
        telefonoSenderista: String, 
        cooperacion: Double?, 
        fechaCooperacionStr: String?
    ): Unit = withContext(Dispatchers.IO) {
        val sql = """
            INSERT INTO participantes_invitacion (id_invitacion, email_senderista, telefono_senderista, cooperacion, fecha_cooperacion, fecha_registro)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id_invitacion, email_senderista) DO UPDATE SET
                cooperacion = EXCLUDED.cooperacion,
                fecha_cooperacion = EXCLUDED.fecha_cooperacion;
        """.trimIndent()
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val parsedFechaCoop = if (!fechaCooperacionStr.isNullOrEmpty()) {
            try {
                sdf.parse(fechaCooperacionStr)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        val sqlFechaCoop = parsedFechaCoop?.let { java.sql.Timestamp(it.time) }
        
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setInt(1, idInvitacion)
                pstmt.setString(2, emailSenderista)
                pstmt.setString(3, telefonoSenderista)
                if (cooperacion != null) {
                    pstmt.setDouble(4, cooperacion)
                } else {
                    pstmt.setNull(4, java.sql.Types.DOUBLE)
                }
                pstmt.setTimestamp(5, sqlFechaCoop)
                pstmt.executeUpdate()
            }
        }
    }

    /**
     * Elimina el registro de participación del senderista en PostgreSQL.
     */
    suspend fun deleteParticipationFromCloud(idInvitacion: Int, emailSenderista: String): Unit = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM participantes_invitacion WHERE id_invitacion = ? AND email_senderista = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setInt(1, idInvitacion)
                pstmt.setString(2, emailSenderista)
                pstmt.executeUpdate()
            }
        }
    }

    /**
     * Elimina el registro de la invitación en PostgreSQL.
     */
    suspend fun deleteInvitationFromCloud(idInvitacion: Int): Unit = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM invitaciones_ruta WHERE id_invitacion = ?"
        getConnection().use { conn ->
            conn.prepareStatement(sql).use { pstmt ->
                pstmt.setInt(1, idInvitacion)
                pstmt.executeUpdate()
            }
        }
    }

    /**
     * Consulta si la participación del senderista sigue registrada en PostgreSQL.
     */
    suspend fun isParticipantRegisteredInCloud(idInvitacion: Int, emailSenderista: String): Boolean = withContext(Dispatchers.IO) {
        val sql = "SELECT COUNT(*) FROM participantes_invitacion WHERE id_invitacion = ? AND email_senderista = ?"
        try {
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setInt(1, idInvitacion)
                    pstmt.setString(2, emailSenderista)
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            return@withContext rs.getInt(1) > 0
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking participation in cloud: ${e.message}", e)
            return@withContext true // Asumir que sí para no borrar falsamente sin conexión
        }
        false
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
     * Verifica si una invitación existe en la base de datos de PostgreSQL.
     */
    suspend fun doesInvitationExistInCloud(idInvitacion: Int): Boolean = withContext(Dispatchers.IO) {
        val sql = "SELECT COUNT(*) FROM invitaciones_ruta WHERE id_invitacion = ?"
        try {
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setInt(1, idInvitacion)
                    pstmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            return@withContext rs.getInt(1) > 0
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking invitation existence in cloud: ${e.message}", e)
            return@withContext true // Asumir que sí ante problemas de red para evitar borrado accidental
        }
        false
    }

    /**
     * Obtiene el listado de participantes de una invitación desde PostgreSQL.
     */
    suspend fun fetchOutingParticipants(idInvitacion: Int): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Map<String, Any>>()
        val sql = """
            SELECT p.telefono_senderista, s.nombre, s.id_beacon
            FROM participantes_invitacion p
            LEFT JOIN senderistas s ON p.email_senderista = s.email
            WHERE p.id_invitacion = ?
        """.trimIndent()
        try {
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setInt(1, idInvitacion)
                    pstmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val map = mapOf(
                                "telefono_senderista" to (rs.getString("telefono_senderista") ?: ""),
                                "nombre" to (rs.getString("nombre") ?: ""),
                                "id_beacon" to (rs.getString("id_beacon") ?: "")
                            )
                            list.add(map)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando participantes de la invitacion: ${e.message}", e)
        }
        list
    }

    /**
     * Elimina todos los datos de un senderista de la base de datos PostgreSQL.
     * Debido a la integridad referencial (ON DELETE CASCADE), esto también debería
     * eliminar las rutas asociadas en la tabla 'rutas' y otros registros dependientes.
     */
    suspend fun deleteAccountFromCloud(email: String): Unit = withContext(Dispatchers.IO) {
        val sql = "DELETE FROM senderistas WHERE email = ?"
        try {
            getConnection().use { conn ->
                conn.prepareStatement(sql).use { pstmt ->
                    pstmt.setString(1, email)
                    val affectedRows = pstmt.executeUpdate()
                    Log.d(TAG, "Cuenta eliminada de la nube ($email). Filas afectadas: $affectedRows")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar la cuenta de la nube: ${e.message}", e)
            throw e
        }
    }
}
