package com.example.faceui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.BasicCadProgram
import com.example.features.CadSketchProfile

@Composable
internal fun V23PlaneDialog(
    state: V23PlaneDialogState,
    program: BasicCadProgram?,
    onCancel: () -> Unit,
    onCreate: (Double, String?) -> Unit
) {
    var offset by remember(state) { mutableStateOf("0") }
    var label by remember(state) { mutableStateOf("") }
    val offsetValue = offset.toDoubleOrNull()
    val sourceLabel = when (state.mode) {
        V23PlaneDialogMode.OFFSET -> program?.planes?.firstOrNull { it.id == state.parentPlaneId }?.label ?: "Plano XY"
        V23PlaneDialogMode.FACE_PARALLEL -> state.face?.label ?: "Cara plana"
    }
    V23ModalSurface {
        Text("Crear plano de referencia", color = V23Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Paralelo a $sourceLabel", color = V23Secondary, fontSize = 9.sp)
        OutlinedTextField(value = label, onValueChange = { label = it.take(40) }, label = { Text("Nombre opcional") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = offset, onValueChange = { offset = v23Numeric(it) }, label = { Text("Separación") }, suffix = { Text("mm") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Un valor negativo crea el plano al lado opuesto de la normal.", color = V23Secondary, fontSize = 8.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancelar") }
            Button(onClick = { onCreate(offsetValue ?: 0.0, label.takeIf { it.isNotBlank() }) }, enabled = offsetValue != null) { Text("Crear plano") }
        }
    }
}

@Composable
internal fun V23SketchCreateDialog(
    program: BasicCadProgram,
    preferredPlaneId: Long,
    onCancel: () -> Unit,
    onCreate: (Long, CadSketchProfile, Map<String, Double>, String?) -> Unit
) {
    var planeId by remember { mutableStateOf(preferredPlaneId.takeIf { id -> program.planes.any { it.id == id } } ?: 1L) }
    var profile by remember { mutableStateOf(CadSketchProfile.CIRCLE) }
    var label by remember { mutableStateOf("") }
    var first by remember(profile) { mutableStateOf(if (profile == CadSketchProfile.CIRCLE) "20" else "30") }
    var second by remember(profile) { mutableStateOf("20") }
    val firstValue = first.toDoubleOrNull()
    val secondValue = second.toDoubleOrNull()
    val valid = firstValue != null && firstValue > 0.0 && (profile == CadSketchProfile.CIRCLE || secondValue != null && secondValue > 0.0)
    V23ModalSurface {
        Text("Nuevo croquis", color = V23Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("1. Seleccione un plano · 2. Defina el perfil · 3. Edite las cotas", color = V23Secondary, fontSize = 8.sp)
        Text("PLANO", color = V23Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            program.planes.forEach { plane ->
                FilterChip(selected = planeId == plane.id, onClick = { planeId = plane.id }, label = { Text(plane.label, fontSize = 8.sp) })
            }
        }
        Text("PERFIL", color = V23Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CadSketchProfile.entries.forEach { item ->
                FilterChip(selected = profile == item, onClick = { profile = item }, label = { Text(item.label) })
            }
        }
        OutlinedTextField(value = label, onValueChange = { label = it.take(40) }, label = { Text("Nombre opcional") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (profile == CadSketchProfile.CIRCLE) {
            OutlinedTextField(value = first, onValueChange = { first = v23Numeric(it) }, label = { Text("Diámetro") }, suffix = { Text("mm") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        } else {
            OutlinedTextField(value = first, onValueChange = { first = v23Numeric(it) }, label = { Text("Ancho") }, suffix = { Text("mm") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = second, onValueChange = { second = v23Numeric(it) }, label = { Text("Alto") }, suffix = { Text("mm") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancelar") }
            Button(onClick = {
                val params = if (profile == CadSketchProfile.CIRCLE) mapOf("diameter" to firstValue!!) else mapOf("width" to firstValue!!, "height" to secondValue!!)
                onCreate(planeId, profile, params, label.takeIf { it.isNotBlank() })
            }, enabled = valid) { Text("Crear croquis") }
        }
    }
}

@Composable
internal fun V23ModalSurface(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.widthIn(min = 390.dp, max = 560.dp).heightIn(max = 620.dp), color = Color.White, contentColor = V23Text, shape = RoundedCornerShape(12.dp), shadowElevation = 14.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}
