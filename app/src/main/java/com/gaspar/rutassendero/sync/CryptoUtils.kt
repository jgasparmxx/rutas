package com.gaspar.rutassendero.sync

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Módulo Criptográfico de Seguridad para Rutas Privadas y Sincronización en Nube.
 *
 * =========================================================================================
 * AUTORÍA CIENTÍFICA Y RECONOCIMIENTO CRIPTOGRÁFICO:
 * =========================================================================================
 * 1. Algoritmo Cifrador de Bloque Rijndael (AES-256):
 *    - Diseñado por los criptógrafos belgas Vincent Rijmen y Joan Daemen (1998).
 *    - Seleccionado por el NIST (National Institute of Standards and Technology) en octubre de 2000
 *      tras un proceso abierto internacional de 3 años y estandarizado como FIPS PUB 197.
 *    - Utiliza una red de sustitución-permutación (SPN) con 14 rondas para claves de 256 bits,
 *      resistente a criptoanálisis lineal y diferencial.
 *
 * 2. Modo de Autenticación y Cifrado Galois/Counter Mode (GCM):
 *    - Diseñado por David A. McGrew y John Viega (2004, Cisco Systems).
 *    - Combina el modo contador (Counter Mode - CTR) para confidencialidad con un código
 *      de autenticación de mensajes (GMAC) basado en la aritmética de cuerpos finitos
 *      (Galois Fields GF(2¹²⁸)), formulados históricamente por el matemático francés Évariste Galois (1832).
 *    - Estandarizado en NIST SP 800-38D (2007).
 *
 * =========================================================================================
 * JUSTIFICACIÓN DE SU USO EN LA APLICACIÓN:
 * - Cifrado Autenticado (AEAD): A diferencia de modos antiguos como CBC, GCM no solo cifra
 *   los datos, sino que genera una etiqueta de autenticación (Tag de 128 bits). Esto garantiza
 *   dos propiedades simultáneas:
 *     1. Confidencialidad: Nadie puede leer las coordenadas GPS de rutas privadas en tránsito ni en reposo.
 *     2. Integridad y No-Repudio: Si un byte es alterado accidental o maliciosamente en la nube,
 *        el descifrado falla inmediatamente por discrepancia en el polinomio de Galois, evitando
 *        inyecciones o rutas corruptas.
 * =========================================================================================
 */
object CryptoUtils {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    // Clave AES-256 precompartida (32 bytes). 
    // Puedes modificar estos valores para cambiar la clave de cifrado de la aplicación.
    val SECRET_KEY = byteArrayOf(
        0x5F, 0x2A, 0x1E, 0x7D, 0x4C, 0x3B, 0x0A, 0x7E,
        0x12, 0x5D, 0x4E, 0x3F, 0x2C, 0x1B, 0x0A, 0x6E,
        0x78, 0x69, 0x5A, 0x4B, 0x3C, 0x2D, 0x1E, 0x0F,
        0x7E, 0x6F, 0x5D, 0x4C, 0x3B, 0x2A, 0x19, 0x08
    )

    /**
     * Cifra un texto en plano usando AES-256 GCM con el IV generado aleatoriamente.
     * Retorna el arreglo de bytes combinado: IV + Texto Cifrado.
     */
    fun encrypt(plainText: String, secretKey: ByteArray = SECRET_KEY): ByteArray {
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv) // Generación segura de IV único

        val keySpec = SecretKeySpec(secretKey, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Concatenar el IV al inicio de los datos cifrados
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return combined
    }

    /**
     * Descifra un arreglo de bytes combinados extrayendo el IV y usando AES-256 GCM.
     * Retorna el texto plano original en formato String.
     */
    fun decrypt(combined: ByteArray, secretKey: ByteArray = SECRET_KEY): String {
        if (combined.size < IV_LENGTH_BYTES) {
            throw IllegalArgumentException("Los datos cifrados están corruptos o incompletos")
        }

        // Extraer el IV de los primeros 12 bytes
        val iv = ByteArray(IV_LENGTH_BYTES)
        System.arraycopy(combined, 0, iv, 0, iv.size)

        // Extraer el texto cifrado restante
        val cipherText = ByteArray(combined.size - iv.size)
        System.arraycopy(combined, iv.size, cipherText, 0, cipherText.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        val keySpec = SecretKeySpec(secretKey, "AES")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
