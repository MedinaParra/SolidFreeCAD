package com.example.industrial

import org.junit.Assert.assertTrue
import org.junit.Test

class IndustrialCadDiagnosticsTest {
    @Test
    fun diagnosticCodesAreStableEnoughForWorkshopReports() {
        val method = IndustrialCadDiagnostics::class.java.getDeclaredMethod(
            "diagnosticCode",
            String::class.java,
            Throwable::class.java
        )
        method.isAccessible = true
        val code = method.invoke(IndustrialCadDiagnostics, "Extrusión", IllegalArgumentException("fallo")) as String
        assertTrue(code.matches(Regex("CAD-[0-9A-F]{6}")))
    }
}
