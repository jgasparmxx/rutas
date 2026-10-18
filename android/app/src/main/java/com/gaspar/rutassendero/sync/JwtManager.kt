package com.gaspar.rutassendero.sync

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Gestor de generación de tokens JWT (HS256) para autenticación con PostgREST.
 *
 * Emite un token criptográfico HMAC-SHA256 firmado con la clave compartida
 * con PostgREST (PGRST_JWT_SECRET en Render).
 *
 * Esto permite a PostgreSQL identificar al usuario mediante su rol ("senderista"),
 * email y teléfono, aplicando las políticas de seguridad a nivel de fila (RLS).
 */
object JwtManager {

    /**
     * Genera un token JWT (HS256) firmado para el senderista actual.
     *
     * @param email Correo electrónico del senderista.
     * @param telefono Teléfono del senderista.
     * @param validityDays Duración del token en días (por defecto 365 días / 1 año).
     * @return Cadena JWT codificada lista para enviar en encabezado 'Authorization: Bearer <token>'.
     */
    fun generateToken(
        email: String,
        telefono: String = "",
        rol: String = "S",
        idAgrupacion: String? = null,
        validityDays: Int = 365
    ): String {
        val cleanEmail = email.trim().lowercase()
        val cleanTelefono = telefono.trim()
        val cleanRol = rol.trim().uppercase().ifEmpty { "S" }

        val header = JSONObject().apply {
            put("alg", "HS256")
            put("typ", "JWT")
        }

        val nowSeconds = System.currentTimeMillis() / 1000L
        val expSeconds = nowSeconds + (validityDays.toLong() * 24L * 60L * 60L)

        val payload = JSONObject().apply {
            put("role", "senderista") // Rol de conexión en PostgreSQL/PostgREST
            put("rol", cleanRol)      // 'S' (Senderista), 'G' (Guía), 'A' (Admin Agrupación), 'O' (Dueño)
            put("email", cleanEmail)
            if (cleanTelefono.isNotEmpty()) {
                put("telefono", cleanTelefono)
            }
            if (!idAgrupacion.isNullOrBlank()) {
                put("id_agrupacion", idAgrupacion.trim())
            }
            put("iat", nowSeconds)
            put("exp", expSeconds)
        }

        val headerBase64 = base64UrlEncode(header.toString().toByteArray(StandardCharsets.UTF_8))
        val payloadBase64 = base64UrlEncode(payload.toString().toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$headerBase64.$payloadBase64"

        val secretBytes = SecureCredentialsManager.getJwtSecret().toByteArray(StandardCharsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val signatureBytes = mac.doFinal(signingInput.toByteArray(StandardCharsets.UTF_8))
        val signatureBase64 = base64UrlEncode(signatureBytes)

        return "$signingInput.$signatureBase64"
    }

    /**
     * Codificación Base64 URL-safe sin relleno ('='), requerida por el estándar JWT (RFC 7519).
     * Compatible tanto en entorno Android nativo como en pruebas unitarias JVM estándar.
     */
    private fun base64UrlEncode(bytes: ByteArray): String {
        return try {
            android.util.Base64.encodeToString(
                bytes, 
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            )
        } catch (e: Throwable) {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
