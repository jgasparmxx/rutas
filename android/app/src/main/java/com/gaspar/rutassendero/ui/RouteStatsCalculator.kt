package com.gaspar.rutassendero.ui

import android.location.Location
import com.gaspar.rutassendero.data.PuntoRutaEntity
import com.gaspar.rutassendero.data.TrazaRutaEntity
import kotlin.math.abs

data class ElevationStats(
    val desnivelPositivo: Double,
    val desnivelNegativo: Double,
    val contadorSubidas: Int,
    val contadorBajadas: Int
)

/**
 * Motor de Cálculo Estadístico, Geodésico y Topográfico de Rutas.
 *
 * =========================================================================================
 * AUTORÍA CIENTÍFICA Y RECONOCIMIENTO DE ALGORITMOS:
 * =========================================================================================
 * 1. Geodesia de Superficie Terrestre (Fórmula de Haversine & Modelo WGS-84):
 *    - James Inman (1835): Publicó la formulación trigonométrica del "Semiverseno" (Haversine)
 *      para calcular la distancia geodésica del círculo máximo entre dos coordenadas esféricas:
 *      hav(d/R) = hav(Δlat) + cos(lat1) · cos(lat2) · hav(Δlon)
 *    - Estandarizado en geodesia satelital moderna mediante el elipsoide WGS-84 (DoD / NGA, 1984).
 *
 * 2. Distancia Euclidiana Tridimensional (Pitágoras & Geometría Analítica):
 *    - Pitágoras de Samos (~570–495 a.C.) y René Descartes (1637, Geometría Analítica):
 *      Generalizado al espacio métrico tridimensional 3D (R³):
 *      Distancia_3D = √( Distancia_2D² + ΔAltitud² )
 *    - Permite calcular la hipotenusa verdadera del sendero en rampas, escaleras naturales
 *      y laderas de montaña, superando el error de proyección cenital plana del GPS convencional.
 *
 * 3. Filtrado Espacial por Umbral de Banda Muerta (Shannon & Nyquist):
 *    - Claude Shannon (1948) y Norbert Wiener (1949) en procesamiento de señales:
 *      Aplica un umbral de supresión de jitter (< 1.5 metros) para rechazar el ruido estático
 *      ocasionado por la dilución de precisión geométrica (GDOP) del receptor GPS.
 *
 * 4. Integración Discreta Altimétrica (Sumas de Riemann):
 *    - Bernhard Riemann (1854): Cálculo de desniveles positivos/negativos acumulados discretos
 *      (Δh = ∑ max(0, h_{i+1} - h_i)) a lo largo del perfil topográfico de la traza.
 * =========================================================================================
 */
object RouteStatsCalculator {

    /**
     * Calcula la distancia tridimensional (3D) acumulada entre beacons (puntos registrados),
     * considerando la hipotenusa euclidiana con el desnivel real del terreno.
     *
     * @param points Lista cronológica de hitos o puntos de control registrados.
     * @return Distancia acumulada real en metros considerando la orografía.
     */
    fun calculateBeaconDistance(points: List<PuntoRutaEntity>): Float {
        if (points.size < 2) return 0f
        var total = 0f
        val loc1 = Location("")
        val loc2 = Location("")
        for (i in 0 until points.size - 1) {
            loc1.latitude = points[i].latitud
            loc1.longitude = points[i].longitud
            loc2.latitude = points[i + 1].latitud
            loc2.longitude = points[i + 1].longitud
            val dist2D = loc1.distanceTo(loc2)
            val dAlt = (points[i + 1].altitud - points[i].altitud).toFloat()
            // Cálculo 3D: hipotenusa considerando desnivel
            val dist3D = if (abs(dAlt) > 0.05f && abs(dAlt) < 500f) {
                kotlin.math.sqrt(dist2D * dist2D + dAlt * dAlt)
            } else {
                dist2D
            }
            total += dist3D
        }
        // Si es circuito (el primer y último punto están a menos de 300 metros y hay al menos 3 puntos), sumamos el tramo de cierre
        if (points.size >= 3) {
            val startLoc = Location("").apply {
                latitude = points.first().latitud
                longitude = points.first().longitud
            }
            val endLoc = Location("").apply {
                latitude = points.last().latitud
                longitude = points.last().longitud
            }
            val closingDist2D = startLoc.distanceTo(endLoc)
            if (closingDist2D < 300f) {
                val dAltClosing = (points.first().altitud - points.last().altitud).toFloat()
                val closingDist3D = if (abs(dAltClosing) > 0.05f && abs(dAltClosing) < 500f) {
                    kotlin.math.sqrt(closingDist2D * closingDist2D + dAltClosing * dAltClosing)
                } else {
                    closingDist2D
                }
                total += closingDist3D
            }
        }
        return total
    }

