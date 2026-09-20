package com.gaspar.rutassendero.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "traza_ruta",
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
data class TrazaRutaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "id_ruta")
    val idRuta: String,

    @ColumnInfo(name = "orden")
    val orden: Int,

    @ColumnInfo(name = "latitud")
    val latitud: Double,

    @ColumnInfo(name = "longitud")
    val longitud: Double,

    @ColumnInfo(name = "altitud")
    val altitud: Double
)
