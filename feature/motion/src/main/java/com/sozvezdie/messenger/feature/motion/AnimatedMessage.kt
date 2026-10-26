package com.sozvezdie.messenger.feature.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Плавное появление сообщения: снизу вверх и с проявлением.
 *
 * Анимация запускается один раз при первом показе элемента списка,
 * поэтому старые сообщения не «прыгают» при каждой перерисовке.
 */
@Composable
fun AnimatedMessage(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(180)) +
            slideInVertically(animationSpec = tween(220)) { height -> height / 3 },
    ) {
        content()
    }
}
