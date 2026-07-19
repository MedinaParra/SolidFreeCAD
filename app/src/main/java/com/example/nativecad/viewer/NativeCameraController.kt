package com.example.nativecad.viewer

import android.opengl.Matrix
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Orbital CAD camera using the same Z-up convention as OpenCASCADE and FreeCAD. */
class NativeCameraController {
    var yaw = 45f
        private set
    var pitch = 28f
        private set
    var radius = 4f
        private set

    private var targetX = 0f
    private var targetY = 0f
    private var targetZ = 0f
    private var modelSize = 1f

    fun orbit(dx: Float, dy: Float) {
        yaw = normalizeYaw(yaw - dx * 0.28f)
        pitch = (pitch + dy * 0.28f).coerceIn(-82f, 82f)
    }

    fun zoom(scaleFactor: Float) {
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) return
        val minimum = max(modelSize * 0.03f, 0.001f)
        val maximum = max(modelSize * 120f, 100f)
        radius = (radius / scaleFactor).coerceIn(minimum, maximum)
    }

    fun pan(dx: Float, dy: Float, viewportWidth: Int, viewportHeight: Int) {
        val denominator = max(1, minOf(viewportWidth, viewportHeight)).toFloat()
        val worldPerPixel = radius * 1.35f / denominator
        val yawRadians = Math.toRadians(yaw.toDouble())
        val rightX = -sin(yawRadians).toFloat()
        val rightY = cos(yawRadians).toFloat()
        targetX -= dx * worldPerPixel * rightX
        targetY -= dx * worldPerPixel * rightY
        targetZ += dy * worldPerPixel
    }

    fun fitTo(mesh: NativeSceneMesh) {
        targetX = (mesh.minX + mesh.maxX) * 0.5f
        targetY = (mesh.minY + mesh.maxY) * 0.5f
        targetZ = (mesh.minZ + mesh.maxZ) * 0.5f
        modelSize = mesh.maxDimension
        radius = max(modelSize * 2.1f, 1f)
        yaw = 45f
        pitch = 28f
    }

    fun setPreset(preset: CameraPreset) {
        when (preset) {
            CameraPreset.ISOMETRIC -> { yaw = 45f; pitch = 28f }
            CameraPreset.FRONT -> { yaw = -90f; pitch = 0f }
            CameraPreset.RIGHT -> { yaw = 0f; pitch = 0f }
            CameraPreset.TOP -> { yaw = 0f; pitch = 82f }
        }
    }

    fun viewMatrix(output: FloatArray) {
        val yawRadians = Math.toRadians(yaw.toDouble())
        val pitchRadians = Math.toRadians(pitch.toDouble())
        val cosPitch = cos(pitchRadians).toFloat()
        val cameraX = targetX + radius * cosPitch * cos(yawRadians).toFloat()
        val cameraY = targetY + radius * cosPitch * sin(yawRadians).toFloat()
        val cameraZ = targetZ + radius * sin(pitchRadians).toFloat()

        // Near top view the conventional Z-up vector becomes parallel to the view;
        // use a stable Y-up fallback for that narrow range.
        val nearTop = kotlin.math.abs(pitch) > 78f
        Matrix.setLookAtM(
            output, 0,
            cameraX, cameraY, cameraZ,
            targetX, targetY, targetZ,
            0f, if (nearTop) 1f else 0f, if (nearTop) 0f else 1f
        )
    }

    fun projectionMatrix(output: FloatArray, width: Int, height: Int) {
        val aspect = if (height <= 0) 1f else width.toFloat() / height.toFloat()
        val near = max(radius / 10_000f, 0.0001f)
        val far = max(radius * 200f, near + 100f)
        Matrix.perspectiveM(output, 0, 42f, aspect, near, far)
    }

    private fun normalizeYaw(value: Float): Float {
        var normalized = value % 360f
        if (normalized > 180f) normalized -= 360f
        if (normalized < -180f) normalized += 360f
        return normalized
    }
}

enum class CameraPreset { ISOMETRIC, FRONT, RIGHT, TOP }
