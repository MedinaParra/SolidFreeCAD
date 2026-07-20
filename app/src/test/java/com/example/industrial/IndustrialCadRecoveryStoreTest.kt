package com.example.industrial

import com.example.features.BasicCadOperation
import com.example.features.BasicCadProgram
import com.example.features.CadSketchPrimitiveKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndustrialCadRecoveryStoreTest {
    @Test
    fun roundTripPreservesPlanesSketchesFeaturesAndParameters() {
        val withPlane = BasicCadProgram().createOffsetPlane(1L, 125.5, "Plano taller")
        val withSketch = withPlane.createSketch(withPlane.activePlaneId, CadSketchPrimitiveKind.RECTANGLE)
        val source = withSketch.append(BasicCadOperation.BOSS_EXTRUDE)
            .updateFeature(2L, mapOf("length" to 80.0, "width" to 45.0, "depth" to 12.5))

        val bytes = IndustrialCadRecoveryStore.encodeForTest(source)
        val restored = IndustrialCadRecoveryStore.decodeForTest(bytes)

        assertEquals(source, restored)
        assertEquals("Plano taller", restored.planeSet.planes.last().label)
        assertEquals(12.5, restored.features.last().parameters["depth"] ?: 0.0, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun checksumRejectsCorruptedSnapshot() {
        val bytes = IndustrialCadRecoveryStore.encodeForTest(BasicCadProgram())
        bytes[bytes.lastIndex / 2] = (bytes[bytes.lastIndex / 2].toInt() xor 0x5A).toByte()
        IndustrialCadRecoveryStore.decodeForTest(bytes)
    }

    @Test
    fun snapshotHasSmallDeterministicPayload() {
        val bytes = IndustrialCadRecoveryStore.encodeForTest(BasicCadProgram())
        assertTrue(bytes.size in 100..32_000)
    }
}
