$targetDir = "app/src/main/java/com/gaspar/rutassendero"
$files = Get-ChildItem -Path $targetDir -Filter *.kt -Recurse

$senderistaFuncs = @("getSenderistaFlow", "getFirstSenderista", "getSenderistaByEmailFlow", "getSenderistaByEmail", "getAllSenderistas", "deleteAllSenderistas", "clearCachedSenderistas", "insertSenderista", "getSenderistaByTelefono", "getSenderistaByBeaconId")
$eventosFuncs = @("getInvitacionesAceptadasFlow", "getInvitacionesAceptadas", "getInvitacionAceptadaById", "insertInvitacionAceptada", "deleteInvitacionAceptada", "getEventosFlow", "getEventos", "getEventoById", "insertEvento", "deleteEvento")
$participantesFuncs = @("getAllParticipantesFlow", "getAllParticipantes", "getParticipanteByBeaconId", "getParticipanteByTelefono", "insertParticipante", "insertParticipantes", "deleteParticipante", "pruneExpiredParticipantes", "clearParticipantes", "getAgrupacionesFlow", "getAgrupaciones", "insertAgrupaciones", "deleteAllAgrupaciones")
$frasesFuncs = @("getAllFrases", "getFrasesForCategory", "getRandomFraseForCategory", "getFraseCount", "insertFrases")

foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw -Encoding UTF8
    $original = $content
    
    foreach ($func in $senderistaFuncs) {
        $content = $content -replace "db\.rutasDao\(\)\.$func", "db.senderistaDao().$func"
    }
    foreach ($func in $eventosFuncs) {
        $content = $content -replace "db\.rutasDao\(\)\.$func", "db.eventosDao().$func"
    }
    foreach ($func in $participantesFuncs) {
        $content = $content -replace "db\.rutasDao\(\)\.$func", "db.participantesDao().$func"
    }
    foreach ($func in $frasesFuncs) {
        $content = $content -replace "db\.rutasDao\(\)\.$func", "db.frasesDao().$func"
    }

    if ($content -cne $original) {
        [IO.File]::WriteAllText($file.FullName, $content, [System.Text.Encoding]::UTF8)
        Write-Host "Modificado: $($file.Name)"
    }
}
Write-Host "Reemplazos masivos finalizados"
