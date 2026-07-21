package com.example.faceui

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.example.nativecad.viewer.CameraPreset
import com.example.nativecad.viewer.NativeSceneMesh
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Touch controller for the GPU-driven face editor.
 *
 * Motion events only update the latest requested parameter. A Choreographer
 * callback samples that value once per display frame and asks OpenGL to draw.
 * Compose receives a throttled numeric update, while geometry remains entirely
 * inside the GL path during the gesture.
 */
class FaceDrivenCadSurfaceView(context: Context) : GLSurfaceView(context) {
    val cadRenderer = GpuFaceDrivenCadRenderer()

    var onFaceSelected: ((EditableCadFace) -> Unit)? = null
    var onParameterPreview: ((EditableCadFace, Float) -> Unit)? = null
    var onParameterCommit: ((EditableCadFace, Float) -> Unit)? = null

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
    private var targetLengthMm = currentLengthMm
    private var targetDiameterMm = currentDiameterMm
    private var movement = 0f
    private var frameLoopRunning = false
    private var lastUiPreviewTime = Long.MIN_VALUE

    private val density = resources.displayMetrics.density
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameLoopRunning || !parameterDrag) return
            cadRenderer.setLivePreview(targetLengthMm, targetDiameterMm)
            requestRender()
            choreographer.postFrameCallback(this)
        }
    }

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
        stopFrameLoop()
        cadRenderer.setMesh(mesh)
        if (fitCamera) cadRenderer.camera.fitTo(mesh)
        requestRender()
    }

    fun setDimensions(lengthMm: Float, diameterMm: Float) {
        currentLengthMm = lengthMm
        currentDiameterMm = diameterMm
        if (!parameterDrag) {
            targetLengthMm = lengthMm
            targetDiameterMm = diameterMm
        }
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

    fun restoreCommittedPreview() {
        stopFrameLoop()
        cadRenderer.clearLivePreview()
        requestRender()
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
                    cadRenderer.hitManipulator(event.x, event.y, 38f * density)
                parameterStartValue = when (parameterDragFace) {
                    EditableCadFace.TOP -> currentLengthMm
                    EditableCadFace.SIDE -> currentDiameterMm
                    EditableCadFace.NONE -> 0f
                }
                parameterPreviewValue = parameterStartValue
                targetLengthMm = currentLengthMm
                targetDiameterMm = currentDiameterMm
                lastUiPreviewTime = Long.MIN_VALUE
                if (parameterDrag) startFrameLoop()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                if (parameterDrag) restoreCommittedPreview()
                parameterDrag = false
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
                    when (parameterDragFace) {
                        EditableCadFace.TOP -> targetLengthMm = parameterPreviewValue
                        EditableCadFace.SIDE -> targetDiameterMm = parameterPreviewValue
                        EditableCadFace.NONE -> Unit
                    }
                    if (event.eventTime - lastUiPreviewTime >= UI_PREVIEW_INTERVAL_MS) {
                        onParameterPreview?.invoke(parameterDragFace, parameterPreviewValue)
                        lastUiPreviewTime = event.eventTime
                    }
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
                        cadRenderer.setLivePreview(targetLengthMm, targetDiameterMm)
                        requestRender()
                        onParameterPreview?.invoke(parameterDragFace, parameterPreviewValue)
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
                stopFrameLoop()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (parameterDrag) restoreCommittedPreview()
                parameterDrag = false
                parameterDragFace = EditableCadFace.NONE
                multiTouch = false
                stopFrameLoop()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        stopFrameLoop()
        queueEvent { cadRenderer.release() }
        super.onDetachedFromWindow()
    }

    private fun startFrameLoop() {
        if (frameLoopRunning) return
        frameLoopRunning = true
        choreographer.postFrameCallback(frameCallback)
    }

    private fun stopFrameLoop() {
        if (!frameLoopRunning) return
        frameLoopRunning = false
        choreographer.removeFrameCallback(frameCallback)
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
        const val UI_PREVIEW_INTERVAL_MS = 120L
    }
}
