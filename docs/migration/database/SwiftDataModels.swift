//
//  SwiftDataModels.swift
//  RutasSendero (iOS Migration)
//
//  Esquema nativo de persistencia para iOS 17+ usando SwiftData.
//  Equivalente 1:1 de las entidades de Room v34.
//

import Foundation
import SwiftData

@Model
public final class SDCacheRuta {
    @Attribute(.unique) public var id: String
    public var nombre: String
    public var fecha: String
    public var latitudOrig: Double
    public var longitudOrig: Double
    public var descripcion: String
    public var distanciaGps: Double
    public var distanciaBeacons: Double
    public var fechaHoraInicio: String?
    public var fechaHoraFin: String?
    public var duracionSegundos: Int64
    public var desnivelPositivo: Double
    public var desnivelNegativo: Double
    public var contadorSubidas: Int
    public var contadorBajadas: Int
    public var telefono: String
    public var datosEncriptados: String?
    public var conectividad: Float
    public var codigo: String
    public var publico: Bool
    public var fechaImportacion: Int64?
    public var fupdate: Int64

    @Relationship(deleteRule: .cascade, inverse: \SDPuntoRuta.ruta)
    public var puntos: [SDPuntoRuta] = []

    @Relationship(deleteRule: .cascade, inverse: \SDTrazaRuta.ruta)
    public var trazas: [SDTrazaRuta] = []

    public init(
        id: String,
        nombre: String,
        fecha: String,
        latitudOrig: Double,
        longitudOrig: Double,
        descripcion: String = "",
        distanciaGps: Double = 0.0,
        distanciaBeacons: Double = 0.0,
        fechaHoraInicio: String? = nil,
        fechaHoraFin: String? = nil,
        duracionSegundos: Int64 = 0,
        desnivelPositivo: Double = 0.0,
        desnivelNegativo: Double = 0.0,
        contadorSubidas: Int = 0,
        contadorBajadas: Int = 0,
        telefono: String = "",
        datosEncriptados: String? = nil,
        conectividad: Float = 0.0,
        codigo: String = "",
        publico: Bool = true,
        fechaImportacion: Int64? = nil,
        fupdate: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.nombre = nombre
        self.fecha = fecha
        self.latitudOrig = latitudOrig
        self.longitudOrig = longitudOrig
        self.descripcion = descripcion
        self.distanciaGps = distanciaGps
        self.distanciaBeacons = distanciaBeacons
        self.fechaHoraInicio = fechaHoraInicio
        self.fechaHoraFin = fechaHoraFin
        self.duracionSegundos = duracionSegundos
        self.desnivelPositivo = desnivelPositivo
        self.desnivelNegativo = desnivelNegativo
        self.contadorSubidas = contadorSubidas
        self.contadorBajadas = contadorBajadas
        self.telefono = telefono
        self.datosEncriptados = datosEncriptados
        self.conectividad = conectividad
        self.codigo = codigo
        self.publico = publico
        self.fechaImportacion = fechaImportacion
        self.fupdate = fupdate
    }
}

@Model
public final class SDPuntoRuta {
    @Attribute(.unique) public var id: Int
    public var fechaHora: String
    public var latitud: Double
    public var longitud: Double
    public var altitud: Double
    public var descripcion: String
    public var fotoPath: String?
    public var esFinRuta: Bool
    public var tieneConectividad: Bool
    public var estado: Int
    public var fupdate: Int64

    public var ruta: SDCacheRuta?

    public init(
        id: Int,
        fechaHora: String,
        latitud: Double,
        longitud: Double,
        altitud: Double,
        descripcion: String,
        fotoPath: String? = nil,
        esFinRuta: Bool = false,
        tieneConectividad: Bool = false,
        estado: Int = 1,
        fupdate: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.fechaHora = fechaHora
        self.latitud = latitud
        self.longitud = longitud
        self.altitud = altitud
        self.descripcion = descripcion
        self.fotoPath = fotoPath
        self.esFinRuta = esFinRuta
        self.tieneConectividad = tieneConectividad
        self.estado = estado
        self.fupdate = fupdate
    }
}

@Model
public final class SDTrazaRuta {
    @Attribute(.unique) public var id: Int
    public var orden: Int
    public var latitud: Double
    public var longitud: Double
    public var altitud: Double
    public var fupdate: Int64

