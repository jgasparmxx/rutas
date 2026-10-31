import Testing
import Foundation
import CoreLocation
import UIKit
@testable import RutasSendero

struct RutasSenderoTests {

    // MARK: - 1. Pruebas de Propiedad y Modo de Solo Consumo
    
    @Test func testRouteOwnershipLocalVsCloud() async throws {
        let userPhone = "3121234567"
        
        // Ruta creada localmente por el usuario
        let localOwnRoute = Route(
            id: "RUTA01",
            name: "Sendero El Chanal",
            date: "20/10/2026",
            originLatitude: 19.2458,
            originLongitude: -103.7251,
            creatorPhone: userPhone,
            importDate: nil
        )
        #expect(localOwnRoute.isOwnRoute(userPhone: userPhone) == true)
        
        // Ruta creada localmente sin teléfono aún asignado
        let localBlankRoute = Route(
            id: "RUTA02",
            name: "Ruta Local Sin Teléfono",
            date: "20/10/2026",
            originLatitude: 19.2458,
            originLongitude: -103.7251,
            creatorPhone: "",
            importDate: nil
        )
        #expect(localBlankRoute.isOwnRoute(userPhone: userPhone) == true)
        
        // Ruta importada de la nube de otro senderista
        let cloudOtherRoute = Route(
            id: "RUTA03",
            name: "Cascada La Media Luna",
            date: "15/10/2026",
            originLatitude: 19.3000,
            originLongitude: -103.8000,
            creatorPhone: "3129998877",
            importDate: 1729440000000
        )
        #expect(cloudOtherRoute.isOwnRoute(userPhone: userPhone) == false)
    }

    // MARK: - 2. Pruebas de Decodificación de Baliza iBeacon y Alerta 911
    
    @Test func testBeaconPayloadEmergencyMinor911() async throws {
        let idBeacon = "c0qu1m4tl4n1" // 6 bytes = 12 hex
        let lat: Float = 19.2458
        let lon: Float = -103.7251
        
        // Construir datos de fabricante simulando paquete emitido por Android / iOS
        var rawData = Data([0x4C, 0x00, 0x02, 0x15]) // Company ID Apple + Type iBeacon
        
        // Proximity UUID (16 bytes): idBeacon (6B) + Lat (4B) + Lon (4B) + Magic 0xBEAC (2B)
        let idBytes: [UInt8] = [0xC0, 0xDE, 0x1A, 0x2B, 0x3C, 0x4D]
        rawData.append(contentsOf: idBytes)
        
        var latBits = lat.bitPattern.bigEndian
        rawData.append(Data(bytes: &latBits, count: 4))
        
        var lonBits = lon.bitPattern.bigEndian
        rawData.append(Data(bytes: &lonBits, count: 4))
        
        rawData.append(contentsOf: [0xBE, 0xAC]) // Magic signature
        
        // Major = 1, Minor = 911 (Emergencia 1:1 Android Kotlin)
        let majorBytes: [UInt8] = [0x00, 0x01]
        let minorBytes911: [UInt8] = [0x03, 0x8F] // 911 en hex = 0x038F
        let txPowerByte: UInt8 = 0xC5 // -59 dBm
        
        rawData.append(contentsOf: majorBytes)
        rawData.append(contentsOf: minorBytes911)
        rawData.append(txPowerByte)
        
        let parsed = BeaconPayload.parse(manufacturerData: rawData)
        #expect(parsed != nil)
        #expect(parsed?.minor == 911)
        #expect(parsed?.isEmergency == true)
        
        // Caso Normal: Minor = 1
        var rawDataNormal = rawData
        rawDataNormal[22] = 0x00
        rawDataNormal[23] = 0x01
        let parsedNormal = BeaconPayload.parse(manufacturerData: rawDataNormal)
        #expect(parsedNormal != nil)
        #expect(parsedNormal?.minor == 1)
        #expect(parsedNormal?.isEmergency == false)
    }

    // MARK: - 3. Pruebas de Máquina de Estados del ViewModel (Visor y Guía)
    
