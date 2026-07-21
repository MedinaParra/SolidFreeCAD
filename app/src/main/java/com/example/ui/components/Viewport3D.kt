package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ThreeDRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CadOperation
import com.example.model.CadSketch
import com.example.model.OperationType
import com.example.model.ProjectState
import com.example.model.SketchEntity
import com.example.model.WorkPlane
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val MIN_CAMERA_SCALE = 0.35f
private const val MAX_CAMERA_SCALE = 45f
private const val ORBIT_SENSITIVITY = 0.28f
private const val MAX_PITCH = 82f

/** A point in the small software-rendered CAD scene. */
data class Point3D(val x: Float, val y: Float, val z: Float)

data class ProjectedPoint(val x: Float, val y: Float, val depth: Float)

data class Face3D(
    val points: List<Point3D>,
    val fillColor: Color,
    val edgeColor: Color = Color(0xFF1B365D),
    val isCut: Boolean = false
) {
    fun averageDepth(yaw: Float, pitch: Float): Float {
        if (points.isEmpty()) return 0f
        return points.sumOf { rotatePoint(it, yaw, pitch).z.toDouble() }.toFloat() / points.size
    }
}

private enum class CameraGestureMode {
    ORBIT,
    PAN_ZOOM
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTextApi::class)
@Composable
fun Viewport3D(
    state: ProjectState,
    onUpdateCamera: (yaw: Float, pitch: Float, scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    onAddSketchEntity: (SketchEntity) -> Unit,
    activeSketchTool: String?,
    modifier: Modifier = Modifier
) {
    var drawingStart by remember { mutableStateOf<Offset?>(null) }
    var drawingCurrent by remember { mutableStateOf<Offset?>(null) }
    var snapFeedbackPoint by remember { mutableStateOf<Offset?>(null) }
    var canvasWidth by remember { mutableStateOf(1000f) }
    var canvasHeight by remember { mutableStateOf(1200f) }
    var cameraGestureMode by remember { mutableStateOf<CameraGestureMode?>(null) }

    val latestState by rememberUpdatedState(state)
    val latestCameraCallback by rememberUpdatedState(onUpdateCamera)
    val textMeasurer = rememberTextMeasurer()

    val renderedYaw = if (state.viewMode3D) state.yaw else orthographicYaw(state.activePlane)
    val renderedPitch = if (state.viewMode3D) state.pitch else orthographicPitch(state.activePlane)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF4F7F9), Color(0xFFD5E3ED))
                )
            )
            .onGloballyPositioned {
                canvasWidth = it.size.width.toFloat()
                canvasHeight = it.size.height.toFloat()
            }
            .pointerInput(state.viewMode3D, activeSketchTool) {
                // Sketch tools own one-finger drawing. Navigation is disabled until
                // the tool is released, avoiding accidental camera movement.
                if (!state.viewMode3D && activeSketchTool != null) return@pointerInput

                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)

                    var yaw = latestState.yaw
                    var pitch = latestState.pitch
                    var scale = latestState.scale
                    var offsetX = latestState.offsetX
                    var offsetY = latestState.offsetY
                    var previousPointerCount = 1

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            val pointerCount = pressed.size
                            if (latestState.viewMode3D) {
                                if (pointerCount == 1) {
                                    cameraGestureMode = CameraGestureMode.ORBIT
                                    if (previousPointerCount == 1) {
                                        val change = pressed.first()
                                        val delta = change.position - change.previousPosition
                                        if (delta.distanceSquared() > 0.20f) {
                                            yaw = normalizeYaw(yaw + delta.x * ORBIT_SENSITIVITY)
                                            pitch = (pitch - delta.y * ORBIT_SENSITIVITY)
                                                .coerceIn(-MAX_PITCH, MAX_PITCH)
                                            latestCameraCallback(yaw, pitch, scale, offsetX, offsetY)
                                        }
                                    }
                                } else {
                                    cameraGestureMode = CameraGestureMode.PAN_ZOOM
                                    if (previousPointerCount >= 2) {
                                        val centroid = centroidOf(pressed, usePrevious = false)
                                        val previousCentroid = centroidOf(pressed, usePrevious = true)
                                        val pan = centroid - previousCentroid
                                        val zoom = pinchZoomOf(pressed)
                                        val newScale = (scale * zoom)
                                            .coerceIn(MIN_CAMERA_SCALE, MAX_CAMERA_SCALE)
                                        val ratio = if (scale > 0f) newScale / scale else 1f
                                        val viewportCenter = Offset(canvasWidth / 2f, canvasHeight / 2f)
                                        val oldOffset = Offset(offsetX, offsetY)
                                        val anchor = centroid - viewportCenter

                                        // Keep the model point below the fingers stationary
                                        // while zooming, then apply the two-finger pan.
                                        val anchoredOffset = anchor - (anchor - oldOffset) * ratio
                                        val newOffset = anchoredOffset + pan

                                        scale = newScale
                                        offsetX = newOffset.x
                                        offsetY = newOffset.y
                                        latestCameraCallback(yaw, pitch, scale, offsetX, offsetY)
                                    }
                                }
                            } else {
                                // In sketch mode without an active tool, one finger pans;
                                // two fingers pan and zoom around their centroid.
                                cameraGestureMode = CameraGestureMode.PAN_ZOOM
                                val centroid = centroidOf(pressed, usePrevious = false)
                                val previousCentroid = centroidOf(pressed, usePrevious = true)
                                val pan = centroid - previousCentroid
                                val zoom = if (pointerCount >= 2) pinchZoomOf(pressed) else 1f
                                val newScale = (scale * zoom)
                                    .coerceIn(MIN_CAMERA_SCALE, MAX_CAMERA_SCALE)
                                val ratio = if (scale > 0f) newScale / scale else 1f
                                val viewportCenter = Offset(canvasWidth / 2f, canvasHeight / 2f)
                                val oldOffset = Offset(offsetX, offsetY)
                                val anchor = centroid - viewportCenter
                                val anchoredOffset = anchor - (anchor - oldOffset) * ratio
                                val newOffset = anchoredOffset + pan

                                scale = newScale
                                offsetX = newOffset.x
                                offsetY = newOffset.y
                                latestCameraCallback(0f, 0f, scale, offsetX, offsetY)
                            }

                            event.changes.forEach { it.consume() }
                            previousPointerCount = pointerCount
                        }
                    } finally {
                        cameraGestureMode = null
                    }
                }
            }
            .pointerInteropFilter { event ->
                if (!state.viewMode3D && activeSketchTool != null) {
                    val centerX = canvasWidth / 2f
                    val centerY = canvasHeight / 2f
                    val scaleFactor = state.scale * 10f
                    val activeSketch = state.sketches.firstOrNull { it.id == state.selectedSketchId }

                    fun screenToCad(x: Float, y: Float): Pair<Float, Float> =
                        Pair(
                            (x - centerX - state.offsetX) / scaleFactor,
                            -(y - centerY - state.offsetY) / scaleFactor
                        )

                    fun cadToScreen(x: Float, y: Float): Offset =
                        Offset(
                            x * scaleFactor + centerX + state.offsetX,
                            -y * scaleFactor + centerY + state.offsetY
                        )

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            val raw = screenToCad(event.x, event.y)
                            val snapped = getSnapPoint(raw.first, raw.second, activeSketch)
                            val screen = cadToScreen(snapped.first, snapped.second)
                            drawingStart = screen
                            drawingCurrent = screen
                            snapFeedbackPoint = if (snapped != raw) screen else null
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val raw = screenToCad(event.x, event.y)
                            val snapped = getSnapPoint(raw.first, raw.second, activeSketch)
                            val screen = cadToScreen(snapped.first, snapped.second)
                            drawingCurrent = screen
                            snapFeedbackPoint = if (snapped != raw) screen else null
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            val start = drawingStart
                            val end = drawingCurrent
                            if (start != null && end != null) {
                                val cadStart = screenToCad(start.x, start.y)
                                val cadEnd = screenToCad(end.x, end.y)
                                val dx = cadEnd.first - cadStart.first
                                val dy = cadEnd.second - cadStart.second
                                val distance = sqrt(dx * dx + dy * dy)

                                if (distance > 0.35f) {
                                    when (activeSketchTool) {
                                        "Line" -> onAddSketchEntity(
                                            SketchEntity.Line(
                                                x1 = cadStart.first,
                                                y1 = cadStart.second,
                                                x2 = cadEnd.first,
                                                y2 = cadEnd.second,
                                                label = String.format("%.1f mm", distance)
                                            )
                                        )

                                        "Circle" -> onAddSketchEntity(
                                            SketchEntity.Circle(
                                                cx = cadStart.first,
                                                cy = cadStart.second,
                                                radius = distance,
                                                label = String.format("R%.1f mm", distance)
                                            )
                                        )

                                        "Rectangle" -> onAddSketchEntity(
                                            SketchEntity.Rectangle(
                                                cx = (cadStart.first + cadEnd.first) / 2f,
                                                cy = (cadStart.second + cadEnd.second) / 2f,
                                                width = abs(dx),
                                                height = abs(dy),
                                                label = String.format("%.1f x %.1f mm", abs(dx), abs(dy))
                                            )
                                        )
                                    }
                                }
                            }
                            drawingStart = null
                            drawingCurrent = null
                            snapFeedbackPoint = null
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            drawingStart = null
                            drawingCurrent = null
                            snapFeedbackPoint = null
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val scalePixels = state.scale * 10f

            drawPlaneGrid(
                plane = state.activePlane,
                yaw = renderedYaw,
                pitch = renderedPitch,
                scale = scalePixels,
                centerX = centerX,
                centerY = centerY,
                is3D = state.viewMode3D,
                offsetX = state.offsetX,
                offsetY = state.offsetY
            )

            buildSceneFaces(state)
                .sortedByDescending { it.averageDepth(renderedYaw, renderedPitch) }
                .forEach { face ->
                    val projected = face.points.map {
                        rotateAndProject(
                            it,
                            renderedYaw,
                            renderedPitch,
                            scalePixels,
                            centerX,
                            centerY,
                            state.offsetX,
                            state.offsetY
                        )
                    }
                    if (projected.size >= 3) {
                        val path = Path().apply {
                            moveTo(projected.first().x, projected.first().y)
                            projected.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        }
                        drawPath(path, face.fillColor)
                        drawPath(path, face.edgeColor, style = Stroke(1.6f))
                    }
                }

            drawSketches(
                state = state,
                yaw = renderedYaw,
                pitch = renderedPitch,
                scale = scalePixels,
                centerX = centerX,
                centerY = centerY,
                textMeasurer = textMeasurer
            )

            drawTemporarySketch(
                activeSketchTool,
                drawingStart,
                drawingCurrent,
                scalePixels,
                textMeasurer
            )

            snapFeedbackPoint?.let {
                drawCircle(Color(0xFF2ECC71), 15f, it, style = Stroke(3f))
                drawCircle(Color(0xFF1E9E55), 5f, it)
            }

            drawOriginTriad(renderedYaw, renderedPitch, size.height)
        }

        ViewStatusChip(
            state = state,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )

        cameraGestureMode?.let {
            GestureFeedbackChip(
                mode = it,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            )
        }

        CameraToolbar(
            state = state,
            onUpdateCamera = onUpdateCamera,
            onFit = {
                val fittedScale = calculateFitScale(state, canvasWidth, canvasHeight)
                onUpdateCamera(-45f, 25f, fittedScale, 0f, 0f)
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
        )

        if (state.viewMode3D) {
            ViewPresetBar(
                onPreset = { yaw, pitch ->
                    onUpdateCamera(yaw, pitch, state.scale, 0f, 0f)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 12.dp)
            )
        }

        NavigationHint(
            is3D = state.viewMode3D,
            hasActiveTool = activeSketchTool != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun ViewStatusChip(state: ProjectState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.90f),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBCC9D3))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                if (state.viewMode3D) Icons.Default.ThreeDRotation else Icons.Default.GridOn,
                contentDescription = null,
                tint = Color(0xFF1B365D),
                modifier = Modifier.size(16.dp)
            )
            Text(
                if (state.viewMode3D) {
                    "3D  ${state.scale.times(100).toInt()}%"
                } else {
                    "Croquis ${state.activePlane.name}"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B365D)
            )
        }
    }
}

