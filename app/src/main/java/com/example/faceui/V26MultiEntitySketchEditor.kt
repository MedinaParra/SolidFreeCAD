package com.example.faceui

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun V26MultiEntitySketchEditor(
    sketch: CadSketch,
    onCancel: () -> Unit,
    onAccept: (CadSketch) -> Unit,
    modifier: Modifier
) {
    var working by remember(sketch) { mutableStateOf(sketch.solveConstraints()) }
    var selectedId by remember(sketch.id) { mutableStateOf(working.primitives.firstOrNull()?.id) }
    val selected = working.primitives.firstOrNull { it.id == selectedId }
    var draft by remember(selected?.id, selected?.parameters) {
        mutableStateOf(selected?.parameters?.mapValues { v24Number(it.value) }.orEmpty())
    }
    var message by remember { mutableStateOf("Seleccione o agregue entidades al croquis.") }

    fun applyDraft(): Boolean {
        val primitive = selected ?: return true
        val parsed = draft.mapValues { (_, value) -> value.toDoubleOrNull() ?: return false }
        working = working.updatePrimitive(primitive.id, parsed)
        message = "Entidad ${primitive.id} actualizada"
        return true
    }

    fun addEntity(kind: CadSketchPrimitiveKind) {
        if (!applyDraft()) {
            message = "Revise las cotas antes de agregar otra entidad"
            return
        }
        val created = working.addPrimitive(kind, v26DefaultAt(kind, working.primitives.size))
        working = created
        selectedId = created.primitives.last().id
        message = "${v26Kind(kind)} añadida"
    }

    Surface(modifier, color = Color(0xFFF7FAFC).copy(alpha = 0.985f), contentColor = V24Text) {
        Column {
            Row(Modifier.fillMaxWidth().background(V24Header).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Editando ${working.label}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${working.primitives.size} entidades · ${working.constraints.size} restricciones · ${working.closedPrimitives.size} regiones", color = V24Secondary, fontSize = 8.sp)
                }
                TextButton(onClick = onCancel) { Text("Cancelar") }
                Button(onClick = {
                    if (applyDraft()) onAccept(working.solveConstraints()) else message = "Existe una cota no válida"
                }) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(4.dp)); Text("Aceptar") }
            }

            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CadSketchPrimitiveKind.entries.forEach { kind ->
                            AssistChip(onClick = { addEntity(kind) }, label = { Text("+ ${v26Kind(kind)}") })
                        }
                    }
                    V26SketchCanvas(working, selectedId, Modifier.weight(1f).fillMaxWidth().padding(12.dp))
                    Surface(color = Color(0xFFEAF1F5)) {
                        Text(message, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), color = V24Secondary, fontSize = 8.sp)
                    }
                }

                VerticalDivider(color = V24Divider)
                Column(Modifier.width(330.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Entidades", fontWeight = FontWeight.Bold)
                    working.primitives.forEach { primitive ->
                        Surface(
                            Modifier.fillMaxWidth().clickable {
                                if (primitive.id == selectedId || applyDraft()) selectedId = primitive.id
                                else message = "Corrija la entidad actual antes de cambiar"
                            },
                            color = if (primitive.id == selectedId) V24Selected else Color(0xFFEDF3F7),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${primitive.id}. ${v26Kind(primitive.kind)}", Modifier.weight(1f), fontWeight = if (primitive.id == selectedId) FontWeight.Bold else FontWeight.Normal, fontSize = 9.sp)
                                Text(if (primitive.closedRegion) "Región" else "Abierta", color = V24Secondary, fontSize = 7.sp)
                            }
                        }
                    }

                    selected?.let { primitive ->
                        HorizontalDivider(color = V24Divider)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Cotas de ${v26Kind(primitive.kind)} ${primitive.id}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            IconButton(onClick = {
                                working = working.removePrimitive(primitive.id)
                                selectedId = working.primitives.firstOrNull()?.id
                                message = "Entidad eliminada"
                            }) { Icon(Icons.Default.Delete, "Eliminar", tint = Color(0xFFB42318)) }
                        }
                        draft.forEach { (key, value) ->
                            OutlinedTextField(
                                value,
                                { text -> draft = draft + (key to text.filter { it.isDigit() || it in ".,-" }.replace(',', '.')) },
                                label = { Text(v26Parameter(key)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Button(onClick = { if (!applyDraft()) message = "Valor numérico no válido" }, modifier = Modifier.fillMaxWidth()) { Text("Aplicar cotas") }

                        Text("Restricciones rápidas", fontWeight = FontWeight.Bold)
                        if (primitive.kind == CadSketchPrimitiveKind.LINE) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(onClick = { if (applyDraft()) working = working.addConstraint(CadSketchConstraintKind.HORIZONTAL, primitive.id) }, modifier = Modifier.weight(1f)) { Text("Horizontal", fontSize = 8.sp) }
                                OutlinedButton(onClick = { if (applyDraft()) working = working.addConstraint(CadSketchConstraintKind.VERTICAL, primitive.id) }, modifier = Modifier.weight(1f)) { Text("Vertical", fontSize = 8.sp) }
                            }
                            working.primitives.lastOrNull { it.id != primitive.id && it.kind == CadSketchPrimitiveKind.LINE }?.let { previous ->
                                OutlinedButton(onClick = {
                                    if (applyDraft()) working = working.addConstraint(CadSketchConstraintKind.COINCIDENT, previous.id, primitive.id, 1, 0)
                                }, modifier = Modifier.fillMaxWidth()) { Text("Inicio coincidente con línea ${previous.id}", fontSize = 8.sp) }
                                OutlinedButton(onClick = {
                                    if (applyDraft()) working = working.addConstraint(CadSketchConstraintKind.EQUAL_LENGTH, previous.id, primitive.id)
                                }, modifier = Modifier.fillMaxWidth()) { Text("Igual longitud que línea ${previous.id}", fontSize = 8.sp) }
                            }
                        }
                        if (primitive.kind in setOf(CadSketchPrimitiveKind.CIRCLE, CadSketchPrimitiveKind.ARC, CadSketchPrimitiveKind.POLYGON)) {
                            working.primitives.lastOrNull {
                                it.id != primitive.id && it.kind in setOf(CadSketchPrimitiveKind.CIRCLE, CadSketchPrimitiveKind.ARC, CadSketchPrimitiveKind.POLYGON)
                            }?.let { previous ->
                                OutlinedButton(onClick = {
                                    if (applyDraft()) working = working.addConstraint(CadSketchConstraintKind.EQUAL_RADIUS, previous.id, primitive.id)
                                }, modifier = Modifier.fillMaxWidth()) { Text("Igual radio que entidad ${previous.id}") }
                            }
                        }
                        OutlinedButton(onClick = {
                            if (applyDraft()) working = working.addConstraint(CadSketchConstraintKind.FIXED, primitive.id)
                        }, modifier = Modifier.fillMaxWidth()) { Text("Fijar entidad") }
                    }

                    HorizontalDivider(color = V24Divider)
                    Text("Restricciones", fontWeight = FontWeight.Bold)
                    if (working.constraints.isEmpty()) Text("Sin restricciones geométricas.", color = V24Secondary, fontSize = 8.sp)
                    working.constraints.forEach { constraint ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${constraint.id}. ${constraint.kind.name.replace('_', ' ')} · ${constraint.firstPrimitiveId}${constraint.secondPrimitiveId?.let { " ↔ $it" }.orEmpty()}", Modifier.weight(1f), fontSize = 8.sp)
                            IconButton(onClick = { working = working.removeConstraint(constraint.id) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "Eliminar") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V26SketchCanvas(sketch: CadSketch, selectedId: Long?, modifier: Modifier) {
    Canvas(modifier) {
        val bounds = v26Bounds(sketch)
        val spanX = (bounds[2] - bounds[0]).coerceAtLeast(20.0)
        val spanY = (bounds[3] - bounds[1]).coerceAtLeast(20.0)
        val scale = minOf(size.width / (spanX * 1.25).toFloat(), size.height / (spanY * 1.25).toFloat())
        val centerX = (bounds[0] + bounds[2]) * 0.5
        val centerY = (bounds[1] + bounds[3]) * 0.5
        fun screen(x: Double, y: Double) = Offset(size.width * 0.5f + ((x - centerX) * scale).toFloat(), size.height * 0.5f - ((y - centerY) * scale).toFloat())
        val origin = screen(0.0, 0.0)
        drawLine(Color(0xFFB0BEC5), Offset(0f, origin.y), Offset(size.width, origin.y), 1.2f)
        drawLine(Color(0xFFB0BEC5), Offset(origin.x, 0f), Offset(origin.x, size.height), 1.2f)

        sketch.primitives.forEach { primitive ->
            val selected = primitive.id == selectedId
            val color = if (selected) Color(0xFFFF6D00) else Color(0xFF1976D2)
            val stroke = Stroke(if (selected) 6f else 4f)
            val p = primitive.parameters
            when (primitive.kind) {
                CadSketchPrimitiveKind.CIRCLE -> drawCircle(color, (((p["diameter"] ?: 20.0) / 2.0) * scale).toFloat(), screen(p["centerX"] ?: 0.0, p["centerY"] ?: 0.0), style = stroke)
                CadSketchPrimitiveKind.RECTANGLE -> {
                    val cx = p["centerX"] ?: 0.0; val cy = p["centerY"] ?: 0.0
                    val width = p["width"] ?: 30.0; val height = p["height"] ?: 20.0
                    drawRect(color, screen(cx - width / 2.0, cy + height / 2.0), Size((width * scale).toFloat(), (height * scale).toFloat()), style = stroke)
                }
                CadSketchPrimitiveKind.LINE -> drawLine(color, screen(p["x1"] ?: 0.0, p["y1"] ?: 0.0), screen(p["x2"] ?: 0.0, p["y2"] ?: 0.0), if (selected) 6f else 4f)
                CadSketchPrimitiveKind.ARC -> {
                    val cx = p["centerX"] ?: 0.0; val cy = p["centerY"] ?: 0.0; val radius = p["radius"] ?: 10.0
                    drawArc(color, -(p["startAngle"] ?: 0.0).toFloat(), -((p["endAngle"] ?: 180.0) - (p["startAngle"] ?: 0.0)).toFloat(), false, screen(cx - radius, cy + radius), Size((radius * 2 * scale).toFloat(), (radius * 2 * scale).toFloat()), style = stroke)
                }
                CadSketchPrimitiveKind.POLYGON -> {
                    val cx = p["centerX"] ?: 0.0; val cy = p["centerY"] ?: 0.0; val radius = p["radius"] ?: 12.0
                    val sides = (p["sides"] ?: 6.0).toInt().coerceIn(3, 64)
                    val path = Path()
                    repeat(sides + 1) { index ->
                        val angle = 2.0 * Math.PI * (index % sides) / sides
                        val point = screen(cx + radius * cos(angle), cy + radius * sin(angle))
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(path, color, style = stroke)
                }
            }
        }
    }
}

private fun v26Bounds(sketch: CadSketch): DoubleArray {
    var minX = -10.0; var minY = -10.0; var maxX = 10.0; var maxY = 10.0
    fun include(x: Double, y: Double) { minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y) }
    sketch.primitives.forEach { primitive ->
        val p = primitive.parameters
        when (primitive.kind) {
            CadSketchPrimitiveKind.CIRCLE -> { val cx = p["centerX"] ?: 0.0; val cy = p["centerY"] ?: 0.0; val r = (p["diameter"] ?: 20.0) / 2; include(cx-r,cy-r); include(cx+r,cy+r) }
            CadSketchPrimitiveKind.RECTANGLE -> { val cx=p["centerX"]?:0.0; val cy=p["centerY"]?:0.0; val w=(p["width"]?:30.0)/2; val h=(p["height"]?:20.0)/2; include(cx-w,cy-h); include(cx+w,cy+h) }
            CadSketchPrimitiveKind.LINE -> { include(p["x1"]?:0.0,p["y1"]?:0.0); include(p["x2"]?:0.0,p["y2"]?:0.0) }
            CadSketchPrimitiveKind.ARC, CadSketchPrimitiveKind.POLYGON -> { val cx=p["centerX"]?:0.0; val cy=p["centerY"]?:0.0; val r=p["radius"]?:12.0; include(cx-r,cy-r); include(cx+r,cy+r) }
        }
    }
    return doubleArrayOf(minX,minY,maxX,maxY)
}

private fun v26DefaultAt(kind: CadSketchPrimitiveKind, index: Int): Map<String, Double> {
    val shift = (index % 6) * 8.0
    val base = kind.defaultSketchParameters()
    return when (kind) {
        CadSketchPrimitiveKind.CIRCLE, CadSketchPrimitiveKind.RECTANGLE, CadSketchPrimitiveKind.ARC, CadSketchPrimitiveKind.POLYGON -> base + mapOf("centerX" to shift, "centerY" to shift / 2)
        CadSketchPrimitiveKind.LINE -> base + mapOf("x1" to -10.0 + shift, "x2" to 10.0 + shift, "y1" to shift / 2, "y2" to shift / 2)
    }
}

private fun v26Kind(kind: CadSketchPrimitiveKind) = when (kind) {
    CadSketchPrimitiveKind.CIRCLE -> "Círculo"
    CadSketchPrimitiveKind.RECTANGLE -> "Rectángulo"
    CadSketchPrimitiveKind.LINE -> "Línea"
    CadSketchPrimitiveKind.ARC -> "Arco"
    CadSketchPrimitiveKind.POLYGON -> "Polígono"
}

private fun v26Parameter(key: String) = when (key) {
    "centerX" -> "Centro X (mm)"; "centerY" -> "Centro Y (mm)"; "diameter" -> "Diámetro (mm)"
    "width" -> "Ancho (mm)"; "height" -> "Alto (mm)"; "x1" -> "X inicial (mm)"; "y1" -> "Y inicial (mm)"
    "x2" -> "X final (mm)"; "y2" -> "Y final (mm)"; "radius" -> "Radio (mm)"
    "startAngle" -> "Ángulo inicial (°)"; "endAngle" -> "Ángulo final (°)"; "sides" -> "Lados"; else -> key
}
