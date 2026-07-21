package com.example.faceui

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.nativecad.viewer.NativeCameraController
import com.example.nativecad.viewer.NativeSceneMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

enum class EditableCadFace { NONE, TOP, SIDE }

/**
 * GLES2 renderer for the direct-manipulation SolidFreeCAD workspace.
 *
 * The active face is rendered as a true orange mesh overlay. The yellow
 * manipulator is built in model coordinates, so it stays attached to the
 * selected face while the user orbits, pans or zooms the camera.
 */
class FaceDrivenCadRenderer : GLSurfaceView.Renderer {
    val camera = NativeCameraController()

    @Volatile private var pendingMesh: NativeSceneMesh? = null
    @Volatile private var pendingSelection: EditableCadFace = EditableCadFace.NONE
    @Volatile private var selectionDirty = false

    @Volatile var selectedFace: EditableCadFace = EditableCadFace.NONE
        private set

    private data class RenderChunk(
        val vertices: FloatBuffer,
        val indices: ShortBuffer,
        val indexCount: Int
    )

    private var activeMesh: NativeSceneMesh? = null
    private var meshChunks: List<RenderChunk> = emptyList()
    private var topChunks: List<RenderChunk> = emptyList()
    private var sideChunks: List<RenderChunk> = emptyList()

    private var meshProgram = 0
    private var flatProgram = 0
    private var width = 1
    private var height = 1

    private var minorGrid: FloatBuffer? = null
    private var minorGridCount = 0
    private var majorGrid: FloatBuffer? = null
    private var majorGridCount = 0
    private var axes: FloatBuffer? = null
    private var arrowLine: FloatBuffer? = null
    private var arrowHead: FloatBuffer? = null
    private var arrowHeadCount = 0

