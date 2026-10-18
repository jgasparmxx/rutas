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

/**
 * Calculador de Gasto Energético y Calorías para Senderismo y Montañismo.
 *
 * =========================================================================================
 * FUNDAMENTO CIENTÍFICO Y RECONOCIMIENTO DE AUTORÍA:
 * =========================================================================================
 * 1. Modelo Metabólico de Desplazamiento Horizontal (Margaria & ACSM):
 *    - Descubierto y formulado por el fisiólogo italiano Rodolfo Margaria (1963) en sus estudios
 *      pioneros sobre bioenergética de la locomoción humana ("Energy cost of running and walking"),
 *      posteriormente refinado por el American College of Sports Medicine (ACSM, 2000).
 *    - Demuestra que el coste energético neto de la marcha sobre terreno plano es directamente
 *      proporcional a la masa corporal y a la distancia recorrida:
 *      Coste_Horizontal ≈ 0.72 - 0.82 kcal / (kg · km)
 *
 * 2. Ecuación de Carga y Pendiente de Pandolf (USARIEM, 1977):
 *    - Desarrollada por Kenneth B. Pandolf, Baruch Givoni y Ralph F. Goldman en el
 *      U.S. Army Research Institute of Environmental Medicine (Natick, MA, EE.UU.).
 *    - Publicación: "Predicting energy expenditure with loads while standing or walking very
 *      slowly", Journal of Applied Physiology (1977).
 *    - La ecuación de Pandolf modela el trabajo contra la gravedad en función del gradiente
 *      inclinado (desnivel positivo), determinando que vencer la fuerza gravitatoria en ascensos
 *      consume una tasa metabólica adicional de aproximadamente 1.6 kcal / (kg · 100 m de desnivel).
 *
 * =========================================================================================
 * FÓRMULA INTEGRADA:
 *    E_total (kcal) = [ Masa (kg) · Distancia (km) · 0.82 ] + [ Masa (kg) · (Δh / 100) · 1.6 ]
 * =========================================================================================
 *
 * JUSTIFICACIÓN DE SU USO EN LA APP:
 * En rutas de montaña, dos senderistas pueden recorrer la misma distancia en kilómetros pero
 * realizar esfuerzos radicalmente distintos debido al desnivel acumulado. La fórmula convencional
 * de podómetros planos subestima entre un 30% y un 60% el gasto en montaña; este algoritmo
 * compensa con precisión el trabajo mecánico vertical real.
 */
object RouteFitnessCalculator {

    /** Coeficiente de coste horizontal (kcal/kg/km) basado en ACSM y Margaria */
    private const val CALORIES_PER_KG_PER_KM = 0.82

    /** Coeficiente de penalización gravitacional por cada 100m de ascenso positivo (kcal/kg/100m) según Pandolf et al. */
    private const val CALORIES_PER_KG_PER_100M_ELEVATION = 1.6

    /**
     * Devuelve el resumen de calorías.
     * Si hay un peso registrado, da el valor exacto. Si no, da las estimaciones por rangos.
     *
     * @param distanceKm Distancia tridimensional recorrida en km.
     * @param elevationGainMeters Desnivel positivo acumulado en metros.
     * @param userWeightKg Masa corporal del usuario en kilogramos (opcional).
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
