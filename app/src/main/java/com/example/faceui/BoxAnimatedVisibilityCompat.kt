package com.example.faceui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Resolves AnimatedVisibility inside a Box nested in a Row.
 *
 * Compose exposes RowScope and ColumnScope overloads. With nested implicit
 * receivers the compiler can select the outer RowScope overload. This nearest
 * BoxScope overload delegates to the ordinary animation implementation.
 */
@Composable
fun BoxScope.AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = EnterTransition.None,
    exit: ExitTransition = ExitTransition.None,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content
    )
}
