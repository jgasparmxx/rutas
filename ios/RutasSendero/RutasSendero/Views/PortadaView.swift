//
//  PortadaView.swift
//  RutasSendero
//
//  Pantalla de Portada (Home Screen) ligera de RutasSendero.
//  No carga MapKit en memoria hasta que el usuario lo solicite expresamente,
//  optimizando radicalmente el uso de CPU/RAM y permitiendo una interacción 100% fluida.
//  1:1 con Android: en rutas consumidas se desactiva la adición de puntos y se habilita
//  el modo Visor por defecto con botón Play para simulación de recorrido virtual.
//

import SwiftUI

public struct PortadaView: View {
    @Bindable var viewModel: MainViewModel
    @State private var isPointsListExpanded: Bool = true
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        VStack(spacing: 8) {
            // 1. Selector de Modos (1:1 Android MainScreen.kt:1606: se oculta al tener ruta activa para recuperar espacio)
            if viewModel.activeRoute == nil {
                modeSelectorView
                    .padding(.horizontal, 16)
                    .padding(.top, 4)
            }
            
            // 2. Contenido Central (Portada o Lista de Puntos de Ruta Activa)
            if let activeRoute = viewModel.activeRoute {
                if viewModel.recordingState == .recording || viewModel.recordingState == .paused || viewModel.activeRole == .registrar {
                    Spacer()
                    recordingRouteCard(activeRoute)
                        .padding(.horizontal, 18)
                    Spacer()
                } else {
                    consumedRouteCard(activeRoute)
                        .padding(.horizontal, 12)
                        .padding(.top, 4)
                }
            } else {
                emptyStatePortada
                
                // 3. Pie de Pantalla: Derechos reservados Tekins S.A. (Solo en la Portada inicial, 1:1 Android MainScreen.kt:1977)
                Text("© Todos los derechos reservados Tekins S.A.")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(AppTheme.darkBackground.opacity(0.55))
                    .padding(.bottom, 6)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .onAppear {
            if viewModel.activeRole != .guia && viewModel.activeRole != .visor {
                viewModel.activeRole = .visor
            }
            let agru = (viewModel.currentHikerProfile?.idAgrupacion?.trimmingCharacters(in: .whitespacesAndNewlines)).flatMap { $0.isEmpty || $0 == "@&" ? nil : $0 } ?? "MH"
            viewModel.syncAgrupacionLogo(idAgrupacion: agru)
        }
    }
    
    // MARK: - Selector de Modos (1:1 Android: Guía y Visor, nunca Trazar)
    @ViewBuilder
    private var modeSelectorView: some View {
        HStack(spacing: 8) {
            modeButton(
                role: .guia,
                title: "Guía",
                systemImage: "location.north.circle.fill",
                activeBg: Color(red: 100/255, green: 116/255, blue: 139/255), // Slate 500 (1:1 Android)
                activeText: .white
            )
            modeButton(
                role: .visor,
                title: "Visor",
                systemImage: "eye.fill",
                activeBg: AppTheme.accent, // Menta brillante ColorTekinsAccent (1:1 Android)
                activeText: AppTheme.darkBackground // Slate 900 (1:1 Android)
            )
        }
        .padding(4)
        .background(.ultraThinMaterial)
        .background(Color.white.opacity(0.35))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(AppTheme.accent.opacity(0.35), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.06), radius: 6, y: 2)
    }
    
