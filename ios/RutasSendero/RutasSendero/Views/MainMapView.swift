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
    @Environment(\.scenePhase) private var scenePhase
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
                
                // Línea punteada de guía entre el senderista y el punto objetivo actual (1:1 Android Components.kt:3468-3477)
                if viewModel.isGuidingActive,
                   let userLoc = viewModel.locationTracker.currentLocation,
                   let target = viewModel.targetGuideWaypoint,
                   isHikerNearTrail {
                    let targetCoord = CLLocationCoordinate2D(latitude: target.latitude, longitude: target.longitude)
                    let dist = userLoc.distance(from: CLLocation(latitude: target.latitude, longitude: target.longitude))
                    let linkColor: Color = {
                        if dist <= 30.0 {
                            return Color(red: 16/255, green: 185/255, blue: 129/255) // #10B981 Verde Cercanía
                        } else if dist <= 100.0 {
                            return Color(red: 245/255, green: 158/255, blue: 11/255) // #F59E0B Ámbar Media
                        } else {
                            return Color(red: 239/255, green: 68/255, blue: 68/255)  // #EF4444 Rojo Lejos
                        }
                    }()
                    MapPolyline(coordinates: [userLoc.coordinate, targetCoord])
                        .stroke(linkColor, style: StrokeStyle(lineWidth: 3.5, lineCap: .round, dash: [7, 6]))
                }
                
                // Marcador de alta visibilidad del Senderista en Modo Guía para ubicarlo al instante al encender pantalla
                if viewModel.isGuidingActive, let userLoc = viewModel.locationTracker.currentLocation {
                    Annotation("Senderista", coordinate: userLoc.coordinate) {
                        HikerGuideMarkerView(courseDegrees: viewModel.locationTracker.currentCourse)
                    }
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
                            } else if viewModel.isGuidingActive && viewModel.targetGuideWaypoint?.id == point.id {
                                if let userLoc = viewModel.locationTracker.currentLocation {
                                    let dist = userLoc.distance(from: CLLocation(latitude: point.latitude, longitude: point.longitude))
                                    let limit = Double(viewModel.tapDistanceLimitMeters)
                                    if dist <= limit {
                                        viewModel.advanceGuideToNextWaypoint()
                                    } else {
                                        viewModel.selectedPointForDetail = point
                                        viewModel.saveStatusMessage = "Demasiado lejos para marcar como visto (Distancia: \(Int(dist))m, Límite: \(viewModel.tapDistanceLimitMeters)m)"
                                    }
                                } else {
                                    viewModel.selectedPointForDetail = point
                                    viewModel.saveStatusMessage = "Esperando señal de GPS..."
                                }
                            } else {
                                viewModel.selectedPointForDetail = point
                            }
                        }
                    }
                }
                
                // Balizas de Compañeros Senderistas detectadas vía BLE (activas en los últimos 25s)
                ForEach(viewModel.nearbyPeersList.filter { Date().timeIntervalSince($0.lastDetectedTimestamp) <= 25.0 }) { peer in
                    let profileName = viewModel.peerSenderistaProfiles[peer.beaconId.lowercased()]?.name
                    let coord = peerMapCoordinate(for: peer)
                    Annotation(
                        profileName ?? "Compañero: \(peer.beaconId.prefix(6).uppercased())",
                        coordinate: coord
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
                    viewModel.isFollowingUser = true
                    focusOnHikerPosition(animated: true)
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
            if viewModel.isGuidingActive && isHikerNearTrail {
                focusOnHikerPosition(animated: true)
            } else {
                focusOnStartingPoint(animated: true)
            }
        }
        .onChange(of: viewModel.activeRoute?.id) { _, newId in
            if newId != nil {
                if viewModel.isGuidingActive && isHikerNearTrail {
                    focusOnHikerPosition(animated: true)
                } else {
                    focusOnStartingPoint(animated: true)
                }
            }
        }
        .onChange(of: viewModel.isGuidingActive) { _, isGuiding in
            if isGuiding {
                if isHikerNearTrail {
                    viewModel.isFollowingUser = true
                    focusOnHikerPosition(animated: true)
                } else {
                    focusOnStartingPoint(animated: true)
                }
            }
        }
        .onChange(of: viewModel.locationTracker.currentLocation?.timestamp) { _, _ in
            if viewModel.isGuidingActive && viewModel.isFollowingUser && isHikerNearTrail {
                focusOnHikerPosition(animated: true)
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active && viewModel.isGuidingActive && isHikerNearTrail {
                viewModel.isFollowingUser = true
                focusOnHikerPosition(animated: true)
            }
        }
        .onChange(of: viewModel.recordedPoints.count) { _, count in
            if count >= 1 && !viewModel.isFollowingUser {
                focusOnStartingPoint(animated: false)
            }
        }
        .onAppear {
            if viewModel.isVirtualTourActive {
                viewModel.isFollowingUser = false
                focusOnTourPoint(index: viewModel.virtualTourIndex)
            } else if viewModel.isGuidingActive && isHikerNearTrail {
                viewModel.isFollowingUser = true
                focusOnHikerPosition(animated: false)
            } else {
                viewModel.isFollowingUser = false
                focusOnStartingPoint(animated: false)
            }
            // Re-aplicar tras el primer ciclo de render de MapKit
            Task { @MainActor in
                try? await Task.sleep(for: .milliseconds(180))
                if viewModel.isVirtualTourActive {
                    focusOnTourPoint(index: viewModel.virtualTourIndex)
                } else if viewModel.isGuidingActive && viewModel.isFollowingUser && isHikerNearTrail {
                    focusOnHikerPosition(animated: false)
                } else if !viewModel.isFollowingUser {
                    focusOnStartingPoint(animated: false)
                }
            }
        }
    }
    
    // MARK: - Helpers de Estilo y Seguimiento del Senderista
    
    /// Determina si el senderista está en o cerca del sendero (<= 2.0 km) para centrar el mapa en su posición en Modo Guía
    private var isHikerNearTrail: Bool {
        guard let userLoc = viewModel.locationTracker.currentLocation else { return false }
        var minDist = Double.greatestFiniteMagnitude
        for pt in viewModel.recordedPoints {
            let d = userLoc.distance(from: CLLocation(latitude: pt.latitude, longitude: pt.longitude))
            if d < minDist { minDist = d }
        }
        if minDist == Double.greatestFiniteMagnitude, let route = viewModel.activeRoute {
            minDist = userLoc.distance(from: CLLocation(latitude: route.originLatitude, longitude: route.originLongitude))
        }
        return minDist <= viewModel.maxDistanceToStartGuideMeters
    }
    
    /// Centra la cámara en la posición real del senderista (y encuadra el siguiente punto objetivo si está cerca)
    private func focusOnHikerPosition(animated: Bool) {
        guard let userLoc = viewModel.locationTracker.currentLocation else {
            if animated {
                withAnimation(.easeInOut(duration: 0.5)) {
                    cameraPosition = .userLocation(fallback: .automatic)
                }
            } else {
                cameraPosition = .userLocation(fallback: .automatic)
            }
            return
        }
        
        var latSpan = 0.0032
        var lonSpan = 0.0032
        if viewModel.isGuidingActive, let target = viewModel.targetGuideWaypoint {
            let dist = userLoc.distance(from: CLLocation(latitude: target.latitude, longitude: target.longitude))
            if dist >= 15.0 && dist <= 750.0 {
                let dLat = abs(target.latitude - userLoc.coordinate.latitude) * 2.5
                let dLon = abs(target.longitude - userLoc.coordinate.longitude) * 2.5
                latSpan = max(0.0028, min(0.012, dLat))
                lonSpan = max(0.0028, min(0.012, dLon))
            }
        }
        
        let region = MKCoordinateRegion(
            center: userLoc.coordinate,
            span: MKCoordinateSpan(latitudeDelta: latSpan, longitudeDelta: lonSpan)
        )
        if animated {
            withAnimation(.easeInOut(duration: 0.45)) {
                cameraPosition = .region(region)
            }
        } else {
            cameraPosition = .region(region)
        }
    }
    
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
    
    private func peerMapCoordinate(for peer: PeerBeaconData) -> CLLocationCoordinate2D {
        if abs(peer.latitude) > 0.001 || abs(peer.longitude) > 0.001 {
            return CLLocationCoordinate2D(latitude: peer.latitude, longitude: peer.longitude)
        }
        let base = viewModel.locationTracker.currentLocation?.coordinate
            ?? viewModel.tourPoints.first.map { CLLocationCoordinate2D(latitude: $0.latitude, longitude: $0.longitude) }
            ?? CLLocationCoordinate2D(latitude: 19.2458, longitude: -103.7251)
        let hashAngle = Double(abs(peer.beaconId.hashValue % 360)) * .pi / 180.0
        let radiusMeters = min(max(peer.estimatedDistanceMeters, 8.0), 80.0)
        let deltaLat = (radiusMeters * cos(hashAngle)) / 111_320.0
        let lonScale = max(0.2, cos(base.latitude * .pi / 180.0))
        let deltaLon = (radiusMeters * sin(hashAngle)) / (111_320.0 * lonScale)
        return CLLocationCoordinate2D(latitude: base.latitude + deltaLat, longitude: base.longitude + deltaLon)
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

// MARK: - Vista Personalizada de Marcador de Estado (1:1 Android getMarkerVisuals sin fotos)

public struct WaypointMarkerView: View {
    public let point: RoutePoint
    public var displayNumber: Int? = nil
    public var isHighlighted: Bool = false
    public var nextStepBadge: String? = nil
    
    private struct MarkerVisuals {
        let bg: Color
        let border: Color
        let systemIcon: String
        let stateName: String
    }
    
    private func resolveVisuals(num: Int) -> MarkerVisuals {
        if num == 1 {
            return MarkerVisuals(
                bg: Color(red: 15/255, green: 118/255, blue: 110/255),     // #0F766E
                border: Color(red: 45/255, green: 212/255, blue: 191/255), // #2DD4BF
                systemIcon: "play.fill",
                stateName: "Inicio"
            )
        }
        if point.isRouteEnd {
            return MarkerVisuals(
                bg: Color(red: 211/255, green: 47/255, blue: 47/255),      // #D32F2F
                border: Color(red: 255/255, green: 205/255, blue: 210/255),// #FFCDD2
                systemIcon: "flag.fill",
                stateName: "Fin de ruta"
            )
        }
        
        let resolvedState = PointStateInfo.forPoint(
            estado: point.estado,
            isRouteEnd: false,
            description: point.descriptionText
        ).estado
        
        switch resolvedState {
        case 2: // Sitio de interés
            return MarkerVisuals(
                bg: Color(red: 46/255, green: 125/255, blue: 50/255),      // #2E7D32
                border: Color(red: 165/255, green: 214/255, blue: 167/255),// #A5D6A7
                systemIcon: "camera.fill",
                stateName: "Sitio de interés"
            )
        case 3: // Bifurcación
            return MarkerVisuals(
                bg: Color(red: 94/255, green: 53/255, blue: 177/255),      // #5E35B1
                border: Color(red: 209/255, green: 196/255, blue: 233/255),// #D1C4E9
                systemIcon: "arrow.triangle.branch",
                stateName: "Bifurcación"
            )
        case 4: // Suelo flojo
            return MarkerVisuals(
                bg: Color(red: 141/255, green: 110/255, blue: 99/255),     // #8D6E63
                border: Color(red: 215/255, green: 204/255, blue: 200/255),// #D7CCC8
                systemIcon: "figure.fall",
                stateName: "Suelo flojo"
            )
        case 5: // Cuidado
            return MarkerVisuals(
                bg: Color(red: 245/255, green: 158/255, blue: 11/255),     // #F59E0B
                border: Color(red: 254/255, green: 243/255, blue: 199/255),// #FEF3C7
                systemIcon: "exclamationmark.triangle.fill",
                stateName: "Cuidado"
            )
        case 6: // Alerta / Peligro
            return MarkerVisuals(
                bg: Color(red: 211/255, green: 47/255, blue: 47/255),      // #D32F2F
                border: Color(red: 255/255, green: 205/255, blue: 210/255),// #FFCDD2
                systemIcon: "skull.fill",
                stateName: "Alerta"
            )
        case 8: // Vehículo
            return MarkerVisuals(
                bg: Color(red: 96/255, green: 125/255, blue: 139/255),     // #607D8B
                border: Color(red: 207/255, green: 216/255, blue: 220/255),// #CFD8DC
                systemIcon: "car.fill",
                stateName: "Vehículo"
            )
        case 9: // Refugio
            return MarkerVisuals(
                bg: Color(red: 78/255, green: 52/255, blue: 46/255),       // #4E342E
                border: Color(red: 215/255, green: 204/255, blue: 200/255),// #D7CCC8
                systemIcon: "house.fill",
                stateName: "Refugio"
            )
        case 10: // Comida
            return MarkerVisuals(
                bg: Color(red: 230/255, green: 81/255, blue: 0/255),       // #E65100
                border: Color(red: 255/255, green: 224/255, blue: 178/255),// #FFE0B2
                systemIcon: "fork.knife",
                stateName: "Comida"
            )
        default: // 1: Punto normal
            return MarkerVisuals(
                bg: Color(red: 25/255, green: 118/255, blue: 210/255),     // #1976D2
                border: Color(red: 144/255, green: 202/255, blue: 249/255),// #90CAF9
                systemIcon: "circle.fill",
                stateName: "Punto normal"
            )
        }
    }
    
    public var body: some View {
        let num = displayNumber ?? point.id
        let visuals = resolveVisuals(num: num)
        
        VStack(spacing: 2) {
            ZStack {
                if isHighlighted {
                    Circle()
                        .stroke(Color(red: 56/255, green: 189/255, blue: 248/255), lineWidth: 3.5)
                        .frame(width: 42, height: 42)
                        .shadow(color: Color(red: 56/255, green: 189/255, blue: 248/255).opacity(0.85), radius: 6)
                }
                
                ZStack(alignment: .bottomTrailing) {
                    Circle()
                        .fill(visuals.bg)
                        .frame(width: 30, height: 30)
                        .overlay(
                            Circle()
                                .stroke(isHighlighted ? Color(red: 56/255, green: 189/255, blue: 248/255) : visuals.border, lineWidth: 2.5)
                        )
                        .shadow(color: Color.black.opacity(0.45), radius: 3, x: 0, y: 2)
                    
                    Image(systemName: visuals.systemIcon)
                        .font(.system(size: visuals.systemIcon == "circle.fill" ? 8 : 13, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 30, height: 30)
                    
                    Text("\(num)")
                        .font(.system(size: 8, weight: .heavy, design: .rounded))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 3.5)
                        .padding(.vertical, 1)
                        .background(Color(red: 15/255, green: 23/255, blue: 42/255))
                        .overlay(
                            Capsule().stroke(visuals.border.opacity(0.8), lineWidth: 0.8)
                        )
                        .clipShape(Capsule())
                        .offset(x: 4, y: 4)
                }
            }
            
            // Etiqueta flotante con el estatus especial (si no es punto normal) + distancia y subida/bajada
            let chipText: String? = {
                let statePrefix = (visuals.stateName != "Punto normal" && visuals.stateName != "Inicio" && visuals.stateName != "Fin de ruta")
                    ? "\(visuals.stateName)"
                    : nil
                if let sp = statePrefix, let badge = nextStepBadge, !badge.isEmpty, badge != "Fin" {
                    return "\(sp) · \(badge)"
                } else if let sp = statePrefix {
                    return sp
                } else {
                    return nextStepBadge
                }
            }()
            
            if let badge = chipText, !badge.isEmpty {
                Text(badge)
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(isHighlighted ? Color(red: 94/255, green: 234/255, blue: 212/255) : .white)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 1.5)
                    .background(AppTheme.darkBackground.opacity(0.9))
                    .clipShape(Capsule())
            }
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

// MARK: - Marcador de Alta Visibilidad del Senderista en Modo Guía

public struct HikerGuideMarkerView: View {
    public var courseDegrees: Double = -1.0
    @State private var isPulsing: Bool = false
    
    public var body: some View {
        VStack(spacing: 2) {
            ZStack {
                Circle()
                    .stroke(Color(red: 16/255, green: 185/255, blue: 129/255).opacity(0.65), lineWidth: 2.5)
                    .frame(width: isPulsing ? 46 : 28, height: isPulsing ? 46 : 28)
                    .scaleEffect(isPulsing ? 1.25 : 0.9)
                    .opacity(isPulsing ? 0 : 0.9)
                    .animation(.easeInOut(duration: 1.4).repeatForever(autoreverses: false), value: isPulsing)
                
                Circle()
                    .fill(Color(red: 5/255, green: 150/255, blue: 105/255))
                    .frame(width: 30, height: 30)
                    .overlay(
                        Circle().stroke(Color.white, lineWidth: 2.5)
                    )
                    .shadow(color: Color.black.opacity(0.55), radius: 4, x: 0, y: 2)
                
                if courseDegrees >= 0 {
                    Image(systemName: "location.north.fill")
                        .font(.system(size: 14, weight: .heavy))
                        .foregroundStyle(.white)
                        .rotationEffect(.degrees(courseDegrees))
                } else {
                    Image(systemName: "figure.hiking")
                        .font(.system(size: 14, weight: .heavy))
                        .foregroundStyle(.white)
                }
            }
            .onAppear {
                isPulsing = true
            }
            
            Text("TÚ")
                .font(.system(size: 8, weight: .heavy, design: .rounded))
                .foregroundStyle(.white)
                .padding(.horizontal, 5)
                .padding(.vertical, 1)
                .background(Color(red: 5/255, green: 150/255, blue: 105/255))
                .overlay(
                    Capsule().stroke(Color.white.opacity(0.85), lineWidth: 1)
                )
                .clipShape(Capsule())
        }
    }
}



