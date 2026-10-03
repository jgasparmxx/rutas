$daoPath = "C:\GASPAR\baches\rutas_admon\composeApp\src\commonMain\kotlin\com\gaspar\rutas\admon\data\RutasAdmonDao.kt"
$entitiesPath = "C:\GASPAR\baches\rutas_admon\composeApp\src\commonMain\kotlin\com\gaspar\rutas\admon\data\Entities.kt"

# Update Dao
$daoContent = Get-Content $daoPath -Raw

$oldDao = @"
    // --- LIMPIEZA DE TABLAS (LOGOUT) ---
    @androidx.room.Transaction
    @Query("`"`"`"
        SELECT * FROM invitaciones_ruta 
        UNION ALL 
        SELECT * FROM eventos 
        ORDER BY CAST(fecha_hora_salida AS REAL) DESC
    `"`"`")
    fun getConvocatoriasConRutaFlow(): Flow<List<ConvocatoriaConRuta>>
"@

$newDao = @"
    // --- LIMPIEZA DE TABLAS (LOGOUT) ---
    @androidx.room.Transaction
    @Query("SELECT * FROM invitaciones_ruta ORDER BY fecha_hora_salida DESC")
    fun getInvitacionesConRutaFlow(): Flow<List<ConvocatoriaConRuta>>

    @androidx.room.Transaction
    @Query("SELECT * FROM eventos ORDER BY fecha_hora_salida DESC")
    fun getEventosConRutaFlow(): Flow<List<EventoConRuta>>
"@

$daoContent = $daoContent.Replace($oldDao, $newDao)
Set-Content -Path $daoPath -Value $daoContent -Encoding UTF8

# Update Entities (Warning fixes and appending EventoConRuta)
$entitiesContent = Get-Content $entitiesPath -Raw

$oldSenderista = @"
@Entity(
    tableName = "senderistas",
    foreignKeys = [
        ForeignKey(
            entity = AgrupacionEntity::class,
            parentColumns = ["id_agrupacion"],
            childColumns = ["id_agrupacion"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
"@

$newSenderista = @"
@Entity(
    tableName = "senderistas",
    foreignKeys = [
        ForeignKey(
            entity = AgrupacionEntity::class,
            parentColumns = ["id_agrupacion"],
            childColumns = ["id_agrupacion"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        androidx.room.Index(value = ["id_agrupacion"])
    ]
)
"@
$entitiesContent = $entitiesContent.Replace($oldSenderista, $newSenderista)

$oldInvitacion = @"
@Entity(
    tableName = "invitaciones_ruta",
    foreignKeys = [
        ForeignKey(
            entity = RutaEntity::class,
            parentColumns = ["id"],
            childColumns = ["id_ruta"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
"@

$newInvitacion = @"
@Entity(
    tableName = "invitaciones_ruta",
    foreignKeys = [
        ForeignKey(
            entity = RutaEntity::class,
            parentColumns = ["id"],
            childColumns = ["id_ruta"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["id_ruta"])
    ]
)
"@
$entitiesContent = $entitiesContent.Replace($oldInvitacion, $newInvitacion)

$oldParticipante = @"
@Entity(
    tableName = "participantes_invitacion",
    primaryKeys = ["id_invitacion", "email_senderista"],
    foreignKeys = [
        ForeignKey(
            entity = InvitacionRutaEntity::class,
            parentColumns = ["id_invitacion"],
            childColumns = ["id_invitacion"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SenderistaEntity::class,
            parentColumns = ["email"],
            childColumns = ["email_senderista"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
"@

$newParticipante = @"
@Entity(
    tableName = "participantes_invitacion",
    primaryKeys = ["id_invitacion", "email_senderista"],
    foreignKeys = [
        ForeignKey(
            entity = InvitacionRutaEntity::class,
            parentColumns = ["id_invitacion"],
            childColumns = ["id_invitacion"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SenderistaEntity::class,
            parentColumns = ["email"],
            childColumns = ["email_senderista"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["id_invitacion"]),
        androidx.room.Index(value = ["email_senderista"])
    ]
)
"@
$entitiesContent = $entitiesContent.Replace($oldParticipante, $newParticipante)

# Append if not exists
if (-not $entitiesContent.Contains("data class EventoConRuta")) {
    $appendCode = @"

data class EventoConRuta(
    @androidx.room.Embedded
    val evento: EventoEntity,
    
    @androidx.room.Relation(
        parentColumn = "id_ruta",
        entityColumn = "id"
    )
    val ruta: RutaEntity?
)

fun EventoEntity.toInvitacionRutaEntity(): InvitacionRutaEntity {
    return InvitacionRutaEntity(
        idInvitacion = this.idInvitacion,
        idRuta = this.idRuta,
        telefono = this.telefono,
        fechaHoraSalida = this.fechaHoraSalida,
        puntoReunion = this.puntoReunion,
        linkGoogleMaps = this.linkGoogleMaps,
        cooperacionMinima = this.cooperacionMinima,
        fechaLimiteRegistro = this.fechaLimiteRegistro,
        fechaCreacion = this.fechaCreacion,
        nivel = this.nivel,
        instrucciones = this.instrucciones,
        latitud = this.latitud,
        longitud = this.longitud,
        nombreAgrupacion = this.nombreAgrupacion,
        costo = this.costo,
        comentario = this.comentario,
        desgloseCostos = this.desgloseCostos,
        tipoRuta = this.tipoRuta,
        fupdate = this.fupdate
    )
}

fun EventoConRuta.toConvocatoriaConRuta(): ConvocatoriaConRuta {
    return ConvocatoriaConRuta(
        invitacion = this.evento.toInvitacionRutaEntity(),
        ruta = this.ruta
    )
}
"@
    $entitiesContent += $appendCode
}

Set-Content -Path $entitiesPath -Value $entitiesContent -Encoding UTF8
Write-Host "Success"
