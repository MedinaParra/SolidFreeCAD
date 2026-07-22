package com.example.faceui

import android.content.Intent
import android.content.Context
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
import com.example.industrial.IndustrialCadDiagnostics
import com.example.industrial.IndustrialCadRecoveryStore
import com.example.industrial.IndustrialWorkshopPreferences
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


@Composable
internal fun V24BottomTray(tab: V24TrayTab, program: BasicCadProgram?, enabled: Boolean, onTab: (V24TrayTab) -> Unit, onClose: () -> Unit, onCreateSketch: (CadSketchPrimitiveKind) -> Unit, onCreatePlane: () -> Unit, onOperation: (BasicCadOperation) -> Unit) {
    Surface(Modifier.fillMaxWidth(0.92f).heightIn(min = 126.dp, max = 185.dp), color = V24Panel, contentColor = V24Text, shape = RoundedCornerShape(12.dp), shadowElevation = 10.dp) {
        Column {
            Row(Modifier.fillMaxWidth().background(V24Header).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                V24TrayTab.entries.forEach { item -> Text(item.label, Modifier.clickable { onTab(item) }.background(if (item == tab) Color.White else Color.Transparent, RoundedCornerShape(5.dp)).heightIn(min = 48.dp).padding(horizontal = 13.dp, vertical = 12.dp), color = if (item == tab) V24Accent else V24Secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f)); IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Ocultar") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    V24TrayTab.SKETCH -> {
                        V24Tile("Plano", "Nuevo plano", enabled, onCreatePlane)
                        V24Tile("Círculo", "Croquis circular", enabled) { onCreateSketch(CadSketchPrimitiveKind.CIRCLE) }
                        V24Tile("Rectángulo", "Croquis rectangular", enabled) { onCreateSketch(CadSketchPrimitiveKind.RECTANGLE) }
                        V24Tile("Línea", "Segmento", enabled) { onCreateSketch(CadSketchPrimitiveKind.LINE) }
                        V24Tile("Arco", "Arco", enabled) { onCreateSketch(CadSketchPrimitiveKind.ARC) }
                        V24Tile("Polígono", "Polígono", enabled) { onCreateSketch(CadSketchPrimitiveKind.POLYGON) }
                    }
                    else -> v24OperationsFor(tab).forEach { op -> V24Tile(op.label, op.description, enabled && (!op.requiresSketch || program?.activeSketchId != null)) { onOperation(op) } }
                }
            }
        }
    }
}

internal fun v24OperationsFor(tab: V24TrayTab): List<BasicCadOperation> = when (tab) {
    V24TrayTab.MATERIAL -> listOf(BasicCadOperation.BOSS_EXTRUDE, BasicCadOperation.BOSS_REVOLVE, BasicCadOperation.BOSS_SWEEP, BasicCadOperation.BOSS_LOFT, BasicCadOperation.RIB)
    V24TrayTab.CUT -> listOf(BasicCadOperation.CUT_EXTRUDE, BasicCadOperation.CUT_REVOLVE, BasicCadOperation.CUT_SWEEP, BasicCadOperation.CUT_LOFT, BasicCadOperation.SIMPLE_HOLE, BasicCadOperation.COUNTERBORE_HOLE, BasicCadOperation.COUNTERSINK_HOLE)
    V24TrayTab.FINISH -> listOf(BasicCadOperation.FILLET, BasicCadOperation.CHAMFER, BasicCadOperation.SHELL, BasicCadOperation.DRAFT)
    V24TrayTab.PATTERN -> listOf(BasicCadOperation.LINEAR_PATTERN, BasicCadOperation.CIRCULAR_PATTERN, BasicCadOperation.MIRROR, BasicCadOperation.MOVE_COPY_BODY)
    V24TrayTab.BODY -> listOf(BasicCadOperation.COMBINE_ADD, BasicCadOperation.COMBINE_SUBTRACT, BasicCadOperation.COMBINE_COMMON)
    V24TrayTab.SKETCH -> emptyList()
}

