//
//  RouteStatsCalculator.swift
//  RutasSendero
//
//  Motor de cálculo estadístico, geodésico y topográfico de rutas.
//  Equivalente 1:1 de RouteStatsCalculator.kt en Android.
//

import Foundation
import CoreLocation

public struct ElevationStats: Sendable, Equatable {
    public let elevationGain: Double
    public let elevationLoss: Double
    public let climbsCount: Int
    public let descentsCount: Int
    
    public init(elevationGain: Double = 0.0, elevationLoss: Double = 0.0, climbsCount: Int = 0, descentsCount: Int = 0) {
        self.elevationGain = elevationGain
        self.elevationLoss = elevationLoss
        self.climbsCount = climbsCount
        self.descentsCount = descentsCount
    }
}

public enum RouteStatsCalculator {
    
    /// Calcula la distancia 3D entre dos coordenadas con altitud en metros.
    public static func distance3D(
        from lat1: Double, lon1: Double, alt1: Double,
        to lat2: Double, lon2: Double, alt2: Double
    ) -> Double {
        let loc1 = CLLocation(latitude: lat1, longitude: lon1)
        let loc2 = CLLocation(latitude: lat2, longitude: lon2)
        let dist2D = loc1.distance(from: loc2)
        let deltaAlt = alt2 - alt1
        
        // Si hay una diferencia altimétrica medible y razonable (< 500m por tramo)
        if abs(deltaAlt) > 0.05 && abs(deltaAlt) < 500.0 {
            return sqrt(dist2D * dist2D + deltaAlt * deltaAlt)
        }
        return dist2D
    }
    
    /// Calcula la distancia 3D acumulada entre los puntos de control (RoutePoints)
    public static func calculatePointsDistance(points: [RoutePoint]) -> Double {
        guard points.count >= 2 else { return 0.0 }
        var total: Double = 0.0
        
        for i in 0..<(points.count - 1) {
            let p1 = points[i]
            let p2 = points[i + 1]
            total += distance3D(
                from: p1.latitude, lon1: p1.longitude, alt1: p1.altitude,
                to: p2.latitude, lon2: p2.longitude, alt2: p2.altitude
            )
        }
        
        // Si es circuito (el primer y último punto están a menos de 300m y hay al menos 3 puntos)
        if points.count >= 3, let first = points.first, let last = points.last {
            let startLoc = CLLocation(latitude: first.latitude, longitude: first.longitude)
            let endLoc = CLLocation(latitude: last.latitude, longitude: last.longitude)
            let closingDist2D = startLoc.distance(from: endLoc)
            
            if closingDist2D < 300.0 {
                let closingDist3D = distance3D(
                    from: last.latitude, lon1: last.longitude, alt1: last.altitude,
                    to: first.latitude, lon2: first.longitude, alt2: first.altitude
                )
                total += closingDist3D
            }
        }
        
        return total
    }
    
    /// Calcula la distancia 3D de la traza deduplicando coordenadas estáticas o ruido jitter (< 1.5 metros).
    public static func calculateTraceDistance(tracePoints: [RouteTrace]) -> Double {
        guard tracePoints.count >= 2 else { return 0.0 }
        
        var uniquePoints: [RouteTrace] = []
        for pt in tracePoints {
            if let last = uniquePoints.last {
                if last.order == pt.order { continue }
                if last.latitude == pt.latitude && last.longitude == pt.longitude { continue }
                
                let loc1 = CLLocation(latitude: last.latitude, longitude: last.longitude)
                let loc2 = CLLocation(latitude: pt.latitude, longitude: pt.longitude)
                if loc1.distance(from: loc2) < 1.5 {
                    continue
                }
            }
            uniquePoints.append(pt)
        }
        
        guard uniquePoints.count >= 2 else { return 0.0 }
        var total: Double = 0.0
        
        for i in 0..<(uniquePoints.count - 1) {
            let p1 = uniquePoints[i]
            let p2 = uniquePoints[i + 1]
            total += distance3D(
                from: p1.latitude, lon1: p1.longitude, alt1: p1.altitude,
                to: p2.latitude, lon2: p2.longitude, alt2: p2.altitude
            )
        }
        
        return total
    }
    
