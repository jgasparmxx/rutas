$files = @(
    'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\ui\RutasViewModel.kt',
    'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\ui\components\Components.kt',
    'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\sync\CloudSyncManager.kt',
    'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\service\GpsTrackingService.kt',
    'c:\GASPAR\baches\rutas\MainActivity.kt',
    'c:\GASPAR\baches\rutas\app\src\main\res\values\strings.xml'
)

foreach ($f in $files) {
    if (Test-Path $f) {
        $content = Get-Content -Raw -Encoding UTF8 $f
        $content = $content.Replace('Ã¡', 'á')
        $content = $content.Replace('Ã©', 'é')
        $content = $content.Replace('Ã­', 'í')
        $content = $content.Replace('Ã³', 'ó')
        $content = $content.Replace('Ãº', 'ú')
        $content = $content.Replace('Ã±', 'ñ')
        $content = $content.Replace('Ã‰', 'É')
        $content = $content.Replace('Ã“', 'Ó')
        $content = $content.Replace('Ãš', 'Ú')
        $content = $content.Replace('Ã‘', 'Ñ')
        $content = $content.Replace('Ã¼', 'ü')
        $content = $content.Replace('Â¡', '¡')
        $content = $content.Replace('Â¿', '¿')
        [System.IO.File]::WriteAllText($f, $content, [System.Text.Encoding]::UTF8)
    }
}
Write-Output "Done"