@Composable
private fun GestureFeedbackChip(mode: CameraGestureMode, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xE6233445),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 4.dp
    ) {
        Text(
            text = if (mode == CameraGestureMode.ORBIT) "Orbitando" else "Mover / Zoom",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CameraToolbar(
    state: ProjectState,
    onUpdateCamera: (Float, Float, Float, Float, Float) -> Unit,
    onFit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CameraIconButton(Icons.Default.Add, "Acercar") {
            onUpdateCamera(
                state.yaw,
                state.pitch,
                (state.scale * 1.20f).coerceAtMost(MAX_CAMERA_SCALE),
                state.offsetX,
                state.offsetY
            )
        }
        CameraIconButton(Icons.Default.Remove, "Alejar") {
            onUpdateCamera(
                state.yaw,
                state.pitch,
                (state.scale / 1.20f).coerceAtLeast(MIN_CAMERA_SCALE),
                state.offsetX,
                state.offsetY
            )
        }
        CameraIconButton(Icons.Default.FilterCenterFocus, "Ajustar a pantalla", onFit)
    }
}

@Composable
private fun CameraIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.92f),
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB6C4CF))
    ) {
        IconButton(onClick) {
            Icon(icon, description, tint = Color(0xFF1B365D), modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun ViewPresetBar(
    onPreset: (yaw: Float, pitch: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.92f),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB6C4CF))
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            PresetButton("ISO") { onPreset(-45f, 25f) }
            PresetButton("F") { onPreset(0f, 0f) }
            PresetButton("S") { onPreset(0f, 90f) }
            PresetButton("D") { onPreset(-90f, 0f) }
        }
    }
}

