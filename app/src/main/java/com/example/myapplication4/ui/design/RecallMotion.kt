package com.example.myapplication4.ui.design

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Android also supplies MotionDurationScale to Compose; this policy removes spatial
 * motion and list placement altogether when the user disables system animations. */
val LocalRecallMotionEnabled = staticCompositionLocalOf { true }

@Composable
fun RecallMotionProvider(content: @Composable () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    fun enabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var motion by remember(resolver) { mutableStateOf(enabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { motion = enabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(LocalRecallMotionEnabled provides motion, content = content)
}

@Composable fun motionDuration(duration: Int) = if (LocalRecallMotionEnabled.current) duration else 0

/** Read animated state in the draw phase, not in the surrounding screen. */
@Composable
fun Modifier.recallPress(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) .985f else 1f,
        tween(motionDuration(RecallMotion.press), easing = FastOutSlowInEasing), label = "press")
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/** Used for a new review card only; never retain an outgoing answer or intercept input. */
@Composable
fun Modifier.recallArrival(identity: Any): Modifier {
    val duration = motionDuration(RecallMotion.standard)
    val alpha = remember(identity) { Animatable(if (duration == 0) 1f else 0f) }
    LaunchedEffect(identity, duration) {
        if (duration == 0) alpha.snapTo(1f)
        else alpha.animateTo(1f, tween(duration, easing = FastOutSlowInEasing))
    }
    return graphicsLayer { this.alpha = alpha.value; translationY = (1f - alpha.value) * 8.dp.toPx() }
}

@Composable
@Suppress("ModifierFactoryExtensionFunction") // Deliberately scoped to LazyItemScope's animateItem API.
fun LazyItemScope.recallItemMotion(): Modifier {
    val enabled = LocalRecallMotionEnabled.current
    return Modifier.animateItem(
        fadeInSpec = if (enabled) tween(RecallMotion.quick) else null,
        placementSpec = if (enabled) tween(RecallMotion.standard, easing = FastOutSlowInEasing) else null,
        fadeOutSpec = if (enabled) tween(RecallMotion.quick) else null,
    )
}

@Composable
fun RecallExpansion(visible: Boolean, content: @Composable () -> Unit) {
    val duration = motionDuration(RecallMotion.standard)
    AnimatedVisibility(visible,
        enter = fadeIn(tween(duration)) + expandVertically(tween(duration, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(duration)) + shrinkVertically(tween(duration, easing = FastOutSlowInEasing)),
    ) { content() }
}

@Composable
fun animatedRecallProgress(value: Float): State<Float> = animateFloatAsState(
    value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f,
    tween(motionDuration(RecallMotion.standard), easing = FastOutSlowInEasing), label = "progress",
)

internal fun interpolateRecallNumber(start: Double, end: Double, fraction: Float): Double =
    when { fraction >= 1f -> end; fraction <= 0f -> start; else -> start * (1.0 - fraction) + end * fraction }

/** Initial data appears immediately. Updates continue from the currently displayed
 * value, including interrupted updates. The formatter owns locale, units and precision.
 * TalkBack always receives the exact final value, not every intermediate frame. */
@Composable
fun AnimatedRecallNumber(
    value: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val safeValue = value.takeIf { it.isFinite() } ?: 0.0
    var displayed by remember { mutableDoubleStateOf(safeValue) }
    val duration = motionDuration(RecallMotion.number)
    LaunchedEffect(safeValue, duration) {
        val start = displayed
        if (duration == 0 || start == safeValue || format(start) == format(safeValue)) displayed = safeValue
        else {
            animate(0f, 1f, animationSpec = tween(duration, easing = FastOutSlowInEasing)) { fraction, _ ->
                displayed = interpolateRecallNumber(start, safeValue, fraction)
            }
            displayed = safeValue
        }
    }
    Text(format(displayed), modifier.clearAndSetSemantics { text = AnnotatedString(format(safeValue)) }, style = style, color = color)
}

@Composable
fun AnimatedRecallCount(value: Int, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = MaterialTheme.colorScheme.onSurface) {
    val s = com.example.myapplication4.util.recallStrings()
    AnimatedRecallNumber(value.toDouble(), { s.number(it.roundToInt()) }, modifier, style, color)
}

/** Related screens follow logical forward/back direction, including Arabic RTL.
 * Main destinations use a smaller travel distance rather than full-page slides. */
fun recallScreenTransition(forward: Boolean, rtl: Boolean, duration: Int, mainTab: Boolean = false): ContentTransform {
    val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
    val divisor = if (mainTab) 24 else 12
    return (fadeIn(tween(duration)) + slideInHorizontally(tween(duration, easing = FastOutSlowInEasing)) { sign * it / divisor }) togetherWith
        (fadeOut(tween(duration / 2)) + slideOutHorizontally(tween(duration, easing = FastOutSlowInEasing)) { -sign * it / divisor })
}
