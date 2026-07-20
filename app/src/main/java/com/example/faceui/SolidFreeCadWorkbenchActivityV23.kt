package com.example.faceui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.features.*
import com.example.nativecad.viewer.CameraPreset
import com.example.nativecad.viewer.NativeSceneMesh
import com.example.ui.theme.MyApplicationTheme
import com.medinaparra.freecadandroid.io.AndroidDocumentLoader
import com.medinaparra.freecadandroid.io.FreeCadArchiveReader
import com.medinaparra.freecadandroid.macro.SolidFreeCadMacroRuntime
import com.medinaparra.freecadandroid.model.SceneMesh
import com.medinaparra.freecadandroid.nativebridge.NativeFreeCadFileBridge
import com.medinaparra.freecadandroid.nativebridge.NativeStepBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private val V23Text = Color(0xFF0F172A)
private val V23Secondary = Color(0xFF334155)
private val V23Accent = Color(0xFF15557C)
private val V23Panel = Color(0xFFF8FAFC)
private val V23Header = Color(0xFFE4ECF2)
private val V23Divider = Color(0xFF94A3B8)
private val V23Selected = Color(0xFFCBE6F6)
private const val V23AnimMs = 230

private enum class V23SelectionType { NONE, PLANE, SKETCH, FEATURE }
private enum class V23TrayTab(val label: String) {
    SKETCH("Croquis"), MATERIAL("Operaciones"), CUT("Cortes"), FINISH("Acabados"), PATTERN("Patrones"), BODY("Cuerpos")
}
private enum class V23PlaneDialogMode { OFFSET, FACE }

private data class V23Selection(val type: V23SelectionType = V23SelectionType.NONE, val id: Long? = null)
private data class V23Document(
    val name: String,
    val format: String,
    val mesh: NativeSceneMesh,
    val summary: String,
    val program: BasicCadProgram?,
    val token: Long,
    val fit: Boolean
)

class SolidFreeCadWorkbenchActivityV23 : ComponentActivity() {
    private var cadSurface: FaceDrivenCadSurfaceView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialUri = intent?.data
        setContent { MyApplicationTheme { V23Workbench(initialUri) { cadSurface = it } } }
    }
    override fun onPause() { cadSurface?.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); cadSurface?.onResume() }
}

