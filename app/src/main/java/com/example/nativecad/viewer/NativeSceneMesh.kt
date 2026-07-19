package com.example.nativecad.viewer

/**
 * Interleaved CAD mesh used by the OpenGL preview.
 * Vertex layout: x, y, z, nx, ny, nz.
 */
data class NativeSceneMesh(
    val vertices: FloatArray,
    val indices: ShortArray,
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float
) {
    val vertexCount: Int get() = vertices.size / 6
    val triangleCount: Int get() = indices.size / 3

    init {
        require(vertices.size % 6 == 0) { "El buffer de vértices debe tener 6 valores por vértice." }
        require(indices.size % 3 == 0) { "El buffer de índices debe contener triángulos completos." }
    }
}
