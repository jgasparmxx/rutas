package com.gaspar.rutassendero

import com.gaspar.rutassendero.ui.RouteStatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteStatsElevationPhraseTest {

    @Test
    fun testElevationDeltaCalculation() {
        assertEquals(20, RouteStatsCalculator.calculateElevationDeltaMeters(1500.0, 1520.2))
        assertEquals(-15, RouteStatsCalculator.calculateElevationDeltaMeters(1520.0, 1504.9))
        assertEquals(0, RouteStatsCalculator.calculateElevationDeltaMeters(1500.0, 1500.3))
        assertNull(RouteStatsCalculator.calculateElevationDeltaMeters(0.0, 1520.0))
        assertNull(RouteStatsCalculator.calculateElevationDeltaMeters(1500.0, 0.0))
    }

    @Test
    fun testSimulationAndCardPhrases() {
        assertEquals(
            "Distancia a caminar al siguiente punto es 100 metros, subida son 20 metros.",
            RouteStatsCalculator.formatNextPointSimulationSpeech(100f, 20)
        )
        assertEquals(
            "Distancia a caminar al siguiente punto es 100 metros, bajada son 15 metros.",
            RouteStatsCalculator.formatNextPointSimulationSpeech(100f, -15)
        )
        assertEquals(
            "Distancia a caminar al siguiente punto: 100 m, subida son 20 metros",
            RouteStatsCalculator.formatNextPointCardPhrase(100f, 20)
        )
        assertEquals(
            "Distancia a caminar al siguiente punto: 100 m, bajada son 15 metros",
            RouteStatsCalculator.formatNextPointCardPhrase(100f, -15)
        )
        assertEquals(
            "↑ Subida 20 m",
            RouteStatsCalculator.formatElevationHeaderBadge(20)
        )
        assertEquals(
            "↓ Bajada 15 m",
            RouteStatsCalculator.formatElevationHeaderBadge(-15)
        )
        assertEquals(
            ", subida de 20 metros",
            RouteStatsCalculator.formatElevationSpeechSuffix(20)
        )
        assertEquals(
            ", bajada de 15 metros",
            RouteStatsCalculator.formatElevationSpeechSuffix(-15)
        )
    }
}

