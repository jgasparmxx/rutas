//
//  FooterBarView.swift
//  RutasSendero
//
//  Barra de navegación inferior (Dock) 1:1 con la versión de Android.
//  Permite acceder al Mapa bajo demanda, encender/apagar el Faro BLE (Beacon),
//  monitorear senderistas cercanos y activar ayudas sonoras.
//

import SwiftUI

public struct FooterBarView: View {
    @Bindable var viewModel: MainViewModel
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        HStack(spacing: 0) {
            let isRecording = (viewModel.recordingState != .stopped || viewModel.activeRole == .registrar)
            
            // 1. Ver Mapa (Solo disponible en modo Guía y Visor, 1:1 Android)
            Button {
                if isRecording {
                    viewModel.saveStatusMessage = "El mapa no está disponible en el modo Registrar (trazar). Solo está disponible en modo Guía y Visor."
                } else {
                    viewModel.showMapScreen = true
                }
            } label: {
                VStack(spacing: 3) {
                    Image(systemName: isRecording ? "record.circle.fill" : "map.fill")
                        .font(.system(size: 22, weight: .semibold))
                    Text(isRecording ? "Grabando" : "Mapa")
                        .font(.system(size: 10, weight: .bold))
                }
                .foregroundStyle(isRecording ? AppTheme.alert : mapButtonColor)
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            
            // 2. Faro / Transmisor BLE (Encender / Apagar Baliza de presencia)
            Button {
                viewModel.toggleBeacon()
            } label: {
                VStack(spacing: 3) {
                    ZStack {
                        Circle()
                            .fill(viewModel.isBeaconEmitting ? AppTheme.accentCyan.opacity(0.25) : Color.clear)
                            .frame(width: 36, height: 36)
                        
                        Image(systemName: viewModel.isBeaconEmitting ? "antenna.radiowaves.left.and.right" : "antenna.radiowaves.left.and.right.slash")
                            .font(.system(size: 20, weight: .semibold))
                    }
                    Text(viewModel.isBeaconEmitting ? "Faro ON" : "Faro OFF")
                        .font(.system(size: 10, weight: .bold))
                }
                .foregroundStyle(viewModel.isBeaconEmitting ? AppTheme.accentCyanDark : AppTheme.darkBackground.opacity(0.6))
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            
            // 3. Monitoreo / Buscar Balizas de Compañeros
            Button {
                viewModel.isShowingNearbyPeersSheet = true
            } label: {
                VStack(spacing: 3) {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "person.2.fill")
                            .font(.system(size: 20, weight: .semibold))
                            .frame(width: 36, height: 36)
                        
                        if !viewModel.nearbyPeers.isEmpty {
                            Text("\(viewModel.nearbyPeers.count)")
                                .font(.system(size: 9, weight: .heavy))
                                .foregroundStyle(AppTheme.darkBackground)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(AppTheme.accent)
                                .clipShape(Capsule())
                                .offset(x: 10, y: -2)
                        }
                    }
                    Text("Monitoreo")
                        .font(.system(size: 10, weight: .bold))
                }
                .foregroundStyle(AppTheme.primary)
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            
            // 4. Ayudas Sonoras (Silbato de Rescate / Pitidos de Orientación)
            Button {
                viewModel.isShowingSoundAlertsSheet = true
            } label: {
                VStack(spacing: 3) {
                    ZStack {
                        Circle()
                            .stroke(Color(red: 255/255, green: 152/255, blue: 0/255), lineWidth: 1.5)
                            .frame(width: 34, height: 34)
                        
                        Image(systemName: "speaker.wave.3.fill")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(Color(red: 255/255, green: 152/255, blue: 0/255))
                    }
                    Text("Sonidos")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(Color(red: 230/255, green: 81/255, blue: 0/255))
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 14)
        .background(.ultraThinMaterial)
        .background(Color.white.opacity(0.45))
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .overlay(
            RoundedRectangle(cornerRadius: 22)
                .stroke(viewModel.isBeaconEmitting ? AppTheme.accentCyanDark.opacity(0.6) : AppTheme.accent.opacity(0.3), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.08), radius: 8, y: 4)
        .padding(.horizontal, 16)
        .padding(.bottom, 6)
    }
    
    private var mapButtonColor: Color {
        AppTheme.primary
    }
}
