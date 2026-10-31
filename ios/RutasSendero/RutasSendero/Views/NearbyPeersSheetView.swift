//
//  NearbyPeersSheetView.swift
//  RutasSendero
//
//  Listado de monitoreo de compañeros senderistas detectados por Balizas BLE.
//

import SwiftUI

public struct NearbyPeersSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 12) {
                        Image(systemName: "antenna.radiowaves.left.and.right")
                            .font(.system(size: 24))
                            .foregroundStyle(AppTheme.accentCyan)
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Monitoreo de Balizas BLE")
                                .font(.headline)
                                .foregroundStyle(AppTheme.textPrimary)
                            Text(viewModel.nearbyPeers.isEmpty ? "Escaneando señales de compañeros en el sendero..." : "\(viewModel.nearbyPeers.count) compañero(s) detectado(s)")
                                .font(.subheadline)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                    .padding(.vertical, 4)
                }
                
                if viewModel.nearbyPeersList.isEmpty {
                    Section {
                        VStack(spacing: 12) {
                            Image(systemName: "person.badge.shield.checkmark")
                                .font(.system(size: 44))
                                .foregroundStyle(AppTheme.textSecondary.opacity(0.4))
                            Text("No se detectan senderistas cercanos en este momento.")
                                .font(.subheadline)
                                .foregroundStyle(AppTheme.textSecondary)
                                .multilineTextAlignment(.center)
                            
                            #if targetEnvironment(simulator)
                            Button("Simular Compañero Cercano") {
                                viewModel.simulatePeerDetected(beaconId: "4B3C2D1E0F9A", distanceMeters: 12.4)
                            }
                            .font(.caption).bold()
                            .padding(.top, 4)
                            #endif
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 24)
                    }
                } else {
                    Section("Senderistas Detectados") {
                        ForEach(viewModel.nearbyPeersList) { peer in
                            let cleanId = peer.beaconId.lowercased()
                            let profile = viewModel.peerSenderistaProfiles[cleanId]
                            
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(AppTheme.accentCyan.opacity(0.2))
                                    .frame(width: 42, height: 42)
                                    .overlay(
                                        Image(systemName: peer.isEmergency ? "exclamationmark.triangle.fill" : "figure.walk")
                                            .foregroundStyle(peer.isEmergency ? AppTheme.alert : AppTheme.accentCyan)
                                    )
                                
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 6) {
                                        Text(profile?.name ?? "Senderista \(peer.beaconId.prefix(6).uppercased())")
                                            .font(.system(size: 15, weight: .bold))
                                            .foregroundStyle(AppTheme.textPrimary)
                                        
                                        if let agru = profile?.idAgrupacion, !agru.isEmpty {
                                            Text(agru)
                                                .font(.system(size: 9, weight: .heavy))
                                                .foregroundStyle(AppTheme.accentCyan)
                                                .padding(.horizontal, 6)
                                                .padding(.vertical, 2)
                                                .background(AppTheme.accentCyan.opacity(0.15))
                                                .clipShape(Capsule())
                                        }
                                    }
                                    
                                    HStack(spacing: 6) {
                                        Text("Baliza: \(peer.beaconId)")
                                            .font(.system(size: 11, design: .monospaced))
                                            .foregroundStyle(AppTheme.textSecondary)
                                        
                                        if let phone = profile?.phone, !phone.isEmpty {
                                            Text("· 📞 \(phone)")
                                                .font(.system(size: 11))
                                                .foregroundStyle(AppTheme.accent)
                                        }
                                    }
                                }
                                
                                Spacer()
                                
                                VStack(alignment: .trailing, spacing: 3) {
                                    Text(String(format: "%.1f m", peer.estimatedDistanceMeters))
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundStyle(peer.isEmergency ? AppTheme.alert : AppTheme.accent)
                                    
                                    Text("RSSI: \(peer.rssi) dBm")
                                        .font(.system(size: 10, design: .monospaced))
                                        .foregroundStyle(AppTheme.textSecondary)
                                }
                            }
                            .padding(.vertical, 3)
                            .onAppear {
                                viewModel.resolvePeerProfile(for: peer.beaconId)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Monitoreo BLE")
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
}
