package com.gaspar.rutassendero.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object RouteFitnessCalculator {

    private const val CALORIES_PER_KG_PER_KM = 0.82
    private const val CALORIES_PER_KG_PER_100M_ELEVATION = 1.6

    /**
     * Devuelve el resumen de calorías.
     * Si hay un peso registrado, da el valor exacto. Si no, da las estimaciones por rangos.
     *
     * @param distanceKm Distancia de la ruta.
     * @param elevationGainMeters Desnivel positivo.
     * @param userWeightKg Peso del usuario (puede ser null o 0.0 si no lo ha registrado).
     */
    fun getCaloriesSummary(
        distanceKm: Double,
        elevationGainMeters: Double,
        userWeightKg: Double? = null
    ): String {
        if (distanceKm <= 0 && elevationGainMeters <= 0) return "Comienza a caminar para estimar las calorías."

        // Si el usuario registró su peso (y es mayor a 0)
        if (userWeightKg != null && userWeightKg > 0.0) {
            val exactCalories = calculateBaseCalories(distanceKm, elevationGainMeters, userWeightKg)
            return "Peso: $userWeightKg kg  🔥 ~$exactCalories kcal"
        }

        // Si NO hay peso registrado, mostramos las estimaciones generales con los nuevos rangos
        val calLight = calculateBaseCalories(distanceKm, elevationGainMeters, 68.0)
        val calMid = calculateBaseCalories(distanceKm, elevationGainMeters, 77.5)
        val calHeavy = calculateBaseCalories(distanceKm, elevationGainMeters, 85.0)

        return """
            🔥 Calorías Estimadas
            < 75 kg: ~$calLight kcal
            75 - 80 kg: ~$calMid kcal
            > 80 kg: ~$calHeavy kcal
        """.trimIndent()
    }

    private fun calculateBaseCalories(distanceKm: Double, elevationGainMeters: Double, weightKg: Double): Int {
        val distanceCalories = weightKg * distanceKm * CALORIES_PER_KG_PER_KM
        val elevationCalories = weightKg * (elevationGainMeters / 100.0) * CALORIES_PER_KG_PER_100M_ELEVATION
        return (distanceCalories + elevationCalories).toInt()
    }
}

@Composable
fun CaloriesSummaryCard(
    distanceKm: Double,
    elevationGainMeters: Double,
    userWeightKg: Double? = null,
    modifier: Modifier = Modifier
) {
    val summaryText = RouteFitnessCalculator.getCaloriesSummary(
        distanceKm = distanceKm,
        elevationGainMeters = elevationGainMeters,
        userWeightKg = userWeightKg
    )

    // Componente transparente que se adapta al color de fondo donde se inserte
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                color = androidx.compose.ui.graphics.Color(0xFFC7BDB1), // Color textMuted del diálogo
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
