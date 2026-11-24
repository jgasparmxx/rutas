//
//  HikingGuideService.swift
//  RutasSendero
//
//  Servicio de cálculo de tiempos estimados (ETA), ritmo de caminata (Pace)
//  y evaluación de puntualidad para el Modo Guía.
//

import Foundation
import CoreLocation
import Observation

/// Resultado de la evaluación temporal de llegada respecto a la hora estimada.
public enum ArrivalTiming: Equatable, Sendable {
    case early(seconds: Double, formattedTime: String)
    case late(seconds: Double, formattedTime: String)
    case onTime(diffSeconds: Double)
}

/// Filtro y suavizado de velocidad del senderista caminando mediante Media Móvil Exponencial (EMA).
public final class HikingPaceCalculator: @unchecked Sendable {
    /// Velocidad base estándar de caminata humana: 1.11 m/s (4.0 km/h).
    public static let defaultWalkingSpeedMps: Double = 1.11
    /// Umbral mínimo de caminata humana (por debajo se considera detenido / pausa): 0.35 m/s (~1.26 km/h).
    public static let minHumanWalkingSpeedMps: Double = 0.35
    /// Umbral máximo de caminata rápida / marcha atlética en sendero: 3.50 m/s (~12.6 km/h).
    public static let maxHumanWalkingSpeedMps: Double = 3.50
    /// Factor de suavizado alfa para la Media Móvil Exponencial (rango 0.2 - 0.3).
    public static let emaAlpha: Double = 0.25

    private(set) public var smoothedSpeedMps: Double = 1.11
    private(set) public var rawSpeedMps: Double = 0.0
    private var hasInitialFix: Bool = false

    public init(initialSpeedMps: Double = 1.11) {
        self.smoothedSpeedMps = initialSpeedMps
        self.rawSpeedMps = initialSpeedMps
    }

    /// Reinicia el calculador de ritmo a un valor base.
    public func reset(initialSpeedMps: Double = 1.11) {
        self.smoothedSpeedMps = initialSpeedMps
        self.rawSpeedMps = initialSpeedMps
        self.hasInitialFix = false
    }

    /// Actualiza la velocidad suavizada a partir de la velocidad bruta del GPS.
    /// Si está detenido (< 0.35 m/s) o hay lecturas corruptas (< 0), se actualiza raw pero
    /// para proyecciones se utilizará la velocidad efectiva de caminata.
    @discardableResult
    public func updateSpeed(gpsSpeedMps: Double) -> Double {
        self.rawSpeedMps = max(0.0, gpsSpeedMps)

        guard gpsSpeedMps >= Self.minHumanWalkingSpeedMps else {
            // Senderista detenido o lectura en reposo. Mantenemos el suavizado previo o decae suavemente
            return smoothedSpeedMps
        }

        let clamped = min(gpsSpeedMps, Self.maxHumanWalkingSpeedMps)
        if !hasInitialFix {
            smoothedSpeedMps = clamped
            hasInitialFix = true
        } else {
            smoothedSpeedMps = (Self.emaAlpha * clamped) + ((1.0 - Self.emaAlpha) * smoothedSpeedMps)
        }
        return smoothedSpeedMps
    }

    /// Velocidad efectiva de marcha para el cálculo de ETAs:
    /// Si el senderista está detenido o en reposo (< 0.35 m/s), retorna el valor base de caminata (1.11 m/s = 4.0 km/h)
    /// para evitar divisiones entre cero y proyecciones infinitas.
    public var effectiveWalkingSpeedMps: Double {
        if smoothedSpeedMps < Self.minHumanWalkingSpeedMps {
            return Self.defaultWalkingSpeedMps
        }
        return smoothedSpeedMps
    }

    public var effectiveWalkingSpeedKmh: Double {
        effectiveWalkingSpeedMps * 3.6
    }

    public var currentSpeedKmh: Double {
        rawSpeedMps * 3.6
    }
}

/// Servicio que orquesta el cómputo de tiempos estimados, marcas esperadas y evaluación de llegada.
@Observable
public final class HikingGuideService: @unchecked Sendable {
    
    // MARK: - Estado de Ritmo y Velocidad
    public let paceCalculator = HikingPaceCalculator()
    
