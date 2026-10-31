//
//  Theme.swift
//  RutasSendero
//
//  Sistema de diseño y tokens visuales para RutasSendero.
//  Inspirado en la temática Forest / Mountain con acabados modernos en SwiftUI.
//

import SwiftUI

public enum AppTheme {
    // MARK: - Paleta de Colores Tekins (1:1 Android)
    
    /// ColorTekinsPrimary: Verde azulado oscuro (Teal 700) para ambiente bosque/senderismo (#0F766E)
    public static let primary = Color(red: 15/255, green: 118/255, blue: 110/255)
    public static let primaryLight = Color(red: 20/255, green: 184/255, blue: 166/255) // Teal 500 (#14B8A6)
    
    /// ColorTekinsAccent: Menta brillante (Teal 400) para relieve y elementos activos (#2DD4BF)
    public static let accent = Color(red: 45/255, green: 212/255, blue: 191/255)
    
    /// colorAzulCeleste: Azul celeste brillante (Sky 400) para simulación de recorrido virtual y GPS (#38BDF8)
    public static let accentCyan = Color(red: 56/255, green: 189/255, blue: 248/255)
    public static let accentCyanDark = Color(red: 2/255, green: 132/255, blue: 199/255) // Sky 600 (#0284C7)
    
    /// ColorTekinsDarkBg: Fondo oscuro Slate-900 (#0F172A)
    public static let darkBackground = Color(red: 15/255, green: 23/255, blue: 42/255)
    
    /// ColorTekinsCardBg: Tarjetas Slate-800 (#1E293B)
    public static let cardBackground = Color(red: 30/255, green: 41/255, blue: 59/255)
    
    /// ColorTekinsCardMuted: Tarjetas inactivas Slate-700 (#334155)
    public static let cardMuted = Color(red: 51/255, green: 65/255, blue: 85/255)
    
    /// Borde de tarjetas con brillo sutil de acento Tekins (#2DD4BF con alpha 0.25)
    public static let cardBorder = Color(red: 45/255, green: 212/255, blue: 191/255).opacity(0.25)
    
    /// ColorTekinsAlert: Rose-500 para alertas y 911 (#F43F5E)
    public static let alert = Color(red: 244/255, green: 63/255, blue: 94/255)
    public static let alertDark = Color(red: 225/255, green: 29/255, blue: 72/255) // Rose-600 (#E11D48)
    
    /// Amarillo de alta visibilidad para registro (#FACC15)
    public static let warningYellow = Color(red: 250/255, green: 204/255, blue: 21/255)
    public static let warningOrange = Color(red: 249/255, green: 115/255, blue: 22/255) // Orange 500 (#F97316)
    
    /// Fondo crema suave para chips de estado en listas (#FFF9C4)
    public static let chipYellowBg = Color(red: 255/255, green: 249/255, blue: 196/255)
    
    /// ColorTekinsSuccess: Esmeralda éxito (#10B981)
    public static let success = Color(red: 16/255, green: 185/255, blue: 129/255)
    
    /// ColorTextoPrincipal: Slate-50 (#F8FAFC)
    public static let textPrimary = Color(red: 248/255, green: 250/255, blue: 252/255)
    
    /// ColorTextoSecundario: Slate-400 (#94A3B8)
    public static let textSecondary = Color(red: 148/255, green: 163/255, blue: 184/255)
    
    // MARK: - Gradientes Dinámicos 1:1 Android (dynamicGradient)
    
    /// Degradado de fondo según el rol o modo activo (1:1 Android dynamicGradient)
    public static func dynamicGradient(for role: AppRole) -> LinearGradient {
        let colors: [Color] = {
            switch role {
            case .visor:
                return [
                    Color(red: 232/255, green: 245/255, blue: 233/255), // #E8F5E9 (Verde menta muy suave)
                    Color(red: 200/255, green: 230/255, blue: 201/255), // #C8E6C9 (Verde suave)
                    Color(red: 129/255, green: 199/255, blue: 132/255)  // #81C784 (Verde hoja suave)
                ]
            case .guia:
                return [
                    Color(red: 250/255, green: 249/255, blue: 246/255), // #FAF9F6 (Blanco hueso)
                    Color(red: 208/255, green: 225/255, blue: 249/255), // #D0E1F9 (Azul hielo suave)
                    Color(red: 77/255, green: 100/255, blue: 141/255)   // #4D648D (Azul grisáceo)
                ]
            case .registrar:
                return [
                    Color.white,                                        // #FFFFFF (Blanco)
                    Color(red: 241/255, green: 245/255, blue: 249/255), // #F1F5F9 (Gris pizarra muy claro)
                    Color(red: 203/255, green: 213/255, blue: 225/255)  // #CBD5E1 (Gris pizarra claro)
                ]
            }
        }()
        return LinearGradient(colors: colors, startPoint: .top, endPoint: .bottom)
    }
    
