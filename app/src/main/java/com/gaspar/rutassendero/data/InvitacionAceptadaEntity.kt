package com.gaspar.rutassendero.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "invitaciones_aceptadas")
data class InvitacionAceptadaEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_invitacion")
    val idInvitacion: Int,

    @ColumnInfo(name = "id_ruta")
    val idRuta: String,

    @ColumnInfo(name = "nombre_ruta")
    val nombreRuta: String,

    @ColumnInfo(name = "telefono_creador")
    val telefonoCreador: String,

    @ColumnInfo(name = "fecha_hora_salida")
    val fechaHoraSalida: String, // format: yyyy-MM-dd HH:mm:ss or similar

    @ColumnInfo(name = "punto_reunion")
    val puntoReunion: String,

    @ColumnInfo(name = "link_google_maps")
    val linkGoogleMaps: String? = null,

    @ColumnInfo(name = "cooperacion_minima")
    val cooperacionMinima: Double,

    @ColumnInfo(name = "fecha_limite_registro")
    val fechaLimiteRegistro: String? = null,

    @ColumnInfo(name = "nivel")
    val nivel: String? = null,

    @ColumnInfo(name = "instrucciones")
    val instrucciones: String? = null,

    @ColumnInfo(name = "latitud")
    val latitud: Double? = null,

    @ColumnInfo(name = "longitud")
    val longitud: Double? = null,

    @ColumnInfo(name = "cooperacion_realizada")
    val cooperacionRealizada: Double? = null,

    @ColumnInfo(name = "fecha_cooperacion_realizada")
    val fechaCooperacionRealizada: String? = null,


    @ColumnInfo(name = "sincronizado_con_nube")
    val sincronizadoConNube: Boolean = false,

    @ColumnInfo(name = "conectividad")
    val conectividad: Float? = null,

    @ColumnInfo(name = "distancia_gps")
    val distanciaGps: Double? = null,

    @ColumnInfo(name = "desnivel_positivo")
    val desnivelPositivo: Double? = null,

    @ColumnInfo(name = "desnivel_negativo")
    val desnivelNegativo: Double? = null,

    @ColumnInfo(name = "contador_subidas")
    val contadorSubidas: Int? = null,

    @ColumnInfo(name = "contador_bajadas")
    val contadorBajadas: Int? = null,

    @ColumnInfo(name = "duracion_segundos")
    val duracionSegundos: Long? = null,

    @ColumnInfo(name = "comprobante_pago", typeAffinity = ColumnInfo.BLOB)
    val comprobantePago: ByteArray? = null,

    @ColumnInfo(name = "fecha_subida_comprobante")
    val fechaSubidaComprobante: String? = null,

    @ColumnInfo(name = "fupdate", defaultValue = "0")
    val fupdate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "tipo_ruta")
    val tipoRuta: String = "S",

    @ColumnInfo(name = "comentario")
    val comentario: String? = null
)
