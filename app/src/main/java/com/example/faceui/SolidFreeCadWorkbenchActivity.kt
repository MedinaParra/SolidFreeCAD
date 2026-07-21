package com.example.faceui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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

class SolidFreeCadWorkbenchActivity : ComponentActivity() {
    private var cadSurface: FaceDrivenCadSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialUri = intent?.data
        setContent {
            MyApplicationTheme {
                SolidFreeCadWorkbench(initialUri) { cadSurface = it }
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

private data class WorkbenchDocument(
    val name: String,
    val format: String,
    val mesh: NativeSceneMesh,
    val summary: String,
    val program: BasicCadProgram?,
    val token: Long,
    val fitCamera: Boolean
)

@Composable
private fun SolidFreeCadWorkbench(
    initialUri: Uri?,
    onSurfaceReady: (FaceDrivenCadSurfaceView) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var program by remember { mutableStateOf(BasicCadProgram()) }
    var document by remember { mutableStateOf<WorkbenchDocument?>(null) }
    var selectedFeatureId by remember { mutableStateOf<Long?>(1L) }
    var selectedFace by remember { mutableStateOf(EditableCadFace.NONE) }
    var panelsVisible by remember { mutableStateOf(true) }
    var operationsVisible by remember { mutableStateOf(false) }
    var editingFeature by remember { mutableStateOf<BasicCadFeature?>(null) }
    var surface by remember { mutableStateOf<FaceDrivenCadSurfaceView?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Preparando FreeCAD…") }
    var error by remember { mutableStateOf<String?>(null) }

    fun rebuild(next: BasicCadProgram, fitCamera: Boolean, message: String) {
        loading = true
        error = null
        selectedFace = EditableCadFace.NONE
        surface?.setSelectedFace(EditableCadFace.NONE)
        scope.launch {
            runCatching { buildProgramDocument(context, next, fitCamera) }
                .onSuccess {
                    program = next
                    document = it
                    selectedFeatureId = next.features.lastOrNull()?.id
                    status = message
                }
                .onFailure {
                    error = readableFailure(it)
                    status = "No se pudo reconstruir el modelo"
                    surface?.restoreCommittedPreview()
                }
            loading = false
        }
    }

    fun newDocument() {
        val next = BasicCadProgram()
        rebuild(next, true, "Pieza paramétrica creada")
    }

    fun openUri(uri: Uri) {
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
                    it.program?.let { loadedProgram -> program = loadedProgram }
                    selectedFeatureId = it.program?.features?.lastOrNull()?.id
                    selectedFace = EditableCadFace.NONE
                    surface?.setSelectedFace(EditableCadFace.NONE)
                    status = "${it.format} cargado"
                }
                .onFailure {
                    error = readableFailure(it)
                    status = "Error al abrir el archivo"
                }
            loading = false
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openUri(uri)
    }

    LaunchedEffect(initialUri) {
        if (initialUri != null) openUri(initialUri) else newDocument()
    }

    val activeProgram = document?.program
    val selectedFeature = activeProgram?.features?.firstOrNull { it.id == selectedFeatureId }
    val directCylinderEditing = activeProgram?.features?.size == 1 &&
        activeProgram.features.first().parameters.containsKey("diameter")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            WorkbenchToolbar(
                documentName = document?.name ?: "Sin título",
                loading = loading,
                panelsVisible = panelsVisible,
                onNew = ::newDocument,
                onOpen = { openDocument.launch(arrayOf("*/*")) },
                onOperations = { operationsVisible = true },
                onFit = { document?.mesh?.let { surface?.fit(it) } },
                onTogglePanels = { panelsVisible = !panelsVisible }
            )
        },
        bottomBar = {
            Surface(color = Color(0xFFE9EEF2), shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(status, modifier = Modifier.weight(1f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(document?.format ?: "—", fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding).background(Color(0xFFE8EDF0))
        ) {
            val showProperties = panelsVisible && maxWidth >= 820.dp
            Row(modifier = Modifier.fillMaxSize()) {
                if (panelsVisible) {
                    FeatureProgramTree(
                        document = document,
                        selectedFeatureId = selectedFeatureId,
                        onSelect = { selectedFeatureId = it },
                        onAdd = { operationsVisible = true },
                        modifier = Modifier.width(230.dp)
                    )
                    VerticalDivider(color = Color(0xFFB8C4CC))
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    AndroidView(
                        factory = { androidContext ->
                            FaceDrivenCadSurfaceView(androidContext).also { view ->
                                surface = view
                                onSurfaceReady(view)
                                view.onFaceSelected = { face ->
                                    if (directCylinderEditing) {
                                        selectedFace = face
                                        view.setSelectedFace(face)
                                        status = when (face) {
                                            EditableCadFace.TOP -> "Cara superior: arrastre la flecha para cambiar profundidad"
                                            EditableCadFace.SIDE -> "Cara curva: arrastre la flecha para cambiar diámetro"
                                            EditableCadFace.NONE -> "Selección cancelada"
                                        }
                                    } else {
                                        selectedFace = EditableCadFace.NONE
                                        view.setSelectedFace(EditableCadFace.NONE)
                                        status = "Edite la operación desde el árbol; la selección directa general está en desarrollo"
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
                                    if (directCylinderEditing) {
                                        val first = program.features.first()
                                        val diameter = if (face == EditableCadFace.SIDE) value.toDouble()
                                            else first.parameters["diameter"] ?: 34.93
                                        val depth = if (face == EditableCadFace.TOP) value.toDouble()
                                            else first.parameters["depth"] ?: 40.0
                                        rebuild(program.updateBaseCylinder(diameter, depth), false, "Saliente-Extruir1 actualizado desde la cara")
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
                            val first = activeProgram?.features?.firstOrNull()
                            view.setDimensions(
                                (first?.parameters?.get("depth") ?: 40.0).toFloat(),
                                (first?.parameters?.get("diameter") ?: 34.93).toFloat()
                            )
                            view.setSelectedFace(if (directCylinderEditing) selectedFace else EditableCadFace.NONE)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        color = Color.White.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(6.dp),
                        shadowElevation = 3.dp
                    ) {
                        Text(
                            if (directCylinderEditing) "Toque una cara; arrastre la flecha amarilla para editar"
                            else "Órbita: 1 dedo · paneo: 2 dedos · zoom: pellizco",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 9.sp,
                            color = Color(0xFF455A64)
                        )
                    }

                    if (!panelsVisible) {
                        Surface(
                            modifier = Modifier.align(Alignment.CenterStart).clickable { panelsVisible = true },
                            color = Color(0xFF2F668D),
                            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                        ) {
                            Text("▶", modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp), color = Color.White)
                        }
                    }

                    if (loading) {
                        Surface(modifier = Modifier.align(Alignment.Center), shape = RoundedCornerShape(8.dp), shadowElevation = 5.dp) {
                            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(25.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Recalculando BRep…", fontSize = 11.sp)
                            }
                        }
                    }

                    error?.let { message ->
                        Surface(
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).widthIn(max = 460.dp),
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

                if (showProperties) {
                    VerticalDivider(color = Color(0xFFB8C4CC))
                    FeatureProperties(
                        feature = selectedFeature,
                        document = document,
                        onEdit = { selectedFeature?.let { editingFeature = it } },
                        onSuppress = {
                            selectedFeature?.let { feature ->
                                rebuild(program.toggleSuppressed(feature.id), false, "Estado de ${feature.label} actualizado")
                            }
                        },
                        onDelete = {
                            selectedFeature?.let { feature ->
                                if (feature.id == program.features.firstOrNull()?.id) {
                                    error = "La operación base no se puede eliminar; cree una pieza nueva"
                                } else {
                                    rebuild(program.remove(feature.id), false, "${feature.label} eliminado")
                                }
                            }
                        },
                        modifier = Modifier.width(250.dp)
                    )
                }
            }

            if (operationsVisible) {
                OperationPalette(
                    onClose = { operationsVisible = false },
                    onSelect = { operation ->
                        operationsVisible = false
                        val next = program.append(operation)
                        rebuild(next, false, "${operation.label} añadido")
                    },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            editingFeature?.let { feature ->
                FeatureParameterEditor(
                    feature = feature,
                    onCancel = { editingFeature = null },
                    onAccept = { values ->
                        editingFeature = null
                        rebuild(program.updateFeature(feature.id, values), false, "${feature.label} recalculado")
                    },
                    onError = { error = it },
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun WorkbenchToolbar(
    documentName: String,
    loading: Boolean,
    panelsVisible: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onOperations: () -> Unit,
    onFit: () -> Unit,
    onTogglePanels: () -> Unit
) {
    Surface(color = Color(0xFFF1F3F5), shadowElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(58.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = Color(0xFFD32F2F), shape = RoundedCornerShape(4.dp)) {
                Text("SF", modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = Color.White, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(7.dp))
            Column(modifier = Modifier.widthIn(max = 165.dp)) {
                Text("SolidFreeCAD", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(documentName, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                ToolbarAction("Nuevo", !loading, onNew)
                ToolbarAction("Abrir", !loading, onOpen)
                ToolbarAction("Operaciones", !loading, onOperations)
                ToolbarAction("Encuadrar", true, onFit)
            }
            IconButton(onClick = onTogglePanels) {
                Icon(
                    if (panelsVisible) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                    if (panelsVisible) "Ocultar paneles" else "Mostrar paneles"
                )
            }
        }
    }
}

@Composable
private fun ToolbarAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(72.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (enabled) Color(0xFF456A7C) else Color(0xFFB0BEC5))
    }
}

@Composable
private fun FeatureProgramTree(
    document: WorkbenchDocument?,
    selectedFeatureId: Long?,
    onSelect: (Long) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxHeight(), color = Color(0xFFF8FAFB)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("FeatureManager", modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onAdd, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, "Añadir operación", modifier = Modifier.size(17.dp)) }
            }
            HorizontalDivider()
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(7.dp)) {
                Text(document?.name ?: "Sin documento", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("  ▾ Cuerpo1", fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
                Text("      ▸ Origen", fontSize = 8.sp, color = Color(0xFF607D8B))
                val features = document?.program?.features.orEmpty()
                if (features.isEmpty()) {
                    Text("      ${document?.format ?: "Geometría importada"}", fontSize = 8.sp, modifier = Modifier.padding(top = 6.dp))
                } else {
                    features.forEachIndexed { index, feature ->
                        val selected = feature.id == selectedFeatureId
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(if (selected) Color(0xFFD6ECFA) else Color.Transparent, RoundedCornerShape(4.dp))
                                .clickable { onSelect(feature.id) }
                                .padding(horizontal = 5.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (feature.suppressed) "○" else "◆", fontSize = 8.sp, color = if (feature.suppressed) Color.Gray else Color(0xFF2F668D))
                            Spacer(Modifier.width(5.dp))
                            Text(feature.label, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (index == 0) Text("          (-) Croquis1", fontSize = 7.sp, color = Color(0xFF546E7A))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureProperties(
    feature: BasicCadFeature?,
    document: WorkbenchDocument?,
    onEdit: () -> Unit,
    onSuppress: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxHeight(), color = Color(0xFFF8FAFB)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("PROPIEDADES", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            if (feature == null) {
                Text(document?.summary ?: "Seleccione una operación", fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            } else {
                Text(feature.label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(feature.operation.description, fontSize = 8.sp, color = Color(0xFF546E7A))
                Text(if (feature.operation.generalTopology) "Operación paramétrica" else "Operación básica beta", fontSize = 7.sp, color = if (feature.operation.generalTopology) Color(0xFF2E7D32) else Color(0xFFE65100))
                HorizontalDivider()
                feature.parameters.forEach { (name, value) ->
                    Text(name, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF78909C))
                    Text(formatNumber(value), fontSize = 10.sp)
                }
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Editar parámetros", fontSize = 9.sp) }
                TextButton(onClick = onSuppress, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (feature.suppressed) Icons.Default.Visibility else Icons.Default.VisibilityOff, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp)); Text(if (feature.suppressed) "Activar" else "Suprimir", fontSize = 9.sp)
                }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Eliminar", fontSize = 9.sp) }
            }
        }
    }
}

@Composable
private fun OperationPalette(
    onClose: () -> Unit,
    onSelect: (BasicCadOperation) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.widthIn(min = 420.dp, max = 650.dp).heightIn(max = 570.dp), shape = RoundedCornerShape(10.dp), shadowElevation = 10.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Operaciones básicas de pieza", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                IconButton(onClick = onClose) { Icon(Icons.Default.Cancel, "Cerrar") }
            }
            HorizontalDivider()
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicCadFeatureFamily.entries.forEach { family ->
                    Text(family.label.uppercase(Locale.ROOT), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF607D8B))
                    BasicCadOperation.entries.filter { it.family == family }.forEach { operation ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(operation) },
                            color = Color(0xFFF1F6F9),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(modifier = Modifier.padding(9.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(operation.label, modifier = Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    if (!operation.generalTopology) Text("BETA", fontSize = 7.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                                }
                                Text(operation.description, fontSize = 8.sp, color = Color(0xFF546E7A))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureParameterEditor(
    feature: BasicCadFeature,
    onCancel: () -> Unit,
    onAccept: (Map<String, Double>) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember(feature.id, feature.parameters) {
        mutableStateOf(feature.parameters.mapValues { formatNumber(it.value) })
    }
    Surface(modifier = modifier.widthIn(min = 360.dp, max = 470.dp).heightIn(max = 570.dp), shape = RoundedCornerShape(10.dp), shadowElevation = 12.dp) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(feature.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                IconButton(onClick = onCancel) { Icon(Icons.Default.Cancel, "Cancelar") }
            }
            HorizontalDivider()
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                draft.forEach { (key, value) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { next -> draft = draft + (key to numericText(next)) },
                        label = { Text(key) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text("Cancelar") }
                Button(onClick = {
                    val parsed = linkedMapOf<String, Double>()
                    for ((key, text) in draft) {
                        val value = text.toDoubleOrNull()
                        if (value == null || !value.isFinite()) {
                            onError("El parámetro $key no es válido")
                            return@Button
                        }
                        if (key !in setOf("dx", "dy", "dz") && value <= 0.0) {
                            onError("El parámetro $key debe ser mayor que cero")
                            return@Button
                        }
                        parsed[key] = value
                    }
                    onAccept(parsed)
                }) { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Recalcular") }
            }
        }
    }
}

private suspend fun buildProgramDocument(
    context: android.content.Context,
    program: BasicCadProgram,
    fitCamera: Boolean
): WorkbenchDocument = withContext(Dispatchers.IO) {
    val macro = BasicCadMacroGenerator.generate(program)
    val scene = SolidFreeCadMacroRuntime.execute(context, macro, linearDeflection = 0.18, angularDeflection = 0.24)
    WorkbenchDocument(
        name = "${program.documentName}.FCStd",
        format = "Modelo paramétrico",
        mesh = scene.mesh.toNativeMesh(),
        summary = buildString {
            appendLine("FreeCAD Base 1.1.1 / Runtime 0.11")
            appendLine(scene.pythonVersion)
            appendLine(scene.documentSummary)
            if (scene.output.isNotBlank()) append(scene.output)
        }.trimEnd(),
        program = program,
        token = System.nanoTime(),
        fitCamera = fitCamera
    )
}

private suspend fun loadExternalDocument(context: android.content.Context, uri: Uri): WorkbenchDocument =
    withContext(Dispatchers.IO) {
        val fallback = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.contains('.') } ?: "document.FCStd"
        val (file, name) = AndroidDocumentLoader.stage(context, uri, "solidfreecad-workbench", fallback)
        val extension = detectExtension(context, uri, file, name)
        when (extension) {
            "step", "stp" -> {
                val scene = NativeStepBridge.importStep(file.absolutePath, name)
                WorkbenchDocument(name, "STEP", scene.mesh.toNativeMesh(), scene.summary, null, System.nanoTime(), true)
            }
            "fcstd" -> {
                val archive = FreeCadArchiveReader.extract(file, File(context.cacheDir, "solidfreecad-fcstd-v011"), name)
                val scene = NativeFreeCadFileBridge.importFcStdObjects(archive.objects, name, archive.summary)
                WorkbenchDocument(name, "FCStd", scene.mesh.toNativeMesh(), scene.summary, null, System.nanoTime(), true)
            }
            "fcmacro", "py" -> {
                val bytes = file.readBytes()
                val scene = SolidFreeCadMacroRuntime.executeFile(context, bytes, linearDeflection = 0.18, angularDeflection = 0.24)
                WorkbenchDocument(
                    name = name,
                    format = "FCMacro",
                    mesh = scene.mesh.toNativeMesh(),
                    summary = buildString {
                        appendLine("Macro FreeCAD ejecutada")
                        appendLine(scene.pythonVersion)
                        appendLine(scene.documentSummary)
                        if (scene.output.isNotBlank()) appendLine(scene.output)
                    }.trimEnd(),
                    program = null,
                    token = System.nanoTime(),
                    fitCamera = true
                )
            }
            else -> error("Formato no compatible: .$extension. Use STEP, FCStd o FCMacro")
        }
    }

private fun detectExtension(context: android.content.Context, uri: Uri, file: File, name: String): String {
    val extension = AndroidDocumentLoader.extension(name)
    if (extension in setOf("step", "stp", "fcstd", "fcmacro", "py")) return extension
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
    if ("python" in mime || "x-fcmacro" in mime) return "fcmacro"
    val prefix = file.inputStream().buffered().use { input ->
        val bytes = ByteArray(4096)
        val count = input.read(bytes)
        if (count > 0) bytes.copyOf(count).toString(Charsets.UTF_8) else ""
    }
    if ("import FreeCAD" in prefix || "import Part" in prefix || "App.newDocument" in prefix) return "fcmacro"
    return extension
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
private fun formatNumber(value: Double): String = "%.4f".format(Locale.US, value).trimEnd('0').trimEnd('.')
