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

object RouteStatsCalculator {

    /**
     * Calcula la distancia tridimensional (3D) acumulada entre beacons (puntos registrados),
     * considerando la hipotenusa con el desnivel real del terreno.
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
}
