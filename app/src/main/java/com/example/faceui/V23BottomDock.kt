package com.example.faceui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.BasicCadOperation
import com.example.features.BasicCadProgram
import com.example.features.CadPlaneKind
import com.example.features.requiresSketch

@Composable
internal fun V23BottomDock(
    category: V23DockCategory,
    enabled: Boolean,
    program: BasicCadProgram,
    selectedSketchId: Long?,
    onCategory: (V23DockCategory) -> Unit,
    onClose: () -> Unit,
    onCreateOffsetPlane: () -> Unit,
    onCreateFacePlane: () -> Unit,
    onToggleBasePlane: (Long) -> Unit,
    onCreateSketch: () -> Unit,
    onEditSketch: () -> Unit,
    onOperation: (BasicCadOperation) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.88f).heightIn(min = 148.dp, max = 198.dp),
        color = Color(0xFFF9FBFC), contentColor = V23Text,
        shape = RoundedCornerShape(12.dp), shadowElevation = 12.dp
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(V23Header).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    V23DockCategory.entries.forEach { item ->
                        V23DockTab(item.label, category == item) { onCategory(item) }
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "Ocultar herramientas", tint = V23Accent)
                }
            }
            HorizontalDivider(color = V23Divider)
            Row(
                Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 9.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (category) {
                    V23DockCategory.PLANES -> {
                        program.planes.filter { it.kind == CadPlaneKind.BASE }.forEach { plane ->
                            V23DockCommand(plane.label, if (plane.visible) "Ocultar" else "Mostrar", Icons.Default.GridOn, enabled) {
                                onToggleBasePlane(plane.id)
                            }
                        }
                        V23DockCommand("Plano paralelo", "Desde plano", Icons.Default.Layers, enabled, onCreateOffsetPlane)
                        V23DockCommand("Plano desde cara", "Cara plana", Icons.Default.CropSquare, enabled, onCreateFacePlane)
                    }
                    V23DockCategory.SKETCH -> {
                        V23DockCommand("Nuevo croquis", "Elegir plano", Icons.Default.Edit, enabled, onCreateSketch)
                        V23DockCommand(
                            "Editar croquis",
                            if (selectedSketchId != null) "Seleccionado" else "Último",
                            Icons.Default.BorderColor,
                            enabled && program.sketches.isNotEmpty(),
                            onEditSketch
                        )
                        V23DockCommand("Círculo", "Nuevo perfil", Icons.Default.RadioButtonUnchecked, enabled, onCreateSketch)
                        V23DockCommand("Rectángulo", "Nuevo perfil", Icons.Default.CropSquare, enabled, onCreateSketch)
                    }
                    else -> v23OperationsFor(category).forEach { V23OperationButton(it, enabled, onOperation) }
                }
            }
        }
    }
}

@Composable
private fun V23DockTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 2.dp).clickable(onClick = onClick),
        color = if (selected) Color.White else Color.Transparent,
        contentColor = if (selected) V23Accent else V23Secondary,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = if (selected) V23Accent else V23Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun V23DockCommand(label: String, subtitle: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(116.dp).heightIn(min = 82.dp).clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) Color(0xFFEDF4F8) else Color(0xFFE5E7EB),
        contentColor = V23Text, shape = RoundedCornerShape(8.dp), shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = if (enabled) V23Accent else Color(0xFF94A3B8), modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, color = if (enabled) V23Text else V23Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, color = V23Secondary, fontSize = 7.sp, maxLines = 1)
        }
    }
}

@Composable
private fun V23OperationButton(operation: BasicCadOperation, enabled: Boolean, onOperation: (BasicCadOperation) -> Unit) {
    Surface(
        modifier = Modifier.width(128.dp).heightIn(min = 88.dp).clickable(enabled = enabled) { onOperation(operation) },
        color = Color(0xFFEDF4F8), contentColor = V23Text,
        shape = RoundedCornerShape(8.dp), shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(v23OperationIcon(operation), null, tint = V23Accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(operation.label, color = V23Text, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!operation.generalTopology) Text("BETA", color = Color(0xFF9A4A04), fontSize = 7.sp, fontWeight = FontWeight.Bold)
            Text(if (operation.requiresSketch()) "Usa croquis" else operation.family.label, color = V23Secondary, fontSize = 7.sp, maxLines = 1)
        }
    }
}
