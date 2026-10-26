package com.sozvezdie.messenger.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

// Набор приятных цветов для аватаров. Цвет выбирается по имени,
// чтобы у одного и того же человека он никогда не менялся.
private val AvatarColors = listOf(
    Color(0xFF4F46E5),
    Color(0xFF0EA5E9),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
    Color(0xFFEF4444),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
)

/**
 * Кружок с первой буквой имени.
 * Фотографий в профиле пока нет, поэтому аватар рисуется из имени.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val color = AvatarColors[name.hashCode().absoluteValue % AvatarColors.size]
    val secondColor = color.copy(alpha = 0.72f)

    Box(
        modifier = modifier
            .size(size)
            .background(Brush.linearGradient(listOf(color, secondColor)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
