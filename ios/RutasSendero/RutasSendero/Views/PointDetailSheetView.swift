//
//  PointDetailSheetView.swift
//  RutasSendero
//
//  Diálogo modal de detalle de punto 1:1 con Android PuntoRutaDetailDialog.
//  Muestra la foto del punto (con opción de tocar para ver en grande, girar y compartir),
//  información satelital (coordenadas, altitud, fecha/hora, conectividad)
//  y controles de navegación anterior/siguiente.
//  En modo Visor y Guía el contenido es de solo lectura y no permite editar ni agregar puntos.
//

import SwiftUI

public struct QuickPointStateOption: Identifiable, Sendable {
    public let id: Int
    public let name: String
    
    public var info: PointStateInfo {
        PointStateInfo.forPoint(estado: id)
    }
    
    public static let allOptions: [QuickPointStateOption] = [
        QuickPointStateOption(id: 1, name: "Normal"),
        QuickPointStateOption(id: 2, name: "Sitio de interés"),
        QuickPointStateOption(id: 3, name: "Bifurcación"),
        QuickPointStateOption(id: 4, name: "Suelo flojo"),
        QuickPointStateOption(id: 5, name: "Cuidado"),
        QuickPointStateOption(id: 6, name: "Alerta"),
        QuickPointStateOption(id: 8, name: "Vehículo"),
        QuickPointStateOption(id: 9, name: "Refugio"),
        QuickPointStateOption(id: 10, name: "Comida")
    ]
    
    public static let specialOptions: [QuickPointStateOption] = allOptions.filter { $0.id != 1 }
}

public struct PointDetailSheetView: View {
    @Bindable var viewModel: MainViewModel
    let point: RoutePoint
    @Environment(\.dismiss) private var dismiss
    
    @State private var isShowingFullScreenPhoto: Bool = false
    @State private var editedDescription: String = ""
    @State private var editedEstado: Int = 1
    @State private var voiceService = VoiceDictationService.shared
    
    public init(viewModel: MainViewModel, point: RoutePoint) {
        self.viewModel = viewModel
        self.point = point
        self._editedDescription = State(initialValue: point.descriptionText)
        self._editedEstado = State(initialValue: point.estado)
    }
    
    public var body: some View {
        let stateInfo = PointStateInfo.forPoint(estado: point.estado, isRouteEnd: point.isRouteEnd, description: point.descriptionText)
        
        return NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Badge del Estado del Punto (1:1 Android)
                    HStack(spacing: 12) {
                        PointStateBadgeView(estado: point.estado, isRouteEnd: point.isRouteEnd, description: point.descriptionText, size: 40)
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(stateInfo.name)
                                .font(.system(size: 15, weight: .bold))
                                .foregroundStyle(stateInfo.color)
                            Text(point.isRouteEnd ? "Punto final de la ruta" : (point.hasConnectivity ? "Hito con señal celular activa" : "Hito en zona sin cobertura celular"))
                                .font(.system(size: 12))
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                        
                        Spacer()
                    }
                    .padding(12)
                    .background(AppTheme.cardBackground.opacity(0.85))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(stateInfo.color.opacity(0.3), lineWidth: 1)
                    )
                    
