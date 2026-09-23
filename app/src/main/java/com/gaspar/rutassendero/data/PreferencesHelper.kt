package com.gaspar.rutassendero.data

import android.content.Context
import android.content.SharedPreferences

/**
 * EXPLICACIÓN DE PREFERENCESHELPER:
 *
 * Para guardar configuraciones simples (como interruptores On/Off, valores numéricos de distancia
 * o cadenas de texto breves), no es óptimo usar una tabla de base de datos como se hacía en B4A (`config`).
 * Android nos provee de "SharedPreferences", que es un archivo XML ligero gestionado por el sistema
 * que almacena pares clave-valor de forma persistente.
 *
 * Esta clase envuelve SharedPreferences para ofrecer getters y setters limpios y legibles
 * a lo largo de toda la aplicación.
 */
class PreferencesHelper(context: Context) {

    // Archivo de preferencias "baches_prefs.xml" en modo privado (solo esta app puede leerlo/escribirlo)
    private val prefs: SharedPreferences = context.getSharedPreferences("baches_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALERT_DISTANCE = "alert_distance"
        private const val KEY_SHOW_INACTIVE = "show_inactive"
        private const val KEY_PREFERRED_MAP = "preferred_map"
        private const val KEY_ENABLE_BEEPS = "enable_beeps"
        private const val KEY_ARRIVAL_TOLERANCE = "arrival_tolerance"
        private const val KEY_TAP_DISTANCE_LIMIT = "tap_distance_limit"
        private const val KEY_APP_ROLE = "app_role"
        private const val KEY_FIRST_LAUNCH_TIME = "first_launch_time"
        private const val KEY_TRIAL_WARNING_SHOWN = "trial_warning_shown"
        private const val KEY_RANGO_DIAS_SALIDAS = "rango_dias_salidas"
        private const val KEY_RANGO_KM_SALIDAS = "rango_km_salidas"
        private const val KEY_LAST_RUN_VERSION_CODE = "last_run_version_code"
        private const val KEY_INSTALLATION_COUNT = "installation_count"
        private const val KEY_LAUNCH_COUNT = "launch_count"
        private const val KEY_TRIAL_DAYS_LIMIT = "trial_days_limit"
        private const val KEY_USE_LAUNCH_LIMIT = "use_launch_limit"
        private const val KEY_MAX_LAUNCH_LIMIT = "max_launch_limit"
        private const val KEY_ACTIVE_INVITACION_ID = "active_invitacion_id"
        private const val KEY_OWNER_EMAIL = "owner_email"
        private const val KEY_ACCOUNT_DELETED = "account_deleted"
        private const val KEY_EMERGENCY_TIMEOUT = "emergency_timeout_minutes"
        private const val KEY_ACTIVE_ROUTE_ID = "active_route_id"
        private const val KEY_ACTIVE_ROUTE_FORWARD = "active_route_forward"
    }

    /**
     * Indica si la cuenta del usuario fue eliminada de forma irreversible.
     */
    var accountDeleted: Boolean
        get() = prefs.getBoolean(KEY_ACCOUNT_DELETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ACCOUNT_DELETED, value).apply()

    /**
     * Guarda y obtiene la distancia de alerta en metros.
     * Si no existe, devuelve 30 por defecto.
     */
    var alertDistance: Int
        get() = prefs.getInt(KEY_ALERT_DISTANCE, 50)
        set(value) = prefs.edit().putInt(KEY_ALERT_DISTANCE, value).apply()

    /**
     * Guarda y obtiene la tolerancia en metros para marcar llegada.
     * Si no existe, devuelve 25 por defecto.
     */
    var arrivalTolerance: Int
        get() = prefs.getInt(KEY_ARRIVAL_TOLERANCE, 25)
        set(value) = prefs.edit().putInt(KEY_ARRIVAL_TOLERANCE, value).apply()

    /**
     * Guarda y obtiene el límite en metros desde el cual se permite el tap manual.
     * Si no existe, devuelve 15 por defecto.
     */
    var tapDistanceLimit: Int
        get() = prefs.getInt(KEY_TAP_DISTANCE_LIMIT, 15)
        set(value) = prefs.edit().putInt(KEY_TAP_DISTANCE_LIMIT, value).apply()

    /**
     * Guarda y obtiene si se deben incluir los baches inactivos en las búsquedas/lista.
     * Si no existe, devuelve false por defecto.
     */
    var showInactive: Boolean
        get() = prefs.getBoolean(KEY_SHOW_INACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_INACTIVE, value).apply()

    /**
     * Guarda y obtiene la aplicación de navegación preferida ("waze" o "maps").
     * Si no existe, devuelve "waze" por defecto.
     */
    var preferredMap: String
        get() = prefs.getString(KEY_PREFERRED_MAP, "waze") ?: "waze"
        set(value) = prefs.edit().putString(KEY_PREFERRED_MAP, value).apply()

