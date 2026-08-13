package dev.stade.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BotBadgeColor = Color(0xFF3B82F6)

@Composable
fun BotBadge(modifier: Modifier = Modifier) {
    Text(
        text = "BOT",
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(BotBadgeColor)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold
    )
}