@Composable
private fun V23Workbench(initialUri: Uri?, onSurfaceReady: (FaceDrivenCadSurfaceView) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf(BasicCadHistory(BasicCadProgram())) }
    var document by remember { mutableStateOf<V23Document?>(null) }
    var selection by remember { mutableStateOf(V23Selection(V23SelectionType.FEATURE, 1L)) }
    var selectedFace by remember { mutableStateOf(EditableCadFace.NONE) }
    var treeVisible by rememberSaveable { mutableStateOf(true) }
    var propertiesVisible by rememberSaveable { mutableStateOf(true) }
    var trayVisible by rememberSaveable { mutableStateOf(true) }
    var trayTab by rememberSaveable { mutableStateOf(V23TrayTab.SKETCH) }
    var sketchEditingId by remember { mutableStateOf<Long?>(null) }
    var planeDialog by remember { mutableStateOf<V23PlaneDialogMode?>(null) }
    var surface by remember { mutableStateOf<FaceDrivenCadSurfaceView?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Preparando FreeCAD…") }
    var error by remember { mutableStateOf<String?>(null) }

    fun rebuild(candidate: BasicCadHistory, fit: Boolean, message: String) {
        if (loading) return
        loading = true
        error = null
        selectedFace = EditableCadFace.NONE
        surface?.setSelectedFace(EditableCadFace.NONE)
        scope.launch {
            runCatching { v23BuildProgram(context, candidate.current, fit) }
                .onSuccess {
                    history = candidate
                    document = it
                    selection = V23Selection(V23SelectionType.FEATURE, candidate.current.features.lastOrNull()?.id)
                    status = message
                }
                .onFailure {
                    error = v23Failure(it)
                    status = "No se pudo reconstruir el modelo"
                    surface?.restoreCommittedPreview()
                }
            loading = false
        }
    }

    fun commit(next: BasicCadProgram, fit: Boolean = false, message: String) {
        if (next == history.current) { status = "No hay cambios para aplicar"; return }
        rebuild(history.commit(next), fit, message)
    }

    fun openUri(uri: Uri) {
        if (loading) return
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        loading = true
        status = "Abriendo documento…"
        error = null
        scope.launch {
            runCatching { v23LoadExternal(context, uri) }
                .onSuccess {
                    document = it
                    it.program?.let { p -> history = history.reset(p) }
                    selection = V23Selection()
                    status = "${it.format} cargado"
                }
                .onFailure { error = v23Failure(it); status = "Error al abrir el archivo" }
            loading = false
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::openUri) }
    LaunchedEffect(initialUri) {
        if (initialUri != null) openUri(initialUri)
        else rebuild(history.reset(BasicCadProgram()), true, "Pieza paramétrica creada")
    }

    val program = document?.program
    val selectedFeature = selection.takeIf { it.type == V23SelectionType.FEATURE }?.id?.let { id -> program?.features?.firstOrNull { it.id == id } }
    val selectedPlane = selection.takeIf { it.type == V23SelectionType.PLANE }?.id?.let { id -> program?.planeSet?.planes?.firstOrNull { it.id == id } }
    val selectedSketch = selection.takeIf { it.type == V23SelectionType.SKETCH }?.id?.let { id -> program?.sketches?.firstOrNull { it.id == id } }
    val directCylinder = program?.features?.size == 1 && program.features.first().parameters.containsKey("diameter")

    Scaffold(
        topBar = {
            V23TopBar(
                documentName = document?.name ?: "Sin título",
                loading = loading,
                canUndo = program != null && history.canUndo,
                canRedo = program != null && history.canRedo,
                onNew = { rebuild(history.reset(BasicCadProgram()), true, "Pieza paramétrica creada") },
                onOpen = { open.launch(arrayOf("*/*")) },
                onUndo = { history.undo()?.let { rebuild(it, false, "Operación deshecha") } },
                onRedo = { history.redo()?.let { rebuild(it, false, "Operación rehecha") } },
                onFit = { document?.mesh?.let { surface?.fit(it) } },
                onPreset = { surface?.setPreset(it) },
                onToggleTree = { treeVisible = !treeVisible },
                onToggleProperties = { propertiesVisible = !propertiesVisible },
                onToggleTray = { trayVisible = !trayVisible }
            )
        },
        bottomBar = {
            V23StatusBar(status, loading, document?.format ?: "—", program?.features?.size)
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFFE7ECEF))) {
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = treeVisible,
                    enter = expandHorizontally(expandFrom = Alignment.Start, animationSpec = tween(V23AnimMs, easing = FastOutSlowInEasing)) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = tween(V23AnimMs, easing = FastOutSlowInEasing)) + fadeOut()
                ) {
                    V23Tree(
                        program = program,
                        selection = selection,
                        enabled = !loading,
                        onSelect = { selection = it; propertiesVisible = true },
                        onCreatePlane = { planeDialog = V23PlaneDialogMode.OFFSET },
                        onClose = { treeVisible = false },
                        modifier = Modifier.width(245.dp)
                    )
                }

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AndroidView(
                        factory = { c -> FaceDrivenCadSurfaceView(c).also { surface = it; onSurfaceReady(it) } },
                        update = { view ->
                            view.onFaceSelected = { face ->
                                if (!loading) {
                                    selectedFace = face
                                    view.setSelectedFace(face)
                                    status = when (face) {
                                        EditableCadFace.TOP -> "Cara plana superior seleccionada"
                                        EditableCadFace.SIDE -> "Cara curva seleccionada"
                                        EditableCadFace.NONE -> "Selección cancelada"
                                    }
                                }
                            }
                            view.onParameterPreview = { face, value ->
                                if (!loading) status = if (face == EditableCadFace.TOP) "Profundidad: ${v23Mm(value.toDouble())}" else "Diámetro: ${v23Mm(value.toDouble())}"
                            }
                            view.onParameterCommit = { face, value ->
                                if (!loading && directCylinder) {
                                    val first = history.current.features.first()
                                    val diameter = if (face == EditableCadFace.SIDE) value.toDouble() else first.parameters["diameter"] ?: 34.93
                                    val depth = if (face == EditableCadFace.TOP) value.toDouble() else first.parameters["depth"] ?: 40.0
                                    commit(history.current.updateBaseCylinder(diameter, depth), message = "Extrusión actualizada desde la flecha")
                                }
                            }
                            document?.let { d -> if (view.tag != d.token) { view.tag = d.token; view.setMesh(d.mesh, d.fit) } }
                            val first = program?.features?.firstOrNull()
                            view.setDimensions((first?.parameters?.get("depth") ?: 40.0).toFloat(), (first?.parameters?.get("diameter") ?: 34.93).toFloat())
                            view.setSelectedFace(if (!loading && directCylinder) selectedFace else EditableCadFace.NONE)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!treeVisible) V23EdgeHandle("Árbol", "▶", { treeVisible = true }, Modifier.align(Alignment.CenterStart), false)
                    if (!propertiesVisible) V23EdgeHandle("Propiedades", "◀", { propertiesVisible = true }, Modifier.align(Alignment.CenterEnd), true)

                    sketchEditingId?.let { id ->
                        program?.sketches?.firstOrNull { it.id == id }?.let { sketch ->
                            V23SketchEditor(
                                sketch = sketch,
                                onCancel = { sketchEditingId = null },
                                onAccept = { parameters ->
                                    val primitive = sketch.primitives.firstOrNull()
                                    if (primitive != null) commit(history.current.updateSketchPrimitive(sketch.id, primitive.id, parameters), message = "${sketch.label} actualizado")
                                    sketchEditingId = null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (loading) V23Loading(Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
                    error?.let { V23Error(it, { error = null }, Modifier.align(Alignment.BottomStart).padding(12.dp)) }
                }

                AnimatedVisibility(
                    visible = propertiesVisible,
                    enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(V23AnimMs, easing = FastOutSlowInEasing)) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(V23AnimMs, easing = FastOutSlowInEasing)) + fadeOut()
                ) {
                    V23Properties(
                        feature = selectedFeature,
                        plane = selectedPlane,
                        sketch = selectedSketch,
                        program = program,
                        enabled = !loading,
                        onApplyFeature = { f, values -> commit(history.current.updateFeature(f.id, values), message = "${f.label} recalculado") },
                        onToggleFeature = { f -> commit(history.current.toggleSuppressed(f.id), message = "Estado de ${f.label} actualizado") },
                        onEditSketch = { sketchEditingId = it.id },
                        onSelectPlane = { id -> commit(history.current.selectPlane(id), message = "Plano activo actualizado") },
                        onTogglePlane = { id -> commit(history.current.togglePlaneVisibility(id), message = "Visibilidad del plano actualizada") },
                        onCreateOffset = { planeDialog = V23PlaneDialogMode.OFFSET },
                        onCreateFace = { planeDialog = V23PlaneDialogMode.FACE },
                        onClose = { propertiesVisible = false },
                        modifier = Modifier.width(285.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = trayVisible && sketchEditingId == null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = tween(V23AnimMs, easing = FastOutSlowInEasing)) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(V23AnimMs, easing = FastOutSlowInEasing)) + fadeOut()
            ) {
                V23BottomTray(
                    tab = trayTab,
                    program = program,
                    enabled = !loading && program != null,
                    onTab = { trayTab = it },
                    onClose = { trayVisible = false },
                    onCreateSketch = { primitive ->
                        program?.let {
                            val next = it.createSketch(it.activePlaneId, primitive)
                            commit(next, message = "Croquis creado en ${next.planeSet.plane(next.activePlaneId).label}")
                            sketchEditingId = next.activeSketchId
                        }
                    },
                    onCreatePlane = { planeDialog = V23PlaneDialogMode.OFFSET },
                    onOperation = { operation ->
                        program?.let {
                            runCatching { it.append(operation, it.activePlaneId, it.activeSketchId) }
                                .onSuccess { next -> commit(next, message = "${operation.label} añadido") }
                                .onFailure { failure -> error = failure.message ?: "La operación requiere un croquis" }
                        }
                    }
                )
            }
            if (!trayVisible && sketchEditingId == null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).clickable { trayVisible = true },
                    color = V23Accent,
                    shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                    shadowElevation = 5.dp
                ) { Text("▲ Operaciones", Modifier.padding(horizontal = 18.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp) }
            }

            planeDialog?.let { mode ->
                V23PlaneDialog(
                    mode = mode,
                    program = history.current,
                    selectedFace = selectedFace,
                    onCancel = { planeDialog = null },
                    onCreate = { parentId, offset, label ->
                        val next = when (mode) {
                            V23PlaneDialogMode.OFFSET -> history.current.createOffsetPlane(parentId, offset, label)
                            V23PlaneDialogMode.FACE -> {
                                if (selectedFace != EditableCadFace.TOP) error("Seleccione una cara plana antes de crear el plano")
                                val depth = history.current.features.firstOrNull()?.parameters?.get("depth") ?: 40.0
                                history.current.createFaceParallelPlane(
                                    CadFaceReference("Cuerpo1", "FaceTop", CadVector3(0.0, 0.0, depth), CadVector3(0.0, 0.0, 1.0)),
                                    offset,
                                    label
                                )
                            }
                        }
                        planeDialog = null
                        commit(next, message = "Plano de referencia creado")
                    }
                )
            }
        }
    }
}

