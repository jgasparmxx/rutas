package com.gaspar.rutassendero.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Overlay de pantalla completa en negro absoluto OLED que protege la app
 * contra toques accidentales cuando el teléfono se guarda en el bolsillo.
 *
 * Muestra información básica de conveniencia y exige una pulsación continua de 0.75 segundos
 * sobre el candado para desbloquear, haciendo imposible que la tela del bolsillo active nada.
 */
@Composable
fun PocketLockOverlay(
    modifier: Modifier = Modifier,
    routeName: String? = null,
    distanceText: String? = null,
    durationText: String? = null,
    pointsCount: Int = 0,
    onUnlock: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Reloj dinámico
    var currentTime by remember {
        mutableStateOf(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }

    // Progreso de pulsación continua (0f a 1f)
    var isPressingUnlock by remember { mutableStateOf(false) }
    var rawProgress by remember { mutableFloatStateOf(0f) }

    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        label = "UnlockHoldProgress"
    )

    LaunchedEffect(isPressingUnlock) {
        if (isPressingUnlock) {
            val holdDurationMs = 750L // 0.75 segundos requeridos (la mitad del tiempo anterior)
            val start = System.currentTimeMillis()
            while (isPressingUnlock) {
                val elapsed = System.currentTimeMillis() - start
                val p = (elapsed.toFloat() / holdDurationMs).coerceIn(0f, 1f)
                rawProgress = p
                if (p >= 1f) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUnlock()
                    break
                }
                delay(16)
            }
        } else {
            rawProgress = 0f
        }
    }

    // Fondo negro OLED con interceptación total de toques
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* Consumir cualquier toque accidental en el fondo */ }
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // --- PARTE SUPERIOR: Reloj e Indicador de Modo Bolsillo ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = currentTime,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Light,
                    color = Color(0xFFE2E8F0),
                    fontFamily = FontFamily.SansSerif
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFFACC15),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MODO BOLSILLO PROTEGIDO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFFFACC15)
                    )
                }
            }

            // --- PARTE CENTRAL: Estadísticas de la ruta activa ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                if (!routeName.isNullOrBlank()) {
                    Text(
                        text = routeName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F2937)),
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (distanceText != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = distanceText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Distancia",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        if (durationText != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = durationText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Tiempo",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$pointsCount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Puntos",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Pantalla protegida contra toques accidentales.\nEl GPS sigue registrando la ruta en segundo plano.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }

            // --- PARTE INFERIOR: Botón de desbloqueo sostenido (0.75 segundos) ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(88.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitFirstDown(requireUnconsumed = false)
                                    isPressingUnlock = true
                                    waitForUpOrCancellation()
                                    isPressingUnlock = false
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (isPressingUnlock) Color(0xFF1E293B) else Color(0xFF0F172A))
                            .border(
                                width = 2.dp,
                                color = if (isPressingUnlock) Color(0xFFFACC15) else Color(0xFF334155),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (rawProgress >= 0.7f) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Desbloquear",
                            tint = if (isPressingUnlock) Color(0xFFFACC15) else Color(0xFF94A3B8),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(86.dp),
                        color = Color(0xFFFACC15),
                        strokeWidth = 4.dp,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isPressingUnlock) "Mantén presionado..." else "Mantén presionado para desbloquear",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isPressingUnlock) Color(0xFFFACC15) else Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
