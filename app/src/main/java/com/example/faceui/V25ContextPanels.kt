package com.example.faceui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.BasicCadFeature
import com.example.features.BasicCadProgram
import com.example.features.CadReferencePlane
import com.example.features.CadSketch
import java.util.Locale

@Composable
internal fun V25Properties(
    surface: CadViewportFaceSelection?,
    feature: BasicCadFeature?,
    plane: CadReferencePlane?,
    sketch: CadSketch?,
    program: BasicCadProgram?,
    enabled: Boolean,
    onApplyFeature: (BasicCadFeature, Map<String, Double>) -> Unit,
    onToggleFeature: (BasicCadFeature) -> Unit,
    onEditSketch: (CadSketch) -> Unit,
    onSelectPlane: (Long) -> Unit,
    onTogglePlane: (Long) -> Unit,
    onCreateOffset: () -> Unit,
    onCreateFace: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    Surface(modifier.fillMaxHeight(), color = V24Panel, contentColor = V24Text) {
        Column {
            Row(Modifier.fillMaxWidth().background(V24Header).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("PROPIEDADES", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.KeyboardArrowRight, "Ocultar", tint = V24Accent) }
            }
            HorizontalDivider(color = V24Divider)
            when {
                surface != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (surface.planar) "Cara plana" else "Superficie curva", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("ID: ${surface.id}", color = V24Secondary, fontSize = 8.sp)
                    Text("Normal: ${v25Normal(surface.normal)}", color = V24Secondary, fontSize = 9.sp)
                    Text("Triángulos conectados: ${surface.triangleOrdinals.size}", color = V24Secondary, fontSize = 9.sp)
                    if (surface.planar) {
                        Button(onClick = onCreateFace, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.ContentCopy, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Plano paralelo a esta cara")
                        }
                        Text("Para crear un croquis coincidente, mantenga seleccionada esta cara y elija una entidad en la pestaña Croquis de la bandeja inferior.", color = V24Secondary, fontSize = 8.sp)
                    } else {
                        Text("Esta superficie ya está disponible para operaciones contextuales. Los planos de referencia solo se crean desde caras planas.", color = V24Secondary, fontSize = 8.sp)
                    }
                }
                feature != null -> V24FeatureProperties(feature, enabled, onApplyFeature, onToggleFeature, Modifier.fillMaxSize())
                plane != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(plane.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Origen: ${v24Vec(plane.origin)}", color = V24Secondary, fontSize = 9.sp)
                    Text("Normal: ${v24Vec(plane.normalizedNormal)}", color = V24Secondary, fontSize = 9.sp)
                    Text("Desplazamiento: ${v24Mm(plane.offset)}", color = V24Secondary, fontSize = 9.sp)
                    Button(onClick = { onSelectPlane(plane.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Usar como plano activo") }
                    OutlinedButton(onClick = { onTogglePlane(plane.id) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (plane.visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        Spacer(Modifier.width(5.dp))
                        Text(if (plane.visible) "Ocultar plano" else "Mostrar plano")
                    }
                    Button(onClick = onCreateOffset, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Crear plano paralelo")
                    }
                }
                sketch != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sketch.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Plano: ${program?.planeSet?.plane(sketch.planeId)?.label}", color = V24Secondary)
                    Text("Entidades: ${sketch.primitives.size}", color = V24Secondary)
                    Button(onClick = { onEditSketch(sketch) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, null)
                        Spacer(Modifier.width(5.dp))
                        Text("Editar croquis")
                    }
                }
                else -> Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seleccione una cara, operación, croquis o plano.", color = V24Secondary)
                    Button(onClick = onCreateOffset, enabled = enabled) { Text("Crear plano paralelo") }
                }
            }
        }
    }
}

@Composable
internal fun V25PlaneDialog(
    mode: V24PlaneDialogMode,
    program: BasicCadProgram,
    selectedSurface: CadViewportFaceSelection?,
    onCancel: () -> Unit,
    onCreate: (Long, Double, String?) -> Unit
) {
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
                    program.planeSet.planes.forEach { plane ->
                        Row(Modifier.fillMaxWidth().clickable { parentId = plane.id }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(parentId == plane.id, { parentId = plane.id })
                            Text(plane.label)
                        }
                    }
                } else {
                    val available = selectedSurface?.planar == true
                    Text(
                        if (available) "Cara plana seleccionada · ${selectedSurface?.id}" else "Seleccione primero una cara plana en el visor",
                        color = if (available) Color(0xFF1B6E32) else Color(0xFFB42318)
                    )
                }
                OutlinedTextField(offset, { offset = it.filter { c -> c.isDigit() || c in ".,-" }.replace(',', '.') }, label = { Text("Desplazamiento (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(label, { label = it }, label = { Text("Nombre opcional") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = { val value = offset.toDoubleOrNull() ?: return@Button; onCreate(parentId, value, label.takeIf { it.isNotBlank() }) },
                enabled = mode == V24PlaneDialogMode.OFFSET || selectedSurface?.planar == true
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
    )
}

internal fun v25Normal(value: FloatArray): String = "(%.3f, %.3f, %.3f)".format(
    Locale.US,
    value.getOrElse(0) { 0f },
    value.getOrElse(1) { 0f },
    value.getOrElse(2) { 0f }
)