    private val model = FloatArray(16)
    private val view = FloatArray(16)
    private val projection = FloatArray(16)
    private val modelView = FloatArray(16)
    private val mvp = FloatArray(16)
    private val lastMvp = FloatArray(16)
    private val inverseMvp = FloatArray(16)

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
            vec3 n = normalize(vNormal);
            vec3 l = normalize(uLightDirection);
            float diffuse = max(dot(n, l), 0.0);
            float reverseLight = max(dot(-n, l), 0.0) * 0.10;
            float intensity = 0.44 + diffuse * 0.56 + reverseLight;
            gl_FragColor = vec4(uColor.rgb * intensity, uColor.a);
        }
    """.trimIndent()

    private val flatVertexShader = """
        uniform mat4 uMvp;
        attribute vec3 aPosition;
        void main() { gl_Position = uMvp * vec4(aPosition, 1.0); }
    """.trimIndent()

    private val flatFragmentShader = """
        precision mediump float;
        uniform vec4 uColor;
        void main() { gl_FragColor = uColor; }
    """.trimIndent()

    fun setMesh(mesh: NativeSceneMesh) {
        pendingMesh = mesh
    }

    fun setSelectedFace(face: EditableCadFace) {
        pendingSelection = face
        selectionDirty = true
    }

    fun worldPerPixel(): Float {
        val denominator = minOf(width, height).coerceAtLeast(1).toFloat()
        return camera.radius * 1.35f / denominator
    }

    @Synchronized
    fun pickFace(screenX: Float, screenY: Float): EditableCadFace {
        val mesh = activeMesh ?: return EditableCadFace.NONE
        if (!Matrix.invertM(inverseMvp, 0, lastMvp, 0)) return EditableCadFace.NONE
        val near = unproject(screenX, screenY, -1f) ?: return EditableCadFace.NONE
        val far = unproject(screenX, screenY, 1f) ?: return EditableCadFace.NONE
        val dx = far[0] - near[0]
        val dy = far[1] - near[1]
        val dz = far[2] - near[2]
        val magnitude = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-8f)
        val rayX = dx / magnitude
        val rayY = dy / magnitude
        val rayZ = dz / magnitude

        val cx = (mesh.minX + mesh.maxX) * 0.5f
        val cy = (mesh.minY + mesh.maxY) * 0.5f
        val radius = maxOf(mesh.maxX - mesh.minX, mesh.maxY - mesh.minY) * 0.5f
        var bestFace = EditableCadFace.NONE
        var bestT = Float.POSITIVE_INFINITY

        if (abs(rayZ) > 1e-7f) {
            val t = (mesh.maxZ - near[2]) / rayZ
            if (t > 0f) {
                val hitX = near[0] + rayX * t
                val hitY = near[1] + rayY * t
                val radial = sqrt((hitX - cx) * (hitX - cx) + (hitY - cy) * (hitY - cy))
                if (radial <= radius * 1.04f) {
                    bestFace = EditableCadFace.TOP
                    bestT = t
                }
            }
        }

        val ox = near[0] - cx
        val oy = near[1] - cy
        val a = rayX * rayX + rayY * rayY
        val b = 2f * (ox * rayX + oy * rayY)
        val c = ox * ox + oy * oy - radius * radius
        val discriminant = b * b - 4f * a * c
        if (a > 1e-8f && discriminant >= 0f) {
            val root = sqrt(discriminant)
            val candidates = floatArrayOf((-b - root) / (2f * a), (-b + root) / (2f * a))
            for (t in candidates) {
                if (t <= 0f || t >= bestT) continue
                val z = near[2] + rayZ * t
                if (z in (mesh.minZ - mesh.maxDimension * 0.01f)..(mesh.maxZ + mesh.maxDimension * 0.01f)) {
                    bestFace = EditableCadFace.SIDE
                    bestT = t
                }
            }
        }
        setSelectedFace(bestFace)
        return bestFace
    }

    @Synchronized
    fun manipulatorScreenVector(face: EditableCadFace): FloatArray? {
        val pair = manipulatorEndpoints(face) ?: return null
        val start = project(pair.first) ?: return null
        val end = project(pair.second) ?: return null
        val dx = end[0] - start[0]
        val dy = end[1] - start[1]
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-5f)
        return floatArrayOf(dx / length, dy / length)
    }

    @Synchronized
    fun hitManipulator(screenX: Float, screenY: Float, radiusPx: Float): Boolean {
        val pair = manipulatorEndpoints(selectedFace) ?: return false
        val a = project(pair.first) ?: return false
        val b = project(pair.second) ?: return false
        val distance = distanceToSegment(screenX, screenY, a[0], a[1], b[0], b[1])
        return distance <= radiusPx
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.965f, 0.975f, 0.982f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        meshProgram = createProgram(meshVertexShader, meshFragmentShader)
        flatProgram = createProgram(flatVertexShader, flatFragmentShader)
        Matrix.setIdentityM(model, 0)
        rebuildGuides(null)
    }

    override fun onSurfaceChanged(gl: GL10?, viewportWidth: Int, viewportHeight: Int) {
        width = viewportWidth.coerceAtLeast(1)
        height = viewportHeight.coerceAtLeast(1)
        GLES20.glViewport(0, 0, width, height)
    }

    @Synchronized
    override fun onDrawFrame(gl: GL10?) {
        pendingMesh?.let { mesh ->
            activeMesh = mesh
            meshChunks = buildChunks(mesh) { true }
            topChunks = buildChunks(mesh) { triangleIsTop(mesh, it) }
            sideChunks = buildChunks(mesh) { triangleIsSide(mesh, it) }
            rebuildGuides(mesh)
            rebuildArrow(mesh, selectedFace)
            pendingMesh = null
        }
        if (selectionDirty) {
            selectedFace = pendingSelection
            rebuildArrow(activeMesh, selectedFace)
            selectionDirty = false
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        camera.viewMatrix(view)
        camera.projectionMatrix(projection, width, height)
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, modelView, 0)
        System.arraycopy(mvp, 0, lastMvp, 0, 16)

        drawGrid()
        drawAxes()
        drawChunks(meshChunks, 0.62f, 0.79f, 0.63f, 1f)
        when (selectedFace) {
            EditableCadFace.TOP -> drawSelection(topChunks)
            EditableCadFace.SIDE -> drawSelection(sideChunks)
            EditableCadFace.NONE -> Unit
        }
        drawManipulator()
    }

    fun release() {
        if (meshProgram != 0) GLES20.glDeleteProgram(meshProgram)
        if (flatProgram != 0) GLES20.glDeleteProgram(flatProgram)
        meshProgram = 0
        flatProgram = 0
        meshChunks = emptyList()
        topChunks = emptyList()
        sideChunks = emptyList()
    }

    private fun drawSelection(chunks: List<RenderChunk>) {
        GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
        GLES20.glPolygonOffset(-1f, -1f)
        drawChunks(chunks, 1.0f, 0.42f, 0.02f, 0.98f)
        GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
    }

    private fun drawChunks(chunks: List<RenderChunk>, r: Float, g: Float, b: Float, a: Float) {
        if (meshProgram == 0 || chunks.isEmpty()) return
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
        GLES20.glUseProgram(meshProgram)
        val mvpLocation = GLES20.glGetUniformLocation(meshProgram, "uMvp")
        val colorLocation = GLES20.glGetUniformLocation(meshProgram, "uColor")
        val lightLocation = GLES20.glGetUniformLocation(meshProgram, "uLightDirection")
        val position = GLES20.glGetAttribLocation(meshProgram, "aPosition")
        val normal = GLES20.glGetAttribLocation(meshProgram, "aNormal")
        if (position < 0 || normal < 0) return
        GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
        GLES20.glUniform4f(colorLocation, r, g, b, a)
        GLES20.glUniform3f(lightLocation, 0.35f, 0.45f, 1f)
        chunks.forEach { chunk ->
            chunk.vertices.position(0)
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 24, chunk.vertices)
            chunk.vertices.position(3)
            GLES20.glEnableVertexAttribArray(normal)
            GLES20.glVertexAttribPointer(normal, 3, GLES20.GL_FLOAT, false, 24, chunk.vertices)
            chunk.indices.position(0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, chunk.indexCount, GLES20.GL_UNSIGNED_SHORT, chunk.indices)
            GLES20.glDisableVertexAttribArray(position)
            GLES20.glDisableVertexAttribArray(normal)
        }
    }

    private fun drawGrid() {
        GLES20.glDepthMask(false)
        drawFlat(minorGrid, minorGridCount, GLES20.GL_LINES, 0.80f, 0.84f, 0.87f, 0.75f, 1f)
        drawFlat(majorGrid, majorGridCount, GLES20.GL_LINES, 0.65f, 0.70f, 0.74f, 0.90f, 1.4f)
        GLES20.glDepthMask(true)
    }

    private fun drawAxes() {
        val buffer = axes ?: return
        GLES20.glDepthMask(false)
        drawFlatSlice(buffer, 0, 2, GLES20.GL_LINES, 0.92f, 0.12f, 0.10f, 1f, 2.5f)
        drawFlatSlice(buffer, 2, 2, GLES20.GL_LINES, 0.08f, 0.62f, 0.18f, 1f, 2.5f)
        drawFlatSlice(buffer, 4, 2, GLES20.GL_LINES, 0.10f, 0.32f, 0.94f, 1f, 2.5f)
        GLES20.glDepthMask(true)
    }

    private fun drawManipulator() {
        if (selectedFace == EditableCadFace.NONE) return
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        drawFlat(arrowLine, 2, GLES20.GL_LINES, 1f, 0.86f, 0.02f, 1f, 6f)
        drawFlat(arrowHead, arrowHeadCount, GLES20.GL_TRIANGLES, 1f, 0.86f, 0.02f, 1f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawFlat(
        buffer: FloatBuffer?, count: Int, mode: Int,
        r: Float, g: Float, b: Float, a: Float, lineWidth: Float
    ) {
        val actual = buffer ?: return
        if (count <= 0 || flatProgram == 0) return
        drawFlatSlice(actual, 0, count, mode, r, g, b, a, lineWidth)
    }

    private fun drawFlatSlice(
        buffer: FloatBuffer, first: Int, count: Int, mode: Int,
        r: Float, g: Float, b: Float, a: Float, lineWidth: Float
    ) {
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
        GLES20.glDrawArrays(mode, first, count)
        GLES20.glDisableVertexAttribArray(position)
    }

    private fun rebuildGuides(mesh: NativeSceneMesh?) {
        val halfSize = ((mesh?.maxDimension ?: 100f) * 1.35f).coerceIn(10f, 1_000_000f)
        val raw = (halfSize * 2f / 20f).coerceAtLeast(0.0001f)
        val magnitude = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
        val normalized = raw / magnitude
        val step = when { normalized < 2f -> magnitude; normalized < 5f -> 2f * magnitude; else -> 5f * magnitude }
        val count = ceil(halfSize / step).toInt().coerceIn(5, 60)
        val minor = ArrayList<Float>()
        val major = ArrayList<Float>()
        for (index in -count..count) {
            val coordinate = index * step
            val target = if (index % 5 == 0) major else minor
            target.addAll(listOf(-halfSize, coordinate, 0f, halfSize, coordinate, 0f))
            target.addAll(listOf(coordinate, -halfSize, 0f, coordinate, halfSize, 0f))
        }
        minorGrid = floatBuffer(minor.toFloatArray())
        minorGridCount = minor.size / 3
        majorGrid = floatBuffer(major.toFloatArray())
        majorGridCount = major.size / 3
        axes = floatBuffer(floatArrayOf(
            -halfSize, 0f, 0f, halfSize, 0f, 0f,
            0f, -halfSize, 0f, 0f, halfSize, 0f,
            0f, 0f, -halfSize, 0f, 0f, halfSize
        ))
    }

    private fun rebuildArrow(mesh: NativeSceneMesh?, face: EditableCadFace) {
        val actual = mesh ?: run {
            arrowLine = null
            arrowHead = null
            arrowHeadCount = 0
            return
        }
        val endpoints = manipulatorEndpoints(face) ?: run {
            arrowLine = null
            arrowHead = null
            arrowHeadCount = 0
            return
        }
        val start = endpoints.first
        val tip = endpoints.second
        val direction = normalize(floatArrayOf(tip[0] - start[0], tip[1] - start[1], tip[2] - start[2]))
        val headLength = actual.maxDimension * 0.16f
        val neck = floatArrayOf(
            tip[0] - direction[0] * headLength,
            tip[1] - direction[1] * headLength,
            tip[2] - direction[2] * headLength
        )
        val u = if (face == EditableCadFace.TOP) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f)
        val v = if (face == EditableCadFace.TOP) floatArrayOf(0f, 1f, 0f) else floatArrayOf(0f, 0f, 1f)
        val radius = headLength * 0.34f
        val ring = arrayOf(
            offset(neck, u, radius), offset(neck, v, radius),
            offset(neck, u, -radius), offset(neck, v, -radius)
        )
        val triangles = ArrayList<Float>(36)
        for (index in ring.indices) {
            val next = ring[(index + 1) % ring.size]
            triangles.addAll(tip.toList())
            triangles.addAll(ring[index].toList())
            triangles.addAll(next.toList())
        }
        arrowLine = floatBuffer(start + tip)
        arrowHead = floatBuffer(triangles.toFloatArray())
        arrowHeadCount = triangles.size / 3
    }

    private fun manipulatorEndpoints(face: EditableCadFace): Pair<FloatArray, FloatArray>? {
        val mesh = activeMesh ?: return null
        val cx = (mesh.minX + mesh.maxX) * 0.5f
        val cy = (mesh.minY + mesh.maxY) * 0.5f
        val cz = (mesh.minZ + mesh.maxZ) * 0.5f
        val length = mesh.maxDimension * 0.72f
        return when (face) {
            EditableCadFace.TOP -> floatArrayOf(cx, cy, mesh.maxZ) to floatArrayOf(cx, cy, mesh.maxZ + length)
            EditableCadFace.SIDE -> floatArrayOf(mesh.maxX, cy, cz) to floatArrayOf(mesh.maxX + length, cy, cz)
            EditableCadFace.NONE -> null
        }
    }

    private fun triangleIsTop(mesh: NativeSceneMesh, triangle: IntArray): Boolean {
        val tolerance = mesh.maxDimension * 0.012f
        var averageZ = 0f
        var averageNormalZ = 0f
        triangle.forEach { index ->
            val base = index * 6
            averageZ += mesh.vertices[base + 2]
            averageNormalZ += mesh.vertices[base + 5]
        }
        averageZ /= 3f
        averageNormalZ /= 3f
        return averageZ >= mesh.maxZ - tolerance && averageNormalZ > 0.35f
    }

    private fun triangleIsSide(mesh: NativeSceneMesh, triangle: IntArray): Boolean {
        var normalZ = 0f
        triangle.forEach { normalZ += mesh.vertices[it * 6 + 5] }
        normalZ /= 3f
        return abs(normalZ) < 0.72f
    }

    private fun buildChunks(mesh: NativeSceneMesh, include: (IntArray) -> Boolean): List<RenderChunk> {
        val result = ArrayList<RenderChunk>()
        val mapping = HashMap<Int, Int>(65_536)
        val localVertices = FloatArray(MAX_CHUNK_VERTICES * 6)
        val localIndices = ShortArray(MAX_CHUNK_INDICES)
        var vertexCount = 0
        var indexCount = 0

        fun flush() {
            if (vertexCount == 0 || indexCount == 0) return
            result += RenderChunk(
                floatBuffer(localVertices.copyOf(vertexCount * 6)),
                shortBuffer(localIndices.copyOf(indexCount)),
                indexCount
            )
            mapping.clear()
            vertexCount = 0
            indexCount = 0
        }

        var cursor = 0
        while (cursor + 2 < mesh.indices.size) {
            val triangle = intArrayOf(mesh.indices[cursor], mesh.indices[cursor + 1], mesh.indices[cursor + 2])
            cursor += 3
            if (!include(triangle)) continue
            val needed = triangle.count { !mapping.containsKey(it) }
            if ((vertexCount + needed > MAX_CHUNK_VERTICES || indexCount + 3 > MAX_CHUNK_INDICES) && indexCount > 0) flush()
            triangle.forEach { globalIndex ->
                val localIndex = mapping[globalIndex] ?: run {
                    val created = vertexCount
                    val source = globalIndex * 6
                    val target = created * 6
                    for (component in 0 until 6) localVertices[target + component] = mesh.vertices[source + component]
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

    private fun unproject(x: Float, y: Float, z: Float): FloatArray? {
        val input = floatArrayOf(2f * x / width - 1f, 1f - 2f * y / height, z, 1f)
        val output = FloatArray(4)
        Matrix.multiplyMV(output, 0, inverseMvp, 0, input, 0)
        if (abs(output[3]) < 1e-8f) return null
        return floatArrayOf(output[0] / output[3], output[1] / output[3], output[2] / output[3])
    }

    private fun project(point: FloatArray): FloatArray? {
        val input = floatArrayOf(point[0], point[1], point[2], 1f)
        val output = FloatArray(4)
        Matrix.multiplyMV(output, 0, lastMvp, 0, input, 0)
        if (abs(output[3]) < 1e-8f) return null
        val nx = output[0] / output[3]
        val ny = output[1] / output[3]
        return floatArrayOf((nx + 1f) * 0.5f * width, (1f - ny) * 0.5f * height)
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val length = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-8f)
        return floatArrayOf(vector[0] / length, vector[1] / length, vector[2] / length)
    }

    private fun offset(point: FloatArray, direction: FloatArray, scale: Float): FloatArray =
        floatArrayOf(point[0] + direction[0] * scale, point[1] + direction[1] * scale, point[2] + direction[2] * scale)

    private fun distanceToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax
        val vy = by - ay
        val lengthSquared = vx * vx + vy * vy
        if (lengthSquared <= 1e-8f) return sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        val t = (((px - ax) * vx + (py - ay) * vy) / lengthSquared).coerceIn(0f, 1f)
        val dx = px - (ax + t * vx)
        val dy = py - (ay + t * vy)
        return sqrt(dx * dx + dy * dy)
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values); position(0)
        }

    private fun shortBuffer(values: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(values.size * Short.SIZE_BYTES).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
            put(values); position(0)
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
        const val MAX_CHUNK_VERTICES = 60_000
        const val MAX_CHUNK_INDICES = 180_000
    }
}
