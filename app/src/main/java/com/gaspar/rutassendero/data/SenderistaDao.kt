package com.gaspar.rutassendero.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SenderistaDao {
    @Query("SELECT * FROM senderista LIMIT 1")
    fun getSenderistaFlow(): Flow<SenderistaEntity?>

    @Query("SELECT * FROM senderista LIMIT 1")
    suspend fun getFirstSenderista(): SenderistaEntity?

    @Query("SELECT * FROM senderista WHERE email = :email LIMIT 1")
    fun getSenderistaByEmailFlow(email: String): Flow<SenderistaEntity?>

    @Query("SELECT * FROM senderista WHERE email = :email LIMIT 1")
    suspend fun getSenderistaByEmail(email: String): SenderistaEntity?

    @Query("SELECT * FROM senderista")
    suspend fun getAllSenderistas(): List<SenderistaEntity>

    @Query("DELETE FROM senderista")
    suspend fun deleteAllSenderistas(): Int

    @Query("DELETE FROM senderista WHERE email != :ownerEmail")
    suspend fun clearCachedSenderistas(ownerEmail: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSenderista(senderista: SenderistaEntity): Long

    @Query("SELECT * FROM senderista WHERE telefono = :telefono LIMIT 1")
    suspend fun getSenderistaByTelefono(telefono: String): SenderistaEntity?

    @Query("SELECT * FROM senderista WHERE id_beacon = :idBeacon LIMIT 1")
    suspend fun getSenderistaByBeaconId(idBeacon: String): SenderistaEntity?
}
