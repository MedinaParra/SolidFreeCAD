package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*

@Composable
fun PropertyManager(
    visible: Boolean,
    opType: OperationType,
    selectedSketchName: String?,
    onAccept: (depth: Float, angle: Float, axis: String, radius: Float, thickness: Float, type: OperationType) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Current parameters
    var localDepth by remember(opType) { mutableStateOf(50f) }
    var localAngle by remember(opType) { mutableStateOf(360f) }
    var localAxis by remember(opType) { mutableStateOf("Y-Axis") }
    var localRadius by remember(opType) { mutableStateOf(5f) }
    var localThickness by remember(opType) { mutableStateOf(2f) }
    var localOpType by remember(opType) { mutableStateOf(opType) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Card(
            modifier = modifier
                .fillMaxHeight()
                .width(280.dp)
                .border(1.dp, Color(0xFFBDC3C7), RoundedCornerShape(0.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4F8)),
            shape = RoundedCornerShape(0.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Top control bar: Aceptar (Green Check) / Cancelar (Red Cross)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE2E9F0))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel button (X)
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFFADBD8), RoundedCornerShape(4.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancelar",
                                tint = Color(0xFFC0392B),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = when (localOpType) {
                                OperationType.EXTRUDE_BOSS, OperationType.EXTRUDE_CUT -> "Propiedades de Extrusión"
                                OperationType.REVOLVE_BOSS, OperationType.REVOLVE_CUT -> "Propiedades de Revolución"
                                OperationType.FILLET -> "Propiedades de Redondeo"
                                OperationType.SHELL -> "Propiedades de Vaciado"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B365D),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        // Accept button (Checkmark)
                        IconButton(
                            onClick = {
                                onAccept(localDepth, localAngle, localAxis, localRadius, localThickness, localOpType)
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFD4EFDF), RoundedCornerShape(4.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Aceptar",
                                tint = Color(0xFF196F3D),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Main form area
                Column(modifier = Modifier.padding(16.dp)) {

                    // Section 1: Selected Sketch
                    if (selectedSketchName != null) {
                        Text(
                            text = "Croquis Seleccionado",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7F8C8D),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFFBDC3C7), RoundedCornerShape(4.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = selectedSketchName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2C3E50)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Section 2: Combined Operation Selector
                    // Direct optimization for consolidated Boss / Cut operators inside the tools!
                    if (localOpType == OperationType.EXTRUDE_BOSS || localOpType == OperationType.EXTRUDE_CUT) {
                        Text(
                            text = "Tipo de Operación (Vaciado Integrado)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7F8C8D),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE5E8E8), RoundedCornerShape(6.dp))
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (localOpType == OperationType.EXTRUDE_BOSS) Color(0xFF3498DB) else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { localOpType = OperationType.EXTRUDE_BOSS }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Saliente (Añadir)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (localOpType == OperationType.EXTRUDE_BOSS) Color.White else Color(0xFF2C3E50)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (localOpType == OperationType.EXTRUDE_CUT) Color(0xFFE74C3C) else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { localOpType = OperationType.EXTRUDE_CUT }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Corte (Vaciar)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (localOpType == OperationType.EXTRUDE_CUT) Color.White else Color(0xFF2C3E50)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (localOpType == OperationType.REVOLVE_BOSS || localOpType == OperationType.REVOLVE_CUT) {
                        Text(
                            text = "Tipo de Revolución",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7F8C8D),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE5E8E8), RoundedCornerShape(6.dp))
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (localOpType == OperationType.REVOLVE_BOSS) Color(0xFF2980B9) else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { localOpType = OperationType.REVOLVE_BOSS }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Base Revolución",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (localOpType == OperationType.REVOLVE_BOSS) Color.White else Color(0xFF2C3E50)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (localOpType == OperationType.REVOLVE_CUT) Color(0xFFC0392B) else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { localOpType = OperationType.REVOLVE_CUT }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Corte Revolución",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (localOpType == OperationType.REVOLVE_CUT) Color.White else Color(0xFF2C3E50)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Section 3: Parameters sliders
                    when (localOpType) {
                        OperationType.EXTRUDE_BOSS, OperationType.EXTRUDE_CUT -> {
                            Text(
                                text = "Dirección 1 (Profundidad)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7F8C8D)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Prof.:", fontSize = 12.sp, color = Color(0xFF34495E))
                                Text(
                                    text = String.format("%.1f mm", localDepth),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C3E50)
                                )
                            }
                            Slider(
                                value = localDepth,
                                onValueChange = { localDepth = it },
                                valueRange = 5f..150f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF1B365D),
                                    activeTrackColor = Color(0xFF34495E)
                                )
                            )
                        }

                        OperationType.REVOLVE_BOSS, OperationType.REVOLVE_CUT -> {
                            Text(
                                text = "Eje de Revolución",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7F8C8D)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = localAxis == "Y-Axis",
                                    onClick = { localAxis = "Y-Axis" },
                                    label = { Text("Eje Y (Vertical)", fontSize = 10.sp) }
                                )
                                FilterChip(
                                    selected = localAxis == "X-Axis",
                                    onClick = { localAxis = "X-Axis" },
                                    label = { Text("Eje X (Horiz)", fontSize = 10.sp) }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Ángulo de Revolución",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7F8C8D)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Ángulo:", fontSize = 12.sp, color = Color(0xFF34495E))
                                Text(
                                    text = "${localAngle.toInt()}°",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C3E50)
                                )
                            }
                            Slider(
                                value = localAngle,
                                onValueChange = { localAngle = it },
                                valueRange = 15f..360f,
                                steps = 23,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF1B365D),
                                    activeTrackColor = Color(0xFF34495E)
                                )
                            )
                        }

                        OperationType.FILLET -> {
                            Text(
                                text = "Radio de Redondeo",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7F8C8D)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Radio:", fontSize = 12.sp, color = Color(0xFF34495E))
                                Text(
                                    text = String.format("%.1f mm", localRadius),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C3E50)
                                )
                            }
                            Slider(
                                value = localRadius,
                                onValueChange = { localRadius = it },
                                valueRange = 1f..30f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF1B365D),
                                    activeTrackColor = Color(0xFF34495E)
                                )
                            )
                        }

                        OperationType.SHELL -> {
                            Text(
                                text = "Espesor de Pared",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7F8C8D)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Espesor:", fontSize = 12.sp, color = Color(0xFF34495E))
                                Text(
                                    text = String.format("%.1f mm", localThickness),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C3E50)
                                )
                            }
                            Slider(
                                value = localThickness,
                                onValueChange = { localThickness = it },
                                valueRange = 0.5f..10f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF1B365D),
                                    activeTrackColor = Color(0xFF34495E)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Tip Info Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFEBF5FB), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFFAED6F1), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = when (localOpType) {
                                OperationType.EXTRUDE_BOSS -> "Un saliente extruido genera un cuerpo sólido 3D estirando el croquis bidimensional por la profundidad seleccionada."
                                OperationType.EXTRUDE_CUT -> "Un corte extruido elimina/vacía material del sólido 3D usando el contorno de tu croquis como molde de corte."
                                OperationType.REVOLVE_BOSS -> "Revolución genera una forma circular sólida girando el croquis de perfil alrededor de un eje central."
                                OperationType.REVOLVE_CUT -> "Corte por revolución quita material circundante en forma concéntrica a un eje."
                                OperationType.FILLET -> "El redondeo crea caras curvas de transición en los bordes para eliminar esquinas vivas de la pieza."
                                OperationType.SHELL -> "El vaciado (Shell) ahueca el sólido dejando una carcasa del espesor de pared especificado."
                            },
                            fontSize = 10.sp,
                            color = Color(0xFF2471A3),
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}
