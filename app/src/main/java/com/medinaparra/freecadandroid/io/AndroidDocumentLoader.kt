package com.medinaparra.freecadandroid.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

object AndroidDocumentLoader {
    fun stage(
        context: Context,
        uri: Uri,
        directoryName: String,
        fallbackName: String
    ): Pair<File, String> {
        val name = displayName(context, uri) ?: fallbackName
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val directory = File(context.cacheDir, directoryName).apply { mkdirs() }
        val target = File(directory, safeName)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Android no pudo abrir el documento seleccionado" }
            target.outputStream().buffered().use { output -> input.copyTo(output) }
        }
        require(target.length() > 0L) { "El documento seleccionado está vacío" }
        return target to name
    }

    fun extension(displayName: String): String =
        displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)

    fun readableFailure(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.last()
        return root.message?.takeIf { it.isNotBlank() }
            ?: error.message?.takeIf { it.isNotBlank() }
            ?: error::class.java.simpleName
    }

    private fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment
    }
}
