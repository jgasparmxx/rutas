package com.gaspar.rutassendero.data

/**
 * Jerarquía de roles del ecosistema Sendero:
 * O (Dueño / Owner) > A (Administrador) > G (Guía) > S (Senderista)
 *
 * - 'O': El más alto. Puede hacer todo (capacidades de O, A, G y S).
 * - 'A': Administrador de agrupación. Puede hacer lo de A, G y S (gestionar invitaciones, crear rutas y consumirlas).
 * - 'G': Guía. Puede hacer lo de G y S (crear rutas y consumirlas).
 * - 'S': Senderista. Nivel base. Consume rutas (las descarga y visualiza).
 */
object RoleHierarchy {
    const val ROL_OWNER = "O"
    const val ROL_ADMIN = "A"
    const val ROL_GUIA = "G"
    const val ROL_SENDERISTA = "S"

    /** Máximo de rutas propias que un Senderista ('S') puede registrar de forma promocional. */
    const val MAX_RUTAS_SENDERISTA = 2

    /** Distancia mínima en km relajada para las rutas promocionales de un Senderista ('S'). */
    const val MIN_DISTANCIA_KM_SENDERISTA = 3.0

    /** Distancia mínima por defecto en km para Guías ('G', 'A', 'O') cuando no hay otra en su perfil. */
    const val MIN_DISTANCIA_KM_DEFAULT_GUIA = 4.0

    /**
     * Nivel numérico de jerarquía (a mayor número, mayor privilegio).
     */
    fun getLevel(rol: String?): Int {
        return when (rol?.trim()?.uppercase()) {
            ROL_OWNER -> 4
            ROL_ADMIN -> 3
            ROL_GUIA -> 2
            ROL_SENDERISTA -> 1
            else -> 1
        }
    }

    /**
     * Determina si el rol tiene privilegios completos de Guía o superior (G, A, O)
     * para crear rutas de manera ilimitada y borrarlas.
     */
    fun canCreateRoutes(rol: String?): Boolean {
        return getLevel(rol) >= 2 // G (2), A (3), O (4)
    }

    /**
     * Determina si el usuario puede iniciar el registro de una nueva ruta considerando su cuota:
     * - Guía / Admin / Dueño (G, A, O): ilimitadas.
     * - Senderista (S): hasta [MAX_RUTAS_SENDERISTA] rutas propias.
     */
    fun canCreateRouteWithQuota(rol: String?, currentOwnRoutesCount: Int): Boolean {
        return if (canCreateRoutes(rol)) {
            true
        } else {
            currentOwnRoutesCount < MAX_RUTAS_SENDERISTA
        }
    }

    /**
     * Determina si el usuario puede borrar una ruta propia una vez finalizada/registrada:
     * - Guía / Admin / Dueño (G, A, O): sí pueden borrarla.
     * - Senderista (S): NO puede borrarla una vez registrada.
     */
    fun canDeleteOwnFinalizedRoute(rol: String?): Boolean {
        return canCreateRoutes(rol)
    }

    /**
     * Calcula la distancia mínima requerida (en km) para finalizar el registro de una ruta:
     * - Guía / Admin / Dueño (G, A, O): usa `minimoLongitud` de su perfil (por defecto 4.0 km).
     * - Senderista (S): se relaja a 3.0 km (o menor si su perfil tuviera un valor aún más bajo).
     */
    fun getEffectiveMinRouteDistanceKm(rol: String?, profileMinLongitud: Double?): Double {
        return if (canCreateRoutes(rol)) {
            profileMinLongitud ?: MIN_DISTANCIA_KM_DEFAULT_GUIA
        } else {
            minOf(profileMinLongitud ?: MIN_DISTANCIA_KM_SENDERISTA, MIN_DISTANCIA_KM_SENDERISTA)
        }
    }

    /**
     * Determina si el rol tiene privilegios de Administrador o superior (A, O).
     * Permite publicar y gestionar invitaciones / salidas.
     */
    fun canManageInvitations(rol: String?): Boolean {
        return getLevel(rol) >= 3 // A (3), O (4)
    }

    /**
     * Determina si el rol tiene privilegios de Dueño / Superadmin (O).
     * Permite moderación global, cambio de roles y administración de agrupaciones.
     */
    fun isOwner(rol: String?): Boolean {
        return getLevel(rol) >= 4 // O (4)
    }

    /**
     * Todos los roles válidos pueden consumir y consultar rutas.
     */
    fun canConsumeRoutes(rol: String?): Boolean {
        return true
    }

    /**
     * Retorna una etiqueta descriptiva y amigable del rol.
     */
    fun getRoleName(rol: String?): String {
        return when (rol?.trim()?.uppercase()) {
            ROL_OWNER -> "Dueño (O)"
            ROL_ADMIN -> "Administrador (A)"
            ROL_GUIA -> "Guía (G)"
            ROL_SENDERISTA -> "Senderista (S)"
            else -> "Senderista (S)"
        }
    }
}