    /// Calcula el desnivel positivo/negativo y contador de subidas y bajadas
    /// aplicando un umbral de supresión de ruido de ±3.0m para prevenir inflado por jitter GPS/barométrico.
    public static func calculateElevationStats(tracePoints: [RouteTrace]) -> ElevationStats {
        guard tracePoints.count >= 2 else { return ElevationStats() }
        
        var gain: Double = 0.0
        var loss: Double = 0.0
        var climbs = 0
        var descents = 0
        
        var lastAltitude = tracePoints[0].altitude
        var currentTrend: Int = 0 // +1 subiendo, -1 bajando, 0 neutral
        var trendAccumulatedDelta: Double = 0.0
        
        for i in 1..<tracePoints.count {
            let alt = tracePoints[i].altitude
            let diff = alt - lastAltitude
            
            // Umbral mínimo de elevación: ±3.0 metros
            if abs(diff) >= 3.0 {
                if diff > 0 {
                    gain += diff
                    if currentTrend != 1 {
                        trendAccumulatedDelta = diff
                        if trendAccumulatedDelta >= 5.0 {
                            climbs += 1
                            currentTrend = 1
                        }
                    } else {
                        trendAccumulatedDelta += diff
                    }
                } else {
                    loss += abs(diff)
                    if currentTrend != -1 {
                        trendAccumulatedDelta = abs(diff)
                        if trendAccumulatedDelta >= 5.0 {
                            descents += 1
                            currentTrend = -1
                        }
                    } else {
                        trendAccumulatedDelta += abs(diff)
                    }
                }
                lastAltitude = alt
            }
        }
        
        return ElevationStats(
            elevationGain: gain,
            elevationLoss: loss,
            climbsCount: climbs,
            descentsCount: descents
        )
    }
    
    /// Estimación de calorías quemadas (Margaria + Pandolf 1:1 Android RouteFitnessCalculator.kt)
    public static func calculateCalories(weightKg: Double = 70.0, distanceMeters: Double, elevationGainMeters: Double = 0.0) -> Double {
        let distanceKm = distanceMeters / 1000.0
        let distanceCalories = weightKg * distanceKm * 0.82
        let elevationCalories = weightKg * (elevationGainMeters / 100.0) * 1.6
        return distanceCalories + elevationCalories
    }
    
    /// Resumen de calorías 1:1 con Android RouteFitnessCalculator.getCaloriesSummary
    public static func getCaloriesSummary(
        distanceKm: Double,
        elevationGainMeters: Double,
        userWeightKg: Double? = nil
    ) -> String {
        if distanceKm <= 0 && elevationGainMeters <= 0 {
            return "Comienza a caminar para estimar las calorías."
        }
        
        if let weight = userWeightKg, weight > 0.0 {
            let exactCalories = Int(calculateCalories(weightKg: weight, distanceMeters: distanceKm * 1000.0, elevationGainMeters: elevationGainMeters))
            let weightText = weight.truncatingRemainder(dividingBy: 1.0) == 0 ? String(format: "%.0f", weight) : String(format: "%.1f", weight)
            return "Peso: \(weightText) kg  🔥 ~\(exactCalories) kcal"
        }
        
        let calLight = Int(calculateCalories(weightKg: 68.0, distanceMeters: distanceKm * 1000.0, elevationGainMeters: elevationGainMeters))
        let calMid = Int(calculateCalories(weightKg: 77.5, distanceMeters: distanceKm * 1000.0, elevationGainMeters: elevationGainMeters))
        let calHeavy = Int(calculateCalories(weightKg: 85.0, distanceMeters: distanceKm * 1000.0, elevationGainMeters: elevationGainMeters))
        
        return "🔥 Calorías Estimadas\n< 75 kg: ~\(calLight) kcal\n75 - 80 kg: ~\(calMid) kcal\n> 80 kg: ~\(calHeavy) kcal"
    }
    
