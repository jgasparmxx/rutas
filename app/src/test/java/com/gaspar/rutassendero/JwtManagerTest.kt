package com.gaspar.rutassendero

import com.gaspar.rutassendero.sync.JwtManager
import com.gaspar.rutassendero.sync.SecureCredentialsManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class JwtManagerTest {

    @Test
    fun testGenerateTokenStructureAndSignature() {
        val email = "jgasparmxx@gmail.com"
        val telefono = "9616603835"
        val rol = "G"
        val agrupacion = "MH"

        val token = JwtManager.generateToken(email, telefono, rol, agrupacion)
        assertNotNull(token)

        val parts = token.split(".")
        assertEquals("El token JWT debe tener exactamente 3 partes (Header, Payload, Signature)", 3, parts.size)

        // 1. Verificar Header
        val headerBytes = Base64.getUrlDecoder().decode(parts[0])
        val headerJson = JSONObject(String(headerBytes, StandardCharsets.UTF_8))
        assertEquals("HS256", headerJson.getString("alg"))
        assertEquals("JWT", headerJson.getString("typ"))

        // 2. Verificar Payload
        val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
        val payloadJson = JSONObject(String(payloadBytes, StandardCharsets.UTF_8))
        assertEquals("senderista", payloadJson.getString("role"))
        assertEquals("G", payloadJson.getString("rol"))
        assertEquals("MH", payloadJson.getString("id_agrupacion"))
        assertEquals(email, payloadJson.getString("email"))
        assertEquals(telefono, payloadJson.getString("telefono"))
        assertTrue("exp debe ser mayor que now", payloadJson.getLong("exp") > System.currentTimeMillis() / 1000L)

        // 3. Verificar Firma Criptográfica con la clave secreta
        val signingInput = "${parts[0]}.${parts[1]}"
        val secretBytes = SecureCredentialsManager.getJwtSecret().toByteArray(StandardCharsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val expectedSignature = mac.doFinal(signingInput.toByteArray(StandardCharsets.UTF_8))
        val expectedSignatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedSignature)

        assertEquals("La firma del token debe coincidir con la firma esperada", expectedSignatureBase64, parts[2])
    }
}
