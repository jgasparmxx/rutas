package com.gaspar.rutassendero

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.gaspar.rutassendero.data.RutasDatabase
import com.gaspar.rutassendero.data.PreferencesHelper

import com.cloudinary.android.MediaManager
import com.gaspar.rutassendero.sync.SecureCredentialsManager

/**
 * Clase de Aplicación principal. Se ejecuta antes de cualquier pantalla o servicio.
 * Aquí inicializamos las instancias globales de la base de datos, preferencias
 * y registramos los canales de notificaciones de Android.
 */
class BachesApplication : Application() {

    // Inicialización perezosa (lazy) de la base de datos Room.
    // Solo se creará la base de datos cuando se acceda a ella por primera vez.
    val database: RutasDatabase by lazy { 
        RutasDatabase.getDatabase(this) 
    }

    // Inicialización perezosa del ayudante de preferencias del usuario.
    val preferencesHelper: PreferencesHelper by lazy { 
        PreferencesHelper(this) 
    }

    override fun onCreate() {
        super.onCreate()
        
        // Inicializar Cloudinary de manera segura
        try {
            MediaManager.init(this, SecureCredentialsManager.getCloudinaryConfig())
        } catch (e: Exception) {
            // Ya puede estar inicializado o haber algún error en las credenciales
            e.printStackTrace()
        }

        // Crear los canales de notificación requeridos para Android Oreo (API 26) o superior
        createNotificationChannels()
    }

    /**
     * Registra los canales de notificación en el sistema Android.
     * Esto permite agrupar y controlar los sonidos/vibración de las notificaciones.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Canal 1: Servicio en primer plano (Foreground Service)
            // Mantiene viva la app en background con baja prioridad (sin molestar al usuario)
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "Servicio de Localización GPS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra la notificación persistente del GPS en segundo plano."
            }

            // Canal 2: Alertas de baches cercanos (Alta prioridad)
            // Vibra, emite sonido (TTS) y aparece en la parte superior (High Importance)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Alertas de Baches Cercanos",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Lanza notificaciones urgentes al acercarse a un bache o tope."
                enableVibration(true)
                // Establece el patrón de vibración: 0ms espera, 500ms vibrar, 250ms esperar, 500ms vibrar
                vibrationPattern = longArrayOf(0, 500, 250, 500)
            }

            // Registrar canales en el sistema
            notificationManager?.createNotificationChannel(serviceChannel)
            notificationManager?.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        // Constantes de identificación de los canales para usarse en el servicio
        const val CHANNEL_SERVICE_ID = "baches_service_channel"
        const val CHANNEL_ALERT_ID = "baches_alert_channel_kotlin"
    }
}
