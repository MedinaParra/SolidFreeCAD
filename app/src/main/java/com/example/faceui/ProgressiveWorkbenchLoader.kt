package com.example.faceui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.util.WeakHashMap

object ProgressiveWorkbenchLoader {
    private val surfaces = WeakHashMap<ComponentActivity, FaceDrivenCadSurfaceViewV27>()

    @JvmStatic
    fun install(activity: ComponentActivity) {
        stage(activity, "L1_loader_install_enter")
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                surfaces[activity]?.onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                surfaces[activity]?.onPause()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                surfaces.remove(activity)
                activity.lifecycle.removeObserver(this)
            }
        })
        stage(activity, "L2_before_setContent")
        activity.setContent {
            MyApplicationTheme {
                V27Workbench(activity.intent?.data) { surface ->
                    surfaces[activity] = surface
                    stage(activity, "L4_surface_ready")
                }
            }
        }
        stage(activity, "L3_setContent_returned")
    }

    private fun stage(activity: ComponentActivity, value: String) {
        runCatching { File(activity.filesDir, "progressive-stage.txt").writeText(value) }
    }
}
