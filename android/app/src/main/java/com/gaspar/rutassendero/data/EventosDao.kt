package com.gaspar.rutassendero.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventosDao {
    // --- INVITACIONES ACEPTADAS (SALIDAS) ---
    @Query("SELECT * FROM invitaciones_aceptadas ORDER BY fecha_hora_salida ASC")
    fun getInvitacionesAceptadasFlow(): Flow<List<InvitacionAceptadaEntity>>

    @Query("SELECT * FROM invitaciones_aceptadas ORDER BY fecha_hora_salida ASC")
    suspend fun getInvitacionesAceptadas(): List<InvitacionAceptadaEntity>

    @Query("SELECT * FROM invitaciones_aceptadas WHERE id_invitacion = :id LIMIT 1")
    suspend fun getInvitacionAceptadaById(id: Int): InvitacionAceptadaEntity?

    @Upsert
    suspend fun insertInvitacionAceptada(invitacion: InvitacionAceptadaEntity): Long

    @Delete
    suspend fun deleteInvitacionAceptada(invitacion: InvitacionAceptadaEntity): Int

    // --- EVENTOS ---
    @Query("SELECT * FROM eventos ORDER BY fecha_hora_salida ASC")
    fun getEventosFlow(): Flow<List<EventoEntity>>

    @Query("SELECT * FROM eventos ORDER BY fecha_hora_salida ASC")
    suspend fun getEventos(): List<EventoEntity>

    @Query("SELECT * FROM eventos WHERE id_evento = :id LIMIT 1")
    suspend fun getEventoById(id: Int): EventoEntity?

    @Upsert
    suspend fun insertEvento(evento: EventoEntity): Long

    @Delete
    suspend fun deleteEvento(evento: EventoEntity): Int
}
