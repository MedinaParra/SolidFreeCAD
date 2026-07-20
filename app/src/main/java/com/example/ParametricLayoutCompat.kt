package com.example

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/**
 * Alignment modifier used by overlay composables that are intentionally kept
 * outside a BoxScope. It positions the child against the finite viewport
 * constraints while preserving the child's measured size.
 */
fun Modifier.align(alignment: Alignment): Modifier = this.then(
    object : LayoutModifier {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints
        ): MeasureResult {
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placeable = measurable.measure(loose)
            val parentWidth = constraints.maxWidth.coerceAtLeast(placeable.width)
            val parentHeight = constraints.maxHeight.coerceAtLeast(placeable.height)
            val position = alignment.align(
                size = IntSize(placeable.width, placeable.height),
                space = IntSize(parentWidth, parentHeight),
                layoutDirection = LayoutDirection.Ltr
            )
            return layout(placeable.width, placeable.height) {
                placeable.place(position.x, position.y)
            }
        }
    }
)
