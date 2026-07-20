package com.example.faceui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.nativecad.viewer.CameraPreset

@Composable
internal fun V23TopBar(
    documentName: String,
    loading: Boolean,
    treeVisible: Boolean,
    rightVisible: Boolean,
    drawerVisible: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTools: () -> Unit,
    onFit: () -> Unit,
    onPreset: (CameraPreset) -> Unit,
    onToggleTree: () -> Unit,
    onToggleRight: () -> Unit
) {
    Surface(color = Color(0xFFF1F3F5), contentColor = V23Text, shadowElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(62.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = Color(0xFFD32F2F), shape = RoundedCornerShape(4.dp)) {
                Text("SF", Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = Color.White, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(7.dp))
            Column(Modifier.widthIn(max = 170.dp)) {
                Text("SolidFreeCAD", color = V23Text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(documentName, color = V23Secondary, fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                V23ToolbarAction("Nuevo", Icons.Default.Add, !loading, onNew)
                V23ToolbarAction("Abrir", Icons.Default.FolderOpen, !loading, onOpen)
                V23ToolbarAction("Deshacer", Icons.Default.Undo, canUndo, onUndo)
                V23ToolbarAction("Rehacer", Icons.Default.Redo, canRedo, onRedo)
                V23ToolbarAction(
                    if (drawerVisible) "Ocultar herramientas" else "Herramientas",
                    if (drawerVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    !loading,
                    onTools
                )
                V23ToolbarAction("Encuadrar", Icons.Default.FilterCenterFocus, true, onFit)
                V23TextAction("ISO", true) { onPreset(CameraPreset.ISOMETRIC) }
                V23TextAction("Frente", true) { onPreset(CameraPreset.FRONT) }
                V23TextAction("Planta", true) { onPreset(CameraPreset.TOP) }
            }
            IconButton(onClick = onToggleTree, enabled = !loading, modifier = Modifier.size(44.dp)) {
                Icon(if (treeVisible) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight, "Árbol", tint = if (loading) V23Muted else V23Accent)
            }
            IconButton(onClick = onToggleRight, enabled = !loading, modifier = Modifier.size(44.dp)) {
                Icon(if (rightVisible) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowLeft, "Propiedades", tint = if (loading) V23Muted else V23Accent)
            }
        }
    }
}

@Composable
internal fun V23ToolbarAction(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(76.dp).defaultMinSize(minHeight = 48.dp).clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(18.dp), tint = if (enabled) V23Accent else Color(0xFF94A3B8))
        Text(label, color = if (enabled) V23Accent else Color(0xFF94A3B8), fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun V23TextAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 2.dp).defaultMinSize(minWidth = 48.dp, minHeight = 38.dp).clickable(enabled = enabled, onClick = onClick),
        color = Color(0xFFE3EDF3), contentColor = V23Accent, shape = RoundedCornerShape(5.dp)
    ) { Box(contentAlignment = Alignment.Center) { Text(label, color = V23Accent, fontSize = 8.sp, fontWeight = FontWeight.Bold) } }
}

@Composable
internal fun V23StatusBar(status: String, loading: Boolean, format: String, featureCount: Int?, sketchCount: Int?, planeCount: Int?) {
    Surface(color = Color(0xFFE9EEF2), contentColor = V23Text, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(status, Modifier.weight(1f), color = V23Text, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            featureCount?.let { Text("$it op.", color = V23Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            sketchCount?.let { Spacer(Modifier.width(7.dp)); Text("$it croq.", color = V23Secondary, fontSize = 8.sp) }
            planeCount?.let { Spacer(Modifier.width(7.dp)); Text("$it planos", color = V23Secondary, fontSize = 8.sp) }
            Spacer(Modifier.width(8.dp)); Text(format, color = V23Secondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun V23Tree(
    document: V23Document?, selection: V23Selection, enabled: Boolean,
    onSelect: (V23Selection) -> Unit, onOpenDrawer: (V23DrawerTab) -> Unit,
    onClose: () -> Unit, modifier: Modifier
) {
    Surface(modifier.fillMaxHeight(), color = V23Panel, contentColor = V23Text) {
        Column {
            Row(Modifier.fillMaxWidth().background(V23Header).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("FeatureManager", Modifier.weight(1f), color = V23Text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onOpenDrawer(V23DrawerTab.CROQUIS) }, enabled = enabled, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Add, "Añadir", tint = if (enabled) V23Accent else V23Muted)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.KeyboardArrowLeft, "Ocultar", tint = V23Accent) }
            }
            HorizontalDivider(color = V23Divider)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp)) {
                Text(document?.name ?: "Sin documento", color = V23Text, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val program = document?.program
                if (program == null) {
                    V23TreeRow(document?.format ?: "Geometría importada", "◆", selection.kind == V23NodeKind.IMPORTED, enabled) {
                        onSelect(V23Selection(V23NodeKind.IMPORTED))
                    }
                    Text("Sin historial paramétrico reconstruido", color = V23Muted, fontSize = 7.sp, modifier = Modifier.padding(start = 18.dp, top = 3.dp))
                    return@Column
                }
                V23TreeRow("Cuerpo1", "▾", selection.kind == V23NodeKind.BODY, enabled) { onSelect(V23Selection(V23NodeKind.BODY)) }
                Text("    ▾ Origen", color = V23Text, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                program.planes.forEach { plane ->
                    val active = plane.id == program.activePlaneId
                    V23TreeRow(
                        plane.label + if (active) "  [activo]" else "",
                        if (plane.visible) "▱" else "▰",
                        selection.kind == V23NodeKind.PLANE && selection.id == plane.id,
                        enabled,
                        22.dp,
                        if (active) V23Accent else V23Text
                    ) { onSelect(V23Selection(V23NodeKind.PLANE, plane.id)) }
                }
                HorizontalDivider(color = V23Divider.copy(alpha = 0.55f), modifier = Modifier.padding(vertical = 5.dp))
                val linkedSketchIds = program.features.mapNotNull { it.sketchId }.toSet()
                program.features.forEach { feature ->
                    V23TreeRow(feature.label, if (feature.suppressed) "○" else "◆", selection.kind == V23NodeKind.FEATURE && selection.id == feature.id, enabled, 8.dp, if (feature.suppressed) V23Muted else V23Text) {
                        onSelect(V23Selection(V23NodeKind.FEATURE, feature.id))
                    }
                    feature.sketchId?.let { sketchId ->
                        program.sketch(sketchId)?.let { sketch ->
                            V23TreeRow(sketch.label, if (sketch.visible) "(-)" else "( )", selection.kind == V23NodeKind.SKETCH && selection.id == sketch.id, enabled, 30.dp, V23Secondary) {
                                onSelect(V23Selection(V23NodeKind.SKETCH, sketch.id))
                            }
                        }
                    }
                }
                program.sketches.filterNot { it.id in linkedSketchIds }.forEach { sketch ->
                    V23TreeRow(sketch.label, if (sketch.visible) "(-)" else "( )", selection.kind == V23NodeKind.SKETCH && selection.id == sketch.id, enabled, 8.dp, V23Secondary) {
                        onSelect(V23Selection(V23NodeKind.SKETCH, sketch.id))
                    }
                }
            }
        }
    }
}

@Composable
internal fun V23TreeRow(
    label: String,
    prefix: String,
    selected: Boolean,
    enabled: Boolean,
    indent: androidx.compose.ui.unit.Dp = 0.dp,
    color: Color = V23Text,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(start = indent).background(if (selected) V23Selected else Color.Transparent, RoundedCornerShape(4.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 5.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(prefix, color = if (enabled) V23Accent else V23Muted, fontSize = 8.sp)
        Spacer(Modifier.width(5.dp))
        Text(label, color = color, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
