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
     * Calcula la distancia rectilínea acumulada entre beacons (puntos registrados).
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
            total += loc1.distanceTo(loc2)
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
            val closingDist = startLoc.distanceTo(endLoc)
            if (closingDist < 300f) {
                total += closingDist
            }
        }
        return total
    }

    /**
     * Calcula la distancia acumulada de la traza deduplicando coordenadas estáticas o redundantes.
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
            total += loc1.distanceTo(loc2)
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
