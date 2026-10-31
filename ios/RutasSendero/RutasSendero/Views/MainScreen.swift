//
//  MainScreen.swift
//  RutasSendero
//
//  Pantalla Principal de RutasSendero en SwiftUI.
//  Integra el mapa interactivo MapKit, barra de estado superior,
//  panel flotante de telemetría y modales de registro.
//

import SwiftUI
import CoreLocation
import SwiftData

public struct MainScreen: View {
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel = MainViewModel()
    
    public init() {}
    
    public var body: some View {
        @Bindable var viewModel = viewModel
        ZStack(alignment: .top) {
            // 1. MODO MAPA: Solo disponible cuando no se está grabando ruta (1:1 Android: currentAppRole != "registrar")
            if viewModel.showMapScreen && viewModel.recordingState == .stopped && viewModel.activeRole != .registrar {
                ZStack(alignment: .top) {
                    MainMapView(viewModel: viewModel)
                        .ignoresSafeArea()
                    
                    VStack {
                        HeaderBarView(viewModel: viewModel)
                        Spacer()
                        RecordingDashboardView(viewModel: viewModel)
                    }
                }
                .transition(.opacity)
            } else {
                // 2. MODO PORTADA (1:1 Android): Vista ultraligera con 0% de sobrecarga de GPU/Mapas
                VStack(spacing: 0) {
                    HeaderBarView(viewModel: viewModel)
                    
                    PortadaView(viewModel: viewModel)
                    
                    FooterBarView(viewModel: viewModel)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(
                    AppTheme.dynamicGradient(for: viewModel.activeRole)
                        .ignoresSafeArea()
                )
                .animation(.easeInOut(duration: 0.35), value: viewModel.activeRole)
                .transition(.opacity)
            }
            
            // 3. Capa de Modo Bolsillo (Bloqueo táctil de pantalla)
            if viewModel.isPocketModeActive {
                ZStack {
                    Color.black.opacity(0.92)
                        .ignoresSafeArea()
                    
                    VStack(spacing: 16) {
                        Image(systemName: "lock.shield.fill")
                            .font(.system(size: 64))
                            .foregroundStyle(AppTheme.warningYellow)
                        
                        Text("MODO BOLSILLO ACTIVO")
                            .font(.headline)
                            .foregroundStyle(.white)
                        
                        Text("La pantalla está protegida contra toques accidentales en el sendero. El rastreo GPS sigue activo.")
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                        
                        Button {
                            viewModel.isPocketModeActive = false
                        } label: {
                            Text("Toca para Desbloquear")
                                .font(.subheadline).bold()
                                .foregroundStyle(.white)
                                .padding(.horizontal, 24)
                                .padding(.vertical, 12)
                                .background(AppTheme.alert)
                                .clipShape(Capsule())
                        }
                        .padding(.top, 16)
                    }
                }
            }
            
            // 4. Diálogo de Orden de Visor / Guía (Ida vs Retorno 1:1 Android)
            if viewModel.isShowingVisorOrderDialog, let route = viewModel.pendingRouteForOrderSelection {
                VisorOrderDialogView(viewModel: viewModel, route: route)
                    .transition(.opacity)
                    .zIndex(100)
            }
            
            // 5. Banner flotante de orientación y estado (Toast 1:1 Android)
            if let message = viewModel.saveStatusMessage {
                VStack {
                    Spacer().frame(height: 70)
                    HStack(spacing: 8) {
                        Image(systemName: "location.north.circle.fill")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(AppTheme.accent)
                        
                        Text(message)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.white)
                            .lineLimit(3)
                        
                        Spacer()
                        
                        Button {
                            withAnimation {
                                viewModel.saveStatusMessage = nil
                            }
                        } label: {
                            Image(systemName: "xmark")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color(red: 15/255, green: 23/255, blue: 42/255).opacity(0.95))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(AppTheme.accent.opacity(0.4), lineWidth: 1)
                    )
                    .shadow(color: Color.black.opacity(0.35), radius: 8, y: 4)
                    .padding(.horizontal, 16)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    
                    Spacer()
                }
                .zIndex(150)
            }
        }
        .onChange(of: viewModel.saveStatusMessage) { _, newMsg in
            if newMsg != nil {
                Task {
                    try? await Task.sleep(for: .seconds(5))
                    if viewModel.saveStatusMessage == newMsg {
                        withAnimation {
                            viewModel.saveStatusMessage = nil
                        }
                    }
                }
            }
        }
        .task {
            // Inyectar repositorio de persistencia SwiftData
            if viewModel.rutasRepository == nil {
                viewModel.rutasRepository = SwiftDataRutasRepository(modelContainer: modelContext.container)
            }
            
            // Retardo para que la jerarquía de ventanas de SwiftUI termine de montarse
            try? await Task.sleep(for: .milliseconds(400))
            
            // Verificar si ya existe perfil local de senderista.
            // Si es primera vez, se abre automáticamente la pantalla de registro obligatoria
            await viewModel.checkSenderistaRegistration()
            
            #if targetEnvironment(simulator)
            // En simulador, simular detección de un compañero cercano tras 2 segundos para validar UI
            try? await Task.sleep(for: .seconds(2))
            if viewModel.nearbyPeers.isEmpty {
                viewModel.simulatePeerDetected(beaconId: "4B3C2D1E0F9A", distanceMeters: 14.5)
            }
            #endif
        }
        // MARK: - Modales y Diálogos (Unificado mediante ActiveSheet)
        .sheet(item: $viewModel.activeSheet) { sheet in
            switch sheet {
            case .senderistaRegistration:
                SenderistaProfileSheetView(viewModel: viewModel)
            case .localRoutes:
                LocalRoutesSheetView(viewModel: viewModel)
            case .newRoute:
                NewRouteSheetView(viewModel: viewModel)
            case .addPoint:
                AddPointSheetView(viewModel: viewModel)
            case .routeDetails:
                RouteDetailsSheetView(viewModel: viewModel)
            case .nearbyPeers:
                NearbyPeersSheetView(viewModel: viewModel)
            case .soundAlerts:
                SoundAlertsSheetView(viewModel: viewModel)
            case .quickDictate:
                QuickDictateSheetView(viewModel: viewModel)
            case .cloudRoutes:
                CloudRoutesSheetView(viewModel: viewModel)
            case .recordingDashboard:
                NavigationStack {
                    RecordingDashboardView(viewModel: viewModel)
                        .navigationTitle("Panel de Grabación")
                        #if os(iOS)
                        .navigationBarTitleDisplayMode(.inline)
                        #endif
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button("Cerrar") {
                                    viewModel.activeSheet = nil
                                }
                            }
                        }
                }
                .presentationDetents([.medium, .large])
            }
        }
        // Diálogo de Detalle del Punto con Foto y Telemetría
        .sheet(item: $viewModel.selectedPointForDetail) { point in
            PointDetailSheetView(viewModel: viewModel, point: point)
        }
        // Visor de Foto a Pantalla Completa con Rotación, Guardado Local y Compartir
        .fullScreenCover(isPresented: $viewModel.isShowingFullScreenPhoto) {
            if let path = viewModel.fullScreenPhotoPath {
                FullScreenPhotoViewer(photoPath: path) {
                    viewModel.isShowingFullScreenPhoto = false
                }
            }
        }
        .alert("Acerca de RutasSendero", isPresented: $viewModel.isShowingAboutSheet) {
            Button("Aceptar", role: .cancel) {}
        } message: {
            Text("RutasSendero v1.0 (iOS)\nSistema de Rastreo Satelital y Balizas BLE para Senderismo y Rescate.\nDesarrollado para Colima y Villa de Álvarez.")
        }
        // Alerta de confirmación para finalizar ruta
        .alert("Finalizar Ruta", isPresented: $viewModel.isShowingFinishRouteConfirm) {
            Button("Cancelar", role: .cancel) {}
            Button("Finalizar y Guardar", role: .destructive) {
                viewModel.finishRoute(force: true)
            }
        } message: {
            Text("¿Deseas dar por concluida la grabación de '\(viewModel.activeRoute?.name ?? "esta ruta")'? Se calcularán las estadísticas finales y se cerrará la traza.")
        }
        // Alerta de distancia mínima de sendero (< 4.0 km)
        .alert("Ruta Demasiado Corta", isPresented: $viewModel.isShowingRouteTooShortAlert) {
            Button("Continuar Caminando", role: .cancel) {}
            Button("Finalizar de Todos Modos", role: .destructive) {
                viewModel.finishRoute(force: true)
            }
        } message: {
            let currentKm = viewModel.totalDistanceMeters / 1000.0
            Text(String(format: "La distancia registrada (%.2f km) es menor a los %.1f km recomendados para senderismo oficial. ¿Deseas finalizar de todos modos?", currentKm, viewModel.minimumRouteDistanceKm))
        }
    }
}

