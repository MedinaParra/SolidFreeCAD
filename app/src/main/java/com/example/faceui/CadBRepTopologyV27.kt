package com.example.faceui

import com.example.nativecad.viewer.NativeSceneMesh
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

enum class CadViewportSelectionModeV27(val label: String) {
    AUTO("Auto"), FACE("Cara"), EDGE("Arista"), VERTEX("Vértice"), LOOP("Bucle")
}

enum class CadLoopRoleV29(val label: String) {
    OUTER("Región exterior"),
    INNER("Región interior"),
    OPEN("Cadena abierta")
}

sealed interface CadViewportSelectionV27 {
    val id: String
    val point: FloatArray
}

data class CadViewportFaceSelectionV27(
    override val id: String,
    val triangleOrdinals: IntArray,
    override val point: FloatArray,
    val normal: FloatArray,
    val planar: Boolean,
    val approximateArea: Float = 0f,
    val boundaryLoopCount: Int = 0,
    val outerLoopCount: Int = 0,
    val innerLoopCount: Int = 0
) : CadViewportSelectionV27

data class CadViewportEdgeSelectionV27(
    override val id: String,
    val edgeId: Int,
    val vertexA: Int,
    val vertexB: Int,
    val start: FloatArray,
    val end: FloatArray,
    override val point: FloatArray,
    val length: Float,
    val boundary: Boolean,
    val sharp: Boolean
) : CadViewportSelectionV27

data class CadViewportVertexSelectionV27(
    override val id: String,
    val vertexIndex: Int,
    override val point: FloatArray,
    val incidentFeatureEdges: Int
) : CadViewportSelectionV27

data class CadViewportLoopSelectionV27(
    override val id: String,
    val edgeIds: IntArray,
    val orderedPoints: FloatArray,
    override val point: FloatArray,
    val normal: FloatArray,
    val closed: Boolean,
    val perimeter: Float,
    val role: CadLoopRoleV29 = if (closed) CadLoopRoleV29.OUTER else CadLoopRoleV29.OPEN,
    val signedArea: Float = 0f,
    val nestingDepth: Int = 0
) : CadViewportSelectionV27

/**
 * Topology inferred from the committed display mesh.
 *
 * This class deliberately does not claim persistent OCCT topology names. It is rebuilt only
 * after a committed BRep mesh arrives. V2.9 adds deterministic outer/inner loop classification
 * in the local plane of each planar face.
 */
class CadBRepTopologyV27(private val mesh: NativeSceneMesh) {
    private data class Key(val a: Int, val b: Int) {
        companion object {
            fun of(a: Int, b: Int) = if (a < b) Key(a, b) else Key(b, a)
        }
    }

    private data class Tri(val i: IntArray, val n: FloatArray, val c: FloatArray, val area: Float)
    private data class Edge(val id: Int, val key: Key, val owners: IntArray, val boundary: Boolean, val sharp: Boolean)
    private data class Hit(val tri: Int, val t: Float, val p: FloatArray)
    private data class RayEdge(val distance: Float, val rayT: Float, val point: FloatArray)
    private data class Loop(
        val edges: IntArray,
        val vertices: IntArray,
        val closed: Boolean,
        val perimeter: Float,
        val signedArea: Float = 0f,
        val nestingDepth: Int = 0,
        val role: CadLoopRoleV29 = if (closed) CadLoopRoleV29.OUTER else CadLoopRoleV29.OPEN,
        val centroid: FloatArray = floatArrayOf(0f, 0f, 0f)
    )

    private data class Point2(val x: Float, val y: Float)

    private val tris = List(mesh.triangleCount) { ordinal ->
        val k = ordinal * 3
        val ids = intArrayOf(mesh.indices[k], mesh.indices[k + 1], mesh.indices[k + 2])
        val a = p(ids[0])
        val b = p(ids[1])
        val c = p(ids[2])
        val cr = cross(sub(b, a), sub(c, a))
        Tri(
            ids,
            norm(cr),
            floatArrayOf(
                (a[0] + b[0] + c[0]) / 3f,
                (a[1] + b[1] + c[1]) / 3f,
                (a[2] + b[2] + c[2]) / 3f
            ),
            len(cr) / 2f
        )
    }
    private val owners = buildOwners()
    private val neighbours = buildNeighbours()
    private val surfaceByTri: IntArray
    private val surfaces: List<IntArray>
    private val edges: List<Edge>
    private val featureEdgeIds: IntArray
    private val featureByVertex: Map<Int, IntArray>

