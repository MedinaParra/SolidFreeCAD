package com.example.faceui

import android.app.Activity
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import java.io.File
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class OpenGlProbeActivity : Activity() {
    private fun stageFile(): File = File(filesDir, "cadengine-stage.txt")
    private fun reportFile(): File = File(filesDir, "cadengine-report.txt")

    override fun onCreate(savedInstanceState: Bundle?) {
        stageFile().writeText("G0_opengl_activity_onCreate")
        super.onCreate(savedInstanceState)
        stageFile().writeText("G1_before_glsurface_create")
        val surface = GLSurfaceView(this)
        surface.setEGLContextClientVersion(2)
        surface.setRenderer(object : GLSurfaceView.Renderer {
            private var firstFrame = true

            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                stageFile().writeText("G2_surface_created")
                val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "desconocido"
                val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "desconocido"
                val version = GLES20.glGetString(GLES20.GL_VERSION) ?: "desconocida"
                reportFile().writeText("OpenGL básico OK\nVendor: " + vendor + "\nRenderer: " + renderer + "\nVersion: " + version)
                GLES20.glClearColor(0.08f, 0.12f, 0.18f, 1f)
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                stageFile().writeText("G3_surface_changed_" + width + "x" + height)
                GLES20.glViewport(0, 0, width, height)
            }

            override fun onDrawFrame(gl: GL10?) {
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
                if (firstFrame) {
                    firstFrame = false
                    stageFile().writeText("G4_first_frame_rendered")
                }
            }
        })
        surface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        setContentView(surface)
        stageFile().writeText("G1b_glsurface_attached")
    }

    override fun onResume() {
        super.onResume()
        (window.decorView.findViewById<android.view.View>(android.R.id.content) as? android.view.ViewGroup)
            ?.getChildAt(0)?.let { it as? GLSurfaceView }?.onResume()
    }

    override fun onPause() {
        (window.decorView.findViewById<android.view.View>(android.R.id.content) as? android.view.ViewGroup)
            ?.getChildAt(0)?.let { it as? GLSurfaceView }?.onPause()
        super.onPause()
    }
}
