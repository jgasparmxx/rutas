//
//  NewRouteSheetView.swift
//  RutasSendero
//
//  Hoja modal para iniciar una nueva grabación de sendero.
//

import SwiftUI

public struct NewRouteSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var routeName: String = ""
    @State private var routeDescription: String = ""
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Nombre de la ruta (ej. Sendero Los Encinos)", text: $routeName)
                        .font(.system(size: 15))
                    
                    TextField("Descripción u objetivo (opcional)", text: $routeDescription, axis: .vertical)
                        .lineLimit(3...5)
                        .font(.system(size: 14))
                } header: {
                    Text("Detalles de la Aventura")
                }
                
                Section {
                    // Sugerencias rápidas
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(["Cerro Grande", "Cascada El Salto", "Mirador del Valle", "Sendero Panorámico"], id: \.self) { suggestion in
                                Button {
                                    routeName = suggestion
                                } label: {
                                    Text(suggestion)
                                        .font(.system(size: 12, weight: .medium))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(AppTheme.cardMuted)
                                        .foregroundStyle(AppTheme.accent)
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                    .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))
                } header: {
                    Text("Sugerencias Rápidas")
                }
                
                Section {
                    HStack {
                        Image(systemName: "location.fill")
                            .foregroundStyle(AppTheme.accent)
                        Text("GPS Listo:")
                        Spacer()
                        Text(viewModel.locationTracker.isGpsReady ? "Sí (±\(Int(viewModel.locationTracker.horizontalAccuracy))m)" : "Buscando señal...")
                            .foregroundStyle(viewModel.locationTracker.isGpsReady ? AppTheme.success : AppTheme.warningOrange)
                    }
                    .font(.system(size: 13))
                }
            }
            .navigationTitle("Nueva Ruta")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Comenzar") {
                        viewModel.startRecording(name: routeName, description: routeDescription)
                        dismiss()
                    }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(AppTheme.accent)
                }
            }
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }
}
