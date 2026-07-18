package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
fun FeatureTree(
    state: ProjectState,
    onSelectSketch: (String) -> Unit,
    onSelectOperation: (String) -> Unit,
    onDeleteSketch: (String) -> Unit,
    onDeleteOperation: (String) -> Unit,
    onSelectPlane: (WorkPlane) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .border(1.dp, Color(0xFFBDC3C7), RoundedCornerShape(0.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xF8F5F8FA)),
        shape = RoundedCornerShape(0.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Project/Part Name
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE2E9F0))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = null,
                        tint = Color(0xFF1B365D),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = state.projectName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B365D)
                    )
                }
            }

            // Subheader: Tree Description
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFECF0F3))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "ÁRBOL DE DISEÑO (SolidWorks)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF7F8C8D)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                // Section 1: Default Reference Planes
                item {
                    Text(
                        text = "Planos de Referencia",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7F8C8D),
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
                    )
                }

                items(WorkPlane.values()) { plane ->
                    val isActivePlane = state.activePlane == plane && !state.viewMode3D
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                            .background(
                                if (isActivePlane) Color(0xFFD4E6F1) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelectPlane(plane) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (isActivePlane) Color(0xFF1F4E79) else Color(0xFF7F8C8D),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when (plane) {
                                    WorkPlane.XY -> "Plano Alzado (XY)"
                                    WorkPlane.XZ -> "Plano Planta (XZ)"
                                    WorkPlane.YZ -> "Plano Perfil (YZ)"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isActivePlane) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActivePlane) Color(0xFF1F4E79) else Color(0xFF2C3E50)
                            )
                        }
                        if (isActivePlane) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Plano Activo",
                                tint = Color(0xFF2980B9),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Section 2: Model History Operations
                item {
                    Divider(color = Color(0xFFE2E9F0), modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Historial de Operaciones",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7F8C8D),
                        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                    )
                }

                // If history is empty, show empty state helper
                if (state.sketches.isEmpty() && state.operations.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sin operaciones.\nSeleccione un plano e inicie un croquis.",
                                fontSize = 11.sp,
                                color = Color(0xFF95A5A6),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Render Sketches in history
                items(state.sketches) { sketch ->
                    val isSelected = state.selectedSketchId == sketch.id
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                if (isSelected) Color(0xFFD6EAF8) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelectSketch(sketch.id) }
                            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridGoldenratio,
                                    contentDescription = null,
                                    tint = Color(0xFF27AE60),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = sketch.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E3F20)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteSketch(sketch.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Borrar Croquis",
                                    tint = Color(0xFFE74C3C),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        // Render sketch geometry details nested inside
                        if (sketch.entities.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .padding(start = 24.dp, top = 2.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                sketch.entities.forEach { ent ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        val (icon, name, labelVal) = when (ent) {
                                            is SketchEntity.Line -> Triple(Icons.Default.Gesture, "Línea", ent.label)
                                            is SketchEntity.Circle -> Triple(Icons.Default.RadioButtonUnchecked, "Círculo", ent.label)
                                            is SketchEntity.Rectangle -> Triple(Icons.Default.CropSquare, "Rectángulo", ent.label)
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = Color(0xFF7F8C8D),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "${name}: $labelVal",
                                            fontSize = 10.sp,
                                            color = Color(0xFF7F8C8D)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Render 3D features / operations in history
                items(state.operations) { op ->
                    val isSelected = state.selectedOperationId == op.id
                    val icon = when (op.type) {
                        OperationType.EXTRUDE_BOSS -> Icons.Default.ViewInAr // Green 3D box
                        OperationType.EXTRUDE_CUT -> Icons.Default.Texture // Red pocket cut
                        OperationType.REVOLVE_BOSS -> Icons.Default.RotateRight // Revolve
                        OperationType.REVOLVE_CUT -> Icons.Default.RotateLeft // Revolve Cut
                        OperationType.FILLET -> Icons.Default.RoundedCorner // Fillet
                        OperationType.SHELL -> Icons.Default.Inbox // Shell
                    }
                    val iconColor = when (op.type) {
                        OperationType.EXTRUDE_BOSS -> Color(0xFFF39C12) // Yellow-Orange Cylinder
                        OperationType.EXTRUDE_CUT -> Color(0xFFE74C3C) // Red Cut Cylinder
                        OperationType.REVOLVE_BOSS -> Color(0xFF2980B9) // Blue spun
                        OperationType.REVOLVE_CUT -> Color(0xFFC0392B) // Dark red revolve cut
                        else -> Color(0xFF9B59B6) // Purple secondary
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(
                                if (isSelected) Color(0xFFFADBD8) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelectOperation(op.id) }
                            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = op.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2C3E50)
                                )
                                Text(
                                    text = when (op.type) {
                                        OperationType.EXTRUDE_BOSS -> "Extrusión: +${op.depth}mm"
                                        OperationType.EXTRUDE_CUT -> "Vaciado: -${op.depth}mm"
                                        OperationType.REVOLVE_BOSS -> "Revolución: ${op.angle}°"
                                        OperationType.REVOLVE_CUT -> "Corte Revolv: ${op.angle}°"
                                        OperationType.FILLET -> "Redondeo: R${op.radius}mm"
                                        OperationType.SHELL -> "Vaciado (Shell): e=${op.thickness}mm"
                                    },
                                    fontSize = 10.sp,
                                    color = Color(0xFF7F8C8D)
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDeleteOperation(op.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Borrar Operación",
                                tint = Color(0xFFE74C3C),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
