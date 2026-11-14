//
//  SwiftDataRutasRepository.swift
//  RutasSendero
//
//  Implementación nativa del repositorio de persistencia usando SwiftData.
//  Conforme a RutasRepositoryProtocol y HikerProfileRepositoryProtocol.
//

import Foundation
import SwiftData

@ModelActor
public actor SwiftDataRutasRepository: RutasRepositoryProtocol, HikerProfileRepositoryProtocol {
    
    // MARK: - Clave compuesta determinista para evitar colisiones @Attribute(.unique) entre rutas
    private static func compositeStorageId(routeId: String, localId: Int) -> Int {
        var hash: UInt64 = 1469598103934665603
        for byte in routeId.utf8 {
            hash ^= UInt64(byte)
            hash &*= 1099511628211
        }
        let routeHash = Int((hash & 0x3FFFFFFF) | 1) // 30 bits positivos no nulos
        let cleanLocal = max(1, localId & 0xFFFFF)   // 20 bits (hasta 1,048,575 puntos/trazas por ruta)
        return (routeHash << 20) | cleanLocal
    }
    
    // MARK: - RutasRepositoryProtocol
    
    public func getAllRoutes() async throws -> [Route] {
        let descriptor = FetchDescriptor<SDCacheRuta>(sortBy: [SortDescriptor(\.fupdate, order: .reverse)])
        let sdRoutes = try modelContext.fetch(descriptor)
        return sdRoutes.map { $0.toDomain() }
    }
    
    public func getRoute(byId id: String) async throws -> Route? {
        let descriptor = FetchDescriptor<SDCacheRuta>(predicate: #Predicate { $0.id == id })
        let sdRoutes = try modelContext.fetch(descriptor)
        return sdRoutes.first?.toDomain()
    }
    
    public func getPoints(forRouteId routeId: String) async throws -> [RoutePoint] {
        let descriptor = FetchDescriptor<SDCacheRuta>(predicate: #Predicate { $0.id == routeId })
        guard let sdRoute = try modelContext.fetch(descriptor).first else { return [] }
        return sdRoute.puntos.map { $0.toDomain(routeId: routeId) }.sorted(by: { $0.id < $1.id })
    }
    
    public func getTrace(forRouteId routeId: String) async throws -> [RouteTrace] {
        let descriptor = FetchDescriptor<SDCacheRuta>(predicate: #Predicate { $0.id == routeId })
        guard let sdRoute = try modelContext.fetch(descriptor).first else { return [] }
        return sdRoute.trazas.sorted(by: { $0.orden < $1.orden }).map { $0.toDomain(routeId: routeId) }
    }
    
    public func save(route: Route) async throws {
        let descriptor = FetchDescriptor<SDCacheRuta>(predicate: #Predicate { $0.id == route.id })
        if let existing = try modelContext.fetch(descriptor).first {
            existing.nombre = route.name
            existing.fecha = route.date
            existing.latitudOrig = route.originLatitude
            existing.longitudOrig = route.originLongitude
            existing.descripcion = route.descriptionText
            existing.distanciaGps = route.gpsDistanceMeters
            existing.distanciaBeacons = route.beaconDistanceMeters
            existing.fechaHoraInicio = route.startTime
            existing.fechaHoraFin = route.endTime
            existing.duracionSegundos = route.durationSeconds
            existing.desnivelPositivo = route.elevationGain
            existing.desnivelNegativo = route.elevationLoss
            existing.contadorSubidas = route.climbsCount
            existing.contadorBajadas = route.descentsCount
            existing.conectividad = Float(route.connectivity)
            existing.codigo = route.accessCode
            existing.publico = route.isPublic
            existing.telefono = route.creatorPhone
            existing.fechaImportacion = route.importDate
            existing.fupdate = Int64(Date().timeIntervalSince1970 * 1000)
        } else {
            let sdRoute = SDCacheRuta(
                id: route.id,
                nombre: route.name,
                fecha: route.date,
                latitudOrig: route.originLatitude,
                longitudOrig: route.originLongitude,
                descripcion: route.descriptionText,
                distanciaGps: route.gpsDistanceMeters,
                distanciaBeacons: route.beaconDistanceMeters,
                fechaHoraInicio: route.startTime,
                fechaHoraFin: route.endTime,
                duracionSegundos: route.durationSeconds,
                desnivelPositivo: route.elevationGain,
                desnivelNegativo: route.elevationLoss,
                contadorSubidas: route.climbsCount,
                contadorBajadas: route.descentsCount,
                telefono: route.creatorPhone,
                conectividad: Float(route.connectivity),
                codigo: route.accessCode,
                publico: route.isPublic,
                fechaImportacion: route.importDate,
                fupdate: route.syncTimestamp > 0 ? route.syncTimestamp : Int64(Date().timeIntervalSince1970 * 1000)
            )
            modelContext.insert(sdRoute)
        }
        try modelContext.save()
    }
    
    public func save(point: RoutePoint) async throws {
        let routeDescriptor = FetchDescriptor<SDCacheRuta>(predicate: #Predicate { $0.id == point.routeId })
        let sdRoute = try modelContext.fetch(routeDescriptor).first
        let storageId = Self.compositeStorageId(routeId: point.routeId, localId: point.id)
        let cleanLocalId = max(1, point.id & 0xFFFFF)
        
        let pointDescriptor = FetchDescriptor<SDPuntoRuta>(predicate: #Predicate { $0.id == storageId })
        if let existing = try modelContext.fetch(pointDescriptor).first ?? sdRoute?.puntos.first(where: { ($0.id > 0xFFFFF ? ($0.id & 0xFFFFF) : $0.id) == cleanLocalId }) {
            existing.fechaHora = point.timestamp
            existing.latitud = point.latitude
            existing.longitud = point.longitude
            existing.altitud = point.altitude
            existing.descripcion = point.descriptionText
            existing.fotoPath = point.photoUrl
            existing.esFinRuta = point.isRouteEnd
            existing.tieneConectividad = point.hasConnectivity
            existing.estado = point.estado
            existing.ruta = sdRoute
        } else {
            let sdPoint = SDPuntoRuta(
                id: storageId,
                fechaHora: point.timestamp,
                latitud: point.latitude,
                longitud: point.longitude,
                altitud: point.altitude,
                descripcion: point.descriptionText,
                fotoPath: point.photoUrl,
                esFinRuta: point.isRouteEnd,
                tieneConectividad: point.hasConnectivity,
                estado: point.estado
            )
            sdPoint.ruta = sdRoute
            modelContext.insert(sdPoint)
        }
        try modelContext.save()
    }
    
    public func save(traceBatch: [RouteTrace]) async throws {
        guard let first = traceBatch.first else { return }
        let routeDescriptor = FetchDescriptor<SDCacheRuta>(predicate: #Predicate { $0.id == first.routeId })
        let sdRoute = try modelContext.fetch(routeDescriptor).first
        
        for trace in traceBatch {
            let storageId = Self.compositeStorageId(routeId: trace.routeId, localId: trace.order > 0 ? trace.order : trace.id)
            let traceDescriptor = FetchDescriptor<SDTrazaRuta>(predicate: #Predicate { $0.id == storageId })
            if let existing = try modelContext.fetch(traceDescriptor).first {
                existing.orden = trace.order
                existing.latitud = trace.latitude
                existing.longitud = trace.longitude
                existing.altitud = trace.altitude
                existing.ruta = sdRoute
            } else {
                let sdTrace = SDTrazaRuta(
                    id: storageId,
                    orden: trace.order,
                    latitud: trace.latitude,
                    longitud: trace.longitude,
                    altitud: trace.altitude
                )
                sdTrace.ruta = sdRoute
                modelContext.insert(sdTrace)
            }
        }
        try modelContext.save()
    }
    
    public func deleteRoute(byId id: String) async throws {
        let descriptor = FetchDescriptor<SDCacheRuta>(predicate: #Predicate { $0.id == id })
        if let sdRoute = try modelContext.fetch(descriptor).first {
            modelContext.delete(sdRoute)
            try modelContext.save()
        }
    }
    
    // MARK: - HikerProfileRepositoryProtocol
    
    public func getProfile() async throws -> HikerProfile? {
        let descriptor = FetchDescriptor<SDSenderista>()
        guard let senderista = try modelContext.fetch(descriptor).first else { return nil }
        return senderista.toDomain()
    }
    
    public func save(profile: HikerProfile) async throws {
        let descriptor = FetchDescriptor<SDSenderista>(predicate: #Predicate { $0.email == profile.email })
        if let existing = try modelContext.fetch(descriptor).first {
            existing.nombre = profile.name
            existing.telefono = profile.phone
            existing.rol = profile.role
            existing.sexo = profile.sexo
            existing.fechaNacimiento = profile.fechaNacimiento
            existing.idAgrupacion = profile.idAgrupacion
            existing.codigoConfianza = profile.codigoConfianza
            existing.idBeacon = profile.beaconId
            existing.nivelSenderista = profile.experienceLevel
            existing.tipoSangre = profile.bloodType
            existing.aQuienesAvisar = profile.contactName
            existing.aQuienAvisarTelefono1 = profile.emergencyPhone1
            existing.aQuienAvisarTelefono2 = profile.emergencyPhone2
            existing.peso = profile.weightKg
            existing.tieneEnfermedadCronica = profile.tieneEnfermedadCronica ? "S" : "N"
            existing.enfermedadCronica1 = profile.enfermedadCronica1
            existing.enfermedadCronica2 = profile.enfermedadCronica2
            existing.tieneAuto = profile.tieneAuto ? "S" : "N"
            existing.direccion = profile.direccion
            existing.llevaMascota = profile.llevaMascota ? "S" : "N"
            existing.alergias = profile.alergias
            existing.seguroMedico = profile.seguroMedico
            existing.minimoLongitud = profile.minimoLongitud
            existing.maxPrivadas = profile.maxPrivadas
            existing.consentimientoTerminos = profile.consentimientoTerminos
            existing.consentimientoFichaMedica = profile.consentimientoFichaMedica
            existing.consentimientoGpsMultimedia = profile.consentimientoGpsMultimedia
            existing.timestampConsentimientoUtc = profile.timestampConsentimientoUtc ?? Int64(Date().timeIntervalSince1970 * 1000)
            existing.fupdate = Int64(Date().timeIntervalSince1970 * 1000)
        } else {
            let sd = SDSenderista(
                email: profile.email,
                rol: profile.role,
                telefono: profile.phone,
                nombre: profile.name,
                sexo: profile.sexo,
                fechaNacimiento: profile.fechaNacimiento,
                aQuienesAvisar: profile.contactName,
                aQuienAvisarTelefono1: profile.emergencyPhone1,
                aQuienAvisarTelefono2: profile.emergencyPhone2,
                tieneEnfermedadCronica: profile.tieneEnfermedadCronica ? "S" : "N",
                enfermedadCronica1: profile.enfermedadCronica1,
                enfermedadCronica2: profile.enfermedadCronica2,
                tieneAuto: profile.tieneAuto ? "S" : "N",
                direccion: profile.direccion,
                llevaMascota: profile.llevaMascota ? "S" : "N",
                alergias: profile.alergias,
                tipoSangre: profile.bloodType,
                seguroMedico: profile.seguroMedico,
                nivelSenderista: profile.experienceLevel,
                idBeacon: profile.beaconId,
                minimoLongitud: profile.minimoLongitud,
                idAgrupacion: profile.idAgrupacion,
                fupdate: Int64(Date().timeIntervalSince1970 * 1000),
                bloqueado: false,
                consentimientoTerminos: profile.consentimientoTerminos,
                consentimientoFichaMedica: profile.consentimientoFichaMedica,
                consentimientoGpsMultimedia: profile.consentimientoGpsMultimedia,
                timestampConsentimientoUtc: profile.timestampConsentimientoUtc ?? Int64(Date().timeIntervalSince1970 * 1000),
                codigoConfianza: profile.codigoConfianza,
                peso: profile.weightKg,
                maxPrivadas: profile.maxPrivadas
            )
            modelContext.insert(sd)
        }
        try modelContext.save()
    }
    
    public func updateBeaconId(_ beaconId: String) async throws {
        let descriptor = FetchDescriptor<SDSenderista>()
        if let senderista = try modelContext.fetch(descriptor).first {
            senderista.idBeacon = beaconId
            try modelContext.save()
        }
    }
}

// MARK: - Mapeo de Entidades SwiftData a Modelos de Dominio

extension SDCacheRuta {
    func toDomain() -> Route {
        Route(
            id: self.id,
            name: self.nombre,
            date: self.fecha,
            originLatitude: self.latitudOrig,
            originLongitude: self.longitudOrig,
            descriptionText: self.descripcion,
            gpsDistanceMeters: self.distanciaGps,
            beaconDistanceMeters: self.distanciaBeacons,
            startTime: self.fechaHoraInicio,
            endTime: self.fechaHoraFin,
            durationSeconds: self.duracionSegundos,
            elevationGain: self.desnivelPositivo,
            elevationLoss: self.desnivelNegativo,
            climbsCount: self.contadorSubidas,
            descentsCount: self.contadorBajadas,
            connectivity: Double(self.conectividad),
            accessCode: self.codigo,
            isPublic: self.publico,
            syncTimestamp: self.fupdate,
            creatorPhone: self.telefono,
            importDate: self.fechaImportacion
        )
    }
}

extension SDPuntoRuta {
    func toDomain(routeId: String) -> RoutePoint {
        var resolvedEstado = self.estado
        if resolvedEstado == 1 && !self.descripcion.isEmpty {
            let lower = self.descripcion.lowercased()
            if lower.contains("bifurcac") || lower.contains("unión de dos ríos") || lower.contains("union de dos rios") {
                resolvedEstado = 3
            } else if lower.contains("suelo flojo") || lower.contains("resbalos") {
                resolvedEstado = 4
            } else if lower.contains("cuidado") || lower.contains("precauci") {
                resolvedEstado = 5
            } else if lower.contains("alerta") || lower.contains("peligro") {
                resolvedEstado = 6
            } else if lower.contains("vehiculo") || lower.contains("vehículo") || lower.contains("carro") {
                resolvedEstado = 8
            } else if lower.contains("refugio") || lower.contains("campamento") {
                resolvedEstado = 9
            } else if lower.contains("comida") || lower.contains("restaurante") {
                resolvedEstado = 10
            } else if lower.contains("interes") || lower.contains("interés") || lower.contains("mirador") || lower.contains("arroyo") || lower.contains("ranchito") {
                resolvedEstado = 2
            }
        }
        let cleanId = self.id > 0xFFFFF ? (self.id & 0xFFFFF) : self.id
        return RoutePoint(
            id: cleanId,
            routeId: routeId,
            timestamp: self.fechaHora,
            latitude: self.latitud,
            longitude: self.longitud,
            altitude: self.altitud,
            descriptionText: self.descripcion,
            photoUrl: self.fotoPath,
            isRouteEnd: self.esFinRuta,
            hasConnectivity: self.tieneConectividad,
            estado: resolvedEstado
        )
    }
}

extension SDTrazaRuta {
    func toDomain(routeId: String) -> RouteTrace {
        let cleanId = self.id > 0xFFFFF ? (self.id & 0xFFFFF) : self.id
        return RouteTrace(
            id: cleanId,
            routeId: routeId,
            order: self.orden,
            latitude: self.latitud,
            longitude: self.longitud,
            altitude: self.altitud
        )
    }
}

extension SDSenderista {
    func toDomain() -> HikerProfile {
        HikerProfile(
            email: self.email,
            name: self.nombre,
            phone: self.telefono,
            role: self.rol,
            sexo: self.sexo,
            fechaNacimiento: self.fechaNacimiento,
            idAgrupacion: self.idAgrupacion,
            codigoConfianza: self.codigoConfianza,
            beaconId: self.idBeacon,
            experienceLevel: self.nivelSenderista,
            bloodType: self.tipoSangre,
            contactName: self.aQuienesAvisar,
            emergencyPhone1: self.aQuienAvisarTelefono1,
            emergencyPhone2: self.aQuienAvisarTelefono2,
            weightKg: self.peso ?? 70.0,
            tieneEnfermedadCronica: self.tieneEnfermedadCronica == "S",
            enfermedadCronica1: self.enfermedadCronica1,
            enfermedadCronica2: self.enfermedadCronica2,
            tieneAuto: self.tieneAuto == "S",
            direccion: self.direccion,
            llevaMascota: self.llevaMascota == "S",
            alergias: self.alergias,
            seguroMedico: self.seguroMedico,
            minimoLongitud: self.minimoLongitud,
            maxPrivadas: self.maxPrivadas,
            consentimientoTerminos: self.consentimientoTerminos,
            consentimientoFichaMedica: self.consentimientoFichaMedica,
            consentimientoGpsMultimedia: self.consentimientoGpsMultimedia,
            timestampConsentimientoUtc: self.timestampConsentimientoUtc
        )
    }
}
