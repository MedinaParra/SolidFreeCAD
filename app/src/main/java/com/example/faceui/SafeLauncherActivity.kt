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

class SafeLauncherActivity : Activity() {
    private lateinit var stageText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        refreshStage()
    }

    override fun onResume() {
        super.onResume()
        if (::stageText.isInitialized) refreshStage()
    }

    private fun buildUi(): ScrollView {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            setBackgroundColor(Color.rgb(238, 243, 247))
        }
        root.addView(TextView(this).apply {
            text = "SolidFreeCAD 3.1.3"
            textSize = 26f
            setTextColor(Color.rgb(25, 35, 45))
            gravity = Gravity.CENTER
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = "Diagnóstico escalonado · Samsung A26 5G"
            textSize = 16f
            setTextColor(Color.rgb(70, 85, 100))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        })

        fun action(label: String, click: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                textSize = 15f
                setOnClickListener { runCatching(click).onFailure { writeFailure("launcher", it) } }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(10) })
        }

        action("1 · Interfaz Compose sin OpenGL/JNI") {
            startActivity(Intent().setClassName(packageName, "com.example.faceui.SolidFreeCadUiPreviewActivity"))
        }
        action("2 · Abrir proceso CAD de diagnóstico") {
            stageFile().writeText("0_launcher_requested_probe")
            startActivity(Intent().setClassName(packageName, "com.example.faceui.CadEngineProbeActivity"))
        }
        action("3 · Probar carga nativa aislada") {
            nativeStageFile().writeText("N0_launcher_requested_nativeprobe")
            startActivity(Intent().setClassName(packageName, "com.example.faceui.NativeLoadProbeActivity"))
        }
        action("Actualizar diagnóstico") { refreshStage() }
        action("Copiar diagnóstico") {
            val report = diagnosticReport()
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("SolidFreeCAD diagnóstico", report))
            stageText.text = report + "\n\nDiagnóstico copiado."
        }

        stageText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(25, 35, 45))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(stageText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return ScrollView(this).apply { addView(root) }
    }

    private fun stageFile(): File = File(filesDir, "cadengine-stage.txt")
    private fun reportFile(): File = File(filesDir, "cadengine-report.txt")
    private fun nativeStageFile(): File = File(filesDir, "nativeprobe-stage.txt")
    private fun nativeReportFile(): File = File(filesDir, "nativeprobe-report.txt")

    private fun writeFailure(source: String, failure: Throwable) {
        val text = source + ": " + failure.javaClass.name + ": " + (failure.message ?: "sin mensaje")
        reportFile().writeText(text)
        refreshStage()
    }

    private fun refreshStage() {
        stageText.text = diagnosticReport()
    }

    private fun diagnosticReport(): String {
        fun read(file: File, fallback: String): String = runCatching { file.takeIf(File::isFile)?.readText() }.getOrNull() ?: fallback
        return buildString {
            appendLine("Estado Android: lanzador seguro activo")
            appendLine("Última etapa CAD: " + read(stageFile(), "sin intento CAD"))
            appendLine("Informe CAD: " + read(reportFile(), "sin informe"))
            appendLine("Última etapa nativa: " + read(nativeStageFile(), "sin intento nativo"))
            appendLine("Informe nativo: " + read(nativeReportFile(), "sin informe"))
            appendLine("Fabricante: " + Build.MANUFACTURER)
            appendLine("Modelo: " + Build.MODEL)
            appendLine("Android: " + Build.VERSION.RELEASE + " · SDK " + Build.VERSION.SDK_INT)
            appendLine("ABI: " + Build.SUPPORTED_ABIS.joinToString())
        }.trim()
    }
}
