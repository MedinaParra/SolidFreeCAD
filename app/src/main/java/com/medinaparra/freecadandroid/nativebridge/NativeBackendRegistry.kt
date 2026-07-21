package com.medinaparra.freecadandroid.nativebridge

/**
 * Single authority for loading SolidFreeCAD native backends.
 *
 * Keeping native library loading here prevents activities and feature bridges from
 * reporting conflicting backend states or attempting obsolete library names.
 */
object NativeBackendRegistry {
    const val CORE_LIBRARY = "freecad_android_core"
    const val FCSTD_LIBRARY = "freecad_files_core"

    private data class LoadState(
        val loaded: Boolean,
        val error: String?
    )

    private fun load(name: String): LoadState = runCatching {
        System.loadLibrary(name)
        LoadState(loaded = true, error = null)
    }.getOrElse { failure ->
        LoadState(
            loaded = false,
            error = failure.message ?: failure::class.java.simpleName
        )
    }

    private val coreState: LoadState by lazy { load(CORE_LIBRARY) }
    private val fcStdState: LoadState by lazy { load(FCSTD_LIBRARY) }

    val coreLoaded: Boolean
        get() = coreState.loaded

    val fcStdLoaded: Boolean
        get() = fcStdState.loaded

    val activeBackend: String
        get() = when {
            coreLoaded && fcStdLoaded -> "FreeCAD-Native + FCStd bridge"
            coreLoaded -> "FreeCAD-Native"
            fcStdLoaded -> "FCStd bridge only"
            else -> "No native backend"
        }

    fun diagnosticLines(): List<String> = listOf(
        "${CORE_LIBRARY}: ${status(coreState)}",
        "${FCSTD_LIBRARY}: ${status(fcStdState)}"
    )

    private fun status(state: LoadState): String = if (state.loaded) {
        "loaded"
    } else {
        "unavailable${state.error?.let { ": $it" }.orEmpty()}"
    }
}