    /// Color distintivo principal según el rol activo (1:1 Android activeColor)
    public static func activeColor(for role: AppRole) -> Color {
        switch role {
        case .guia:
            return Color(red: 71/255, green: 85/255, blue: 105/255) // Slate 600 #475569
        case .registrar:
            return Color(red: 21/255, green: 128/255, blue: 61/255) // Green 700 #15803D
        case .visor:
            return Color(red: 15/255, green: 118/255, blue: 110/255) // Teal 700 #0F766E
        }
    }
    
    /// Color de fondo para Header flotante según el rol (1:1 Android headerBgColor)
    public static func headerBgColor(for role: AppRole) -> Color {
        switch role {
        case .guia:
            return Color(red: 100/255, green: 116/255, blue: 139/255).opacity(0.15)
        case .registrar:
            return Color(red: 250/255, green: 249/255, blue: 246/255).opacity(0.12)
        case .visor:
            return accent.opacity(0.15)
        }
    }
    
    /// Color de borde para Header flotante según el rol (1:1 Android headerBorderColor)
    public static func headerBorderColor(for role: AppRole) -> Color {
        switch role {
        case .guia:
            return Color(red: 100/255, green: 116/255, blue: 139/255).opacity(0.4)
        case .registrar:
            return Color(red: 250/255, green: 249/255, blue: 246/255).opacity(0.4)
        case .visor:
            return accent.opacity(0.4)
        }
    }
    
    public static let trailGradient = LinearGradient(
        colors: [accent, accentCyan],
        startPoint: .leading,
        endPoint: .trailing
    )
    
    public static let cardGradient = LinearGradient(
        colors: [cardBackground.opacity(0.95), cardBackground.opacity(0.85)],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
    
    public static let recordingGlow = RadialGradient(
        colors: [alert.opacity(0.3), Color.clear],
        center: .center,
        startRadius: 5,
        endRadius: 35
    )
}

// MARK: - Modificadores de Estilo
public struct GlassCardModifier: ViewModifier {
    var cornerRadius: CGFloat = 16
    var borderColor: Color = AppTheme.cardBorder
    
    public func body(content: Content) -> some View {
        content
            .background(.ultraThinMaterial)
            .background(AppTheme.cardBackground.opacity(0.75))
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(borderColor, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.2), radius: 8, x: 0, y: 4)
    }
}

public extension View {
    func glassCardStyle(cornerRadius: CGFloat = 16, borderColor: Color = AppTheme.cardBorder) -> some View {
        self.modifier(GlassCardModifier(cornerRadius: cornerRadius, borderColor: borderColor))
    }
}

// MARK: - Información y Visualización de Estados de Puntos (1:1 con Kotlin getEstadoPuntoInfo)

public struct PointStateInfo: Sendable, Equatable {
    public let estado: Int
    public let name: String
    public let systemImage: String
    public let color: Color
    public let backgroundColor: Color
    
    public init(estado: Int, name: String, systemImage: String, color: Color, backgroundColor: Color) {
        self.estado = estado
        self.name = name
        self.systemImage = systemImage
        self.color = color
        self.backgroundColor = backgroundColor
    }
    
