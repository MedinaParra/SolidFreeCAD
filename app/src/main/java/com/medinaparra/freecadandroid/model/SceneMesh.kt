package com.medinaparra.freecadandroid.model

/** Mesh contract used by the validated FreeCAD-Native 0.8 JNI runtime. */
data class SceneMesh(
    val vertices: FloatArray,
    val indices: IntArray,
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
        require(vertices.size % 6 == 0) { "Mesh vertices must use x,y,z,nx,ny,nz layout" }
        require(indices.size % 3 == 0) { "Mesh indices must contain complete triangles" }
        require(indices.all { it >= 0 && it < vertexCount }) { "Mesh contains an invalid vertex index" }
    }
}
