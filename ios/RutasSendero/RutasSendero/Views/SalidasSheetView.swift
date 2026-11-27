//
//  SalidasSheetView.swift
//  RutasSendero
//
//  Vista unificada para "3. Planeación Salidas" (1:1 Android SalidasDialog).
//  Divide la interfaz en 3 pestañas:
//    - Pestaña 0: Disponibles (Solo salidas futuras filtradas por privacidad/agrupación)
//    - Pestaña 1: Mis Salidas (Aceptadas pendientes con fecha >= Date())
//    - Pestaña 2: Historial (Aceptadas concluidas con fecha < Date() y botón eliminar)
//

import SwiftUI
import CoreLocation

public struct SalidasSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var selectedTab: Int = 0 // 0: Disponibles, 1: Mis Salidas, 2: Historial
    @State private var searchQuery: String = ""
    
    // Detalle de salida disponible
    @State private var selectedOutingForDetail: OutingInvitation? = nil
    
    // Registrar cooperación
    @State private var outingForCooperation: OutingInvitation? = nil
    @State private var coopAmountInput: String = ""
    
    // Confirmación de cancelación o eliminación
    @State private var outingToCancel: OutingInvitation? = nil
    @State private var outingToDeleteFromHistory: OutingInvitation? = nil
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        NavigationStack {
            ZStack {
                AppTheme.darkBackground
                    .ignoresSafeArea()
                
                VStack(spacing: 0) {
                    // Selector de 3 Pestañas
                    tabsHeader
                        .padding(.horizontal, 16)
                        .padding(.top, 12)
                        .padding(.bottom, 8)
                    
                    // Barra de búsqueda y refresco
                    searchAndActionBar
                        .padding(.horizontal, 16)
                        .padding(.bottom, 10)
                    
                    // Contenido según la pestaña seleccionada
                    Group {
                        switch selectedTab {
                        case 0:
                            disponiblesTabView
                        case 1:
                            misSalidasTabView
                        case 2:
                            historialTabView
                        default:
                            EmptyView()
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                
                // Overlay de progreso
                if let msg = viewModel.outingsProgressMessage {
                    loadingOverlay(message: msg)
                }
            }
            .navigationTitle("Gestión de Salidas")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") {
                        dismiss()
                    }
                    .foregroundStyle(AppTheme.accent)
                }
            }
            .task {
                await viewModel.fetchAvailableOutings()
            }
            // Sheet de Detalle de Salida Disponible
            .sheet(item: $selectedOutingForDetail) { outing in
                OutingDetailSheet(outing: outing, viewModel: viewModel) {
                    selectedOutingForDetail = nil
                }
            }
            // Dialog de Registro de Cooperación
            .sheet(item: $outingForCooperation) { outing in
                CooperationSheet(outing: outing, viewModel: viewModel) {
                    outingForCooperation = nil
                }
            }
            // Alerta de confirmación de cancelación de asistencia
            .alert("Cancelar Asistencia", isPresented: Binding(
                get: { outingToCancel != nil },
                set: { if !$0 { outingToCancel = nil } }
            )) {
                Button("No, conservar", role: .cancel) {
                    outingToCancel = nil
                }
                Button("Sí, cancelar", role: .destructive) {
                    if let outing = outingToCancel {
                        Task {
                            await viewModel.cancelAcceptedOuting(outing)
                            outingToCancel = nil
                        }
                    }
                }
            } message: {
                if let outing = outingToCancel {
                    Text("¿Estás seguro de que deseas cancelar tu asistencia a '\(outing.nombreRuta)'?")
                }
            }
            // Alerta de confirmación para eliminar del historial
            .alert("Eliminar del Historial", isPresented: Binding(
                get: { outingToDeleteFromHistory != nil },
                set: { if !$0 { outingToDeleteFromHistory = nil } }
            )) {
                Button("Cancelar", role: .cancel) {
                    outingToDeleteFromHistory = nil
                }
                Button("Eliminar", role: .destructive) {
                    if let outing = outingToDeleteFromHistory {
                        Task {
                            await viewModel.deleteHistoryOuting(outing)
                            outingToDeleteFromHistory = nil
                        }
                    }
                }
            } message: {
                if let outing = outingToDeleteFromHistory {
                    Text("¿Eliminar definitivamente el registro de la salida concluida '\(outing.nombreRuta)' de este dispositivo?")
                }
            }
        }
    }
    
    // MARK: - Barra de 3 Pestañas
    private var tabsHeader: some View {
        HStack(spacing: 6) {
            tabButton(title: "Disponibles", index: 0, badgeCount: viewModel.availableOutings.count)
            tabButton(title: "Mis Salidas", index: 1, badgeCount: viewModel.pendingAcceptedOutings.count)
            tabButton(title: "Historial", index: 2, badgeCount: viewModel.historyAcceptedOutings.count)
        }
        .padding(4)
        .background(AppTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
    
    private func tabButton(title: String, index: Int, badgeCount: Int) -> some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectedTab = index
            }
        } label: {
            HStack(spacing: 5) {
                Text(title)
                    .font(.system(size: 13, weight: selectedTab == index ? .bold : .medium))
                
                if badgeCount > 0 {
                    Text("\(badgeCount)")
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(selectedTab == index ? Color.black.opacity(0.3) : AppTheme.accent.opacity(0.2))
                        .clipShape(Capsule())
                }
            }
            .foregroundStyle(selectedTab == index ? Color.white : AppTheme.textSecondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(selectedTab == index ? AppTheme.accent : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }
    
    // MARK: - Barra de Búsqueda y Refresco
    private var searchAndActionBar: some View {
        HStack(spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(AppTheme.textSecondary)
                
                TextField("Buscar ruta, evento o creador...", text: $searchQuery)
                    .font(.system(size: 14))
                    .foregroundStyle(AppTheme.textPrimary)
                
                if !searchQuery.isEmpty {
                    Button {
                        searchQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(AppTheme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            
            Button {
                Task {
                    await viewModel.fetchAvailableOutings()
                }
            } label: {
                Image(systemName: "arrow.clockwise")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(viewModel.isLoadingOutings ? AppTheme.textSecondary : AppTheme.accent)
                    .rotationEffect(.degrees(viewModel.isLoadingOutings ? 360 : 0))
                    .animation(viewModel.isLoadingOutings ? .linear(duration: 1).repeatForever(autoreverses: false) : .default, value: viewModel.isLoadingOutings)
                    .padding(10)
                    .background(AppTheme.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .disabled(viewModel.isLoadingOutings)
        }
    }
    
    // MARK: - Pestaña 0: Disponibles
    private var disponiblesTabView: some View {
        Group {
            if viewModel.isLoadingOutings && viewModel.availableOutings.isEmpty {
                VStack(spacing: 16) {
                    ProgressView()
                        .tint(AppTheme.accent)
                    Text("Cargando salidas y eventos disponibles...")
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                }
            } else {
                let filtered = viewModel.availableOutings.filter { inv in
                    if searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return true }
                    let query = searchQuery.lowercased()
                    let name = inv.nombreRuta.lowercased()
                    let inst = (inv.instrucciones ?? "").lowercased()
                    let group = (inv.idAgrupacion ?? "").lowercased()
                    return name.contains(query) || inst.contains(query) || group.contains(query)
                }
                
                if filtered.isEmpty {
                    VStack(spacing: 12) {
                        Image(systemName: "calendar.badge.exclamationmark")
                            .font(.system(size: 48))
                            .foregroundStyle(AppTheme.textSecondary.opacity(0.6))
                        Text("No hay salidas disponibles")
                            .font(.headline)
                            .foregroundStyle(AppTheme.textPrimary)
                        Text("No se encontraron salidas futuras o eventos privados que coincidan con tus criterios.")
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(filtered) { outing in
                                OutingAvailableCard(outing: outing) {
                                    selectedOutingForDetail = outing
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 24)
                    }
                    .refreshable {
                        await viewModel.fetchAvailableOutings()
                    }
                }
            }
        }
    }
    
    // MARK: - Pestaña 1: Mis Salidas (Pendientes)
    private var misSalidasTabView: some View {
        Group {
            let pending = viewModel.pendingAcceptedOutings.filter { inv in
                if searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return true }
                let query = searchQuery.lowercased()
                let name = inv.nombreRuta.lowercased()
                let inst = (inv.instrucciones ?? "").lowercased()
                return name.contains(query) || inst.contains(query)
            }
            
            if pending.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "figure.hiking")
                        .font(.system(size: 48))
                        .foregroundStyle(AppTheme.textSecondary.opacity(0.6))
                    Text("No tienes salidas pendientes")
                        .font(.headline)
                        .foregroundStyle(AppTheme.textPrimary)
                    Text("Explora las salidas disponibles para unirte a una ruta o evento.")
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(pending) { outing in
                            MyOutingPendingCard(
                                outing: outing,
                                onRegisterCooperation: {
                                    outingForCooperation = outing
                                },
                                onCancelAttendance: {
                                    outingToCancel = outing
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
                .refreshable {
                    await viewModel.fetchAvailableOutings()
                }
            }
        }
    }
    
    // MARK: - Pestaña 2: Historial (Concluidas)
    private var historialTabView: some View {
        Group {
            let history = viewModel.historyAcceptedOutings.filter { inv in
                if searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return true }
                let query = searchQuery.lowercased()
                let name = inv.nombreRuta.lowercased()
                let inst = (inv.instrucciones ?? "").lowercased()
                return name.contains(query) || inst.contains(query)
            }
            
            if history.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 48))
                        .foregroundStyle(AppTheme.textSecondary.opacity(0.6))
                    Text("Historial vacío")
                        .font(.headline)
                        .foregroundStyle(AppTheme.textPrimary)
                    Text("Las salidas y eventos cuya fecha ya haya pasado se mostrarán en esta sección.")
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(history) { outing in
                            HistoryOutingCard(
                                outing: outing,
                                onDeleteHistory: {
                                    outingToDeleteFromHistory = outing
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
            }
        }
    }
    
    // MARK: - Overlay de Carga
    private func loadingOverlay(message: String) -> some View {
        ZStack {
            Color.black.opacity(0.65)
                .ignoresSafeArea()
            
            VStack(spacing: 16) {
                ProgressView()
                    .tint(AppTheme.accent)
                    .scaleEffect(1.3)
                
                Text(message)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }
            .padding(24)
            .background(AppTheme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .shadow(radius: 12)
        }
    }
}

// MARK: - Tarjeta de Salida Disponible (Pestaña 0)
private struct OutingAvailableCard: View {
    let outing: OutingInvitation
    let onSelect: () -> Void
    
    var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: 10) {
                // Cabecera: Tipo de ruta y badge de privacidad
                HStack {
                    HStack(spacing: 4) {
                        Image(systemName: outing.tipoRuta == "E" ? "sparkles" : "figure.hiking")
                        Text(outing.tipoRuta == "E" ? "Evento" : "Ruta")
                    }
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(outing.tipoRuta == "E" ? Color.purple : AppTheme.accent)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background((outing.tipoRuta == "E" ? Color.purple : AppTheme.accent).opacity(0.15))
                    .clipShape(Capsule())
                    
                    if outing.tipoRuta == "E" {
                        if outing.esPublico {
                            Text("Público")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(Color.green)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Color.green.opacity(0.15))
                                .clipShape(Capsule())
                        } else {
                            Text("Grupo: \(outing.idAgrupacion ?? "Privado")")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(Color.orange)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Color.orange.opacity(0.15))
                                .clipShape(Capsule())
                        }
                    }
                    
                    Spacer()
                    
                    if let dist = outing.distanciaKmAlUsuario {
                        HStack(spacing: 3) {
                            Image(systemName: "location.fill")
                            Text(String(format: "%.1f km", dist))
                        }
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(AppTheme.textSecondary)
                    }
                }
                
                // Nombre de la ruta o evento
                Text(outing.nombreRuta)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(AppTheme.textPrimary)
                    .lineLimit(2)
                
                // Fecha de salida
                HStack(spacing: 6) {
                    Image(systemName: "calendar")
                        .foregroundStyle(AppTheme.accent)
                    Text(OutingDateUtils.formatDisplay(outing.fechaHoraSalida))
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(AppTheme.textPrimary)
                }
                
                // Punto de reunión
                if !outing.puntoReunion.isEmpty {
                    HStack(spacing: 6) {
                        Image(systemName: "mappin.circle.fill")
                            .foregroundStyle(Color.red.opacity(0.8))
                        Text(outing.puntoReunion)
                            .font(.system(size: 12))
                            .foregroundStyle(AppTheme.textSecondary)
                            .lineLimit(1)
                    }
                }
                
                Divider()
                    .overlay(Color.white.opacity(0.08))
                
                // Cooperación y acción
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Cooperación sugerida")
                            .font(.system(size: 10))
                            .foregroundStyle(AppTheme.textSecondary)
                        Text(outing.cooperacionMinima > 0 ? String(format: "$%.2f MXN", outing.cooperacionMinima) : "Gratuita")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(AppTheme.accent)
                    }
                    
                    Spacer()
                    
                    HStack(spacing: 4) {
                        Text("Ver Detalle")
                            .font(.system(size: 13, weight: .semibold))
                        Image(systemName: "chevron.right")
                            .font(.system(size: 11, weight: .bold))
                    }
                    .foregroundStyle(AppTheme.accent)
                }
            }
            .padding(14)
            .background(AppTheme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(AppTheme.accent.opacity(0.18), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Tarjeta de Salida Pendiente (Pestaña 1)
private struct MyOutingPendingCard: View {
    let outing: OutingInvitation
    let onRegisterCooperation: () -> Void
    let onCancelAttendance: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                HStack(spacing: 4) {
                    Circle()
                        .fill(Color.orange)
                        .frame(width: 8, height: 8)
                    Text("Pendiente por Realizar")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.orange)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(Color.orange.opacity(0.15))
                .clipShape(Capsule())
                
                Spacer()
                
                Text(outing.tipoRuta == "E" ? "Evento" : "Ruta")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(AppTheme.textSecondary)
            }
            
            Text(outing.nombreRuta)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(AppTheme.textPrimary)
            
            HStack(spacing: 6) {
                Image(systemName: "calendar")
                    .foregroundStyle(AppTheme.accent)
                Text(OutingDateUtils.formatDisplay(outing.fechaHoraSalida))
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(AppTheme.textPrimary)
            }
            
            if !outing.puntoReunion.isEmpty {
                HStack(spacing: 6) {
                    Image(systemName: "mappin.circle.fill")
                        .foregroundStyle(Color.red.opacity(0.8))
                    Text(outing.puntoReunion)
                        .font(.system(size: 12))
                        .foregroundStyle(AppTheme.textSecondary)
                }
            }
            
            // Estado de cooperación
            HStack {
                if let coop = outing.cooperacionRealizada, coop > 0 {
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(Color.green)
                        Text(String(format: "Aportado: $%.2f", coop))
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(Color.green)
                    }
                } else if outing.cooperacionMinima > 0 {
                    Text(String(format: "Cooperación sugerida: $%.2f", outing.cooperacionMinima))
                        .font(.system(size: 12))
                        .foregroundStyle(AppTheme.textSecondary)
                }
                
                Spacer()
                
                if let maps = outing.linkGoogleMaps, let url = URL(string: maps) {
                    Link(destination: url) {
                        HStack(spacing: 4) {
                            Image(systemName: "map")
                            Text("Punto")
                        }
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(AppTheme.accent)
                    }
                }
            }
            
            Divider()
                .overlay(Color.white.opacity(0.08))
            
            // Botones de acción
            HStack(spacing: 12) {
                Button(action: onRegisterCooperation) {
                    HStack(spacing: 4) {
                        Image(systemName: "dollarsign.circle.fill")
                        Text("Cooperación")
                    }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(AppTheme.accent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(AppTheme.accent.opacity(0.15))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                
                Button(action: onCancelAttendance) {
                    HStack(spacing: 4) {
                        Image(systemName: "xmark.circle")
                        Text("Cancelar")
                    }
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.red)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(Color.red.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
        }
        .padding(14)
        .background(AppTheme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.orange.opacity(0.25), lineWidth: 1)
        )
    }
}

// MARK: - Tarjeta de Salida del Historial (Pestaña 2)
private struct HistoryOutingCard: View {
    let outing: OutingInvitation
    let onDeleteHistory: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                // Distintivo "Concluida"
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(Color.green)
                    Text("Concluida")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Color.green)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(Color.green.opacity(0.15))
                .clipShape(Capsule())
                
                Spacer()
                
                // Botón para eliminar el registro del historial local
                Button(action: onDeleteHistory) {
                    HStack(spacing: 4) {
                        Image(systemName: "trash")
                        Text("Eliminar")
                    }
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color.red.opacity(0.85))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.red.opacity(0.1))
                    .clipShape(Capsule())
                }
            }
            
            Text(outing.nombreRuta)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(AppTheme.textPrimary)
            
            HStack(spacing: 6) {
                Image(systemName: "calendar")
                    .foregroundStyle(AppTheme.textSecondary)
                Text(OutingDateUtils.formatDisplay(outing.fechaHoraSalida))
                    .font(.system(size: 13))
                    .foregroundStyle(AppTheme.textSecondary)
            }
            
            if let coop = outing.cooperacionRealizada, coop > 0 {
                HStack(spacing: 4) {
                    Image(systemName: "dollarsign.circle")
                    Text(String(format: "Cooperación realizada: $%.2f", coop))
                }
                .font(.system(size: 12))
                .foregroundStyle(AppTheme.textSecondary)
            }
        }
        .padding(14)
        .background(AppTheme.cardBackground.opacity(0.7))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.white.opacity(0.06), lineWidth: 1)
        )
    }
}

