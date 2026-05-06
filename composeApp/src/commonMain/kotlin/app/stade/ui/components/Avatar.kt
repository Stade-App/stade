package app.stade.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape
) {
    val palette = listOf(
        Color(0xFF1E6091) to Color(0xFF61A5C2),
        Color(0xFF166B5C) to Color(0xFF5BC0AB),
        Color(0xFF7A3E9D) to Color(0xFFB695D8),
        Color(0xFF9C4221) to Color(0xFFE3956A),
        Color(0xFF8E2A4A) to Color(0xFFD66A8C),
        Color(0xFF1F4F8F) to Color(0xFF6FA3DD),
        Color(0xFF4D6B1F) to Color(0xFFA8C46C),
        Color(0xFF7A4D00) to Color(0xFFD9A24E)
    )
    val seed = name.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7fffffff }
    val (a, b) = palette[seed % palette.size]
    val initial = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    val fontSize = (size.value * 0.42f).sp

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.offset(y = (-1).dp),
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = fontSize,
                lineHeight = fontSize
            )
        )
    }
}