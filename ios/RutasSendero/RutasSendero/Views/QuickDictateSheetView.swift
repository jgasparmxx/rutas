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
    @State private var hasActivatedDictation: Bool = false
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    private var lastPoint: RoutePoint? {
        viewModel.recordedPoints.last
    }
    
    private func saveDictationAndClose() {
        if voiceService.isRecording {
            voiceService.stopDictation()
        }
        let trimmed = dictatedText.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty {
            viewModel.appendVoiceToLastPoint(text: trimmed)
        }
        dismiss()
    }
    
    private func cancelDictationAndClose() {
        if voiceService.isRecording {
            voiceService.stopDictation()
        }
        dismiss()
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
                    
                    // Al activar el dictado con el botón verde, se oculta para que solo queden Guardar y Cancelar
                    if !hasActivatedDictation {
                        Button {
                            Task {
                                let ok = await voiceService.requestPermissions()
                                if ok {
                                    hasActivatedDictation = true
                                    voiceService.startDictation { transcribed in
                                        dictatedText = transcribed
                                    }
                                } else {
                                    isShowingAlert = true
                                }
                            }
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "mic.fill")
                                    .font(.system(size: 20, weight: .bold))
                                Text("INICIAR DICTADO POR VOZ")
                                    .font(.system(size: 14, weight: .heavy))
                            }
                            .foregroundStyle(.white)
                            .padding(.horizontal, 24)
                            .padding(.vertical, 14)
                            .background(Color(red: 76/255, green: 175/255, blue: 80/255))
                            .clipShape(Capsule())
                            .shadow(color: Color(red: 76/255, green: 175/255, blue: 80/255).opacity(0.4), radius: 8, y: 3)
                        }
                        .buttonStyle(.plain)
                    } else {
                        VStack(spacing: 14) {
                            if voiceService.isRecording {
                                HStack(spacing: 8) {
                                    Circle()
                                        .fill(AppTheme.alert)
                                        .frame(width: 9, height: 9)
                                    Text("Escuchando voz... hable claro y pausado.")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(AppTheme.alert)
                                }
                            }
                            
                            HStack(spacing: 12) {
                                Button {
                                    cancelDictationAndClose()
                                } label: {
                                    HStack(spacing: 6) {
                                        Image(systemName: "xmark.circle.fill")
                                            .font(.system(size: 16, weight: .bold))
                                        Text("Cancelar")
                                            .font(.system(size: 14, weight: .bold))
                                    }
                                    .foregroundStyle(AppTheme.textSecondary)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 13)
                                    .background(AppTheme.cardMuted)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                }
                                .buttonStyle(.plain)
                                
                                Button {
                                    saveDictationAndClose()
                                } label: {
                                    HStack(spacing: 6) {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(.system(size: 16, weight: .bold))
                                        Text("Guardar")
                                            .font(.system(size: 14, weight: .heavy))
                                    }
                                    .foregroundStyle(Color(red: 15/255, green: 23/255, blue: 42/255))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 13)
                                    .background(dictatedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? AppTheme.accent.opacity(0.35) : AppTheme.accent)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                }
                                .buttonStyle(.plain)
                                .disabled(dictatedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                            }
                            .padding(.horizontal, 16)
                        }
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
                        cancelDictationAndClose()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") {
                        saveDictationAndClose()
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
