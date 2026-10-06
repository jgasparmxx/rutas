package com.gaspar.rutassendero.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FrasesDao {
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
}
