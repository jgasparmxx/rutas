package com.gaspar.rutassendero.ui.components

// REGULA LIMITACIÓN DE RUTAS PRIVADAS: Por regla el usuario sólo puede tener un máximo de 3 rutas no públicas (privadas). Esta regla cambiará cuando coloquemos la app en la tienda (Play Store).

import android.graphics.BitmapFactory
import android.location.Location
import com.gaspar.rutassendero.data.ParticipanteEntity
import com.gaspar.rutassendero.utils.CaloriesSummaryCard
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.flow.first
import androidx.core.text.HtmlCompat
import androidx.compose.ui.text.font.FontStyle
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.util.Log
import com.gaspar.rutassendero.data.RutaEntity
import com.gaspar.rutassendero.data.PuntoRutaEntity
import com.gaspar.rutassendero.data.InvitacionAceptadaEntity
import com.gaspar.rutassendero.R
import androidx.compose.ui.res.painterResource
import com.gaspar.rutassendero.data.TrazaRutaEntity
import com.gaspar.rutassendero.data.SenderistaEntity
import com.gaspar.rutassendero.data.RutasDatabase
import com.gaspar.rutassendero.data.AgrupacionEntity
import com.gaspar.rutassendero.ui.PuntoRutaUIModel
import android.app.DatePickerDialog
import java.util.Calendar
import com.gaspar.rutassendero.ui.RutasViewModel
import com.gaspar.rutassendero.ui.DriveSyncState
import com.gaspar.rutassendero.ui.CloudSyncState
import com.gaspar.rutassendero.sync.GoogleDriveSyncManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import java.io.File
import android.content.Context
import android.content.Intent
import android.content.ContentValues
import android.provider.MediaStore
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.media.ExifInterface
import android.graphics.Matrix
import android.graphics.Bitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTransformGestures

// --- CONFIGURACIÓN DE COLORES PREMIUM (BRANDING RUTASSENDERO) ---
val ColorTekinsPrimary = Color(0xFF0F766E)     // Verde azulado oscuro (Teal 700) para ambiente bosque/senderismo
val ColorTekinsAccent = Color(0xFF2DD4BF)      // Menta brillante (Teal 400) para relieve
val ColorTekinsDarkBg = Color(0xFF0F172A)      // Fondo oscuro Slate-900
val ColorTekinsCardBg = Color(0xFF1E293B)      // Tarjeta Slate-800
val ColorTekinsCardMuted = Color(0xFF334155)   // Tarjeta inactiva Slate-700
val ColorTekinsAlert = Color(0xFFF43F5E)       // Rose/Red para alertas
val ColorTekinsAlertDark = Color(0xFFE11D48)   // Rose oscuro
val ColorTekinsSuccess = Color(0xFF10B981)     // Esmeralda éxito
val ColorTextoPrincipal = Color(0xFFF8FAFC)    // Texto blanco Slate-50
val ColorTextoSecundario = Color(0xFF94A3B8)   // Texto gris Slate-400

/**
 * Utilidad para decodificar y rotar un bitmap según sus metadatos EXIF.
 */
fun decodeAndRotateBitmap(filePath: String): Bitmap? {
    try {
        val file = File(filePath)
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(filePath) ?: return null
        val exif = ExifInterface(filePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        var rotate = 0f
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotate = 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> rotate = 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> rotate = 270f
        }
        if (rotate != 0f) {
            matrix.postRotate(rotate)
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            return rotatedBitmap
        }
        return bitmap
    } catch (e: Exception) {
        Log.e("RutasSendero", "Error al decodificar y rotar la imagen: ${e.message}", e)
        return null
    }
}

suspend fun aplicarRotacionFisicaAArchivo(rutaArchivo: String, gradosRotacion: Float): Boolean {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (gradosRotacion % 360 == 0f) return@withContext true
            val bitmapOriginal = BitmapFactory.decodeFile(rutaArchivo) ?: return@withContext false
            val matrix = Matrix().apply { postRotate(gradosRotacion) }
            val bitmapRotado = Bitmap.createBitmap(
                bitmapOriginal, 0, 0, 
                bitmapOriginal.width, bitmapOriginal.height, 
                matrix, true
            )
            java.io.FileOutputStream(rutaArchivo).use { out ->
                bitmapRotado.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            bitmapOriginal.recycle()
            true
        } catch (e: Exception) {
            Log.e("RutasSendero", "Error al rotar imagen: ${e.message}", e)
            false
        }
    }
}

/**
 * Resuelve el archivo físico local para una ruta dada (ruta directa, URL o nombre de archivo en filesDir/cacheDir).
 */
fun resolveImageFile(context: Context, filePath: String): File? {
    if (filePath.isEmpty() || filePath.startsWith("res://")) return null
    val direct = File(filePath)
    if (direct.exists() && direct.length() > 0) return direct

    val fileName = if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
        filePath.substringAfterLast("/")
    } else {
        direct.name
    }

    if (fileName.isNotEmpty()) {
        val fFiles = File(context.filesDir, fileName)
        if (fFiles.exists() && fFiles.length() > 0) return fFiles
        val fCache = File(context.cacheDir, fileName)
        if (fCache.exists() && fCache.length() > 0) return fCache
    }
    return null
}

/**
 * COMPONENTE: Mostrar imagen local o de la nube de forma nativa, resiliente y segura.
 */
@Composable
fun LocalImage(filePath: String, modifier: Modifier = Modifier) {
    if (filePath.startsWith("res://")) {
        val resourceName = filePath.removePrefix("res://")
        val context = androidx.compose.ui.platform.LocalContext.current
        val resId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
        if (resId != 0) {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(id = resId),
                    contentDescription = "Icono de estado",
                    tint = Color.Unspecified,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        } else {
            Box(modifier = modifier.background(Color.Gray), contentAlignment = Alignment.Center) {
                Text("Sin Icono", color = Color.White, fontSize = 12.sp)
            }
        }
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var resolvedFile by remember(filePath) { mutableStateOf(resolveImageFile(context, filePath)) }
    var isDownloading by remember(filePath) { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        if (resolvedFile == null && (filePath.startsWith("http://") || filePath.startsWith("https://"))) {
            if (com.gaspar.rutassendero.utils.NetworkUtils.isNetworkAvailable(context)) {
                isDownloading = true
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val fileName = filePath.substringAfterLast("/")
                        val target = File(context.filesDir, fileName)
                        java.net.URL(filePath).openStream().use { input ->
                            java.io.FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (target.exists() && target.length() > 0) {
                            resolvedFile = target
                        }
                    } catch (e: Exception) {
                        Log.w("LocalImage", "No se pudo cachear foto remota: ${e.message}")
                    } finally {
                        isDownloading = false
                    }
                }
            }
        }
    }

    val file = resolvedFile
    if (file != null && file.exists()) {
        val bitmap = remember(file.absolutePath) {
            try {
                decodeAndRotateBitmap(file.absolutePath)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Foto identificativa del punto",
                contentScale = ContentScale.Fit,
                modifier = modifier
            )
        } else {
            Box(
                modifier = modifier.background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Text("Error de imagen", color = Color.White, fontSize = 12.sp)
            }
        }
    } else if (isDownloading) {
        Box(
            modifier = modifier.background(ColorTekinsCardMuted),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = ColorTekinsAccent, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Cargando foto...", color = ColorTextoSecundario, fontSize = 11.sp)
            }
        }
    } else if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
        Box(
            modifier = modifier.background(ColorTekinsCardMuted),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = ColorTextoSecundario, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Foto en la nube (Sin conexión)", color = ColorTextoSecundario, fontSize = 11.sp)
            }
        }
    } else {
        Box(
            modifier = modifier.background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Text("Sin Foto", color = Color.White, fontSize = 12.sp)
        }
    }
}

/**
 * DIÁLOGO: Visor de fotos a pantalla completa con gestos de zoom (pinch-to-zoom),
 * arrastre (panning), doble toque para zoom rápido y opción de compartir.
 */