    init {
        val built = buildSurfaces()
        surfaceByTri = built.first
        surfaces = built.second
        edges = owners.entries
            .sortedWith(compareBy<Map.Entry<Key, IntArray>> { it.key.a }.thenBy { it.key.b })
            .mapIndexed { id, entry ->
                val boundary = entry.value.size == 1
                val crossSurface = entry.value.map { surfaceByTri[it] }.distinct().size > 1
                val alignment = if (entry.value.size < 2) 1f else dot(tris[entry.value[0]].n, tris[entry.value[1]].n)
                Edge(id, entry.key, entry.value, boundary, crossSurface || alignment < SHARP_DOT)
            }
        featureEdgeIds = edges.filter { it.boundary || it.sharp }.map { it.id }.toIntArray()
        val map = hashMapOf<Int, MutableList<Int>>()
        featureEdgeIds.forEach { id ->
            val edge = edges[id]
            map.getOrPut(edge.key.a) { arrayListOf() }.add(id)
            map.getOrPut(edge.key.b) { arrayListOf() }.add(id)
        }
        featureByVertex = map.mapValues { it.value.sorted().toIntArray() }
    }

    fun pick(rayOrigin: FloatArray, rayDirection: FloatArray): CadViewportFaceSelectionV27? =
        pickFace(rayOrigin, rayDirection)

    fun pick(
        origin: FloatArray,
        direction: FloatArray,
        mode: CadViewportSelectionModeV27,
        worldTolerance: Float
    ): CadViewportSelectionV27? {
        val tolerance = max(worldTolerance, mesh.maxDimension * 0.0002f)
        return when (mode) {
            CadViewportSelectionModeV27.FACE -> pickFace(origin, direction)
            CadViewportSelectionModeV27.EDGE -> pickEdge(origin, direction, tolerance)
            CadViewportSelectionModeV27.VERTEX -> pickVertex(origin, direction, tolerance)
            CadViewportSelectionModeV27.LOOP -> pickLoop(origin, direction)
            CadViewportSelectionModeV27.AUTO ->
                pickVertex(origin, direction, tolerance * .72f)
                    ?: pickEdge(origin, direction, tolerance)
                    ?: pickFace(origin, direction)
        }
    }

    fun pickFace(origin: FloatArray, direction: FloatArray): CadViewportFaceSelectionV27? {
        val hit = hit(origin, norm(direction)) ?: return null
        val cluster = surfaces[surfaceByTri[hit.tri]]
        val seed = tris[hit.tri].n
        val planar = isPlanar(cluster, hit.p, seed)
        val normal = if (planar) average(cluster, seed) else seed.copyOf()
        val boundaryLoops = if (planar) classifyLoops(cluster, normal) else loops(cluster)
        val hash = cluster.fold(17) { accumulator, value -> accumulator * 31 + value }
        return CadViewportFaceSelectionV27(
            id = "MeshFace-${hit.tri}-${hash.toUInt().toString(16)}",
            triangleOrdinals = cluster.copyOf(),
            point = hit.p,
            normal = normal,
            planar = planar,
            approximateArea = cluster.sumOf { tris[it].area.toDouble() }.toFloat(),
            boundaryLoopCount = boundaryLoops.size,
            outerLoopCount = boundaryLoops.count { it.role == CadLoopRoleV29.OUTER },
            innerLoopCount = boundaryLoops.count { it.role == CadLoopRoleV29.INNER }
        )
    }

    fun pickEdge(origin: FloatArray, direction: FloatArray, tolerance: Float): CadViewportEdgeSelectionV27? {
        val d = norm(direction)
        val front = hit(origin, d)?.t ?: Float.POSITIVE_INFINITY
        var best: Pair<Edge, RayEdge>? = null
        featureEdgeIds.forEach { id ->
            val edge = edges[id]
            val a = p(edge.key.a)
            val b = p(edge.key.b)
            val ray = raySegment(origin, d, a, b)
            val previous = best?.second
            val improves = previous == null || ray.distance < previous.distance - EPS ||
                (abs(ray.distance - previous.distance) <= EPS && ray.rayT < previous.rayT)
            if (ray.rayT > EPS && ray.distance <= tolerance && ray.rayT <= front + tolerance * 3.5f && improves) {
                best = edge to ray
            }
        }
        val pair = best ?: return null
        val edge = pair.first
        val a = p(edge.key.a)
        val b = p(edge.key.b)
        return CadViewportEdgeSelectionV27(
            "MeshEdge-${edge.id}-${edge.key.a}-${edge.key.b}",
            edge.id,
            edge.key.a,
            edge.key.b,
            a,
            b,
            pair.second.point,
            len(sub(b, a)),
            edge.boundary,
            edge.sharp
        )
    }

