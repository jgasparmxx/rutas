//
//  GuideEtaBadgeView.swift
//  RutasSendero
//
//  Barra informativa / Badge interactivo de tiempos estimados (ETA) y ritmo
//  de caminata para el Modo Guía en SwiftUI.
//

import SwiftUI

public enum GuideEtaBadgeStyle: Sendable {
    case bar
    case pill
}

public struct GuideEtaBadgeView: View {
    @Bindable var viewModel: MainViewModel
    public var style: GuideEtaBadgeStyle = .bar
    
    @State private var isPressed: Bool = false
    
    public init(viewModel: MainViewModel, style: GuideEtaBadgeStyle = .bar) {
        self.viewModel = viewModel
        self.style = style
    }
    
    private var isLate: Bool {
        viewModel.guideService.hasAccumulatedDelay()
    }
    
    public var body: some View {
        guard viewModel.activeRole == .guia && viewModel.isGuidingActive else {
            return AnyView(EmptyView())
        }
        
        let metaText = HikingGuideService.formatBadgeDuration(viewModel.guideService.metaEtaSeconds)
        let sigText = HikingGuideService.formatBadgeDuration(viewModel.guideService.targetEtaSeconds)
        let speedText = String(format: "%.1f", viewModel.guideService.currentSpeedKmh)
        
        return AnyView(
            Button {
                viewModel.announceCurrentGuidePaceStatus()
            } label: {
                if style == .pill {
                    pillLayout(metaText: metaText, sigText: sigText, speedText: speedText)
                } else {
                    barLayout(metaText: metaText, sigText: sigText, speedText: speedText)
                }
            }
            .buttonStyle(PaceBadgeButtonStyle())
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Estado de guía: Meta en \(metaText), siguiente punto en \(sigText), velocidad \(speedText) kilómetros por hora.")
            .accessibilityHint("Toca para escuchar el informe de voz y ritmo.")
        )
    }
    
    // MARK: - Diseño Estilo Barra (Para Dashboard / Panel Inferior)
    
    @ViewBuilder
    private func barLayout(metaText: String, sigText: String, speedText: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: isLate ? "exclamationmark.triangle.fill" : "stopwatch.fill")
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(isLate ? AppTheme.warningYellow : AppTheme.accent)
            
            Text("Meta: ~\(metaText)")
                .font(.system(size: 12, weight: .heavy, design: .rounded))
                .foregroundStyle(AppTheme.textPrimary)
            
            Text("·")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(AppTheme.textSecondary)
            
            Text("Sig: ~\(sigText)")
                .font(.system(size: 12, weight: .heavy, design: .rounded))
                .foregroundStyle(isLate ? AppTheme.warningYellow : AppTheme.accent)
            
            Text("·")
                .font(.system(size: 12, weight: .bold))
                .foregroundStyle(AppTheme.textSecondary)
            
            Text("\(speedText) km/h")
                .font(.system(size: 12, weight: .heavy, design: .rounded))
                .foregroundStyle(AppTheme.textPrimary)
            
            Spacer(minLength: 4)
            
            Image(systemName: "speaker.wave.2.fill")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(AppTheme.accent.opacity(0.85))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(isLate ? AppTheme.warningYellow.opacity(0.14) : AppTheme.cardMuted.opacity(0.85))
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(isLate ? AppTheme.warningYellow.opacity(0.4) : AppTheme.accent.opacity(0.3), lineWidth: 1)
                )
        )
    }
    
    // MARK: - Diseño Estilo Píldora (Para Barra Superior)
    
    @ViewBuilder
    private func pillLayout(metaText: String, sigText: String, speedText: String) -> some View {
        HStack(spacing: 5) {
            Text("⏱️")
                .font(.system(size: 11))
            
            Text("Meta: ~\(metaText) · Sig: ~\(sigText) · \(speedText) km/h")
                .font(.system(size: 11, weight: .heavy, design: .rounded))
                .foregroundStyle(isLate ? AppTheme.warningYellow : AppTheme.textPrimary)
                .lineLimit(1)
            
            Image(systemName: "speaker.wave.2")
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(AppTheme.accent)
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 4)
        .background(
            Capsule()
                .fill(isLate ? AppTheme.warningYellow.opacity(0.18) : AppTheme.cardMuted.opacity(0.92))
                .overlay(
                    Capsule()
                        .stroke(isLate ? AppTheme.warningYellow.opacity(0.4) : AppTheme.cardBorder, lineWidth: 1)
                )
        )
    }
}

/// Efecto táctil sutil para presionar el badge
private struct PaceBadgeButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1.0)
            .opacity(configuration.isPressed ? 0.85 : 1.0)
            .animation(.easeInOut(duration: 0.12), value: configuration.isPressed)
    }
}
