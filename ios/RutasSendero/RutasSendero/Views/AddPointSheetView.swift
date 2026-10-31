//
//  AddPointSheetView.swift
//  RutasSendero
//
//  Hoja modal para registrar un nuevo punto de control (Waypoint)
//  con descripción, tags rápidos, coordenadas satelitales en vivo,
//  captura de fotografía con la cámara y dictado por voz.
//

import SwiftUI
import CoreLocation

public struct AddPointSheetView: View {
    @Bindable var viewModel: MainViewModel
    @Environment(\.dismiss) private var dismiss
    
    @State private var pointDescription: String = ""
    @State private var isFinRuta: Bool = false
    @State private var selectedEstado: Int = 1
    
    // Foto
    @State private var capturedImage: UIImage? = nil
    @State private var capturedImagePath: String? = nil
    @State private var isShowingCameraPicker: Bool = false
    
    // Voz
    @State private var voiceService = VoiceDictationService.shared
    @State private var isShowingVoiceAlert: Bool = false
    
    private let availableEstados: [(id: Int, name: String)] = [
        (1, "Normal"),
        (2, "Interés"),
        (3, "Bifurcación"),
        (4, "Suelo flojo"),
        (5, "Cuidado"),
        (6, "Alerta"),
        (8, "Vehículo"),
        (9, "Refugio"),
        (10, "Comida")
    ]
    
    private let quickTags = [
        "Mirador", "Descanso", "Cruce de senderos", "Agua / Arroyo",
        "Peligro / Precaución", "Campamento", "Cueva", "Hito geodésico"
    ]
    
    public init(viewModel: MainViewModel) {
        self.viewModel = viewModel
    }
    
