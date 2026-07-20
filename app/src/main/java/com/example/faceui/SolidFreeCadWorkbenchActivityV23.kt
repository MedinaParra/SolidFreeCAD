package com.example.faceui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.features.requiresSketch
import com.example.ui.theme.MyApplicationTheme

class SolidFreeCadWorkbenchActivityV23 : ComponentActivity() {
    private var cadSurface: FaceDrivenCadSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialUri = intent?.data
        setContent {
            MyApplicationTheme {
                V23Workbench(initialUri) { cadSurface = it }
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

@Composable
private fun V23Workbench(initialUri: Uri?, onSurfaceReady: (FaceDrivenCadSurfaceView) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context, scope) { V23WorkbenchController(context, scope) }
    val compactScreen = LocalConfiguration.current.screenWidthDp < 850
    val preferences = remember { context.getSharedPreferences(V23Preferences, Context.MODE_PRIVATE) }

    var treeVisible by rememberSaveable { mutableStateOf(preferences.getBoolean("tree_visible", true)) }
    var rightVisible by rememberSaveable { mutableStateOf(preferences.getBoolean("right_visible", true)) }
    var dockVisible by rememberSaveable { mutableStateOf(preferences.getBoolean("dock_visible", true)) }
    var dockCategory by rememberSaveable {
        mutableStateOf(runCatching {
            V23DockCategory.valueOf(preferences.getString("dock_category", V23DockCategory.SKETCH.name) ?: V23DockCategory.SKETCH.name)
        }.getOrDefault(V23DockCategory.SKETCH))
    }
    var planeDialog by remember { mutableStateOf<V23PlaneDialogState?>(null) }
    var sketchDialogVisible by remember { mutableStateOf(false) }
    var editingSketchId by remember { mutableStateOf<Long?>(null) }
    var pendingEditSketchId by remember { mutableStateOf<Long?>(null) }
    var planarPickMode by remember { mutableStateOf(false) }

    LaunchedEffect(treeVisible, rightVisible, dockVisible, dockCategory) {
        preferences.edit().putBoolean("tree_visible", treeVisible).putBoolean("right_visible", rightVisible)
            .putBoolean("dock_visible", dockVisible).putString("dock_category", dockCategory.name).apply()
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(controller::open) }
    LaunchedEffect(initialUri) { if (initialUri != null) controller.open(initialUri) else controller.newDocument() }
    LaunchedEffect(controller.loading, pendingEditSketchId, controller.program?.revision) {
        val pending = pendingEditSketchId
        if (!controller.loading && pending != null && controller.program?.sketches?.any { it.id == pending } == true) {
            editingSketchId = pending
            pendingEditSketchId = null
        }
    }

    val program = controller.program
    val selectedFeature = program?.features?.firstOrNull { controller.selectionType == V23SelectionType.FEATURE && it.id == controller.selectionId }
    val selectedPlane = program?.planes?.firstOrNull { controller.selectionType == V23SelectionType.PLANE && it.id == controller.selectionId }
    val selectedSketch = program?.sketches?.firstOrNull { controller.selectionType == V23SelectionType.SKETCH && it.id == controller.selectionId }
    val selectedPlaneId = selectedPlane?.id ?: selectedFeature?.planeId ?: selectedSketch?.planeId ?: 1L
    val preferredSketch = selectedSketch ?: selectedFeature?.sketchId?.let { id -> program?.sketches?.firstOrNull { it.id == id } } ?: program?.sketches?.lastOrNull()

    fun commitSafely(block: () -> Unit) {
        runCatching(block).onFailure { controller.reportError(v23Failure(it)) }
    }

    Scaffold(
        topBar = {
            V23TopBar(
                documentName = controller.document?.name ?: "Sin título",
                loading = controller.loading,
                treeVisible = treeVisible,
                rightVisible = rightVisible,
                dockVisible = dockVisible,
                canUndo = !controller.loading && program != null && controller.history.canUndo,
                canRedo = !controller.loading && program != null && controller.history.canRedo,
                onNew = controller::newDocument,
                onOpen = { openDocument.launch(arrayOf("*/*")) },
                onUndo = controller::undo,
                onRedo = controller::redo,
                onFit = { controller.document?.mesh?.let { controller.surface?.fit(it) } },
                onPreset = { controller.surface?.setPreset(it) },
                onToggleTree = { treeVisible = !treeVisible },
                onToggleRight = { rightVisible = !rightVisible },
                onToggleDock = { dockVisible = !dockVisible },
                onSketch = { dockCategory = V23DockCategory.SKETCH; dockVisible = true }
            )
        },
        bottomBar = {
            V23StatusBar(controller.status, controller.loading, controller.document?.format ?: "—", program?.features?.size, program?.planes?.size)
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).background(V23Viewport)) {
            val compact = maxWidth < 850.dp
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = treeVisible,
                    enter = expandHorizontally(expandFrom = Alignment.Start, animationSpec = v23Tween()) + slideInHorizontally(initialOffsetX = { -it }, animationSpec = v23Tween()) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = v23Tween()) + slideOutHorizontally(targetOffsetX = { -it }, animationSpec = v23Tween()) + fadeOut()
                ) {
                    Row {
                        V23Tree(
                            document = controller.document,
                            selectionType = controller.selectionType,
                            selectedId = controller.selectionId,
                            enabled = !controller.loading,
                            onSelect = { type, id -> controller.select(type, id); if (!compact) rightVisible = true },
                            onShowDock = { category -> dockCategory = category; dockVisible = true },
                            onClose = { treeVisible = false },
                            modifier = Modifier.width(if (compact) 214.dp else 236.dp)
                        )
                        VerticalDivider(color = V23Divider)
                    }
                }

                V23Viewport(
                    controller = controller,
                    program = program,
                    treeVisible = treeVisible,
                    rightVisible = rightVisible,
                    dockVisible = dockVisible,
                    planarPickMode = planarPickMode,
                    selectedPlaneId = selectedPlane?.id,
                    onSurfaceReady = onSurfaceReady,
                    onShowTree = { treeVisible = true },
                    onShowRight = { rightVisible = true },
                    onShowDock = { dockVisible = true },
                    onPlanarFacePicked = { pick ->
                        planarPickMode = false
                        if (pick == null) controller.reportError("No se detectó una cara plana")
                        else planeDialog = V23PlaneDialogState(V23PlaneDialogMode.FACE_PARALLEL, face = pick.toReference())
                    }
                )

                AnimatedVisibility(
                    visible = rightVisible,
                    enter = expandHorizontally(expandFrom = Alignment.End, animationSpec = v23Tween()) + slideInHorizontally(initialOffsetX = { it }, animationSpec = v23Tween()) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = v23Tween()) + slideOutHorizontally(targetOffsetX = { it }, animationSpec = v23Tween()) + fadeOut()
                ) {
                    Row {
                        VerticalDivider(color = V23Divider)
                        V23PropertiesPanel(
                            selectionType = controller.selectionType,
                            feature = selectedFeature,
                            plane = selectedPlane,
                            sketch = selectedSketch,
                            document = controller.document,
                            program = program,
                            enabled = !controller.loading && program != null,
                            onClose = { rightVisible = false },
                            onApplyFeature = { values -> selectedFeature?.let { feature -> program?.let { active -> controller.commit(active.updateFeature(feature.id, values), false, "${feature.label} recalculado", V23SelectionType.FEATURE, feature.id) } } },
                            onSuppressFeature = { selectedFeature?.let { feature -> program?.let { active -> controller.commit(active.toggleSuppressed(feature.id), false, "Estado de ${feature.label} actualizado", V23SelectionType.FEATURE, feature.id) } } },
                            onDeleteFeature = { selectedFeature?.let { feature -> program?.let { active -> if (feature.id == active.features.firstOrNull()?.id) controller.reportError("La operación base no se puede eliminar") else controller.commit(active.remove(feature.id), false, "${feature.label} eliminado") } } },
                            onEditLinkedSketch = { editingSketchId = it },
                            onApplyPlane = { offset, visible -> selectedPlane?.let { plane -> program?.let { active -> commitSafely { controller.commit(active.updatePlane(plane.id, offset, visible), false, "${plane.label} actualizado", V23SelectionType.PLANE, plane.id) } } } },
                            onDeletePlane = { selectedPlane?.let { plane -> program?.let { active -> commitSafely { controller.commit(active.removePlane(plane.id), false, "${plane.label} eliminado") } } } },
                            onEditSketch = { editingSketchId = it },
                            onToggleSketch = { selectedSketch?.let { sketch -> program?.let { active -> controller.commit(active.toggleSketchVisibility(sketch.id), false, "Visibilidad de ${sketch.label} actualizada", V23SelectionType.SKETCH, sketch.id) } } },
                            onDeleteSketch = { selectedSketch?.let { sketch -> program?.let { active -> commitSafely { controller.commit(active.removeSketch(sketch.id), false, "${sketch.label} eliminado") } } } },
                            modifier = Modifier.width(if (compact) 264.dp else 292.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = dockVisible && program != null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = v23Tween()) + slideInVertically(initialOffsetY = { it }, animationSpec = v23Tween()) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = v23Tween()) + slideOutVertically(targetOffsetY = { it }, animationSpec = v23Tween()) + fadeOut()
            ) {
                program?.let { active ->
                    V23BottomDock(
                        category = dockCategory,
                        enabled = !controller.loading,
                        program = active,
                        selectedSketchId = preferredSketch?.id,
                        onCategory = { dockCategory = it },
                        onClose = { dockVisible = false },
                        onCreateOffsetPlane = { planeDialog = V23PlaneDialogState(V23PlaneDialogMode.OFFSET, parentPlaneId = selectedPlaneId) },
                        onCreateFacePlane = { planarPickMode = true; dockVisible = false; controller.setStatus("Toque una cara plana para crear un plano paralelo") },
                        onToggleBasePlane = { planeId -> controller.commit(active.togglePlaneVisibility(planeId), false, "Visibilidad de plano actualizada", V23SelectionType.PLANE, planeId) },
                        onCreateSketch = { sketchDialogVisible = true },
                        onEditSketch = { val sketch = preferredSketch; if (sketch == null) sketchDialogVisible = true else editingSketchId = sketch.id },
                        onOperation = { operation ->
                            val sketchId = if (operation.requiresSketch()) preferredSketch?.id else null
                            if (operation.requiresSketch() && sketchId == null) {
                                dockCategory = V23DockCategory.SKETCH
                                sketchDialogVisible = true
                                controller.setStatus("Cree o seleccione un croquis antes de ${operation.label}")
                            } else commitSafely {
                                val next = active.append(operation, sketchId)
                                val featureId = next.features.last().id
                                controller.commit(next, false, "${operation.label} añadido", V23SelectionType.FEATURE, featureId)
                                dockVisible = false
                                rightVisible = true
                            }
                        }
                    )
                }
            }

            planeDialog?.let { state ->
                V23PlaneDialog(state, program, onCancel = { planeDialog = null }) { offset, label ->
                    val active = program ?: return@V23PlaneDialog
                    commitSafely {
                        val next = when (state.mode) {
                            V23PlaneDialogMode.OFFSET -> active.addOffsetPlane(state.parentPlaneId ?: 1L, offset, label)
                            V23PlaneDialogMode.FACE_PARALLEL -> active.addFaceParallelPlane(state.face ?: error("Cara no disponible"), offset, label)
                        }
                        val planeId = next.planes.last().id
                        controller.commit(next, false, "${next.planes.last().label} creado", V23SelectionType.PLANE, planeId)
                        planeDialog = null
                        rightVisible = true
                    }
                }
            }

            if (sketchDialogVisible && program != null) {
                V23SketchCreateDialog(program, selectedPlaneId, onCancel = { sketchDialogVisible = false }) { planeId, profile, parameters, label ->
                    commitSafely {
                        val next = program.addSketch(planeId, profile, parameters, label)
                        val sketchId = next.sketches.last().id
                        pendingEditSketchId = sketchId
                        controller.commit(next, false, "${next.sketches.last().label} creado", V23SelectionType.SKETCH, sketchId)
                        sketchDialogVisible = false
                        dockVisible = false
                        rightVisible = true
                    }
                }
            }

            editingSketchId?.let { sketchId ->
                val sketch = program?.sketches?.firstOrNull { it.id == sketchId }
                val plane = sketch?.let { item -> program.planes.firstOrNull { it.id == item.planeId } }
                if (sketch != null && plane != null) {
                    V23SketchEditor(
                        sketch = sketch,
                        plane = plane,
                        onCancel = { editingSketchId = null },
                        onApply = { values -> controller.commit(program.updateSketch(sketch.id, values, sketch.visible), false, "${sketch.label} actualizado", V23SelectionType.SKETCH, sketch.id); editingSketchId = null },
                        onError = controller::reportError
                    )
                } else editingSketchId = null
            }
        }
    }
}
