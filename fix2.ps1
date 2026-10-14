$path = "app/src/main/java/com/gaspar/rutassendero/service/GpsTrackingService.kt"
$lines = Get-Content $path -Encoding UTF8

$startIdx = -1
$endIdx = -1

for ($i=0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match "fun startBeaconEmissionInternal") {
        $startIdx = $i
    }
    if ($startIdx -ne -1 -and $i -gt $startIdx -and $lines[$i] -match "fun stopBeaconEmissionInternal") {
        $endIdx = $i
        break
    }
}

if ($startIdx -ne -1 -and $endIdx -ne -1) {
    $newLines = @()
    for ($i=0; $i -lt $startIdx; $i++) { $newLines += $lines[$i] }

    $newMethod = @"
    fun startBeaconEmissionInternal(idBeacon: String, isEmergency: Boolean = false, onSuccess: () -> Unit, onFailure: (Int) -> Unit) {
        val lastLoc = currentLocation.value
        serviceScope.launch {
            val success = beaconManager.startEmitting(
                idBeacon = idBeacon,
                latitude = lastLoc?.latitude?.toFloat(),
                longitude = lastLoc?.longitude?.toFloat(),
                minor = if (isEmergency) 911 else 1
            )
            
            if (success) {
                _isBeaconEmitting.value = true
                _isBeaconEmergencyEmitting.value = isEmergency
                
                // Monitor location changes and update advertising with current GPS
                beaconEmissionJob?.cancel()
                beaconEmissionJob = serviceScope.launch {
                    var lastAdvertisedLoc: Location? = lastLoc
                    var lastAdvertiseTime = System.currentTimeMillis()
                    currentLocation.collect { newLoc ->
                        if (newLoc != null) {
                            val now = System.currentTimeMillis()
                            val dist = lastAdvertisedLoc?.distanceTo(newLoc) ?: Float.MAX_VALUE
                            
                            // Only restart advertising if moved > 10 meters AND at least 20 seconds have passed
                            if ((dist > 10.0f && now - lastAdvertiseTime > 20000L) || lastAdvertisedLoc == null) {
                                lastAdvertisedLoc = newLoc
                                lastAdvertiseTime = now
                                beaconManager.startEmitting(
                                    idBeacon = idBeacon,
                                    latitude = newLoc.latitude.toFloat(),
                                    longitude = newLoc.longitude.toFloat(),
                                    minor = if (isEmergency) 911 else 1
                                )
                            }
                        }
                    }
                }
                
                onSuccess()
            } else {
                _isBeaconEmitting.value = false
                onFailure(-1)
            }
        }
    }

"@.TrimEnd()

    # Split by newline and add to array
    $newLines += $newMethod -split "`r`n|`n"
    
    # Add empty line before stopBeaconEmissionInternal if needed
    $newLines += ""

    for ($i=$endIdx; $i -lt $lines.Length; $i++) { $newLines += $lines[$i] }

    [IO.File]::WriteAllLines((Resolve-Path $path).Path, $newLines, [System.Text.Encoding]::UTF8)
    Write-Host "Modificado GpsTrackingService exitosamente!"
} else {
    Write-Host "No se encontraron los limites del metodo."
}
