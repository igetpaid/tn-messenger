package com.sozvezdie.messenger.feature.motion

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

/**
 * Легкое уменьшение элемента при нажатии.
 * Используется в списке чатов и на карточках, чтобы интерфейс
 * ощущался «живым», как в настоящих мессенджерах.
 *
 * interactionSource передаётся тот же, что у clickable,
 * иначе анимация не узнает о нажатии.
 */
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "pressScale",
    )
    scale(scale)
}