// MARK: - Hoja de Información / Resumen de Ruta

public struct RouteDetailsSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    public var body: some View {
        NavigationStack {
            List {
                Section("Ruta") {
                    HStack {
                        Text("Nombre")
                        Spacer()
                        Text(viewModel.activeRoute?.name ?? "Sin ruta activa")
                            .foregroundStyle(AppTheme.textPrimary)
                            .bold()
                    }
                    if let date = viewModel.activeRoute?.date, !date.isEmpty {
                        HStack {
                            Text("Fecha")
                            Spacer()
                            Text(date)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                    HStack {
                        Text("Modo")
                        Spacer()
                        Text(viewModel.activeRole == .visor ? "Modo Visor" : (viewModel.activeRole == .guia ? "Modo Guía" : "Modo Trazar"))
                            .foregroundStyle(viewModel.activeRole == .visor ? Color(red: 56/255, green: 189/255, blue: 248/255) : AppTheme.accent)
                    }
                }
                
                // Categoría Cínica de Senderismo 1:1 Kotlin RutaDetailDialog
                if let route = viewModel.activeRoute {
                    let totalDist = max(viewModel.totalDistanceMeters, route.gpsDistanceMeters)
                    let distKm = totalDist / 1000.0
                    let elevation = max(viewModel.elevationStats.elevationGain, route.elevationGain)
                    let categoria: String = {
                        if distKm < 5.0 && elevation < 150.0 { return "Principiante" }
                        if distKm < 12.0 && elevation < 500.0 { return "Intermedio" }
                        if distKm < 25.0 && elevation < 1200.0 { return "Experto" }
                        return "Nivel Dios"
                    }()
                    let frase: String = {
                        switch categoria {
                        case "Principiante": return "Es fácil, hasta mi abuelita en pantuflas lo haría."
                        case "Intermedio": return "Tienes que entrenar, mi chavo, para poder terminarla."
                        case "Experto": return "Lleva suficiente agua y despídete de tus rodillas por una semana."
                        default: return "Sólo para cabritos monteses y semidioses del Olimpo. ¡Buena suerte!"
                        }
                    }()
                    
                    Section("Nivel de Exigencia") {
                        HStack {
                            Text("Categoría")
                            Spacer()
                            Text(categoria)
                                .font(.system(size: 13, weight: .bold))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Color(red: 56/255, green: 189/255, blue: 248/255).opacity(0.18))
                                .foregroundStyle(Color(red: 56/255, green: 189/255, blue: 248/255))
                                .clipShape(Capsule())
                        }
                        Text("\"\(frase)\"")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(AppTheme.textSecondary)
                            .italic()
                    }
                }
                
                Section("Métricas de la Ruta") {
                    let totalDist = max(viewModel.totalDistanceMeters, viewModel.activeRoute?.gpsDistanceMeters ?? 0.0)
                    HStack {
                        Text("Distancia 3D")
                        Spacer()
                        Text(RouteStatsCalculator.formatDistance(totalDist))
                            .bold()
                    }
                    let totalSecs = viewModel.elapsedSeconds > 0 ? viewModel.elapsedSeconds : (viewModel.activeRoute?.durationSeconds ?? 0)
                    if totalSecs > 0 {
                        HStack {
                            Text("Tiempo estimado / ruta")
                            Spacer()
                            Text(RouteStatsCalculator.formatDuration(totalSecs))
                                .monospaced()
                        }
                    }
                    let gain = max(viewModel.elevationStats.elevationGain, viewModel.activeRoute?.elevationGain ?? 0.0)
                    HStack {
                        Text("Desnivel positivo")
                        Spacer()
                        Text(String(format: "+%.0f m", gain))
                            .foregroundStyle(AppTheme.primaryLight)
                    }
                    let loss = max(viewModel.elevationStats.elevationLoss, viewModel.activeRoute?.elevationLoss ?? 0.0)
                    HStack {
                        Text("Desnivel negativo")
                        Spacer()
                        Text(String(format: "-%.0f m", loss))
                            .foregroundStyle(AppTheme.alert)
                    }
                    let climbs = max(viewModel.elevationStats.climbsCount, viewModel.activeRoute?.climbsCount ?? 0)
                    let descents = max(viewModel.elevationStats.descentsCount, viewModel.activeRoute?.descentsCount ?? 0)
                    if climbs > 0 || descents > 0 {
                        HStack {
                            Text("Subidas / Bajadas")
                            Spacer()
                            Text("\(climbs) subidas / \(descents) bajadas")
                        }
                    }
                    let calories = viewModel.caloriesBurned > 0 ? viewModel.caloriesBurned : RouteStatsCalculator.calculateCalories(weightKg: viewModel.hikerWeightKg, distanceMeters: totalDist)
                    HStack {
                        Text("Calorías estimadas")
                        Spacer()
                        Text(String(format: "%.0f kcal", calories))
                    }
                    HStack {
                        Text("Puntos guardados")
                        Spacer()
                        Text("\(viewModel.recordedPoints.count)")
                            .bold()
                    }
                }
                
                Section("Almacenamiento Local (SwiftData)") {
                    HStack {
                        Text("Estado de persistencia")
                        Spacer()
                        if viewModel.isSavedLocally {
                            Label("Guardada", systemImage: "checkmark.circle.fill")
                                .foregroundStyle(AppTheme.success)
                        } else {
                            Text(viewModel.recordingState == .stopped ? "Listo para guardar" : "En grabación")
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                    if let msg = viewModel.saveStatusMessage {
                        Text(msg)
                            .font(.caption)
                            .foregroundStyle(AppTheme.accentCyan)
                    }
                }
                
                if !viewModel.nearbyPeers.isEmpty {
                    Section("Compañeros BLE Detectados (\(viewModel.nearbyPeers.count))") {
                        ForEach(viewModel.nearbyPeersList) { peer in
                            HStack {
                                Image(systemName: "figure.walk")
                                    .foregroundStyle(AppTheme.accentCyan)
                                    .font(.title3)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Baliza: \(peer.beaconId)")
                                        .font(.subheadline).bold()
                                    Text("RSSI: \(peer.rssi) dBm")
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textSecondary)
                                }
                                Spacer()
                                Text(String(format: "%.1f m", peer.estimatedDistanceMeters))
                                    .font(.headline)
                                    .foregroundStyle(AppTheme.accentCyan)
                            }
                        }
                    }
                }
                
                if viewModel.recordingState != .stopped {
                    Section {
                        Button(role: .destructive) {
                            viewModel.cancelRecording()
                            dismiss()
                        } label: {
                            HStack {
                                Spacer()
                                Text("Cancelar y Descartar Grabación")
                                Spacer()
                            }
                        }
                    }
                }
            }
            .navigationTitle("Detalles del Sendero")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Cerrar") {
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}
