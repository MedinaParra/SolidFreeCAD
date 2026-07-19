package com.example.faceui

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.nativecad.viewer.CameraPreset
import com.example.nativecad.viewer.NativeSceneMesh
import com.example.parametric.ParametricFeatureState
import com.example.parametric.SketchGeometryMode
import com.example.ui.theme.MyApplicationTheme
import com.medinaparra.freecadandroid.io.AndroidDocumentLoader
import com.medinaparra.freecadandroid.io.FreeCadArchiveReader
import com.medinaparra.freecadandroid.model.SceneMesh
import com.medinaparra.freecadandroid.nativebridge.NativeCadBridge
import com.medinaparra.freecadandroid.nativebridge.NativeFreeCadFileBridge
import com.medinaparra.freecadandroid.nativebridge.NativeStepBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class FaceDrivenSolidFreeCadActivity : ComponentActivity() {
    private var cadSurface: FaceDrivenCadSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                FaceDrivenWorkspace(onSurfaceReady = { cadSurface = it })
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

private enum class WorkspaceMode { MODEL, EDIT_EXTRUSION, EDIT_SKETCH }
private enum class FeatureNode { DOCUMENT, BODY, EXTRUSION, SKETCH, IMPORTED }

private data class FaceWorkspaceDocument(
    val name: String,
    val objectName: String,
    val format: String,
    val mesh: NativeSceneMesh,
    val summary: String,
    val parametric: Boolean,
    val token: Long,
    val fitCamera: Boolean
)

@Composable
private fun FaceDrivenWorkspace(onSurfaceReady: (FaceDrivenCadSurfaceView) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var featureState by remember { mutableStateOf(ParametricFeatureState()) }
    var document by remember { mutableStateOf<FaceWorkspaceDocument?>(null) }
    var selectedFace by remember { mutableStateOf(EditableCadFace.NONE) }
    var selectedNode by remember { mutableStateOf(FeatureNode.EXTRUSION) }
    var mode by remember { mutableStateOf(WorkspaceMode.MODEL) }
    var panelsVisible by remember { mutableStateOf(true) }
    var surface by remember { mutableStateOf<FaceDrivenCadSurfaceView?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Listo") }
    var error by remember { mutableStateOf<String?>(null) }
    var extrusionDraft by remember { mutableStateOf(featureState.extrusion.lengthMm.toString()) }
    var sketchDraft by remember { mutableStateOf(featureState.sketch.diameterMm.toString()) }

    fun rebuild(
        next: ParametricFeatureState,
        fitCamera: Boolean,
        successStatus: String,
        node: FeatureNode
    ) {
        loading = true
        error = null
        scope.launch {
            runCatching { buildCylinderDocument(next, fitCamera) }
                .onSuccess {
                    featureState = next
                    document = it
                    selectedNode = node
                    extrusionDraft = formatNumber(next.extrusion.lengthMm)
                    sketchDraft = formatNumber(next.sketch.diameterMm)
                    status = successStatus
                }
                .onFailure {
                    error = readableFailure(it)
                    status = "No se pudo recalcular el sólido"
                }
            loading = false
        }
    }

    fun newDocument() {
        mode = WorkspaceMode.MODEL
        selectedFace = EditableCadFace.NONE
        surface?.setSelectedFace(EditableCadFace.NONE)
        rebuild(ParametricFeatureState(), true, "Documento paramétrico creado", FeatureNode.EXTRUSION)
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            loading = true
            error = null
            status = "Abriendo documento…"
            scope.launch {
                runCatching { loadExternalDocument(context, uri) }
                    .onSuccess {
                        document = it
                        selectedNode = FeatureNode.IMPORTED
                        selectedFace = EditableCadFace.NONE
                        surface?.setSelectedFace(EditableCadFace.NONE)
                        mode = WorkspaceMode.MODEL
                        status = "${it.format} cargado en el entorno único"
                    }
                    .onFailure {
                        error = readableFailure(it)
                        status = "Error al abrir el documento"
                    }
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { newDocument() }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DirectWorkspaceToolbar(
                documentName = document?.name ?: "Sin título",
                panelsVisible = panelsVisible,
                loading = loading,
                editable = document?.parametric == true,
                mode = mode,
                onNew = ::newDocument,
                onOpen = { openDocument.launch(arrayOf("*/*")) },
                onEditExtrusion = {
                    if (document?.parametric == true) {
                        selectedNode = FeatureNode.EXTRUSION
                        selectedFace = EditableCadFace.TOP
                        surface?.setSelectedFace(EditableCadFace.TOP)
                        extrusionDraft = formatNumber(featureState.extrusion.lengthMm)
                        mode = WorkspaceMode.EDIT_EXTRUSION
                        panelsVisible = true
                        status = "Editando Saliente-Extruir1"
                    }
                },
                onEditSketch = {
                    if (document?.parametric == true) {
                        selectedNode = FeatureNode.SKETCH
                        selectedFace = EditableCadFace.SIDE
                        surface?.setSelectedFace(EditableCadFace.SIDE)
                        sketchDraft = formatNumber(featureState.sketch.diameterMm)
                        mode = WorkspaceMode.EDIT_SKETCH
                        panelsVisible = true
                        status = "Editando Croquis1"
                    }
                },
                onFit = { document?.mesh?.let { surface?.fit(it) } },
                onTogglePanels = { panelsVisible = !panelsVisible }
            )
        },
        bottomBar = {
            DirectStatusBar(status, selectedFace, document, loading)
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFE8EDF0))
        ) {
            val showProperties = panelsVisible && maxWidth >= 820.dp
            Row(modifier = Modifier.fillMaxSize()) {
                if (panelsVisible) {
                    when (mode) {
                        WorkspaceMode.EDIT_EXTRUSION -> ExtrusionFeatureEditor(
                            value = extrusionDraft,
                            reversed = featureState.extrusion.reversed,
                            onValueChange = { extrusionDraft = numericText(it) },
                            onReverse = {
                                val next = featureState.toggleExtrusionDirection()
                                rebuild(next, false, "Dirección de Saliente-Extruir1 invertida", FeatureNode.EXTRUSION)
                            },
                            onAccept = {
                                val length = extrusionDraft.toDoubleOrNull()
                                if (length == null || length <= 0.0) {
                                    error = "Ingrese una profundidad válida"
                                } else {
                                    mode = WorkspaceMode.MODEL
                                    rebuild(
                                        featureState.resizeFromTopFace(length),
                                        false,
                                        "Saliente-Extruir1 actualizado a ${formatMm(length)}",
                                        FeatureNode.EXTRUSION
                                    )
                                }
                            },
                            onCancel = {
                                extrusionDraft = formatNumber(featureState.extrusion.lengthMm)
                                mode = WorkspaceMode.MODEL
                                status = "Edición de extrusión cancelada"
                            },
                            modifier = Modifier.width(245.dp)
                        )

                        else -> SolidWorksFeatureTree(
                            document = document,
                            selected = selectedNode,
                            onSelect = { node ->
                                selectedNode = node
                                mode = WorkspaceMode.MODEL
                                when (node) {
                                    FeatureNode.EXTRUSION -> {
                                        selectedFace = EditableCadFace.TOP
                                        surface?.setSelectedFace(EditableCadFace.TOP)
                                        status = "Saliente-Extruir1 seleccionado"
                                    }
                                    FeatureNode.SKETCH -> {
                                        selectedFace = EditableCadFace.SIDE
                                        surface?.setSelectedFace(EditableCadFace.SIDE)
                                        status = "Croquis1 seleccionado · pulse Editar croquis"
                                    }
                                    else -> {
                                        selectedFace = EditableCadFace.NONE
                                        surface?.setSelectedFace(EditableCadFace.NONE)
                                    }
                                }
                            },
                            onEditExtrusion = {
                                selectedNode = FeatureNode.EXTRUSION
                                extrusionDraft = formatNumber(featureState.extrusion.lengthMm)
                                mode = WorkspaceMode.EDIT_EXTRUSION
                            },
                            onEditSketch = {
                                selectedNode = FeatureNode.SKETCH
                                sketchDraft = formatNumber(featureState.sketch.diameterMm)
                                mode = WorkspaceMode.EDIT_SKETCH
                            },
                            modifier = Modifier.width(220.dp)
                        )
                    }
                    VerticalDivider(color = Color(0xFFB8C4CC))
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (mode == WorkspaceMode.EDIT_SKETCH && document?.parametric == true) {
                        SketchDimensionEditor(
                            diameterText = sketchDraft,
                            onDiameterChange = { sketchDraft = numericText(it) },
                            onAccept = {
                                val diameter = sketchDraft.toDoubleOrNull()
                                if (diameter == null || diameter <= 0.0) {
                                    error = "Ingrese un diámetro válido"
                                } else {
                                    mode = WorkspaceMode.MODEL
                                    rebuild(
                                        featureState.editSketch(diameter, SketchGeometryMode.PROFILE),
                                        false,
                                        "Croquis1 editado · Saliente-Extruir1 recalculado",
                                        FeatureNode.SKETCH
                                    )
                                }
                            },
                            onCancel = {
                                sketchDraft = formatNumber(featureState.sketch.diameterMm)
                                mode = WorkspaceMode.MODEL
                                status = "Edición de Croquis1 cancelada"
                            }
                        )
                    } else {
                        AndroidView(
                            factory = { androidContext ->
                                FaceDrivenCadSurfaceView(androidContext).also { view ->
                                    surface = view
                                    onSurfaceReady(view)
                                    view.onFaceSelected = { face ->
                                        if (document?.parametric == true) {
                                            selectedFace = face
                                            selectedNode = when (face) {
                                                EditableCadFace.TOP -> FeatureNode.EXTRUSION
                                                EditableCadFace.SIDE -> FeatureNode.SKETCH
                                                EditableCadFace.NONE -> FeatureNode.BODY
                                            }
                                            status = when (face) {
                                                EditableCadFace.TOP -> "Cara superior seleccionada · arrastre la flecha para cambiar profundidad"
                                                EditableCadFace.SIDE -> "Cara curva seleccionada · arrastre la flecha para cambiar diámetro"
                                                EditableCadFace.NONE -> "Ninguna cara seleccionada"
                                            }
                                        } else {
                                            view.setSelectedFace(EditableCadFace.NONE)
                                        }
                                    }
                                    view.onParameterPreview = { face, value ->
                                        status = when (face) {
                                            EditableCadFace.TOP -> "Profundidad: ${formatMm(value.toDouble())}"
                                            EditableCadFace.SIDE -> "Diámetro: ${formatMm(value.toDouble())}"
                                            EditableCadFace.NONE -> status
                                        }
                                    }
                                    view.onParameterCommit = { face, value ->
                                        if (document?.parametric == true) {
                                            when (face) {
                                                EditableCadFace.TOP -> rebuild(
                                                    featureState.resizeFromTopFace(value.toDouble()),
                                                    false,
                                                    "Saliente-Extruir1 actualizado desde la cara",
                                                    FeatureNode.EXTRUSION
                                                )
                                                EditableCadFace.SIDE -> rebuild(
                                                    featureState.resizeFromSideFace(value.toDouble()),
                                                    false,
                                                    "Croquis1 actualizado automáticamente desde la cara curva",
                                                    FeatureNode.SKETCH
                                                )
                                                EditableCadFace.NONE -> Unit
                                            }
                                        }
                                    }
                                }
                            },
                            update = { view ->
                                val active = document
                                if (active != null && view.tag != active.token) {
                                    view.tag = active.token
                                    view.setMesh(active.mesh, active.fitCamera)
                                }
                                view.setDimensions(
                                    featureState.extrusion.lengthMm.toFloat(),
                                    featureState.sketch.diameterMm.toFloat()
                                )
                                view.setSelectedFace(selectedFace)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                            color = Color.White.copy(alpha = 0.90f),
                            shape = RoundedCornerShape(6.dp),
                            shadowElevation = 3.dp
                        ) {
                            Text(
                                when (selectedFace) {
                                    EditableCadFace.TOP -> "Cara superior · profundidad ${formatMm(featureState.extrusion.lengthMm)}"
                                    EditableCadFace.SIDE -> "Cara curva · Ø${formatMm(featureState.sketch.diameterMm)}"
                                    EditableCadFace.NONE -> "Toque una cara para seleccionarla"
                                },
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 9.sp,
                                color = Color(0xFF455A64)
                            )
                        }
                    }

                    if (!panelsVisible) {
                        Surface(
                            modifier = Modifier.align(Alignment.CenterStart).clickable { panelsVisible = true },
                            color = Color(0xFF2F668D),
                            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                            shadowElevation = 4.dp
                        ) {
                            Text("▶", modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp), color = Color.White)
                        }
                    }

                    if (loading) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 5.dp
                        ) {
                            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(25.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Recalculando BRep…", fontSize = 11.sp)
                            }
                        }
                    }

                    error?.let { message ->
                        Surface(
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).widthIn(max = 440.dp),
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(7.dp),
                            shadowElevation = 5.dp
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(message, modifier = Modifier.weight(1f), fontSize = 9.sp, color = Color(0xFF6D1B1B))
                                IconButton(onClick = { error = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Cancel, "Cerrar", modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }

                if (showProperties && mode == WorkspaceMode.MODEL) {
                    VerticalDivider(color = Color(0xFFB8C4CC))
                    DirectPropertiesPanel(
                        selectedNode = selectedNode,
                        selectedFace = selectedFace,
                        state = featureState,
                        document = document,
                        onEditExtrusion = {
                            extrusionDraft = formatNumber(featureState.extrusion.lengthMm)
                            mode = WorkspaceMode.EDIT_EXTRUSION
                        },
                        onEditSketch = {
                            sketchDraft = formatNumber(featureState.sketch.diameterMm)
                            mode = WorkspaceMode.EDIT_SKETCH
                        },
                        modifier = Modifier.width(235.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DirectWorkspaceToolbar(
    documentName: String,
    panelsVisible: Boolean,
    loading: Boolean,
    editable: Boolean,
    mode: WorkspaceMode,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onEditExtrusion: () -> Unit,
    onEditSketch: () -> Unit,
    onFit: () -> Unit,
    onTogglePanels: () -> Unit
) {
    Surface(color = Color(0xFFF1F3F5), shadowElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.background(Color(0xFFD32F2F), RoundedCornerShape(4.dp)).padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Text("SF", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.widthIn(max = 155.dp)) {
                Text("SolidFreeCAD", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF37474F))
                Text(documentName, fontSize = 8.sp, color = Color(0xFF607D8B), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                ToolbarItem(Icons.Default.NoteAdd, "Nuevo", !loading, false, onNew)
                ToolbarItem(Icons.Default.FolderOpen, "Abrir", !loading, false, onOpen)
                ToolbarItem(Icons.Default.Save, "Guardar", false, false) {}
                ToolbarDivider()
                ToolbarItem(Icons.Default.Layers, "Extrusión", editable && !loading, mode == WorkspaceMode.EDIT_EXTRUSION, onEditExtrusion)
                ToolbarItem(Icons.Default.Edit, "Croquis", editable && !loading, mode == WorkspaceMode.EDIT_SKETCH, onEditSketch)
                ToolbarItem(Icons.Default.FilterCenterFocus, "Encuadrar", true, false, onFit)
            }
            IconButton(onClick = onTogglePanels) {
                Icon(
                    if (panelsVisible) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                    if (panelsVisible) "Ocultar paneles" else "Mostrar paneles",
                    tint = Color(0xFF456A7C)
                )
            }
        }
    }
}

@Composable
private fun ToolbarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.width(58.dp)
            .background(if (active) Color(0xFFCBE9FC) else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, modifier = Modifier.size(19.dp), tint = if (enabled) Color(0xFF456A7C) else Color(0xFFB0BEC5))
        Text(label, fontSize = 7.sp, maxLines = 1, color = if (enabled) Color(0xFF455A64) else Color(0xFFB0BEC5))
    }
}

@Composable
private fun ToolbarDivider() {
    VerticalDivider(modifier = Modifier.height(34.dp).padding(horizontal = 4.dp), color = Color(0xFFCCD5DB))
}

@Composable
private fun SolidWorksFeatureTree(
    document: FaceWorkspaceDocument?,
    selected: FeatureNode,
    onSelect: (FeatureNode) -> Unit,
    onEditExtrusion: () -> Unit,
    onEditSketch: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                TreeRow(Icons.Default.Description, document?.name ?: "Pieza1", selected == FeatureNode.DOCUMENT, 0) { onSelect(FeatureNode.DOCUMENT) }
                if (document?.parametric == true) {
                    TreeRow(Icons.Default.AccountTree, "Cuerpo1", selected == FeatureNode.BODY, 1) { onSelect(FeatureNode.BODY) }
                    TreeRow(
                        if (extrusionExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        "Saliente-Extruir1",
                        selected == FeatureNode.EXTRUSION,
                        2
                    ) {
                        extrusionExpanded = !extrusionExpanded
                        onSelect(FeatureNode.EXTRUSION)
                    }
                    if (extrusionExpanded) {
                        TreeRow(Icons.Default.Circle, "(-) Croquis1", selected == FeatureNode.SKETCH, 3) { onSelect(FeatureNode.SKETCH) }
                    }
                } else if (document != null) {
                    TreeRow(Icons.Default.Layers, document.objectName, selected == FeatureNode.IMPORTED, 1) { onSelect(FeatureNode.IMPORTED) }
                }
            }
            if (document?.parametric == true) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Button(onClick = onEditExtrusion, modifier = Modifier.fillMaxWidth().height(38.dp)) {
                        Text("Editar Saliente-Extruir1", fontSize = 9.sp)
                    }
                    TextButton(onClick = onEditSketch, modifier = Modifier.fillMaxWidth().height(34.dp)) {
                        Text("Editar Croquis1", fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    indent: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) Color(0xFFCFEAFB) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = (8 + indent * 14).dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(15.dp), tint = if (selected) Color(0xFF1565C0) else Color(0xFF546E7A))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ExtrusionFeatureEditor(
    value: String,
    reversed: Boolean,
    onValueChange: (String) -> Unit,
    onReverse: () -> Unit,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxHeight(), color = Color(0xFFF7F8F9)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Layers, null, tint = Color(0xFF3D6D86), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Saliente-Extruir1", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                IconButton(onClick = onAccept, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Check, "Aceptar", tint = Color(0xFF2E7D32))
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Cancel, "Cancelar", tint = Color(0xFFC62828))
                }
                Icon(Icons.Default.Visibility, null, tint = Color(0xFF455A64), modifier = Modifier.size(17.dp))
            }
            HorizontalDivider()
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EditorSection("DESDE") {
                    ReadOnlyField("Plano de croquis")
                }
                EditorSection("DIRECCIÓN 1") {
                    ReadOnlyField("Hasta profundidad específica")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onReverse, modifier = Modifier.size(38.dp)) {
                            Icon(if (reversed) Icons.Default.ArrowBack else Icons.Default.ArrowForward, "Invertir dirección")
                        }
                        OutlinedTextField(
                            value = value,
                            onValueChange = onValueChange,
                            label = { Text("Profundidad", fontSize = 8.sp) },
                            suffix = { Text("mm", fontSize = 8.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = false, onCheckedChange = null, enabled = false)
                        Text("Ángulo de salida hacia afuera", fontSize = 9.sp, color = Color(0xFF78909C))
                    }
                }
                Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        "La flecha amarilla del visor modifica esta misma profundidad; no crea una operación nueva.",
                        modifier = Modifier.padding(9.dp),
                        fontSize = 8.sp,
                        color = Color(0xFF2E5E34)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, content: @Composable Column.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF455A64))
        HorizontalDivider()
        content()
    }
}

