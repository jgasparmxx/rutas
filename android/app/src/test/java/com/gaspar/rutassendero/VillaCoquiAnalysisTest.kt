package com.gaspar.rutassendero

import com.gaspar.rutassendero.sync.CryptoUtils
import com.gaspar.rutassendero.sync.SecureCredentialsManager
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.sql.DriverManager
import java.sql.ResultSet
import kotlin.math.*

class VillaCoquiAnalysisTest {

    private fun haversineDist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun wgs84Dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a = 6378137.0
        val f = 1.0 / 298.257223563
        val b = 6356752.3142
        val l = Math.toRadians(lon2 - lon1)
        val u1 = atan((1.0 - f) * tan(Math.toRadians(lat1)))
        val u2 = atan((1.0 - f) * tan(Math.toRadians(lat2)))
        val sinU1 = sin(u1); val cosU1 = cos(u1)
        val sinU2 = sin(u2); val cosU2 = cos(u2)

        var lambda = l
        var lambdaP: Double
        var iterLimit = 100
        var cosSqAlpha: Double
        var sinSigma: Double
        var cos2SigmaM: Double
        var cosSigma: Double
        var sigma: Double

        do {
            val sinLambda = sin(lambda)
            val cosLambda = cos(lambda)
            sinSigma = sqrt((cosU2 * sinLambda).pow(2) + (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda).pow(2))
            if (sinSigma == 0.0) return 0.0
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1.0 - sinAlpha.pow(2)
            cos2SigmaM = if (cosSqAlpha != 0.0) cosSigma - 2.0 * sinU1 * sinU2 / cosSqAlpha else 0.0
            val c = f / 16.0 * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha))
            lambdaP = lambda
            lambda = l + (1.0 - c) * f * sinAlpha *
                    (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1.0 + 2.0 * cos2SigmaM.pow(2))))
        } while (abs(lambda - lambdaP) > 1e-12 && --iterLimit > 0)

        if (iterLimit == 0) return haversineDist(lat1, lon1, lat2, lon2)

        val uSq = cosSqAlpha * (a.pow(2) - b.pow(2)) / b.pow(2)
        val kA = 1.0 + uSq / 16384.0 * (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175.0 * uSq)))
        val kB = uSq / 1024.0 * (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)))
        val deltaSigma = kB * sinSigma * (cos2SigmaM + kB / 4.0 * (cosSigma * (-1.0 + 2.0 * cos2SigmaM.pow(2)) -
                kB / 6.0 * cos2SigmaM * (-3.0 + 4.0 * sinSigma.pow(2)) * (-3.0 + 4.0 * cos2SigmaM.pow(2))))
        return b * kA * (sigma - deltaSigma)
    }

    data class TrazaPt(val ord: Int, val lat: Double, val lon: Double, val alt: Double)
    data class BeaconPt(val id: String, val ord: Int, val desc: String, val lat: Double, val lon: Double, val alt: Double, val fechaHora: String, val esFin: Boolean)

    data class RouteData(
        val id: String,
        val nombre: String,
        val fecha: String,
        val fInicio: String?,
        val fFin: String?,
        val duracionSeg: Long,
        val distGpsBD: Double,
        val distBeaconsBD: Double,
        val desnivelPos: Double,
        val desnivelNeg: Double,
        val subidas: Int,
        val bajadas: Int,
        val desc: String,
        val emailCreador: String?,
        val traza: List<TrazaPt>,
        val beacons: List<BeaconPt>
    )

    private fun parseRoute(rs: ResultSet): RouteData {
        val id = rs.getString("id")
        val nombre = rs.getString("nombre")
        val fecha = rs.getString("fecha")
        val fInicio = rs.getString("fecha_hora_inicio")
        val fFin = rs.getString("fecha_hora_fin")
        val duracion = rs.getLong("duracion_segundos")
        val distGps = rs.getDouble("distancia_gps")
        val distBeacons = rs.getDouble("distancia_beacons")
        val desnivelPos = rs.getDouble("desnivel_positivo")
        val desnivelNeg = rs.getDouble("desnivel_negativo")
        val subidas = rs.getInt("contador_subidas")
        val bajadas = rs.getInt("contador_bajadas")
        val desc = rs.getString("descripcion") ?: ""
        val email = rs.getString("email_creador")
        val encData = rs.getString("datos_encriptados")

        val trazaList = mutableListOf<TrazaPt>()
        val beaconList = mutableListOf<BeaconPt>()

        if (encData != null && encData.isNotBlank()) {
            try {
                val encBytes = java.util.Base64.getDecoder().decode(encData)
                val decJson = CryptoUtils.decrypt(encBytes)
                val jsonObj = JSONObject(decJson)

                val trazaArr = jsonObj.optJSONArray("traza") ?: org.json.JSONArray()
                for (i in 0 until trazaArr.length()) {
                    val t = trazaArr.getJSONObject(i)
                    trazaList.add(TrazaPt(t.optInt("orden"), t.optDouble("latitud"), t.optDouble("longitud"), t.optDouble("altitud")))
                }

                val puntosArr = jsonObj.optJSONArray("puntos") ?: org.json.JSONArray()
                for (i in 0 until puntosArr.length()) {
                    val p = puntosArr.getJSONObject(i)
                    beaconList.add(
                        BeaconPt(
                            p.optString("id"),
                            p.optInt("orden", i + 1),
                            p.optString("descripcion"),
                            p.optDouble("latitud"),
                            p.optDouble("longitud"),
                            p.optDouble("altitud"),
                            p.optString("fecha_hora"),
                            p.optBoolean("es_fin_ruta")
                        )
                    )
                }
            } catch (e: Exception) {
                println("Error decrypting route $id: ${e.message}")
            }
        }

        return RouteData(
            id, nombre, fecha, fInicio, fFin, duracion, distGps, distBeacons,
            desnivelPos, desnivelNeg, subidas, bajadas, desc, email, trazaList, beaconList
        )
    }

    private fun calcAppTraceDistance(pts: List<TrazaPt>): Double {
        if (pts.size < 2) return 0.0
        val uniquePoints = mutableListOf<TrazaPt>()
        for (pt in pts) {
            if (uniquePoints.isEmpty()) {
                uniquePoints.add(pt)
            } else {
                val last = uniquePoints.last()
                if (last.ord == pt.ord) continue
                if (last.lat == pt.lat && last.lon == pt.lon) continue
                val dist = wgs84Dist(last.lat, last.lon, pt.lat, pt.lon)
                if (dist < 1.5) continue
                uniquePoints.add(pt)
            }
        }
        if (uniquePoints.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until uniquePoints.size - 1) {
            val dist2D = wgs84Dist(uniquePoints[i].lat, uniquePoints[i].lon, uniquePoints[i + 1].lat, uniquePoints[i + 1].lon)
            val dAlt = (uniquePoints[i + 1].alt - uniquePoints[i].alt).toFloat()
            val dist3D = if (abs(dAlt) > 0.05f && abs(dAlt) < 150f) {
                sqrt(dist2D * dist2D + dAlt * dAlt)
            } else {
                dist2D
            }
            total += dist3D
        }
        return total
    }

    private fun calcBeaconDistanceWithClosing(beacons: List<BeaconPt>): Double {
        if (beacons.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until beacons.size - 1) {
            val dist2D = wgs84Dist(beacons[i].lat, beacons[i].lon, beacons[i + 1].lat, beacons[i + 1].lon)
            val dAlt = (beacons[i + 1].alt - beacons[i].alt).toFloat()
            val dist3D = if (abs(dAlt) > 0.05f && abs(dAlt) < 500f) {
                sqrt(dist2D * dist2D + dAlt * dAlt)
            } else {
                dist2D
            }
            total += dist3D
        }
        if (beacons.size >= 3) {
            val closing2D = wgs84Dist(beacons.first().lat, beacons.first().lon, beacons.last().lat, beacons.last().lon)
            if (closing2D < 300.0) {
                val dAlt = (beacons.first().alt - beacons.last().alt).toFloat()
                val closing3D = if (abs(dAlt) > 0.05f && abs(dAlt) < 500f) {
                    sqrt(closing2D * closing2D + dAlt * dAlt)
                } else {
                    closing2D
                }
                println("  [Circuito cerrado detectado! Tramo de cierre: ${String.format("%.2f", closing3D)} m]")
                total += closing3D
            }
        }
        return total
    }

    @Test
    fun analyzeVillaCoquiComparison() {
        println("=================================================================")
        println("         ANÁLISIS COMPARATIVO DE RUTAS VILLA-COQUI Y COQUIMATLÁN ")
        println("=================================================================")

        val uri = SecureCredentialsManager.getPostgresUri()
        val user = SecureCredentialsManager.getPostgresUser()
        val password = SecureCredentialsManager.getPostgresPassword()

        Class.forName("org.postgresql.Driver")
        val conn = DriverManager.getConnection(uri, user, password)

        println("\n--- 1. ÚLTIMAS 15 RUTAS REGISTRADAS EN LA BASE DE DATOS ---")
        val stmt = conn.createStatement()
        val rsList = stmt.executeQuery(
            "SELECT id, nombre, fecha, distancia_gps, distancia_beacons, duracion_segundos, fecha_registro_nube " +
            "FROM rutas ORDER BY fecha_registro_nube DESC LIMIT 15"
        )
        while (rsList.next()) {
            println(
                "ID: ${rsList.getString("id").padEnd(10)} | " +
                "Nombre: ${rsList.getString("nombre").padEnd(25)} | " +
                "Fecha: ${rsList.getString("fecha")} | " +
                "DistGPS: ${String.format("%.1f", rsList.getDouble("distancia_gps"))} m | " +
                "DistBeacons: ${String.format("%.1f", rsList.getDouble("distancia_beacons"))} m | " +
                "Reg: ${rsList.getString("fecha_registro_nube")}"
            )
        }
        rsList.close()

        println("\n--- 2. BUSCANDO RUTAS 'villa' O 'coqui' ---")
        val pstmt = conn.prepareStatement(
            "SELECT * FROM rutas WHERE LOWER(nombre) LIKE '%villa%' OR LOWER(nombre) LIKE '%coqui%' " +
            "OR LOWER(id) LIKE '%villa%' OR LOWER(id) LIKE '%coqui%' " +
            "ORDER BY fecha_registro_nube DESC"
        )
        val rsSearch = pstmt.executeQuery()
        val foundRoutes = mutableListOf<RouteData>()
        while (rsSearch.next()) {
            val r = parseRoute(rsSearch)
            foundRoutes.add(r)
            println("Encontrada: ID=${r.id} | Nombre='${r.nombre}' | Fecha=${r.fecha} | Traza=${r.traza.size} pts | Beacons=${r.beacons.size} | DistGPS=${r.distGpsBD} m | DistBeacons=${r.distBeaconsBD} m")
        }
        rsSearch.close()

        if (foundRoutes.isEmpty()) {
            println("ALERTA: No se encontraron rutas con 'villa' o 'coqui'.")
            return
        }

        // Generate report file
        val reportSb = StringBuilder()
        reportSb.append("# INFORME COMPARATIVO DE RUTAS: VILLA-COQUI vs COQUIMATLÁN\n\n")

        for (r in foundRoutes) {
            println("\n=======================================================")
            println("DETALLE RUTA: ${r.nombre} (ID: ${r.id})")
            println("=======================================================")
            println("Fecha: ${r.fecha} | Inicio: ${r.fInicio} | Fin: ${r.fFin}")
            println("Duración: ${r.duracionSeg} seg (${r.duracionSeg / 60} min ${r.duracionSeg % 60} seg)")
            println("Distancia GPS (en BD): ${r.distGpsBD} m (${r.distGpsBD / 1000.0} km)")
            println("Distancia Beacons (en BD): ${r.distBeaconsBD} m (${r.distBeaconsBD / 1000.0} km)")
            println("Desnivel: +${r.desnivelPos} m / -${r.desnivelNeg} m (Subidas: ${r.subidas}, Bajadas: ${r.bajadas})")
            println("Cantidad puntos traza: ${r.traza.size}")
            println("Cantidad puntos balizas: ${r.beacons.size}")

            if (r.traza.isNotEmpty()) {
                val first = r.traza.first()
                val last = r.traza.last()
                val directDist = wgs84Dist(first.lat, first.lon, last.lat, last.lon)
                println("Punto inicial: (#${first.ord}) Lat=${first.lat}, Lon=${first.lon}, Alt=${first.alt} m")
                println("Punto final:   (#${last.ord}) Lat=${last.lat}, Lon=${last.lon}, Alt=${last.alt} m")
                println("Distancia línea recta inicio a fin: ${String.format("%.2f", directDist)} m (Circuito: ${directDist < 300.0})")

                val appDist = calcAppTraceDistance(r.traza)
                println("Distancia calculada por RouteStatsCalculator: ${String.format("%.2f", appDist)} m")

                // Saltos grandes (> 30m)
                var jumps30 = 0
                var jumps50 = 0
                var maxJump = 0.0
                var maxJumpIdx = 0
                for (i in 0 until r.traza.size - 1) {
                    val d = wgs84Dist(r.traza[i].lat, r.traza[i].lon, r.traza[i + 1].lat, r.traza[i + 1].lon)
                    if (d > 30.0) jumps30++
                    if (d > 50.0) jumps50++
                    if (d > maxJump) {
                        maxJump = d
                        maxJumpIdx = i
                    }
                }
                println("Saltos > 30m: $jumps30 | Saltos > 50m: $jumps50 | Salto máximo: ${String.format("%.2f", maxJump)} m (entre #${r.traza[maxJumpIdx].ord} y #${r.traza[maxJumpIdx + 1].ord})")

                // Distancias acumuladas con filtros
                val f0 = r.traza.zipWithNext { a, b -> wgs84Dist(a.lat, a.lon, b.lat, b.lon) }.sum()
                println("Distancia bruta total (filtro 0m): ${String.format("%.2f", f0)} m")
            }

            if (r.beacons.isNotEmpty()) {
                println("--- BALIZAS REGISTRADAS ---")
                for (b in r.beacons) {
                    println("  Baliza #${b.ord}: '${b.desc}' | Hora=${b.fechaHora} | Lat=${b.lat}, Lon=${b.lon}, Alt=${b.alt} | Fin=${b.esFin}")
                }
                val beaconCalc = calcBeaconDistanceWithClosing(r.beacons)
                println("Distancia Balizas calculada (con cierre si aplica): ${String.format("%.2f", beaconCalc)} m")
            }
        }

        // Compare if we have at least 2 routes
        if (foundRoutes.size >= 2) {
            val r1 = foundRoutes[0]
            val r2 = foundRoutes[1]

            println("\n=======================================================")
            println("            COMPARATIVA DIRECTA: RUTA 1 vs RUTA 2")
            println("=======================================================")
            println("Ruta 1: '${r1.nombre}' (${r1.id}) vs Ruta 2: '${r2.nombre}' (${r2.id})")
            println("Diferencia Distancia GPS en BD: ${abs(r1.distGpsBD - r2.distGpsBD)} m (${String.format("%.3f", abs(r1.distGpsBD - r2.distGpsBD) / 1000.0)} km)")
            println("Diferencia Distancia Beacons en BD: ${abs(r1.distBeaconsBD - r2.distBeaconsBD)} m (${String.format("%.3f", abs(r1.distBeaconsBD - r2.distBeaconsBD) / 1000.0)} km)")
            println("Diferencia Duración: ${abs(r1.duracionSeg - r2.duracionSeg)} seg (${abs(r1.duracionSeg - r2.duracionSeg) / 60} min)")
            println("Puntos Traza: ${r1.traza.size} vs ${r2.traza.size}")

            if (r1.traza.isNotEmpty() && r2.traza.isNotEmpty()) {
                val startDist = wgs84Dist(r1.traza.first().lat, r1.traza.first().lon, r2.traza.first().lat, r2.traza.first().lon)
                val endDist = wgs84Dist(r1.traza.last().lat, r1.traza.last().lon, r2.traza.last().lat, r2.traza.last().lon)
                println("Distancia entre PUNTOS DE INICIO de ambas rutas: ${String.format("%.2f", startDist)} m")
                println("Distancia entre PUNTOS DE FIN de ambas rutas: ${String.format("%.2f", endDist)} m")
            }
        }

        conn.close()
    }
}