@Composable
private fun PresetButton(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(width = 38.dp, height = 34.dp),
        shape = RoundedCornerShape(7.dp),
        color = Color(0xFFF0F4F7)
    ) {
        IconButton(onClick) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B365D))
        }
    }
}

@Composable
private fun NavigationHint(
    is3D: Boolean,
    hasActiveTool: Boolean,
    modifier: Modifier = Modifier
) {
    val text = when {
        hasActiveTool -> "Arrastra para dibujar"
        is3D -> "1 dedo: girar  •  2 dedos: mover  •  pellizcar: zoom"
        else -> "1 dedo: mover  •  pellizcar: zoom"
    }
    Surface(
        modifier = modifier.widthIn(max = 330.dp),
        color = Color(0xCCFFFFFF),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x99AAB7C2))
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 9.sp,
            color = Color(0xFF455A64),
            fontWeight = FontWeight.Medium
        )
    }
}

private fun buildSceneFaces(state: ProjectState): List<Face3D> {
    val sketches = state.sketches.associateBy { it.id }
    val result = mutableListOf<Face3D>()

    state.operations.forEach { operation ->
        val sketch = operation.sketchId?.let(sketches::get) ?: return@forEach
        when (operation.type) {
            OperationType.EXTRUDE_BOSS,
            OperationType.EXTRUDE_CUT -> addExtrusionFaces(result, sketch, operation)

            OperationType.REVOLVE_BOSS,
            OperationType.REVOLVE_CUT -> addRevolutionFaces(result, sketch, operation)

            OperationType.FILLET,
            OperationType.SHELL -> Unit
        }
    }
    return result
}

