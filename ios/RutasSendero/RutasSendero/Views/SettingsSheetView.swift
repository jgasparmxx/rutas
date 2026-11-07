//
//  SettingsSheetView.swift
//  RutasSendero
//
//  Diálogo de Configuración de Parámetros de Senderismo (1:1 Android SettingsDialog en Components.kt:2345-2591).
//

import SwiftUI

public struct SettingsSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var distanceText: String
    @State private var arrivalToleranceText: String
    @State private var tapDistanceLimitText: String
    @State private var emergencyTimeoutText: String
    @State private var rangoDiasSalidasText: String
    @State private var rangoKmSalidasText: String
    @State private var enableBeeps: Bool
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
        _distanceText = State(initialValue: "\(viewModel.alertDistanceMeters)")
        _arrivalToleranceText = State(initialValue: "\(Int(viewModel.arrivalToleranceMeters))")
        _tapDistanceLimitText = State(initialValue: "\(viewModel.tapDistanceLimitMeters)")
        _emergencyTimeoutText = State(initialValue: "\(viewModel.emergencyTimeoutMinutes)")
        _rangoDiasSalidasText = State(initialValue: "\(viewModel.rangoDiasBusquedaSalidas)")
        _rangoKmSalidasText = State(initialValue: "\(viewModel.rangoKmBusquedaSalidas)")
        _enableBeeps = State(initialValue: viewModel.enableDopplerBeeps)
    }
    
    public var body: some View {
        ZStack {
            Color.black.opacity(0.6)
                .ignoresSafeArea()
                .onTapGesture {
                    dismiss()
                }
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 0) {
                    // Cabecera 1:1 Android SettingsDialog
                    Text("Ajustes de Senderismo")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(AppTheme.textPrimary)
                        .frame(maxWidth: .infinity, alignment: .center)
                    
                    Spacer().frame(height: 2)
                    
                    Text("(Todas las medidas en metros)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(AppTheme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                    
                    Spacer().frame(height: 16)
                    
                    // Fila 1: Aviso cercanía y Aceptación punto
                    HStack(spacing: 16) {
                        parameterField(
                            title: "Aviso Cerc.",
                            subtitle: "Metros para alertar",
                            text: $distanceText
                        )
                        parameterField(
                            title: "Acepta Pto.",
                            subtitle: "Radio para marcar (m)",
                            text: $arrivalToleranceText
                        )
                    }
                    .padding(.vertical, 4)
                    
                    Spacer().frame(height: 8)
                    
                    // Fila 2: Tap manual y Timeout 911
                    HStack(spacing: 16) {
                        parameterField(
                            title: "Tap Manual",
                            subtitle: "Límite registro (m)",
                            text: $tapDistanceLimitText
                        )
                        parameterField(
                            title: "Espera 911",
                            subtitle: "Reactivar SOS (min)",
                            text: $emergencyTimeoutText
                        )
                    }
                    .padding(.vertical, 4)
                    
                    Spacer().frame(height: 12)
                    
                    Rectangle()
                        .fill(AppTheme.cardMuted)
                        .frame(height: 1)
                        .padding(.vertical, 4)
                    
                    Text("Búsquedas Senderos")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(AppTheme.accent)
                    
                    Spacer().frame(height: 8)
                    
                    // Fila 3: Rango búsqueda y Rango cercanía
                    HStack(spacing: 16) {
                        parameterField(
                            title: "Reciente (d)",
                            subtitle: "Días atrás a buscar",
                            text: $rangoDiasSalidasText
                        )
                        parameterField(
                            title: "Cerca (km)",
                            subtitle: "Radio de búsqueda",
                            text: $rangoKmSalidasText
                        )
                    }
                    .padding(.vertical, 4)
                    
                    Spacer().frame(height: 16)
                    
                    // Activar Pitidos Doppler (Fila Completa)
                    Toggle(isOn: $enableBeeps) {
                        Text("Activar Pitidos Doppler")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(AppTheme.textPrimary)
                    }
                    .tint(AppTheme.accent)
                    
                    Spacer().frame(height: 22)
                    
                    // Botones inferiores Cancelar / Guardar (1:1 Android)
                    HStack(spacing: 12) {
                        Spacer()
                        
                        Button {
                            dismiss()
                        } label: {
                            Text("Cancelar")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(AppTheme.textSecondary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.plain)
                        
                        Button {
                            let distInt = Int(distanceText) ?? 50
                            let toleranceInt = Int(arrivalToleranceText) ?? 25
                            let tapLimitInt = Int(tapDistanceLimitText) ?? 15
                            let emergencyTimeoutInt = Int(emergencyTimeoutText) ?? 5
                            let diasInt = Int(rangoDiasSalidasText) ?? 20
                            let kmInt = Int(rangoKmSalidasText) ?? 30
                            
                            viewModel.saveHikingSettings(
                                alertDistance: distInt,
                                arrivalTolerance: toleranceInt,
                                tapDistanceLimit: tapLimitInt,
                                emergencyTimeout: emergencyTimeoutInt,
                                rangoDiasSalidas: diasInt,
                                rangoKmSalidas: kmInt,
                                enableBeeps: enableBeeps
                            )
                            dismiss()
                        } label: {
                            Text("Guardar")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 22)
                                .padding(.vertical, 10)
                                .background(AppTheme.primary)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(20)
                .background(AppTheme.cardBackground)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(AppTheme.accent.opacity(0.3), lineWidth: 1)
                )
                .shadow(color: Color.black.opacity(0.45), radius: 16, y: 8)
                .padding(.horizontal, 16)
                .padding(.vertical, 24)
            }
        }
        .presentationBackground(.clear)
    }
    
    @ViewBuilder
    private func parameterField(title: String, subtitle: String, text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(AppTheme.textPrimary)
            
            Text(subtitle)
                .font(.system(size: 9))
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(1)
                .padding(.bottom, 2)
            
            TextField("", text: Binding(
                get: { text.wrappedValue },
                set: { newVal in
                    text.wrappedValue = newVal.filter { $0.isNumber }
                }
            ))
            .keyboardType(.numberPad)
            .multilineTextAlignment(.center)
            .font(.system(size: 14, weight: .semibold, design: .rounded))
            .foregroundStyle(AppTheme.textPrimary)
            .padding(.vertical, 8)
            .padding(.horizontal, 8)
            .background(AppTheme.darkBackground.opacity(0.55))
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(
                VStack {
                    Spacer()
                    Rectangle()
                        .fill(AppTheme.accent.opacity(0.75))
                        .frame(height: 1.5)
                }
                .clipShape(RoundedRectangle(cornerRadius: 8))
            )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

