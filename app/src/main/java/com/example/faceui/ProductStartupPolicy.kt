package com.example.faceui

object ProductStartupPolicy {
    private val healthyPrefixes = listOf(
        "L4_surface_ready",
        "PRODUCT_READY",
    )

    private val interruptedPrefixes = listOf(
        "P4_",
        "P5_",
        "P6_",
        "P7_",
        "L1_",
        "L2_",
        "L3_",
        "P_FAIL_",
    )

    fun shouldAutoOpen(lastStage: String?, forceSafeMode: Boolean): Boolean {
        if (forceSafeMode) return false
        val stage = lastStage?.trim().orEmpty()
        if (stage.isEmpty() || stage == "sin intento") return true
        if (healthyPrefixes.any(stage::startsWith)) return true
        return interruptedPrefixes.none(stage::startsWith)
    }

    fun isHealthy(lastStage: String?): Boolean {
        val stage = lastStage?.trim().orEmpty()
        return healthyPrefixes.any(stage::startsWith)
    }
}
