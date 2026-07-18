package com.example.ui.components

import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import kotlin.math.*

// 3D Point Representation
data class Point3D(val x: Float, val y: Float, val z: Float)

// 2D Screen Point with Depth for Painter's Algorithm
data class ProjectedPoint(val x: Float, val y: Float, val depth: Float)

// Polygons/Faces to render in 3D
data class Face3D(
    val points: List<Point3D>,
    val fillColor: Color,
    val edgeColor: Color = Color(0xFF1B365D),
    val isCut: Boolean = false,
    val label: String = ""
) {
    fun getAverageDepth(yaw: Float, pitch: Float): Float {
        var sumDepth = 0f
        val radX = Math.toRadians(pitch.toDouble())
        val radY = Math.toRadians(yaw.toDouble())

        for (p in points) {
            // Yaw Rotation (Y-Axis)
            val x1 = p.x * cos(radY) - p.z * sin(radY)
            val z1 = p.x * sin(radY) + p.z * cos(radY)
            // Pitch Rotation (X-Axis)
            val z2 = p.y * sin(radX) + z1 * cos(radX)
            sumDepth += z2.toFloat()
        }
        return sumDepth / points.size
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTextApi::class)
@Composable
fun Viewport3D(
    state: ProjectState,
    onUpdateCamera: (yaw: Float, pitch: Float, scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    onAddSketchEntity: (SketchEntity) -> Unit,
    activeSketchTool: String?, // "Line", "Circle", "Rectangle", "Dimension", null
    modifier: Modifier = Modifier
) {
    var drawingStart by remember { mutableStateOf<Offset?>(null) }
    var drawingCurrent by remember { mutableStateOf<Offset?>(null) }
    var snapFeedbackPoint by remember { mutableStateOf<Offset?>(null) }

    var canvasWidth by remember { mutableStateOf(1000f) }
    var canvasHeight by remember { mutableStateOf(1200f) }

    // Smooth camera angles transition when sketch/3D mode toggles
    val animatedYaw by animateFloatAsState(
        targetValue = if (state.viewMode3D) state.yaw else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 150f),
        label = "yaw"
    )
    val animatedPitch by animateFloatAsState(
        targetValue = if (state.viewMode3D) state.pitch else {
            when (state.activePlane) {
                WorkPlane.XY -> 0f
                WorkPlane.XZ -> 90f
                WorkPlane.YZ -> 0f // Side orthographic uses adjusted projection
            }
        },
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 150f),
        label = "pitch"
    )

    // Base viewport colors mimicking SolidWorks light-blue/grey sky gradient
    val backgroundStart = Color(0xFFEBF1F5)
    val backgroundEnd = Color(0xFFD4E2EC)

    // Layout configuration
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(backgroundStart, backgroundEnd)
                )
            )
            .onGloballyPositioned { layoutCoordinates ->
                canvasWidth = layoutCoordinates.size.width.toFloat()
                canvasHeight = layoutCoordinates.size.height.toFloat()
            }
            .pointerInput(state.viewMode3D, activeSketchTool) {
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    if (state.viewMode3D) {
                        val newScale = (state.scale * zoom).coerceIn(0.5f, 50f)
                        val newOffsetX = state.offsetX + pan.x
                        val newOffsetY = state.offsetY + pan.y

                        if (zoom == 1f && rotation == 0f) {
                            // One-finger drag: rotate camera angles
                            val sensitivity = 0.5f
                            val newYaw = state.yaw + pan.x * sensitivity
                            val newPitch = (state.pitch - pan.y * sensitivity).coerceIn(-85f, 85f)
                            onUpdateCamera(newYaw, newPitch, state.scale, state.offsetX, state.offsetY)
                        } else {
                            // Pinch zoom or two-finger pan
                            onUpdateCamera(state.yaw, state.pitch, newScale, newOffsetX, newOffsetY)
                        }
                    } else if (activeSketchTool == null) {
                        // 2D Mode and no active sketch tool: pan and zoom sketcher freeform
                        val newScale = (state.scale * zoom).coerceIn(0.5f, 50f)
                        val newOffsetX = state.offsetX + pan.x
                        val newOffsetY = state.offsetY + pan.y
                        onUpdateCamera(0f, 0f, newScale, newOffsetX, newOffsetY)
                    }
                }
            }
            .pointerInteropFilter { event ->
                // Handled sketch drawing in 2D Mode with snapping to endpoints
                if (!state.viewMode3D && activeSketchTool != null) {
                    val viewCenterX = canvasWidth / 2f
                    val viewCenterY = canvasHeight / 2f
                    val scaleFactor = state.scale * 10f
                    val activeSketch = state.sketches.firstOrNull { it.id == state.selectedSketchId }

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            val rawStartX = (event.x - viewCenterX) / scaleFactor
                            val rawStartY = -(event.y - viewCenterY) / scaleFactor
                            val (snappedX, snappedY) = getSnapPoint(rawStartX, rawStartY, activeSketch)

                            val screenX = snappedX * scaleFactor + viewCenterX
                            val screenY = -snappedY * scaleFactor + viewCenterY

                            drawingStart = Offset(screenX, screenY)
                            drawingCurrent = Offset(screenX, screenY)

                            if (snappedX != rawStartX || snappedY != rawStartY) {
                                snapFeedbackPoint = Offset(screenX, screenY)
                            } else {
                                snapFeedbackPoint = null
                            }
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val rawEndX = (event.x - viewCenterX) / scaleFactor
                            val rawEndY = -(event.y - viewCenterY) / scaleFactor
                            val (snappedX, snappedY) = getSnapPoint(rawEndX, rawEndY, activeSketch)

                            val screenX = snappedX * scaleFactor + viewCenterX
                            val screenY = -snappedY * scaleFactor + viewCenterY

                            drawingCurrent = Offset(screenX, screenY)

                            if (snappedX != rawEndX || snappedY != rawEndY) {
                                snapFeedbackPoint = Offset(screenX, screenY)
                            } else {
                                snapFeedbackPoint = null
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            val start = drawingStart
                            val current = drawingCurrent
                            if (start != null && current != null) {
                                val cadStartX = (start.x - viewCenterX) / scaleFactor
                                val cadStartY = -(start.y - viewCenterY) / scaleFactor
                                val cadEndX = (current.x - viewCenterX) / scaleFactor
                                val cadEndY = -(current.y - viewCenterY) / scaleFactor

                                val dx = cadEndX - cadStartX
                                val dy = cadEndY - cadStartY
                                val dist = sqrt(dx * dx + dy * dy)

                                if (dist > 0.5f) { // Prevent tiny click registers
                                    when (activeSketchTool) {
                                        "Line" -> {
                                            onAddSketchEntity(
                                                SketchEntity.Line(
                                                    x1 = cadStartX, y1 = cadStartY,
                                                    x2 = cadEndX, y2 = cadEndY,
                                                    label = String.format("%.1f mm", dist)
                                                )
                                            )
                                        }
                                        "Circle" -> {
                                            onAddSketchEntity(
                                                SketchEntity.Circle(
                                                    cx = cadStartX, cy = cadStartY,
                                                    radius = dist,
                                                    label = String.format("R%.1f mm", dist)
                                                )
                                            )
                                        }
                                        "Rectangle" -> {
                                            val w = abs(dx)
                                            val h = abs(dy)
                                            onAddSketchEntity(
                                                SketchEntity.Rectangle(
                                                    cx = (cadStartX + cadEndX) / 2f,
                                                    cy = (cadStartY + cadEndY) / 2f,
                                                    width = w, height = h,
                                                    label = String.format("%.1f x %.1f mm", w, h)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
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
        // Render CAD geometry
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f
            val baseScale = state.scale * 10f // Scale multiplier for visibility

            // 1. Draw Grid on Active plane with camera offsets
            drawPlaneGrid(
                drawScope = this,
                plane = state.activePlane,
                yaw = animatedYaw,
                pitch = animatedPitch,
                scale = baseScale,
                centerX = centerX,
                centerY = centerY,
                is3D = state.viewMode3D,
                offsetX = state.offsetX,
                offsetY = state.offsetY
            )

            // 2. Generate and Render 3D Solids (Painter's Algorithm)
            val facesToRender = mutableListOf<Face3D>()

            // Map operations and sketches to 3D solid geometry
            val sketchMap = state.sketches.associateBy { it.id }
            state.operations.forEach { op ->
                val sketch = op.sketchId?.let { sketchMap[it] } ?: return@forEach
                val plane = sketch.plane
                val opType = op.type

                sketch.entities.forEach { entity ->
                    when (entity) {
                        is SketchEntity.Rectangle -> {
                            val w = entity.width
                            val h = entity.height
                            val cx = entity.cx
                            val cy = entity.cy

                            if (opType == OperationType.EXTRUDE_BOSS || opType == OperationType.EXTRUDE_CUT) {
                                // Form extruded prism
                                val isCut = opType == OperationType.EXTRUDE_CUT
                                val fillColor = if (isCut) Color(0x7FFF0000) else Color(0x90A1C4FD)
                                val edgeColor = if (isCut) Color(0xFFC0392B) else Color(0xFF1F4E79)

                                val d = op.depth
                                // Generate vertices
                                val ptsBottom = listOf(
                                    to3DPoint(cx - w/2, cy - h/2, 0f, plane),
                                    to3DPoint(cx + w/2, cy - h/2, 0f, plane),
                                    to3DPoint(cx + w/2, cy + h/2, 0f, plane),
                                    to3DPoint(cx - w/2, cy + h/2, 0f, plane)
                                )
                                val ptsTop = listOf(
                                    to3DPoint(cx - w/2, cy - h/2, d, plane),
                                    to3DPoint(cx + w/2, cy - h/2, d, plane),
                                    to3DPoint(cx + w/2, cy + h/2, d, plane),
                                    to3DPoint(cx - w/2, cy + h/2, d, plane)
                                )

                                // Add bottom face
                                facesToRender.add(Face3D(ptsBottom.reversed(), fillColor, edgeColor, isCut, "Base"))
                                // Add top face
                                facesToRender.add(Face3D(ptsTop, fillColor, edgeColor, isCut, "Techo"))
                                // Add side faces
                                for (i in 0..3) {
                                    val next = (i + 1) % 4
                                    facesToRender.add(
                                        Face3D(
                                            listOf(ptsBottom[i], ptsBottom[next], ptsTop[next], ptsTop[i]),
                                            fillColor, edgeColor, isCut, "Lado $i"
                                        )
                                    )
                                }
                            } else if (opType == OperationType.REVOLVE_BOSS || opType == OperationType.REVOLVE_CUT) {
                                // Form revolved ring (approximate cylinder/torus segments)
                                val isCut = opType == OperationType.REVOLVE_CUT
                                val fillColor = if (isCut) Color(0x7FFF0000) else Color(0x90C2E9FB)
                                val edgeColor = if (isCut) Color(0xFFC0392B) else Color(0xFF1F4E79)

                                val revAngle = op.angle
                                val steps = 16
                                val angleStep = revAngle / steps

                                // Form a sequence of profiles revolved around the axis
                                for (step in 0 until steps) {
                                    val a1 = Math.toRadians((step * angleStep).toDouble()).toFloat()
                                    val a2 = Math.toRadians(((step + 1) * angleStep).toDouble()).toFloat()

                                    // Rotate Rectangle corners around Revolve Axis (default Y-Axis)
                                    val profile1 = getRevolvedPoints(cx, cy, w, h, a1, op.axis, plane)
                                    val profile2 = getRevolvedPoints(cx, cy, w, h, a2, op.axis, plane)

                                    // Create faces between adjacent profiles
                                    for (i in 0..3) {
                                        val next = (i + 1) % 4
                                        facesToRender.add(
                                            Face3D(
                                                listOf(profile1[i], profile1[next], profile2[next], profile2[i]),
                                                fillColor, edgeColor, isCut
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        is SketchEntity.Circle -> {
                            val r = entity.radius
                            val cx = entity.cx
                            val cy = entity.cy

                            if (opType == OperationType.EXTRUDE_BOSS || opType == OperationType.EXTRUDE_CUT) {
                                val isCut = opType == OperationType.EXTRUDE_CUT
                                val fillColor = if (isCut) Color(0x7FFF0000) else Color(0x90A1C4FD)
                                val edgeColor = if (isCut) Color(0xFFC0392B) else Color(0xFF1F4E79)
                                val d = op.depth

                                val segments = 16
                                val botPts = mutableListOf<Point3D>()
                                val topPts = mutableListOf<Point3D>()

                                for (i in 0 until segments) {
                                    val ang = (i * 2 * Math.PI / segments).toFloat()
                                    val lx = cx + r * cos(ang)
                                    val ly = cy + r * sin(ang)
                                    botPts.add(to3DPoint(lx, ly, 0f, plane))
                                    topPts.add(to3DPoint(lx, ly, d, plane))
                                }

                                // Caps
                                facesToRender.add(Face3D(botPts.reversed(), fillColor, edgeColor, isCut, "Base Circular"))
                                facesToRender.add(Face3D(topPts, fillColor, edgeColor, isCut, "Techo Circular"))

                                // Side faces
                                for (i in 0 until segments) {
                                    val next = (i + 1) % segments
                                    facesToRender.add(
                                        Face3D(
                                            listOf(botPts[i], botPts[next], topPts[next], topPts[i]),
                                            fillColor, edgeColor, isCut
                                        )
                                    )
                                }
                            } else if (opType == OperationType.REVOLVE_BOSS || opType == OperationType.REVOLVE_CUT) {
                                // Revolve circular profile (Creates a Torus solid)
                                val isCut = opType == OperationType.REVOLVE_CUT
                                val fillColor = if (isCut) Color(0x7FFF0000) else Color(0x90C2E9FB)
                                val edgeColor = if (isCut) Color(0xFFC0392B) else Color(0xFF1F4E79)

                                val steps = 16
                                val segs = 12
                                val revAngle = op.angle

                                for (step in 0 until steps) {
                                    val a1 = Math.toRadians((step * revAngle / steps).toDouble()).toFloat()
                                    val a2 = Math.toRadians(((step + 1) * revAngle / steps).toDouble()).toFloat()

                                    val profile1 = getCircleRevolvedPoints(cx, cy, r, segs, a1, op.axis, plane)
                                    val profile2 = getCircleRevolvedPoints(cx, cy, r, segs, a2, op.axis, plane)

                                    for (i in 0 until segs) {
                                        val next = (i + 1) % segs
                                        facesToRender.add(
                                            Face3D(
                                                listOf(profile1[i], profile1[next], profile2[next], profile2[i]),
                                                fillColor, edgeColor, isCut
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        is SketchEntity.Line -> {
                            // If simple line extrudes (renders as thin wall plane sheet)
                            if (opType == OperationType.EXTRUDE_BOSS || opType == OperationType.EXTRUDE_CUT) {
                                val isCut = opType == OperationType.EXTRUDE_CUT
                                val fillColor = if (isCut) Color(0x60FF0000) else Color(0x70A1C4FD)
                                val edgeColor = if (isCut) Color(0xFFC0392B) else Color(0xFF1F4E79)
                                val d = op.depth

                                val p1Bot = to3DPoint(entity.x1, entity.y1, 0f, plane)
                                val p2Bot = to3DPoint(entity.x2, entity.y2, 0f, plane)
                                val p1Top = to3DPoint(entity.x1, entity.y1, d, plane)
                                val p2Top = to3DPoint(entity.x2, entity.y2, d, plane)

                                facesToRender.add(
                                    Face3D(
                                        listOf(p1Bot, p2Bot, p2Top, p1Top),
                                        fillColor, edgeColor, isCut
                                    )
                                )
                            } else if (opType == OperationType.REVOLVE_BOSS || opType == OperationType.REVOLVE_CUT) {
                                val isCut = opType == OperationType.REVOLVE_CUT
                                val fillColor = if (isCut) Color(0x7FFF0000) else Color(0x90C2E9FB)
                                val edgeColor = if (isCut) Color(0xFFC0392B) else Color(0xFF1F4E79)

                                val revAngle = op.angle
                                val steps = 16
                                val angleStep = revAngle / steps

                                for (step in 0 until steps) {
                                    val a1 = Math.toRadians((step * angleStep).toDouble()).toFloat()
                                    val a2 = Math.toRadians(((step + 1) * angleStep).toDouble()).toFloat()

                                    val pts1 = getLineRevolvedPoints(entity.x1, entity.y1, entity.x2, entity.y2, a1, op.axis, plane)
                                    val pts2 = getLineRevolvedPoints(entity.x1, entity.y1, entity.x2, entity.y2, a2, op.axis, plane)

                                    facesToRender.add(
                                        Face3D(
                                            listOf(pts1[0], pts1[1], pts2[1], pts2[0]),
                                            fillColor, edgeColor, isCut
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Painter's Algorithm: Sort faces by distance depth
            val sortedFaces = facesToRender.sortedByDescending { it.getAverageDepth(animatedYaw, animatedPitch) }

            // Render Shaded Faces
            sortedFaces.forEach { face ->
                val path = Path()
                face.points.forEachIndexed { idx, pt ->
                    val proj = rotateAndProject(pt, animatedYaw, animatedPitch, baseScale, centerX, centerY, state.offsetX, state.offsetY)
                    if (idx == 0) {
                        path.moveTo(proj.x, proj.y)
                    } else {
                        path.lineTo(proj.x, proj.y)
                    }
                }
                path.close()

                // Draw face filling
                drawPath(path = path, color = face.fillColor)
                // Draw face edges/outlines
                drawPath(path = path, color = face.edgeColor, style = Stroke(width = 2f))
            }

            // 3. Render Current Active Sketch Entities in 2D or 3D overlay
            state.sketches.forEach { sk ->
                val isActiveSketch = sk.id == state.selectedSketchId
                val sketchColor = if (isActiveSketch) Color(0xFF0056B3) else Color(0x705F7D95)
                val strokeW = if (isActiveSketch) 4f else 2f

                sk.entities.forEach { ent ->
                    when (ent) {
                        is SketchEntity.Line -> {
                            val p1 = rotateAndProject(to3DPoint(ent.x1, ent.y1, 0f, sk.plane), animatedYaw, animatedPitch, baseScale, centerX, centerY, state.offsetX, state.offsetY)
                            val p2 = rotateAndProject(to3DPoint(ent.x2, ent.y2, 0f, sk.plane), animatedYaw, animatedPitch, baseScale, centerX, centerY, state.offsetX, state.offsetY)
                            drawLine(color = sketchColor, start = Offset(p1.x, p1.y), end = Offset(p2.x, p2.y), strokeWidth = strokeW)

                            // Show dimension label if in 2D active mode
                            if (!state.viewMode3D && isActiveSketch && ent.label.isNotEmpty()) {
                                drawDimensionLine(this, Offset(p1.x, p1.y), Offset(p2.x, p2.y), ent.label, textMeasurer)
                            }
                        }
                        is SketchEntity.Circle -> {
                            val c = rotateAndProject(to3DPoint(ent.cx, ent.cy, 0f, sk.plane), animatedYaw, animatedPitch, baseScale, centerX, centerY, state.offsetX, state.offsetY)
                            val rInPixels = ent.radius * baseScale
                            
                            drawCircle(
                                color = sketchColor,
                                radius = rInPixels,
                                center = Offset(c.x, c.y),
                                style = Stroke(width = strokeW)
                            )
                            // Draw central point
                            drawCircle(color = sketchColor, radius = 4f, center = Offset(c.x, c.y))

                            // Draw diameter line
                            if (!state.viewMode3D && isActiveSketch && ent.label.isNotEmpty()) {
                                val pLeft = Offset(c.x - rInPixels, c.y)
                                val pRight = Offset(c.x + rInPixels, c.y)
                                drawDimensionLine(this, pLeft, pRight, ent.label, textMeasurer)
                            }
                        }
                        is SketchEntity.Rectangle -> {
                            // Render rectangle as closed loop lines
                            val xL = ent.cx - ent.width / 2f
                            val xR = ent.cx + ent.width / 2f
                            val yB = ent.cy - ent.height / 2f
                            val yT = ent.cy + ent.height / 2f

                            val p1 = rotateAndProject(to3DPoint(xL, yB, 0f, sk.plane), animatedYaw, animatedPitch, baseScale, centerX, centerY, state.offsetX, state.offsetY)
                            val p2 = rotateAndProject(to3DPoint(xR, yB, 0f, sk.plane), animatedYaw, animatedPitch, baseScale, centerX, centerY, state.offsetX, state.offsetY)
                            val p3 = rotateAndProject(to3DPoint(xR, yT, 0f, sk.plane), animatedYaw, animatedPitch, baseScale, centerX, centerY, state.offsetX, state.offsetY)
                            val p4 = rotateAndProject(to3DPoint(xL, yT, 0f, sk.plane), animatedYaw, animatedPitch, baseScale, centerX, centerY, state.offsetX, state.offsetY)

                            drawLine(color = sketchColor, start = Offset(p1.x, p1.y), end = Offset(p2.x, p2.y), strokeWidth = strokeW)
                            drawLine(color = sketchColor, start = Offset(p2.x, p2.y), end = Offset(p3.x, p3.y), strokeWidth = strokeW)
                            drawLine(color = sketchColor, start = Offset(p3.x, p3.y), end = Offset(p4.x, p4.y), strokeWidth = strokeW)
                            drawLine(color = sketchColor, start = Offset(p4.x, p4.y), end = Offset(p1.x, p1.y), strokeWidth = strokeW)

                            // Label
                            if (!state.viewMode3D && isActiveSketch && ent.label.isNotEmpty()) {
                                drawDimensionLine(this, Offset(p1.x, p1.y), Offset(p2.x, p2.y), "${ent.width} mm", textMeasurer)
                                drawDimensionLine(this, Offset(p2.x, p2.y), Offset(p3.x, p3.y), "${ent.height} mm", textMeasurer)
                            }
                        }
                    }
                }
            }

            // 4. Render Active Temp Gesture Drawing (Drafting Feedback)
            val tempStart = drawingStart
            val tempCurrent = drawingCurrent
            if (tempStart != null && tempCurrent != null && activeSketchTool != null) {
                val dx = tempCurrent.x - tempStart.x
                val dy = tempCurrent.y - tempStart.y
                val dist = sqrt(dx * dx + dy * dy)
                val cadDist = dist / baseScale

                when (activeSketchTool) {
                    "Line" -> {
                        drawLine(color = Color(0xFFE67E22), start = tempStart, end = tempCurrent, strokeWidth = 3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                        drawCircle(color = Color(0xFFE67E22), radius = 6f, center = tempStart)
                        drawCircle(color = Color(0xFFE67E22), radius = 6f, center = tempCurrent)
                        drawTextCentered(this, String.format("%.1f mm", cadDist), tempCurrent + Offset(15f, -15f), textMeasurer)
                    }
                    "Circle" -> {
                        drawCircle(color = Color(0xFFE67E22), radius = dist, center = tempStart, style = Stroke(width = 3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                        drawLine(color = Color(0xFFE67E22), start = tempStart, end = tempCurrent, strokeWidth = 2f)
                        drawCircle(color = Color(0xFFE67E22), radius = 5f, center = tempStart)
                        drawTextCentered(this, String.format("R %.1f mm", cadDist), tempCurrent + Offset(15f, -15f), textMeasurer)
                    }
                    "Rectangle" -> {
                        val rectSize = Size(abs(dx), abs(dy))
                        val topLeft = Offset(min(tempStart.x, tempCurrent.x), min(tempStart.y, tempCurrent.y))
                        drawRect(
                            color = Color(0xFFE67E22),
                            topLeft = topLeft,
                            size = rectSize,
                            style = Stroke(width = 3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                        )
                        drawCircle(color = Color(0xFFE67E22), radius = 5f, center = tempStart)
                        drawCircle(color = Color(0xFFE67E22), radius = 5f, center = tempCurrent)
                        val lbl = String.format("%.1f x %.1f mm", abs(dx)/baseScale, abs(dy)/baseScale)
                        drawTextCentered(this, lbl, tempCurrent + Offset(15f, -15f), textMeasurer)
                    }
                }
            }

            // Render Snapping Feedback highlight
            val snapPt = snapFeedbackPoint
            if (snapPt != null) {
                drawCircle(
                    color = Color(0xFF2ECC71),
                    radius = 16f,
                    center = snapPt,
                    style = Stroke(width = 4f)
                )
                drawCircle(
                    color = Color(0xFF27AE60),
                    radius = 6f,
                    center = snapPt
                )
            }

            // 5. Render 3D Origin Triad at screen corner (SolidWorks classic bottom-left)
            drawOriginTriad(this, animatedYaw, animatedPitch, height)
        }

        // Viewport indicators (Plane Indicator)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color(0xD0FFFFFF), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFBDC3C7), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GridOn,
                    contentDescription = null,
                    tint = Color(0xFF1B365D),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (state.viewMode3D) "Vista 3D" else "Croquis: Plano ${state.activePlane.name}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B365D)
                )
            }
        }

        // Floating Camera Control Toolbar (Zoom +, Zoom -, Center View)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    val newScale = (state.scale * 1.25f).coerceIn(0.5f, 50f)
                    onUpdateCamera(state.yaw, state.pitch, newScale, state.offsetX, state.offsetY)
                },
                containerColor = Color.White.copy(alpha = 0.85f),
                contentColor = Color(0xFF1B365D),
                modifier = Modifier.size(38.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Acercar", modifier = Modifier.size(18.dp))
            }

            FloatingActionButton(
                onClick = {
                    val newScale = (state.scale * 0.8f).coerceIn(0.5f, 50f)
                    onUpdateCamera(state.yaw, state.pitch, newScale, state.offsetX, state.offsetY)
                },
                containerColor = Color.White.copy(alpha = 0.85f),
                contentColor = Color(0xFF1B365D),
                modifier = Modifier.size(38.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Alejar", modifier = Modifier.size(18.dp))
            }

            FloatingActionButton(
                onClick = {
                    // Reset zoom, offset, and rotation to standard ISO view
                    onUpdateCamera(-45f, 25f, 4.0f, 0f, 0f)
                },
                containerColor = Color.White.copy(alpha = 0.85f),
                contentColor = Color(0xFF1B365D),
                modifier = Modifier.size(38.dp),
                shape = CircleShape
            ) {
                Icon(Icons.Default.FilterCenterFocus, contentDescription = "Restablecer vista", modifier = Modifier.size(18.dp))
            }
        }
    }
}

// Projection Math
fun rotateAndProject(
    pt: Point3D,
    yaw: Float,
    pitch: Float,
    scale: Float,
    centerX: Float,
    centerY: Float,
    offsetX: Float = 0f,
    offsetY: Float = 0f
): ProjectedPoint {
    val radX = Math.toRadians(pitch.toDouble())
    val radY = Math.toRadians(yaw.toDouble())

    // Rotate Y-Axis (Yaw)
    val x1 = pt.x * cos(radY) - pt.z * sin(radY)
    val z1 = pt.x * sin(radY) + pt.z * cos(radY)

    // Rotate X-Axis (Pitch)
    val y2 = pt.y * cos(radX) - z1 * sin(radX)
    val z2 = pt.y * sin(radX) + z1 * cos(radX)

    // Apply scale, center offset, and panning camera offsets
    val screenX = (x1 * scale + centerX + offsetX).toFloat()
    val screenY = (-y2 * scale + centerY + offsetY).toFloat() // Flip screen Y-Axis

    return ProjectedPoint(screenX, screenY, z2.toFloat())
}

// Plane mapping helpers
fun to3DPoint(x: Float, y: Float, zOffset: Float, plane: WorkPlane): Point3D {
    return when (plane) {
        WorkPlane.XY -> Point3D(x, y, zOffset)
        WorkPlane.XZ -> Point3D(x, zOffset, y)
        WorkPlane.YZ -> Point3D(zOffset, x, y)
    }
}

// Snapping algorithm for Croquis (Sketch) endpoints
fun getSnapPoint(
    x: Float,
    y: Float,
    activeSketch: CadSketch?,
    snapThreshold: Float = 2.0f
): Pair<Float, Float> {
    if (activeSketch == null) return Pair(x, y)
    var closestPt: Pair<Float, Float>? = null
    var minDistance = Float.MAX_VALUE

    activeSketch.entities.forEach { entity ->
        val candidatePoints = mutableListOf<Pair<Float, Float>>()
        when (entity) {
            is SketchEntity.Line -> {
                candidatePoints.add(Pair(entity.x1, entity.y1))
                candidatePoints.add(Pair(entity.x2, entity.y2))
            }
            is SketchEntity.Rectangle -> {
                val w2 = entity.width / 2f
                val h2 = entity.height / 2f
                candidatePoints.add(Pair(entity.cx - w2, entity.cy - h2))
                candidatePoints.add(Pair(entity.cx + w2, entity.cy - h2))
                candidatePoints.add(Pair(entity.cx + w2, entity.cy + h2))
                candidatePoints.add(Pair(entity.cx - w2, entity.cy + h2))
            }
            is SketchEntity.Circle -> {
                candidatePoints.add(Pair(entity.cx, entity.cy))
            }
        }

        for (pt in candidatePoints) {
            val dist = sqrt((pt.first - x) * (pt.first - x) + (pt.second - y) * (pt.second - y))
            if (dist < minDistance) {
                minDistance = dist
                closestPt = pt
            }
        }
    }

    return if (closestPt != null && minDistance < snapThreshold) {
        closestPt!!
    } else {
        Pair(x, y)
    }
}

// Calculate revolve coordinates around X or Y axis for Rectangle
fun getRevolvedPoints(
    cx: Float, cy: Float, w: Float, h: Float,
    angleRad: Float, axis: String, plane: WorkPlane
): List<Point3D> {
    val xL = cx - w / 2f
    val xR = cx + w / 2f
    val yB = cy - h / 2f
    val yT = cy + h / 2f

    val corners2D = listOf(
        Pair(xL, yB),
        Pair(xR, yB),
        Pair(xR, yT),
        Pair(xL, yT)
    )

    return corners2D.map { (px, py) ->
        if (axis == "X-Axis") {
            // Revolve around X-Axis: py rotates in YZ plane
            val ry = py * cos(angleRad)
            val rz = py * sin(angleRad)
            to3DPoint(px, ry, rz, plane)
        } else {
            // Revolve around Y-Axis: px rotates in XZ plane
            val rx = px * cos(angleRad)
            val rz = px * sin(angleRad)
            to3DPoint(rx, py, rz, plane)
        }
    }
}

// Calculate revolve coordinates for Circle profile
fun getCircleRevolvedPoints(
    cx: Float, cy: Float, r: Float, segments: Int,
    angleRad: Float, axis: String, plane: WorkPlane
): List<Point3D> {
    val points = mutableListOf<Point3D>()
    for (i in 0 until segments) {
        val u = (i * 2 * Math.PI / segments).toFloat()
        val px = cx + r * cos(u)
        val py = cy + r * sin(u)

        if (axis == "X-Axis") {
            val ry = py * cos(angleRad)
            val rz = py * sin(angleRad)
            points.add(to3DPoint(px, ry, rz, plane))
        } else {
            val rx = px * cos(angleRad)
            val rz = px * sin(angleRad)
            points.add(to3DPoint(rx, py, rz, plane))
        }
    }
    return points
}

// Calculate revolve coordinates for Line segment
fun getLineRevolvedPoints(
    x1: Float, y1: Float, x2: Float, y2: Float,
    angleRad: Float, axis: String, plane: WorkPlane
): List<Point3D> {
    val pts = listOf(Pair(x1, y1), Pair(x2, y2))
    return pts.map { (px, py) ->
        if (axis == "X-Axis") {
            val ry = py * cos(angleRad)
            val rz = py * sin(angleRad)
            to3DPoint(px, ry, rz, plane)
        } else {
            val rx = px * cos(angleRad)
            val rz = px * sin(angleRad)
            to3DPoint(rx, py, rz, plane)
        }
    }
}

// Draw CAD grid helper
fun drawPlaneGrid(
    drawScope: DrawScope,
    plane: WorkPlane,
    yaw: Float,
    pitch: Float,
    scale: Float,
    centerX: Float,
    centerY: Float,
    is3D: Boolean,
    offsetX: Float = 0f,
    offsetY: Float = 0f
) {
    val gridCount = 6
    val spacing = 15f // 15mm spacing

    val gridColor = if (is3D) Color(0x308899A6) else Color(0x608899A6)
    val majorGridColor = if (is3D) Color(0x605F7D95) else Color(0x905F7D95)

    // Draw grid lines
    for (i in -gridCount..gridCount) {
        val coord = i * spacing

        // Line parallel to primary axis
        val p1 = rotateAndProject(to3DPoint(coord, -gridCount * spacing, 0f, plane), yaw, pitch, scale, centerX, centerY, offsetX, offsetY)
        val p2 = rotateAndProject(to3DPoint(coord, gridCount * spacing, 0f, plane), yaw, pitch, scale, centerX, centerY, offsetX, offsetY)

        // Line parallel to secondary axis
        val p3 = rotateAndProject(to3DPoint(-gridCount * spacing, coord, 0f, plane), yaw, pitch, scale, centerX, centerY, offsetX, offsetY)
        val p4 = rotateAndProject(to3DPoint(gridCount * spacing, coord, 0f, plane), yaw, pitch, scale, centerX, centerY, offsetX, offsetY)

        val strokeW = if (i == 0) 2.5f else 1f
        val color = if (i == 0) majorGridColor else gridColor

        drawScope.drawLine(color = color, start = Offset(p1.x, p1.y), end = Offset(p2.x, p2.y), strokeWidth = strokeW)
        drawScope.drawLine(color = color, start = Offset(p3.x, p3.y), end = Offset(p4.x, p4.y), strokeWidth = strokeW)
    }
}

// Draw beautiful SolidWorks-like 3D Coordinate Triad at bottom left
fun drawOriginTriad(drawScope: DrawScope, yaw: Float, pitch: Float, screenHeight: Float) {
    val size = 55f
    val basePos = Point3D(0f, 0f, 0f)
    val originX = 110f
    val originY = screenHeight - 110f

    val triadScale = 1.0f

    val projOrigin = rotateAndProject(basePos, yaw, pitch, triadScale, originX, originY)
    val projX = rotateAndProject(Point3D(size, 0f, 0f), yaw, pitch, triadScale, originX, originY)
    val projY = rotateAndProject(Point3D(0f, size, 0f), yaw, pitch, triadScale, originX, originY)
    val projZ = rotateAndProject(Point3D(0f, 0f, size), yaw, pitch, triadScale, originX, originY)

    // X Axis - Red
    drawScope.drawLine(color = Color(0xFFD32F2F), start = Offset(projOrigin.x, projOrigin.y), end = Offset(projX.x, projX.y), strokeWidth = 4f)
    // Y Axis - Green
    drawScope.drawLine(color = Color(0xFF388E3C), start = Offset(projOrigin.x, projOrigin.y), end = Offset(projY.x, projY.y), strokeWidth = 4f)
    // Z Axis - Blue
    drawScope.drawLine(color = Color(0xFF1976D2), start = Offset(projOrigin.x, projOrigin.y), end = Offset(projZ.x, projZ.y), strokeWidth = 4f)

    // Tiny labels
    drawScope.drawCircle(color = Color(0xFFD32F2F), radius = 5f, center = Offset(projX.x, projX.y))
    drawScope.drawCircle(color = Color(0xFF388E3C), radius = 5f, center = Offset(projY.x, projY.y))
    drawScope.drawCircle(color = Color(0xFF1976D2), radius = 5f, center = Offset(projZ.x, projZ.y))
}

// Dimension line drawing helper
fun drawDimensionLine(
    drawScope: DrawScope,
    p1: Offset,
    p2: Offset,
    text: String,
    textMeasurer: TextMeasurer
) {
    val labelColor = Color(0xFF007ACC)
    val lineStyle = Stroke(width = 1.5f)

    // Draw dimension extension lines (small offsets perpendicular)
    val dx = p2.x - p1.x
    val dy = p2.y - p1.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 5f) return

    val nx = -dy / len
    val ny = dx / len
    val offsetDistance = 30f

    // Dimension offset points
    val dp1 = Offset(p1.x + nx * offsetDistance, p1.y + ny * offsetDistance)
    val dp2 = Offset(p2.x + nx * offsetDistance, p2.y + ny * offsetDistance)

    // Thin lines from model to dim line
    drawScope.drawLine(color = labelColor, start = p1, end = dp1, strokeWidth = 1f)
    drawScope.drawLine(color = labelColor, start = p2, end = dp2, strokeWidth = 1f)

    // Dimension line
    drawScope.drawLine(color = labelColor, start = dp1, end = dp2, strokeWidth = 1.5f)

    // Draw tiny arrows at ends
    drawArrowHead(drawScope, dp1, dp2, labelColor)
    drawArrowHead(drawScope, dp2, dp1, labelColor)

    // Draw value text
    val centerDim = Offset((dp1.x + dp2.x) / 2f, (dp1.y + dp2.y) / 2f)
    drawTextCentered(drawScope, text, centerDim + Offset(nx * 10f, ny * 10f), textMeasurer)
}

fun drawArrowHead(drawScope: DrawScope, tip: Offset, from: Offset, color: Color) {
    val dx = tip.x - from.x
    val dy = tip.y - from.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 1f) return
    val ux = dx / len
    val uy = dy / len

    val arrowLength = 8f
    val arrowWidth = 4f

    val base = Offset(tip.x - ux * arrowLength, tip.y - uy * arrowLength)
    val left = Offset(base.x - uy * arrowWidth, base.y + ux * arrowWidth)
    val right = Offset(base.x + uy * arrowWidth, base.y - ux * arrowWidth)

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawScope.drawPath(path = path, color = color)
}

fun drawTextCentered(
    drawScope: DrawScope,
    text: String,
    position: Offset,
    textMeasurer: TextMeasurer
) {
    val textStyle = TextStyle(
        color = Color(0xFF007ACC),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        background = Color(0xE0FFFFFF)
    )

    val result = textMeasurer.measure(
        text = AnnotatedString(text),
        style = textStyle
    )

    drawScope.drawText(
        textLayoutResult = result,
        topLeft = Offset(position.x - result.size.width / 2f, position.y - result.size.height / 2f)
    )
}
