package app.stade.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Stade marka damgası — yuvarlak köşeli kare içinde "S" harfi, gradyan dolgu.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val gradient = Brush.linearGradient(
        listOf(Color(0xFF1E6091), Color(0xFF61A5C2))
    )
    val corner = size * 0.28f
    val fontSize = (size.value * 0.5f).sp
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "S",
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = fontSize)
        )
    }
}


