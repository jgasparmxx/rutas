package com.gaspar.rutassendero.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the local database.
 * 
 * Este DAO ahora maneja exclusivamente la responsabilidad de Rutas (Rutas, Puntos y Trazas),
 * cumpliendo con el principio SRP.
 */
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
}
