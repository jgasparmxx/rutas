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
    @Environment(\.scenePhase) private var scenePhase
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
                        // En modo Visor sin simulación activa NO se muestra recuadro inferior (el botón Play ya está arriba en HeaderBar)
                        if (viewModel.activeRole == .visor && viewModel.isVirtualTourActive) || viewModel.activeRole == .guia {
                            RecordingDashboardView(viewModel: viewModel)
                        }
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
            
            // 5. Alerta Persistente de Auxilio 911 de Compañero Cercano con opción directa de Ver o Silenciar Alarma (1:1 Android)
            if let emergencyPeer = viewModel.activeUnacknowledgedEmergencyPeer {
                let peerId = emergencyPeer.beaconId.lowercased()
                let peerName = viewModel.peerSenderistaProfiles[peerId]?.name ?? emergencyPeer.beaconId.uppercased()
                VStack {
                    Spacer().frame(height: 68)
                    VStack(spacing: 8) {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 18, weight: .heavy))
                                .foregroundStyle(.yellow)
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text("¡AUXILIO 911! \(peerName)")
                                    .font(.system(size: 13, weight: .heavy))
                                    .foregroundStyle(.white)
                                    .lineLimit(1)
                                Text("Distancia estimada: ~\(Int(emergencyPeer.estimatedDistanceMeters)) m")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(Color.yellow)
                            }
                            Spacer()
                        }
                        
                        HStack(spacing: 8) {
                            Button {
                                viewModel.isShowingNearbyPeersSheet = true
                            } label: {
                                Text("Ubicar en Mapa")
                                    .font(.system(size: 12, weight: .heavy))
                                    .foregroundStyle(AppTheme.alert)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .background(Color.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            .buttonStyle(.plain)
                            
                            Button {
                                viewModel.acknowledgePeerEmergency(beaconId: emergencyPeer.beaconId)
                            } label: {
                                Text("Silenciar Alarma")
                                    .font(.system(size: 12, weight: .heavy))
                                    .foregroundStyle(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .background(Color.black.opacity(0.35))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(Color.white.opacity(0.75), lineWidth: 1)
                                    )
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(12)
                    .background(AppTheme.alert)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(Color.yellow, lineWidth: 1.5)
                    )
                    .shadow(color: AppTheme.alert.opacity(0.55), radius: 10, y: 4)
                    .padding(.horizontal, 14)
                    
                    Spacer()
                }
                .zIndex(160)
            }
            
            // 6. Banner flotante de orientación y estado (Toast 1:1 Android)
            if let message = viewModel.saveStatusMessage, viewModel.activeUnacknowledgedEmergencyPeer == nil {
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
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                viewModel.refreshBleScanning()
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
            case .settings:
                SettingsSheetView(viewModel: viewModel)
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
        // Cámara Directa en 1 Clic para Registrar Punto al Instante (1:1 Android MainScreen.kt:426-453)
        .fullScreenCover(isPresented: $viewModel.isShowingQuickCameraForPoint) {
            ImagePickerView(
                selectedImage: .constant(nil),
                imagePath: .constant(nil),
                preferredSourceType: .camera,
                onImageSaved: { savedPath in
                    viewModel.completeQuickPointCapture(photoPath: savedPath)
                }
            )
            .ignoresSafeArea()
        }
        .alert("Acerca de RutasSendero", isPresented: $viewModel.isShowingAboutSheet) {
            Button("Aceptar", role: .cancel) {}
        } message: {
            Text("RutasSendero v1.0 (iOS)\nSistema de Rastreo Satelital y Balizas BLE para Senderismo y Rescate.\nDesarrollado para Colima y Villa de Álvarez.")
        }
        // Alerta de confirmación para finalizar ruta
        .alert("Finalizar Ruta", isPresented: $viewModel.isShowingFinishRouteConfirm) {
            Button("Cancelar", role: .cancel) {}
            Button("Tomar Foto Fin y Guardar") {
                viewModel.startQuickPointCapture(estado: 1, isFinRuta: true)
            }
            Button("Finalizar sin Foto", role: .destructive) {
                viewModel.finishRoute(force: true)
            }
        } message: {
            Text("¿Deseas dar por concluida la grabación de '\(viewModel.activeRoute?.name ?? "esta ruta")'? Puedes tomar la foto del punto final o guardar directamente.")
        }
        // Alerta de distancia mínima de sendero (< 4.0 km)
        .alert("Ruta Demasiado Corta", isPresented: $viewModel.isShowingRouteTooShortAlert) {
            Button("Continuar Caminando", role: .cancel) {}
            Button("Tomar Foto Fin y Guardar") {
                viewModel.startQuickPointCapture(estado: 1, isFinRuta: true)
            }
            Button("Finalizar sin Foto", role: .destructive) {
                viewModel.finishRoute(force: true)
            }
        } message: {
            let currentKm = viewModel.totalDistanceMeters / 1000.0
            Text(String(format: "La distancia registrada (%.2f km) es menor a los %.1f km recomendados para senderismo oficial. ¿Deseas finalizar de todos modos?", currentKm, viewModel.minimumRouteDistanceKm))
        }
    }
}

