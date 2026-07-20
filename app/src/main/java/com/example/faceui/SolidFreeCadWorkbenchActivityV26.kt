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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.features.*
import com.example.industrial.IndustrialCadDiagnostics
import com.example.industrial.IndustrialCadRecoveryStore
import com.example.industrial.IndustrialWorkshopPreferences
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SolidFreeCadWorkbenchActivityV26 : ComponentActivity() {
    private var cadSurface: FaceDrivenCadSurfaceViewV25? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialUri = intent?.data
        setContent { MyApplicationTheme { V26Workbench(initialUri) { cadSurface = it } } }
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

@Composable
private fun V26Workbench(initialUri: Uri?, onSurfaceReady: (FaceDrivenCadSurfaceViewV25) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf(BasicCadHistory(BasicCadProgram())) }
    var document by remember { mutableStateOf<V24Document?>(null) }
    var selection by remember { mutableStateOf(V24Selection(V24SelectionType.FEATURE, 1L)) }
    var selectedFace by remember { mutableStateOf(EditableCadFace.NONE) }
    var selectedSurface by remember { mutableStateOf<CadViewportFaceSelection?>(null) }
    var treeVisible by rememberSaveable { mutableStateOf(true) }
    var propertiesVisible by rememberSaveable { mutableStateOf(true) }
    var trayVisible by rememberSaveable { mutableStateOf(true) }
    var trayTab by rememberSaveable { mutableStateOf(V24TrayTab.SKETCH) }
    var sketchEditingId by remember { mutableStateOf<Long?>(null) }
    var planeDialog by remember { mutableStateOf<V24PlaneDialogMode?>(null) }
    var surface by remember { mutableStateOf<FaceDrivenCadSurfaceViewV25?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Preparando FreeCAD…") }
    var error by remember { mutableStateOf<String?>(null) }
    var workshopDialog by remember { mutableStateOf(false) }
    var gloveMode by remember { mutableStateOf(IndustrialWorkshopPreferences.gloveMode(context)) }
    var autosaveStatus by remember { mutableStateOf("Sin sesión recuperable") }
    var hasRecovery by remember { mutableStateOf(false) }
    var recoveryPrompt by remember { mutableStateOf<IndustrialCadRecoveryStore.RecoverySnapshot?>(null) }

    fun friendlyFailure(operation: String, throwable: Throwable): String {
        val failure = IndustrialCadDiagnostics.failure(context, operation, throwable)
        return "${failure.userMessage}\nCódigo ${failure.code}"
    }

    fun persistRecovery(program: BasicCadProgram) {
        scope.launch(Dispatchers.IO) {
            val result = runCatching { IndustrialCadRecoveryStore.save(context, program) }
            withContext(Dispatchers.Main) {
                result.onSuccess { snapshot ->
                    hasRecovery = true
                    autosaveStatus = "Guardado automático · revisión ${snapshot.program.revision}"
                }.onFailure { failure ->
                    val diagnostic = IndustrialCadDiagnostics.failure(context, "Autoguardado", failure)
                    autosaveStatus = "Autoguardado pendiente · ${diagnostic.code}"
                }
            }
        }
    }

    fun rebuild(candidate: BasicCadHistory, fit: Boolean, message: String) {
        if (loading) return
        loading = true
        error = null
        selectedFace = EditableCadFace.NONE
        surface?.setSelectedFace(EditableCadFace.NONE)
        scope.launch {
            runCatching { v24BuildProgram(context, candidate.current, fit) }
                .onSuccess {
                    history = candidate
                    document = it
                    selectedSurface = null
                    surface?.clearGenericSelection()
                    selection = V24Selection(V24SelectionType.FEATURE, candidate.current.features.lastOrNull()?.id)
                    status = message
                    IndustrialCadDiagnostics.event(context, "BRep", "$message · revisión ${candidate.current.revision}")
                    persistRecovery(candidate.current)
                }
                .onFailure {
                    error = friendlyFailure("Reconstruir modelo", it)
                    status = "El último modelo válido se mantiene"
                    surface?.restoreCommittedPreview()
                }
            loading = false
        }
    }

    fun commit(next: BasicCadProgram, fit: Boolean = false, message: String) {
        if (next == history.current) {
            status = "No hay cambios para aplicar"
            return
        }
        rebuild(history.commit(next), fit, message)
    }

    fun openUri(uri: Uri) {
        if (loading) return
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        loading = true
        status = "Abriendo documento…"
        error = null
        scope.launch {
            runCatching { v24LoadExternal(context, uri) }
                .onSuccess {
                    document = it
                    it.program?.let { program -> history = history.reset(program) }
                    selection = V24Selection()
                    selectedSurface = null
                    status = "${it.format} cargado"
                    IndustrialCadDiagnostics.event(context, "Abrir", "${it.format} · ${it.name}")
                }
                .onFailure {
                    error = friendlyFailure("Abrir archivo", it)
                    status = "No se pudo abrir el archivo"
                }
            loading = false
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::openUri) }

    LaunchedEffect(initialUri) {
        IndustrialCadDiagnostics.event(context, "Inicio", "Interfaz BRep de taller iniciada")
        if (initialUri != null) {
            openUri(initialUri)
        } else {
            val snapshot = withContext(Dispatchers.IO) { IndustrialCadRecoveryStore.loadLatest(context) }
            if (snapshot != null) {
                recoveryPrompt = snapshot
                hasRecovery = true
                autosaveStatus = "Sesión disponible · revisión ${snapshot.program.revision}"
                status = "Se encontró una sesión recuperable"
            } else {
                rebuild(history.reset(BasicCadProgram()), true, "Pieza paramétrica creada")
            }
        }
    }

    val program = document?.program
    val selectedFeature = selection.takeIf { it.type == V24SelectionType.FEATURE }?.id?.let { id -> program?.features?.firstOrNull { it.id == id } }
    val selectedPlane = selection.takeIf { it.type == V24SelectionType.PLANE }?.id?.let { id -> program?.planeSet?.planes?.firstOrNull { it.id == id } }
    val selectedSketch = selection.takeIf { it.type == V24SelectionType.SKETCH }?.id?.let { id -> program?.sketches?.firstOrNull { it.id == id } }
    val directCylinder = program?.features?.size == 1 && program.features.first().parameters.containsKey("diameter")

    CompositionLocalProvider(LocalV24GloveMode provides gloveMode) {
        Scaffold(
            topBar = {
                V24TopBar(
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
                    onToggleTray = { trayVisible = !trayVisible },
                    onWorkshop = { workshopDialog = true }
                )
            },
            bottomBar = { V24StatusBar(status, loading, document?.format ?: "—", program?.features?.size) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFFE7ECEF))) {
                Row(Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = treeVisible,
                        enter = expandHorizontally(expandFrom = Alignment.Start, animationSpec = tween(V24AnimMs, easing = FastOutSlowInEasing)) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = tween(V24AnimMs, easing = FastOutSlowInEasing)) + fadeOut()
                    ) {
                        V24Tree(
                            program = program,
                            selection = selection,
                            enabled = !loading,
                            onSelect = { selected ->
                                selectedSurface = null
                                surface?.clearGenericSelection()
                                selection = selected
                                propertiesVisible = true
                            },
                            onCreatePlane = { planeDialog = V24PlaneDialogMode.OFFSET },
                            onClose = { treeVisible = false },
                            modifier = Modifier.width(245.dp)
                        )
                    }

                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        AndroidView(
                            factory = { androidContext ->
                                FaceDrivenCadSurfaceViewV25(androidContext).also {
                                    surface = it
                                    onSurfaceReady(it)
                                }
                            },
                            update = { view ->
                                view.onFaceSelected = { face ->
                                    if (!loading) {
                                        selectedFace = face
                                        view.setSelectedFace(face)
                                    }
                                }
                                view.onGenericFaceSelected = { face ->
                                    if (!loading) {
                                        selectedSurface = face
                                        if (face != null) {
                                            selection = V24Selection()
                                            propertiesVisible = true
                                            status = if (face.planar) {
                                                "Cara plana seleccionada · normal ${v25Normal(face.normal)}"
                                            } else {
                                                "Superficie curva seleccionada"
                                            }
                                        }
                                    }
                                }
                                view.onReferencePlaneSelected = { planeId ->
                                    if (!loading) {
                                        selectedSurface = null
                                        selectedFace = EditableCadFace.NONE
                                        selection = V24Selection(V24SelectionType.PLANE, planeId)
                                        propertiesVisible = true
                                        status = program?.planeSet?.planes?.firstOrNull { it.id == planeId }?.let { "${it.label} seleccionado" } ?: "Plano seleccionado"
                                    }
                                }
                                view.onParameterPreview = { face, value ->
                                    if (!loading) status = if (face == EditableCadFace.TOP) "Profundidad: ${v24Mm(value.toDouble())}" else "Diámetro: ${v24Mm(value.toDouble())}"
                                }
                                view.onParameterCommit = { face, value ->
                                    if (!loading && directCylinder) {
                                        val first = history.current.features.first()
                                        val diameter = if (face == EditableCadFace.SIDE) value.toDouble() else first.parameters["diameter"] ?: 34.93
                                        val depth = if (face == EditableCadFace.TOP) value.toDouble() else first.parameters["depth"] ?: 40.0
                                        commit(history.current.updateBaseCylinder(diameter, depth), message = "Extrusión actualizada desde la flecha")
                                    }
                                }
                                document?.let { activeDocument ->
                                    if (view.tag != activeDocument.token) {
                                        view.tag = activeDocument.token
                                        view.setMesh(activeDocument.mesh, activeDocument.fit)
                                    }
                                }
                                val first = program?.features?.firstOrNull()
                                view.setDimensions((first?.parameters?.get("depth") ?: 40.0).toFloat(), (first?.parameters?.get("diameter") ?: 34.93).toFloat())
                                view.setDirectEditingEnabled(!loading && directCylinder)
                                view.setSelectedFace(if (!loading && directCylinder) selectedFace else EditableCadFace.NONE)
                                view.setReferencePlanes(program?.planeSet?.planes?.map { plane ->
                                    CadViewportPlane(
                                        id = plane.id,
                                        origin = floatArrayOf(plane.origin.x.toFloat(), plane.origin.y.toFloat(), plane.origin.z.toFloat()),
                                        normal = floatArrayOf(plane.normalizedNormal.x.toFloat(), plane.normalizedNormal.y.toFloat(), plane.normalizedNormal.z.toFloat()),
                                        visible = plane.visible,
                                        active = plane.id == program.activePlaneId
                                    )
                                }.orEmpty())
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (!treeVisible) V24EdgeHandle("Árbol", "▶", { treeVisible = true }, Modifier.align(Alignment.CenterStart), false)
                        if (!propertiesVisible) V24EdgeHandle("Propiedades", "◀", { propertiesVisible = true }, Modifier.align(Alignment.CenterEnd), true)

                        sketchEditingId?.let { id ->
                            program?.sketches?.firstOrNull { it.id == id }?.let { sketch ->
                                V26MultiEntitySketchEditor(
                                    sketch = sketch,
                                    onCancel = { sketchEditingId = null },
                                    onAccept = { updatedSketch ->
                                        commit(history.current.replaceSketch(updatedSketch), message = "${updatedSketch.label} actualizado")
                                        sketchEditingId = null
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        if (loading) V24Loading(Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
                        error?.let { V24Error(it, { error = null }, Modifier.align(Alignment.BottomStart).padding(12.dp)) }
                    }

                    AnimatedVisibility(
                        visible = propertiesVisible,
                        enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = tween(V24AnimMs, easing = FastOutSlowInEasing)) + fadeIn(),
                        exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(V24AnimMs, easing = FastOutSlowInEasing)) + fadeOut()
                    ) {
                        V25Properties(
                            surface = selectedSurface,
                            feature = selectedFeature,
                            plane = selectedPlane,
                            sketch = selectedSketch,
                            program = program,
                            enabled = !loading,
                            onApplyFeature = { feature, values -> commit(history.current.updateFeature(feature.id, values), message = "${feature.label} recalculado") },
                            onToggleFeature = { feature -> commit(history.current.toggleSuppressed(feature.id), message = "Estado de ${feature.label} actualizado") },
                            onEditSketch = { sketchEditingId = it.id },
                            onSelectPlane = { id -> commit(history.current.selectPlane(id), message = "Plano activo actualizado") },
                            onTogglePlane = { id -> commit(history.current.togglePlaneVisibility(id), message = "Visibilidad del plano actualizada") },
                            onCreateOffset = { planeDialog = V24PlaneDialogMode.OFFSET },
                            onCreateFace = { planeDialog = V24PlaneDialogMode.FACE },
                            onClose = { propertiesVisible = false },
                            modifier = Modifier.width(285.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = trayVisible && sketchEditingId == null,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = tween(V24AnimMs, easing = FastOutSlowInEasing)) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(V24AnimMs, easing = FastOutSlowInEasing)) + fadeOut()
                ) {
                    V24BottomTray(
                        tab = trayTab,
                        program = program,
                        enabled = !loading && program != null,
                        onTab = { trayTab = it },
                        onClose = { trayVisible = false },
                        onCreateSketch = { primitive ->
                            program?.let { current ->
                                val face = selectedSurface
                                val withPlane = if (face?.planar == true) {
                                    current.createFaceParallelPlane(
                                        CadFaceReference(
                                            objectId = "Cuerpo1",
                                            faceId = face.id,
                                            origin = CadVector3(face.point[0].toDouble(), face.point[1].toDouble(), face.point[2].toDouble()),
                                            normal = CadVector3(face.normal[0].toDouble(), face.normal[1].toDouble(), face.normal[2].toDouble())
                                        ),
                                        0.0,
                                        "Plano de cara"
                                    )
                                } else current
                                val next = withPlane.createSketch(withPlane.activePlaneId, primitive)
                                commit(next, message = "Croquis creado en ${next.planeSet.plane(next.activePlaneId).label}")
                                sketchEditingId = next.activeSketchId
                            }
                        },
                        onCreatePlane = { planeDialog = V24PlaneDialogMode.OFFSET },
                        onOperation = { operation ->
                            program?.let { current ->
                                runCatching { current.append(operation, current.activePlaneId, current.activeSketchId) }
                                    .onSuccess { next -> commit(next, message = "${operation.label} añadido") }
                                    .onFailure { failure -> error = friendlyFailure("Añadir ${operation.label}", failure) }
                            }
                        }
                    )
                }

                if (!trayVisible && sketchEditingId == null) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).clickable { trayVisible = true },
                        color = V24Accent,
                        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
                        shadowElevation = 5.dp
                    ) {
                        Text("▲ Operaciones", Modifier.padding(horizontal = 18.dp, vertical = 7.dp), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                    }
                }

                planeDialog?.let { mode ->
                    V25PlaneDialog(
                        mode = mode,
                        program = history.current,
                        selectedSurface = selectedSurface,
                        onCancel = { planeDialog = null },
                        onCreate = { parentId, offset, label ->
                            runCatching {
                                when (mode) {
                                    V24PlaneDialogMode.OFFSET -> history.current.createOffsetPlane(parentId, offset, label)
                                    V24PlaneDialogMode.FACE -> {
                                        val face = selectedSurface?.takeIf { it.planar } ?: error("Seleccione una cara plana antes de crear el plano")
                                        history.current.createFaceParallelPlane(
                                            CadFaceReference(
                                                objectId = "Cuerpo1",
                                                faceId = face.id,
                                                origin = CadVector3(face.point[0].toDouble(), face.point[1].toDouble(), face.point[2].toDouble()),
                                                normal = CadVector3(face.normal[0].toDouble(), face.normal[1].toDouble(), face.normal[2].toDouble())
                                            ),
                                            offset,
                                            label
                                        )
                                    }
                                }
                            }.onSuccess { next ->
                                planeDialog = null
                                commit(next, message = "Plano de referencia creado")
                            }.onFailure { failure -> error = friendlyFailure("Crear plano", failure) }
                        }
                    )
                }

                recoveryPrompt?.let { snapshot ->
                    AlertDialog(
                        onDismissRequest = {},
                        icon = { Icon(Icons.Default.Restore, null) },
                        title = { Text("Recuperar trabajo de taller", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Se encontró una sesión paramétrica guardada de forma automática.")
                                Text("${snapshot.program.documentName} · revisión ${snapshot.program.revision} · ${snapshot.source}", color = V24Secondary)
                                Text("La recuperación se reconstruirá con FreeCAD antes de reemplazar el modelo visible.")
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                recoveryPrompt = null
                                rebuild(history.reset(snapshot.program), true, "Sesión de taller recuperada")
                            }) { Text("Recuperar") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                recoveryPrompt = null
                                IndustrialCadRecoveryStore.clear(context)
                                hasRecovery = false
                                autosaveStatus = "Sin sesión recuperable"
                                rebuild(history.reset(BasicCadProgram()), true, "Pieza paramétrica creada")
                            }) { Text("Crear documento nuevo") }
                        }
                    )
                }

                if (workshopDialog) {
                    IndustrialWorkshopDialog(
                        gloveMode = gloveMode,
                        autosaveStatus = autosaveStatus,
                        hasRecovery = hasRecovery,
                        onGloveMode = { enabled ->
                            gloveMode = enabled
                            IndustrialWorkshopPreferences.setGloveMode(context, enabled)
                            status = if (enabled) "Modo guantes activado" else "Modo guantes desactivado"
                        },
                        onExportDiagnostics = {
                            val report = IndustrialCadDiagnostics.exportReport(context)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico SolidFreeCAD")
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(Intent.createChooser(share, "Compartir diagnóstico"))
                        },
                        onClearRecovery = {
                            IndustrialCadRecoveryStore.clear(context)
                            hasRecovery = false
                            autosaveStatus = "Sin sesión recuperable"
                            status = "Sesión recuperable borrada"
                        },
                        onClearDiagnostics = {
                            IndustrialCadDiagnostics.clear(context)
                            status = "Registro técnico limpiado"
                        },
                        onDismiss = { workshopDialog = false }
                    )
                }
            }
        }
    }
}
