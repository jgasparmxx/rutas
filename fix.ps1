$files = @(
    'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\ui\RutasViewModel.kt',
    'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\ui\components\Components.kt',
    'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\sync\CloudSyncManager.kt',
    'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\service\GpsTrackingService.kt',
    'c:\GASPAR\baches\rutas\MainActivity.kt',
    'c:\GASPAR\baches\rutas\app\src\main\res\values\strings.xml'
)

$replacements = @{
    'Ã¡' = 'á'
    'Ã©' = 'é'
    'Ã­' = 'í'
    'Ã³' = 'ó'
    'Ãº' = 'ú'
    'Ã±' = 'ñ'
    'Ã ' = 'Á'
    'Ã‰' = 'É'
    'Ã ' = 'Í'
    'Ã“' = 'Ó'
    'Ãš' = 'Ú'
    'Ã‘' = 'Ñ'
    'Ã¼' = 'ü'
}

foreach ($f in $files) {
    if (Test-Path $f) {
        $content = Get-Content -Raw -Encoding UTF8 $f
        foreach ($key in $replacements.Keys) {
            $content = $content.Replace($key, $replacements[$key])
        }
        [System.IO.File]::WriteAllText($f, $content, [System.Text.Encoding]::UTF8)
    }
}
Write-Output "Done"
