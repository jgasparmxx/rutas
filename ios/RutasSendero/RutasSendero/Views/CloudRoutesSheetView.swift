//
//  CloudRoutesSheetView.swift
//  RutasSendero
//
//  Catálogo interactivo de rutas en la nube (PostgREST / Render).
//  Permite explorar, buscar e importar rutas comunitarias y oficiales
//  descifrándolas en tiempo real hacia la base de datos local SQLite/SwiftData.
//

import SwiftUI

public struct CloudRoutesSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var cloudRoutes: [CloudRouteSummary] = []
    @State private var isLoading: Bool = true
    @State private var errorMessage: String? = nil
    @State private var searchText: String = ""
    @State private var downloadingRouteId: String? = nil
    @State private var downloadedSuccessMessage: String? = nil
    @State private var lastDownloadedRoute: Route? = nil
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    private var filteredRoutes: [CloudRouteSummary] {
        if searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return cloudRoutes
        }
        let term = searchText.lowercased()
        return cloudRoutes.filter {
            $0.name.lowercased().contains(term) ||
            $0.creatorName.lowercased().contains(term) ||
            $0.descriptionText.lowercased().contains(term)
        }
    }
    
    private func isRouteDownloadedLocally(_ id: String) -> Bool {
        viewModel.availableLocalRoutes.contains { $0.id == id }
    }
    
    public var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    VStack(spacing: 16) {
                        ProgressView()
                            .scaleEffect(1.5)
                            .tint(AppTheme.accent)
                        
                        Text("Conectando con la nube de RutasSendero...")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let error = errorMessage {
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
                } else if filteredRoutes.isEmpty {
                    VStack(spacing: 14) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 44))
                            .foregroundStyle(AppTheme.textSecondary.opacity(0.6))
                        
                        Text("No se encontraron rutas")
                            .font(.headline)
                            .foregroundStyle(AppTheme.textPrimary)
                        
                        Text("Prueba con otro término de búsqueda.")
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List {
                        ForEach(filteredRoutes) { route in
                            cloudRouteRow(route)
                        }
                    }
                    .listStyle(.insetGrouped)
                    .searchable(text: $searchText, prompt: "Buscar por nombre o autor...")
                }
            }
            .navigationTitle("Rutas en la Nube")
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
            .alert("Ruta Importada", isPresented: Binding(
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
                Button("Ver en Lista Local") {
                    dismiss()
                    viewModel.isShowingLocalRoutesSheet = true
                }
                Button("Aceptar", role: .cancel) {
                    downloadedSuccessMessage = nil
                }
            } message: {
                Text(downloadedSuccessMessage ?? "")
            }
            .task {
                loadCloudRoutes()
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
    
    // MARK: - Fila de Ruta en la Nube
    
    private func cloudRouteRow(_ route: CloudRouteSummary) -> some View {
        let isLocal = isRouteDownloadedLocally(route.id)
        let isDownloadingThis = downloadingRouteId == route.id
        
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(route.name)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(AppTheme.textPrimary)
                    
                    Text("Por: \(route.creatorName)")
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
                Text("Fecha: \(route.date)")
                    .font(.system(size: 11))
                    .foregroundStyle(AppTheme.textSecondary.opacity(0.8))
                
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
                    Button {
                        // Cargar ruta localmente y presentar el diálogo respetando el rol de Guía
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
                } else {
                    Button {
                        downloadRoute(route)
                    } label: {
                        HStack(spacing: 5) {
                            Image(systemName: "arrow.down.circle.fill")
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
                    downloadedSuccessMessage = "La ruta '\(downloadedRoute.name)' se descargó y descifró exitosamente. Ahora está disponible en tus rutas locales."
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
