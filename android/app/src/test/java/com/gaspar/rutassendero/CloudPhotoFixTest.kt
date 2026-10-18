package com.gaspar.rutassendero

import com.gaspar.rutassendero.sync.SecureCredentialsManager
import com.gaspar.rutassendero.sync.CryptoUtils
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class CloudPhotoFixTest {

    @org.junit.Ignore("One-time DB migration script")
    @Test
    fun fixPorLaPiedraRoute() {
        println("=== DEOBFUSCATING CREDENTIALS ===")
        val uri = SecureCredentialsManager.getPostgresUri()
        val user = SecureCredentialsManager.getPostgresUser()
        val password = SecureCredentialsManager.getPostgresPassword()

        println("=== PARSING ORIGINAL PATHS FROM CLOUDINARY_PHOTO_MAPPING.MD ===")
        val mappingFile = File("C:/GASPAR/baches/rutas/cloudinary_photo_mapping.md")
        if (!mappingFile.exists()) {
            throw Exception("Mapping file not found at ${mappingFile.absolutePath}!")
        }
        val lines = mappingFile.readLines()
        val originalPaths = mutableMapOf<Int, String>()
        var inPorlaSection = false
        for (line in lines) {
            if (line.contains("### Ruta: **por la piedra** (ID: `PORLA`)")) {
                inPorlaSection = true
                continue
            }
            if (inPorlaSection && line.contains("Total de puntos:")) {
                inPorlaSection = false
            }
            if (inPorlaSection) {
                val parts = line.split("|")
                if (parts.size >= 5) {
                    val puntoStr = parts[1].trim()
                    val puntoIndex = puntoStr.toIntOrNull()
                    if (puntoIndex != null) {
                        val fotoField = parts[4].trim()
                        val cleanPath = if (fotoField.startsWith("`") && fotoField.endsWith("`")) {
                            fotoField.substring(1, fotoField.length - 1)
                        } else {
                            fotoField
                        }
                        originalPaths[puntoIndex] = cleanPath
                    }
                }
            }
        }
        println("Parsed original photo paths for ${originalPaths.size} points.")

        // Read current payload from database
        println("=== CONNECTING TO NEON POSTGRESQL ===")
        Class.forName("org.postgresql.Driver")
        val conn = DriverManager.getConnection(uri, user, password)
        
        println("=== FETCHING CURRENT PAYLOAD ===")
        val selectPstmt = conn.prepareStatement("SELECT datos_encriptados FROM rutas WHERE id = ?")
        selectPstmt.setString(1, "PORLA")
        val rs = selectPstmt.executeQuery()
        if (!rs.next()) {
            rs.close()
            selectPstmt.close()
            conn.close()
            throw Exception("Route PORLA not found in the database!")
        }
        val encryptedBase64 = rs.getString("datos_encriptados")
        rs.close()
        selectPstmt.close()

        println("=== DECRYPTING CURRENT PAYLOAD ===")
        val encryptedBytes = java.util.Base64.getDecoder().decode(encryptedBase64)
        val decryptedJsonStr = CryptoUtils.decrypt(encryptedBytes)
        val payload = JSONObject(decryptedJsonStr)
        val points = payload.getJSONArray("puntos")
        println("Found ${points.length()} points in database payload.")

        val cloudName = SecureCredentialsManager.getCloudinaryCloudName()

        println("=== REBUILDING CORRECT CLOUDINARY PHOTO URLS ===")
        var correctedCount = 0
        for (i in 0 until points.length()) {
            val pt = points.getJSONObject(i)
            val desc = pt.getString("descripcion")
            val currentFotoPath = if (pt.has("foto_path")) pt.getString("foto_path") else ""
            
            // Punto index in markdown is 1-based, matching array index i + 1
            val puntoIndexMarkdown = i + 1
            val originalPath = originalPaths[puntoIndexMarkdown]
            if (originalPath == null) {
                println("Point #$puntoIndexMarkdown | Desc: '$desc' | No original path found in markdown!")
                continue
            }

            if (originalPath.startsWith("/data/user/")) {
                // Reconstruct the correct Cloudinary URL using the filename of the local path
                val filename = File(originalPath).name
                val correctCloudUrl = "https://res.cloudinary.com/$cloudName/image/upload/rutas_senderismo/$filename"
                
                pt.put("foto_path", correctCloudUrl)
                correctedCount++
                println("Point #$puntoIndexMarkdown | Desc: '$desc' | Updated: $currentFotoPath -> $correctCloudUrl")
            } else {
                // Keep the original Cloud URL
                pt.put("foto_path", originalPath)
                println("Point #$puntoIndexMarkdown | Desc: '$desc' | Kept original Cloud URL: $originalPath")
            }
        }
        println("Rebuilt corrected paths for $correctedCount points.")

        // Encrypt the updated JSON payload
        println("=== ENCRYPTING NEW PAYLOAD ===")
        val updatedJsonStr = payload.toString()
        val newEncryptedBytes = CryptoUtils.encrypt(updatedJsonStr)
        val newEncryptedBase64 = java.util.Base64.getEncoder().encodeToString(newEncryptedBytes)

        // Write the updated payload back to database
        println("=== UPDATING DATABASE ROW ===")
        val updatePstmt = conn.prepareStatement("UPDATE rutas SET datos_encriptados = ? WHERE id = ?")
        updatePstmt.setString(1, newEncryptedBase64)
        updatePstmt.setString(2, "PORLA")
        val rowsAffected = updatePstmt.executeUpdate()
        println("Database update finished. Rows affected: $rowsAffected")
        updatePstmt.close()
        conn.close()

        println("=== DATABASE UPDATED SUCCESSFULLY FOR ROUTE PORLA ===")
    }

    @Test
    fun searchAllRoutesAndBambu() {
        val uri = SecureCredentialsManager.getPostgresUri()
        val user = SecureCredentialsManager.getPostgresUser()
        val password = SecureCredentialsManager.getPostgresPassword()

        Class.forName("org.postgresql.Driver")
        DriverManager.getConnection(uri, user, password).use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT id, nombre, email_creador, telefono_creador, datos_encriptados FROM rutas")
                while (rs.next()) {
                    val id = rs.getString("id")
                    val nombre = rs.getString("nombre")
                    val email = rs.getString("email_creador")
                    val tel = rs.getString("telefono_creador")
                    val enc = rs.getString("datos_encriptados")
                    println("RUTA: id=$id, nombre=$nombre, email=$email, tel=$tel")

                    if (!enc.isNullOrEmpty()) {
                        try {
                            val dec = CryptoUtils.decrypt(java.util.Base64.getDecoder().decode(enc))
                            if (dec.contains("bambu", ignoreCase = true)) {
                                println("   --> FOUND BAMBU in route $id ($nombre)!")
                            }
                        } catch (e: Exception) {
                            println("   --> Error decrypting $id: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}