// MARK: - Diálogo Resumen de Ruta (1:1 con Android RutaDetailDialog)

public struct RouteDetailsSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    // Paleta cálida 1:1 con Android RutaDetailDialog (Components.kt:6020-6023)
    private let warmBg = Color(red: 34/255, green: 31/255, blue: 28/255)      // #221F1C
    private let textColor = Color(red: 250/255, green: 246/255, blue: 240/255)// #FAF6F0
    private let textMuted = Color(red: 199/255, green: 189/255, blue: 177/255)// #C7BDB1
    private let accentColor = AppTheme.accent                                  // #2DD4BF
    
    public var body: some View {
        ZStack {
            Color.black.opacity(0.6)
                .ignoresSafeArea()
                .onTapGesture {
                    dismiss()
                }
            
            if let route = viewModel.activeRoute {
                ScrollView(showsIndicators: false) {
                    routeSummaryCard(route: route)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 24)
                }
            } else {
                VStack(spacing: 14) {
                    Text("Sin ruta seleccionada")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(textColor)
                    Button {
                        dismiss()
                    } label: {
                        Text("Cerrar")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Color(red: 15/255, green: 23/255, blue: 42/255))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 11)
                            .background(accentColor)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                }
                .padding(20)
                .background(warmBg)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(accentColor.opacity(0.4), lineWidth: 1)
                )
                .padding(.horizontal, 24)
            }
        }
        .presentationBackground(.clear)
    }
    
    @ViewBuilder
    private func routeSummaryCard(route: Route) -> some View {
        let userPhone = viewModel.currentHikerProfile?.phone ?? ""
        let isOwn = route.isOwnRoute(userPhone: userPhone)
        let gpsDistanceMeters: Double = {
            if !viewModel.recordedTrace.isEmpty {
                let d = RouteStatsCalculator.calculateTraceDistance(tracePoints: viewModel.recordedTrace)
                if d > 0 { return d }
            }
            return max(route.gpsDistanceMeters, viewModel.totalDistanceMeters)
        }()
        let effectiveDurationSeconds = resolveEffectiveDuration(for: route)
        let durStr: String = {
            if effectiveDurationSeconds > 0 {
                let hours = effectiveDurationSeconds / 3600
                let minutes = (effectiveDurationSeconds % 3600) / 60
                return String(format: "%02d:%02d", hours, minutes)
            }
            return "N/A"
        }()
        let avgSpeedStr: String = {
            if effectiveDurationSeconds > 0 && gpsDistanceMeters > 0 {
                let kmh = (gpsDistanceMeters / 1000.0) / (Double(effectiveDurationSeconds) / 3600.0)
                return String(format: "%.1f km/h", kmh)
            }
            return "--"
        }()
        let connectivityPct: Double = {
            if route.connectivity > 0 {
                return route.connectivity
            }
            let pts = viewModel.recordedPoints
            guard !pts.isEmpty else { return 100.0 }
            let connected = pts.filter { $0.hasConnectivity }.count
            return (Double(connected) / Double(pts.count)) * 100.0
        }()
        let gain = max(viewModel.elevationStats.elevationGain, route.elevationGain)
        let loss = max(viewModel.elevationStats.elevationLoss, route.elevationLoss)
        let climbs = max(viewModel.elevationStats.climbsCount, route.climbsCount)
        let descents = max(viewModel.elevationStats.descentsCount, route.descentsCount)
        let diffInfo = resolveDifficulty(distanceKm: gpsDistanceMeters / 1000.0, elevationGain: gain)
        let profileWeight: Double? = {
            if let w = viewModel.currentHikerProfile?.weightKg, w > 0 {
                return w
            }
            return nil
        }()
        let caloriesText = RouteStatsCalculator.getCaloriesSummary(
            distanceKm: gpsDistanceMeters / 1000.0,
            elevationGainMeters: gain,
            userWeightKg: profileWeight
        )
        let startDistanceStr: String? = {
            guard let userLoc = viewModel.locationTracker.currentLocation else { return nil }
            let startLoc = CLLocation(latitude: route.originLatitude, longitude: route.originLongitude)
            let d = userLoc.distance(from: startLoc)
            if d >= 1000.0 {
                return String(format: "%.1f km", d / 1000.0)
            } else {
                return "\(Int(d)) m"
            }
        }()
        
        VStack(spacing: 0) {
            // 1. Título: Ruta y Fecha
            Text(route.date.isEmpty ? "Ruta" : "Ruta - \(route.date)")
                .font(.system(size: 14, weight: .regular))
                .foregroundStyle(textMuted)
                .multilineTextAlignment(.center)
            
            Spacer().frame(height: 4)
            
            // 2. Nombre de la ruta
            Text(route.name)
                .font(.system(size: 18, weight: .heavy))
                .foregroundStyle(textColor)
                .multilineTextAlignment(.center)
            
            // 3. Switch Pública / Privada + Clave (solo si es ruta propia, 1:1 Android)
            if isOwn {
                Spacer().frame(height: 10)
                HStack(spacing: 10) {
                    Toggle("", isOn: Binding(
                        get: { route.isPublic },
                        set: { newValue in
                            Task {
                                await viewModel.updateActiveRoutePrivacy(isPublic: newValue)
                            }
                        }
                    ))
                    .labelsHidden()
                    .tint(accentColor)
                    
                    Text(route.isPublic ? "Pública" : "Privada")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(textColor)
                    
                    if !route.isPublic {
                        let codeLabel = route.accessCode.isEmpty ? String(route.id.prefix(5)).uppercased() : route.accessCode
                        Text("Clave: \(codeLabel)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(AppTheme.alert)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(AppTheme.alert.opacity(0.2))
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                    }
                }
            }
            
            Spacer().frame(height: 16)
            Rectangle()
                .fill(textMuted.opacity(0.2))
                .frame(height: 1)
            Spacer().frame(height: 14)
            
            // 4. Duración, Dist. 3D y Vel. Media (3 columnas 1:1 Android)
            HStack {
                VStack(spacing: 3) {
                    Text("Duración")
                        .font(.system(size: 11))
                        .foregroundStyle(textMuted)
                    Text("\(durStr) hrs")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(textColor)
                }
                .frame(maxWidth: .infinity)
                
                VStack(spacing: 3) {
                    Text("Dist. 3D")
                        .font(.system(size: 11))
                        .foregroundStyle(textMuted)
                    Text(String(format: "%.2f kms", gpsDistanceMeters / 1000.0))
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(textColor)
                }
                .frame(maxWidth: .infinity)
                
                VStack(spacing: 3) {
                    Text("Vel. Media")
                        .font(.system(size: 11))
                        .foregroundStyle(textMuted)
                    Text(avgSpeedStr)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(textColor)
                }
                .frame(maxWidth: .infinity)
            }
            
            Spacer().frame(height: 8)
            
            // 5. Conectividad centrada con letras amarillas e icono Wi-Fi (1:1 Android)
            HStack(spacing: 6) {
                Image(systemName: "wifi")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(AppTheme.warningYellow)
                Text(String(format: "Conectividad: %.2f%%", connectivityPct))
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(AppTheme.warningYellow)
            }
            
            Spacer().frame(height: 12)
            Rectangle()
                .fill(textMuted.opacity(0.15))
                .frame(height: 1)
            Spacer().frame(height: 12)
            
            // 6. Desnivel y Tramos integrados en una sola fila (4 columnas 1:1 Android)
            HStack(spacing: 4) {
                VStack(spacing: 3) {
                    HStack(spacing: 2) {
                        Image(systemName: "arrow.up")
                            .font(.system(size: 10, weight: .bold))
                        Text("Desn. Pos")
                            .font(.system(size: 10))
                    }
                    .foregroundStyle(accentColor)
                    
                    Text(String(format: "%.1f m", gain))
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(textColor)
                }
                .frame(maxWidth: .infinity)
                
                VStack(spacing: 3) {
                    HStack(spacing: 2) {
                        Image(systemName: "arrow.down")
                            .font(.system(size: 10, weight: .bold))
                        Text("Desn. Neg")
                            .font(.system(size: 10))
                    }
                    .foregroundStyle(AppTheme.alert)
                    
                    Text(String(format: "%.1f m", loss))
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(textColor)
                }
                .frame(maxWidth: .infinity)
                
                VStack(spacing: 3) {
                    HStack(spacing: 2) {
                        Image(systemName: "arrow.up")
                            .font(.system(size: 10, weight: .bold))
                        Text("Subidas")
                            .font(.system(size: 10))
                    }
                    .foregroundStyle(accentColor)
                    
                    Text("\(climbs)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(textColor)
                }
                .frame(maxWidth: .infinity)
                
                VStack(spacing: 3) {
                    HStack(spacing: 2) {
                        Image(systemName: "arrow.down")
                            .font(.system(size: 10, weight: .bold))
                        Text("Bajadas")
                            .font(.system(size: 10))
                    }
                    .foregroundStyle(AppTheme.alert)
                    
                    Text("\(descents)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(textColor)
                }
                .frame(maxWidth: .infinity)
            }
            
            Spacer().frame(height: 12)
            Rectangle()
                .fill(textMuted.opacity(0.15))
                .frame(height: 1)
            Spacer().frame(height: 12)
            
            // 7. Valoración de Dificultad + Frase Cínica (1:1 Android)
            VStack(spacing: 6) {
                Text("Valoración de Dificultad")
                    .font(.system(size: 12))
                    .foregroundStyle(textMuted)
                
                Text(diffInfo.category)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(textColor)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
                    .background(diffInfo.color)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
                
                Text("\"\(diffInfo.phrase)\"")
                    .font(.system(size: 12))
                    .italic()
                    .foregroundStyle(textMuted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 12)
                    .padding(.top, 2)
            }
            
            Spacer().frame(height: 16)
            
            // 8. Tarjeta de Calorías Estimadas (Margaria + Pandolf 1:1 Android CaloriesSummaryCard)
            Text(caloriesText)
                .font(.system(size: 12))
                .lineSpacing(3)
                .foregroundStyle(textMuted)
                .multilineTextAlignment(.center)
                .padding(.vertical, 4)
            
            Spacer().frame(height: 14)
            
            // 9. Distancia al inicio de la ruta + Botón Cómo llegar (Google Maps) (1:1 Android)
            if let distStr = startDistanceStr {
                Text("Estás a \(distStr) del inicio de la ruta")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(accentColor)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 12)
            } else {
                Text("Distancia al inicio no disponible (GPS inactivo)")
                    .font(.system(size: 12))
                    .foregroundStyle(textMuted)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 12)
            }
            
            Spacer().frame(height: 10)
            
            Button {
                viewModel.openGoogleMapsDirections(
                    lat: route.originLatitude,
                    lon: route.originLongitude,
                    label: route.name
                )
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "location.north.fill")
                        .font(.system(size: 13, weight: .bold))
                    Text("Cómo llegar (Google Maps)")
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundStyle(Color(red: 59/255, green: 130/255, blue: 246/255)) // #3B82F6
                .padding(.vertical, 9)
                .padding(.horizontal, 16)
                .background(Color(red: 59/255, green: 130/255, blue: 246/255).opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color(red: 59/255, green: 130/255, blue: 246/255).opacity(0.4), lineWidth: 1)
                )
            }
            .buttonStyle(.plain)
            
            if viewModel.recordingState != .stopped {
                Spacer().frame(height: 12)
                Button(role: .destructive) {
                    viewModel.cancelRecording()
                    dismiss()
                } label: {
                    Text("Cancelar y Descartar Grabación")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(AppTheme.alert)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
            }
            
            Spacer().frame(height: 20)
            
            // 10. Botón Cerrar (1:1 Android)
            Button {
                dismiss()
            } label: {
                Text("Cerrar")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color(red: 15/255, green: 23/255, blue: 42/255)) // #0F172A
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(accentColor)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        }
        .padding(20)
        .background(warmBg)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(accentColor.opacity(0.4), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.5), radius: 18, y: 8)
    }
    
    // MARK: - Helpers 1:1 Android
    
    private func resolveEffectiveDuration(for route: Route) -> Int64 {
        if viewModel.elapsedSeconds > 0 { return viewModel.elapsedSeconds }
        if route.durationSeconds > 0 { return route.durationSeconds }
        
        let pts = viewModel.recordedPoints
        guard let firstPt = pts.first,
              let lastPt = pts.first(where: { $0.isRouteEnd }) ?? pts.last,
              firstPt.id != lastPt.id else {
            return 0
        }
        let formats = ["dd/MM/yyyy HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd HH:mm:ss"]
        for fmt in formats {
            let df = DateFormatter()
            df.locale = Locale(identifier: "en_US_POSIX")
            df.dateFormat = fmt
            if let d1 = df.date(from: firstPt.timestamp),
               let d2 = df.date(from: lastPt.timestamp),
               d2 > d1 {
                return Int64(d2.timeIntervalSince(d1))
            }
        }
        return 0
    }
    
    private func resolveDifficulty(distanceKm: Double, elevationGain: Double) -> (category: String, color: Color, phrase: String) {
        if distanceKm < 5.0 && elevationGain < 150.0 {
            return (
                "Principiante",
                Color(red: 21/255, green: 128/255, blue: 61/255), // #15803D
                "Es fácil, hasta mi abuelita en pantuflas lo haría."
            )
        } else if distanceKm < 12.0 && elevationGain < 500.0 {
            return (
                "Intermedio",
                Color(red: 217/255, green: 119/255, blue: 6/255), // #D97706
                "Tienes que entrenar, mi chavo, para poder terminarla."
            )
        } else if distanceKm < 25.0 && elevationGain < 1200.0 {
            return (
                "Experto",
                Color(red: 190/255, green: 18/255, blue: 60/255), // #BE123C
                "Lleva suficiente agua y despídete de tus rodillas por una semana."
            )
        } else {
            return (
                "Nivel Dios",
                Color(red: 109/255, green: 40/255, blue: 217/255), // #6D28D9
                "Sólo para cabritos monteses y semidioses del Olimpo. ¡Buena suerte!"
            )
        }
    }
}
