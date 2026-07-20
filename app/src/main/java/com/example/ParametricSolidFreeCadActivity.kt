package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.nativecad.NativeCadBridge as LightweightCadBridge
import com.example.nativecad.viewer.CameraPreset
import com.example.nativecad.viewer.NativeCadSurfaceView
import com.example.nativecad.viewer.NativeSceneMesh
import com.example.nativecad.viewer.ReferencePlane
import com.example.parametric.DirectDragTarget
import com.example.parametric.InteractionMode
import com.example.parametric.ParametricFeatureState
import com.example.parametric.ParametricSelection
import com.example.parametric.SketchGeometryMode
import com.example.ui.theme.MyApplicationTheme
import com.medinaparra.freecadandroid.io.AndroidDocumentLoader
import com.medinaparra.freecadandroid.io.FreeCadArchiveReader
import com.medinaparra.freecadandroid.model.SceneMesh
import com.medinaparra.freecadandroid.nativebridge.NativeCadBridge as OcctCadBridge
import com.medinaparra.freecadandroid.nativebridge.NativeFreeCadFileBridge
import com.medinaparra.freecadandroid.nativebridge.NativeStepBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class ParametricSolidFreeCadActivity : ComponentActivity() {
    private var cadSurface: NativeCadSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ParametricSolidFreeCadApp(onSurfaceReady = { cadSurface = it })
            }
        }
    }

    override fun onPause() {
        cadSurface?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        cadSurface?.onResume()
    }
}

private data class CadRenderDocument(
    val name: String,
    val format: String,
    val objectName: String,
    val mesh: NativeSceneMesh,
    val engine: String,
    val summary: String,
    val isNativeBrep: Boolean,
    val meshToken: Long,
    val fitCamera: Boolean
)

