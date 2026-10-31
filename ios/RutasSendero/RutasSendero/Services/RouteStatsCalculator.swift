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
    
    /// Estimación de calorías quemadas: Peso (kg) * Distancia (km) * 1.036
    public static func calculateCalories(weightKg: Double = 70.0, distanceMeters: Double) -> Double {
        let distanceKm = distanceMeters / 1000.0
        return weightKg * distanceKm * 1.036
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
}
