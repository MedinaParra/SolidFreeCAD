package com.example.nativecad.viewer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * SolidFreeCAD unified GLES2 renderer.
 *
 * OCCT returns 32-bit indices. For broad phone compatibility the renderer
 * remaps the mesh into independent chunks with unsigned 16-bit indices.
 */
class NativeCadRenderer : GLSurfaceView.Renderer {
    val camera = NativeCameraController()

    @Volatile
    private var pendingMesh: NativeSceneMesh? = null

    @Volatile
    private var pendingPlane: ReferencePlane? = null

    @Volatile
    private var planeChanged = false

    private data class RenderChunk(
        val vertices: FloatBuffer,
        val indices: ShortBuffer,
        val indexCount: Int
    )

    private var chunks: List<RenderChunk> = emptyList()
    private var activeMesh: NativeSceneMesh? = null
    private var selectedPlane: ReferencePlane? = null

    private var meshProgram = 0
    private var flatProgram = 0
    private var width = 1
    private var height = 1

    private var minorGridBuffer: FloatBuffer? = null
    private var minorGridVertices = 0
    private var majorGridBuffer: FloatBuffer? = null
    private var majorGridVertices = 0
    private var axisBuffer: FloatBuffer? = null
    private var axisVertices = 0
    private var planeFillBuffer: FloatBuffer? = null
    private var planeBorderBuffer: FloatBuffer? = null

    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private val meshVertexShader = """
        uniform mat4 uMvp;
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        varying vec3 vNormal;
        void main() {
            vNormal = normalize(aNormal);
            gl_Position = uMvp * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    private val meshFragmentShader = """
        precision mediump float;
        uniform vec4 uColor;
        uniform vec3 uLightDirection;
        varying vec3 vNormal;
        void main() {
            vec3 normal = normalize(vNormal);
            vec3 lightDirection = normalize(uLightDirection);
            float diffuse = max(dot(normal, lightDirection), 0.0);
            float reverseLight = max(dot(-normal, lightDirection), 0.0) * 0.12;
            float intensity = 0.38 + diffuse * 0.62 + reverseLight;
            gl_FragColor = vec4(uColor.rgb * intensity, uColor.a);
        }
    """.trimIndent()

    private val flatVertexShader = """
        uniform mat4 uMvp;
        attribute vec3 aPosition;
        void main() {
            gl_Position = uMvp * vec4(aPosition, 1.0);
        }
    """.trimIndent()

    private val flatFragmentShader = """
        precision mediump float;
        uniform vec4 uColor;
        void main() {
            gl_FragColor = uColor;
        }
    """.trimIndent()

    fun setMesh(mesh: NativeSceneMesh) {
        pendingMesh = mesh
    }

    fun setSelectedPlane(plane: ReferencePlane?) {
        pendingPlane = plane
        planeChanged = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Same light blue/grey family used by the original SolidFreeCAD viewport.
        GLES20.glClearColor(0.91f, 0.94f, 0.96f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        meshProgram = createProgram(meshVertexShader, meshFragmentShader)
        flatProgram = createProgram(flatVertexShader, flatFragmentShader)
        Matrix.setIdentityM(modelMatrix, 0)
        rebuildGuides(null)
    }

    override fun onSurfaceChanged(gl: GL10?, viewportWidth: Int, viewportHeight: Int) {
        width = viewportWidth.coerceAtLeast(1)
        height = viewportHeight.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        pendingMesh?.let { mesh ->
            activeMesh = mesh
            chunks = buildChunks(mesh)
            rebuildGuides(mesh)
            pendingMesh = null
        }
        if (planeChanged) {
            selectedPlane = pendingPlane
            rebuildPlane(activeMesh)
            planeChanged = false
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        camera.viewMatrix(viewMatrix)
        camera.projectionMatrix(projectionMatrix, width, height)
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

        drawGrid()
        drawAxes()
        drawMesh()
        drawSelectedPlane()
    }

    fun release() {
        if (meshProgram != 0) GLES20.glDeleteProgram(meshProgram)
        if (flatProgram != 0) GLES20.glDeleteProgram(flatProgram)
        meshProgram = 0
        flatProgram = 0
        chunks = emptyList()
        minorGridBuffer = null
        majorGridBuffer = null
        axisBuffer = null
        planeFillBuffer = null
        planeBorderBuffer = null
    }

    private fun drawMesh() {
        if (meshProgram == 0 || chunks.isEmpty()) return
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
        GLES20.glUseProgram(meshProgram)

        val mvp = GLES20.glGetUniformLocation(meshProgram, "uMvp")
        val color = GLES20.glGetUniformLocation(meshProgram, "uColor")
        val light = GLES20.glGetUniformLocation(meshProgram, "uLightDirection")
        val position = GLES20.glGetAttribLocation(meshProgram, "aPosition")
        val normal = GLES20.glGetAttribLocation(meshProgram, "aNormal")
        if (position < 0 || normal < 0) return

        GLES20.glUniformMatrix4fv(mvp, 1, false, mvpMatrix, 0)
        // SolidFreeCAD part blue.
        GLES20.glUniform4f(color, 0.37f, 0.62f, 0.82f, 1f)
        GLES20.glUniform3f(light, 0.35f, 0.45f, 1f)

        chunks.forEach { chunk ->
            chunk.vertices.position(0)
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 24, chunk.vertices)
            chunk.vertices.position(3)
            GLES20.glEnableVertexAttribArray(normal)
            GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 24, chunk.vertices)
            chunk.indices.position(0)
            GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                chunk.indexCount,
                GLES20.GL_UNSIGNED_SHORT,
                chunk.indices
            )
            GLES20.glDisableVertexAttribArray(position)
            GLES20.glDisableVertexAttribArray(normal)
        }
    }

    private fun drawGrid() {
        if (flatProgram == 0) return
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(false)
        drawFlat(minorGridBuffer, minorGridVertices, GLES20.GL_LINES, 0.72f, 0.79f, 0.84f, 0.62f, 1f)
        drawFlat(majorGridBuffer, majorGridVertices, GLES20.GL_LINES, 0.55f, 0.66f, 0.73f, 0.82f, 1.4f)
        GLES20.glDepthMask(true)
    }

    private fun drawAxes() {
        if (flatProgram == 0 || axisBuffer == null) return
        val buffer = axisBuffer ?: return
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(false)
        // X axis
        buffer.position(0)
        drawFlatSlice(buffer, 0, 2, 0.84f, 0.20f, 0.20f, 1f, 2.2f)
        // Y axis
        drawFlatSlice(buffer, 2, 2, 0.20f, 0.62f, 0.28f, 1f, 2.2f)
        // Z axis
        drawFlatSlice(buffer, 4, 2, 0.16f, 0.42f, 0.86f, 1f, 2.2f)
        GLES20.glDepthMask(true)
    }

    private fun drawSelectedPlane() {
        if (selectedPlane == null || flatProgram == 0) return
        val fill = planeFillBuffer ?: return
        val border = planeBorderBuffer ?: return

        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        drawFlat(fill, 6, GLES20.GL_TRIANGLES, 0.30f, 0.72f, 1.0f, 0.24f, 1f)

        // Keep the selected reference plane clearly visible, as in the tree selection.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        drawFlat(border, 4, GLES20.GL_LINE_LOOP, 0.18f, 0.62f, 0.96f, 1f, 3f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
    }

    private fun drawFlat(
        buffer: FloatBuffer?,
        vertexCount: Int,
        mode: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        lineWidth: Float
    ) {
        val actual = buffer ?: return
        if (vertexCount <= 0) return
        actual.position(0)
        drawFlatSlice(actual, 0, vertexCount, red, green, blue, alpha, lineWidth, mode)
    }

    private fun drawFlatSlice(
        buffer: FloatBuffer,
        firstVertex: Int,
        vertexCount: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        lineWidth: Float,
        mode: Int = GLES20.GL_LINES
    ) {
        GLES20.glUseProgram(flatProgram)
        val mvp = GLES20.glGetUniformLocation(flatProgram, "uMvp")
        val color = GLES20.glGetUniformLocation(flatProgram, "uColor")
        val position = GLES20.glGetAttribLocation(flatProgram, "aPosition")
        if (position < 0) return
        GLES20.glUniformMatrix4fv(mvp, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(color, red, green, blue, alpha)
        GLES20.glLineWidth(lineWidth)
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 12, buffer)
        GLES20.glDrawArrays(mode, firstVertex, vertexCount)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun rebuildGuides(mesh: NativeSceneMesh?) {
        val size = guideHalfSize(mesh)
        val step = gridStep(size)
        val lineCount = ceil(size / step).toInt().coerceIn(5, 60)
        val minor = ArrayList<Float>()
        val major = ArrayList<Float>()

        for (index in -lineCount..lineCount) {
            val coordinate = index * step
            val target = if (index % 5 == 0) major else minor
            // XY reference grid, Z-up CAD coordinates.
            target.addAll(listOf(-size, coordinate, 0f, size, coordinate, 0f))
            target.addAll(listOf(coordinate, -size, 0f, coordinate, size, 0f))
        }
        minorGridBuffer = floatBuffer(minor.toFloatArray())
        minorGridVertices = minor.size / 3
        majorGridBuffer = floatBuffer(major.toFloatArray())
        majorGridVertices = major.size / 3

        val axisLength = size
        axisBuffer = floatBuffer(
            floatArrayOf(
                -axisLength, 0f, 0f, axisLength, 0f, 0f,
                0f, -axisLength, 0f, 0f, axisLength, 0f,
                0f, 0f, -axisLength, 0f, 0f, axisLength
            )
        )
        axisVertices = 6
        rebuildPlane(mesh)
    }

    private fun rebuildPlane(mesh: NativeSceneMesh?) {
        val plane = selectedPlane
        if (plane == null) {
            planeFillBuffer = null
            planeBorderBuffer = null
            return
        }
        val size = guideHalfSize(mesh) * 0.72f
        val corners = when (plane) {
            ReferencePlane.XY -> floatArrayOf(
                -size, -size, 0f,
                size, -size, 0f,
                size, size, 0f,
                -size, size, 0f
            )
            ReferencePlane.XZ -> floatArrayOf(
                -size, 0f, -size,
                size, 0f, -size,
                size, 0f, size,
                -size, 0f, size
            )
            ReferencePlane.YZ -> floatArrayOf(
                0f, -size, -size,
                0f, size, -size,
                0f, size, size,
                0f, -size, size
            )
        }
        planeBorderBuffer = floatBuffer(corners)
        planeFillBuffer = floatBuffer(
            floatArrayOf(
                corners[0], corners[1], corners[2],
                corners[3], corners[4], corners[5],
                corners[6], corners[7], corners[8],
                corners[0], corners[1], corners[2],
                corners[6], corners[7], corners[8],
                corners[9], corners[10], corners[11]
            )
        )
    }

    private fun guideHalfSize(mesh: NativeSceneMesh?): Float {
        val dimension = mesh?.maxDimension ?: 100f
        return (dimension * 1.35f).coerceIn(10f, 1_000_000f)
    }

    private fun gridStep(halfSize: Float): Float {
        val raw = (halfSize * 2f / 20f).coerceAtLeast(0.0001f)
        val magnitude = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
        val normalized = raw / magnitude
        val factor = when {
            normalized < 2f -> 1f
            normalized < 5f -> 2f
            else -> 5f
        }
        return factor * magnitude
    }

    private fun buildChunks(mesh: NativeSceneMesh): List<RenderChunk> {
        val result = ArrayList<RenderChunk>()
        val mapping = HashMap<Int, Int>(65_536)
        val localVertices = FloatArray(MAX_CHUNK_VERTICES * 6)
        val localIndices = ShortArray(MAX_CHUNK_INDICES)
        var vertexCount = 0
        var indexCount = 0

        fun flush() {
            if (vertexCount == 0 || indexCount == 0) return
            val vertexCopy = localVertices.copyOf(vertexCount * 6)
            val indexCopy = localIndices.copyOf(indexCount)
            result += RenderChunk(
                vertices = floatBuffer(vertexCopy),
                indices = shortBuffer(indexCopy),
                indexCount = indexCount
            )
            mapping.clear()
            vertexCount = 0
            indexCount = 0
        }

        var cursor = 0
        while (cursor + 2 < mesh.indices.size) {
            val triangle = intArrayOf(
                mesh.indices[cursor],
                mesh.indices[cursor + 1],
                mesh.indices[cursor + 2]
            )
            cursor += 3
            if (triangle.any { it !in 0 until mesh.vertexCount }) continue
            val needed = triangle.count { !mapping.containsKey(it) }
            if ((vertexCount + needed > MAX_CHUNK_VERTICES || indexCount + 3 > MAX_CHUNK_INDICES) && indexCount > 0) {
                flush()
            }

            triangle.forEach { globalIndex ->
                val localIndex = mapping[globalIndex] ?: run {
                    val created = vertexCount
                    val sourceOffset = globalIndex * 6
                    val targetOffset = created * 6
                    for (component in 0 until 6) {
                        localVertices[targetOffset + component] = mesh.vertices[sourceOffset + component]
                    }
                    mapping[globalIndex] = created
                    vertexCount++
                    created
                }
                localIndices[indexCount++] = localIndex.toShort()
            }
        }
        flush()
        return result
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    private fun shortBuffer(values: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(values.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(values)
                position(0)
            }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertex == 0) return 0
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragment == 0) {
            GLES20.glDeleteShader(vertex)
            return 0
        }
        val result = GLES20.glCreateProgram()
        if (result == 0) return 0
        GLES20.glAttachShader(result, vertex)
        GLES20.glAttachShader(result, fragment)
        GLES20.glLinkProgram(result)
        val status = IntArray(1)
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        if (status[0] == 0) {
            Log.e("NativeCadRenderer", "Error enlazando shaders: ${GLES20.glGetProgramInfoLog(result)}")
            GLES20.glDeleteProgram(result)
            return 0
        }
        return result
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("NativeCadRenderer", "Error compilando shader: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private companion object {
        const val MAX_CHUNK_VERTICES = 60_000
        const val MAX_CHUNK_INDICES = 180_000
    }
}
