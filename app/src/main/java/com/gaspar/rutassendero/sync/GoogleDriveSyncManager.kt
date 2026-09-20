package com.gaspar.rutassendero.sync

import android.accounts.Account
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.gaspar.rutassendero.data.PuntoRutaEntity
import com.gaspar.rutassendero.data.RutaEntity
import com.gaspar.rutassendero.data.RutasDatabase
import com.gaspar.rutassendero.data.TrazaRutaEntity
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GoogleDriveSyncManager {
    private const val TAG = "GoogleDriveSync"
    private const val DRIVE_FILE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"

    data class DriveFile(val id: String, val name: String, val mimeType: String)

    /**
     * Verifica si el dispositivo está conectado a Wi-Fi.
     */
    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            info != null && info.isConnected && info.type == ConnectivityManager.TYPE_WIFI
        }
    }

    /**
     * Obtiene el token de acceso OAuth 2.0 en un hilo de fondo.
     */
    suspend fun getAccessToken(context: Context, account: Account): String = withContext(Dispatchers.IO) {
        GoogleAuthUtil.getToken(context, account, DRIVE_FILE_SCOPE)
    }

    /**
     * Genera el script SQL con los comandos INSERT para respaldar la ruta, sus puntos y su traza.
     */
    fun generateSqlScript(ruta: RutaEntity, puntos: List<PuntoRutaEntity>, traza: List<TrazaRutaEntity>): String {
        val sb = StringBuilder()
        sb.append("-- Copia de seguridad de la Ruta: ${ruta.nombre}\n")
        sb.append("-- Fecha de creación: ${ruta.fecha}\n")
        sb.append("-- Generado el: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")

        // Insertar Ruta con Estadísticas
        sb.append("INSERT OR REPLACE INTO rutas (id, nombre, fecha, latitud_orig, longitud_orig, descripcion, distancia_gps, distancia_beacons, fecha_hora_inicio, fecha_hora_fin, duracion_segundos, desnivel_positivo, desnivel_negativo, contador_subidas, contador_bajadas, conectividad) VALUES (")
        sb.append("'${escapeSql(ruta.id)}', ")
        sb.append("'${escapeSql(ruta.nombre)}', ")
        sb.append("'${escapeSql(ruta.fecha)}', ")

        sb.append("${ruta.latitudOrig}, ")
        sb.append("${ruta.longitudOrig}, ")
        sb.append("'${escapeSql(ruta.descripcion)}', ")
        sb.append("${ruta.distanciaGps}, ")
        sb.append("${ruta.distanciaBeacons}, ")
        if (ruta.fechaHoraInicio != null) sb.append("'${escapeSql(ruta.fechaHoraInicio)}', ") else sb.append("NULL, ")
        if (ruta.fechaHoraFin != null) sb.append("'${escapeSql(ruta.fechaHoraFin)}', ") else sb.append("NULL, ")
        sb.append("${ruta.duracionSegundos}, ")
        sb.append("${ruta.desnivelPositivo}, ")
        sb.append("${ruta.desnivelNegativo}, ")
        sb.append("${ruta.contadorSubidas}, ")
        sb.append("${ruta.contadorBajadas}, ")
        sb.append("${ruta.conectividad}")
        sb.append(");\n\n")

        // Insertar Puntos
        if (puntos.isNotEmpty()) {
            sb.append("-- Puntos de Ruta\n")
            for (p in puntos) {
                sb.append("INSERT OR REPLACE INTO puntos_ruta (id, id_ruta, fecha_hora, latitud, longitud, altitud, descripcion, foto_path, es_fin_ruta, tiene_conectividad) VALUES (")
                sb.append("${p.id}, ")
                sb.append("'${escapeSql(p.idRuta)}', ")
                sb.append("'${escapeSql(p.fechaHora)}', ")
                sb.append("${p.latitud}, ")
                sb.append("${p.longitud}, ")
                sb.append("${p.altitud}, ")
                sb.append("'${escapeSql(p.descripcion)}', ")
                if (p.fotoPath != null) {
                    val fileName = File(p.fotoPath).name
                    sb.append("'$fileName', ")
                } else {
                    sb.append("NULL, ")
                }
                sb.append(if (p.esFinRuta) 1 else 0)
                sb.append(", ")
                sb.append(if (p.tieneConectividad) 1 else 0)
                sb.append(");\n")
            }
        }

        // Insertar Traza de Ruta
        if (traza.isNotEmpty()) {
            sb.append("\n-- Traza de Ruta\n")
            for (t in traza) {
                sb.append("INSERT OR REPLACE INTO traza_ruta (id, id_ruta, orden, latitud, longitud, altitud) VALUES (")
                sb.append("${t.id}, ")
                sb.append("'${escapeSql(t.idRuta)}', ")
                sb.append("${t.orden}, ")
                sb.append("${t.latitud}, ")
                sb.append("${t.longitud}, ")
                sb.append("${t.altitud}")
                sb.append(");\n")
            }
        }
        return sb.toString()
    }

    private fun escapeSql(str: String): String {
        return str.replace("'", "''")
    }

    // --- CLIENTE REST HTTP DE GOOGLE DRIVE API V3 ---

    private suspend fun makeGetRequest(token: String, urlString: String): String = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        
        val code = conn.responseCode
        if (code == HttpURLConnection.HTTP_OK) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw Exception("GET error $code: $error")
        }
    }

    private suspend fun makePostRequest(token: String, urlString: String, body: String): String = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        
        conn.outputStream.use { out ->
            out.write(body.toByteArray(Charsets.UTF_8))
        }
        
        val code = conn.responseCode
        if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CREATED) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw Exception("POST error $code: $error")
        }
    }

    /**
     * Busca un archivo o carpeta en Google Drive por nombre y tipo.
     */
    suspend fun findFile(token: String, name: String, mimeType: String, parentId: String? = null): DriveFile? {
        var query = "name = '${name.replace("'", "\\'")}' and mimeType = '$mimeType' and trashed = false"
        if (parentId != null) {
            query += " and '$parentId' in parents"
        }
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,mimeType)"
        
        return try {
            val response = makeGetRequest(token, url)
            val filesJson = JSONObject(response).getJSONArray("files")
            if (filesJson.length() > 0) {
                val fileObj = filesJson.getJSONObject(0)
                DriveFile(fileObj.getString("id"), fileObj.getString("name"), fileObj.getString("mimeType"))
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error buscando archivo '$name': ${e.message}")
            null
        }
    }

    /**
     * Crea una carpeta en Google Drive.
     */
    suspend fun createFolder(token: String, name: String, parentId: String? = null): String {
        val parentsPart = if (parentId != null) ",\"parents\":[\"$parentId\"]" else ""
        val body = "{\"name\":\"${name.replace("\"", "\\\"")}\",\"mimeType\":\"application/vnd.google-apps.folder\"$parentsPart}"
        val response = makePostRequest(token, "https://www.googleapis.com/drive/v3/files", body)
        return JSONObject(response).getString("id")
    }

    /**
     * Sube un archivo a Google Drive usando Multipart Upload.
     */
    suspend fun uploadMultipartFile(
        token: String,
        name: String,
        mimeType: String,
        data: ByteArray,
        parentId: String? = null
    ): String = withContext(Dispatchers.IO) {
        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        val boundary = "Boundary-${System.currentTimeMillis()}"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")

        conn.outputStream.use { out ->
            val writer = out.writer(Charsets.UTF_8)
            
            // Parte 1: Metadatos
            writer.write("--$boundary\r\n")
            writer.write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            val parentsJson = if (parentId != null) ",\"parents\":[\"$parentId\"]" else ""
            writer.write("{\"name\":\"${name.replace("\"", "\\\"")}\"$parentsJson}\r\n")
            writer.write("--$boundary\r\n")
            
            // Parte 2: Datos binarios
            writer.write("Content-Type: $mimeType\r\n\r\n")
            writer.flush()
            
            out.write(data)
            out.flush()
            
            writer.write("\r\n--$boundary--\r\n")
            writer.flush()
        }

        val code = conn.responseCode
        if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_CREATED) {
            val response = conn.inputStream.bufferedReader().readText()
            JSONObject(response).getString("id")
        } else {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw Exception("Upload error $code: $error")
        }
    }

    /**
     * Lista todos los archivos (que no sean carpetas) dentro de una carpeta padre en Drive.
     */
    suspend fun listFilesInFolder(token: String, parentId: String): List<DriveFile> {
        val query = "'$parentId' in parents and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,mimeType)&pageSize=1000"
        
        val response = makeGetRequest(token, url)
        val filesJson = JSONObject(response).getJSONArray("files")
        val list = mutableListOf<DriveFile>()
        for (i in 0 until filesJson.length()) {
            val obj = filesJson.getJSONObject(i)
            list.add(DriveFile(obj.getString("id"), obj.getString("name"), obj.getString("mimeType")))
        }
        return list
    }

    /**
     * Lista todas las subcarpetas dentro de una carpeta padre en Drive.
     */
    suspend fun listSubfoldersInFolder(token: String, parentId: String): List<DriveFile> {
        val query = "'$parentId' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,mimeType)&pageSize=1000"
        
        val response = makeGetRequest(token, url)
        val filesJson = JSONObject(response).getJSONArray("files")
        val list = mutableListOf<DriveFile>()
        for (i in 0 until filesJson.length()) {
            val obj = filesJson.getJSONObject(i)
            list.add(DriveFile(obj.getString("id"), obj.getString("name"), obj.getString("mimeType")))
        }
        return list
    }

    /**
     * Descarga el contenido binario de un archivo de Drive.
     */
    suspend fun downloadFile(token: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val url = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        
        val code = conn.responseCode
        if (code == HttpURLConnection.HTTP_OK) {
            conn.inputStream.use { it.readBytes() }
        } else {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw Exception("Download error $code: $error")
        }
    }

    /**
     * Elimina un archivo o carpeta en Google Drive por su ID.
     */
    suspend fun deleteFile(token: String, fileId: String): Unit = withContext(Dispatchers.IO) {
        val url = URL("https://www.googleapis.com/drive/v3/files/$fileId")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Authorization", "Bearer $token")
        
        val code = conn.responseCode
        if (code != HttpURLConnection.HTTP_NO_CONTENT && code != HttpURLConnection.HTTP_OK) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: ""
            throw Exception("DELETE error $code: $error")
        }
        Log.d(TAG, "Archivo/Carpeta con ID $fileId eliminado de Google Drive.")
    }
}
