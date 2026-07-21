package com.example.faceui

enum class CadWindowClassV30 { COMPACT, MEDIUM, EXPANDED }

data class CadAdaptiveWorkbenchV30(
    val windowClass: CadWindowClassV30,
    val touchTargetDp: Int,
    val treeWidthDp: Int,
    val propertiesWidthDp: Int,
    val allowsTwoPersistentPanels: Boolean,
    val trayMaxHeightDp: Int,
    val selectionLabelSp: Int
) {
    val compact: Boolean get() = windowClass == CadWindowClassV30.COMPACT

    companion object {
        fun resolve(widthDp: Float, heightDp: Float, gloveMode: Boolean): CadAdaptiveWorkbenchV30 {
            require(widthDp > 0f && heightDp > 0f) { "Workbench dimensions must be positive" }
            val windowClass = when {
                widthDp >= 1_100f && heightDp >= 650f -> CadWindowClassV30.EXPANDED
                widthDp >= 760f -> CadWindowClassV30.MEDIUM
                else -> CadWindowClassV30.COMPACT
            }
            val touch = if (gloveMode) 58 else 48
            return when (windowClass) {
                CadWindowClassV30.COMPACT -> CadAdaptiveWorkbenchV30(
                    windowClass, touch, 224, 252, false, if (heightDp < 500f) 154 else 180, 11
                )
                CadWindowClassV30.MEDIUM -> CadAdaptiveWorkbenchV30(
                    windowClass, touch, 238, 276, false, 190, 11
                )
                CadWindowClassV30.EXPANDED -> CadAdaptiveWorkbenchV30(
                    windowClass, touch, 270, 320, true, 220, 12
                )
            }
        }
    }
}
