package com.sozvezdie.messenger.feature.motion

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Три точки, которые мигают по очереди.
 * Показываются в заголовке чата, когда собеседник печатает.
 */
@Composable
fun TypingDots(
    modifier: Modifier = Modifier,
    color: Color = Color.Gray,
) {
    val transition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            // Каждая точка начинает мигать чуть позже предыдущей.
            val alpha = transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            ).value

            Row(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .background(color, CircleShape),
            ) {}
        }
    }
}
