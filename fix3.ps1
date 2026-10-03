$path = "C:\GASPAR\baches\rutas_admon\composeApp\src\commonMain\kotlin\com\gaspar\rutas\admon\ui\screens\ConvocationsScreen.kt"
$content = Get-Content $path -Raw

$oldFlow = "val convocationsWithRoute by dao.getConvocatoriasConRutaFlow().collectAsState(initial = emptyList())"
$newFlow = @"
val invitaciones by dao.getInvitacionesConRutaFlow().collectAsState(initial = emptyList())
    val eventos by dao.getEventosConRutaFlow().collectAsState(initial = emptyList())
    
    val convocationsWithRoute = remember(invitaciones, eventos) {
        val eventosConvertidos = eventos.map { it.toConvocatoriaConRuta() }
        (invitaciones + eventosConvertidos).sortedByDescending { it.invitacion.fechaHoraSalida }
    }
"@

$content = $content.Replace($oldFlow, $newFlow)

$oldImport = "import com.gaspar.rutas.admon.data.EventoEntity"
$newImport = "import com.gaspar.rutas.admon.data.EventoEntity`r`nimport com.gaspar.rutas.admon.data.toConvocatoriaConRuta"
$content = $content.Replace($oldImport, $newImport)

Set-Content -Path $path -Value $content -Encoding UTF8
Write-Host "Success"
