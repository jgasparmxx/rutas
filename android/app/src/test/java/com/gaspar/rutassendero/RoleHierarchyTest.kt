package com.gaspar.rutassendero

import com.gaspar.rutassendero.data.RoleHierarchy
import org.junit.Assert.*
import org.junit.Test

class RoleHierarchyTest {

    @Test
    fun testHierarchyLevels() {
        assertEquals("O debe ser nivel 4 (más alto)", 4, RoleHierarchy.getLevel("O"))
        assertEquals("A debe ser nivel 3", 3, RoleHierarchy.getLevel("A"))
        assertEquals("G debe ser nivel 2", 2, RoleHierarchy.getLevel("G"))
        assertEquals("S debe ser nivel 1 (base)", 1, RoleHierarchy.getLevel("S"))
        assertEquals("Cualquier otro rol debe ser nivel 1 (base)", 1, RoleHierarchy.getLevel("desconocido"))
        assertEquals("Null debe ser nivel 1", 1, RoleHierarchy.getLevel(null))
    }

    @Test
    fun testCanCreateRoutes() {
        // O (Dueño), A (Admin) y G (Guía) pueden crear y subir rutas a la nube
        assertTrue("Owner 'O' puede crear rutas", RoleHierarchy.canCreateRoutes("O"))
        assertTrue("Admin 'A' puede crear rutas (hereda de G)", RoleHierarchy.canCreateRoutes("A"))
        assertTrue("Guia 'G' puede crear rutas", RoleHierarchy.canCreateRoutes("G"))
        assertFalse("Senderista 'S' NO puede crear rutas", RoleHierarchy.canCreateRoutes("S"))
        assertFalse("Rol nulo NO puede crear rutas", RoleHierarchy.canCreateRoutes(null))
        assertFalse("Rol inválido NO puede crear rutas", RoleHierarchy.canCreateRoutes("xyz"))
    }

    @Test
    fun testCanManageInvitations() {
        // O (Dueño) y A (Admin) pueden gestionar invitaciones/salidas
        assertTrue("Owner 'O' puede gestionar invitaciones", RoleHierarchy.canManageInvitations("O"))
        assertTrue("Admin 'A' puede gestionar invitaciones", RoleHierarchy.canManageInvitations("A"))
        assertFalse("Guia 'G' NO puede gestionar invitaciones de agrupación", RoleHierarchy.canManageInvitations("G"))
        assertFalse("Senderista 'S' NO puede gestionar invitaciones", RoleHierarchy.canManageInvitations("S"))
    }

    @Test
    fun testIsOwner() {
        assertTrue("Owner 'O' es dueño", RoleHierarchy.isOwner("O"))
        assertFalse("Admin 'A' no es dueño", RoleHierarchy.isOwner("A"))
        assertFalse("Guia 'G' no es dueño", RoleHierarchy.isOwner("G"))
        assertFalse("Senderista 'S' no es dueño", RoleHierarchy.isOwner("S"))
    }

    @Test
    fun testCanConsumeRoutes() {
        // Todos los roles pueden consumir rutas
        assertTrue(RoleHierarchy.canConsumeRoutes("O"))
        assertTrue(RoleHierarchy.canConsumeRoutes("A"))
        assertTrue(RoleHierarchy.canConsumeRoutes("G"))
        assertTrue(RoleHierarchy.canConsumeRoutes("S"))
    }

    @Test
    fun testSenderistaPromotionalRules() {
        // Senderista puede crear hasta 2 rutas propias (0 o 1 previas -> true; >= 2 -> false)
        assertTrue(RoleHierarchy.canCreateRouteWithQuota("S", 0))
        assertTrue(RoleHierarchy.canCreateRouteWithQuota("S", 1))
        assertFalse(RoleHierarchy.canCreateRouteWithQuota("S", 2))
        assertFalse(RoleHierarchy.canCreateRouteWithQuota("S", 3))

        // Guía / Admin / Owner no tienen límite de 2 rutas
        assertTrue(RoleHierarchy.canCreateRouteWithQuota("G", 2))
        assertTrue(RoleHierarchy.canCreateRouteWithQuota("G", 25))
        assertTrue(RoleHierarchy.canCreateRouteWithQuota("A", 10))
        assertTrue(RoleHierarchy.canCreateRouteWithQuota("O", 50))

        // Distancia mínima relajada a 3.0 km para Senderista incluso si en BD tenía 4.0 km
        assertEquals(3.0, RoleHierarchy.getEffectiveMinRouteDistanceKm("S", 4.0), 0.001)
        assertEquals(3.0, RoleHierarchy.getEffectiveMinRouteDistanceKm("S", null), 0.001)
        assertEquals(4.0, RoleHierarchy.getEffectiveMinRouteDistanceKm("G", 4.0), 0.001)
        assertEquals(4.0, RoleHierarchy.getEffectiveMinRouteDistanceKm("G", null), 0.001)

        // Senderista NO puede borrar una ruta propia una vez registrada; Guía/Admin/Owner sí
        assertFalse(RoleHierarchy.canDeleteOwnFinalizedRoute("S"))
        assertTrue(RoleHierarchy.canDeleteOwnFinalizedRoute("G"))
        assertTrue(RoleHierarchy.canDeleteOwnFinalizedRoute("A"))
        assertTrue(RoleHierarchy.canDeleteOwnFinalizedRoute("O"))
    }
}
