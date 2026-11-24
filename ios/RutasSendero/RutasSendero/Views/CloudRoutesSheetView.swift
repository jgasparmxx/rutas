//
//  CloudRoutesSheetView.swift
//  RutasSendero
//
//  Vista unificada para "2. Ver otras rutas" (1:1 Android CloudRoutesDialog + Otras Rutas Descargadas).
//  Integra en una sola pantalla las rutas de otros senderistas ya descargadas en el iPhone
//  (disponibles incluso sin internet) y el catálogo en la nube (PostgREST / Render)
//  con búsqueda por ruta o autor, filtro de cercanía (< 20 km), soporte para código
//  de acceso en rutas privadas y descarga/descifrado directo en un toque.
//

import SwiftUI
import CoreLocation

public struct CloudRoutesSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var cloudRoutes: [CloudRouteSummary] = []
    @State private var isLoading: Bool = true
    @State private var errorMessage: String? = nil
    @State private var searchText: String = ""
    @State private var showNearbyOnly: Bool = false
    @State private var downloadingRouteId: String? = nil
    @State private var downloadedSuccessMessage: String? = nil
    @State private var lastDownloadedRoute: Route? = nil
    
    // Soporte para Rutas Privadas con Código de Acceso (1:1 Android Components.kt:6719-6725, 6874-6969)
    @State private var pendingPrivateRoute: CloudRouteSummary? = nil
    @State private var typedAccessCode: String = ""
    @State private var accessCodeError: Bool = false
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    private var userEmail: String {
        (viewModel.currentHikerProfile?.email ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }
    
    private var userPhoneDigits: String {
        String((viewModel.currentHikerProfile?.phone ?? "").filter { $0.isNumber }.suffix(10))
    }
    
    private func isOwnCloudRoute(_ route: CloudRouteSummary) -> Bool {
        let rEmail = route.creatorEmail.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !userEmail.isEmpty && !rEmail.isEmpty && rEmail == userEmail {
            return true
        }
        let rPhone = String(route.creatorPhone.filter { $0.isNumber }.suffix(10))
        if userPhoneDigits.count >= 7 && !rPhone.isEmpty && rPhone == userPhoneDigits {
            return true
        }
        return false
    }
    
    /// Rutas de otros senderistas ya descargadas localmente en el iPhone (disponibles offline)
    private var localOtherRoutes: [Route] {
        let phone = viewModel.currentHikerProfile?.phone ?? ""
        return viewModel.availableLocalRoutes.filter { !$0.isOwnRoute(userPhone: phone) }
    }
    
    /// Rutas locales de otros senderistas que no aparecen en la respuesta actual de la nube (ej. modo sin conexión)
    private var offlineOnlyLocalOtherRoutes: [Route] {
        let cloudIds = Set(cloudRoutes.map { $0.id })
        let base = localOtherRoutes.filter { !cloudIds.contains($0.id) }
        let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let searched = term.isEmpty ? base : base.filter {
            $0.name.lowercased().contains(term) ||
            $0.creatorPhone.lowercased().contains(term) ||
            $0.descriptionText.lowercased().contains(term)
        }
        if showNearbyOnly, let userLoc = viewModel.locationTracker.currentLocation {
            return searched.filter { route in
                guard route.originLatitude != 0.0 || route.originLongitude != 0.0 else { return false }
                let rLoc = CLLocation(latitude: route.originLatitude, longitude: route.originLongitude)
                return userLoc.distance(from: rLoc) <= 20000.0
            }
        }
        return searched
    }
    
    /// Rutas de otros senderistas en la nube filtradas por búsqueda y cercanía (< 20 km, 1:1 Android Components.kt:6538-6572)
    private var filteredRoutes: [CloudRouteSummary] {
        // 1. Mostrar solo rutas de otros senderistas (!isOwn)
        var list = cloudRoutes.filter { !isOwnCloudRoute($0) }
        
        // 2. Filtrar por búsqueda (nombre, autor o descripción)
        let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if !term.isEmpty {
            list = list.filter {
                $0.name.lowercased().contains(term) ||
                $0.creatorName.lowercased().contains(term) ||
                $0.descriptionText.lowercased().contains(term)
            }
        }
        
        // 3. Filtrar por distancia (< 20 km) si mostrarCercanas está activo y tenemos GPS
        if showNearbyOnly, let userLoc = viewModel.locationTracker.currentLocation {
            list = list.filter { route in
                guard route.originLatitude != 0.0 || route.originLongitude != 0.0 else { return false }
                let rLoc = CLLocation(latitude: route.originLatitude, longitude: route.originLongitude)
                return userLoc.distance(from: rLoc) <= 20000.0
            }
        }
        
        // Ordenar poniendo primero las que ya están descargadas en este iPhone para acceso rápido
        return list.sorted { a, b in
            let aLocal = isRouteDownloadedLocally(a.id)
            let bLocal = isRouteDownloadedLocally(b.id)
            if aLocal != bLocal {
                return aLocal && !bLocal
            }
            return false
        }
    }
    
    private func isRouteDownloadedLocally(_ id: String) -> Bool {
        viewModel.availableLocalRoutes.contains { $0.id == id }
    }
    
    private func formattedDistanceToOrigin(lat: Double, lng: Double) -> String? {
        guard (lat != 0.0 || lng != 0.0), let userLoc = viewModel.locationTracker.currentLocation else { return nil }
        let dist = userLoc.distance(from: CLLocation(latitude: lat, longitude: lng))
        if dist >= 1000.0 {
            return String(format: "A %.1f km", dist / 1000.0)
        } else {
            return String(format: "A %.0f m", dist)
        }
    }
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Barra superior de filtro de cercanía (< 20 km, 1:1 Android Components.kt:6654-6671)
                HStack(spacing: 10) {
                    Button {
                        withAnimation(.easeInOut(duration: 0.18)) {
                            showNearbyOnly.toggle()
                        }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: showNearbyOnly ? "checkmark.square.fill" : "square")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(showNearbyOnly ? AppTheme.accent : AppTheme.textSecondary)
                            
                            Text("Mostrar rutas cercanas (< 20 km)")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(AppTheme.textPrimary)
                        }
                    }
                    .buttonStyle(.plain)
                    
                    Spacer()
                    
                    if !localOtherRoutes.isEmpty {
                        Text("\(localOtherRoutes.count) en iPhone")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(AppTheme.success)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(AppTheme.success.opacity(0.15))
                            .clipShape(Capsule())
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(AppTheme.cardBackground.opacity(0.95))
                
                Group {
                    if isLoading && cloudRoutes.isEmpty && localOtherRoutes.isEmpty {
                        VStack(spacing: 16) {
                            ProgressView()
                                .scaleEffect(1.5)
                                .tint(AppTheme.accent)
                            
                            Text("Consultando rutas disponibles...")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if let error = errorMessage, cloudRoutes.isEmpty && offlineOnlyLocalOtherRoutes.isEmpty {
                        VStack(spacing: 16) {
                            Image(systemName: "icloud.slash")
                                .font(.system(size: 50))
                                .foregroundStyle(AppTheme.alert)
                            
                            Text("No se pudo conectar a la nube")
                                .font(.headline)
                                .foregroundStyle(AppTheme.textPrimary)
                            
                            Text(error)
                                .font(.subheadline)
                                .foregroundStyle(AppTheme.textSecondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 32)
                            
                            Button {
                                loadCloudRoutes()
                            } label: {
                                Label("Reintentar Conexión", systemImage: "arrow.clockwise")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 22)
                                    .padding(.vertical, 11)
                                    .background(AppTheme.accent)
                                    .clipShape(Capsule())
                            }
                            .padding(.top, 8)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else if filteredRoutes.isEmpty && offlineOnlyLocalOtherRoutes.isEmpty {
                        VStack(spacing: 14) {
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 44))
                                .foregroundStyle(AppTheme.textSecondary.opacity(0.6))
                            
                            Text(showNearbyOnly ? "No se encontraron rutas dentro de 20 km" : "No se encontraron otras rutas")
                                .font(.headline)
                                .foregroundStyle(AppTheme.textPrimary)
                            
                            Text(showNearbyOnly ? "Desactiva el filtro de cercanía o prueba con otro término de búsqueda." : "Prueba con otro nombre de ruta o senderista.")
                                .font(.subheadline)
                                .foregroundStyle(AppTheme.textSecondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 32)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        List {
                            if !offlineOnlyLocalOtherRoutes.isEmpty {
                                Section("Descargadas en este iPhone") {
                                    ForEach(offlineOnlyLocalOtherRoutes) { localRoute in
                                        offlineLocalOtherRouteRow(localRoute)
                                    }
                                }
                            }
                            
                            if !filteredRoutes.isEmpty {
                                Section(offlineOnlyLocalOtherRoutes.isEmpty ? "Rutas Disponibles (\(filteredRoutes.count))" : "Catálogo en la Nube (\(filteredRoutes.count))") {
                                    ForEach(filteredRoutes) { route in
                                        cloudRouteRow(route)
                                    }
                                }
                            }
                        }
                        .listStyle(.insetGrouped)
                        .searchable(text: $searchText, prompt: "Buscar por ruta o senderista...")
                    }
                }
            }
            .navigationTitle("Ver Otras Rutas")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        loadCloudRoutes()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
            .alert("Ruta Privada", isPresented: Binding(
                get: { pendingPrivateRoute != nil },
                set: { if !$0 { pendingPrivateRoute = nil; typedAccessCode = ""; accessCodeError = false } }
            )) {
                TextField("Código de Acceso", text: $typedAccessCode)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                Button("Cancelar", role: .cancel) {
                    pendingPrivateRoute = nil
                    typedAccessCode = ""
                    accessCodeError = false
                }
                Button("Descargar") {
                    if let target = pendingPrivateRoute {
                        let expected = target.code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                        let entered = typedAccessCode.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
                        if !expected.isEmpty && entered == expected {
                            pendingPrivateRoute = nil
                            typedAccessCode = ""
                            accessCodeError = false
                            downloadRoute(target)
                        } else {
                            accessCodeError = true
                            errorMessage = "Código de acceso incorrecto para la ruta privada '\(target.name)'."
                        }
                    }
                }
            } message: {
                if let target = pendingPrivateRoute {
                    Text("Introduce el código de acceso proporcionado por el autor para descargar la ruta \"\(target.name)\":")
                }
            }
            .alert("Ruta Lista", isPresented: Binding(
                get: { downloadedSuccessMessage != nil },
                set: { if !$0 { downloadedSuccessMessage = nil } }
            )) {
                if let route = lastDownloadedRoute {
                    let isGuia = (viewModel.activeRole == .guia)
                    Button(isGuia ? "Iniciar Guía de la Ruta" : "Abrir en Modo Visor") {
                        if !isGuia {
                            viewModel.activeRole = .visor
                        }
                        viewModel.pendingRouteForOrderSelection = route
                        viewModel.isShowingVisorOrderDialog = true
                        dismiss()
                    }
                }
                Button("Seguir Explorando", role: .cancel) {
                    downloadedSuccessMessage = nil
                }
            } message: {
                Text(downloadedSuccessMessage ?? "")
            }
            .task {
                await viewModel.loadLocalRoutes()
                loadCloudRoutes()
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
    
    // MARK: - Fila de Ruta de Otro Senderista ya descargada (Modo Offline)
    
    private func offlineLocalOtherRouteRow(_ route: Route) -> some View {
        let isGuia = (viewModel.activeRole == .guia)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(route.name)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(AppTheme.textPrimary)
                        
                        Text("DESCARGADA")
                            .font(.system(size: 9, weight: .heavy))
                            .foregroundStyle(AppTheme.success)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(AppTheme.success.opacity(0.15))
                            .clipShape(Capsule())
                    }
                    
                    if !route.creatorPhone.isEmpty {
                        Text("Autor / Tel: \(route.creatorPhone)")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(AppTheme.accent)
                    }
                }
                
                Spacer()
                
                Text(RouteStatsCalculator.formatDistance(route.gpsDistanceMeters))
                    .font(.system(size: 13, weight: .bold, design: .monospaced))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(AppTheme.cardMuted)
                    .foregroundStyle(AppTheme.accent)
                    .clipShape(Capsule())
            }
            
            HStack {
                Text("Fecha: \(route.date)")
                    .font(.system(size: 11))
                    .foregroundStyle(AppTheme.textSecondary.opacity(0.8))
                
                if let distOrigin = formattedDistanceToOrigin(lat: route.originLatitude, lng: route.originLongitude) {
                    Text("· \(distOrigin)")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(AppTheme.accent)
                }
                
                Spacer()
                
                Button {
                    viewModel.pendingRouteForOrderSelection = route
                    viewModel.isShowingVisorOrderDialog = true
                    dismiss()
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: isGuia ? "figure.hiking" : "eye.fill")
                        Text(isGuia ? "Guiar" : "Abrir")
                    }
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(isGuia ? AppTheme.accent : AppTheme.success)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background((isGuia ? AppTheme.accent : AppTheme.success).opacity(0.15))
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 6)
    }
    
    // MARK: - Fila de Ruta en la Nube (Unificada con Estado Local)
    
    private func cloudRouteRow(_ route: CloudRouteSummary) -> some View {
        let isLocal = isRouteDownloadedLocally(route.id)
        let isDownloadingThis = downloadingRouteId == route.id
        
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(route.name)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(AppTheme.textPrimary)
                        
                        // Insignia PÚBLICA / PRIVADA (1:1 Android Components.kt:6779-6785)
                        Text(route.isPublic ? "PÚBLICA" : "PRIVADA")
                            .font(.system(size: 9, weight: .heavy))
                            .foregroundStyle(route.isPublic ? AppTheme.success : AppTheme.alert)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background((route.isPublic ? AppTheme.success : AppTheme.alert).opacity(0.15))
                            .clipShape(Capsule())
                        
                        if isLocal {
                            Text("EN IPHONE")
                                .font(.system(size: 9, weight: .heavy))
                                .foregroundStyle(AppTheme.accent)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(AppTheme.accent.opacity(0.15))
                                .clipShape(Capsule())
                        }
                    }
                    
                    Text("Autor: \(route.creatorName)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(AppTheme.accent)
                }
                
                Spacer()
                
                // Distancia GPS
                Text(route.formattedDistanceKm)
                    .font(.system(size: 13, weight: .bold, design: .monospaced))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(AppTheme.cardMuted)
                    .foregroundStyle(AppTheme.accent)
                    .clipShape(Capsule())
            }
            
            if !route.descriptionText.isEmpty {
                Text(route.descriptionText)
                    .font(.system(size: 12))
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(2)
            }
            
            HStack {
                HStack(spacing: 4) {
                    Text("Fecha: \(route.date)")
                        .font(.system(size: 11))
                        .foregroundStyle(AppTheme.textSecondary.opacity(0.8))
                    
                    if let distOrigin = formattedDistanceToOrigin(lat: route.originLatitude, lng: route.originLongitude) {
                        Text("· \(distOrigin)")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(AppTheme.accent)
                    }
                }
                
                Spacer()
                
                if isDownloadingThis {
                    HStack(spacing: 6) {
                        ProgressView()
                            .scaleEffect(0.8)
                        Text("Descargando...")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(AppTheme.warningYellow)
                    }
                } else if isLocal {
                    HStack(spacing: 8) {
                        // Botón para re-descargar / actualizar desde la nube si el autor la modificó
                        Button {
                            requestDownloadRoute(route)
                        } label: {
                            Image(systemName: "icloud.and.arrow.down")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(AppTheme.textSecondary)
                                .padding(6)
                                .background(AppTheme.cardMuted)
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)
                        
                        Button {
                            // Cargar ruta localmente y presentar el diálogo respetando el rol de Guía o Visor
                            Task {
                                if let repo = viewModel.rutasRepository,
                                   let localRoute = try? await repo.getRoute(byId: route.id) {
                                    await MainActor.run {
                                        viewModel.pendingRouteForOrderSelection = localRoute
                                        viewModel.isShowingVisorOrderDialog = true
                                        dismiss()
                                    }
                                }
                            }
                        } label: {
                            let isGuia = (viewModel.activeRole == .guia)
                            HStack(spacing: 5) {
                                Image(systemName: isGuia ? "figure.hiking" : "checkmark.circle.fill")
                                Text(isGuia ? "Guiar" : "Abrir")
                            }
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(isGuia ? AppTheme.accent : AppTheme.success)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background((isGuia ? AppTheme.accent : AppTheme.success).opacity(0.15))
                            .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                } else {
                    Button {
                        requestDownloadRoute(route)
                    } label: {
                        HStack(spacing: 5) {
                            Image(systemName: route.isPublic ? "arrow.down.circle.fill" : "lock.fill")
                            Text("Descargar")
                        }
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(AppTheme.primary)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 4)
        }
        .padding(.vertical, 6)
    }
    
    // MARK: - Solicitar Descarga (Verifica si es Pública o Privada 1:1 Android)
    
    private func requestDownloadRoute(_ summary: CloudRouteSummary) {
        if !summary.isPublic && !summary.code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            typedAccessCode = ""
            accessCodeError = false
            pendingPrivateRoute = summary
        } else {
            downloadRoute(summary)
        }
    }
    
    // MARK: - Cargar catálogo
    
    private func loadCloudRoutes() {
        if cloudRoutes.isEmpty {
            isLoading = true
        }
        errorMessage = nil
        Task {
            do {
                let routes = try await CloudSyncService.shared.fetchCloudRoutes()
                await MainActor.run {
                    self.cloudRoutes = routes
                    self.isLoading = false
                }
            } catch {
                await MainActor.run {
                    if self.cloudRoutes.isEmpty {
                        self.errorMessage = error.localizedDescription
                    }
                    self.isLoading = false
                }
            }
        }
    }
    
    // MARK: - Descargar Ruta
    
    private func downloadRoute(_ summary: CloudRouteSummary) {
        guard let repo = viewModel.rutasRepository else { return }
        downloadingRouteId = summary.id
        
        Task {
            do {
                let downloadedRoute = try await CloudSyncService.shared.downloadAndSaveRoute(
                    routeId: summary.id,
                    into: repo
                )
                await viewModel.loadLocalRoutes()
                
                await MainActor.run {
                    downloadingRouteId = nil
                    lastDownloadedRoute = downloadedRoute
                    downloadedSuccessMessage = "La ruta '\(downloadedRoute.name)' se descargó y descifró exitosamente. ¿Deseas abrirla ahora?"
                }
            } catch {
                await MainActor.run {
                    downloadingRouteId = nil
                    errorMessage = "Error al descargar ruta: \(error.localizedDescription)"
                }
            }
        }
    }
}
