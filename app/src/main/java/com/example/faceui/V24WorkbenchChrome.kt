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
internal fun V24TopBar(documentName: String, loading: Boolean, canUndo: Boolean, canRedo: Boolean, onNew: () -> Unit, onOpen: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onFit: () -> Unit, onPreset: (CameraPreset) -> Unit, onToggleTree: () -> Unit, onToggleProperties: () -> Unit, onToggleTray: () -> Unit, onWorkshop: () -> Unit) {
    val gloveMode = LocalV24GloveMode.current
    Surface(color = Color(0xFFF1F3F5), contentColor = V24Text, shadowElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(if (gloveMode) 72.dp else 60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFD32F2F), shape = RoundedCornerShape(4.dp)) { Text("SF", Modifier.padding(horizontal = 7.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.widthIn(max = 150.dp)) { Text("SolidFreeCAD", fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(documentName, color = V24Secondary, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                V24Action("Nuevo", Icons.Default.NoteAdd, !loading, onNew)
                V24Action("Abrir", Icons.Default.FolderOpen, !loading, onOpen)
                V24Action("Deshacer", Icons.Default.Undo, canUndo && !loading, onUndo)
                V24Action("Rehacer", Icons.Default.Redo, canRedo && !loading, onRedo)
                V24Action("ISO", Icons.Default.ViewInAr, true) { onPreset(CameraPreset.ISOMETRIC) }
                V24Action("Frente", Icons.Default.CropSquare, true) { onPreset(CameraPreset.FRONT) }
                V24Action("Planta", Icons.Default.Layers, true) { onPreset(CameraPreset.TOP) }
                V24Action("Encuadrar", Icons.Default.FilterCenterFocus, true, onFit)
                V24Action("Árbol", Icons.Default.AccountTree, true, onToggleTree)
                V24Action("Prop.", Icons.Default.Tune, true, onToggleProperties)
                V24Action("Bandeja", Icons.Default.KeyboardArrowUp, true, onToggleTray)
                V24Action("Taller", Icons.Default.HealthAndSafety, true, onWorkshop)
            }
        }
    }
}

@Composable internal fun V24Action(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val gloveMode = LocalV24GloveMode.current
    Column(
        Modifier.width(if (gloveMode) 78.dp else 62.dp)
            .heightIn(min = if (gloveMode) 60.dp else 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = if (gloveMode) 7.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = if (enabled) V24Accent else Color(0xFF94A3B8), modifier = Modifier.size(if (gloveMode) 27.dp else 20.dp))
        Text(label, color = if (enabled) V24Text else Color(0xFF94A3B8), fontSize = if (gloveMode) 9.sp else 7.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun V24Tree(program: BasicCadProgram?, selection: V24Selection, enabled: Boolean, onSelect: (V24Selection) -> Unit, onCreatePlane: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxHeight(), color = V24Panel, contentColor = V24Text) {
        Column {
            Row(Modifier.fillMaxWidth().background(V24Header).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("FeatureManager", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                IconButton(onClick = onCreatePlane, enabled = enabled, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.GridOn, "Crear plano", tint = V24Accent) }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowLeft, "Ocultar", tint = V24Accent) }
            }
            HorizontalDivider(color = V24Divider)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
                Text(program?.documentName?.plus(".FCStd") ?: "Documento importado", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("  ▾ Cuerpo1", fontWeight = FontWeight.Medium, fontSize = 9.sp, modifier = Modifier.padding(top = 5.dp))
                Text("      ▾ Origen", color = V24Secondary, fontSize = 8.sp)
                program?.planeSet?.planes?.forEach { plane ->
                    V24TreeRow("          ◫ ${plane.label}", selection == V24Selection(V24SelectionType.PLANE, plane.id), enabled) { onSelect(V24Selection(V24SelectionType.PLANE, plane.id)) }
                }
                program?.sketches?.forEach { sketch ->
                    V24TreeRow("      ✎ ${sketch.label}", selection == V24Selection(V24SelectionType.SKETCH, sketch.id), enabled) { onSelect(V24Selection(V24SelectionType.SKETCH, sketch.id)) }
                }
                program?.features?.forEach { feature ->
                    V24TreeRow("      ${if (feature.suppressed) "○" else "◆"} ${feature.label}", selection == V24Selection(V24SelectionType.FEATURE, feature.id), enabled) { onSelect(V24Selection(V24SelectionType.FEATURE, feature.id)) }
                    feature.sketchId?.let { sketchId -> program.sketches.firstOrNull { it.id == sketchId }?.let { Text("          └ ${it.label}", color = V24Secondary, fontSize = 7.sp) } }
                }
            }
        }
    }
}

@Composable internal fun V24TreeRow(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Text(text, Modifier.fillMaxWidth().background(if (selected) V24Selected else Color.Transparent, RoundedCornerShape(4.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 5.dp, vertical = 7.dp), color = V24Text, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
}

@Composable
internal fun V24Properties(feature: BasicCadFeature?, plane: CadReferencePlane?, sketch: CadSketch?, program: BasicCadProgram?, enabled: Boolean, onApplyFeature: (BasicCadFeature, Map<String, Double>) -> Unit, onToggleFeature: (BasicCadFeature) -> Unit, onEditSketch: (CadSketch) -> Unit, onSelectPlane: (Long) -> Unit, onTogglePlane: (Long) -> Unit, onCreateOffset: () -> Unit, onCreateFace: () -> Unit, onClose: () -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxHeight(), color = V24Panel, contentColor = V24Text) {
        Column {
            Row(Modifier.fillMaxWidth().background(V24Header).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("PROPIEDADES", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowRight, "Ocultar", tint = V24Accent) }
            }
            HorizontalDivider(color = V24Divider)
            when {
                feature != null -> V24FeatureProperties(feature, enabled, onApplyFeature, onToggleFeature, Modifier.fillMaxSize())
                plane != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(plane.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Origen: ${v24Vec(plane.origin)}", color = V24Secondary, fontSize = 9.sp)
                    Text("Normal: ${v24Vec(plane.normalizedNormal)}", color = V24Secondary, fontSize = 9.sp)
                    Text("Desplazamiento: ${v24Mm(plane.offset)}", color = V24Secondary, fontSize = 9.sp)
                    Button(onClick = { onSelectPlane(plane.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Usar como plano activo") }
                    OutlinedButton(onClick = { onTogglePlane(plane.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Icon(if (plane.visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null); Spacer(Modifier.width(5.dp)); Text(if (plane.visible) "Ocultar plano" else "Mostrar plano") }
                    Button(onClick = onCreateOffset, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(5.dp)); Text("Crear plano paralelo") }
                }
                sketch != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sketch.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Plano: ${program?.planeSet?.plane(sketch.planeId)?.label}", color = V24Secondary)
                    Text("Entidades: ${sketch.primitives.size}", color = V24Secondary)
                    Button(onClick = { onEditSketch(sketch) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(5.dp)); Text("Editar croquis") }
                }
                else -> Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seleccione una operación, croquis o plano.", color = V24Secondary)
                    Button(onClick = onCreateOffset, enabled = enabled) { Text("Crear plano paralelo") }
                    OutlinedButton(onClick = onCreateFace, enabled = enabled) { Text("Plano paralelo a cara") }
                }
            }
        }
    }
}

@Composable
internal fun V24FeatureProperties(feature: BasicCadFeature, enabled: Boolean, onApply: (BasicCadFeature, Map<String, Double>) -> Unit, onToggle: (BasicCadFeature) -> Unit, modifier: Modifier) {
    var draft by remember(feature.id, feature.parameters) { mutableStateOf(feature.parameters.mapValues { v24Number(it.value) }) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(feature.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(feature.operation.description, color = V24Secondary, fontSize = 9.sp)
        feature.parameters.forEach { (key, _) ->
            OutlinedTextField(draft[key].orEmpty(), { text -> draft = draft + (key to text.filter { it.isDigit() || it in ".,-" }.replace(',', '.')) }, label = { Text(key) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = { val parsed = draft.mapValues { it.value.toDoubleOrNull() ?: return@Button }; onApply(feature, parsed) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(5.dp)); Text("Aplicar y recalcular") }
        OutlinedButton(onClick = { onToggle(feature) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(if (feature.suppressed) "Activar" else "Suprimir") }
    }
}
