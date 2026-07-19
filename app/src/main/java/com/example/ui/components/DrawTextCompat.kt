package com.example.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText as composeDrawText

/**
 * Keeps text rendering calls local to the viewport package while delegating to
 * Compose's experimental DrawScope implementation.
 */
fun DrawScope.drawText(textLayoutResult: TextLayoutResult, topLeft: Offset) {
    composeDrawText(textLayoutResult = textLayoutResult, topLeft = topLeft)
}