    fun pickVertex(origin: FloatArray, direction: FloatArray, tolerance: Float): CadViewportVertexSelectionV27? {
        val d = norm(direction)
        val front = hit(origin, d)?.t ?: Float.POSITIVE_INFINITY
        var best = -1
        var bestDistance = Float.POSITIVE_INFINITY
        var bestRayT = Float.POSITIVE_INFINITY
        featureByVertex.keys.forEach { vertex ->
            val point = p(vertex)
            val rayT = dot(sub(point, origin), d)
            if (rayT <= EPS || rayT > front + tolerance * 3.5f) return@forEach
            val distance = len(sub(point, add(origin, mul(d, rayT))))
            val improves = distance < bestDistance - EPS ||
                (abs(distance - bestDistance) <= EPS && rayT < bestRayT)
            if (distance <= tolerance && improves) {
                best = vertex
                bestDistance = distance
                bestRayT = rayT
            }
        }
        return if (best < 0) null else CadViewportVertexSelectionV27(
            "MeshVertex-$best",
            best,
            p(best),
            featureByVertex[best]?.size ?: 0
        )
    }

    fun pickLoop(origin: FloatArray, direction: FloatArray): CadViewportLoopSelectionV27? {
        val face = pickFace(origin, direction) ?: return null
        val candidates = if (face.planar) classifyLoops(face.triangleOrdinals, face.normal) else loops(face.triangleOrdinals)
        if (candidates.isEmpty()) return null
        val loop = candidates.minByOrNull { candidate ->
            candidate.vertices.minOfOrNull { vertex -> len(sub(p(vertex), face.point)).toDouble() }
                ?: Double.MAX_VALUE
        } ?: return null
        return loop.toSelection(face.normal)
    }

    internal fun clusterFromSeed(seedOrdinal: Int): IntArray =
        surfaces[surfaceByTri[seedOrdinal]].copyOf()

    internal fun classifiedLoopsFromSeed(seedOrdinal: Int): List<CadViewportLoopSelectionV27> {
        val cluster = surfaces[surfaceByTri[seedOrdinal]]
        val normal = average(cluster, tris[seedOrdinal].n)
        return classifyLoops(cluster, normal).map { it.toSelection(normal) }
    }

    private fun Loop.toSelection(normal: FloatArray): CadViewportLoopSelectionV27 {
        val values = FloatArray(vertices.size * 3)
        vertices.forEachIndexed { index, vertex ->
            val point = p(vertex)
            values[index * 3] = point[0]
            values[index * 3 + 1] = point[1]
            values[index * 3 + 2] = point[2]
        }
        return CadViewportLoopSelectionV27(
            id = "MeshLoop-${edges.joinToString("-")}",
            edgeIds = edges.copyOf(),
            orderedPoints = values,
            point = centroid.copyOf(),
            normal = normal.copyOf(),
            closed = closed,
            perimeter = perimeter,
            role = role,
            signedArea = signedArea,
            nestingDepth = nestingDepth
        )
    }

    private fun buildOwners(): Map<Key, IntArray> {
        val map = hashMapOf<Key, MutableList<Int>>()
        tris.forEachIndexed { ordinal, triangle ->
            val indices = triangle.i
            arrayOf(
                Key.of(indices[0], indices[1]),
                Key.of(indices[1], indices[2]),
                Key.of(indices[2], indices[0])
            ).forEach { key -> map.getOrPut(key) { arrayListOf() }.add(ordinal) }
        }
        return map.mapValues { it.value.toIntArray() }
    }

    private fun buildNeighbours(): Array<IntArray> {
        val neighbours = Array(tris.size) { linkedSetOf<Int>() }
        owners.values.forEach { ordinals ->
            ordinals.forEach { a -> ordinals.forEach { b -> if (a != b) neighbours[a].add(b) } }
        }
        return Array(neighbours.size) { neighbours[it].toIntArray() }
    }

