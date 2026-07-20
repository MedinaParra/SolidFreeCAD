package com.example.faceui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.features.BasicCadOperation
import com.example.features.BasicCadProgram
import com.example.features.CadSketchProfile
import kotlin.math.abs

@Composable
internal fun V23Viewport(
    controller: V23WorkbenchController,
    program: BasicCadProgram?,
    treeVisible: Boolean,
    rightVisible: Boolean,
    dockVisible: Boolean,
    planarPickMode: Boolean,
    selectedPlaneId: Long?,
    onSurfaceReady: (FaceDrivenCadSurfaceView) -> Unit,
    onShowTree: () -> Unit,
    onShowRight: () -> Unit,
    onShowDock: () -> Unit,
    onPlanarFacePicked: (PlanarFacePick?) -> Unit
) {
    val directCylinder = v23DirectCylinder(program)
    val firstFeature = program?.features?.firstOrNull()
    val firstSketch = firstFeature?.sketchId?.let { id -> program.sketches.firstOrNull { it.id == id } }
    val overlays = program?.let { active ->
        val size = (controller.document?.mesh?.maxDimension ?: 40f).coerceAtLeast(20f) * 1.25f
        active.planes.mapNotNull { plane ->
            if (!plane.visible && plane.id != selectedPlaneId) return@mapNotNull null
            runCatching { active.resolvePlane(plane.id).toOverlay(selectedPlaneId, size) }.getOrNull()
        }
    }.orEmpty()

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                FaceDrivenCadSurfaceView(context).also { view ->
                    controller.surface = view
                    onSurfaceReady(view)
                }
            },
            update = { view ->
                view.onFaceSelected = { face ->
                    if (!controller.loading && directCylinder) {
                        controller.selectedFace = face
                        view.setSelectedFace(face)
                        controller.setStatus(
                            when (face) {
                                EditableCadFace.TOP -> "Cara superior: arrastre la flecha para cambiar profundidad"
                                EditableCadFace.SIDE -> "Cara curva: arrastre la flecha para cambiar diámetro"
                                EditableCadFace.NONE -> "Selección cancelada"
                            }
                        )
                    } else if (!controller.loading) {
                        controller.selectedFace = EditableCadFace.NONE
                        view.setSelectedFace(EditableCadFace.NONE)
                        controller.setStatus("Seleccione una operación en el árbol o una herramienta en la bandeja inferior")
                    }
                }
                view.onParameterPreview = { face, value ->
                    if (!controller.loading) {
                        controller.setStatus(
                            when (face) {
                                EditableCadFace.TOP -> "Profundidad: ${v23Mm(value.toDouble())}"
                                EditableCadFace.SIDE -> "Diámetro: ${v23Mm(value.toDouble())}"
                                EditableCadFace.NONE -> controller.status
                            }
                        )
                    }
                }
                view.onParameterCommit = { face, value ->
                    if (!controller.loading && directCylinder && program != null && firstFeature != null) {
                        val diameter = if (face == EditableCadFace.SIDE) value.toDouble()
                        else firstSketch?.parameters?.get("diameter") ?: firstFeature.parameters["diameter"] ?: 34.93
                        val depth = if (face == EditableCadFace.TOP) value.toDouble()
                        else firstFeature.parameters["depth"] ?: 40.0
                        runCatching { program.updateBaseCylinder(diameter, depth) }
                            .onSuccess {
                                controller.commit(it, false, "${firstFeature.label} actualizado desde la cara", V23SelectionType.FEATURE, firstFeature.id)
                            }
                            .onFailure { controller.reportError(v23Failure(it)) }
                    }
                }
                view.onPlanarFacePicked = onPlanarFacePicked
                controller.document?.let { document ->
                    if (view.tag != document.token) {
                        view.tag = document.token
                        view.setMesh(document.mesh, document.fit)
                    }
                }
                val depth = firstFeature?.parameters?.get("depth") ?: 40.0
                val diameter = firstSketch?.parameters?.get("diameter") ?: firstFeature?.parameters?.get("diameter") ?: 34.93
                view.setDimensions(depth.toFloat(), diameter.toFloat())
                view.setReferencePlanes(overlays)
                view.setPlanarFacePickMode(planarPickMode)
                view.setSelectedFace(if (directCylinder && !controller.loading && !planarPickMode) controller.selectedFace else EditableCadFace.NONE)
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(visible = !treeVisible, modifier = Modifier.align(Alignment.CenterStart), enter = fadeIn() + slideInHorizontally(initialOffsetX = { -it }), exit = fadeOut() + slideOutHorizontally(targetOffsetX = { -it })) {
            V23ViewportHandle("Árbol", "▶", onShowTree, right = false)
        }
        AnimatedVisibility(visible = !rightVisible, modifier = Modifier.align(Alignment.CenterEnd), enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }), exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })) {
            V23ViewportHandle("Prop.", "◀", onShowRight, right = true)
        }
        if (!dockVisible) {
            Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).clickable(onClick = onShowDock), color = V23Accent, contentColor = Color.White, shape = RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp), shadowElevation = 5.dp) {
                Text("▲  HERRAMIENTAS CAD", Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (dockVisible) 206.dp else 48.dp), color = Color.White.copy(alpha = 0.93f), contentColor = V23Secondary, shape = RoundedCornerShape(7.dp), shadowElevation = 2.dp) {
            Text(when {
                planarPickMode -> "Toque una cara plana para crear un plano paralelo"
                directCylinder -> "Toque una cara y arrastre la flecha amarilla"
                else -> "Órbita: 1 dedo · paneo: 2 dedos · zoom: pellizco"
            }, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = if (planarPickMode) Color(0xFF8A4B08) else V23Secondary, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        }
        AnimatedVisibility(visible = controller.loading, modifier = Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Surface(modifier = Modifier.padding(top = 12.dp).widthIn(min = 230.dp, max = 360.dp), color = Color.White, contentColor = V23Text, shape = RoundedCornerShape(8.dp), shadowElevation = 6.dp) {
                Column {
                    LinearProgressIndicator(Modifier.widthIn(min = 230.dp, max = 360.dp))
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("FreeCAD está recalculando el BRep", color = V23Text, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        controller.error?.let { message -> V23ErrorCard(message, controller::clearError, Modifier.align(Alignment.BottomStart).padding(12.dp)) }
    }
}

@Composable
private fun V23ViewportHandle(label: String, arrow: String, onClick: () -> Unit, right: Boolean) {
    Surface(modifier = Modifier.clickable(onClick = onClick), color = V23Accent, contentColor = Color.White, shape = if (right) RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp) else RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp), shadowElevation = 4.dp) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(arrow, color = Color.White, fontWeight = FontWeight.Bold)
            Text(label, color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun v23DirectCylinder(program: BasicCadProgram?): Boolean {
    val feature = program?.features?.singleOrNull() ?: return false
    if (feature.operation != BasicCadOperation.BOSS_EXTRUDE) return false
    val sketch = feature.sketchId?.let { id -> program.sketches.firstOrNull { it.id == id } } ?: return false
    if (sketch.profile != CadSketchProfile.CIRCLE) return false
    val plane = runCatching { program.resolvePlane(sketch.planeId) }.getOrNull() ?: return false
    return plane.normal.z > 0.999 && abs(plane.normal.x) < 0.001 && abs(plane.normal.y) < 0.001 && abs(plane.origin.x) < 0.001 && abs(plane.origin.y) < 0.001 && abs(plane.origin.z) < 0.001
}
