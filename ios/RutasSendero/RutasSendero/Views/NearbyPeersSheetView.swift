//
//  NearbyPeersSheetView.swift
//  RutasSendero
//
//  Rastreo y Mapa de Senderistas Cercanos por Balizas BLE (1:1 Android ListadoSenderistasDialog + TrackingMapView).
//

import SwiftUI
import MapKit
import CoreLocation

public struct NearbyPeersSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    /// 0 = Lista de senderistas, 1 = Mapa de los beacons cercanos (por omisión Mapa cuando está en modo Guía, 1:1 Android)
    @State private var selectedTab: Int
    @State private var nowTick: Date = Date()
    @State private var selectedPeerForEmergency: PeerBeaconData? = nil
    @State private var mapCameraPosition: MapCameraPosition = .automatic
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
        _selectedTab = State(initialValue: (viewModel.isGuidingActive || viewModel.activeRole == .guia) ? 1 : 0)
    }
    
    private var ownBeaconDisplay: String {
        let id = (viewModel.currentHikerProfile?.beaconId ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        return id.isEmpty ? "Sin ID" : id.uppercased()
    }
    
    private func isPeerConnected(_ peer: PeerBeaconData) -> Bool {
        nowTick.timeIntervalSince(peer.lastDetectedTimestamp) <= 25.0
    }
    
    public var body: some View {
        let allPeers = viewModel.nearbyPeersList
        let activePeers = allPeers.filter { isPeerConnected($0) }
        
        VStack(spacing: 14) {
            // 1. Cabecera (1:1 Android ListadoSenderistasDialog)
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Rastreo de senderista")
                        .font(.system(size: 20, weight: .heavy))
                        .foregroundStyle(AppTheme.textPrimary)
                    
                    HStack(spacing: 6) {
                        Circle()
                            .fill(viewModel.bleScanner.isScanning ? Color(red: 16/255, green: 185/255, blue: 129/255) : AppTheme.alert)
                            .frame(width: 8, height: 8)
                        
                        if viewModel.bleScanner.isScanning {
                            (Text("(\(ownBeaconDisplay)) ").fontWeight(.bold) + Text("..Buscando senderistas..."))
                                .font(.system(size: 12))
                                .foregroundStyle(AppTheme.textSecondary)
                                .lineLimit(1)
                        } else {
                            Button {
                                viewModel.startBleScanning()
                                viewModel.refreshBleScanning()
                            } label: {
                                Text(viewModel.bleScanner.lastScannerError ?? "Escaneo inactivo · Toca para reintentar")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundStyle(AppTheme.alert)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                
                Spacer()
                
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 26))
                        .foregroundStyle(AppTheme.textSecondary)
                }
                .buttonStyle(.plain)
            }
            
            // 2. Selector de Pestañas: Lista de senderistas | Mapa (1:1 Android)
            HStack(spacing: 4) {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        selectedTab = 0
                    }
                } label: {
                    Text("Lista de senderistas")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(selectedTab == 0 ? .white : AppTheme.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(selectedTab == 0 ? AppTheme.accent : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
                
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        selectedTab = 1
                    }
                    fitMapToPeersAndUser()
                } label: {
                    Text("Mapa")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(selectedTab == 1 ? .white : AppTheme.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(selectedTab == 1 ? AppTheme.accent : Color.clear)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
            }
            .padding(4)
            .background(AppTheme.cardBackground.opacity(0.6))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            
            // 3. Selector de frecuencia de actualización (1.5s, 3s, 5s - 1:1 Android)
            HStack {
                Text("Actualizar mapa y lista:")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(AppTheme.textSecondary)
                
                Spacer()
                
                HStack(spacing: 6) {
                    ForEach([(1.5, "1.5s"), (3.0, "3s"), (5.0, "5s")], id: \.0) { interval, label in
                        let isSelected = abs(viewModel.trackingRefreshIntervalSeconds - interval) < 0.1
                        Button {
                            viewModel.trackingRefreshIntervalSeconds = interval
                        } label: {
                            Text(label)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(isSelected ? .white : AppTheme.textSecondary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(isSelected ? AppTheme.accent : AppTheme.cardBackground)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(isSelected ? Color.white.opacity(0.35) : AppTheme.cardBorder, lineWidth: 1)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            
            // 4. Contenido Principal: Pestaña Mapa vs Pestaña Lista
            if selectedTab == 1 {
                nearbyBeaconsMapView(activePeers: activePeers)
            } else {
                nearbyBeaconsListView(allPeers: allPeers, activeCount: activePeers.count)
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(AppTheme.darkBackground.ignoresSafeArea())
        .onAppear {
            viewModel.refreshBleScanning()
            nowTick = Date()
            fitMapToPeersAndUser()
        }
        .onChange(of: viewModel.nearbyPeers.count) { _, _ in
            nowTick = Date()
            if selectedTab == 1 {
                fitMapToPeersAndUser()
            }
        }
        .onChange(of: activePeers.count) { _, _ in
            if selectedTab == 1 {
                fitMapToPeersAndUser()
            }
        }
        .task(id: viewModel.trackingRefreshIntervalSeconds) {
            let ns = UInt64(max(1.0, viewModel.trackingRefreshIntervalSeconds) * 1_000_000_000)
            var tickCounter = 0
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: ns)
                guard !Task.isCancelled else { break }
                nowTick = Date()
                tickCounter += 1
                if tickCounter % 4 == 0 {
                    viewModel.bleScanner.ensureScanHealthy()
                }
            }
        }
        .sheet(item: $selectedPeerForEmergency) { peer in
            PeerEmergencyDetailSheet(
                peer: peer,
                profile: viewModel.peerSenderistaProfiles[peer.beaconId.lowercased()],
                onAcknowledgeEmergency: {
                    viewModel.acknowledgePeerEmergency(beaconId: peer.beaconId)
                }
            )
        }
    }
    
    // MARK: - Pestaña Mapa de Beacons Cercanos (1:1 Android TrackingMapView)
    
    @ViewBuilder
    private func nearbyBeaconsMapView(activePeers: [PeerBeaconData]) -> some View {
        ZStack(alignment: .topTrailing) {
            Map(position: $mapCameraPosition) {
                UserAnnotation()
                
                ForEach(activePeers) { peer in
                    let coord = displayCoordinate(for: peer)
                    let profile = viewModel.peerSenderistaProfiles[peer.beaconId.lowercased()]
                    let hasExactGps = abs(peer.latitude) > 0.001 || abs(peer.longitude) > 0.001
                    let title = profile?.name ?? "Senderista \(peer.beaconId.prefix(6).uppercased())"
                    
                    Annotation(
                        hasExactGps ? title : "\(title) (BLE)",
                        coordinate: coord
                    ) {
                        PeerMarkerView(peer: peer, hikerName: profile?.name)
                            .onTapGesture {
                                selectedPeerForEmergency = peer
                            }
                    }
                }
            }
            .mapStyle(.standard(elevation: .realistic))
            .mapControls {
                MapScaleView()
            }
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(AppTheme.accent.opacity(0.3), lineWidth: 1)
            )
            
            VStack(spacing: 10) {
                Button {
                    viewModel.refreshBleScanning()
                    fitMapToPeersAndUser()
                } label: {
                    MapControlButton(systemImage: "dot.radiowaves.left.and.right", tint: AppTheme.accentCyan)
                }
                .buttonStyle(.plain)
                
                Button {
                    fitMapToPeersAndUser()
                } label: {
                    MapControlButton(systemImage: "arrow.up.left.and.arrow.down.right")
                }
                .buttonStyle(.plain)
            }
            .padding(12)
            
            if activePeers.isEmpty {
                VStack {
                    Spacer()
                    HStack(spacing: 8) {
                        ProgressView()
                            .tint(AppTheme.accentCyan)
                            .scaleEffect(0.85)
                        Text("Escaneando beacons cercanos (iOS y Android)...")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.black.opacity(0.78))
                    .clipShape(Capsule())
                    .padding(.bottom, 16)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    // MARK: - Pestaña Lista de Senderistas
    
    @ViewBuilder
    private func nearbyBeaconsListView(allPeers: [PeerBeaconData], activeCount: Int) -> some View {
        VStack(spacing: 10) {
            if allPeers.isEmpty {
                Spacer()
                VStack(spacing: 12) {
                    Image(systemName: "antenna.radiowaves.left.and.right")
                        .font(.system(size: 44))
                        .foregroundStyle(AppTheme.accentCyan.opacity(0.5))
                    
                    Text("No hay participantes detectados en el rango de Bluetooth.")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(AppTheme.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                    
                    #if targetEnvironment(simulator)
                    Button("Simular Compañero Cercano") {
                        viewModel.simulatePeerDetected(beaconId: "4b3c2d1e0f9a", distanceMeters: 12.4)
                    }
                    .font(.caption).bold()
                    .foregroundStyle(AppTheme.accentCyan)
                    .padding(.top, 4)
                    #endif
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(allPeers) { peer in
                            let cleanId = peer.beaconId.lowercased()
                            let profile = viewModel.peerSenderistaProfiles[cleanId]
                            let connected = isPeerConnected(peer)
                            let isEmergencyActive = peer.isEmergency && !viewModel.acknowledgedEmergencies.contains(cleanId)
                            
                            HStack(spacing: 12) {
                                Circle()
                                    .fill((isEmergencyActive ? AppTheme.alert : AppTheme.accentCyan).opacity(0.2))
                                    .frame(width: 44, height: 44)
                                    .overlay(
                                        Image(systemName: isEmergencyActive ? "exclamationmark.triangle.fill" : "figure.walk")
                                            .font(.system(size: 18, weight: .bold))
                                            .foregroundStyle(isEmergencyActive ? AppTheme.alert : (connected ? AppTheme.accentCyan : AppTheme.textSecondary))
                                    )
                                
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 6) {
                                        Text(profile?.name ?? "Senderista \(peer.beaconId.prefix(6).uppercased())")
                                            .font(.system(size: 15, weight: .bold))
                                            .foregroundStyle(AppTheme.textPrimary)
                                            .lineLimit(1)
                                        
                                        if let agru = profile?.idAgrupacion, !agru.isEmpty, agru != "@&" {
                                            Text(agru)
                                                .font(.system(size: 9, weight: .heavy))
                                                .foregroundStyle(AppTheme.accentCyan)
                                                .padding(.horizontal, 6)
                                                .padding(.vertical, 2)
                                                .background(AppTheme.accentCyan.opacity(0.15))
                                                .clipShape(Capsule())
                                        }
                                    }
                                    
                                    HStack(spacing: 6) {
                                        Text("ID: \(peer.beaconId.uppercased())")
                                            .font(.system(size: 11, design: .monospaced))
                                            .foregroundStyle(AppTheme.textSecondary)
                                        
                                        if let blood = profile?.bloodType, !blood.isEmpty {
                                            Text("· 🩸 \(blood)")
                                                .font(.system(size: 11, weight: .semibold))
                                                .foregroundStyle(AppTheme.textPrimary)
                                        }
                                    }
                                }
                                
                                Spacer()
                                
                                VStack(alignment: .trailing, spacing: 3) {
                                    if connected {
                                        Text(String(format: "%.1f m", peer.estimatedDistanceMeters))
                                            .font(.system(size: 14, weight: .heavy, design: .rounded))
                                            .foregroundStyle(isEmergencyActive ? AppTheme.alert : AppTheme.accent)
                                        
                                        Text("RSSI: \(peer.rssi) dBm")
                                            .font(.system(size: 10, design: .monospaced))
                                            .foregroundStyle(AppTheme.textSecondary)
                                    } else {
                                        Text("Fuera de alcance")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundStyle(AppTheme.warningYellow)
                                    }
                                }
                            }
                            .padding(12)
                            .background(isEmergencyActive ? AppTheme.alert.opacity(0.18) : AppTheme.cardBackground)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(isEmergencyActive ? AppTheme.alert : AppTheme.cardBorder, lineWidth: isEmergencyActive ? 1.5 : 1)
                            )
                            .contentShape(Rectangle())
                            .onTapGesture(count: 2) {
                                viewModel.acknowledgePeerEmergency(beaconId: peer.beaconId)
                            }
                            .onTapGesture {
                                selectedPeerForEmergency = peer
                            }
                            .onAppear {
                                viewModel.resolvePeerProfile(for: peer.beaconId)
                            }
                        }
                    }
                }
                
                // Barra inferior de conteo 1:1 Android (línea 4429)
                HStack {
                    Spacer()
                    Text("Total senderistas: \(allPeers.count) | Conectados: \(activeCount)")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(Color.yellow)
                    Spacer()
                }
                .padding(.vertical, 8)
                .background(Color.black)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    // MARK: - Geoposicionamiento de Balizas en el Mapa
    
    /// Si el teléfono compañero ya transmitió coordenadas GPS válidas, usa su ubicación exacta;
    /// si encendió el Faro en interiores antes de fijar GPS (0.0, 0.0), proyecta su posición estimada por RSSI
    /// alrededor del usuario para que siempre sea visible en el mapa de beacons cercanos.
    private func displayCoordinate(for peer: PeerBeaconData) -> CLLocationCoordinate2D {
        if abs(peer.latitude) > 0.001 || abs(peer.longitude) > 0.001 {
            return CLLocationCoordinate2D(latitude: peer.latitude, longitude: peer.longitude)
        }
        let base = viewModel.locationTracker.currentLocation?.coordinate
            ?? CLLocationCoordinate2D(latitude: 19.2458, longitude: -103.7251)
        let hashAngle = Double(abs(peer.beaconId.hashValue % 360)) * .pi / 180.0
        let radiusMeters = min(max(peer.estimatedDistanceMeters, 8.0), 80.0)
        let deltaLat = (radiusMeters * cos(hashAngle)) / 111_320.0
        let lonScale = max(0.2, cos(base.latitude * .pi / 180.0))
        let deltaLon = (radiusMeters * sin(hashAngle)) / (111_320.0 * lonScale)
        return CLLocationCoordinate2D(latitude: base.latitude + deltaLat, longitude: base.longitude + deltaLon)
    }
    
    private func fitMapToPeersAndUser() {
        var coords: [CLLocationCoordinate2D] = []
        if let userCoord = viewModel.locationTracker.currentLocation?.coordinate {
            coords.append(userCoord)
        }
        for peer in viewModel.nearbyPeersList where isPeerConnected(peer) {
            coords.append(displayCoordinate(for: peer))
        }
        
        guard !coords.isEmpty else { return }
        if coords.count == 1 {
            withAnimation(.easeInOut(duration: 0.45)) {
                mapCameraPosition = .region(
                    MKCoordinateRegion(
                        center: coords[0],
                        span: MKCoordinateSpan(latitudeDelta: 0.003, longitudeDelta: 0.003)
                    )
                )
            }
            return
        }
        
        var minLat = coords[0].latitude
        var maxLat = coords[0].latitude
        var minLon = coords[0].longitude
        var maxLon = coords[0].longitude
        for c in coords {
            minLat = min(minLat, c.latitude)
            maxLat = max(maxLat, c.latitude)
            minLon = min(minLon, c.longitude)
            maxLon = max(maxLon, c.longitude)
        }
        let center = CLLocationCoordinate2D(
            latitude: (minLat + maxLat) / 2.0,
            longitude: (minLon + maxLon) / 2.0
        )
        let span = MKCoordinateSpan(
            latitudeDelta: max(0.0025, (maxLat - minLat) * 1.6),
            longitudeDelta: max(0.0025, (maxLon - minLon) * 1.6)
        )
        withAnimation(.easeInOut(duration: 0.45)) {
            mapCameraPosition = .region(MKCoordinateRegion(center: center, span: span))
        }
    }
}

// MARK: - Ficha de Emergencia y Datos Médicos del Compañero (1:1 Android AlertDialog EMERGENCIA)

private struct PeerEmergencyDetailSheet: View {
    let peer: PeerBeaconData
    let profile: HikerProfile?
    let onAcknowledgeEmergency: () -> Void
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 12) {
                        Circle()
                            .fill(peer.isEmergency ? AppTheme.alert : AppTheme.accentCyan)
                            .frame(width: 48, height: 48)
                            .overlay(
                                Image(systemName: peer.isEmergency ? "exclamationmark.triangle.fill" : "person.fill")
                                    .font(.system(size: 20, weight: .bold))
                                    .foregroundStyle(.white)
                            )
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(profile?.name ?? "Senderista \(peer.beaconId.uppercased())")
                                .font(.system(size: 18, weight: .heavy))
                                .foregroundStyle(AppTheme.textPrimary)
                            Text("Baliza: \(peer.beaconId.uppercased()) · ~\(String(format: "%.1f m", peer.estimatedDistanceMeters))")
                                .font(.system(size: 12, design: .monospaced))
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                    
                    Divider().background(AppTheme.cardBorder)
                    
                    VStack(alignment: .leading, spacing: 8) {
                        infoRow(label: "Tipo de sangre", value: profile?.bloodType ?? "No especificado")
                        infoRow(label: "Teléfono", value: profile?.phone ?? "No registrado")
                        infoRow(label: "Alergias", value: profile?.alergias ?? "Ninguna registrada")
                        if let enf = profile?.enfermedadCronica1, !enf.isEmpty {
                            infoRow(label: "Condición médica", value: enf)
                        }
                        infoRow(label: "Contacto de emergencia", value: profile?.contactName ?? "No especificado")
                        infoRow(label: "Tel. Emergencia 1", value: profile?.emergencyPhone1 ?? "No registrado")
                        if let tel2 = profile?.emergencyPhone2, !tel2.isEmpty {
                            infoRow(label: "Tel. Emergencia 2", value: tel2)
                        }
                    }
                    .padding(14)
                    .background(AppTheme.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    
                    if peer.isEmergency {
                        Button {
                            onAcknowledgeEmergency()
                            dismiss()
                        } label: {
                            Text("Silenciar Alarma 911")
                                .font(.system(size: 14, weight: .heavy))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(AppTheme.alert)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(16)
            }
            .background(AppTheme.darkBackground.ignoresSafeArea())
            .navigationTitle(peer.isEmergency ? "EMERGENCIA 911" : "Ficha del Senderista")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Cerrar") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
    
    @ViewBuilder
    private func infoRow(label: String, value: String) -> some View {
        HStack(alignment: .top) {
            Text("\(label):")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(AppTheme.textSecondary)
                .frame(width: 145, alignment: .leading)
            Text(value)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(AppTheme.textPrimary)
            Spacer()
        }
    }
}
