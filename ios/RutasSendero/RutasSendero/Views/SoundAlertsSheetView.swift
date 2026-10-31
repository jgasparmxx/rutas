//
//  SoundAlertsSheetView.swift
//  RutasSendero
//
//  Herramientas de ayuda sonora y emergencia en senderismo (Silbatos y Alertas).
//

import SwiftUI
import AudioToolbox
import AVFoundation

public struct SoundAlertsSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var isPlayingWhistle: Bool = false
    @State private var isSosPinging: Bool = false
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Ayudas y Alertas Sonoras")
                            .font(.headline)
                            .foregroundStyle(AppTheme.textPrimary)
                        Text("Herramientas audibles para orientarte o emitir llamadas de auxilio en el sendero sin cobertura móvil.")
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                    .padding(.vertical, 4)
                }
                
                Section("Silbato de Emergencia") {
                    HStack(spacing: 14) {
                        ZStack {
                            Circle()
                                .fill(Color(red: 255/255, green: 152/255, blue: 0/255).opacity(0.2))
                                .frame(width: 44, height: 44)
                            Image(systemName: isPlayingWhistle ? "speaker.wave.3.fill" : "speaker.wave.1.fill")
                                .font(.system(size: 20))
                                .foregroundStyle(Color(red: 255/255, green: 152/255, blue: 0/255))
                        }
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Silbato Continuo de Alerta")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(AppTheme.textPrimary)
                            Text("Emite un sonido penetrante para búsqueda y rescate.")
                                .font(.caption)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                        
                        Spacer()
                        
                        Button {
                            toggleWhistle()
                        } label: {
                            Text(isPlayingWhistle ? "Detener" : "Sonar")
                                .font(.subheadline).bold()
                                .foregroundStyle(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(isPlayingWhistle ? AppTheme.alert : Color(red: 255/255, green: 152/255, blue: 0/255))
                                .clipShape(Capsule())
                        }
                    }
                    .padding(.vertical, 4)
                }
                
                Section("Pitidos de Código SOS") {
                    HStack(spacing: 14) {
                        ZStack {
                            Circle()
                                .fill(AppTheme.alert.opacity(0.2))
                                .frame(width: 44, height: 44)
                            Image(systemName: "dot.radiowaves.left.and.right")
                                .font(.system(size: 20))
                                .foregroundStyle(AppTheme.alert)
                        }
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Patrón SOS (··· --- ···)")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(AppTheme.textPrimary)
                            Text("Emite pulsos sonoros internacionales de auxilio.")
                                .font(.caption)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                        
                        Spacer()
                        
                        Button {
                            toggleSos()
                        } label: {
                            Text(isSosPinging ? "Detener" : "Activar")
                                .font(.subheadline).bold()
                                .foregroundStyle(.white)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .background(isSosPinging ? AppTheme.alert : AppTheme.primary)
                                .clipShape(Capsule())
                        }
                    }
                    .padding(.vertical, 4)
                }
                
                Section("Guía por Voz (TTS)") {
                    HStack(spacing: 14) {
                        Image(systemName: "waveform.circle.fill")
                            .font(.system(size: 32))
                            .foregroundStyle(AppTheme.accentCyan)
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Orientación de Sendero")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(AppTheme.textPrimary)
                            Text("Anuncios periódicos de distancia al siguiente punto y rumbo.")
                                .font(.caption)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                }
            }
            .navigationTitle("Herramientas Sonoras")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Cerrar") {
                        dismiss()
                    }
                }
            }
        }
    }
    
    private func toggleWhistle() {
        isPlayingWhistle.toggle()
        if isPlayingWhistle {
            AudioServicesPlaySystemSound(1005) // Tono estándar del sistema
        }
    }
    
    private func toggleSos() {
        isSosPinging.toggle()
        if isSosPinging {
            AudioServicesPlaySystemSound(kSystemSoundID_Vibrate)
        }
    }
}