private fun addExtrusionFaces(
    target: MutableList<Face3D>,
    sketch: CadSketch,
    operation: CadOperation
) {
    val isCut = operation.type == OperationType.EXTRUDE_CUT
    val fill = if (isCut) Color(0x66E74C3C) else Color(0xAAA9C9EE)
    val edge = if (isCut) Color(0xFFC0392B) else Color(0xFF234F79)

    sketch.entities.forEach { entity ->
        when (entity) {
            is SketchEntity.Rectangle -> {
                val halfW = entity.width / 2f
                val halfH = entity.height / 2f
                val profile = listOf(
                    Pair(entity.cx - halfW, entity.cy - halfH),
                    Pair(entity.cx + halfW, entity.cy - halfH),
                    Pair(entity.cx + halfW, entity.cy + halfH),
                    Pair(entity.cx - halfW, entity.cy + halfH)
                )
                addExtrudedProfile(target, profile, true, operation.depth, sketch.plane, fill, edge, isCut)
            }

            is SketchEntity.Circle -> {
                val profile = (0 until 24).map { index ->
                    val angle = 2.0 * PI * index / 24.0
                    Pair(
                        entity.cx + entity.radius * cos(angle).toFloat(),
                        entity.cy + entity.radius * sin(angle).toFloat()
                    )
                }
                addExtrudedProfile(target, profile, true, operation.depth, sketch.plane, fill, edge, isCut)
            }

            is SketchEntity.Line -> {
                val profile = listOf(Pair(entity.x1, entity.y1), Pair(entity.x2, entity.y2))
                addExtrudedProfile(target, profile, false, operation.depth, sketch.plane, fill, edge, isCut)
            }
        }
    }
}

