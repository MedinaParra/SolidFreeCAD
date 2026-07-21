package com.medinaparra.freecadandroid.runtime

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class InstalledPythonRuntime(
    val home: File,
    val abi: String,
    val version: String
)

/**
 * Installs the ABI-specific CPython/FreeCAD compatibility modules from APK assets.
 * The extraction is versioned, zip-slip protected and stored outside user documents.
 */
object PythonRuntimeInstaller {
    private const val markerName = ".solidfreecad-runtime"

    @Synchronized
    fun install(context: Context): InstalledPythonRuntime {
        val abi = BaseRuntimeDescriptor.selectAbi(Build.SUPPORTED_ABIS.toList())
            ?: error(
                "Este dispositivo no usa una ABI compatible. Disponibles: " +
                    Build.SUPPORTED_ABIS.joinToString()
            )
        val versionKey = buildString {
            append(BaseRuntimeDescriptor.runtimeVersion)
            append('-')
            append(BaseRuntimeDescriptor.sourceCommit.take(8))
        }
        val parent = File(context.noBackupFilesDir, "freecad-python")
        val destination = File(parent, "$versionKey/$abi")
        val marker = File(destination, markerName)
        if (isValid(destination, marker, abi)) {
            return InstalledPythonRuntime(destination, abi, BaseRuntimeDescriptor.runtimeVersion)
        }

        parent.mkdirs()
        val staging = File(parent, ".install-$versionKey-$abi-${System.nanoTime()}")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            extractAsset(context, BaseRuntimeDescriptor.pythonAssetPath(abi), staging)
            validateLayout(staging)
            File(staging, markerName).writeText(markerContents(abi), Charsets.UTF_8)

            destination.deleteRecursively()
            destination.parentFile?.mkdirs()
            if (!staging.renameTo(destination)) {
                staging.copyRecursively(destination, overwrite = true)
                staging.deleteRecursively()
            }
            check(isValid(destination, File(destination, markerName), abi)) {
                "El runtime Python se extrajo pero no superó la validación final"
            }
            return InstalledPythonRuntime(destination, abi, BaseRuntimeDescriptor.runtimeVersion)
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw IllegalStateException("No se pudo instalar el runtime FreeCAD/Python: ${error.message}", error)
        }
    }

    private fun extractAsset(context: Context, assetPath: String, destination: File) {
        val rootPath = destination.canonicalFile.toPath()
        context.assets.open(assetPath).use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = File(destination, entry.name).canonicalFile
                    check(target.toPath().startsWith(rootPath)) {
                        "Entrada insegura en runtime: ${entry.name}"
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output -> zip.copyTo(output, 64 * 1024) }
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun validateLayout(root: File) {
        val sitePackages = File(root, "lib/python${BaseRuntimeDescriptor.pythonVersion}/site-packages")
        check(File(sitePackages, "FreeCAD.py").isFile) { "Falta FreeCAD.py" }
        check(File(sitePackages, "Part.py").isFile) { "Falta Part.py" }
        check(File(root, "lib/python${BaseRuntimeDescriptor.pythonVersion}").isDirectory) {
            "Falta la biblioteca estándar de CPython"
        }
    }

    private fun isValid(root: File, marker: File, abi: String): Boolean = runCatching {
        if (!marker.isFile || marker.readText(Charsets.UTF_8) != markerContents(abi)) return false
        validateLayout(root)
        true
    }.getOrDefault(false)

    private fun markerContents(abi: String): String = buildString {
        appendLine("runtime=${BaseRuntimeDescriptor.runtimeVersion}")
        appendLine("freecad=${BaseRuntimeDescriptor.freeCadVersion}")
        appendLine("commit=${BaseRuntimeDescriptor.sourceCommit}")
        appendLine("abi=$abi")
    }
}
