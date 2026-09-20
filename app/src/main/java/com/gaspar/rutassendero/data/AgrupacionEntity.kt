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
    val nombre: String
)