private fun addExtrudedProfile(
    target: MutableList<Face3D>,
    profile: List<Pair<Float, Float>>,
    closed: Boolean,
    depth: Float,
    plane: WorkPlane,
    fill: Color,
    edge: Color,
    isCut: Boolean
) {
    if (profile.size < 2) return
    val bottom = profile.map { to3DPoint(it.first, it.second, 0f, plane) }
    val top = profile.map { to3DPoint(it.first, it.second, depth, plane) }

    if (closed && profile.size >= 3) {
        target += Face3D(bottom.reversed(), fill, edge, isCut)
        target += Face3D(top, fill, edge, isCut)
    }

    val segmentCount = if (closed) profile.size else profile.size - 1
    repeat(segmentCount) { index ->
        val next = if (closed) (index + 1) % profile.size else index + 1
        target += Face3D(
            listOf(bottom[index], bottom[next], top[next], top[index]),
            fill,
            edge,
            isCut
        )
    }
}

private fun addRevolutionFaces(
    target: MutableList<Face3D>,
    sketch: CadSketch,
    operation: CadOperation
) {
    val isCut = operation.type == OperationType.REVOLVE_CUT
    val fill = if (isCut) Color(0x66E74C3C) else Color(0xAAB7DCF1)
    val edge = if (isCut) Color(0xFFC0392B) else Color(0xFF245E7A)
    val totalAngle = operation.angle.coerceIn(1f, 360f)
    val steps = max(6, (totalAngle / 15f).toInt())

    sketch.entities.forEach { entity ->
        val (profile, closed) = when (entity) {
            is SketchEntity.Rectangle -> {
                val halfW = entity.width / 2f
                val halfH = entity.height / 2f
                Pair(
                    listOf(
                        Pair(entity.cx - halfW, entity.cy - halfH),
                        Pair(entity.cx + halfW, entity.cy - halfH),
                        Pair(entity.cx + halfW, entity.cy + halfH),
                        Pair(entity.cx - halfW, entity.cy + halfH)
                    ),
                    true
                )
            }

            is SketchEntity.Circle -> Pair(
                (0 until 16).map { index ->
                    val angle = 2.0 * PI * index / 16.0
                    Pair(
                        entity.cx + entity.radius * cos(angle).toFloat(),
                        entity.cy + entity.radius * sin(angle).toFloat()
                    )
                },
                true
            )

            is SketchEntity.Line -> Pair(
                listOf(Pair(entity.x1, entity.y1), Pair(entity.x2, entity.y2)),
                false
            )
        }

        if (profile.size < 2) return@forEach
        repeat(steps) { step ->
            val a1 = Math.toRadians((totalAngle * step / steps).toDouble()).toFloat()
            val a2 = Math.toRadians((totalAngle * (step + 1) / steps).toDouble()).toFloat()
            val ring1 = profile.map {
                rotateAroundAxis(
                    to3DPoint(it.first, it.second, 0f, sketch.plane),
                    a1,
                    operation.axis
                )
            }
            val ring2 = profile.map {
                rotateAroundAxis(
                    to3DPoint(it.first, it.second, 0f, sketch.plane),
                    a2,
                    operation.axis
                )
            }
            val segmentCount = if (closed) profile.size else profile.size - 1
            repeat(segmentCount) { index ->
                val next = if (closed) (index + 1) % profile.size else index + 1
                target += Face3D(
                    listOf(ring1[index], ring1[next], ring2[next], ring2[index]),
                    fill,
                    edge,
                    isCut
                )
            }
        }
    }
}

private fun rotateAroundAxis(point: Point3D, angle: Float, axis: String): Point3D {
    val c = cos(angle)
    val s = sin(angle)
    return if (axis.startsWith("X", ignoreCase = true)) {
        Point3D(point.x, point.y * c - point.z * s, point.y * s + point.z * c)
    } else {
        Point3D(point.x * c + point.z * s, point.y, -point.x * s + point.z * c)
    }
}