@Composable
private fun ParametricSolidFreeCadApp(onSurfaceReady: (NativeCadSurfaceView) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var featureState by remember { mutableStateOf<ParametricFeatureState?>(null) }
    var document by remember { mutableStateOf<CadRenderDocument?>(null) }
    var selected by remember { mutableStateOf(ParametricSelection.DOCUMENT) }
    var selectedPlane by remember { mutableStateOf<ReferencePlane?>(null) }
    var interactionMode by remember { mutableStateOf(InteractionMode.SELECT) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Listo") }
    var error by remember { mutableStateOf<String?>(null) }
    var surface by remember { mutableStateOf<NativeCadSurfaceView?>(null) }

    fun rebuildParametric(
        next: ParametricFeatureState,
        fitCamera: Boolean,
        successStatus: String,
        selectionAfter: ParametricSelection
    ) {
        loading = true
        error = null
        status = "Recalculando Sketch001 → Extrusión001…"
        scope.launch {
            runCatching { buildParametricDocument(next, fitCamera) }
                .onSuccess {
                    featureState = next
                    document = it
                    selected = selectionAfter
                    selectedPlane = null
                    status = successStatus
                }
                .onFailure {
                    error = readableParametricError(it)
                    status = "No se pudo recalcular la operación"
                }
            loading = false
        }
    }

    fun createNewDocument() {
        interactionMode = InteractionMode.SELECT
        val initial = ParametricFeatureState()
        rebuildParametric(
            next = initial,
            fitCamera = true,
            successStatus = "Documento paramétrico creado",
            selectionAfter = ParametricSelection.EXTRUSION
        )
    }

    fun editSketch() {
        val state = featureState ?: return
        selected = ParametricSelection.SKETCH
        selectedPlane = null
        interactionMode = InteractionMode.EDIT_SKETCH
        surface?.setPreset(CameraPreset.TOP)
        status = "Editando ${state.sketch.label}"
    }

    fun toggleDragMode() {
        if (featureState == null) return
        interactionMode = if (interactionMode == InteractionMode.DIRECT_DRAG) {
            InteractionMode.SELECT
        } else {
            InteractionMode.DIRECT_DRAG
        }
        selectedPlane = null
        status = if (interactionMode == InteractionMode.DIRECT_DRAG) {
            "Arrastre la flecha axial o radial"
        } else {
            "Selección"
        }
    }

    fun selectPlane(plane: ReferencePlane, selection: ParametricSelection) {
        selected = selection
        selectedPlane = plane
        interactionMode = InteractionMode.SELECT
        status = "${plane.label} seleccionado"
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            loading = true
            error = null
            status = "Abriendo documento…"
            interactionMode = InteractionMode.SELECT
            scope.launch {
                runCatching { loadParametricCadDocument(context, uri) }
                    .onSuccess {
                        featureState = null
                        document = it
                        selected = ParametricSelection.IMPORTED_GEOMETRY
                        selectedPlane = null
                        status = if (it.isNativeBrep) {
                            "${it.format} cargado como BRep nativo"
                        } else {
                            "${it.format} cargado en modo compatible"
                        }
                    }
                    .onFailure {
                        error = readableParametricError(it)
                        status = "Error al abrir el documento"
                    }
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { createNewDocument() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ParametricToolbar(
                documentName = document?.name ?: "Sin título",
                loading = loading,
                hasParametricPart = featureState != null,
                selected = selected,
                interactionMode = interactionMode,
                onNew = ::createNewDocument,
                onOpen = {
                    openDocument.launch(
                        arrayOf(
                            "application/step",
                            "model/step",
                            "application/zip",
                            "application/octet-stream",
                            "*/*"
                        )
                    )
                },
                onEditSketch = ::editSketch,
                onDrag = ::toggleDragMode,
                onFit = { document?.mesh?.let { surface?.fit(it) } }
            )
        },
        bottomBar = {
            ParametricStatusBar(
                status = status,
                featureState = featureState,
                document = document,
                nativeAvailable = OcctCadBridge.isAvailable
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFE7EDF1))
        ) {
            val showProperties = maxWidth >= 760.dp
            Row(modifier = Modifier.fillMaxSize()) {
                ParametricFeatureTree(
                    document = document,
                    featureState = featureState,
                    selected = selected,
                    onSelect = { next ->
                        selected = next
                        interactionMode = InteractionMode.SELECT
                        selectedPlane = null
                        status = when (next) {
                            ParametricSelection.SKETCH -> "Sketch001 seleccionado · disponible para edición"
                            ParametricSelection.EXTRUSION -> "Extrusión001 seleccionada"
                            ParametricSelection.BODY -> featureState?.bodyName ?: "Cuerpo"
                            else -> document?.objectName ?: "Documento activo"
                        }
                    },
                    onSelectXY = { selectPlane(ReferencePlane.XY, ParametricSelection.PLANE_XY) },
                    onSelectXZ = { selectPlane(ReferencePlane.XZ, ParametricSelection.PLANE_XZ) },
                    onSelectYZ = { selectPlane(ReferencePlane.YZ, ParametricSelection.PLANE_YZ) },
                    modifier = Modifier.width(if (showProperties) 215.dp else 170.dp)
                )

                VerticalDivider(color = Color(0xFFB8C5CE))

                ParametricViewport(
                    document = document,
                    featureState = featureState,
                    selectedPlane = selectedPlane,
                    interactionMode = interactionMode,
                    loading = loading,
                    error = error,
                    onSurfaceReady = {
                        surface = it
                        onSurfaceReady(it)
                    },
                    onPreviewDrag = { target, value ->
                        status = when (target) {
                            DirectDragTarget.TOP_FACE -> "Extrusión001 · longitud ${formatMm(value)}"
                            DirectDragTarget.SIDE_FACE -> "Sketch001 · diámetro ${formatMm(value)}"
                        }
                    },
                    onCommitTopFace = { length ->
                        featureState?.let {
                            rebuildParametric(
                                next = it.resizeFromTopFace(length),
                                fitCamera = false,
                                successStatus = "Extrusión001 actualizada a ${formatMm(length)}",
                                selectionAfter = ParametricSelection.EXTRUSION
                            )
                        }
                    },
                    onCommitSideFace = { diameter ->
                        featureState?.let {
                            rebuildParametric(
                                next = it.resizeFromSideFace(diameter),
                                fitCamera = false,
                                successStatus = "Sketch001 actualizado automáticamente a Ø${formatMm(diameter)}",
                                selectionAfter = ParametricSelection.SKETCH
                            )
                        }
                    },
                    onAcceptSketch = { diameter, geometryMode ->
                        val current = featureState ?: return@ParametricViewport
                        interactionMode = InteractionMode.SELECT
                        rebuildParametric(
                            next = current.editSketch(diameter, geometryMode),
                            fitCamera = false,
                            successStatus = "Sketch001 editado · Extrusión001 recalculada",
                            selectionAfter = ParametricSelection.SKETCH
                        )
                    },
                    onCancelSketch = {
                        interactionMode = InteractionMode.SELECT
                        status = "Edición de Sketch001 cancelada"
                    },
                    onDismissError = { error = null },
                    modifier = Modifier.weight(1f)
                )

                if (showProperties) {
                    VerticalDivider(color = Color(0xFFB8C5CE))
                    ParametricPropertyPanel(
                        document = document,
                        featureState = featureState,
                        selected = selected,
                        selectedPlane = selectedPlane,
                        onEditSketch = ::editSketch,
                        onDirectDrag = {
                            selected = ParametricSelection.EXTRUSION
                            interactionMode = InteractionMode.DIRECT_DRAG
                            status = "Arrastre una cara mediante sus manipuladores"
                        },
                        onReverse = {
                            featureState?.let {
                                rebuildParametric(
                                    next = it.toggleExtrusionDirection(),
                                    fitCamera = false,
                                    successStatus = "Dirección de Extrusión001 invertida",
                                    selectionAfter = ParametricSelection.EXTRUSION
                                )
                            }
                        },
                        modifier = Modifier.width(235.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ParametricToolbar(
    documentName: String,
    loading: Boolean,
    hasParametricPart: Boolean,
    selected: ParametricSelection,
    interactionMode: InteractionMode,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onEditSketch: () -> Unit,
    onDrag: () -> Unit,
    onFit: () -> Unit
) {
    Surface(color = Color(0xFFF0F3F5), shadowElevation = 3.dp) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFD32F2F), RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text("SF", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
                Spacer(Modifier.width(7.dp))
                Column(modifier = Modifier.widthIn(max = 175.dp)) {
                    Text("SolidFreeCAD", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF37474F))
                    Text(documentName, fontSize = 9.sp, color = Color(0xFF607D8B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    ParametricToolbarButton(Icons.Default.NoteAdd, "Nuevo", !loading, false, onNew)
                    ParametricToolbarButton(Icons.Default.FolderOpen, "Abrir", !loading, false, onOpen)
                    ParametricToolbarButton(Icons.Default.Save, "Guardar", false, false) {}
                    ParametricToolbarSeparator()
                    ParametricToolbarButton(
                        Icons.Default.Circle,
                        "Círculo",
                        hasParametricPart && !loading,
                        interactionMode == InteractionMode.EDIT_SKETCH,
                        onEditSketch
                    )
                    ParametricToolbarButton(
                        Icons.Default.Gesture,
                        "Arrastrar",
                        hasParametricPart && !loading,
                        interactionMode == InteractionMode.DIRECT_DRAG,
                        onDrag
                    )
                    ParametricToolbarButton(
                        Icons.Default.Edit,
                        "Editar sketch",
                        hasParametricPart && selected == ParametricSelection.SKETCH && !loading,
                        interactionMode == InteractionMode.EDIT_SKETCH,
                        onEditSketch
                    )
                    ParametricToolbarSeparator()
                    ParametricToolbarButton(Icons.Default.FilterCenterFocus, "Encuadrar", true, false, onFit)
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    color = if (OcctCadBridge.isAvailable) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (OcctCadBridge.isAvailable) Icons.Default.CheckCircle else Icons.Default.Warning,
                            null,
                            tint = if (OcctCadBridge.isAvailable) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (OcctCadBridge.isAvailable) "OCCT 0.8" else "COMPATIBLE", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFBBC7CF))
        }
    }
}

@Composable
private fun ParametricToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(55.dp)
            .background(if (active) Color(0xFFCBE9FC) else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, modifier = Modifier.size(19.dp), tint = if (enabled) Color(0xFF456A7C) else Color(0xFFB0BEC5))
        Text(label, fontSize = 7.sp, maxLines = 1, color = if (enabled) Color(0xFF455A64) else Color(0xFFB0BEC5))
    }
}

@Composable
private fun ParametricToolbarSeparator() {
    VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 4.dp), color = Color(0xFFCCD5DB))
}

@Composable
private fun ParametricFeatureTree(
    document: CadRenderDocument?,
    featureState: ParametricFeatureState?,
    selected: ParametricSelection,
    onSelect: (ParametricSelection) -> Unit,
    onSelectXY: () -> Unit,
    onSelectXZ: () -> Unit,
    onSelectYZ: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bodyExpanded by remember { mutableStateOf(true) }
    var originExpanded by remember { mutableStateOf(true) }
    var extrusionExpanded by remember { mutableStateOf(true) }

    Surface(modifier = modifier.fillMaxHeight(), color = Color(0xFFF8FAFB)) {
        Column {
            Text(
                "ÁRBOL DE OPERACIONES",
                modifier = Modifier.fillMaxWidth().background(Color(0xFFE1E8ED)).padding(9.dp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF455A64)
            )
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 5.dp)) {
                ParametricTreeRow(
                    Icons.Default.Description,
                    document?.name ?: "Sin título",
                    selected == ParametricSelection.DOCUMENT,
                    0,
                    onClick = { onSelect(ParametricSelection.DOCUMENT) }
                )
                if (featureState != null) {
                    ParametricTreeRow(
                        if (bodyExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        featureState.bodyName,
                        selected == ParametricSelection.BODY,
                        1,
                        onClick = {
                            bodyExpanded = !bodyExpanded
                            onSelect(ParametricSelection.BODY)
                        }
                    )
                    if (bodyExpanded) {
                        ParametricTreeRow(
                            if (originExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                            "Origen",
                            selected == ParametricSelection.ORIGIN,
                            2,
                            onClick = {
                                originExpanded = !originExpanded
                                onSelect(ParametricSelection.ORIGIN)
                            }
                        )
                        if (originExpanded) {
                            ParametricTreeRow(Icons.Default.GridOn, "Plano XY", selected == ParametricSelection.PLANE_XY, 3, onSelectXY)
                            ParametricTreeRow(Icons.Default.GridOn, "Plano XZ", selected == ParametricSelection.PLANE_XZ, 3, onSelectXZ)
                            ParametricTreeRow(Icons.Default.GridOn, "Plano YZ", selected == ParametricSelection.PLANE_YZ, 3, onSelectYZ)
                        }
                        ParametricTreeRow(
                            if (extrusionExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                            featureState.extrusion.label,
                            selected == ParametricSelection.EXTRUSION,
                            2,
                            onClick = {
                                extrusionExpanded = !extrusionExpanded
                                onSelect(ParametricSelection.EXTRUSION)
                            },
                            badge = "BRep"
                        )
                        if (extrusionExpanded) {
                            ParametricTreeRow(
                                Icons.Default.Circle,
                                featureState.sketch.label,
                                selected == ParametricSelection.SKETCH,
                                3,
                                onClick = { onSelect(ParametricSelection.SKETCH) },
                                badge = "Ø${formatCompact(featureState.sketch.diameterMm)}"
                            )
                        }
                    }
                } else if (document != null) {
                    ParametricTreeRow(
                        Icons.Default.ViewInAr,
                        document.objectName,
                        selected == ParametricSelection.IMPORTED_GEOMETRY,
                        1,
                        onClick = { onSelect(ParametricSelection.IMPORTED_GEOMETRY) },
                        badge = if (document.isNativeBrep) "BRep" else "Malla"
                    )
                }
            }
        }
    }
}

@Composable
private fun ParametricTreeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    indent: Int,
    onClick: () -> Unit,
    badge: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFFCBE9FC) else Color.Transparent)
            .border(if (selected) 1.dp else 0.dp, if (selected) Color(0xFF72BCE8) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = (7 + indent * 13).dp, end = 5.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(15.dp), tint = if (selected) Color(0xFF1976A8) else Color(0xFF607D8B))
        Spacer(Modifier.width(6.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF37474F))
        badge?.let { Text(it, fontSize = 7.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ParametricViewport(
    document: CadRenderDocument?,
    featureState: ParametricFeatureState?,
    selectedPlane: ReferencePlane?,
    interactionMode: InteractionMode,
    loading: Boolean,
    error: String?,
    onSurfaceReady: (NativeCadSurfaceView) -> Unit,
    onPreviewDrag: (DirectDragTarget, Double) -> Unit,
    onCommitTopFace: (Double) -> Unit,
    onCommitSideFace: (Double) -> Unit,
    onAcceptSketch: (Double, SketchGeometryMode) -> Unit,
    onCancelSketch: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var view by remember { mutableStateOf<NativeCadSurfaceView?>(null) }

    LaunchedEffect(interactionMode) {
        if (interactionMode == InteractionMode.EDIT_SKETCH) view?.setPreset(CameraPreset.TOP)
    }

    Box(modifier = modifier.fillMaxHeight().background(Color(0xFFD5E3ED))) {
        AndroidView(
            factory = { context ->
                NativeCadSurfaceView(context).also {
                    view = it
                    onSurfaceReady(it)
                }
            },
            update = { cadView ->
                val current = document
                if (current != null && cadView.tag != current.meshToken) {
                    cadView.tag = current.meshToken
                    cadView.setMesh(current.mesh, fitCamera = current.fitCamera)
                }
                cadView.setSelectedPlane(selectedPlane)
            },
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(9.dp),
            color = Color.White.copy(alpha = 0.88f),
            shape = RoundedCornerShape(5.dp),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
                Text(document?.name ?: "Documento nuevo", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))
                Text(
                    when {
                        interactionMode == InteractionMode.EDIT_SKETCH -> "EDITANDO SKETCH001"
                        interactionMode == InteractionMode.DIRECT_DRAG -> "ARRASTRAR CARAS · PARAMÉTRICO"
                        selectedPlane != null -> "${selectedPlane.label} · SELECCIÓN AZUL"
                        document?.isNativeBrep == true -> "BRep OCCT · ${document.format}"
                        else -> "Grilla XY · Z arriba"
                    },
                    fontSize = 8.sp,
                    color = if (interactionMode != InteractionMode.SELECT || selectedPlane != null) Color(0xFF1976A8) else Color(0xFF607D8B),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ParametricViewButton("ISO") { view?.setPreset(CameraPreset.ISOMETRIC) }
            ParametricViewButton("F") { view?.setPreset(CameraPreset.FRONT) }
            ParametricViewButton("R") { view?.setPreset(CameraPreset.RIGHT) }
            ParametricViewButton("T") { view?.setPreset(CameraPreset.TOP) }
            ParametricViewButton("FIT") { document?.mesh?.let { view?.fit(it) } }
        }

        if (interactionMode == InteractionMode.DIRECT_DRAG && featureState != null) {
            DirectFaceDragOverlay(
                state = featureState,
                onPreview = onPreviewDrag,
                onCommitTop = onCommitTopFace,
                onCommitSide = onCommitSideFace
            )
        }

        if (interactionMode == InteractionMode.EDIT_SKETCH && featureState != null) {
            SketchCircleEditor(
                state = featureState,
                onAccept = onAcceptSketch,
                onCancel = onCancelSketch
            )
        }

        if (loading) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White.copy(alpha = 0.95f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 8.dp
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Recalculando geometría CAD…", fontSize = 11.sp)
                }
            }
        }

        error?.let {
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).widthIn(max = 430.dp),
                color = Color(0xFFFFEBEE),
                shape = RoundedCornerShape(7.dp),
                shadowElevation = 5.dp
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Error, null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(it, modifier = Modifier.weight(1f), fontSize = 9.sp, color = Color(0xFF6D1B1B))
                    IconButton(onClick = onDismissError, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Cancel, "Cerrar", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectFaceDragOverlay(
    state: ParametricFeatureState,
    onPreview: (DirectDragTarget, Double) -> Unit,
    onCommitTop: (Double) -> Unit,
    onCommitSide: (Double) -> Unit
) {
    Surface(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        color = Color(0xFFFFF8E1).copy(alpha = 0.94f),
        shape = RoundedCornerShape(7.dp),
        shadowElevation = 4.dp
    ) {
        Text(
            "Arrastre la flecha amarilla: axial = Extrusión001 · radial = Sketch001",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 9.sp,
            color = Color(0xFF6D4C00)
        )
    }

    DirectDragHandle(
        target = DirectDragTarget.TOP_FACE,
        startValue = state.extrusion.lengthMm,
        modifier = Modifier.align(Alignment.TopCenter).offset(y = 78.dp),
        onPreview = onPreview,
        onCommit = onCommitTop
    )
    DirectDragHandle(
        target = DirectDragTarget.SIDE_FACE,
        startValue = state.sketch.diameterMm,
        modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-78).dp),
        onPreview = onPreview,
        onCommit = onCommitSide
    )
}

@Composable
private fun DirectDragHandle(
    target: DirectDragTarget,
    startValue: Double,
    modifier: Modifier,
    onPreview: (DirectDragTarget, Double) -> Unit,
    onCommit: (Double) -> Unit
) {
    var draft by remember(startValue, target) { mutableStateOf(startValue) }
    var accumulated by remember(startValue, target) { mutableStateOf(0f) }
    val isTop = target == DirectDragTarget.TOP_FACE

    Surface(
        modifier = modifier
            .width(if (isTop) 92.dp else 108.dp)
            .pointerInput(startValue, target) {
                detectDragGestures(
                    onDragStart = {
                        accumulated = 0f
                        draft = startValue
                    },
                    onDragEnd = {
                        if (abs(draft - startValue) > 0.001) onCommit(draft)
                    },
                    onDragCancel = { draft = startValue }
                ) { change, dragAmount ->
                    change.consume()
                    accumulated += if (isTop) -dragAmount.y else dragAmount.x
                    val sensitivity = if (isTop) 0.30 else 0.35
                    val minimum = if (isTop) ParametricFeatureState.MIN_LENGTH_MM else ParametricFeatureState.MIN_DIAMETER_MM
                    val maximum = if (isTop) ParametricFeatureState.MAX_LENGTH_MM else ParametricFeatureState.MAX_DIAMETER_MM
                    draft = (startValue + accumulated * sensitivity).coerceIn(minimum, maximum)
                    onPreview(target, draft)
                }
            },
        color = Color(0xFFFFFDE7).copy(alpha = 0.96f),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 7.dp,
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFC107))
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isTop) Icons.Default.ArrowUpward else Icons.Default.SwapHoriz,
                null,
                tint = Color(0xFFF9A825),
                modifier = Modifier.size(30.dp)
            )
            Text(if (isTop) "CARA SUPERIOR" else "CARA LATERAL", fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Text(
                if (isTop) formatMm(draft) else "Ø${formatMm(draft)}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5D4037)
            )
        }
    }
}

