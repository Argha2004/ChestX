package com.medical.chestxray.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A soft ease-out curve tuned to feel like iOS spring motion — quick to start,
 * gently decelerating into place. Use for screen and content transitions.
 */
val IOSEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * Makes any composable gently scale down while pressed and spring back on release,
 * mimicking the tactile feedback of iOS controls. Fully self-contained: just append
 * `.pressScale()` to a Button (or any) modifier. It observes presses without
 * consuming them, so the underlying click still fires.
 */
fun Modifier.pressScale(pressedScale: Float = 0.96f): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        // Crisp press-in, minimal wobble on release — like a native iOS control.
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
        }
}

/**
 * A one-shot entrance animation: the element gently fades and slides up into place
 * when it first appears, giving screens that "content settling in" iOS feel. Pass a
 * [delayMillis] to stagger sibling elements (e.g. 0, 60, 120, …).
 */
fun Modifier.entrance(
    delayMillis: Int = 0,
    slide: Dp = 16.dp
): Modifier = composed {
    val slidePx = with(LocalDensity.current) { slide.toPx() }
    var appeared by remember { mutableStateOf(false) }
    // A gentle, almost-critically-damped spring gives a natural "settle into place"
    // rather than a mechanical linear fade.
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "entrance"
    )
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        appeared = true
    }
    graphicsLayer {
        alpha = progress.coerceIn(0f, 1f)
        translationY = (1f - progress) * slidePx
    }
}

/**
 * Eases an integer toward [target] (e.g. counting a statistic up on first load).
 */
@Composable
fun animatedInt(target: Int, durationMillis: Int = 850): Int {
    val value by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = durationMillis, easing = IOSEasing),
        label = "animatedInt"
    )
    return value
}

/**
 * A smooth, continuously rotating gradient arc — a lightweight, premium-feeling
 * loading indicator in place of the stock Material spinner.
 */
@Composable
fun SmoothLoader(
    modifier: Modifier = Modifier,
    indicatorSize: Dp = 44.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 3.5.dp
) {
    val transition = rememberInfiniteTransition(label = "smoothLoader")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(indicatorSize)) {
        val stroke = strokeWidth.toPx()
        val arcSize = this.size.minDimension - stroke
        rotate(rotation) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(color.copy(alpha = 0.05f), color)
                ),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(arcSize, arcSize),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}
