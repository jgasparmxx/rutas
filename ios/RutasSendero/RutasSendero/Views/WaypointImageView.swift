//
//  WaypointImageView.swift
//  RutasSendero
//
//  Componentes para visualizar fotos de puntos de control (Waypoints):
//  - WaypointImageView: Imagen mediana/grande con soporte offline y remoto
//  - WaypointThumbnailView: Miniatura para filas de lista y marcadores de mapa
//

import SwiftUI

public struct WaypointImageView: View {
    public let photoPath: String?
    public var cornerRadius: CGFloat = 12
    
    public init(photoPath: String?, cornerRadius: CGFloat = 12) {
        self.photoPath = photoPath
        self.cornerRadius = cornerRadius
    }
    
    public var body: some View {
        Group {
            if let path = photoPath, !path.isEmpty {
                if path.hasPrefix("http://") || path.hasPrefix("https://") {
                    AsyncImage(url: URL(string: path)) { phase in
                        switch phase {
                        case .empty:
                            ProgressView()
                                .tint(AppTheme.accent)
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                        case .success(let image):
                            image
                                .resizable()
                                .scaledToFill()
                        case .failure:
                            fallbackView
                        @unknown default:
                            fallbackView
                        }
                    }
                } else if let localImage = ImageStorageHelper.loadImage(from: path) {
                    Image(uiImage: localImage)
                        .resizable()
                        .scaledToFill()
                } else {
                    fallbackView
                }
            } else {
                fallbackView
            }
        }
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
    }
    
    private var fallbackView: some View {
        ZStack {
            AppTheme.cardMuted
            VStack(spacing: 6) {
                Image(systemName: "photo")
                    .font(.system(size: 28))
                    .foregroundStyle(AppTheme.textSecondary.opacity(0.6))
                Text("Sin foto")
                    .font(.system(size: 11))
                    .foregroundStyle(AppTheme.textSecondary.opacity(0.6))
            }
        }
    }
}

// MARK: - Miniatura Cuadrada para Filas de Lista y Marcadores
public struct WaypointThumbnailView: View {
    public let photoPath: String?
    public var size: CGFloat = 44
    public var borderColor: Color = AppTheme.cardBorder
    
    public init(photoPath: String?, size: CGFloat = 44, borderColor: Color = AppTheme.cardBorder) {
        self.photoPath = photoPath
        self.size = size
        self.borderColor = borderColor
    }
    
    public var body: some View {
        ZStack {
            if let path = photoPath, !path.isEmpty {
                if path.hasPrefix("http://") || path.hasPrefix("https://") {
                    AsyncImage(url: URL(string: path)) { phase in
                        if let image = phase.image {
                            image
                                .resizable()
                                .scaledToFill()
                        } else {
                            placeholderIcon
                        }
                    }
                } else if let localImage = ImageStorageHelper.loadImage(from: path) {
                    Image(uiImage: localImage)
                        .resizable()
                        .scaledToFill()
                } else {
                    placeholderIcon
                }
            } else {
                placeholderIcon
            }
        }
        .frame(width: size, height: size)
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(borderColor, lineWidth: 1.5)
        )
    }
    
    private var placeholderIcon: some View {
        ZStack {
            AppTheme.cardMuted
            Image(systemName: "mappin.and.ellipse")
                .font(.system(size: size * 0.45))
                .foregroundStyle(AppTheme.accent)
        }
    }
}
