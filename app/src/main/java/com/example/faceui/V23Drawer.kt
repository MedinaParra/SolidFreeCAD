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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.BasicCadFeatureFamily
import com.example.features.BasicCadOperation
import com.example.features.BasicCadProgram
import com.example.features.CadReferencePlane
import com.example.features.CadReferencePlaneKind
import com.example.features.CadSketch
import com.example.features.CadSketchProfileType
import com.example.features.requiresClosedSketch
import java.util.Locale

@Composable
internal fun V23BottomDrawer(
    tab: V23DrawerTab,
    program: BasicCadProgram?,
    selectedPlane: CadReferencePlane?,
    selectedSketch: CadSketch?,
    enabled: Boolean,
    onTab: (V23DrawerTab) -> Unit,
    onClose: () -> Unit,
    onSelectPlane: (Long) -> Unit,
    onCreateOffsetPlane: () -> Unit,
    onCreateFacePlane: () -> Unit,
    onCreateSketch: (CadSketchProfileType) -> Unit,
    onEditSketch: (Long) -> Unit,
    onOperation: (BasicCadOperation) -> Unit
) {
    Surface(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).widthIn(max = 980.dp).fillMaxWidth().heightIn(min = 150.dp, max = 190.dp),
        color = Color(0xFFF8FAFC), contentColor = V23Text,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 8.dp, bottomEnd = 8.dp), shadowElevation = 10.dp
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(V23Header).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                V23DrawerTab.entries.forEach { item -> V23DrawerTabButton(item.label, item == tab, enabled) { onTab(item) } }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, "Ocultar herramientas", tint = V23Accent)
                }
            }
            HorizontalDivider(color = V23Divider)
            when (tab) {
                V23DrawerTab.PLANOS -> V23PlaneTools(program, selectedPlane, enabled, onSelectPlane, onCreateOffsetPlane, onCreateFacePlane)
                V23DrawerTab.CROQUIS -> V23SketchTools(selectedSketch, enabled, onCreateSketch, onEditSketch)
                V23DrawerTab.OPERACIONES -> V23OperationTools(
                    BasicCadOperation.entries.filter { it.family == BasicCadFeatureFamily.SKETCH_BASED }, selectedSketch, enabled, onOperation
                )
                V23DrawerTab.CORTES -> V23OperationTools(
                    BasicCadOperation.entries.filter { it.family == BasicCadFeatureFamily.REMOVE_MATERIAL }, selectedSketch, enabled, onOperation
                )
                V23DrawerTab.ACABADOS -> V23OperationTools(
                    BasicCadOperation.entries.filter { it.family == BasicCadFeatureFamily.DRESS_UP || it.family == BasicCadFeatureFamily.BOOLEAN }, selectedSketch, enabled, onOperation
                )
                V23DrawerTab.PATRONES -> V23OperationTools(
                    BasicCadOperation.entries.filter { it.family == BasicCadFeatureFamily.TRANSFORM }, selectedSketch, enabled, onOperation
                )
            }
        }
    }
}

@Composable
internal fun V23DrawerTabButton(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 2.dp).clickable(enabled = enabled, onClick = onClick),
        color = if (selected) Color.White else Color.Transparent,
        contentColor = if (selected) V23Accent else V23Secondary,
        shape = RoundedCornerShape(6.dp), shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = if (selected) V23Accent else V23Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun V23PlaneTools(
    program: BasicCadProgram?, selectedPlane: CadReferencePlane?, enabled: Boolean,
    onSelectPlane: (Long) -> Unit, onCreateOffsetPlane: () -> Unit, onCreateFacePlane: () -> Unit
) {
    Row(
        Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        program?.planes?.filter { it.kind == CadReferencePlaneKind.PRINCIPAL }?.forEach { plane ->
            V23ToolCard(plane.label, if (plane.id == program.activePlaneId) "Plano activo" else "Seleccionar", false, enabled) { onSelectPlane(plane.id) }
        }
        V23ToolCard(
            "Plano paralelo", "A ${selectedPlane?.label ?: program?.activePlane()?.label ?: "plano activo"} · 10 mm", false,
            enabled && program != null, onCreateOffsetPlane
        )
        V23ToolCard("Plano desde cara", "Toque una cara plana o curva", false, enabled && program != null, onCreateFacePlane)
    }
}

@Composable
internal fun V23SketchTools(selectedSketch: CadSketch?, enabled: Boolean, onCreate: (CadSketchProfileType) -> Unit, onEdit: (Long) -> Unit) {
    Row(
        Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(CadSketchProfileType.CIRCLE, CadSketchProfileType.RECTANGLE, CadSketchProfileType.SLOT, CadSketchProfileType.POLYGON).forEach { type ->
            V23ToolCard("Nuevo ${type.label.lowercase(Locale.ROOT)}", "Crear croquis cerrado", false, enabled) { onCreate(type) }
        }
        V23ToolCard("Editar croquis", selectedSketch?.label ?: "Seleccione un croquis", false, enabled && selectedSketch != null) {
            selectedSketch?.let { onEdit(it.id) }
        }
        V23ToolCard("Línea", "Geometría abierta", true, enabled) { onCreate(CadSketchProfileType.LINE) }
        V23ToolCard("Arco", "Geometría abierta", true, enabled) { onCreate(CadSketchProfileType.ARC) }
    }
}

@Composable
internal fun V23OperationTools(
    operations: List<BasicCadOperation>, selectedSketch: CadSketch?, enabled: Boolean, onOperation: (BasicCadOperation) -> Unit
) {
    Row(
        Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        operations.forEach { operation ->
            val requiresSketch = operation.requiresClosedSketch()
            val available = !requiresSketch || selectedSketch?.profileType?.closed == true
            V23ToolCard(
                operation.label,
                when {
                    requiresSketch && selectedSketch == null -> "Requiere croquis"
                    requiresSketch -> selectedSketch.label
                    else -> operation.description
                },
                !operation.generalTopology,
                enabled && available
            ) { onOperation(operation) }
        }
    }
}

@Composable
internal fun V23ToolCard(title: String, subtitle: String, beta: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(142.dp).height(96.dp).clickable(enabled = enabled, onClick = onClick),
        color = if (enabled) Color(0xFFEDF3F7) else Color(0xFFE5E7EB),
        contentColor = if (enabled) V23Text else Color(0xFF64748B),
        shape = RoundedCornerShape(8.dp), shadowElevation = if (enabled) 2.dp else 0.dp
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), color = if (enabled) V23Text else Color(0xFF64748B), fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (beta) Text("BETA", color = Color(0xFFB54708), fontSize = 6.sp, fontWeight = FontWeight.Bold)
            }
            Text(subtitle, color = if (enabled) V23Secondary else Color(0xFF64748B), fontSize = 7.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
