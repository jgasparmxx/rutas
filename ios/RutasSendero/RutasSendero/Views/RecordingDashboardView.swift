//
//  RecordingDashboardView.swift
//  RutasSendero
//
//  Panel flotante inferior de grabación y métricas de telemetría en tiempo real.
//  Diseño HUD premium para exteriores con controles de alta visibilidad.
//  Incluye HUD de Recorrido Virtual / Simulación 1:1 con Android MainScreen.kt (líneas 973-1180).
//

import SwiftUI
import CoreLocation

public struct RecordingDashboardView: View {
    @Bindable var viewModel: MainViewModel
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        VStack(spacing: 12) {
            if viewModel.isVirtualTourActive && viewModel.activeRole == .visor {
                virtualTourHUDView
            } else if viewModel.activeRole == .guia {
                guideRouteMapHUDView
            } else {
                switch viewModel.recordingState {
                case .stopped:
                    stoppedStateView
                case .recording, .paused:
                    activeRecordingView
                }
            }
        }
        .padding(16)
        .glassCardStyle(cornerRadius: 24)
        .padding(.horizontal, 12)
        .padding(.bottom, 20)
    }
    
    // MARK: - HUD de Recorrido Virtual / Simulación (1:1 Android)
    
    private var virtualTourHUDView: some View {
        let pts = viewModel.tourPoints
        let currentIndex = viewModel.virtualTourIndex
        
        return VStack(spacing: 10) {
            if currentIndex < pts.count {
                let currentPoint = pts[currentIndex]
                let nextPoint = (currentIndex < pts.count - 1) ? pts[currentIndex + 1] : nil
                let stateInfo = PointStateInfo.forPoint(
                    estado: currentPoint.estado,
                    isRouteEnd: currentPoint.isRouteEnd,
                    description: currentPoint.descriptionText
                )
                let nextPhrase = RouteStatsCalculator.nextPointPhrase(from: currentPoint, to: nextPoint)
                
                // Cabecera del Tour: Badge de Punto, Tipo de Punto y Botón de Salir (sin altitud absoluta)
                HStack(alignment: .center) {
                    HStack(spacing: 6) {
                        Text("PUNTO \(currentIndex + 1)/\(pts.count)")
                            .font(.system(size: 11, weight: .heavy))
                            .foregroundStyle(Color(red: 15/255, green: 23/255, blue: 42/255))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color(red: 56/255, green: 189/255, blue: 248/255))
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                        
                        Text(stateInfo.name)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundStyle(stateInfo.color)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background(stateInfo.backgroundColor)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                    }
                    
                    Spacer()
                    
                    Button {
                        viewModel.stopVirtualTour()
                    } label: {
                        HStack(spacing: 4) {
                            Text("Salir")
                                .font(.system(size: 11, weight: .bold))
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 16))
                        }
                        .foregroundStyle(AppTheme.textSecondary)
                    }
                    .buttonStyle(.plain)
                }
                
                // Foto y Descripción del Punto + Frase de distancia y subida/bajada al siguiente punto
                HStack(spacing: 12) {
                    if let photoPath = currentPoint.photoUrl, !photoPath.isEmpty {
                        WaypointThumbnailView(photoPath: photoPath, size: 54)
                            .onTapGesture {
                                viewModel.selectedPointForDetail = currentPoint
                            }
                    }
                    
                    VStack(alignment: .leading, spacing: 4) {
                        Text(currentPoint.descriptionText.isEmpty ? stateInfo.name : currentPoint.descriptionText)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(AppTheme.textPrimary)
                            .lineLimit(2)
                        
                        if let nextPhrase = nextPhrase {
                            Text(nextPhrase)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(Color(red: 94/255, green: 234/255, blue: 212/255))
                                .fixedSize(horizontal: false, vertical: true)
                        } else {
                            Text("¡Último punto del recorrido!")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(AppTheme.success)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(Rectangle())
                .onTapGesture {
                    viewModel.selectedPointForDetail = currentPoint
                }
                
                // Botonera de Control de Reproducción estilo grabadora (1:1 Android líneas 1108-1180)
                HStack(spacing: 12) {
                    // Anterior
                    Button {
                        viewModel.previousVirtualTourPoint()
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "backward.end.fill")
                                .font(.system(size: 14, weight: .bold))
                            Text("Anterior")
                                .font(.system(size: 12, weight: .bold))
                        }
                        .foregroundStyle(currentIndex > 0 ? AppTheme.textPrimary : AppTheme.textSecondary.opacity(0.4))
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .background(AppTheme.cardMuted)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .disabled(currentIndex == 0)
                    .buttonStyle(.plain)
                    
                    // Pausa / Reanudar (Background Negro Destacado)
                    Button {
                        viewModel.togglePauseVirtualTour()
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: viewModel.isVirtualTourPaused ? "play.fill" : "pause.fill")
                                .font(.system(size: 15, weight: .bold))
                            Text(viewModel.isVirtualTourPaused ? "Reanudar" : "Pausar")
                                .font(.system(size: 12, weight: .heavy))
                        }
                        .foregroundStyle(Color(red: 56/255, green: 189/255, blue: 248/255))
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .background(Color.black)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color(red: 56/255, green: 189/255, blue: 248/255), lineWidth: 1.5)
                        )
                        .shadow(color: Color.black.opacity(0.5), radius: 4, y: 2)
                    }
                    .buttonStyle(.plain)
                    
                    // Siguiente / Finalizar
                    Button {
                        viewModel.nextVirtualTourPoint()
                    } label: {
                        HStack(spacing: 4) {
                            Text(currentIndex < pts.count - 1 ? "Siguiente" : "Finalizar")
                                .font(.system(size: 12, weight: .heavy))
                            Image(systemName: currentIndex < pts.count - 1 ? "forward.end.fill" : "checkmark")
                                .font(.system(size: 14, weight: .bold))
                        }
                        .foregroundStyle(Color(red: 15/255, green: 23/255, blue: 42/255))
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .background(Color(red: 56/255, green: 189/255, blue: 248/255))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
    
    // MARK: - HUD de Modo Guía en el Mapa (1:1 Android, NUNCA Simular Recorrido)
    
    @ViewBuilder
    private var guideRouteMapHUDView: some View {
        if let route = viewModel.activeRoute {
            VStack(spacing: 12) {
                guideRouteHeaderView(route)
                
                if let target = viewModel.targetGuideWaypoint {
                    guideTargetCardView(target)
                } else if let msg = viewModel.guidanceStatusMessage {
                    guideStatusMessageCardView(msg)
                }
                
                guideBackToPortadaButton
            }
        }
    }
    
    @ViewBuilder
    private func guideRouteHeaderView(_ route: Route) -> some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text("MODO GUÍA")
                        .font(.system(size: 10, weight: .heavy))
                        .foregroundStyle(AppTheme.accent)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(AppTheme.accent.opacity(0.15))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                    
                    if viewModel.isRouteConsumptionOnly {
                        Text("· IMPORTADA")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                }
                
                Text(route.name)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(AppTheme.textPrimary)
                    .lineLimit(1)
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 2) {
                Text(RouteStatsCalculator.formatDistance(route.gpsDistanceMeters))
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundStyle(AppTheme.textPrimary)
                Text("\(viewModel.recordedPoints.count) hitos")
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(AppTheme.textSecondary)
            }
        }
    }
    
    @ViewBuilder
    private func guideTargetCardView(_ target: RoutePoint) -> some View {
        let distText: String = {
            if let d = viewModel.distanceToTargetGuideWaypoint {
                return RouteStatsCalculator.formatDistanceShort(d)
            }
            return "Calculando..."
        }()
        let remElev = viewModel.remainingElevationToTargetGuideWaypoint
        let elevBadge = RouteStatsCalculator.formatElevationHeaderBadge(remElev)
        let elevColor: Color = {
            if let r = remElev {
                if r > 0 { return Color(red: 52/255, green: 211/255, blue: 153/255) }
                if r < 0 { return Color(red: 96/255, green: 165/255, blue: 250/255) }
            }
            return AppTheme.textSecondary
        }()
        
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("🎯 GUIANDO AL PUNTO:")
                        .font(.system(size: 11, weight: .heavy))
                        .foregroundStyle(AppTheme.accent)
                    
                    Text(target.name)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(AppTheme.textPrimary)
                        .lineLimit(1)
                }
                
                Spacer()
                
                Button {
                    viewModel.stopGuiding()
                } label: {
                    Text("Detener Guía")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(AppTheme.alert)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
            
            Divider()
                .background(AppTheme.cardBorder)
            
            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Al sig. punto:")
                        .font(.system(size: 10))
                        .foregroundStyle(AppTheme.textSecondary)
                    HStack(spacing: 6) {
                        Text(distText)
                            .font(.system(size: 16, weight: .heavy, design: .rounded))
                            .foregroundStyle(AppTheme.textPrimary)
                        
                        if !elevBadge.isEmpty {
                            Text(elevBadge)
                                .font(.system(size: 12, weight: .heavy, design: .rounded))
                                .foregroundStyle(elevColor)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(elevColor.opacity(0.16))
                                .clipShape(Capsule())
                        }
                    }
                }
                
                VStack(alignment: .leading, spacing: 1) {
                    Text("Velocidad:")
                        .font(.system(size: 10))
                        .foregroundStyle(AppTheme.textSecondary)
                    Text(RouteStatsCalculator.formatSpeedKmh(viewModel.locationTracker.currentSpeedKmh))
                        .font(.system(size: 15, weight: .heavy, design: .rounded))
                        .foregroundStyle(AppTheme.accent)
                }
                
                Spacer()
                
                let points = viewModel.tourPoints
                if let idx = points.firstIndex(where: { $0.id == target.id }), idx < points.count - 1 {
                    Button {
                        viewModel.advanceGuideToNextWaypoint()
                    } label: {
                        HStack(spacing: 3) {
                            Text("Siguiente")
                                .font(.system(size: 10, weight: .bold))
                            Image(systemName: "chevron.right")
                                .font(.system(size: 9, weight: .bold))
                        }
                        .foregroundStyle(AppTheme.accent)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(AppTheme.accent.opacity(0.12))
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(12)
        .background(AppTheme.cardMuted.opacity(0.7))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(AppTheme.accent.opacity(0.3), lineWidth: 1)
        )
    }
    
    @ViewBuilder
    private func guideStatusMessageCardView(_ msg: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "figure.hiking")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(AppTheme.accent)
            Text(msg)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(AppTheme.textPrimary)
                .lineLimit(2)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.accent.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
    
    @ViewBuilder
    private var guideBackToPortadaButton: some View {
        Button {
            withAnimation(.easeInOut(duration: 0.25)) {
                viewModel.showMapScreen = false
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 12, weight: .bold))
                Text("Volver a Portada")
                    .font(.system(size: 12, weight: .bold))
            }
            .foregroundStyle(AppTheme.textSecondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
        }
        .buttonStyle(.plain)
    }
    
    // MARK: - Estado: Grabación Detenida (Sin ruta activa)
    
    private var stoppedStateView: some View {
        VStack(spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("¿Listo para el sendero?")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(AppTheme.textPrimary)
                    
                    Text("Inicia la grabación para registrar traza, desniveles y balizas.")
                        .font(.system(size: 12, weight: .regular))
                        .foregroundStyle(AppTheme.textSecondary)
                }
                
                Spacer()
                
                // Botón Baliza (Faro BLE)
                Button {
                    viewModel.toggleBeacon()
                } label: {
                    Image(viewModel.isBeaconEmitting ? "LighthouseOn" : "LighthouseOff")
                        .resizable()
                        .scaledToFill()
                        .frame(width: 44, height: 44)
                        .clipShape(Circle())
                        .overlay(
                            Circle().stroke(viewModel.isBeaconEmitting ? AppTheme.accentCyan : AppTheme.cardBorder, lineWidth: 2)
                        )
                        .shadow(color: viewModel.isBeaconEmitting ? AppTheme.warningYellow.opacity(0.55) : .clear, radius: 6)
                }
                .buttonStyle(.plain)
            }
            
            // Botón Principal de Iniciar
            Button {
                viewModel.isShowingNewRouteSheet = true
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "record.circle.fill")
                        .font(.system(size: 20, weight: .bold))
                    
                    Text("INICIAR GRABACIÓN")
                        .font(.system(size: 15, weight: .heavy))
                        .tracking(0.5)
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(AppTheme.primary)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .shadow(color: AppTheme.primary.opacity(0.4), radius: 8, x: 0, y: 4)
            }
            .buttonStyle(.plain)
        }
    }
    
    // MARK: - Estado: Grabación Activa / Pausada (Trazando nueva ruta)
    
    private var activeRecordingView: some View {
        VStack(spacing: 14) {
            // Header del HUD: Estado + Distancia Hero + Tiempo
            HStack(alignment: .center) {
                // Indicador de Grabando
                HStack(spacing: 6) {
                    Circle()
                        .fill(viewModel.recordingState == .recording ? AppTheme.accent : AppTheme.warningOrange)
                        .frame(width: 10, height: 10)
                        .scaleEffect(viewModel.recordingState == .recording ? 1.1 : 1.0)
                        .animation(
                            viewModel.recordingState == .recording ?
                                Animation.easeInOut(duration: 0.8).repeatForever(autoreverses: true) :
                                .default,
                            value: viewModel.recordingState
                        )
                    
                    Text(viewModel.recordingState.rawValue.uppercased())
                        .font(.system(size: 11, weight: .heavy))
                        .foregroundStyle(viewModel.recordingState == .recording ? AppTheme.accent : AppTheme.warningOrange)
                }
                
                Spacer()
                
                // Cronómetro Transcurrido
                HStack(spacing: 4) {
                    Image(systemName: "stopwatch.fill")
                        .font(.system(size: 12))
                        .foregroundStyle(AppTheme.textSecondary)
                    
                    Text(RouteStatsCalculator.formatDuration(viewModel.elapsedSeconds))
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                        .foregroundStyle(AppTheme.textPrimary)
                }
            }
            
            // Distancia Destacada (Hero)
            HStack(alignment: .lastTextBaseline, spacing: 4) {
                Text(RouteStatsCalculator.formatDistance(viewModel.totalDistanceMeters).components(separatedBy: " ").first ?? "0.0")
                    .font(.system(size: 40, weight: .black, design: .rounded))
                    .foregroundStyle(AppTheme.textPrimary)
                
                Text(RouteStatsCalculator.formatDistance(viewModel.totalDistanceMeters).components(separatedBy: " ").last ?? "km")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(AppTheme.textSecondary)
                
                Spacer()
                
                // Desnivel +
                VStack(alignment: .trailing, spacing: 2) {
                    Text("DESNIVEL +")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(AppTheme.textSecondary)
                    
                    Text(RouteStatsCalculator.formatElevation(viewModel.elevationStats.elevationGain))
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .foregroundStyle(AppTheme.accentCyan)
                }
            }
            
            // Grid de Métricas Secundarias
            HStack(spacing: 8) {
                MetricTile(
                    title: "PUNTOS",
                    value: "\(viewModel.recordedPoints.count)",
                    systemImage: "mappin.circle.fill"
                )
                
                MetricTile(
                    title: "CALORÍAS",
                    value: String(format: "%.0f kcal", viewModel.caloriesBurned),
                    systemImage: "flame.fill"
                )
                
                MetricTile(
                    title: "ALTITUD",
                    value: RouteStatsCalculator.formatElevation(viewModel.locationTracker.currentAltitude),
                    systemImage: "mountain.2.fill"
                )
                
                MetricTile(
                    title: "VELOCIDAD",
                    value: RouteStatsCalculator.formatSpeedKmh(viewModel.locationTracker.currentSpeedKmh),
                    systemImage: "figure.walk"
                )
            }
            
            // Selector rápido de punto de estado (1 clic + cámara 1:1 Android)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(QuickPointStateOption.specialOptions) { item in
                        let info = item.info
                        Button {
                            viewModel.startQuickPointCapture(estado: item.id, isFinRuta: false)
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: info.systemImage)
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(info.color)
                                Text(item.name)
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundStyle(AppTheme.textPrimary)
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 6)
                            .background(info.backgroundColor)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(info.color.opacity(0.6), lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            
            // Botonera de Control de Grabación (Trazar)
            HStack(spacing: 10) {
                // Botón REGISTRAR PUNTO con Cámara en 1 Clic (Safety Yellow de Alta Visibilidad)
                Button {
                    viewModel.startQuickPointCapture(estado: 1, isFinRuta: false)
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "camera.fill")
                            .font(.system(size: 16, weight: .bold))
                        
                        Text("REGISTRAR PUNTO")
                            .font(.system(size: 12, weight: .heavy))
                    }
                    .foregroundStyle(Color(red: 15/255, green: 23/255, blue: 42/255))
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(AppTheme.warningYellow)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .shadow(color: AppTheme.warningYellow.opacity(0.3), radius: 6, x: 0, y: 3)
                }
                .buttonStyle(.plain)
                
                // Botón Pausar / Reanudar
                Button {
                    if viewModel.recordingState == .recording {
                        viewModel.pauseRecording()
                    } else {
                        viewModel.resumeRecording()
                    }
                } label: {
                    ZStack {
                        // Background negro destacado (1:1 solicitud usuario)
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color.black)
                            .frame(width: 48, height: 48)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(AppTheme.accent, lineWidth: 1.5)
                            )
                            .shadow(color: Color.black.opacity(0.5), radius: 4, y: 2)
                        
                        Image(systemName: viewModel.recordingState == .recording ? "pause.fill" : "play.fill")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(viewModel.recordingState == .recording ? .white : AppTheme.accent)
                    }
                }
                .buttonStyle(.plain)
                
                // Botón FIN DE RUTA
                Button {
                    viewModel.requestFinishRoute()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "flag.checkered")
                            .font(.system(size: 14, weight: .bold))
                        
                        Text("FINALIZAR")
                            .font(.system(size: 11, weight: .heavy))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .frame(height: 48)
                    .background(AppTheme.alert)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .shadow(color: AppTheme.alert.opacity(0.3), radius: 6, x: 0, y: 3)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Tarjeta de Métrica Individual

public struct MetricTile: View {
    public let title: String
    public let value: String
    public let systemImage: String
    
    public var body: some View {
        VStack(spacing: 3) {
            Image(systemName: systemImage)
                .font(.system(size: 11))
                .foregroundStyle(AppTheme.accent)
            
            Text(value)
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(AppTheme.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            
            Text(title)
                .font(.system(size: 8, weight: .semibold))
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .padding(.horizontal, 4)
        .background(AppTheme.cardMuted.opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}