    /**
     * Calcula la distancia acumulada tridimensional (3D) de la traza deduplicando coordenadas estáticas o redundantes,
     * incorporando la variación altimétrica en cada segmento.
     */
    fun calculateTraceDistance(tracePoints: List<TrazaRutaEntity>): Float {
        if (tracePoints.size < 2) return 0f
        
        // Deduplicar: filtrar puntos con coordenadas idénticas, orden duplicado o jitter (< 1.5 metros)
        val uniquePoints = mutableListOf<TrazaRutaEntity>()
        for (pt in tracePoints) {
            if (uniquePoints.isEmpty()) {
                uniquePoints.add(pt)
            } else {
                val last = uniquePoints.last()
                // Si el orden es igual, omitir
                if (last.orden == pt.orden) continue
                
                // Si las coordenadas son idénticas, omitir
                if (last.latitud == pt.latitud && last.longitud == pt.longitud) continue
                
                // Si están a menos de 1.5 metros (ruido de GPS estático), omitir
                val loc1 = Location("").apply {
                    latitude = last.latitud
                    longitude = last.longitud
                }
                val loc2 = Location("").apply {
                    latitude = pt.latitud
                    longitude = pt.longitud
                }
                if (loc1.distanceTo(loc2) < 1.5f) {
                    continue
                }
                
                uniquePoints.add(pt)
            }
        }

        if (uniquePoints.size < 2) return 0f
        var total = 0f
        val loc1 = Location("")
        val loc2 = Location("")
        for (i in 0 until uniquePoints.size - 1) {
            loc1.latitude = uniquePoints[i].latitud
            loc1.longitude = uniquePoints[i].longitud
            loc2.latitude = uniquePoints[i + 1].latitud
            loc2.longitude = uniquePoints[i + 1].longitud
            val dist2D = loc1.distanceTo(loc2)
            val dAlt = (uniquePoints[i + 1].altitud - uniquePoints[i].altitud).toFloat()
            // Cálculo tridimensional (3D) de la distancia: hipotenusa con el desnivel real del terreno
            val dist3D = if (abs(dAlt) > 0.05f && abs(dAlt) < 150f) {
                kotlin.math.sqrt(dist2D * dist2D + dAlt * dAlt)
            } else {
                dist2D
            }
            total += dist3D
        }
        return total
    }

    /**
     * Calcula el desnivel positivo/negativo acumulado y el número de tramos de ascenso/descenso.
     */
    fun calculateElevationStats(tracePoints: List<TrazaRutaEntity>): ElevationStats {
        var desnivelPos = 0.0
        var desnivelNeg = 0.0
        var subidas = 0
        var bajadas = 0

        if (tracePoints.size >= 2) {
            for (i in 0 until tracePoints.size - 1) {
                val diff = tracePoints[i + 1].altitud - tracePoints[i].altitud
                if (diff > 0.0) {
                    desnivelPos += diff
                    subidas++
                } else if (diff < 0.0) {
                    desnivelNeg += abs(diff)
                    bajadas++
                }
            }
        }
        return ElevationStats(desnivelPos, desnivelNeg, subidas, bajadas)
    }

    /**
     * Calcula las calorias quemadas estimadas basadas en el peso del senderista y la distancia.
     * Formula sugerida: Peso (kg) * Distancia (km) * 1.036
     */
    fun calculateCalories(pesoKg: Double, distanceMeters: Float): Double {
        val distanceKm = distanceMeters / 1000.0
        return pesoKg * distanceKm * 1.036
    }

    /**
     * Determina si un valor de altitud GPS es válido (descarta 0.0 por defecto o lecturas corruptas).
     */
    fun hasValidAltitude(alt: Double): Boolean {
        return !alt.isNaN() && !alt.isInfinite() && abs(alt) > 0.1
    }

