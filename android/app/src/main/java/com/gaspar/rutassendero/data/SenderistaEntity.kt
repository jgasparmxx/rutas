package com.gaspar.rutassendero.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "senderista")
data class SenderistaEntity(
    @PrimaryKey
    @ColumnInfo(name = "email")
    val email: String,

    @ColumnInfo(name = "rol")
    val rol: String = "S",

    @ColumnInfo(name = "telefono")
    val telefono: String,

    @ColumnInfo(name = "nombre")
    val nombre: String,

    @ColumnInfo(name = "sexo")
    val sexo: String, // "F" para femenino, "M" para masculino

    @ColumnInfo(name = "fecha_nacimiento")
    val fechaNacimiento: String, // dd/MM/yyyy

    @ColumnInfo(name = "a_quienes_avisar")
    val aQuienesAvisar: String,

    @ColumnInfo(name = "a_quien_avisar_telefono1")
    val aQuienAvisarTelefono1: String,

    @ColumnInfo(name = "a_quien_avisar_telefono2")
    val aQuienAvisarTelefono2: String? = null,

    @ColumnInfo(name = "tiene_enfermedad_cronica")
    val tieneEnfermedadCronica: String, // "S" o "N"

    @ColumnInfo(name = "enfermedad_cronica1")
    val enfermedadCronica1: String? = null,

    @ColumnInfo(name = "enfermedad_cronica2")
    val enfermedadCronica2: String? = null,

    @ColumnInfo(name = "tiene_auto")
    val tieneAuto: String? = null, // "S" o "N"

    @ColumnInfo(name = "direccion")
    val direccion: String? = null,

    @ColumnInfo(name = "lleva_mascota")
    val llevaMascota: String? = null, // "S" o "N"

    @ColumnInfo(name = "alergias")
    val alergias: String? = null,

    @ColumnInfo(name = "tipo_sangre")
    val tipoSangre: String? = null,

    @ColumnInfo(name = "seguro_medico")
    val seguroMedico: String? = null,


    @ColumnInfo(name = "nivel_senderista", defaultValue = "principiante")
    val nivelSenderista: String = "principiante",

    @ColumnInfo(name = "id_beacon")
    val idBeacon: String? = null,

    @ColumnInfo(name = "minimo_longitud", defaultValue = "4.0")
    val minimoLongitud: Double = 4.0,

    @ColumnInfo(name = "id_agrupacion")
    val idAgrupacion: String? = null,

    @ColumnInfo(name = "fupdate")
    val fupdate: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "bloqueado", defaultValue = "0")
    val bloqueado: Boolean = false,

    @ColumnInfo(name = "consentimiento_terminos", defaultValue = "0")
    val consentimientoTerminos: Boolean = false,

    @ColumnInfo(name = "consentimiento_ficha_medica", defaultValue = "0")
    val consentimientoFichaMedica: Boolean = false,

    @ColumnInfo(name = "consentimiento_gps_multimedia", defaultValue = "0")
    val consentimientoGpsMultimedia: Boolean = false,

    @ColumnInfo(name = "timestamp_consentimiento_utc")
    val timestampConsentimientoUtc: Long? = null,

    @ColumnInfo(name = "codigo_confianza")
    val codigoConfianza: String? = null,

    @ColumnInfo(name = "peso")
    val peso: Double? = null,

    @ColumnInfo(name = "max_privadas", defaultValue = "3")
    val maxPrivadas: Int = 3
)
