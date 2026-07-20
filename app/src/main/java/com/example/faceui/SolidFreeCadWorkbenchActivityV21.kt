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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.features.BasicCadFeature
import com.example.features.BasicCadFeatureFamily
import com.example.features.BasicCadMacroGenerator
import com.example.features.BasicCadOperation
import com.example.features.BasicCadProgram
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

private val V21Text = Color(0xFF111827)
private val V21Secondary = Color(0xFF334155)
private val V21Muted = Color(0xFF475569)
private val V21Accent = Color(0xFF174E73)
private val V21Panel = Color(0xFFF8FAFC)
private val V21Header = Color(0xFFE7EEF3)
private val V21Divider = Color(0xFF94A3B8)
private val V21Selected = Color(0xFFCDE7F7)
private const val V21AnimMs = 260

private enum class V21RightMode { PROPERTIES, OPERATIONS }

private data class V21Document(
    val name: String,
    val format: String,
    val mesh: NativeSceneMesh,
    val summary: String,
    val program: BasicCadProgram?,
    val token: Long,
    val fit: Boolean
)

class SolidFreeCadWorkbenchActivityV21 : ComponentActivity() {
    private var cadSurface: FaceDrivenCadSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialUri = intent?.data
        setContent {
            MyApplicationTheme {
                V21Workbench(initialUri) { cadSurface = it }
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
private fun V21Workbench(
    initialUri: Uri?,
    onSurfaceReady: (FaceDrivenCadSurfaceView) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var program by remember { mutableStateOf(BasicCadProgram()) }
    var document by remember { mutableStateOf<V21Document?>(null) }
    var selectedId by remember { mutableStateOf<Long?>(1L) }
    var selectedFace by remember { mutableStateOf(EditableCadFace.NONE) }
    var treeVisible by remember { mutableStateOf(true) }
    var rightVisible by remember { mutableStateOf(true) }
    var rightMode by remember { mutableStateOf(V21RightMode.PROPERTIES) }
    var surface by remember { mutableStateOf<FaceDrivenCadSurfaceView?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Preparando FreeCAD…") }
    var error by remember { mutableStateOf<String?>(null) }

    fun rebuild(next: BasicCadProgram, fit: Boolean, message: String) {
        loading = true
        error = null
        selectedFace = EditableCadFace.NONE
        surface?.setSelectedFace(EditableCadFace.NONE)
        scope.launch {
            runCatching { v21BuildProgram(context, next, fit) }
                .onSuccess {
                    program = next
                    document = it
                    selectedId = next.features.lastOrNull()?.id
                    status = message
                }
                .onFailure {
                    error = v21Failure(it)
                    status = "No se pudo reconstruir el modelo"
                    surface?.restoreCommittedPreview()
                }
            loading = false
        }
    }

    fun openUri(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        loading = true
        error = null
        status = "Abriendo documento…"
        scope.launch {
            runCatching { v21LoadExternal(context, uri) }
                .onSuccess {
                    document = it
                    it.program?.let { loaded -> program = loaded }
                    selectedId = it.program?.features?.lastOrNull()?.id
                    status = "${it.format} cargado"
                }
                .onFailure {
                    error = v21Failure(it)
                    status = "Error al abrir el archivo"
                }
            loading = false
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(::openUri)
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            openUri(initialUri)
        } else {
            rebuild(BasicCadProgram(), true, "Pieza paramétrica creada")
        }
    }

    val active = document?.program
    val selected = active?.features?.firstOrNull { it.id == selectedId }
    val directCylinder = active?.features?.size == 1 &&
        active.features.first().parameters.containsKey("diameter")

    Scaffold(
        topBar = {
            Surface(
                color = Color(0xFFF1F3F5),
                contentColor = V21Text,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .statusBarsPadding()
                        .height(58.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color(0xFFD32F2F),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "SF",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.widthIn(max = 165.dp)) {
                        Text(
                            "SolidFreeCAD",
                            color = V21Text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            document?.name ?: "Sin título",
                            color = V21Secondary,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        V21Toolbar("Nuevo") {
                            rebuild(BasicCadProgram(), true, "Pieza paramétrica creada")
                        }
                        V21Toolbar("Abrir") { open.launch(arrayOf("*/*")) }
                        V21Toolbar("Operaciones") {
                            rightMode = V21RightMode.OPERATIONS
                            rightVisible = true
                        }
                        V21Toolbar("Propiedades") {
                            rightMode = V21RightMode.PROPERTIES
                            rightVisible = true
                        }
                        V21Toolbar("Encuadrar") {
                            document?.mesh?.let { surface?.fit(it) }
                        }
                    }
                    IconButton(onClick = { treeVisible = !treeVisible }) {
                        Icon(
                            if (treeVisible) Icons.Default.KeyboardArrowLeft
                            else Icons.Default.KeyboardArrowRight,
                            "Árbol",
                            tint = V21Accent
                        )
                    }
                    IconButton(onClick = { rightVisible = !rightVisible }) {
                        Icon(
                            if (rightVisible) Icons.Default.KeyboardArrowRight
                            else Icons.Default.KeyboardArrowLeft,
                            "Panel derecho",
                            tint = V21Accent
                        )
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color(0xFFE9EEF2),
                contentColor = V21Text,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        status,
                        modifier = Modifier.weight(1f),
                        color = V21Text,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        document?.format ?: "—",
                        color = V21Secondary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .background(Color(0xFFE8EDF0))
        ) {
            val compact = maxWidth < 850.dp
            Row(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = treeVisible,
                    enter = expandHorizontally(
                        expandFrom = Alignment.Start,
                        animationSpec = v21Tween()
                    ) + slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = v21Tween()
                    ) + fadeIn(tween(160)),
                    exit = shrinkHorizontally(
                        shrinkTowards = Alignment.Start,
                        animationSpec = v21Tween()
                    ) + slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = v21Tween()
                    ) + fadeOut(tween(150))
                ) {
                    Row {
                        V21Tree(
                            document = document,
                            selectedId = selectedId,
                            onSelect = {
                                selectedId = it
                                rightMode = V21RightMode.PROPERTIES
                                if (!compact) rightVisible = true
                            },
                            onAdd = {
                                rightMode = V21RightMode.OPERATIONS
                                rightVisible = true
                                if (compact) treeVisible = false
                            },
                            onClose = { treeVisible = false },
                            modifier = Modifier.width(if (compact) 210.dp else 230.dp)
                        )
                        VerticalDivider(color = V21Divider)
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    AndroidView(
                        factory = { androidContext ->
                            FaceDrivenCadSurfaceView(androidContext).also { view ->
                                surface = view
                                onSurfaceReady(view)
                                view.onFaceSelected = { face ->
                                    if (directCylinder) {
                                        selectedFace = face
                                        view.setSelectedFace(face)
                                        status = when (face) {
                                            EditableCadFace.TOP ->
                                                "Cara superior: arrastre para cambiar profundidad"
                                            EditableCadFace.SIDE ->
                                                "Cara curva: arrastre para cambiar diámetro"
                                            EditableCadFace.NONE -> "Selección cancelada"
                                        }
                                    } else {
                                        selectedFace = EditableCadFace.NONE
                                        view.setSelectedFace(EditableCadFace.NONE)
                                        status = "Edite la operación desde el árbol"
                                    }
                                }
                                view.onParameterPreview = { face, value ->
                                    status = if (face == EditableCadFace.TOP) {
                                        "Profundidad: ${v21Mm(value.toDouble())}"
                                    } else {
                                        "Diámetro: ${v21Mm(value.toDouble())}"
                                    }
                                }
                                view.onParameterCommit = { face, value ->
                                    if (directCylinder) {
                                        val first = program.features.first()
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
                                        rebuild(
                                            program.updateBaseCylinder(diameter, depth),
                                            false,
                                            "Saliente-Extruir1 actualizado"
                                        )
                                    }
                                }
                            }
                        },
                        update = { view ->
                            document?.let { current ->
                                if (view.tag != current.token) {
                                    view.tag = current.token
                                    view.setMesh(current.mesh, current.fit)
                                }
                            }
                            val first = active?.features?.firstOrNull()
                            view.setDimensions(
                                (first?.parameters?.get("depth") ?: 40.0).toFloat(),
                                (first?.parameters?.get("diameter") ?: 34.93).toFloat()
                            )
                            view.setSelectedFace(
                                if (directCylinder) selectedFace else EditableCadFace.NONE
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (!treeVisible) {
                        V21Handle(
                            label = "Árbol",
                            arrow = "▶",
                            onClick = { treeVisible = true },
                            modifier = Modifier.align(Alignment.CenterStart),
                            right = false
                        )
                    }
                    if (!rightVisible) {
                        V21Handle(
                            label = "Panel",
                            arrow = "◀",
                            onClick = { rightVisible = true },
                            modifier = Modifier.align(Alignment.CenterEnd),
                            right = true
                        )
                    }
                    if (loading) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White,
                            contentColor = V21Text,
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 5.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(25.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Recalculando BRep…",
                                    color = V21Text,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    error?.let { message ->
                        Surface(
                            modifier = Modifier.align(Alignment.BottomStart)
                                .padding(12.dp)
                                .widthIn(max = 460.dp),
                            color = Color(0xFFFFEBEE),
                            contentColor = Color(0xFF5F1414),
                            shape = RoundedCornerShape(7.dp),
                            shadowElevation = 5.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    null,
                                    tint = Color(0xFFC62828)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    message,
                                    modifier = Modifier.weight(1f),
                                    color = Color(0xFF5F1414),
                                    fontSize = 9.sp
                                )
                                IconButton(onClick = { error = null }) {
                                    Icon(Icons.Default.Cancel, "Cerrar")
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = rightVisible,
                    enter = expandHorizontally(
                        expandFrom = Alignment.End,
                        animationSpec = v21Tween()
                    ) + slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = v21Tween()
                    ) + fadeIn(tween(160)),
                    exit = shrinkHorizontally(
                        shrinkTowards = Alignment.End,
                        animationSpec = v21Tween()
                    ) + slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = v21Tween()
                    ) + fadeOut(tween(150))
                ) {
                    Row {
                        VerticalDivider(color = V21Divider)
                        V21RightPanel(
                            mode = rightMode,
                            feature = selected,
                            document = document,
                            onMode = { rightMode = it },
                            onClose = { rightVisible = false },
                            onOperation = { operation ->
                                rightMode = V21RightMode.PROPERTIES
                                rebuild(
                                    program.append(operation),
                                    false,
                                    "${operation.label} añadido"
                                )
                            },
                            onApply = { values ->
                                selected?.let {
                                    rebuild(
                                        program.updateFeature(it.id, values),
                                        false,
                                        "${it.label} recalculado"
                                    )
                                }
                            },
                            onSuppress = {
                                selected?.let {
                                    rebuild(
                                        program.toggleSuppressed(it.id),
                                        false,
                                        "Estado actualizado"
                                    )
                                }
                            },
                            onDelete = {
                                selected?.let { feature ->
                                    if (feature.id == program.features.firstOrNull()?.id) {
                                        error = "La operación base no se puede eliminar"
                                    } else {
                                        rebuild(
                                            program.remove(feature.id),
                                            false,
                                            "${feature.label} eliminado"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.width(if (compact) 260.dp else 285.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun v21Tween() = tween<Int>(
    durationMillis = V21AnimMs,
    easing = FastOutSlowInEasing
)

@Composable
private fun V21Toolbar(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.width(76.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        color = V21Accent,
        fontSize = 8.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun V21Tree(
    document: V21Document?,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = V21Panel,
        contentColor = V21Text
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(V21Header)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "FeatureManager",
                    modifier = Modifier.weight(1f),
                    color = V21Text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, "Añadir", tint = V21Accent)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowLeft,
                        "Ocultar",
                        tint = V21Accent
                    )
                }
            }
            HorizontalDivider(color = V21Divider)
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                Text(
                    document?.name ?: "Sin documento",
                    color = V21Text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "  ▾ Cuerpo1",
                    color = V21Text,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 5.dp)
                )
                Text("      ▸ Origen", color = V21Muted, fontSize = 8.sp)
                val features = document?.program?.features.orEmpty()
                if (features.isEmpty()) {
                    Text(
                        "      ${document?.format ?: "Geometría importada"}",
                        color = V21Secondary,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                features.forEachIndexed { index, feature ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(
                                if (feature.id == selectedId) V21Selected
                                else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelect(feature.id) }
                            .padding(horizontal = 5.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (feature.suppressed) "○" else "◆",
                            color = if (feature.suppressed) V21Muted else V21Accent,
                            fontSize = 8.sp
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            feature.label,
                            color = V21Text,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (index == 0) {
                        Text(
                            "          (-) Croquis1",
                            color = V21Secondary,
                            fontSize = 7.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun V21RightPanel(
    mode: V21RightMode,
    feature: BasicCadFeature?,
    document: V21Document?,
    onMode: (V21RightMode) -> Unit,
    onClose: () -> Unit,
    onOperation: (BasicCadOperation) -> Unit,
    onApply: (Map<String, Double>) -> Unit,
    onSuppress: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        color = V21Panel,
        contentColor = V21Text,
        shadowElevation = 3.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(V21Header)
                    .padding(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                V21Tab(
                    "Propiedades",
                    mode == V21RightMode.PROPERTIES
                ) { onMode(V21RightMode.PROPERTIES) }
                V21Tab(
                    "Operaciones",
                    mode == V21RightMode.OPERATIONS
                ) { onMode(V21RightMode.OPERATIONS) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        "Ocultar",
                        tint = V21Accent
                    )
                }
            }
            HorizontalDivider(color = V21Divider)
            if (mode == V21RightMode.OPERATIONS) {
                V21Operations(onOperation, Modifier.fillMaxSize())
            } else {
                V21Properties(
                    feature,
                    document,
                    onApply,
                    onSuppress,
                    onDelete,
                    Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun V21Tab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) Color.White else Color.Transparent,
        contentColor = if (selected) V21Accent else V21Secondary,
        shape = RoundedCornerShape(5.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            color = if (selected) V21Accent else V21Secondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun V21Properties(
    feature: BasicCadFeature?,
    document: V21Document?,
    onApply: (Map<String, Double>) -> Unit,
    onSuppress: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier
) {
    if (feature == null) {
        Text(
            document?.summary ?: "Seleccione una operación",
            modifier = modifier.padding(10.dp),
            color = V21Secondary,
            fontSize = 8.sp
        )
        return
    }
    var draft by remember(feature.id, feature.parameters) {
        mutableStateOf(feature.parameters.mapValues { v21Number(it.value) })
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            feature.label,
            color = V21Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            feature.operation.description,
            color = V21Secondary,
            fontSize = 8.sp
        )
        Text(
            if (feature.operation.generalTopology) {
                "OPERACIÓN PARAMÉTRICA"
            } else {
                "OPERACIÓN BÁSICA BETA"
            },
            color = if (feature.operation.generalTopology) {
                Color(0xFF1B6E32)
            } else {
                Color(0xFFB54708)
            },
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold
        )
        HorizontalDivider(color = V21Divider)
        draft.forEach { (key, value) ->
            OutlinedTextField(
                value = value,
                onValueChange = { draft = draft + (key to v21Numeric(it)) },
                label = { Text(key, color = V21Secondary) },
                textStyle = LocalTextStyle.current.copy(
                    color = V21Text,
                    fontWeight = FontWeight.Medium
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Button(
            onClick = {
                val parsed = linkedMapOf<String, Double>()
                for ((key, text) in draft) {
                    val value = text.toDoubleOrNull() ?: return@Button
                    if (!value.isFinite()) return@Button
                    parsed[key] = value
                }
                onApply(parsed)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Check, null)
            Spacer(Modifier.width(5.dp))
            Text("Aplicar y recalcular")
        }
        TextButton(onClick = onSuppress, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (feature.suppressed) Icons.Default.Visibility
                else Icons.Default.VisibilityOff,
                null
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (feature.suppressed) "Activar" else "Suprimir",
                color = V21Accent
            )
        }
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Icon(
                Icons.Default.Delete,
                null,
                tint = Color(0xFFB42318)
            )
            Spacer(Modifier.width(5.dp))
            Text("Eliminar", color = Color(0xFFB42318))
        }
    }
}

@Composable
private fun V21Operations(
    onSelect: (BasicCadOperation) -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        BasicCadFeatureFamily.entries.forEach { family ->
            Text(
                family.label.uppercase(Locale.ROOT),
                color = V21Secondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
            BasicCadOperation.entries.filter { it.family == family }
                .forEach { operation ->
                    Surface(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onSelect(operation) },
                        color = Color(0xFFEDF3F7),
                        contentColor = V21Text,
                        shape = RoundedCornerShape(6.dp),
                        shadowElevation = 1.dp
                    ) {
                        Column(Modifier.padding(9.dp)) {
                            Row {
                                Text(
                                    operation.label,
                                    modifier = Modifier.weight(1f),
                                    color = V21Text,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!operation.generalTopology) {
                                    Text(
                                        "BETA",
                                        color = Color(0xFFB54708),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                operation.description,
                                color = V21Secondary,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun V21Handle(
    label: String,
    arrow: String,
    onClick: () -> Unit,
    modifier: Modifier,
    right: Boolean
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = V21Accent,
        contentColor = Color.White,
        shape = if (right) {
            RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
        } else {
            RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
        },
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(arrow, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                label,
                color = Color.White,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private suspend fun v21BuildProgram(
    context: android.content.Context,
    program: BasicCadProgram,
    fit: Boolean
): V21Document = withContext(Dispatchers.IO) {
    val scene = SolidFreeCadMacroRuntime.execute(
        context,
        BasicCadMacroGenerator.generate(program),
        0.18,
        0.24
    )
    V21Document(
        name = "${program.documentName}.FCStd",
        format = "Modelo paramétrico",
        mesh = scene.mesh.v21Native(),
        summary = "FreeCAD Base 1.1.1 / Runtime 0.11\n" +
            "${scene.pythonVersion}\n${scene.documentSummary}",
        program = program,
        token = System.nanoTime(),
        fit = fit
    )
}

private suspend fun v21LoadExternal(
    context: android.content.Context,
    uri: Uri
): V21Document = withContext(Dispatchers.IO) {
    val fallback = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { it.contains('.') }
        ?: "document.FCStd"
    val (file, name) = AndroidDocumentLoader.stage(
        context,
        uri,
        "solidfreecad-workbench-v21",
        fallback
    )
    when (val extension = v21Extension(context, uri, file, name)) {
        "step", "stp" -> {
            val scene = NativeStepBridge.importStep(file.absolutePath, name)
            V21Document(
                name,
                "STEP",
                scene.mesh.v21Native(),
                scene.summary,
                null,
                System.nanoTime(),
                true
            )
        }
        "fcstd" -> {
            val archive = FreeCadArchiveReader.extract(
                file,
                File(context.cacheDir, "solidfreecad-fcstd-v21"),
                name
            )
            val scene = NativeFreeCadFileBridge.importFcStdObjects(
                archive.objects,
                name,
                archive.summary
            )
            V21Document(
                name,
                "FCStd",
                scene.mesh.v21Native(),
                scene.summary,
                null,
                System.nanoTime(),
                true
            )
        }
        "fcmacro", "py" -> {
            val scene = SolidFreeCadMacroRuntime.executeFile(
                context,
                file.readBytes(),
                0.18,
                0.24
            )
            V21Document(
                name,
                "FCMacro",
                scene.mesh.v21Native(),
                "Macro FreeCAD ejecutada\n${scene.pythonVersion}\n" +
                    "${scene.documentSummary}\n${scene.output}",
                null,
                System.nanoTime(),
                true
            )
        }
        else -> error(
            "Formato no compatible: .$extension. Use STEP, FCStd o FCMacro"
        )
    }
}

private fun v21Extension(
    context: android.content.Context,
    uri: Uri,
    file: File,
    name: String
): String {
    val extension = AndroidDocumentLoader.extension(name)
    if (extension in setOf("step", "stp", "fcstd", "fcmacro", "py")) {
        return extension
    }
    val mime = context.contentResolver.getType(uri)
        .orEmpty()
        .lowercase(Locale.ROOT)
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
    ) {
        "fcmacro"
    } else {
        extension
    }
}

private fun SceneMesh.v21Native() = NativeSceneMesh(
    vertices = vertices,
    indices = indices,
    minX = minX,
    minY = minY,
    minZ = minZ,
    maxX = maxX,
    maxY = maxY,
    maxZ = maxZ
)

private fun v21Numeric(value: String) = value.filterIndexed { index, char ->
    char.isDigit() || char == '.' || char == ',' ||
        (char == '-' && index == 0)
}.replace(',', '.')

private fun v21Failure(error: Throwable) =
    generateSequence(error) { it.cause }.last().message
        ?: error.message
        ?: error::class.java.simpleName

private fun v21Mm(value: Double) = "%.2f mm".format(Locale.US, value)

private fun v21Number(value: Double) = "%.4f".format(Locale.US, value)
    .trimEnd('0')
    .trimEnd('.')
