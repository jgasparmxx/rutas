//
//  ImagePickerView.swift
//  RutasSendero
//
//  Componente nativo para capturar fotos con la cámara del dispositivo
//  o seleccionar de la fototeca si la cámara no está disponible (ej. simulador),
//  guardando la imagen en el almacenamiento local seguro de la app.
//

import SwiftUI
import UIKit

public struct ImagePickerView: UIViewControllerRepresentable {
    @Binding public var selectedImage: UIImage?
    @Binding public var imagePath: String?
    @Environment(\.dismiss) private var dismiss
    
    public var preferredSourceType: UIImagePickerController.SourceType = .camera
    public var onImageSaved: ((String) -> Void)? = nil
    
    public init(
        selectedImage: Binding<UIImage?>,
        imagePath: Binding<String?>,
        preferredSourceType: UIImagePickerController.SourceType = .camera,
        onImageSaved: ((String) -> Void)? = nil
    ) {
        self._selectedImage = selectedImage
        self._imagePath = imagePath
        self.preferredSourceType = preferredSourceType
        self.onImageSaved = onImageSaved
    }
    
    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    public func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        
        if UIImagePickerController.isSourceTypeAvailable(preferredSourceType) {
            picker.sourceType = preferredSourceType
        } else {
            // Fallback elegante si la cámara no está disponible (ej. simulador)
            picker.sourceType = .photoLibrary
        }
        
        return picker
    }
    
    public func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    
    public final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: ImagePickerView
        
        init(_ parent: ImagePickerView) {
            self.parent = parent
        }
        
        public func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]
        ) {
            if let uiImage = info[.originalImage] as? UIImage {
                self.parent.selectedImage = uiImage
                
                // Guardar la foto localmente en Documents/fotos (1:1 Android filesDir/foto_*.jpg)
                if let savedPath = ImageStorageHelper.saveImageLocally(uiImage) {
                    self.parent.imagePath = savedPath
                    self.parent.onImageSaved?(savedPath)
                }
            }
            parent.dismiss()
        }
        
        public func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}

public enum ImageStorageHelper {
    public static func saveImageLocally(_ image: UIImage) -> String? {
        guard let data = image.jpegData(compressionQuality: 0.8) else { return nil }
        
        let fileManager = FileManager.default
        guard let documentsDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
            return nil
        }
        
        let photosDir = documentsDirectory.appendingPathComponent("fotos", isDirectory: true)
        if !fileManager.fileExists(atPath: photosDir.path) {
            try? fileManager.createDirectory(at: photosDir, withIntermediateDirectories: true)
        }
        
        let filename = "foto_\(Int(Date().timeIntervalSince1970 * 1000)).jpg"
        let fileURL = photosDir.appendingPathComponent(filename)
        
        do {
            try data.write(to: fileURL)
            return fileURL.path
        } catch {
            print("Error guardando foto local: \(error)")
            return nil
        }
    }
    
    public static func resolveLocalPath(_ path: String) -> String {
        if FileManager.default.fileExists(atPath: path) {
            return path
        }
        // Si el contenedor de sandbox cambió de UUID, buscar por nombre en Documents/fotos/
        let filename = (path as NSString).lastPathComponent
        if let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
            let candidatePhotos = docs.appendingPathComponent("fotos").appendingPathComponent(filename).path
            if FileManager.default.fileExists(atPath: candidatePhotos) {
                return candidatePhotos
            }
            let candidateDirect = docs.appendingPathComponent(filename).path
            if FileManager.default.fileExists(atPath: candidateDirect) {
                return candidateDirect
            }
        }
        return path
    }
    
    public static func loadImage(from path: String) -> UIImage? {
        let resolved = resolveLocalPath(path)
        if FileManager.default.fileExists(atPath: resolved) {
            return UIImage(contentsOfFile: resolved)
        }
        return nil
    }
    
    /// Rota la imagen física local en grados (90, 180, 270) y la reescribe en disco.
    /// IMPORTANTE: Esta modificación es estrictamente local en el dispositivo y no se sube a la nube.
    public static func rotateAndSavePhysically(filePath: String, degrees: CGFloat) -> Bool {
        let resolved = resolveLocalPath(filePath)
        guard let originalImage = UIImage(contentsOfFile: resolved) else { return false }
        
        let radians = degrees * .pi / 180.0
        var newSize = CGRect(origin: .zero, size: originalImage.size)
            .applying(CGAffineTransform(rotationAngle: radians))
            .integral.size
        newSize.width = abs(newSize.width)
        newSize.height = abs(newSize.height)
        
        let renderer = UIGraphicsImageRenderer(size: newSize)
        let rotatedImage = renderer.image { context in
            let ctx = context.cgContext
            ctx.translateBy(x: newSize.width / 2, y: newSize.height / 2)
            ctx.rotate(by: radians)
            originalImage.draw(in: CGRect(
                x: -originalImage.size.width / 2,
                y: -originalImage.size.height / 2,
                width: originalImage.size.width,
                height: originalImage.size.height
            ))
        }
        
        guard let data = rotatedImage.jpegData(compressionQuality: 0.88) else { return false }
        do {
            try data.write(to: URL(fileURLWithPath: resolved), options: .atomic)
            print("✅ [ImageStorageHelper] Foto rotada y guardada localmente (sin nube): \(resolved)")
            return true
        } catch {
            print("❌ [ImageStorageHelper] Error guardando foto rotada: \(error)")
            return false
        }
    }
}

// MARK: - Hoja de Compartir Nativa de iOS (UIActivityViewController)
public struct ShareSheetView: UIViewControllerRepresentable {
    public let items: [Any]
    
    public init(items: [Any]) {
        self.items = items
    }
    
    public func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    
    public func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