    // MARK: - Formateadores para UI
    
    public static func formatDistance(_ meters: Double) -> String {
        if meters >= 1000.0 {
            return String(format: "%.2f km", meters / 1000.0)
        } else {
            return String(format: "%.0f m", meters)
        }
    }
    
    public static func formatDuration(_ seconds: Int64) -> String {
        let hours = seconds / 3600
        let minutes = (seconds % 3600) / 60
        let remainingSeconds = seconds % 60
        if hours > 0 {
            return String(format: "%02d:%02d:%02d", hours, minutes, remainingSeconds)
        } else {
            return String(format: "%02d:%02d", minutes, remainingSeconds)
        }
    }
    
    public static func formatSpeedKmh(_ kmh: Double) -> String {
        let speed = max(0.0, kmh)
        return String(format: "%.1f km/h", speed)
    }
    
    public static func formatElevation(_ meters: Double) -> String {
        return String(format: "%.0f m", meters)
    }
    
    // MARK: - Desnivel y Frases de Distancia / Altura al Siguiente Punto (1:1 Android RouteStatsCalculator.kt)
    
    /// Determina si un valor de altitud GPS es válido (descarta 0.0 por defecto o lecturas corruptas).
    public static func hasValidAltitude(_ alt: Double) -> Bool {
        return !alt.isNaN && !alt.isInfinite && abs(alt) > 0.1
    }
    
    /// Calcula la diferencia de altitud redondeada en metros (destino - origen).
    /// Retorna positivo si es subida, negativo si es bajada, 0 si es plano, o `nil` si falta altitud válida.
    public static func calculateElevationDeltaMeters(fromAlt: Double?, toAlt: Double?) -> Int? {
        guard let fromAlt = fromAlt, let toAlt = toAlt else { return nil }
        guard hasValidAltitude(fromAlt), hasValidAltitude(toAlt) else { return nil }
        let diff = toAlt - fromAlt
        if abs(diff) > 3000.0 { return nil }
        return Int(diff.rounded())
    }
    
    /// Estima cuánto falta por subir o bajar hacia el punto objetivo durante el guiado activo.
    /// Si existe un punto previo válido en la secuencia de guiado, interpola sobre el desnivel grabado
    /// del tramo para evitar desfases de geoide/elipsoide entre distintos teléfonos.
    public static func estimateRemainingElevationToTarget(
        prevPoint: RoutePoint?,
        targetPoint: RoutePoint,
        currentLocation: CLLocation?,
        remainingDistanceMeters: Double?
    ) -> Int? {
        guard hasValidAltitude(targetPoint.altitude) else { return nil }
        
        if let prevPoint = prevPoint, hasValidAltitude(prevPoint.altitude) {
            let totalDelta = targetPoint.altitude - prevPoint.altitude
            if abs(totalDelta) > 3000.0 { return nil }
            let prevLoc = CLLocation(latitude: prevPoint.latitude, longitude: prevPoint.longitude)
            let targetLoc = CLLocation(latitude: targetPoint.latitude, longitude: targetPoint.longitude)
            let segDist = prevLoc.distance(from: targetLoc)
            let ratio: Double
            if let rem = remainingDistanceMeters, segDist > 1.0 {
                ratio = min(max(rem / segDist, 0.0), 1.0)
            } else {
                ratio = 1.0
            }
            return Int((totalDelta * ratio).rounded())
        }
        
        if let loc = currentLocation, loc.verticalAccuracy >= 0, hasValidAltitude(loc.altitude) {
            return calculateElevationDeltaMeters(fromAlt: loc.altitude, toAlt: targetPoint.altitude)
        }
        
        return nil
    }
    