    public var ruta: SDCacheRuta?

    public init(
        id: Int,
        orden: Int,
        latitud: Double,
        longitud: Double,
        altitud: Double,
        fupdate: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.id = id
        self.orden = orden
        self.latitud = latitud
        self.longitud = longitud
        self.altitud = altitud
        self.fupdate = fupdate
    }
}

@Model
public final class SDSenderista {
    @Attribute(.unique) public var email: String
    public var rol: String
    public var telefono: String
    public var nombre: String
    public var sexo: String
    public var fechaNacimiento: String
    public var aQuienesAvisar: String
    public var aQuienAvisarTelefono1: String
    public var aQuienAvisarTelefono2: String?
    public var tieneEnfermedadCronica: String
    public var enfermedadCronica1: String?
    public var enfermedadCronica2: String?
    public var tieneAuto: String?
    public var direccion: String?
    public var llevaMascota: String?
    public var alergias: String?
    public var tipoSangre: String?
    public var seguroMedico: String?
    public var nivelSenderista: String
    public var idBeacon: String?
    public var minimoLongitud: Double
    public var idAgrupacion: String?
    public var fupdate: Int64
    public var bloqueado: Bool
    public var consentimientoTerminos: Bool
    public var consentimientoFichaMedica: Bool
    public var consentimientoGpsMultimedia: Bool
    public var timestampConsentimientoUtc: Int64?
    public var codigoConfianza: String?
    public var peso: Double?
    public var maxPrivadas: Int

    public init(
        email: String,
        rol: String = "S",
        telefono: String,
        nombre: String,
        sexo: String,
        fechaNacimiento: String,
        aQuienesAvisar: String,
        aQuienAvisarTelefono1: String,
        aQuienAvisarTelefono2: String? = nil,
        tieneEnfermedadCronica: String = "N",
        enfermedadCronica1: String? = nil,
        enfermedadCronica2: String? = nil,
        tieneAuto: String? = nil,
        direccion: String? = nil,
        llevaMascota: String? = nil,
        alergias: String? = nil,
        tipoSangre: String? = nil,
        seguroMedico: String? = nil,
        nivelSenderista: String = "principiante",
        idBeacon: String? = nil,
        minimoLongitud: Double = 4.0,
        idAgrupacion: String? = nil,
        fupdate: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        bloqueado: Bool = false,
        consentimientoTerminos: Bool = false,
        consentimientoFichaMedica: Bool = false,
        consentimientoGpsMultimedia: Bool = false,
        timestampConsentimientoUtc: Int64? = nil,
        codigoConfianza: String? = nil,
        peso: Double? = nil,
        maxPrivadas: Int = 3
    ) {
        self.email = email
        self.rol = rol
        self.telefono = telefono
        self.nombre = nombre
        self.sexo = sexo
        self.fechaNacimiento = fechaNacimiento
        self.aQuienesAvisar = aQuienesAvisar
        self.aQuienAvisarTelefono1 = aQuienAvisarTelefono1
        self.aQuienAvisarTelefono2 = aQuienAvisarTelefono2
        self.tieneEnfermedadCronica = tieneEnfermedadCronica
        self.enfermedadCronica1 = enfermedadCronica1
        self.enfermedadCronica2 = enfermedadCronica2
        self.tieneAuto = tieneAuto
        self.direccion = direccion
        self.llevaMascota = llevaMascota
        self.alergias = alergias
        self.tipoSangre = tipoSangre
        self.seguroMedico = seguroMedico
        self.nivelSenderista = nivelSenderista
        self.idBeacon = idBeacon
        self.minimoLongitud = minimoLongitud
        self.idAgrupacion = idAgrupacion
        self.fupdate = fupdate
        self.bloqueado = bloqueado
        self.consentimientoTerminos = consentimientoTerminos
        self.consentimientoFichaMedica = consentimientoFichaMedica
        self.consentimientoGpsMultimedia = consentimientoGpsMultimedia
        self.timestampConsentimientoUtc = timestampConsentimientoUtc
        self.codigoConfianza = codigoConfianza
        self.peso = peso
        self.maxPrivadas = maxPrivadas
    }
}