@Composable
private fun SketchCircleEditor(
    state: ParametricFeatureState,
    onAccept: (Double, SketchGeometryMode) -> Unit,
    onCancel: () -> Unit
) {
    var diameterText by remember(state.revision) { mutableStateOf(formatCompact(state.sketch.diameterMm)) }
    var geometryMode by remember(state.revision) { mutableStateOf(state.sketch.geometryMode) }
    val diameter = parseLocalizedDouble(diameterText)
    val validDiameter = diameter != null && diameter in ParametricFeatureState.MIN_DIAMETER_MM..ParametricFeatureState.MAX_DIAMETER_MM
    val constructionBlocked = geometryMode == SketchGeometryMode.CONSTRUCTION

    Surface(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        color = Color(0xFFF7FAFC).copy(alpha = 0.97f),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 10.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val spacing = 24.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(Color(0xFFD7E1E7), Offset(x, 0f), Offset(x, size.height), 1f)
                    x += spacing
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(Color(0xFFD7E1E7), Offset(0f, y), Offset(size.width, y), 1f)
                    y += spacing
                }
                val center = Offset(size.width * 0.54f, size.height * 0.53f)
                val radius = size.minDimension * 0.26f
                drawLine(Color(0xFFEF5350), Offset(0f, center.y), Offset(size.width, center.y), 2f)
                drawLine(Color(0xFF43A047), Offset(center.x, 0f), Offset(center.x, size.height), 2f)
                if (geometryMode != SketchGeometryMode.CONSTRUCTION) {
                    drawCircle(Color(0xFF90CAF9).copy(alpha = 0.30f), radius, center)
                }
                drawCircle(
                    color = if (geometryMode == SketchGeometryMode.CONSTRUCTION) Color(0xFF607D8B) else Color(0xFF1B5E20),
                    radius = radius,
                    center = center,
                    style = Stroke(
                        width = 3f,
                        pathEffect = if (geometryMode == SketchGeometryMode.CONSTRUCTION) {
                            PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                        } else null
                    )
                )
                drawCircle(Color(0xFF1565C0), 7f, center)
                drawLine(Color(0xFF263238), Offset(center.x - radius, center.y - 20f), Offset(center.x + radius, center.y - 20f), 2f)
            }

            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp).width(225.dp),
                color = Color.White,
                shape = RoundedCornerShape(7.dp),
                shadowElevation = 5.dp
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("EDITAR SKETCH001", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF37474F))
                    Text("Círculo centrado en el origen · Plano XY", fontSize = 8.sp, color = Color(0xFF607D8B))
                    OutlinedTextField(
                        value = diameterText,
                        onValueChange = { diameterText = it },
                        label = { Text("Diámetro") },
                        suffix = { Text("mm") },
                        singleLine = true,
                        isError = !validDiameter,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Salida del sketch", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF607D8B))
                    SketchModeChip(SketchGeometryMode.PROFILE, geometryMode) { geometryMode = it }
                    SketchModeChip(SketchGeometryMode.SURFACE, geometryMode) { geometryMode = it }
                    SketchModeChip(SketchGeometryMode.CONSTRUCTION, geometryMode) { geometryMode = it }
                    if (constructionBlocked) {
                        Text(
                            "Sketch001 alimenta Extrusión001. Suprima primero la extrusión para convertirlo en construcción.",
                            fontSize = 8.sp,
                            color = Color(0xFFC62828)
                        )
                    } else {
                        Text(
                            if (geometryMode == SketchGeometryMode.SURFACE) {
                                "La región plana queda seleccionable y sigue siendo el perfil de Extrusión001."
                            } else {
                                "El perfil cerrado genera una región seleccionable para arrastrar."
                            },
                            fontSize = 8.sp,
                            color = Color(0xFF455A64)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.Center).offset(x = 130.dp, y = (-100).dp),
                color = Color.White,
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1976D2))
            ) {
                Text(
                    "Ø ${diameter?.let(::formatMm) ?: "—"}",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    color = Color(0xFF0D47A1),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.align(Alignment.BottomCenter).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(onClick = onCancel) {
                    Icon(Icons.Default.Cancel, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Cancelar")
                }
                Button(
                    enabled = validDiameter && !constructionBlocked,
                    onClick = { onAccept(requireNotNull(diameter), geometryMode) }
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Aceptar sketch")
                }
            }
        }
    }
}

