//
//  FullScreenPhotoViewer.swift
//  RutasSendero
//
//  Visor de Fotos a Pantalla Completa 1:1 con Android FullScreenPhotoViewerDialog.
//  Permite zoom táctil, paneo, compartir la imagen mediante ShareSheet,
//  girar la foto de 90° en 90° y guardar la rotación físicamente de forma local
//  en el dispositivo sin sincronizar ni subir dicha modificación a la nube.
//

import SwiftUI

public struct FullScreenPhotoViewer: View {
    public let photoPath: String
    public var onDismiss: () -> Void
    
    // Zoom y paneo
    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    
    // Rotación y guardado local
    @State private var visualRotation: CGFloat = 0.0
    @State private var isSaving: Bool = false
    @State private var saveMessage: String? = nil
    @State private var reloadToken: Int = 0
    @State private var isShowingShareSheet: Bool = false
    
    public init(photoPath: String, onDismiss: @escaping () -> Void) {
        self.photoPath = photoPath
        self.onDismiss = onDismiss
    }
    
    private var currentImage: UIImage? {
        // Forzar recarga basada en reloadToken
        _ = reloadToken
        return ImageStorageHelper.loadImage(from: photoPath)
    }
    
    public var body: some View {
        ZStack {
            Color.black
                .ignoresSafeArea()
            
            // Imagen con gestos de zoom, arrastre y rotación
            if let image = currentImage {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
                    .rotationEffect(.degrees(visualRotation))
                    .scaleEffect(scale)
                    .offset(offset)
                    .gesture(
                        SimultaneousGesture(
                            // Gesto de zoom (Pinch)
                            MagnificationGesture()
                                .onChanged { value in
                                    let delta = value / lastScale
                                    lastScale = value
                                    scale = min(max(scale * delta, 1.0), 5.0)
                                }
                                .onEnded { _ in
                                    lastScale = 1.0
                                    if scale < 1.0 {
                                        withAnimation(.spring()) {
                                            scale = 1.0
                                            offset = .zero
                                        }
                                    }
                                },
                            // Gesto de paneo (Drag)
                            DragGesture()
                                .onChanged { value in
                                    if scale > 1.0 {
                                        offset = CGSize(
                                            width: lastOffset.width + value.translation.width,
                                            height: lastOffset.height + value.translation.height
                                        )
                                    }
                                }
                                .onEnded { _ in
                                    lastOffset = offset
                                }
                        )
                    )
                    .onTapGesture(count: 2) {
                        // Doble toque para alternar zoom 1x <-> 2.5x
                        withAnimation(.easeInOut(duration: 0.3)) {
                            if scale > 1.0 {
                                scale = 1.0
                                offset = .zero
                                lastOffset = .zero
                            } else {
                                scale = 2.5
                            }
                        }
                    }
            } else if photoPath.hasPrefix("http://") || photoPath.hasPrefix("https://") {
                AsyncImage(url: URL(string: photoPath)) { phase in
                    if let image = phase.image {
                        image
                            .resizable()
                            .scaledToFit()
                            .rotationEffect(.degrees(visualRotation))
                            .scaleEffect(scale)
                    } else if phase.error != nil {
                        Text("No se pudo cargar la imagen remota")
                            .foregroundStyle(.white)
                    } else {
                        ProgressView()
                            .tint(Color(red: 56/255, green: 189/255, blue: 248/255))
                    }
                }
            } else {
                Text("No se encontró el archivo de la foto")
                    .foregroundStyle(.white)
            }
            
            // Toast / Mensaje de Guardado
            if let msg = saveMessage {
                VStack {
                    Text(msg)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.85))
                        .clipShape(Capsule())
                        .overlay(
                            Capsule().stroke(AppTheme.success, lineWidth: 1.5)
                        )
                        .padding(.top, 70)
                    Spacer()
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
            
            // Controles Superiores: Volver y Compartir
            VStack {
                HStack {
                    // Botón Volver / Cerrar
                    Button {
                        onDismiss()
                    } label: {
                        Image(systemName: "arrow.left")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .background(Color.black.opacity(0.6))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                    
                    Spacer()
                    
                    // Botón Compartir
                    Button {
                        isShowingShareSheet = true
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(.white)
                            .frame(width: 44, height: 44)
                            .background(Color.black.opacity(0.6))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 20)
                .padding(.top, 50)
                
                Spacer()
                
                // Barra Inferior de Acciones: Girar y Guardar
                HStack(spacing: 20) {
                    // Botón Girar (+90°)
                    Button {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            visualRotation = (visualRotation + 90.0).truncatingRemainder(dividingBy: 360.0)
                        }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "rotate.right.fill")
                                .font(.system(size: 15, weight: .bold))
                            Text("Girar 90°")
                                .font(.system(size: 14, weight: .bold))
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(AppTheme.cardMuted.opacity(0.85))
                        .clipShape(Capsule())
                        .overlay(
                            Capsule().stroke(Color.white.opacity(0.2), lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                    
                    // Botón Guardar Rotación Local (Sin subir a la nube)
                    let hasPendingRotation = (Int(visualRotation) % 360 != 0)
                    Button {
                        guard hasPendingRotation && !isSaving else { return }
                        isSaving = true
                        
                        let rot = visualRotation
                        let success = ImageStorageHelper.rotateAndSavePhysically(filePath: photoPath, degrees: rot)
                        
                        if success {
                            visualRotation = 0
                            reloadToken += 1
                            withAnimation {
                                saveMessage = "¡Foto rotada y guardada localmente!"
                            }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                                withAnimation {
                                    saveMessage = nil
                                }
                            }
                        } else {
                            withAnimation {
                                saveMessage = "Error al guardar foto localmente"
                            }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                                withAnimation {
                                    saveMessage = nil
                                }
                            }
                        }
                        isSaving = false
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 15, weight: .bold))
                            Text(isSaving ? "Guardando..." : "Guardar")
                                .font(.system(size: 14, weight: .bold))
                        }
                        .foregroundStyle(hasPendingRotation ? Color(red: 15/255, green: 23/255, blue: 42/255) : Color.white.opacity(0.4))
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(hasPendingRotation ? AppTheme.accent : AppTheme.cardMuted.opacity(0.5))
                        .clipShape(Capsule())
                    }
                    .disabled(!hasPendingRotation || isSaving)
                    .buttonStyle(.plain)
                }
                .padding(.bottom, 40)
            }
        }
        .sheet(isPresented: $isShowingShareSheet) {
            let resolved = ImageStorageHelper.resolveLocalPath(photoPath)
            if FileManager.default.fileExists(atPath: resolved) {
                ShareSheetView(items: [URL(fileURLWithPath: resolved)])
            } else if let img = currentImage {
                ShareSheetView(items: [img])
            }
        }
    }
}
