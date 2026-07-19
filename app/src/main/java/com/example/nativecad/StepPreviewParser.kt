package com.example.nativecad

import com.example.nativecad.viewer.NativeSceneMesh
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

internal data class StepPreviewResult(
    val mesh: NativeSceneMesh?,
    val mode: String?,
    val sourcePointCount: Int,
    val sourceTriangleCount: Int,
    val truncated: Boolean
)

/**
 * Lightweight offline STEP preview parser.
 *
 * This is not a replacement for OpenCASCADE. It supports AP242 tessellated
 * entities when present and otherwise computes a geometric envelope from
 * CARTESIAN_POINT data so the user can verify scale and orientation.
 */
internal object StepPreviewParser {
    private const val MAX_ENTITIES = 500_000
    private const val MAX_RENDER_VERTICES = 65_535
    private const val MAX_RENDER_TRIANGLES = 300_000

    private data class Vec3(val x: Double, val y: Double, val z: Double)

    private val numberPattern = "[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[Ee][-+]?\\d+)?"
    private val pointTupleRegex = Regex(
        "\\(\\s*($numberPattern)\\s*,\\s*($numberPattern)\\s*,\\s*($numberPattern)\\s*\\)"
    )
    private val integerTupleRegex = Regex("\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)")
    private val referenceRegex = Regex("#(\\d+)")

    fun parse(text: String): StepPreviewResult {
        val entities = splitEntities(text)
        if (entities.isEmpty()) return StepPreviewResult(null, null, 0, 0, false)

        val pointLists = mutableMapOf<Int, List<Vec3>>()
        val loosePoints = ArrayList<Vec3>()

        for ((id, body) in entities) {
            val normalized = body.trimStart().uppercase(Locale.ROOT)
            when {
                normalized.startsWith("CARTESIAN_POINT_LIST_3D") -> {
                    val points = parsePointTuples(body)
                    if (points.isNotEmpty()) pointLists[id] = points
                }
                normalized.startsWith("CARTESIAN_POINT(") -> {
                    pointTupleRegex.find(body)?.toVec3()?.let(loosePoints::add)
                }
            }
        }

        val combinedPoints = ArrayList<Vec3>()
        val combinedTriangles = ArrayList<IntArray>()
        var sourceTriangles = 0
        var truncated = false

        for ((_, body) in entities) {
            if (!body.trimStart().uppercase(Locale.ROOT).startsWith("TRIANGULATED_FACE_SET")) continue
            val pointListId = referenceRegex.findAll(body)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .firstOrNull(pointLists::containsKey)
                ?: continue
            val points = pointLists.getValue(pointListId)
            if (points.isEmpty()) continue

            val base = combinedPoints.size
            combinedPoints.addAll(points)
            integerTupleRegex.findAll(body).forEach { match ->
                val a = match.groupValues[1].toIntOrNull() ?: return@forEach
                val b = match.groupValues[2].toIntOrNull() ?: return@forEach
                val c = match.groupValues[3].toIntOrNull() ?: return@forEach
                if (a in 1..points.size && b in 1..points.size && c in 1..points.size && a != b && b != c && a != c) {
                    sourceTriangles++
                    if (combinedTriangles.size < MAX_RENDER_TRIANGLES) {
                        combinedTriangles += intArrayOf(base + a - 1, base + b - 1, base + c - 1)
                    } else {
                        truncated = true
                    }
                }
            }
        }

        if (combinedTriangles.isNotEmpty()) {
            val mesh = buildTriangleMesh(combinedPoints, combinedTriangles)
            if (mesh != null) {
                return StepPreviewResult(
                    mesh = mesh,
                    mode = "Teselación STEP AP242",
                    sourcePointCount = combinedPoints.size,
                    sourceTriangleCount = sourceTriangles,
                    truncated = truncated || combinedPoints.size > MAX_RENDER_VERTICES
                )
            }
        }

        val envelopePoints = ArrayList<Vec3>()
        pointLists.values.forEach(envelopePoints::addAll)
        envelopePoints.addAll(loosePoints)
        if (envelopePoints.isEmpty()) {
            return StepPreviewResult(null, null, 0, sourceTriangles, truncated)
        }

        return StepPreviewResult(
            mesh = buildEnvelopeMesh(envelopePoints),
            mode = "Envolvente de puntos STEP",
            sourcePointCount = envelopePoints.size,
            sourceTriangleCount = sourceTriangles,
            truncated = truncated
        )
    }

