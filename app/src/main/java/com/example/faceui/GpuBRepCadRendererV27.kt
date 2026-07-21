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

/** Adds topology multiselection and visible planes without replacing GPU/VSYNC direct editing. */
class GpuBRepCadRendererV27 : GLSurfaceView.Renderer {
    private val base = GpuFaceDrivenCadRenderer()
    val camera: NativeCameraController get() = base.camera
    val selectedFace: EditableCadFace get() = base.selectedFace

    @Volatile private var pendingMesh: NativeSceneMesh? = null
    @Volatile private var pendingNativeTopology: CadNativeTopologySnapshotV30? = null
    @Volatile private var pendingPlanes: List<CadViewportPlane> = emptyList()
    @Volatile private var planesDirty = false
    private var mesh: NativeSceneMesh? = null
    private var topology: CadBRepTopologyV27? = null
    private var selectionGeometries: List<SelectionGeometry> = emptyList()
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

    private data class SelectionGeometry(
        val buffer: FloatBuffer,
        val vertexCount: Int,
        val primitive: Int,
        val color: FloatArray,
        val lineWidth: Float = 1f,
        val pointSize: Float = 1f,
        val active: Boolean
    )

    private val vertexShader = """
        uniform mat4 uMvp;
        attribute vec3 aPosition;
        uniform float uPointSize;
        void main(){ gl_Position=uMvp*vec4(aPosition,1.0); gl_PointSize=uPointSize; }
    """.trimIndent()

    private val fragmentShader = """
        precision mediump float;
        uniform vec4 uColor;
        void main(){ gl_FragColor=uColor; }
    """.trimIndent()

    fun setMesh(value: NativeSceneMesh, nativeTopology: CadNativeTopologySnapshotV30? = null) {
        pendingNativeTopology = nativeTopology
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
        selectionGeometries = emptyList()
    }

    @Synchronized
    fun setTopologySelections(selectionSet: CadTopologySelectionSetV29) {
        selectionGeometries = selectionSet.selections.mapNotNull { selection ->
            buildSelectionGeometry(selection, selection.id == selectionSet.activeId)
        }.sortedBy { it.active }
    }

    @Synchronized
    fun pickTopology(x: Float, y: Float, mode: CadViewportSelectionModeV27): CadViewportSelectionV27? =
        pickTopologyCandidates(x, y, mode).firstOrNull()

    @Synchronized
    fun pickTopologyCandidates(x: Float, y: Float, mode: CadViewportSelectionModeV27): List<CadViewportSelectionV27> {
        val ray = selectionRay(x, y) ?: return emptyList()
        val tolerance = (base.worldPerPixel() * PICK_RADIUS_PX)
            .coerceAtLeast((mesh?.maxDimension ?: 1f) * 2e-4f)
        return topology?.pickCandidates(ray.first, ray.second, mode, tolerance).orEmpty()
    }

