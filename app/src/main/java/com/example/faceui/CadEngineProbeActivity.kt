package com.example.faceui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

class CadEngineProbeActivity : Activity() {
    private lateinit var reportText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        stageFile().writeText("1_probe_onCreate_enter")
        super.onCreate(savedInstanceState)
        stageFile().writeText("2_probe_after_super")
        setContentView(buildUi())
        stageFile().writeText("3_probe_ui_ready")
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::reportText.isInitialized) refresh()
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
            text = "Proceso CAD aislado activo"
            textSize = 24f
            setTextColor(Color.rgb(20, 35, 50))
            gravity = Gravity.CENTER
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun action(label: String, click: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                textSize = 15f
                setOnClickListener { runCatching(click).onFailure { recordFailure(label, it) } }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(10) })
        }

        action("A · Verificar clase del workbench") {
            stageFile().writeText("A0_before_class_for_name")
            val type = Class.forName("com.example.faceui.SolidFreeCadWorkbenchActivityV27", false, classLoader)
            reportFile().writeText("Clase CAD verificada: " + type.name)
            stageFile().writeText("A1_class_for_name_ok")
            refresh()
        }
        action("B · Probar OpenGL básico") {
            stageFile().writeText("B0_request_basic_opengl")
            startActivity(Intent(this, OpenGlProbeActivity::class.java))
        }
        action("C · Abrir CAD completo") {
            stageFile().writeText("C0_before_start_workbench")
            startActivity(Intent().setClassName(packageName, "com.example.faceui.SolidFreeCadWorkbenchActivityV27"))
            stageFile().writeText("C1_start_workbench_returned")
        }
        action("Actualizar") { refresh() }
        action("Copiar informe") {
            val report = diagnosticReport()
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("SolidFreeCAD CAD probe", report))
            reportText.text = report + "\n\nInforme copiado."
        }

        reportText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(25, 35, 45))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(reportText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) })
        return ScrollView(this).apply { addView(root) }
    }

    private fun stageFile(): File = File(filesDir, "cadengine-stage.txt")
    private fun reportFile(): File = File(filesDir, "cadengine-report.txt")

    private fun recordFailure(source: String, failure: Throwable) {
        val text = source + "\n" + failure.javaClass.name + ": " + (failure.message ?: "sin mensaje") + "\n" + failure.stackTraceToString()
        reportFile().writeText(text)
        stageFile().writeText("FAIL_" + source.substringBefore(' '))
        refresh()
    }

    private fun refresh() {
        reportText.text = diagnosticReport()
    }

    private fun diagnosticReport(): String {
        val stage = runCatching { stageFile().readText() }.getOrDefault("sin etapa")
        val report = runCatching { reportFile().readText() }.getOrDefault("sin informe")
        return buildString {
            appendLine("Proceso: :cadengine")
            appendLine("Etapa: " + stage)
            appendLine("Informe: " + report)
            appendLine("Modelo: " + Build.MODEL)
            appendLine("SDK: " + Build.VERSION.SDK_INT)
            appendLine("ABI: " + Build.SUPPORTED_ABIS.joinToString())
        }.trim()
    }
}