@Composable
private fun V23TopBar(documentName: String, loading: Boolean, canUndo: Boolean, canRedo: Boolean, onNew: () -> Unit, onOpen: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onFit: () -> Unit, onPreset: (CameraPreset) -> Unit, onToggleTree: () -> Unit, onToggleProperties: () -> Unit, onToggleTray: () -> Unit) {
    Surface(color = Color(0xFFF1F3F5), contentColor = V23Text, shadowElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFD32F2F), shape = RoundedCornerShape(4.dp)) { Text("SF", Modifier.padding(horizontal = 7.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 150.dp)) { Text("SolidFreeCAD", fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(documentName, color = V23Secondary, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                V23Action("Nuevo", Icons.Default.NoteAdd, !loading, onNew)
                V23Action("Abrir", Icons.Default.FolderOpen, !loading, onOpen)
                V23Action("Deshacer", Icons.Default.Undo, canUndo && !loading, onUndo)
                V23Action("Rehacer", Icons.Default.Redo, canRedo && !loading, onRedo)
                V23Action("ISO", Icons.Default.ViewInAr, true) { onPreset(CameraPreset.ISOMETRIC) }
                V23Action("Frente", Icons.Default.CropSquare, true) { onPreset(CameraPreset.FRONT) }
                V23Action("Planta", Icons.Default.Layers, true) { onPreset(CameraPreset.TOP) }
                V23Action("Encuadrar", Icons.Default.FilterCenterFocus, true, onFit)
                V23Action("Árbol", Icons.Default.AccountTree, true, onToggleTree)
                V23Action("Prop.", Icons.Default.Tune, true, onToggleProperties)
                V23Action("Bandeja", Icons.Default.KeyboardArrowUp, true, onToggleTray)
            }
        }
    }
}

