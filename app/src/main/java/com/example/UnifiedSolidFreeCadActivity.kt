package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.nativecad.NativeCadBridge as LightweightCadBridge
import com.example.nativecad.viewer.CameraPreset
import com.example.nativecad.viewer.NativeCadSurfaceView
import com.example.nativecad.viewer.NativeSceneMesh
import com.example.nativecad.viewer.ReferencePlane
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

class UnifiedSolidFreeCadActivity : ComponentActivity() {
    private var cadSurface: NativeCadSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                UnifiedSolidFreeCadApp(onSurfaceReady = { cadSurface = it })
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

private data class UnifiedCadDocument(
    val name: String,
    val format: String,
    val objectName: String,
    val mesh: NativeSceneMesh,
    val engine: String,
    val summary: String,
    val isNativeBrep: Boolean
)

private enum class TreeSelection {
    DOCUMENT,
    GEOMETRY,
    PLANE_XY,
    PLANE_XZ,
    PLANE_YZ
}

@Composable
private fun UnifiedSolidFreeCadApp(onSurfaceReady: (NativeCadSurfaceView) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var document by remember { mutableStateOf<UnifiedCadDocument?>(null) }
    var selected by remember { mutableStateOf(TreeSelection.DOCUMENT) }
    var selectedPlane by remember { mutableStateOf<ReferencePlane?>(null) }
    var originExpanded by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Listo") }
    var error by remember { mutableStateOf<String?>(null) }
    var surface by remember { mutableStateOf<NativeCadSurfaceView?>(null) }

    fun selectPlane(plane: ReferencePlane, treeSelection: TreeSelection) {
        selected = treeSelection
        selectedPlane = plane
        status = "${plane.label} seleccionado"
    }

    fun createNewDocument() {
        loading = true
        error = null
        status = "Creando documento OCCT…"
        scope.launch {
            runCatching { createStarterDocument() }
                .onSuccess {
                    document = it
                    selected = TreeSelection.GEOMETRY
                    selectedPlane = null
                    status = "Documento nativo creado"
                }
                .onFailure {
                    error = readableError(it)
                    status = "No se pudo crear el documento nativo"
                }
            loading = false
        }
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
            scope.launch {
                runCatching { loadCadDocument(context, uri) }
                    .onSuccess {
                        document = it
                        selected = TreeSelection.GEOMETRY
                        selectedPlane = null
                        status = if (it.isNativeBrep) {
                            "${it.format} cargado como BRep nativo"
                        } else {
                            "${it.format} cargado con vista previa compatible"
                        }
                    }
                    .onFailure {
                        error = readableError(it)
                        status = "Error al abrir el documento"
                    }
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        createNewDocument()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SolidFreeCadToolbar(
                documentName = document?.name ?: "Sin título",
                loading = loading,
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
                onFit = { document?.mesh?.let { surface?.fit(it) } }
            )
        },
        bottomBar = {
            SolidFreeCadStatusBar(
                status = status,
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
                FeatureTreePanel(
                    document = document,
                    selected = selected,
                    originExpanded = originExpanded,
                    onToggleOrigin = { originExpanded = !originExpanded },
                    onSelectDocument = {
                        selected = TreeSelection.DOCUMENT
                        selectedPlane = null
                        status = "Documento activo"
                    },
                    onSelectGeometry = {
                        selected = TreeSelection.GEOMETRY
                        selectedPlane = null
                        status = document?.objectName ?: "Geometría"
                    },
                    onSelectXY = { selectPlane(ReferencePlane.XY, TreeSelection.PLANE_XY) },
                    onSelectXZ = { selectPlane(ReferencePlane.XZ, TreeSelection.PLANE_XZ) },
                    onSelectYZ = { selectPlane(ReferencePlane.YZ, TreeSelection.PLANE_YZ) },
                    modifier = Modifier.width(if (showProperties) 205.dp else 165.dp)
                )

                VerticalDivider(color = Color(0xFFB8C5CE))

                UnifiedViewport(
                    document = document,
                    selectedPlane = selectedPlane,
                    loading = loading,
                    error = error,
                    onSurfaceReady = {
                        surface = it
                        onSurfaceReady(it)
                    },
                    onDismissError = { error = null },
                    modifier = Modifier.weight(1f)
                )

                if (showProperties) {
                    VerticalDivider(color = Color(0xFFB8C5CE))
                    PropertyPanel(
                        document = document,
                        selected = selected,
                        selectedPlane = selectedPlane,
                        modifier = Modifier.width(225.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SolidFreeCadToolbar(
    documentName: String,
    loading: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onFit: () -> Unit
) {
    Surface(color = Color(0xFFF0F3F5), shadowElevation = 3.dp) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 8.dp),
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
                Column(modifier = Modifier.widthIn(max = 190.dp)) {
                    Text("SolidFreeCAD", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF37474F))
                    Text(
                        documentName,
                        fontSize = 9.sp,
                        color = Color(0xFF607D8B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarButton(Icons.Default.NoteAdd, "Nuevo", enabled = !loading, onClick = onNew)
                    ToolbarButton(Icons.Default.FolderOpen, "Abrir", enabled = !loading, onClick = onOpen)
                    ToolbarButton(Icons.Default.Save, "Guardar", enabled = false, onClick = {})
                    ToolbarSeparator()
                    ToolbarButton(Icons.Default.FilterCenterFocus, "Encuadrar", onClick = onFit)
                    ToolbarButton(Icons.Default.GridOn, "Grilla", enabled = true, onClick = {})
                    ToolbarSeparator()
                    Text(
                        "PIEZA",
                        modifier = Modifier
                            .background(Color(0xFFDDE7EE), RoundedCornerShape(3.dp))
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        color = Color(0xFF455A64),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    color = if (OcctCadBridge.isAvailable) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (OcctCadBridge.isAvailable) Icons.Default.CheckCircle else Icons.Default.Warning,
                            null,
                            tint = if (OcctCadBridge.isAvailable) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (OcctCadBridge.isAvailable) "OCCT 0.8" else "MODO COMPATIBLE",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFFBBC7CF))
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            label,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) Color(0xFF546E7A) else Color(0xFFB0BEC5)
        )
        Text(label, fontSize = 7.sp, color = if (enabled) Color(0xFF455A64) else Color(0xFFB0BEC5))
    }
}

@Composable
private fun ToolbarSeparator() {
    VerticalDivider(
        modifier = Modifier.height(30.dp).padding(horizontal = 4.dp),
        color = Color(0xFFCCD5DB)
    )
}

@Composable
private fun FeatureTreePanel(
    document: UnifiedCadDocument?,
    selected: TreeSelection,
    originExpanded: Boolean,
    onToggleOrigin: () -> Unit,
    onSelectDocument: () -> Unit,
    onSelectGeometry: () -> Unit,
    onSelectXY: () -> Unit,
    onSelectXZ: () -> Unit,
    onSelectYZ: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxHeight(), color = Color(0xFFF8FAFB)) {
        Column {
            Text(
                "ÁRBOL DEL MODELO",
                modifier = Modifier.fillMaxWidth().background(Color(0xFFE1E8ED)).padding(9.dp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF455A64)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 5.dp)
            ) {
                TreeRow(
                    icon = Icons.Default.Description,
                    label = document?.name ?: "Sin título",
                    selected = selected == TreeSelection.DOCUMENT,
                    indent = 0,
                    onClick = onSelectDocument
                )
                TreeRow(
                    icon = if (originExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    label = "Origen",
                    selected = false,
                    indent = 1,
                    onClick = onToggleOrigin
                )
                if (originExpanded) {
                    TreeRow(Icons.Default.GridOn, "Plano XY", selected == TreeSelection.PLANE_XY, 2, onSelectXY)
                    TreeRow(Icons.Default.GridOn, "Plano XZ", selected == TreeSelection.PLANE_XZ, 2, onSelectXZ)
                    TreeRow(Icons.Default.GridOn, "Plano YZ", selected == TreeSelection.PLANE_YZ, 2, onSelectYZ)
                }
                if (document != null) {
                    TreeRow(
                        icon = Icons.Default.ViewInAr,
                        label = document.objectName,
                        selected = selected == TreeSelection.GEOMETRY,
                        indent = 1,
                        onClick = onSelectGeometry,
                        native = document.isNativeBrep
                    )
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
    onClick: () -> Unit,
    native: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFFCBE9FC) else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) Color(0xFF72BCE8) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(start = (7 + indent * 13).dp, end = 5.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(15.dp),
            tint = if (selected) Color(0xFF1976A8) else Color(0xFF607D8B)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFF37474F)
        )
        if (native) {
            Text("BRep", fontSize = 7.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun UnifiedViewport(
    document: UnifiedCadDocument?,
    selectedPlane: ReferencePlane?,
    loading: Boolean,
    error: String?,
    onSurfaceReady: (NativeCadSurfaceView) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var view by remember { mutableStateOf<NativeCadSurfaceView?>(null) }
    Box(modifier = modifier.fillMaxHeight().background(Color(0xFFD5E3ED))) {
        AndroidView(
            factory = { context ->
                NativeCadSurfaceView(context).also {
                    view = it
                    onSurfaceReady(it)
                }
            },
            update = { surface ->
                val mesh = document?.mesh
                if (mesh != null && surface.tag !== mesh) {
                    surface.tag = mesh
                    surface.setMesh(mesh)
                }
                surface.setSelectedPlane(selectedPlane)
            },
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(9.dp),
            color = Color.White.copy(alpha = 0.86f),
            shape = RoundedCornerShape(5.dp),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
                Text(
                    document?.name ?: "Documento nuevo",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF37474F)
                )
                Text(
                    when {
                        selectedPlane != null -> "${selectedPlane.label} · SELECCIÓN AZUL"
                        document?.isNativeBrep == true -> "BRep OCCT · ${document.format}"
                        else -> "Grilla XY · Z arriba"
                    },
                    fontSize = 8.sp,
                    color = if (selectedPlane != null) Color(0xFF1976A8) else Color(0xFF607D8B),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            ViewButton("ISO") { view?.setPreset(CameraPreset.ISOMETRIC) }
            ViewButton("F") { view?.setPreset(CameraPreset.FRONT) }
            ViewButton("R") { view?.setPreset(CameraPreset.RIGHT) }
            ViewButton("T") { view?.setPreset(CameraPreset.TOP) }
            ViewButton("FIT") { document?.mesh?.let { view?.fit(it) } }
        }

        if (loading) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 8.dp
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Procesando geometría CAD…", fontSize = 11.sp)
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
                        Icon(Icons.Default.Close, "Cerrar", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewButton(label: String, onClick: () -> Unit) {
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
private fun PropertyPanel(
    document: UnifiedCadDocument?,
    selected: TreeSelection,
    selectedPlane: ReferencePlane?,
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
                        PropertyValue("Elemento", selectedPlane.label)
                        PropertyValue("Tipo", "Plano de referencia")
                        PropertyValue("Visualización", "Azul claro")
                        PropertyValue("Estado", "Seleccionado")
                    }
                    document != null -> {
                        PropertyValue("Documento", document.name)
                        PropertyValue("Elemento", if (selected == TreeSelection.GEOMETRY) document.objectName else "Documento")
                        PropertyValue("Formato", document.format)
                        PropertyValue("Motor", document.engine)
                        PropertyValue("Vértices", document.mesh.vertexCount.toString())
                        PropertyValue("Triángulos", document.mesh.triangleCount.toString())
                        PropertyValue("Ancho X", formatDimension(document.mesh.maxX - document.mesh.minX))
                        PropertyValue("Fondo Y", formatDimension(document.mesh.maxY - document.mesh.minY))
                        PropertyValue("Alto Z", formatDimension(document.mesh.maxZ - document.mesh.minZ))
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
private fun PropertyValue(label: String, value: String) {
    Column {
        Text(label.uppercase(Locale.ROOT), fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78909C))
        Text(value, fontSize = 10.sp, color = Color(0xFF37474F), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SolidFreeCadStatusBar(
    status: String,
    document: UnifiedCadDocument?,
    nativeAvailable: Boolean
) {
    Surface(color = Color(0xFFE9EEF2), shadowElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(status, modifier = Modifier.weight(1f), fontSize = 8.sp, color = Color(0xFF455A64))
            document?.let {
                Text(
                    "${it.mesh.vertexCount} V · ${it.mesh.triangleCount} T",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF607D8B)
                )
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

private suspend fun createStarterDocument(): UnifiedCadDocument = withContext(Dispatchers.IO) {
    runCatching {
        val scene = OcctCadBridge.createStarterScene()
        UnifiedCadDocument(
            name = "Pieza1.FCStd",
            format = "Documento nativo",
            objectName = "Pieza",
            mesh = scene.mesh.toUnifiedMesh(),
            engine = "FreeCAD-Native 0.8 / OCCT",
            summary = scene.buildInfo + "\n" + scene.documentSummary,
            isNativeBrep = true
        )
    }.getOrElse { failure ->
        UnifiedCadDocument(
            name = "Pieza1",
            format = "Documento compatible",
            objectName = "Pieza",
            mesh = fallbackBoxMesh(),
            engine = "OpenGL compatible",
            summary = "El núcleo nativo no se cargó: ${readableError(failure)}",
            isNativeBrep = false
        )
    }
}

private suspend fun loadCadDocument(context: android.content.Context, uri: Uri): UnifiedCadDocument =
    withContext(Dispatchers.IO) {
        val staged = AndroidDocumentLoader.stage(context, uri, "solidfreecad-imports", "document.step")
        val localFile = staged.first
        val displayName = staged.second
        when (AndroidDocumentLoader.extension(displayName)) {
            "step", "stp" -> loadStepDocument(context, uri, localFile, displayName)
            "fcstd" -> loadFcStdDocument(context, localFile, displayName)
            else -> error("Formato no compatible. Seleccione STEP, STP o FCStd")
        }
    }

private suspend fun loadStepDocument(
    context: android.content.Context,
    uri: Uri,
    localFile: File,
    displayName: String
): UnifiedCadDocument {
    val nativeAttempt = runCatching {
        NativeStepBridge.importStep(localFile.absolutePath, displayName)
    }
    nativeAttempt.getOrNull()?.let { scene ->
        return UnifiedCadDocument(
            name = displayName,
            format = "STEP",
            objectName = displayName.substringBeforeLast('.').ifBlank { "STEP importado" },
            mesh = scene.mesh.toUnifiedMesh(),
            engine = "OpenCASCADE STEPControl_Reader",
            summary = scene.summary,
            isNativeBrep = true
        )
    }

    val fallback = LightweightCadBridge.inspect(context, uri).getOrThrow()
    val mesh = fallback.previewMesh
        ?: error("OpenCASCADE no pudo abrir el STEP y tampoco fue posible generar una vista compatible")
    return UnifiedCadDocument(
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
        isNativeBrep = false
    )
}

private fun loadFcStdDocument(
    context: android.content.Context,
    localFile: File,
    displayName: String
): UnifiedCadDocument {
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
    return UnifiedCadDocument(
        name = displayName,
        format = "FCStd",
        objectName = archive.documentName,
        mesh = scene.mesh.toUnifiedMesh(),
        engine = "FreeCAD-Native BRepIo",
        summary = scene.summary,
        isNativeBrep = true
    )
}

private fun SceneMesh.toUnifiedMesh(): NativeSceneMesh = NativeSceneMesh(
    vertices = vertices,
    indices = indices,
    minX = minX,
    minY = minY,
    minZ = minZ,
    maxX = maxX,
    maxY = maxY,
    maxZ = maxZ
)

private fun fallbackBoxMesh(): NativeSceneMesh {
    val x0 = -40f; val x1 = 40f
    val y0 = -25f; val y1 = 25f
    val z0 = 0f; val z1 = 20f
    val vertices = floatArrayOf(
        x0,y0,z1, 0f,0f,1f,  x1,y0,z1, 0f,0f,1f,  x1,y1,z1, 0f,0f,1f,  x0,y1,z1, 0f,0f,1f,
        x1,y0,z0, 0f,0f,-1f, x0,y0,z0, 0f,0f,-1f, x0,y1,z0, 0f,0f,-1f, x1,y1,z0, 0f,0f,-1f,
        x0,y1,z1, 0f,1f,0f,  x1,y1,z1, 0f,1f,0f,  x1,y1,z0, 0f,1f,0f,  x0,y1,z0, 0f,1f,0f,
        x0,y0,z0, 0f,-1f,0f, x1,y0,z0, 0f,-1f,0f, x1,y0,z1, 0f,-1f,0f, x0,y0,z1, 0f,-1f,0f,
        x1,y0,z1, 1f,0f,0f,  x1,y0,z0, 1f,0f,0f,  x1,y1,z0, 1f,0f,0f,  x1,y1,z1, 1f,0f,0f,
        x0,y0,z0, -1f,0f,0f, x0,y0,z1, -1f,0f,0f, x0,y1,z1, -1f,0f,0f, x0,y1,z0, -1f,0f,0f
    )
    val indices = intArrayOf(
        0,1,2, 0,2,3, 4,5,6, 4,6,7, 8,9,10, 8,10,11,
        12,13,14, 12,14,15, 16,17,18, 16,18,19, 20,21,22, 20,22,23
    )
    return NativeSceneMesh(vertices, indices, x0, y0, z0, x1, y1, z1)
}

private fun readableError(error: Throwable): String =
    generateSequence(error) { it.cause }.last().message
        ?: error.message
        ?: error::class.java.simpleName

private fun formatDimension(value: Float): String = "%.2f mm".format(Locale.US, value)
