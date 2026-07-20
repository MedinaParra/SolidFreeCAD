package com.example.faceui

import com.example.nativecad.viewer.NativeSceneMesh
import kotlin.math.abs
import kotlin.math.sqrt

/** A topology-aware viewport selection derived from the rendered BRep triangulation. */
data class CadViewportFaceSelection(
    val id: String,
    val triangleOrdinals: IntArray,
    val point: FloatArray,
    val normal: FloatArray,
    val planar: Boolean
)

data class CadViewportPlane(
    val id: Long,
    val origin: FloatArray,
    val normal: FloatArray,
    val visible: Boolean,
    val active: Boolean
)

/**
 * Lightweight topology cache for touch picking.
 *
 * It is rebuilt only when a definitive mesh is committed. Ray tests run on tap,
 * never during drag, so the live GPU preview remains independent of topology work.
 */
class CadBRepTopology(private val mesh: NativeSceneMesh) {
    private data class Edge(val a: Int, val b: Int) {
        companion object {
            fun of(first: Int, second: Int) = if (first <= second) Edge(first, second) else Edge(second, first)
        }
    }

    private data class Triangle(
        val indices: IntArray,
        val normal: FloatArray,
        val centroid: FloatArray
    )

    private val triangles: List<Triangle> = List(mesh.triangleCount) { ordinal ->
        val cursor = ordinal * 3
        val indices = intArrayOf(mesh.indices[cursor], mesh.indices[cursor + 1], mesh.indices[cursor + 2])
        val a = point(indices[0])
        val b = point(indices[1])
        val c = point(indices[2])
        Triangle(
            indices,
            normalized(cross(subtract(b, a), subtract(c, a))),
            floatArrayOf((a[0] + b[0] + c[0]) / 3f, (a[1] + b[1] + c[1]) / 3f, (a[2] + b[2] + c[2]) / 3f)
        )
    }

    private val neighbors: Array<IntArray> = buildNeighbors()

    fun pick(rayOrigin: FloatArray, rayDirection: FloatArray): CadViewportFaceSelection? {
        val direction = normalized(rayDirection)
        var bestOrdinal = -1
        var bestDistance = Float.POSITIVE_INFINITY
        var bestPoint: FloatArray? = null
        triangles.forEachIndexed { ordinal, triangle ->
            val distance = intersectTriangle(rayOrigin, direction, triangle.indices) ?: return@forEachIndexed
            if (distance > 0f && distance < bestDistance) {
                bestDistance = distance
                bestOrdinal = ordinal
                bestPoint = floatArrayOf(
                    rayOrigin[0] + direction[0] * distance,
                    rayOrigin[1] + direction[1] * distance,
                    rayOrigin[2] + direction[2] * distance
                )
            }
        }
        if (bestOrdinal < 0) return null
        val cluster = clusterSurface(bestOrdinal)
        val seedNormal = triangles[bestOrdinal].normal
        val hitPoint = bestPoint ?: return null
        val planar = isPlanar(cluster, hitPoint, seedNormal)
        val faceNormal = if (planar) averageNormal(cluster, seedNormal) else seedNormal.copyOf()
        val hash = cluster.fold(17) { acc, value -> acc * 31 + value }
        return CadViewportFaceSelection(
            id = "MeshFace-${bestOrdinal}-${hash.toUInt().toString(16)}",
            triangleOrdinals = cluster,
            point = hitPoint,
            normal = faceNormal,
            planar = planar
        )
    }

    internal fun clusterFromSeed(seedOrdinal: Int): IntArray = clusterSurface(seedOrdinal)

