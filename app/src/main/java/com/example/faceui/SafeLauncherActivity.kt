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
            setPadding(dp(24), dp(30), dp(24), dp(28))
            setBackgroundColor(Color.rgb(238, 243, 247))
        }
        root.addView(TextView(this).apply {
            text = "SolidFreeCAD 3.1.4"
            textSize = 28f
            setTextColor(Color.rgb(25, 35, 45))
            gravity = Gravity.CENTER
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(TextView(this).apply {
            text = "CAD táctil con FreeCAD y OpenCASCADE"
            textSize = 16f
            setTextColor(Color.rgb(70, 85, 100))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        })
        root.addView(TextView(this).apply {
            text = "El desarrollo 3.1 permanece completo. Esta versión inicia el entorno en forma progresiva para evitar el cierre temprano detectado en Android 16."
            textSize = 15f
            setTextColor(Color.rgb(25, 35, 45))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) })

        fun action(label: String, click: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                textSize = 15f
                setOnClickListener { runCatching(click).onFailure { writeFailure("launcher", it) } }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(10) })
        }

        action("Abrir SolidFreeCAD") {
            progressiveStageFile().writeText("0_launcher_requested_progressive")
            startActivity(Intent().setClassName(packageName, "com.example.faceui.ProgressiveCadActivity"))
        }
        action("Vista previa de interfaz") {
            startActivity(Intent().setClassName(packageName, "com.example.faceui.SolidFreeCadUiPreviewActivity"))
        }
        action("Diagnóstico de GPU y proceso CAD") {
            stageFile().writeText("0_launcher_requested_probe")
            startActivity(Intent().setClassName(packageName, "com.example.faceui.CadEngineProbeActivity"))
        }
        action("Diagnóstico de librerías nativas") {
            nativeStageFile().writeText("N0_launcher_requested_nativeprobe")
            startActivity(Intent().setClassName(packageName, "com.example.faceui.NativeLoadProbeActivity"))
        }
        action("Actualizar estado") { refreshStage() }
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
    private fun progressiveStageFile(): File = File(filesDir, "progressive-stage.txt")
    private fun progressiveReportFile(): File = File(filesDir, "progressive-report.txt")

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
            appendLine("Estado Android: lanzador SolidFreeCAD activo")
            appendLine("Carga progresiva: " + read(progressiveStageFile(), "sin intento"))
            appendLine("Informe progresivo: " + read(progressiveReportFile(), "sin informe"))
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
