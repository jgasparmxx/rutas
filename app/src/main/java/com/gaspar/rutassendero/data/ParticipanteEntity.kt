package com.gaspar.rutassendero.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "participantes")
data class ParticipanteEntity(
    @PrimaryKey
    @ColumnInfo(name = "telefono_senderista")
    val telefonoSenderista: String,

    @ColumnInfo(name = "nombre_senderista")
    val nombreSenderista: String,

    @ColumnInfo(name = "id_beacon")
    val idBeacon: String? = null,

    @ColumnInfo(name = "latitud")
    val latitud: Double? = null,

    @ColumnInfo(name = "longitud")
    val longitud: Double? = null,

    @ColumnInfo(name = "distancia_estimada")
    val distanciaEstimada: String? = null,

    @ColumnInfo(name = "ultimo_rssi")
    val ultimoRssi: Int? = null,

    @ColumnInfo(name = "ultima_deteccion")
    val ultimaDeteccion: Long? = null,

    @ColumnInfo(name = "fecha_ingreso")
    val fechaIngreso: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "a_quienes_avisar")
    val aQuienesAvisar: String? = null,

    @ColumnInfo(name = "a_quien_avisar_telefono1")
    val aQuienAvisarTelefono1: String? = null,

    @ColumnInfo(name = "a_quien_avisar_telefono2")
    val aQuienAvisarTelefono2: String? = null,

    @ColumnInfo(name = "tipo_sangre")
    val tipoSangre: String? = null,

    @ColumnInfo(name = "fecha_nacimiento")
    val fechaNacimiento: String? = null,

    @ColumnInfo(name = "alergias")
    val alergias: String? = null,

    @ColumnInfo(name = "enfermedades_cronicas")
    val enfermedadesCronicas: String? = null,

    @ColumnInfo(name = "codigo_confianza")
    val codigoConfianza: String? = null,

    @ColumnInfo(name = "sexo")
    val sexo: String? = null,

    @ColumnInfo(name = "fupdate", defaultValue = "0")
    val fupdate: Long = System.currentTimeMillis()
) {
    val edad: Int?
        get() {
            if (fechaNacimiento.isNullOrBlank()) return null
            val formats = listOf("dd/MM/yyyy", "yyyy-MM-dd", "yyyy/MM/dd")
            for (format in formats) {
                try {
                    val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
                    val birthDate: java.util.Date = sdf.parse(fechaNacimiento) ?: continue
                    val today = java.util.Calendar.getInstance()
                    val birth = java.util.Calendar.getInstance().apply { time = birthDate }
                    
                    var age = today.get(java.util.Calendar.YEAR) - birth.get(java.util.Calendar.YEAR)
                    if (today.get(java.util.Calendar.DAY_OF_YEAR) < birth.get(java.util.Calendar.DAY_OF_YEAR)) {
                        age--
                    }
                    return age
                } catch (e: Exception) {
                    // Try next format
                }
            }
            return null
        }
}
