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
    @State private var cameraPosition: MapCameraPosition = .userLocation(fallback: .automatic)
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        ZStack(alignment: .topTrailing) {
            Map(position: $cameraPosition) {
                // Posición del usuario con pulso nativo
                UserAnnotation()
                
                // Traza en vivo del sendero (Polilínea con brillo)
                if viewModel.recordedCoordinates.count >= 2 {
                    MapPolyline(coordinates: viewModel.recordedCoordinates)
                        .stroke(AppTheme.accent, style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
                }
                
                // Marcadores de los puntos de control (Waypoints con foto)
                ForEach(viewModel.recordedPoints) { point in
                    let isCurrentTourPoint = viewModel.isVirtualTourActive &&
                        (viewModel.virtualTourIndex < viewModel.tourPoints.count) &&
                        (viewModel.tourPoints[viewModel.virtualTourIndex].id == point.id)
                    let isTargetGuidePoint = viewModel.isGuidingActive && (viewModel.targetGuideWaypoint?.id == point.id)
                    let isHighlighted = isCurrentTourPoint || isTargetGuidePoint
                    
                    Annotation(
                        point.descriptionText,
                        coordinate: CLLocationCoordinate2D(latitude: point.latitude, longitude: point.longitude)
                    ) {
                        WaypointMarkerView(point: point, isHighlighted: isHighlighted)
                            .onTapGesture {
                                viewModel.selectedPointForDetail = point
                            }
                    }
                }
                
                // Balizas de Compañeros Senderistas detectadas vía BLE
                ForEach(viewModel.nearbyPeersList) { peer in
                    Annotation(
                        "Compañero: \(peer.beaconId)",
                        coordinate: CLLocationCoordinate2D(latitude: peer.latitude, longitude: peer.longitude)
                    ) {
                        PeerMarkerView(peer: peer)
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
                
                // Centrar en el Usuario
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
        .onChange(of: viewModel.virtualTourIndex) { _, newIndex in
            focusOnTourPoint(index: newIndex)
        }
        .onChange(of: viewModel.activeRoute?.id) { _, newId in
            if newId != nil {
                fitOrCenterRoute()
            }
        }
        .onChange(of: viewModel.isGuidingActive) { _, isGuiding in
            if isGuiding {
                fitOrCenterRoute()
            }
        }
        .onChange(of: viewModel.recordedCoordinates.count) { _, count in
            if count >= 2 {
                fitOrCenterRoute()
            }
        }
        .onChange(of: viewModel.recordedPoints.count) { _, count in
            if count >= 1 {
                fitOrCenterRoute()
            }
        }
        .onAppear {
            if viewModel.isVirtualTourActive {
                focusOnTourPoint(index: viewModel.virtualTourIndex)
            } else if viewModel.activeRoute != nil || viewModel.recordedCoordinates.count >= 2 || !viewModel.recordedPoints.isEmpty {
                fitOrCenterRoute()
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
    
    private func focusOnTourPoint(index: Int) {
        let pts = viewModel.tourPoints
        guard index < pts.count else { return }
        let pt = pts[index]
        viewModel.isFollowingUser = false
        withAnimation(.easeInOut(duration: 0.8)) {
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
    public var isHighlighted: Bool = false
    
    public var body: some View {
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
                        WaypointThumbnailView(photoPath: path, size: 36, borderColor: markerBackgroundColor)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(markerBackgroundColor, lineWidth: 2.5))
                            .shadow(color: Color.black.opacity(0.4), radius: 4, y: 2)
                        
                        Text("\(point.id)")
                            .font(.system(size: 8, weight: .heavy))
                            .foregroundStyle(.white)
                            .padding(.horizontal, 3)
                            .padding(.vertical, 1)
                            .background(markerBackgroundColor)
                            .clipShape(Capsule())
                            .offset(x: 2, y: 2)
                    }
                } else {
                    Circle()
                        .fill(point.id == 1 ? Color.black : markerBackgroundColor)
                        .frame(width: 32, height: 32)
                        .overlay(
                            Circle().stroke(point.id == 1 ? Color(red: 56/255, green: 189/255, blue: 248/255) : Color.white, lineWidth: 2)
                        )
                        .shadow(color: Color.black.opacity(0.4), radius: 4, x: 0, y: 2)
                    
                    if point.isRouteEnd {
                        Image(systemName: "flag.checkered")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                    } else if point.id == 1 {
                        Image(systemName: "play.fill")
                            .font(.system(size: 12, weight: .black))
                            .foregroundStyle(Color(red: 56/255, green: 189/255, blue: 248/255))
                    } else {
                        Text("\(point.id)")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(isHighlighted ? Color(red: 15/255, green: 23/255, blue: 42/255) : .white)
                    }
                }
            }
            
            // Etiqueta flotante con altitud
            Text(RouteStatsCalculator.formatElevation(point.altitude))
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(.white)
                .padding(.horizontal, 4)
                .padding(.vertical, 1)
                .background(AppTheme.darkBackground.opacity(0.85))
                .clipShape(Capsule())
        }
    }
    
    private var markerBackgroundColor: Color {
        if isHighlighted {
            return Color(red: 56/255, green: 189/255, blue: 248/255) // Azul Celeste
        } else if point.isRouteEnd {
            return AppTheme.alert
        } else if point.id == 1 {
            return AppTheme.primaryLight
        } else {
            return AppTheme.warningYellow
        }
    }
}

// MARK: - Vista de Baliza de Compañero (Peer BLE)

public struct PeerMarkerView: View {
    public let peer: PeerBeaconData
    @State private var isPulsing: Bool = false
    
    public var body: some View {
        VStack(spacing: 2) {
            ZStack {
                // Anillo de pulso de radio onda BLE
                Circle()
                    .stroke(AppTheme.accentCyan.opacity(0.5), lineWidth: 2)
                    .frame(width: isPulsing ? 46 : 28, height: isPulsing ? 46 : 28)
                    .scaleEffect(isPulsing ? 1.25 : 0.9)
                    .opacity(isPulsing ? 0 : 0.8)
                    .animation(.easeInOut(duration: 1.5).repeatForever(autoreverses: false), value: isPulsing)
                
                Circle()
                    .fill(AppTheme.accentCyan)
                    .frame(width: 30, height: 30)
                    .overlay(
                        Circle().stroke(Color.white, lineWidth: 2)
                    )
                    .shadow(color: AppTheme.accentCyan.opacity(0.6), radius: 5, x: 0, y: 2)
                
                Image(systemName: "figure.walk")
                    .font(.system(size: 14, weight: .bold))
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
                .background(AppTheme.cardBackground.opacity(0.9))
                .overlay(
                    Capsule().stroke(AppTheme.accentCyan.opacity(0.6), lineWidth: 1)
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


