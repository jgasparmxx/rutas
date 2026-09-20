import os

files = [
    r'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\ui\RutasViewModel.kt',
    r'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\ui\components\Components.kt',
    r'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\sync\CloudSyncManager.kt',
    r'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\service\GpsTrackingService.kt',
    r'c:\GASPAR\baches\rutas\app\src\main\java\com\gaspar\rutassendero\MainActivity.kt'
]

replacements = {
    'Ã¡': 'á',
    'Ã©': 'é',
    'Ã­': 'í',
    'Ã³': 'ó',
    'Ãº': 'ú',
    'Ã±': 'ñ',
    'Ã ': 'Á',
    'Ã‰': 'É',
    'Ã ': 'Í',
    'Ã“': 'Ó',
    'Ãš': 'Ú',
    'Ã‘': 'Ñ',
    'Ã¼': 'ü'
}

for f in files:
    if os.path.exists(f):
        with open(f, 'r', encoding='utf-8') as file:
            content = file.read()
        
        for k, v in replacements.items():
            content = content.replace(k, v)
            
        with open(f, 'w', encoding='utf-8') as file:
            file.write(content)
print('Done')
