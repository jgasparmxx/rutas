package com.gaspar.rutassendero.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Entidad que representa una ruta almacenada en la base de datos local (Room).
 * Contiene información descriptiva, métricas y metadatos de la ruta.
 */
@Entity(tableName = "rutas")
data class RutaEntity(
    /** Identificador único alfanumérico (ej. RUTAX12345). */
    @PrimaryKey
    val id: String, 

    @ColumnInfo(name = "nombre")
    val nombre: String,

    @ColumnInfo(name = "fecha")
    val fecha: String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),


    @ColumnInfo(name = "latitud_orig")
    val latitudOrig: Double,

    @ColumnInfo(name = "longitud_orig")
    val longitudOrig: Double,

    @ColumnInfo(name = "descripcion")
    val descripcion: String = "",

    @ColumnInfo(name = "distancia_gps", defaultValue = "0.0")
    val distanciaGps: Double = 0.0,

    @ColumnInfo(name = "distancia_beacons", defaultValue = "0.0")
    val distanciaBeacons: Double = 0.0,

    @ColumnInfo(name = "fecha_hora_inicio")
    val fechaHoraInicio: String? = null,

    @ColumnInfo(name = "fecha_hora_fin")
    val fechaHoraFin: String? = null,

    @ColumnInfo(name = "duracion_segundos", defaultValue = "0")
    val duracionSegundos: Long = 0L,

    @ColumnInfo(name = "desnivel_positivo", defaultValue = "0.0")
    val desnivelPositivo: Double = 0.0,

    @ColumnInfo(name = "desnivel_negativo", defaultValue = "0.0")
    val desnivelNegativo: Double = 0.0,

    @ColumnInfo(name = "contador_subidas", defaultValue = "0")
    val contadorSubidas: Int = 0,

    @ColumnInfo(name = "contador_bajadas", defaultValue = "0")
    val contadorBajadas: Int = 0,

    @ColumnInfo(name = "telefono", defaultValue = "")
    val telefono: String = "",

    @ColumnInfo(name = "datos_encriptados")
    val datosEncriptados: String? = null,

    @ColumnInfo(name = "conectividad", defaultValue = "0.0")
    val conectividad: Float = 0.0f,

    @ColumnInfo(name = "codigo", defaultValue = "")
    val codigo: String = "",

    @ColumnInfo(name = "publico", defaultValue = "0")
    val publico: Boolean = true,

    @ColumnInfo(name = "fecha_importacion")
    val fechaImportacion: Long? = null,

    @ColumnInfo(name = "fupdate", defaultValue = "0")
    val fupdate: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Genera un identificador único para una nueva ruta basado en su nombre.
         * Combina hasta los primeros 5 caracteres del nombre (limpios) con 5 caracteres aleatorios.
         *
         * @param name El nombre de la ruta provisto por el usuario.
         * @return El ID generado de 10 caracteres.
         */
        fun generateRouteId(name: String): String {
            val clean = name.uppercase().filter { it.isLetterOrDigit() }
            val prefix = when {
                clean.length >= 5 -> clean.substring(0, 5)
                clean.isNotEmpty() -> clean.padEnd(5, 'X')
                else -> "RUTAX"
            }
            
            val allowedChars = ('A'..'Z') + ('0'..'9')
            val randomSuffix = (1..5).joinToString("") { allowedChars.random().toString() }
            
            return prefix + randomSuffix
        }
    }
}