@Composable
fun FullScreenPhotoViewerDialog(
    filePath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isResource = filePath.startsWith("res://")
    val effectivePath = remember(filePath) {
        resolveImageFile(context, filePath)?.absolutePath ?: filePath
    }
    
    // Key para recargar la imagen si se guarda rotada
    var reloadKey by remember { mutableIntStateOf(0) }

    val bitmap = remember(effectivePath, reloadKey) {
        if (isResource) null else {
            try {
                decodeAndRotateBitmap(effectivePath)
            } catch (e: Exception) {
                null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            var rotacionVisual by remember { mutableFloatStateOf(0f) }
            var guardando by remember { mutableStateOf(false) }
            val coroutineScope = rememberCoroutineScope()

            Box(modifier = Modifier.fillMaxSize()) {
                if (bitmap != null) {
                    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        if (scale > 1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                            offset = Offset.Zero
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        offset += pan
                                    } else {
                                        offset = Offset.Zero
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Foto en pantalla completa",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                    rotationZ = rotacionVisual
                                )
                        )
                    }
                } else if (isResource) {
                    val resourceName = filePath.removePrefix("res://")
                    val resId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
                    if (resId != 0) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(id = resId),
                                contentDescription = "Icono a pantalla completa",
                                tint = Color.White,
                                modifier = Modifier.size(250.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No se pudo cargar la imagen", color = Color.White)
                    }
                }

                // Barra superior de acciones (Cerrar y Compartir)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }

                    if (bitmap != null) {
                        IconButton(
                            onClick = {
                                sharePhoto(context, File(filePath))
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir foto",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Barra inferior de acciones (Girar y Guardar)
                if (bitmap != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { rotacionVisual += 90f },
                            enabled = !guardando,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = ColorTekinsCardMuted,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Girar")
                            Spacer(Modifier.width(8.dp))
                            Text("Girar")
                        }

                        Button(
                            onClick = {
                                if (rotacionVisual % 360f != 0f) {
                                    guardando = true
                                    coroutineScope.launch {
                                        val exito = aplicarRotacionFisicaAArchivo(filePath, rotacionVisual)
                                        if (exito) {
                                            rotacionVisual = 0f
                                            reloadKey++ // Forzar recarga de la imagen
                                            Toast.makeText(context, "Foto rotada y guardada", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT).show()
                                        }
                                        guardando = false
                                    }
                                } else {
                                    Toast.makeText(context, "Gira la foto primero", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !guardando,
                            colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent)
                        ) {
                            if (guardando) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp), 
                                    color = Color.White, 
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = "Guardar")
                                Spacer(Modifier.width(8.dp))
                                Text("Guardar rotación")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Helper para obtener nombre, icono drawable y color representativo según el estado del punto.
 */
fun getEstadoPuntoInfo(estado: Int, esFinRuta: Boolean = false): Triple<String, Int, Color> {
    if (esFinRuta) {
        return Triple("Fin de ruta", R.drawable.ic_peligro, Color(0xFFD32F2F))
    }
    return when(estado) {
        1 -> Triple("Punto normal", R.drawable.ic_punto_normal, Color(0xFF1976D2))
        2 -> Triple("Sitio de interés", R.drawable.ic_sitio_interes, Color(0xFF2E7D32))
        3 -> Triple("Bifurcación", R.drawable.ic_bifurcacion, Color(0xFF5E35B1))
        4 -> Triple("Suelo flojo", R.drawable.ic_suelo_flojo, Color(0xFF8D6E63))
        5 -> Triple("Cuidado", R.drawable.ic_aviso_cuidado, Color(0xFFFBC02D))
        6 -> Triple("Alerta", R.drawable.ic_calavera_alerta, Color(0xFFD32F2F))
        8 -> Triple("Vehículo", R.drawable.ic_aviso_vehiculo, Color(0xFF607D8B))
        9 -> Triple("Refugio", R.drawable.ic_casa, Color(0xFF4E342E))
        10 -> Triple("Comida", R.drawable.ic_comida, Color(0xFFE65100))
        else -> Triple("Punto normal", R.drawable.ic_punto_normal, Color(0xFF1976D2))
    }
}

/**
 * COMPONENTE: Tarjeta individual para mostrar un punto de ruta.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PuntoRutaItem(
    model: PuntoRutaUIModel,
    isTarget: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val punto = model.entity

    // Animación para indicar que es el punto al que debemos ir (Target)
    val infiniteTransition = rememberInfiniteTransition(label = "target_alert")
    val targetBgColor by infiniteTransition.animateColor(
        initialValue = ColorTekinsPrimary,
        targetValue = ColorTekinsPrimary.copy(alpha = 0.7f),
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "target_bg"
    )

    val cardBg = if (isTarget) targetBgColor else ColorTekinsCardBg
    val borderStroke = if (isTarget) BorderStroke(2.dp, ColorTekinsAccent) else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp, horizontal = 10.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val estadoInfo = getEstadoPuntoInfo(punto.estado, punto.esFinRuta)

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF9C4)), // Fondo amarillo suave
                contentAlignment = Alignment.Center
            ) {
                if (punto.esFinRuta) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = ColorTekinsAlert,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = estadoInfo.second),
                        contentDescription = null,
                        tint = estadoInfo.third,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = buildString {
                        if (isTarget) append("🎯 ")
                        if (punto.esFinRuta) append("🏁 ")
                        append(punto.descripcion)
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextoPrincipal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format("Alt: %.1fm | (%.5f, %.5f)", punto.altitud, punto.latitud, punto.longitud),
                    fontSize = 10.sp,
                    color = ColorTextoSecundario,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (punto.tieneConectividad) "📶 Conectado" else "📵 Sin señal",
                        fontSize = 10.sp,
                        color = if (punto.tieneConectividad) Color(0xFF10B981) else ColorTextoSecundario,
                        fontWeight = if (punto.tieneConectividad) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Chip para el estado del punto
                    Surface(
                        color = Color(0xFFFFF9C4), // Fondo claro
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = estadoInfo.first,
                            fontSize = 10.sp,
                            color = estadoInfo.third,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Distancia del senderista al punto
            val distanceText = when {
                model.distanceMeters == null -> "--"
                model.distanceMeters >= 1000f -> String.format("%.1f km", model.distanceMeters / 1000f)
                else -> "${model.distanceMeters.toInt()} m"
            }

            Text(
                text = distanceText,
                fontSize = if (isTarget) 18.sp else 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isTarget) ColorTekinsAccent else ColorTextoPrincipal,
                textAlign = TextAlign.End
            )
        }
    }
}

/**
 * DIÁLOGO: Crear una nueva ruta.
 */
@Composable
fun AddRutaDialog(
    lat: Double,
    lng: Double,
    onAdd: (nombre: String, descripcion: String, publico: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }

    var descripcion by remember { mutableStateOf("") }
    var publico by remember { mutableStateOf(true) } // Default is public (true)
    var showError by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val db = remember { com.gaspar.rutassendero.data.RutasDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Crear Nueva Ruta",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextoPrincipal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre de la Ruta") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorTekinsAccent,
                        unfocusedBorderColor = ColorTextoSecundario,
                        focusedLabelColor = ColorTekinsAccent,
                        unfocusedLabelColor = ColorTextoSecundario,
                        focusedTextColor = ColorTextoPrincipal,
                        unfocusedTextColor = ColorTextoPrincipal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )



                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción de la Ruta") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorTekinsAccent,
                        unfocusedBorderColor = ColorTextoSecundario,
                        focusedLabelColor = ColorTekinsAccent,
                        unfocusedLabelColor = ColorTextoSecundario,
                        focusedTextColor = ColorTextoPrincipal,
                        unfocusedTextColor = ColorTextoPrincipal
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = publico,
                        onCheckedChange = { publico = it },
                        colors = CheckboxDefaults.colors(checkedColor = ColorTekinsAccent)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Ruta Pública (se comparte en la nube)",
                        color = ColorTextoPrincipal,
                        fontSize = 13.sp
                    )
                }

                if (showError) {
                    Text(
                        text = "El nombre de la ruta es obligatorio.",
                        color = ColorTekinsAlert,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = ColorTextoSecundario)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nombre.trim().isNotEmpty()) {
                                scope.launch {
                                    if (!publico) {
                                        val count = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val phone = db.rutasDao().getAllSenderistas().firstOrNull()?.telefono ?: ""
                                            db.rutasDao().getPrivateRutasCount(phone)
                                        }
                                        val limit = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            db.rutasDao().getAllSenderistas().firstOrNull()?.maxPrivadas ?: 3
                                        }
                                        if (count >= limit) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                Toast.makeText(context, "Límite alcanzado: Sólo puedes tener un máximo de $limit rutas no públicas.", Toast.LENGTH_LONG).show()
                                            }
                                            return@launch
                                        }
                                    }
                                    onAdd(nombre.trim(), descripcion.trim(), publico)
                                }
                            } else {
                                showError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary)
                    ) {
                        Text("Guardar", color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * DIÁLOGO: Editar una ruta existente.
 */
@Composable
fun EditRutaDialog(
    ruta: RutaEntity,
    onSave: (nombre: String, descripcion: String) -> Unit,
    onDismiss: () -> Unit
) {
    var nombre by remember { mutableStateOf(ruta.nombre) }

    var descripcion by remember { mutableStateOf(ruta.descripcion) }
    var showError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Editar Detalles de la Ruta",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextoPrincipal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre de la Ruta") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorTekinsAccent,
                        unfocusedBorderColor = ColorTextoSecundario,
                        focusedLabelColor = ColorTekinsAccent,
                        unfocusedLabelColor = ColorTextoSecundario,
                        focusedTextColor = ColorTextoPrincipal,
                        unfocusedTextColor = ColorTextoPrincipal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )



                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción de la Ruta") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorTekinsAccent,
                        unfocusedBorderColor = ColorTextoSecundario,
                        focusedLabelColor = ColorTekinsAccent,
                        unfocusedLabelColor = ColorTextoSecundario,
                        focusedTextColor = ColorTextoPrincipal,
                        unfocusedTextColor = ColorTextoPrincipal
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                if (showError) {
                    Text(
                        text = "El nombre de la ruta es obligatorio.",
                        color = ColorTekinsAlert,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = ColorTextoSecundario)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nombre.trim().isNotEmpty()) {
                                onSave(nombre.trim(), descripcion.trim())
                            } else {
                                showError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary)
                    ) {
                        Text("Guardar", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun getSortKey(fechaStr: String): String {
    val parts = fechaStr.split("/")
    if (parts.size == 3) {
        val day = parts[0].padStart(2, '0')
        val month = parts[1].padStart(2, '0')
        val year = parts[2]
        return "$year$month$day"
    }
    return fechaStr
}

internal fun matchPhones(p1: String, p2: String): Boolean {
    val clean1 = p1.filter { it.isDigit() }
    val clean2 = p2.filter { it.isDigit() }
    if (clean1.isEmpty() || clean2.isEmpty()) return clean1 == clean2
    return if (clean1.length >= 10 && clean2.length >= 10) {
        clean1.takeLast(10) == clean2.takeLast(10)
    } else {
        clean1 == clean2
    }
}

/**
 * DIÁLOGO: Seleccionar una ruta existente o eliminar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectRutaDialog(
    rutas: List<RutaEntity>,
    location: Location?,
    isGuideMode: Boolean,
    senderistaName: String = "",
    userPhone: String = "",
    senderistaRol: String = "S",
    showActions: Boolean = true,
    onSelect: (RutaEntity) -> Unit,
    onAddClick: () -> Unit = {},
    onCloudImportClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    onDelete: (RutaEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var showAll by remember { mutableStateOf(false) }
    val isGuia = com.gaspar.rutassendero.data.RoleHierarchy.canCreateRoutes(senderistaRol)
    var activeTab by remember { mutableStateOf(if (isGuia) 0 else 1) } // 0: Mis Rutas, 1: Otras Rutas
    var rutaToDeleteConfirm by remember { mutableStateOf<RutaEntity?>(null) }

    // Filtrar rutas según la pestaña seleccionada
    val filteredRutas = remember(rutas, activeTab, userPhone) {
        rutas.filter {
            val isOwn = matchPhones(it.telefono, userPhone) || (it.fechaImportacion == null && it.telefono.isEmpty())
            if (activeTab == 0) {
                isOwn
            } else {
                !isOwn
            }
        }
    }

    // Ordenar las rutas por fecha de forma descendente (más recientes primero)
    val sortedRutas = remember(filteredRutas) {
        filteredRutas.sortedWith { r1, r2 ->
            val key1 = getSortKey(r1.fecha)
            val key2 = getSortKey(r2.fecha)
            val dateComp = key2.compareTo(key1)
            if (dateComp != 0) dateComp else r2.id.compareTo(r1.id)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Seleccionar Ruta",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextoPrincipal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Pestañas (Mis Rutas vs Otras Rutas)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .background(ColorTekinsCardMuted, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    if (isGuia) {
                        TabButton(
                            text = "Mis Rutas",
                            isSelected = activeTab == 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            activeTab = 0
                            showAll = false
                        }
                    }
                    TabButton(
                        text = "Otras Rutas",
                        isSelected = activeTab == 1,
                        modifier = Modifier.weight(1f)
                    ) {
                        activeTab = 1
                        showAll = false
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (sortedRutas.isEmpty()) {
                    Text(
                        text = if (activeTab == 0) {
                            "No hay rutas locales creadas por ti."
                        } else {
                            "No hay rutas de otros senderistas descargadas."
                        },
                        color = ColorTextoSecundario,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                } else {
                    val titleText = if (activeTab == 0) {
                        if (showAll) "Mis rutas locales (más recientes primero):"
                        else if (sortedRutas.size > 5) "Mis 5 rutas locales más recientes:"
                        else "Mis rutas locales (más recientes primero):"
                    } else {
                        if (showAll) "Rutas de otros (más recientes primero):"
                        else if (sortedRutas.size > 5) "Las 5 rutas de otros más recientes:"
                        else "Rutas de otros (más recientes primero):"
                    }
                    Text(
                        text = titleText,
                        fontSize = 13.sp,
                        color = ColorTekinsAccent,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        val displayRutas = if (showAll) sortedRutas else sortedRutas.take(5)
                        displayRutas.forEach { ruta ->
                            key(ruta.id) {
                                val distToStart = if (location != null) {
                                    val rLoc = Location("").apply {
                                        latitude = ruta.latitudOrig
                                        longitude = ruta.longitudOrig
                                    }
                                    location.distanceTo(rLoc)
                                } else {
                                    null
                                }
                                val distStr = if (distToStart != null) {
                                    if (distToStart >= 1000f) {
                                        String.format(java.util.Locale.US, " | A %.1f km", distToStart / 1000f)
                                    } else {
                                        " | A ${distToStart.toInt()} m"
                                    }
                                } else {
                                    ""
                                }

                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { dismissValue ->
                                        if (dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                            rutaToDeleteConfirm = ruta
                                        }
                                        false
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = true,
                                    enableDismissFromEndToStart = false,
                                    backgroundContent = {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(vertical = 4.dp)
                                                .background(ColorTekinsAlert, RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Eliminar",
                                                    tint = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Eliminar",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    },
                                    content = {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(ColorTekinsCardBg)
                                                .clickable { onSelect(ruta) }
                                                .padding(vertical = 10.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(ruta.nombre, fontWeight = FontWeight.Bold, color = ColorTextoPrincipal)
                                                val dateParts = ruta.fecha.split("/")
                                                val dateFormatted = if (dateParts.size == 3) {
                                                    val day = dateParts[0]
                                                    val month = dateParts[1]
                                                    val year = dateParts[2]
                                                    val shortYear = if (year.length == 4) year.substring(2) else year
                                                    "$day/$month/$shortYear"
                                                } else {
                                                    ruta.fecha
                                                }
                                                val distKm = ruta.distanciaGps / 1000.0
                                                val distStrFormatted = String.format(java.util.Locale("es", "ES"), "%.2f", distKm)

                                                Spacer(modifier = Modifier.height(2.dp))
                                                val autorStr = if (activeTab == 0) {
                                                    if (senderistaName.isNotEmpty()) senderistaName else "Mío"
                                                } else {
                                                    ruta.telefono.ifEmpty { "Desconocido" }
                                                }
                                                val keyLabel = if (!ruta.publico && ruta.codigo.isNotEmpty()) {
                                                    "\nClave de acceso: ${ruta.codigo}"
                                                } else {
                                                    ""
                                                }
                                                Text("autor: $autorStr, $dateFormatted, $distStrFormatted kms$distStr$keyLabel", fontSize = 11.sp, color = ColorTextoSecundario)
                                            }
                                            Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = ColorTekinsAccent, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                )
                                Divider(color = ColorTekinsCardMuted)
                            }
                        }

                        if (!showAll && sortedRutas.size > 5) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAll = true }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Ver más (${sortedRutas.size - 5} más)...",
                                    color = ColorTekinsAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Ver más",
                                    tint = ColorTekinsAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }


                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showActions) {
                            if (!isGuideMode) {
                                Button(
                                    onClick = onAddClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsSuccess),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Nueva", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Button(
                                onClick = onCloudImportClick,
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nube", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        if (activeTab == 0) {
                            Button(
                                onClick = onSyncClick,
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sincronizar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cerrar", color = ColorTextoSecundario, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (rutaToDeleteConfirm != null) {
        val isOwn = matchPhones(rutaToDeleteConfirm!!.telefono, userPhone) || (rutaToDeleteConfirm!!.fechaImportacion == null && rutaToDeleteConfirm!!.telefono.isEmpty())
        val title = if (isOwn) "Eliminar Mi Ruta" else "Eliminar Ruta de Otros"
        val message = if (isOwn) {
            "¿Deseas eliminar tu ruta '${rutaToDeleteConfirm!!.nombre}'? Se borrará localmente y en el servidor, incluyendo las fotos locales y en la nube."
        } else {
            "¿Deseas eliminar la ruta '${rutaToDeleteConfirm!!.nombre}' descargada de otro senderista? Se borrará todo localmente."
        }

        AlertDialog(
            onDismissRequest = { rutaToDeleteConfirm = null },
            containerColor = ColorTekinsCardBg,
            titleContentColor = ColorTextoPrincipal,
            textContentColor = ColorTextoSecundario,
            title = { Text(text = title, fontWeight = FontWeight.Bold) },
            text = { Text(text = message) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAlert),
                    onClick = {
                        val temp = rutaToDeleteConfirm!!
                        rutaToDeleteConfirm = null
                        onDelete(temp)
                    }
                ) {
                    Text("Borrar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { rutaToDeleteConfirm = null }
                ) {
                    Text("Cancelar", color = ColorTextoSecundario)
                }
            }
        )
    }
}
/**
 * DIÁLOGO: Registrar un punto de ruta.
 */
@Composable
fun AddPuntoRutaDialog(
    idRuta: String,
    lat: Double,
    lng: Double,
    alt: Double,
    accuracy: Float,
    capturedPhotoPath: String?,
    dictatedText: String?,
    isFinRuta: Boolean = false,
    onLaunchCamera: () -> Unit,
    onLaunchMic: () -> Unit,
    onClearDictatedText: () -> Unit,
    onSave: (desc: String, fotoPath: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var descripcion by remember { mutableStateOf(if (isFinRuta) "Fin de Ruta" else "") }

    LaunchedEffect(dictatedText) {
        if (dictatedText != null) {
            descripcion = dictatedText
            onClearDictatedText()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (isFinRuta) "Registrar Fin de Ruta" else "Registrar Punto de Ruta",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextoPrincipal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = String.format("Lat: %.5f | Lng: %.5f (±%.1fm)\nAlt: %.1fm", lat, lng, accuracy, alt),
                    fontSize = 11.sp,
                    color = ColorTextoSecundario,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Área de foto
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ColorTekinsCardMuted),
                    contentAlignment = Alignment.Center
                ) {
                    if (capturedPhotoPath != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LocalImage(filePath = capturedPhotoPath, modifier = Modifier.fillMaxSize())
                            // Botón de re-toma
                            IconButton(
                                onClick = onLaunchCamera,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "Cambiar Foto", tint = Color.White)
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onLaunchCamera,
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(ColorTekinsPrimary, CircleShape)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = "Tomar Foto", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tomar Foto de Referencia (Obligatorio)", fontSize = 12.sp, color = ColorTextoSecundario)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción o Referencia visual (Opcional)") },
                    trailingIcon = {
                        IconButton(onClick = onLaunchMic) {
                            Icon(Icons.Default.Mic, contentDescription = "Dictar voz", tint = ColorTekinsAccent)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorTekinsAccent,
                        unfocusedBorderColor = ColorTextoSecundario,
                        focusedLabelColor = ColorTekinsAccent,
                        unfocusedLabelColor = ColorTextoSecundario,
                        focusedTextColor = ColorTextoPrincipal,
                        unfocusedTextColor = ColorTextoPrincipal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Fila de botones para Cancelar / Guardar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = ColorTextoSecundario, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            val finalDesc = descripcion.trim()
                            onSave(finalDesc, capturedPhotoPath)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary),
                        enabled = capturedPhotoPath != null
                    ) {
                        Text("Guardar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * DIÁLOGO: Detalle de un Punto de Ruta.
 * Permite ver la información del punto, modificar el texto y foto con doble tap,
 * y borrar el punto directamente. Cuenta con un diseño más grande y helphint instructivo.
 */
/**
 * DIÁLOGO: Detalle de un Punto de Ruta (Diseño Simplificado y Auto-Guardado).
 * Sin botones en la parte inferior para ahorrar espacio. Muestra un botón de cerrar arriba.
 * Al hacer doble tap en la foto o el texto, se permite modificarlos y los cambios se guardan
 * automáticamente de forma persistente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuntoRutaDetailDialog(
    punto: PuntoRutaEntity,
    dictatedText: String?,
    onLaunchMic: () -> Unit,
    onClearDictatedText: () -> Unit,
    onSave: (String, Int) -> Unit, // Auto-guarda al cambiar descripción o estado
    onSetAsFinRuta: () -> Unit = {},
    onDismiss: () -> Unit,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    isLastPoint: Boolean = false,
    isCreator: Boolean = true,
    routeHasEnd: Boolean = false,
    onRecoverPhoto: (onFinished: (Boolean) -> Unit) -> Unit = {}
) {
    var currentText by remember { mutableStateOf(punto.descripcion) }
    var currentState by remember { mutableStateOf(punto.estado) }
    var expanded by remember { mutableStateOf(false) }
    var showFullScreenPhoto by remember { mutableStateOf(false) }
    var isRecoveringPhoto by remember { mutableStateOf(false) }
    var showConfirmFinDialog1 by remember { mutableStateOf(false) }
    var showConfirmFinDialog2 by remember { mutableStateOf(false) }
    
    // Sincronizar cambios cuando el punto cambia de ID (ej. al hacer swipe)
    LaunchedEffect(punto.id) {
        currentText = punto.descripcion
        currentState = punto.estado
        isRecoveringPhoto = false
        showConfirmFinDialog1 = false
        showConfirmFinDialog2 = false
    }
    
    // Sincronizar dictado por voz
    LaunchedEffect(dictatedText) {
        if (dictatedText != null && isCreator) {
            currentText = dictatedText
            onClearDictatedText()
            onSave(dictatedText, currentState)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        var totalDragX by remember { mutableStateOf(0f) }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(10.dp)
                .pointerInput(punto.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            if (totalDragX < -100f) {
                                onNext()
                            } else if (totalDragX > 100f) {
                                onPrevious()
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        }
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Fila superior: Título y botón cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detalle del Punto",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorTextoPrincipal
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = ColorTextoSecundario)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Selector de estado
                val estadosPunto = listOf(
                    Triple(1, "Punto normal", Color(0xFF1976D2)),
                    Triple(2, "Sitio de interés", Color(0xFF2E7D32)),
                    Triple(3, "Bifurcación", Color(0xFF5E35B1)),
                    Triple(4, "Suelo flojo", Color(0xFF8D6E63)),
                    Triple(5, "Cuidado", Color(0xFFFBC02D)),
                    Triple(6, "Alerta", Color(0xFFD32F2F)),
                    Triple(8, "Vehículo", Color(0xFF607D8B)),
                    Triple(9, "Refugio", Color(0xFF4E342E)),
                    Triple(10, "Comida", Color(0xFFE65100))
                )
                val getIconForState = { estadoId: Int ->
                    when(estadoId) {
                        1 -> R.drawable.ic_punto_normal
                        2 -> R.drawable.ic_sitio_interes
                        3 -> R.drawable.ic_bifurcacion
                        4 -> R.drawable.ic_suelo_flojo
                        5 -> R.drawable.ic_aviso_cuidado
                        6 -> R.drawable.ic_calavera_alerta
                        8 -> R.drawable.ic_aviso_vehiculo
                        9 -> R.drawable.ic_casa
                        10 -> R.drawable.ic_comida
                        else -> R.drawable.ic_punto_normal
                    }
                }
                
                val currentStateData = estadosPunto.find { it.first == currentState } ?: estadosPunto[0]
                
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Button(
                        onClick = { if (isCreator && !routeHasEnd) expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = currentStateData.third),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = getIconForState(currentState)),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = currentStateData.second, color = Color.White, fontWeight = FontWeight.Bold)
                        
                        if (isCreator && !routeHasEnd) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                        }
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f).background(ColorTekinsCardBg)
                    ) {
                        estadosPunto.forEach { option ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(id = getIconForState(option.first)),
                                            contentDescription = null,
                                            tint = option.third,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(option.second, color = ColorTextoPrincipal)
                                    }
                                },
                                onClick = {
                                    currentState = option.first
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Foto de referencia (Hacer clic para abrir en pantalla completa)
                val displayPhotoPath = punto.fotoPath
                if (!displayPhotoPath.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showFullScreenPhoto = true }
                    ) {
                        LocalImage(
                            filePath = displayPhotoPath,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(ColorTekinsCardMuted, RoundedCornerShape(10.dp))
                            .clickable(enabled = !isRecoveringPhoto) {
                                isRecoveringPhoto = true
                                onRecoverPhoto { success ->
                                    isRecoveringPhoto = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRecoveringPhoto) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = ColorTekinsAccent,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Buscando foto en la nube...",
                                    fontSize = 12.sp,
                                    color = ColorTextoSecundario
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = ColorTekinsAccent,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Sin foto. Toca para buscar en la nube.",
                                    fontSize = 12.sp,
                                    color = ColorTextoSecundario
                                )
                            }
                        }
                    }
                }

                if (showFullScreenPhoto && !displayPhotoPath.isNullOrEmpty()) {
                    FullScreenPhotoViewerDialog(
                        filePath = displayPhotoPath,
                        onDismiss = { showFullScreenPhoto = false }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Datos de coordenadas y altitud
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ColorTekinsCardMuted.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = String.format("Lat.: %.6f | Long.: %.6f", punto.latitud, punto.longitud),
                        fontSize = 11.sp,
                        color = ColorTextoSecundario,
                        fontWeight = FontWeight.Medium
                    )
                    val fechaMinutos = if (punto.fechaHora.count { it == ':' } >= 2) punto.fechaHora.substringBeforeLast(":") else punto.fechaHora
                    Text(
                        text = String.format("Altitud: %.1f m | Fecha: %s", punto.altitud, fechaMinutos),
                        fontSize = 11.sp,
                        color = ColorTextoSecundario
                    )
                    Text(
                        text = if (punto.tieneConectividad) "Conectado (📶)" else "Sin señal (📵)",
                        fontSize = 11.sp,
                        color = if (punto.tieneConectividad) Color(0xFF10B981) else ColorTextoSecundario,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Campo de texto para la descripción
                OutlinedTextField(
                    value = currentText,
                    onValueChange = { if (isCreator) currentText = it },
                    readOnly = !isCreator,
                    label = { Text("Descripción o Referencia visual") },
                    trailingIcon = if (isCreator) {
                        {
                            IconButton(onClick = onLaunchMic) {
                                Icon(Icons.Default.Mic, contentDescription = "Dictar voz", tint = ColorTekinsAccent)
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorTekinsAccent,
                        unfocusedBorderColor = ColorTextoSecundario,
                        focusedLabelColor = ColorTekinsAccent,
                        unfocusedLabelColor = ColorTextoSecundario,
                        focusedTextColor = ColorTextoPrincipal,
                        unfocusedTextColor = ColorTextoPrincipal
                    )
                )

                if (!punto.esFinRuta && isCreator && !routeHasEnd) {
                    if (isLastPoint) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                showConfirmFinDialog1 = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E)), // Alert Crimson
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Establecer como Fin de Ruta",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else if (punto.esFinRuta) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF10B981).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Este punto es el Fin de la Ruta",
                            color = Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Fila de botones para cerrar y guardar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isCreator && !displayPhotoPath.isNullOrEmpty()) {
                        val context = LocalContext.current
                        Button(
                            onClick = {
                                sharePhoto(context, java.io.File(displayPhotoPath))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartir Foto",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compartir Foto", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cerrar", color = ColorTextoSecundario, fontWeight = FontWeight.SemiBold)
                    }

                    if (isCreator) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                onSave(currentText.trim(), currentState)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary)
                        ) {
                            Text("Guardar Cambios", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showConfirmFinDialog1) {
            AlertDialog(
                onDismissRequest = { showConfirmFinDialog1 = false },
                containerColor = ColorTekinsCardBg,
                titleContentColor = ColorTextoPrincipal,
                textContentColor = ColorTextoSecundario,
                title = {
                    Text(
                        text = "¿Establecer como Fin de Ruta?",
                        fontWeight = FontWeight.Bold,
                        color = ColorTextoPrincipal
                    )
                },
                text = {
                    Text(
                        text = "¿Deseas marcar este punto como el final del recorrido?",
                        color = ColorTextoSecundario,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmFinDialog1 = false
                            showConfirmFinDialog2 = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                    ) {
                        Text("Sí, continuar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmFinDialog1 = false }) {
                        Text("Cancelar", color = ColorTextoSecundario)
                    }
                }
            )
        }

        if (showConfirmFinDialog2) {
            AlertDialog(
                onDismissRequest = { showConfirmFinDialog2 = false },
                containerColor = ColorTekinsCardBg,
                titleContentColor = ColorTextoPrincipal,
                textContentColor = ColorTextoSecundario,
                title = {
                    Text(
                        text = "¿Estás seguro?",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF43F5E)
                    )
                },
                text = {
                    Text(
                        text = "Al confirmar, la ruta quedará cerrada definitivamente y ya no podrás agregar más puntos a este recorrido.\n\n¿Deseas cerrar la ruta con este punto final?",
                        color = ColorTextoSecundario,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmFinDialog2 = false
                            onSave(currentText.trim(), currentState)
                            onSetAsFinRuta()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF43F5E))
                    ) {
                        Text("Sí, estoy seguro", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmFinDialog2 = false }) {
                        Text("Cancelar", color = ColorTextoSecundario)
                    }
                }
            )
        }
    }
}

/**
 * DIÁLOGO: Sonidos del senderista (Beeps de comunicación y emergencia).
 * 
 * Este diálogo permite al senderista tocar distintos tonos acústicos (pitidos/silbatos)
 * para comunicarse en la montaña o emitir alertas de rescate.
 *
 * @param onPlayShort Callback para reproducir 1 pitido corto (Pedir Ubicación).
 * @param onPlayLong Callback para reproducir 1 pitido largo (Todo bien).
 * @param onPlayTwoShort Callback para reproducir 2 pitidos cortos (Ven a mí).
 * @param onPlayEmergencyHelp Callback para reproducir la señal de auxilio (5 pitidos largos).
 * @param onPlayEmergencyAlert Callback para reproducir la señal de emergencia (10 pitidos cortos).
 * @param onPlaySos Callback para reproducir la señal de S.O.S. en código Morse internacional.
 * @param onDismiss Callback para cerrar este diálogo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderistaSoundsDialog(
    onPlayShort: () -> Unit,
    onPlayLong: () -> Unit,
    onPlayTwoShort: () -> Unit,
    onPlayEmergencyHelp: () -> Unit,
    onPlayEmergencyAlert: () -> Unit,
    onPlaySos: () -> Unit,
    onPlayBeacon911: () -> Unit,
    onDismiss: () -> Unit
) {
    // Guarda el texto descriptivo del silbido/alerta que se tocó por última vez.
    // Al ser un estado mutable (mutableStateOf), cuando cambia su valor, Compose
    // vuelve a dibujar automáticamente la interfaz (recomposición) para mostrar la etiqueta.
    var activeDescription by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        // Tarjeta principal (Surface) que contiene todo el diseño del modal
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()) // Permite scroll vertical si la pantalla es pequeña
            ) {
                // Título del Diálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_whistle_bright),
                        contentDescription = "Icono de Silbato",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, Color(0xFFFF9800), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Silbatos y Alertas",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorTextoPrincipal,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sección 1: Sonidos de comunicación rutinaria
                Text("Comunicación Senderista:", fontSize = 12.sp, color = ColorTekinsAccent, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(6.dp))

                // 1. Botón: Pedir Ubicación (1 pitido corto)
                OutlinedButton(
                    onClick = {
                        // Asignamos la explicación al label y ejecutamos el sonido
                        activeDescription = "Pedir Ubicación: 1 pitido corto (indica o solicita ubicación)"
                        onPlayShort()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextoPrincipal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = ColorTekinsAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pedir Ubicación")
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. Botón: Todo bien (1 pitido largo)
                OutlinedButton(
                    onClick = {
                        activeDescription = "Todo bien: 1 pitido largo (respuesta afirmativa / todo en orden)"
                        onPlayLong()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextoPrincipal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = ColorTekinsAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Todo bien")
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3. Botón: Ven a mí (2 pitidos cortos)
                OutlinedButton(
                    onClick = {
                        activeDescription = "Ven a mí: 2 pitidos cortos (llamado para aproximarse)"
                        onPlayTwoShort()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextoPrincipal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = ColorTekinsAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ven a mí")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sección 2: Sonidos de alerta y emergencia (colores de advertencia/críticos)
                Text("Señal de Emergencia:", fontSize = 12.sp, color = ColorTekinsAlert, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Botón: Solicito ayuda (5 pitidos largos de auxilio)
                Button(
                    onClick = {
                        activeDescription = "Solicito ayuda: 5 pitidos largos repetitivos de auxilio"
                        onPlayEmergencyHelp()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)), // Color Naranja/Ámbar de advertencia
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Help, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Solicito ayuda", color = Color.White)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 5. Botón: Emergencia (10 pitidos cortos continuos)
                Button(
                    onClick = {
                        activeDescription = "Emergencia: 10 pitidos cortos continuos"
                        onPlayEmergencyAlert()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)), // Color Rojo de peligro crítico
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Emergencia", color = Color.White)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 6. Botón: SOS (Código Morse SOS)
                Button(
                    onClick = {
                        activeDescription = "Señal S O S: Código Morse internacional (... --- ...)"
                        onPlaySos()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)), // Color Rojo Oscuro extremo
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Señal S O S", color = Color.White)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 7. Botón: Lanzar 911 por Beacon
                Button(
                    onClick = {
                        activeDescription = "Lanzar 911 por Beacon: Emitiendo señal de emergencia 911 por Bluetooth"
                        onPlayBeacon911()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)), // Rojo intenso
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lanzar 911 por Beacon", color = Color.White)
                }

                // Tarjeta explicativa dinámica: se muestra únicamente si activeDescription no es null.
                // Es un contenedor donde indicamos para qué sirve el silbido tocado y qué patrón hace.
                if (activeDescription != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ColorTekinsCardMuted,
                        border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Descripción del sonido tocado:",
                                fontSize = 11.sp,
                                color = ColorTekinsAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = activeDescription ?: "",
                                fontSize = 13.sp,
                                color = ColorTextoPrincipal,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = LocalContext.current
                    var isFlashlightOn by remember { mutableStateOf(false) }

                    Button(
                        onClick = {
                            try {
                                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                                val cameraId = cameraManager.cameraIdList.firstOrNull()
                                if (cameraId != null) {
                                    cameraManager.setTorchMode(cameraId, !isFlashlightOn)
                                    isFlashlightOn = !isFlashlightOn
                                } else {
                                    Toast.makeText(context, "No se encontró cámara con linterna", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al usar linterna", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFlashlightOn) Color.Red else Color(0xFFFFEB3B)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isFlashlightOn) Icons.Default.FlashOff else Icons.Default.FlashOn,
                            contentDescription = "Linterna",
                            tint = if (isFlashlightOn) Color.White else Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isFlashlightOn) "Apagar!!!" else "Linterna",
                            color = if (isFlashlightOn) Color.White else Color.Black,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    TextButton(
                        onClick = onDismiss
                    ) {
                        Text("Cerrar", color = ColorTextoSecundario)
                    }
                }
            }
        }
    }
}

/**
 * DIÁLOGO: Configuración
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    initialDistance: Int,
    initialShowInactive: Boolean,
    initialPreferredMap: String,
    initialEnableBeeps: Boolean,
    initialArrivalTolerance: Int,
    initialTapDistanceLimit: Int,
    initialAppRole: String,
    initialRangoDiasSalidas: Int,
    initialRangoKmSalidas: Int,
    initialEmergencyTimeoutMinutes: Int,
    onEditSenderistaClick: () -> Unit,
    onExportDbClick: () -> Unit,
    onSave: (Int, Boolean, String, Boolean, Int, Int, String, Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var distance by remember { mutableStateOf(initialDistance.toString()) }
    var enableBeeps by remember { mutableStateOf(initialEnableBeeps) }
    var arrivalTolerance by remember { mutableStateOf(initialArrivalTolerance.toString()) }
    var tapDistanceLimit by remember { mutableStateOf(initialTapDistanceLimit.toString()) }
    var emergencyTimeoutMinutes by remember { mutableStateOf(initialEmergencyTimeoutMinutes.toString()) }
    var appRole by remember { mutableStateOf(initialAppRole) }
    var showRoleMenu by remember { mutableStateOf(false) }
    var rangoDiasSalidas by remember { mutableStateOf(initialRangoDiasSalidas.toString()) }
    var rangoKmSalidas by remember { mutableStateOf(initialRangoKmSalidas.toString()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
            modifier = Modifier.padding(16.dp).fillMaxWidth(0.95f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Ajustes de Senderismo",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextoPrincipal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "(Todas las medidas en metros)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorTextoSecundario,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Fila 1: Aviso cercanía y Aceptación punto
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aviso Cerc.", color = ColorTextoPrincipal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Metros para alertar", color = ColorTextoSecundario, fontSize = 9.sp, modifier = Modifier.padding(bottom = 2.dp))
                        TextField(
                            value = distance,
                            onValueChange = { distance = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = ColorTextoPrincipal, fontSize = 13.sp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = ColorTekinsAccent,
                                unfocusedIndicatorColor = ColorTextoSecundario
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Acepta Pto.", color = ColorTextoPrincipal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Radio para marcar (m)", color = ColorTextoSecundario, fontSize = 9.sp, modifier = Modifier.padding(bottom = 2.dp))
                        TextField(
                            value = arrivalTolerance,
                            onValueChange = { arrivalTolerance = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = ColorTextoPrincipal, fontSize = 13.sp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = ColorTekinsAccent,
                                unfocusedIndicatorColor = ColorTextoSecundario
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fila 2: Tap manual y Timeout 911
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tap Manual", color = ColorTextoPrincipal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Límite registro (m)", color = ColorTextoSecundario, fontSize = 9.sp, modifier = Modifier.padding(bottom = 2.dp))
                        TextField(
                            value = tapDistanceLimit,
                            onValueChange = { tapDistanceLimit = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = ColorTextoPrincipal, fontSize = 13.sp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = ColorTekinsAccent,
                                unfocusedIndicatorColor = ColorTextoSecundario
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Espera 911", color = ColorTextoPrincipal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Reactivar SOS (min)", color = ColorTextoSecundario, fontSize = 9.sp, modifier = Modifier.padding(bottom = 2.dp))
                        TextField(
                            value = emergencyTimeoutMinutes,
                            onValueChange = { emergencyTimeoutMinutes = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = ColorTextoPrincipal, fontSize = 13.sp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = ColorTekinsAccent,
                                unfocusedIndicatorColor = ColorTextoSecundario
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = ColorTekinsCardMuted, modifier = Modifier.padding(vertical = 4.dp))
                Text("Búsquedas Senderos", color = ColorTekinsAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                // Fila 3: Rango búsqueda y Rango cercanía
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reciente (d)", color = ColorTextoPrincipal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Días atrás a buscar", color = ColorTextoSecundario, fontSize = 9.sp, modifier = Modifier.padding(bottom = 2.dp))
                        TextField(
                            value = rangoDiasSalidas,
                            onValueChange = { rangoDiasSalidas = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = ColorTextoPrincipal, fontSize = 13.sp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = ColorTekinsAccent,
                                unfocusedIndicatorColor = ColorTextoSecundario
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cerca (km)", color = ColorTextoPrincipal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Radio de búsqueda", color = ColorTextoSecundario, fontSize = 9.sp, modifier = Modifier.padding(bottom = 2.dp))
                        TextField(
                            value = rangoKmSalidas,
                            onValueChange = { rangoKmSalidas = it.filter { char -> char.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = ColorTextoPrincipal, fontSize = 13.sp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                focusedIndicatorColor = ColorTekinsAccent,
                                unfocusedIndicatorColor = ColorTextoSecundario
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Activar Pitidos Doppler (Fila Completa)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Activar Pitidos Doppler", color = ColorTextoPrincipal, fontSize = 14.sp)
                    Switch(
                        checked = enableBeeps,
                        onCheckedChange = { enableBeeps = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorTekinsAccent,
                            checkedTrackColor = ColorTekinsPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = ColorTextoSecundario)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val distInt = distance.toIntOrNull() ?: 50
                            val toleranceInt = arrivalTolerance.toIntOrNull() ?: 25
                            val tapLimitInt = tapDistanceLimit.toIntOrNull() ?: 15
                            val diasInt = rangoDiasSalidas.toIntOrNull() ?: 20
                            val kmInt = rangoKmSalidas.toIntOrNull() ?: 30
                            val emergencyTimeoutInt = emergencyTimeoutMinutes.toIntOrNull() ?: 5
                            onSave(distInt, initialShowInactive, initialPreferredMap, enableBeeps, toleranceInt, tapLimitInt, appRole, diasInt, kmInt, emergencyTimeoutInt)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary)
                    ) {
                        Text("Guardar", color = Color.White)
                    }
                }
            }
        }
    }
}




// =================================================================================
// ESTRUCTURA DE SOPORTE PARA EL MAPA (Equivalente a un "Type" en B4A o "Record" en Delphi)
// Guarda el estado del mapa para saber si ya cargó y qué datos tiene asignados.
// =================================================================================
private class MapStateHolder {
    var isLoaded: Boolean = false                  // ¿El HTML del mapa terminó de cargarse en el WebView?
    var points: List<PuntoRutaEntity> = emptyList() // Lista de coordenadas de la ruta
    var tracePoints: List<TrazaRutaEntity> = emptyList() // Lista de puntos de seguimiento continuo
    var currentLocation: Location? = null          // Ubicación GPS actual del usuario
}

// Limpia caracteres conflictivos del texto para no romper el motor de JavaScript
// (Evita inyecciones de código accidentales o errores al interpretar comillas)
private fun cleanJsString(str: String): String {
    return str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\'", "\\\'")
        .replace("\r", "")
        .replace("\n", " ")
}

// =================================================================================
/**
 * Aplica el algoritmo de suavizado Catmull-Rom Splines a la traza de coordenadas.
 * Genera puntos intermedios para lograr un renderizado curvo en el mapa Leaflet.
 */
private fun smoothTracePoints(points: List<TrazaRutaEntity>, steps: Int = 6): List<TrazaRutaEntity> {
    if (points.size < 3) return points
    
    val smoothed = mutableListOf<TrazaRutaEntity>()
    val n = points.size
    
    // Extrapolar puntos extremos para el algoritmo (duplicando inicio y fin)
    val p = ArrayList<TrazaRutaEntity>(n + 2)
    p.add(points[0])
    for (pt in points) {
        p.add(pt)
    }
    p.add(points[n - 1])
    
    var localId = 0
    val idRuta = points[0].idRuta

    // Catmull-Rom Spline
    for (i in 1 until n) {
        val p0 = p[i - 1]
        val p1 = p[i]
        val p2 = p[i + 1]
        val p3 = p[i + 2]
        
        for (j in 0 until steps) {
            val t = j.toDouble() / steps.toDouble()
            val t2 = t * t
            val t3 = t2 * t
            
            val lat = 0.5 * (
                (2.0 * p1.latitud) +
                (-p0.latitud + p2.latitud) * t +
                (2.0 * p0.latitud - 5.0 * p1.latitud + 4.0 * p2.latitud - p3.latitud) * t2 +
                (-p0.latitud + 3.0 * p1.latitud - 3.0 * p2.latitud + p3.latitud) * t3
            )
            
            val lng = 0.5 * (
                (2.0 * p1.longitud) +
                (-p0.longitud + p2.longitud) * t +
                (2.0 * p0.longitud - 5.0 * p1.longitud + 4.0 * p2.longitud - p3.longitud) * t2 +
                (-p0.longitud + 3.0 * p1.longitud - 3.0 * p2.longitud + p3.longitud) * t3
            )

            val alt = 0.5 * (
                (2.0 * p1.altitud) +
                (-p0.altitud + p2.altitud) * t +
                (2.0 * p0.altitud - 5.0 * p1.altitud + 4.0 * p2.altitud - p3.altitud) * t2 +
                (-p0.altitud + 3.0 * p1.altitud - 3.0 * p2.altitud + p3.altitud) * t3
            )
            
            smoothed.add(
                TrazaRutaEntity(
                    id = ++localId,
                    idRuta = idRuta,
                    orden = localId,
                    latitud = lat,
                    longitud = lng,
                    altitud = alt
                )
            )
        }
    }
    // Añadir el último punto exacto
    smoothed.add(
        TrazaRutaEntity(
            id = ++localId,
            idRuta = idRuta,
            orden = localId,
            latitud = points[n - 1].latitud,
            longitud = points[n - 1].longitud,
            altitud = points[n - 1].altitud
        )
    )
    
    return smoothed
}

// FUNCIÓN PARA ENVIAR DATOS AL MAPA (Equivalente a llamar a una función JS desde B4A/Delphi)
// Inyecta dinámicamente un comando JavaScript en el WebView que ejecuta la función JS "updateData(...)".
// =================================================================================
private fun updateWebViewData(
    webView: WebView,
    points: List<PuntoRutaEntity>,
    tracePoints: List<TrazaRutaEntity>,
    currentLocation: Location?,
    resetView: Boolean,
    routeStartLat: Double? = null,
    routeStartLng: Double? = null,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    android.util.Log.d("GoogleMapView", "updateWebViewData called: points size = ${points.size}, trace size = ${tracePoints.size}, resetView = $resetView, location = $currentLocation")
    
    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
    // Convertimos la lista de puntos de Kotlin a formato JSON para JavaScript, calculando la distancia del tramo
    var accumDistance = 0f
    val pointsJson = points.mapIndexed { idx, pt ->
        val safeDesc = cleanJsString(pt.descripcion)
        val (segDist, accumStr) = if (idx == 0) {
            Pair("Inicio", "0 m")
        } else {
            val prevPt = points[idx - 1]
            val loc1 = Location("").apply { latitude = prevPt.latitud; longitude = prevPt.longitud }
            val loc2 = Location("").apply { latitude = pt.latitud; longitude = pt.longitud }
            val distMeters = loc1.distanceTo(loc2)
            accumDistance += distMeters
            val formattedSeg = if (distMeters >= 1000f) String.format(java.util.Locale.US, "%.1f km", distMeters / 1000f) else "${distMeters.toInt()} m"
            val formattedAccum = if (accumDistance >= 1000f) String.format(java.util.Locale.US, "%.1f km", accumDistance / 1000f) else "${accumDistance.toInt()} m"
            Pair(formattedSeg, formattedAccum)
        }
        """{"lat": ${pt.latitud}, "lng": ${pt.longitud}, "desc": "${idx + 1}. $safeDesc", "segDist": "$segDist", "accumDist": "$accumStr", "estado": ${pt.estado}, "esFinRuta": ${pt.esFinRuta}, "altitud": ${pt.altitud.toInt()}}"""
    }.joinToString(",")
    val pointsJsonStr = "[$pointsJson]"

    // Suavizar y serializar la traza de ruta
    val smoothedTrace = smoothTracePoints(tracePoints)
    val traceJson = smoothedTrace.map { pt ->
        """{"lat": ${pt.latitud}, "lng": ${pt.longitud}}"""
    }.joinToString(",")
    val traceJsonStr = "[$traceJson]"

    // Convertimos la ubicación del usuario a formato JSON
    val userLocJsonStr = if (currentLocation != null) {
        """{"lat": ${currentLocation.latitude}, "lng": ${currentLocation.longitude}, "desc": "Mi ubicación actual"}"""
    } else {
        "null"
    }

    val routeStartJsonStr = if (routeStartLat != null && routeStartLng != null) {
        """{"lat": $routeStartLat, "lng": $routeStartLng}"""
    } else {
        "null"
    }

    // Armamos la llamada a la función JS definida en nuestro HTML/Leaflet
    val js = "if (typeof updateData === 'function') { updateData($pointsJsonStr, $traceJsonStr, $userLocJsonStr, $resetView, $routeStartJsonStr); }"
    
    // Ejecutamos el JavaScript en el WebView en el hilo principal
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        webView.evaluateJavascript(js, null)
    }
    }
}

/**
 * COMPONENTE: GoogleMapView nativo simulado usando OpenStreetMap y CartoDB en un WebView optimizado.
 * Dibuja los marcadores de los puntos con índices numéricos y una línea poligonal que los une.
 * Es 100% gratuito, no requiere clave de API y funciona de forma inmediata.
 * 
 * CONCEPTOS PARA PROGRAMADORES DE B4A Y DELPHI MONKEY:
 * 
 * 1. Declarativo (Compose) vs. Imperativo:
 *    En B4A/Delphi diseñas la pantalla y arrastras componentes. En Compose (Kotlin moderno), la interfaz
 *    se dibuja dinámicamente según el estado de las variables. Si una variable cambia, Compose "recompone"
 *    (redibuja) la pantalla automáticamente.
 * 
 * 2. remember:
 *    Equivale a declarar una variable global en la clase de tu Formulario o Activity. Evita que la variable
 *    se destruya y se vuelva a inicializar cada vez que la pantalla se redibuja (recomposición).
 * 
 * 3. mutableStateOf:
 *    Es una propiedad reactiva. Al cambiar su valor, avisa a Compose que debe redibujar la pantalla.
 *    Es similar al Setter de una propiedad en Delphi que llama internamente a "Invalidate".
 * 
 * 4. AndroidView:
 *    Es un puente o contenedor que nos permite incrustar un control clásico de Android (en este caso, un
 *    WebView) dentro del lienzo moderno de Jetpack Compose (similar a incrustar un componente FMX nativo).
 */
@Composable
fun GoogleMapView(
    points: List<PuntoRutaEntity>,
    tracePoints: List<TrazaRutaEntity>,
    currentLocation: Location?,
    onPointClick: (PuntoRutaEntity) -> Unit,
    modifier: Modifier = Modifier,
    routeStartLat: Double? = null,
    routeStartLng: Double? = null,
    onPointHover: ((PuntoRutaEntity?) -> Unit)? = null,
    activePointId: Int? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Stable JS interface that holds latest references to avoid re-adding
    val webInterface = remember {
        object {
            var currentPoints = points
            var currentOnPointClick = onPointClick
            var currentOnPointHover = onPointHover

            @android.webkit.JavascriptInterface
            fun onMarkerClick(index: Int) {
                coroutineScope.launch(Dispatchers.Main) {
                    if (index in currentPoints.indices) {
                        currentOnPointClick(currentPoints[index])
                    }
                }
            }

            @android.webkit.JavascriptInterface
            fun onMarkerHover(index: Int) {
                coroutineScope.launch(Dispatchers.Main) {
                    if (index in currentPoints.indices) {
                        currentOnPointHover?.invoke(currentPoints[index])
                    }
                }
            }

            @android.webkit.JavascriptInterface
            fun onMarkerHoverEnd() {
                coroutineScope.launch(Dispatchers.Main) {
                    currentOnPointHover?.invoke(null)
                }
            }

            @android.webkit.JavascriptInterface
            fun onMapClick() {
                coroutineScope.launch(Dispatchers.Main) {
                    currentOnPointHover?.invoke(null)
                }
            }
        }
    }

    // Keep interface updated with fresh parameters on recompositions
    webInterface.currentPoints = points
    webInterface.currentOnPointClick = onPointClick
    webInterface.currentOnPointHover = onPointHover
    
    // Identificamos el ID o coordenada de la ruta actual para saber cuándo cambia de ruta el usuario
    val currentRouteKey = if (routeStartLat != null && routeStartLng != null) {
        "$routeStartLat,$routeStartLng"
    } else {
        points.firstOrNull()?.idRuta ?: ""
    }
    
    // lastRouteKey retiene la clave de la ruta anterior (como una variable miembro en Delphi o B4A)
    var lastRouteKey by remember { mutableStateOf("") }
    
    // Si la ruta cambió, marcamos "routeChanged = true" para re-centrar el mapa automáticamente
    val routeChanged = currentRouteKey != lastRouteKey
    if (routeChanged && currentRouteKey.isNotEmpty()) {
        lastRouteKey = currentRouteKey
    }

    // Cargar Leaflet JS y CSS desde los assets (DirAssets en B4A) como texto para inyectarlos de forma offline
    val leafletJs = remember {
        try {
            context.assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("GoogleMapView", "Error reading leaflet.js asset", e)
            ""
        }
    }
    val leafletCss = remember {
        try {
            context.assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("GoogleMapView", "Error reading leaflet.css asset", e)
            ""
        }
    }

    // Puente para incrustar y sincronizar el WebView clásico en Compose
    AndroidView(
        // FACTORY: Se ejecuta una sola vez para CREAR el control WebView (Como el FormCreate de Delphi o el Activity_Create de B4A)
        factory = { ctx ->
            WebView(ctx).apply {
                // Configurar dimensiones MATCH_PARENT para evitar colapso de tamaño
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )

                // Inicializar holder inmediatamente en la etiqueta para evitar condiciones de carrera
                tag = MapStateHolder().apply {
                    this.points = points
                    this.tracePoints = tracePoints
                    this.currentLocation = currentLocation
                }
                
                // Configuración y permisos del WebView para permitir Javascript y archivos
                settings.javaScriptEnabled = true
                addJavascriptInterface(webInterface, "AndroidApp")
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                // CRÍTICO: Activamos la caché agresiva del WebView.
                // Si hay internet, descargará los mapas; si estás en la montaña sin señal,
                // reutilizará automáticamente las imágenes guardadas en caché (equivalente a CacheMode en B4A).
                settings.cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
                
                // CRÍTICO: Establecer User-Agent personalizado para evitar bloqueos HTTP 403 de OpenStreetMap
                settings.userAgentString = "RutasSenderoApp/1.0 (com.gaspar.rutassendero)"
                
                // Forzar limpieza de caché temporalmente para eliminar los "Access blocked" cacheados
                // clearCache(true) // COMENTADO: Evita perder el caché offline y causar pantalla negra al cambiar entre Lista/Mapa
                
                // Manejador de eventos de carga de página (Como el WebView_PageFinished en B4A)
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d("GoogleMapView", "onPageFinished: Leaflet WebView loaded.")
                        val holder = view?.tag as? MapStateHolder
                        if (holder != null) {
                            holder.isLoaded = true
                            Log.d("GoogleMapView", "onPageFinished: MapStateHolder found. Triggering initial updateWebViewData.")
                            updateWebViewData(
                                webView = view,
                                points = holder.points,
                                tracePoints = holder.tracePoints,
                                currentLocation = holder.currentLocation,
                                resetView = true,
                                routeStartLat = routeStartLat,
                                routeStartLng = routeStartLng,
                                coroutineScope = coroutineScope
                            )
                        } else {
                            Log.e("GoogleMapView", "onPageFinished: MapStateHolder tag is null!")
                        }
                    }
                }
                
                // Captura los mensajes de consola de JavaScript y los redirige al Logcat de Android Studio
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            Log.d("OSM_MAP_JS", "${it.message()} -- From line ${it.lineNumber()} of ${it.sourceId()}")
                        }
                        return true
                    }
                }

                // Generar HTML inicial con Leaflet y CartoDB Voyager tiles
                val initialLat = routeStartLat
                    ?: points.firstOrNull()?.latitud 
                    ?: tracePoints.firstOrNull()?.latitud 
                    ?: currentLocation?.latitude 
                    ?: 0.0
                val initialLng = routeStartLng
                    ?: points.firstOrNull()?.longitud 
                    ?: tracePoints.firstOrNull()?.longitud 
                    ?: currentLocation?.longitude 
                    ?: 0.0
                
                val htmlHead = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                        <style>
                            $leafletCss
                            body, html, #map { height: 100vh; width: 100vw; margin: 0; padding: 0; background: #0F172A; }
                            .leaflet-container { background: #0F172A; }
                            .leaflet-div-icon { background: transparent !important; border: none !important; }
                            
                            /* Estilo del pin de ruta personalizado según estado */
                            .route-marker-pin {
                                width: 28px;
                                height: 28px;
                                border-radius: 50%;
                                box-shadow: 0 2px 8px rgba(0,0,0,0.55);
                                display: flex;
                                align-items: center;
                                justify-content: center;
                                position: relative;
                                cursor: pointer;
                                transition: transform 0.15s ease-in-out;
                            }
                            .route-marker-pin:hover {
                                transform: scale(1.18);
                                z-index: 1000 !important;
                            }
                            .route-marker-pin svg {
                                display: block;
                            }
                            .route-pin-badge {
                                position: absolute;
                                top: -6px;
                                right: -6px;
                                min-width: 14px;
                                height: 14px;
                                padding: 0 3px;
                                background: #0F172A;
                                color: #FFFFFF;
                                border: 1.5px solid #CBD5E1;
                                border-radius: 7px;
                                font-size: 8.5px;
                                font-weight: bold;
                                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                                display: flex;
                                align-items: center;
                                justify-content: center;
                                box-shadow: 0 1px 3px rgba(0,0,0,0.6);
                            }
                            
                            /* Estilo del tooltip HelpHint no permanente */
                            .route-tooltip-hint {
                                background-color: rgba(15, 23, 42, 0.95) !important;
                                border: 1.5px solid #2DD4BF !important;
                                color: #F8FAFC !important;
                                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif !important;
                                font-size: 11px !important;
                                padding: 4px 8px !important;
                                border-radius: 6px !important;
                                box-shadow: 0 4px 12px rgba(0,0,0,0.5) !important;
                                white-space: nowrap !important;
                                pointer-events: none;
                            }
                            .route-tooltip-hint b {
                                color: #2DD4BF;
                                font-size: 11.5px;
                            }
                            .route-tooltip-sub {
                                font-size: 9.5px;
                                color: #94A3B8;
                                margin-top: 1px;
                            }
                            
                            /* Quitar flecha de Leaflet para simplificar */
                            .leaflet-tooltip-top:before {
                                border-top-color: transparent !important;
                            }
                            
                            /* Estilo del pin de ubicación actual pulsante */
                            .user-marker-pin {
                                width: 18px;
                                height: 18px;
                                background-color: #3B82F6;
                                border: 2.5px solid white;
                                border-radius: 50%;
                                box-shadow: 0 0 10px rgba(59, 130, 246, 0.9);
                                position: relative;
                            }
                            .user-marker-pin::after {
                                content: '';
                                position: absolute;
                                top: -6px;
                                left: -6px;
                                width: 26px;
                                height: 26px;
                                border: 2.5px solid #3B82F6;
                                border-radius: 50%;
                                animation: pulsate 1.8s infinite ease-out;
                                opacity: 0;
                            }
                            @keyframes pulsate {
                                0% { transform: scale(0.5); opacity: 0; }
                                50% { opacity: 0.5; }
                                100% { transform: scale(1.5); opacity: 0; }
                            }
                        </style>
                        <script>$leafletJs</script>
                    </head>
                    <body>
                        <div id="map"></div>
                """.trimIndent()
                
                val bodyScript = """
                    <script>
                        var map = null;
                        var markersGroup = null;
                        var polyline = null;
                        var userMarker = null;
                        var hasSetInitialBounds = false;
                        var pendingData = null;
                        var pinnedMarker = null;
                        var hoverTimeout = null;

                        window.clearPinnedMarker = function() {
                            if (hoverTimeout) {
                                clearTimeout(hoverTimeout);
                                hoverTimeout = null;
                            }
                            if (pinnedMarker) {
                                pinnedMarker.closeTooltip();
                                pinnedMarker = null;
                            }
                        };

                        function getMarkerVisuals(pt, idx) {
                            var state = pt.estado || 1;
                            var isEnd = pt.esFinRuta;
                            var bg = '#1976D2';
                            var border = '#90CAF9';
                            var iconSvg = '<svg viewBox="0 0 24 24" width="14" height="14" fill="white"><circle cx="12" cy="12" r="5"/></svg>';
                            var stateName = 'Punto normal';

                            if (idx === 0) {
                                bg = '#0F766E';
                                border = '#2DD4BF';
                                iconSvg = '<svg viewBox="0 0 24 24" width="14" height="14" fill="white"><path d="M8 5v14l11-7z"/></svg>';
                                stateName = 'Inicio';
                            } else if (isEnd) {
                                bg = '#D32F2F';
                                border = '#FFCDD2';
                                iconSvg = '<svg viewBox="0 0 24 24" width="14" height="14" fill="white"><path d="M14.4 6L14 4H5v17h2v-7h5.6l.4 2h7V6z"/></svg>';
                                stateName = 'Fin de ruta';
                            } else {
                                switch(state) {
                                    case 2: // Sitio de interés
                                        bg = '#2E7D32';
                                        border = '#A5D6A7';
                                        iconSvg = '<svg viewBox="0 0 24 24" width="15" height="15" fill="white"><path d="M12 9a3 3 0 100 6 3 3 0 000-6zm-1.8-6l-1.3 2H5a2 2 0 00-2 2v11a2 2 0 002 2h14a2 2 0 002-2V7a2 2 0 00-2-2h-3.9l-1.3-2h-3.6z"/></svg>';
                                        stateName = 'Sitio de interés';
                                        break;
                                    case 3: // Bifurcación
                                        bg = '#5E35B1';
                                        border = '#D1C4E9';
                                        iconSvg = '<svg viewBox="0 0 24 24" width="15" height="15" fill="white"><path d="M7 5a2 2 0 100 4 2 2 0 000-4zm10 0a2 2 0 100 4 2 2 0 000-4zM7 15a2 2 0 100 4 2 2 0 000-4zm1 2v-4a3 3 0 013-3h3.2l-1.6 1.6 1.4 1.4 4-4-4-4-1.4 1.4 1.6 1.6H11a5 5 0 00-5 5v4h2z"/></svg>';
                                        stateName = 'Bifurcación';
                                        break;
                                    case 4: // Suelo flojo
                                        bg = '#8D6E63';
                                        border = '#D7CCC8';
                                        iconSvg = '<svg viewBox="0 0 24 24" width="15" height="15" fill="white"><path d="M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z"/></svg>';
                                        stateName = 'Suelo flojo';
                                        break;
                                    case 5: // Cuidado
                                        bg = '#F59E0B';
                                        border = '#FEF3C7';
                                        iconSvg = '<svg viewBox="0 0 24 24" width="15" height="15" fill="white"><path d="M12 2L1 21h22L12 2zm0 4.2l8.5 14.8H3.5L12 6.2zM11 11h2v4h-2zm0 6h2v2h-2z"/></svg>';
                                        stateName = 'Cuidado';
                                        break;
                                    case 6: // Alerta / Peligro
                                        bg = '#D32F2F';
                                        border = '#FFCDD2';
                                        iconSvg = '<svg viewBox="0 0 24 24" width="15" height="15" fill="white"><path d="M12 2C8.1 2 5 5.1 5 9c0 2.4 1.2 4.5 3 5.7V17c0 .6.4 1 1 1h1v2h4v-2h1c.6 0 1-.4 1-1v-2.3c1.8-1.2 3-3.3 3-5.7 0-3.9-3.1-7-7-7zm-3 8a1.5 1.5 0 110-3 1.5 1.5 0 010 3zm6 0a1.5 1.5 0 110-3 1.5 1.5 0 010 3z"/></svg>';
                                        stateName = 'Alerta';
                                        break;
                                    case 8: // Vehículo
                                        bg = '#607D8B';
                                        border = '#CFD8DC';
                                        iconSvg = '<svg viewBox="0 0 24 24" width="15" height="15" fill="white"><path d="M18.9 5c-.2-.6-.8-1-1.4-1h-11c-.7 0-1.2.4-1.4 1L3 11v8c0 .6.4 1 1 1h1c.6 0 1-.4 1-1v-1h12v1c0 .6.4 1 1 1h1c.6 0 1-.4 1-1v-8l-2.1-6zM6.5 15a1.5 1.5 0 110-3 1.5 1.5 0 010 3zm11 0a1.5 1.5 0 110-3 1.5 1.5 0 010 3zM5 10l1.5-4.5h11L19 10H5z"/></svg>';
                                        stateName = 'Vehículo';
                                        break;
                                    case 9: // Refugio
                                        bg = '#4E342E';
                                        border = '#D7CCC8';
                                        iconSvg = '<svg viewBox="0 0 24 24" width="15" height="15" fill="white"><path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"/></svg>';
                                        stateName = 'Refugio';
                                        break;
                                    case 10: // Comida
                                        bg = '#E65100';
                                        border = '#FFE0B2';
                                        iconSvg = '<svg viewBox="0 0 24 24" width="15" height="15" fill="white"><path d="M11 9H9V2H7v7H5V2H3v7c0 2.1 1.7 3.8 3.8 4v7h2.4v-7c2.1-.2 3.8-1.9 3.8-4V2h-2v7zm5-4v5h3V2h-3v3zm0 7v9h3v-9c2.2 0 4-1.8 4-4V2h-2v3h-2V2h-2v3h-1c-1.1 0-2 .9-2 2v2c0 1.7 1.3 3 3 3z"/></svg>';
                                        stateName = 'Comida';
                                        break;
                                    default:
                                        bg = '#1976D2';
                                        border = '#90CAF9';
                                        iconSvg = '<svg viewBox="0 0 24 24" width="14" height="14" fill="white"><circle cx="12" cy="12" r="5"/></svg>';
                                        stateName = 'Punto normal';
                                        break;
                                }
                            }
                            return { bg: bg, border: border, iconSvg: iconSvg, stateName: stateName };
                        }
                        
                        function initMap() {
                            console.log("Leaflet script loaded. Initializing map.");
                            map = L.map('map', { zoomControl: false }).setView([$initialLat, $initialLng], 15);
                            
                            // Añadir control de zoom abajo a la derecha para no obstruir el header
                            L.control.zoom({ position: 'bottomright' }).addTo(map);
                            
                            // --- OPCIONES DE MAPAS DISPONIBLES ---
                            // Instrucciones: Para probar otra opción, comenta la línea de L.tileLayer activa (ponle // al inicio)
                            // y quítale los // a la opción que quieras probar.

                            // OPCIÓN 1: Esri World Topo Map (Activa actualmente) - Excelente para senderismo y topografía
                            L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}', {
                                maxZoom: 19,
                                attribution: '&copy; Esri'
                            }).addTo(map);

                            // OPCIÓN 2: OpenTopoMap - Especializado en relieve y curvas de nivel (puede ser un poco lento al cargar)
                            // L.tileLayer('https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png', {
                            //     maxZoom: 17,
                            //     attribution: '&copy; OpenTopoMap'
                            // }).addTo(map);

                            // OPCIÓN 3: Esri World Imagery - Mapa Satelital de Alta Resolución
                            // L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                            //     maxZoom: 19,
                            //     attribution: '&copy; Esri Satélite'
                            // }).addTo(map);

                            // OPCIÓN 4: OpenStreetMap Estándar - Mapa clásico
                            // L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png?cb=2', {
                            //     maxZoom: 19,
                            //     attribution: '&copy; OpenStreetMap'
                            // }).addTo(map);
                            // -------------------------------------
                            
                            markersGroup = L.layerGroup().addTo(map);

                            map.on('click', function(e) {
                                if (e && e.originalEvent && e.originalEvent._markerClickHandled) {
                                    return;
                                }
                                window.clearPinnedMarker();
                                if (window.AndroidApp && typeof window.AndroidApp.onMapClick === 'function') {
                                    window.AndroidApp.onMapClick();
                                }
                            });
                            
                            // invalidar tamaño para asegurar renderizado correcto tras renderizar contenedor
                            if (pendingData) {
                                console.log("Applying pending updateData.");
                                updateData(pendingData.points, pendingData.trace, pendingData.userLoc, pendingData.resetView, pendingData.routeStart);
                                pendingData = null;
                            }
                        }
                        
                        initMap();
                        
                        var lastPointsSignature = "";

                        function updateData(points, trace, userLoc, resetView, routeStart) {
                            if (!map) {
                                pendingData = { points: points, trace: trace, userLoc: userLoc, resetView: resetView, routeStart: routeStart };
                                return;
                            }
                            
                            console.log("updateData: " + (points ? points.length : 0) + " points, " + (trace ? trace.length : 0) + " trace points");
                            if (resetView) {
                                hasSetInitialBounds = false;
                            }
                            
                            var currentPointsSig = points ? points.length + "_" + (points[0] ? points[0].lat + "_" + points[0].lng : "") : "";
                            var pointsChanged = (currentPointsSig !== lastPointsSignature) || resetView;
                            var poiLatlngs = [];
                            
                            if (pointsChanged) {
                                lastPointsSignature = currentPointsSig;
                                markersGroup.clearLayers();
                                
                                if (points && points.length > 0) {
                                    points.forEach(function(pt, idx) {
                                        var visuals = getMarkerVisuals(pt, idx);
                                        var pinHtml = '<div class="route-marker-pin" style="background-color:' + visuals.bg + '; border: 2.5px solid ' + visuals.border + ';">' +
                                                      visuals.iconSvg +
                                                      '<span class="route-pin-badge">' + (idx + 1) + '</span>' +
                                                      '</div>';

                                        var routeIcon = L.divIcon({
                                            className: 'custom-route-icon-container',
                                            html: pinHtml,
                                            iconSize: [28, 28],
                                            iconAnchor: [14, 14]
                                        });
                                        
                                        var marker = L.marker([pt.lat, pt.lng], { icon: routeIcon });
                                        markersGroup.addLayer(marker);
                                        
                                        var hintContent = "<b>" + pt.desc + "</b>";
                                        hintContent += "<div class='route-tooltip-sub'><span style='color:" + visuals.border + ";'>● " + visuals.stateName + "</span>";
                                        if (idx > 0 && pt.segDist) {
                                            hintContent += " · Tramo: " + pt.segDist + " (va " + pt.accumDist + ")";
                                        }
                                        if (pt.altitud) {
                                            hintContent += " · " + pt.altitud + "m";
                                        }
                                        hintContent += "</div>";

                                        marker.bindTooltip(hintContent, { 
                                            permanent: false, 
                                            direction: 'top', 
                                            className: 'route-tooltip-hint',
                                            offset: [0, -16],
                                            opacity: 0.98
                                        });

                                        // Desactivar el auto-close y auto-open predeterminado de Leaflet para tener el control total
                                        marker.off('mouseover');
                                        marker.off('mouseout');
                                        
                                        // Evento Hover: timeout extendido de 20 segundos al retirar el cursor
                                        marker.on('mouseover', function() {
                                            if (hoverTimeout) {
                                                clearTimeout(hoverTimeout);
                                                hoverTimeout = null;
                                            }
                                            marker.openTooltip();
                                            if (window.AndroidApp && typeof window.AndroidApp.onMarkerHover === 'function') {
                                                window.AndroidApp.onMarkerHover(idx);
                                            }
                                        });
                                        marker.on('mouseout', function() {
                                            // Si el marcador está retenido/fijado por clic, NUNCA se cierra
                                            if (pinnedMarker === marker) {
                                                return;
                                            }
                                            if (hoverTimeout) clearTimeout(hoverTimeout);
                                            hoverTimeout = setTimeout(function() {
                                                if (pinnedMarker !== marker) {
                                                    marker.closeTooltip();
                                                }
                                            }, 20000); // 20 segundos de timeout
                                        });

                                        // Evento Click / Tap: RETENER EN PANTALLA fijado permanentemente
                                        marker.on('click', function(e) {
                                            if (e && e.originalEvent) {
                                                e.originalEvent._markerClickHandled = true;
                                                L.DomEvent.stopPropagation(e.originalEvent);
                                            }
                                            if (hoverTimeout) {
                                                clearTimeout(hoverTimeout);
                                                hoverTimeout = null;
                                            }
                                            if (pinnedMarker && pinnedMarker !== marker) {
                                                pinnedMarker.closeTooltip();
                                            }
                                            pinnedMarker = marker;
                                            marker.openTooltip();
                                            
                                            if (window.AndroidApp && typeof window.AndroidApp.onMarkerClick === 'function') {
                                                window.AndroidApp.onMarkerClick(idx);
                                            }
                                        });
                                        poiLatlngs.push([pt.lat, pt.lng]);
                                    });
                                }
                            } else {
                                if (points && points.length > 0) {
                                    points.forEach(function(pt) {
                                        poiLatlngs.push([pt.lat, pt.lng]);
                                    });
                                }
                            }
                            
                            if (userLoc) {
                                var userIcon = L.divIcon({
                                    className: 'user-marker-pin',
                                    html: '',
                                    iconSize: [18, 18],
                                    iconAnchor: [9, 9]
                                });
                                
                                if (!userMarker) {
                                    userMarker = L.marker([userLoc.lat, userLoc.lng], { icon: userIcon, zIndexOffset: 1000 }).addTo(map);
                                    userMarker.bindPopup("<b>" + userLoc.desc + "</b>");
                                } else {
                                    userMarker.setLatLng([userLoc.lat, userLoc.lng]);
                                    userMarker.setZIndexOffset(1000);
                                }
                            } else {
                                if (userMarker) {
                                    map.removeLayer(userMarker);
                                    userMarker = null;
                                }
                            }
                            
                            if (polyline) {
                                map.removeLayer(polyline);
                                polyline = null;
                            }
                            
                            var traceLatlngs = [];
                            if (trace && trace.length > 0) {
                                trace.forEach(function(pt) {
                                    traceLatlngs.push([pt.lat, pt.lng]);
                                });
                            }
                            
                            if (traceLatlngs.length > 0) {
                                polyline = L.polyline(traceLatlngs, {
                                    color: '#2DD4BF', 
                                    weight: 6,
                                    opacity: 0.9
                                }).addTo(map);
                            } else if (poiLatlngs.length > 0) {
                                // Fallback: si no hay traza aún, unimos los POIs con una línea punteada
                                polyline = L.polyline(poiLatlngs, {
                                    color: '#2DD4BF', 
                                    weight: 6,
                                    opacity: 0.8,
                                    dashArray: '5, 10'
                                }).addTo(map);
                            }
                            
                            // --- Línea dinámica de proximidad ---
                            if (window.dynamicConnectionLine) {
                                map.removeLayer(window.dynamicConnectionLine);
                                window.dynamicConnectionLine = null;
                            }
                            
                            var nearestDist = Infinity;
                            var nearestLatLng = null;
                            var uL = null;
                            
                            if (userLoc && poiLatlngs && poiLatlngs.length > 0) {
                                uL = L.latLng(userLoc.lat, userLoc.lng);
                                poiLatlngs.forEach(function(pt) {
                                    var pL = L.latLng(pt[0], pt[1]);
                                    var d = uL.distanceTo(pL);
                                    if (d < nearestDist) {
                                        nearestDist = d;
                                        nearestLatLng = pL;
                                    }
                                });
                            }
                            
                            if (uL && nearestLatLng && nearestDist !== Infinity) {
                                var color = '#10B981'; // Verde < 30m
                                if (nearestDist > 30 && nearestDist <= 100) color = '#F59E0B'; // Amarillo < 100m
                                if (nearestDist > 100) color = '#EF4444'; // Rojo > 100m
                                
                                window.dynamicConnectionLine = L.polyline([uL, nearestLatLng], {
                                    color: color,
                                    weight: 4,
                                    dashArray: '6, 6',
                                    opacity: 0.9
                                }).addTo(map);
                                
                                // Mostrar la distancia sobre el usuario temporalmente si está algo lejos
                                if (userMarker) {
                                    userMarker.setPopupContent("<b>A " + Math.round(nearestDist) + "m de la ruta</b>");
                                }
                            }

                            if (!hasSetInitialBounds) {
                                var boundsPoints = traceLatlngs.length > 0 ? traceLatlngs.slice() : poiLatlngs.slice();
                                if (boundsPoints.length > 0) {
                                    var bounds = L.latLngBounds(boundsPoints);
                                    map.fitBounds(bounds, { padding: [60, 60] });
                                    hasSetInitialBounds = true;
                                } else if (routeStart) {
                                    map.setView([routeStart.lat, routeStart.lng], 15);
                                    hasSetInitialBounds = true;
                                }
                            }
                            
                            map.invalidateSize();
                        }
                    </script>
                """.trimIndent()
                
                val html = htmlHead + bodyScript + "</body></html>"
                loadDataWithBaseURL("https://app.rutassendero.com", html, "text/html", "UTF-8", null)
            }
        },
        
        // UPDATE: Se ejecuta CADA VEZ que Compose detecta cambios en las variables
        update = { webView ->
            val holder = webView.tag as? MapStateHolder ?: MapStateHolder().also { webView.tag = it }
            val pointsChanged = holder.points != points
            val traceChanged = holder.tracePoints != tracePoints
            val locationChanged = holder.currentLocation != currentLocation
            
            holder.points = points
            holder.tracePoints = tracePoints
            holder.currentLocation = currentLocation
            
            // Si la página del mapa ya terminó de cargarse en el WebView, inyectamos los datos actualizados
            if (holder.isLoaded) {
                if (activePointId == null) {
                    webView.evaluateJavascript("if (typeof window.clearPinnedMarker === 'function') window.clearPinnedMarker();", null)
                }
                if (pointsChanged || traceChanged || locationChanged || routeChanged) {
                    updateWebViewData(
                        webView = webView,
                        points = points,
                        tracePoints = tracePoints,
                        currentLocation = currentLocation,
                        resetView = routeChanged,
                        routeStartLat = routeStartLat,
                        routeStartLng = routeStartLng,
                        coroutineScope = coroutineScope
                    )
                }
            }
        },
        
        // ONRELEASE: Se ejecuta al DESTRUIRSE el componente (Como el FormDestroy de Delphi o el Activity_Pause(UserClosed=True) de B4A)
        // Libera los recursos del motor Chromium para evitar que la aplicación consuma memoria en segundo plano.
        onRelease = { webView ->
            Log.d("GoogleMapView", "onRelease: Destroying WebView to prevent native crashes.")
            webView.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
        }
    )
}

// ==========================================
// RASTREO Y SEGUIMIENTO: MAPA DE SENDERISTAS
// ==========================================

private class TrackingMapStateHolder {
    var isLoaded: Boolean = false
    var participants: List<ParticipanteEntity> = emptyList()
    var activeEmergencies: Set<String> = emptySet()
    var currentLocation: Location? = null
}

fun getInitials(name: String): String {
    if (name.isBlank()) return "?"
    val parts = name.trim().split(Regex("\\s+"))
    val initials = parts.mapNotNull { it.firstOrNull()?.uppercaseChar() }
    return initials.take(3).joinToString("")
}

// Inyección dinámica de datos en el WebView del mapa de seguimiento
private fun updateTrackingWebViewData(
    webView: WebView,
    participants: List<ParticipanteEntity>,
    activeEmergencies: Set<String>,
    currentLocation: Location?,
    resetView: Boolean
) {
    // Filtrar participantes con ubicación válida y mapear a JSON
    val validParticipants = participants.filter { it.latitud != null && it.longitud != null && it.latitud != 0.0 && it.longitud != 0.0 }
    val participantsJson = validParticipants.mapIndexed { idx, p ->
        val initials = getInitials(p.nombreSenderista)
        val cleanName = p.nombreSenderista.replace("\"", "\\\"").replace("'", "\\'")
        val cleanSangre = (p.tipoSangre ?: "No especificado").replace("\"", "\\\"").replace("'", "\\'")
        val isEmergency = activeEmergencies.contains(p.idBeacon)
        """{"lat": ${p.latitud}, "lng": ${p.longitud}, "name": "$cleanName", "initials": "$initials", "blood": "$cleanSangre", "index": $idx, "id": "${p.telefonoSenderista}", "isEmergency": $isEmergency}"""
    }.joinToString(",")
    val participantsJsonStr = "[$participantsJson]"

    val userLocJsonStr = if (currentLocation != null) {
        """{"lat": ${currentLocation.latitude}, "lng": ${currentLocation.longitude}, "desc": "Mi ubicación actual"}"""
    } else {
        "null"
    }

    val js = "if (typeof updateData === 'function') { updateData($participantsJsonStr, $userLocJsonStr, $resetView); }"
    webView.evaluateJavascript(js, null)
}

@Composable
fun TrackingMapView(
    participants: List<ParticipanteEntity>,
    activeEmergencies: Set<String> = emptySet(),
    currentLocation: Location?,
    onParticipantClick: (ParticipanteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val webInterface = remember {
        object {
            var currentParticipants = participants
            var currentOnParticipantClick = onParticipantClick

            @android.webkit.JavascriptInterface
            fun onMarkerClick(index: Int) {
                coroutineScope.launch(Dispatchers.Main) {
                    val valid = currentParticipants.filter { it.latitud != null && it.longitud != null && it.latitud != 0.0 && it.longitud != 0.0 }
                    if (index in valid.indices) {
                        currentOnParticipantClick(valid[index])
                    }
                }
            }
        }
    }

    webInterface.currentParticipants = participants
    webInterface.currentOnParticipantClick = onParticipantClick

    val leafletJs = remember {
        try {
            context.assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("TrackingMapView", "Error reading leaflet.js", e)
            ""
        }
    }
    val leafletCss = remember {
        try {
            context.assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e("TrackingMapView", "Error reading leaflet.css", e)
            ""
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )

                tag = TrackingMapStateHolder().apply {
                    this.participants = participants
                    this.activeEmergencies = activeEmergencies
                    this.currentLocation = currentLocation
                }

                settings.javaScriptEnabled = true
                addJavascriptInterface(webInterface, "AndroidApp")
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
                settings.userAgentString = "RutasSenderoApp/1.0 (com.gaspar.rutassendero)"
                
                // Forzar limpieza de caché temporalmente para eliminar los "Access blocked" cacheados
                clearCache(true)

                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val holder = view?.tag as? TrackingMapStateHolder
                        if (holder != null) {
                            holder.isLoaded = true
                            updateTrackingWebViewData(
                                webView = view,
                                participants = holder.participants,
                                activeEmergencies = holder.activeEmergencies,
                                currentLocation = holder.currentLocation,
                                resetView = true
                            )
                        }
                    }
                }

                val initialLat = currentLocation?.latitude ?: participants.firstOrNull { it.latitud != null && it.latitud != 0.0 }?.latitud ?: 0.0
                val initialLng = currentLocation?.longitude ?: participants.firstOrNull { it.longitud != null && it.longitud != 0.0 }?.longitud ?: 0.0

                val htmlHead = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                        <style>
                            $leafletCss
                            body, html, #map { height: 100vh; width: 100vw; margin: 0; padding: 0; background: #0F172A; }
                            .leaflet-container { background: #0F172A; }
                            
                            /* Estilo del marcador del participante con iniciales */
                            .participant-marker-pin {
                                width: 28px;
                                height: 28px;
                                background-color: #F97316; /* Naranja vibrante para senderistas */
                                border: 2px solid white;
                                border-radius: 50%;
                                box-shadow: 0 0 6px rgba(0,0,0,0.5);
                                display: flex;
                                align-items: center;
                                justify-content: center;
                                color: white;
                                font-family: sans-serif;
                                font-size: 10px;
                                font-weight: bold;
                            }
                            .participant-marker-pin.emergency {
                                background-color: #EF4444 !important;
                                border-color: yellow !important;
                                box-shadow: 0 0 15px rgba(239,68,68,1);
                                animation: pulse-emergency 1s infinite;
                            }
                            @keyframes pulse-emergency {
                                0% { transform: scale(1); }
                                50% { transform: scale(1.2); }
                                100% { transform: scale(1); }
                            }
                            
                            /* Estilo del pin de ubicación actual pulsante */
                            .user-marker-pin {
                                width: 18px;
                                height: 18px;
                                background-color: #3B82F6;
                                border: 2.5px solid white;
                                border-radius: 50%;
                                box-shadow: 0 0 10px rgba(59, 130, 246, 0.9);
                                position: relative;
                            }
                            .user-marker-pin::after {
                                content: '';
                                position: absolute;
                                top: -6px;
                                left: -6px;
                                width: 26px;
                                height: 26px;
                                border: 2.5px solid #3B82F6;
                                border-radius: 50%;
                                animation: pulsate 1.8s infinite ease-out;
                                opacity: 0;
                            }
                            @keyframes pulsate {
                                0% { transform: scale(0.5); opacity: 0; }
                                50% { opacity: 0.5; }
                                100% { transform: scale(1.5); opacity: 0; }
                            }
                        </style>
                        <script>$leafletJs</script>
                    </head>
                    <body>
                        <div id="map"></div>
                """.trimIndent()

                val bodyScript = """
                    <script>
                        var map = null;
                        var markers = {}; // Store markers by participant ID
                        var userMarker = null;
                        var hasSetInitialBounds = false;
                        var pendingData = null;
                        
                        function initMap() {
                            map = L.map('map', { zoomControl: false }).setView([$initialLat, $initialLng], 15);
                            L.control.zoom({ position: 'bottomright' }).addTo(map);
                            
                            // --- OPCIONES DE MAPAS DISPONIBLES ---
                            // Instrucciones: Para probar otra opción, comenta la línea de L.tileLayer activa (ponle // al inicio)
                            // y quítale los // a la opción que quieras probar.

                            // OPCIÓN 1: Esri World Topo Map (Activa actualmente) - Excelente para senderismo y topografía
                            L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}', {
                                maxZoom: 19,
                                attribution: '&copy; Esri'
                            }).addTo(map);

                            // OPCIÓN 2: OpenTopoMap - Especializado en relieve y curvas de nivel (puede ser un poco lento al cargar)
                            // L.tileLayer('https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png', {
                            //     maxZoom: 17,
                            //     attribution: '&copy; OpenTopoMap'
                            // }).addTo(map);

                            // OPCIÓN 3: Esri World Imagery - Mapa Satelital de Alta Resolución
                            // L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                            //     maxZoom: 19,
                            //     attribution: '&copy; Esri Satélite'
                            // }).addTo(map);

                            // OPCIÓN 4: OpenStreetMap Estándar - Mapa clásico
                            // L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png?cb=2', {
                            //     maxZoom: 19,
                            //     attribution: '&copy; OpenStreetMap'
                            // }).addTo(map);
                            // -------------------------------------
                            
                            if (pendingData) {
                                updateData(pendingData.participants, pendingData.userLoc, pendingData.resetView);
                                pendingData = null;
                            }
                        }
                        
                        initMap();
                        
                        function updateData(participants, userLoc, resetView) {
                            if (!map) {
                                pendingData = { participants: participants, userLoc: userLoc, resetView: resetView };
                                return;
                            }
                            
                            if (resetView) {
                                hasSetInitialBounds = false;
                            }
                            
                            var points = [];
                            var activeIds = {};
                            
                            if (participants && participants.length > 0) {
                                participants.forEach(function(p) {
                                    activeIds[p.id] = true;
                                    points.push([p.lat, p.lng]);
                                    
                                    var popupContent = "<b>" + p.name + "</b><br/><span style='font-size:11px;color:#64748B;'>Tipo Sangre: " + p.blood + "</span>";
                                    
                                    if (markers[p.id]) {
                                        // Update existing marker position smoothly
                                        markers[p.id].setLatLng([p.lat, p.lng]);
                                        markers[p.id].setPopupContent(popupContent);
                                    } else {
                                        // Create new marker
                                        var pinClass = p.isEmergency ? 'participant-marker-pin emergency' : 'participant-marker-pin';
                                        var pIcon = L.divIcon({
                                            className: pinClass,
                                            html: '<div>' + p.initials + '</div>',
                                            iconSize: [28, 28],
                                            iconAnchor: [14, 14]
                                        });
                                        
                                        var marker = L.marker([p.lat, p.lng], { icon: pIcon }).addTo(map);
                                        marker.bindPopup(popupContent);
                                        
                                        marker.on('click', function() {
                                            if (window.AndroidApp && typeof window.AndroidApp.onMarkerClick === 'function') {
                                                window.AndroidApp.onMarkerClick(p.index);
                                            }
                                        });
                                        markers[p.id] = marker;
                                    }
                                });
                            }
                            
                            // Remove markers that are no longer active
                            for (var id in markers) {
                                if (!activeIds[id]) {
                                    map.removeLayer(markers[id]);
                                    delete markers[id];
                                }
                            }
                            
                            if (userLoc) {
                                var userIcon = L.divIcon({
                                    className: 'user-marker-pin',
                                    html: '',
                                    iconSize: [18, 18],
                                    iconAnchor: [9, 9]
                                });
                                
                                if (!userMarker) {
                                    userMarker = L.marker([userLoc.lat, userLoc.lng], { icon: userIcon, zIndexOffset: 1000 }).addTo(map);
                                    userMarker.bindPopup("<b>" + userLoc.desc + "</b>");
                                } else {
                                    userMarker.setLatLng([userLoc.lat, userLoc.lng]);
                                    userMarker.setZIndexOffset(1000);
                                }
                                points.push([userLoc.lat, userLoc.lng]);
                            } else {
                                if (userMarker) {
                                    map.removeLayer(userMarker);
                                    userMarker = null;
                                }
                            }
                            
                            if (!hasSetInitialBounds && points.length > 0) {
                                var bounds = L.latLngBounds(points);
                                map.fitBounds(bounds, { padding: [40, 40] });
                                hasSetInitialBounds = true;
                            }
                            
                            map.invalidateSize();
                        }
                    </script>
                """.trimIndent()

                val html = htmlHead + bodyScript + "</body></html>"
                loadDataWithBaseURL("https://app.rutassendero.com", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            val holder = webView.tag as? TrackingMapStateHolder ?: TrackingMapStateHolder().also { webView.tag = it }
            holder.participants = participants
            holder.activeEmergencies = activeEmergencies
            holder.currentLocation = currentLocation
            
            if (holder.isLoaded) {
                updateTrackingWebViewData(
                    webView = webView,
                    participants = participants,
                    activeEmergencies = activeEmergencies,
                    currentLocation = currentLocation,
                    resetView = false
                )
            }
        },
        onRelease = { webView ->
            webView.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
        },
        modifier = modifier
    )
}

