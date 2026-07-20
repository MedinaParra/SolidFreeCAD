package com.example.faceui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.BasicCadFeature
import com.example.features.BasicCadProgram
import com.example.features.CadPlaneKind
import com.example.features.CadReferencePlane
import com.example.features.CadSketchDefinition

@Composable
internal fun V23PropertiesPanel(
    selectionType: V23SelectionType,
    feature: BasicCadFeature?,
    plane: CadReferencePlane?,
    sketch: CadSketchDefinition?,
    document: V23Document?,
    program: BasicCadProgram?,
    enabled: Boolean,
    onClose: () -> Unit,
    onApplyFeature: (Map<String, Double>) -> Unit,
    onSuppressFeature: () -> Unit,
    onDeleteFeature: () -> Unit,
    onEditLinkedSketch: (Long) -> Unit,
    onApplyPlane: (Double, Boolean) -> Unit,
    onDeletePlane: () -> Unit,
    onEditSketch: (Long) -> Unit,
    onToggleSketch: () -> Unit,
    onDeleteSketch: () -> Unit,
    modifier: Modifier
) {
    Surface(modifier.fillMaxHeight(), color = V23Panel, contentColor = V23Text, shadowElevation = 3.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(V23Header).padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PROPIEDADES", Modifier.weight(1f), color = V23Text, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.KeyboardArrowRight, "Ocultar", tint = V23Accent)
                }
            }
            HorizontalDivider(color = V23Divider)
            when (selectionType) {
                V23SelectionType.FEATURE -> V23FeatureProperties(
                    feature, program, enabled, onApplyFeature, onSuppressFeature,
                    onDeleteFeature, onEditLinkedSketch, Modifier.fillMaxSize()
                )
                V23SelectionType.PLANE -> V23PlaneProperties(
                    plane, program, enabled, onApplyPlane, onDeletePlane, Modifier.fillMaxSize()
                )
                V23SelectionType.SKETCH -> V23SketchProperties(
                    sketch, program, enabled, onEditSketch, onToggleSketch,
                    onDeleteSketch, Modifier.fillMaxSize()
                )
                V23SelectionType.DOCUMENT -> Text(
                    document?.summary ?: "Seleccione un elemento",
                    Modifier.padding(12.dp), color = V23Secondary, fontSize = 8.sp
                )
            }
        }
    }
}