    public var currentSpeedKmh: Double {
        paceCalculator.currentSpeedKmh
    }
    
    public var effectiveSpeedKmh: Double {
        paceCalculator.effectiveWalkingSpeedKmh
    }
    
    // MARK: - Hito / Punto de Paso Objetivo Actual
    public var targetDistanceMeters: Double = 0.0
    public var targetEtaSeconds: Double = 0.0
    public var targetExpectedArrivalTime: Date? = nil
    public var targetExpectedSpeedKmh: Double = 4.0
    
    // MARK: - Meta Final Global
    public var metaRemainingDistanceMeters: Double = 0.0
    public var metaEtaSeconds: Double = 0.0
    public var metaExpectedArrivalTime: Date? = nil
    public var metaExpectedSpeedKmh: Double = 4.0
    
    // MARK: - Inicialización
    public init() {}
    
    /// Reinicia completamente el estado del servicio de guiado.
    public func reset() {
        paceCalculator.reset()
        targetDistanceMeters = 0.0
        targetEtaSeconds = 0.0
        targetExpectedArrivalTime = nil
        targetExpectedSpeedKmh = 4.0
        metaRemainingDistanceMeters = 0.0
        metaEtaSeconds = 0.0
        metaExpectedArrivalTime = nil
        metaExpectedSpeedKmh = 4.0
    }
    
    /// Inicializa la guía completa al inicio de una ruta (o al arrancar Modo Guía).
    public func startGuidance(
        currentLocation: CLLocation?,
        target: RoutePoint,
        targetIndex: Int,
        points: [RoutePoint],
        forward: Bool
    ) {
        reset()
        
        let userLoc = currentLocation ?? CLLocation(latitude: target.latitude, longitude: target.longitude)
        let tLoc = CLLocation(latitude: target.latitude, longitude: target.longitude)
        let distToTarget = userLoc.distance(from: tLoc)
        let distRemainingSegments = calculateRemainingSegmentsDistance(fromIndex: targetIndex, points: points, forward: forward)
        let totalRemaining = distToTarget + distRemainingSegments
        
        let speedMps = paceCalculator.effectiveWalkingSpeedMps
        let speedKmh = paceCalculator.effectiveWalkingSpeedKmh
        
        let tEta = distToTarget / speedMps
        let mEta = totalRemaining / speedMps
        
        let now = Date()
        self.targetDistanceMeters = distToTarget
        self.targetEtaSeconds = tEta
        self.targetExpectedArrivalTime = now.addingTimeInterval(tEta)
        self.targetExpectedSpeedKmh = speedKmh
        
        self.metaRemainingDistanceMeters = totalRemaining
        self.metaEtaSeconds = mEta
        self.metaExpectedArrivalTime = now.addingTimeInterval(mEta)
        self.metaExpectedSpeedKmh = speedKmh
    }
    
    /// Avanza al siguiente hito de la ruta registrando las marcas esperadas para el nuevo tramo.
    public func advanceToNextWaypoint(
        currentLocation: CLLocation?,
        newTarget: RoutePoint,
        newTargetIndex: Int,
        points: [RoutePoint],
        forward: Bool
    ) {
        let userLoc = currentLocation ?? CLLocation(latitude: newTarget.latitude, longitude: newTarget.longitude)
        let tLoc = CLLocation(latitude: newTarget.latitude, longitude: newTarget.longitude)
        let distToTarget = userLoc.distance(from: tLoc)
        let distRemainingSegments = calculateRemainingSegmentsDistance(fromIndex: newTargetIndex, points: points, forward: forward)
        let totalRemaining = distToTarget + distRemainingSegments
        
        let speedMps = paceCalculator.effectiveWalkingSpeedMps
        let speedKmh = paceCalculator.effectiveWalkingSpeedKmh
        
        let tEta = distToTarget / speedMps
        let mEta = totalRemaining / speedMps
        
        let now = Date()
        self.targetDistanceMeters = distToTarget
        self.targetEtaSeconds = tEta
        self.targetExpectedArrivalTime = now.addingTimeInterval(tEta)
        self.targetExpectedSpeedKmh = speedKmh
        
        self.metaRemainingDistanceMeters = totalRemaining
        self.metaEtaSeconds = mEta
        // Conservamos la hora estimada de meta original si ya fue fijada; en caso de que no exista, la establecemos
        if self.metaExpectedArrivalTime == nil {
            self.metaExpectedArrivalTime = now.addingTimeInterval(mEta)
            self.metaExpectedSpeedKmh = speedKmh
        }
    }
    
