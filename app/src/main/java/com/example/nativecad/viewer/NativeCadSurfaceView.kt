package com.example.nativecad.viewer

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector

class NativeCadSurfaceView(context: Context) : GLSurfaceView(context) {
    val cadRenderer = NativeCadRenderer()

    private var lastX = 0f
    private var lastY = 0f
    private var lastCentroidX = 0f
    private var lastCentroidY = 0f
    private var multiTouch = false

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
        setPreserveEGLContextOnPause(true)
        setRenderer(cadRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setMesh(mesh: NativeSceneMesh, fitCamera: Boolean = true) {
        cadRenderer.setMesh(mesh)
        if (fitCamera) cadRenderer.camera.fitTo(mesh)
        requestRender()
    }

    fun setSelectedPlane(plane: ReferencePlane?) {
        cadRenderer.setSelectedPlane(plane)
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                multiTouch = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
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
                } else if (!multiTouch && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> multiTouch = false
        }
        return true
    }

    override fun onDetachedFromWindow() {
        queueEvent { cadRenderer.release() }
        super.onDetachedFromWindow()
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
}
