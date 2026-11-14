//
//  HeaderBarView.swift
//  RutasSendero
//
//  Barra superior con estética Mountain / Forest.
//  Muestra el estado del GPS, nombre de la ruta, rol activo y botón de emergencia 911.
//

import SwiftUI
import CoreLocation

public struct HeaderBarView: View {
    @Bindable var viewModel: MainViewModel
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        HStack(spacing: 8) {
            // Si el mapa está activo, mostrar botón de Volver a Portada (1:1 Android)
            if viewModel.showMapScreen {
                Button {
                    if viewModel.isVirtualTourActive {
                        viewModel.stopVirtualTour()
                    }
                    withAnimation(.easeInOut(duration: 0.25)) {
                        viewModel.showMapScreen = false
                    }
                } label: {
                    ZStack {
                        Circle()
                            .fill(AppTheme.cardMuted.opacity(0.8))
                            .frame(width: 38, height: 38)
                        
                        Image(systemName: "chevron.left")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(AppTheme.accent)
                    }
                }
                .buttonStyle(.plain)
            }
            
            // Botón Izquierdo: Menú de Rutas (Siempre visible)
            Menu {
                Text("RUTAS")
                
                Button {
                    Task {
                        await viewModel.loadLocalRoutes()
                        viewModel.localRoutesInitialTab = 0
                        viewModel.isShowingLocalRoutesSheet = true
                    }
                } label: {
                    Label("1. Locales", systemImage: "iphone")
                }
                
                Button {
                    Task {
                        await viewModel.loadLocalRoutes()
                        viewModel.localRoutesInitialTab = 1
                        viewModel.isShowingLocalRoutesSheet = true
                    }
                } label: {
                    Label("2. Ver otras rutas", systemImage: "arrow.triangle.2.circlepath")
                }
                
                Button {
                    viewModel.isShowingCloudRoutesSheet = true
                } label: {
                    Label("3. Descargar de la nube", systemImage: "icloud.and.arrow.down")
                }
                
                Button {
                    viewModel.saveStatusMessage = "Salidas y eventos programados próximamente."
                } label: {
                    Label("4. Salidas", systemImage: "calendar")
                }
                
                Divider()
                
                // Permitido para agregar nueva ruta (solo visible en menú pop para usuario con rol 'G' o superior)
                if viewModel.canCreateRoutes {
                    Button {
                        viewModel.isShowingNewRouteSheet = true
                    } label: {
                        Label("Agregar nueva ruta", systemImage: "plus.circle.fill")
                    }
                }
                
                if viewModel.activeRoute != nil {
                    Divider()
                    Button(role: .destructive) {
                        viewModel.clearActiveRoute()
                    } label: {
                        Label("Cerrar ruta activa", systemImage: "xmark.circle")
                    }
                }
            } label: {
                ZStack {
                    Circle()
                        .fill(viewModel.showMapScreen ? AppTheme.cardMuted.opacity(0.8) : Color.white.opacity(0.65))
                        .frame(width: 38, height: 38)
                    
                    Image(systemName: "mountain.2.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(viewModel.showMapScreen ? AppTheme.accent : AppTheme.primary)
                }
            }
            .buttonStyle(.plain)
            
            // Botón contextual: Alternar orden de puntos (visible solo con ruta activa en visor/guía)
            if viewModel.activeRoute != nil && (viewModel.activeRole == .guia || viewModel.activeRole == .visor) {
                Button {
                    viewModel.isPointsListReversed.toggle()
                } label: {
                    ZStack {
                        Circle()
                            .fill(viewModel.showMapScreen ? AppTheme.cardMuted.opacity(0.8) : Color.white.opacity(0.65))
                            .frame(width: 34, height: 34)
                        
                        Image(systemName: "arrow.up.arrow.down")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(viewModel.isPointsListReversed ? AppTheme.activeColor(for: viewModel.activeRole) : (viewModel.showMapScreen ? AppTheme.textSecondary : AppTheme.darkBackground.opacity(0.6)))
                    }
                }
                .buttonStyle(.plain)
            }
            
            // Centro: En Mapa muestra telemetría de Simulación / Guía (distancia + subida/bajada); en Portada muestra Ruta y Fecha
            Button {
                if viewModel.activeRoute != nil {
                    viewModel.isShowingRouteDetailsSheet = true
                }
            } label: {
                if viewModel.showMapScreen && viewModel.isVirtualTourActive {
                    let pts = viewModel.tourPoints
                    let curPt = viewModel.virtualTourIndex < pts.count ? pts[viewModel.virtualTourIndex] : nil
                    let nextPt = (viewModel.virtualTourIndex + 1) < pts.count ? pts[viewModel.virtualTourIndex + 1] : nil
                    let simSubLine: (text: String, color: Color)? = {
                        guard let c = curPt, let n = nextPt else { return nil }
                        let d = CLLocation(latitude: c.latitude, longitude: c.longitude)
                            .distance(from: CLLocation(latitude: n.latitude, longitude: n.longitude))
                        let dAlt = RouteStatsCalculator.calculateElevationDeltaMeters(fromAlt: c.altitude, toAlt: n.altitude)
                        let badge = RouteStatsCalculator.formatElevationHeaderBadge(dAlt)
                        let dStr = RouteStatsCalculator.formatDistanceShort(d)
                        let txt = badge.isEmpty ? "Sig: \(dStr)" : "Sig: \(dStr) · \(badge)"
                        let col: Color
                        if let delta = dAlt, delta > 0 {
                            col = Color(red: 52/255, green: 211/255, blue: 153/255)
                        } else if let delta = dAlt, delta < 0 {
                            col = Color(red: 96/255, green: 165/255, blue: 250/255)
                        } else {
                            col = Color(red: 56/255, green: 189/255, blue: 248/255)
                        }
                        return (txt, col)
                    }()
                    
                    VStack(alignment: .center, spacing: 1) {
                        Text(curPt?.name ?? viewModel.activeRoute?.name ?? "Simulación")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(AppTheme.textPrimary)
                            .lineLimit(1)
                        
                        if let sub = simSubLine {
                            Text(sub.text)
                                .font(.system(size: 11, weight: .heavy, design: .rounded))
                                .foregroundStyle(sub.color)
                                .lineLimit(1)
                        } else {
                            Text("Último punto del recorrido")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundStyle(AppTheme.success)
                                .lineLimit(1)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                } else if viewModel.showMapScreen && viewModel.activeRole == .guia && viewModel.isGuidingActive, let target = viewModel.targetGuideWaypoint {
                    let remElev = viewModel.remainingElevationToTargetGuideWaypoint
                    let elevBadge = RouteStatsCalculator.formatElevationHeaderBadge(remElev)
                    let distShort: String? = {
                        if let d = viewModel.distanceToTargetGuideWaypoint, d > 0 {
                            return RouteStatsCalculator.formatDistanceShort(d)
                        }
                        return nil
                    }()
                    let guideSubLine: String = {
                        var parts: [String] = []
                        if let ds = distShort {
                            parts.append("Al sig. punto: \(ds)")
                        }
                        if !elevBadge.isEmpty {
                            parts.append(elevBadge)
                        }
                        return parts.joined(separator: " · ")
                    }()
                    let elevColor: Color = {
                        if let delta = remElev {
                            if delta > 0 { return Color(red: 52/255, green: 211/255, blue: 153/255) }
                            if delta < 0 { return Color(red: 96/255, green: 165/255, blue: 250/255) }
                        }
                        return AppTheme.accent
                    }()
                    
                    VStack(alignment: .center, spacing: 1) {
                        Text("🎯 \(target.name)")
                            .font(.system(size: 13, weight: .heavy))
                            .foregroundStyle(AppTheme.textPrimary)
                            .lineLimit(1)
                        
                        if !guideSubLine.isEmpty {
                            Text(guideSubLine)
                                .font(.system(size: 11, weight: .heavy, design: .rounded))
                                .foregroundStyle(elevColor)
                                .lineLimit(1)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                } else {
                    VStack(alignment: .center, spacing: 2) {
                        HStack(spacing: 4) {
                            Text(viewModel.activeRoute?.name ?? "RutasSendero")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(viewModel.activeRoute != nil ? AppTheme.activeColor(for: viewModel.activeRole) : (viewModel.showMapScreen ? AppTheme.textPrimary : AppTheme.darkBackground))
                                .lineLimit(1)
                            
                            if viewModel.activeRoute != nil {
                                Image(systemName: "info.circle.fill")
                                    .font(.system(size: 11))
                                    .foregroundStyle(AppTheme.activeColor(for: viewModel.activeRole))
                            }
                        }
                        
                        if let route = viewModel.activeRoute {
                            // 1:1 Android Kotlin: Cuando hay ruta seleccionada, mostrar la Fecha + Distancia total en km
                            let totalMeters = route.gpsDistanceMeters > 0 ? route.gpsDistanceMeters : (route.beaconDistanceMeters > 0 ? route.beaconDistanceMeters : viewModel.totalDistanceMeters)
                            let distText = totalMeters >= 1000.0 ? String(format: "%.2f km", totalMeters / 1000.0) : String(format: "%.0f m", totalMeters)
                            let subtitleText = route.date.isEmpty ? "Distancia total: \(distText)" : "\(route.date) • \(distText)"
                            
                            HStack(spacing: 4) {
                                Image(systemName: "calendar")
                                    .font(.system(size: 9))
                                    .foregroundStyle(roleColor)
                                
                                Text(subtitleText)
                                    .font(.system(size: 10, weight: .bold, design: .rounded))
                                    .foregroundStyle(roleColor)
                                    .lineLimit(1)
                            }
                        } else {
                            // Píldora de Estado del GPS cuando NO hay ruta seleccionada
                            HStack(spacing: 4) {
                                Circle()
                                    .fill(gpsStatusColor)
                                    .frame(width: 6, height: 6)
                                    .shadow(color: gpsStatusColor.opacity(0.8), radius: 2)
                                
                                Text(gpsStatusText)
                                    .font(.system(size: 10, weight: .semibold, design: .monospaced))
                                    .foregroundStyle(viewModel.showMapScreen ? AppTheme.textSecondary : AppTheme.darkBackground.opacity(0.75))
                                    .lineLimit(1)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
            }
            .buttonStyle(.plain)
            .disabled(viewModel.activeRoute == nil)
            
            // Botón contextual: Voz Guía vs Pitidos Doppler (1:1 Android MainScreen.kt:938)
            if viewModel.activeRole == .guia && viewModel.isGuidingActive {
                Button {
                    viewModel.toggleVoiceGuidance()
                } label: {
                    ZStack {
                        Circle()
                            .fill(viewModel.showMapScreen ? AppTheme.cardMuted.opacity(0.8) : Color.white.opacity(0.65))
                            .frame(width: 36, height: 36)
                        
                        Image(systemName: viewModel.isVoiceGuidanceEnabled ? "speaker.wave.2.fill" : "speaker.slash.fill")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(viewModel.isVoiceGuidanceEnabled ? AppTheme.accent : (viewModel.showMapScreen ? AppTheme.textSecondary : AppTheme.darkBackground.opacity(0.65)))
                    }
                }
                .buttonStyle(.plain)
            }
            
            // Botón contextual: Bloqueo Modo Bolsillo (solo cuando hay ruta seleccionada y no es visor)
            if viewModel.activeRoute != nil && viewModel.activeRole != .visor {
                Button {
                    viewModel.isPocketModeActive.toggle()
                } label: {
                    ZStack {
                        Circle()
                            .fill(viewModel.isPocketModeActive ? AppTheme.accent.opacity(0.25) : AppTheme.cardMuted.opacity(0.8))
                            .frame(width: 36, height: 36)
                        
                        Image(systemName: viewModel.isPocketModeActive ? "lock.fill" : "lock.open")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(viewModel.isPocketModeActive ? AppTheme.accent : AppTheme.textSecondary)
                    }
                }
                .buttonStyle(.plain)
            }
            
            // Botón contextual: Emergencia 911 (Solo en Modo Guía con ruta activa con información o cuando hay emergencia activa, 1:1 Android 6f224ff)
            let hasActiveRouteWithInfo = viewModel.activeRoute != nil && (!viewModel.recordedPoints.isEmpty || !viewModel.recordedTrace.isEmpty)
            if (viewModel.activeRole == .guia && hasActiveRouteWithInfo) || viewModel.isEmergency911Active {
                Emergency911HeaderButton(viewModel: viewModel)
            }
            
            // Botón Derecho: Menú de Aplicación (Siempre visible)
            Menu {
                Text("APLICACIÓN")
                
                Button {
                    viewModel.isShowingSenderistaRegistration = true
                } label: {
                    Label("Perfil del Senderista", systemImage: "person.crop.circle")
                }
                
                Button {
                    viewModel.isShowingSettingsSheet = true
                } label: {
                    Label("Configuración", systemImage: "gearshape.fill")
                }
                
                if viewModel.activeRoute != nil {
                    // 1:1 Android (MainScreen.kt línea 3803) Iniciar/Pausar/Detener Recorrido Virtual en menú del sistema
                    if viewModel.activeRole == .visor && !viewModel.recordedPoints.isEmpty {
                        Button {
                            if viewModel.isVirtualTourActive {
                                viewModel.togglePauseVirtualTour()
                            } else {
                                viewModel.startVirtualTour()
                            }
                        } label: {
                            Label(
                                viewModel.isVirtualTourActive ? (viewModel.isVirtualTourPaused ? "Reanudar Recorrido Virtual" : "Pausar Recorrido Virtual") : "Iniciar Recorrido Virtual",
                                systemImage: viewModel.isVirtualTourActive ? (viewModel.isVirtualTourPaused ? "play.fill" : "pause.fill") : "play.fill"
                            )
                        }
                        
                        if viewModel.isVirtualTourActive {
                            Button(role: .destructive) {
                                viewModel.stopVirtualTour()
                            } label: {
                                Label("Detener Recorrido Virtual", systemImage: "stop.fill")
                            }
                        }
                    }
                    
                    if viewModel.activeRole != .visor {
                        Button {
                            viewModel.isPocketModeActive.toggle()
                        } label: {
                            Label(viewModel.isPocketModeActive ? "Desactivar Modo Bolsillo" : "Modo Bolsillo (Bloqueo)", systemImage: "lock.fill")
                        }
                    }
                    
                    Button {
                        viewModel.isShowingRouteDetailsSheet = true
                    } label: {
                        Label("Estadísticas de Ruta", systemImage: "chart.bar.xaxis")
                    }
                }
                
                Divider()
                
                Button {
                    viewModel.isShowingAboutSheet = true
                } label: {
                    Label("Acerca de RutasSendero", systemImage: "info.circle")
                }
                
                Divider()
                
                Button(role: .destructive) {
                    // Salir del sistema (1:1 Android onExitApp)
                    UIControl().sendAction(#selector(URLSessionTask.suspend), to: UIApplication.shared, for: nil)
                } label: {
                    Label("Salir del Sistema", systemImage: "rectangle.portrait.and.arrow.right")
                }
            } label: {
                ZStack {
                    Circle()
                        .fill(viewModel.showMapScreen ? AppTheme.cardMuted.opacity(0.8) : Color.white.opacity(0.65))
                        .frame(width: 38, height: 38)
                    
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(viewModel.showMapScreen ? AppTheme.accent : AppTheme.primary)
                }
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(.ultraThinMaterial)
        .background(
            viewModel.showMapScreen
                ? AppTheme.cardBackground.opacity(0.85)
                : AppTheme.headerBgColor(for: viewModel.activeRole)
        )
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(
                    viewModel.showMapScreen
                        ? AppTheme.cardBorder
                        : AppTheme.headerBorderColor(for: viewModel.activeRole),
                    lineWidth: 1
                )
        )
        .shadow(color: Color.black.opacity(viewModel.showMapScreen ? 0.2 : 0.08), radius: 6, x: 0, y: 3)
        .padding(.horizontal, 10)
        .padding(.top, 4)
    }
    
    // MARK: - Helpers Visuales
    
    private var gpsStatusColor: Color {
        if !viewModel.locationTracker.isGpsReady {
            return AppTheme.alert
        }
        let acc = viewModel.locationTracker.horizontalAccuracy
        if acc < 0 {
            return AppTheme.warningOrange
        } else if acc <= 10.0 {
            return AppTheme.success
        } else {
            return AppTheme.warningYellow
        }
    }
    
    private var gpsStatusText: String {
        if !viewModel.locationTracker.isGpsReady {
            return "SIN SEÑAL GPS"
        }
        let acc = viewModel.locationTracker.horizontalAccuracy
        if acc < 0 {
            return "CONECTANDO..."
        } else {
            return String(format: "GPS ACTIVO (±%.1fm)", acc)
        }
    }
    
    private var roleColor: Color {
        AppTheme.activeColor(for: viewModel.activeRole)
    }
}

// MARK: - Botón 911 con Confirmación de Seguridad (4 Clics en 2.5s, 1:1 Android Kotlin)

private struct Emergency911HeaderButton: View {
    @Bindable var viewModel: MainViewModel
    @State private var clicks911: Int = 0
    @State private var timerTask: Task<Void, Never>? = nil
    
    var body: some View {
        Button {
            clicks911 += 1
            timerTask?.cancel()
            if clicks911 >= 4 {
                clicks911 = 0
                viewModel.toggleEmergency911()
            } else {
                timerTask = Task {
                    try? await Task.sleep(nanoseconds: 2_500_000_000)
                    if !Task.isCancelled {
                        await MainActor.run {
                            clicks911 = 0
                        }
                    }
                }
            }
        } label: {
            ZStack {
                Circle()
                    .fill(buttonBgColor)
                    .frame(width: 38, height: 38)
                    .shadow(color: buttonBgColor.opacity(0.4), radius: 4)
                
                Text(buttonLabel)
                    .font(.system(size: clicks911 > 0 ? 15 : 12, weight: .black, design: .rounded))
                    .foregroundStyle(.white)
            }
        }
        .buttonStyle(.plain)
        .padding(.trailing, 2)
    }
    
    private var buttonBgColor: Color {
        if clicks911 > 0 {
            return AppTheme.alert
        }
        return viewModel.isEmergency911Active ? .black : AppTheme.alert
    }
    
    private var buttonLabel: String {
        if clicks911 > 0 {
            return "\(clicks911)"
        }
        return viewModel.isEmergency911Active ? "FIN" : "911"
    }
}