    /// Actualiza periódicamente con cada nueva lectura GPS la velocidad suavizada y los ETAs dinámicos.
    public func updateProgress(
        location: CLLocation,
        target: RoutePoint,
        targetIndex: Int,
        points: [RoutePoint],
        forward: Bool
    ) {
        // Actualizar ritmo
        paceCalculator.updateSpeed(gpsSpeedMps: location.speed)
        
        let targetLoc = CLLocation(latitude: target.latitude, longitude: target.longitude)
        let distToTarget = location.distance(from: targetLoc)
        let distRemainingSegments = calculateRemainingSegmentsDistance(fromIndex: targetIndex, points: points, forward: forward)
        let totalRemaining = distToTarget + distRemainingSegments
        
        let speedMps = paceCalculator.effectiveWalkingSpeedMps
        
        self.targetDistanceMeters = distToTarget
        self.targetEtaSeconds = distToTarget / speedMps
        
        self.metaRemainingDistanceMeters = totalRemaining
        self.metaEtaSeconds = totalRemaining / speedMps
    }
    
    // MARK: - Evaluación de Llegada
    
    /// Evalúa la puntualidad al entrar al radio de tolerancia de un hito intermedio.
    /// Umbrales funcionales:
    /// - Temprano: anticipación >= 15 segundos respecto a targetExpectedArrivalTime.
    /// - Tarde: retraso >= 30 segundos respecto a targetExpectedArrivalTime.
    /// - Normal: dentro del margen (-15s a +30s).
    public func evaluateWaypointArrival(arrivalDate: Date = Date()) -> ArrivalTiming {
        guard let expected = targetExpectedArrivalTime else {
            return .onTime(diffSeconds: 0.0)
        }
        let diffSeconds = arrivalDate.timeIntervalSince(expected)
        
        if diffSeconds <= -15.0 {
            let earlySeconds = abs(diffSeconds)
            return .early(seconds: earlySeconds, formattedTime: Self.formatSpokenDuration(earlySeconds))
        } else if diffSeconds >= 30.0 {
            return .late(seconds: diffSeconds, formattedTime: Self.formatSpokenDuration(diffSeconds))
        } else {
            return .onTime(diffSeconds: diffSeconds)
        }
    }
    
    /// Evalúa la puntualidad al llegar a la META FINAL de la ruta.
    /// Umbrales funcionales:
    /// - Temprano: anticipación >= 15 segundos respecto a metaExpectedArrivalTime.
    /// - Tarde: retraso >= 30 segundos respecto a metaExpectedArrivalTime.
    /// - Regular: dentro del margen.
    public func evaluateMetaArrival(arrivalDate: Date = Date()) -> ArrivalTiming {
        guard let expected = metaExpectedArrivalTime else {
            return .onTime(diffSeconds: 0.0)
        }
        let diffSeconds = arrivalDate.timeIntervalSince(expected)
        
        if diffSeconds <= -15.0 {
            let earlySeconds = abs(diffSeconds)
            return .early(seconds: earlySeconds, formattedTime: Self.formatSpokenDuration(earlySeconds))
        } else if diffSeconds >= 30.0 {
            return .late(seconds: diffSeconds, formattedTime: Self.formatSpokenDuration(diffSeconds))
        } else {
            return .onTime(diffSeconds: diffSeconds)
        }
    }
    
    /// Retraso acumulado en segundos para el punto actual (positivo si va tarde, negativo si va adelantado).
    public func currentTargetDelaySeconds(now: Date = Date()) -> Double {
        guard let expected = targetExpectedArrivalTime else { return 0.0 }
        return now.timeIntervalSince(expected)
    }
    
    /// Indica si el senderista lleva retraso acumulado apreciable (>= 30s) para el punto actual.
    public func hasAccumulatedDelay(thresholdSeconds: Double = 30.0, now: Date = Date()) -> Bool {
        return currentTargetDelaySeconds(now: now) >= thresholdSeconds
    }
    
    // MARK: - Generación de Discurso y Formateo
    