    @Synchronized
    fun pickReferencePlane(x: Float, y: Float): Long? {
        val ray = selectionRay(x, y) ?: return null
        var id: Long? = null
        var best = Float.POSITIVE_INFINITY
        planes.forEach { plane ->
            val denominator = CadBRepTopologyV27.dot(plane.normal, ray.second)
            if (abs(denominator) < EPSILON) return@forEach
            val distance = CadBRepTopologyV27.dot(
                plane.normal,
                CadBRepTopologyV27.sub(plane.origin, ray.first)
            ) / denominator
            if (distance <= 0f || distance >= best) return@forEach
            val hit = floatArrayOf(
                ray.first[0] + ray.second[0] * distance,
                ray.first[1] + ray.second[1] * distance,
                ray.first[2] + ray.second[2] * distance
            )
            val local = CadBRepTopologyV27.sub(hit, plane.origin)
            if (
                abs(CadBRepTopologyV27.dot(local, plane.u)) <= plane.halfSize &&
                abs(CadBRepTopologyV27.dot(local, plane.v)) <= plane.halfSize
            ) {
                id = plane.id
                best = distance
            }
        }
        return id
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        base.onSurfaceCreated(gl, config)
        flatProgram = createProgram(vertexShader, fragmentShader)
        Matrix.setIdentityM(model, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        base.onSurfaceChanged(gl, width, height)
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
    }

    @Synchronized
    override fun onDrawFrame(gl: GL10?) {
        base.onDrawFrame(gl)
        pendingMesh?.let { nextMesh ->
            mesh = nextMesh
            val native = pendingNativeTopology
            topology = CadBRepTopologyV27(
                mesh = nextMesh,
                triangleFaceIds = native?.triangleFaceIds,
                nativeFaces = native?.faces.orEmpty(),
                nativeRevision = native?.revision
            )
            clearGenericSelection()
            rebuildPlanes(pendingPlanes)
            pendingMesh = null
            pendingNativeTopology = null
        }
        if (planesDirty) {
            rebuildPlanes(pendingPlanes)
            planesDirty = false
        }
        updateMvp()
        drawPlanes()
        drawSelections()
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

    private fun selectionRay(x: Float, y: Float): Pair<FloatArray, FloatArray>? {
        updateMvp()
        if (!Matrix.invertM(inverseMvp, 0, mvp, 0)) return null
        val near = unproject(x, y, -1f) ?: return null
        val far = unproject(x, y, 1f) ?: return null
        return near to CadBRepTopologyV27.norm(CadBRepTopologyV27.sub(far, near))
    }

    private fun unproject(x: Float, y: Float, z: Float): FloatArray? {
        val input = floatArrayOf(2f * x / width - 1f, 1f - 2f * y / height, z, 1f)
        val output = FloatArray(4)
        Matrix.multiplyMV(output, 0, inverseMvp, 0, input, 0)
        if (abs(output[3]) < EPSILON) return null
        return floatArrayOf(output[0] / output[3], output[1] / output[3], output[2] / output[3])
    }

    private fun buildSelectionGeometry(
        selected: CadViewportSelectionV27,
        active: Boolean
    ): SelectionGeometry? {
        val current = mesh ?: return null
        return when (selected) {
            is CadViewportFaceSelectionV27 -> {
                val ordinals = selected.triangleOrdinals.take(MAX_SELECTION_TRIANGLES)
                val values = FloatArray(ordinals.size * 9)
                var cursor = 0
                ordinals.forEach { ordinal ->
                    val indexOffset = ordinal * 3
                    if (indexOffset + 2 >= current.indices.size) return@forEach
                    repeat(3) { corner ->
                        val vertexOffset = current.indices[indexOffset + corner] * 6
                        values[cursor++] = current.vertices[vertexOffset]
                        values[cursor++] = current.vertices[vertexOffset + 1]
                        values[cursor++] = current.vertices[vertexOffset + 2]
                    }
                }
                if (cursor == 0) null else SelectionGeometry(
                    buffer = floatBuffer(values.copyOf(cursor)),
                    vertexCount = cursor / 3,
                    primitive = GLES20.GL_TRIANGLES,
                    color = if (active) ACTIVE_FACE else SECONDARY_FACE,
                    active = active
                )
            }
            is CadViewportEdgeSelectionV27 -> SelectionGeometry(
                buffer = floatBuffer(selected.start + selected.end),
                vertexCount = 2,
                primitive = GLES20.GL_LINES,
                color = if (active) ACTIVE_EDGE else SECONDARY_EDGE,
                lineWidth = if (active) 6f else 3f,
                active = active
            )
            is CadViewportVertexSelectionV27 -> SelectionGeometry(
                buffer = floatBuffer(selected.point.copyOf()),
                vertexCount = 1,
                primitive = GLES20.GL_POINTS,
                color = if (active) ACTIVE_VERTEX else SECONDARY_VERTEX,
                pointSize = if (active) 18f else 12f,
                active = active
            )
            is CadViewportLoopSelectionV27 -> {
                val source = selected.orderedPoints
                val pointCount = source.size / 3
                if (pointCount < 2) return null
                val values = FloatArray((pointCount - 1) * 6)
                var cursor = 0
                for (index in 0 until pointCount - 1) {
                    val a = index * 3
                    val b = (index + 1) * 3
                    values[cursor++] = source[a]
                    values[cursor++] = source[a + 1]
                    values[cursor++] = source[a + 2]
                    values[cursor++] = source[b]
                    values[cursor++] = source[b + 1]
                    values[cursor++] = source[b + 2]
                }
                SelectionGeometry(
                    buffer = floatBuffer(values),
                    vertexCount = values.size / 3,
                    primitive = GLES20.GL_LINES,
                    color = loopColor(selected.role, active),
                    lineWidth = if (active) 6f else 3f,
                    active = active
                )
            }
        }
    }

    private fun loopColor(role: CadLoopRoleV29, active: Boolean): FloatArray = when (role) {
        CadLoopRoleV29.OUTER -> if (active) ACTIVE_OUTER_LOOP else SECONDARY_OUTER_LOOP
        CadLoopRoleV29.INNER -> if (active) ACTIVE_INNER_LOOP else SECONDARY_INNER_LOOP
        CadLoopRoleV29.OPEN -> if (active) ACTIVE_OPEN_LOOP else SECONDARY_OPEN_LOOP
    }

    private fun rebuildPlanes(source: List<CadViewportPlane>) {
        val half = ((mesh?.maxDimension ?: 100f) * .62f).coerceIn(8f, 1_000_000f)
        planes = source.filter { it.visible }.mapNotNull { plane ->
            val normal = runCatching { CadBRepTopologyV27.norm(plane.normal) }.getOrNull()
                ?: return@mapNotNull null
            val helper = if (abs(normal[2]) < .85f) {
                floatArrayOf(0f, 0f, 1f)
            } else {
                floatArrayOf(0f, 1f, 0f)
            }
            val u = CadBRepTopologyV27.norm(CadBRepTopologyV27.cross(helper, normal))
            val v = CadBRepTopologyV27.norm(CadBRepTopologyV27.cross(normal, u))
            fun corner(su: Float, sv: Float) = floatArrayOf(
                plane.origin[0] + u[0] * half * su + v[0] * half * sv,
                plane.origin[1] + u[1] * half * su + v[1] * half * sv,
                plane.origin[2] + u[2] * half * su + v[2] * half * sv
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
                half,
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
            val color = if (plane.active) floatArrayOf(.08f, .48f, .92f) else floatArrayOf(.16f, .62f, .78f)
            drawFlat(
                plane.fill, 6, GLES20.GL_TRIANGLES,
                color[0], color[1], color[2], if (plane.active) .22f else .10f,
                lineWidth = 1f, pointSize = 1f
            )
            drawFlat(
                plane.outline, 8, GLES20.GL_LINES,
                color[0], color[1], color[2], if (plane.active) .95f else .62f,
                lineWidth = if (plane.active) 3f else 1.5f, pointSize = 1f
            )
        }
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    private fun drawSelections() {
        if (selectionGeometries.isEmpty()) return
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(false)
        selectionGeometries.forEach { geometry ->
            if (geometry.primitive == GLES20.GL_TRIANGLES) {
                GLES20.glEnable(GLES20.GL_POLYGON_OFFSET_FILL)
                GLES20.glPolygonOffset(if (geometry.active) -1.5f else -.8f, if (geometry.active) -1.5f else -.8f)
            }
            val color = geometry.color
            drawFlat(
                geometry.buffer,
                geometry.vertexCount,
                geometry.primitive,
                color[0], color[1], color[2], color[3],
                geometry.lineWidth,
                geometry.pointSize
            )
            if (geometry.primitive == GLES20.GL_TRIANGLES) {
                GLES20.glDisable(GLES20.GL_POLYGON_OFFSET_FILL)
            }
        }
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    private fun drawFlat(
        buffer: FloatBuffer,
        count: Int,
        mode: Int,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        lineWidth: Float,
        pointSize: Float
    ) {
        if (flatProgram == 0 || count <= 0) return
        GLES20.glUseProgram(flatProgram)
        val mvpLocation = GLES20.glGetUniformLocation(flatProgram, "uMvp")
        val colorLocation = GLES20.glGetUniformLocation(flatProgram, "uColor")
        val positionLocation = GLES20.glGetAttribLocation(flatProgram, "aPosition")
        if (positionLocation < 0) return
        GLES20.glUniformMatrix4fv(mvpLocation, 1, false, mvp, 0)
        GLES20.glUniform4f(colorLocation, red, green, blue, alpha)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(flatProgram, "uPointSize"), pointSize)
        GLES20.glLineWidth(lineWidth)
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(positionLocation, 3, GLES20.GL_FLOAT, false, 12, buffer)
        GLES20.glDrawArrays(mode, 0, count)
        GLES20.glDisableVertexAttribArray(positionLocation)
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
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
        const val PICK_RADIUS_PX = 18f

        val ACTIVE_FACE = floatArrayOf(1f, .42f, .02f, .94f)
        val SECONDARY_FACE = floatArrayOf(.06f, .58f, .94f, .34f)
        val ACTIVE_EDGE = floatArrayOf(1f, .72f, .02f, 1f)
        val SECONDARY_EDGE = floatArrayOf(.12f, .72f, 1f, .82f)
        val ACTIVE_VERTEX = floatArrayOf(1f, .18f, .04f, 1f)
        val SECONDARY_VERTEX = floatArrayOf(.12f, .86f, 1f, .9f)
        val ACTIVE_OUTER_LOOP = floatArrayOf(.10f, .84f, .35f, 1f)
        val SECONDARY_OUTER_LOOP = floatArrayOf(.18f, .68f, .42f, .82f)
        val ACTIVE_INNER_LOOP = floatArrayOf(.95f, .16f, .68f, 1f)
        val SECONDARY_INNER_LOOP = floatArrayOf(.76f, .24f, .72f, .82f)
        val ACTIVE_OPEN_LOOP = floatArrayOf(1f, .48f, .08f, 1f)
        val SECONDARY_OPEN_LOOP = floatArrayOf(.96f, .62f, .18f, .82f)
    }
}
