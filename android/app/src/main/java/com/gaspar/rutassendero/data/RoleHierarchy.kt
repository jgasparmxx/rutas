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
     * Determina si el rol tiene privilegios de Guía o superior (G, A, O).
     * Permite mapear, agregar y subir rutas a la nube.
     */
    fun canCreateRoutes(rol: String?): Boolean {
        return getLevel(rol) >= 2 // G (2), A (3), O (4)
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
