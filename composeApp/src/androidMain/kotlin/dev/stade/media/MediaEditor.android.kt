package dev.stade.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@Composable
actual fun ForceFullScreenDialogWindow() {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.requestApplyInsets(window.decorView)
    }
}

@Composable
actual fun rememberNavigationBarHeight(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    val fallbackPx = remember(view) {
        val res = view.context.resources
        val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
        if (id > 0) runCatching { res.getDimensionPixelSize(id) }.getOrDefault(0) else 0
    }
    var heightPx by remember(fallbackPx) { mutableStateOf(fallbackPx) }
    DisposableEffect(view) {
        val listener = androidx.core.view.OnApplyWindowInsetsListener { _, insets ->
            val navInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            if (navInset > 0) heightPx = navInset
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(view, listener)
        ViewCompat.requestApplyInsets(view)
        onDispose {
            ViewCompat.setOnApplyWindowInsetsListener(view, null)
        }
    }
    return with(density) { heightPx.toDp() }
}

actual fun applyMediaEdits(original: ByteArray, crop: CropRect?, strokes: List<EditStroke>): ByteArray {
    val src = BitmapFactory.decodeByteArray(original, 0, original.size) ?: return original
    val mutable = src.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutable)
    val w = mutable.width.toFloat()
    val h = mutable.height.toFloat()

    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        if (stroke.points.size == 1) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                style = Paint.Style.FILL
            }
            val p = stroke.points[0]
            canvas.drawCircle(p.x * w, p.y * h, (stroke.widthFraction * w) / 2f, paint)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = stroke.widthFraction * w
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            path.moveTo(stroke.points[0].x * w, stroke.points[0].y * h)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].x * w, stroke.points[i].y * h)
            }
            canvas.drawPath(path, paint)
        }
    }

    val edited = if (crop != null) {
        val left = (crop.left * w).roundToInt().coerceIn(0, mutable.width - 1)
        val top = (crop.top * h).roundToInt().coerceIn(0, mutable.height - 1)
        val right = (crop.right * w).roundToInt().coerceIn(left + 1, mutable.width)
        val bottom = (crop.bottom * h).roundToInt().coerceIn(top + 1, mutable.height)
        Bitmap.createBitmap(mutable, left, top, right - left, bottom - top)
    } else {
        mutable
    }

    val out = ByteArrayOutputStream()
    edited.compress(Bitmap.CompressFormat.JPEG, 90, out)
    return out.toByteArray()
}