    public static func formatDistanceSpoken(_ distanceMeters: Double) -> String {
        if distanceMeters >= 1000.0 {
            return String(format: "%.1f kilómetros", distanceMeters / 1000.0)
        } else {
            let m = Int(distanceMeters)
            return m == 1 ? "1 metro" : "\(m) metros"
        }
    }
    
    public static func formatDistanceShort(_ distanceMeters: Double) -> String {
        if distanceMeters >= 1000.0 {
            return String(format: "%.1f km", distanceMeters / 1000.0)
        } else {
            return "\(Int(distanceMeters)) m"
        }
    }
    
    public static func formatElevationSpokenPart(_ deltaMeters: Int?) -> String {
        guard let deltaMeters = deltaMeters else { return "" }
        if deltaMeters > 0 {
            let unit = deltaMeters == 1 ? "metro" : "metros"
            return "subida son \(deltaMeters) \(unit)"
        } else if deltaMeters < 0 {
            let b = abs(deltaMeters)
            let unit = b == 1 ? "metro" : "metros"
            return "bajada son \(b) \(unit)"
        } else {
            return "en terreno plano"
        }
    }
    
    public static func formatElevationSpeechSuffix(_ deltaMeters: Int?) -> String {
        guard let deltaMeters = deltaMeters else { return "" }
        if deltaMeters > 0 {
            let unit = deltaMeters == 1 ? "metro" : "metros"
            return ", subida de \(deltaMeters) \(unit)"
        } else if deltaMeters < 0 {
            let b = abs(deltaMeters)
            let unit = b == 1 ? "metro" : "metros"
            return ", bajada de \(b) \(unit)"
        } else {
            return ", sin desnivel"
        }
    }
    
    public static func formatNextPointSimulationSpeech(distanceMeters: Double, deltaMeters: Int?) -> String {
        let distSpoken = formatDistanceSpoken(distanceMeters)
        let elevPart = formatElevationSpokenPart(deltaMeters)
        if !elevPart.isEmpty {
            return "Distancia a caminar al siguiente punto es \(distSpoken), \(elevPart)."
        } else {
            return "Distancia a caminar al siguiente punto es \(distSpoken)."
        }
    }
    
    public static func formatNextPointCardPhrase(distanceMeters: Double, deltaMeters: Int?) -> String {
        let distShort = formatDistanceShort(distanceMeters)
        guard let deltaMeters = deltaMeters else {
            return "Distancia a caminar al siguiente punto: \(distShort)"
        }
        if deltaMeters > 0 {
            let unit = deltaMeters == 1 ? "metro" : "metros"
            return "Distancia a caminar al siguiente punto: \(distShort), subida son \(deltaMeters) \(unit)"
        } else if deltaMeters < 0 {
            let b = abs(deltaMeters)
            let unit = b == 1 ? "metro" : "metros"
            return "Distancia a caminar al siguiente punto: \(distShort), bajada son \(b) \(unit)"
        } else {
            return "Distancia a caminar al siguiente punto: \(distShort), terreno plano"
        }
    }
    
    public static func formatElevationHeaderBadge(_ deltaMeters: Int?) -> String {
        guard let deltaMeters = deltaMeters else { return "" }
        if deltaMeters > 0 {
            return "↑ Subida \(deltaMeters) m"
        } else if deltaMeters < 0 {
            return "↓ Bajada \(abs(deltaMeters)) m"
        } else {
            return "Plano"
        }
    }
    
    /// Construye la frase de distancia y subida/bajada desde `current` hacia `next` (o `nil` si no hay siguiente punto).
    public static func nextPointPhrase(from current: RoutePoint, to next: RoutePoint?) -> String? {
        guard let next = next else { return nil }
        let loc1 = CLLocation(latitude: current.latitude, longitude: current.longitude)
        let loc2 = CLLocation(latitude: next.latitude, longitude: next.longitude)
        let dist = loc1.distance(from: loc2)
        let delta = calculateElevationDeltaMeters(fromAlt: current.altitude, toAlt: next.altitude)
        return formatNextPointCardPhrase(distanceMeters: dist, deltaMeters: delta)
    }
}
