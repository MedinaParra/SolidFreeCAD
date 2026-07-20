package com.example.faceui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.BasicCadFeature
import com.example.features.CadReferencePlane
import com.example.features.CadReferencePlaneKind
import com.example.features.CadSketch
import kotlin.math.abs

@Composable
internal fun V23PropertiesPanel(
    selection: V23Selection,
    document: V23Document?,
    feature: BasicCadFeature?,
    sketch: CadSketch?,
    plane: CadReferencePlane?,
    enabled: Boolean,
    onClose: () -> Unit,
    onApplyFeature: (Map<String, Double>) -> Unit,
    onSuppress: () -> Unit,
    onDeleteFeature: () -> Unit,
    onEditSketch: (Long) -> Unit,
    onToggleSketch: (Long) -> Unit,
    onUpdatePlaneOffset: (Long, Double) -> Unit,
    onTogglePlane: (Long) -> Unit,
    onCreateSketch: (Long) -> Unit,
    modifier: Modifier
) {
    Surface(modifier.fillMaxHeight(), color = V23Panel, contentColor = V23Text, shadowElevation = 3.dp) {
        Column {
            Row(Modifier.fillMaxWidth().background(V23Header).padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("PROPIEDADES", Modifier.weight(1f), color = V23Text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.KeyboardArrowRight, "Ocultar", tint = V23Accent) }
            }
            HorizontalDivider(color = V23Divider)
            when (selection.kind) {
                V23NodeKind.FEATURE -> V23FeatureProperties(feature, enabled, onApplyFeature, onSuppress, onDeleteFeature, Modifier.fillMaxSize())
                V23NodeKind.SKETCH -> V23SketchProperties(sketch, enabled, onEditSketch, onToggleSketch, Modifier.fillMaxSize())
                V23NodeKind.PLANE -> V23PlaneProperties(plane, enabled, onUpdatePlaneOffset, onTogglePlane, onCreateSketch, Modifier.fillMaxSize())
                V23NodeKind.BODY, V23NodeKind.IMPORTED -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(document?.name ?: "Sin documento", color = V23Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(document?.format ?: "—", color = V23Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text(document?.summary ?: "Seleccione un elemento", color = V23Secondary, fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable
internal fun V23FeatureProperties(
    feature: BasicCadFeature?, enabled: Boolean, onApply: (Map<String, Double>) -> Unit,
    onSuppress: () -> Unit, onDelete: () -> Unit, modifier: Modifier
) {
    if (feature == null) { Text("Seleccione una operación", modifier.padding(12.dp), color = V23Secondary, fontSize = 9.sp); return }
    val focusManager = LocalFocusManager.current
    var draft by remember(feature.id, feature.parameters) { mutableStateOf(feature.parameters.mapValues { v23Number(it.value) }) }
    val validation = remember(draft) { v23ValidateParameters(draft) }
    val changed = remember(draft, feature.parameters) { validation.error == null && !v23MapsEqual(validation.values, feature.parameters) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(feature.label, color = V23Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(feature.operation.description, color = V23Secondary, fontSize = 8.sp)
        feature.sketchId?.let { Text("Croquis asociado: $it", color = V23Muted, fontSize = 7.sp) }
        feature.planeId?.let { Text("Plano asociado: $it", color = V23Muted, fontSize = 7.sp) }
        Text(
            if (feature.operation.generalTopology) "OPERACIÓN PARAMÉTRICA" else "OPERACIÓN BÁSICA BETA",
            color = if (feature.operation.generalTopology) Color(0xFF1B6E32) else Color(0xFFB54708), fontSize = 7.sp, fontWeight = FontWeight.Bold
        )
        HorizontalDivider(color = V23Divider)
        draft.forEach { (key, value) ->
            OutlinedTextField(
                value = value, onValueChange = { draft = draft + (key to v23Numeric(it)) }, enabled = enabled,
                label = { Text(key, color = V23Secondary) },
                textStyle = LocalTextStyle.current.copy(color = V23Text, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }), singleLine = true,
                isError = validation.error != null, modifier = Modifier.fillMaxWidth()
            )
        }
        validation.error?.let { Text(it, color = Color(0xFFB42318), fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        if (changed) Text("Cambios sin aplicar", color = Color(0xFF8A4B08), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Button(
            onClick = { focusManager.clearFocus(); onApply(validation.values) }, enabled = enabled && changed,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp)
        ) { Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text("Aplicar y recalcular", fontSize = 9.sp) }
        TextButton(onClick = onSuppress, enabled = enabled, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)) {
            Icon(if (feature.suppressed) Icons.Default.Visibility else Icons.Default.VisibilityOff, null); Spacer(Modifier.size(5.dp));
            Text(if (feature.suppressed) "Activar" else "Suprimir", color = V23Accent)
        }
        TextButton(onClick = onDelete, enabled = enabled, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color(0xFFB42318)); Spacer(Modifier.size(5.dp)); Text("Eliminar", color = Color(0xFFB42318))
        }
    }
}

@Composable
internal fun V23SketchProperties(sketch: CadSketch?, enabled: Boolean, onEdit: (Long) -> Unit, onToggle: (Long) -> Unit, modifier: Modifier) {
    if (sketch == null) { Text("Seleccione un croquis", modifier.padding(12.dp), color = V23Secondary, fontSize = 9.sp); return }
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(sketch.label, color = V23Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(sketch.profileType.label, color = V23Accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text("Plano: ${sketch.planeId}", color = V23Secondary, fontSize = 8.sp)
        Text(if (sketch.fullyDefined) "TOTALMENTE DEFINIDO" else "SUBDEFINIDO", color = if (sketch.fullyDefined) Color(0xFF1B6E32) else Color(0xFFB54708), fontSize = 7.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider(color = V23Divider)
        sketch.parameters.forEach { (name, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, color = V23Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold); Text(v23Number(value), color = V23Text, fontSize = 9.sp)
            }
        }
        Button(onClick = { onEdit(sketch.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp)) { Text("Editar croquis", fontSize = 9.sp) }
        TextButton(onClick = { onToggle(sketch.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)) {
            Icon(if (sketch.visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null); Spacer(Modifier.size(5.dp));
            Text(if (sketch.visible) "Ocultar" else "Mostrar", color = V23Accent)
        }
    }
}

@Composable
internal fun V23PlaneProperties(
    plane: CadReferencePlane?, enabled: Boolean, onUpdateOffset: (Long, Double) -> Unit,
    onToggle: (Long) -> Unit, onCreateSketch: (Long) -> Unit, modifier: Modifier
) {
    if (plane == null) { Text("Seleccione un plano", modifier.padding(12.dp), color = V23Secondary, fontSize = 9.sp); return }
    val focusManager = LocalFocusManager.current
    var offsetText by remember(plane.id, plane.offsetMm) { mutableStateOf(v23Number(plane.offsetMm)) }
    val parsedOffset = offsetText.toDoubleOrNull()
    val canApply = plane.kind != CadReferencePlaneKind.PRINCIPAL && parsedOffset != null && parsedOffset.isFinite() && abs(parsedOffset - plane.offsetMm) > 1.0e-8
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(plane.label, color = V23Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            when (plane.kind) {
                CadReferencePlaneKind.PRINCIPAL -> "PLANO CARTESIANO PRINCIPAL"
                CadReferencePlaneKind.OFFSET -> "PLANO PARALELO DESPLAZADO"
                CadReferencePlaneKind.FACE_PARALLEL -> "PLANO PARALELO/TANGENTE A CARA"
            }, color = V23Accent, fontSize = 7.sp, fontWeight = FontWeight.Bold
        )
        plane.sourcePlaneId?.let { Text("Referencia: Plano $it", color = V23Secondary, fontSize = 8.sp) }
        plane.sourceFaceReference?.let { Text("Referencia: $it", color = V23Secondary, fontSize = 8.sp) }
        Text("Origen: ${v23Vector(plane.origin)}", color = V23Secondary, fontSize = 8.sp)
        Text("Normal: ${v23Vector(plane.normal)}", color = V23Secondary, fontSize = 8.sp)
        HorizontalDivider(color = V23Divider)
        if (plane.kind != CadReferencePlaneKind.PRINCIPAL) {
            OutlinedTextField(
                value = offsetText, onValueChange = { offsetText = v23Numeric(it) }, enabled = enabled,
                label = { Text("Separación / desfase (mm)", color = V23Secondary) },
                textStyle = LocalTextStyle.current.copy(color = V23Text, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }), singleLine = true,
                isError = parsedOffset == null, modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { parsedOffset?.let { focusManager.clearFocus(); onUpdateOffset(plane.id, it) } }, enabled = enabled && canApply,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp)
            ) { Text("Actualizar plano", fontSize = 9.sp) }
        }
        Button(onClick = { onCreateSketch(plane.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 46.dp)) {
            Text("Crear croquis en este plano", fontSize = 9.sp)
        }
        TextButton(onClick = { onToggle(plane.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)) {
            Icon(if (plane.visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null); Spacer(Modifier.size(5.dp));
            Text(if (plane.visible) "Ocultar plano" else "Mostrar plano", color = V23Accent)
        }
    }
}