@Composable
private fun ReadOnlyField(value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFB0BEC5), RoundedCornerShape(2.dp)),
        color = Color.White
    ) {
        Text(value, modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp), fontSize = 9.sp)
    }
}

@Composable
private fun SketchDimensionEditor(
    diameterText: String,
    onDiameterChange: (String) -> Unit,
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F6F7))) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = min(size.width, size.height) * 0.31f
            val center = Offset(size.width * 0.52f, size.height * 0.51f)
            drawCircle(Color(0xFFB7BBC1), radius, center)
            drawCircle(Color(0xFF202428), radius, center, style = Stroke(width = 3f))
            drawLine(Color(0xFFE53935), center - Offset(0f, 18f), center + Offset(0f, 18f), strokeWidth = 3f)
            drawLine(Color(0xFFE53935), center - Offset(18f, 0f), center + Offset(18f, 0f), strokeWidth = 3f)
            val angle = Math.toRadians(-12.0)
            val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            val start = center - direction * radius
            val end = center + direction * radius
            drawLine(Color(0xFF42A5F5), start, end, strokeWidth = 3f)
            drawCircle(Color(0xFF42A5F5), 5f, start)
            drawCircle(Color(0xFF42A5F5), 5f, end)
            val extension = direction * 34f
            drawLine(Color(0xFF42A5F5), end, end + extension, strokeWidth = 2f)
            drawLine(
                Color(0xFF9E9E9E),
                Offset(center.x, 0f),
                Offset(center.x, size.height),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 8f))
            )
        }

        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Button(onClick = onAccept, modifier = Modifier.height(40.dp)) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Aceptar croquis", fontSize = 9.sp)
            }
            TextButton(onClick = onCancel, modifier = Modifier.height(40.dp)) {
                Icon(Icons.Default.Cancel, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancelar", fontSize = 9.sp)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            color = Color.White.copy(alpha = 0.92f),
            shape = RoundedCornerShape(6.dp),
            shadowElevation = 3.dp
        ) {
            Text("Editando Croquis1 · cota diametral", modifier = Modifier.padding(8.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(
            value = diameterText,
            onValueChange = onDiameterChange,
            prefix = { Text("Ø", color = Color(0xFF42A5F5), fontWeight = FontWeight.Bold) },
            suffix = { Text("mm", fontSize = 8.sp) },
            label = { Text("Diámetro", fontSize = 8.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.align(Alignment.Center)
                .offset(x = (canvasWidth * 0.18f / LocalContext.current.resources.displayMetrics.density).dp, y = (-canvasHeight * 0.10f / LocalContext.current.resources.displayMetrics.density).dp)
                .width(145.dp)
        )
    }
}

@Composable
private fun DirectPropertiesPanel(
    selectedNode: FeatureNode,
    selectedFace: EditableCadFace,
    state: ParametricFeatureState,
    document: FaceWorkspaceDocument?,
    onEditExtrusion: () -> Unit,
    onEditSketch: () -> Unit,
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
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PropertyLine("Elemento", when (selectedNode) {
                    FeatureNode.EXTRUSION -> "Saliente-Extruir1"
                    FeatureNode.SKETCH -> "Croquis1"
                    FeatureNode.IMPORTED -> document?.objectName ?: "Importado"
                    else -> document?.name ?: "Documento"
                })
                PropertyLine("Cara", when (selectedFace) {
                    EditableCadFace.TOP -> "Plana superior"
                    EditableCadFace.SIDE -> "Cilíndrica"
                    EditableCadFace.NONE -> "—"
                })
                if (document?.parametric == true) {
                    PropertyLine("Profundidad", formatMm(state.extrusion.lengthMm))
                    PropertyLine("Diámetro", "Ø${formatMm(state.sketch.diameterMm)}")
                    PropertyLine("Desde", "Plano de croquis")
                    HorizontalDivider()
                    Button(onClick = if (selectedNode == FeatureNode.SKETCH) onEditSketch else onEditExtrusion, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selectedNode == FeatureNode.SKETCH) "Editar Croquis1" else "Editar operación", fontSize = 9.sp)
                    }
                }
                document?.let {
                    HorizontalDivider()
                    PropertyLine("Formato", it.format)
                    PropertyLine("Vértices", it.mesh.vertexCount.toString())
                    PropertyLine("Triángulos", it.mesh.triangleCount.toString())
                    Text(it.summary, fontSize = 7.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF607D8B), maxLines = 12)
                }
            }
        }
    }
}

