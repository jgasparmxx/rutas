//
//  QuickDictateSheetView.swift
//  RutasSendero
//
//  Hoja modal rápida para dictar notas de voz al último punto registrado (1:1 Android)
//

import SwiftUI

public struct QuickDictateSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var voiceService = VoiceDictationService.shared
    @State private var dictatedText: String = ""
    @State private var isShowingAlert: Bool = false
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    private var lastPoint: RoutePoint? {
        viewModel.recordedPoints.last
    }
    
    public var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                if let pt = lastPoint {
                    VStack(spacing: 6) {
                        Text("Punto #\(pt.id)")
                            .font(.system(size: 20, weight: .heavy))
                            .foregroundStyle(AppTheme.textPrimary)
                        
                        Text(pt.descriptionText)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(AppTheme.accent)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 16)
                    }
                    .padding(.top, 10)
                    
                    // Área de transcripción
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Nota de voz a agregar:")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(AppTheme.textSecondary)
                        
                        TextField("Hable o escriba aquí...", text: $dictatedText, axis: .vertical)
                            .lineLimit(3...5)
                            .padding(12)
                            .background(AppTheme.cardMuted)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .padding(.horizontal, 16)
                    
                    // Botón de Micrófono
                    Button {
                        if voiceService.isRecording {
                            voiceService.stopDictation()
                        } else {
                            Task {
                                let ok = await voiceService.requestPermissions()
                                if ok {
                                    voiceService.startDictation { transcribed in
                                        dictatedText = transcribed
                                    }
                                } else {
                                    isShowingAlert = true
                                }
                            }
                        }
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: voiceService.isRecording ? "stop.circle.fill" : "mic.fill")
                                .font(.system(size: 20, weight: .bold))
                            Text(voiceService.isRecording ? "DETENER DICTADO" : "INICIAR DICTADO POR VOZ")
                                .font(.system(size: 14, weight: .heavy))
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 24)
                        .padding(.vertical, 14)
                        .background(voiceService.isRecording ? AppTheme.alert : Color(red: 76/255, green: 175/255, blue: 80/255))
                        .clipShape(Capsule())
                        .shadow(color: (voiceService.isRecording ? AppTheme.alert : Color(red: 76/255, green: 175/255, blue: 80/255)).opacity(0.4), radius: 8, y: 3)
                    }
                    .buttonStyle(.plain)
                    
                    if voiceService.isRecording {
                        Text("Escuchando voz... hable claro y pausado.")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(AppTheme.alert)
                    }
                    
                    Spacer()
                } else {
                    Text("No hay puntos registrados en esta ruta.")
                        .foregroundStyle(AppTheme.textSecondary)
                }
            }
            .padding()
            .navigationTitle("Dictar al Punto")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") {
                        if voiceService.isRecording {
                            voiceService.stopDictation()
                        }
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") {
                        if voiceService.isRecording {
                            voiceService.stopDictation()
                        }
                        if !dictatedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            viewModel.appendVoiceToLastPoint(text: dictatedText.trimmingCharacters(in: .whitespacesAndNewlines))
                        }
                        dismiss()
                    }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(AppTheme.accent)
                    .disabled(dictatedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .alert("Permiso de Micrófono", isPresented: $isShowingAlert) {
                Button("Entendido", role: .cancel) {}
            } message: {
                Text("Permita el acceso al micrófono en Configuración > RutasSendero para dictar.")
            }
            .onDisappear {
                if voiceService.isRecording {
                    voiceService.stopDictation()
                }
            }
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }
}
