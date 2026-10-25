package com.example.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Controller for coordinating staggered grid entrance animations.
 * Ensures the animation plays smoothly when the photo gallery first loads
 * or when refreshed/filtered, and avoids re-staggering already-viewed items
 * during lazy scrolling.
 */
@Stable
class StaggerState internal constructor(
    initialTrigger: Int = 0
) {
    var triggerCount by mutableIntStateOf(initialTrigger)
        private set

    var hasCompleted by mutableStateOf(false)
        internal set

    fun replay() {
        hasCompleted = false
        triggerCount++
    }
}

/**
 * Creates and remembers a [StaggerState]. Whenever [triggerKey] changes,
 * the staggered entrance is reset and triggered for initial viewport items.
 */
@Composable
fun rememberStaggerState(triggerKey: Any? = Unit): StaggerState {
    val state = remember { StaggerState() }

    LaunchedEffect(triggerKey) {
        state.hasCompleted = false
        state.replay()
        // Allow time for visible items (up to 24 items * 30ms delay + 450ms spring settling)
        delay(1200L)
        state.hasCompleted = true
    }

    return state
}

/**
 * Wraps a grid item in a staggered entrance animation.
 * On first load, items appear with an index-based delay, scaling up smoothly
 * with spring physics and sliding upward from an initial offset with a fade-in.
 */
@Composable
fun StaggeredGridItem(
    index: Int,
    staggerState: StaggerState,
    modifier: Modifier = Modifier,
    baseDelayMs: Long = 28L,
    maxStaggerItems: Int = 24,
    content: @Composable () -> Unit
) {
    // If stagger animation has completed (e.g. user scrolled after first load)
    // or the item is far down the list, immediately render without delay.
    if (staggerState.hasCompleted || index > maxStaggerItems) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    val animatable = remember(staggerState.triggerCount) { Animatable(0f) }

    LaunchedEffect(staggerState.triggerCount) {
        val delayMs = (index.coerceAtMost(maxStaggerItems) * baseDelayMs)
        if (delayMs > 0) {
            delay(delayMs)
        }
        animatable.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Box(
        modifier = modifier.graphicsLayer {
            val progress = animatable.value
            alpha = progress.coerceIn(0f, 1f)
            val scale = 0.82f + (0.18f * progress)
            scaleX = scale
            scaleY = scale
            translationY = (1f - progress) * 65f
        }
    ) {
        content()
    }
}