    @MainActor
    @Test func testSelectionOfOtherRouteActivatesVisorAndConsumptionMode() async throws {
        let viewModel = MainViewModel()
        let profile = HikerProfile(
            email: "guia@rutas.com",
            name: "Guía Oficial",
            phone: "3121234567",
            role: "G",
            emergencyPhone1: "3120000000"
        )
        viewModel.currentHikerProfile = profile
        
        let foreignRoute = Route(
            id: "RUTACLOUD99",
            name: "Volcán de Colima - Ruta Norte",
            date: "18/10/2026",
            originLatitude: 19.5123,
            originLongitude: -103.6234,
            creatorPhone: "3128887766", // Teléfono de otro senderista
            importDate: 1729440000000
        )
        
        await viewModel.selectRouteForTracking(foreignRoute)
        
        // Al seleccionar la ruta de otro senderista:
        // 1. Debe activarse el modo VISOR por omisión
        #expect(viewModel.activeRole == .visor)
        // 2. Debe marcarse como ruta de solo consumo
        #expect(viewModel.isRouteConsumptionOnly == true)
        // 3. No se pueden agregar puntos a esta ruta
        let initialCount = viewModel.recordedPoints.count
        viewModel.addPoint(description: "Intento no permitido")
        #expect(viewModel.recordedPoints.count == initialCount)
    }

    @MainActor
    @Test func testVirtualTourControls() async throws {
        let viewModel = MainViewModel()
        viewModel.activeRole = .visor
        
        let dummyPoint1 = RoutePoint(id: 1, routeId: "R1", timestamp: "10:00", latitude: 19.24, longitude: -103.72, altitude: 500, descriptionText: "Inicio", photoUrl: nil, isRouteEnd: false, hasConnectivity: true)
        let dummyPoint2 = RoutePoint(id: 2, routeId: "R1", timestamp: "10:15", latitude: 19.25, longitude: -103.73, altitude: 550, descriptionText: "Mirador", photoUrl: nil, isRouteEnd: false, hasConnectivity: true)
        let dummyPoint3 = RoutePoint(id: 3, routeId: "R1", timestamp: "10:30", latitude: 19.26, longitude: -103.74, altitude: 600, descriptionText: "Fin", photoUrl: nil, isRouteEnd: true, hasConnectivity: true)
        viewModel.recordedPoints = [dummyPoint1, dummyPoint2, dummyPoint3]
        
        // Iniciar Recorrido Virtual
        viewModel.startVirtualTour()
        #expect(viewModel.isVirtualTourActive == true)
        #expect(viewModel.virtualTourIndex == 0)
        #expect(viewModel.showMapScreen == true)
        
        // Pausar
        viewModel.togglePauseVirtualTour()
        #expect(viewModel.isVirtualTourPaused == true)
        
        // Reanudar
        viewModel.togglePauseVirtualTour()
        #expect(viewModel.isVirtualTourPaused == false)
        
        // Avanzar manual
        viewModel.nextVirtualTourPoint()
        #expect(viewModel.virtualTourIndex == 1)
        
        // Detener
        viewModel.stopVirtualTour()
        #expect(viewModel.isVirtualTourActive == false)
        #expect(viewModel.virtualTourIndex == 0)
        
        // Verificación crítica (Regla de negocio 1:1 solicitud usuario):
        // En modo GUÍA nunca se debe permitir simular recorrido (Play). Es exclusivo de Visor.
        viewModel.activeRole = .guia
        viewModel.startVirtualTour()
        #expect(viewModel.isVirtualTourActive == false)
        viewModel.togglePauseVirtualTour()
        #expect(viewModel.isVirtualTourActive == false)
    }

    @MainActor
    @Test func testGuiaMode911Emergency() async throws {
        let viewModel = MainViewModel()
        viewModel.activeRole = .guia
        
        #expect(viewModel.isEmergency911Active == false)
        
        // Activar 911
        viewModel.toggleEmergency911()
        #expect(viewModel.isEmergency911Active == true)
        #expect(viewModel.isBeaconEmitting == true)
        #expect(viewModel.saveStatusMessage?.contains("911") == true)
        
        // Cancelar 911
        viewModel.toggleEmergency911()
        #expect(viewModel.isEmergency911Active == false)
    }

