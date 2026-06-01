package app.stade.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.stade.ui.theme.BrandColor
import org.jetbrains.compose.resources.painterResource
import stade.composeapp.generated.resources.Res
import stade.composeapp.generated.resources.ic_launcher_monochrome

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    Image(
        painter = painterResource(Res.drawable.ic_launcher_monochrome),
        contentDescription = "App Logo",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.BrandColor)
    )
}