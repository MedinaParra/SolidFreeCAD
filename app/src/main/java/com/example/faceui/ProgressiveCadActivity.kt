package com.example.faceui

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
import androidx.activity.ComponentActivity
import java.io.File
import java.lang.reflect.InvocationTargetException

class ProgressiveCadActivity : ComponentActivity() {
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        stage("P1_progressive_onCreate_enter")
        super.onCreate(savedInstanceState)
        stage("P2_progressive_after_super")
        showBootstrap()
        stage("P3_progressive_bootstrap_visible")
    }

    private fun showBootstrap(error: Throwable? = null) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(34), dp(24), dp(28))
            setBackgroundColor(Color.rgb(238, 243, 247))
        }
        column.addView(TextView(this).apply {
            text = "SolidFreeCAD 3.1.4"
            textSize = 28f
            setTextColor(Color.rgb(24, 34, 44))
            gravity = Gravity.CENTER
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(TextView(this).apply {
            text = "Carga progresiva del entorno CAD"
            textSize = 16f
            setTextColor(Color.rgb(74, 88, 102))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(20))
        })
        column.addView(TextView(this).apply {
            text = "El visor, el árbol, las herramientas STEP y el motor nativo se cargan después de que esta pantalla ya está activa. Así Android 16 puede mostrar el error sin cerrar toda la aplicación."
            textSize = 15f
            setTextColor(Color.rgb(28, 40, 52))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(16)
        })

        column.addView(Button(this).apply {
            text = "Abrir entorno CAD completo"
            textSize = 16f
            setOnClickListener { installWorkbench() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)).apply { bottomMargin = dp(12) })

        column.addView(Button(this).apply {
            text = "Copiar diagnóstico de carga"
            textSize = 15f
            setOnClickListener {
                val report = diagnosticReport()
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("SolidFreeCAD carga progresiva", report))
                statusText.text = report + "\n\nDiagnóstico copiado."
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(14) })

        statusText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(24, 34, 44))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setBackgroundColor(Color.WHITE)
            text = diagnosticReport(error)
        }
        column.addView(statusText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun installWorkbench() {
        stage("P4_before_loader_class")
        statusText.text = "Cargando árbol, visor y herramientas…"
        runCatching {
            val loaderType = Class.forName("com.example.faceui.ProgressiveWorkbenchLoader", true, classLoader)
            stage("P5_loader_class_ready")
            val install = loaderType.getMethod("install", ComponentActivity::class.java)
            stage("P6_before_loader_install")
            install.invoke(null, this)
            stage("P7_loader_install_returned")
        }.onFailure { failure ->
            val root = if (failure is InvocationTargetException) failure.targetException ?: failure else failure
            reportFile().writeText(root.stackTraceToString())
            stage("P_FAIL_" + root.javaClass.simpleName)
            showBootstrap(root)
        }
    }

    private fun stage(value: String) {
        runCatching { stageFile().writeText(value) }
    }

    private fun stageFile(): File = File(filesDir, "progressive-stage.txt")
    private fun reportFile(): File = File(filesDir, "progressive-report.txt")

    private fun diagnosticReport(error: Throwable? = null): String {
        val stage = runCatching { stageFile().readText() }.getOrDefault("sin etapa")
        val persisted = runCatching { reportFile().readText() }.getOrDefault("sin error registrado")
        return buildString {
            appendLine("Modo: carga progresiva")
            appendLine("Etapa: " + stage)
            appendLine("Error actual: " + (error?.let { it.javaClass.name + ": " + (it.message ?: "sin mensaje") } ?: "ninguno"))
            appendLine("Registro: " + persisted)
        }.trim()
    }
}