    /**
     * Calcula la diferencia de altitud redondeada en metros (destino - origen).
     * Retorna positivo si es subida, negativo si es bajada, 0 si es plano, o null si falta altitud válida.
     */
    fun calculateElevationDeltaMeters(fromAlt: Double?, toAlt: Double?): Int? {
        if (fromAlt == null || toAlt == null) return null
        if (!hasValidAltitude(fromAlt) || !hasValidAltitude(toAlt)) return null
        val diff = toAlt - fromAlt
        if (abs(diff) > 3000.0) return null
        return kotlin.math.round(diff).toInt()
    }

    /**
     * Estima cuánto falta por subir o bajar hacia el punto objetivo durante el guiado activo.
     * Si existe un punto previo válido en la secuencia de guiado, interpola sobre el desnivel grabado
     * del tramo para evitar desfases de geoide/elipsoide entre distintos teléfonos.
     */
    fun estimateRemainingElevationToTarget(
        prevPoint: PuntoRutaEntity?,
        targetPoint: PuntoRutaEntity,
        currentLocation: Location?,
        remainingDistanceMeters: Float?
    ): Int? {
        if (!hasValidAltitude(targetPoint.altitud)) return null

        if (prevPoint != null && hasValidAltitude(prevPoint.altitud)) {
            val totalDelta = targetPoint.altitud - prevPoint.altitud
            if (abs(totalDelta) > 3000.0) return null
            val prevLoc = Location("").apply {
                latitude = prevPoint.latitud
                longitude = prevPoint.longitud
            }
            val targetLoc = Location("").apply {
                latitude = targetPoint.latitud
                longitude = targetPoint.longitud
            }
            val segDist = prevLoc.distanceTo(targetLoc)
            val ratio = if (remainingDistanceMeters != null && segDist > 1f) {
                (remainingDistanceMeters / segDist).coerceIn(0f, 1f)
            } else {
                1f
            }
            return kotlin.math.round(totalDelta * ratio).toInt()
        }

        if (currentLocation != null && currentLocation.hasAltitude() && hasValidAltitude(currentLocation.altitude)) {
            return calculateElevationDeltaMeters(currentLocation.altitude, targetPoint.altitud)
        }

        return null
    }

    fun formatDistanceSpoken(distanceMeters: Float): String {
        return if (distanceMeters >= 1000f) {
            String.format(java.util.Locale.US, "%.1f kilómetros", distanceMeters / 1000f)
        } else {
            val m = distanceMeters.toInt()
            if (m == 1) "1 metro" else "$m metros"
        }
    }

