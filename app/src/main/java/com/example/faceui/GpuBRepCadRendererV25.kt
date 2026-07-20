package com.example.faceui

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.nativecad.viewer.NativeCameraController
import com.example.nativecad.viewer.NativeSceneMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Adds topology-aware selection and visible reference planes without replacing
 * the proven GPU/VSYNC direct-edit renderer.
 */
class GpuBRepCadRendererV25 : GLSurfaceView.Renderer {
    private val base = GpuFaceDrivenCadRenderer()
    val camera: NativeCameraController get() = base.camera
    val selectedFace: EditableCadFace get() = base.selectedFace

    @Volatile private var pendingMesh: NativeSceneMesh? = null
    @Volatile private var pendingPlanes: List<CadViewportPlane> = emptyList()
    @Volatile private var planesDirty = false

    private var mesh: NativeSceneMesh? = null
    private var topology: CadBRepTopology? = null
    private var selection: CadViewportFaceSelection? = null
    private var selectionBuffer: FloatBuffer? = null
    private var selectionVertexCount = 0
    private var planes: List<PlaneGeometry> = emptyList()

    private var width = 1
    private var height = 1
    private var flatProgram = 0
    private val model = FloatArray(16)
    private val view = FloatArray(16)
    private val projection = FloatArray(16)
    private val modelView = FloatArray(16)
    private val mvp = FloatArray(16)
    private val inverseMvp = FloatArray(16)

    private data class PlaneGeometry(
        val id: Long,
        val origin: FloatArray,
        val normal: FloatArray,
        val u: FloatArray,
        val v: FloatArray,
        val halfSize: Float,
        val active: Boolean,
        val fill: FloatBuffer,
        val outline: FloatBuffer
    )

    private val vertexShader = """
        uniform mat4 uMvp;
        attribute vec3 aPosition;
        void main() { gl_Position = uMvp * vec4(aPosition, 1.0); }
    """.trimIndent()

    private val fragmentShader = """
        precision mediump float;
        uniform vec4 uColor;
        void main() { gl_FragColor = uColor; }
    """.trimIndent()

    fun setMesh(value: NativeSceneMesh) {
        pendingMesh = value
        base.setMesh(value)
    }

    fun setSelectedFace(face: EditableCadFace) = base.setSelectedFace(face)
    fun setLivePreview(length: Float, diameter: Float) = base.setLivePreview(length, diameter)
    fun clearLivePreview() = base.clearLivePreview()
    fun worldPerPixel(): Float = base.worldPerPixel()
    fun pickFace(x: Float, y: Float): EditableCadFace = base.pickFace(x, y)
    fun manipulatorScreenVector(face: EditableCadFace): FloatArray? = base.manipulatorScreenVector(face)
    fun hitManipulator(x: Float, y: Float, radius: Float): Boolean = base.hitManipulator(x, y, radius)

    fun setReferencePlanes(value: List<CadViewportPlane>) {
        pendingPlanes = value.map { it.copy(origin = it.origin.copyOf(), normal = it.normal.copyOf()) }
        planesDirty = true
    }

    @Synchronized
    fun clearGenericSelection() {
        selection = null
        selectionBuffer = null
        selectionVertexCount = 0
    }

    @Synchronized
    fun pickGenericFace(screenX: Float, screenY: Float): CadViewportFaceSelection? {
        val ray = selectionRay(screenX, screenY) ?: return null
        val picked = topology?.pick(ray.first, ray.second)
        selection = picked
        rebuildSelectionBuffer(picked)
        return picked
    }

    @Synchronized
    fun pickReferencePlane(screenX: Float, screenY: Float): Long? {
        val ray = selectionRay(screenX, screenY) ?: return null
        var bestId: Long? = null
        var bestDistance = Float.POSITIVE_INFINITY
        planes.forEach { plane ->
            val denominator = CadBRepTopology.dot(plane.normal, ray.second)
            if (abs(denominator) < EPSILON) return@forEach
            val distance = CadBRepTopology.dot(plane.normal, CadBRepTopology.subtract(plane.origin, ray.first)) / denominator
            if (distance <= 0f || distance >= bestDistance) return@forEach
            val hit = floatArrayOf(
                ray.first[0] + ray.second[0] * distance,
                ray.first[1] + ray.second[1] * distance,
                ray.first[2] + ray.second[2] * distance
            )
            val local = CadBRepTopology.subtract(hit, plane.origin)
            if (abs(CadBRepTopology.dot(local, plane.u)) <= plane.halfSize &&
                abs(CadBRepTopology.dot(local, plane.v)) <= plane.halfSize
            ) {
                bestId = plane.id
                bestDistance = distance
            }
        }
        return bestId
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        base.onSurfaceCreated(gl, config)
        flatProgram = createProgram(vertexShader, fragmentShader)
        Matrix.setIdentityM(model, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, viewportWidth: Int, viewportHeight: Int) {
        base.onSurfaceChanged(gl, viewportWidth, viewportHeight)
        width = viewportWidth.coerceAtLeast(1)
        height = viewportHeight.coerceAtLeast(1)
    }

    @Synchronized
    override fun onDrawFrame(gl: GL10?) {
        base.onDrawFrame(gl)
        pendingMesh?.let { committed ->
            mesh = committed
            topology = CadBRepTopology(committed)
            clearGenericSelection()
            rebuildPlanes(pendingPlanes)
            pendingMesh = null
        }
        if (planesDirty) {
            rebuildPlanes(pendingPlanes)
            planesDirty = false
        }
        updateMvp()
        drawPlanes()
        drawSelection()
    }

    fun release() {
        base.release()
        if (flatProgram != 0) GLES20.glDeleteProgram(flatProgram)
        flatProgram = 0
        clearGenericSelection()
        planes = emptyList()
        topology = null
        mesh = null
    }

    private fun updateMvp() {
        camera.viewMatrix(view)
        camera.projectionMatrix(projection, width, height)
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)
    }

