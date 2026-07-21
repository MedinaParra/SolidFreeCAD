package com.example.faceui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.features.BasicCadFeature
import com.example.features.BasicCadProgram
import com.example.features.CadReferencePlane
import com.example.features.CadSketch
import java.util.Locale

@Composable
internal fun V27SelectionFilterBar(
    mode: CadViewportSelectionModeV27,
    multiple: Boolean,
    selectionCount: Int,
    enabled: Boolean,
    onMode: (CadViewportSelectionModeV27) -> Unit,
    onMultiple: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xF7FFFFFF),
        contentColor = V24Text,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 5.dp
    ) {
        Row(
            Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("SELECCIÓN", fontWeight = FontWeight.Bold, fontSize = 8.sp, color = V24Secondary)
            CadViewportSelectionModeV27.entries.forEach { candidate ->
                FilterChip(
                    selected = mode == candidate,
                    onClick = { onMode(candidate) },
                    enabled = enabled,
                    label = { Text(candidate.label, fontSize = 8.sp) },
                    modifier = Modifier.height(30.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = V24Accent,
                        selectedLabelColor = Color.White
                    )
                )
            }
            FilterChip(
                selected = multiple,
                onClick = { onMultiple(!multiple) },
                enabled = enabled,
                label = {
                    Text(
                        if (selectionCount > 0) "Múltiple · $selectionCount" else "Múltiple",
                        fontSize = 8.sp
                    )
                },
                modifier = Modifier.height(30.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF176B87),
                    selectedLabelColor = Color.White
                )
            )
            IconButton(
                onClick = onClear,
                enabled = enabled && selectionCount > 0,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.Clear, "Limpiar selección", tint = V24Accent, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
internal fun V27Properties(
    topologySelection: CadTopologySelectionSetV29,
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
    val topology = topologySelection.active
    if (topology == null) {
        V25Properties(
            null,
            feature,
            plane,
            sketch,
            program,
            enabled,
            onApplyFeature,
            onToggleFeature,
            onEditSketch,
            onSelectPlane,
            onTogglePlane,
            onCreateOffset,
            onCreateFace,
            onClose,
            modifier
        )
        return
    }

    Surface(modifier.fillMaxHeight(), color = V24Panel, contentColor = V24Text) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(V24Header).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (topologySelection.isMultiple) "PROPIEDADES DE SELECCIÓN" else "PROPIEDADES TOPOLOGÍA",
                    Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowRight, "Ocultar", tint = V24Accent)
                }
            }
            HorizontalDivider(color = V24Divider)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (topologySelection.isMultiple) {
                    MultipleSelectionSummary(topologySelection)
                    HorizontalDivider(color = V24Divider)
                    Text("ELEMENTO ACTIVO", color = V24Secondary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
                SingleTopologyProperties(topology, enabled, onCreateFace)
            }
        }
    }
}

@Composable
private fun MultipleSelectionSummary(selection: CadTopologySelectionSetV29) {
    Text("${selection.size} elementos seleccionados", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Metric("Composición", buildString {
        append("${selection.faceCount} cara(s)")
        append(" · ${selection.edgeCount} arista(s)")
        append(" · ${selection.vertexCount} vértice(s)")
        append(" · ${selection.loopCount} bucle(s)")
    })
    if (selection.faceCount > 0) Metric("Área acumulada", "${v27Number(selection.approximateArea)} mm²")
    if (selection.edgeCount > 0) Metric("Longitud de aristas", "${v27Number(selection.totalEdgeLength)} mm")
    if (selection.loopCount > 0) Metric("Perímetro de bucles", "${v27Number(selection.totalLoopPerimeter)} mm")
    Text(
        "El elemento activo se muestra con mayor intensidad. Los demás permanecen resaltados como selección secundaria.",
        color = V24Secondary,
        fontSize = 8.sp
    )
}

@Composable
private fun SingleTopologyProperties(
    topology: CadViewportSelectionV27,
    enabled: Boolean,
    onCreateFace: () -> Unit
) {
    when (topology) {
        is CadViewportFaceSelectionV27 -> {
            Text(if (topology.planar) "Cara plana" else "Superficie curva", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TopologyId(topology.id)
            Metric("Área aproximada", "${v27Number(topology.approximateArea)} mm²")
            Metric("Normal", v25Normal(topology.normal))
            Metric("Triángulos", topology.triangleOrdinals.size.toString())
            Metric("Bucles de borde", topology.boundaryLoopCount.toString())
            if (topology.planar) {
                Metric("Regiones exteriores", topology.outerLoopCount.toString())
                Metric("Regiones interiores", topology.innerLoopCount.toString())
                Button(onClick = onCreateFace, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Plano paralelo a esta cara")
                }
                Text(
                    "La cara puede usarse como soporte de croquis desde la bandeja inferior.",
                    color = V24Secondary,
                    fontSize = 8.sp
                )
            }
        }
        is CadViewportEdgeSelectionV27 -> {
            Text("Arista", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TopologyId(topology.id)
            Metric("Longitud", "${v27Number(topology.length)} mm")
            Metric("Inicio", v27Point(topology.start))
            Metric("Fin", v27Point(topology.end))
            Metric(
                "Tipo",
                when {
                    topology.boundary -> "Borde abierto"
                    topology.sharp -> "Arista viva"
                    else -> "Transición"
                }
            )
            Text(
                "Referencia preparada para eje, redondeo, chaflán y Move contextual.",
                color = V24Secondary,
                fontSize = 8.sp
            )
        }
        is CadViewportVertexSelectionV27 -> {
            Text("Vértice", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TopologyId(topology.id)
            Metric("Coordenadas", v27Point(topology.point))
            Metric("Aristas incidentes", topology.incidentFeatureEdges.toString())
            Text(
                "Referencia preparada para medida, anclaje y planos derivados.",
                color = V24Secondary,
                fontSize = 8.sp
            )
        }
        is CadViewportLoopSelectionV27 -> {
            Text(topology.role.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            TopologyId(topology.id)
            Metric("Perímetro", "${v27Number(topology.perimeter)} mm")
            Metric("Aristas", topology.edgeIds.size.toString())
            Metric("Normal asociada", v25Normal(topology.normal))
            Metric("Estado", if (topology.closed) "Región cerrada" else "Cadena abierta")
            if (topology.closed) {
                Metric("Área firmada", "${v27Number(topology.signedArea)} mm²")
                Metric("Profundidad de anidamiento", topology.nestingDepth.toString())
            }
            Text(
                when (topology.role) {
                    CadLoopRoleV29.OUTER -> "La región exterior se resalta en verde."
                    CadLoopRoleV29.INNER -> "La región interior o agujero se resalta en magenta."
                    CadLoopRoleV29.OPEN -> "La cadena abierta se resalta en naranja."
                },
                color = V24Secondary,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun TopologyId(value: String) {
    Text("ID: $value", color = V24Secondary, fontSize = 7.sp)
}

@Composable
private fun Metric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label.uppercase(Locale.ROOT), color = V24Secondary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 10.sp)
    }
}

internal fun v27Point(value: FloatArray): String = "(%.3f, %.3f, %.3f)".format(
    Locale.US,
    value.getOrElse(0) { 0f },
    value.getOrElse(1) { 0f },
    value.getOrElse(2) { 0f }
)

internal fun v27Number(value: Float): String = "%.3f".format(Locale.US, value)
