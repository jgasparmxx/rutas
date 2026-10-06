package com.gaspar.rutassendero.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RutaEntity::class, PuntoRutaEntity::class, SenderistaEntity::class, TrazaRutaEntity::class, InvitacionAceptadaEntity::class, EventoEntity::class, FraseDificultadEntity::class, ParticipanteEntity::class, AgrupacionEntity::class], version = 34, exportSchema = false)
abstract class RutasDatabase : RoomDatabase() {

    abstract fun rutasDao(): RutasDao
    abstract fun senderistaDao(): SenderistaDao
    abstract fun eventosDao(): EventosDao
    abstract fun participantesDao(): ParticipantesDao
    abstract fun frasesDao(): FrasesDao

    companion object {
        @Volatile
        private var INSTANCE: RutasDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE puntos_ruta ADD COLUMN es_fin_ruta INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rutas ADD COLUMN distancia_gps REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rutas ADD COLUMN distancia_beacons REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rutas ADD COLUMN fecha_hora_inicio TEXT")
                db.execSQL("ALTER TABLE rutas ADD COLUMN fecha_hora_fin TEXT")
                db.execSQL("ALTER TABLE rutas ADD COLUMN duracion_segundos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rutas ADD COLUMN desnivel_positivo REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rutas ADD COLUMN desnivel_negativo REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE rutas ADD COLUMN contador_subidas INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rutas ADD COLUMN contador_bajadas INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Senderista changes
                db.execSQL("ALTER TABLE senderista ADD COLUMN nivel_senderista TEXT NOT NULL DEFAULT 'principiante'")
                
                // Rutas changes
                db.execSQL("ALTER TABLE rutas ADD COLUMN telefono TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rutas ADD COLUMN datos_encriptados TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rutas ADD COLUMN conectividad REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE puntos_ruta ADD COLUMN tiene_conectividad INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS invitaciones_aceptadas (
                        id_invitacion INTEGER NOT NULL PRIMARY KEY,
                        id_ruta TEXT NOT NULL,
                        nombre_ruta TEXT NOT NULL,
                        telefono_creador TEXT NOT NULL,
                        fecha_hora_salida TEXT NOT NULL,
                        punto_reunion TEXT NOT NULL,
                        link_google_maps TEXT,
                        cooperacion_minima REAL NOT NULL,
                        fecha_limite_registro TEXT,
                        nivel TEXT,
                        instrucciones TEXT,
                        latitud REAL,
                        longitud REAL,
                        cooperacion_realizada REAL,
                        fecha_cooperacion_realizada TEXT,
                        sincronizado_con_nube INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN nombre_agrupacion TEXT") // Eliminado
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN conectividad REAL")
                db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN distancia_gps REAL")
                db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN desnivel_positivo REAL")
                db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN desnivel_negativo REAL")
                db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN contador_subidas INTEGER")
                db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN contador_bajadas INTEGER")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN duracion_segundos INTEGER")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS frases_dificultad (
                        id TEXT NOT NULL PRIMARY KEY,
                        categoria TEXT NOT NULL,
                        frase TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE senderista ADD COLUMN id_beacon TEXT")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rutas ADD COLUMN codigo TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE rutas ADD COLUMN publico INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE rutas ADD COLUMN fecha_importacion INTEGER")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS participantes_invitacion_activa (
                        id_invitacion INTEGER NOT NULL,
                        telefono_senderista TEXT NOT NULL,
                        nombre_senderista TEXT NOT NULL,
                        id_beacon TEXT,
                        latitud REAL,
                        longitud REAL,
                        distancia_estimada TEXT,
                        ultimo_rssi INTEGER,
                        ultima_deteccion INTEGER,
                        PRIMARY KEY(id_invitacion, telefono_senderista)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS participantes_invitacion_activa")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS participantes (
                        telefono_senderista TEXT NOT NULL PRIMARY KEY,
                        nombre_senderista TEXT NOT NULL,
                        id_beacon TEXT,
                        latitud REAL,
                        longitud REAL,
                        distancia_estimada TEXT,
                        ultimo_rssi INTEGER,
                        ultima_deteccion INTEGER,
                        fecha_ingreso INTEGER NOT NULL,
                        a_quienes_avisar TEXT,
                        a_quien_avisar_telefono1 TEXT,
                        a_quien_avisar_telefono2 TEXT,
                        tipo_sangre TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE participantes ADD COLUMN fecha_nacimiento TEXT")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE participantes ADD COLUMN alergias TEXT")
                db.execSQL("ALTER TABLE participantes ADD COLUMN enfermedades_cronicas TEXT")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE senderista ADD COLUMN minimo_longitud REAL NOT NULL DEFAULT 4.0")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE senderista ADD COLUMN bloqueado INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Agregar columnas de consentimiento
                db.execSQL("ALTER TABLE senderista ADD COLUMN consentimiento_terminos INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE senderista ADD COLUMN consentimiento_ficha_medica INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE senderista ADD COLUMN consentimiento_gps_multimedia INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE senderista ADD COLUMN timestamp_consentimiento_utc INTEGER")
                
                // Recrear tabla para cambiar tipo_sangre a nullable
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `senderista_new` (`email` TEXT NOT NULL, `rol` TEXT NOT NULL, `telefono` TEXT NOT NULL, `nombre` TEXT NOT NULL, `sexo` TEXT NOT NULL, `fecha_nacimiento` TEXT NOT NULL, `a_quienes_avisar` TEXT NOT NULL, `a_quien_avisar_telefono1` TEXT NOT NULL, `a_quien_avisar_telefono2` TEXT, `tiene_enfermedad_cronica` TEXT NOT NULL, `enfermedad_cronica1` TEXT, `enfermedad_cronica2` TEXT, `tiene_auto` TEXT, `direccion` TEXT, `lleva_mascota` TEXT, `alergias` TEXT, `tipo_sangre` TEXT, `seguro_medico` TEXT, `nivel_senderista` TEXT NOT NULL DEFAULT 'principiante', `id_beacon` TEXT, `minimo_longitud` REAL NOT NULL DEFAULT 4.0, `fupdate` INTEGER NOT NULL, `bloqueado` INTEGER NOT NULL DEFAULT 0, `consentimiento_terminos` INTEGER NOT NULL DEFAULT 0, `consentimiento_ficha_medica` INTEGER NOT NULL DEFAULT 0, `consentimiento_gps_multimedia` INTEGER NOT NULL DEFAULT 0, `timestamp_consentimiento_utc` INTEGER, PRIMARY KEY(`email`))
                """.trimIndent())
                
                db.execSQL("""
                    INSERT INTO senderista_new (email, rol, telefono, nombre, sexo, fecha_nacimiento, a_quienes_avisar, a_quien_avisar_telefono1, a_quien_avisar_telefono2, tiene_enfermedad_cronica, enfermedad_cronica1, enfermedad_cronica2, tiene_auto, direccion, lleva_mascota, alergias, tipo_sangre, seguro_medico, nivel_senderista, id_beacon, minimo_longitud, fupdate, bloqueado, consentimiento_terminos, consentimiento_ficha_medica, consentimiento_gps_multimedia, timestamp_consentimiento_utc)
                    SELECT email, rol, telefono, nombre, sexo, fecha_nacimiento, a_quienes_avisar, a_quien_avisar_telefono1, a_quien_avisar_telefono2, tiene_enfermedad_cronica, enfermedad_cronica1, enfermedad_cronica2, tiene_auto, direccion, lleva_mascota, alergias, tipo_sangre, seguro_medico, nivel_senderista, id_beacon, minimo_longitud, fupdate, bloqueado, consentimiento_terminos, consentimiento_ficha_medica, consentimiento_gps_multimedia, timestamp_consentimiento_utc FROM senderista
                """.trimIndent())
                
                db.execSQL("DROP TABLE senderista")
                db.execSQL("ALTER TABLE senderista_new RENAME TO senderista")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE puntos_ruta ADD COLUMN estado INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE senderista ADD COLUMN codigo_confianza TEXT")
                db.execSQL("ALTER TABLE participantes ADD COLUMN codigo_confianza TEXT")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE participantes ADD COLUMN sexo TEXT")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE puntos_ruta SET estado = 6 WHERE estado = 7")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE senderista ADD COLUMN peso REAL")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS agrupaciones (
                        id_agrupacion TEXT NOT NULL PRIMARY KEY,
                        nombre TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE senderista ADD COLUMN id_agrupacion TEXT")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE senderista ADD COLUMN max_privadas INTEGER NOT NULL DEFAULT 3")
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rutas ADD COLUMN fupdate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE puntos_ruta ADD COLUMN fupdate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE traza_ruta ADD COLUMN fupdate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN fupdate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE frases_dificultad ADD COLUMN fupdate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE participantes ADD COLUMN fupdate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE agrupaciones ADD COLUMN fupdate INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN comprobante_pago BLOB")
                db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN fecha_subida_comprobante TEXT")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN tipo_ruta TEXT NOT NULL DEFAULT 'S'")
                db.execSQL("ALTER TABLE invitaciones_aceptadas ADD COLUMN comentario TEXT")
            }
        }

        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `eventos` (" +
                    "`id_evento` INTEGER NOT NULL, " +
                    "`nombre_evento` TEXT NOT NULL, " +
                    "`telefono_creador` TEXT NOT NULL, " +
                    "`fecha_hora_salida` TEXT NOT NULL, " +
                    "`punto_reunion` TEXT NOT NULL, " +
                    "`link_google_maps` TEXT, " +
                    "`cooperacion_minima` REAL NOT NULL, " +
                    "`fecha_limite_registro` TEXT, " +
                    "`instrucciones` TEXT, " +
                    "`latitud` REAL, " +
                    "`longitud` REAL, " +
                    "`cooperacion_realizada` REAL, " +
                    "`fecha_cooperacion_realizada` TEXT, " +
                    "`sincronizado_con_nube` INTEGER NOT NULL, " +
                    "`comprobante_pago` BLOB, " +
                    "`fecha_subida_comprobante` TEXT, " +
                    "`fupdate` INTEGER NOT NULL DEFAULT 0, " +
                    "`comentario` TEXT, " +
                    "PRIMARY KEY(`id_evento`))"
                )
            }
        }

        fun getDatabase(context: Context): RutasDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RutasDatabase::class.java,
                    "rutas.db"
                )
                .addMigrations(
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, 
                    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                    MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                    MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                    MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25,
                    MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29,
                    MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34
                )
                .fallbackToDestructiveMigration()
                .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
