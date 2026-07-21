package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.model.ProjectState
import com.example.nativecad.NativeCadBridge
import com.example.nativecad.NativeCadCapabilities
import com.example.nativecad.NativeDocumentSummary
import com.example.nativecad.viewer.CameraPreset
import com.example.nativecad.viewer.NativeCadSurfaceView
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.util.UUID

private enum class NativeHybridScreen { HOME, REPORT, PREVIEW, WORKSPACE }

class NativeHybridActivityV2 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                NativeHybridAppV2()
            }
        }
    }
}

@Composable
private fun NativeHybridAppV2() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capabilities = remember { NativeCadBridge.capabilities() }
    var screen by remember { mutableStateOf(NativeHybridScreen.HOME) }
    var summary by remember { mutableStateOf<NativeDocumentSummary?>(null) }
    var projectName by remember { mutableStateOf("Pieza_Nativa") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            loading = true
            error = null
            scope.launch {
                NativeCadBridge.inspect(context, uri)
                    .onSuccess { document ->
                        summary = document
                        projectName = document.fileName.substringBeforeLast('.').ifBlank { "Documento_Nativo" }
                        screen = NativeHybridScreen.REPORT
                    }
                    .onFailure { throwable -> error = throwable.message ?: "No se pudo abrir el documento." }
                loading = false
            }
        }
    }

    val openDocument = {
        launcher.launch(arrayOf("application/step", "model/step", "application/zip", "application/octet-stream", "*/*"))
    }

    when (screen) {
        NativeHybridScreen.HOME -> NativeHomeV2(
            capabilities = capabilities,
            loading = loading,
            error = error,
            onOpen = openDocument,
            onNew = {
                projectName = "Pieza_Nativa_${UUID.randomUUID().toString().take(4)}"
                screen = NativeHybridScreen.WORKSPACE
            }
        )
        NativeHybridScreen.REPORT -> NativeReportV2(
            capabilities = capabilities,
            summary = summary,
            onBack = { screen = NativeHybridScreen.HOME },
            onOpenAnother = openDocument,
            onPreview = { screen = NativeHybridScreen.PREVIEW },
            onWorkspace = { screen = NativeHybridScreen.WORKSPACE }
        )
        NativeHybridScreen.PREVIEW -> NativePreviewV2(
            summary = summary,
            onBack = { screen = NativeHybridScreen.REPORT },
            onWorkspace = { screen = NativeHybridScreen.WORKSPACE }
        )
        NativeHybridScreen.WORKSPACE -> CadWorkspaceScreen(
            initialState = ProjectState().copy(projectName = projectName),
            onGoBackToWelcome = { screen = NativeHybridScreen.HOME },
            onNewClick = {
                projectName = "Pieza_Nativa_${UUID.randomUUID().toString().take(4)}"
                screen = NativeHybridScreen.WORKSPACE
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeHomeV2(
    capabilities: NativeCadCapabilities,
    loading: Boolean,
    error: String?,
    onOpen: () -> Unit,
    onNew: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color(0xFFD32F2F), shape = RoundedCornerShape(5.dp)) {
                            Text("SF", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                        }
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text("SolidFreeCAD Native", fontWeight = FontWeight.Bold)
                            Text("STEP OpenGL preview · fase 2", fontSize = 10.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFECEFF1))
            )
        },
        bottomBar = {
            Surface(color = Color(0xFFECEFF1)) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(capabilities.sourceRepository, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("v1.2", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(Color(0xFFF5F7FA)).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(4.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.ViewInAr, null, modifier = Modifier.size(62.dp), tint = Color(0xFFD32F2F))
                    Text("Interfaz CAD con vista STEP OpenGL", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text(
                        "Abre STEP o FCStd sin conexión. Los STEP con teselación AP242 se muestran como malla; los demás generan una envolvente geométrica verificable.",
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = Color(0xFF546E7A)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpen, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) {
                            Icon(Icons.Default.FolderOpen, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (loading) "Procesando…" else "Abrir archivo")
                        }
                        OutlinedButton(onClick = onNew) {
                            Icon(Icons.Default.NoteAdd, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Nueva pieza")
                        }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            }

            Text("Capacidades activas", fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NativeCapability("Selector de documentos Android", capabilities.documentBridgeReady, "STEP, STP y FCStd")
                    NativeCapability("Inspección STEP/FCStd", capabilities.stepInspectionReady && capabilities.fcStdInspectionReady, "Procesamiento local")
                    NativeCapability("Visor OpenGL", capabilities.openGlPreviewReady, "Órbita, paneo y zoom")
                    NativeCapability("BRep OpenCASCADE", capabilities.stepGeometryReady, if (capabilities.stepGeometryReady) "Biblioteca cargada" else "Aún requiere ARM64")
                    NativeCapability("Árbol paramétrico FCStd", capabilities.fcStdParametricReady, "Pendiente FreeCAD Core")
                }
            }

            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFFF8E1)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, null, tint = Color(0xFFF57F17))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "La teselación AP242 sí representa triángulos presentes en el STEP. La envolvente es solo una comprobación de dimensiones y orientación; no reemplaza el sólido BRep.",
                        fontSize = 12.sp,
                        color = Color(0xFF5D4037)
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeCapability(label: String, ready: Boolean, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 10.sp, color = Color(0xFF78909C))
        }
        Surface(
            color = if (ready) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
            shape = RoundedCornerShape(50),
            modifier = Modifier.border(1.dp, if (ready) Color(0xFF81C784) else Color(0xFFEF9A9A), RoundedCornerShape(50))
        ) {
            Text(
                if (ready) "LISTO" else "PENDIENTE",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (ready) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativeReportV2(
    capabilities: NativeCadCapabilities,
    summary: NativeDocumentSummary?,
    onBack: () -> Unit,
    onOpenAnother: () -> Unit,
    onPreview: () -> Unit,
    onWorkspace: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documento importado") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (summary == null) {
                Text("No hay documento cargado.")
                return@Column
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Description, null, tint = Color(0xFF1565C0), modifier = Modifier.size(34.dp))
                    Text(summary.fileName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(summary.format.displayName, color = Color(0xFF1565C0), fontWeight = FontWeight.SemiBold)
                    Text(nativeFormatBytes(summary.sizeBytes), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Card {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NativeReportRow("Esquema", summary.schema ?: "No identificado")
                    summary.units?.let { NativeReportRow("Unidades", it) }
                    if (summary.entityCount > 0) NativeReportRow("Entidades STEP", summary.entityCount.toString())
                    if (summary.objectCount > 0) NativeReportRow("Objetos FCStd", summary.objectCount.toString())
                    if (summary.brepEntryCount > 0) NativeReportRow("Recursos BRep", summary.brepEntryCount.toString())
                    summary.previewMode?.let { NativeReportRow("Vista 3D", it) }
                    if (summary.previewSourcePointCount > 0) NativeReportRow("Puntos fuente", summary.previewSourcePointCount.toString())
                    if (summary.previewTriangleCount > 0) NativeReportRow("Triángulos", summary.previewTriangleCount.toString())
                    if (summary.productNames.isNotEmpty()) {
                        HorizontalDivider()
                        Text("Productos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        summary.productNames.forEach { Text("• $it", fontSize = 12.sp) }
                    }
                }
            }
            summary.notes.forEach { note ->
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF8E1)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFFF57F17), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(note, fontSize = 11.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenAnother, modifier = Modifier.weight(1f)) { Text("Abrir otro") }
                Button(onClick = onPreview, enabled = summary.previewMesh != null, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.ViewInAr, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Vista 3D")
                }
            }
            Button(onClick = onWorkspace, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Construction, null)
                Spacer(Modifier.width(6.dp))
                Text("Entrar al entorno CAD")
            }
            Text(
                "Motor objetivo: ${capabilities.sourceRepository}",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF78909C)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NativePreviewV2(
    summary: NativeDocumentSummary?,
    onBack: () -> Unit,
    onWorkspace: () -> Unit
) {
    val mesh = summary?.previewMesh
    var view by remember { mutableStateOf<NativeCadSurfaceView?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(summary?.fileName ?: "Vista 3D", maxLines = 1)
                        Text(summary?.previewMode ?: "OpenGL", fontSize = 10.sp)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") } },
                actions = { IconButton(onClick = onWorkspace) { Icon(Icons.Default.Construction, "Entorno CAD") } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFF0E1114))) {
            if (mesh == null) {
                Text("No hay malla disponible.", color = Color.White, modifier = Modifier.align(Alignment.Center))
                return@Box
            }
            AndroidView(
                factory = { context ->
                    NativeCadSurfaceView(context).also { surface ->
                        surface.setMesh(mesh)
                        view = surface
                    }
                },
                update = { surface -> surface.setMesh(mesh) },
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                color = Color(0xCC1B2329),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(summary.previewMode ?: "Vista STEP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("${mesh.vertexCount} vértices · ${mesh.triangleCount} triángulos", color = Color(0xFFB0BEC5), fontSize = 10.sp)
                    Text("1 dedo: órbita · 2 dedos: pan · pellizco: zoom", color = Color(0xFF90A4AE), fontSize = 9.sp)
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                color = Color(0xDD263238),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    NativeViewButton(Icons.Default.CenterFocusStrong, "Ajustar") { view?.fit(mesh) }
                    NativeViewButton(Icons.Default.ViewInAr, "ISO") { view?.setPreset(CameraPreset.ISOMETRIC) }
                    NativeViewButton(Icons.Default.FilterCenterFocus, "Frontal") { view?.setPreset(CameraPreset.FRONT) }
                    NativeViewButton(Icons.Default.KeyboardArrowRight, "Derecha") { view?.setPreset(CameraPreset.RIGHT) }
                    NativeViewButton(Icons.Default.VerticalAlignTop, "Superior") { view?.setPreset(CameraPreset.TOP) }
                }
            }
            if (summary.previewMode == "Envolvente de puntos STEP") {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp, 12.dp, 12.dp, 78.dp),
                    color = Color(0xE6F57F17),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ENVOLVENTE · NO ES BREP", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun NativeViewButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.size(44.dp)) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun NativeReportRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color(0xFF546E7A))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

private fun nativeFormatBytes(bytes: Long): String {
    if (bytes < 0) return "Tamaño no informado"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    return "%.1f MB".format(kb / 1024.0)
}
