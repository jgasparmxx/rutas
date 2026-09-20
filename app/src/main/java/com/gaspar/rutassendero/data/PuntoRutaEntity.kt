package com.gaspar.rutassendero.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "puntos_ruta",
    foreignKeys = [
        ForeignKey(
            entity = RutaEntity::class,
            parentColumns = ["id"],
            childColumns = ["id_ruta"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["id_ruta"])]
)
data class PuntoRutaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "id_ruta")
    val idRuta: String,

    @ColumnInfo(name = "fecha_hora")
    val fechaHora: String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),

    @ColumnInfo(name = "latitud")
    val latitud: Double,

    @ColumnInfo(name = "longitud")
    val longitud: Double,

    @ColumnInfo(name = "altitud")
    val altitud: Double,

    @ColumnInfo(name = "descripcion")
    val descripcion: String,

    @ColumnInfo(name = "foto_path")
    val fotoPath: String? = null,

    @ColumnInfo(name = "es_fin_ruta", defaultValue = "0")
    val esFinRuta: Boolean = false,

    @ColumnInfo(name = "tiene_conectividad", defaultValue = "0")
    val tieneConectividad: Boolean = false,

    @ColumnInfo(name = "estado", defaultValue = "1")
    val estado: Int = 1
)
