package com.gaspar.rutassendero.sync

object SecureCredentialsManager {
    // Clave de ofuscación XOR
    private const val XOR_KEY: Byte = 0x5A

    // Credenciales ofuscadas mediante XOR con la clave 0x5A
    private val OBFUSCATED_PG_URI = byteArrayOf(
        48, 62, 56, 57, 96, 42, 53, 41, 46, 61, 40, 63, 41, 43, 54, 96, 
        117, 117, 63, 42, 119, 53, 40, 59, 52, 61, 63, 119, 59, 40, 46, 
        119, 59, 35, 42, 54, 59, 34, 108, 42, 116, 57, 119, 111, 116, 47, 
        41, 119, 63, 59, 41, 46, 119, 104, 116, 59, 45, 41, 116, 52, 63, 
        53, 52, 116, 46, 63, 57, 50, 96, 111, 110, 105, 104, 117, 52, 63, 
        53, 52, 62, 56, 101, 41, 41, 54, 55, 53, 62, 63, 103, 40, 63, 43, 
        47, 51, 40, 63
    )

    private val OBFUSCATED_PG_USER = byteArrayOf(
        52, 63, 53, 52, 62, 56, 5, 53, 45, 52, 63, 40
    )

    private val OBFUSCATED_PG_PASS = byteArrayOf(
        52, 42, 61, 5, 28, 25, 53, 104, 31, 60, 19, 8, 34, 57, 40, 108
    )

    private val OBFUSCATED_CLOUDINARY_CLOUD = byteArrayOf(
        52, 46, 109, 63, 34, 109, 53, 56
    )

    private val OBFUSCATED_CLOUDINARY_KEY = byteArrayOf(
        110, 109, 105, 108, 107, 109, 109, 99, 111, 110, 107, 98, 105, 99, 104
    )

    private val OBFUSCATED_CLOUDINARY_SECRET = byteArrayOf(
        35, 59, 51, 61, 110, 104, 45, 2, 107, 61, 22, 28, 119, 
        105, 46, 119, 54, 44, 107, 54, 104, 57, 43, 111, 23, 10, 27
    )

    // Clave secreta para firma de tokens JWT (PGRST_JWT_SECRET en PostgREST / Render)
    private val OBFUSCATED_JWT_SECRET = byteArrayOf(
        55, 51, 5, 57, 54, 59, 44, 63, 5, 41, 63, 57, 40, 63, 46, 59, 
        5, 41, 47, 42, 63, 40, 5, 41, 63, 61, 47, 40, 59, 5, 40, 47, 
        46, 59, 41, 5, 41, 63, 52, 62, 63, 40, 53, 5, 104, 106, 104, 108
    )

    /**
     * De-ofusca un arreglo de bytes utilizando la clave XOR.
     */
    private fun deobfuscate(data: ByteArray): String {
        val result = ByteArray(data.size)
        for (i in data.indices) {
            result[i] = (data[i].toInt() xor XOR_KEY.toInt()).toByte()
        }
        return String(result, Charsets.UTF_8)
    }

    /**
     * URL base de la API REST de PostgREST desplegada en Render (o servidor propio).
     */
    fun getPostgrestBaseUrl(): String = "https://postgrest-latest-09dh.onrender.com"

    fun getJwtSecret(): String = deobfuscate(OBFUSCATED_JWT_SECRET)

    fun getPostgresUri(): String = deobfuscate(OBFUSCATED_PG_URI)
    fun getPostgresUser(): String = deobfuscate(OBFUSCATED_PG_USER)
    fun getPostgresPassword(): String = deobfuscate(OBFUSCATED_PG_PASS)

    fun getCloudinaryCloudName(): String = deobfuscate(OBFUSCATED_CLOUDINARY_CLOUD)
    fun getCloudinaryApiKey(): String = deobfuscate(OBFUSCATED_CLOUDINARY_KEY)
    fun getCloudinaryApiSecret(): String = deobfuscate(OBFUSCATED_CLOUDINARY_SECRET)

    /**
     * Retorna la configuración de Cloudinary para inicializar el MediaManager.
     */
    fun getCloudinaryConfig(): Map<String, String> {
        return mapOf(
            "cloud_name" to getCloudinaryCloudName(),
            "api_key" to getCloudinaryApiKey(),
            "api_secret" to getCloudinaryApiSecret()
        )
    }
}