    fun formatDistanceShort(distanceMeters: Float): String {
        return if (distanceMeters >= 1000f) {
            String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000f)
        } else {
            "${distanceMeters.toInt()} m"
        }
    }

    fun formatElevationSpokenPart(deltaMeters: Int?): String {
        if (deltaMeters == null) return ""
        return when {
            deltaMeters > 0 -> {
                val unit = if (deltaMeters == 1) "metro" else "metros"
                "subida son $deltaMeters $unit"
            }
            deltaMeters < 0 -> {
                val b = abs(deltaMeters)
                val unit = if (b == 1) "metro" else "metros"
                "bajada son $b $unit"
            }
            else -> "en terreno plano"
        }
    }

    fun formatElevationSpeechSuffix(deltaMeters: Int?): String {
        if (deltaMeters == null) return ""
        return when {
            deltaMeters > 0 -> {
                val unit = if (deltaMeters == 1) "metro" else "metros"
                ", subida de $deltaMeters $unit"
            }
            deltaMeters < 0 -> {
                val b = abs(deltaMeters)
                val unit = if (b == 1) "metro" else "metros"
                ", bajada de $b $unit"
            }
            else -> ", sin desnivel"
        }
    }

    fun formatNextPointSimulationSpeech(distanceMeters: Float, deltaMeters: Int?): String {
        val distSpoken = formatDistanceSpoken(distanceMeters)
        val elevPart = formatElevationSpokenPart(deltaMeters)
        return if (elevPart.isNotEmpty()) {
            "Distancia a caminar al siguiente punto es $distSpoken, $elevPart."
        } else {
            "Distancia a caminar al siguiente punto es $distSpoken."
        }
    }

    fun formatNextPointCardPhrase(distanceMeters: Float, deltaMeters: Int?): String {
        val distShort = formatDistanceShort(distanceMeters)
        return when {
            deltaMeters == null -> "Distancia a caminar al siguiente punto: $distShort"
            deltaMeters > 0 -> {
                val unit = if (deltaMeters == 1) "metro" else "metros"
                "Distancia a caminar al siguiente punto: $distShort, subida son $deltaMeters $unit"
            }
            deltaMeters < 0 -> {
                val b = abs(deltaMeters)
                val unit = if (b == 1) "metro" else "metros"
                "Distancia a caminar al siguiente punto: $distShort, bajada son $b $unit"
            }
            else -> "Distancia a caminar al siguiente punto: $distShort, terreno plano"
        }
    }

    fun formatElevationHeaderBadge(deltaMeters: Int?): String {
        if (deltaMeters == null) return ""
        return when {
            deltaMeters > 0 -> "↑ Subida ${deltaMeters} m"
            deltaMeters < 0 -> "↓ Bajada ${abs(deltaMeters)} m"
            else -> "Plano"
        }
    }

    /**
     * Formatea una duración en segundos a una frase en español natural para locución por voz (TTS).
     * Ejemplo: "4 minutos", "1 hora y 12 minutos", "menos de un minuto".
     */
    fun formatDurationSpoken(seconds: Long): String {
        if (seconds <= 0) return "menos de un minuto"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val sec = seconds % 60
        return when {
            hours > 0 && minutes > 0 -> {
                val hUnit = if (hours == 1L) "1 hora" else "$hours horas"
                val mUnit = if (minutes == 1L) "1 minuto" else "$minutes minutos"
                "$hUnit y $mUnit"
            }
            hours > 0 && minutes == 0L -> {
                if (hours == 1L) "1 hora" else "$hours horas"
            }
            minutes > 0 -> {
                if (minutes == 1L) "1 minuto" else "$minutes minutos"
            }
            else -> {
                if (sec <= 15) "menos de un minuto" else "$sec segundos"
            }
        }
    }

    /**
     * Formatea una duración en segundos a un texto compacto para encabezados de interfaz de usuario.
     * Ejemplo: "4 min", "1h 12m", "< 1 min".
     */
    fun formatDurationShort(seconds: Long): String {
        if (seconds <= 0) return "< 1 min"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes} min"
            else -> "< 1 min"
        }
    }

    /**
     * Calcula la distancia tridimensional acumulada restante desde la posición actual del senderista
     * hasta la meta final de la ruta (recorriendo en orden los hitos intermedios).
     */
    fun calculateRemainingDistanceToMeta(
        userLoc: Location,
        currentTarget: PuntoRutaEntity,
        puntos: List<PuntoRutaEntity>,
        forward: Boolean
    ): Float {
        val targetLoc = Location("").apply {
            latitude = currentTarget.latitud
            longitude = currentTarget.longitud
        }
        var totalRemDist = userLoc.distanceTo(targetLoc)
        val curIdx = puntos.indexOfFirst { it.id == currentTarget.id }
        if (curIdx != -1) {
            if (forward) {
                for (i in curIdx until puntos.size - 1) {
                    val p1 = puntos[i]
                    val p2 = puntos[i + 1]
                    val l1 = Location("").apply { latitude = p1.latitud; longitude = p1.longitud }
                    val l2 = Location("").apply { latitude = p2.latitud; longitude = p2.longitud }
                    val d2d = l1.distanceTo(l2)
                    val dAlt = (p2.altitud - p1.altitud).toFloat()
                    val d3d = if (abs(dAlt) > 0.05f && abs(dAlt) < 500f) {
                        kotlin.math.sqrt(d2d * d2d + dAlt * dAlt)
                    } else {
                        d2d
                    }
                    totalRemDist += d3d
                }
            } else {
                for (i in curIdx downTo 1) {
                    val p1 = puntos[i]
                    val p2 = puntos[i - 1]
                    val l1 = Location("").apply { latitude = p1.latitud; longitude = p1.longitud }
                    val l2 = Location("").apply { latitude = p2.latitud; longitude = p2.longitud }
                    val d2d = l1.distanceTo(l2)
                    val dAlt = (p2.altitud - p1.altitud).toFloat()
                    val d3d = if (abs(dAlt) > 0.05f && abs(dAlt) < 500f) {
                        kotlin.math.sqrt(d2d * d2d + dAlt * dAlt)
                    } else {
                        d2d
                    }
                    totalRemDist += d3d
                }
            }
        }
        return totalRemDist
    }
}