// ==========================================
// SECCIÓN DE FOTOS DE RECUERDO DE SENDERISMO
// ==========================================

fun getSouvenirPhotoFile(context: android.content.Context, routeName: String): File {
    val root = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
    val safeRouteName = routeName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val dateStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
    val dir = File(root, "TekinsRutas/$safeRouteName/$dateStr")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return File(dir, "souvenir_${System.currentTimeMillis()}.jpg")
}

fun hasSouvenirPhotos(context: android.content.Context, routeName: String): Boolean {
    val root = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
    val safeRouteName = routeName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    val dateStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
    val dir = File(root, "TekinsRutas/$safeRouteName/$dateStr")
    return if (dir.exists()) {
        val list = dir.listFiles { file -> file.extension.lowercase() == "jpg" || file.extension.lowercase() == "jpeg" }
        !list.isNullOrEmpty()
    } else {
        false
    }
}

fun saveToPublicGallery(context: android.content.Context, file: File, routeName: String): Boolean {
    val resolver = context.contentResolver
    val contentValues = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val safeRouteName = routeName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/TekinsRutas/$safeRouteName")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    if (imageUri != null) {
        try {
            resolver.openOutputStream(imageUri).use { outStream ->
                if (outStream != null) {
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outStream)
                    }
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            val intent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                data = imageUri
            }
            context.sendBroadcast(intent)
            return true
        } catch (e: Exception) {
            android.util.Log.e("Gallery", "Error al guardar en galería: ${e.message}")
        }
    }
    return false
}