@Composable
private fun PropertyLine(label: String, value: String) {
    Column {
        Text(label.uppercase(Locale.ROOT), fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78909C))
        Text(value, fontSize = 10.sp, color = Color(0xFF37474F))
    }
}

@Composable
private fun DirectStatusBar(
    status: String,
    face: EditableCadFace,
    document: FaceWorkspaceDocument?,
    loading: Boolean
) {
    Surface(color = Color(0xFFE9EEF2), shadowElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(status, modifier = Modifier.weight(1f), fontSize = 8.sp, color = Color(0xFF455A64), maxLines = 1)
            if (loading) CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                when (face) {
                    EditableCadFace.TOP -> "CARA SUPERIOR"
                    EditableCadFace.SIDE -> "CARA CURVA"
                    EditableCadFace.NONE -> "SELECCIÓN"
                },
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = if (face == EditableCadFace.NONE) Color(0xFF607D8B) else Color(0xFFE65100)
            )
            Spacer(Modifier.width(10.dp))
            Text(document?.format ?: "—", fontSize = 7.sp, color = Color(0xFF607D8B))
        }
    }
}

private suspend fun buildCylinderDocument(state: ParametricFeatureState, fitCamera: Boolean): FaceWorkspaceDocument =
    withContext(Dispatchers.IO) {
        val scene = NativeCadBridge.createCylinderScene(
            diameterMm = state.safeDiameter(),
            lengthMm = state.safeLength(),
            documentName = state.documentName
        )
        FaceWorkspaceDocument(
            name = state.documentName,
            objectName = "Saliente-Extruir1",
            format = "FreeCAD paramétrico",
            mesh = scene.mesh.toNativeMesh(),
            summary = "FreeCAD Base 1.1.1\n${scene.buildInfo}\n${scene.documentSummary}",
            parametric = true,
            token = state.revision,
            fitCamera = fitCamera
        )
    }