@Composable private fun V23Action(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(62.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = if (enabled) V23Accent else Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
        Text(label, color = if (enabled) V23Text else Color(0xFF94A3B8), fontSize = 7.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun V23Tree(program: BasicCadProgram?, selection: V23Selection, enabled: Boolean, onSelect: (V23Selection) -> Unit, onCreatePlane: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxHeight(), color = V23Panel, contentColor = V23Text) {
        Column {
            Row(Modifier.fillMaxWidth().background(V23Header).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("FeatureManager", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                IconButton(onClick = onCreatePlane, enabled = enabled, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.GridOn, "Crear plano", tint = V23Accent) }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowLeft, "Ocultar", tint = V23Accent) }
            }
            HorizontalDivider(color = V23Divider)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
                Text(program?.documentName?.plus(".FCStd") ?: "Documento importado", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("  ▾ Cuerpo1", fontWeight = FontWeight.Medium, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
                Text("      ▾ Origen", color = V23Secondary, fontSize = 8.sp)
                program?.planeSet?.planes?.forEach { plane ->
                    V23TreeRow("          ◫ ${plane.label}", selection == V23Selection(V23SelectionType.PLANE, plane.id), enabled) { onSelect(V23Selection(V23SelectionType.PLANE, plane.id)) }
                }
                program?.sketches?.forEach { sketch ->
                    V23TreeRow("      ✎ ${sketch.label}", selection == V23Selection(V23SelectionType.SKETCH, sketch.id), enabled) { onSelect(V23Selection(V23SelectionType.SKETCH, sketch.id)) }
                }
                program?.features?.forEach { feature ->
                    V23TreeRow("      ${if (feature.suppressed) "○" else "◆"} ${feature.label}", selection == V23Selection(V23SelectionType.FEATURE, feature.id), enabled) { onSelect(V23Selection(V23SelectionType.FEATURE, feature.id)) }
                    feature.sketchId?.let { sketchId -> program.sketches.firstOrNull { it.id == sketchId }?.let { Text("          └ ${it.label}", color = V23Secondary, fontSize = 7.sp) } }
                }
            }
        }
    }
}

@Composable private fun V23TreeRow(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().background(if (selected) V23Selected else Color.Transparent, RoundedCornerShape(4.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 5.dp, vertical = 7.dp), color = V23Text, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
}

@Composable
private fun V23Properties(feature: BasicCadFeature?, plane: CadReferencePlane?, sketch: CadSketch?, program: BasicCadProgram?, enabled: Boolean, onApplyFeature: (BasicCadFeature, Map<String, Double>) -> Unit, onToggleFeature: (BasicCadFeature) -> Unit, onEditSketch: (CadSketch) -> Unit, onSelectPlane: (Long) -> Unit, onTogglePlane: (Long) -> Unit, onCreateOffset: () -> Unit, onCreateFace: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxHeight(), color = V23Panel, contentColor = V23Text) {
        Column {
            Row(Modifier.fillMaxWidth().background(V23Header).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("PROPIEDADES", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowRight, "Ocultar", tint = V23Accent) }
            }
            HorizontalDivider(color = V23Divider)
            when {
                feature != null -> V23FeatureProperties(feature, enabled, onApplyFeature, onToggleFeature, Modifier.fillMaxSize())
                plane != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(plane.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Origen: ${v23Vec(plane.origin)}", color = V23Secondary, fontSize = 9.sp)
                    Text("Normal: ${v23Vec(plane.normalizedNormal)}", color = V23Secondary, fontSize = 9.sp)
                    Text("Desplazamiento: ${v23Mm(plane.offset)}", color = V23Secondary, fontSize = 9.sp)
                    Button(onClick = { onSelectPlane(plane.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Usar como plano activo") }
                    OutlinedButton(onClick = { onTogglePlane(plane.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Icon(if (plane.visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null); Spacer(Modifier.width(5.dp)); Text(if (plane.visible) "Ocultar plano" else "Mostrar plano") }
                    Button(onClick = onCreateOffset, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(5.dp)); Text("Crear plano paralelo") }
                }
                sketch != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sketch.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Plano: ${program?.planeSet?.plane(sketch.planeId)?.label}", color = V23Secondary)
                    Text("Entidades: ${sketch.primitives.size}", color = V23Secondary)
                    Button(onClick = { onEditSketch(sketch) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(5.dp)); Text("Editar croquis") }
                }
                else -> Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seleccione una operación, croquis o plano.", color = V23Secondary)
                    Button(onClick = onCreateOffset, enabled = enabled) { Text("Crear plano paralelo") }
                    OutlinedButton(onClick = onCreateFace, enabled = enabled) { Text("Plano paralelo a cara") }
                }
            }
        }
    }
}

@Composable
private fun V23FeatureProperties(feature: BasicCadFeature, enabled: Boolean, onApply: (BasicCadFeature, Map<String, Double>) -> Unit, onToggle: (BasicCadFeature) -> Unit, modifier: Modifier) {
    var draft by remember(feature.id, feature.parameters) { mutableStateOf(feature.parameters.mapValues { v23Number(it.value) }) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(feature.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(feature.operation.description, color = V23Secondary, fontSize = 9.sp)
        feature.parameters.forEach { (key, _) ->
            OutlinedTextField(draft[key].orEmpty(), { text -> draft = draft + (key to text.filter { it.isDigit() || it in ".,-" }.replace(',', '.')) }, label = { Text(key) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = { val parsed = draft.mapValues { it.value.toDoubleOrNull() ?: return@Button }; onApply(feature, parsed) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(5.dp)); Text("Aplicar y recalcular") }
        OutlinedButton(onClick = { onToggle(feature) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(if (feature.suppressed) "Activar" else "Suprimir") }
    }
}

@Composable
private fun V23BottomTray(tab: V23TrayTab, program: BasicCadProgram?, enabled: Boolean, onTab: (V23TrayTab) -> Unit, onClose: () -> Unit, onCreateSketch: (CadSketchPrimitiveKind) -> Unit, onCreatePlane: () -> Unit, onOperation: (BasicCadOperation) -> Unit) {
    Surface(Modifier.fillMaxWidth(0.92f).heightIn(min = 126.dp, max = 185.dp), color = V23Panel, contentColor = V23Text, shape = RoundedCornerShape(12.dp), shadowElevation = 10.dp) {
        Column {
            Row(Modifier.fillMaxWidth().background(V23Header).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                V23TrayTab.entries.forEach { item -> Text(item.label, Modifier.clickable { onTab(item) }.background(if (item == tab) Color.White else Color.Transparent, RoundedCornerShape(5.dp)).padding(horizontal = 10.dp, vertical = 7.dp), color = if (item == tab) V23Accent else V23Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f)); IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Ocultar") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    V23TrayTab.SKETCH -> {
                        V23Tile("Plano", "Nuevo plano", enabled, onCreatePlane)
                        V23Tile("Círculo", "Croquis circular", enabled) { onCreateSketch(CadSketchPrimitiveKind.CIRCLE) }
                        V23Tile("Rectángulo", "Croquis rectangular", enabled) { onCreateSketch(CadSketchPrimitiveKind.RECTANGLE) }
                        V23Tile("Línea", "Segmento", enabled) { onCreateSketch(CadSketchPrimitiveKind.LINE) }
                        V23Tile("Arco", "Arco", enabled) { onCreateSketch(CadSketchPrimitiveKind.ARC) }
                        V23Tile("Polígono", "Polígono", enabled) { onCreateSketch(CadSketchPrimitiveKind.POLYGON) }
                    }
                    else -> v23OperationsFor(tab).forEach { op -> V23Tile(op.label, op.description, enabled && (!op.requiresSketch || program?.activeSketchId != null)) { onOperation(op) } }
                }
            }
        }
    }
}

private fun v23OperationsFor(tab: V23TrayTab): List<BasicCadOperation> = when (tab) {
    V23TrayTab.MATERIAL -> listOf(BasicCadOperation.BOSS_EXTRUDE, BasicCadOperation.BOSS_REVOLVE, BasicCadOperation.BOSS_SWEEP, BasicCadOperation.BOSS_LOFT, BasicCadOperation.RIB)
    V23TrayTab.CUT -> listOf(BasicCadOperation.CUT_EXTRUDE, BasicCadOperation.CUT_REVOLVE, BasicCadOperation.CUT_SWEEP, BasicCadOperation.CUT_LOFT, BasicCadOperation.SIMPLE_HOLE, BasicCadOperation.COUNTERBORE_HOLE, BasicCadOperation.COUNTERSINK_HOLE)
    V23TrayTab.FINISH -> listOf(BasicCadOperation.FILLET, BasicCadOperation.CHAMFER, BasicCadOperation.SHELL, BasicCadOperation.DRAFT)
    V23TrayTab.PATTERN -> listOf(BasicCadOperation.LINEAR_PATTERN, BasicCadOperation.CIRCULAR_PATTERN, BasicCadOperation.MIRROR, BasicCadOperation.MOVE_COPY_BODY)
    V23TrayTab.BODY -> listOf(BasicCadOperation.COMBINE_ADD, BasicCadOperation.COMBINE_SUBTRACT, BasicCadOperation.COMBINE_COMMON)
    V23TrayTab.SKETCH -> emptyList()
}

@Composable private fun V23Tile(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(Modifier.width(132.dp).height(82.dp).clickable(enabled = enabled, onClick = onClick), color = if (enabled) Color(0xFFEDF3F7) else Color(0xFFE2E8F0), shape = RoundedCornerShape(8.dp), shadowElevation = 1.dp) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.Center) { Text(title, color = if (enabled) V23Text else Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 9.sp, maxLines = 2); Spacer(Modifier.height(4.dp)); Text(subtitle, color = if (enabled) V23Secondary else Color(0xFF94A3B8), fontSize = 7.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun V23SketchEditor(sketch: CadSketch, onCancel: () -> Unit, onAccept: (Map<String, Double>) -> Unit, modifier: Modifier) {
    val primitive = sketch.primitives.firstOrNull() ?: return
    var draft by remember(sketch.id, primitive.parameters) { mutableStateOf(primitive.parameters.mapValues { v23Number(it.value) }) }
    Surface(modifier, color = Color(0xFFF7FAFC).copy(alpha = 0.97f), contentColor = V23Text) {
        Column {
            Row(Modifier.fillMaxWidth().background(V23Header).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Editando ${sketch.label}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(onClick = onCancel) { Text("Cancelar") }
                Button(onClick = { val parsed = draft.mapValues { it.value.toDoubleOrNull() ?: return@Button }; onAccept(parsed) }) { Icon(Icons.Default.Check, null); Text("Aceptar") }
            }
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().padding(18.dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawLine(Color(0xFF94A3B8), Offset(0f, center.y), Offset(size.width, center.y), 1f)
                        drawLine(Color(0xFF94A3B8), Offset(center.x, 0f), Offset(center.x, size.height), 1f)
                        when (primitive.kind) {
                            CadSketchPrimitiveKind.CIRCLE -> drawCircle(Color(0xFF1976D2), radius = minOf(size.width, size.height) * 0.22f, center = center, style = Stroke(5f))
                            CadSketchPrimitiveKind.RECTANGLE -> drawRect(Color(0xFF1976D2), topLeft = Offset(size.width * 0.27f, size.height * 0.3f), size = androidx.compose.ui.geometry.Size(size.width * 0.46f, size.height * 0.4f), style = Stroke(5f))
                            CadSketchPrimitiveKind.LINE -> drawLine(Color(0xFF1976D2), Offset(size.width * 0.25f, center.y), Offset(size.width * 0.75f, center.y), 5f)
                            CadSketchPrimitiveKind.ARC -> drawArc(Color(0xFF1976D2), 180f, 180f, false, Offset(size.width * 0.3f, size.height * 0.3f), androidx.compose.ui.geometry.Size(size.width * 0.4f, size.height * 0.4f), style = Stroke(5f))
                            CadSketchPrimitiveKind.POLYGON -> drawCircle(Color(0xFF1976D2), radius = minOf(size.width, size.height) * 0.22f, center = center, style = Stroke(5f))
                        }
                    }
                }
                Column(Modifier.width(270.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cotas y restricciones", fontWeight = FontWeight.Bold)
                    draft.forEach { (key, value) -> OutlinedTextField(value, { draft = draft + (key to it.filter { c -> c.isDigit() || c in ".,-" }.replace(',', '.')) }, label = { Text(key) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    Text("Las cotas modifican el croquis y las operaciones dependientes al aceptar.", color = V23Secondary, fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable
private fun V23PlaneDialog(mode: V23PlaneDialogMode, program: BasicCadProgram, selectedFace: EditableCadFace, onCancel: () -> Unit, onCreate: (Long, Double, String?) -> Unit) {
    var parentId by remember { mutableStateOf(program.activePlaneId) }
    var offset by remember { mutableStateOf("10") }
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (mode == V23PlaneDialogMode.OFFSET) "Crear plano paralelo" else "Plano paralelo a cara") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (mode == V23PlaneDialogMode.OFFSET) {
                    Text("Plano de referencia", fontWeight = FontWeight.Bold)
                    program.planeSet.planes.forEach { plane -> Row(Modifier.fillMaxWidth().clickable { parentId = plane.id }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(parentId == plane.id, { parentId = plane.id }); Text(plane.label) } }
                } else Text(if (selectedFace == EditableCadFace.TOP) "Cara plana superior seleccionada" else "Seleccione primero una cara plana en el visor", color = if (selectedFace == EditableCadFace.TOP) Color(0xFF1B6E32) else Color(0xFFB42318))
                OutlinedTextField(offset, { offset = it.filter { c -> c.isDigit() || c in ".,-" }.replace(',', '.') }, label = { Text("Desplazamiento (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(label, { label = it }, label = { Text("Nombre opcional") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { val value = offset.toDoubleOrNull() ?: return@Button; onCreate(parentId, value, label.takeIf { it.isNotBlank() }) }, enabled = mode == V23PlaneDialogMode.OFFSET || selectedFace == EditableCadFace.TOP) { Text("Crear") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
    )
}

@Composable private fun V23EdgeHandle(label: String, arrow: String, onClick: () -> Unit, modifier: Modifier, right: Boolean) { Surface(modifier.clickable(onClick = onClick), color = V23Accent, contentColor = Color.White, shape = if (right) RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp) else RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp), shadowElevation = 4.dp) { Column(Modifier.padding(horizontal = 7.dp, vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(arrow, fontWeight = FontWeight.Bold); Text(label, fontSize = 7.sp, fontWeight = FontWeight.Bold) } } }
@Composable private fun V23Loading(modifier: Modifier) { Surface(modifier.widthIn(min = 230.dp), color = Color.White, shape = RoundedCornerShape(8.dp), shadowElevation = 5.dp) { Column(Modifier.padding(12.dp)) { Text("FreeCAD está recalculando el BRep…", fontWeight = FontWeight.Bold, fontSize = 9.sp); Spacer(Modifier.height(7.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) } } }
@Composable private fun V23Error(message: String, onClose: () -> Unit, modifier: Modifier) { Surface(modifier.widthIn(max = 460.dp), color = Color(0xFFFFEBEE), contentColor = Color(0xFF5F1414), shape = RoundedCornerShape(8.dp), shadowElevation = 5.dp) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Error, null, tint = Color(0xFFC62828)); Spacer(Modifier.width(7.dp)); Text(message, Modifier.weight(1f), fontSize = 9.sp); IconButton(onClick = onClose) { Icon(Icons.Default.Cancel, "Cerrar") } } } }
@Composable private fun V23StatusBar(status: String, loading: Boolean, format: String, count: Int?) { Surface(color = Color(0xFFE9EEF2), contentColor = V23Text, shadowElevation = 2.dp) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Text(status, Modifier.weight(1f), fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("$format${count?.let { " · $it operaciones" } ?: ""}", color = V23Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold) } } }

private suspend fun v23BuildProgram(context: android.content.Context, program: BasicCadProgram, fit: Boolean): V23Document = withContext(Dispatchers.IO) {
    val scene = SolidFreeCadMacroRuntime.execute(context, BasicCadMacroGenerator.generate(program), 0.18, 0.24)
    V23Document("${program.documentName}.FCStd", "Modelo paramétrico", scene.mesh.v23Native(), "FreeCAD Base 1.1.1 / Runtime 0.11\n${scene.pythonVersion}\n${scene.documentSummary}", program, System.nanoTime(), fit)
}
private suspend fun v23LoadExternal(context: android.content.Context, uri: Uri): V23Document = withContext(Dispatchers.IO) {
    val fallback = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.contains('.') } ?: "document.FCStd"
    val (file, name) = AndroidDocumentLoader.stage(context, uri, "solidfreecad-workbench-v23", fallback)
    val extension = v23Extension(context, uri, file, name)
    when (extension) {
        "step", "stp" -> NativeStepBridge.importStep(file.absolutePath, name).let { V23Document(name, "STEP", it.mesh.v23Native(), it.summary, null, System.nanoTime(), true) }
        "fcstd" -> { val archive = FreeCadArchiveReader.extract(file, File(context.cacheDir, "solidfreecad-fcstd-v23"), name); NativeFreeCadFileBridge.importFcStdObjects(archive.objects, name, archive.summary).let { V23Document(name, "FCStd", it.mesh.v23Native(), it.summary, null, System.nanoTime(), true) } }
        "fcmacro", "py" -> SolidFreeCadMacroRuntime.executeFile(context, file.readBytes(), 0.18, 0.24).let { V23Document(name, "FCMacro", it.mesh.v23Native(), "Macro FreeCAD ejecutada\n${it.pythonVersion}\n${it.documentSummary}\n${it.output}", null, System.nanoTime(), true) }
        else -> error("Formato no compatible: .$extension. Use STEP, FCStd o FCMacro")
    }
}
private fun v23Extension(context: android.content.Context, uri: Uri, file: File, name: String): String {
    val ext = AndroidDocumentLoader.extension(name); if (ext in setOf("step", "stp", "fcstd", "fcmacro", "py")) return ext
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT); if ("python" in mime || "x-fcmacro" in mime) return "fcmacro"
    val prefix = file.inputStream().buffered().use { val b = ByteArray(4096); val n = it.read(b); if (n > 0) b.copyOf(n).toString(Charsets.UTF_8) else "" }
    return if ("import FreeCAD" in prefix || "import Part" in prefix || "App.newDocument" in prefix) "fcmacro" else ext
}
private fun SceneMesh.v23Native() = NativeSceneMesh(vertices, indices, minX, minY, minZ, maxX, maxY, maxZ)
private fun v23Failure(error: Throwable) = generateSequence(error) { it.cause }.last().message ?: error.message ?: error::class.java.simpleName
private fun v23Mm(value: Double) = "%.2f mm".format(Locale.US, value)
private fun v23Number(value: Double) = "%.4f".format(Locale.US, value).trimEnd('0').trimEnd('.')
private fun v23Vec(value: CadVector3) = "(%.2f, %.2f, %.2f)".format(Locale.US, value.x, value.y, value.z)