    public static func forPoint(estado: Int, isRouteEnd: Bool = false, description: String = "") -> PointStateInfo {
        if isRouteEnd {
            return PointStateInfo(
                estado: 0,
                name: "Fin de ruta",
                systemImage: "flag.checkered",
                color: Color(red: 211/255, green: 47/255, blue: 47/255), // #D32F2F (1:1 Android)
                backgroundColor: AppTheme.chipYellowBg
            )
        }
        
        var effectiveEstado = estado
        if effectiveEstado == 1 && !description.isEmpty {
            let lower = description.lowercased()
            if lower.contains("bifurcac") || lower.contains("unión de dos ríos") || lower.contains("union de dos rios") {
                effectiveEstado = 3
            } else if lower.contains("suelo flojo") || lower.contains("resbalos") {
                effectiveEstado = 4
            } else if lower.contains("cuidado") || lower.contains("precauci") {
                effectiveEstado = 5
            } else if lower.contains("alerta") || lower.contains("peligro") {
                effectiveEstado = 6
            } else if lower.contains("vehiculo") || lower.contains("vehículo") || lower.contains("carro") {
                effectiveEstado = 8
            } else if lower.contains("refugio") || lower.contains("campamento") {
                effectiveEstado = 9
            } else if lower.contains("comida") || lower.contains("restaurante") {
                effectiveEstado = 10
            } else if lower.contains("interes") || lower.contains("interés") || lower.contains("mirador") || lower.contains("arroyo") || lower.contains("ranchito") {
                effectiveEstado = 2
            }
        }
        
        switch effectiveEstado {
        case 1:
            return PointStateInfo(
                estado: 1,
                name: "Punto normal",
                systemImage: "mappin.and.ellipse",
                color: Color(red: 25/255, green: 118/255, blue: 210/255), // Azul #1976D2
                backgroundColor: AppTheme.chipYellowBg
            )
        case 2:
            return PointStateInfo(
                estado: 2,
                name: "Sitio de interés",
                systemImage: "binoculars.fill",
                color: Color(red: 46/255, green: 125/255, blue: 50/255), // Verde bosque #2E7D32
                backgroundColor: AppTheme.chipYellowBg
            )
        case 3:
            return PointStateInfo(
                estado: 3,
                name: "Bifurcación",
                systemImage: "arrow.triangle.branch",
                color: Color(red: 94/255, green: 53/255, blue: 177/255), // Morado #5E35B1
                backgroundColor: AppTheme.chipYellowBg
            )
        case 4:
            return PointStateInfo(
                estado: 4,
                name: "Suelo flojo",
                systemImage: "figure.fall",
                color: Color(red: 141/255, green: 110/255, blue: 99/255), // Marrón tierra #8D6E63
                backgroundColor: AppTheme.chipYellowBg
            )
        case 5:
            return PointStateInfo(
                estado: 5,
                name: "Cuidado",
                systemImage: "exclamationmark.triangle.fill",
                color: Color(red: 251/255, green: 192/255, blue: 45/255), // Amarillo #FBC02D
                backgroundColor: AppTheme.chipYellowBg
            )
        case 6:
            return PointStateInfo(
                estado: 6,
                name: "Alerta",
                systemImage: "skull.fill",
                color: Color(red: 211/255, green: 47/255, blue: 47/255), // Rojo calavera #D32F2F
                backgroundColor: AppTheme.chipYellowBg
            )
        case 8:
            return PointStateInfo(
                estado: 8,
                name: "Vehículo",
                systemImage: "car.fill",
                color: Color(red: 96/255, green: 125/255, blue: 139/255), // Gris acero #607D8B
                backgroundColor: AppTheme.chipYellowBg
            )
        case 9:
            return PointStateInfo(
                estado: 9,
                name: "Refugio",
                systemImage: "tent.fill",
                color: Color(red: 78/255, green: 52/255, blue: 46/255), // Marrón oscuro #4E342E
                backgroundColor: AppTheme.chipYellowBg
            )
        case 10:
            return PointStateInfo(
                estado: 10,
                name: "Comida",
                systemImage: "fork.knife",
                color: Color(red: 230/255, green: 81/255, blue: 0/255), // Naranja #E65100
                backgroundColor: AppTheme.chipYellowBg
            )
        default:
            return PointStateInfo(
                estado: 1,
                name: "Punto normal",
                systemImage: "mappin.and.ellipse",
                color: Color(red: 25/255, green: 118/255, blue: 210/255),
                backgroundColor: AppTheme.chipYellowBg
            )
        }
    }
}

public struct PointStateBadgeView: View {
    let estado: Int
    var isRouteEnd: Bool = false
    var description: String = ""
    var size: CGFloat = 46
    
    public init(estado: Int, isRouteEnd: Bool = false, description: String = "", size: CGFloat = 46) {
        self.estado = estado
        self.isRouteEnd = isRouteEnd
        self.description = description
        self.size = size
    }
    
    public var body: some View {
        let info = PointStateInfo.forPoint(estado: estado, isRouteEnd: isRouteEnd, description: description)
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.22)
                .fill(AppTheme.chipYellowBg)
                .frame(width: size, height: size)
                .overlay(
                    RoundedRectangle(cornerRadius: size * 0.22)
                        .stroke(info.color.opacity(0.35), lineWidth: 1)
                )
            
            Image(systemName: info.systemImage)
                .font(.system(size: size * 0.48, weight: .bold))
                .foregroundStyle(info.color)
        }
    }
}