private suspend fun loadExternalDocument(context: android.content.Context, uri: Uri): FaceWorkspaceDocument =
    withContext(Dispatchers.IO) {
        val staged = AndroidDocumentLoader.stage(context, uri, "solidfreecad-face-ui", "document.step")
        val file = staged.first
        val name = staged.second
        when (AndroidDocumentLoader.extension(name)) {
            "step", "stp" -> {
                val scene = NativeStepBridge.importStep(file.absolutePath, name)
                FaceWorkspaceDocument(
                    name = name,
                    objectName = name.substringBeforeLast('.'),
                    format = "STEP",
                    mesh = scene.mesh.toNativeMesh(),
                    summary = scene.summary,
                    parametric = false,
                    token = System.nanoTime(),
                    fitCamera = true
                )
            }
            "fcstd" -> {
                val archive = FreeCadArchiveReader.extract(file, File(context.cacheDir, "solidfreecad-face-fcstd"), name)
                val scene = NativeFreeCadFileBridge.importFcStdBreps(
                    archive.brepFiles.map(File::getAbsolutePath),
                    name,
                    archive.summary
                )
                FaceWorkspaceDocument(
                    name = name,
                    objectName = archive.documentName,
                    format = "FCStd",
                    mesh = scene.mesh.toNativeMesh(),
                    summary = scene.summary,
                    parametric = false,
                    token = System.nanoTime(),
                    fitCamera = true
                )
            }
            else -> error("Seleccione un archivo STEP, STP o FCStd")
        }
    }

private fun SceneMesh.toNativeMesh(): NativeSceneMesh = NativeSceneMesh(
    vertices = vertices,
    indices = indices,
    minX = minX,
    minY = minY,
    minZ = minZ,
    maxX = maxX,
    maxY = maxY,
    maxZ = maxZ
)

private fun numericText(value: String): String = value.filterIndexed { index, char ->
    char.isDigit() || char == '.' || char == ',' || (char == '-' && index == 0)
}.replace(',', '.')

private fun readableFailure(error: Throwable): String =
    generateSequence(error) { it.cause }.last().message ?: error.message ?: error::class.java.simpleName

private fun formatMm(value: Double): String = "%.2f mm".format(Locale.US, value)
private fun formatNumber(value: Double): String = "%.2f".format(Locale.US, value)
