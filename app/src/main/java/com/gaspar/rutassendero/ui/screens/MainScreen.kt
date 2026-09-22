@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.gaspar.rutassendero.ui.screens

import android.location.Location
import android.widget.Toast
import android.content.Intent
import android.content.Context
import android.net.Uri
import com.gaspar.rutassendero.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import com.gaspar.rutassendero.data.RutaEntity
import com.gaspar.rutassendero.data.PuntoRutaEntity
import com.gaspar.rutassendero.data.TrazaRutaEntity
import com.gaspar.rutassendero.data.ParticipanteEntity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gaspar.rutassendero.ui.RutasViewModel
import com.gaspar.rutassendero.ui.DriveSyncState
import com.gaspar.rutassendero.ui.CloudSyncState
import com.gaspar.rutassendero.ui.RouteStatsCalculator
import com.gaspar.rutassendero.ui.components.*
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: RutasViewModel,
    onLaunchVoiceRecognition: () -> Unit,
    onOpenExternalMap: (Double, Double, String) -> Unit,
    onOpenCamera: (File) -> Unit,
    onMinimizeApp: () -> Unit,
    onExitApp: () -> Unit,
    onRequestBluetoothPermission: ((Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "1.0"
        }
    }
    val dbVersion = remember {
        try {
            viewModel.db.openHelper.readableDatabase.version
        } catch (e: Exception) {
            13
        }
    }

    // Observación de estados de la UI desde el ViewModel
    if (viewModel.prefs.accountDeleted) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Cuenta Eliminada", color = ColorTekinsAlert) },
            text = { Text("Tu cuenta ha sido eliminada. Ya no eres participante de la aplicación. Para volver a usarla, por favor borra los datos de la aplicación o reinstálala.") },
            confirmButton = {
                Button(
                    onClick = {
                        (context as? android.app.Activity)?.finishAffinity()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAlert)
                ) {
                    Text("Cerrar", color = Color.White)
                }
            }
        )
        return
    }

    val rutas by viewModel.rutasState.collectAsState()
    val puntosList by viewModel.puntosListState.collectAsState()
    val selectedRoute by viewModel.selectedRoute.collectAsState()
    val isGpsActive by viewModel.isGpsActive.collectAsState()
    val location by viewModel.uiLocation.collectAsState()
    val isPointsListReversed by viewModel.isPointsListReversed.collectAsState()
    val selectedRouteTrace by viewModel.selectedRouteTrace.collectAsState()
    var isPointsListExpanded by remember { mutableStateOf(false) }
    val pointsListState = rememberLazyListState()
    LaunchedEffect(isPointsListReversed) {
        if (puntosList.isNotEmpty()) {
            pointsListState.animateScrollToItem(0)
        }
    }
    val isRouteFinished = remember(puntosList) { puntosList.any { it.entity.esFinRuta } }

    val targetWaypoint by viewModel.targetWaypoint.collectAsState()
    val distanceToTarget by viewModel.distanceToTarget.collectAsState()
    val relativeDirection by viewModel.relativeDirection.collectAsState()
    val isGuiding by viewModel.isGuiding.collectAsState()
    val guideDirectionForward by viewModel.guideDirectionForward.collectAsState()
    val capturedPhotoPath by viewModel.capturedPhotoPath.collectAsState()
    val dictatedText by viewModel.dictatedText.collectAsState()
    val selectedRoutePoints by viewModel.selectedRoutePoints.collectAsState()

    // --- Controladores de Diálogos y Menús de la UI ---
    val coroutineScope = rememberCoroutineScope()
    
    val appendVoiceLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenTexts = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            if (!spokenTexts.isNullOrEmpty()) {
                val fullText = spokenTexts[0].trim()
                if (fullText.isNotEmpty() && selectedRoute != null) {
                    viewModel.appendDescriptionToLastPoint(selectedRoute!!.id, fullText)
                    Toast.makeText(context, "Descripción añadida: $fullText", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var routePendingSelection by remember { mutableStateOf<RutaEntity?>(null) }
    var showRouteModeDialog by remember { mutableStateOf(false) }

    var showSettings by remember { mutableStateOf(false) }
    var showGuideTargetDialog by remember { mutableStateOf(false) }
    var showSelectRutaDialog by remember { mutableStateOf(false) }
    var showAddRutaDialog by remember { mutableStateOf(false) }
    var pendingEstado by remember { mutableStateOf(1) }
    var showSoundsDialog by remember { mutableStateOf(false) }
    var showSenderistasList by remember { mutableStateOf(false) }
    var showMapScreen by remember { mutableStateOf(false) }
    var showWaypointDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isGuiding) {
        if (isGuiding) {
            showMapScreen = true
        }
    }

    var showEditRutaDialog by remember { mutableStateOf(false) }
    var showDriveSyncDialog by remember { mutableStateOf(false) }
    var showCloudImportDialog by remember { mutableStateOf(false) }
    var showCloudDownloadDialog by remember { mutableStateOf(false) }
    
    var showGalleryViewer by remember { mutableStateOf(false) }
    var expectingSouvenirPhoto by remember { mutableStateOf(false) }
    var recoveryErrorMsg by remember { mutableStateOf<String?>(null) }
    var currentAppRole by remember { mutableStateOf(viewModel.prefs.appRole) }
    
    var showConfirmFinRutaDialog by remember { mutableStateOf(false) }
    var isQuickStateFabExpanded by remember { mutableStateOf(true) }
    
    var isSelectedRouteFinalized by remember { mutableStateOf(false) }
    var showRouteTooShortDialog by remember { mutableStateOf(false) }
    LaunchedEffect(selectedRoute) {
        val route = selectedRoute
        isSelectedRouteFinalized = if (route != null) viewModel.isRutaFinalized(route.id) else false
        if (route == null && currentAppRole == "registrar") {
            currentAppRole = "visor"
            viewModel.prefs.appRole = "visor"
        }
    }

    val openRouteWithRole = remember(currentAppRole) {
        { ruta: RutaEntity ->
            coroutineScope.launch {
                val isFinalized = viewModel.isRutaFinalized(ruta.id)
                if ((currentAppRole == "guia" || currentAppRole == "visor") && !isFinalized) {
                    Toast.makeText(context, "No se puede abrir como guía ni como visor una ruta que se está registrando (trazando).", Toast.LENGTH_LONG).show()
                } else {
                    if (currentAppRole == "guia") {
                        val points = viewModel.getPuntosForRuta(ruta.id)
                        if (points.isEmpty()) {
                            Toast.makeText(context, "La ruta no tiene puntos", Toast.LENGTH_SHORT).show()
                            viewModel.selectRuta(ruta)
                            viewModel.stopGuiding()
                        } else {
                            viewModel.selectRuta(ruta)
                            showGuideTargetDialog = true
                        }
                    } else {
                        if (currentAppRole == "registrar" && isFinalized) {
                            currentAppRole = "visor"
                            viewModel.prefs.appRole = "visor"
                        }
                        viewModel.selectRuta(ruta)
                        viewModel.stopGuiding()
                    }
                }
            }
            Unit
        }
    }

    val senderistaUiState by viewModel.senderistaUiState.collectAsState()
    val senderista = senderistaUiState.senderista
    val senderistaLoaded = senderistaUiState.isLoaded
    var showSenderistaDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    val agrupaciones by viewModel.agrupacionesList.collectAsState()

    // --- Controladores de Salidas ---
    var showSalidasDialog by remember { mutableStateOf(false) }
    var showUpcomingOutingWarningDialog by remember { mutableStateOf<com.gaspar.rutassendero.data.InvitacionAceptadaEntity?>(null) }


    LaunchedEffect(senderistaUiState) {
        if (senderistaUiState.isLoaded && senderistaUiState.senderista == null) {
            showSenderistaDialog = true
        }
    }

    if (showWaypointDialog && targetWaypoint != null) {
        Dialog(onDismissRequest = { showWaypointDialog = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black,
                border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val distText = if ((distanceToTarget ?: 0f) > 0f) "${String.format("%.1f", distanceToTarget ?: 0f)}m restantes" else "Llegando..."
                    Text(
                        text = targetWaypoint?.descripcion ?: "Punto",
                        color = Color.Yellow,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = distText,
                        color = Color.Yellow.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { showWaypointDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent)
                    ) {
                        Text("Cerrar", color = Color.White)
                    }
                }
            }
        }
    }

    var nextOutingChecked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!nextOutingChecked) {
            viewModel.checkUpcomingOutings { closest ->
                showUpcomingOutingWarningDialog = closest
            }
            nextOutingChecked = true
        }
    }

    val isBeaconEmitting by viewModel.isBeaconEmitting.collectAsState()
    
    LaunchedEffect(currentAppRole, selectedRoute, isBeaconEmitting, showSenderistasList) {
        val shouldTrack = (currentAppRole == "guia" && selectedRoute != null) || isBeaconEmitting || showSenderistasList
        if (shouldTrack) {
            viewModel.startBleTracking()
        } else {
            viewModel.stopBleTracking()
        }
    }

    val activeEmergencies by viewModel.activeEmergencies.collectAsState()
    val activeParticipants by viewModel.activeOutingParticipants.collectAsState()
    var dismissedEmergencies by remember { mutableStateOf(setOf<String>()) }
    
    val currentEmergencyId = activeEmergencies.firstOrNull { it !in dismissedEmergencies }
    if (currentEmergencyId != null && currentAppRole == "guia") {
        val emergencyParticipant = activeParticipants.find { it.idBeacon == currentEmergencyId }
        val name = emergencyParticipant?.nombreSenderista ?: "Senderista ($currentEmergencyId)"
        
        AlertDialog(
            onDismissRequest = { dismissedEmergencies = dismissedEmergencies + currentEmergencyId },
            title = { Text("¡EMERGENCIA 911!", color = ColorTekinsAlert, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) },
            text = { Text("El senderista $name está en problemas y ha activado el botón de auxilio.", fontSize = 16.sp) },
            confirmButton = {
                Button(
                    onClick = { 
                        dismissedEmergencies = dismissedEmergencies + currentEmergencyId
                        showSenderistasList = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAlert)
                ) {
                    Text("Ir al Rastreador", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissedEmergencies = dismissedEmergencies + currentEmergencyId }) {
                    Text("Cerrar", color = ColorTextoSecundario)
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.calendarEventFlow.collect { event ->
            try {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = android.provider.CalendarContract.Events.CONTENT_URI
                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginTimeMs)
                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, event.endTimeMs)
                    putExtra(android.provider.CalendarContract.Events.TITLE, event.title)
                    putExtra(android.provider.CalendarContract.Events.DESCRIPTION, event.description)
                    putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, event.location)
                    putExtra(android.provider.CalendarContract.Events.AVAILABILITY, android.provider.CalendarContract.Events.AVAILABILITY_BUSY)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo abrir el calendario.", Toast.LENGTH_LONG).show()
            }
        }
    }


    LaunchedEffect(capturedPhotoPath) {
        val path = capturedPhotoPath
        if (path != null) {
            if (expectingSouvenirPhoto) {
                expectingSouvenirPhoto = false
                viewModel.clearCapturedPhoto()
            } else if (viewModel.isCapturingPhotoForPoint && viewModel.locationSnapshotForPoint != null) {
                // Auto-guardar el punto al regresar de la cámara
                viewModel.addPuntoRuta(
                    idRuta = selectedRoute!!.id,
                    desc = "", // Dejar vacío para autogenerar "Faro #X" o "Fin de Ruta"
                    lat = viewModel.locationSnapshotForPoint!!.latitude,
                    lng = viewModel.locationSnapshotForPoint!!.longitude,
                    alt = viewModel.locationSnapshotForPoint!!.altitude,
                    fotoPath = path,
                    esFinRuta = viewModel.isFinRutaPending,
                    estado = pendingEstado
                )
                viewModel.isCapturingPhotoForPoint = false
                viewModel.locationSnapshotForPoint = null
                pendingEstado = 1
                viewModel.clearCapturedPhoto()
                viewModel.clearDictatedText()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.photoCaptureCancelledEvent.collect {
            if (viewModel.isCapturingPhotoForPoint) {
                viewModel.isCapturingPhotoForPoint = false
                viewModel.locationSnapshotForPoint = null
                Toast.makeText(context, "Registro de punto cancelado.", Toast.LENGTH_SHORT).show()
            }
            expectingSouvenirPhoto = false
        }
    }

    var showDistanceVerificationDialog by remember { mutableStateOf(false) }
    var mapForceRecomposeKey by remember { mutableStateOf(0) }
    var activeMapPoint by remember { mutableStateOf<PuntoRutaEntity?>(null) }

    var selectedPuntoForDetail by remember { mutableStateOf<PuntoRutaEntity?>(null) }
    var puntoToDeleteConfirm by remember { mutableStateOf<PuntoRutaEntity?>(null) }

    // Degradados premium según el rol/modo activo para un feedback visual claro
    val dynamicGradient = remember(currentAppRole) {
        val colors = when (currentAppRole) {
            "visor" -> listOf(
                Color(0xFFE8F5E9), // Verde menta muy suave
                Color(0xFFC8E6C9), // Verde suave
                Color(0xFF81C784)  // Verde hoja suave
            )
            "guia" -> listOf(
                Color(0xFFFAF9F6), // Blanco hueso
                Color(0xFFD0E1F9), // Azul hielo suave
                Color(0xFF4D648D)  // Azul grisáceo
            )
            "registrar" -> listOf(
                Color(0xFFFFFFFF), // Blanco
                Color(0xFFF1F5F9), // Gris pizarra muy claro
                Color(0xFFCBD5E1)  // Gris pizarra claro
            )
            else -> listOf(
                Color(0xFFFAF9F6),
                Color(0xFFC2D3C6),
                Color(0xFF0F766E)
            )
        }
        Brush.verticalGradient(colors)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(dynamicGradient)
    ) {
        if (showMapScreen && currentAppRole != "registrar") {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                topBar = {
                    var totalDragXMap by remember { mutableStateOf(0f) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEB3B)),
                        border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.4f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp)
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { totalDragXMap = 0f },
                                        onDragEnd = {
                                            if (totalDragXMap < -80f) {
                                                onExitApp()
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            totalDragXMap += dragAmount
                                        }
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { 
                                    showMapScreen = false 
                                    activeMapPoint = null
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            if (activeMapPoint != null) {
                                val punto = activeMapPoint!!
                                val ptIndex = selectedRoutePoints.indexOfFirst { it.id == punto.id }
                                val estadoInfo = getEstadoPuntoInfo(punto.estado, punto.esFinRuta)
                                
                                val (segText, accumText) = remember(punto, selectedRoutePoints) {
                                    if (ptIndex <= 0) {
                                        Pair("Inicio", "0 m")
                                    } else {
                                        var acc = 0f
                                        for (i in 1..ptIndex) {
                                            val p1 = selectedRoutePoints[i - 1]
                                            val p2 = selectedRoutePoints[i]
                                            val loc1 = Location("").apply { latitude = p1.latitud; longitude = p1.longitud }
                                            val loc2 = Location("").apply { latitude = p2.latitud; longitude = p2.longitud }
                                            acc += loc1.distanceTo(loc2)
                                        }
                                        val prev = selectedRoutePoints[ptIndex - 1]
                                        val locPrev = Location("").apply { latitude = prev.latitud; longitude = prev.longitud }
                                        val locCur = Location("").apply { latitude = punto.latitud; longitude = punto.longitud }
                                        val seg = locPrev.distanceTo(locCur)
                                        val segStr = if (seg >= 1000f) String.format(java.util.Locale.US, "%.1f km", seg / 1000f) else "${seg.toInt()} m"
                                        val accStr = if (acc >= 1000f) String.format(java.util.Locale.US, "%.1f km", acc / 1000f) else "${acc.toInt()} m"
                                        Pair(segStr, accStr)
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedPuntoForDetail = punto },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(estadoInfo.third.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (punto.esFinRuta) {
                                            Icon(
                                                imageVector = Icons.Default.Flag,
                                                contentDescription = "Fin de ruta",
                                                tint = ColorTekinsAlert,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(id = estadoInfo.second),
                                                contentDescription = estadoInfo.first,
                                                tint = estadoInfo.third,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "#${ptIndex + 1} · ${punto.descripcion}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = buildString {
                                                append(estadoInfo.first)
                                                if (ptIndex > 0) append(" · Tramo: $segText (va $accumText)")
                                                if (punto.altitud > 0) append(" · ${punto.altitud.toInt()}m")
                                            },
                                            fontSize = 11.sp,
                                            color = Color(0xFF334155),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(onClick = { selectedPuntoForDetail = punto }) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Ver detalles / Editar",
                                        tint = ColorTekinsPrimary
                                    )
                                }

                                IconButton(onClick = { activeMapPoint = null }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar info de punto",
                                        tint = Color.DarkGray
                                    )
                                }
                            } else if (isGuiding && targetWaypoint != null) {
                                val distText = if ((distanceToTarget ?: 0f) > 0f) " (${String.format("%.1f", distanceToTarget ?: 0f)}m)" else ""
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { showWaypointDialog = true }
                                ) {
                                    Text(
                                        text = "${targetWaypoint?.descripcion ?: "Punto"}$distText",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.Black,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    // Cálculo de avance tridimensional
                                    val totalDist = remember(selectedRoutePoints) { RouteStatsCalculator.calculateBeaconDistance(selectedRoutePoints) }
                                    val passedPoints = remember(selectedRoutePoints, targetWaypoint) {
                                        selectedRoutePoints.takeWhile { it.id != targetWaypoint?.id }
                                    }
                                    val passedDist = remember(passedPoints) { RouteStatsCalculator.calculateBeaconDistance(passedPoints) }
                                    val lastPassedPoint = passedPoints.lastOrNull()
                                    val distFromLast = remember(lastPassedPoint, location) {
                                        if (lastPassedPoint != null && location != null) {
                                            val loc1 = android.location.Location("").apply { 
                                                latitude = lastPassedPoint.latitud 
                                                longitude = lastPassedPoint.longitud 
                                            }
                                            val d2d = loc1.distanceTo(location!!)
                                            val dAlt = (location!!.altitude - lastPassedPoint.altitud).toFloat()
                                            if (kotlin.math.abs(dAlt) > 0.05f && kotlin.math.abs(dAlt) < 300f) {
                                                kotlin.math.sqrt(d2d * d2d + dAlt * dAlt)
                                            } else {
                                                d2d
                                            }
                                        } else {
                                            0f
                                        }
                                    }
                                    val currentProgressDist = passedDist + distFromLast
                                    val totalDistStr = if (totalDist >= 1000f) String.format(java.util.Locale.US, "%.2f km", totalDist / 1000f) else "${totalDist.toInt()} m"
                                    val currentDistStr = if (currentProgressDist >= 1000f) String.format(java.util.Locale.US, "%.2f km", currentProgressDist / 1000f) else "${currentProgressDist.toInt()} m"
                                    val percentage = if (totalDist > 0) ((currentProgressDist / totalDist) * 100).toInt().coerceIn(0, 100) else 0
                                    
                                    val currentSpeedKmh = if (location != null && location!!.hasSpeed()) (location!!.speed * 3.6f) else 0f
                                    val speedStr = String.format(java.util.Locale.US, "%.1f km/h", currentSpeedKmh)

                                    Text(
                                        text = "$currentDistStr de $totalDistStr ($percentage%) · Vel: $speedStr",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A), // Azul casi negro (Slate 900)
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                
                                IconButton(onClick = { mapForceRecomposeKey++ }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Recargar Mapa",
                                        tint = Color.Black
                                    )
                                }
                                
                                val isVoiceGuidanceEnabled by viewModel.isVoiceGuidanceEnabled.collectAsState()
                                IconButton(onClick = { viewModel.toggleVoiceGuidance() }) {
                                    androidx.compose.material3.Icon(
                                        imageVector = if (isVoiceGuidanceEnabled) androidx.compose.material.icons.Icons.Default.VolumeUp else androidx.compose.material.icons.Icons.Default.VolumeOff,
                                        contentDescription = "Alternar voz guía",
                                        tint = if (isVoiceGuidanceEnabled) ColorTekinsPrimary else Color.Black
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Mapa de la Ruta",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.DarkGray
                                    )
                                    Text(
                                        text = selectedRoute?.nombre ?: "Sin Ruta",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.Black,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { mapForceRecomposeKey++ }) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Recargar Mapa",
                                        tint = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    androidx.compose.runtime.key(mapForceRecomposeKey) {
                        GoogleMapView(
                            points = selectedRoutePoints,
                            tracePoints = selectedRouteTrace,
                            currentLocation = location,
                            onPointClick = { punto ->
                                activeMapPoint = punto
                                if (isGuiding && targetWaypoint?.id == punto.id) {
                                    val currentLoc = location
                                    if (currentLoc != null) {
                                        val targetLoc = Location("").apply {
                                            latitude = punto.latitud
                                            longitude = punto.longitud
                                        }
                                        val dist = currentLoc.distanceTo(targetLoc)
                                        val limit = viewModel.prefs.tapDistanceLimit.toFloat()
                                        if (dist <= limit) {
                                            viewModel.advanceWaypointManually()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Demasiado lejos para marcar como visto (Distancia: ${dist.toInt()}m, Límite: ${limit.toInt()}m)",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Esperando señal de GPS...", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onPointHover = { punto ->
                                activeMapPoint = punto
                            },
                            activePointId = activeMapPoint?.id,
                            modifier = Modifier.fillMaxSize(),
                            routeStartLat = selectedRoute?.latitudOrig,
                            routeStartLng = selectedRoute?.longitudOrig
                        )
                    }
                }
            }
        } else {
            Scaffold(
                modifier = modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                topBar = {
                    val isSynced = selectedRoute?.let { viewModel.prefs.isRouteSyncedToCloud(it.id) } ?: false
                    val is911Active by viewModel.isBeaconEmergencyEmitting.collectAsState()
                    HeaderBar(
                        isGpsActive = isGpsActive,
                        location = location,
                        selectedRoute = selectedRoute,
                        points = selectedRoutePoints,
                        tracePoints = selectedRouteTrace,
                        isGuideMode = currentAppRole != "registrar",
                        currentAppRole = currentAppRole,
                        senderistaRol = senderista?.rol ?: "S",
                        onSelectRutaClick = {
                            coroutineScope.launch {
                                val activeRoute = viewModel.getActiveRoute()
                                if (activeRoute != null) {
                                    if (selectedRoute?.id != activeRoute.id) {
                                        viewModel.selectRuta(activeRoute)
                                        viewModel.stopGuiding()
                                    }
                                    currentAppRole = "registrar"
                                    viewModel.prefs.appRole = "registrar"
                                    Toast.makeText(context, "Ruta abierta detectada: '${activeRoute.nombre}'. Por favor, registra el siguiente punto.", Toast.LENGTH_LONG).show()
                                } else {
                                    showSelectRutaDialog = true
                                }
                            }
                        },
                        onClearRouteClick = { viewModel.selectRuta(null) },
                        onMinimizeApp = onMinimizeApp,
                        onExitApp = onExitApp,
                        onSwipeLeft = onExitApp,
                        onHeaderDoubleTap = { showEditRutaDialog = true },
                        onHeaderClick = {
                             if (selectedRoute != null) {
                                  showDistanceVerificationDialog = true
                             }
                        },
                        onDriveSyncClick = { showDriveSyncDialog = true },
                        isFinalized = isSelectedRouteFinalized,
                        isSynced = isSynced,
                        onCloudImportClick = { showCloudImportDialog = true },
                        onAddRutaClick = {
                            coroutineScope.launch {
                                val activeRoute = viewModel.getActiveRoute()
                                if (activeRoute != null) {
                                    viewModel.selectRuta(activeRoute)
                                    viewModel.stopGuiding()
                                    Toast.makeText(context, "Se abrió la ruta activa '${activeRoute.nombre}' para que la finalices o borres.", Toast.LENGTH_LONG).show()
                                } else {
                                    showAddRutaDialog = true
                                }
                            }
                        },
                        onSalidasClick = { showSalidasDialog = true },
                        onPerfilClick = { showSenderistaDialog = true },
                        isPointsListReversed = isPointsListReversed,
                        onPointsOrderChange = { reversed ->
                            viewModel.isPointsListReversed.value = reversed
                        },
                        onSettingsClick = { showSettings = true },
                        is911Active = is911Active,
                        on911Click = {
                            val senderistaObj = senderista
                            if (senderistaObj == null || senderistaObj.idBeacon.isNullOrBlank()) {
                                Toast.makeText(context, "No se puede emitir 911 sin ID de Beacon.", Toast.LENGTH_SHORT).show()
                            } else {
                                val action = {
                                    if (is911Active) {
                                        viewModel.startBeaconEmission(
                                            idBeacon = senderistaObj.idBeacon,
                                            isEmergency = false,
                                            onSuccess = { Toast.makeText(context, "Emergencia cancelada.", Toast.LENGTH_LONG).show() },
                                            onFailure = { Toast.makeText(context, "Error al cancelar", Toast.LENGTH_LONG).show() }
                                        )
                                    } else {
                                        viewModel.startBeaconEmission(
                                            idBeacon = senderistaObj.idBeacon,
                                            isEmergency = true,
                                            onSuccess = { Toast.makeText(context, "¡EMERGENCIA 911 ACTIVADA!", Toast.LENGTH_LONG).show() },
                                            onFailure = { Toast.makeText(context, "Error al activar", Toast.LENGTH_LONG).show() }
                                        )
                                    }
                                }

                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    val hasAdvertisePermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.BLUETOOTH_ADVERTISE
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    
                                    if (!hasAdvertisePermission) {
                                        onRequestBluetoothPermission { granted ->
                                            if (granted) {
                                                action()
                                            } else {
                                                Toast.makeText(context, "Permiso de Bluetooth denegado. No se puede emitir.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        action()
                                    }
                                } else {
                                    action()
                                }
                            }
                        }
                    )
                },
                
                // 2. PIE DE PÁGINA (DOCK DE ACCIONES)
                bottomBar = {
                    val isBeaconActive by viewModel.isBeaconEmitting.collectAsState()
                    FooterBar(
                        hasRouteSelected = selectedRoute != null,
                        isBeaconActive = isBeaconActive,
                        isGuideActive = true, // Permite ver monitoreo en todos los modos (guía, trazar, visor)
                        onBeaconToggle = { active ->
                            if (active) {
                                val senderistaObj = senderista
                                if (senderistaObj == null) {
                                    Toast.makeText(context, "Por favor, registra el perfil del senderista primero.", Toast.LENGTH_SHORT).show()
                                    return@FooterBar
                                }
                                val idBeacon = senderistaObj.idBeacon
                                if (idBeacon.isNullOrBlank()) {
                                    Toast.makeText(context, "El senderista no tiene ID de Beacon. Espere a que se genere.", Toast.LENGTH_SHORT).show()
                                    return@FooterBar
                                }

                                // Check permission on Android 12+
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    val hasAdvertisePermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.BLUETOOTH_ADVERTISE
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    
                                    if (!hasAdvertisePermission) {
                                        onRequestBluetoothPermission { granted ->
                                            if (granted) {
                                                viewModel.startBeaconEmission(
                                                    idBeacon = idBeacon,
                                                    onSuccess = {
                                                        Toast.makeText(context, "Emisión de Beacon iniciada (ID: $idBeacon)", Toast.LENGTH_SHORT).show()
                                                    },
                                                    onFailure = { err ->
                                                        val msg = when (err) {
                                                            -1 -> "Hardware no soporta BLE o Bluetooth apagado."
                                                            -2 -> "No se pudo obtener el emisor BLE."
                                                            -3 -> "Permisos de Bluetooth denegados."
                                                            else -> "Error al iniciar emisión: Código $err"
                                                        }
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        }
                                        return@FooterBar
                                    }
                                }

                                viewModel.startBeaconEmission(
                                    idBeacon = idBeacon,
                                    onSuccess = {
                                        Toast.makeText(context, "Emisión de Beacon iniciada (ID: $idBeacon)", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { err ->
                                        val msg = when (err) {
                                            -1 -> "Hardware no soporta BLE o Bluetooth apagado."
                                            -2 -> "No se pudo obtener el emisor BLE."
                                            -3 -> "Permisos de Bluetooth denegados."
                                            else -> "Error al iniciar emisión: Código $err"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                )
                            } else {
                                viewModel.stopBeaconEmission()
                                Toast.makeText(context, "Emisión de Beacon detenida", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSoundsClick = { showSoundsDialog = true },
                        onMapClick = {
                            if (selectedRoute != null) {
                                if (currentAppRole == "registrar") {
                                    Toast.makeText(context, "El mapa no está disponible en el modo Registrar (trazar). Solo está disponible en modo Guía y Visor.", Toast.LENGTH_LONG).show()
                                } else {
                                    showMapScreen = true
                                }
                            }
                        },
                        onListClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                val hasScanPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.BLUETOOTH_SCAN
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                val hasConnectPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.BLUETOOTH_CONNECT
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                
                                if (!hasScanPermission || !hasConnectPermission) {
                                    onRequestBluetoothPermission { granted ->
                                        if (granted) {
                                            showSenderistasList = true
                                        } else {
                                            Toast.makeText(context, "Permisos de Bluetooth denegados.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    showSenderistasList = true
                                }
                            } else {
                                showSenderistasList = true
                            }
                        }
                    )
                }
            ) { innerPadding ->
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                // 1.5. SELECTOR DE MODOS (Guía, Trazar, Visor)
                // Se oculta automáticamente si hay una ruta seleccionada
                if (selectedRoute == null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorTekinsCardBg),
                        border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.25f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val modes = listOf(
                                Triple("guia", "Guía", Icons.Default.Explore),
                                Triple("visor", "Visor", Icons.Default.Visibility)
                            )
                            
                            modes.forEach { (role, label, icon) ->
                                val isSelected = currentAppRole == role
                                val bg = if (isSelected) {
                                    when (role) {
                                        "guia" -> Color(0xFF64748B)      // Slate 500 (Gris)
                                        "registrar" -> Color(0xFFFAF9F6) // Blanco Marfil
                                        else -> ColorTekinsAccent        // Menta brillante (el verde actual)
                                    }
                                } else {
                                    Color.Transparent
                                }
                                val contentColor = if (isSelected) {
                                    when (role) {
                                        "registrar" -> Color(0xFF0F172A) // Slate 900 para contraste en blanco marfil
                                        "visor" -> Color(0xFF0F172A)     // Slate 900 para contraste en menta brillante
                                        else -> Color.White
                                    }
                                } else {
                                    ColorTextoSecundario
                                }
                                
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.prefs.appRole = role
                                            currentAppRole = role
                                            viewModel.isPointsListReversed.value = (role != "guia")
                                            // Si cambiamos de modo y hay guiado activo pero no es modo guía, detener guiado
                                            if (role != "guia" && isGuiding) {
                                                viewModel.stopGuiding()
                                            }
                                        },
                                    color = bg,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            tint = contentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tarjeta de Guía Activa (Doppler)
                if (isGuiding && targetWaypoint != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorTekinsCardBg),
                        border = BorderStroke(1.5.dp, ColorTekinsAccent)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "🎯 GUIANDO AL PUNTO:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorTekinsAccent
                                    )
                                    Text(
                                        text = targetWaypoint!!.descripcion,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorTextoPrincipal
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.stopGuiding()
                                        viewModel.selectRuta(null)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAlert),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Detener", color = Color.White, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    val dist = distanceToTarget
                                    val distStr = when {
                                        dist == null -> "Calculando..."
                                        dist >= 1000f -> String.format(java.util.Locale.US, "%.2f km", dist / 1000f)
                                        else -> "${dist.toInt()} metros"
                                    }
                                    Text("Al punto:", fontSize = 11.sp, color = ColorTextoSecundario)
                                    Text(distStr, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = ColorTextoPrincipal)
                                }

                                val currentSpeedKmh = if (location != null && location!!.hasSpeed()) location!!.speed * 3.6f else 0f
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Velocidad:", fontSize = 11.sp, color = ColorTextoSecundario)
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f km/h", currentSpeedKmh),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ColorTekinsPrimary
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Dirección:", fontSize = 11.sp, color = ColorTextoSecundario)
                                    Text(
                                        text = relativeDirection ?: "Camina para orientar",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorTekinsAccent
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            // Mostrar barra de Doppler acústico
                            val progress = if (distanceToTarget != null) {
                                val limit = viewModel.prefs.alertDistance.toFloat()
                                ((limit - distanceToTarget!!) / limit).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            Text(
                                text = if (progress > 0f) "🔊 Alerta Acústica Activa (Frecuencia Doppler)" else "🔇 Fuera de rango acústico",
                                fontSize = 10.sp,
                                color = if (progress > 0f) ColorTekinsSuccess else ColorTextoSecundario,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                color = ColorTekinsAccent,
                                trackColor = ColorTekinsCardMuted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            // Avance general de la ruta tridimensional
                            val totalRouteDist = remember(selectedRoutePoints) { RouteStatsCalculator.calculateBeaconDistance(selectedRoutePoints) }
                            val passedPts = remember(selectedRoutePoints, targetWaypoint) {
                                selectedRoutePoints.takeWhile { it.id != targetWaypoint?.id }
                            }
                            val passedD = remember(passedPts) { RouteStatsCalculator.calculateBeaconDistance(passedPts) }
                            val lastPassedPt = passedPts.lastOrNull()
                            val distFromLastPt = remember(lastPassedPt, location) {
                                if (lastPassedPt != null && location != null) {
                                    val loc1 = android.location.Location("").apply { 
                                        latitude = lastPassedPt.latitud 
                                        longitude = lastPassedPt.longitud 
                                    }
                                    val d2d = loc1.distanceTo(location!!)
                                    val dAlt = (location!!.altitude - lastPassedPt.altitud).toFloat()
                                    if (kotlin.math.abs(dAlt) > 0.05f && kotlin.math.abs(dAlt) < 300f) {
                                        kotlin.math.sqrt(d2d * d2d + dAlt * dAlt)
                                    } else {
                                        d2d
                                    }
                                } else 0f
                            }
                            val currentProgress = passedD + distFromLastPt
                            val routeProgressPct = if (totalRouteDist > 0) ((currentProgress / totalRouteDist) * 100).toInt().coerceIn(0, 100) else 0
                            val totalDistLabel = if (totalRouteDist >= 1000f) String.format(java.util.Locale.US, "%.2f km", totalRouteDist / 1000f) else "${totalRouteDist.toInt()} m"
                            val currentDistLabel = if (currentProgress >= 1000f) String.format(java.util.Locale.US, "%.2f km", currentProgress / 1000f) else "${currentProgress.toInt()} m"

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Avance de ruta: $currentDistLabel de $totalDistLabel",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ColorTextoSecundario
                                )
                                Text(
                                    text = "$routeProgressPct%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorTekinsAccent
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (routeProgressPct / 100f).coerceIn(0f, 1f) },
                                color = ColorTekinsPrimary,
                                trackColor = ColorTekinsCardMuted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = if (guideDirectionForward) "Sentido: Al Final" else "Sentido: Al Inicio",
                                fontSize = 12.sp,
                                color = ColorTextoPrincipal,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ColorTekinsCardMuted, RoundedCornerShape(8.dp))
                                    .padding(vertical = 8.dp, horizontal = 12.dp)
                            )
                        }
                    }
                }

                // Lista de puntos o vista vacía
                if (selectedRoute == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {

                            Image(
                                painter = painterResource(id = R.drawable.logo_senderismo),
                                contentDescription = "Logo Senderismo con Amigos",
                                modifier = Modifier
                                    .width(380.dp)
                                    .aspectRatio(16f / 9f)
                            )
                        /*
                            Image(
                                painter = painterResource(id = R.drawable.logo_luna_k),
                                contentDescription = "Logo LunaK Viajes y Senderismo Colima",
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, ColorTekinsAccent, CircleShape)
                            )
                         */
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "¿A dónde vamos hoy?",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTekinsDarkBg
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Selecciona una ruta o registra tu aventura para compartir",
                                fontSize = 14.sp,
                                color = ColorTekinsDarkBg.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Versión $versionName (Beta Externa - Pruebas de campo DB v$dbVersion)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTekinsDarkBg.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = com.gaspar.rutassendero.BuildConfig.UPDATE_DATE,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ColorTekinsDarkBg.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("(@Sirmartillo id en wattsap)")
                                    }
                                    append(", Desarrollado con geminis.\n© Todos los derechos reservados Tekins S.A.")
                                },
                                fontSize = 11.sp,
                                color = ColorTekinsDarkBg.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        if (puntosList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (!isGuiding && currentAppRole == "registrar" && !isRouteFinished) {
                                                    if (location != null) {
                                                        viewModel.clearCapturedPhoto()
                                                        viewModel.isFinRutaPending = false
                                                        viewModel.locationSnapshotForPoint = location
                                                        viewModel.isCapturingPhotoForPoint = true
                                                        val file = File(context.filesDir, "foto_${System.currentTimeMillis()}.jpg")
                                                        onOpenCamera(file)
                                                    } else {
                                                        Toast.makeText(context, "Esperando señal de GPS...", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Explore,
                                        contentDescription = null,
                                        tint = ColorTekinsPrimary,
                                        modifier = Modifier.size(72.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Ruta sin puntos",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorTekinsDarkBg
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Usa el botón de abajo o doble tap en la pantalla para registrar tu primer punto.\nTip: Doble clic en el nombre arriba para editar la ruta.",
                                        fontSize = 13.sp,
                                        color = ColorTekinsDarkBg.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = {
                                            if (location != null) {
                                                viewModel.clearCapturedPhoto()
                                                viewModel.isFinRutaPending = false
                                                viewModel.locationSnapshotForPoint = location
                                                viewModel.isCapturingPhotoForPoint = true
                                                val file = File(context.filesDir, "foto_${System.currentTimeMillis()}.jpg")
                                                onOpenCamera(file)
                                            } else {
                                                Toast.makeText(context, "Esperando señal de GPS...", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFACC15), // Amarillo de Alta Visibilidad
                                            contentColor = Color(0xFF0F172A)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth(0.9f)
                                            .height(64.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddLocation, 
                                            contentDescription = null, 
                                            tint = Color(0xFF0F172A),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "REGISTRAR PRIMER PUNTO", 
                                            color = Color(0xFF0F172A), 
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(0.9f),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { showCloudDownloadDialog = true },
                                            border = BorderStroke(1.5.dp, ColorTekinsAccent),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTekinsAccent),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(60.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                tint = ColorTekinsAccent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "BAJAR DEL SERVIDOR",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorTekinsAccent,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { 
                                                selectedRoute?.let { 
                                                    viewModel.clearLocalPhotosAndPointsAndTrace(it)
                                                    viewModel.deleteRuta(it)
                                                    viewModel.selectRuta(null)
                                                }
                                            },
                                            border = BorderStroke(1.5.dp, ColorTekinsAlert),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTekinsAlert),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(60.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Cancel,
                                                contentDescription = null,
                                                tint = ColorTekinsAlert,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "CANCELAR RUTA",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorTekinsAlert,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Si estamos en modo registrar y NO está expandida la lista de puntos, mostramos resumen
                            if (currentAppRole == "registrar" && !isPointsListExpanded) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f)
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth(0.95f),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = ColorTekinsCardBg),
                                        border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.25f)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AddLocation,
                                                contentDescription = null,
                                                tint = ColorTekinsAccent,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "Registro Activo",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorTextoPrincipal
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(color = ColorTekinsCardMuted, modifier = Modifier.padding(vertical = 4.dp))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Ruta: ${selectedRoute?.nombre ?: ""}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextoPrincipal)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Puntos guardados: ${puntosList.size}", fontSize = 13.sp, color = ColorTextoSecundario)
                                            
                                            val lastPoint = puntosList.firstOrNull()
                                            if (lastPoint != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Último punto: ${lastPoint.entity.descripcion}",
                                                    fontSize = 12.sp,
                                                    color = ColorTextoSecundario,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                            if (puntosList.size == 1) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = "¡Punto inicial registrado!\nPor favor, registra el siguiente punto de la ruta utilizando el botón de abajo o haciendo doble toque en la pantalla.",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = ColorTekinsAccent,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(20.dp))
                                            
                                            // Botón para dictar descripción extra al último punto
                                            if (lastPoint != null && lastPoint.entity.esFinRuta == false) {
                                                Button(
                                                    onClick = {
                                                        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Añadir a la descripción...")
                                                        }
                                                        try {
                                                            appendVoiceLauncher.launch(intent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Reconocimiento de voz no disponible", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("DICTAR DESCRIPCIÓN AL PUNTO", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                            }
                                            
                                            Button(
                                                onClick = { isPointsListExpanded = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("VER LISTA DE PUNTOS", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = pointsListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    if (!isGuiding && currentAppRole == "registrar" && !isRouteFinished) {
                                                        if (location != null) {
                                                            viewModel.clearCapturedPhoto()
                                                            viewModel.isFinRutaPending = false
                                                            viewModel.locationSnapshotForPoint = location
                                                            viewModel.isCapturingPhotoForPoint = true
                                                            val file = File(context.filesDir, "foto_${System.currentTimeMillis()}.jpg")
                                                            onOpenCamera(file)
                                                        } else {
                                                            Toast.makeText(context, "Esperando señal de GPS...", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            )
                                        },
                                    contentPadding = PaddingValues(bottom = 12.dp, top = 4.dp)
                                ) {
                                    if (currentAppRole == "registrar") {
                                        item {
                                            OutlinedButton(
                                                onClick = { isPointsListExpanded = false },
                                                border = BorderStroke(1.dp, ColorTextoSecundario.copy(alpha = 0.4f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextoSecundario),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    .height(40.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("OCULTAR LISTA DE PUNTOS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    items(
                                        items = puntosList,
                                        key = { it.entity.id }
                                    ) { model ->
                                        val isTarget = isGuiding && targetWaypoint?.id == model.entity.id
                                        
                                        if (currentAppRole == "guia" || currentAppRole == "visor" || isRouteFinished || isSelectedRouteFinalized) {
                                            PuntoRutaItem(
                                                model = model,
                                                isTarget = isTarget,
                                                onClick = { selectedPuntoForDetail = model.entity },
                                                onLongClick = { }
                                            )
                                        } else {
                                            val dismissState = rememberSwipeToDismissBoxState(
                                                confirmValueChange = { dismissValue ->
                                                    if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                                        puntoToDeleteConfirm = model.entity
                                                        false
                                                    } else {
                                                        false
                                                    }
                                                }
                                            )
 
                                            SwipeToDismissBox(
                                                state = dismissState,
                                                backgroundContent = {
                                                    val color = when (dismissState.dismissDirection) {
                                                        SwipeToDismissBoxValue.EndToStart -> ColorTekinsAlert
                                                        SwipeToDismissBoxValue.StartToEnd -> ColorTekinsAlert
                                                        else -> Color.Transparent
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(vertical = 4.dp, horizontal = 10.dp)
                                                            .background(color, RoundedCornerShape(12.dp)),
                                                        contentAlignment = Alignment.CenterEnd
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 16.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.White)
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text("Eliminar", color = Color.White, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                },
                                                content = {
                                                    PuntoRutaItem(
                                                        model = model,
                                                        isTarget = isTarget,
                                                        onClick = { selectedPuntoForDetail = model.entity },
                                                        onLongClick = { }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (!isGuiding && currentAppRole == "registrar") {
                                if (isRouteFinished) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = ColorTekinsCardBg),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Ruta Finalizada (Solo Lectura)",
                                                    fontWeight = FontWeight.Bold,
                                                    color = ColorTextoPrincipal,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                     text = "esta ruta ya se registro, para que pueda ser usada para otras fechas, o otros senderistas",
                                                     color = ColorTextoSecundario,
                                                     fontSize = 11.sp
                                                 )
                                            }
                                        }
                                    }
                                } else {
                                    // TARJETA FIJA CON BOTONES DE REGISTRAR PUNTO Y FIN DE RUTA
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = ColorTekinsCardBg),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFFFACC15).copy(alpha = 0.5f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        if (location != null) {
                                                            viewModel.clearCapturedPhoto()
                                                            viewModel.isFinRutaPending = false
                                                            viewModel.locationSnapshotForPoint = location
                                                            viewModel.isCapturingPhotoForPoint = true
                                                            val file = File(context.filesDir, "foto_${System.currentTimeMillis()}.jpg")
                                                            onOpenCamera(file)
                                                        } else {
                                                            Toast.makeText(context, "Esperando señal de GPS...", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFFFACC15), // Safety Yellow
                                                        contentColor = Color(0xFF0F172A)    // Dark Slate
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(56.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AddLocation, 
                                                        contentDescription = null, 
                                                        tint = Color(0xFF0F172A), 
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "REGISTRAR PUNTO", 
                                                        fontSize = 13.sp, 
                                                        color = Color(0xFF0F172A), 
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }

                                                Button(
                                                    onClick = {
                                                        if (location != null) {
                                                            val traceDist = if (selectedRouteTrace.isNotEmpty()) RouteStatsCalculator.calculateTraceDistance(selectedRouteTrace) else 0f
                                                            val beaconDist = if (puntosList.isNotEmpty()) RouteStatsCalculator.calculateBeaconDistance(puntosList.map { it.entity }) else 0f
                                                            val finalDist = maxOf(traceDist, beaconDist)
                                                            val currentDistanceKm = finalDist / 1000f
                                                            val minDistanceKm = senderista?.minimoLongitud ?: 4.0
                                                            
                                                            if (currentDistanceKm < minDistanceKm) {
                                                                showRouteTooShortDialog = true
                                                            } else {
                                                                showConfirmFinRutaDialog = true
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "Esperando señal de GPS...", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = ColorTekinsAlert, // Rojo Alerta
                                                        contentColor = Color.White
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(56.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Flag, 
                                                        contentDescription = null, 
                                                        tint = Color.White, 
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "FIN DE RUTA", 
                                                        fontSize = 13.sp, 
                                                        color = Color.White, 
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "💡 Tip: Doble clic arriba para editar la ruta.",
                                                fontSize = 11.sp,
                                                color = ColorTextoSecundario,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            if (puntosList.size == 1) {
                                                Text(
                                                    text = "👉 ¡Punto inicial registrado! Registra el siguiente punto para continuar.",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ColorTekinsAccent,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
                
                // --- QUICK STATE SELECTION OVERLAY ---
                val isRouteFinishedLocal = selectedRoutePoints.isNotEmpty() && selectedRoutePoints.any { it.esFinRuta }
                if (currentAppRole == "registrar" && selectedRoute != null && !isRouteFinishedLocal) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 90.dp, end = 12.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        class EstadoItem(val id: Int, val iconId: Int, val nombre: String, val color: Color)
                        val estadosList = listOf(
                            EstadoItem(2, R.drawable.ic_sitio_interes, "Sitio de interés", Color(0xFF2E7D32)),
                            EstadoItem(3, R.drawable.ic_bifurcacion, "Bifurcación", Color(0xFF5E35B1)),
                            EstadoItem(4, R.drawable.ic_suelo_flojo, "Suelo flojo", Color(0xFF8D6E63)),
                            EstadoItem(5, R.drawable.ic_aviso_cuidado, "Cuidado", Color(0xFFFBC02D)),
                            EstadoItem(6, R.drawable.ic_calavera_alerta, "Alerta", Color(0xFFD32F2F)),
                            EstadoItem(8, R.drawable.ic_aviso_vehiculo, "Vehículo", Color(0xFF607D8B)),
                            EstadoItem(9, R.drawable.ic_casa, "Refugio", Color(0xFF4E342E)),
                            EstadoItem(10, R.drawable.ic_comida, "Comida", Color(0xFFE65100))
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            if (isQuickStateFabExpanded) {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 280.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    items(estadosList) { estado ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
                                                .border(1.5.dp, estado.color, RoundedCornerShape(20.dp))
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable {
                                                    isQuickStateFabExpanded = false
                                                    if (location != null) {
                                                        pendingEstado = estado.id
                                                        viewModel.clearCapturedPhoto()
                                                        viewModel.isFinRutaPending = false
                                                        viewModel.locationSnapshotForPoint = location
                                                        viewModel.isCapturingPhotoForPoint = true
                                                        val file = File(context.filesDir, "foto_${System.currentTimeMillis()}.jpg")
                                                        onOpenCamera(file)
                                                    } else {
                                                        Toast.makeText(context, "Esperando señal de GPS...", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = estado.nombre,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = estado.color
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                painter = painterResource(id = estado.iconId),
                                                contentDescription = estado.nombre,
                                                tint = estado.color,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            FloatingActionButton(
                                onClick = { isQuickStateFabExpanded = !isQuickStateFabExpanded },
                                containerColor = ColorTekinsAccent,
                                contentColor = Color.White,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = if (isQuickStateFabExpanded) Icons.Default.Close else Icons.Default.AddLocation,
                                    contentDescription = "Puntos Especiales"
                                )
                            }
                        }
                    }
                }
            }
        }
    }


        // --- DIÁLOGOS DE RUTA Y CONFIGURACIÓN ---

        if (showGuideTargetDialog && selectedRoutePoints.isNotEmpty()) {
            var selectedToStart by remember { mutableStateOf(false) } // false = al final, true = al inicio
            
            AlertDialog(
                onDismissRequest = { showGuideTargetDialog = false },
                containerColor = ColorTekinsCardBg,
                titleContentColor = ColorTextoPrincipal,
                textContentColor = ColorTextoSecundario,
                title = { Text("Indíqueme hacia dónde quiere llegar", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(
                            color = ColorTekinsCardMuted,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        // Opción: Al Final
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedToStart = false }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = !selectedToStart,
                                onClick = { selectedToStart = false },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ColorTekinsAccent,
                                    unselectedColor = ColorTextoSecundario
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Al Final (Ida)",
                                    fontWeight = FontWeight.Bold,
                                    color = ColorTextoPrincipal,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Ir al final del sendero.",
                                    color = ColorTextoSecundario,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Opción: Al Inicio
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedToStart = true }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedToStart,
                                onClick = { selectedToStart = true },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = ColorTekinsAccent,
                                    unselectedColor = ColorTextoSecundario
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Al Inicio (Retorno)",
                                    fontWeight = FontWeight.Bold,
                                    color = ColorTextoPrincipal,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Regresar al inicio.",
                                    color = ColorTextoSecundario,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val route = selectedRoute
                            val currentLoc = location
                            if (route != null && currentLoc != null) {
                                val results = FloatArray(1)
                                var minDistanceKm = Float.MAX_VALUE
                                var closestPoint: com.gaspar.rutassendero.data.PuntoRutaEntity? = null
                                
                                // Distancia al inicio de la ruta
                                Location.distanceBetween(
                                    currentLoc.latitude, currentLoc.longitude,
                                    route.latitudOrig, route.longitudOrig,
                                    results
                                )
                                minDistanceKm = results[0] / 1000f

                                // Distancia a los puntos de la ruta
                                selectedRoutePoints.forEach { punto ->
                                    Location.distanceBetween(
                                        currentLoc.latitude, currentLoc.longitude,
                                        punto.latitud, punto.longitud,
                                        results
                                    )
                                    val distKm = results[0] / 1000f
                                    if (distKm < minDistanceKm) {
                                        minDistanceKm = distKm
                                        closestPoint = punto
                                    }
                                }

                                if (minDistanceKm > 2.0f) {
                                    Toast.makeText(
                                        context,
                                        "Está muy lejos del sendero (a ${String.format("%.1f", minDistanceKm)} km). Acérquese para iniciar. Redirigiendo a Google Maps...",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    
                                    currentAppRole = "visor"
                                    viewModel.prefs.appRole = "visor"

                                    val gmmIntentUri = Uri.parse("geo:0,0?q=${route.latitudOrig},${route.longitudOrig}(Inicio de ${route.nombre})")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    try {
                                        context.startActivity(mapIntent)
                                    } catch (e: Exception) {
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${route.latitudOrig},${route.longitudOrig}"))
                                        context.startActivity(webIntent)
                                    }
                                    showGuideTargetDialog = false
                                    return@Button
                                } else if (closestPoint != null && minDistanceKm > 0.05f) {
                                    Toast.makeText(
                                        context,
                                        "Estás a ${String.format("%.1f", minDistanceKm)} km de un punto. Dirígete hacia él para seguir la ruta.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            viewModel.startGuidingToDestination(toStart = selectedToStart)
                            showGuideTargetDialog = false
                            showMapScreen = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAccent)
                    ) {
                        Text("Comenzar", color = ColorTekinsDarkBg, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGuideTargetDialog = false }) {
                        Text("Cancelar", color = ColorTextoSecundario)
                    }
                }
            )
        }

        if (showSelectRutaDialog) {
            LaunchedEffect(Unit) {
                viewModel.checkAndCleanOtherRoutes()
            }
            SelectRutaDialog(
                rutas = rutas,
                location = location,
                isGuideMode = currentAppRole != "registrar",
                senderistaName = senderista?.nombre ?: "",
                userPhone = senderista?.telefono ?: "",
                senderistaRol = senderista?.rol ?: "S",
                showActions = false, // Ocultar botones Nueva y Nube en diálogo local
                onSelect = { ruta ->
                    coroutineScope.launch {
                        val isFinalized = viewModel.isRutaFinalized(ruta.id)
                        if ((currentAppRole == "guia" || currentAppRole == "visor") && !isFinalized) {
                            Toast.makeText(context, "No se puede abrir como guía ni como visor una ruta que se está registrando (trazando).", Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.checkAndResolveRoute(
                                context = context,
                                routeId = ruta.id,
                                onAlreadyCorrect = {
                                    showSelectRutaDialog = false
                                    openRouteWithRole(ruta)
                                },
                                onResolveNeeded = {
                                    Toast.makeText(context, "Faltan fotos o datos. Re-descargando para resolver...", Toast.LENGTH_SHORT).show()
                                    viewModel.selectRuta(ruta)
                                    showSelectRutaDialog = false
                                    showCloudDownloadDialog = true
                                    viewModel.downloadRouteFromCloud(context = context, routeId = ruta.id)
                                }
                            )
                        }
                    }
                },
                onAddClick = {
                    coroutineScope.launch {
                        val activeRoute = viewModel.getActiveRoute()
                        if (activeRoute != null) {
                            viewModel.selectRuta(activeRoute)
                            viewModel.stopGuiding()
                            showSelectRutaDialog = false
                            Toast.makeText(context, "Se abrió la ruta activa '${activeRoute.nombre}' para que la finalices o borres.", Toast.LENGTH_LONG).show()
                        } else if (senderista?.rol?.equals("G", ignoreCase = true) == true) {
                            showSelectRutaDialog = false
                            showAddRutaDialog = true
                        } else {
                            Toast.makeText(context, "No tienes permisos para agregar rutas.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onCloudImportClick = {
                    showSelectRutaDialog = false
                    showCloudImportDialog = true
                },
                onSyncClick = { viewModel.triggerManualCloudSync(context) },
                onDelete = { ruta ->
                    viewModel.deleteRuta(ruta)
                },
                onDismiss = { showSelectRutaDialog = false }
            )
        }

        if (showAddRutaDialog) {
            AddRutaDialog(
                lat = location?.latitude ?: 0.0,
                lng = location?.longitude ?: 0.0,
                onAdd = { nombre, descripcion, publico ->
                    viewModel.addRuta(nombre, descripcion, location?.latitude ?: 0.0, location?.longitude ?: 0.0, publico)
                    currentAppRole = "registrar"
                    viewModel.prefs.appRole = "registrar"
                    showAddRutaDialog = false
                },
                onDismiss = { showAddRutaDialog = false }
            )
        }

        if (showEditRutaDialog && selectedRoute != null) {
            EditRutaDialog(
                ruta = selectedRoute!!,
                onSave = { nombre, descripcion ->
                    val updatedRoute = selectedRoute!!.copy(
                        nombre = nombre,
                        descripcion = descripcion
                    )
                    viewModel.updateRuta(updatedRoute)
                    showEditRutaDialog = false
                },
                onDismiss = { showEditRutaDialog = false }
            )
        }



        if (showSoundsDialog) {
            SenderistaSoundsDialog(
                onPlayShort = { viewModel.playShortBeep() },
                onPlayLong = { viewModel.playLongBeep() },
                onPlayTwoShort = { viewModel.playTwoShortBeeps() },
                onPlayEmergencyHelp = { viewModel.playEmergencyHelp() },
                onPlayEmergencyAlert = { viewModel.playEmergencyAlert() },
                onPlaySos = { viewModel.playSosSignal() },
                onPlayBeacon911 = {
                    val senderistaObj = senderista
                    if (senderistaObj == null) {
                        android.widget.Toast.makeText(context, "Por favor, registra el perfil del senderista primero.", android.widget.Toast.LENGTH_SHORT).show()
                    } else if (senderistaObj.idBeacon.isNullOrBlank()) {
                        android.widget.Toast.makeText(context, "El senderista no tiene ID de Beacon.", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.startBeaconEmission(
                            idBeacon = senderistaObj.idBeacon,
                            isEmergency = true,
                            onSuccess = {
                                android.widget.Toast.makeText(context, "Emisión 911 de Beacon iniciada", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { err ->
                                android.widget.Toast.makeText(context, "Error al iniciar emisión 911: $err", android.widget.Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                },
                onDismiss = {
                    viewModel.stopEmergencySound()
                    showSoundsDialog = false
                }
            )
        }

        if (showSenderistasList) {
            ListadoSenderistasDialog(
                viewModel = viewModel,
                onDismiss = { showSenderistasList = false }
            )
        }
        


        if (showGalleryViewer && selectedRoute != null) {
            GalleryViewerDialog(
                routeName = selectedRoute!!.nombre,
                onDismiss = { showGalleryViewer = false }
            )
        }

        selectedPuntoForDetail?.let { punto ->
            val isCreator = selectedRoute?.let { route ->
                route.telefono.isEmpty() || route.telefono == (senderista?.telefono ?: "")
            } ?: true

            PuntoRutaDetailDialog(
                punto = punto,
                dictatedText = dictatedText,
                onLaunchMic = { onLaunchVoiceRecognition() },
                onClearDictatedText = { viewModel.clearDictatedText() },
                onSave = { newDesc, newEstado ->
                    val updated = selectedPuntoForDetail?.copy(
                        descripcion = newDesc,
                        estado = newEstado
                    )
                    if (updated != null) {
                        viewModel.updatePuntoRuta(updated)
                        selectedPuntoForDetail = updated
                    }
                },
                onSetAsFinRuta = {
                    val cur = selectedPuntoForDetail ?: punto
                    val updated = cur.copy(
                        esFinRuta = true,
                        descripcion = if (cur.descripcion.isEmpty() || cur.descripcion.startsWith("Faro #")) "Fin de Ruta" else cur.descripcion
                    )
                    viewModel.updatePuntoRuta(updated)
                    selectedPuntoForDetail = updated
                    Toast.makeText(context, "Punto establecido como Fin de Ruta. La ruta ha finalizado.", Toast.LENGTH_LONG).show()
                },
                onDismiss = {
                    selectedPuntoForDetail = null
                    viewModel.clearCapturedPhoto()
                    viewModel.clearDictatedText()
                },
                onNext = {
                    val idx = selectedRoutePoints.indexOfFirst { it.id == punto.id }
                    if (idx != -1 && idx < selectedRoutePoints.size - 1) {
                        selectedPuntoForDetail = selectedRoutePoints[idx + 1]
                    } else {
                        Toast.makeText(context, "Último punto de la ruta", Toast.LENGTH_SHORT).show()
                    }
                },
                onPrevious = {
                    val idx = selectedRoutePoints.indexOfFirst { it.id == punto.id }
                    if (idx > 0) {
                        selectedPuntoForDetail = selectedRoutePoints[idx - 1]
                    } else {
                        Toast.makeText(context, "Primer punto de la ruta", Toast.LENGTH_SHORT).show()
                    }
                },
                isLastPoint = (selectedRoutePoints.lastOrNull()?.id == punto.id),
                isCreator = isCreator,
                routeHasEnd = selectedRoutePoints.any { it.esFinRuta },
                onRecoverPhoto = { onFinished ->
                    viewModel.recoverPhotoForWaypoint(context, punto) { success, errorMsg, localPath ->
                        onFinished(success)
                        if (success && localPath != null) {
                            selectedPuntoForDetail = punto.copy(fotoPath = localPath)
                        } else if (!success && errorMsg != null) {
                            recoveryErrorMsg = errorMsg
                        }
                    }
                }
            )
        }

        puntoToDeleteConfirm?.let { punto ->
            AlertDialog(
                onDismissRequest = { puntoToDeleteConfirm = null },
                containerColor = ColorTekinsCardBg,
                titleContentColor = ColorTextoPrincipal,
                textContentColor = ColorTextoSecundario,
                title = { Text("Eliminar Punto", fontWeight = FontWeight.Bold) },
                text = { Text("¿Estás seguro de que deseas eliminar permanentemente el punto '${punto.descripcion}'?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deletePuntoRuta(punto)
                            puntoToDeleteConfirm = null
                        }
                    ) {
                        Text("Eliminar", color = ColorTekinsAlert, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { puntoToDeleteConfirm = null }) {
                        Text("Cancelar", color = ColorTextoSecundario)
                    }
                }
            )
        }

        recoveryErrorMsg?.let { errorMsg ->
            AlertDialog(
                onDismissRequest = { recoveryErrorMsg = null },
                containerColor = ColorTekinsCardBg,
                titleContentColor = ColorTextoPrincipal,
                textContentColor = ColorTextoSecundario,
                title = { Text("Detalles de la foto", fontWeight = FontWeight.Bold) },
                text = { Text(errorMsg) },
                confirmButton = {
                    TextButton(onClick = { recoveryErrorMsg = null }) {
                        Text("Aceptar", color = ColorTekinsAccent, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (showSettings) {
            SettingsDialog(
                initialDistance = viewModel.prefs.alertDistance,
                initialShowInactive = viewModel.prefs.showInactive,
                initialPreferredMap = viewModel.prefs.preferredMap,
                initialEnableBeeps = viewModel.prefs.enableBeeps,
                initialArrivalTolerance = viewModel.prefs.arrivalTolerance,
                initialTapDistanceLimit = viewModel.prefs.tapDistanceLimit,
                initialAppRole = currentAppRole,
                initialRangoDiasSalidas = viewModel.prefs.rangoDiasBusquedaSalidas,
                initialRangoKmSalidas = viewModel.prefs.rangoKmBusquedaSalidas,
                initialEmergencyTimeoutMinutes = viewModel.prefs.emergencyTimeoutMinutes,
                onEditSenderistaClick = {
                    showSettings = false
                    showSenderistaDialog = true
                },
                onExportDbClick = {
                    viewModel.exportDatabaseToDownloads(context)
                },
                onSave = { distance, showInc, prefMap, beeps, tolerance, tapLimit, role, diasSalidas, kmSalidas, emergencyTimeout ->
                    viewModel.updateSettings(distance, showInc, prefMap, beeps, tolerance, tapLimit, role, emergencyTimeout)
                    viewModel.prefs.rangoDiasBusquedaSalidas = diasSalidas
                    viewModel.prefs.rangoKmBusquedaSalidas = kmSalidas
                    currentAppRole = role
                    showSettings = false
                },
                onDismiss = { showSettings = false }
            )
        }

        if (showSalidasDialog) {
            SalidasDialog(
                viewModel = viewModel,
                onSelectRoute = { routeEntity ->
                    openRouteWithRole(routeEntity)
                },
                onDismiss = { showSalidasDialog = false }
            )
        }

        if (showUpcomingOutingWarningDialog != null) {
            UpcomingOutingWarningDialog(
                outing = showUpcomingOutingWarningDialog!!,
                onDismiss = { showUpcomingOutingWarningDialog = null }
            )
        }

        if (showSenderistaDialog) {
            SenderistaDialog(
                currentSenderista = senderista,
                agrupaciones = agrupaciones,
                onSave = { profile ->
                    viewModel.saveSenderista(profile)
                    showSenderistaDialog = false
                },
                onDismiss = {
                    showSenderistaDialog = false
                    if (senderista == null) {
                        onExitApp()
                    }
                },
                onDeleteAccount = {
                    showSenderistaDialog = false
                    showDeleteAccountDialog = true
                }
            )
        }

        if (showDeleteAccountDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAccountDialog = false },
                title = { Text("Eliminar Cuenta", color = ColorTekinsAlert) },
                text = { Text("La eliminación de la cuenta es irreversible. Se borrarán todos tus datos personales, rutas, y registros de la nube y del dispositivo. ¿Estás seguro de que deseas proceder?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteAccount { success, error ->
                                showDeleteAccountDialog = false
                                if (success) {
                                    Toast.makeText(context, "Cuenta eliminada correctamente.", Toast.LENGTH_LONG).show()
                                    (context as? android.app.Activity)?.finishAffinity()
                                } else {
                                    Toast.makeText(context, "Error al eliminar: $error", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAlert)
                    ) {
                        Text("Eliminar de forma irreversible", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAccountDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showDriveSyncDialog && selectedRoute != null) {
            val isSynced = viewModel.prefs.isRouteSyncedToCloud(selectedRoute!!.id)
            DriveSyncDialog(
                viewModel = viewModel,
                route = selectedRoute!!,
                isSynced = isSynced,
                onDismiss = {
                    viewModel.resetSyncState()
                    showDriveSyncDialog = false
                }
            )
        }

        if (showCloudImportDialog) {
            CloudRoutesDialog(
                viewModel = viewModel,
                localRoutes = rutas,
                location = location,
                onImportSuccess = { importedRoute ->
                    openRouteWithRole(importedRoute)
                    showCloudImportDialog = false
                },
                onDismiss = {
                    showCloudImportDialog = false
                }
            )
        }

        if (showCloudDownloadDialog && selectedRoute != null) {
            CloudDownloadDialog(
                viewModel = viewModel,
                routeId = selectedRoute!!.id,
                routeName = selectedRoute!!.nombre,
                onDismiss = {
                    viewModel.resetCloudSyncState()
                    showCloudDownloadDialog = false
                }
            )
        }

        if (showDistanceVerificationDialog && selectedRoute != null) {
            RutaDetailDialog(
                ruta = selectedRoute!!,
                tracePoints = selectedRouteTrace,
                userLocation = location,
                onDismiss = { showDistanceVerificationDialog = false }
            )
        }

        if (showRouteTooShortDialog) {
            AlertDialog(
                onDismissRequest = { showRouteTooShortDialog = false },
                title = { Text("Ruta Demasiado Corta") },
                text = { Text("La ruta actual no alcanza el límite mínimo de ${senderista?.minimoLongitud ?: 4.0} km configurado en tu perfil. Si decides cerrarla ahora, se borrarán todos los datos y fotos locales de esta ruta. ¿Qué deseas hacer?") },
                confirmButton = {
                    TextButton(onClick = {
                        showRouteTooShortDialog = false
                        selectedRoute?.let { 
                            viewModel.clearLocalPhotosAndPointsAndTrace(it)
                            viewModel.deleteRuta(it)
                            viewModel.selectRuta(null)
                        }
                    }) {
                        Text("Cerrar y Borrar", color = ColorTekinsAlert)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRouteTooShortDialog = false }) {
                        Text("Seguir Haciendo la Ruta")
                    }
                }
            )
        }

        if (showConfirmFinRutaDialog) {
            var confirmText by remember { mutableStateOf("") }
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            
            LaunchedEffect(Unit) {
                // Sonido de alerta
                viewModel.playLongBeep()
                // Vibración
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(1000, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(1000)
                }
            }

            AlertDialog(
                onDismissRequest = { showConfirmFinRutaDialog = false },
                containerColor = ColorTekinsCardBg,
                titleContentColor = ColorTextoPrincipal,
                textContentColor = ColorTextoSecundario,
                title = { Text("Finalizar Ruta", fontWeight = FontWeight.Bold, color = ColorTekinsAlert) },
                text = { 
                    Column {
                        Text("¿Estás seguro de que deseas finalizar la ruta? Se tomará una fotografía final y se guardará el recorrido.")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Escribe la palabra 'correcto' para confirmar:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmText,
                            onValueChange = { confirmText = it },
                            singleLine = true,
                            label = { Text("Escribe 'correcto' para finalizar") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorTekinsAlert,
                                focusedLabelColor = ColorTekinsAlert,
                                cursorColor = ColorTekinsAlert
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmFinRutaDialog = false
                            if (location != null) {
                                viewModel.clearCapturedPhoto()
                                viewModel.isFinRutaPending = true
                                viewModel.locationSnapshotForPoint = location
                                viewModel.isCapturingPhotoForPoint = true
                                val file = File(context.filesDir, "foto_${System.currentTimeMillis()}.jpg")
                                onOpenCamera(file)
                            } else {
                                Toast.makeText(context, "Se perdió la señal de GPS.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorTekinsAlert),
                        enabled = confirmText.trim().equals("correcto", ignoreCase = true)
                    ) {
                        Text("Sí, finalizar", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmFinRutaDialog = false }) {
                        Text("Cancelar", color = ColorTextoSecundario)
                    }
                }
            )
        }
    }
}

/**
 * CABECERA SUPERIOR: Estilo Mountain/Forest
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderBar(
    isGpsActive: Boolean,
    location: Location?,
    selectedRoute: RutaEntity?,
    points: List<PuntoRutaEntity>,
    tracePoints: List<TrazaRutaEntity> = emptyList(),
    isGuideMode: Boolean,
    currentAppRole: String,
    senderistaRol: String = "S",
    onSelectRutaClick: () -> Unit,
    onClearRouteClick: () -> Unit,
    onMinimizeApp: () -> Unit,
    onExitApp: () -> Unit,
    onSwipeLeft: () -> Unit,
    onHeaderDoubleTap: () -> Unit = {},
    onHeaderClick: () -> Unit = {},
    onDriveSyncClick: () -> Unit = {},
    isFinalized: Boolean = false,
    isSynced: Boolean = false,
    onCloudImportClick: () -> Unit,
    onAddRutaClick: () -> Unit,
    onSalidasClick: () -> Unit,
    onPerfilClick: () -> Unit,
    isPointsListReversed: Boolean = false,
    onPointsOrderChange: (Boolean) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    is911Active: Boolean = false,
    on911Click: () -> Unit = {}
) {
    val context = LocalContext.current

    val totalDistance = if (selectedRoute != null && points.isNotEmpty()) {
        RouteStatsCalculator.calculateBeaconDistance(points)
    } else {
        null
    }

    val totalTraceDistance = if (selectedRoute != null && tracePoints.isNotEmpty()) {
        RouteStatsCalculator.calculateTraceDistance(tracePoints)
    } else {
        null
    }

    val activeColor = when (currentAppRole) {
        "guia" -> Color(0xFF475569)      // Slate 600 (Gris oscuro legible)
        "registrar" -> Color(0xFF15803D) // Green 700 (Verde oscuro legible)
        else -> Color(0xFF0F766E)        // Teal 700 (Verde azulado corporativo)
    }

    val finalDistance = maxOf(totalTraceDistance ?: 0f, totalDistance ?: 0f)

    val (statusText, statusTextColor) = when {
        selectedRoute != null && (finalDistance > 0f || tracePoints.isNotEmpty() || points.isNotEmpty()) -> {
            val distStr = if (finalDistance >= 1000f) {
                String.format(java.util.Locale.US, "Distancia total: %.2f km", finalDistance / 1000f)
            } else {
                String.format(java.util.Locale.US, "Distancia total: %.0f m", finalDistance)
            }
            distStr to activeColor
        }
        !isGpsActive -> "GPS APAGADO" to ColorTekinsAlert
        location == null -> {
            "CONECTANDO GPS..." to Color(0xFFD97706)
        }
        else -> {
            String.format(java.util.Locale.US, "GPS ACTIVO (±%.1fm)", location.accuracy) to activeColor
        }
    }

    val headerBgColor = when (currentAppRole) {
        "guia" -> Color(0xFF64748B).copy(alpha = 0.15f)
        "registrar" -> Color(0xFFFAF9F6).copy(alpha = 0.12f)
        else -> ColorTekinsAccent.copy(alpha = 0.15f)
    }

    val headerBorderColor = when (currentAppRole) {
        "guia" -> Color(0xFF64748B).copy(alpha = 0.4f)
        "registrar" -> Color(0xFFFAF9F6).copy(alpha = 0.4f)
        else -> ColorTekinsAccent.copy(alpha = 0.4f)
    }

    val badgeText = when (currentAppRole) {
        "guia" -> "GUÍA"
        "registrar" -> "TRAZAR"
        else -> "VISOR"
    }

    val badgeTextColor = activeColor

    val badgeBg = when (currentAppRole) {
        "guia" -> Color(0xFF64748B).copy(alpha = 0.2f)
        "registrar" -> Color(0xFF15803D).copy(alpha = 0.15f)
        else -> ColorTekinsAccent.copy(alpha = 0.25f)
    }

    val badgeIcon = when (currentAppRole) {
        "guia" -> Icons.Default.Explore
        "registrar" -> Icons.Default.AddLocation
        else -> Icons.Default.Visibility
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp),
        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
        colors = CardDefaults.cardColors(containerColor = headerBgColor),
        border = BorderStroke(1.dp, headerBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Botón Izquierdo: Siempre menú de Rutas
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                var isMenuExpanded by remember { mutableStateOf(false) }

                Box {
                    IconButton(
                        onClick = {
                            if (selectedRoute != null && !isFinalized) {
                                onSelectRutaClick()
                            } else {
                                isMenuExpanded = true
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = ColorTekinsCardMuted,
                            contentColor = ColorTekinsAccent
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terrain,
                            contentDescription = "Opciones de Ruta",
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Menú para Rutas
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { isMenuExpanded = false },
                        modifier = Modifier.background(Color(0xFFFAF9F6))
                    ) {
                        Text(
                            text = "Rutas",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        HorizontalDivider(
                            color = ColorTekinsCardMuted.copy(alpha = 0.3f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                        )
                        DropdownMenuItem(
                            text = { Text("1. Locales", color = ColorTekinsDarkBg) },
                            onClick = {
                                isMenuExpanded = false
                                onSelectRutaClick()
                            },
                            leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = ColorTekinsPrimary) }
                        )
                        DropdownMenuItem(
                            text = { Text("2. Ver otras rutas", color = ColorTekinsDarkBg) },
                            onClick = {
                                isMenuExpanded = false
                                onCloudImportClick()
                            },
                            leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null, tint = ColorTekinsPrimary) }
                        )
                        DropdownMenuItem(
                            text = { Text("3. Salidas", color = ColorTekinsDarkBg) },
                            onClick = {
                                isMenuExpanded = false
                                onSalidasClick()
                            },
                            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = ColorTekinsPrimary) }
                        )
                        if (senderistaRol.equals("G", ignoreCase = true)) {
                            DropdownMenuItem(
                                text = { Text("Agregar nueva ruta", color = ColorTekinsDarkBg, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    isMenuExpanded = false
                                    onAddRutaClick()
                                },
                                leadingIcon = { 
                                    Icon(
                                        imageVector = Icons.Default.Add, 
                                        contentDescription = null, 
                                        tint = ColorTekinsPrimary,
                                        modifier = Modifier.size(24.dp)
                                    ) 
                                }
                            )
                        }

                        if (selectedRoute != null) {
                            HorizontalDivider(
                                color = ColorTekinsCardMuted.copy(alpha = 0.3f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            DropdownMenuItem(
                                text = { Text("Cerrar ruta activa", color = ColorTekinsAlert) },
                                onClick = {
                                    isMenuExpanded = false
                                    onClearRouteClick()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = ColorTekinsAlert,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                if (selectedRoute != null && (currentAppRole == "guia" || currentAppRole == "visor")) {
                    Spacer(modifier = Modifier.width(4.dp))
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(
                                    text = if (isPointsListReversed) "Viendo puntos del final al inicio" else "Viendo puntos del inicio al final",
                                    fontSize = 12.sp
                                )
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        IconButton(
                            onClick = { onPointsOrderChange(!isPointsListReversed) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Alternar orden",
                                tint = if (isPointsListReversed) activeColor else ColorTextoSecundario,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Centro: Título e Info de GPS con gesto de swipe a la izquierda para cerrar
            var totalDragX by remember { mutableStateOf(0f) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(selectedRoute) {
                        detectTapGestures(
                            onTap = {
                                if (selectedRoute != null) {
                                    onHeaderClick()
                                }
                            },
                            onDoubleTap = {
                                if (selectedRoute != null && !isGuideMode) {
                                    onHeaderDoubleTap()
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { totalDragX = 0f },
                            onDragEnd = {
                                if (totalDragX < -80f) {
                                    onSwipeLeft()
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount
                            }
                        )
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = selectedRoute?.nombre ?: "",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedRoute != null) activeColor else ColorTekinsDarkBg,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (selectedRoute != null) {
                            Text(
                                text = selectedRoute.fecha,
                                fontSize = 10.sp,
                                color = ColorTextoSecundario,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }


                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusTextColor,
                    textAlign = TextAlign.Center
                )
            }

            // Botón Derecho: Menú de Aplicación / Cerrar
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentAppRole == "guia") {
                    var clicks911 by remember { mutableStateOf(0) }
                    LaunchedEffect(clicks911) {
                        if (clicks911 > 0) {
                            kotlinx.coroutines.delay(2500)
                            clicks911 = 0
                        }
                    }
                    val btnColor = if (is911Active) Color.Black else ColorTekinsAlert
                    val btnText = if (clicks911 > 0) "$clicks911" else if (is911Active) "FIN" else "911"
                    val btnTextColor = if (is911Active) Color.White else Color.White
                    
                    Button(
                        onClick = { 
                            clicks911++
                            if (clicks911 >= 4) {
                                clicks911 = 0
                                on911Click()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .size(44.dp)
                            .padding(end = 6.dp),
                        shape = CircleShape
                    ) {
                        Text(btnText, color = btnTextColor, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    }
                }
                
                var isRightMenuExpanded by remember { mutableStateOf(false) }

                Box {
                    val context = androidx.compose.ui.platform.LocalContext.current
                IconButton(
                    onClick = { isRightMenuExpanded = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = ColorTekinsCardMuted,
                        contentColor = ColorTekinsAccent
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Opciones de Cierre",
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = isRightMenuExpanded,
                    onDismissRequest = { isRightMenuExpanded = false },
                    modifier = Modifier.background(Color(0xFFFAF9F6))
                ) {
                    Text(
                        text = "Aplicación",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    HorizontalDivider(
                        color = ColorTekinsCardMuted.copy(alpha = 0.3f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                    )



                    DropdownMenuItem(
                        text = { Text("Perfil del Senderista", color = ColorTekinsDarkBg) },
                        onClick = {
                            isRightMenuExpanded = false
                            onPerfilClick()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = ColorTekinsPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Configuración", color = ColorTekinsDarkBg) },
                        onClick = {
                            isRightMenuExpanded = false
                            onSettingsClick()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = ColorTekinsPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Minimizar radar (segundo plano)", color = ColorTekinsDarkBg) },
                        onClick = {
                            isRightMenuExpanded = false
                            onMinimizeApp()
                        },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = ColorTekinsPrimary) }
                    )

                    DropdownMenuItem(
                        text = { Text("Cerrar aplicación", color = ColorTekinsAlert) },
                        onClick = {
                            isRightMenuExpanded = false
                            onExitApp()
                        },
                        leadingIcon = { Icon(Icons.Default.Cancel, contentDescription = null, tint = ColorTekinsAlert) }
                    )

                    if (selectedRoute != null) {
                        HorizontalDivider(
                            color = ColorTekinsCardMuted.copy(alpha = 0.3f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        /*
                        // INSTRUCCIONES: Para habilitar el Respaldo en Google Drive,
                        // simplemente descomenta el siguiente bloque de código.
                        DropdownMenuItem(
                            text = { Text("Respaldo Google Drive", color = ColorTekinsDarkBg) },
                            onClick = {
                                isRightMenuExpanded = false
                                onDriveSyncClick()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_google_drive),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        )
                        */
                    }
                }
            }
        }
    }
}
}

/**
 * PIE DE PÁGINA (DOCK DE ACCIONES REESTRUCTURADO)
 */
@Composable
fun FooterBar(
    hasRouteSelected: Boolean,
    isBeaconActive: Boolean,
    isGuideActive: Boolean,
    onBeaconToggle: (Boolean) -> Unit,
    onSoundsClick: () -> Unit,
    onMapClick: () -> Unit,
    onListClick: () -> Unit
) {
    val footerBgColor = if (isBeaconActive) Color(0xFF0F2537) else ColorTekinsCardBg
    val footerBorderColor = if (isBeaconActive) Color(0xFF38BDF8).copy(alpha = 0.4f) else ColorTekinsAccent.copy(alpha = 0.3f)
    
    val settingsIconColor = if (isBeaconActive) Color.White.copy(alpha = 0.7f) else ColorTextoSecundario
    val mapIconColor = if (isBeaconActive) {
        if (hasRouteSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.3f)
    } else {
        if (hasRouteSelected) ColorTekinsAccent else ColorTextoSecundario.copy(alpha = 0.4f)
    }
    val bluetoothIconColor = if (isBeaconActive) Color(0xFF38BDF8) else ColorTextoSecundario.copy(alpha = 0.6f)
    val soundsIconColor = if (isBeaconActive) Color(0xFF38BDF8) else ColorTekinsAccent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        colors = CardDefaults.cardColors(containerColor = footerBgColor),
        border = BorderStroke(1.dp, footerBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // Ver Mapa (Deshabilitado si no hay ruta seleccionada)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Abre el mapa con el recorrido del sendero.", fontSize = 12.sp) } },
                state = rememberTooltipState()
            ) {
                IconButton(
                    onClick = onMapClick,
                    enabled = hasRouteSelected
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Ver Mapa",
                        tint = mapIconColor,
                        modifier = Modifier.size(40.dp) // Aumentado para distribución
                    )
                }
            }

            // Transmisor Beacon (Faro)
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { 
                    PlainTooltip { 
                        Text(
                            text = if (isBeaconActive) "Faro ENCENDIDO: Visible para otros senderistas." else "Faro APAGADO: Oculto para otros senderistas.",
                            fontSize = 12.sp
                        ) 
                    } 
                },
                state = rememberTooltipState()
            ) {
                IconButton(
                    onClick = { onBeaconToggle(!isBeaconActive) }
                ) {
                    val imageRes = if (isBeaconActive) R.drawable.ic_lighthouse_on else R.drawable.ic_lighthouse_off
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = "Beacon",
                        modifier = Modifier
                            .size(48.dp) // Aumentado
                            .clip(CircleShape)
                            .border(2.dp, bluetoothIconColor, CircleShape)
                    )
                }
            }

            // Botón Monitoreo (sólo visible para Guías)
            if (isGuideActive) {
                val listIconColor = if (isBeaconActive) Color(0xFF38BDF8) else ColorTekinsAccent
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Ver a los senderistas en ruta que tengan el faro prendido.", fontSize = 12.sp) } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = onListClick) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Monitoreo",
                            tint = listIconColor,
                            modifier = Modifier.size(40.dp) // Aumentado
                        )
                    }
                }
            }

            // Botón Senderista (Pitidos/Silbatos) - Al final, con colores llamativos
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Herramientas de ayuda y emergencia en senderismo.", fontSize = 12.sp) } },
                state = rememberTooltipState()
            ) {
                IconButton(onClick = onSoundsClick) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_whistle_bright),
                        contentDescription = "Silbatos",
                        modifier = Modifier
                            .size(48.dp) // Aumentado
                            .clip(CircleShape)
                            .border(2.dp, Color(0xFFFF9800), CircleShape) // Borde naranja brillante para resaltar
                    )
                }
            }
        }
    }
}

@Composable
fun ListadoSenderistasDialog(
    viewModel: RutasViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val participants by viewModel.activeOutingParticipants.collectAsState()
    val isScanning by viewModel.isBleScanning.collectAsState()
    val userLocation by viewModel.currentLocation.collectAsState()
    val senderistaUiState by viewModel.senderistaUiState.collectAsState()
    val idBeacon = senderistaUiState.senderista?.idBeacon ?: "Sin ID"
    val selectedInterval by viewModel.trackingRefreshIntervalMs.collectAsState()
    val activeEmergencies by viewModel.activeEmergencies.collectAsState()
    val isGuiding by viewModel.isGuiding.collectAsState()

    var selectedParticipantForEmergency by remember { mutableStateOf<ParticipanteEntity?>(null) }
    var selectedTab by remember { mutableStateOf(if (isGuiding) 1 else 0) } // 1 = Mapa, 0 = Lista



    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = ColorTekinsDarkBg,
            border = BorderStroke(1.dp, ColorTekinsAccent.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Rastreo de senderista",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ColorTextoPrincipal
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isScanning) Color(0xFF10B981) else Color(0xFFEF4444),
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isScanning) buildAnnotatedString {
                                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("($idBeacon)")
                                    }
                                    append("..Buscando senderistas...")
                                } else buildAnnotatedString { append("Escaneo Inactivo") },
                                fontSize = 12.sp,
                                color = ColorTextoSecundario
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = ColorTextoSecundario)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tabs Selector (Segmented control)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ColorTekinsCardBg.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val listSelected = selectedTab == 0
                    val mapSelected = selectedTab == 1
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (listSelected) ColorTekinsAccent else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Lista de senderistas",
                            color = if (listSelected) Color.White else ColorTextoSecundario,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (mapSelected) ColorTekinsAccent else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Mapa",
                            color = if (mapSelected) Color.White else ColorTextoSecundario,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Refresh frequency selector
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Actualizar mapa y lista:",
                        color = ColorTextoSecundario,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            1500L to "1.5s",
                            3000L to "3s",
                            5000L to "5s"
                        ).forEach { (ms, label) ->
                            val isSelected = selectedInterval == ms
                            val bg = if (isSelected) ColorTekinsAccent else ColorTekinsCardBg
                            val borderCol = if (isSelected) Color.White.copy(alpha = 0.3f) else Color.Transparent
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                                    .clickable { viewModel.trackingRefreshIntervalMs.value = ms }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else ColorTextoSecundario,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (participants.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay participantes detectados en el rango de Bluetooth.",
                            color = ColorTextoSecundario,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    if (selectedTab == 1) {
                        // Leaflet tracking map for nearby hikers (Full screen weight)
                        TrackingMapView(
                            participants = participants.filter { it.distanciaEstimada != "Fuera de alcance" },
                            activeEmergencies = activeEmergencies,
                            currentLocation = userLocation,
                            onParticipantClick = { participant ->
                                selectedParticipantForEmergency = participant
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, ColorTekinsAccent.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        )
                    } else {
                        // Columna para contener la lista y el resumen al final
                        Column(modifier = Modifier.weight(1f)) {
                            // LazyColumn for participants list (Full screen weight)
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(participants) { participant ->
                                    SenderistaTrackingRow(
                                        participant = participant,
                                        isEmergency = activeEmergencies.contains(participant.idBeacon),
                                        onClick = { selectedParticipantForEmergency = participant },
                                        onDoubleClick = {
                                            participant.idBeacon?.let { id ->
                                                viewModel.acknowledgeEmergency(id)
                                                android.widget.Toast.makeText(context, "Alarma silenciada para ${participant.nombreSenderista}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            }
                            
                            val total = participants.size
                            val prendidos = participants.count { it.distanciaEstimada != "Fuera de alcance" && it.distanciaEstimada != null }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Total senderistas: $total | Conectados: $prendidos",
                                    color = Color.Yellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedParticipantForEmergency?.let { p ->
        AlertDialog(
            onDismissRequest = { selectedParticipantForEmergency = null },
            title = {
                Text(
                    text = "EMERGENCIA",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = Color.Black
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Participante: ${p.nombreSenderista}",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "A quién avisar: ${p.aQuienesAvisar ?: "No especificado"}",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    
                    if (!p.aQuienAvisarTelefono1.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { dialPhoneNumber(context, p.aQuienAvisarTelefono1) }
                                .background(Color.White)
                                .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Llamar 1",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Llamar Contacto 1",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.DarkGray
                                )
                                Text(
                                    text = p.aQuienAvisarTelefono1,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    if (!p.aQuienAvisarTelefono2.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { dialPhoneNumber(context, p.aQuienAvisarTelefono2) }
                                .background(Color.White)
                                .border(2.dp, Color.Black, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Llamar 2",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Llamar Contacto 2",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.DarkGray
                                )
                                Text(
                                    text = p.aQuienAvisarTelefono2,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    val validTipoSangre = !p.tipoSangre.isNullOrBlank() && p.tipoSangre != "No especificado" && p.tipoSangre != "No agregó el tipo de sangre"
                    val hasHealthData = validTipoSangre || !p.alergias.isNullOrBlank() || !p.enfermedadesCronicas.isNullOrBlank()
                    
                    if (hasHealthData && !p.codigoConfianza.isNullOrBlank()) {
                        var enteredCode by remember { mutableStateOf("") }
                        val isCodeValid = enteredCode.equals(p.codigoConfianza, ignoreCase = true)
                        
                        if (!isCodeValid) {
                            OutlinedTextField(
                                value = enteredCode,
                                onValueChange = { enteredCode = it.uppercase() },
                                label = { Text("Código de Confianza") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Black,
                                    unfocusedBorderColor = Color.Gray,
                                    focusedLabelColor = Color.Black,
                                    unfocusedLabelColor = Color.Gray
                                )
                            )
                            Text(
                                text = "Ingrese el código para ver datos de salud.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        } else {
                            val ageDisplay = p.edad?.let { " | Edad: $it años" } ?: ""
                            Text(
                                text = "Tipo de Sangre: ${p.tipoSangre ?: "No especificado"}$ageDisplay",
                                color = Color(0xFFD32F2F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            if (!p.alergias.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Alergias: ${p.alergias}",
                                    color = Color(0xFFD32F2F),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!p.enfermedadesCronicas.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Enfermedades Crónicas: ${p.enfermedadesCronicas}",
                                    color = Color.Black,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (hasHealthData) {
                        val ageDisplay = p.edad?.let { " | Edad: $it años" } ?: ""
                        Text(
                            text = "Tipo de Sangre: ${p.tipoSangre ?: "No especificado"}$ageDisplay",
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (!p.alergias.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Alergias: ${p.alergias}",
                                color = Color(0xFFD32F2F),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!p.enfermedadesCronicas.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enfermedades Crónicas: ${p.enfermedadesCronicas}",
                                color = Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedParticipantForEmergency = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text("Cerrar", color = Color(0xFFFFEB3B), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        p.idBeacon?.let { id ->
                            android.widget.Toast.makeText(context, "Consultando en la nube...", android.widget.Toast.LENGTH_SHORT).show()
                            viewModel.forceFetchParticipant(id) { updatedParticipant ->
                                if (updatedParticipant != null) {
                                    selectedParticipantForEmergency = updatedParticipant
                                }
                            }
                        } ?: run {
                            android.widget.Toast.makeText(context, "Este senderista no tiene Beacon", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    enabled = false // Deshabilitado temporalmente a petición del usuario
                ) {
                    Text("Actualizar de Nube", color = Color.White)
                }
            },
            containerColor = Color(0xFFFFEB3B)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SenderistaTrackingRow(
    participant: ParticipanteEntity,
    isEmergency: Boolean = false,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit = {}
) {
    val isOutOfRange = participant.distanciaEstimada == "Fuera de alcance" || participant.distanciaEstimada == null
    // ALTA VISIBILIDAD: Fondo amarillo brillante para emergencias/monitoreo en exteriores. 
    val cardBg = if (isEmergency) Color.Red else if (isOutOfRange) Color.DarkGray else Color(0xFFFFEB3B) 
    val borderCol = if (isEmergency) Color.White else if (isOutOfRange) Color.Gray else Color.Black
    val distanceColor = if (isEmergency || isOutOfRange) Color.White else Color.Black
    val textColor = if (isEmergency || isOutOfRange) Color.White else Color.Black
    val subTextColor = if (isEmergency || isOutOfRange) Color.White.copy(alpha=0.8f) else Color.DarkGray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = onDoubleClick
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(2.dp, borderCol)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = participant.nombreSenderista,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Beacon: ${participant.idBeacon ?: "No especificado"}",
                    fontSize = 14.sp,
                    color = subTextColor
                )
                val infoLine = buildString {
                    if (participant.sexo != null) append("Sexo: ${participant.sexo}")
                    if (participant.edad != null) {
                        if (isNotEmpty()) append(", ")
                        append("Edad: ${participant.edad} años")
                    }
                }
                if (infoLine.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = infoLine,
                        fontSize = 12.sp,
                        color = subTextColor
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
                if (isOutOfRange) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_lighthouse_off),
                            contentDescription = "Desconectado",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color(0xFFE0E0E0), CircleShape) // Borde gris claro para contrastar sobre gris oscuro nocturno
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No Veo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE0E0E0)
                        )
                    }
                } else {
                    val rssi = participant.ultimoRssi ?: -100
                    val iconColor = when {
                        rssi >= -60 -> Color(0xFF1B5E20) // Verde muy oscuro (alto contraste en amarillo para el sol)
                        rssi >= -85 -> Color(0xFFE65100) // Naranja oscuro
                        else -> Color(0xFFB71C1C)        // Rojo oscuro
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_lighthouse_on),
                            contentDescription = "Conectado",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .border(2.dp, iconColor, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

private fun dialPhoneNumber(context: Context, phoneNumber: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "No se pudo abrir el marcador", Toast.LENGTH_SHORT).show()
    }
}