@Composable
private fun SketchModeChip(
    mode: SketchGeometryMode,
    selectedMode: SketchGeometryMode,
    onSelect: (SketchGeometryMode) -> Unit
) {
    FilterChip(
        selected = mode == selectedMode,
        onClick = { onSelect(mode) },
        label = { Text(mode.label, fontSize = 9.sp) },
        leadingIcon = {
            Icon(
                when (mode) {
                    SketchGeometryMode.PROFILE -> Icons.Default.Circle
                    SketchGeometryMode.SURFACE -> Icons.Default.Layers
                    SketchGeometryMode.CONSTRUCTION -> Icons.Default.GridOn
                },
                null,
                modifier = Modifier.size(15.dp)
            )
        }
    )
}

@Composable
private fun ParametricViewButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(39.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFFF7FAFC).copy(alpha = 0.93f),
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB6C5CF))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF455A64))
        }
    }
}

@Composable
private fun ParametricPropertyPanel(
    document: CadRenderDocument?,
    featureState: ParametricFeatureState?,
    selected: ParametricSelection,
    selectedPlane: ReferencePlane?,
    onEditSketch: () -> Unit,
    onDirectDrag: () -> Unit,
    onReverse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxHeight(), color = Color(0xFFF8FAFB)) {
        Column {
            Text(
                "PROPIEDADES",
                modifier = Modifier.fillMaxWidth().background(Color(0xFFE1E8ED)).padding(9.dp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF455A64)
            )
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    selectedPlane != null -> {
                        ParametricProperty("Elemento", selectedPlane.label)
                        ParametricProperty("Tipo", "Plano de referencia")
                        ParametricProperty("Visualización", "Azul claro")
                    }
                    featureState != null && selected == ParametricSelection.SKETCH -> {
                        Text("SKETCH001", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        ParametricProperty("Plano", featureState.sketch.plane)
                        ParametricProperty("Geometría", "Círculo")
                        ParametricProperty("Diámetro", "Ø${formatMm(featureState.sketch.diameterMm)}")
                        ParametricProperty("Salida", featureState.sketch.geometryMode.label)
                        Button(onClick = onEditSketch, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Editar sketch")
                        }
                    }
                    featureState != null && selected == ParametricSelection.EXTRUSION -> {
                        Text("EXTRUSIÓN001", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        ParametricProperty("Perfil", featureState.sketch.label)
                        ParametricProperty("Longitud", formatMm(featureState.extrusion.lengthMm))
                        ParametricProperty("Dirección", if (featureState.extrusion.reversed) "Invertida" else "+Z")
                        ParametricProperty("Resultado", "Sólido BRep")
                        Button(onClick = onDirectDrag, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Gesture, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Arrastrar caras")
                        }
                        TextButton(onClick = onReverse, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Invertir dirección")
                        }
                    }
                    featureState != null && selected == ParametricSelection.BODY -> {
                        ParametricProperty("Cuerpo", featureState.bodyName)
                        ParametricProperty("Operaciones", "1")
                        ParametricProperty("Sketches", "1")
                        ParametricProperty("Estado", "Recalculado r${featureState.revision}")
                    }
                    document != null -> {
                        ParametricProperty("Documento", document.name)
                        ParametricProperty("Elemento", document.objectName)
                        ParametricProperty("Formato", document.format)
                        ParametricProperty("Motor", document.engine)
                        ParametricProperty("Vértices", document.mesh.vertexCount.toString())
                        ParametricProperty("Triángulos", document.mesh.triangleCount.toString())
                        ParametricProperty("Ancho X", formatMm((document.mesh.maxX - document.mesh.minX).toDouble()))
                        ParametricProperty("Fondo Y", formatMm((document.mesh.maxY - document.mesh.minY).toDouble()))
                        ParametricProperty("Alto Z", formatMm((document.mesh.maxZ - document.mesh.minZ).toDouble()))
                        HorizontalDivider()
                        Text("INFORMACIÓN DEL MOTOR", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF607D8B))
                        Text(document.summary, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF455A64))
                    }
                    else -> Text("No hay documento activo", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ParametricProperty(label: String, value: String) {
    Column {
        Text(label.uppercase(Locale.ROOT), fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78909C))
        Text(value, fontSize = 10.sp, color = Color(0xFF37474F), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ParametricStatusBar(
    status: String,
    featureState: ParametricFeatureState?,
    document: CadRenderDocument?,
    nativeAvailable: Boolean
) {
    Surface(color = Color(0xFFE9EEF2), shadowElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(status, modifier = Modifier.weight(1f), fontSize = 8.sp, color = Color(0xFF455A64))
            featureState?.let {
                Text(
                    "Ø${formatCompact(it.sketch.diameterMm)} · L ${formatCompact(it.extrusion.lengthMm)} mm · r${it.revision}",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF607D8B)
                )
                Spacer(Modifier.width(12.dp))
            } ?: document?.let {
                Text("${it.mesh.vertexCount} V · ${it.mesh.triangleCount} T", fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.width(12.dp))
            }
            Text(
                if (nativeAvailable) "FreeCAD-Native 0.8 · ARM64" else "Fallback OpenGL",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (nativeAvailable) Color(0xFF2E7D32) else Color(0xFFEF6C00)
            )
        }
    }
}

private suspend fun buildParametricDocument(
    state: ParametricFeatureState,
    fitCamera: Boolean
): CadRenderDocument = withContext(Dispatchers.IO) {
    runCatching {
        val scene = OcctCadBridge.createCylinderScene(
            diameterMm = state.safeDiameter(),
            lengthMm = state.safeLength(),
            documentName = state.documentName
        )
        CadRenderDocument(
            name = state.documentName,
            format = "Documento paramétrico",
            objectName = state.bodyName,
            mesh = scene.mesh.toParametricMesh(state.extrusion.reversed),
            engine = "FreeCAD-Native 0.8 / OCCT",
            summary = buildString {
                appendLine(scene.buildInfo)
                appendLine(scene.documentSummary)
                appendLine("Sketch001: círculo Ø${formatMm(state.sketch.diameterMm)}")
                append("Extrusión001: ${formatMm(state.extrusion.lengthMm)}")
            },
            isNativeBrep = true,
            meshToken = System.nanoTime(),
            fitCamera = fitCamera
        )
    }.getOrElse { failure ->
        CadRenderDocument(
            name = state.documentName,
            format = "Documento paramétrico compatible",
            objectName = state.bodyName,
            mesh = fallbackCylinderMesh(state),
            engine = "OpenGL paramétrico compatible",
            summary = "El núcleo nativo no se cargó: ${readableParametricError(failure)}",
            isNativeBrep = false,
            meshToken = System.nanoTime(),
            fitCamera = fitCamera
        )
    }
}

private suspend fun loadParametricCadDocument(
    context: android.content.Context,
    uri: Uri
): CadRenderDocument = withContext(Dispatchers.IO) {
    val staged = AndroidDocumentLoader.stage(context, uri, "solidfreecad-imports", "document.step")
    val localFile = staged.first
    val displayName = staged.second
    when (AndroidDocumentLoader.extension(displayName)) {
        "step", "stp" -> loadParametricStep(context, uri, localFile, displayName)
        "fcstd" -> loadParametricFcStd(context, localFile, displayName)
        else -> error("Formato no compatible. Seleccione STEP, STP o FCStd")
    }
}

private suspend fun loadParametricStep(
    context: android.content.Context,
    uri: Uri,
    localFile: File,
    displayName: String
): CadRenderDocument {
    val nativeAttempt = runCatching { NativeStepBridge.importStep(localFile.absolutePath, displayName) }
    nativeAttempt.getOrNull()?.let { scene ->
        return CadRenderDocument(
            name = displayName,
            format = "STEP",
            objectName = displayName.substringBeforeLast('.').ifBlank { "STEP importado" },
            mesh = scene.mesh.toParametricMesh(false),
            engine = "OpenCASCADE STEPControl_Reader",
            summary = scene.summary,
            isNativeBrep = true,
            meshToken = System.nanoTime(),
            fitCamera = true
        )
    }
    val fallback = LightweightCadBridge.inspect(context, uri).getOrThrow()
    val mesh = fallback.previewMesh ?: error("No fue posible generar una vista del STEP")
    return CadRenderDocument(
        name = displayName,
        format = "STEP",
        objectName = displayName.substringBeforeLast('.').ifBlank { "STEP importado" },
        mesh = mesh,
        engine = fallback.previewMode ?: "Parser STEP compatible",
        summary = buildString {
            appendLine("Vista compatible; no es BRep editable")
            nativeAttempt.exceptionOrNull()?.message?.let { appendLine("OCCT: $it") }
            fallback.notes.forEach { appendLine(it) }
        }.trimEnd(),
        isNativeBrep = false,
        meshToken = System.nanoTime(),
        fitCamera = true
    )
}

private fun loadParametricFcStd(
    context: android.content.Context,
    localFile: File,
    displayName: String
): CadRenderDocument {
    val archive = FreeCadArchiveReader.extract(
        archiveFile = localFile,
        outputRoot = File(context.cacheDir, "solidfreecad-fcstd"),
        displayName = displayName
    )
    val scene = NativeFreeCadFileBridge.importFcStdBreps(
        localPaths = archive.brepFiles.map(File::getAbsolutePath),
        displayName = displayName,
        archiveSummary = archive.summary
    )
    return CadRenderDocument(
        name = displayName,
        format = "FCStd",
        objectName = archive.documentName,
        mesh = scene.mesh.toParametricMesh(false),
        engine = "FreeCAD-Native BRepIo",
        summary = scene.summary,
        isNativeBrep = true,
        meshToken = System.nanoTime(),
        fitCamera = true
    )
}

private fun SceneMesh.toParametricMesh(reversed: Boolean): NativeSceneMesh {
    if (!reversed) {
        return NativeSceneMesh(vertices, indices, minX, minY, minZ, maxX, maxY, maxZ)
    }
    val transformed = vertices.copyOf()
    var index = 0
    while (index < transformed.size) {
        transformed[index + 2] = -transformed[index + 2]
        transformed[index + 5] = -transformed[index + 5]
        index += 6
    }
    return NativeSceneMesh(transformed, indices, minX, minY, -maxZ, maxX, maxY, -minZ)
}

private fun fallbackCylinderMesh(state: ParametricFeatureState, segments: Int = 64): NativeSceneMesh {
    val radius = (state.sketch.diameterMm * 0.5).toFloat()
    val length = state.extrusion.lengthMm.toFloat()
    val z0 = if (state.extrusion.reversed) -length else 0f
    val z1 = if (state.extrusion.reversed) 0f else length
    val vertices = ArrayList<Float>()
    val indices = ArrayList<Int>()

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Int {
        val id = vertices.size / 6
        vertices += x; vertices += y; vertices += z
        vertices += nx; vertices += ny; vertices += nz
        return id
    }

    val lower = IntArray(segments + 1)
    val upper = IntArray(segments + 1)
    for (i in 0..segments) {
        val angle = 2.0 * PI * i / segments
        val nx = cos(angle).toFloat()
        val ny = sin(angle).toFloat()
        lower[i] = vertex(radius * nx, radius * ny, z0, nx, ny, 0f)
        upper[i] = vertex(radius * nx, radius * ny, z1, nx, ny, 0f)
    }
    for (i in 0 until segments) {
        indices += lower[i]; indices += lower[i + 1]; indices += upper[i + 1]
        indices += lower[i]; indices += upper[i + 1]; indices += upper[i]
    }

    val topCenter = vertex(0f, 0f, z1, 0f, 0f, 1f)
    val topRing = IntArray(segments + 1)
    val bottomCenter = vertex(0f, 0f, z0, 0f, 0f, -1f)
    val bottomRing = IntArray(segments + 1)
    for (i in 0..segments) {
        val angle = 2.0 * PI * i / segments
        val x = radius * cos(angle).toFloat()
        val y = radius * sin(angle).toFloat()
        topRing[i] = vertex(x, y, z1, 0f, 0f, 1f)
        bottomRing[i] = vertex(x, y, z0, 0f, 0f, -1f)
    }
    for (i in 0 until segments) {
        indices += topCenter; indices += topRing[i]; indices += topRing[i + 1]
        indices += bottomCenter; indices += bottomRing[i + 1]; indices += bottomRing[i]
    }

    return NativeSceneMesh(
        vertices = vertices.toFloatArray(),
        indices = indices.toIntArray(),
        minX = -radius,
        minY = -radius,
        minZ = minOf(z0, z1),
        maxX = radius,
        maxY = radius,
        maxZ = maxOf(z0, z1)
    )
}

private fun parseLocalizedDouble(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()
private fun formatCompact(value: Double): String = "%.2f".format(Locale.US, value)
private fun formatMm(value: Double): String = "%.2f mm".format(Locale.US, value)
private fun readableParametricError(error: Throwable): String =
    generateSequence(error) { it.cause }.last().message ?: error.message ?: error::class.java.simpleName
