package com.example.faceui

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.features.CadVector3
import com.example.nativecad.viewer.NativeSceneMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.sqrt
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Adds reference-plane guides and generic planar-face picking without changing the face editor. */
class ReferencePlaneCompositeRenderer(
    private val delegate: GpuFaceDrivenCadRenderer
) : GLSurfaceView.Renderer {
    @Volatile private var pendingMesh: NativeSceneMesh? = null
    @Volatile private var pendingPlanes: List<ReferencePlaneOverlay>? = null
    private var mesh: NativeSceneMesh? = null
    private var planes: List<ReferencePlaneOverlay> = emptyList()
    private var program = 0
    private var width = 1
    private var height = 1
    private val view = FloatArray(16)
    private val projection = FloatArray(16)
    private val mvp = FloatArray(16)
    private val inverseMvp = FloatArray(16)

    fun setMesh(value: NativeSceneMesh) {
        pendingMesh = value
        delegate.setMesh(value)
    }

    fun setReferencePlanes(value: List<ReferencePlaneOverlay>) {
        pendingPlanes = value.toList()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        delegate.onSurfaceCreated(gl, config)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        delegate.onSurfaceChanged(gl, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingMesh?.let { mesh = it; pendingMesh = null }
        pendingPlanes?.let { planes = it; pendingPlanes = null }
        delegate.onDrawFrame(gl)
        drawPlanes()
    }

    fun release() {
        delegate.release()
        if (program != 0) GLES20.glDeleteProgram(program)
        program = 0
    }

    @Synchronized
    fun pickPlanarFace(screenX: Float, screenY: Float): PlanarFacePick? {
        val current = mesh ?: return null
        buildMvp()
        if (!Matrix.invertM(inverseMvp, 0, mvp, 0)) return null
        val near = unproject(screenX, screenY, -1f) ?: return null
        val far = unproject(screenX, screenY, 1f) ?: return null
        val direction = normalize(floatArrayOf(far[0] - near[0], far[1] - near[1], far[2] - near[2]))
        var bestT = Float.POSITIVE_INFINITY
        var best: PlanarFacePick? = null
        var cursor = 0
        var triangleIndex = 0
        while (cursor + 2 < current.indices.size) {
            val ia = current.indices[cursor]
            val ib = current.indices[cursor + 1]
            val ic = current.indices[cursor + 2]
            cursor += 3
            val a = position(current, ia)
            val b = position(current, ib)
            val c = position(current, ic)
            val t = rayTriangle(near, direction, a, b, c)
            if (t != null && t > 0f && t < bestT) {
                val na = normal(current, ia)
                val nb = normal(current, ib)
                val nc = normal(current, ic)
                val average = normalize(floatArrayOf(na[0] + nb[0] + nc[0], na[1] + nb[1] + nc[1], na[2] + nb[2] + nc[2]))
                if (dot(na, average) > PLANAR_DOT && dot(nb, average) > PLANAR_DOT && dot(nc, average) > PLANAR_DOT) {
                    val point = floatArrayOf(near[0] + direction[0] * t, near[1] + direction[1] * t, near[2] + direction[2] * t)
                    val edge = floatArrayOf(b[0] - a[0], b[1] - a[1], b[2] - a[2])
                    val projected = floatArrayOf(
                        edge[0] - average[0] * dot(edge, average),
                        edge[1] - average[1] * dot(edge, average),
                        edge[2] - average[2] * dot(edge, average)
                    )
                    val xAxis = if (sqrt(dot(projected, projected)) > 1e-5f) normalize(projected) else stableXAxis(average)
                    bestT = t
                    best = PlanarFacePick(point[0], point[1], point[2], average[0], average[1], average[2], xAxis[0], xAxis[1], xAxis[2], triangleIndex)
                }
            }
            triangleIndex++
        }
        return best
    }

    private fun drawPlanes() {
        if (program == 0 || planes.isEmpty()) return
        buildMvp()
        GLES20.glUseProgram(program)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val matrix = GLES20.glGetUniformLocation(program, "uMvp")
        val color = GLES20.glGetUniformLocation(program, "uColor")
        GLES20.glUniformMatrix4fv(matrix, 1, false, mvp, 0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        for (plane in planes) {
            val vertices = planeVertices(plane)
            val buffer = floatBuffer(vertices)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 0, buffer)
            if (plane.selected) GLES20.glUniform4f(color, 1f, 0.67f, 0.08f, 0.95f)
            else GLES20.glUniform4f(color, 0.15f, 0.55f, 0.88f, 0.72f)
            GLES20.glLineWidth(if (plane.selected) 3f else 2f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertices.size / 3)
        }
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun planeVertices(plane: ReferencePlaneOverlay): FloatArray {
        val normal = plane.normal.normalized()
        val x = (plane.xAxis - normal * plane.xAxis.dot(normal)).normalized()
        val y = normal.cross(x).normalized()
        val half = plane.size.toDouble() / 2.0
        val a = plane.origin - x * half - y * half
        val b = plane.origin + x * half - y * half
        val c = plane.origin + x * half + y * half
        val d = plane.origin - x * half + y * half
        val values = ArrayList<Float>()
        fun add(from: CadVector3, to: CadVector3) {
            values += from.x.toFloat(); values += from.y.toFloat(); values += from.z.toFloat()
            values += to.x.toFloat(); values += to.y.toFloat(); values += to.z.toFloat()
        }
        add(a, b); add(b, c); add(c, d); add(d, a)
        add(plane.origin - x * half, plane.origin + x * half)
        add(plane.origin - y * half, plane.origin + y * half)
        return values.toFloatArray()
    }

    private fun buildMvp() {
        delegate.camera.viewMatrix(view)
        delegate.camera.projectionMatrix(projection, width, height)
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
    }

    private fun unproject(x: Float, y: Float, z: Float): FloatArray? {
        val input = floatArrayOf(2f * x / width - 1f, 1f - 2f * y / height, z, 1f)
        val output = FloatArray(4)
        Matrix.multiplyMV(output, 0, inverseMvp, 0, input, 0)
        if (abs(output[3]) < 1e-8f) return null
        return floatArrayOf(output[0] / output[3], output[1] / output[3], output[2] / output[3])
    }

    private fun position(mesh: NativeSceneMesh, index: Int): FloatArray {
        val base = index * 6
        return floatArrayOf(mesh.vertices[base], mesh.vertices[base + 1], mesh.vertices[base + 2])
    }

    private fun normal(mesh: NativeSceneMesh, index: Int): FloatArray {
        val base = index * 6
        return normalize(floatArrayOf(mesh.vertices[base + 3], mesh.vertices[base + 4], mesh.vertices[base + 5]))
    }

    private fun rayTriangle(origin: FloatArray, direction: FloatArray, a: FloatArray, b: FloatArray, c: FloatArray): Float? {
        val edge1 = subtract(b, a)
        val edge2 = subtract(c, a)
        val p = cross(direction, edge2)
        val determinant = dot(edge1, p)
        if (abs(determinant) < 1e-7f) return null
        val inverse = 1f / determinant
        val tVector = subtract(origin, a)
        val u = dot(tVector, p) * inverse
        if (u !in 0f..1f) return null
        val q = cross(tVector, edge1)
        val v = dot(direction, q) * inverse
        if (v < 0f || u + v > 1f) return null
        val t = dot(edge2, q) * inverse
        return t.takeIf { it > 1e-6f }
    }

    private fun stableXAxis(normal: FloatArray): FloatArray {
        val seed = if (abs(normal[2]) < 0.92f) floatArrayOf(0f, 0f, 1f) else floatArrayOf(1f, 0f, 0f)
        return normalize(cross(seed, normal))
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val length = sqrt(dot(vector, vector)).coerceAtLeast(1e-8f)
        return floatArrayOf(vector[0] / length, vector[1] / length, vector[2] / length)
    }
    private fun subtract(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    private fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    private fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values); position(0)
        }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        fun compile(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) { GLES20.glDeleteShader(shader); return 0 }
            return shader
        }
        val vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (vertex == 0 || fragment == 0) return 0
        val result = GLES20.glCreateProgram()
        GLES20.glAttachShader(result, vertex); GLES20.glAttachShader(result, fragment); GLES20.glLinkProgram(result)
        GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
        return result
    }

    private companion object {
        const val PLANAR_DOT = 0.999f
        const val VERTEX_SHADER = "uniform mat4 uMvp; attribute vec3 aPosition; void main(){ gl_Position=uMvp*vec4(aPosition,1.0); }"
        const val FRAGMENT_SHADER = "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor=uColor; }"
    }
}
