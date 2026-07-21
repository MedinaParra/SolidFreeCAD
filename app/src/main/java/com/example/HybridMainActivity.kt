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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProjectState
import com.example.nativecad.NativeCadBridge
import com.example.nativecad.NativeCadCapabilities
import com.example.nativecad.NativeDocumentSummary
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.util.UUID

private enum class HybridScreen {
    HOME,
    WORKSPACE,
    IMPORT_REPORT
}

class HybridMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SolidFreeCadNativeApp()
            }
        }
    }
}

@Composable
private fun SolidFreeCadNativeApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val capabilities = remember { NativeCadBridge.capabilities() }
    var screen by remember { mutableStateOf(HybridScreen.HOME) }
    var summary by remember { mutableStateOf<NativeDocumentSummary?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("Pieza_Nativa") }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            loading = true
            errorMessage = null
            scope.launch {
                NativeCadBridge.inspect(context, uri)
                    .onSuccess {
                        summary = it
                        projectName = it.fileName.substringBeforeLast('.').ifBlank { "Documento_Nativo" }
                        screen = HybridScreen.IMPORT_REPORT
                    }
                    .onFailure { errorMessage = it.message ?: "No se pudo analizar el archivo." }
                loading = false
            }
        }
    }

    when (screen) {
        HybridScreen.HOME -> HybridHomeScreen(
            capabilities = capabilities,
            loading = loading,
            errorMessage = errorMessage,
            onNewDocument = {
                projectName = "Pieza_Nativa_${UUID.randomUUID().toString().take(4)}"
                screen = HybridScreen.WORKSPACE
            },
            onOpenDocument = {
                openDocument.launch(
                    arrayOf(
                        "application/step",
                        "model/step",
                        "application/octet-stream",
                        "application/zip",
                        "*/*"
                    )
                )
            }
        )

        HybridScreen.WORKSPACE -> CadWorkspaceScreen(
            initialState = ProjectState().copy(projectName = projectName),
            onGoBackToWelcome = { screen = HybridScreen.HOME },
            onNewClick = {
                projectName = "Pieza_Nativa_${UUID.randomUUID().toString().take(4)}"
                screen = HybridScreen.WORKSPACE
            }
        )

        HybridScreen.IMPORT_REPORT -> ImportReportScreen(
            capabilities = capabilities,
            summary = summary,
            onBack = { screen = HybridScreen.HOME },
            onOpenAnother = {
                openDocument.launch(arrayOf("*/*"))
            },
            onContinueToWorkspace = { screen = HybridScreen.WORKSPACE }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HybridHomeScreen(
    capabilities: NativeCadCapabilities,
    loading: Boolean,
    errorMessage: String?,
    onNewDocument: () -> Unit,
    onOpenDocument: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFD32F2F), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text("SF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("SolidFreeCAD", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("FreeCAD-Native integration preview", fontSize = 10.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFECEFF1),
                    titleContentColor = Color(0xFF37474F)
                )
            )
        },
        bottomBar = {
            Surface(color = Color(0xFFECEFF1), tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Motor objetivo: ${capabilities.sourceRepository}", fontSize = 10.sp)
                    Text("v1 híbrida", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF5F7FA))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.ViewInAr, null, modifier = Modifier.size(58.dp), tint = Color(0xFFD32F2F))
                    Text("Interfaz CAD + motor FreeCAD nativo", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                    Text(
                        "Esta APK conserva el entorno gráfico de SolidFreeCAD y añade la primera capa de integración con FreeCAD-Native.",
                        color = Color(0xFF546E7A),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onNewDocument, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) {
                            Icon(Icons.Default.NoteAdd, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Nueva pieza")
                        }
                        OutlinedButton(onClick = onOpenDocument, enabled = !loading) {
                            Icon(Icons.Default.FolderOpen, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (loading) "Analizando…" else "Abrir STEP/FCStd")
                        }
                    }
                    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }

            Text("Estado del motor", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    CapabilityRow("Puente de documentos", capabilities.documentBridgeReady, "Activo")
                    CapabilityRow("Inspección STEP", capabilities.stepInspectionReady, "Activa")
                    CapabilityRow("Inspección FCStd", capabilities.fcStdInspectionReady, "Activa")
                    CapabilityRow(
                        "Biblioteca freecad_native",
                        capabilities.nativeLibraryLoaded,
                        if (capabilities.nativeLibraryLoaded) "Cargada" else "Pendiente de empaquetar"
                    )
                    CapabilityRow(
                        "Geometría STEP BRep",
                        capabilities.stepGeometryReady,
                        if (capabilities.stepGeometryReady) "Activa" else "Próxima etapa OCCT"
                    )
                    CapabilityRow(
                        "Árbol paramétrico FCStd",
                        capabilities.fcStdParametricReady,
                        if (capabilities.fcStdParametricReady) "Activo" else "Próxima etapa FreeCAD Core"
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Engineering, null, tint = Color(0xFFF57F17))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "La apertura actual inspecciona la estructura real de STEP y FCStd completamente offline. Todavía no afirma renderizar BRep: esa función se activará al incorporar las bibliotecas ARM64 de FreeCAD/OpenCASCADE desde FreeCAD-Native.",
                        fontSize = 12.sp,
                        color = Color(0xFF5D4037)
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityRow(label: String, enabled: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, fontSize = 10.sp, color = Color(0xFF78909C))
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = if (enabled) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
            modifier = Modifier.border(
                1.dp,
                if (enabled) Color(0xFF81C784) else Color(0xFFEF9A9A),
                RoundedCornerShape(50)
            )
        ) {
            Text(
                if (enabled) "LISTO" else "PENDIENTE",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportReportScreen(
    capabilities: NativeCadCapabilities,
    summary: NativeDocumentSummary?,
    onBack: () -> Unit,
    onOpenAnother: () -> Unit,
    onContinueToWorkspace: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico de documento nativo") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (summary == null) {
                Text("No hay información disponible.")
                return@Column
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Default.Description, null, tint = Color(0xFF1565C0), modifier = Modifier.size(34.dp))
                    Text(summary.fileName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(summary.format.displayName, color = Color(0xFF1565C0), fontWeight = FontWeight.SemiBold)
                    Text(formatBytes(summary.sizeBytes), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }

            Card {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReportRow("Esquema", summary.schema ?: "No identificado")
                    summary.units?.let { ReportRow("Unidades", it) }
                    if (summary.entityCount > 0) ReportRow("Entidades STEP", summary.entityCount.toString())
                    if (summary.objectCount > 0) ReportRow("Objetos FCStd", summary.objectCount.toString())
                    if (summary.brepEntryCount > 0) ReportRow("Recursos BRep", summary.brepEntryCount.toString())
                    if (summary.productNames.isNotEmpty()) {
                        Divider()
                        Text("Productos detectados", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        summary.productNames.forEach { Text("• $it", fontSize = 12.sp) }
                    }
                }
            }

            summary.notes.forEach { note ->
                Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFF8E1)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFFF57F17), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(note, fontSize = 11.sp)
                    }
                }
            }

            Text(
                "Origen del motor: ${capabilities.sourceRepository}",
                fontSize = 10.sp,
                color = Color(0xFF78909C),
                fontFamily = FontFamily.Monospace
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenAnother, modifier = Modifier.weight(1f)) {
                    Text("Abrir otro")
                }
                Button(onClick = onContinueToWorkspace, modifier = Modifier.weight(1f)) {
                    Text("Entorno CAD")
                }
            }
            Text(
                "Entrar al entorno CAD crea por ahora un documento vinculado por nombre; no convierte la inspección en geometría editable.",
                fontSize = 10.sp,
                color = Color(0xFF78909C),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color(0xFF546E7A))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "Tamaño no informado"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    return "%.1f MB".format(kb / 1024.0)
}
