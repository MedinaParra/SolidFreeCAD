package com.example.faceui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.CadReferencePlane
import com.example.features.CadSketchDefinition
import com.example.features.CadSketchProfile
import java.util.Locale
import kotlin.math.min

private val SketchText = Color(0xFF0F172A)
private val SketchSecondary = Color(0xFF334155)
private val SketchBlue = Color(0xFF1565C0)
private val SketchGeometry = Color(0xFF0D47A1)
private val SketchFill = Color(0x332196F3)

@Composable
fun V23SketchEditor(
    sketch: CadSketchDefinition,
    plane: CadReferencePlane,
    onCancel: () -> Unit,
    onApply: (Map<String, Double>) -> Unit,
    onError: (String) -> Unit
) {
    var draft by remember(sketch.id, sketch.parameters) {
        mutableStateOf(sketch.parameters.mapValues { formatSketchNumber(it.value) })
    }
    val parsed = parseSketchDraft(draft)
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF9FBFC), contentColor = SketchText) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFE5EDF3)).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Cancel, "Cancelar croquis", tint = Color(0xFFB42318)) }
                Button(onClick = {
                    val values = parsed.getOrElse {
                        onError(it.message ?: "Revise las cotas del croquis")
                        return@Button
                    }
                    onApply(values)
                }, enabled = parsed.isSuccess) {
                    Icon(Icons.Default.Check, null); Spacer(Modifier.width(5.dp)); Text("Aceptar croquis")
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sketch.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${sketch.profile.label} · ${plane.label}", color = SketchSecondary, fontSize = 9.sp)
                }
                Text("EDICIÓN DE CROQUIS", color = SketchBlue, fontWeight = FontWeight.Bold, fontSize = 9.sp)
            }
            HorizontalDivider(color = Color(0xFF94A3B8))
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                        val center = Offset(size.width * 0.5f, size.height * 0.5f)
                        val extent = min(size.width, size.height) * 0.34f
                        drawLine(Color(0xFFB0BEC5), Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 1.5f)
                        drawLine(Color(0xFFB0BEC5), Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = 1.5f)
                        drawCircle(Color(0xFF546E7A), radius = 4f, center = center)
                        when (sketch.profile) {
                            CadSketchProfile.CIRCLE -> {
                                val diameter = parsed.getOrNull()?.get("diameter") ?: sketch.parameters["diameter"] ?: 20.0
                                val radius = extent.coerceAtMost(extent * (diameter / maxOf(diameter, 1.0)).toFloat())
                                drawCircle(SketchFill, radius = radius, center = center)
                                drawCircle(SketchGeometry, radius = radius, center = center, style = Stroke(width = 4f))
                                val left = Offset(center.x - radius, center.y)
                                val right = Offset(center.x + radius, center.y)
                                drawLine(SketchBlue, left, right, strokeWidth = 3f)
                                drawLine(SketchBlue, Offset(left.x, left.y - 12f), Offset(left.x, left.y + 12f), strokeWidth = 2f)
                                drawLine(SketchBlue, Offset(right.x, right.y - 12f), Offset(right.x, right.y + 12f), strokeWidth = 2f)
                            }
                            CadSketchProfile.RECTANGLE -> {
                                val values = parsed.getOrNull() ?: sketch.parameters
                                val width = values["width"] ?: 30.0
                                val height = values["height"] ?: 20.0
                                val maximum = maxOf(width, height, 1.0)
                                val halfWidth = extent * (width / maximum).toFloat()
                                val halfHeight = extent * (height / maximum).toFloat()
                                drawRect(SketchFill, topLeft = Offset(center.x - halfWidth, center.y - halfHeight), size = androidx.compose.ui.geometry.Size(halfWidth * 2f, halfHeight * 2f))
                                drawRect(SketchGeometry, topLeft = Offset(center.x - halfWidth, center.y - halfHeight), size = androidx.compose.ui.geometry.Size(halfWidth * 2f, halfHeight * 2f), style = Stroke(width = 4f))
                                drawLine(SketchBlue, Offset(center.x - halfWidth, center.y + halfHeight + 25f), Offset(center.x + halfWidth, center.y + halfHeight + 25f), strokeWidth = 3f)
                                drawLine(SketchBlue, Offset(center.x + halfWidth + 25f, center.y - halfHeight), Offset(center.x + halfWidth + 25f, center.y + halfHeight), strokeWidth = 3f)
                            }
                        }
                    }
                }
                Surface(modifier = Modifier.width(270.dp).fillMaxHeight(), color = Color(0xFFF1F5F8), shadowElevation = 4.dp) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("COTAS", color = SketchText, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("Las cotas modifican el perfil en el mismo croquis; no crean una operación nueva.", color = SketchSecondary, fontSize = 8.sp, lineHeight = 11.sp)
                        draft.forEach { (key, value) ->
                            OutlinedTextField(value = value, onValueChange = { next -> draft = draft + (key to sketchNumeric(next)) }, label = { Text(sketchParameterLabel(key)) }, suffix = { Text("mm") }, textStyle = LocalTextStyle.current.copy(color = SketchText, fontWeight = FontWeight.Medium), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        parsed.exceptionOrNull()?.message?.let { message -> Text(message, color = Color(0xFFB42318), fontSize = 8.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

private fun parseSketchDraft(draft: Map<String, String>): Result<Map<String, Double>> = runCatching {
    draft.mapValues { (key, text) ->
        val value = text.toDoubleOrNull() ?: error("${sketchParameterLabel(key)} debe contener un número")
        require(value.isFinite() && value > 0.0) { "${sketchParameterLabel(key)} debe ser mayor que cero" }
        value
    }
}

private fun sketchNumeric(value: String): String = value.filterIndexed { index, char -> char.isDigit() || char == '.' || char == ',' || (char == '-' && index == 0) }.replace(',', '.')
private fun sketchParameterLabel(key: String): String = when (key) { "diameter" -> "Diámetro Ø"; "width" -> "Ancho"; "height" -> "Alto"; else -> key }
private fun formatSketchNumber(value: Double): String = "%.4f".format(Locale.US, value).trimEnd('0').trimEnd('.')
