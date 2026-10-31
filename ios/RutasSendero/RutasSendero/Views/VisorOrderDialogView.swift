//
//  VisorOrderDialogView.swift
//  RutasSendero
//
//  Diálogo modal 1:1 con Android MainScreen.kt (líneas 2627-2735: showVisorOrderDialog).
//  Pregunta al senderista cómo desea ver la ruta (del Inicio al Final o del Final al Inicio)
//  antes de activar el modo visor y cargar los puntos de la ruta.
//

import SwiftUI

public struct VisorOrderDialogView: View {
    @Bindable var viewModel: MainViewModel
    let route: Route
    
    @State private var selectedToStart: Bool = false // false = Ida (Inicio al Final), true = Retorno (Final al Inicio)
    
    public init(viewModel: MainViewModel, route: Route) {
        self.viewModel = viewModel
        self.route = route
    }
    
    public var body: some View {
        ZStack {
            Color.black.opacity(0.65)
                .ignoresSafeArea()
                .onTapGesture {
                    viewModel.isShowingVisorOrderDialog = false
                    viewModel.pendingRouteForOrderSelection = nil
                }
            
            // Si el senderista está en rol Guía, respeta su estado y se comporta con los atributos de Guía
            let isGuideMode = (viewModel.activeRole == .guia)
            let accentColor = isGuideMode ? AppTheme.accent : Color(red: 56/255, green: 189/255, blue: 248/255)
            
            VStack(spacing: 16) {
                // Título
                VStack(spacing: 6) {
                    HStack {
                        Image(systemName: isGuideMode ? "figure.hiking" : "arrow.triangle.swap")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(accentColor)
                        
                        Text(isGuideMode ? "¿Cómo deseas realizar la ruta?" : "¿Cómo deseas ver la ruta?")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(AppTheme.textPrimary)
                    }
                    
                    Text(isGuideMode ? "Selecciona el sentido para iniciar la guía en el sendero:" : "Selecciona el orden para visualizar y simular los puntos:")
                        .font(.system(size: 13))
                        .foregroundStyle(AppTheme.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 8)
                }
                
                Divider()
                    .background(AppTheme.cardBorder)
                
                // Opciones de selección
                VStack(spacing: 10) {
                    // Opción 1: Al Final (Ida)
                    Button {
                        selectedToStart = false
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: !selectedToStart ? "largecircle.fill.circle" : "circle")
                                .font(.system(size: 20))
                                .foregroundStyle(!selectedToStart ? accentColor : AppTheme.textSecondary)
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text(isGuideMode ? "Al Final (Ida)" : "Del Inicio al Final (Ida)")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundStyle(AppTheme.textPrimary)
                                
                                Text(isGuideMode ? "Ir hacia el final del sendero." : "Ver los puntos desde el punto de partida inicial.")
                                    .font(.system(size: 12))
                                    .foregroundStyle(AppTheme.textSecondary)
                            }
                            
                            Spacer()
                        }
                        .padding(12)
                        .background(!selectedToStart ? accentColor.opacity(0.12) : AppTheme.cardMuted.opacity(0.4))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(!selectedToStart ? accentColor : Color.clear, lineWidth: 1.5)
                        )
                    }
                    .buttonStyle(.plain)
                    
                    // Opción 2: Al Inicio (Retorno)
                    Button {
                        selectedToStart = true
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: selectedToStart ? "largecircle.fill.circle" : "circle")
                                .font(.system(size: 20))
                                .foregroundStyle(selectedToStart ? accentColor : AppTheme.textSecondary)
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text(isGuideMode ? "Al Inicio (Retorno)" : "Del Final al Inicio (Retorno)")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundStyle(AppTheme.textPrimary)
                                
                                Text(isGuideMode ? "Regresar al inicio del sendero." : "Ver los puntos en sentido inverso (retorno al inicio).")
                                    .font(.system(size: 12))
                                    .foregroundStyle(AppTheme.textSecondary)
                            }
                            
                            Spacer()
                        }
                        .padding(12)
                        .background(selectedToStart ? accentColor.opacity(0.12) : AppTheme.cardMuted.opacity(0.4))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(selectedToStart ? accentColor : Color.clear, lineWidth: 1.5)
                        )
                    }
                    .buttonStyle(.plain)
                }
                
                // Botones de Acción
                HStack(spacing: 12) {
                    Button {
                        viewModel.isShowingVisorOrderDialog = false
                        viewModel.pendingRouteForOrderSelection = nil
                    } label: {
                        Text("Cancelar")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(AppTheme.textSecondary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(AppTheme.cardMuted)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                    
                    Button {
                        Task {
                            if isGuideMode {
                                await viewModel.startGuideRoute(route, toStart: selectedToStart)
                            } else {
                                await viewModel.selectRouteForTracking(route, reversed: selectedToStart, showMap: true)
                            }
                        }
                    } label: {
                        Text(isGuideMode ? "Comenzar" : "Ver Ruta")
                            .font(.system(size: 15, weight: .heavy))
                            .foregroundStyle(Color(red: 15/255, green: 23/255, blue: 42/255))
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(accentColor)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                            .shadow(color: accentColor.opacity(0.4), radius: 6, y: 3)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.top, 4)
            }
            .padding(20)
            .background(AppTheme.cardBackground)
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(AppTheme.cardBorder, lineWidth: 1.5)
            )
            .shadow(color: Color.black.opacity(0.5), radius: 20, y: 10)
            .padding(.horizontal, 24)
        }
    }
}
