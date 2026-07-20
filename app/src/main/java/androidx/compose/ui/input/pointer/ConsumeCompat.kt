@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package androidx.compose.ui.input.pointer

/** Compatibility extension for Compose versions where consume() is a member. */
fun PointerInputChange.consume() {
    // detectDragGestures already owns this pointer sequence. The native member,
    // when present, shadows this extension; older variants safely no-op here.
}