@Composable internal fun V24Tile(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val gloveMode = LocalV24GloveMode.current
    Surface(Modifier.width(if (gloveMode) 156.dp else 132.dp).height(if (gloveMode) 98.dp else 82.dp).clickable(enabled = enabled, onClick = onClick), color = if (enabled) Color(0xFFEDF3F7) else Color(0xFFE2E8F0), shape = RoundedCornerShape(8.dp), shadowElevation = 1.dp) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.Center) { Text(title, color = if (enabled) V24Text else Color(0xFF94A3B8), fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2); Spacer(Modifier.height(4.dp)); Text(subtitle, color = if (enabled) V24Secondary else Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
internal fun V24SketchEditor(sketch: CadSketch, onCancel: () -> Unit, onAccept: (Map<String, Double>) -> Unit, modifier: Modifier) {
    val primitive = sketch.primitives.firstOrNull() ?: return
    var draft by remember(sketch.id, primitive.parameters) { mutableStateOf(primitive.parameters.mapValues { v24Number(it.value) }) }
    Surface(modifier, color = Color(0xFFF7FAFC).copy(alpha = 0.97f), contentColor = V24Text) {
        Column {
            Row(Modifier.fillMaxWidth().background(V24Header).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    Text("Las cotas modifican el croquis y las operaciones dependientes al aceptar.", color = V24Secondary, fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable
internal fun V24PlaneDialog(mode: V24PlaneDialogMode, program: BasicCadProgram, selectedFace: EditableCadFace, onCancel: () -> Unit, onCreate: (Long, Double, String?) -> Unit) {
    var parentId by remember { mutableStateOf(program.activePlaneId) }
    var offset by remember { mutableStateOf("10") }
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (mode == V24PlaneDialogMode.OFFSET) "Crear plano paralelo" else "Plano paralelo a cara") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (mode == V24PlaneDialogMode.OFFSET) {
                    Text("Plano de referencia", fontWeight = FontWeight.Bold)
                    program.planeSet.planes.forEach { plane -> Row(Modifier.fillMaxWidth().clickable { parentId = plane.id }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(parentId == plane.id, { parentId = plane.id }); Text(plane.label) } }
                } else Text(if (selectedFace == EditableCadFace.TOP) "Cara plana superior seleccionada" else "Seleccione primero una cara plana en el visor", color = if (selectedFace == EditableCadFace.TOP) Color(0xFF1B6E32) else Color(0xFFB42318))
                OutlinedTextField(offset, { offset = it.filter { c -> c.isDigit() || c in ".,-" }.replace(',', '.') }, label = { Text("Desplazamiento (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(label, { label = it }, label = { Text("Nombre opcional") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { val value = offset.toDoubleOrNull() ?: return@Button; onCreate(parentId, value, label.takeIf { it.isNotBlank() }) }, enabled = mode == V24PlaneDialogMode.OFFSET || selectedFace == EditableCadFace.TOP) { Text("Crear") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
    )
}

@Composable internal fun V24EdgeHandle(label: String, arrow: String, onClick: () -> Unit, modifier: Modifier, right: Boolean) { val gloveMode = LocalV24GloveMode.current; Surface(modifier.clickable(onClick = onClick), color = V24Accent, contentColor = Color.White, shape = if (right) RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp) else RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp), shadowElevation = 4.dp) { Column(Modifier.padding(horizontal = if (gloveMode) 11.dp else 7.dp, vertical = if (gloveMode) 22.dp else 15.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(arrow, fontWeight = FontWeight.Bold, fontSize = if (gloveMode) 18.sp else 14.sp); Text(label, fontSize = if (gloveMode) 9.sp else 7.sp, fontWeight = FontWeight.Bold) } } }
@Composable internal fun V24Loading(modifier: Modifier) { Surface(modifier.widthIn(min = 230.dp), color = Color.White, shape = RoundedCornerShape(8.dp), shadowElevation = 5.dp) { Column(Modifier.padding(12.dp)) { Text("FreeCAD está recalculando el BRep…", fontWeight = FontWeight.Bold, fontSize = 9.sp); Spacer(Modifier.height(7.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) } } }
@Composable internal fun V24Error(message: String, onClose: () -> Unit, modifier: Modifier) { Surface(modifier.widthIn(max = 460.dp), color = Color(0xFFFFEBEE), contentColor = Color(0xFF5F1414), shape = RoundedCornerShape(8.dp), shadowElevation = 5.dp) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Error, null, tint = Color(0xFFC62828)); Spacer(Modifier.width(7.dp)); Text(message, Modifier.weight(1f), fontSize = 9.sp); IconButton(onClick = onClose) { Icon(Icons.Default.Cancel, "Cerrar") } } } }
@Composable internal fun V24StatusBar(status: String, loading: Boolean, format: String, count: Int?) { Surface(color = Color(0xFFE9EEF2), contentColor = V24Text, shadowElevation = 2.dp) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Text(status, Modifier.weight(1f), fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis); if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("$format${count?.let { " · $it operaciones" } ?: ""}", color = V24Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold) } } }
