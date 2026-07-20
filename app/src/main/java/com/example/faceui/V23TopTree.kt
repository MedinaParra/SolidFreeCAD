package com.example.faceui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.CadPlaneKind
import com.example.nativecad.viewer.CameraPreset

@Composable
internal fun V23TopBar(
    documentName: String, loading: Boolean, treeVisible: Boolean, rightVisible: Boolean,
    dockVisible: Boolean, canUndo: Boolean, canRedo: Boolean,
    onNew: () -> Unit, onOpen: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit,
    onFit: () -> Unit, onPreset: (CameraPreset) -> Unit, onToggleTree: () -> Unit,
    onToggleRight: () -> Unit, onToggleDock: () -> Unit, onSketch: () -> Unit
) {
    Surface(color = Color(0xFFF1F4F6), contentColor = V23Text, shadowElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(66.dp).padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFD32F2F), shape = RoundedCornerShape(5.dp)) {
                Text("SF", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.widthIn(max = 150.dp)) {
                Text("SolidFreeCAD", color = V23Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(documentName, color = V23Secondary, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(5.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                V23ToolbarAction("Nuevo", Icons.Default.NoteAdd, !loading, onNew)
                V23ToolbarAction("Abrir", Icons.Default.FolderOpen, !loading, onOpen)
                V23ToolbarAction("Deshacer", Icons.Default.Undo, canUndo, onUndo)
                V23ToolbarAction("Rehacer", Icons.Default.Redo, canRedo, onRedo)
                V23ToolbarAction("Croquis", Icons.Default.Edit, !loading, onSketch)
                V23ToolbarAction("Herramientas", Icons.Default.Build, !loading, onToggleDock)
                V23ToolbarAction("Encuadrar", Icons.Default.FilterCenterFocus, true, onFit)
                V23TextAction("ISO", true) { onPreset(CameraPreset.ISOMETRIC) }
                V23TextAction("Frente", true) { onPreset(CameraPreset.FRONT) }
                V23TextAction("Planta", true) { onPreset(CameraPreset.TOP) }
            }
            IconButton(onClick = onToggleTree, enabled = !loading, modifier = Modifier.size(46.dp)) {
                Icon(if (treeVisible) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight, "Árbol", tint = V23Accent)
            }
            IconButton(onClick = onToggleRight, enabled = !loading, modifier = Modifier.size(46.dp)) {
                Icon(if (rightVisible) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowLeft, "Propiedades", tint = V23Accent)
            }
            if (dockVisible) Icon(Icons.Default.KeyboardArrowDown, "Bandeja visible", tint = V23Muted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun V23ToolbarAction(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(67.dp).defaultMinSize(minHeight = 48.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = if (enabled) V23Accent else Color(0xFF94A3B8), modifier = Modifier.size(19.dp))
        Text(label, color = if (enabled) V23Text else Color(0xFF94A3B8), fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun V23TextAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 2.dp).defaultMinSize(minWidth = 48.dp, minHeight = 40.dp).clickable(enabled = enabled, onClick = onClick),
        color = Color(0xFFE5EDF3), contentColor = V23Accent, shape = RoundedCornerShape(6.dp)
    ) { Box(contentAlignment = Alignment.Center) { Text(label, color = V23Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold) } }
}

@Composable
internal fun V23StatusBar(status: String, loading: Boolean, format: String, featureCount: Int?, planeCount: Int?) {
    Surface(color = Color(0xFFE9EEF2), contentColor = V23Text, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(status, Modifier.weight(1f), color = V23Text, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            featureCount?.let { Text("  $it operaciones", color = V23Secondary, fontSize = 8.sp) }
            planeCount?.takeIf { it > 0 }?.let { Text("  $it planos", color = V23Secondary, fontSize = 8.sp) }
            Spacer(Modifier.width(8.dp)); Text(format, color = V23Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun V23Tree(
    document: V23Document?, selectionType: V23SelectionType, selectedId: Long, enabled: Boolean,
    onSelect: (V23SelectionType, Long) -> Unit, onShowDock: (V23DockCategory) -> Unit,
    onClose: () -> Unit, modifier: Modifier
) {
    Surface(modifier.fillMaxHeight(), color = V23Panel, contentColor = V23Text) {
        Column {
            Row(Modifier.fillMaxWidth().background(V23Header).padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("FeatureManager", Modifier.weight(1f), color = V23Text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onShowDock(V23DockCategory.SKETCH) }, enabled = enabled, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Add, "Crear", tint = V23Accent) }
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.KeyboardArrowLeft, "Ocultar", tint = V23Accent) }
            }
            HorizontalDivider(color = V23Divider)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(7.dp)) {
                val program = document?.program
                V23TreeRow(document?.name ?: "Sin documento", 0, selectionType == V23SelectionType.DOCUMENT, true, "▣") { onSelect(V23SelectionType.DOCUMENT, 0L) }
                Text("  ▾ Cuerpo1", color = V23Text, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
                if (program == null) {
                    Text("      ${document?.format ?: "Geometría importada"}", color = V23Secondary, fontSize = 8.sp, modifier = Modifier.padding(5.dp))
                } else {
                    Text("      ▾ Origen", color = V23Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    program.planes.filter { it.kind == CadPlaneKind.BASE }.forEach { plane ->
                        V23TreeRow(plane.label, 3, selectionType == V23SelectionType.PLANE && selectedId == plane.id, enabled, if (plane.visible) "▱" else "▭") { onSelect(V23SelectionType.PLANE, plane.id) }
                    }
                    val customPlanes = program.planes.filter { it.kind != CadPlaneKind.BASE }
                    if (customPlanes.isNotEmpty()) {
                        Text("      ▾ Planos de referencia", color = V23Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                        customPlanes.forEach { plane ->
                            V23TreeRow(plane.label, 3, selectionType == V23SelectionType.PLANE && selectedId == plane.id, enabled, if (plane.visible) "▱" else "▭") { onSelect(V23SelectionType.PLANE, plane.id) }
                        }
                    }
                    if (program.sketches.isNotEmpty()) {
                        Text("      ▾ Croquis", color = V23Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                        program.sketches.forEach { sketch ->
                            V23TreeRow(sketch.label, 3, selectionType == V23SelectionType.SKETCH && selectedId == sketch.id, enabled, if (sketch.visible) "⌑" else "□") { onSelect(V23SelectionType.SKETCH, sketch.id) }
                        }
                    }
                    Text("      ▾ Operaciones", color = V23Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                    program.features.forEach { feature ->
                        V23TreeRow(feature.label, 3, selectionType == V23SelectionType.FEATURE && selectedId == feature.id, enabled, if (feature.suppressed) "○" else "◆") { onSelect(V23SelectionType.FEATURE, feature.id) }
                        feature.sketchId?.let { sketchId ->
                            val label = program.sketches.firstOrNull { it.id == sketchId }?.label ?: "Croquis"
                            Text("          ↳ $label", color = V23Secondary, fontSize = 7.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun V23TreeRow(label: String, level: Int, selected: Boolean, enabled: Boolean, icon: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = (level * 7).dp)
            .background(if (selected) V23Selected else Color.Transparent, RoundedCornerShape(5.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 5.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, color = if (enabled) V23Accent else V23Muted, fontSize = 8.sp); Spacer(Modifier.width(5.dp))
        Text(label, color = V23Text, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