// MARK: - Sheet de Detalle de Salida Disponible
private struct OutingDetailSheet: View {
    let outing: OutingInvitation
    @Bindable var viewModel: MainViewModel
    let onDismiss: () -> Void
    
    @State private var isAccepting: Bool = false
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Badge tipo y privacidad
                    HStack {
                        Text(outing.tipoRuta == "E" ? "Evento de Senderismo" : "Salida a Ruta")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(AppTheme.accent)
                        
                        if outing.tipoRuta == "E" {
                            Text(outing.esPublico ? "Público" : "Privado (\(outing.idAgrupacion ?? ""))")
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundStyle(outing.esPublico ? Color.green : Color.orange)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background((outing.esPublico ? Color.green : Color.orange).opacity(0.15))
                                .clipShape(Capsule())
                        }
                    }
                    
                    Text(outing.nombreRuta)
                        .font(.title2.bold())
                        .foregroundStyle(AppTheme.textPrimary)
                    
                    // Tarjeta de Datos Clave
                    VStack(alignment: .leading, spacing: 12) {
                        detailRow(icon: "calendar", title: "Fecha y Hora de Salida", value: OutingDateUtils.formatDisplay(outing.fechaHoraSalida))
                        
                        if !outing.puntoReunion.isEmpty {
                            detailRow(icon: "mappin.circle.fill", title: "Punto de Reunión", value: outing.puntoReunion)
                        }
                        
                        if let limit = outing.fechaLimiteRegistro, !limit.isEmpty {
                            detailRow(icon: "clock.badge.exclamationmark", title: "Límite de Registro", value: OutingDateUtils.formatDisplay(limit))
                        }
                        
                        if let dist = outing.distanciaGps, dist > 0 {
                            detailRow(icon: "arrow.triangle.swap", title: "Distancia Estimada", value: String(format: "%.2f km", dist / 1000.0))
                        }
                        
                        detailRow(
                            icon: "dollarsign.circle",
                            title: "Cooperación",
                            value: outing.cooperacionMinima > 0 ? String(format: "$%.2f MXN", outing.cooperacionMinima) : "Gratuita"
                        )
                    }
                    .padding(14)
                    .background(AppTheme.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    
                    // Instrucciones
                    if let inst = outing.instrucciones, !inst.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Instrucciones y Recomendaciones")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(AppTheme.textPrimary)
                            Text(inst)
                                .font(.system(size: 13))
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(AppTheme.cardBackground)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    
                    // Botón de Google Maps si existe
                    if let maps = outing.linkGoogleMaps, let url = URL(string: maps) {
                        Link(destination: url) {
                            HStack {
                                Image(systemName: "map.fill")
                                Text("Abrir Punto de Reunión en Google Maps")
                                    .fontWeight(.semibold)
                            }
                            .font(.system(size: 14))
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(Color.blue)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                    }
                    
                    // Botón Principal: Aceptar Salida
                    Button {
                        isAccepting = true
                        Task {
                            await viewModel.acceptOuting(outing)
                            isAccepting = false
                            onDismiss()
                        }
                    } label: {
                        HStack {
                            if isAccepting {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Image(systemName: "checkmark.circle.fill")
                                Text("Aceptar Salida y Registrarme")
                                    .fontWeight(.bold)
                            }
                        }
                        .font(.system(size: 15))
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(AppTheme.accent)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .disabled(isAccepting)
                }
                .padding(20)
            }
            .navigationTitle("Detalle de Salida")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cerrar") { onDismiss() }
                }
            }
        }
    }
    
    private func detailRow(icon: String, title: String, value: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 15))
                .foregroundStyle(AppTheme.accent)
                .frame(width: 20)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 11))
                    .foregroundStyle(AppTheme.textSecondary)
                Text(value)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(AppTheme.textPrimary)
            }
        }
    }
}

