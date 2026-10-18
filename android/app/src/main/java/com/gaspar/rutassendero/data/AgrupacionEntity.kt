package com.gaspar.rutassendero.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "agrupaciones")
data class AgrupacionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_agrupacion")
    val idAgrupacion: String,

    @ColumnInfo(name = "nombre")
    val nombre: String,

    @ColumnInfo(name = "fupdate", defaultValue = "0")
    val fupdate: Long = System.currentTimeMillis()
) {
    companion object {
        val SEED_AGRUPACIONES = listOf(
            AgrupacionEntity(idAgrupacion = "MH", nombre = "Miguel Hidalgo"),
            AgrupacionEntity(idAgrupacion = "CL1", nombre = "COLIBRI")
        )
    }
}
