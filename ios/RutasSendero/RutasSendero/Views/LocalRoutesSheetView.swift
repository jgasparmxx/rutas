//
//  LocalRoutesSheetView.swift
//  RutasSendero
//
//  Pantalla modal para listar y seleccionar rutas guardadas localmente en SwiftData.
//  Permite al senderista elegir una ruta para darle seguimiento en el mapa o guiarla.
//

import SwiftUI

public struct LocalRoutesSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var selectedTab: Int = 0 // 0: Mis Rutas, 1: Otras Rutas
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
        self._selectedTab = State(initialValue: viewModel.localRoutesInitialTab)
    }
    
    private var userPhone: String {
        viewModel.currentHikerProfile?.phone ?? ""
    }
    
    private var myRoutes: [Route] {
        viewModel.availableLocalRoutes.filter { $0.isOwnRoute(userPhone: userPhone) }
    }
    
    private var otherRoutes: [Route] {
        viewModel.availableLocalRoutes.filter { !$0.isOwnRoute(userPhone: userPhone) }
    }
    
    private var displayedRoutes: [Route] {
        selectedTab == 0 ? myRoutes : otherRoutes
    }
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                tabBarSelector
                
                Group {
                    if displayedRoutes.isEmpty {
                        emptyStateView
                    } else {
                        routesListView
                    }
                }
            }
            .navigationTitle("Rutas Guardadas")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") {
                        dismiss()
                    }
                }
                
                // Botón de Sincronización con flechas circulando
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        Task {
                            await viewModel.triggerManualCloudSync()
                        }
                    } label: {
                        HStack(spacing: 5) {
                            Image(systemName: "arrow.triangle.2.circlepath")
                                .font(.system(size: 16, weight: .bold))
                                .rotationEffect(.degrees(viewModel.isSyncingRoutes ? 360 : 0))
                                .animation(
                                    viewModel.isSyncingRoutes
                                        ? .linear(duration: 1).repeatForever(autoreverses: false)
                                        : .default,
                                    value: viewModel.isSyncingRoutes
                                )
                        }
                        .foregroundStyle(AppTheme.accent)
                    }
                    .disabled(viewModel.isSyncingRoutes)
                }
            }
            .task {
                await viewModel.loadLocalRoutes()
            }
            .onAppear {
                selectedTab = viewModel.localRoutesInitialTab
            }
        }
    }
    
    // MARK: - Barra de Pestañas Ultra-Rápida
    @ViewBuilder
    private var tabBarSelector: some View {
        HStack(spacing: 4) {
            Button {
                selectedTab = 0
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "mountain.2.fill")
                        .font(.system(size: 12))
                    Text("Mis Rutas (\(myRoutes.count))")
                        .font(.system(size: 13, weight: selectedTab == 0 ? .heavy : .medium))
                }
                .foregroundStyle(selectedTab == 0 ? Color(red: 15/255, green: 23/255, blue: 42/255) : AppTheme.textSecondary)
                .frame(maxWidth: .infinity)
                .frame(height: 38)
                .background(selectedTab == 0 ? AppTheme.accent : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 9))
            }
            .buttonStyle(.plain)
            
            Button {
                selectedTab = 1
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "arrow.triangle.2.circlepath")
                        .font(.system(size: 12))
                    Text("Otras Rutas (\(otherRoutes.count))")
                        .font(.system(size: 13, weight: selectedTab == 1 ? .heavy : .medium))
                }
                .foregroundStyle(selectedTab == 1 ? Color(red: 15/255, green: 23/255, blue: 42/255) : AppTheme.textSecondary)
                .frame(maxWidth: .infinity)
                .frame(height: 38)
                .background(selectedTab == 1 ? (viewModel.activeRole == .guia ? AppTheme.accent : Color(red: 56/255, green: 189/255, blue: 248/255)) : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 9))
            }
            .buttonStyle(.plain)
        }
        .padding(4)
        .background(AppTheme.cardMuted.opacity(0.95))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
    
    // MARK: - Estado Vacío
    @ViewBuilder
    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: selectedTab == 0 ? "mountain.2" : "icloud.and.arrow.down")
                .font(.system(size: 60))
                .foregroundStyle(AppTheme.textSecondary.opacity(0.6))
            
            Text(selectedTab == 0 ? "No hay rutas propias guardadas" : "No hay otras rutas descargadas")
                .font(.headline)
                .foregroundStyle(AppTheme.textPrimary)
            
            Text(selectedTab == 0 ?
                "Aún no tienes senderos registrados en este dispositivo. Puedes comenzar a grabar uno nuevo con el botón de abajo." :
                "Las rutas descargadas de la nube pertenecientes a otros senderistas aparecerán aquí para guiar o recorrer."
            )
            .font(.subheadline)
            .foregroundStyle(AppTheme.textSecondary)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 32)
            
            if selectedTab == 0 {
                Button {
                    Task {
                        await viewModel.triggerManualCloudSync()
                    }
                } label: {
                    HStack {
                        Image(systemName: "arrow.triangle.2.circlepath")
                        Text("Sincronizar con la Nube")
                    }
                    .font(.headline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(AppTheme.primary)
                    .clipShape(Capsule())
                }
                .padding(.top, 12)
            } else {
                Button {
                    dismiss()
                    viewModel.isShowingCloudRoutesSheet = true
                } label: {
                    HStack {
                        Image(systemName: "icloud.and.arrow.down.fill")
                        Text("Descargar de la Nube")
                    }
                    .font(.headline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(AppTheme.accent)
                    .clipShape(Capsule())
                }
                .padding(.top, 12)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
    
    // MARK: - Lista de Rutas
    @ViewBuilder
    private var routesListView: some View {
        List {
            ForEach(displayedRoutes) { route in
                LocalRouteRowView(
                    route: route,
                    activeRole: viewModel.activeRole,
                    isConsumptionOnly: selectedTab == 1,
                    onUpload: selectedTab == 0 ? {
                        await viewModel.uploadRouteToCloud(route)
                    } : nil
                ) {
                    // Respeta estrictamente el modo activo del senderista (Guía o Visor)
                    viewModel.pendingRouteForOrderSelection = route
                    viewModel.isShowingVisorOrderDialog = true
                    dismiss()
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

// MARK: - Fila de Ruta Local

private struct LocalRouteRowView: View {
    let route: Route
    var activeRole: AppRole = .guia
    var isConsumptionOnly: Bool = false
    var onUpload: (() async -> Bool)? = nil
    let onSelect: () -> Void
    @State private var isUploading: Bool = false
    @State private var uploadSuccess: Bool = false
    @State private var uploadFailed: Bool = false
    
    var body: some View {
        let isGuia = (activeRole == .guia)
        let btnColor = isGuia ? AppTheme.accent : Color(red: 56/255, green: 189/255, blue: 248/255)
        
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(route.name)
                            .font(.headline)
                            .foregroundStyle(AppTheme.textPrimary)
                        
                        if isConsumptionOnly {
                            Text(isGuia ? "IMPORTADA" : "CONSUMO")
                                .font(.system(size: 9, weight: .heavy))
                                .foregroundStyle(btnColor)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(btnColor.opacity(0.15))
                                .clipShape(Capsule())
                        }
                    }
                    
                    HStack(spacing: 6) {
                        Text(route.date)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                        
                        if isConsumptionOnly && !route.creatorPhone.isEmpty {
                            Text("· Creador: \(route.creatorPhone)")
                                .font(.caption)
                                .foregroundStyle(AppTheme.accent)
                        }
                    }
                }
                
                Spacer()
                
                if !isConsumptionOnly, let onUpload = onUpload {
                    Button {
                        isUploading = true
                        uploadFailed = false
                        Task {
                            let ok = await onUpload()
                            await MainActor.run {
                                isUploading = false
                                uploadSuccess = ok
                                uploadFailed = !ok
                            }
                        }
                    } label: {
                        Image(systemName: uploadSuccess ? "checkmark.circle.fill" : (uploadFailed ? "exclamationmark.icloud.fill" : (isUploading ? "arrow.triangle.2.circlepath" : "icloud.and.arrow.up")))
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(uploadSuccess ? AppTheme.accent : (uploadFailed ? AppTheme.alert : AppTheme.accentCyan))
                            .padding(7)
                            .background((uploadFailed ? AppTheme.alert : AppTheme.accentCyan).opacity(0.12))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                    .disabled(isUploading)
                }
                
                Button {
                    onSelect()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: isGuia ? "figure.hiking" : "eye.fill")
                            .font(.system(size: 10, weight: .bold))
                        Text(isGuia ? "GUIAR" : (isConsumptionOnly ? "VISOR" : "ABRIR"))
                            .font(.system(size: 11, weight: .heavy))
                    }
                    .foregroundStyle(Color(red: 15/255, green: 23/255, blue: 42/255))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(btnColor)
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
            
            // Grid de Métricas de la Ruta
            HStack(spacing: 16) {
                HStack(spacing: 4) {
                    Image(systemName: "ruler")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.accent)
                    Text(RouteStatsCalculator.formatDistance(route.gpsDistanceMeters))
                        .font(.caption).bold()
                }
                
                HStack(spacing: 4) {
                    Image(systemName: "clock")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textSecondary)
                    Text(RouteStatsCalculator.formatDuration(route.durationSeconds))
                        .font(.caption)
                }
                
                HStack(spacing: 4) {
                    Image(systemName: "arrow.up.right")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.primaryLight)
                    Text(String(format: "+%.0fm", route.elevationGain))
                        .font(.caption)
                }
            }
            .foregroundStyle(AppTheme.textSecondary)
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture {
            onSelect()
        }
    }
}