    private fun buildSurfaces(): Pair<IntArray, List<IntArray>> {
        val ids = IntArray(tris.size) { -1 }
        val groups = arrayListOf<IntArray>()
        tris.indices.forEach { seed ->
            if (ids[seed] < 0) {
                val id = groups.size
                val queue = ArrayDeque<Int>()
                val group = arrayListOf<Int>()
                ids[seed] = id
                queue.add(seed)
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    group.add(current)
                    neighbours[current].forEach { next ->
                        if (ids[next] < 0 && dot(tris[current].n, tris[next].n) >= SURFACE_DOT) {
                            ids[next] = id
                            queue.add(next)
                        }
                    }
                }
                groups.add(group.sorted().toIntArray())
            }
        }
        return ids to groups
    }

    private fun loops(cluster: IntArray): List<Loop> {
        val set = cluster.toHashSet()
        val remaining = edges
            .filter { edge -> edge.owners.count { it in set } == 1 }
            .map { it.id }
            .toMutableSet()
        if (remaining.isEmpty()) return emptyList()

        val adjacency = hashMapOf<Int, MutableList<Int>>()
        remaining.forEach { id ->
            val edge = edges[id]
            adjacency.getOrPut(edge.key.a) { arrayListOf() }.add(id)
            adjacency.getOrPut(edge.key.b) { arrayListOf() }.add(id)
        }

        val output = arrayListOf<Loop>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.minOrNull()!!
            val component = linkedSetOf<Int>()
            val queue = ArrayDeque<Int>()
            component.add(seed)
            queue.add(seed)
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                val edge = edges[id]
                listOf(edge.key.a, edge.key.b).forEach { vertex ->
                    adjacency[vertex].orEmpty().forEach { candidate ->
                        if (candidate in remaining && component.add(candidate)) queue.add(candidate)
                    }
                }
            }

            val componentVertices = component
                .flatMap { id -> listOf(edges[id].key.a, edges[id].key.b) }
                .distinct()
            val start = componentVertices
                .filter { vertex -> adjacency[vertex].orEmpty().count { it in component } == 1 }
                .minOrNull()
                ?: componentVertices.minOrNull()!!

            val unused = component.toMutableSet()
            val orderedEdges = arrayListOf<Int>()
            val orderedVertices = arrayListOf(start)
            var current = start
            while (unused.isNotEmpty()) {
                val next = adjacency[current].orEmpty().filter { it in unused }.minOrNull() ?: break
                unused.remove(next)
                orderedEdges.add(next)
                val edge = edges[next]
                current = if (edge.key.a == current) edge.key.b else edge.key.a
                orderedVertices.add(current)
                if (current == start && unused.isEmpty()) break
            }
            remaining.removeAll(component)

            if (orderedEdges.isNotEmpty()) {
                val perimeter = orderedEdges.sumOf { id ->
                    val edge = edges[id]
                    len(sub(p(edge.key.a), p(edge.key.b))).toDouble()
                }.toFloat()
                val closed = orderedVertices.size > 2 && orderedVertices.first() == orderedVertices.last()
                output.add(
                    Loop(
                        orderedEdges.toIntArray(),
                        orderedVertices.toIntArray(),
                        closed,
                        perimeter,
                        centroid = averagePoints(orderedVertices)
                    )
                )
            }
        }
        return output.sortedByDescending { it.perimeter }
    }

    private fun classifyLoops(cluster: IntArray, normal: FloatArray): List<Loop> {
        val raw = loops(cluster)
        if (raw.isEmpty()) return emptyList()
        val basis = planeBasis(normal)
        val polygons = raw.map { loop ->
            val points = loop.vertices
                .dropLast(if (loop.closed && loop.vertices.size > 1) 1 else 0)
                .map { vertex -> project(p(vertex), basis.first, basis.second) }
            points
        }
        val signedAreas = polygons.map(::signedArea)
        val samples = polygons.mapIndexed { index, polygon -> interiorSample(polygon, signedAreas[index]) }
        val areaTolerance = max(mesh.maxDimension * mesh.maxDimension * 1e-9f, 1e-7f)

        return raw.mapIndexed { index, loop ->
            if (!loop.closed || polygons[index].size < 3 || abs(signedAreas[index]) <= areaTolerance) {
                loop.copy(role = CadLoopRoleV29.OPEN, signedArea = signedAreas[index], nestingDepth = 0)
            } else {
                val ownArea = abs(signedAreas[index])
                val depth = polygons.indices.count { containerIndex ->
                    containerIndex != index &&
                        abs(signedAreas[containerIndex]) > ownArea + areaTolerance &&
                        pointInPolygon(samples[index], polygons[containerIndex])
                }
                loop.copy(
                    role = if (depth % 2 == 0) CadLoopRoleV29.OUTER else CadLoopRoleV29.INNER,
                    signedArea = signedAreas[index],
                    nestingDepth = depth,
                    centroid = unproject(samples[index], basis.first, basis.second, loop.centroid, normal)
                )
            }
        }.sortedWith(compareBy<Loop> { it.nestingDepth }.thenByDescending { abs(it.signedArea) })
    }

    private fun planeBasis(normal: FloatArray): Pair<FloatArray, FloatArray> {
        val n = norm(normal)
        val helper = if (abs(n[2]) < .85f) floatArrayOf(0f, 0f, 1f) else floatArrayOf(0f, 1f, 0f)
        val u = norm(cross(helper, n))
        val v = norm(cross(n, u))
        return u to v
    }

    private fun project(point: FloatArray, u: FloatArray, v: FloatArray): Point2 =
        Point2(dot(point, u), dot(point, v))

    private fun unproject(
        point: Point2,
        u: FloatArray,
        v: FloatArray,
        fallback: FloatArray,
        normal: FloatArray
    ): FloatArray {
        val planeOffset = dot(fallback, norm(normal))
        return add(add(mul(u, point.x), mul(v, point.y)), mul(norm(normal), planeOffset))
    }

    private fun signedArea(points: List<Point2>): Float {
        if (points.size < 3) return 0f
        var twiceArea = 0f
        points.indices.forEach { index ->
            val a = points[index]
            val b = points[(index + 1) % points.size]
            twiceArea += a.x * b.y - b.x * a.y
        }
        return twiceArea * .5f
    }

    private fun interiorSample(points: List<Point2>, area: Float): Point2 {
        if (points.isEmpty()) return Point2(0f, 0f)
        if (points.size < 3 || abs(area) <= EPS) {
            return Point2(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
        }
        var cx = 0f
        var cy = 0f
        var factorSum = 0f
        points.indices.forEach { index ->
            val a = points[index]
            val b = points[(index + 1) % points.size]
            val factor = a.x * b.y - b.x * a.y
            factorSum += factor
            cx += (a.x + b.x) * factor
            cy += (a.y + b.y) * factor
        }
        val centroid = if (abs(factorSum) > EPS) {
            Point2(cx / (3f * factorSum), cy / (3f * factorSum))
        } else {
            Point2(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
        }
        if (pointInPolygon(centroid, points)) return centroid

        val mean = Point2(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
        val first = points.first()
        return Point2(first.x * .85f + mean.x * .15f, first.y * .85f + mean.y * .15f)
    }

    private fun pointInPolygon(point: Point2, polygon: List<Point2>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var previous = polygon.last()
        polygon.forEach { current ->
            val crosses = (current.y > point.y) != (previous.y > point.y)
            if (crosses) {
                val denominator = previous.y - current.y
                val xAtY = if (abs(denominator) <= EPS) current.x else
                    (previous.x - current.x) * (point.y - current.y) / denominator + current.x
                if (point.x < xAtY) inside = !inside
            }
            previous = current
        }
        return inside
    }

    private fun averagePoints(vertices: List<Int>): FloatArray {
        if (vertices.isEmpty()) return floatArrayOf(0f, 0f, 0f)
        val distinct = if (vertices.size > 1 && vertices.first() == vertices.last()) vertices.dropLast(1) else vertices
        if (distinct.isEmpty()) return floatArrayOf(0f, 0f, 0f)
        val result = floatArrayOf(0f, 0f, 0f)
        distinct.forEach { vertex ->
            val point = p(vertex)
            result[0] += point[0]
            result[1] += point[1]
            result[2] += point[2]
        }
        val count = distinct.size.toFloat()
        return floatArrayOf(result[0] / count, result[1] / count, result[2] / count)
    }

    private fun hit(origin: FloatArray, direction: FloatArray): Hit? {
        var best = -1
        var bestDistance = Float.POSITIVE_INFINITY
        tris.forEachIndexed { ordinal, triangle ->
            val distance = intersect(origin, direction, triangle.i)
            if (distance != null && distance < bestDistance) {
                best = ordinal
                bestDistance = distance
            }
        }
        return if (best < 0) null else Hit(best, bestDistance, add(origin, mul(direction, bestDistance)))
    }

    private fun intersect(origin: FloatArray, direction: FloatArray, indices: IntArray): Float? {
        val a = p(indices[0])
        val b = p(indices[1])
        val c = p(indices[2])
        val edge1 = sub(b, a)
        val edge2 = sub(c, a)
        val h = cross(direction, edge2)
        val determinant = dot(edge1, h)
        if (abs(determinant) < EPS) return null
        val inverse = 1f / determinant
        val s = sub(origin, a)
        val u = dot(s, h) * inverse
        if (u < 0f || u > 1f) return null
        val q = cross(s, edge1)
        val v = dot(direction, q) * inverse
        if (v < 0f || u + v > 1f) return null
        return (dot(edge2, q) * inverse).takeIf { it > EPS }
    }

    private fun raySegment(
        origin: FloatArray,
        direction: FloatArray,
        start: FloatArray,
        end: FloatArray
    ): RayEdge {
        val segment = sub(end, start)
        val offset = sub(origin, start)
        val aa = dot(direction, direction)
        val bb = dot(direction, segment)
        val cc = dot(segment, segment)
        val dd = dot(direction, offset)
        val ee = dot(segment, offset)
        val denominator = aa * cc - bb * bb
        var rayT: Float
        var segmentT: Float
        if (cc < EPS) {
            rayT = max(0f, -dd / aa)
            segmentT = 0f
        } else if (abs(denominator) < EPS) {
            segmentT = (ee / cc).coerceIn(0f, 1f)
            rayT = max(0f, dot(sub(add(start, mul(segment, segmentT)), origin), direction) / aa)
        } else {
            rayT = (bb * ee - cc * dd) / denominator
            segmentT = (aa * ee - bb * dd) / denominator
            if (rayT < 0f) {
                rayT = 0f
                segmentT = (ee / cc).coerceIn(0f, 1f)
            } else if (segmentT !in 0f..1f) {
                segmentT = segmentT.coerceIn(0f, 1f)
                rayT = max(0f, dot(sub(add(start, mul(segment, segmentT)), origin), direction) / aa)
            }
        }
        val segmentPoint = add(start, mul(segment, segmentT))
        return RayEdge(
            len(sub(add(origin, mul(direction, rayT)), segmentPoint)),
            rayT,
            segmentPoint
        )
    }

    private fun isPlanar(cluster: IntArray, point: FloatArray, normal: FloatArray): Boolean {
        val tolerance = mesh.maxDimension * .0002f + 1e-5f
        return cluster.all { ordinal ->
            dot(normal, tris[ordinal].n) >= .9992f &&
                abs(dot(normal, sub(tris[ordinal].c, point))) <= tolerance
        }
    }

    private fun average(cluster: IntArray, fallback: FloatArray): FloatArray {
        val sum = floatArrayOf(0f, 0f, 0f)
        cluster.forEach { ordinal ->
            val normal = tris[ordinal].n
            sum[0] += normal[0]
            sum[1] += normal[1]
            sum[2] += normal[2]
        }
        return if (len(sum) < EPS) fallback.copyOf() else norm(sum)
    }

    private fun p(index: Int): FloatArray {
        val offset = index * 6
        return floatArrayOf(mesh.vertices[offset], mesh.vertices[offset + 1], mesh.vertices[offset + 2])
    }

    companion object {
        private const val EPS = 1e-7f
        private const val SURFACE_DOT = .92f
        private const val SHARP_DOT = .985f

        internal fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        internal fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        )
        internal fun sub(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
        internal fun add(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] + b[0], a[1] + b[1], a[2] + b[2])
        internal fun mul(a: FloatArray, scalar: Float) = floatArrayOf(a[0] * scalar, a[1] * scalar, a[2] * scalar)
        internal fun len(a: FloatArray) = sqrt(dot(a, a))
        internal fun norm(a: FloatArray): FloatArray {
            val length = max(EPS, len(a))
            return floatArrayOf(a[0] / length, a[1] / length, a[2] / length)
        }
    }
}
