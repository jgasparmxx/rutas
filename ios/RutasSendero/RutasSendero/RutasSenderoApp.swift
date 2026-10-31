//
//  RutasSenderoApp.swift
//  RutasSendero
//
//  Created by Jorge Gaspar on 10/19/26.
//

import SwiftUI
import SwiftData

@main
struct RutasSenderoApp: App {
    let container: ModelContainer
    
    init() {
        do {
            let schema = Schema([
                SDCacheRuta.self,
                SDPuntoRuta.self,
                SDTrazaRuta.self,
                SDSenderista.self
            ])
            let modelConfiguration = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
            container = try ModelContainer(for: schema, configurations: [modelConfiguration])
        } catch {
            fatalError("No se pudo inicializar ModelContainer de SwiftData: \(error.localizedDescription)")
        }
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .modelContainer(container)
    }
}