@Composable
private fun V23FeatureProperties(
    feature: BasicCadFeature?,
    program: BasicCadProgram?,
    enabled: Boolean,
    onApply: (Map<String, Double>) -> Unit,
    onSuppress: () -> Unit,
    onDelete: () -> Unit,
    onEditLinkedSketch: (Long) -> Unit,
    modifier: Modifier
) {
    if (feature == null) {
        Text("Seleccione una operación", modifier.padding(12.dp), color = V23Secondary, fontSize = 9.sp)
        return
    }
    var draft by remember(feature.id, feature.parameters) {
        mutableStateOf(feature.parameters.mapValues { v23Number(it.value) })
    }
    val validation = remember(draft) { v23ValidateParameters(draft) }
    val changed = validation.error == null && !v23MapsEqual(validation.values, feature.parameters)
    Column(modifier.verticalScroll(rememberScrollState()).padding(11.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(feature.label, color = V23Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(feature.operation.description, color = V23Secondary, fontSize = 8.sp, lineHeight = 11.sp)
        feature.sketchId?.let { sketchId ->
            val label = program?.sketches?.firstOrNull { it.id == sketchId }?.label ?: "Croquis"
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onEditLinkedSketch(sketchId) },
                color = Color(0xFFE9F2F8), shape = RoundedCornerShape(6.dp)
            ) {
                Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, null, tint = V23Accent)
                    Spacer(Modifier.width(6.dp))
                    Text("Editar $label", color = V23Accent, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
        }
        HorizontalDivider(color = V23Divider)
        draft.forEach { (key, value) ->
            OutlinedTextField(
                value = value,
                onValueChange = { draft = draft + (key to v23Numeric(it)) },
                label = { Text(v23ParameterLabel(key), color = V23Secondary) },
                textStyle = LocalTextStyle.current.copy(color = V23Text, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth()
            )
        }
        validation.error?.let {
            Text(it, color = Color(0xFFB42318), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        if (changed) Text("Cambios sin aplicar", color = Color(0xFF9A4A04), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Button(
            onClick = { onApply(validation.values) },
            enabled = enabled && changed,
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
        ) {
            Icon(Icons.Default.Check, null); Spacer(Modifier.width(5.dp)); Text("Aplicar y recalcular")
        }
        TextButton(onClick = onSuppress, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            Icon(if (feature.suppressed) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
            Spacer(Modifier.width(5.dp)); Text(if (feature.suppressed) "Activar" else "Suprimir", color = V23Accent)
        }
        TextButton(onClick = onDelete, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color(0xFFB42318)); Spacer(Modifier.width(5.dp)); Text("Eliminar", color = Color(0xFFB42318))
        }
    }
}

@Composable
private fun V23PlaneProperties(
    plane: CadReferencePlane?,
    program: BasicCadProgram?,
    enabled: Boolean,
    onApply: (Double, Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier
) {
    if (plane == null) {
        Text("Seleccione un plano", modifier.padding(12.dp), color = V23Secondary, fontSize = 9.sp)
        return
    }
    var offsetText by remember(plane.id, plane.offsetMm) { mutableStateOf(v23Number(plane.offsetMm)) }
    var visible by remember(plane.id, plane.visible) { mutableStateOf(plane.visible) }
    val offset = offsetText.toDoubleOrNull()
    val resolved = program?.let { runCatching { it.resolvePlane(plane.id) }.getOrNull() }
    Column(modifier.verticalScroll(rememberScrollState()).padding(11.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(plane.label, color = V23Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(
            when (plane.kind) {
                CadPlaneKind.BASE -> "Plano cartesiano base"
                CadPlaneKind.OFFSET -> "Paralelo a ${program?.planes?.firstOrNull { it.id == plane.parentPlaneId }?.label ?: "plano"}"
                CadPlaneKind.FACE_PARALLEL -> "Paralelo a ${plane.supportFaceLabel ?: "cara plana"}"
            }, color = V23Secondary, fontSize = 8.sp
        )
        resolved?.let {
            Text("Origen: ${v23Vector(it.origin)}", color = V23Secondary, fontSize = 8.sp)
            Text("Normal: ${v23Vector(it.normal)}", color = V23Secondary, fontSize = 8.sp)
        }
        HorizontalDivider(color = V23Divider)
        if (plane.kind != CadPlaneKind.BASE) {
            OutlinedTextField(
                value = offsetText,
                onValueChange = { offsetText = v23Numeric(it) },
                label = { Text("Separación", color = V23Secondary) },
                suffix = { Text("mm") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth()
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Visible en el visor", Modifier.weight(1f), color = V23Text, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            Switch(checked = visible, onCheckedChange = { visible = it }, enabled = enabled)
        }
        Button(
            onClick = { onApply(offset ?: plane.offsetMm, visible) },
            enabled = enabled && offset != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
        ) {
            Icon(Icons.Default.Check, null); Spacer(Modifier.width(5.dp)); Text("Aplicar")
        }
        if (plane.kind != CadPlaneKind.BASE) {
            TextButton(onClick = onDelete, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                Icon(Icons.Default.Delete, null, tint = Color(0xFFB42318)); Spacer(Modifier.width(5.dp)); Text("Eliminar plano", color = Color(0xFFB42318))
            }
        }
    }
}

@Composable
private fun V23SketchProperties(
    sketch: CadSketchDefinition?,
    program: BasicCadProgram?,
    enabled: Boolean,
    onEdit: (Long) -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier
) {
    if (sketch == null) {
        Text("Seleccione un croquis", modifier.padding(12.dp), color = V23Secondary, fontSize = 9.sp)
        return
    }
    val plane = program?.planes?.firstOrNull { it.id == sketch.planeId }
    val usedBy = program?.features.orEmpty().filter { it.sketchId == sketch.id }
    Column(modifier.verticalScroll(rememberScrollState()).padding(11.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(sketch.label, color = V23Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("${sketch.profile.label} sobre ${plane?.label ?: "plano"}", color = V23Secondary, fontSize = 8.sp)
        sketch.parameters.forEach { (key, value) ->
            Row(Modifier.fillMaxWidth()) {
                Text(v23ParameterLabel(key), Modifier.weight(1f), color = V23Secondary, fontSize = 8.sp)
                Text("${v23Number(value)} mm", color = V23Text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (usedBy.isNotEmpty()) Text("Usado por: ${usedBy.joinToString { it.label }}", color = V23Secondary, fontSize = 8.sp)
        Button(onClick = { onEdit(sketch.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)) {
            Icon(Icons.Default.Edit, null); Spacer(Modifier.width(5.dp)); Text("Editar en el entorno gráfico")
        }
        TextButton(onClick = onToggle, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            Icon(if (sketch.visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
            Spacer(Modifier.width(5.dp)); Text(if (sketch.visible) "Ocultar croquis" else "Mostrar croquis", color = V23Accent)
        }
        TextButton(onClick = onDelete, enabled = enabled && usedBy.isEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color(0xFFB42318)); Spacer(Modifier.width(5.dp)); Text("Eliminar croquis", color = Color(0xFFB42318))
        }
    }
}
