$path = "C:\GASPAR\baches\rutas_admon\composeApp\src\commonMain\kotlin\com\gaspar\rutas\admon\data\RutasAdmonDao.kt"
$content = Get-Content $path -Raw
$pattern = "(?s)@androidx\.room\.Transaction\s*@Query\(`"`"`"[\s\S]*?UNION ALL[\s\S]*?ORDER BY CAST\(fecha_hora_salida AS REAL\) DESC\s*`"`"`"\)\s*fun getConvocatoriasConRutaFlow\(\):\s*Flow<List<ConvocatoriaConRuta>>"
$replacement = "@androidx.room.Transaction`r`n    @Query(`"SELECT * FROM invitaciones_ruta ORDER BY fecha_hora_salida DESC`")`r`n    fun getInvitacionesConRutaFlow(): Flow<List<ConvocatoriaConRuta>>`r`n`r`n    @androidx.room.Transaction`r`n    @Query(`"SELECT * FROM eventos ORDER BY fecha_hora_salida DESC`")`r`n    fun getEventosConRutaFlow(): Flow<List<EventoConRuta>>"
$content = $content -replace $pattern, $replacement
Set-Content -Path $path -Value $content -Encoding UTF8
Write-Host "Replaced!"