    @MainActor
    @Test func testVisorOrderSelectionAndDefaultListView() async throws {
        let viewModel = MainViewModel()
        let testRoute = Route(
            id: "R-ORDER-TEST",
            name: "Ruta Volcán de Colima",
            date: "20/10/2026",
            originLatitude: 19.5,
            originLongitude: -103.6,
            creatorPhone: "3129998877",
            importDate: nil
        )
        let p1 = RoutePoint(id: 1, routeId: "R-ORDER-TEST", timestamp: "08:00", latitude: 19.5, longitude: -103.6, altitude: 2000, descriptionText: "Inicio La Joya", photoUrl: nil, isRouteEnd: false, hasConnectivity: true)
        let p2 = RoutePoint(id: 2, routeId: "R-ORDER-TEST", timestamp: "08:30", latitude: 19.51, longitude: -103.61, altitude: 2500, descriptionText: "El Refugio", photoUrl: nil, isRouteEnd: false, hasConnectivity: true)
        let p3 = RoutePoint(id: 3, routeId: "R-ORDER-TEST", timestamp: "09:00", latitude: 19.52, longitude: -103.62, altitude: 3000, descriptionText: "Cumbre", photoUrl: nil, isRouteEnd: true, hasConnectivity: true)

        // 1. Selección de sentido Ida (Inicio al Final)
        await viewModel.selectRouteForTracking(testRoute, reversed: false)
        viewModel.recordedPoints = [p1, p2, p3]
        
        #expect(viewModel.activeRole == .visor)
        #expect(viewModel.isVisorReversed == false)
        #expect(viewModel.showMapScreen == false) // Por omisión muestra la lista de puntos, no el mapa pesado
        #expect(viewModel.tourPoints.first?.id == 1)
        #expect(viewModel.tourPoints.last?.id == 3)
        
        // 2. Selección de sentido Retorno (Final al Inicio)
        await viewModel.selectRouteForTracking(testRoute, reversed: true)
        viewModel.recordedPoints = [p1, p2, p3]
        #expect(viewModel.isVisorReversed == true)
        #expect(viewModel.showMapScreen == false)
        #expect(viewModel.tourPoints.first?.id == 3)
        #expect(viewModel.tourPoints.last?.id == 1)
        
        // 3. Navegación de detalle de punto
        viewModel.selectPointForDetail(p3)
        #expect(viewModel.selectedPointForDetail?.id == 3)
        viewModel.nextPointDetail()
        #expect(viewModel.selectedPointForDetail?.id == 2)
        viewModel.nextPointDetail()
        #expect(viewModel.selectedPointForDetail?.id == 1)
    }

    @Test func testLocalPhysicalImageRotation() async throws {
        // Crear imagen de prueba 40x20
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 40, height: 20))
        let sampleImage = renderer.image { ctx in
            UIColor.blue.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 40, height: 20))
        }
        
        // Guardar localmente
        guard let savedPath = ImageStorageHelper.saveImageLocally(sampleImage) else {
            Issue.record("No se pudo guardar la imagen de prueba")
            return
        }
        
        // Aplicar rotación física de 90° (local, sin subir a la nube)
        let rotated = ImageStorageHelper.rotateAndSavePhysically(filePath: savedPath, degrees: 90)
        #expect(rotated == true)
        
        // Cargar imagen rotada y validar dimensiones (debe invertirse a ~20x40)
        let loaded = ImageStorageHelper.loadImage(from: savedPath)
        #expect(loaded != nil)
        if let img = loaded {
            #expect(img.size.height > img.size.width)
        }
        
        // Limpiar archivo de prueba
        try? FileManager.default.removeItem(atPath: savedPath)
    }

    @MainActor
    @Test func testGuiaModePreservedWhenStartingGuideRouteForForeignRoute() async throws {
        let viewModel = MainViewModel()
        let profile = HikerProfile(
            email: "guia_oficial@rutas.com",
            name: "Guía Certificado",
            phone: "3121112233",
            role: "G",
            emergencyPhone1: "3120000000"
        )
        viewModel.currentHikerProfile = profile
        viewModel.activeRole = .guia
        
        let otherRoute = Route(
            id: "R-OTRA-999",
            name: "Cañón del Zaus",
            date: "22/10/2026",
            originLatitude: 19.3456,
            originLongitude: -103.7890,
            creatorPhone: "3124445566", // Otro creador
            importDate: 1729500000000
        )
        
        #expect(otherRoute.isOwnRoute(userPhone: profile.phone) == false)
        
        // Simular que el Guía inicia la guía de esta otra ruta (toStart = false: del inicio al final)
        await viewModel.startGuideRoute(otherRoute, toStart: false)
        
        // Verificaciones críticas:
        // 1. Debe mantenerse estrictamente en modo Guía (no degradarse a Visor)
        #expect(viewModel.activeRole == .guia)
        #expect(viewModel.isGuidingActive == true)
        
        // 2. Debe marcarse como de solo consumo (no se puede editar la ruta de otro creador)
        #expect(viewModel.isRouteConsumptionOnly == true)
        
        // 3. Debe tener la ruta activa asignada y mostrar pantalla de mapa
        #expect(viewModel.activeRoute?.id == otherRoute.id)
        #expect(viewModel.showMapScreen == true)
        
        // 4. localRoutesInitialTab selector
        viewModel.localRoutesInitialTab = 1
        #expect(viewModel.localRoutesInitialTab == 1)
        viewModel.localRoutesInitialTab = 0
        #expect(viewModel.localRoutesInitialTab == 0)
    }
}

