package com.example.faceui

import com.example.nativecad.viewer.NativeSceneMesh
import kotlin.math.max

/**
 * Lightweight visual deformation used only while the user drags a 3D manipulator.
 *
 * OpenCASCADE remains the source of truth. During the gesture we keep the existing
 * topology and move its vertices, which makes the model follow the finger without
 * rebuilding the BRep on every motion event. Releasing the manipulator triggers the
 * definitive native recompute.
 */
object LiveParametricPreview {
    fun deformCylinder(
        source: NativeSceneMesh,
        targetLengthMm: Float,
        targetDiameterMm: Float
    ): NativeSceneMesh {
        require(targetLengthMm.isFinite() && targetLengthMm > 0f) { "Longitud de previsualización no válida" }
        require(targetDiameterMm.isFinite() && targetDiameterMm > 0f) { "Diámetro de previsualización no válido" }

        val sourceLength = (source.maxZ - source.minZ).coerceAtLeast(MIN_SPAN)
        val sourceDiameter = max(source.maxX - source.minX, source.maxY - source.minY).coerceAtLeast(MIN_SPAN)
        val radialScale = targetDiameterMm / sourceDiameter
        val axialScale = targetLengthMm / sourceLength
        val centerX = (source.minX + source.maxX) * 0.5f
        val centerY = (source.minY + source.maxY) * 0.5f
        val anchorZ = source.minZ

        val vertices = source.vertices.copyOf()
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        var offset = 0
        while (offset + 5 < vertices.size) {
            val x = centerX + (vertices[offset] - centerX) * radialScale
            val y = centerY + (vertices[offset + 1] - centerY) * radialScale
            val z = anchorZ + (vertices[offset + 2] - anchorZ) * axialScale
            vertices[offset] = x
            vertices[offset + 1] = y
            vertices[offset + 2] = z

            minX = minOf(minX, x)
            minY = minOf(minY, y)
            minZ = minOf(minZ, z)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
            maxZ = maxOf(maxZ, z)
            offset += 6
        }

        return NativeSceneMesh(
            vertices = vertices,
            indices = source.indices,
            minX = minX,
            minY = minY,
            minZ = minZ,
            maxX = maxX,
            maxY = maxY,
            maxZ = maxZ
        )
    }

    private const val MIN_SPAN = 1e-6f
}