    private fun buildNeighbors(): Array<IntArray> {
        val edgeOwners = HashMap<Edge, MutableList<Int>>(triangles.size * 2)
        triangles.forEachIndexed { ordinal, triangle ->
            val i = triangle.indices
            arrayOf(Edge.of(i[0], i[1]), Edge.of(i[1], i[2]), Edge.of(i[2], i[0])).forEach { edge ->
                edgeOwners.getOrPut(edge) { ArrayList(2) }.add(ordinal)
            }
        }
        val mutable = Array(triangles.size) { LinkedHashSet<Int>() }
        edgeOwners.values.forEach { owners ->
            if (owners.size < 2) return@forEach
            owners.forEach { a -> owners.forEach { b -> if (a != b) mutable[a].add(b) } }
        }
        return Array(mutable.size) { mutable[it].toIntArray() }
    }

    private fun clusterSurface(seedOrdinal: Int): IntArray {
        require(seedOrdinal in triangles.indices)
        val visited = BooleanArray(triangles.size)
        val queue = ArrayDeque<Int>()
        val result = ArrayList<Int>()
        visited[seedOrdinal] = true
        queue.add(seedOrdinal)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result += current
            val currentNormal = triangles[current].normal
            neighbors[current].forEach { candidate ->
                if (visited[candidate]) return@forEach
                val alignment = dot(currentNormal, triangles[candidate].normal)
                if (alignment >= SURFACE_CONTINUITY_DOT) {
                    visited[candidate] = true
                    queue.add(candidate)
                }
            }
        }
        return result.sorted().toIntArray()
    }

    private fun isPlanar(cluster: IntArray, hitPoint: FloatArray, seedNormal: FloatArray): Boolean {
        val tolerance = mesh.maxDimension * PLANAR_DISTANCE_RATIO + 1e-5f
        return cluster.all { ordinal ->
            val triangle = triangles[ordinal]
            dot(seedNormal, triangle.normal) >= PLANAR_NORMAL_DOT &&
                abs(dot(seedNormal, subtract(triangle.centroid, hitPoint))) <= tolerance
        }
    }

    private fun averageNormal(cluster: IntArray, fallback: FloatArray): FloatArray {
        val sum = floatArrayOf(0f, 0f, 0f)
        cluster.forEach { ordinal ->
            val normal = triangles[ordinal].normal
            sum[0] += normal[0]
            sum[1] += normal[1]
            sum[2] += normal[2]
        }
        return if (length(sum) <= EPSILON) fallback.copyOf() else normalized(sum)
    }

    private fun intersectTriangle(origin: FloatArray, direction: FloatArray, indices: IntArray): Float? {
        val a = point(indices[0])
        val b = point(indices[1])
        val c = point(indices[2])
        val edge1 = subtract(b, a)
        val edge2 = subtract(c, a)
        val p = cross(direction, edge2)
        val determinant = dot(edge1, p)
        if (abs(determinant) < EPSILON) return null
        val inverse = 1f / determinant
        val t = subtract(origin, a)
        val u = dot(t, p) * inverse
        if (u < -BARYCENTRIC_EPSILON || u > 1f + BARYCENTRIC_EPSILON) return null
        val q = cross(t, edge1)
        val v = dot(direction, q) * inverse
        if (v < -BARYCENTRIC_EPSILON || u + v > 1f + BARYCENTRIC_EPSILON) return null
        val distance = dot(edge2, q) * inverse
        return distance.takeIf { it > EPSILON }
    }

    private fun point(index: Int): FloatArray {
        val offset = index * 6
        return floatArrayOf(mesh.vertices[offset], mesh.vertices[offset + 1], mesh.vertices[offset + 2])
    }

    companion object {
        private const val EPSILON = 1e-7f
        private const val BARYCENTRIC_EPSILON = 1e-5f
        private const val SURFACE_CONTINUITY_DOT = 0.92f
        private const val PLANAR_NORMAL_DOT = 0.9992f
        private const val PLANAR_DISTANCE_RATIO = 2e-4f

        internal fun normalized(value: FloatArray): FloatArray {
            val magnitude = length(value).coerceAtLeast(EPSILON)
            return floatArrayOf(value[0] / magnitude, value[1] / magnitude, value[2] / magnitude)
        }

        internal fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        )

        internal fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        internal fun subtract(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
        internal fun length(value: FloatArray) = sqrt(dot(value, value))
    }
}
