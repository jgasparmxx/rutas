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
        $content = $content.Replace([string][char]195 + [string][char]161, [string][char]225)  # á
        $content = $content.Replace([string][char]195 + [string][char]169, [string][char]233)  # é
        $content = $content.Replace([string][char]195 + [string][char]173, [string][char]237)  # í
        $content = $content.Replace([string][char]195 + [string][char]179, [string][char]243)  # ó
        $content = $content.Replace([string][char]195 + [string][char]186, [string][char]250)  # ú
        $content = $content.Replace([string][char]195 + [string][char]177, [string][char]241)  # ñ
        $content = $content.Replace([string][char]195 + [string][char]137, [string][char]201)  # É
        $content = $content.Replace([string][char]195 + [string][char]147, [string][char]211)  # Ó
        $content = $content.Replace([string][char]195 + [string][char]154, [string][char]218)  # Ú
        $content = $content.Replace([string][char]195 + [string][char]145, [string][char]209)  # Ñ
        $content = $content.Replace([string][char]195 + [string][char]188, [string][char]252)  # ü
        $content = $content.Replace([string][char]195 + [string][char]129, [string][char]193)  # Á
        $content = $content.Replace([string][char]195 + [string][char]141, [string][char]205)  # Í
        $content = $content.Replace([string][char]194 + [string][char]161, [string][char]161)  # ¡
        $content = $content.Replace([string][char]194 + [string][char]191, [string][char]191)  # ¿
        [System.IO.File]::WriteAllText($f, $content, [System.Text.Encoding]::UTF8)
    }
}
Write-Output "Done"