fun sharePhoto(context: android.content.Context, file: File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "com.gaspar.rutassendero.fileprovider",
            file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Compartir foto de sendero"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Error al compartir la foto: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SouvenirPhotoSubMenuDialog(
    routeName: String,
    onTakeClick: () -> Unit,
    onViewClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.5.dp, ColorTekinsAccent.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Fotos del Sendero",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ColorTextoPrincipal
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = ColorTextoSecundario)
                    }
                }
                
                Text(
                    text = routeName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTekinsAccent,
                    modifier = Modifier.align(Alignment.Start).padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    onClick = {
                        onTakeClick()
                        onDismiss()
                    },
                    colors = CardDefaults.cardColors(containerColor = ColorTekinsPrimary.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = ColorTekinsAccent,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Tomar Foto de Recuerdo",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextoPrincipal
                            )
                            Text(
                                text = "Guarda un recuerdo de hoy en la ruta",
                                fontSize = 12.sp,
                                color = ColorTextoSecundario
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    onClick = {
                        onViewClick()
                        onDismiss()
                    },
                    colors = CardDefaults.cardColors(containerColor = ColorTekinsCardMuted),
                    border = BorderStroke(1.dp, ColorTextoSecundario.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = ColorTextoSecundario,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Ver Fotos de la Ruta",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextoPrincipal
                            )
                            Text(
                                text = "Visualiza y exporta las fotos a la galería",
                                fontSize = 12.sp,
                                color = ColorTextoSecundario
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryViewerDialog(
    routeName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    val photosList = remember(routeName) {
        val root = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        val safeRouteName = routeName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val dateStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        val dir = File(root, "TekinsRutas/$safeRouteName/$dateStr")
        if (dir.exists()) {
            dir.listFiles { file -> file.extension.lowercase() == "jpg" || file.extension.lowercase() == "jpeg" }?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }

    var selectedPhotoPath by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Fotos de Hoy",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ColorTextoPrincipal
                        )
                        Text(
                            text = routeName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTekinsAccent
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = ColorTextoSecundario)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (photosList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = ColorTextoSecundario.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Aún no hay fotos de recuerdo tomadas hoy.",
                                fontSize = 14.sp,
                                color = ColorTextoSecundario,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(photosList) { file ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = ColorTekinsCardMuted),
                                border = BorderStroke(1.dp, ColorTextoSecundario.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clickable { selectedPhotoPath = file.absolutePath }
                                    ) {
                                        LocalImage(
                                            filePath = file.absolutePath,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val timeStr = remember(file) {
                                            java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
                                        }
                                        Text(
                                            text = "Hora: $timeStr",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorTextoPrincipal
                                        )
                                        
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            IconButton(
                                                onClick = {
                                                    val success = saveToPublicGallery(context, file, routeName)
                                                    if (success) {
                                                        android.widget.Toast.makeText(context, "¡Foto exportada a tu Galería con éxito!", android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        android.widget.Toast.makeText(context, "No se pudo exportar la foto.", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    containerColor = ColorTekinsPrimary,
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Save,
                                                    contentDescription = "Exportar a Galería",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    sharePhoto(context, file)
                                                },
                                                colors = IconButtonDefaults.iconButtonColors(
                                                    containerColor = ColorTekinsAccent,
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Share,
                                                    contentDescription = "Compartir foto",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedPhotoPath != null) {
        FullScreenPhotoViewerDialog(
            filePath = selectedPhotoPath!!,
            onDismiss = { selectedPhotoPath = null }
        )
    }
}

/**
 * DIÁLOGO: Sincronización y Respaldo de Rutas con Google Drive.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveSyncDialog(
    viewModel: RutasViewModel,
    route: RutaEntity,
    isSynced: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val syncState by viewModel.driveSyncState.collectAsState()
    
    // Configuración de Google Sign-In
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }
    var activeAccount by remember {
        mutableStateOf(GoogleSignIn.getLastSignedInAccount(context))
    }

    // Lanzador para el inicio de sesión de Google
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            activeAccount = account
            Toast.makeText(context, "Cuenta vinculada con éxito", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Error de autenticación: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    // Monitorear Wi-Fi
    var isWifiConnected by remember {
        mutableStateOf(GoogleDriveSyncManager.isWifiConnected(context))
    }
    
    LaunchedEffect(Unit) {
        isWifiConnected = GoogleDriveSyncManager.isWifiConnected(context)
    }

    var showClearConfirm by remember { mutableStateOf(false) }
    var showFreeSpaceConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            if (syncState !is DriveSyncState.Syncing) onDismiss()
        }
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cabecera del Diálogo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Sincronización",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ColorTextoPrincipal
                        )
                        Icon(
                            imageVector = if (isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = if (isWifiConnected) "Wi-Fi Conectado" else "Se requiere Wi-Fi",
                            tint = if (isWifiConnected) ColorTekinsSuccess else ColorTekinsAlert,
                            modifier = Modifier.size(20.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = activeAccount?.email ?: "Cuenta de Google no vinculada",
                            tint = if (activeAccount != null) ColorTekinsAccent else ColorTextoSecundario,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = syncState !is DriveSyncState.Syncing
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = ColorTextoSecundario)
                    }
                }
                
                Text(
                    text = "Ruta: ${route.nombre}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTekinsAccent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                Divider(color = ColorTekinsCardMuted, modifier = Modifier.padding(bottom = 16.dp))

                // Panel de Operaciones e Historial de Progreso
                if (syncState is DriveSyncState.Idle || syncState is DriveSyncState.Success || syncState is DriveSyncState.Error) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (activeAccount == null) {
                            // Botón de Inicio de Sesión
                            Button(
                                onClick = {
                                    googleSignInLauncher.launch(googleSignInClient.signInIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "VINCULAR CUENTA",
                                    color = ColorTekinsDarkBg,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.Cloud, contentDescription = null, tint = ColorTekinsDarkBg)
                            }
                        } else {
                            // Subir copia
                            Button(
                                onClick = {
                                    val account = activeAccount!!.account
                                    if (account != null) {
                                        viewModel.uploadRouteToDrive(context, route, account)
                                    }
                                },
                                enabled = isWifiConnected,
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "SUBIR A",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_google_drive),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Descargar copia
                            OutlinedButton(
                                onClick = {
                                    val account = activeAccount!!.account
                                    if (account != null) {
                                        viewModel.downloadRouteFromDrive(context, route, account)
                                    }
                                },
                                enabled = isWifiConnected,
                                border = BorderStroke(1.5.dp, ColorTekinsAccent),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTekinsAccent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "OBTENER",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_google_drive),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }



                        // Liberar Espacio (Borrar fotos, puntos y trazas locales)
                        OutlinedButton(
                            onClick = {
                                showFreeSpaceConfirm = true
                            },
                            border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "LIBERAR ESPACIO",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFF59E0B))
                        }

                        // Eliminar Ruta Completamente del Teléfono
                        OutlinedButton(
                            onClick = {
                                showClearConfirm = true
                            },
                            border = BorderStroke(1.5.dp, ColorTekinsAlert),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTekinsAlert),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "ELIMINAR",
                                fontWeight = FontWeight.Bold,
                                color = ColorTekinsAlert,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ColorTekinsAlert)
                        }

                        if (activeAccount != null) {
                            // Cerrar sesión
                            TextButton(
                                onClick = {
                                    googleSignInClient.signOut().addOnCompleteListener {
                                        activeAccount = null
                                    }
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Desvincular cuenta", color = ColorTextoSecundario)
                            }
                        }
                    }
                }

                // Sección de Progreso Activo
                if (syncState !is DriveSyncState.Idle) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    when (val state = syncState) {
                        is DriveSyncState.CheckingWifi -> {
                            CircularProgressIndicator(color = ColorTekinsAccent)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Comprobando conexión de red...", color = ColorTextoPrincipal, fontSize = 13.sp)
                        }
                        is DriveSyncState.Authenticating -> {
                            CircularProgressIndicator(color = ColorTekinsAccent)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Autenticando con Google Play Services...", color = ColorTextoPrincipal, fontSize = 13.sp)
                        }
                        is DriveSyncState.Syncing -> {
                            CircularProgressIndicator(color = ColorTekinsAccent)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.progress,
                                color = ColorTextoPrincipal,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        is DriveSyncState.Success -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ColorTekinsSuccess, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "¡Operación completada con éxito!",
                                color = ColorTekinsSuccess,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.resetSyncState() },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary)
                            ) {
                                Text("Aceptar", color = Color.White)
                            }
                        }
                        is DriveSyncState.Error -> {
                            Icon(Icons.Default.Error, contentDescription = null, tint = ColorTekinsAlert, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                color = ColorTekinsAlert,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.resetSyncState() },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsCardMuted)
                            ) {
                                Text("Reintentar", color = ColorTextoPrincipal)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    // Confirmación para Eliminar Ruta Completa
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = ColorTekinsCardBg,
            titleContentColor = ColorTextoPrincipal,
            textContentColor = ColorTextoSecundario,
            title = { Text("¿Eliminar ruta por completo?", fontWeight = FontWeight.Bold) },
            text = { 
                Text(
                    text = "Esto borrará de forma permanente la ruta '${route.nombre}' y todos sus puntos del teléfono.\n\n" +
                           "¡Esta acción no se puede deshacer localmente!",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRuta(route)
                        showClearConfirm = false
                        onDismiss() // Cerrar el diálogo de sincronización
                    }
                ) {
                    Text("ELIMINAR RUTA", color = ColorTekinsAlert, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancelar", color = ColorTextoSecundario)
                }
            }
        )
    }

    // Confirmación para Liberar Espacio (Borrar fotos, puntos y trazas)
    if (showFreeSpaceConfirm) {
        AlertDialog(
            onDismissRequest = { showFreeSpaceConfirm = false },
            containerColor = ColorTekinsCardBg,
            titleContentColor = ColorTextoPrincipal,
            textContentColor = ColorTextoSecundario,
            title = { Text("¿Liberar espacio de esta ruta?", fontWeight = FontWeight.Bold) },
            text = { 
                Text(
                    text = "Esto eliminará permanentemente del teléfono:\n\n" +
                           "• Las fotos asociadas a los puntos\n" +
                           "• Las coordenadas del trayecto (trazas)\n" +
                           "• Todos los puntos registrados\n\n" +
                           "La ruta se conservará. Podrás volver a descargar los puntos si hiciste un respaldo en Google Drive.\n\n" +
                           "¡Esta acción local no se puede deshacer!",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLocalPhotosAndPointsAndTrace(route)
                        showFreeSpaceConfirm = false
                        onDismiss() // Cerrar el diálogo de sincronización
                    }
                ) {
                    Text("LIBERAR ESPACIO", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFreeSpaceConfirm = false }) {
                    Text("Cancelar", color = ColorTextoSecundario)
                }
            }
        )
    }
}

data class SurveyQuestion(
    val title: String,
    val questionText: String,
    val options: List<String>
)

val surveyQuestions = listOf(
    SurveyQuestion(
        title = "1. Frecuencia",
        questionText = "¿Con qué frecuencia realizas salidas a la montaña o caminatas al aire libre?",
        options = listOf(
            "A) Rara vez o nunca",
            "B) 1 a 3 veces al año",
            "C) 1 a 2 veces al mes",
            "D) Casi todas las semanas / A diario"
        )
    ),
    SurveyQuestion(
        title = "2. Distancia y Desnivel",
        questionText = "¿Cuál es la distancia y dificultad media que sueles completar en una ruta de un día?",
        options = listOf(
            "A) Menos de 5 km en terreno plano",
            "B) 5 a 12 km con subidas suaves",
            "C) 12 a 25 km con desnivel moderado o pronunciado",
            "D) Más de 25 km o rutas de alta montaña con gran desnivel"
        )
    ),
    SurveyQuestion(
        title = "3. Equipamiento",
        questionText = "¿Qué tipo de equipo utilizas habitualmente?",
        options = listOf(
            "A) Ropa cómoda y tenis deportivos comunes",
            "B) Calzado de senderismo básico y mochila ligera",
            "C) Calzado técnico, vestimenta por capas, bastones, botiquín y GPS",
            "D) Equipo técnico especializado (alta montaña, navegación satelital, crampones/piolet según temporada)"
        )
    ),
    SurveyQuestion(
        title = "4. Orientación y Autonomía",
        questionText = "¿Cómo te desenvuelves para seguir una ruta?",
        options = listOf(
            "A) Solo voy por caminos totalmente señalizados o acompañado de un guía",
            "B) Utilizo aplicaciones móviles (Komoot, Wikiloc, Strava) en rutas marcadas",
            "C) Sé interpretar mapas topográficos, brújula y mapas GPS sin cobertura",
            "D) Capaz de abrir ruta en terreno desconocido, sin sendero ni señalización"
        )
    ),
    SurveyQuestion(
        title = "5. Terreno y Clima",
        questionText = "¿En qué condiciones te sientes cómodo y seguro caminando?",
        options = listOf(
            "A) Días soleados en senderos pavimentados o muy bien definidos",
            "B) Senderos de tierra con ligera inclinación y clima estable",
            "C) Terreno rocoso, irregular o resbaladizo, aun con lluvia ligera o frío",
            "D) Terreno de alta montaña expuesto, nieve/hielo y condiciones climáticas adversas"
        )
    ),
    SurveyQuestion(
        title = "6. Trayectoria / Rol",
        questionText = "¿Cómo definirías tu relación con el senderismo?",
        options = listOf(
            "A) Actividad recreativa muy casual u ocasional",
            "B) Aficionado constante que busca rutas familiares o comerciales",
            "C) Senderista autónomo con experiencia en autosuficiencia y gestión de riesgos",
            "D) Guía certificado, atleta de ultradistancia/montaña o montañista profesional"
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HikingSurveyDialog(
    onDismiss: () -> Unit,
    onCompleted: (String, Int) -> Unit
) {
    val context = LocalContext.current
    var answers by remember { mutableStateOf(IntArray(6) { -1 }) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Cuestionario de Nivelación",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTekinsAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                Text(
                    text = "Por favor responde las 6 preguntas para evaluar tu nivel.",
                    fontSize = 12.sp,
                    color = ColorTextoSecundario,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    surveyQuestions.forEachIndexed { qIdx, question ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ColorTekinsCardBg),
                            border = BorderStroke(1.dp, ColorTekinsCardMuted),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = question.title,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorTextoPrincipal,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = question.questionText,
                                    color = ColorTextoSecundario,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                question.options.forEachIndexed { oIdx, optionText ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val newAnswers = answers.copyOf()
                                                newAnswers[qIdx] = oIdx
                                                answers = newAnswers
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = (answers[qIdx] == oIdx),
                                            onClick = {
                                                val newAnswers = answers.copyOf()
                                                newAnswers[qIdx] = oIdx
                                                answers = newAnswers
                                            },
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = ColorTekinsAccent,
                                                unselectedColor = ColorTextoSecundario
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = optionText,
                                            color = ColorTextoPrincipal,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextoSecundario)
                    ) {
                        Text("Cancelar")
                    }

                    val allAnswered = answers.all { it >= 0 }
                    Button(
                        onClick = {
                            val totalScore = answers.sum()
                            val level = when {
                                totalScore <= 3 -> "ocasional"
                                totalScore <= 8 -> "principiante"
                                totalScore <= 14 -> "experto"
                                else -> "profesional"
                            }
                            onCompleted(level, totalScore)
                        },
                        enabled = allAnswered,
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorTekinsAccent,
                            disabledContainerColor = ColorTekinsCardMuted
                        )
                    ) {
                        Text(
                            text = "Calcular Nivel",
                            color = if (allAnswered) ColorTekinsDarkBg else ColorTextoSecundario,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderistaDialog(
    currentSenderista: SenderistaEntity?,
    agrupaciones: List<AgrupacionEntity> = emptyList(),
    onSave: (SenderistaEntity) -> Unit,
    onDismiss: () -> Unit,
    onDeleteAccount: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    // Default values if currentSenderista is null
    val defaultEmail = remember {
        try {
            GoogleSignIn.getLastSignedInAccount(context)?.email ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    val defaultPhone = remember {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_NUMBERS) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                telephonyManager?.line1Number ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    var email by remember { mutableStateOf(currentSenderista?.email ?: defaultEmail) }
    var rol by remember { mutableStateOf(currentSenderista?.rol ?: "S") }
    var telefono by remember { mutableStateOf(currentSenderista?.telefono ?: defaultPhone) }
    var nombre by remember { mutableStateOf(currentSenderista?.nombre ?: "") }
    var sexo by remember { mutableStateOf(currentSenderista?.sexo ?: "") } // "M" or "F"
    
    // Divide the stored dd/MM/yyyy or yyyy-MM-dd date into separate fields for Year, Month, and Day
    val parts = remember(currentSenderista?.fechaNacimiento) {
        currentSenderista?.fechaNacimiento?.split(Regex("[/\\-]")) ?: listOf("", "", "")
    }
    val isLegacyFormat = parts.size == 3 && parts[0].length == 4
    var ano by remember(currentSenderista?.fechaNacimiento) { mutableStateOf(if (parts.size == 3) (if (isLegacyFormat) parts[0] else parts[2]) else "") }
    var mes by remember(currentSenderista?.fechaNacimiento) { mutableStateOf(if (parts.size == 3) parts[1] else "") }
    var dia by remember(currentSenderista?.fechaNacimiento) { mutableStateOf(if (parts.size == 3) (if (isLegacyFormat) parts[2] else parts[0]) else "") }

    var aQuienesAvisar by remember(currentSenderista) { mutableStateOf(currentSenderista?.aQuienesAvisar ?: "") }
    var aQuienAvisarTelefono1 by remember(currentSenderista) { mutableStateOf(currentSenderista?.aQuienAvisarTelefono1 ?: "") }
    var aQuienAvisarTelefono2 by remember(currentSenderista) { mutableStateOf(currentSenderista?.aQuienAvisarTelefono2 ?: "") }
    var tieneEnfermedadCronica by remember(currentSenderista) { mutableStateOf(currentSenderista?.tieneEnfermedadCronica == "S") }
    var enfermedadCronica1 by remember(currentSenderista) { mutableStateOf(currentSenderista?.enfermedadCronica1 ?: "") }
    var enfermedadCronica2 by remember(currentSenderista) { mutableStateOf(currentSenderista?.enfermedadCronica2 ?: "") }
    var tieneAuto by remember(currentSenderista) { mutableStateOf(currentSenderista?.tieneAuto == "S") }
    var direccion by remember(currentSenderista) { mutableStateOf(currentSenderista?.direccion ?: "") }
    var llevaMascota by remember(currentSenderista) { mutableStateOf(currentSenderista?.llevaMascota == "S") }
    var alergias by remember(currentSenderista) { mutableStateOf(currentSenderista?.alergias ?: "") }
    var tipoSangre by remember(currentSenderista) { mutableStateOf(currentSenderista?.tipoSangre ?: "") }
    var seguroMedico by remember(currentSenderista) { mutableStateOf(currentSenderista?.seguroMedico ?: "") }

    var nivelSenderista by remember(currentSenderista) { mutableStateOf(currentSenderista?.nivelSenderista ?: "principiante") }
    var codigoConfianza by remember(currentSenderista) { mutableStateOf(currentSenderista?.codigoConfianza ?: "") }
    var pesoStr by remember(currentSenderista?.peso) { mutableStateOf(currentSenderista?.peso?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "") }
    var idAgrupacion by remember(currentSenderista) { mutableStateOf(currentSenderista?.idAgrupacion?.takeIf { it.isNotBlank() && it != "@&" } ?: "MH") }
    var showAgrupacionMenu by remember { mutableStateOf(false) }
    var cloudRecovered by remember { mutableStateOf(false) }

    LaunchedEffect(email, telefono) {
        if (currentSenderista == null && !cloudRecovered) {
            val cleanEmail = email.trim()
            val cleanPhone = telefono.filter { it.isDigit() }
            if (cleanEmail.contains("@") && cleanEmail.contains(".") && cleanPhone.length >= 10) {
                try {
                    val cloudSenderista = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.gaspar.rutassendero.sync.CloudSyncManager.fetchSenderistaByEmail(cleanEmail)
                    }
                    if (cloudSenderista != null) {
                        val cloudPhoneDigits = cloudSenderista.telefono.filter { it.isDigit() }
                        val matches = if (cloudPhoneDigits.length >= 10 && cleanPhone.length >= 10) {
                            cloudPhoneDigits.takeLast(10) == cleanPhone.takeLast(10)
                        } else {
                            cloudPhoneDigits == cleanPhone
                        }
                        if (matches) {
                            cloudRecovered = true
                            nombre = cloudSenderista.nombre
                            rol = if (cloudSenderista.rol.equals("G", ignoreCase = true)) "G" else cloudSenderista.rol.uppercase()
                            sexo = cloudSenderista.sexo
                            
                            val cParts = cloudSenderista.fechaNacimiento.split(Regex("[/\\-]"))
                            if (cParts.size == 3) {
                                val cLegacy = cParts[0].length == 4
                                ano = if (cLegacy) cParts[0] else cParts[2]
                                mes = cParts[1]
                                dia = if (cLegacy) cParts[2] else cParts[0]
                            }
                            aQuienesAvisar = cloudSenderista.aQuienesAvisar
                            aQuienAvisarTelefono1 = cloudSenderista.aQuienAvisarTelefono1
                            aQuienAvisarTelefono2 = cloudSenderista.aQuienAvisarTelefono2 ?: ""
                            tieneEnfermedadCronica = cloudSenderista.tieneEnfermedadCronica == "S"
                            enfermedadCronica1 = cloudSenderista.enfermedadCronica1 ?: ""
                            enfermedadCronica2 = cloudSenderista.enfermedadCronica2 ?: ""
                            tieneAuto = cloudSenderista.tieneAuto == "S"
                            direccion = cloudSenderista.direccion ?: ""
                            llevaMascota = cloudSenderista.llevaMascota == "S"
                            alergias = cloudSenderista.alergias ?: ""
                            tipoSangre = cloudSenderista.tipoSangre ?: ""
                            seguroMedico = cloudSenderista.seguroMedico ?: ""
                            nivelSenderista = cloudSenderista.nivelSenderista
                            codigoConfianza = cloudSenderista.codigoConfianza ?: ""
                            pesoStr = cloudSenderista.peso?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: ""
                            idAgrupacion = cloudSenderista.idAgrupacion?.takeIf { it.isNotBlank() && it != "@&" } ?: "MH"
                            Toast.makeText(context, "Se recuperaron los datos de la nube para $cleanEmail.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SenderistaDialog", "Error auto-recuperando datos: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(nombre) {
        if (codigoConfianza.isBlank() && nombre.isNotBlank()) {
            val iniciales = nombre.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
            val randomChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            var newCode = iniciales
            while (newCode.length < 6) {
                newCode += randomChars.random()
            }
            codigoConfianza = newCode.take(6).uppercase()
        }
    }
    var showSexoMenu by remember { mutableStateOf(false) }
    var showSangreMenu by remember { mutableStateOf(false) }
    var showNivelMenu by remember { mutableStateOf(false) }
    var showSurveyDialog by remember { mutableStateOf(false) }

    // Estados para términos legales vinculados a currentSenderista
    var isPrivacyAccepted by remember(currentSenderista?.consentimientoTerminos) { mutableStateOf(currentSenderista?.consentimientoTerminos ?: false) }
    var isMedicalDataAccepted by remember(currentSenderista?.consentimientoFichaMedica) { mutableStateOf(currentSenderista?.consentimientoFichaMedica ?: false) }
    var isGpsMediaAccepted by remember(currentSenderista?.consentimientoGpsMultimedia) { mutableStateOf(currentSenderista?.consentimientoGpsMultimedia ?: false) }
    var showPrivacyBottomSheet by remember { mutableStateOf(false) }

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                ano = String.format(Locale.US, "%04d", year)
                mes = String.format(Locale.US, "%02d", month + 1)
                dia = String.format(Locale.US, "%02d", dayOfMonth)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = if (currentSenderista == null) "Registro de Senderista" else "Editar Perfil de Senderista",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTekinsAccent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                Text(
                    text = "Los campos marcados con * son obligatorios",
                    fontSize = 12.sp,
                    color = ColorTekinsAlert,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // --- SECCIÓN 1: DATOS OBLIGATORIOS (NOT NULL) ---
                    Text(
                        text = "Datos Obligatorios",
                        fontWeight = FontWeight.Bold,
                        color = ColorTextoPrincipal,
                        fontSize = 14.sp
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        readOnly = currentSenderista != null,
                        label = { Text("Email *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorTekinsAccent,
                            unfocusedBorderColor = ColorTextoSecundario,
                            focusedLabelColor = ColorTekinsAccent,
                            unfocusedLabelColor = ColorTextoSecundario,
                            focusedTextColor = ColorTextoPrincipal,
                            unfocusedTextColor = ColorTextoPrincipal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = telefono,
                        onValueChange = { telefono = it },
                        readOnly = currentSenderista != null,
                        label = { Text("Teléfono dispositivo *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorTekinsAccent,
                            unfocusedBorderColor = ColorTextoSecundario,
                            focusedLabelColor = ColorTekinsAccent,
                            unfocusedLabelColor = ColorTextoSecundario,
                            focusedTextColor = ColorTextoPrincipal,
                            unfocusedTextColor = ColorTextoPrincipal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre Completo *") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorTekinsAccent,
                            unfocusedBorderColor = ColorTextoSecundario,
                            focusedLabelColor = ColorTekinsAccent,
                            unfocusedLabelColor = ColorTextoSecundario,
                            focusedTextColor = ColorTextoPrincipal,
                            unfocusedTextColor = ColorTextoPrincipal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Agrupación Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val currentAgrupacionName = when {
                            idAgrupacion == "@&" || idAgrupacion.isBlank() -> "Sin Agrupación (Opcional)"
                            else -> agrupaciones.find { it.idAgrupacion.equals(idAgrupacion, ignoreCase = true) }?.nombre
                                ?: com.gaspar.rutassendero.data.AgrupacionEntity.SEED_AGRUPACIONES.find { it.idAgrupacion.equals(idAgrupacion, ignoreCase = true) }?.nombre
                                ?: if (idAgrupacion.equals("MH", ignoreCase = true)) "Miguel Hidalgo (MH)" else idAgrupacion
                        }
                        OutlinedTextField(
                            value = currentAgrupacionName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Agrupación") },
                            trailingIcon = {
                                IconButton(onClick = { showAgrupacionMenu = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Seleccionar Agrupación")
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorTekinsAccent,
                                unfocusedBorderColor = ColorTextoSecundario,
                                focusedLabelColor = ColorTekinsAccent,
                                unfocusedLabelColor = ColorTextoSecundario,
                                focusedTextColor = ColorTextoPrincipal,
                                unfocusedTextColor = ColorTextoPrincipal
                            ),
                            modifier = Modifier.fillMaxWidth()
                                .clickable { showAgrupacionMenu = true }
                        )
                        DropdownMenu(
                            expanded = showAgrupacionMenu,
                            onDismissRequest = { showAgrupacionMenu = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sin Agrupación (Opcional)") },
                                onClick = {
                                    idAgrupacion = "@&"
                                    showAgrupacionMenu = false
                                }
                            )
                            val options = if (agrupaciones.isNotEmpty()) agrupaciones else com.gaspar.rutassendero.data.AgrupacionEntity.SEED_AGRUPACIONES
                            options.forEach { agru ->
                                DropdownMenuItem(
                                    text = { Text("${agru.nombre} (${agru.idAgrupacion})") },
                                    onClick = {
                                        idAgrupacion = agru.idAgrupacion
                                        showAgrupacionMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Sexo Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = when(sexo) {
                                "M" -> "Masculino"
                                "F" -> "Femenino"
                                else -> ""
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Sexo *") },
                            trailingIcon = {
                                IconButton(onClick = { showSexoMenu = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ColorTextoSecundario)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorTekinsAccent,
                                unfocusedBorderColor = ColorTextoSecundario,
                                focusedLabelColor = ColorTekinsAccent,
                                unfocusedLabelColor = ColorTextoSecundario,
                                focusedTextColor = ColorTextoPrincipal,
                                unfocusedTextColor = ColorTextoPrincipal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = showSexoMenu,
                            onDismissRequest = { showSexoMenu = false },
                            modifier = Modifier.background(ColorTekinsCardBg)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Masculino", color = ColorTextoPrincipal) },
                                onClick = {
                                    sexo = "M"
                                    showSexoMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Femenino", color = ColorTextoPrincipal) },
                                onClick = {
                                    sexo = "F"
                                    showSexoMenu = false
                                }
                            )
                        }
                    }

                    // Fecha de Nacimiento (Three fields YYYY/MM/DD side-by-side with auto-focus and calendar button)
                    val focusManager = LocalFocusManager.current
                    Text(
                        text = "Fecha de Nacimiento *",
                        color = ColorTextoSecundario,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Año (AAAA)
                        OutlinedTextField(
                            value = ano,
                            onValueChange = {
                                if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                    ano = it
                                    if (it.length == 4) {
                                        focusManager.moveFocus(FocusDirection.Next)
                                    }
                                }
                            },
                            label = { Text("Año") },
                            placeholder = { Text("AAAA") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorTekinsAccent,
                                unfocusedBorderColor = ColorTextoSecundario,
                                focusedLabelColor = ColorTekinsAccent,
                                unfocusedLabelColor = ColorTextoSecundario,
                                focusedTextColor = ColorTextoPrincipal,
                                unfocusedTextColor = ColorTextoPrincipal
                            ),
                            modifier = Modifier.weight(1.5f)
                        )

                        // Mes (MM)
                        OutlinedTextField(
                            value = mes,
                            onValueChange = {
                                if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                                    mes = it
                                    if (it.length == 2) {
                                        focusManager.moveFocus(FocusDirection.Next)
                                    }
                                }
                            },
                            label = { Text("Mes") },
                            placeholder = { Text("MM") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorTekinsAccent,
                                unfocusedBorderColor = ColorTextoSecundario,
                                focusedLabelColor = ColorTekinsAccent,
                                unfocusedLabelColor = ColorTextoSecundario,
                                focusedTextColor = ColorTextoPrincipal,
                                unfocusedTextColor = ColorTextoPrincipal
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Día (DD)
                        OutlinedTextField(
                            value = dia,
                            onValueChange = {
                                if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                                    dia = it
                                }
                            },
                            label = { Text("Día") },
                            placeholder = { Text("DD") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorTekinsAccent,
                                unfocusedBorderColor = ColorTextoSecundario,
                                focusedLabelColor = ColorTekinsAccent,
                                unfocusedLabelColor = ColorTextoSecundario,
                                focusedTextColor = ColorTextoPrincipal,
                                unfocusedTextColor = ColorTextoPrincipal
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Calendario Picker Button
                        IconButton(
                            onClick = {
                                val y = ano.toIntOrNull()
                                val m = mes.toIntOrNull()
                                val d = dia.toIntOrNull()
                                if (y != null && m != null && d != null && m in 1..12 && d in 1..31) {
                                    datePickerDialog.updateDate(y, m - 1, d)
                                }
                                datePickerDialog.show()
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Seleccionar fecha del calendario",
                                tint = ColorTekinsAccent
                            )
                        }
                    }

                    OutlinedTextField(
                        value = aQuienesAvisar,
                        onValueChange = { aQuienesAvisar = it },
                        label = { Text("¿A quiénes avisar en emergencia? *") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorTekinsAccent,
                            unfocusedBorderColor = ColorTextoSecundario,
                            focusedLabelColor = ColorTekinsAccent,
                            unfocusedLabelColor = ColorTextoSecundario,
                            focusedTextColor = ColorTextoPrincipal,
                            unfocusedTextColor = ColorTextoPrincipal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = aQuienAvisarTelefono1,
                        onValueChange = { aQuienAvisarTelefono1 = it },
                        label = { Text("Teléfono de Emergencia 1 *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorTekinsAccent,
                            unfocusedBorderColor = ColorTextoSecundario,
                            focusedLabelColor = ColorTekinsAccent,
                            unfocusedLabelColor = ColorTextoSecundario,
                            focusedTextColor = ColorTextoPrincipal,
                            unfocusedTextColor = ColorTextoPrincipal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = codigoConfianza,
                        onValueChange = { newValue ->
                            if (newValue.length <= 6) {
                                codigoConfianza = newValue.uppercase()
                            }
                        },
                        label = { Text("Código de Confianza (6 caracteres) *") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorTekinsAccent,
                            unfocusedBorderColor = ColorTextoSecundario,
                            focusedLabelColor = ColorTekinsAccent,
                            unfocusedLabelColor = ColorTextoSecundario,
                            focusedTextColor = ColorTextoPrincipal,
                            unfocusedTextColor = ColorTextoPrincipal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = ColorTekinsCardMuted)
                    Spacer(modifier = Modifier.height(10.dp))

                    // --- SECCIÓN 2: DATOS OPCIONALES / CONDICIONALES ---
                    Text(
                        text = "Datos Opcionales (Pero en una Emergencia útiles.. su Decisión)",
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.Yellow,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tipo de Sangre Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val tiposSangre = listOf("No especificado", "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
                        OutlinedTextField(
                            value = tipoSangre,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Tipo de Sangre (Opcional)") },
                            trailingIcon = {
                                IconButton(onClick = { showSangreMenu = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ColorTextoSecundario)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorTekinsAccent,
                                unfocusedBorderColor = ColorTextoSecundario,
                                focusedLabelColor = ColorTekinsAccent,
                                unfocusedLabelColor = ColorTextoSecundario,
                                focusedTextColor = ColorTextoPrincipal,
                                unfocusedTextColor = ColorTextoPrincipal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = showSangreMenu,
                            onDismissRequest = { showSangreMenu = false },
                            modifier = Modifier.background(ColorTekinsCardBg)
                        ) {
                            tiposSangre.forEach { tipo ->
                                DropdownMenuItem(
                                    text = { Text(tipo, color = ColorTextoPrincipal) },
                                    onClick = {
                                        tipoSangre = tipo
                                        showSangreMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    
                    OutlinedTextField(
                        value = pesoStr,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                pesoStr = newValue
                            }
                        },
                        label = { Text("Peso en kg (Opcional)") },
                        placeholder = { Text("Ej. 75.5") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorTekinsAccent,
                            unfocusedBorderColor = ColorTextoSecundario,
                            focusedLabelColor = ColorTekinsAccent,
                            unfocusedLabelColor = ColorTextoSecundario,
                            focusedTextColor = ColorTextoPrincipal,
                            unfocusedTextColor = ColorTextoPrincipal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = aQuienAvisarTelefono2 ?: "",
                        onValueChange = { aQuienAvisarTelefono2 = it },
                        label = { Text("Teléfono de Emergencia 2 (Opcional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorTekinsAccent,
                            unfocusedBorderColor = ColorTextoSecundario,
                            focusedLabelColor = ColorTekinsAccent,
                            unfocusedLabelColor = ColorTextoSecundario,
                            focusedTextColor = ColorTextoPrincipal,
                            unfocusedTextColor = ColorTextoPrincipal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = direccion,
                        onValueChange = { direccion = it },
                        label = { Text("Dirección (Opcional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorTekinsAccent,
                            unfocusedBorderColor = ColorTextoSecundario,
                            focusedLabelColor = ColorTekinsAccent,
                            unfocusedLabelColor = ColorTextoSecundario,
                            focusedTextColor = ColorTextoPrincipal,
                            unfocusedTextColor = ColorTextoPrincipal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = alergias,
                        onValueChange = { alergias = it },
                        label = { Text("Alergias (Opcional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorTekinsAccent,
                            unfocusedBorderColor = ColorTextoSecundario,
                            focusedLabelColor = ColorTekinsAccent,
                            unfocusedLabelColor = ColorTextoSecundario,
                            focusedTextColor = ColorTextoPrincipal,
                            unfocusedTextColor = ColorTextoPrincipal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = seguroMedico,
                        onValueChange = { seguroMedico = it },
                        label = { Text("Seguro Médico (Opcional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorTekinsAccent,
                            unfocusedBorderColor = ColorTextoSecundario,
                            focusedLabelColor = ColorTekinsAccent,
                            unfocusedLabelColor = ColorTextoSecundario,
                            focusedTextColor = ColorTextoPrincipal,
                            unfocusedTextColor = ColorTextoPrincipal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )



                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = when (nivelSenderista) {
                                "ocasional" -> "Ocasional"
                                "principiante" -> "Principiante"
                                "intermedio" -> "Experto"
                                "avanzado" -> "Experto"
                                "experto" -> "Experto"
                                "profesional" -> "Profesional"
                                else -> nivelSenderista
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Nivel de Senderista") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorTekinsAccent,
                                unfocusedBorderColor = ColorTextoSecundario,
                                focusedLabelColor = ColorTekinsAccent,
                                unfocusedLabelColor = ColorTextoSecundario,
                                focusedTextColor = ColorTextoPrincipal,
                                unfocusedTextColor = ColorTextoPrincipal
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                            Button(
                                onClick = { showNivelMenu = true },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent)
                            ) {
                                Text("Evaluar Exp.", color = ColorTekinsDarkBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            DropdownMenu(
                                expanded = showNivelMenu,
                                onDismissRequest = { showNivelMenu = false },
                                modifier = Modifier.background(ColorTekinsCardBg)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Ocasional", color = ColorTextoPrincipal) },
                                    onClick = {
                                        nivelSenderista = "ocasional"
                                        showNivelMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Principiante", color = ColorTextoPrincipal) },
                                    onClick = {
                                        nivelSenderista = "principiante"
                                        showNivelMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Experto", color = ColorTextoPrincipal) },
                                    onClick = {
                                        nivelSenderista = "experto"
                                        showNivelMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Profesional", color = ColorTextoPrincipal) },
                                    onClick = {
                                        nivelSenderista = "profesional"
                                        showNivelMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Hacer evaluación para saber...", color = ColorTekinsAccent) },
                                    onClick = {
                                        showNivelMenu = false
                                        showSurveyDialog = true
                                    }
                                )
                            }
                        }
                    }

                    // Switches
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("¿Tiene Auto?", color = ColorTextoPrincipal)
                        Switch(
                            checked = tieneAuto,
                            onCheckedChange = { tieneAuto = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorTekinsAccent,
                                checkedTrackColor = ColorTekinsPrimary
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("¿Lleva Mascota?", color = ColorTextoPrincipal)
                        Switch(
                            checked = llevaMascota,
                            onCheckedChange = { llevaMascota = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorTekinsAccent,
                                checkedTrackColor = ColorTekinsPrimary
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("¿Tiene Enfermedad Crónica?", color = ColorTextoPrincipal)
                        Switch(
                            checked = tieneEnfermedadCronica,
                            onCheckedChange = { tieneEnfermedadCronica = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorTekinsAccent,
                                checkedTrackColor = ColorTekinsPrimary
                            )
                        )
                    }

                    if (tieneEnfermedadCronica) {
                        OutlinedTextField(
                            value = enfermedadCronica1,
                            onValueChange = { enfermedadCronica1 = it },
                            label = { Text("Enfermedad Crónica 1 * (Obligatoria)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorTekinsAccent,
                                unfocusedBorderColor = ColorTextoSecundario,
                                focusedLabelColor = ColorTekinsAccent,
                                unfocusedLabelColor = ColorTextoSecundario,
                                focusedTextColor = ColorTextoPrincipal,
                                unfocusedTextColor = ColorTextoPrincipal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = enfermedadCronica2,
                            onValueChange = { enfermedadCronica2 = it },
                            label = { Text("Enfermedad Crónica 2 (Opcional)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorTekinsAccent,
                                unfocusedBorderColor = ColorTextoSecundario,
                                focusedLabelColor = ColorTekinsAccent,
                                unfocusedLabelColor = ColorTextoSecundario,
                                focusedTextColor = ColorTextoPrincipal,
                                unfocusedTextColor = ColorTextoPrincipal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Checkboxes de Privacidad y Términos
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Checkbox(
                            checked = isPrivacyAccepted,
                            onCheckedChange = { isPrivacyAccepted = it },
                            colors = CheckboxDefaults.colors(checkedColor = ColorTekinsAccent, uncheckedColor = ColorTextoSecundario)
                        )
                        val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
                            append("He leído y acepto los Términos de Uso y el ")
                            pushStringAnnotation(tag = "privacy", annotation = "privacy")
                            withStyle(style = androidx.compose.ui.text.SpanStyle(color = ColorTekinsAccent, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                                append("Aviso de Privacidad Integral")
                            }
                            pop()
                            append(".")
                        }
                        androidx.compose.foundation.text.ClickableText(
                            text = annotatedString,
                            style = androidx.compose.ui.text.TextStyle(color = ColorTextoPrincipal, fontSize = 14.sp),
                            onClick = { offset ->
                                annotatedString.getStringAnnotations(tag = "privacy", start = offset, end = offset).firstOrNull()?.let {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://jgasparmxx.github.io/tkssenderismo-docs/"))
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Checkbox(
                            checked = isMedicalDataAccepted,
                            onCheckedChange = { isMedicalDataAccepted = it },
                            colors = CheckboxDefaults.colors(checkedColor = ColorTekinsAccent, uncheckedColor = ColorTextoSecundario)
                        )
                        val hasMedicalData = tipoSangre.isNotBlank() && tipoSangre != "No especificado" || alergias.isNotBlank() || tieneEnfermedadCronica
                        val label = if (hasMedicalData) "Consiento el tratamiento de mis datos médicos (*)" else "Consiento el tratamiento de mis datos médicos (Opcional)"
                        Text(label, color = ColorTextoPrincipal, fontSize = 14.sp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Checkbox(
                            checked = isGpsMediaAccepted,
                            onCheckedChange = { isGpsMediaAccepted = it },
                            colors = CheckboxDefaults.colors(checkedColor = ColorTekinsAccent, uncheckedColor = ColorTextoSecundario)
                        )
                        Text("Autorizo el acceso a mi ubicación GPS (primer plano y segundo plano) y galería/cámara.", color = ColorTextoPrincipal, fontSize = 14.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (currentSenderista == null) {
                                    Toast.makeText(context, "El perfil de senderista es obligatorio para utilizar la aplicación.", Toast.LENGTH_LONG).show()
                                }
                                onDismiss()
                            }
                        ) {
                            Text("Cancelar", color = ColorTextoSecundario)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                        onClick = {
                            // Validaciones
                            if (email.isBlank() || telefono.isBlank() || nombre.isBlank() ||
                                sexo.isBlank() || ano.isBlank() || mes.isBlank() || dia.isBlank() ||
                                aQuienesAvisar.isBlank() || aQuienAvisarTelefono1.isBlank() ||
                                codigoConfianza.length < 3) {
                                Toast.makeText(context, "Por favor complete todos los campos obligatorios (*) y el código de al menos 3 caracteres.", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            val hasMedicalData = tipoSangre.isNotBlank() && tipoSangre != "No especificado" || alergias.isNotBlank() || tieneEnfermedadCronica
                            if (hasMedicalData && !isMedicalDataAccepted) {
                                Toast.makeText(context, "Debe aceptar el tratamiento de datos médicos.", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (!isPrivacyAccepted || !isGpsMediaAccepted) {
                                Toast.makeText(context, "Debe aceptar los Términos de Uso y Permisos obligatorios.", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            val formattedAno = ano.trim().padStart(4, '0')
                            val formattedMes = mes.trim().padStart(2, '0')
                            val formattedDia = dia.trim().padStart(2, '0')

                            val y = formattedAno.toIntOrNull() ?: 0
                            val m = formattedMes.toIntOrNull() ?: 0
                            val d = formattedDia.toIntOrNull() ?: 0

                            if (y < 1900 || y > 2100 || m < 1 || m > 12 || d < 1 || d > 31) {
                                Toast.makeText(context, "Por favor ingrese una fecha válida (año entre 1900 y 2100, mes 1-12, día 1-31).", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            val fechaNacimientoStr = "$formattedDia/$formattedMes/$formattedAno"

                            if (tieneEnfermedadCronica && enfermedadCronica1.isBlank()) {
                                Toast.makeText(context, "Por favor especifique la Enfermedad Crónica 1.", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            val normalizedRol = if (rol.equals("G", ignoreCase = true)) "G" else if (rol.isBlank()) "S" else rol.trim().uppercase()
                            val profile = SenderistaEntity(
                                email = email.trim(),
                                rol = normalizedRol,
                                telefono = telefono.trim(),
                                nombre = nombre.trim(),
                                sexo = sexo,
                                fechaNacimiento = fechaNacimientoStr,
                                aQuienesAvisar = aQuienesAvisar.trim(),
                                aQuienAvisarTelefono1 = aQuienAvisarTelefono1.trim(),
                                aQuienAvisarTelefono2 = aQuienAvisarTelefono2.takeIf { it.isNotBlank() }?.trim(),
                                tieneEnfermedadCronica = if (tieneEnfermedadCronica) "S" else "N",
                                enfermedadCronica1 = enfermedadCronica1.takeIf { tieneEnfermedadCronica && it.isNotBlank() }?.trim(),
                                enfermedadCronica2 = enfermedadCronica2.takeIf { tieneEnfermedadCronica && it.isNotBlank() }?.trim(),
                                tieneAuto = if (tieneAuto) "S" else "N",
                                direccion = direccion.takeIf { it.isNotBlank() }?.trim(),
                                llevaMascota = if (llevaMascota) "S" else "N",
                                alergias = alergias.takeIf { it.isNotBlank() }?.trim(),
                                tipoSangre = tipoSangre.takeIf { it.isNotBlank() && it != "No especificado" },
                                seguroMedico = seguroMedico.takeIf { it.isNotBlank() }?.trim(),

                                nivelSenderista = nivelSenderista,
                                idBeacon = currentSenderista?.idBeacon,
                                fupdate = System.currentTimeMillis(),
                                consentimientoTerminos = isPrivacyAccepted,
                                consentimientoFichaMedica = isMedicalDataAccepted,
                                consentimientoGpsMultimedia = isGpsMediaAccepted,
                                timestampConsentimientoUtc = System.currentTimeMillis(),
                                codigoConfianza = codigoConfianza,
                                peso = pesoStr.toDoubleOrNull(),
                                idAgrupacion = when {
                                    idAgrupacion == "@&" -> null
                                    idAgrupacion.isBlank() -> "MH"
                                    else -> idAgrupacion.trim()
                                }
                            )
                            onSave(profile)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent)
                    ) {
                        Text("Guardar", color = ColorTekinsDarkBg, fontWeight = FontWeight.Bold)
                    }
                    } // close inner Row

                    if (currentSenderista != null && onDeleteAccount != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = onDeleteAccount
                        ) {
                            Text("Eliminar Cuenta", color = ColorTekinsAlert, fontWeight = FontWeight.Bold)
                        }
                    }
                } // close outer Column
            }
        }
    }

    if (showSurveyDialog) {
        HikingSurveyDialog(
            onDismiss = { showSurveyDialog = false },
            onCompleted = { level, _ ->
                nivelSenderista = level
                showSurveyDialog = false
                val levelLabel = when(level) {
                    "ocasional" -> "Ocasional"
                    "principiante" -> "Principiante"
                    "experto" -> "Experto"
                    "profesional" -> "Profesional"
                    else -> level
                }
                Toast.makeText(context, "Tu evaluación es: $levelLabel", Toast.LENGTH_LONG).show()
            }
        )
    }

    if (showPrivacyBottomSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showPrivacyBottomSheet = false },
            containerColor = ColorTekinsCardBg
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Aviso de Privacidad Integral", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ColorTextoPrincipal)
                Spacer(modifier = Modifier.height(16.dp))
                Text("1.1. Identidad y Domicilio del Responsable\nEl Responsable del tratamiento de sus datos personales es el desarrollador/operador de la aplicación de senderismo, quien se compromete a salvaguardar la privacidad, confidencialidad e integridad de la información.\n\n1.2. Datos Personales Recabados\nPara la prestación y operatividad del servicio se recaban datos generales (nombre, teléfono, etc.), datos de salud opcionales (tipo de sangre, alergias) y datos telemétricos (coordenadas GPS).\n\n1.3. Finalidades del Tratamiento\nGestión de perfil, trazado de rutas, vinculación de fotografías y ficha de emergencia.\n\n1.4. Transferencia de Datos\nNo se compartirán con terceros ajenos salvo requerimiento expreso de autoridades en emergencias.\n\n1.5. Derechos ARCO\nLos datos se protegen y el usuario puede ejercer sus derechos de Acceso, Rectificación, Cancelación y Oposición.", color = ColorTextoSecundario, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun RutaDetailDialog(
    ruta: RutaEntity,
    tracePoints: List<TrazaRutaEntity> = emptyList(),
    userLocation: Location? = null,
    onDismiss: () -> Unit
) {
    val warmBg = Color(0xFF221F1C) // Warm dark background
    val textColor = Color(0xFFFAF6F0) // Warm cream/white text
    val textMuted = Color(0xFFC7BDB1) // Warm gray text
    val accentColor = Color(0xFF2DD4BF) // Keep ColorTekinsAccent for consistency
    val context = LocalContext.current
    val db = remember { com.gaspar.rutassendero.data.RutasDatabase.getDatabase(context) }
    var fraseCinica by remember(ruta) { mutableStateOf("Cargando...") }

    var senderistaPhone by remember { mutableStateOf("") }
    var senderistaPeso by remember { mutableStateOf<Double?>(null) }
    var effectiveDuracionSegundos by remember(ruta) { mutableStateOf(ruta.duracionSegundos) }
    LaunchedEffect(ruta) {
        val s = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.rutasDao().getSenderistaFlow().first()
        }
        senderistaPhone = s?.telefono ?: ""
        senderistaPeso = s?.peso

        if (effectiveDuracionSegundos <= 0L) {
            val pts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                db.rutasDao().getPuntosForRuta(ruta.id)
            }
            val firstPt = pts.firstOrNull()
            val lastPt = pts.find { it.esFinRuta } ?: pts.lastOrNull()
            if (firstPt != null && lastPt != null && firstPt != lastPt) {
                val format = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                try {
                    val d1 = format.parse(firstPt.fechaHora)
                    val d2 = format.parse(lastPt.fechaHora)
                    if (d1 != null && d2 != null && d2.time > d1.time) {
                        effectiveDuracionSegundos = (d2.time - d1.time) / 1000L
                    }
                } catch (e: Exception) { }
            }
        }
    }

    val isOwnRoute = remember(ruta, senderistaPhone) {
        if (senderistaPhone.isNotEmpty()) {
            ruta.telefono == senderistaPhone || ruta.telefono.isEmpty()
        } else {
            ruta.telefono.isEmpty()
        }
    }

    var isRoutePublic by remember(ruta) { mutableStateOf(ruta.publico) }
    var routeCode by remember(ruta) { mutableStateOf(ruta.codigo) }
    val coroutineScope = rememberCoroutineScope()

    val gpsDistanceMeters = remember(ruta, tracePoints) {
        if (tracePoints.isNotEmpty()) {
            com.gaspar.rutassendero.ui.RouteStatsCalculator.calculateTraceDistance(tracePoints).toDouble()
        } else {
            ruta.distanciaGps
        }
    }

    LaunchedEffect(ruta, gpsDistanceMeters) {
        val distKm = gpsDistanceMeters / 1000.0
        val elevation = ruta.desnivelPositivo
        val categoria = when {
            distKm < 5.0 && elevation < 150.0 -> "Principiante"
            distKm < 12.0 && elevation < 500.0 -> "Intermedio"
            distKm < 25.0 && elevation < 1200.0 -> "Experto"
            else -> "Nivel Dios"
        }
        val randomPhrase = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.rutasDao().getRandomFraseForCategory(categoria)?.frase
        } ?: when (categoria) {
            "Principiante" -> "Es fácil, hasta mi abuelita en pantuflas lo haría."
            "Intermedio" -> "Tienes que entrenar, mi chavo, para poder terminarla."
            "Experto" -> "Lleva suficiente agua y despídete de tus rodillas por una semana."
            else -> "Sólo para cabritos monteses y semidioses del Olimpo. ¡Buena suerte!"
        }
        fraseCinica = randomPhrase
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = warmBg,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Título: Ruta y fecha
                Text(
                    text = "Ruta - ${ruta.fecha}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = textMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Nombre de la ruta
                Text(
                    text = ruta.nombre,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                    textAlign = TextAlign.Center
                )

                // Cambio de privado-público sólo si es del senderista
                if (isOwnRoute) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Switch(
                            checked = isRoutePublic,
                            onCheckedChange = { isChecked ->
                                coroutineScope.launch {
                                    if (!isChecked) { // Quiere cambiar a privado
                                        val count = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val phone = db.rutasDao().getAllSenderistas().firstOrNull()?.telefono ?: ""
                                            db.rutasDao().getPrivateRutasCount(phone)
                                        }
                                        val limit = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            db.rutasDao().getAllSenderistas().firstOrNull()?.maxPrivadas ?: 3
                                        }
                                        if (count >= limit) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                Toast.makeText(context, "Límite alcanzado: Sólo puedes tener un máximo de $limit rutas no públicas.", Toast.LENGTH_LONG).show()
                                            }
                                            return@launch
                                        }
                                        val newCode = if (ruta.codigo.isEmpty()) {
                                            val allowedChars = ('A'..'Z') + ('0'..'9')
                                            (1..5).map { allowedChars.random() }.joinToString("")
                                        } else {
                                            ruta.codigo
                                        }
                                        val updatedRuta = ruta.copy(publico = false, codigo = newCode)
                                        isRoutePublic = false
                                        routeCode = newCode
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            db.rutasDao().insertRuta(updatedRuta)
                                            try {
                                                com.gaspar.rutassendero.sync.CloudSyncManager.syncRoute(context, db, updatedRuta.id) { }
                                            } catch (e: Exception) {
                                                Log.w("RutaDetailDialog", "No se pudo sincronizar privacidad: ${e.message}")
                                            }
                                        }
                                    } else { // Quiere cambiar a público
                                        val updatedRuta = ruta.copy(publico = true)
                                        isRoutePublic = true
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            db.rutasDao().insertRuta(updatedRuta)
                                            try {
                                                com.gaspar.rutassendero.sync.CloudSyncManager.syncRoute(context, db, updatedRuta.id) { }
                                            } catch (e: Exception) {
                                                Log.w("RutaDetailDialog", "No se pudo sincronizar privacidad: ${e.message}")
                                            }
                                        }
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = accentColor,
                                checkedTrackColor = accentColor.copy(alpha = 0.5f),
                                uncheckedThumbColor = textMuted,
                                uncheckedTrackColor = warmBg
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRoutePublic) "Pública" else "Privada",
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isRoutePublic) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(
                                modifier = Modifier
                                    .background(ColorTekinsAlert.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Clave: $routeCode",
                                    color = ColorTekinsAlert,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = textMuted.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // Duración, Distancia 3D, Velocidad y Conectividad
                val durStr = if (effectiveDuracionSegundos > 0L) {
                    val hours = effectiveDuracionSegundos / 3600
                    val minutes = (effectiveDuracionSegundos % 3600) / 60
                    String.format(java.util.Locale.US, "%02d:%02d", hours, minutes)
                } else {
                    "N/A"
                }

                val velPromedioKmh = if (effectiveDuracionSegundos > 0L && gpsDistanceMeters > 0) {
                    (gpsDistanceMeters / 1000.0) / (effectiveDuracionSegundos / 3600.0)
                } else null

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Duración", fontSize = 11.sp, color = textMuted)
                        Text(
                            text = "$durStr hrs",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Dist. 3D", fontSize = 11.sp, color = textMuted)
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f kms", gpsDistanceMeters / 1000.0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Vel. Media", fontSize = 11.sp, color = textMuted)
                        Text(
                            text = velPromedioKmh?.let { String.format(java.util.Locale.US, "%.1f km/h", it) } ?: "--",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Renglón de Conectividad centrado con letras amarillas e icono
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Conectividad",
                        tint = Color(0xFFFACC15),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = String.format(java.util.Locale.US, "Conectividad: %.2f%%", ruta.conectividad),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFACC15)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = textMuted.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(10.dp))

                // Sección Desnivel y Tramos integrados en una sola fila
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Positivo
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Desn. Pos", fontSize = 10.sp, color = Color(0xFF2DD4BF))
                        }
                        Text(String.format(java.util.Locale.US, "%.1f m", ruta.desnivelPositivo), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    // Negativo
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Desn. Neg", fontSize = 10.sp, color = Color(0xFFF43F5E))
                        }
                        Text(String.format(java.util.Locale.US, "%.1f m", ruta.desnivelNegativo), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    // Subida
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Subidas", fontSize = 10.sp, color = Color(0xFF2DD4BF))
                        }
                        Text("${ruta.contadorSubidas}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    // Bajada
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Bajadas", fontSize = 10.sp, color = Color(0xFFF43F5E))
                        }
                        Text("${ruta.contadorBajadas}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = textMuted.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.dp))

                // Dificultad y Color
                val diff = remember(ruta, gpsDistanceMeters) {
                    val distKm = gpsDistanceMeters / 1000.0
                    val elevation = ruta.desnivelPositivo
                    when {
                        distKm < 5.0 && elevation < 150.0 -> {
                            Pair("Principiante", Color(0xFF15803D)) // Soft Green
                        }
                        distKm < 12.0 && elevation < 500.0 -> {
                            Pair("Intermedio", Color(0xFFD97706)) // Soft Amber
                        }
                        distKm < 25.0 && elevation < 1200.0 -> {
                            Pair("Experto", Color(0xFFBE123C)) // Soft Crimson
                        }
                        else -> {
                            Pair("Nivel Dios", Color(0xFF6D28D9)) // Purple
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Valoración de Dificultad",
                        fontSize = 12.sp,
                        color = textMuted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(diff.second)
                            .padding(vertical = 4.dp, horizontal = 12.dp)
                    ) {
                        Text(
                            text = diff.first,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFAF6F0)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"$fraseCinica\"",
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // === NUEVO: Tarjeta de Calorías Estimadas ===
                // Ahora ya pasamos el peso de la base de datos (si el usuario lo capturó, mostrará sus calorías exactas, si no, los rangos)
                CaloriesSummaryCard(
                    distanceKm = gpsDistanceMeters / 1000.0,
                    elevationGainMeters = ruta.desnivelPositivo,
                    userWeightKg = senderistaPeso 
                )

                Spacer(modifier = Modifier.height(16.dp))

                val startDistanceStr = remember(ruta, userLocation) {
                    if (userLocation != null) {
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            userLocation.latitude, userLocation.longitude,
                            ruta.latitudOrig, ruta.longitudOrig,
                            results
                        )
                        val distanceMeters = results[0]
                        if (distanceMeters >= 1000f) {
                            String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000f)
                        } else {
                            "${distanceMeters.toInt()} m"
                        }
                    } else {
                        null
                    }
                }

                if (startDistanceStr != null) {
                    Text(
                        text = "Estás a $startDistanceStr del inicio de la ruta",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Text(
                        text = "Distancia al inicio no disponible (GPS inactivo)",
                        fontSize = 12.sp,
                        color = textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .background(Color(0xFF3B82F6).copy(alpha = 0.1f))
                        .clickable {
                            val gmmIntentUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${ruta.latitudOrig},${ruta.longitudOrig}")
                            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                            try {
                                context.startActivity(mapIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No se pudo abrir Google Maps", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cómo llegar (Google Maps)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B82F6)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botón Cerrar
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CloudRoutesDialog(
    viewModel: RutasViewModel,
    localRoutes: List<RutaEntity>,
    location: Location?,
    onImportSuccess: (RutaEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cloudRoutes by viewModel.cloudRoutes.collectAsState()
    val loading by viewModel.loadingCloudRoutes.collectAsState()
    val syncState by viewModel.cloudSyncState.collectAsState()

    var selectedRouteIdForDownload by remember { mutableStateOf<String?>(null) }
    var selectedRouteNameForDownload by remember { mutableStateOf<String?>(null) }
    var selectedRouteCodeForDownload by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var mostrarCercanas by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }

    val senderista by viewModel.senderistaState.collectAsState()
    val userEmail = senderista?.email ?: ""
    val userTelefono = senderista?.telefono ?: ""

    LaunchedEffect(Unit) {
        viewModel.fetchCloudRoutes()
    }

    // Filtered routes based on search query or 20km radius check
    val filteredRoutes = remember(cloudRoutes, searchQuery, mostrarCercanas, location, localRoutes, userEmail, userTelefono) {
        val query = searchQuery.trim().lowercase()
        var baseList = cloudRoutes

        // 1. Filtrar por búsqueda si hay consulta
        if (query.isNotEmpty()) {
            baseList = baseList.filter { routeMap ->
                val nombre = (routeMap["nombre"] as? String ?: "").lowercase()
                val senderistaNombre = (routeMap["senderista_nombre"] as? String ?: "").lowercase()
                nombre.contains(query) || senderistaNombre.contains(query)
            }
        }

        // 2. Filtrar por distancia si mostrarCercanas es true y tenemos ubicación
        if (mostrarCercanas && location != null) {
            baseList = baseList.filter { routeMap ->
                val lat = routeMap["latitud_orig"] as? Double ?: 0.0
                val lng = routeMap["longitud_orig"] as? Double ?: 0.0
                val rLoc = Location("").apply {
                    latitude = lat
                    longitude = lng
                }
                location.distanceTo(rLoc) <= 20000.0 // 20 km
            }
        }

        // 3. Mostrar solo rutas de otros senderistas
        baseList.filter { routeMap ->
            val routePhone = routeMap["telefono_creador"] as? String ?: ""
            val routeEmail = routeMap["email_creador"] as? String ?: ""
            val isOwn = (userEmail.isNotBlank() && routeEmail.equals(userEmail, ignoreCase = true)) ||
                        (userTelefono.isNotBlank() && matchPhones(routePhone, userTelefono))
            !isOwn
        }
    }

    // Reset page on search query or filter change
    LaunchedEffect(searchQuery, mostrarCercanas) {
        currentPage = 0
    }

    val pageSize = 5
    val totalPages = (filteredRoutes.size + pageSize - 1) / pageSize
    val displayedRoutes = remember(filteredRoutes, currentPage) {
        val startIdx = currentPage * pageSize
        val endIdx = minOf(startIdx + pageSize, filteredRoutes.size)
        if (startIdx < filteredRoutes.size) {
            filteredRoutes.subList(startIdx, endIdx)
        } else {
            emptyList()
        }
    }

    Dialog(onDismissRequest = {
        if (syncState !is CloudSyncState.Syncing && syncState !is CloudSyncState.CheckingConnection) {
            onDismiss()
        }
    }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(550.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Rutas Disponibles",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextoPrincipal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Barra de búsqueda
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Buscar por ruta o senderista", fontSize = 12.sp, color = ColorTextoSecundario) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = ColorTextoPrincipal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ColorTekinsAccent,
                        unfocusedBorderColor = ColorTextoSecundario,
                        focusedLabelColor = ColorTekinsAccent,
                        unfocusedLabelColor = ColorTextoSecundario,
                        focusedTextColor = ColorTextoPrincipal,
                        unfocusedTextColor = ColorTextoPrincipal,
                        focusedLeadingIconColor = ColorTekinsAccent,
                        unfocusedLeadingIconColor = ColorTextoSecundario,
                        focusedTrailingIconColor = ColorTekinsAccent,
                        unfocusedTrailingIconColor = ColorTextoSecundario
                    ),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp), tint = ColorTextoSecundario) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", modifier = Modifier.size(20.dp), tint = ColorTextoSecundario)
                            }
                        }
                    }
                )

                // Parametrización: Mostrar rutas cercanas (default false)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = mostrarCercanas,
                        onCheckedChange = { mostrarCercanas = it },
                        colors = CheckboxDefaults.colors(checkedColor = ColorTekinsAccent)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Mostrar rutas cercanas (< 20 km)",
                        color = ColorTextoPrincipal,
                        fontSize = 13.sp
                    )
                }

                if (loading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ColorTekinsAccent)
                    }
                } else if (filteredRoutes.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val noRoutesText = if (searchQuery.isEmpty() && mostrarCercanas && location != null) {
                            "No se encontraron rutas dentro de 20 km."
                        } else {
                            "No se encontraron coincidencias en la nube."
                        }
                        Text(
                            text = noRoutesText,
                            color = ColorTextoSecundario,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        displayedRoutes.forEach { routeMap ->
                            val id = routeMap["id"] as String
                            val nombre = routeMap["nombre"] as String
                            val fecha = routeMap["fecha"] as String
                            val descripcion = routeMap["descripcion"] as String
                            val senderistaNombre = routeMap["senderista_nombre"] as? String ?: "Anónimo"
                            val emailCreador = routeMap["email_creador"] as? String ?: ""
                            val gpsDist = routeMap["distancia_gps"] as? Double ?: 0.0
                            val isOwnRoute = userEmail.isNotEmpty() && emailCreador.equals(userEmail, ignoreCase = true)
                            val existsLocally = localRoutes.any { it.id == id }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (existsLocally) {
                                            val localRoute = localRoutes.firstOrNull { it.id == id }
                                            if (localRoute != null) {
                                                onImportSuccess(localRoute)
                                            }
                                        } else {
                                            selectedRouteIdForDownload = id
                                            selectedRouteNameForDownload = nombre
                                            val isRoutePublic = routeMap["publico"] as? Boolean ?: false
                                            val correctCodigo = routeMap["codigo"] as? String ?: ""

                                            if (!isRoutePublic) {
                                                selectedRouteCodeForDownload = correctCodigo
                                                showPasswordDialog = true
                                            } else {
                                                viewModel.downloadRouteFromCloud(
                                                    context = context,
                                                    routeId = id,
                                                    onSuccess = {
                                                        viewModel.resetCloudSyncState()
                                                        kotlinx.coroutines.MainScope().launch {
                                                            val db = RutasDatabase.getDatabase(context)
                                                            val downloaded = db.rutasDao().getRutaById(id)
                                                            if (downloaded != null) {
                                                                onImportSuccess(downloaded)
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = nombre,
                                            fontWeight = FontWeight.Bold,
                                            color = ColorTextoPrincipal
                                        )
                                        if (isOwnRoute) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(ColorTekinsAccent.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "MI RUTA",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = ColorTekinsAccent
                                                )
                                            }
                                        }
                                        val isRoutePublic = routeMap["publico"] as? Boolean ?: false
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isRoutePublic) ColorTekinsSuccess.copy(alpha = 0.2f) else ColorTekinsAlert.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (isRoutePublic) "PÚBLICA" else "PRIVADA",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isRoutePublic) ColorTekinsSuccess else ColorTekinsAlert
                                            )
                                        }
                                    }
                                    val dateParts = fecha.split("/")
                                    val dateFormatted = if (dateParts.size == 3) {
                                        val day = dateParts[0]
                                        val month = dateParts[1]
                                        val year = dateParts[2]
                                        val shortYear = if (year.length == 4) year.substring(2) else year
                                        "$day/$month/$shortYear"
                                    } else {
                                        fecha
                                    }
                                    val distKm = gpsDist / 1000.0
                                    val distStrFormatted = String.format(java.util.Locale("es", "ES"), "%.2f", distKm)

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "autor: $senderistaNombre, $dateFormatted, $distStrFormatted kms",
                                        fontSize = 11.sp,
                                        color = ColorTextoSecundario
                                    )
                                }
                                if (existsLocally) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionar",
                                        tint = ColorTekinsSuccess,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = "Descargar",
                                        tint = ColorTekinsAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Divider(color = ColorTekinsCardMuted)
                        }
                    }

                    // Botones de navegación para paginación
                    if (totalPages > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                enabled = currentPage > 0
                            ) {
                                Text("Anterior", fontSize = 12.sp)
                            }
                            Text(
                                text = "Pág. ${currentPage + 1} de $totalPages",
                                fontSize = 12.sp,
                                color = ColorTextoSecundario
                            )
                            TextButton(
                                onClick = { if (currentPage < totalPages - 1) currentPage++ },
                                enabled = currentPage < totalPages - 1
                            ) {
                                Text("Siguiente", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = syncState !is CloudSyncState.Syncing && syncState !is CloudSyncState.CheckingConnection
                    ) {
                        Text("Cerrar", color = ColorTextoSecundario)
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        var typedCode by remember { mutableStateOf("") }
        var codeError by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { showPasswordDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ColorTekinsCardBg,
                border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ruta Privada",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorTextoPrincipal
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Introduce el código de acceso para descargar la ruta \"$selectedRouteNameForDownload\":",
                        fontSize = 13.sp,
                        color = ColorTextoSecundario,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = typedCode,
                        onValueChange = {
                            typedCode = it
                            codeError = false
                        },
                        label = { Text("Código de Acceso") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorTekinsAccent,
                            unfocusedBorderColor = ColorTextoSecundario,
                            focusedLabelColor = ColorTekinsAccent,
                            unfocusedLabelColor = ColorTextoSecundario,
                            focusedTextColor = ColorTextoPrincipal,
                            unfocusedTextColor = ColorTextoPrincipal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (codeError) {
                        Text(
                            text = "Código incorrecto. Inténtalo de nuevo.",
                            color = ColorTekinsAlert,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showPasswordDialog = false }) {
                            Text("Cancelar", color = ColorTextoSecundario)
                        }
                        Button(
                            onClick = {
                                if (typedCode == selectedRouteCodeForDownload) {
                                    showPasswordDialog = false
                                    selectedRouteIdForDownload?.let { routeId ->
                                        viewModel.downloadRouteFromCloud(
                                            context = context,
                                            routeId = routeId,
                                            onSuccess = {
                                                viewModel.resetCloudSyncState()
                                                kotlinx.coroutines.MainScope().launch {
                                                    val db = RutasDatabase.getDatabase(context)
                                                    val downloaded = db.rutasDao().getRutaById(routeId)
                                                    if (downloaded != null) {
                                                        onImportSuccess(downloaded)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    codeError = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent)
                        ) {
                            Text("Aceptar", color = ColorTekinsDarkBg)
                        }
                    }
                }
            }
        }
    }

    // Mostrar el estado de la descarga
    if (selectedRouteIdForDownload != null && syncState !is CloudSyncState.Idle) {
        CloudDownloadDialog(
            viewModel = viewModel,
            routeId = selectedRouteIdForDownload!!,
            routeName = selectedRouteNameForDownload ?: "",
            onDismiss = {
                viewModel.resetCloudSyncState()
                selectedRouteIdForDownload = null
                selectedRouteNameForDownload = null
            }
        )
    }
}

@Composable
fun CloudDownloadDialog(
    viewModel: RutasViewModel,
    routeId: String,
    routeName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val syncState by viewModel.cloudSyncState.collectAsState()

    Dialog(
        onDismissRequest = {
            if (syncState !is CloudSyncState.Syncing && syncState !is CloudSyncState.CheckingConnection) {
                onDismiss()
            }
        }
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ColorTekinsCardBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (syncState !is CloudSyncState.Success) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Descargando...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ColorTextoPrincipal
                        )
                        IconButton(
                            onClick = onDismiss,
                            enabled = syncState !is CloudSyncState.Syncing && syncState !is CloudSyncState.CheckingConnection
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = ColorTextoSecundario)
                        }
                    }

                    Text(
                        text = "Ruta: $routeName",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorTekinsAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    Divider(color = ColorTekinsCardMuted, modifier = Modifier.padding(bottom = 16.dp))
                }

                when (val state = syncState) {
                    is CloudSyncState.Idle -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = ColorTekinsAccent,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "¿Deseas descargar los puntos y traza de esta ruta desde PostgreSQL en la nube?",
                                color = ColorTextoPrincipal,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.downloadRouteFromCloud(context, routeId) },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("DESCARGAR DE LA NUBE", color = ColorTekinsDarkBg, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    is CloudSyncState.CheckingConnection -> {
                        CircularProgressIndicator(color = ColorTekinsAccent)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Estableciendo conexión segura...", color = ColorTextoPrincipal, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    is CloudSyncState.Syncing -> {
                        CircularProgressIndicator(color = ColorTekinsAccent)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(state.progress, color = ColorTextoPrincipal, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    is CloudSyncState.Success -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ColorTekinsSuccess, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Descarga Completada", color = ColorTekinsSuccess, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Aceptar", color = Color.White)
                        }
                    }
                    is CloudSyncState.Error -> {
                        Icon(Icons.Default.Error, contentDescription = null, tint = ColorTekinsAlert, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(state.message, color = ColorTekinsAlert, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsCardMuted),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cerrar", color = ColorTekinsAlert)
                        }
                    }
                }
            }
        }
    }
}

// =================================================================================
// COMPONENTES DE SALIDAS (OUTINGS / INVITACIONES A SALIR)
// =================================================================================

@Composable
fun SalidasDialog(
    viewModel: RutasViewModel,
    onSelectRoute: (RutaEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    val availableOutings by viewModel.availableOutings.collectAsState()
    val loadingAvailable by viewModel.loadingAvailableOutings.collectAsState()
    val acceptedOutings by viewModel.invitacionesAceptadas.collectAsState()
    val progressMessage by viewModel.outingsProgress.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Disponibles, 1: Mis Salidas
    var searchQuery by remember { mutableStateOf("") }
    
    var selectedOutingForDetail by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selectedMyOutingForDetail by remember { mutableStateOf<InvitacionAceptadaEntity?>(null) }
    
    // Registrar cooperación states
    var showCooperationDialog by remember { mutableStateOf<InvitacionAceptadaEntity?>(null) }
    var coopAmountInput by remember { mutableStateOf("") }
    
    // Confirmación de cancelación state
    var outingToCancelConfirm by remember { mutableStateOf<InvitacionAceptadaEntity?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var localLoadingMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchAvailableOutings()
    }

    Dialog(
        onDismissRequest = {
            if (progressMessage == null && localLoadingMessage == null) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = ColorTekinsDarkBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Cabecera
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Gestión de Salidas",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ColorTextoPrincipal
                        )
                        IconButton(onClick = onDismiss, enabled = progressMessage == null) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = ColorTextoSecundario)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pestañas (Disponibles vs Mis Salidas)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(ColorTekinsCardBg, RoundedCornerShape(12.dp))
                            .padding(4.dp)
                    ) {
                        TabButton(
                            text = "Disponibles",
                            isSelected = activeTab == 0,
                            modifier = Modifier.weight(1f)
                        ) {
                            activeTab = 0
                        }
                        TabButton(
                            text = "Mis Salidas",
                            isSelected = activeTab == 1,
                            modifier = Modifier.weight(1f)
                        ) {
                            activeTab = 1
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (activeTab == 0) {
                        // TAB DISPONIBLES
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                label = { Text("Buscar ruta o creador...") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ColorTekinsAccent,
                                    unfocusedBorderColor = ColorTextoSecundario,
                                    focusedLabelColor = ColorTekinsAccent,
                                    unfocusedLabelColor = ColorTextoSecundario,
                                    focusedTextColor = ColorTextoPrincipal,
                                    unfocusedTextColor = ColorTextoPrincipal
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.fetchAvailableOutings() },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = ColorTekinsCardBg)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = ColorTekinsAccent)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (loadingAvailable) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = ColorTekinsAccent)
                            }
                        } else {
                            val filtered = availableOutings.filter { map ->
                                val rName = (map["ruta_nombre"] as? String ?: "").lowercase()
                                val inst = (map["instrucciones"] as? String ?: "").lowercase()
                                val query = searchQuery.lowercase()
                                rName.contains(query) || inst.contains(query)
                            }

                            if (filtered.isEmpty()) {
                                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text("No hay salidas disponibles", color = ColorTextoSecundario, fontSize = 14.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(filtered) { outing ->
                                        OutingItemRow(outing = outing) {
                                            selectedOutingForDetail = outing
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // TAB MIS SALIDAS
                        if (acceptedOutings.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("Aún no has aceptado ninguna salida", color = ColorTextoSecundario, fontSize = 14.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(acceptedOutings) { outingEntity ->
                                    MyOutingItemRow(outing = outingEntity) {
                                        viewModel.verificarExistenciaInvitacion(
                                            idInvitacion = outingEntity.idInvitacion,
                                            onDeleted = {
                                                Toast.makeText(context, "La salida ya no existe en el servidor. Se eliminó localmente.", Toast.LENGTH_LONG).show()
                                            },
                                            onExists = {
                                                selectedMyOutingForDetail = outingEntity
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Cargando overlay
                val displayLoadingMessage = progressMessage ?: localLoadingMessage
                if (displayLoadingMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ColorTekinsCardBg),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, ColorTekinsAccent),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = ColorTekinsAccent)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(displayLoadingMessage, color = ColorTextoPrincipal, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }


    // Dialogo de Detalle de Salida Disponible (Optimizado para caber en una pantalla)
    if (selectedOutingForDetail != null) {
        val o = selectedOutingForDetail!!
        val rutaNombre = o["ruta_nombre"] as? String ?: "Ruta sin nombre"
        val fechaSalida = o["fecha_hora_salida"] as? String ?: ""
        val puntoReunion = o["punto_reunion"] as? String ?: ""
        val linkMaps = o["link_google_maps"] as? String ?: ""
        val coopMin = o["cooperacion_minima"] as? Double ?: 0.0
        val nivel = o["nivel"] as? String ?: "No especificado"
        val instrucciones = o["instrucciones"] as? String ?: ""
        val distVal = o["distancia_gps_val"] as? Double ?: -1.0
        val valueText = "Ninguna"

        Dialog(onDismissRequest = { selectedOutingForDetail = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ColorTekinsCardBg,
                border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Detalle de la Salida",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorTekinsAccent,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val lat = o["latitud"] as? Double ?: 0.0
                    val lng = o["longitud"] as? Double ?: 0.0
                    val tipoRuta = o["tipo_ruta"] as? String ?: "S"
                    val comentario = o["comentario"] as? String

                    // Link to Google Maps below Punto de Reunión
                    val mapsUrl = if (!linkMaps.isNullOrBlank()) {
                        linkMaps
                    } else if (lat != 0.0 && lng != 0.0) {
                        "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
                    } else {
                        "https://www.google.com/maps/search/?api=1&query=${Uri.encode(puntoReunion)}"
                    }

                    if (tipoRuta == "E") {
                        EventoDetailContent(
                            agrupacion = valueText,
                            idRuta = rutaNombre,
                            fechaHoraSalida = fechaSalida,
                            instruccionesHtml = instrucciones,
                            puntoReunion = puntoReunion,
                            mapsUrl = mapsUrl,
                            comentario = comentario ?: "",
                            coopMinima = coopMin,
                            colorAcento = ColorTekinsAccent,
                            colorTextoPrincipal = ColorTextoPrincipal
                        )
                    } else {
                        DetailField(label = "Agrupación", value = valueText)
                        val displayTipoRuta = "Ruta"
                        DetailField(label = "Tipo", value = displayTipoRuta)
                        DetailField(label = "Nombre", value = rutaNombre)
                        if (!comentario.isNullOrBlank()) {
                            DetailField(label = "Comentario", value = comentario)
                        }
                        DetailField(label = "Fecha y Hora", value = fechaSalida)
                        DetailField(label = "Dificultad (Nivel)", value = nivel)
                        DetailField(label = "Punto de Reunión", value = puntoReunion)
                        
                        Text(
                            text = "ve mapa punto de reunion",
                            color = ColorTekinsAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No se pudo abrir el mapa.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )

                        if (distVal >= 0.0) {
                            DetailField(label = "Distancia al punto", value = String.format(Locale.getDefault(), "%.1f Kms", distVal))
                        }
                        
                        if (coopMin > 0.0) {
                            DetailField(
                                label = "Cooperación Mínima", 
                                value = "$$coopMin"
                            )
                        }
                    }
                    
                    // Características de la Ruta
                    val conectividadPct = (o["conectividad"] as? Number)?.toFloat() ?: 0.0f
                    val distGps = (o["distancia_gps"] as? Number)?.toDouble() ?: 0.0
                    val desnPositivo = (o["desnivel_positivo"] as? Number)?.toDouble() ?: 0.0
                    val desnNegativo = (o["desnivel_negativo"] as? Number)?.toDouble() ?: 0.0
                    val contSubidas = (o["contador_subidas"] as? Number)?.toInt() ?: 0
                    val contBajadas = (o["contador_bajadas"] as? Number)?.toInt() ?: 0
                    val duracionSegundos = (o["duracion_segundos"] as? Number)?.toLong() ?: 0L

                    val durStr = if (duracionSegundos > 0L) {
                        val hours = duracionSegundos / 3600
                        val minutes = (duracionSegundos % 3600) / 60
                        String.format(java.util.Locale.US, "%02d:%02d", hours, minutes)
                    } else {
                        "N/A"
                    }

                    if (tipoRuta != "E") {
                        Text(
                            text = "Características de la Ruta",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTekinsAccent,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        DetailField(label = "Porcentaje de Cobertura", value = String.format(Locale.getDefault(), "%.2f %%", conectividadPct))
                        DetailField(label = "Distancia", value = String.format(Locale.getDefault(), "%.2f km", distGps / 1000.0))
                        DetailField(label = "Duración *Hh:MM'", value = durStr)
                        DetailField(label = "Desniveles (+ / -)", value = String.format(Locale.getDefault(), "+%.1f m / -%.1f m", desnPositivo, desnNegativo))
                        DetailField(label = "Veces que fue + / -", value = String.format(Locale.getDefault(), "+%d / -%d", contSubidas, contBajadas))

                        if (instrucciones.isNotEmpty()) {
                            DetailField(
                                label = "Requerimientos", 
                                value = instrucciones,
                                valueFontSize = 12.sp,
                                valueFontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { selectedOutingForDetail = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("VOLVER", color = ColorTextoSecundario)
                        }
                        
                        Button(
                            onClick = {
                                if (coopMin > 0.0) {
                                    Toast.makeText(
                                        context,
                                        "Salida aceptada. Requiere cooperación mínima de $$coopMin. Avísanos cuando la realices.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                viewModel.acceptOuting(
                                    context = context,
                                    outing = o,
                                    onSuccess = {
                                        Toast.makeText(context, "Salida aceptada y agendada con éxito.", Toast.LENGTH_SHORT).show()
                                        selectedOutingForDetail = null
                                    },
                                    onError = { err ->
                                        Toast.makeText(context, "Error: $err", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent),
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ACEPTAR", color = ColorTekinsDarkBg, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Dialogo de Detalle de Salida Aceptada (Mi Salida)
    if (selectedMyOutingForDetail != null) {
        val o = selectedMyOutingForDetail!!
        
        val valueText = "Ninguna"
        
        Dialog(onDismissRequest = { selectedMyOutingForDetail = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = ColorTekinsCardBg,
                border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Mi Salida Aceptada",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorTekinsAccent,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Link to Google Maps below Punto de Reunión
                    val lat = o.latitud ?: 0.0
                    val lng = o.longitud ?: 0.0
                    val mapsUrl = if (!o.linkGoogleMaps.isNullOrEmpty()) {
                        o.linkGoogleMaps
                    } else if (lat != 0.0 && lng != 0.0) {
                        "https://www.google.com/maps/search/?api=1&query=$lat,$lng"
                    } else {
                        "https://www.google.com/maps/search/?api=1&query=${Uri.encode(o.puntoReunion)}"
                    }

                    if (o.tipoRuta == "E") {
                        EventoDetailContent(
                            agrupacion = valueText,
                            idRuta = o.nombreRuta,
                            fechaHoraSalida = o.fechaHoraSalida,
                            instruccionesHtml = o.instrucciones ?: "",
                            puntoReunion = o.puntoReunion,
                            mapsUrl = mapsUrl,
                            comentario = o.comentario ?: "",
                            coopMinima = o.cooperacionMinima,
                            colorAcento = ColorTekinsAccent,
                            colorTextoPrincipal = ColorTextoPrincipal
                        )
                        if (o.cooperacionMinima > 0.0) {
                            val real = o.cooperacionRealizada
                            val estado = if (real != null && real >= o.cooperacionMinima) {
                                "Realizada: $$real (Sincronizado: ${if (o.sincronizadoConNube) "Sí" else "Pendiente"})"
                            } else {
                                "Pendiente"
                            }
                            DetailField(label = "Estado de Cooperación", value = estado)
                        }
                    } else {
                        DetailField(label = "Agrupación", value = valueText)
                        val displayTipoRuta = "Ruta"
                        DetailField(label = "Tipo", value = displayTipoRuta)
                        DetailField(label = "Nombre", value = o.nombreRuta)
                        if (!o.comentario.isNullOrBlank()) {
                            DetailField(label = "Comentario", value = o.comentario)
                        }
                        DetailField(label = "Fecha y Hora", value = o.fechaHoraSalida)
                        DetailField(label = "Dificultad (Nivel)", value = o.nivel ?: "No especificado")
                        DetailField(label = "Punto de Reunión", value = o.puntoReunion)

                        Text(
                            text = "ve mapa punto de reunion",
                            color = ColorTekinsAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .clickable {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No se pudo abrir el mapa.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )
                        
                        if (o.cooperacionMinima > 0.0) {
                            DetailField(
                                label = "Cooperación Mínima", 
                                value = "$${o.cooperacionMinima}"
                            )
                            
                            val real = o.cooperacionRealizada
                            val estado = if (real != null && real >= o.cooperacionMinima) {
                                "Realizada: $$real (Sincronizado: ${if (o.sincronizadoConNube) "Sí" else "Pendiente"})"
                            } else {
                                "Pendiente"
                            }
                            DetailField(label = "Estado de Cooperación", value = estado)
                        }
                    }

                    // Características de la Ruta
                    val conectividadPct = o.conectividad ?: 0.0f
                    val distGps = o.distanciaGps ?: 0.0
                    val desnPositivo = o.desnivelPositivo ?: 0.0
                    val desnNegativo = o.desnivelNegativo ?: 0.0
                    val contSubidas = o.contadorSubidas ?: 0
                    val contBajadas = o.contadorBajadas ?: 0
                    val duracionSegundos = o.duracionSegundos ?: 0L

                    val durStr = if (duracionSegundos > 0L) {
                        val hours = duracionSegundos / 3600
                        val minutes = (duracionSegundos % 3600) / 60
                        String.format(java.util.Locale.US, "%02d:%02d", hours, minutes)
                    } else {
                        "N/A"
                    }

                    if (o.tipoRuta != "E") {
                        Text(
                            text = "Características de la Ruta",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTekinsAccent,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                        DetailField(label = "Porcentaje de Cobertura", value = String.format(Locale.getDefault(), "%.2f %%", conectividadPct))
                        DetailField(label = "Distancia", value = String.format(Locale.getDefault(), "%.2f km", distGps / 1000.0))
                        DetailField(label = "Duración *Hh:MM'", value = durStr)
                        DetailField(label = "Desniveles (+ / -)", value = String.format(Locale.getDefault(), "+%.1f m / -%.1f m", desnPositivo, desnNegativo))
                        DetailField(label = "Veces que fue + / -", value = String.format(Locale.getDefault(), "+%d / -%d", contSubidas, contBajadas))

                        if (!o.instrucciones.isNullOrEmpty()) {
                            DetailField(
                                label = "Requerimientos",
                                value = o.instrucciones,
                                valueFontSize = 12.sp,
                                valueFontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (o.idInvitacion > 0 && !o.tipoRuta.equals("E", ignoreCase = true)) {
                            Button(
                                onClick = {
                                    val idRuta = o.idRuta
                                    coroutineScope.launch {
                                        localLoadingMessage = "Cargando ruta..."
                                        try {
                                            var routeEntity = viewModel.db.rutasDao().getRutaById(idRuta)
                                            if (routeEntity == null) {
                                                localLoadingMessage = "Descargando datos de ruta..."
                                                com.gaspar.rutassendero.sync.CloudSyncManager.downloadRoute(context, viewModel.db, idRuta) { progress ->
                                                    localLoadingMessage = progress
                                                }
                                                routeEntity = viewModel.db.rutasDao().getRutaById(idRuta)
                                            }
                                            if (routeEntity != null) {
                                                onSelectRoute(routeEntity)
                                                selectedMyOutingForDetail = null
                                                onDismiss() // Cierra SalidasDialog
                                            } else {
                                                Toast.makeText(context, "No se pudo encontrar la ruta.", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            localLoadingMessage = null
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CONSULTA RUTA", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        val yaPago = o.comprobantePago != null || (o.cooperacionRealizada != null && o.cooperacionRealizada > 0.0)
                        if (o.cooperacionMinima > 0.0) {
                            Button(
                                onClick = {
                                    coopAmountInput = o.cooperacionMinima.toString()
                                    showCooperationDialog = o
                                    selectedMyOutingForDetail = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, tint = ColorTekinsDarkBg)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (yaPago) "VER COOPERACIÓN" else "REGISTRAR COOPERACIÓN", color = ColorTekinsDarkBg, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!yaPago) {
                                Button(
                                    onClick = {
                                        outingToCancelConfirm = o
                                        selectedMyOutingForDetail = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAlert),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("CANCELAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                            
                            TextButton(
                                onClick = { selectedMyOutingForDetail = null },
                                modifier = Modifier.weight(0.6f)
                            ) {
                                Text("CERRAR", color = ColorTextoSecundario)
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo para registrar cooperación
    val localCoop = showCooperationDialog
    if (localCoop != null) {
        val yaRegistrado = localCoop.comprobantePago != null || (localCoop.cooperacionRealizada != null && localCoop.cooperacionRealizada > 0.0)

        if (yaRegistrado) {
            // Intentar resincronizar si quedó pendiente
            androidx.compose.runtime.LaunchedEffect(localCoop.idInvitacion) {
                if (!localCoop.sincronizadoConNube) {
                    viewModel.reintentarSincronizarCooperacion(
                        idInvitacion = localCoop.idInvitacion,
                        onSuccess = {
                            android.widget.Toast.makeText(context, "Sincronización en la nube exitosa.", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            android.widget.Toast.makeText(context, "No se pudo subir: $err", android.widget.Toast.LENGTH_LONG).show()
                            try {
                                val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                                val logFile = java.io.File(downloadsDir, "error_sync_cooperacion.txt")
                                logFile.appendText("Error en sincronización:\n$err\n\n")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                }
            }

            // VERSIÓN SOLO LECTURA
            Dialog(onDismissRequest = { showCooperationDialog = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ColorTekinsCardBg,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cooperación Registrada", color = ColorTekinsSuccess, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Monto aportado: $${localCoop.cooperacionRealizada ?: 0.0}", color = ColorTextoPrincipal, fontSize = 14.sp)
                        Text("Fecha: ${localCoop.fechaSubidaComprobante ?: localCoop.fechaCooperacionRealizada ?: "Desconocida"}", color = ColorTextoSecundario, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (localCoop.comprobantePago != null) {
                            val bitmap = remember(localCoop.comprobantePago) {
                                BitmapFactory.decodeByteArray(localCoop.comprobantePago, 0, localCoop.comprobantePago.size)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Comprobante de pago",
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Button(
                                    onClick = {
                                        try {
                                            val file = java.io.File(context.cacheDir, "comprobante_pago_${localCoop.idInvitacion}.pdf")
                                            file.writeBytes(localCoop.comprobantePago)
                                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.gaspar.rutassendero.fileprovider", file)
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "application/pdf")
                                                flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            }
                                            context.startActivity(android.content.Intent.createChooser(intent, "Abrir comprobante PDF"))
                                        } catch (e: android.content.ActivityNotFoundException) {
                                            android.widget.Toast.makeText(context, "No tienes una aplicación instalada para abrir archivos PDF.", android.widget.Toast.LENGTH_LONG).show()
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "No se pudo abrir el PDF: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = ColorTekinsDarkBg)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("VER PDF ADJUNTADO", color = ColorTekinsDarkBg, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text("No se adjuntó comprobante en este registro.", color = ColorTextoSecundario, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                viewModel.reintentarSincronizarCooperacion(
                                    idInvitacion = localCoop.idInvitacion,
                                    onSuccess = {
                                        android.widget.Toast.makeText(context, "Comprobante reenviado con éxito a la nube.", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        android.widget.Toast.makeText(context, "Error al reenviar: $err", android.widget.Toast.LENGTH_LONG).show()
                                        try {
                                            val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                                            val logFile = java.io.File(downloadsDir, "error_sync_cooperacion.txt")
                                            logFile.appendText("Error forzado:\n$err\n\n")
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("REENVIAR COMPROBANTE", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = { showCooperationDialog = null },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary)
                            ) {
                                Text("Cerrar", color = Color.White)
                            }
                        }
                    }
                }
            }
        } else {
            // VERSIÓN PARA REGISTRAR (OBLIGATORIO)
            var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var selectedFileName by remember { mutableStateOf<String?>(null) }
            var fileError by remember { mutableStateOf<String?>(null) }

            val filePickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: android.net.Uri? ->
                uri?.let {
                    val cursor = context.contentResolver.query(it, null, null, null, null)
                    cursor?.use { c ->
                        val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && c.moveToFirst()) {
                            selectedFileName = c.getString(nameIndex)
                        }
                    }
                    val mimeType = context.contentResolver.getType(it)
                    val fileSize = context.contentResolver.openFileDescriptor(it, "r")?.statSize ?: 0
                    if (mimeType == "application/pdf" && fileSize > 1.5 * 1024 * 1024) {
                        fileError = "El PDF es demasiado grande. Límite: 1.5 MB"
                        selectedFileUri = null
                    } else {
                        fileError = null
                        selectedFileUri = it
                    }
                }
            }

            Dialog(onDismissRequest = { showCooperationDialog = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ColorTekinsCardBg,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Registrar Pago de Cooperación", color = ColorTextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Introduce el monto aportado (Mínimo sugerido: $${localCoop.cooperacionMinima}):", color = ColorTextoSecundario, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = coopAmountInput,
                            onValueChange = { coopAmountInput = it },
                            label = { Text("Monto ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorTekinsAccent,
                                unfocusedBorderColor = ColorTextoSecundario,
                                focusedTextColor = ColorTextoPrincipal,
                                unfocusedTextColor = ColorTextoPrincipal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Comprobante (Obligatorio):", color = ColorTextoPrincipal, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { filePickerLauncher.launch("*/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsCardMuted)
                            ) {
                                Text("Adjuntar", color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedFileName ?: "Sin archivo",
                                color = ColorTextoSecundario,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (fileError != null) {
                            Text(text = fileError!!, color = Color.Red, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showCooperationDialog = null }) {
                                Text("Cancelar", color = ColorTextoSecundario)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (selectedFileUri == null) {
                                        android.widget.Toast.makeText(context, "Es obligatorio adjuntar un comprobante de pago.", android.widget.Toast.LENGTH_LONG).show()
                                        return@Button
                                    }

                                    val amount = coopAmountInput.toDoubleOrNull() ?: localCoop.cooperacionMinima
                                    
                                    var finalBytes: ByteArray? = null
                                    selectedFileUri?.let { uri ->
                                        val mimeType = context.contentResolver.getType(uri)
                                        context.contentResolver.openInputStream(uri)?.use { stream ->
                                            if (mimeType?.startsWith("image") == true) {
                                                val bitmap = BitmapFactory.decodeStream(stream)
                                                if (bitmap != null) {
                                                    val out = java.io.ByteArrayOutputStream()
                                                    val ratio = kotlin.math.min(800f / bitmap.width, 800f / bitmap.height)
                                                    val width = kotlin.math.round(ratio * bitmap.width).toInt()
                                                    val height = kotlin.math.round(ratio * bitmap.height).toInt()
                                                    val resized = android.graphics.Bitmap.createScaledBitmap(bitmap, width, height, true)
                                                    resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out)
                                                    finalBytes = out.toByteArray()
                                                }
                                            } else {
                                                finalBytes = stream.readBytes()
                                            }
                                        }
                                    }

                                    if (finalBytes == null) {
                                        android.widget.Toast.makeText(context, "Error al procesar el archivo adjunto.", android.widget.Toast.LENGTH_LONG).show()
                                        return@Button
                                    }

                                    viewModel.registrarCooperacion(
                                        localCoop.idInvitacion,
                                        amount,
                                        comprobanteBytes = finalBytes,
                                        onSuccess = {
                                            android.widget.Toast.makeText(context, "Cooperación registrada con éxito.", android.widget.Toast.LENGTH_SHORT).show()
                                            showCooperationDialog = null
                                        },
                                        onError = { err ->
                                            android.widget.Toast.makeText(context, "Error: $err", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsPrimary)
                            ) {
                                Text("Guardar", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    // Diálogo de Confirmación para Cancelar Asistencia
    val localCancel = outingToCancelConfirm
    if (localCancel != null) {
        AlertDialog(
            onDismissRequest = { outingToCancelConfirm = null },
            containerColor = ColorTekinsCardBg,
            titleContentColor = ColorTextoPrincipal,
            textContentColor = ColorTextoSecundario,
            title = { Text("Cancelar Asistencia", color = ColorTextoPrincipal) },
            text = { Text("¿Seguro que deseas cancelar tu asistencia a la salida de la ruta '${localCancel.nombreRuta}'? Esto también eliminará tu participación en el servidor.", color = ColorTextoSecundario) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelarAsistencia(
                            localCancel.idInvitacion,
                            onSuccess = {
                                Toast.makeText(context, "Asistencia cancelada.", Toast.LENGTH_SHORT).show()
                                outingToCancelConfirm = null
                            },
                            onError = { err ->
                                Toast.makeText(context, "Error: $err", Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAlert)
                ) {
                    Text("SÍ, CANCELAR", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { outingToCancelConfirm = null }) {
                    Text("VOLVER", color = ColorTextoSecundario)
                }
            }
        )
    }
}

@Composable
fun TabButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) ColorTekinsAccent else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) ColorTekinsDarkBg else ColorTextoSecundario,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun OutingItemRow(
    outing: Map<String, Any>,
    onClick: () -> Unit
) {
    val rName = outing["ruta_nombre"] as? String ?: "Ruta sin nombre"
    val fecha = outing["fecha_hora_salida"] as? String ?: ""
    val nivel = outing["nivel"] as? String ?: "No especificado"

    val dist = outing["distancia_gps_val"] as? Double ?: -1.0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = ColorTekinsCardBg),
        border = BorderStroke(1.dp, ColorTekinsCardMuted),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = rName, color = ColorTextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                val tipoRuta = outing["tipo_ruta"] as? String ?: "S"
                val displayNivel = if (tipoRuta == "E") "Evento" else nivel
                Badge(containerColor = ColorTekinsAccent.copy(alpha = 0.2f)) {
                    Text(
                        text = displayNivel, 
                        color = ColorTekinsAccent, 
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Bold, 
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(text = "Fecha: $fecha", color = ColorTextoSecundario, fontSize = 12.sp)
            
            if (dist >= 0.0) {
                Text(
                    text = String.format(Locale.getDefault(), "A %.1f Kms de ti", dist),
                    color = ColorTekinsAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun MyOutingItemRow(
    outing: InvitacionAceptadaEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = ColorTekinsCardBg),
        border = BorderStroke(1.dp, ColorTekinsCardMuted),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = outing.nombreRuta, color = ColorTextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                val statusColor = if (outing.sincronizadoConNube) ColorTekinsSuccess else ColorTekinsAlert
                Badge(containerColor = statusColor.copy(alpha = 0.2f)) {
                    Text(
                        text = if (outing.sincronizadoConNube) "Sincronizado (${outing.tipoRuta})" else "Pendiente (${outing.tipoRuta})",
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(text = "Fecha: ${outing.fechaHoraSalida}", color = ColorTextoSecundario, fontSize = 12.sp)
            Text(text = "Punto de Reunión: ${outing.puntoReunion}", color = ColorTextoSecundario, fontSize = 12.sp, maxLines = 1)
            
            if (outing.cooperacionMinima > 0.0) {
                val real = outing.cooperacionRealizada
                val text = if (real != null) "Cooperación realizada: $$real" else "Cooperación pendiente: $${outing.cooperacionMinima}"
                Text(
                    text = text,
                    color = if (real != null) ColorTekinsSuccess else ColorTekinsAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DetailField(
    label: String,
    value: String,
    isLink: Boolean = false,
    valueFontSize: TextUnit = 14.sp,
    valueFontWeight: FontWeight = FontWeight.Normal
) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = ColorTextoSecundario, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (isLink) {
            Text(
                text = value,
                color = ColorTekinsAccent,
                fontSize = valueFontSize,
                fontWeight = valueFontWeight,
                modifier = Modifier
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se pudo abrir el enlace.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 2.dp)
            )
        } else {
            Text(text = value, color = ColorTextoPrincipal, fontSize = valueFontSize, fontWeight = valueFontWeight)
        }
    }
}

@Composable
fun EventoDetailContent(
    agrupacion: String,
    idRuta: String,
    fechaHoraSalida: String,
    instruccionesHtml: String,
    puntoReunion: String,
    mapsUrl: String,
    comentario: String,
    coopMinima: Double,
    colorTextoPrincipal: Color = Color.White, 
    colorAcento: Color = Color(0xFF00FF00)
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "agrupacion : $agrupacion",
            color = colorTextoPrincipal,
            fontSize = 14.sp
        )
        
        Text(
            text = idRuta,
            color = colorTextoPrincipal,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        
        Divider(color = colorAcento, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
        
        Row {
            Text("salida: ", color = colorTextoPrincipal, fontSize = 14.sp)
            Text(fechaHoraSalida, color = colorTextoPrincipal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text("detalle", color = colorTextoPrincipal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            factory = { ctx ->
                TextView(ctx).apply {
                    setTextColor(android.graphics.Color.WHITE) 
                    textSize = 14f
                }
            },
            update = { textView ->
                textView.text = HtmlCompat.fromHtml(
                    instruccionesHtml.ifBlank { "Sin instrucciones adicionales." }, 
                    HtmlCompat.FROM_HTML_MODE_COMPACT
                )
            }
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Punto de reunion",
            color = colorTextoPrincipal,
            fontSize = 14.sp,
            textDecoration = TextDecoration.Underline
        )
        Text(
            text = puntoReunion.ifBlank { "Por definir" },
            color = colorTextoPrincipal,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ubicacion: ", color = colorTextoPrincipal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Ver en Google Maps",
                color = colorAcento,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapsUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No se pudo abrir el mapa.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(4.dp)
            )
        }
        
        Divider(color = colorAcento, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
        
        Text(
            text = "Costos e Informacion adicional",
            color = colorTextoPrincipal,
            fontSize = 14.sp,
            fontStyle = FontStyle.Italic
        )
        
        Text(
            text = comentario.ifBlank { "Sin comentarios adicionales" },
            color = colorTextoPrincipal,
            fontSize = 18.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cooperacion ", color = colorTextoPrincipal, fontSize = 14.sp, fontStyle = FontStyle.Italic)
            Text("$$coopMinima", color = colorTextoPrincipal, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun UpcomingOutingWarningDialog(
    outing: InvitacionAceptadaEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorTekinsCardBg,
        titleContentColor = ColorTextoPrincipal,
        textContentColor = ColorTextoSecundario,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Event, contentDescription = null, tint = ColorTekinsAccent, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("¡Salida Más Próxima!", color = ColorTextoPrincipal, fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tienes una salida agendada próximamente:", color = ColorTextoSecundario)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Ruta: ${outing.nombreRuta}", color = ColorTextoPrincipal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Fecha: ${outing.fechaHoraSalida}", color = ColorTekinsAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Punto de Reunión: ${outing.puntoReunion}", color = ColorTextoPrincipal, fontSize = 13.sp)
                if (outing.cooperacionMinima > 0.0 && outing.cooperacionRealizada == null) {
                    Text("Nota: Recuerda registrar tu cooperación de $${outing.cooperacionMinima}", color = ColorTekinsAlert, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ENTENDIDO", color = ColorTekinsDarkBg, fontWeight = FontWeight.Bold)
            }
        }
    )
}