                    // 1. Fotografía de Referencia (Tocar para ampliar a pantalla completa)
                    if let photoPath = point.photoUrl, !photoPath.isEmpty {
                        ZStack(alignment: .bottomTrailing) {
                            WaypointImageView(photoPath: photoPath, cornerRadius: 16)
                                .frame(height: 240)
                                .frame(maxWidth: .infinity)
                            
                            // Badge indicador de ampliación
                            HStack(spacing: 6) {
                                Image(systemName: "arrow.up.left.and.arrow.down.right")
                                    .font(.system(size: 11, weight: .bold))
                                Text("Toca para ampliar y girar")
                                    .font(.system(size: 11, weight: .semibold))
                            }
                            .foregroundStyle(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.black.opacity(0.65))
                            .clipShape(Capsule())
                            .padding(10)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            isShowingFullScreenPhoto = true
                        }
                    } else {
                        // Estado vacío de foto
                        VStack(spacing: 8) {
                            Image(systemName: "photo.badge.exclamationmark")
                                .font(.system(size: 36))
                                .foregroundStyle(AppTheme.textSecondary.opacity(0.6))
                            Text("Sin fotografía registrada para este punto")
                                .font(.system(size: 12))
                                .foregroundStyle(AppTheme.textSecondary.opacity(0.8))
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 120)
                        .background(AppTheme.cardMuted.opacity(0.4))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    
                    // 2. Tarjeta de Telemetría y Coordenadas Satelitales
                    VStack(alignment: .leading, spacing: 10) {
                        HStack {
                            Label("DATOS SATELITALES", systemImage: "antenna.radiowaves.left.and.right")
                                .font(.system(size: 11, weight: .heavy))
                                .foregroundStyle(Color(red: 56/255, green: 189/255, blue: 248/255))
                            Spacer()
                            
                            HStack(spacing: 4) {
                                Image(systemName: point.hasConnectivity ? "wifi" : "wifi.slash")
                                    .font(.system(size: 11, weight: .bold))
                                Text(point.hasConnectivity ? "Conectado" : "Sin señal")
                                    .font(.system(size: 11, weight: .semibold))
                            }
                            .foregroundStyle(point.hasConnectivity ? AppTheme.success : AppTheme.textSecondary)
                        }
                        
                        Divider()
                            .background(AppTheme.cardBorder)
                        
                        HStack(spacing: 16) {
                            VStack(alignment: .leading, spacing: 3) {
                                Text("COORDENADAS")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundStyle(AppTheme.textSecondary)
                                Text(String(format: "%.6f, %.6f", point.latitude, point.longitude))
                                    .font(.system(size: 13, weight: .semibold, design: .monospaced))
                                    .foregroundStyle(AppTheme.textPrimary)
                            }
                            
                            Spacer()
                            
                            VStack(alignment: .trailing, spacing: 3) {
                                Text("ALTITUD")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundStyle(AppTheme.textSecondary)
                                Text(RouteStatsCalculator.formatElevation(point.altitude))
                                    .font(.system(size: 14, weight: .heavy, design: .rounded))
                                    .foregroundStyle(AppTheme.accentCyan)
                            }
                        }
                        
                        if !point.timestamp.isEmpty {
                            HStack {
                                Text("Registrado:")
                                    .font(.system(size: 11))
                                    .foregroundStyle(AppTheme.textSecondary)
                                Spacer()
                                Text(point.timestamp)
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundStyle(AppTheme.textPrimary)
                            }
                        }
                    }
                    .padding(14)
                    .background(AppTheme.cardBackground)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(AppTheme.cardBorder, lineWidth: 1)
                    )
                    
                    // 3. Descripción y Estado del Punto (Editable cuando la ruta es propia / en registro 1:1 Android PuntoRutaDetailDialog)
                    let pts = viewModel.tourPoints
                    let currentIndex = pts.firstIndex(where: { $0.id == point.id }) ?? 0
                    let nextPoint = (currentIndex < pts.count - 1) ? pts[currentIndex + 1] : nil
                    let nextPhrase = RouteStatsCalculator.nextPointPhrase(from: point, to: nextPoint)
                    
                    descriptionAndStateSection(nextPhrase: nextPhrase)
                    
