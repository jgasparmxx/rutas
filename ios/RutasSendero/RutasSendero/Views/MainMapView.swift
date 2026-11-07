//
//  MainMapView.swift
//  RutasSendero
//
//  Componente de mapa nativo en SwiftUI con MapKit (iOS 17+).
//  Renderiza la posición del usuario, la polilínea del trayecto en vivo
//  y los marcadores de los puntos de control registrados.
//

import SwiftUI
import MapKit

public struct MainMapView: View {
    @Bindable var viewModel: MainViewModel
    @State private var cameraPosition: MapCameraPosition = .automatic
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        let orderedPts = viewModel.tourPoints
        
        ZStack(alignment: .topTrailing) {
            Map(position: $cameraPosition) {
                // Posición del usuario con pulso nativo
                UserAnnotation()
                
                // Traza en vivo del sendero (Polilínea con brillo)
                if viewModel.recordedCoordinates.count >= 2 {
                    MapPolyline(coordinates: viewModel.recordedCoordinates)
                        .stroke(AppTheme.accent, style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
                }
                
                // Marcadores de los puntos de control (Waypoints con foto y desnivel al siguiente punto)
                ForEach(Array(orderedPts.enumerated()), id: \.element.id) { idx, point in
                    let isCurrentTourPoint = viewModel.isVirtualTourActive && (viewModel.virtualTourIndex == idx)
                    let isTargetGuidePoint = viewModel.isGuidingActive && (viewModel.targetGuideWaypoint?.id == point.id)
                    let isHighlighted = isCurrentTourPoint || isTargetGuidePoint
                    let nextStepLabel: String? = {
                        guard idx < orderedPts.count - 1 else { return "Fin" }
                        let nextPt = orderedPts[idx + 1]
                        let d = CLLocation(latitude: point.latitude, longitude: point.longitude)
                            .distance(from: CLLocation(latitude: nextPt.latitude, longitude: nextPt.longitude))
                        let dStr = RouteStatsCalculator.formatDistanceShort(d)
                        if let delta = RouteStatsCalculator.calculateElevationDeltaMeters(fromAlt: point.altitude, toAlt: nextPt.altitude) {
                            if delta > 0 { return "\(dStr) · ↑\(delta)m" }
                            if delta < 0 { return "\(dStr) · ↓\(abs(delta))m" }
                        }
                        return dStr
                    }()
                    
                    Annotation(
                        point.descriptionText,
                        coordinate: CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude)
                    ) {
                        WaypointMarkerView(
                            point: point,
                            displayNumber: idx + 1,
                            isHighlighted: isHighlighted,
                            nextStepBadge: nextStepLabel
                        )
                        .onTapGesture {
                            if viewModel.isVirtualTourActive {
                                TextToSpeechService.shared.stop()
                                viewModel.virtualTourIndex = idx
                                viewModel.stepVirtualTour()
                            } else {
                                viewModel.selectedPointForDetail = point
                            }
                        }
                    }
                }
                
                // Balizas de Compañeros Senderistas detectadas vía BLE con ubicación válida
                ForEach(viewModel.nearbyPeersList.filter { abs($0.latitude) > 0.001 || abs($0.longitude) > 0.001 }) { peer in
                    let profileName = viewModel.peerSenderistaProfiles[peer.beaconId.lowercased()]?.name
                    Annotation(
                        profileName ?? "Compañero: \(peer.beaconId.prefix(6).uppercased())",
                        coordinate: CLLocationCoordinate2D(latitude: peer.latitude, longitude: peer.longitude)
                    ) {
                        PeerMarkerView(peer: peer, hikerName: profileName)
                            .onTapGesture {
                                viewModel.isShowingNearbyPeersSheet = true
                            }
                    }
                }
            }
            .mapStyle(currentMapKitStyle)
            .mapControls {
                MapScaleView()
            }
            .ignoresSafeArea()
            
            // Botonera Flotante de Controles de Mapa
            VStack(spacing: 12) {
                // Selector de Estilo de Mapa
                Menu {
                    ForEach(MapStyleMode.allCases) { style in
                        Button {
                            viewModel.mapStyle = style
                        } label: {
                            HStack {
                                Text(style.rawValue)
                                if viewModel.mapStyle == style {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                } label: {
                    MapControlButton(systemImage: mapStyleIcon)
                }
                .buttonStyle(.plain)
                
                // Ir al Punto de Inicio de la Ruta
                if viewModel.activeRoute != nil || !viewModel.recordedPoints.isEmpty {
                    Button {
                        focusOnStartingPoint(animated: true)
                    } label: {
                        MapControlButton(systemImage: "flag.fill", tint: Color(red: 56/255, green: 189/255, blue: 248/255))
                    }
                    .buttonStyle(.plain)
                }
                
                // Centrar en el Usuario (bajo demanda explícita)
                Button {
                    withAnimation(.easeInOut(duration: 0.5)) {
                        cameraPosition = .userLocation(fallback: .automatic)
                        viewModel.isFollowingUser = true
                    }
                } label: {
                    MapControlButton(
                        systemImage: "location.fill",
                        tint: viewModel.isFollowingUser ? AppTheme.accent : AppTheme.textPrimary
                    )
                }
                .buttonStyle(.plain)
                
                // Ajustar al recorrido grabado completo o a los hitos de la ruta
                if viewModel.recordedCoordinates.count >= 2 || !viewModel.recordedPoints.isEmpty || viewModel.activeRoute != nil {
                    Button {
                        fitOrCenterRoute()
                    } label: {
                        MapControlButton(systemImage: "arrow.up.left.and.arrow.down.right")
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.trailing, 16)
            .padding(.top, 120) // Debajo de la HeaderBar con espacio generoso
        }
        .onChange(of: viewModel.isVirtualTourActive) { _, isActive in
            if isActive {
                // Antes de avanzar en la simulación, colocar el mapa en el punto donde inicia
                focusOnTourPoint(index: viewModel.virtualTourIndex)
            }
        }
        .onChange(of: viewModel.virtualTourIndex) { _, newIndex in
            if viewModel.isVirtualTourActive {
                focusOnTourPoint(index: newIndex)
            }
        }
        .onChange(of: viewModel.isVisorReversed) { _, _ in
            focusOnStartingPoint(animated: true)
        }
        .onChange(of: viewModel.activeRoute?.id) { _, newId in
            if newId != nil {
                focusOnStartingPoint(animated: true)
            }
        }
        .onChange(of: viewModel.isGuidingActive) { _, isGuiding in
            if isGuiding {
                focusOnStartingPoint(animated: true)
            }
        }
        .onChange(of: viewModel.recordedPoints.count) { _, count in
            if count >= 1 && !viewModel.isFollowingUser {
                focusOnStartingPoint(animated: false)
            }
        }
        .onAppear {
            viewModel.isFollowingUser = false
            if viewModel.isVirtualTourActive {
                focusOnTourPoint(index: viewModel.virtualTourIndex)
            } else {
                focusOnStartingPoint(animated: false)
            }
            // Re-aplicar tras el primer ciclo de render de MapKit para impedir que UserAnnotation mueva la cámara al usuario
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(180))
                guard !viewModel.isFollowingUser else { return }
                if viewModel.isVirtualTourActive {
                    focusOnTourPoint(index: viewModel.virtualTourIndex)
                } else {
                    focusOnStartingPoint(animated: false)
                }
            }
        }
    }
    
    // MARK: - Helpers de Estilo
    
    private var currentMapKitStyle: MapStyle {
        switch viewModel.mapStyle {
        case .standard:
            return .standard(elevation: .realistic)
        case .satellite:
            return .imagery(elevation: .realistic)
        case .hybrid:
            return .hybrid(elevation: .realistic)
        }
    }
    
    private var mapStyleIcon: String {
        switch viewModel.mapStyle {
        case .standard: return "map.fill"
        case .satellite: return "globe.americas.fill"
        case .hybrid: return "square.2.layers.3d.top.filled"
        }
    }
    
    /// Coloca el mapa directamente en el punto donde inicia la ruta o la simulación (respetando Ida vs Retorno),
    /// en lugar de quedarse en la ubicación actual del usuario.
    private func focusOnStartingPoint(animated: Bool) {
        viewModel.isFollowingUser = false
        let startCoord: CLLocationCoordinate2D? = {
            if let firstPt = viewModel.tourPoints.first {
                return CLLocationCoordinate2D(latitude: firstPt.latitude, longitude: firstPt.longitude)
            }
            if let target = viewModel.targetGuideWaypoint {
                return CLLocationCoordinate2D(latitude: target.latitude, longitude: target.longitude)
            }
            if let firstTrace = viewModel.recordedCoordinates.first {
                return firstTrace
            }
            if let route = viewModel.activeRoute {
                return CLLocationCoordinate2D(latitude: route.originLatitude, longitude: route.originLongitude)
            }
            return nil
        }()
        
        guard let center = startCoord else { return }
        let region = MKCoordinateRegion(
            center: center,
            span: MKCoordinateSpan(latitudeDelta: 0.0042, longitudeDelta: 0.0042)
        )
        if animated {
            withAnimation(.easeInOut(duration: 0.6)) {
                cameraPosition = .region(region)
            }
        } else {
            cameraPosition = .region(region)
        }
    }
    
    private func focusOnTourPoint(index: Int) {
        let pts = viewModel.tourPoints
        guard index < pts.count else { return }
        let pt = pts[index]
        viewModel.isFollowingUser = false
        withAnimation(.easeInOut(duration: 0.7)) {
            cameraPosition = .region(
                MKCoordinateRegion(
                    center: CLLocationCoordinate2D(latitude: pt.latitude, longitude: pt.longitude),
                    span: MKCoordinateSpan(latitudeDelta: 0.0035, longitudeDelta: 0.0035)
                )
            )
        }
    }
    
    public func fitOrCenterRoute() {
        viewModel.isFollowingUser = false
        
        if viewModel.recordedCoordinates.count >= 2 {
            var minLat = viewModel.recordedCoordinates[0].latitude
            var maxLat = viewModel.recordedCoordinates[0].latitude
            var minLon = viewModel.recordedCoordinates[0].longitude
            var maxLon = viewModel.recordedCoordinates[0].longitude
            
            for coord in viewModel.recordedCoordinates {
                minLat = min(minLat, coord.latitude)
                maxLat = max(maxLat, coord.latitude)
                minLon = min(minLon, coord.longitude)
                maxLon = max(maxLon, coord.longitude)
            }
            
            let center = CLLocationCoordinate2D(
                latitude: (minLat + maxLat) / 2,
                longitude: (minLon + maxLon) / 2
            )
            let span = MKCoordinateSpan(
                latitudeDelta: max(0.006, (maxLat - minLat) * 1.4),
                longitudeDelta: max(0.006, (maxLon - minLon) * 1.4)
            )
            
            withAnimation(.easeInOut(duration: 0.6)) {
                cameraPosition = .region(MKCoordinateRegion(center: center, span: span))
            }
        } else if !viewModel.recordedPoints.isEmpty {
            var minLat = viewModel.recordedPoints[0].latitude
            var maxLat = viewModel.recordedPoints[0].latitude
            var minLon = viewModel.recordedPoints[0].longitude
            var maxLon = viewModel.recordedPoints[0].longitude
            
            for pt in viewModel.recordedPoints {
                minLat = min(minLat, pt.latitude)
                maxLat = max(maxLat, pt.latitude)
                minLon = min(minLon, pt.longitude)
                maxLon = max(maxLon, pt.longitude)
            }
            
            let center = CLLocationCoordinate2D(
                latitude: (minLat + maxLat) / 2,
                longitude: (minLon + maxLon) / 2
            )
            let span = MKCoordinateSpan(
                latitudeDelta: max(0.008, (maxLat - minLat) * 1.5),
                longitudeDelta: max(0.008, (maxLon - minLon) * 1.5)
            )
            
            withAnimation(.easeInOut(duration: 0.6)) {
                cameraPosition = .region(MKCoordinateRegion(center: center, span: span))
            }
        } else if let route = viewModel.activeRoute {
            let center = CLLocationCoordinate2D(
                latitude: route.originLatitude,
                longitude: route.originLongitude
            )
            let span = MKCoordinateSpan(latitudeDelta: 0.015, longitudeDelta: 0.015)
            withAnimation(.easeInOut(duration: 0.6)) {
                cameraPosition = .region(MKCoordinateRegion(center: center, span: span))
            }
        }
    }
}

// MARK: - Vista Personalizada de Marcador (Waypoint)

public struct WaypointMarkerView: View {
    public let point: RoutePoint
    public var displayNumber: Int? = nil
    public var isHighlighted: Bool = false
    public var nextStepBadge: String? = nil
    
    public var body: some View {
        let num = displayNumber ?? point.id
        VStack(spacing: 2) {
            ZStack {
                if isHighlighted {
                    Circle()
                        .stroke(Color(red: 56/255, green: 189/255, blue: 248/255), lineWidth: 3.5)
                        .frame(width: 44, height: 44)
                        .shadow(color: Color(red: 56/255, green: 189/255, blue: 248/255).opacity(0.8), radius: 6)
                }
                
                if let path = point.photoUrl, !path.isEmpty {
                    // Marcador con foto real del punto
                    ZStack(alignment: .bottomTrailing) {
                        WaypointThumbnailView(photoPath: path, size: 36, borderColor: markerBackgroundColor(num: num))
                            .clipShape(Circle())
                            .overlay(Circle().stroke(markerBackgroundColor(num: num), lineWidth: 2.5))
                            .shadow(color: Color.black.opacity(0.4), radius: 4, y: 2)
                        
                        Text("\(num)")
                            .font(.system(size: 8, weight: .heavy))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 3)
                            .padding(.vertical, 1)
                            .background(markerBackgroundColor(num: num))
                            .clipShape(Capsule())
                            .offset(x: 2, y: 2)
                    }
                } else {
                    Circle()
                        .fill(num == 1 ? Color.black : markerBackgroundColor(num: num))
                        .frame(width: 32, height: 32)
                        .overlay(
                            Circle().stroke(num == 1 ? Color(red: 56/255, green: 189/255, blue: 248/255) : Color.white, lineWidth: 2)
                        )
                        .shadow(color: Color.black.opacity(0.4), radius: 4, x: 0, y: 2)
                    
                    if point.isRouteEnd && num != 1 {
                        Image(systemName: "flag.checkered")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                    } else if num == 1 {
                        Image(systemName: "play.fill")
                            .font(.system(size: 12, weight: .black))
                            .foregroundStyle(Color(red: 56/255, green: 189/255, blue: 248/255))
                    } else {
                        Text("\(num)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(isHighlighted ? Color(red: 15/255, green: 23/255, blue: 42/255) : .white)
                    }
                }
            }
            
            // Etiqueta flotante con distancia y subida/bajada al siguiente punto (sin altitud absoluta)
            if let badge = nextStepBadge, !badge.isEmpty {
                Text(badge)
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(isHighlighted ? Color(red: 94/255, green: 234/255, blue: 212/255) : .white)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1.5)
                    .background(AppTheme.darkBackground.opacity(0.88))
                    .clipShape(Capsule())
            }
        }
    }
    
    private func markerBackgroundColor(num: Int) -> Color {
        if isHighlighted {
            return Color(red: 56/255, green: 189/255, blue: 248/255) // Azul Celeste
        } else if point.isRouteEnd && num != 1 {
            return AppTheme.alert
        } else if num == 1 {
            return AppTheme.primaryLight
        } else {
            return AppTheme.warningYellow
        }
    }
}

// MARK: - Vista de Baliza de Compañero (Peer BLE 1:1 Android TrackingMapView)

public struct PeerMarkerView: View {
    public let peer: PeerBeaconData
    public var hikerName: String? = nil
    @State private var isPulsing: Bool = false
    
    private var initials: String {
        guard let name = hikerName?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty else {
            return String(peer.beaconId.prefix(2)).uppercased()
        }
        let parts = name.split(whereSeparator: { $0.isWhitespace })
        let chars = parts.compactMap { $0.first.map { String($0).uppercased() } }
        let joined = chars.prefix(3).joined()
        return joined.isEmpty ? "?" : joined
    }
    
    public var body: some View {
        let pinColor = peer.isEmergency ? AppTheme.alert : Color(red: 249/255, green: 115/255, blue: 22/255)
        
        VStack(spacing: 2) {
            ZStack {
                // Anillo de pulso de radio onda BLE
                Circle()
                    .stroke(pinColor.opacity(0.6), lineWidth: 2)
                    .frame(width: isPulsing ? 48 : 30, height: isPulsing ? 48 : 30)
                    .scaleEffect(isPulsing ? 1.25 : 0.9)
                    .opacity(isPulsing ? 0 : 0.85)
                    .animation(.easeInOut(duration: peer.isEmergency ? 0.9 : 1.5).repeatForever(autoreverses: false), value: isPulsing)
                
                Circle()
                    .fill(pinColor)
                    .frame(width: 32, height: 32)
                    .overlay(
                        Circle().stroke(peer.isEmergency ? Color.yellow : Color.white, lineWidth: 2)
                    )
                    .shadow(color: pinColor.opacity(0.65), radius: 5, x: 0, y: 2)
                
                Text(initials)
                    .font(.system(size: 11, weight: .heavy, design: .rounded))
                    .foregroundStyle(.white)
            }
            .onAppear {
                isPulsing = true
            }
            
            // Distancia estimada calculada del RSSI
            Text(String(format: "%.0fm", peer.estimatedDistanceMeters))
                .font(.system(size: 9, weight: .heavy, design: .rounded))
                .foregroundStyle(.white)
                .padding(.horizontal, 5)
                .padding(.vertical, 1.5)
                .background(AppTheme.cardBackground.opacity(0.92))
                .overlay(
                    Capsule().stroke(pinColor.opacity(0.75), lineWidth: 1)
                )
                .clipShape(Capsule())
        }
    }
}

// MARK: - Botón Flotante de Control de Mapa

public struct MapControlButton: View {
    public let systemImage: String
    public var tint: Color = AppTheme.textPrimary
    
    public var body: some View {
        ZStack {
            Circle()
                .fill(.ultraThinMaterial)
                .background(AppTheme.cardBackground.opacity(0.8))
                .frame(width: 44, height: 44)
                .clipShape(Circle())
                .overlay(
                    Circle().stroke(AppTheme.cardBorder, lineWidth: 1)
                )
                .shadow(color: Color.black.opacity(0.25), radius: 5, x: 0, y: 2)
            
            Image(systemName: systemImage)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(tint)
        }
    }
}


