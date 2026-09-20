package com.gaspar.rutassendero.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RutasDao {
    // --- RUTAS ---
    @Query("SELECT * FROM rutas ORDER BY id DESC")
    fun getAllRutasFlow(): Flow<List<RutaEntity>>

    @Query("SELECT * FROM rutas")
    suspend fun getAllRutas(): List<RutaEntity>

    @Query("SELECT * FROM rutas WHERE id = :id LIMIT 1")
    suspend fun getRutaById(id: String): RutaEntity?

    @Query("SELECT * FROM rutas WHERE nombre = :nombre LIMIT 1")
    suspend fun getRutaByNombre(nombre: String): RutaEntity?

    @Upsert
    suspend fun insertRuta(ruta: RutaEntity): Long

    @Delete
    suspend fun deleteRuta(ruta: RutaEntity): Int

    @Query("SELECT COUNT(*) FROM rutas WHERE publico = 0 AND (telefono = :userPhone OR telefono = '')")
    suspend fun getPrivateRutasCount(userPhone: String): Int

    // --- PUNTOS DE RUTA ---
    @Query("SELECT * FROM puntos_ruta WHERE id_ruta = :idRuta ORDER BY id ASC")
    fun getPuntosForRutaFlow(idRuta: String): Flow<List<PuntoRutaEntity>>

    @Query("SELECT * FROM puntos_ruta WHERE id_ruta = :idRuta ORDER BY id ASC")
    suspend fun getPuntosForRuta(idRuta: String): List<PuntoRutaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPuntoRuta(punto: PuntoRutaEntity): Long

    @Delete
    suspend fun deletePuntoRuta(punto: PuntoRutaEntity): Int

    @Query("DELETE FROM puntos_ruta WHERE id_ruta = :idRuta")
    suspend fun deletePuntosForRuta(idRuta: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM puntos_ruta WHERE id_ruta = :idRuta AND es_fin_ruta = 1 LIMIT 1)")
    suspend fun isRutaFinalized(idRuta: String): Boolean

    // --- SENDERISTA ---
    @Query("SELECT * FROM senderista LIMIT 1")
    fun getSenderistaFlow(): Flow<SenderistaEntity?>

    @Query("SELECT * FROM senderista WHERE email = :email LIMIT 1")
    fun getSenderistaByEmailFlow(email: String): Flow<SenderistaEntity?>

    @Query("SELECT * FROM senderista")
    suspend fun getAllSenderistas(): List<SenderistaEntity>

    @Query("DELETE FROM senderista")
    suspend fun deleteAllSenderistas(): Int

    @Query("DELETE FROM senderista WHERE email != :ownerEmail")
    suspend fun clearCachedSenderistas(ownerEmail: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSenderista(senderista: SenderistaEntity): Long

    // --- TRAZA DE RUTA ---
    @Query("SELECT * FROM traza_ruta WHERE id_ruta = :idRuta ORDER BY orden ASC")
    fun getTrazaForRutaFlow(idRuta: String): Flow<List<TrazaRutaEntity>>

    @Query("SELECT * FROM traza_ruta WHERE id_ruta = :idRuta ORDER BY orden ASC")
    suspend fun getTrazaForRuta(idRuta: String): List<TrazaRutaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrazaRuta(traza: TrazaRutaEntity): Long

    @Query("DELETE FROM traza_ruta WHERE id_ruta = :idRuta")
    suspend fun deleteTrazaForRuta(idRuta: String): Int

    @Query("""
        SELECT COUNT(*) FROM rutas r 
        WHERE NOT EXISTS (
            SELECT 1 FROM puntos_ruta p 
            WHERE p.id_ruta = r.id AND p.es_fin_ruta = 1
        )
    """)
    suspend fun getUnfinalizedRoutesCount(): Int

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

    // --- FRASES DE DIFICULTAD ---
    @Query("SELECT * FROM frases_dificultad")
    suspend fun getAllFrases(): List<FraseDificultadEntity>

    @Query("SELECT * FROM frases_dificultad WHERE categoria = :categoria")
    suspend fun getFrasesForCategory(categoria: String): List<FraseDificultadEntity>

    @Query("SELECT * FROM frases_dificultad WHERE categoria = :categoria ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomFraseForCategory(categoria: String): FraseDificultadEntity?

    @Query("SELECT COUNT(*) FROM frases_dificultad")
    suspend fun getFraseCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrases(frases: List<FraseDificultadEntity>): List<Long>

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

    @Query("SELECT * FROM senderista WHERE telefono = :telefono LIMIT 1")
    suspend fun getSenderistaByTelefono(telefono: String): SenderistaEntity?

    @Query("SELECT * FROM senderista WHERE id_beacon = :idBeacon LIMIT 1")
    suspend fun getSenderistaByBeaconId(idBeacon: String): SenderistaEntity?

    // --- AGRUPACIONES ---
    @Query("SELECT * FROM agrupaciones")
    suspend fun getAgrupaciones(): List<AgrupacionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAgrupaciones(agrupaciones: List<AgrupacionEntity>): List<Long>

    @Query("DELETE FROM agrupaciones")
    suspend fun deleteAllAgrupaciones(): Int
}

