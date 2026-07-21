package com.example.faceui

import android.content.Context
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.features.BasicCadFeature
import com.example.features.BasicCadFeatureFamily
import com.example.features.BasicCadHistory
import com.example.features.BasicCadMacroGenerator
import com.example.features.BasicCadOperation
import com.example.features.BasicCadProgram
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
import kotlin.math.abs

private val V22Text = Color(0xFF0F172A)
private val V22Secondary = Color(0xFF334155)
private val V22Muted = Color(0xFF475569)
private val V22Accent = Color(0xFF15557C)
private val V22Panel = Color(0xFFF8FAFC)
private val V22Header = Color(0xFFE4ECF2)
private val V22Divider = Color(0xFF94A3B8)
private val V22Selected = Color(0xFFCBE6F6)
private val V22Viewport = Color(0xFFE7ECEF)
private const val V22AnimMs = 230
private const val V22Preferences = "solidfreecad_workbench_v22"

private enum class V22RightMode { PROPERTIES, OPERATIONS }

private data class V22Document(
    val name: String,
    val format: String,
    val mesh: NativeSceneMesh,
    val summary: String,
    val program: BasicCadProgram?,
    val token: Long,
    val fit: Boolean
)

private data class V22ParameterValidation(
    val values: Map<String, Double> = emptyMap(),
    val error: String? = null
)