private fun DrawScope.drawSketches(
    state: ProjectState,
    yaw: Float,
    pitch: Float,
    scale: Float,
    centerX: Float,
    centerY: Float,
    textMeasurer: TextMeasurer
) {
    state.sketches.forEach { sketch ->
        val active = sketch.id == state.selectedSketchId
        val color = if (active) Color(0xFF005CB8) else Color(0x885E7A8F)
        val stroke = if (active) 3.5f else 1.8f

        fun project(x: Float, y: Float): ProjectedPoint = rotateAndProject(
            to3DPoint(x, y, 0f, sketch.plane),
            yaw,
            pitch,
            scale,
            centerX,
            centerY,
            state.offsetX,
            state.offsetY
        )

        sketch.entities.forEach { entity ->
            when (entity) {
                is SketchEntity.Line -> {
                    val p1 = project(entity.x1, entity.y1)
                    val p2 = project(entity.x2, entity.y2)
                    drawLine(color, Offset(p1.x, p1.y), Offset(p2.x, p2.y), stroke)
                    if (!state.viewMode3D && active && entity.label.isNotBlank()) {
                        drawDimensionLine(Offset(p1.x, p1.y), Offset(p2.x, p2.y), entity.label, textMeasurer)
                    }
                }

                is SketchEntity.Rectangle -> {
                    val halfW = entity.width / 2f
                    val halfH = entity.height / 2f
                    val points = listOf(
                        project(entity.cx - halfW, entity.cy - halfH),
                        project(entity.cx + halfW, entity.cy - halfH),
                        project(entity.cx + halfW, entity.cy + halfH),
                        project(entity.cx - halfW, entity.cy + halfH)
                    )
                    points.indices.forEach { index ->
                        val next = (index + 1) % points.size
                        drawLine(
                            color,
                            Offset(points[index].x, points[index].y),
                            Offset(points[next].x, points[next].y),
                            stroke
                        )
                    }
                    if (!state.viewMode3D && active) {
                        drawDimensionLine(
                            Offset(points[0].x, points[0].y),
                            Offset(points[1].x, points[1].y),
                            String.format("%.1f mm", entity.width),
                            textMeasurer
                        )
                    }
                }

                is SketchEntity.Circle -> {
                    val samples = (0..40).map { index ->
                        val angle = 2.0 * PI * index / 40.0
                        project(
                            entity.cx + entity.radius * cos(angle).toFloat(),
                            entity.cy + entity.radius * sin(angle).toFloat()
                        )
                    }
                    val path = Path().apply {
                        moveTo(samples.first().x, samples.first().y)
                        samples.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, color, style = Stroke(stroke))
                    val center = project(entity.cx, entity.cy)
                    drawCircle(color, 3.5f, Offset(center.x, center.y))
                    if (!state.viewMode3D && active && entity.label.isNotBlank()) {
                        val left = project(entity.cx - entity.radius, entity.cy)
                        val right = project(entity.cx + entity.radius, entity.cy)
                        drawDimensionLine(
                            Offset(left.x, left.y),
                            Offset(right.x, right.y),
                            entity.label,
                            textMeasurer
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawTemporarySketch(
    activeTool: String?,
    start: Offset?,
    current: Offset?,
    scale: Float,
    textMeasurer: TextMeasurer
) {
    if (activeTool == null || start == null || current == null) return
    val dx = current.x - start.x
    val dy = current.y - start.y
    val screenDistance = sqrt(dx * dx + dy * dy)
    val cadDistance = screenDistance / scale
    val orange = Color(0xFFE67E22)
    val dash = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))

    when (activeTool) {
        "Line" -> {
            drawLine(orange, start, current, 3f, pathEffect = dash)
            drawTextCentered(String.format("%.1f mm", cadDistance), current + Offset(12f, -18f), textMeasurer)
        }

        "Circle" -> {
            drawCircle(orange, screenDistance, start, style = Stroke(3f, pathEffect = dash))
            drawLine(orange, start, current, 1.5f)
            drawTextCentered(String.format("R %.1f mm", cadDistance), current + Offset(12f, -18f), textMeasurer)
        }

        "Rectangle" -> {
            val topLeft = Offset(min(start.x, current.x), min(start.y, current.y))
            drawRect(
                orange,
                topLeft,
                Size(abs(dx), abs(dy)),
                style = Stroke(3f, pathEffect = dash)
            )
            drawTextCentered(
                String.format("%.1f x %.1f mm", abs(dx) / scale, abs(dy) / scale),
                current + Offset(12f, -18f),
                textMeasurer
            )
        }
    }
}

private fun DrawScope.drawPlaneGrid(
    plane: WorkPlane,
    yaw: Float,
    pitch: Float,
    scale: Float,
    centerX: Float,
    centerY: Float,
    is3D: Boolean,
    offsetX: Float,
    offsetY: Float
) {
    val extent = 180f
    val step = 10f
    val count = (extent / step).toInt()

    fun project(x: Float, y: Float): Offset {
        val projected = rotateAndProject(
            to3DPoint(x, y, 0f, plane),
            yaw,
            pitch,
            scale,
            centerX,
            centerY,
            offsetX,
            offsetY
        )
        return Offset(projected.x, projected.y)
    }

    for (index in -count..count) {
        val value = index * step
        val major = index % 5 == 0
        val color = when {
            index == 0 -> Color(0xFF708090)
            major -> Color(0x66899BA8)
            else -> Color(0x33899BA8)
        }
        val width = if (index == 0) 1.8f else if (major) 1.1f else 0.7f
        drawLine(color, project(value, -extent), project(value, extent), width)
        drawLine(color, project(-extent, value), project(extent, value), width)
    }

    if (is3D) {
        val origin = rotateAndProject(Point3D(0f, 0f, 0f), yaw, pitch, scale, centerX, centerY, offsetX, offsetY)
        drawCircle(Color(0xFF263238), 3f, Offset(origin.x, origin.y))
    }
}

private fun DrawScope.drawOriginTriad(yaw: Float, pitch: Float, height: Float) {
    val origin = Offset(42f, height - 46f)
    val length = 28f
    val axes = listOf(
        Pair(Point3D(1f, 0f, 0f), Color(0xFFD32F2F)),
        Pair(Point3D(0f, 1f, 0f), Color(0xFF2E7D32)),
        Pair(Point3D(0f, 0f, 1f), Color(0xFF1565C0))
    )
    axes.forEach { (axis, color) ->
        val rotated = rotatePoint(axis, yaw, pitch)
        val end = origin + Offset(rotated.x * length, -rotated.y * length)
        drawLine(color, origin, end, 3f)
        drawCircle(color, 3.5f, end)
    }
    drawCircle(Color(0xFF263238), 4f, origin)
}

private fun DrawScope.drawDimensionLine(
    start: Offset,
    end: Offset,
    label: String,
    textMeasurer: TextMeasurer
) {
    val direction = end - start
    val length = sqrt(direction.x * direction.x + direction.y * direction.y)
    if (length < 1f) return
    val normal = Offset(-direction.y / length, direction.x / length)
    val offset = normal * 18f
    val a = start + offset
    val b = end + offset
    drawLine(Color(0xFF37474F), a, b, 1.2f)
    drawLine(Color(0xFF37474F), start, a, 0.8f)
    drawLine(Color(0xFF37474F), end, b, 0.8f)
    drawTextCentered(label, (a + b) / 2f + normal * 9f, textMeasurer)
}

private fun DrawScope.drawTextCentered(
    text: String,
    position: Offset,
    textMeasurer: TextMeasurer
) {
    val layout = textMeasurer.measure(
        text = text,
        style = androidx.compose.ui.text.TextStyle(
            color = Color(0xFF263238),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            background = Color(0xCCFFFFFF)
        )
    )
    drawText(
        layout,
        topLeft = position - Offset(layout.size.width / 2f, layout.size.height / 2f)
    )
}

fun rotateAndProject(
    point: Point3D,
    yaw: Float,
    pitch: Float,
    scale: Float,
    centerX: Float,
    centerY: Float,
    offsetX: Float = 0f,
    offsetY: Float = 0f
): ProjectedPoint {
    val rotated = rotatePoint(point, yaw, pitch)
    return ProjectedPoint(
        x = rotated.x * scale + centerX + offsetX,
        y = -rotated.y * scale + centerY + offsetY,
        depth = rotated.z
    )
}

private fun rotatePoint(point: Point3D, yaw: Float, pitch: Float): Point3D {
    val yawRadians = Math.toRadians(yaw.toDouble()).toFloat()
    val pitchRadians = Math.toRadians(pitch.toDouble()).toFloat()
    val yawCos = cos(yawRadians)
    val yawSin = sin(yawRadians)
    val pitchCos = cos(pitchRadians)
    val pitchSin = sin(pitchRadians)

    val x1 = point.x * yawCos - point.z * yawSin
    val z1 = point.x * yawSin + point.z * yawCos
    val y2 = point.y * pitchCos - z1 * pitchSin
    val z2 = point.y * pitchSin + z1 * pitchCos
    return Point3D(x1, y2, z2)
}

fun to3DPoint(x: Float, y: Float, zOffset: Float, plane: WorkPlane): Point3D = when (plane) {
    WorkPlane.XY -> Point3D(x, y, zOffset)
    WorkPlane.XZ -> Point3D(x, zOffset, y)
    WorkPlane.YZ -> Point3D(zOffset, x, y)
}

fun getSnapPoint(
    x: Float,
    y: Float,
    activeSketch: CadSketch?,
    snapThreshold: Float = 2f
): Pair<Float, Float> {
    if (activeSketch == null) return Pair(x, y)
    var closest: Pair<Float, Float>? = null
    var distance = Float.MAX_VALUE

    activeSketch.entities.forEach { entity ->
        val candidates = when (entity) {
            is SketchEntity.Line -> listOf(Pair(entity.x1, entity.y1), Pair(entity.x2, entity.y2))
            is SketchEntity.Rectangle -> {
                val halfW = entity.width / 2f
                val halfH = entity.height / 2f
                listOf(
                    Pair(entity.cx - halfW, entity.cy - halfH),
                    Pair(entity.cx + halfW, entity.cy - halfH),
                    Pair(entity.cx + halfW, entity.cy + halfH),
                    Pair(entity.cx - halfW, entity.cy + halfH)
                )
            }
            is SketchEntity.Circle -> listOf(Pair(entity.cx, entity.cy))
        }
        candidates.forEach { candidate ->
            val dx = candidate.first - x
            val dy = candidate.second - y
            val candidateDistance = sqrt(dx * dx + dy * dy)
            if (candidateDistance < distance) {
                distance = candidateDistance
                closest = candidate
            }
        }
    }
    return if (closest != null && distance <= snapThreshold) closest!! else Pair(x, y)
}

private fun calculateFitScale(state: ProjectState, width: Float, height: Float): Float {
    var maxExtent = 20f
    state.sketches.forEach { sketch ->
        sketch.entities.forEach { entity ->
            when (entity) {
                is SketchEntity.Line -> {
                    maxExtent = max(maxExtent, max(abs(entity.x1), abs(entity.x2)))
                    maxExtent = max(maxExtent, max(abs(entity.y1), abs(entity.y2)))
                }
                is SketchEntity.Circle -> {
                    maxExtent = max(maxExtent, abs(entity.cx) + entity.radius)
                    maxExtent = max(maxExtent, abs(entity.cy) + entity.radius)
                }
                is SketchEntity.Rectangle -> {
                    maxExtent = max(maxExtent, abs(entity.cx) + entity.width / 2f)
                    maxExtent = max(maxExtent, abs(entity.cy) + entity.height / 2f)
                }
            }
        }
    }
    state.operations.forEach { maxExtent = max(maxExtent, abs(it.depth)) }
    val usable = min(width, height) * 0.34f
    return (usable / (maxExtent * 10f)).coerceIn(MIN_CAMERA_SCALE, MAX_CAMERA_SCALE)
}

private fun centroidOf(changes: List<PointerInputChange>, usePrevious: Boolean): Offset {
    if (changes.isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    changes.forEach {
        val position = if (usePrevious) it.previousPosition else it.position
        x += position.x
        y += position.y
    }
    return Offset(x / changes.size, y / changes.size)
}

private fun pinchZoomOf(changes: List<PointerInputChange>): Float {
    if (changes.size < 2) return 1f
    val currentCenter = centroidOf(changes, usePrevious = false)
    val previousCenter = centroidOf(changes, usePrevious = true)
    var currentRadius = 0f
    var previousRadius = 0f
    changes.forEach {
        currentRadius += (it.position - currentCenter).distance()
        previousRadius += (it.previousPosition - previousCenter).distance()
    }
    currentRadius /= changes.size
    previousRadius /= changes.size
    if (previousRadius < 0.5f) return 1f
    return (currentRadius / previousRadius).coerceIn(0.85f, 1.18f)
}

internal fun normalizeYaw(value: Float): Float {
    var normalized = value % 360f
    if (normalized > 180f) normalized -= 360f
    if (normalized < -180f) normalized += 360f
    return normalized
}

private fun Offset.distance(): Float = sqrt(x * x + y * y)
private fun Offset.distanceSquared(): Float = x * x + y * y

private fun orthographicYaw(plane: WorkPlane): Float = when (plane) {
    WorkPlane.XY -> 0f
    WorkPlane.XZ -> 0f
    WorkPlane.YZ -> -90f
}

private fun orthographicPitch(plane: WorkPlane): Float = when (plane) {
    WorkPlane.XY -> 0f
    WorkPlane.XZ -> 90f
    WorkPlane.YZ -> 0f
}