                    // 4. Navegación entre Puntos (Anterior y Siguiente)
                    navigationButtonsSection(currentIndex: currentIndex, totalCount: pts.count)
                }
                .padding(16)
            }
            .background(AppTheme.darkBackground.ignoresSafeArea())
            .navigationTitle("Punto #\(point.id)")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Cerrar") {
                        if voiceService.isRecording {
                            voiceService.stopDictation()
                        }
                        dismiss()
                    }
                    .foregroundStyle(AppTheme.accent)
                }
            }
            .fullScreenCover(isPresented: $isShowingFullScreenPhoto) {
                if let path = point.photoUrl {
                    FullScreenPhotoViewer(photoPath: path) {
                        isShowingFullScreenPhoto = false
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
    
    @ViewBuilder
    private func descriptionAndStateSection(nextPhrase: String?) -> some View {
        let isEditable = !viewModel.isRouteConsumptionOnly
        
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("DESCRIPCIÓN Y COMENTARIOS")
                    .font(.system(size: 11, weight: .heavy))
                    .foregroundStyle(AppTheme.textSecondary)
                Spacer()
                if isEditable {
                    Button {
                        if voiceService.isRecording {
                            voiceService.stopDictation()
                        } else {
                            Task {
                                let ok = await voiceService.requestPermissions()
                                if ok {
                                    voiceService.startDictation { transcribed in
                                        editedDescription = transcribed
                                    }
                                }
                            }
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: voiceService.isRecording ? "stop.circle.fill" : "mic.fill")
                            Text(voiceService.isRecording ? "Detener" : "Dictar")
                        }
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(voiceService.isRecording ? .white : AppTheme.accent)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(voiceService.isRecording ? AppTheme.alert : AppTheme.accent.opacity(0.15))
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            
            if isEditable {
                editablePointFieldsView
            } else {
                Text(point.descriptionText.isEmpty ? "Punto de control sin descripción detallada." : point.descriptionText)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(AppTheme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(AppTheme.cardMuted.opacity(0.5))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            
            if let nextPhrase = nextPhrase {
                HStack(spacing: 8) {
                    Image(systemName: "figure.walk")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(Color(red: 94/255, green: 234/255, blue: 212/255))
                    Text(nextPhrase)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color(red: 94/255, green: 234/255, blue: 212/255))
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(Color(red: 15/255, green: 118/255, blue: 110/255).opacity(0.28))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color(red: 94/255, green: 234/255, blue: 212/255).opacity(0.45), lineWidth: 1)
                )
            }
        }
        .onChange(of: point.id) { _, _ in
            editedDescription = point.descriptionText
            editedEstado = point.estado
        }
    }
    
    @ViewBuilder
    private var editablePointFieldsView: some View {
        TextField("Agrega un comentario o descripción al punto...", text: $editedDescription, axis: .vertical)
            .lineLimit(2...5)
            .font(.system(size: 15, weight: .medium))
            .foregroundStyle(AppTheme.textPrimary)
            .padding(12)
            .background(AppTheme.cardMuted.opacity(0.6))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(AppTheme.accent.opacity(0.35), lineWidth: 1)
            )
        
        Text("ESTADO DEL PUNTO")
            .font(.system(size: 10, weight: .heavy))
            .foregroundStyle(AppTheme.textSecondary)
            .padding(.top, 2)
        
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(QuickPointStateOption.allOptions) { item in
                    let isSelected = (editedEstado == item.id)
                    let info = item.info
                    Button {
                        editedEstado = item.id
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: info.systemImage)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundStyle(isSelected ? .white : info.color)
                            Text(item.name)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundStyle(isSelected ? .white : AppTheme.textPrimary)
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(isSelected ? info.color : info.backgroundColor)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        
        Button {
            if voiceService.isRecording {
                voiceService.stopDictation()
            }
            var updated = point
            updated.descriptionText = editedDescription.trimmingCharacters(in: .whitespacesAndNewlines)
            updated.estado = editedEstado
            viewModel.updateRecordedPoint(updated)
            dismiss()
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 15, weight: .bold))
                Text("Guardar Comentario / Estado")
                    .font(.system(size: 13, weight: .heavy))
            }
            .foregroundStyle(Color(red: 15/255, green: 23/255, blue: 42/255))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 11)
            .background(AppTheme.accent)
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }
    
    @ViewBuilder
    private func navigationButtonsSection(currentIndex: Int, totalCount: Int) -> some View {
        HStack(spacing: 12) {
            Button {
                viewModel.previousPointDetail()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 13, weight: .bold))
                    Text("Anterior")
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundStyle(currentIndex > 0 ? AppTheme.textPrimary : AppTheme.textSecondary.opacity(0.4))
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(AppTheme.cardMuted)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .disabled(currentIndex == 0)
            .buttonStyle(.plain)
            
            Text("\(currentIndex + 1) de \(totalCount)")
                .font(.system(size: 12, weight: .bold, design: .monospaced))
                .foregroundStyle(AppTheme.textSecondary)
                .padding(.horizontal, 8)
            
            Button {
                viewModel.nextPointDetail()
            } label: {
                HStack(spacing: 6) {
                    Text("Siguiente")
                        .font(.system(size: 13, weight: .bold))
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .bold))
                }
                .foregroundStyle(currentIndex < totalCount - 1 ? AppTheme.textPrimary : AppTheme.textSecondary.opacity(0.4))
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(AppTheme.cardMuted)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .disabled(currentIndex >= totalCount - 1)
            .buttonStyle(.plain)
        }
        .padding(.top, 8)
    }
}
