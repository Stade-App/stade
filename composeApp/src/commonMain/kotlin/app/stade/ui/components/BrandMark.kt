package app.stade.ui.components

import androidx.compose.foundation.Image // Bunu ekle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource // KMP'nin resource kütüphanesi
import stade.composeapp.generated.resources.Res
import stade.composeapp.generated.resources.ic_launcher_foreground
import stade.composeapp.generated.resources.ic_launcher_monochrome


@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 200.dp // Varsayılan boyutu biraz daha büyüttüm
) {
    Image(
        painter = painterResource(Res.drawable.ic_launcher_monochrome),
        contentDescription = "App Logo",
        // modifier parametresi hem dışarıdan gönderilen özellikleri alır hem de boyutu uygular
        modifier = modifier.size(size),
        // Fit kullanarak logonun hiçbir yerinin kırpılmadan tamamen görünmesini sağladık
        contentScale = ContentScale.Fit
    )
}