    @ViewBuilder
    private func modeButton(role: AppRole, title: String, systemImage: String, activeBg: Color, activeText: Color) -> some View {
        let isSelected: Bool = (viewModel.activeRole == role)
        Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                viewModel.activeRole = role
                if role == .guia {
                    viewModel.isPointsListReversed = false
                } else if role == .visor {
                    viewModel.isPointsListReversed = true
                }
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: systemImage)
                    .font(.system(size: 13, weight: .semibold))
                Text(title)
                    .font(.system(size: 12, weight: .bold))
            }
            .foregroundStyle(isSelected ? activeText : AppTheme.darkBackground.opacity(0.7))
            .frame(maxWidth: .infinity)
            .frame(height: 38)
            .background(isSelected ? activeBg : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }
    
    // MARK: - Estado Inicial de Portada (Logo y Textos 1:1 Android)
    @ViewBuilder
    private var emptyStatePortada: some View {
        let hasAgrupacionLogo = (viewModel.agrupacionLogoImage != nil)
        
        ZStack(alignment: .topLeading) {
            // Renglón superior: Logo de la app pegado a la izquierda y frase 'RUTA CON AMIGOS' a su derecha (1:1 Android)
            if hasAgrupacionLogo {
                HStack(alignment: .center, spacing: 8) {
                    Image("LogoSenderismo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 145, height: 81.5)
                    
                    Text("RUTA CON AMIGOS")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(Color(red: 21/255, green: 128/255, blue: 61/255)) // Green 700 (0xFF15803D 1:1 Android)
                        .kerning(0.5)
                    
                    Spacer()
                }
                .padding(.top, 4)
                .padding(.leading, 4)
                .padding(.trailing, 12)
            }
            
            // Logo de la agrupación con la frase: desplazados más hacia abajo en la pantalla (1:1 Android)
            VStack(spacing: 0) {
                if let logoImg = viewModel.agrupacionLogoImage {
                    Image(uiImage: logoImg)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: 320, maxHeight: 260)
                        .shadow(color: Color.black.opacity(0.12), radius: 6, y: 3)
                } else {
                    Image("LogoSenderismo")
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: 310)
                        .aspectRatio(16.0 / 9.0, contentMode: .fit)
                        .shadow(color: Color.black.opacity(0.15), radius: 8, y: 4)
                }
                
                Spacer().frame(height: 14)
                
                Text("¿A dónde vamos hoy?")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(AppTheme.darkBackground)
                    .multilineTextAlignment(.center)
                
                Spacer().frame(height: 6)
                
                Text("Selecciona una ruta o registra tu aventura para compartir")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(AppTheme.darkBackground.opacity(0.8))
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 24)
            .padding(.top, hasAgrupacionLogo ? 105 : 10)
            .padding(.bottom, 45)
            .frame(maxHeight: .infinity, alignment: .center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    // MARK: - Lista de Puntos en Modo Visor / Guía (1:1 Android MainScreen.kt:1986: sin sub-header redundante)
    @ViewBuilder
    private func consumedRouteCard(_ route: Route) -> some View {
        let pts = viewModel.tourPoints
        
        if pts.isEmpty {
            VStack(spacing: 12) {
                Spacer()
                Image(systemName: "map.circle")
                    .font(.system(size: 48))
                    .foregroundStyle(AppTheme.darkBackground.opacity(0.45))
                Text("No hay puntos registrados en esta ruta")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(AppTheme.darkBackground.opacity(0.75))
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVStack(spacing: 8) {
                    ForEach(Array(pts.enumerated()), id: \.element.id) { index, pt in
                        let nextPt = (index < pts.count - 1) ? pts[index + 1] : nil
                        let nextPhrase = RouteStatsCalculator.nextPointPhrase(from: pt, to: nextPt)
                        ConsumedPointRowView(index: index, pt: pt, nextPhrase: nextPhrase) {
                            viewModel.selectedPointForDetail = pt
                        }
                    }
                }
                .padding(.vertical, 4)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
    
    // MARK: - Tarjeta de Registro Activo (Trazar Nueva Ruta 1:1 Android)
    @ViewBuilder
    private func recordingRouteCard(_ route: Route) -> some View {
        VStack(spacing: 14) {
            // Cabecera de la tarjeta con nombre de ruta y botón cancelar
            HStack {
                HStack(spacing: 8) {
                    Circle()
                        .fill(AppTheme.accent)
                        .frame(width: 8, height: 8)
                    
                    Text("REGISTRO ACTIVO")
                        .font(.system(size: 11, weight: .heavy))
                        .foregroundStyle(AppTheme.accent)
                }
                
                Spacer()
                
                Button(role: .destructive) {
                    viewModel.clearActiveRoute()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 20))
                        .foregroundStyle(AppTheme.alert.opacity(0.8))
                }
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(route.name)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(AppTheme.textPrimary)
                    .lineLimit(2)
                
                Text("Puntos guardados: \(viewModel.recordedPoints.count)")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(AppTheme.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            
            Divider()
                .background(AppTheme.cardBorder)
            
            // Mensaje contextual 1:1 con Android
            if viewModel.recordedPoints.isEmpty {
                VStack(spacing: 6) {
                    Text("¡Ruta iniciada! Toma la foto del Punto #1")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(AppTheme.accent)
                    
                    Text("Toca el botón amarillo o un estado especial para abrir la cámara y registrar el punto al instante.")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(AppTheme.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
                .background(AppTheme.accent.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            } else if let lastPt = viewModel.recordedPoints.last {
                HStack {
                    Image(systemName: "mappin.circle")
                        .foregroundStyle(AppTheme.accentCyan)
                    Text("Último punto (#\(lastPt.id)): \(lastPt.descriptionText)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(AppTheme.textPrimary)
                        .lineLimit(1)
                    Spacer()
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
                .background(AppTheme.cardMuted.opacity(0.6))
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .onTapGesture {
                    viewModel.selectedPointForDetail = lastPt
                }
            }
            
            // Botón Principal: Registrar Punto con Cámara en 1 Clic (1:1 Android MainScreen.kt:2456)
            Button {
                viewModel.startQuickPointCapture(estado: 1, isFinRuta: false)
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 18, weight: .bold))
                    Text(viewModel.recordedPoints.isEmpty ? "REGISTRAR PRIMER PUNTO" : "REGISTRAR SIGUIENTE PUNTO")
                        .font(.system(size: 14, weight: .heavy))
                }
                .foregroundStyle(Color(red: 15/255, green: 23/255, blue: 42/255))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    LinearGradient(
                        colors: [
                            Color(red: 250/255, green: 204/255, blue: 21/255), // Amarillo de Alta Visibilidad
                            Color(red: 234/255, green: 179/255, blue: 8/255)
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .shadow(color: Color(red: 250/255, green: 204/255, blue: 21/255).opacity(0.35), radius: 6, y: 3)
            }
            .buttonStyle(.plain)
            
            // Barra de Estados Rápidos: 1 Clic -> Abre Cámara y Registra con ese Estado (1:1 Android MainScreen.kt:2510-2592)
            VStack(alignment: .leading, spacing: 6) {
                Text("O REGISTRAR PUNTO DE ESTADO (1 CLIC + FOTO):")
                    .font(.system(size: 10, weight: .heavy))
                    .foregroundStyle(AppTheme.textSecondary)
                
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(QuickPointStateOption.specialOptions) { item in
                            let info = item.info
                            Button {
                                viewModel.startQuickPointCapture(estado: item.id, isFinRuta: false)
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: info.systemImage)
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundStyle(info.color)
                                    Text(item.name)
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundStyle(AppTheme.textPrimary)
                                }
                                .padding(.horizontal, 10)
                                .padding(.vertical, 8)
                                .background(info.backgroundColor)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .stroke(info.color.opacity(0.6), lineWidth: 1)
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            
            // Botón para dictar o agregar descripción extra al último punto (1:1 Android)
            if let lastPt = viewModel.recordedPoints.last, !lastPt.isRouteEnd {
                Button {
                    viewModel.isShowingDictateSheet = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "mic.fill")
                            .font(.system(size: 15, weight: .bold))
                        Text("DICTAR COMENTARIO AL PUNTO #\(lastPt.id)")
                            .font(.system(size: 13, weight: .heavy))
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(Color(red: 76/255, green: 175/255, blue: 80/255))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .shadow(color: Color(red: 76/255, green: 175/255, blue: 80/255).opacity(0.35), radius: 6, y: 2)
                }
                .buttonStyle(.plain)
            }
            
            // Botón para Abrir Modal / Panel de Grabar Ruta con Telemetría
            Button {
                viewModel.isShowingRecordingDashboard = true
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "gauge.with.needle.fill")
                        .font(.system(size: 15, weight: .bold))
                    Text("ABRIR PANEL DE GRABACIÓN")
                        .font(.system(size: 13, weight: .heavy))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .background(AppTheme.primary)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .shadow(color: AppTheme.primary.opacity(0.35), radius: 6, y: 2)
            }
            .buttonStyle(.plain)
            
            // Botón Secundario: Ver / Ocultar Lista de Puntos (1:1 Android isPointsListExpanded)
            if !viewModel.recordedPoints.isEmpty {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        isPointsListExpanded.toggle()
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: isPointsListExpanded ? "eye.slash.fill" : "eye.fill")
                            .font(.system(size: 12))
                        Text(isPointsListExpanded ? "OCULTAR LISTA DE PUNTOS" : "VER LISTA DE PUNTOS (\(viewModel.recordedPoints.count))")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundStyle(AppTheme.accent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .background(AppTheme.cardMuted.opacity(0.5))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(AppTheme.accent.opacity(0.3), lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
            }
            
            // Lista compacta desplegable de puntos registrados (tocar cualquiera abre detalle/edición 1:1 Android)
            if isPointsListExpanded && !viewModel.recordedPoints.isEmpty {
                ScrollView {
                    VStack(spacing: 6) {
                        ForEach(viewModel.recordedPoints) { pt in
                            RecordedPointRowView(point: pt)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    viewModel.selectedPointForDetail = pt
                                }
                        }
                    }
                }
                .frame(maxHeight: 140)
            }
            
            // Acciones secundarias: Finalizar ruta
            HStack(spacing: 12) {
                Button {
                    viewModel.requestFinishRoute()
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "flag.checkered")
                            .font(.system(size: 12, weight: .bold))
                        Text("FINALIZAR RUTA")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundStyle(AppTheme.alert)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(AppTheme.alert.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(AppTheme.alert.opacity(0.3), lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 2)
        }
        .padding(16)
        .background(AppTheme.cardBackground.opacity(0.92))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(AppTheme.accent.opacity(0.35), lineWidth: 1.5)
        )
    }
}

// MARK: - Subcomponentes de Fila (Optimizados para el Compilador de Swift)

private struct ConsumedPointRowView: View {
    let index: Int
    let pt: RoutePoint
    let nextPhrase: String?
    let onSelect: () -> Void
    
    var body: some View {
        let stateInfo = PointStateInfo.forPoint(estado: pt.estado, isRouteEnd: pt.isRouteEnd, description: pt.descriptionText)
        
        HStack(spacing: 12) {
            PointStateBadgeView(estado: pt.estado, isRouteEnd: pt.isRouteEnd, description: pt.descriptionText, size: 46)
            
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    if pt.isRouteEnd {
                        Text("🏁")
                            .font(.system(size: 13))
                    } else if index == 0 {
                        Text("🟢")
                            .font(.system(size: 11))
                    }
                    Text("#\(index + 1) · \(pt.descriptionText.isEmpty ? stateInfo.name : pt.descriptionText)")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(AppTheme.textPrimary)
                        .lineLimit(1)
                }
                
                if let nextPhrase = nextPhrase {
                    Text(nextPhrase)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Color(red: 94/255, green: 234/255, blue: 212/255))
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    Text("Último punto del recorrido")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(AppTheme.success)
                }
                
                HStack(spacing: 6) {
                    Text(pt.hasConnectivity ? "📶 Conectado" : "📵 Sin señal")
                        .font(.system(size: 10, weight: pt.hasConnectivity ? .bold : .regular))
                        .foregroundStyle(pt.hasConnectivity ? Color(red: 16/255, green: 185/255, blue: 129/255) : AppTheme.textSecondary)
                    
                    Text(stateInfo.name)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(stateInfo.color)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(stateInfo.backgroundColor)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }
            }
            
            Spacer()
            
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(AppTheme.textSecondary.opacity(0.6))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(AppTheme.cardBackground.opacity(0.95))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(AppTheme.cardBorder, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.15), radius: 4, y: 2)
        .contentShape(Rectangle())
        .onTapGesture {
            onSelect()
        }
    }
}

private struct RecordedPointRowView: View {
    let point: RoutePoint
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("#\(point.id) \(point.descriptionText)")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(AppTheme.textPrimary)
                Text(String(format: "Alt: %.0fm", point.altitude))
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(AppTheme.textSecondary)
            }
            Spacer()
            if point.isRouteEnd {
                Text("FIN")
                    .font(.system(size: 9, weight: .bold))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(AppTheme.alert.opacity(0.2))
                    .foregroundStyle(AppTheme.alert)
                    .clipShape(Capsule())
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(AppTheme.cardBackground.opacity(0.6))
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}
