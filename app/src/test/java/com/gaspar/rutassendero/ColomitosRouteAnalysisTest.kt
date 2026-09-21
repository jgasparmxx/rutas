package com.gaspar.rutassendero

import com.gaspar.rutassendero.sync.CryptoUtils
import com.gaspar.rutassendero.sync.SecureCredentialsManager
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.sql.DriverManager
import kotlin.math.*

class ColomitosRouteAnalysisTest {

    // Haversine distance in meters
    private fun haversineDist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // WGS84 Geodesic distance (Andoyer / Karney / Vincenty approximation matching Android Location.distanceBetween)
    private fun wgs84Dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val a = 6378137.0 // semi-major axis
        val f = 1.0 / 298.257223563 // flattening
        val b = 6356752.3142 // semi-minor axis
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
            if (sinSigma == 0.0) return 0.0 // co-incident points
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

    @Test
    fun analyzeColomitosRoute() {
        println("=== CONNECTING TO NEON POSTGRESQL ===")
        val uri = SecureCredentialsManager.getPostgresUri()
        val user = SecureCredentialsManager.getPostgresUser()
        val password = SecureCredentialsManager.getPostgresPassword()

        Class.forName("org.postgresql.Driver")
        val conn = DriverManager.getConnection(uri, user, password)

        println("=== 1. LISTING RECENT ROUTES ===")
        val stmtRecent = conn.createStatement()
        val rsRecent = stmtRecent.executeQuery(
            "SELECT id, nombre, fecha, distancia_gps, distancia_beacons, duracion_segundos, fecha_registro_nube FROM rutas ORDER BY fecha_registro_nube DESC LIMIT 10"
        )
        while (rsRecent.next()) {
            println("Route: ID=${rsRecent.getString("id")} | Name='${rsRecent.getString("nombre")}' | Date=${rsRecent.getString("fecha")} | DistGPS=${rsRecent.getDouble("distancia_gps")} m | DistBeacons=${rsRecent.getDouble("distancia_beacons")} m | Reg=${rsRecent.getString("fecha_registro_nube")}")
        }
        rsRecent.close()
        stmtRecent.close()

        println("\n=== 2. SEARCHING FOR COLOMITOS ROUTES ===")
        val pstmt = conn.prepareStatement(
            "SELECT id, nombre, fecha, latitud_orig, longitud_orig, descripcion, distancia_gps, distancia_beacons, " +
            "fecha_hora_inicio, fecha_hora_fin, duracion_segundos, desnivel_positivo, desnivel_negativo, " +
            "contador_subidas, contador_bajadas, datos_encriptados, fecha_registro_nube, email_creador " +
            "FROM rutas WHERE LOWER(nombre) LIKE '%colomito%' OR LOWER(id) LIKE '%colomito%' OR LOWER(descripcion) LIKE '%colomito%'"
        )
        val rs = pstmt.executeQuery()
        var foundAny = false

        while (rs.next()) {
            foundAny = true
            val id = rs.getString("id")
            val nombre = rs.getString("nombre")
            val fecha = rs.getString("fecha")
            val distGps = rs.getDouble("distancia_gps")
            val distBeacons = rs.getDouble("distancia_beacons")
            val duracion = rs.getLong("duracion_segundos")
            val desnivelPos = rs.getDouble("desnivel_positivo")
            val desnivelNeg = rs.getDouble("desnivel_negativo")
            val subidas = rs.getInt("contador_subidas")
            val bajadas = rs.getInt("contador_bajadas")
            val fInicio = rs.getString("fecha_hora_inicio")
            val fFin = rs.getString("fecha_hora_fin")
            val desc = rs.getString("descripcion")
            val encData = rs.getString("datos_encriptados")

            println("\n=======================================================")
            println("FOUND ROUTE: $id - '$nombre'")
            println("=======================================================")
            println("Fecha: $fecha | Inicio: $fInicio | Fin: $fFin")
            println("Duracion: $duracion seg (${duracion / 3600}h ${(duracion % 3600) / 60}m)")
            println("Distancia GPS reportada en BD: $distGps m (${distGps / 1000.0} km)")
            println("Distancia Beacons reportada en BD: $distBeacons m (${distBeacons / 1000.0} km)")
            println("Desnivel: +${desnivelPos}m / -${desnivelNeg}m | Subidas: $subidas, Bajadas: $bajadas")
            println("Descripcion:\n$desc")

            if (encData != null && encData.isNotBlank()) {
                val encBytes = java.util.Base64.getDecoder().decode(encData)
                val decJson = CryptoUtils.decrypt(encBytes)
                
                // Save decrypted JSON to scratch for in-depth analysis
                val dumpFile = File("C:/Users/jgaspar/.gemini/antigravity-ide/brain/e122c9a5-2699-467c-b42a-444df93982a5/scratch/route_${id}_colomitos.json")
                dumpFile.parentFile.mkdirs()
                dumpFile.writeText(decJson)
                println("Decrypted JSON saved to: ${dumpFile.absolutePath} (${decJson.length} bytes)")

                val jsonObj = JSONObject(decJson)
                val puntosArr = jsonObj.optJSONArray("puntos") ?: org.json.JSONArray()
                val trazaArr = jsonObj.optJSONArray("traza") ?: org.json.JSONArray()

                println("Total Beacons (Puntos): ${puntosArr.length()}")
                println("Total Traza GPS Points: ${trazaArr.length()}")

                // Analyze Puntos
                println("\n--- PUNTOS (BEACONS) ---")
                for (pIdx in 0 until puntosArr.length()) {
                    val p = puntosArr.getJSONObject(pIdx)
                    println("Punto #${pIdx + 1} | ID=${p.optString("id")} | Desc='${p.optString("descripcion")}' | Hora=${p.optString("fecha_hora")} | Lat=${p.optDouble("latitud")}, Lon=${p.optDouble("longitud")}, Alt=${p.optDouble("altitud")} | Fin=${p.optBoolean("es_fin_ruta")}")
                }

                // Analyze Traza Points
                println("\n--- TRAZA GPS ANALYSIS ---")
                data class Pt(val ord: Int, val lat: Double, val lon: Double, val alt: Double)
                val pts = mutableListOf<Pt>()
                for (tIdx in 0 until trazaArr.length()) {
                    val t = trazaArr.getJSONObject(tIdx)
                    pts.add(Pt(t.optInt("orden"), t.optDouble("latitud"), t.optDouble("longitud"), t.optDouble("altitud")))
                }

                if (pts.isNotEmpty()) {
                    println("First Traza Point: #${pts.first().ord} Lat=${pts.first().lat}, Lon=${pts.first().lon}, Alt=${pts.first().alt}")
                    println("Last Traza Point:  #${pts.last().ord} Lat=${pts.last().lat}, Lon=${pts.last().lon}, Alt=${pts.last().alt}")
                    
                    val directStartToEnd = wgs84Dist(pts.first().lat, pts.first().lon, pts.last().lat, pts.last().lon)
                    println("Distancia lineal inicio a fin: ${String.format("%.2f", directStartToEnd)} m (¿Es circuito/ida y vuelta?: ${directStartToEnd < 300.0})")

                    // 1. Raw cumulative distance (no filter)
                    var rawDist = 0.0
                    for (i in 0 until pts.size - 1) {
                        rawDist += wgs84Dist(pts[i].lat, pts[i].lon, pts[i + 1].lat, pts[i + 1].lon)
                    }
                    println("Distancia Traza SIN FILTRAR (raw wgs84): ${String.format("%.2f", rawDist)} m (${String.format("%.3f", rawDist / 1000.0)} km)")

                    // 2. App's RouteStatsCalculator logic (jitter < 1.5m and duplicate points)
                    val unique1_5 = mutableListOf<Pt>()
                    for (pt in pts) {
                        if (unique1_5.isEmpty()) {
                            unique1_5.add(pt)
                        } else {
                            val last = unique1_5.last()
                            if (last.ord == pt.ord) continue
                            if (last.lat == pt.lat && last.lon == pt.lon) continue
                            val d = wgs84Dist(last.lat, last.lon, pt.lat, pt.lon)
                            if (d < 1.5) continue
                            unique1_5.add(pt)
                        }
                    }
                    var distUnique1_5 = 0.0
                    for (i in 0 until unique1_5.size - 1) {
                        distUnique1_5 += wgs84Dist(unique1_5[i].lat, unique1_5[i].lon, unique1_5[i + 1].lat, unique1_5[i + 1].lon)
                    }
                    println("Distancia Traza con filtro 1.5m (App RouteStatsCalculator): ${String.format("%.2f", distUnique1_5)} m (${String.format("%.3f", distUnique1_5 / 1000.0)} km) [Puntos resultantes: ${unique1_5.size}/${pts.size}]")

                    // 3. Check distances with different smoothing / jitter filters
                    val filters = listOf(0.0, 1.0, 2.0, 3.0, 5.0, 8.0, 10.0, 12.0)
                    for (thr in filters) {
                        val filtered = mutableListOf<Pt>()
                        for (pt in pts) {
                            if (filtered.isEmpty()) {
                                filtered.add(pt)
                            } else {
                                val last = filtered.last()
                                val d = wgs84Dist(last.lat, last.lon, pt.lat, pt.lon)
                                if (d < thr) continue
                                filtered.add(pt)
                            }
                        }
                        var dTotal = 0.0
                        for (i in 0 until filtered.size - 1) {
                            dTotal += wgs84Dist(filtered[i].lat, filtered[i].lon, filtered[i + 1].lat, filtered[i + 1].lon)
                        }
                        println("  Filtro ${thr}m -> Dist: ${String.format("%.2f", dTotal)} m (${String.format("%.3f", dTotal / 1000.0)} km), Puntos: ${filtered.size}")
                    }

                    // 4. Large jumps / gaps analysis
                    println("\n--- DETECCIÓN DE SALTOS GRANDES O HUECOS (> 50m entre puntos consecutivos) ---")
                    var bigJumps = 0
                    for (i in 0 until pts.size - 1) {
                        val d = wgs84Dist(pts[i].lat, pts[i].lon, pts[i + 1].lat, pts[i + 1].lon)
                        if (d > 50.0) {
                            bigJumps++
                            println("Salto #${pts[i].ord} -> #${pts[i + 1].ord}: ${String.format("%.1f", d)} m (De [${pts[i].lat}, ${pts[i].lon}] a [${pts[i + 1].lat}, ${pts[i + 1].lon}])")
                        }
                    }
                    if (bigJumps == 0) println("No se encontraron saltos > 50m entre puntos consecutivos.")
                    else println("Total de saltos > 50m: $bigJumps")

                    // 5. Check step distances distribution
                    val stepDists = mutableListOf<Double>()
                    for (i in 0 until pts.size - 1) {
                        stepDists.add(wgs84Dist(pts[i].lat, pts[i].lon, pts[i + 1].lat, pts[i + 1].lon))
                    }
                    if (stepDists.isNotEmpty()) {
                        println("\n--- DISTRIBUCIÓN DE DISTANCIAS ENTRE PUNTOS CONSECUTIVOS ---")
                        println("Min dist: ${String.format("%.2f", stepDists.minOrNull() ?: 0.0)} m")
                        println("Max dist: ${String.format("%.2f", stepDists.maxOrNull() ?: 0.0)} m")
                        println("Avg dist: ${String.format("%.2f", stepDists.average())} m")
                        println("Count < 10m: ${stepDists.count { it < 10.0 }}")
                        println("Count 10m-15m: ${stepDists.count { it in 10.0..15.0 }}")
                        println("Count 15m-25m: ${stepDists.count { it in 15.0..25.0 }}")
                        println("Count 25m-50m: ${stepDists.count { it in 25.0..50.0 }}")
                        println("Count > 50m: ${stepDists.count { it > 50.0 }}")
                    }

                    // 6. Calculate beacon distance
                    var beaconTotal = 0.0
                    for (pIdx in 0 until puntosArr.length() - 1) {
                        val p1 = puntosArr.getJSONObject(pIdx)
                        val p2 = puntosArr.getJSONObject(pIdx + 1)
                        beaconTotal += wgs84Dist(p1.optDouble("latitud"), p1.optDouble("longitud"), p2.optDouble("latitud"), p2.optDouble("longitud"))
                    }
                    println("\nDistancia acumulada directa entre Beacons: ${String.format("%.2f", beaconTotal)} m (${String.format("%.3f", beaconTotal / 1000.0)} km)")

                    // 7. 3D Distance (incorporating altitude changes)
                    var dist3D = 0.0
                    for (i in 0 until pts.size - 1) {
                        val d2d = wgs84Dist(pts[i].lat, pts[i].lon, pts[i + 1].lat, pts[i + 1].lon)
                        val dAlt = pts[i + 1].alt - pts[i].alt
                        dist3D += sqrt(d2d.pow(2) + dAlt.pow(2))
                    }
                    val diff3D = dist3D - rawDist
                    println("\n--- ANÁLISIS 3D (DESNIVEL / HIPOTENUSA) ---")
                    println("Distancia 3D acumulada: ${String.format("%.2f", dist3D)} m (${String.format("%.3f", dist3D / 1000.0)} km)")
                    println("Diferencia 3D vs 2D: +${String.format("%.2f", diff3D)} m (+${String.format("%.2f", (diff3D / rawDist) * 100.0)}%)")

                    // 8. Catmull-Rom Spline Distance
                    fun catmullRomSmooth(points: List<Pt>, steps: Int = 6): List<Pt> {
                        if (points.size < 3) return points
                        val smoothed = mutableListOf<Pt>()
                        val n = points.size
                        val p = ArrayList<Pt>(n + 2)
                        p.add(points[0])
                        for (pt in points) p.add(pt)
                        p.add(points[n - 1])
                        for (i in 1 until n) {
                            val p0 = p[i - 1]; val p1 = p[i]; val p2 = p[i + 1]; val p3 = p[i + 2]
                            for (j in 0 until steps) {
                                val t = j.toDouble() / steps.toDouble()
                                val t2 = t * t
                                val t3 = t2 * t
                                val lat = 0.5 * ((2.0 * p1.lat) + (-p0.lat + p2.lat) * t + (2.0 * p0.lat - 5.0 * p1.lat + 4.0 * p2.lat - p3.lat) * t2 + (-p0.lat + 3.0 * p1.lat - 3.0 * p2.lat + p3.lat) * t3)
                                val lng = 0.5 * ((2.0 * p1.lon) + (-p0.lon + p2.lon) * t + (2.0 * p0.lon - 5.0 * p1.lon + 4.0 * p2.lon - p3.lon) * t2 + (-p0.lon + 3.0 * p1.lon - 3.0 * p2.lon + p3.lon) * t3)
                                val alt = 0.5 * ((2.0 * p1.alt) + (-p0.alt + p2.alt) * t + (2.0 * p0.alt - 5.0 * p1.alt + 4.0 * p2.alt - p3.alt) * t2 + (-p0.alt + 3.0 * p1.alt - 3.0 * p2.alt + p3.alt) * t3)
                                smoothed.add(Pt(0, lat, lng, alt))
                            }
                        }
                        smoothed.add(points.last())
                        return smoothed
                    }
                    val smoothedPts = catmullRomSmooth(pts, steps = 6)
                    var smoothedDist = 0.0
                    for (i in 0 until smoothedPts.size - 1) {
                        smoothedDist += wgs84Dist(smoothedPts[i].lat, smoothedPts[i].lon, smoothedPts[i + 1].lat, smoothedPts[i + 1].lon)
                    }
                    println("\n--- ANÁLISIS DE CURVATURA CON CATMULL-ROM SPLINE ---")
                    println("Puntos spline interpolados: ${smoothedPts.size}")
                    println("Distancia con Spline Suavizado: ${String.format("%.2f", smoothedDist)} m (${String.format("%.3f", smoothedDist / 1000.0)} km)")
                    println("Incremento por curvatura spline: +${String.format("%.2f", smoothedDist - rawDist)} m")

                    // 9. Angular changes and chord truncation analysis
                    fun initialBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
                        val lat1Rad = Math.toRadians(lat1)
                        val lat2Rad = Math.toRadians(lat2)
                        val dLonRad = Math.toRadians(lon2 - lon1)
                        val y = sin(dLonRad) * cos(lat2Rad)
                        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLonRad)
                        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
                    }

                    var angleGt30 = 0
                    var angleGt45 = 0
                    var angleGt60 = 0
                    var angleGt90 = 0
                    var angleGt120 = 0
                    var totalAngularChange = 0.0
                    var estimatedArcCurvatureLoss = 0.0

                    for (i in 0 until pts.size - 2) {
                        val b1 = initialBearing(pts[i].lat, pts[i].lon, pts[i + 1].lat, pts[i + 1].lon)
                        val b2 = initialBearing(pts[i + 1].lat, pts[i + 1].lon, pts[i + 2].lat, pts[i + 2].lon)
                        var diffAngle = abs(b2 - b1)
                        if (diffAngle > 180.0) diffAngle = 360.0 - diffAngle
                        totalAngularChange += diffAngle
                        if (diffAngle >= 30.0) angleGt30++
                        if (diffAngle >= 45.0) angleGt45++
                        if (diffAngle >= 60.0) angleGt60++
                        if (diffAngle >= 90.0) angleGt90++
                        if (diffAngle >= 120.0) angleGt120++

                        val segLen = wgs84Dist(pts[i + 1].lat, pts[i + 1].lon, pts[i + 2].lat, pts[i + 2].lon)
                        if (diffAngle > 10.0) {
                            val halfRad = Math.toRadians(diffAngle / 2.0)
                            val arcRatio = halfRad / sin(halfRad)
                            val extraArc = segLen * (arcRatio - 1.0)
                            estimatedArcCurvatureLoss += extraArc
                        }
                    }

                    println("\n--- ANÁLISIS DE GIROS Y RECTIFICACIÓN DE CURVAS (CHORD EFFECT) ---")
                    println("Total de cambios angulares evaluados: ${pts.size - 2}")
                    println("Giros >= 30°: $angleGt30 (${String.format("%.1f", (angleGt30.toDouble() / (pts.size - 2)) * 100.0)}%)")
                    println("Giros >= 45°: $angleGt45 (${String.format("%.1f", (angleGt45.toDouble() / (pts.size - 2)) * 100.0)}%)")
                    println("Giros >= 60°: $angleGt60 (${String.format("%.1f", (angleGt60.toDouble() / (pts.size - 2)) * 100.0)}%)")
                    println("Giros >= 90° (cerrados/zigzag): $angleGt90 (${String.format("%.1f", (angleGt90.toDouble() / (pts.size - 2)) * 100.0)}%)")
                    println("Giros >= 120° (horquillas extremas): $angleGt120")
                    println("Cambio angular total acumulado: ${String.format("%.1f", totalAngularChange)}°")
                    println("Pérdida geométrica estimada por cuerdas en curvas (12m chord vs arco circular): +${String.format("%.2f", estimatedArcCurvatureLoss)} m")

                    // 10. GPS Drift and Multipath Simulation on other apps (e.g. 1Hz sampling / Garmin / Strava)
                    println("\n--- MODELADO DE DISPOSITIVOS EXTERNOS (GARMIN / STRAVA / WIKILOC / SMARTWATCH) ---")
                    println("Duración total de la ruta: $duracion segundos (6h 29m 56s)")
                    println("Distancia de nuestra app (muestreo espacial de ~12.4m): ${String.format("%.2f", rawDist / 1000.0)} km")
                    println("Nuestra app ignora completamente el tiempo detenido/reposo (solo registra al desplazarse >= 12m reales).")
                    println("Si un dispositivo externo graba a 1 Hz (1 segundo) con deriva GPS estándar en cañadas/bosque:")
                    val driftsPerSecond = listOf(0.05, 0.08, 0.10, 0.12)
                    for (drift in driftsPerSecond) {
                        val addedDriftMeters = duracion * drift
                        val simulatedTotalKm = (rawDist + addedDriftMeters) / 1000.0
                        println("  Deriva de ${String.format("%.2f", drift)} m/s (típica bajo vegetación/pausas) -> +${String.format("%.2f", addedDriftMeters / 1000.0)} km -> Total reportado: ${String.format("%.2f", simulatedTotalKm)} km")
                    }
                }
            }
        }

        if (!foundAny) {
            println("NO ROUTES FOUND WITH NAME 'COLOMITOS'! Checking all routes in DB:")
            val allStmt = conn.createStatement()
            val allRs = allStmt.executeQuery("SELECT id, nombre, fecha, distancia_gps FROM rutas ORDER BY fecha DESC")
            while (allRs.next()) {
                println("  ID=${allRs.getString("id")} | Name='${allRs.getString("nombre")}' | Date=${allRs.getString("fecha")} | DistGPS=${allRs.getDouble("distancia_gps")}")
            }
            allRs.close()
            allStmt.close()
        }

        rs.close()
        pstmt.close()
        conn.close()
        println("\n=== ANALYSIS COMPLETE ===")
    }
}