class SolidFreeCadWorkbenchActivityV22 : ComponentActivity() {
    private var cadSurface: FaceDrivenCadSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialUri = intent?.data
        setContent {
            MyApplicationTheme {
                V22Workbench(initialUri) { cadSurface = it }
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
private fun V22Workbench(
    initialUri: Uri?,
    onSurfaceReady: (FaceDrivenCadSurfaceView) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val compactScreen = LocalConfiguration.current.screenWidthDp < 850
    val preferences = remember {
        context.getSharedPreferences(V22Preferences, Context.MODE_PRIVATE)
    }

    var history by remember { mutableStateOf(BasicCadHistory(BasicCadProgram())) }
    var document by remember { mutableStateOf<V22Document?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(1L) }
    var selectedFace by remember { mutableStateOf(EditableCadFace.NONE) }
    var treeVisible by rememberSaveable {
        mutableStateOf(preferences.getBoolean("tree_visible", true))
    }
    var rightVisible by rememberSaveable {
        mutableStateOf(preferences.getBoolean("right_visible", true))
    }
    var rightMode by rememberSaveable {
        mutableStateOf(
            runCatching {
                V22RightMode.valueOf(
                    preferences.getString("right_mode", V22RightMode.PROPERTIES.name)
                        ?: V22RightMode.PROPERTIES.name
                )
            }.getOrDefault(V22RightMode.PROPERTIES)
        )
    }
    var surface by remember { mutableStateOf<FaceDrivenCadSurfaceView?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Preparando FreeCAD…") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(treeVisible, rightVisible, rightMode) {
        preferences.edit()
            .putBoolean("tree_visible", treeVisible)
            .putBoolean("right_visible", rightVisible)
            .putString("right_mode", rightMode.name)
            .apply()
    }

    fun rebuildCandidate(candidate: BasicCadHistory, fit: Boolean, message: String) {
        if (loading) return
        loading = true
        error = null
        selectedFace = EditableCadFace.NONE
        surface?.setSelectedFace(EditableCadFace.NONE)
        scope.launch {
            runCatching { v22BuildProgram(context, candidate.current, fit) }
                .onSuccess {
                    history = candidate
                    document = it
                    selectedId = candidate.current.features.lastOrNull()?.id
                    status = message
                }
                .onFailure {
                    error = v22Failure(it)
                    status = "No se pudo reconstruir el modelo"
                    surface?.restoreCommittedPreview()
                }
            loading = false
        }
    }

    fun commitProgram(next: BasicCadProgram, fit: Boolean, message: String) {
        if (next == history.current) {
            status = "No hay cambios para aplicar"
            return
        }
        rebuildCandidate(history.commit(next), fit, message)
    }

    fun createDocument() {
        rebuildCandidate(history.reset(BasicCadProgram()), true, "Pieza paramétrica creada")
    }

    fun openUri(uri: Uri) {
        if (loading) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        loading = true
        error = null
        status = "Abriendo documento…"
        selectedFace = EditableCadFace.NONE
        surface?.setSelectedFace(EditableCadFace.NONE)
        scope.launch {
            runCatching { v22LoadExternal(context, uri) }
                .onSuccess {
                    document = it
                    it.program?.let { loaded -> history = history.reset(loaded) }
                    selectedId = it.program?.features?.lastOrNull()?.id
                    status = "${it.format} cargado"
                }
                .onFailure {
                    error = v22Failure(it)
                    status = "Error al abrir el archivo"
                }
            loading = false
        }
    }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(::openUri) }

    LaunchedEffect(initialUri) {
        if (initialUri != null) openUri(initialUri) else createDocument()
    }

    val activeProgram = document?.program
    val selectedFeature = activeProgram?.features?.firstOrNull { it.id == selectedId }
    val directCylinder = activeProgram?.features?.size == 1 &&
        activeProgram.features.first().parameters.containsKey("diameter")
    val undoEnabled = !loading && activeProgram != null && history.canUndo
    val redoEnabled = !loading && activeProgram != null && history.canRedo

    Scaffold(
        topBar = {
            V22TopBar(
                documentName = document?.name ?: "Sin título",
                loading = loading,
                treeVisible = treeVisible,
                rightVisible = rightVisible,
                canUndo = undoEnabled,
                canRedo = redoEnabled,
                onNew = ::createDocument,
                onOpen = { openDocument.launch(arrayOf("*/*")) },
                onUndo = {
                    history.undo()?.let { rebuildCandidate(it, false, "Operación deshecha") }
                },
                onRedo = {
                    history.redo()?.let { rebuildCandidate(it, false, "Operación rehecha") }
                },
                onOperations = {
                    rightMode = V22RightMode.OPERATIONS
                    rightVisible = true
                    if (compactScreen) treeVisible = false
                },
                onProperties = {
                    rightMode = V22RightMode.PROPERTIES
                    rightVisible = true
                    if (compactScreen) treeVisible = false
                },
                onFit = { document?.mesh?.let { surface?.fit(it) } },
                onPreset = { preset -> surface?.setPreset(preset) },
                onToggleTree = { treeVisible = !treeVisible },
                onToggleRight = { rightVisible = !rightVisible }
            )
        },
        bottomBar = {
            V22StatusBar(
                status = status,
                loading = loading,
                format = document?.format ?: "—",
                featureCount = activeProgram?.features?.size
            )
        }
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(V22Viewport)
        ) {
            val compact = maxWidth < 850.dp
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = treeVisible,
                    enter = expandHorizontally(
                        expandFrom = Alignment.Start,
                        animationSpec = v22Tween()
                    ) + slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = v22Tween()
                    ) + fadeIn(tween(140)),
                    exit = shrinkHorizontally(
                        shrinkTowards = Alignment.Start,
                        animationSpec = v22Tween()
                    ) + slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = v22Tween()
                    ) + fadeOut(tween(130))
                ) {
                    Row {
                        V22Tree(
                            document = document,
                            selectedId = selectedId,
                            enabled = !loading,
                            onSelect = {
                                selectedId = it
                                rightMode = V22RightMode.PROPERTIES
                                if (!compact) rightVisible = true
                            },
                            onAdd = {
                                rightMode = V22RightMode.OPERATIONS
                                rightVisible = true
                                if (compact) treeVisible = false
                            },
                            onClose = { treeVisible = false },
                            modifier = Modifier.width(if (compact) 214.dp else 236.dp)
                        )
                        VerticalDivider(color = V22Divider)
                    }
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AndroidView(
                        factory = { androidContext ->
                            FaceDrivenCadSurfaceView(androidContext).also { view ->
                                surface = view
                                onSurfaceReady(view)
                            }
                        },
                        update = { view ->
                            view.onFaceSelected = { face ->
                                if (!loading && directCylinder) {
                                    selectedFace = face
                                    view.setSelectedFace(face)
                                    status = when (face) {
                                        EditableCadFace.TOP -> "Cara superior: arrastre para cambiar profundidad"
                                        EditableCadFace.SIDE -> "Cara curva: arrastre para cambiar diámetro"
                                        EditableCadFace.NONE -> "Selección cancelada"
                                    }
                                } else if (!loading) {
                                    selectedFace = EditableCadFace.NONE
                                    view.setSelectedFace(EditableCadFace.NONE)
                                    status = "Edite la operación desde el árbol"
                                }
                            }
                            view.onParameterPreview = { face, value ->
                                if (!loading) {
                                    status = when (face) {
                                        EditableCadFace.TOP -> "Profundidad: ${v22Mm(value.toDouble())}"
                                        EditableCadFace.SIDE -> "Diámetro: ${v22Mm(value.toDouble())}"
                                        EditableCadFace.NONE -> status
                                    }
                                }
                            }
                            view.onParameterCommit = { face, value ->
                                if (!loading && directCylinder) {
                                    val first = history.current.features.first()
                                    val diameter = if (face == EditableCadFace.SIDE) {
                                        value.toDouble()
                                    } else {
                                        first.parameters["diameter"] ?: 34.93
                                    }
                                    val depth = if (face == EditableCadFace.TOP) {
                                        value.toDouble()
                                    } else {
                                        first.parameters["depth"] ?: 40.0
                                    }
                                    commitProgram(
                                        history.current.updateBaseCylinder(diameter, depth),
                                        false,
                                        "Saliente-Extruir1 actualizado"
                                    )
                                }
                            }

                            document?.let { active ->
                                if (view.tag != active.token) {
                                    view.tag = active.token
                                    view.setMesh(active.mesh, active.fit)
                                }
                            }
                            val first = activeProgram?.features?.firstOrNull()
                            view.setDimensions(
                                (first?.parameters?.get("depth") ?: 40.0).toFloat(),
                                (first?.parameters?.get("diameter") ?: 34.93).toFloat()
                            )
                            view.setSelectedFace(
                                if (directCylinder && !loading) selectedFace
                                else EditableCadFace.NONE
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    AnimatedVisibility(
                        visible = !treeVisible,
                        modifier = Modifier.align(Alignment.CenterStart),
                        enter = fadeIn(tween(150)) + slideInHorizontally(initialOffsetX = { -it }),
                        exit = fadeOut(tween(100)) + slideOutHorizontally(targetOffsetX = { -it })
                    ) {
                        V22Handle("Árbol", "▶", { treeVisible = true }, right = false)
                    }
                    AnimatedVisibility(
                        visible = !rightVisible,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        enter = fadeIn(tween(150)) + slideInHorizontally(initialOffsetX = { it }),
                        exit = fadeOut(tween(100)) + slideOutHorizontally(targetOffsetX = { it })
                    ) {
                        V22Handle("Panel", "◀", { rightVisible = true }, right = true)
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(10.dp),
                        color = Color.White.copy(alpha = 0.93f),
                        contentColor = V22Secondary,
                        shape = RoundedCornerShape(7.dp),
                        shadowElevation = 2.dp
                    ) {
                        Text(
                            text = if (directCylinder) {
                                "Toque una cara y arrastre la flecha amarilla"
                            } else {
                                "Órbita: 1 dedo · paneo: 2 dedos · zoom: pellizco"
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = V22Secondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    AnimatedVisibility(
                        visible = loading,
                        modifier = Modifier.align(Alignment.TopCenter),
                        enter = fadeIn(tween(120)),
                        exit = fadeOut(tween(120))
                    ) {
                        Surface(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .widthIn(min = 230.dp, max = 360.dp),
                            color = Color.White,
                            contentColor = V22Text,
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 6.dp
                        ) {
                            Column {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "FreeCAD está recalculando el BRep",
                                        color = V22Text,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    error?.let { message ->
                        V22ErrorCard(
                            message = message,
                            onClose = { error = null },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = rightVisible,
                    enter = expandHorizontally(
                        expandFrom = Alignment.End,
                        animationSpec = v22Tween()
                    ) + slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = v22Tween()
                    ) + fadeIn(tween(140)),
                    exit = shrinkHorizontally(
                        shrinkTowards = Alignment.End,
                        animationSpec = v22Tween()
                    ) + slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = v22Tween()
                    ) + fadeOut(tween(130))
                ) {
                    Row {
                        VerticalDivider(color = V22Divider)
                        V22RightPanel(
                            mode = rightMode,
                            feature = selectedFeature,
                            document = document,
                            enabled = !loading,
                            onMode = { rightMode = it },
                            onClose = { rightVisible = false },
                            onOperation = { operation ->
                                rightMode = V22RightMode.PROPERTIES
                                commitProgram(
                                    history.current.append(operation),
                                    false,
                                    "${operation.label} añadido"
                                )
                            },
                            onApply = { values ->
                                selectedFeature?.let { feature ->
                                    commitProgram(
                                        history.current.updateFeature(feature.id, values),
                                        false,
                                        "${feature.label} recalculado"
                                    )
                                }
                            },
                            onSuppress = {
                                selectedFeature?.let { feature ->
                                    commitProgram(
                                        history.current.toggleSuppressed(feature.id),
                                        false,
                                        "Estado de ${feature.label} actualizado"
                                    )
                                }
                            },
                            onDelete = {
                                selectedFeature?.let { feature ->
                                    if (feature.id == history.current.features.firstOrNull()?.id) {
                                        error = "La operación base no se puede eliminar; cree una pieza nueva"
                                    } else {
                                        commitProgram(
                                            history.current.remove(feature.id),
                                            false,
                                            "${feature.label} eliminado"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.width(if (compact) 264.dp else 292.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun V22TopBar(
    documentName: String,
    loading: Boolean,
    treeVisible: Boolean,
    rightVisible: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onOperations: () -> Unit,
    onProperties: () -> Unit,
    onFit: () -> Unit,
    onPreset: (CameraPreset) -> Unit,
    onToggleTree: () -> Unit,
    onToggleRight: () -> Unit
) {
    Surface(color = Color(0xFFF1F4F6), contentColor = V22Text, shadowElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = Color(0xFFD32F2F), shape = RoundedCornerShape(5.dp)) {
                Text(
                    "SF",
                    Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.widthIn(max = 155.dp)) {
                Text(
                    "SolidFreeCAD",
                    color = V22Text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    documentName,
                    color = V22Secondary,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(5.dp))
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                V22ToolbarAction(Icons.Default.NoteAdd, "Nuevo", !loading, onNew)
                V22ToolbarAction(Icons.Default.FolderOpen, "Abrir", !loading, onOpen)
                V22ToolbarAction(Icons.Default.Undo, "Deshacer", canUndo, onUndo)
                V22ToolbarAction(Icons.Default.Redo, "Rehacer", canRedo, onRedo)
                V22ToolbarAction(Icons.Default.Layers, "Operaciones", !loading, onOperations)
                V22ToolbarAction(Icons.Default.Tune, "Propiedades", !loading, onProperties)
                V22ToolbarAction(Icons.Default.FilterCenterFocus, "Encuadrar", true, onFit)
                V22TextAction("ISO", true) { onPreset(CameraPreset.ISOMETRIC) }
                V22TextAction("Frente", true) { onPreset(CameraPreset.FRONT) }
                V22TextAction("Planta", true) { onPreset(CameraPreset.TOP) }
            }
            IconButton(
                onClick = onToggleTree,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (treeVisible) Icons.Default.KeyboardArrowLeft
                    else Icons.Default.KeyboardArrowRight,
                    if (treeVisible) "Ocultar árbol" else "Mostrar árbol",
                    tint = V22Accent
                )
            }
            IconButton(
                onClick = onToggleRight,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (rightVisible) Icons.Default.KeyboardArrowRight
                    else Icons.Default.KeyboardArrowLeft,
                    if (rightVisible) "Ocultar panel derecho" else "Mostrar panel derecho",
                    tint = V22Accent
                )
            }
        }
    }
}

@Composable
private fun V22ToolbarAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            icon,
            label,
            modifier = Modifier.size(19.dp),
            tint = if (enabled) V22Accent else Color(0xFF94A3B8)
        )
        Text(
            label,
            color = if (enabled) V22Text else Color(0xFF64748B),
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun V22TextAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .defaultMinSize(minWidth = 48.dp, minHeight = 42.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = Color(0xFFE4EDF3),
        contentColor = V22Accent,
        shape = RoundedCornerShape(5.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 7.dp),
                color = V22Accent,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun V22StatusBar(
    status: String,
    loading: Boolean,
    format: String,
    featureCount: Int?
) {
    Surface(color = Color(0xFFE5ECF1), contentColor = V22Text, shadowElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                status,
                Modifier.weight(1f),
                color = V22Text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (loading) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(7.dp))
            }
            featureCount?.let {
                Text(
                    "$it operaciones",
                    color = V22Secondary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(format, color = V22Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun V22Tree(
    document: V22Document?,
    selectedId: Long?,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    Surface(modifier.fillMaxHeight(), color = V22Panel, contentColor = V22Text) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(V22Header)
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "FeatureManager",
                        color = V22Text,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Historial de operaciones",
                        color = V22Secondary,
                        fontSize = 7.sp
                    )
                }
                IconButton(
                    onClick = onAdd,
                    enabled = enabled,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        "Añadir operación",
                        tint = if (enabled) V22Accent else V22Divider
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.KeyboardArrowLeft, "Ocultar árbol", tint = V22Accent)
                }
            }
            HorizontalDivider(color = V22Divider)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Text(
                    document?.name ?: "Sin documento",
                    color = V22Text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "  ▾ Cuerpo1",
                    color = V22Text,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text("      ▸ Origen", color = V22Muted, fontSize = 8.sp)
                val features = document?.program?.features.orEmpty()
                if (features.isEmpty()) {
                    Text(
                        "      ${document?.format ?: "Geometría importada"}",
                        color = V22Secondary,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(top = 7.dp)
                    )
                }
                features.forEachIndexed { index, feature ->
                    val selected = feature.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) V22Selected else Color.Transparent,
                                RoundedCornerShape(5.dp)
                            )
                            .clickable(enabled = enabled) { onSelect(feature.id) }
                            .defaultMinSize(minHeight = 42.dp)
                            .padding(horizontal = 6.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (feature.suppressed) "○" else "◆",
                            color = if (feature.suppressed) V22Muted else V22Accent,
                            fontSize = 9.sp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            feature.label,
                            color = if (feature.suppressed) V22Muted else V22Text,
                            fontSize = 9.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index == 0) {
                        Text(
                            "          (-) Croquis1",
                            color = V22Secondary,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun V22RightPanel(
    mode: V22RightMode,
    feature: BasicCadFeature?,
    document: V22Document?,
    enabled: Boolean,
    onMode: (V22RightMode) -> Unit,
    onClose: () -> Unit,
    onOperation: (BasicCadOperation) -> Unit,
    onApply: (Map<String, Double>) -> Unit,
    onSuppress: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier.fillMaxHeight(),
        color = V22Panel,
        contentColor = V22Text,
        shadowElevation = 3.dp
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(V22Header)
                    .padding(horizontal = 5.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                V22Tab("Propiedades", mode == V22RightMode.PROPERTIES) {
                    onMode(V22RightMode.PROPERTIES)
                }
                V22Tab("Operaciones", mode == V22RightMode.OPERATIONS) {
                    onMode(V22RightMode.OPERATIONS)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.KeyboardArrowRight, "Ocultar panel", tint = V22Accent)
                }
            }
            HorizontalDivider(color = V22Divider)
            if (mode == V22RightMode.OPERATIONS) {
                V22Operations(onOperation, enabled, Modifier.fillMaxSize())
            } else {
                V22Properties(
                    feature = feature,
                    document = document,
                    enabled = enabled,
                    onApply = onApply,
                    onSuppress = onSuppress,
                    onDelete = onDelete,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun V22Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = 42.dp)
            .clickable(onClick = onClick),
        color = if (selected) Color.White else Color.Transparent,
        contentColor = if (selected) V22Accent else V22Secondary,
        shape = RoundedCornerShape(6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                color = if (selected) V22Accent else V22Secondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun V22Properties(
    feature: BasicCadFeature?,
    document: V22Document?,
    enabled: Boolean,
    onApply: (Map<String, Double>) -> Unit,
    onSuppress: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier
) {
    if (feature == null) {
        Column(
            modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "PROPIEDADES DEL DOCUMENTO",
                color = V22Text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                document?.summary ?: "Seleccione una operación en el árbol",
                color = V22Secondary,
                fontSize = 8.sp,
                lineHeight = 12.sp
            )
        }
        return
    }

    val focusManager = LocalFocusManager.current
    var draft by remember(feature.id, feature.parameters) {
        mutableStateOf(feature.parameters.mapValues { v22Number(it.value) })
    }
    val validation = remember(draft) { v22ValidateParameters(draft) }
    val changed = remember(validation.values, feature.parameters) {
        validation.error == null && !v22MapsEqual(validation.values, feature.parameters)
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(feature.label, color = V22Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(feature.operation.description, color = V22Secondary, fontSize = 9.sp)
        Text(
            if (feature.operation.generalTopology) "OPERACIÓN PARAMÉTRICA"
            else "OPERACIÓN BÁSICA BETA",
            color = if (feature.operation.generalTopology) Color(0xFF176B31)
            else Color(0xFF9A4A04),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(color = V22Divider)

        draft.forEach { (key, value) ->
            OutlinedTextField(
                value = value,
                onValueChange = { next -> draft = draft + (key to v22Numeric(next)) },
                enabled = enabled,
                label = { Text(key, color = V22Secondary) },
                textStyle = LocalTextStyle.current.copy(
                    color = V22Text,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                isError = validation.error?.contains(key, ignoreCase = true) == true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        validation.error?.let { message ->
            Text(
                message,
                color = Color(0xFFB42318),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (changed) {
            Text(
                "Cambios sin aplicar",
                color = Color(0xFF8A4B08),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                onApply(validation.values)
            },
            enabled = enabled && changed && validation.error == null,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
        ) {
            Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Aplicar y recalcular", fontWeight = FontWeight.Bold)
        }
        TextButton(
            onClick = onSuppress,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp)
        ) {
            Icon(
                if (feature.suppressed) Icons.Default.Visibility
                else Icons.Default.VisibilityOff,
                null,
                tint = V22Accent
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (feature.suppressed) "Activar operación" else "Suprimir operación",
                color = V22Accent,
                fontWeight = FontWeight.Bold
            )
        }
        TextButton(
            onClick = onDelete,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 46.dp)
        ) {
            Icon(Icons.Default.Delete, null, tint = Color(0xFFB42318))
            Spacer(Modifier.width(6.dp))
            Text("Eliminar operación", color = Color(0xFFB42318), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun V22Operations(
    onSelect: (BasicCadOperation) -> Unit,
    enabled: Boolean,
    modifier: Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalized = query.trim().lowercase(Locale.ROOT)
    val visibleOperations = remember(normalized) {
        BasicCadOperation.entries.filter { operation ->
            normalized.isBlank() ||
                operation.label.lowercase(Locale.ROOT).contains(normalized) ||
                operation.description.lowercase(Locale.ROOT).contains(normalized) ||
                operation.family.label.lowercase(Locale.ROOT).contains(normalized)
        }
    }

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            enabled = enabled,
            leadingIcon = { Icon(Icons.Default.Search, null, tint = V22Accent) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, "Limpiar búsqueda", tint = V22Secondary)
                    }
                }
            },
            label = { Text("Buscar operación", color = V22Secondary) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(9.dp)
        )
        HorizontalDivider(color = V22Divider)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (visibleOperations.isEmpty()) {
                Text(
                    "No hay operaciones que coincidan con “$query”",
                    color = V22Secondary,
                    fontSize = 9.sp
                )
            }
            BasicCadFeatureFamily.entries.forEach { family ->
                val familyOperations = visibleOperations.filter { it.family == family }
                if (familyOperations.isNotEmpty()) {
                    Text(
                        family.label.uppercase(Locale.ROOT),
                        color = V22Secondary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    familyOperations.forEach { operation ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 58.dp)
                                .clickable(enabled = enabled) { onSelect(operation) },
                            color = Color(0xFFEDF3F7),
                            contentColor = V22Text,
                            shape = RoundedCornerShape(7.dp),
                            shadowElevation = 1.dp
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        operation.label,
                                        Modifier.weight(1f),
                                        color = V22Text,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!operation.generalTopology) {
                                        Text(
                                            "BETA",
                                            color = Color(0xFF9A4A04),
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    operation.description,
                                    color = V22Secondary,
                                    fontSize = 8.sp,
                                    lineHeight = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V22Handle(
    label: String,
    arrow: String,
    onClick: () -> Unit,
    right: Boolean
) {
    Surface(
        modifier = Modifier
            .defaultMinSize(minWidth = 42.dp, minHeight = 76.dp)
            .clickable(onClick = onClick),
        color = V22Accent,
        contentColor = Color.White,
        shape = if (right) {
            RoundedCornerShape(topStart = 9.dp, bottomStart = 9.dp)
        } else {
            RoundedCornerShape(topEnd = 9.dp, bottomEnd = 9.dp)
        },
        shadowElevation = 5.dp
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(arrow, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun V22ErrorCard(
    message: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(max = 470.dp),
        color = Color(0xFFFFEBEE),
        contentColor = Color(0xFF5F1414),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Error, null, tint = Color(0xFFC62828))
            Spacer(Modifier.width(7.dp))
            Text(
                message,
                Modifier.weight(1f),
                color = Color(0xFF5F1414),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = onClose, modifier = Modifier.size(42.dp)) {
                Icon(Icons.Default.Cancel, "Cerrar error", tint = Color(0xFF7F1D1D))
            }
        }
    }
}

private fun <T> v22Tween() = tween<T>(V22AnimMs, easing = FastOutSlowInEasing)

private fun v22ValidateParameters(draft: Map<String, String>): V22ParameterValidation {
    val parsed = linkedMapOf<String, Double>()
    for ((key, text) in draft) {
        val value = text.toDoubleOrNull()
            ?: return V22ParameterValidation(error = "$key debe contener un número válido")
        if (!value.isFinite()) {
            return V22ParameterValidation(error = "$key debe ser un número finito")
        }
        when (key) {
            "dx", "dy", "dz" -> Unit
            "count" -> {
                if (value < 2.0 || value % 1.0 != 0.0) {
                    return V22ParameterValidation(error = "count debe ser un entero mayor o igual que 2")
                }
            }
            else -> if (value <= 0.0) {
                return V22ParameterValidation(error = "$key debe ser mayor que cero")
            }
        }
        parsed[key] = value
    }
    return V22ParameterValidation(values = parsed)
}

private fun v22MapsEqual(left: Map<String, Double>, right: Map<String, Double>): Boolean {
    if (left.keys != right.keys) return false
    return left.all { (key, value) ->
        abs(value - (right[key] ?: return@all false)) <= 1.0e-9
    }
}

private suspend fun v22BuildProgram(
    context: Context,
    program: BasicCadProgram,
    fit: Boolean
): V22Document = withContext(Dispatchers.IO) {
    val scene = SolidFreeCadMacroRuntime.execute(
        context,
        BasicCadMacroGenerator.generate(program),
        0.18,
        0.24
    )
    V22Document(
        name = "${program.documentName}.FCStd",
        format = "Modelo paramétrico",
        mesh = scene.mesh.v22Native(),
        summary = buildString {
            appendLine("FreeCAD Base 1.1.1 / Runtime 0.11")
            appendLine(scene.pythonVersion)
            append(scene.documentSummary)
            if (scene.output.isNotBlank()) {
                appendLine()
                append(scene.output)
            }
        },
        program = program,
        token = System.nanoTime(),
        fit = fit
    )
}

private suspend fun v22LoadExternal(context: Context, uri: Uri): V22Document =
    withContext(Dispatchers.IO) {
        val fallback = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.contains('.') }
            ?: "document.FCStd"
        val (file, name) = AndroidDocumentLoader.stage(
            context,
            uri,
            "solidfreecad-workbench-v22",
            fallback
        )
        val extension = v22Extension(context, uri, file, name)
        when (extension) {
            "step", "stp" -> NativeStepBridge.importStep(file.absolutePath, name).let {
                V22Document(name, "STEP", it.mesh.v22Native(), it.summary, null, System.nanoTime(), true)
            }
            "fcstd" -> {
                val archive = FreeCadArchiveReader.extract(
                    file,
                    File(context.cacheDir, "solidfreecad-fcstd-v22"),
                    name
                )
                NativeFreeCadFileBridge.importFcStdObjects(
                    archive.objects,
                    name,
                    archive.summary
                ).let {
                    V22Document(name, "FCStd", it.mesh.v22Native(), it.summary, null, System.nanoTime(), true)
                }
            }
            "fcmacro", "py" -> SolidFreeCadMacroRuntime.executeFile(
                context,
                file.readBytes(),
                0.18,
                0.24
            ).let {
                V22Document(
                    name,
                    "FCMacro",
                    it.mesh.v22Native(),
                    buildString {
                        appendLine("Macro FreeCAD ejecutada")
                        appendLine(it.pythonVersion)
                        appendLine(it.documentSummary)
                        if (it.output.isNotBlank()) append(it.output)
                    },
                    null,
                    System.nanoTime(),
                    true
                )
            }
            else -> error("Formato no compatible: .$extension. Use STEP, FCStd o FCMacro")
        }
    }

private fun v22Extension(context: Context, uri: Uri, file: File, name: String): String {
    val extension = AndroidDocumentLoader.extension(name)
    if (extension in setOf("step", "stp", "fcstd", "fcmacro", "py")) return extension
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
    if ("python" in mime || "x-fcmacro" in mime) return "fcmacro"
    val prefix = file.inputStream().buffered().use { input ->
        val bytes = ByteArray(4096)
        val count = input.read(bytes)
        if (count > 0) bytes.copyOf(count).toString(Charsets.UTF_8) else ""
    }
    return if (
        "import FreeCAD" in prefix ||
        "import Part" in prefix ||
        "App.newDocument" in prefix
    ) "fcmacro" else extension
}

private fun SceneMesh.v22Native() = NativeSceneMesh(
    vertices = vertices,
    indices = indices,
    minX = minX,
    minY = minY,
    minZ = minZ,
    maxX = maxX,
    maxY = maxY,
    maxZ = maxZ
)

private fun v22Numeric(value: String) = value.filterIndexed { index, character ->
    character.isDigit() ||
        character == '.' ||
        character == ',' ||
        (character == '-' && index == 0)
}.replace(',', '.')

private fun v22Failure(error: Throwable) =
    generateSequence(error) { it.cause }.last().message
        ?: error.message
        ?: error::class.java.simpleName

private fun v22Mm(value: Double) = "%.2f mm".format(Locale.US, value)
private fun v22Number(value: Double) = "%.4f".format(Locale.US, value).trimEnd('0').trimEnd('.')