    private fun splitEntities(text: String): Map<Int, String> {
        val result = LinkedHashMap<Int, String>()
        var cursor = 0
        while (cursor < text.length && result.size < MAX_ENTITIES) {
            val hash = text.indexOf('#', cursor)
            if (hash < 0) break
            var digitEnd = hash + 1
            while (digitEnd < text.length && text[digitEnd].isDigit()) digitEnd++
            val id = text.substring(hash + 1, digitEnd).toIntOrNull()
            if (id == null) {
                cursor = hash + 1
                continue
            }
            var equals = digitEnd
            while (equals < text.length && text[equals].isWhitespace()) equals++
            if (equals >= text.length || text[equals] != '=') {
                cursor = digitEnd
                continue
            }
            val bodyStart = equals + 1
            var end = bodyStart
            var quoted = false
            while (end < text.length) {
                val ch = text[end]
                if (ch == '\'') {
                    if (quoted && end + 1 < text.length && text[end + 1] == '\'') {
                        end += 2
                        continue
                    }
                    quoted = !quoted
                } else if (ch == ';' && !quoted) {
                    break
                }
                end++
            }
            if (end >= text.length) break
            result[id] = text.substring(bodyStart, end).trim()
            cursor = end + 1
        }
        return result
    }

    private fun parsePointTuples(body: String): List<Vec3> = pointTupleRegex.findAll(body)
        .mapNotNull { it.toVec3() }
        .filter { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
        .take(MAX_RENDER_VERTICES * 4)
        .toList()

    private fun MatchResult.toVec3(): Vec3? {
        val x = groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
        val y = groupValues.getOrNull(2)?.toDoubleOrNull() ?: return null
        val z = groupValues.getOrNull(3)?.toDoubleOrNull() ?: return null
        return Vec3(x, y, z)
    }

    private fun buildTriangleMesh(points: List<Vec3>, triangles: List<IntArray>): NativeSceneMesh? {
        val remap = LinkedHashMap<Int, Int>()
        val compactPoints = ArrayList<Vec3>()
        val compactTriangles = ArrayList<IntArray>()

        for (triangle in triangles) {
            val mapped = IntArray(3)
            var accepted = true
            for (corner in 0..2) {
                val oldIndex = triangle[corner]
                if (oldIndex !in points.indices) {
                    accepted = false
                    break
                }
                val existing = remap[oldIndex]
                if (existing != null) {
                    mapped[corner] = existing
                } else {
                    if (compactPoints.size >= MAX_RENDER_VERTICES) {
                        accepted = false
                        break
                    }
                    val newIndex = compactPoints.size
                    remap[oldIndex] = newIndex
                    compactPoints += points[oldIndex]
                    mapped[corner] = newIndex
                }
            }
            if (accepted && mapped[0] != mapped[1] && mapped[1] != mapped[2] && mapped[0] != mapped[2]) {
                compactTriangles += mapped
            }
        }

        if (compactPoints.isEmpty() || compactTriangles.isEmpty()) return null

        val normals = Array(compactPoints.size) { DoubleArray(3) }
        for (triangle in compactTriangles) {
            val p0 = compactPoints[triangle[0]]
            val p1 = compactPoints[triangle[1]]
            val p2 = compactPoints[triangle[2]]
            val ux = p1.x - p0.x
            val uy = p1.y - p0.y
            val uz = p1.z - p0.z
            val vx = p2.x - p0.x
            val vy = p2.y - p0.y
            val vz = p2.z - p0.z
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            if (abs(nx) + abs(ny) + abs(nz) < 1e-18) continue
            triangle.forEach { index ->
                normals[index][0] += nx
                normals[index][1] += ny
                normals[index][2] += nz
            }
        }

        val vertices = FloatArray(compactPoints.size * 6)
        compactPoints.forEachIndexed { index, point ->
            val normal = normals[index]
            val length = sqrt(normal[0] * normal[0] + normal[1] * normal[1] + normal[2] * normal[2])
            val offset = index * 6
            vertices[offset] = point.x.toFloat()
            vertices[offset + 1] = point.y.toFloat()
            vertices[offset + 2] = point.z.toFloat()
            if (length > 1e-12) {
                vertices[offset + 3] = (normal[0] / length).toFloat()
                vertices[offset + 4] = (normal[1] / length).toFloat()
                vertices[offset + 5] = (normal[2] / length).toFloat()
            } else {
                vertices[offset + 5] = 1f
            }
        }

        val indices = ShortArray(compactTriangles.size * 3)
        compactTriangles.forEachIndexed { triangleIndex, triangle ->
            indices[triangleIndex * 3] = triangle[0].toShort()
            indices[triangleIndex * 3 + 1] = triangle[1].toShort()
            indices[triangleIndex * 3 + 2] = triangle[2].toShort()
        }
        return createMesh(vertices, indices)
    }

    private fun buildEnvelopeMesh(points: List<Vec3>): NativeSceneMesh {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY
        points.forEach { point ->
            minX = minOf(minX, point.x); maxX = maxOf(maxX, point.x)
            minY = minOf(minY, point.y); maxY = maxOf(maxY, point.y)
            minZ = minOf(minZ, point.z); maxZ = maxOf(maxZ, point.z)
        }
        fun expandedMin(min: Double, max: Double) = if (abs(max - min) < 1e-6) min - 0.5 else min
        fun expandedMax(min: Double, max: Double) = if (abs(max - min) < 1e-6) max + 0.5 else max
        val x0 = expandedMin(minX, maxX).toFloat(); val x1 = expandedMax(minX, maxX).toFloat()
        val y0 = expandedMin(minY, maxY).toFloat(); val y1 = expandedMax(minY, maxY).toFloat()
        val z0 = expandedMin(minZ, maxZ).toFloat(); val z1 = expandedMax(minZ, maxZ).toFloat()

        val vertices = floatArrayOf(
            x0,y0,z1, 0f,0f,1f,  x1,y0,z1, 0f,0f,1f,  x1,y1,z1, 0f,0f,1f,  x0,y1,z1, 0f,0f,1f,
            x1,y0,z0, 0f,0f,-1f, x0,y0,z0, 0f,0f,-1f, x0,y1,z0, 0f,0f,-1f, x1,y1,z0, 0f,0f,-1f,
            x0,y1,z1, 0f,1f,0f,  x1,y1,z1, 0f,1f,0f,  x1,y1,z0, 0f,1f,0f,  x0,y1,z0, 0f,1f,0f,
            x0,y0,z0, 0f,-1f,0f, x1,y0,z0, 0f,-1f,0f, x1,y0,z1, 0f,-1f,0f, x0,y0,z1, 0f,-1f,0f,
            x1,y0,z1, 1f,0f,0f,  x1,y0,z0, 1f,0f,0f,  x1,y1,z0, 1f,0f,0f,  x1,y1,z1, 1f,0f,0f,
            x0,y0,z0, -1f,0f,0f, x0,y0,z1, -1f,0f,0f, x0,y1,z1, -1f,0f,0f, x0,y1,z0, -1f,0f,0f
        )
        val indices = shortArrayOf(
            0,1,2, 0,2,3, 4,5,6, 4,6,7, 8,9,10, 8,10,11,
            12,13,14, 12,14,15, 16,17,18, 16,18,19, 20,21,22, 20,22,23
        )
        return createMesh(vertices, indices)
    }

    private fun createMesh(vertices: FloatArray, indices: ShortArray): NativeSceneMesh {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        var index = 0
        while (index < vertices.size) {
            minX = minOf(minX, vertices[index]); maxX = maxOf(maxX, vertices[index])
            minY = minOf(minY, vertices[index + 1]); maxY = maxOf(maxY, vertices[index + 1])
            minZ = minOf(minZ, vertices[index + 2]); maxZ = maxOf(maxZ, vertices[index + 2])
            index += 6
        }
        return NativeSceneMesh(vertices, indices, minX, minY, minZ, maxX, maxY, maxZ)
    }
}
