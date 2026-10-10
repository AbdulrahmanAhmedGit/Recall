package com.example.myapplication4.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Arabic/RTL mirrors navigation; horizontal direction locking preserves vertical reading. */
@Composable
internal fun Modifier.reviewGestures(identity: Any, enabled: Boolean, next: () -> Unit, previous: () -> Unit): Modifier {
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestNext by rememberUpdatedState(next)
    val latestPrevious by rememberUpdatedState(previous)
    return pointerInput(identity, enabled, threshold, rtl) {
        if (!enabled) return@pointerInput
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onHorizontalDrag = { change, delta -> change.consume(); distance += delta },
            onDragCancel = { distance = 0f },
            onDragEnd = {
                val forwardDistance = if (rtl) -distance else distance
                if (forwardDistance >= threshold) latestNext()
                else if (forwardDistance <= -threshold) latestPrevious()
                distance = 0f
            },
        )
    }
}