    private fun selectionRay(screenX: Float, screenY: Float): Pair<FloatArray, FloatArray>? {
        updateMvp()
        if (!Matrix.invertM(inverseMvp, 0, mvp, 0)) return null
        val near = unproject(screenX, screenY, -1f) ?: return null
        val far = unproject(screenX, screenY, 1f) ?: return null
        return near to CadBRepTopology.normalized(CadBRepTopology.subtract(far, near))
    }

    private fun unproject(x: Float, y: Float, z: Float): FloatArray? {
        val input = floatArrayOf(2f * x / width - 1f, 1f - 2f * y / height, z, 1f)
        val output = FloatArray(4)
        Matrix.multiplyMV(output, 0, inverseMvp, 0, input, 0)
        if (abs(output[3]) < EPSILON) return null
        return floatArrayOf(output[0] / output[3], output[1] / output[3], output[2] / output[3])
    }

    private fun rebuildSelectionBuffer(selected: CadViewportFaceSelection?) {
        val currentMesh = mesh
        if (selected == null || currentMesh == null) {
            selectionBuffer = null
            selectionVertexCount = 0
            return
        }
        val ordinals = selected.triangleOrdinals.take(MAX_SELECTION_TRIANGLES)
        val values = FloatArray(ordinals.size * 9)
        var cursor = 0
        ordinals.forEach { ordinal ->
            val indexCursor = ordinal * 3
            if (indexCursor + 2 >= currentMesh.indices.size) return@forEach
            repeat(3) { corner ->
                val vertex = currentMesh.indices[indexCursor + corner] * 6
                values[cursor++] = currentMesh.vertices[vertex]
                values[cursor++] = currentMesh.vertices[vertex + 1]
                values[cursor++] = currentMesh.vertices[vertex + 2]
            }
        }
        selectionBuffer = floatBuffer(values.copyOf(cursor))
        selectionVertexCount = cursor / 3
    }

    private fun rebuildPlanes(source: List<CadViewportPlane>) {
        val halfSize = ((mesh?.maxDimension ?: 100f) * 0.62f).coerceIn(8f, 1_000_000f)
        planes = source.filter { it.visible }.mapNotNull { plane ->
            val normal = runCatching { CadBRepTopology.normalized(plane.normal) }.getOrNull() ?: return@mapNotNull null
            val helper = if (abs(normal[2]) < 0.85f) floatArrayOf(0f, 0f, 1f) else floatArrayOf(0f, 1f, 0f)
            val u = CadBRepTopology.normalized(CadBRepTopology.cross(helper, normal))
            val v = CadBRepTopology.normalized(CadBRepTopology.cross(normal, u))
            fun corner(su: Float, sv: Float) = floatArrayOf(
                plane.origin[0] + u[0] * halfSize * su + v[0] * halfSize * sv,
                plane.origin[1] + u[1] * halfSize * su + v[1] * halfSize * sv,
                plane.origin[2] + u[2] * halfSize * su + v[2] * halfSize * sv
            )
            val a = corner(-1f, -1f)
            val b = corner(1f, -1f)
            val c = corner(1f, 1f)
            val d = corner(-1f, 1f)
            PlaneGeometry(
                plane.id,
                plane.origin.copyOf(),
                normal,
                u,
                v,
                halfSize,
                plane.active,
                floatBuffer(a + b + c + a + c + d),
                floatBuffer(a + b + b + c + c + d + d + a)
            )
        }
    }

    private fun drawPlanes() {
        if (planes.isEmpty()) return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(false)
        planes.forEach { plane ->
            val color = if (plane.active) floatArrayOf(0.08f, 0.48f, 0.92f) else floatArrayOf(0.16f, 0.62f, 0.78f)
            drawFlat(plane.fill, 6, GLES20.GL_TRIANGLES, color[0], color[1], color[2], if (plane.active) 0.22f else 0.10f, 1f)
            drawFlat(plane.outline, 8, GLES20.GL_LINES, color[0], color[1], color[2], if (plane.active) 0.95f else 0.62f, if (plane.active) 3f else 1.5f)
        }
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    private fun drawSelection() {
        val buffer = selectionBuffer ?: return
        if (selectionVertexCount <= 0) return
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-1f, -1f)
        GLES20.glDepthMask(false)
        drawFlat(buffer, selectionVertexCount, GLES20.GL_TRIANGLES, 1f, 0.42f, 0.02f, 0.94f, 1f)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    private fun drawFlat(buffer: FloatBuffer, count: Int, mode: Int, r: Float, g: Float, b: Float, a: Float, lineWidth: Float) {
        if (flatProgram == 0 || count <= 0) return
        GLES20.glUseProgram(flatProgram)
        val mvpLocation = GLES20.glGetUniformLocation(flatProgram, "uMvp")
        val colorLocation = GLES20.glGetUniformLocation(flatProgram, "uColor")
        val position = GLES20.glGetAttribLocation(flatProgram, "aPosition")
        if (position < 0) return
        GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
        GLES20.glUniform4f(colorLocation, r, g, b, a)
        GLES20.glLineWidth(lineWidth)
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, buffer)
        GLES20.glDrawArrays(mode, 0, count)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values)
            position(0)
        }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                GLES20.glDeleteShader(shader)
                return 0
            }
            return shader
        }
        val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertex == 0 || fragment == 0) return 0
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private companion object {
        const val EPSILON = 1e-7f
        const val MAX_SELECTION_TRIANGLES = 200_000
    }
}
