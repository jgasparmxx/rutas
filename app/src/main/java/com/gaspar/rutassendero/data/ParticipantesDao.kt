package com.gaspar.rutassendero.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ParticipantesDao {
    // --- PARTICIPANTES (BEACONS SCANNING) ---
    @Query("SELECT * FROM participantes")
    fun getAllParticipantesFlow(): Flow<List<ParticipanteEntity>>

    @Query("SELECT * FROM participantes")
    suspend fun getAllParticipantes(): List<ParticipanteEntity>

    @Query("SELECT * FROM participantes WHERE id_beacon = :idBeacon LIMIT 1")
    suspend fun getParticipanteByBeaconId(idBeacon: String): ParticipanteEntity?

    @Query("SELECT * FROM participantes WHERE telefono_senderista = :telefono LIMIT 1")
    suspend fun getParticipanteByTelefono(telefono: String): ParticipanteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipante(participante: ParticipanteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipantes(participantes: List<ParticipanteEntity>): List<Long>

    @Query("DELETE FROM participantes WHERE telefono_senderista = :telefono")
    suspend fun deleteParticipante(telefono: String): Int

    @Query("DELETE FROM participantes WHERE fecha_ingreso < :limitTime")
    suspend fun pruneExpiredParticipantes(limitTime: Long): Int

    @Query("DELETE FROM participantes")
    suspend fun clearParticipantes(): Int

    // --- AGRUPACIONES ---
    @Query("SELECT * FROM agrupaciones ORDER BY nombre ASC")
    fun getAgrupacionesFlow(): Flow<List<AgrupacionEntity>>

    @Query("SELECT * FROM agrupaciones ORDER BY nombre ASC")
    suspend fun getAgrupaciones(): List<AgrupacionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgrupaciones(agrupaciones: List<AgrupacionEntity>): List<Long>

    @Query("DELETE FROM agrupaciones")
    suspend fun deleteAllAgrupaciones(): Int
}