    /**
     * Guarda y obtiene si la alarma sonora de pitido (Beep) está activa.
     * Si no existe, devuelve true por defecto.
     */
    var enableBeeps: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_BEEPS, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_BEEPS, value).apply()

    /**
     * Guarda y obtiene el rol de la aplicación ("registrar" o "guia").
     * Si no existe, devuelve "registrar" por defecto.
     */
    var appRole: String
        get() = prefs.getString(KEY_APP_ROLE, "registrar") ?: "registrar"
        set(value) = prefs.edit().putString(KEY_APP_ROLE, value).apply()

    /**
     * Guarda y obtiene la fecha/hora del primer inicio de la aplicación en milisegundos.
     * Si no existe, devuelve 0L.
     */
    var firstLaunchTime: Long
        get() = prefs.getLong(KEY_FIRST_LAUNCH_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_FIRST_LAUNCH_TIME, value).apply()

    /**
     * Indica si ya se mostró la advertencia inicial de que la aplicación es de prueba de 20 días.
     * Si no existe, devuelve false.
     */
    var trialWarningShown: Boolean
        get() = prefs.getBoolean(KEY_TRIAL_WARNING_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_TRIAL_WARNING_SHOWN, value).apply()

    /**
     * Rango de búsqueda en días para invitaciones de salida (por defecto 20 días).
     */
    var rangoDiasBusquedaSalidas: Int
        get() = prefs.getInt(KEY_RANGO_DIAS_SALIDAS, 20)
        set(value) = prefs.edit().putInt(KEY_RANGO_DIAS_SALIDAS, value).apply()

    /**
     * Rango de búsqueda en kilómetros para invitaciones de salida (por defecto 30 kms).
     */
    var rangoKmBusquedaSalidas: Int
        get() = prefs.getInt(KEY_RANGO_KM_SALIDAS, 30)
        set(value) = prefs.edit().putInt(KEY_RANGO_KM_SALIDAS, value).apply()

    /**
     * Guarda y obtiene la última versión de la aplicación ejecutada.
     */
    var lastRunVersionCode: Int
        get() = prefs.getInt(KEY_LAST_RUN_VERSION_CODE, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_RUN_VERSION_CODE, value).apply()

    /**
     * Guarda y obtiene el número de veces que se ha instalado/actualizado una versión diferente.
     */
    var installationCount: Int
        get() = prefs.getInt(KEY_INSTALLATION_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_INSTALLATION_COUNT, value).apply()

    /**
     * Guarda y obtiene el número total de inicios/lanzamientos de la aplicación.
     */
    var launchCount: Int
        get() = prefs.getInt(KEY_LAUNCH_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_LAUNCH_COUNT, value).apply()

    /**
     * Límite de días de prueba (por defecto 90 días / 3 meses).
     */
    var trialDaysLimit: Int
        get() {
            val days = prefs.getInt(KEY_TRIAL_DAYS_LIMIT, 90)
            return if (days < 90) 90 else days
        }
        set(value) = prefs.edit().putInt(KEY_TRIAL_DAYS_LIMIT, value).apply()

    /**
     * Si está activo, el límite de prueba se basará en el número de ejecuciones/inicios en lugar de días.
     */
    var useLaunchLimit: Boolean
        get() = prefs.getBoolean(KEY_USE_LAUNCH_LIMIT, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_LAUNCH_LIMIT, value).apply()

    /**
     * Límite máximo de inicios de la app si `useLaunchLimit` está activo.
     */
    var maxLaunchLimit: Int
        get() = prefs.getInt(KEY_MAX_LAUNCH_LIMIT, 50)
        set(value) = prefs.edit().putInt(KEY_MAX_LAUNCH_LIMIT, value).apply()

    /**
     * Guarda y obtiene el identificador de la invitación/salida activa (-1 por defecto).
     */
    var activeInvitacionId: Int
        get() = prefs.getInt(KEY_ACTIVE_INVITACION_ID, -1)
        set(value) = prefs.edit().putInt(KEY_ACTIVE_INVITACION_ID, value).apply()

    /**
     * Guarda y obtiene el correo electrónico del propietario del dispositivo.
     */
    var ownerEmail: String?
        get() = prefs.getString(KEY_OWNER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_OWNER_EMAIL, value).apply()

    /**
     * Timeout en minutos para resetear una emergencia (por defecto 5 minutos).
     */
    var emergencyTimeoutMinutes: Int
        get() = prefs.getInt(KEY_EMERGENCY_TIMEOUT, 5)
        set(value) = prefs.edit().putInt(KEY_EMERGENCY_TIMEOUT, value).apply()

    /**
     * Guarda y obtiene el ID de la ruta activa en modo guía.
     */
    var activeRouteId: String?
        get() = prefs.getString(KEY_ACTIVE_ROUTE_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_ROUTE_ID, value).apply()

    /**
     * Guarda y obtiene la dirección de la ruta activa en modo guía.
     */
    var activeRouteForward: Boolean
        get() = prefs.getBoolean(KEY_ACTIVE_ROUTE_FORWARD, true)
        set(value) = prefs.edit().putBoolean(KEY_ACTIVE_ROUTE_FORWARD, value).apply()



    fun isRouteSyncedToCloud(routeId: String): Boolean {
        val synced = prefs.getStringSet("synced_cloud_routes", emptySet()) ?: emptySet()
        return synced.contains(routeId)
    }

    fun markRouteSyncedToCloud(routeId: String) {
        val synced = prefs.getStringSet("synced_cloud_routes", emptySet())?.toMutableSet() ?: mutableSetOf()
        synced.add(routeId)
        prefs.edit().putStringSet("synced_cloud_routes", synced).apply()
    }

    fun clearRouteSyncedToCloud(routeId: String) {
        val synced = prefs.getStringSet("synced_cloud_routes", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (synced.remove(routeId)) {
            prefs.edit().putStringSet("synced_cloud_routes", synced).apply()
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