    public var body: some View {
        NavigationStack {
            Form {
                // Telemetría GPS en Tiempo Real
                Section {
                    if let loc = viewModel.locationTracker.currentLocation {
                        HStack {
                            Text("Coordenadas:")
                            Spacer()
                            Text(String(format: "%.5f, %.5f", loc.coordinate.latitude, loc.coordinate.longitude))
                                .font(.system(size: 13, design: .monospaced))
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                        
                        HStack {
                            Text("Altitud:")
                            Spacer()
                            Text(RouteStatsCalculator.formatElevation(loc.altitude))
                                .font(.system(size: 13, weight: .bold, design: .monospaced))
                                .foregroundStyle(AppTheme.accent)
                        }
                        
                        HStack {
                            Text("Precisión:")
                            Spacer()
                            Text(String(format: "±%.1f metros", loc.horizontalAccuracy))
                                .font(.system(size: 13, design: .monospaced))
                                .foregroundStyle(loc.horizontalAccuracy <= 10.0 ? AppTheme.success : AppTheme.warningYellow)
                        }
                    } else {
                        HStack {
                            Image(systemName: "location.slash")
                                .foregroundStyle(AppTheme.alert)
                            Text("Esperando señal de GPS...")
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                } header: {
                    Text("Posición Satelital")
                }
                
                // Fotografía del Hito / Punto (Cámara)
                Section {
                    if let image = capturedImage {
                        VStack(spacing: 8) {
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFill()
                                .frame(maxWidth: .infinity, maxHeight: 180)
                                .clipped()
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                            
                            HStack {
                                Button(role: .destructive) {
                                    capturedImage = nil
                                    capturedImagePath = nil
                                } label: {
                                    Label("Eliminar Foto", systemImage: "trash")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(AppTheme.alert)
                                }
                                
                                Spacer()
                                
                                Button {
                                    isShowingCameraPicker = true
                                } label: {
                                    Label("Repetir Foto", systemImage: "arrow.triangle.2.circlepath.camera")
                                        .font(.system(size: 12, weight: .semibold))
                                        .foregroundStyle(AppTheme.accent)
                                }
                            }
                            .padding(.top, 4)
                        }
                    } else {
                        Button {
                            isShowingCameraPicker = true
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "camera.fill")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundStyle(AppTheme.accent)
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Tomar Fotografía del Punto")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundStyle(AppTheme.textPrimary)
                                    Text("Captura el hito, cruce o panorama de referencia")
                                        .font(.system(size: 11))
                                        .foregroundStyle(AppTheme.textSecondary)
                                }
                                
                                Spacer()
                                
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12))
                                    .foregroundStyle(AppTheme.textSecondary)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                } header: {
                    Text("Fotografía")
                }
                
                // Descripción del Punto y Dictado por Voz
                Section {
                    HStack(alignment: .center, spacing: 8) {
                        TextField("Descripción o hito (ej. Mirador La Peña)", text: $pointDescription)
                            .font(.system(size: 15))
                        
                        // Botón de Dictado por Voz
                        Button {
                            if voiceService.isRecording {
                                voiceService.stopDictation()
                            } else {
                                Task {
                                    let hasPermission = await voiceService.requestPermissions()
                                    if hasPermission {
                                        voiceService.startDictation { transcribed in
                                            pointDescription = transcribed
                                        }
                                    } else {
                                        isShowingVoiceAlert = true
                                    }
                                }
                            }
                        } label: {
                            Image(systemName: voiceService.isRecording ? "stop.circle.fill" : "mic.fill")
                                .font(.system(size: 18, weight: .bold))
                                .foregroundStyle(voiceService.isRecording ? AppTheme.alert : AppTheme.success)
                                .frame(width: 36, height: 36)
                                .background((voiceService.isRecording ? AppTheme.alert : AppTheme.success).opacity(0.15))
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)
                    }
                    
                    if voiceService.isRecording {
                        HStack(spacing: 8) {
                            Circle()
                                .fill(AppTheme.alert)
                                .frame(width: 8, height: 8)
                            Text("Escuchando... hable con claridad")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(AppTheme.alert)
                            Spacer()
                            Text("Toca parar al terminar")
                                .font(.system(size: 11))
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                        .padding(.vertical, 2)
                    }
                } header: {
                    Text("Detalle del Punto #\(viewModel.recordedPoints.count + 1)")
                }
                
                // Estado / Tipo de Punto (1:1 Android)
                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(availableEstados, id: \.id) { item in
                                let info = PointStateInfo.forPoint(estado: item.id)
                                let isSelected = (selectedEstado == item.id)
                                
                                Button {
                                    selectedEstado = item.id
                                } label: {
                                    VStack(spacing: 5) {
                                        ZStack {
                                            RoundedRectangle(cornerRadius: 10)
                                                .fill(isSelected ? info.color : info.backgroundColor)
                                                .frame(width: 44, height: 44)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 10)
                                                        .stroke(isSelected ? Color.white : info.color.opacity(0.4), lineWidth: isSelected ? 2 : 1)
                                                )
                                            
                                            Image(systemName: info.systemImage)
                                                .font(.system(size: 20, weight: .bold))
                                                .foregroundStyle(isSelected ? .white : info.color)
                                        }
                                        
                                        Text(item.name)
                                            .font(.system(size: 10, weight: isSelected ? .bold : .medium))
                                            .foregroundStyle(isSelected ? info.color : AppTheme.textSecondary)
                                            .lineLimit(1)
                                    }
                                    .padding(.vertical, 4)
                                    .padding(.horizontal, 2)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 4)
                    }
                    .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                } header: {
                    Text("Tipo y Estado del Punto")
                }
                
                // Tags Rápidos
                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(quickTags, id: \.self) { tag in
                                Button {
                                    if pointDescription.isEmpty {
                                        pointDescription = tag
                                    } else {
                                        pointDescription += " - \(tag)"
                                    }
                                } label: {
                                    Text(tag)
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
                    Text("Etiquetas Rápidas")
                }
                
                // Historial de Puntos Guardados (1:1 Android: consultar lista sin sobrecargar la pantalla principal)
                if !viewModel.recordedPoints.isEmpty {
                    Section {
                        DisclosureGroup("Ver puntos ya guardados (\(viewModel.recordedPoints.count))") {
                            ForEach(viewModel.recordedPoints) { pt in
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        HStack(spacing: 6) {
                                            Text("#\(pt.id) \(pt.descriptionText)")
                                                .font(.system(size: 13, weight: .semibold))
                                                .foregroundStyle(AppTheme.textPrimary)
                                            
                                            if pt.photoUrl != nil {
                                                Image(systemName: "camera.fill")
                                                    .font(.system(size: 10))
                                                    .foregroundStyle(AppTheme.accent)
                                            }
                                        }
                                        
                                        Text(String(format: "Alt: %.0fm • %.5f, %.5f", pt.altitude, pt.latitude, pt.longitude))
                                            .font(.system(size: 11, design: .monospaced))
                                            .foregroundStyle(AppTheme.textSecondary)
                                    }
                                    
                                    Spacer()
                                    
                                    if pt.isRouteEnd {
                                        Text("FIN")
                                            .font(.system(size: 10, weight: .heavy))
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(AppTheme.alert.opacity(0.2))
                                            .foregroundStyle(AppTheme.alert)
                                            .clipShape(Capsule())
                                    }
                                }
                                .padding(.vertical, 3)
                            }
                        }
                    } header: {
                        Text("Historial de la Ruta")
                    }
                }
                
                // Marcar como Fin de Ruta
                Section {
                    Toggle("Marcar como Fin de la Ruta", isOn: $isFinRuta)
                        .tint(AppTheme.alert)
                } footer: {
                    if isFinRuta {
                        Text("Al guardar este punto se finalizará la grabación del sendero.")
                            .foregroundStyle(AppTheme.alert)
                    }
                }
            }
            .navigationTitle("Registrar Punto")
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
                    Button("Guardar Punto") {
                        if voiceService.isRecording {
                            voiceService.stopDictation()
                        }
                        viewModel.addPoint(
                            description: pointDescription,
                            photoUrl: capturedImagePath,
                            isFinRuta: isFinRuta,
                            estado: selectedEstado
                        )
                        dismiss()
                    }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(AppTheme.warningYellow)
                }
            }
            .sheet(isPresented: $isShowingCameraPicker) {
                ImagePickerView(
                    selectedImage: $capturedImage,
                    imagePath: $capturedImagePath,
                    preferredSourceType: .camera
                )
            }
            .alert("Permiso de Micrófono", isPresented: $isShowingVoiceAlert) {
                Button("Entendido", role: .cancel) {}
            } message: {
                Text("Por favor permite el acceso al micrófono y reconocimiento de voz en Ajustes para dictar notas.")
            }
            .onDisappear {
                if voiceService.isRecording {
                    voiceService.stopDictation()
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}