    /// Genera la locución bajo demanda al tocar el badge interactivo de Modo Guía.
    public func generateInteractiveStatusSpeech(targetName: String, now: Date = Date()) -> String {
        let currentKmhStr = String(format: "%.1f", currentSpeedKmh)
        let targetEtaSpoken = Self.formatSpokenDuration(targetEtaSeconds)
        let metaEtaSpoken = Self.formatSpokenDuration(metaEtaSeconds)
        
        var speech = "Velocidad actual: \(currentKmhStr) kilómetros por hora. Tiempo estimado al punto \(targetName): \(targetEtaSpoken). Tiempo a la meta: \(metaEtaSpoken)."
        
        let delaySeconds = currentTargetDelaySeconds(now: now)
        if delaySeconds >= 30.0 {
            let delayStr = Self.formatSpokenDuration(delaySeconds)
            let expSpeedStr = String(format: "%.1f", targetExpectedSpeedKmh)
            speech += " ¡Atención! Llevas \(delayStr) de retraso para este punto. Se esperaba velocidad de \(expSpeedStr) y traes \(currentKmhStr) kilómetros por hora. ¡Apura el paso!"
        }
        
        return speech
    }
    
    // MARK: - Cálculos Geodésicos Auxiliares
    
    private func calculateRemainingSegmentsDistance(
        fromIndex: Int,
        points: [RoutePoint],
        forward: Bool
    ) -> Double {
        guard !points.isEmpty else { return 0.0 }
        
        let indices: [Int] = forward
            ? Array(fromIndex..<points.count)
            : Array((0...fromIndex).reversed())
        
        guard indices.count >= 2 else { return 0.0 }
        
        var total = 0.0
        for i in 0..<(indices.count - 1) {
            let p1 = points[indices[i]]
            let p2 = points[indices[i + 1]]
            let l1 = CLLocation(latitude: p1.latitude, longitude: p1.longitude)
            let l2 = CLLocation(latitude: p2.latitude, longitude: p2.longitude)
            total += l1.distance(from: l2)
        }
        return total
    }
    
    // MARK: - Formateadores
    
    /// Formato hablado natural en español para TTS (ej. "45 segundos", "3 minutos", "1 hora con 15 minutos").
    public static func formatSpokenDuration(_ seconds: Double) -> String {
        let s = max(0.0, seconds)
        if s < 60.0 {
            let secInt = Int(s.rounded())
            return secInt == 1 ? "1 segundo" : "\(secInt) segundos"
        }
        
        let totalMinutes = Int(s / 60.0)
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        
        if hours > 0 {
            let hUnit = hours == 1 ? "hora" : "horas"
            if minutes > 0 {
                let mUnit = minutes == 1 ? "minuto" : "minutos"
                return "\(hours) \(hUnit) con \(minutes) \(mUnit)"
            } else {
                return "\(hours) \(hUnit)"
            }
        } else {
            let mUnit = totalMinutes == 1 ? "minuto" : "minutos"
            return "\(totalMinutes) \(mUnit)"
        }
    }
    
    /// Formato compacto para badges de UI (ej. "45s", "14 min", "1h 12m").
    public static func formatBadgeDuration(_ seconds: Double) -> String {
        let s = max(0.0, seconds)
        if s < 60.0 {
            return "\(Int(s.rounded()))s"
        }
        
        let totalMinutes = Int((s / 60.0).rounded())
        let hours = totalMinutes / 60
        let minutes = totalMinutes % 60
        
        if hours > 0 {
            return minutes > 0 ? "\(hours)h \(minutes)m" : "\(hours)h"
        } else {
            return "\(totalMinutes) min"
        }
    }
    
    /// Texto formateado para el Badge interactivo requerido:
    /// "⏱️ Meta: ~[TiempoMeta] · Sig: ~[TiempoSig] · [Velocidad] km/h"
    public var interactiveBadgeText: String {
        let metaText = Self.formatBadgeDuration(metaEtaSeconds)
        let sigText = Self.formatBadgeDuration(targetEtaSeconds)
        let speedText = String(format: "%.1f", currentSpeedKmh)
        return "⏱️ Meta: ~\(metaText) · Sig: ~\(sigText) · \(speedText) km/h"
    }
}