// MARK: - Sheet de Registro de Cooperación
private struct CooperationSheet: View {
    let outing: OutingInvitation
    @Bindable var viewModel: MainViewModel
    let onDismiss: () -> Void
    
    @State private var amountString: String = ""
    @State private var isSubmitting: Bool = false
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Text(outing.nombreRuta)
                    .font(.headline)
                    .foregroundStyle(AppTheme.textPrimary)
                    .multilineTextAlignment(.center)
                
                Text("Monto sugerido: \(outing.cooperacionMinima > 0 ? String(format: "$%.2f MXN", outing.cooperacionMinima) : "Voluntaria")")
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)
                
                TextField("Monto aportado ($ MXN)", text: $amountString)
                    #if os(iOS)
                    .keyboardType(.decimalPad)
                    #endif
                    .font(.system(size: 18, weight: .bold))
                    .padding()
                    .background(AppTheme.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                
                Spacer()
                
                Button {
                    let amount = Double(amountString) ?? outing.cooperacionMinima
                    isSubmitting = true
                    Task {
                        await viewModel.registerCooperation(for: outing, amount: amount)
                        isSubmitting = false
                        onDismiss()
                    }
                } label: {
                    HStack {
                        if isSubmitting {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Text("Guardar Cooperación")
                                .fontWeight(.bold)
                        }
                    }
                    .font(.system(size: 15))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(AppTheme.accent)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .disabled(isSubmitting)
            }
            .padding(24)
            .navigationTitle("Registrar Cooperación")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { onDismiss() }
                }
            }
        }
    }
}
