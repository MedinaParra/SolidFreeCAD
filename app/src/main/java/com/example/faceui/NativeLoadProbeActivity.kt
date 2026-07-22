package com.example.faceui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class NativeLoadProbeActivity : Activity() {
    private lateinit var reportText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        stageFile().writeText("N1_nativeprobe_onCreate_enter")
        super.onCreate(savedInstanceState)
        stageFile().writeText("N2_nativeprobe_after_super")
        setContentView(buildUi())
        stageFile().writeText("N3_nativeprobe_ui_ready")
        refresh()
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(Color.rgb(245, 248, 251))
        }
        root.addView(TextView(this).apply {
            text = "Prueba nativa aislada"
            textSize = 24f
            setTextColor(Color.rgb(20, 35, 50))
            gravity = Gravity.CENTER
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun action(label: String, library: String) {
            root.addView(Button(this).apply {
                text = label
                textSize = 15f
                setOnClickListener { loadLibrary(library) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(10) })
        }

        action("A · Cargar libc++_shared", "c++_shared")
        action("B · Cargar CPython", "python3.14")
        action("C · Cargar OpenCASCADE TKernel", "TKernel")
        action("D · Cargar FreeCAD core", "freecad_android_core")
        root.addView(Button(this).apply {
            text = "Copiar informe"
            textSize = 15f
            setOnClickListener {
                val report = diagnosticReport()
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("SolidFreeCAD native probe", report))
                reportText.text = report + "\n\nInforme copiado."
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(10) })

        reportText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(25, 35, 45))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(reportText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
        return ScrollView(this).apply { addView(root) }
    }

    private fun stageFile(): File = File(filesDir, "nativeprobe-stage.txt")
    private fun reportFile(): File = File(filesDir, "nativeprobe-report.txt")

    private fun loadLibrary(name: String) {
        stageFile().writeText("N_before_load_" + name)
        runCatching { System.loadLibrary(name) }
            .onSuccess {
                stageFile().writeText("N_loaded_" + name)
                reportFile().appendText("OK: " + name + "\n")
            }
            .onFailure { failure ->
                stageFile().writeText("N_failed_" + name)
                reportFile().appendText("FAIL: " + name + "\n" + failure.javaClass.name + ": " + (failure.message ?: "sin mensaje") + "\n")
            }
        refresh()
    }

    private fun refresh() {
        reportText.text = diagnosticReport()
    }

    private fun diagnosticReport(): String {
        val stage = runCatching { stageFile().readText() }.getOrDefault("sin etapa")
        val report = runCatching { reportFile().readText() }.getOrDefault("sin informe")
        return "Proceso: :nativeprobe\nEtapa: " + stage + "\n" + report
    }
}
