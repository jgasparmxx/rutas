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
        let isRecording = (viewModel.recordingState != .stopped || viewModel.activeRole == .registrar)
        let beaconOn = viewModel.isBeaconEmitting
        
        HStack(spacing: 0) {
            // 1. Ver Mapa (Solo disponible en modo Guía y Visor, 1:1 Android)
            Button {
                if isRecording {
                    viewModel.saveStatusMessage = "El mapa no está disponible en el modo Registrar (trazar). Solo está disponible en modo Guía y Visor."
                } else if viewModel.activeRoute == nil {
                    viewModel.saveStatusMessage = "Selecciona primero una ruta para abrir el mapa."
                } else {
                    viewModel.showMapScreen = true
                }
            } label: {
                VStack(spacing: 3) {
                    Image(systemName: isRecording ? "record.circle.fill" : "map.fill")
                        .font(.system(size: 22, weight: .semibold))
                        .frame(width: 40, height: 40)
                    Text(isRecording ? "Grabando" : "Mapa")
                        .font(.system(size: 10, weight: .bold))
                }
                .foregroundStyle(isRecording ? AppTheme.alert : mapButtonColor)
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            
            // 2. Faro / Transmisor BLE (Encender / Apagar Baliza de presencia 1:1 Android ic_lighthouse_on/off)
            Button {
                viewModel.toggleBeacon()
            } label: {
                VStack(spacing: 3) {
                    ZStack {
                        Image(beaconOn ? "LighthouseOn" : "LighthouseOff")
                            .resizable()
                            .scaledToFill()
                            .frame(width: 42, height: 42)
                            .clipShape(Circle())
                            .overlay(
                                Circle()
                                    .stroke(
                                        beaconOn ? AppTheme.accentCyan : AppTheme.textSecondary.opacity(0.6),
                                        lineWidth: 2
                                    )
                            )
                            .shadow(
                                color: beaconOn ? AppTheme.warningYellow.opacity(0.65) : Color.clear,
                                radius: beaconOn ? 7 : 0
                            )
                        
                        if viewModel.isStartingBeacon {
                            Circle()
                                .fill(Color.black.opacity(0.35))
                                .frame(width: 42, height: 42)
                            ProgressView()
                                .tint(.white)
                                .scaleEffect(0.8)
                        }
                    }
                    Text(beaconOn ? "Faro ON" : "Faro OFF")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(beaconOn ? AppTheme.accentCyan : AppTheme.darkBackground.opacity(0.7))
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            
            // 3. Monitoreo / Buscar Balizas de Compañeros
            Button {
                viewModel.isShowingNearbyPeersSheet = true
            } label: {
                VStack(spacing: 3) {
                    ZStack(alignment: .topTrailing) {
                        Image(systemName: "list.bullet")
                            .font(.system(size: 22, weight: .bold))
                            .frame(width: 40, height: 40)
                        
                        if !viewModel.nearbyPeers.isEmpty {
                            Text("\(viewModel.nearbyPeers.count)")
                                .font(.system(size: 9, weight: .heavy))
                                .foregroundStyle(AppTheme.darkBackground)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(AppTheme.accent)
                                .clipShape(Capsule())
                                .offset(x: 8, y: 0)
                        }
                    }
                    Text("Monitoreo")
                        .font(.system(size: 10, weight: .bold))
                }
                .foregroundStyle(beaconOn ? AppTheme.accentCyan : AppTheme.primary)
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
            
            // 4. Ayudas Sonoras (Silbato de Rescate / Pitidos de Orientación 1:1 Android ic_whistle_bright)
            Button {
                viewModel.isShowingSoundAlertsSheet = true
            } label: {
                VStack(spacing: 3) {
                    Image("WhistleBright")
                        .resizable()
                        .scaledToFill()
                        .frame(width: 42, height: 42)
                        .clipShape(Circle())
                        .overlay(
                            Circle()
                                .stroke(Color(red: 255/255, green: 152/255, blue: 0/255), lineWidth: 2)
                        )
                    
                    Text("Sonidos")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(beaconOn ? Color(red: 255/255, green: 183/255, blue: 77/255) : Color(red: 230/255, green: 81/255, blue: 0/255))
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 14)
        .background {
            if beaconOn {
                // 1:1 Android: Color(0xFF0F2537) al encender el Faro
                Color(red: 15/255, green: 37/255, blue: 55/255)
            } else {
                ZStack {
                    Rectangle().fill(.ultraThinMaterial)
                    Color.white.opacity(0.45)
                }
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 22))
        .overlay(
            RoundedRectangle(cornerRadius: 22)
                .stroke(beaconOn ? AppTheme.accentCyan.opacity(0.65) : AppTheme.accent.opacity(0.3), lineWidth: beaconOn ? 1.5 : 1)
        )
        .shadow(color: Color.black.opacity(beaconOn ? 0.25 : 0.08), radius: 8, y: 4)
        .animation(.easeInOut(duration: 0.25), value: beaconOn)
        .padding(.horizontal, 16)
        .padding(.bottom, 6)
    }
    
    private var mapButtonColor: Color {
        if viewModel.isBeaconEmitting {
            return viewModel.activeRoute != nil ? AppTheme.accentCyan : Color.white.opacity(0.45)
        }
        return viewModel.activeRoute != nil ? AppTheme.primary : AppTheme.darkBackground.opacity(0.4)
    }
}
