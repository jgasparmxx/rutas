package com.gaspar.rutassendero.sync

import com.gaspar.rutassendero.data.TrazaRutaEntity
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64

object RouteDataPacker {

    /**
     * Encodes a list of trace points into a single string using the Encoded Polyline Algorithm.
     * This format is extremely lightweight and standard for mapping APIs.
     */
    fun packageRouteData(points: List<TrazaRutaEntity>): String {
        val result = StringBuilder()
        var lastLat = 0
        var lastLng = 0

        for (point in points) {
            val lat = (point.latitud * 1e5).toInt()
            val lng = (point.longitud * 1e5).toInt()

            val dLat = lat - lastLat
            val dLng = lng - lastLng

            encodeValue(dLat, result)
            encodeValue(dLng, result)

            lastLat = lat
            lastLng = lng
        }

        return result.toString()
    }

    private fun encodeValue(value: Int, result: StringBuilder) {
        var valToEncode = if (value < 0) (value shl 1).inv() else (value shl 1)
        while (valToEncode >= 0x20) {
            result.append(((0x20 or (valToEncode and 0x1f)) + 63).toChar())
            valToEncode = valToEncode ushr 5
        }
        result.append((valToEncode + 63).toChar())
    }

    /**
     * Alternative packaging: Converts the trace points list to JSON, compresses it with GZIP,
     * and encodes the result in Base64.
     */
    fun packageRouteDataGzipJson(points: List<TrazaRutaEntity>): String {
        val jsonArray = JSONArray()
        for (p in points) {
            val item = JSONObject()
            item.put("ord", p.orden)
            item.put("lat", p.latitud)
            item.put("lng", p.longitud)
            item.put("alt", p.altitud)
            jsonArray.put(item)
        }
        val jsonString = jsonArray.toString()
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(jsonString.toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }
}
