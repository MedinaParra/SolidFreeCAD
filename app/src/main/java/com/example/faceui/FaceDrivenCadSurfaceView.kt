package com.example.faceui

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.example.nativecad.viewer.CameraPreset
import com.example.nativecad.viewer.NativeSceneMesh
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * One-finger tap selects a model face. Dragging the yellow 3D manipulator
 * edits the parameter behind that face. Orbit, pan and pinch remain available.
 *
 * While dragging, a lightweight mesh deformation follows the finger in real
 * time. The native OpenCASCADE BRep is rebuilt only after the gesture ends.
 */
class FaceDrivenCadSurfaceView(context: Context) : GLSurfaceView(context) {
    val cadRenderer = FaceDrivenCadRenderer()

    var onFaceSelected: ((EditableCadFace) -> Unit)? = null
    var onParameterPreview: ((EditableCadFace, Float) -> Unit)? = null
    var onParameterCommit: ((EditableCadFace, Float) -> Unit)? = null

    private var committedMesh: NativeSceneMesh? = null
    private var currentLengthMm = 40f
    private var currentDiameterMm = 34.93f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastCentroidX = 0f
    private var lastCentroidY = 0f
    private var multiTouch = false
    private var parameterDrag = false
    private var parameterDragFace = EditableCadFace.NONE
    private var parameterStartValue = 0f
    private var parameterPreviewValue = 0f
    private var movement = 0f
    private var lastPreviewEventTime = Long.MIN_VALUE

    private val density = resources.displayMetrics.density
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                cadRenderer.camera.zoom(detector.scaleFactor)
                requestRender()
                return true
            }
        }
    )

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(cadRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setMesh(mesh: NativeSceneMesh, fitCamera: Boolean = true) {
        committedMesh = mesh
        cadRenderer.setMesh(mesh)
        if (fitCamera) cadRenderer.camera.fitTo(mesh)
        requestRender()
    }

    fun setDimensions(lengthMm: Float, diameterMm: Float) {
        currentLengthMm = lengthMm
        currentDiameterMm = diameterMm
    }

    fun setSelectedFace(face: EditableCadFace) {
        cadRenderer.setSelectedFace(face)
        requestRender()
    }

    fun fit(mesh: NativeSceneMesh) {
        cadRenderer.camera.fitTo(mesh)
        requestRender()
    }

    fun setPreset(preset: CameraPreset) {
        cadRenderer.camera.setPreset(preset)
        requestRender()
    }

    /** Restores the last committed BRep mesh after a cancelled gesture. */
    fun restoreCommittedPreview() {
        committedMesh?.let {
            cadRenderer.setMesh(it)
            requestRender()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                movement = 0f
                multiTouch = false
                parameterDragFace = cadRenderer.selectedFace
                parameterDrag = parameterDragFace != EditableCadFace.NONE &&
                    cadRenderer.hitManipulator(event.x, event.y, 34f * density)
                parameterStartValue = when (parameterDragFace) {
                    EditableCadFace.TOP -> currentLengthMm
                    EditableCadFace.SIDE -> currentDiameterMm
                    EditableCadFace.NONE -> 0f
                }
                parameterPreviewValue = parameterStartValue
                lastPreviewEventTime = Long.MIN_VALUE
                if (parameterDrag) renderMode = RENDERMODE_CONTINUOUSLY
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                if (parameterDrag) restoreCommittedPreview()
                parameterDrag = false
                renderMode = RENDERMODE_WHEN_DIRTY
                val centroid = centroid(event)
                lastCentroidX = centroid.first
                lastCentroidY = centroid.second
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val centroid = centroid(event)
                    cadRenderer.camera.pan(
                        centroid.first - lastCentroidX,
                        centroid.second - lastCentroidY,
                        width,
                        height
                    )
                    lastCentroidX = centroid.first
                    lastCentroidY = centroid.second
                    multiTouch = true
                    requestRender()
                } else if (parameterDrag) {
                    val screenVector = cadRenderer.manipulatorScreenVector(parameterDragFace)
                    val totalDx = event.x - downX
                    val totalDy = event.y - downY
                    val projectedPixels = if (screenVector != null) {
                        totalDx * screenVector[0] + totalDy * screenVector[1]
                    } else {
                        when (parameterDragFace) {
                            EditableCadFace.TOP -> -totalDy
                            EditableCadFace.SIDE -> totalDx
                            EditableCadFace.NONE -> 0f
                        }
                    }
                    val worldDelta = projectedPixels * cadRenderer.worldPerPixel()
                    parameterPreviewValue = when (parameterDragFace) {
                        EditableCadFace.TOP -> (parameterStartValue + worldDelta).coerceIn(0.5f, 5000f)
                        EditableCadFace.SIDE -> (parameterStartValue + worldDelta * 2f).coerceIn(1f, 5000f)
                        EditableCadFace.NONE -> parameterStartValue
                    }
                    updateLivePreview(event.eventTime, force = false)
                    onParameterPreview?.invoke(parameterDragFace, parameterPreviewValue)
                    movement = maxOf(movement, sqrt(totalDx * totalDx + totalDy * totalDy))
                } else if (!multiTouch && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    movement += abs(dx) + abs(dy)
                    cadRenderer.camera.orbit(dx, dy)
                    lastX = event.x
                    lastY = event.y
                    requestRender()
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    multiTouch = false
                    lastX = event.getX(0)
                    lastY = event.getY(0)
                }
            }

            MotionEvent.ACTION_UP -> {
                when {
                    parameterDrag -> {
                        updateLivePreview(event.eventTime, force = true)
                        onParameterCommit?.invoke(parameterDragFace, parameterPreviewValue)
                    }
                    !multiTouch && distance(downX, downY, event.x, event.y) < 13f * density -> {
                        val selected = cadRenderer.pickFace(event.x, event.y)
                        onFaceSelected?.invoke(selected)
                        requestRender()
                    }
                }
                parameterDrag = false
                parameterDragFace = EditableCadFace.NONE
                multiTouch = false
                renderMode = RENDERMODE_WHEN_DIRTY
                requestRender()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (parameterDrag) restoreCommittedPreview()
                parameterDrag = false
                parameterDragFace = EditableCadFace.NONE
                multiTouch = false
                renderMode = RENDERMODE_WHEN_DIRTY
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        queueEvent { cadRenderer.release() }
        super.onDetachedFromWindow()
    }

    private fun updateLivePreview(eventTime: Long, force: Boolean) {
        if (!force && eventTime - lastPreviewEventTime < PREVIEW_FRAME_INTERVAL_MS) return
        val source = committedMesh ?: return
        val targetLength = when (parameterDragFace) {
            EditableCadFace.TOP -> parameterPreviewValue
            else -> currentLengthMm
        }
        val targetDiameter = when (parameterDragFace) {
            EditableCadFace.SIDE -> parameterPreviewValue
            else -> currentDiameterMm
        }
        runCatching {
            LiveParametricPreview.deformCylinder(source, targetLength, targetDiameter)
        }.onSuccess {
            cadRenderer.setMesh(it)
            requestRender()
            lastPreviewEventTime = eventTime
        }
    }

    private fun centroid(event: MotionEvent): Pair<Float, Float> {
        var x = 0f
        var y = 0f
        for (index in 0 until event.pointerCount) {
            x += event.getX(index)
            y += event.getY(index)
        }
        return x / event.pointerCount to y / event.pointerCount
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val PREVIEW_FRAME_INTERVAL_MS = 8L
    }
}
