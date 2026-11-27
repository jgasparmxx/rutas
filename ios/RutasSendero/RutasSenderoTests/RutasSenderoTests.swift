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

    // MARK: - 4. Pruebas de Ritmo de Caminata (EMA), ETAs y Evaluación de Puntualidad en Modo Guía
    
    @Test func testHikingPaceCalculatorSmoothingAndBounds() async throws {
        let pace = HikingPaceCalculator()
        pace.reset()
        
        // 1. Estado inicial detenido: velocidad efectiva debe ser 1.11 m/s (4.0 km/h)
        #expect(pace.effectiveWalkingSpeedMps == HikingPaceCalculator.defaultWalkingSpeedMps)
        #expect(abs(pace.effectiveWalkingSpeedKmh - 4.0) < 0.05)
        
        // 2. Reporte de velocidad en reposo (< 0.35 m/s): se mantiene la base efectiva
        pace.updateSpeed(gpsSpeedMps: 0.1)
        #expect(pace.effectiveWalkingSpeedMps == HikingPaceCalculator.defaultWalkingSpeedMps)
        
        // 3. Primer fix de caminata (1.2 m/s = 4.32 km/h): adopta el valor inicial
        pace.updateSpeed(gpsSpeedMps: 1.2)
        #expect(abs(pace.smoothedSpeedMps - 1.2) < 0.001)
        
        // 4. Segundo fix con EMA: nueva muestra 1.6 m/s
        // EMA = 0.25 * 1.6 + 0.75 * 1.2 = 0.40 + 0.90 = 1.30 m/s
        pace.updateSpeed(gpsSpeedMps: 1.6)
        #expect(abs(pace.smoothedSpeedMps - 1.3) < 0.001)
        
        // 5. Pico anormal de velocidad (> 3.5 m/s, ej. auto o salto GPS 10.0 m/s): debe acotarse a 3.5 m/s
        // EMA = 0.25 * 3.5 + 0.75 * 1.3 = 0.875 + 0.975 = 1.85 m/s
        pace.updateSpeed(gpsSpeedMps: 10.0)
        #expect(abs(pace.smoothedSpeedMps - 1.85) < 0.001)
    }

    @Test func testHikingGuideServiceEtasAndArrivalTiming() async throws {
        let service = HikingGuideService()
        
        let p1 = RoutePoint(id: 1, routeId: "R_ETA", timestamp: "08:00", latitude: 19.2400, longitude: -103.7200, altitude: 500, descriptionText: "Inicio", photoUrl: nil, isRouteEnd: false, hasConnectivity: true)
        let p2 = RoutePoint(id: 2, routeId: "R_ETA", timestamp: "08:15", latitude: 19.2450, longitude: -103.7200, altitude: 520, descriptionText: "Paso Intermedio", photoUrl: nil, isRouteEnd: false, hasConnectivity: true)
        let p3 = RoutePoint(id: 3, routeId: "R_ETA", timestamp: "08:30", latitude: 19.2500, longitude: -103.7200, altitude: 550, descriptionText: "Meta Final", photoUrl: nil, isRouteEnd: true, hasConnectivity: true)
        let points = [p1, p2, p3]
        
        let startLoc = CLLocation(latitude: 19.2400, longitude: -103.7200)
        service.startGuidance(
            currentLocation: startLoc,
            target: p2,
            targetIndex: 1,
            points: points,
            forward: true
        )
        
        // 1. Verificación de cálculo de distancias y marcas esperadas
        #expect(service.targetDistanceMeters > 0.0)
        #expect(service.metaRemainingDistanceMeters > service.targetDistanceMeters)
        #expect(service.targetExpectedArrivalTime != nil)
        #expect(service.metaExpectedArrivalTime != nil)
        #expect(service.targetExpectedSpeedKmh > 0.0)
        
        // 2. Evaluación de llegada a hito intermedio:
        guard let targetExpected = service.targetExpectedArrivalTime else {
            Issue.record("targetExpectedArrivalTime no debe ser nil")
            return
        }
        
        // A. Llegada TEMPRANA (15s o más de anticipación)
        let earlyArrivalDate = targetExpected.addingTimeInterval(-20.0)
        let earlyTiming = service.evaluateWaypointArrival(arrivalDate: earlyArrivalDate)
        switch earlyTiming {
        case .early(let secs, _):
            #expect(abs(secs - 20.0) < 0.1)
        default:
            Issue.record("Se esperaba .early, se obtuvo \(earlyTiming)")
        }
        
        // B. Llegada TARDE (30s o más de retraso)
        let lateArrivalDate = targetExpected.addingTimeInterval(35.0)
        let lateTiming = service.evaluateWaypointArrival(arrivalDate: lateArrivalDate)
        switch lateTiming {
        case .late(let secs, _):
            #expect(abs(secs - 35.0) < 0.1)
        default:
            Issue.record("Se esperaba .late, se obtuvo \(lateTiming)")
        }
        
        // C. Llegada A TIEMPO NORMAL (dentro del margen -15s a +30s)
        let onTimeArrivalDate = targetExpected.addingTimeInterval(5.0)
        let onTimeTiming = service.evaluateWaypointArrival(arrivalDate: onTimeArrivalDate)
        switch onTimeTiming {
        case .onTime(let diff):
            #expect(abs(diff - 5.0) < 0.1)
        default:
            Issue.record("Se esperaba .onTime, se obtuvo \(onTimeTiming)")
        }
        
        // 3. Evaluación de llegada a la META FINAL
        guard let metaExpected = service.metaExpectedArrivalTime else {
            Issue.record("metaExpectedArrivalTime no debe ser nil")
            return
        }
        let earlyMeta = service.evaluateMetaArrival(arrivalDate: metaExpected.addingTimeInterval(-18.0))
        #expect(earlyMeta != .onTime(diffSeconds: 0))
        
        let lateMeta = service.evaluateMetaArrival(arrivalDate: metaExpected.addingTimeInterval(45.0))
        #expect(lateMeta != .onTime(diffSeconds: 0))
        
        // 4. Verificación de formato interactivo para Badge
        let badgeText = service.interactiveBadgeText
        #expect(badgeText.contains("Meta: ~"))
        #expect(badgeText.contains("Sig: ~"))
        #expect(badgeText.contains("km/h"))
        
        // 5. Verificación de informe de voz bajo demanda
        let speechOnTime = service.generateInteractiveStatusSpeech(targetName: "Paso Intermedio", now: targetExpected.addingTimeInterval(-5.0))
        #expect(speechOnTime.contains("Velocidad actual:"))
        #expect(speechOnTime.contains("Paso Intermedio"))
        #expect(speechOnTime.contains("¡Apura el paso!") == false)
        
        let speechLate = service.generateInteractiveStatusSpeech(targetName: "Paso Intermedio", now: targetExpected.addingTimeInterval(40.0))
        #expect(speechLate.contains("retraso"))
        #expect(speechLate.contains("¡Apura el paso!"))
    }

    @MainActor
    @Test func testModoVisorIsStrictlyPassive() async throws {
        let viewModel = MainViewModel()
        viewModel.activeRole = .guia
        viewModel.isGuidingActive = true
        
        // Al cambiar a Modo Visor:
        viewModel.activeRole = .visor
        
        // Esperar ciclo de despacho asíncrono
        try? await Task.sleep(nanoseconds: 10_000_000)
        
        // 1. Debe detener la guía
        #expect(viewModel.isGuidingActive == false)
        #expect(viewModel.targetGuideWaypoint == nil)
        
        // 2. Debe reiniciar el servicio de guiado y ETAs
        #expect(viewModel.guideService.targetExpectedArrivalTime == nil)
        #expect(viewModel.guideService.metaExpectedArrivalTime == nil)
        
        // 3. No debe tener navegación de alta precisión en segundo plano
        #expect(viewModel.locationTracker.isTracking == false)
    }

    // MARK: - 8. Pruebas de Reglas de Negocio de Salidas, Fechas Futuras y Privacidad
    
    @Test func testOutingDateUtilsFutureAndPastFilters() async throws {
        // Fechas pasadas (deben descartarse)
        let pastDate1 = "2020-01-01 10:00:00"
        let pastDate2 = "2026-10-13 08:30:00"
        #expect(OutingDateUtils.isFutureOrCurrent(pastDate1) == false)
        #expect(OutingDateUtils.isFutureOrCurrent(pastDate2) == false)
        
        // Fechas futuras lejanas (deben aceptarse)
        let futureDate1 = "2030-12-31 23:59:59"
        let futureDate2 = "2030-05-15T09:00:00"
        #expect(OutingDateUtils.isFutureOrCurrent(futureDate1) == true)
        #expect(OutingDateUtils.isFutureOrCurrent(futureDate2) == true)
        
        // Fecha actual o muy cercana (margen de gracia de 6 horas)
        let now = Date()
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd HH:mm:ss"
        df.locale = Locale(identifier: "en_US_POSIX")
        let todayStr = df.string(from: now)
        #expect(OutingDateUtils.isFutureOrCurrent(todayStr) == true)
        
        let threeHoursAgo = df.string(from: now.addingTimeInterval(-3 * 3600))
        #expect(OutingDateUtils.isFutureOrCurrent(threeHoursAgo) == true) // dentro del margen de 6h
        
        let sevenHoursAgo = df.string(from: now.addingTimeInterval(-7 * 3600))
        #expect(OutingDateUtils.isFutureOrCurrent(sevenHoursAgo) == false) // fuera del margen de 6h
    }

    @MainActor
    @Test func testOutingPrivacyAndGroupRules() async throws {
        let viewModel = MainViewModel()
        
        // Usuario registrado en la agrupación "MH" (Miguel Hidalgo)
        viewModel.currentHikerProfile = HikerProfile(
            email: "senderista@rutas.com",
            name: "Senderista MH",
            phone: "3121112233",
            idAgrupacion: "MH",
            emergencyPhone1: "3120000000"
        )
        
        let farFutureDate = "2030-06-01 07:00:00"
        
        // 1. Evento público (esPublico = true) -> Visible para todos
        let publicEvent = OutingInvitation(
            idInvitacion: -1,
            idRuta: "EVENTO",
            nombreRuta: "Gran Caminata Abierta",
            telefonoCreador: "3129998877",
            fechaHoraSalida: farFutureDate,
            puntoReunion: "Plaza Central",
            tipoRuta: "E",
            esPublico: true,
            idAgrupacion: nil
        )
        
        // 2. Evento privado para la misma agrupación "MH" -> Visible
        let privateEventSameGroup = OutingInvitation(
            idInvitacion: -2,
            idRuta: "EVENTO",
            nombreRuta: "Ruta Exclusiva MH",
            telefonoCreador: "3129998877",
            fechaHoraSalida: farFutureDate,
            puntoReunion: "Parque MH",
            tipoRuta: "E",
            esPublico: false,
            idAgrupacion: "MH"
        )
        
        // 3. Evento privado para otra agrupación "CL1" -> Oculto
        let privateEventOtherGroup = OutingInvitation(
            idInvitacion: -3,
            idRuta: "EVENTO",
            nombreRuta: "Ruta Exclusiva Colibrí",
            telefonoCreador: "3129998877",
            fechaHoraSalida: farFutureDate,
            puntoReunion: "Cerro Grande",
            tipoRuta: "E",
            esPublico: false,
            idAgrupacion: "CL1"
        )
        
        // 4. Salida pasada (2026-10-13) -> Descartada
        let pastEvent = OutingInvitation(
            idInvitacion: -4,
            idRuta: "EVENTO",
            nombreRuta: "Evento Pasado Antiguo",
            telefonoCreador: "3129998877",
            fechaHoraSalida: "2026-10-13 08:00:00",
            puntoReunion: "Auditorio",
            tipoRuta: "E",
            esPublico: true,
            idAgrupacion: nil
        )
        
        viewModel.rawAvailableOutings = [publicEvent, privateEventSameGroup, privateEventOtherGroup, pastEvent]
        
        let disponibles = viewModel.availableOutings
        let disponiblesIds = Set(disponibles.map { $0.idInvitacion })
        
        #expect(disponiblesIds.contains(-1)) // Público -> Visible
        #expect(disponiblesIds.contains(-2)) // Mismo grupo MH -> Visible
        #expect(!disponiblesIds.contains(-3)) // Grupo diferente CL1 -> Oculto
        #expect(!disponiblesIds.contains(-4)) // Fecha pasada -> Descartado
    }

    @MainActor
    @Test func testOutingTabsSegregation() async throws {
        let viewModel = MainViewModel()
        viewModel.currentHikerProfile = HikerProfile(
            email: "test@rutas.com",
            name: "Tester",
            phone: "3121112233",
            emergencyPhone1: "3120000000"
        )
        
        let futureDate = "2030-08-20 06:00:00"
        let pastDate = "2020-05-10 07:00:00"
        
        let pendingAccepted = OutingInvitation(
            idInvitacion: 101,
            idRuta: "R1",
            nombreRuta: "Salida Pendiente 2030",
            telefonoCreador: "3120001111",
            fechaHoraSalida: futureDate,
            puntoReunion: "Entrada Bosque",
            tipoRuta: "S"
        )
        
        let historyAccepted = OutingInvitation(
            idInvitacion: 102,
            idRuta: "R2",
            nombreRuta: "Salida Concluida 2020",
            telefonoCreador: "3120001111",
            fechaHoraSalida: pastDate,
            puntoReunion: "Entrada Cascada",
            tipoRuta: "S",
            cooperacionRealizada: 50.0
        )
        
        viewModel.acceptedOutings = [pendingAccepted, historyAccepted]
        
        // Pestaña 1 - Mis Salidas: Solo pendientes (fecha >= Date())
        let pendientes = viewModel.pendingAcceptedOutings
        #expect(pendientes.count == 1)
        #expect(pendientes.first?.idInvitacion == 101)
        
        // Pestaña 2 - Historial: Solo concluidas (fecha < Date())
        let historial = viewModel.historyAcceptedOutings
        #expect(historial.count == 1)
        #expect(historial.first?.idInvitacion == 102)
    }
}

