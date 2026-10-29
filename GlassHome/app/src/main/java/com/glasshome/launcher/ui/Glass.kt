package com.glasshome.launcher.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.glasshome.launcher.data.AppEntry
import com.glasshome.launcher.data.AppRepository
import com.glasshome.launcher.data.HomeItem
import com.glasshome.launcher.data.SettingsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalSettings = staticCompositionLocalOf { SettingsData() }
val LocalDark = staticCompositionLocalOf { true }
val LocalWallpaper = staticCompositionLocalOf<WallpaperState?> { null }
val LocalRootSize = staticCompositionLocalOf { IntSize.Zero }
val LocalRepo = staticCompositionLocalOf<AppRepository> { error("AppRepository not provided") }

val Accent = Color(0xFF0A84FF)
val Danger = Color(0xFFFF453A)

@Composable fun textPrimary(): Color = if (LocalDark.current) Color.White else Color(0xE6000000)
@Composable fun textSecondary(): Color = if (LocalDark.current) Color.White.copy(0.62f) else Color.Black.copy(0.55f)

fun enterT(level: Int): EnterTransition = when (level) {
    0 -> EnterTransition.None
    1 -> fadeIn(tween(160))
    else -> fadeIn(tween(220)) + slideInVertically(tween(320)) { -it / 6 }
}
fun exitT(level: Int): ExitTransition = when (level) {
    0 -> ExitTransition.None
    1 -> fadeOut(tween(120))
    else -> fadeOut(tween(180)) + slideOutVertically(tween(260)) { -it / 6 }
}

/**
 * Liquid-Glass-inspired surface: pre-blurred wallpaper drawn aligned with the screen (a real
 * frosted backdrop that costs nothing per frame), tint, specular sheen and a gradient rim.
 * With "Reduced effects" it falls back to a plain translucent fill.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    strength: Float? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val s = LocalSettings.current
    val dark = LocalDark.current
    val wp = LocalWallpaper.current
    val root = LocalRootSize.current
    val alpha = strength ?: s.glassOpacity
    val reduced = s.reducedEffects
    val pos = remember { mutableStateOf(Offset.Zero) }
    val sat = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.35f) }) }
    Box(
        modifier
            .onGloballyPositioned { pos.value = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                if (!reduced && wp != null && root.width > 0) {
                    translate(-pos.value.x, -pos.value.y) {
                        drawImage(wp.blurred, dstSize = IntSize(root.width, root.height),
                            colorFilter = sat, filterQuality = FilterQuality.Medium)
                    }
                }
                val base = if (dark) Color(0xFF1B1B1F) else Color.White
                drawRect(base.copy(alpha = if (reduced) 0.82f else alpha))
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(if (dark) 0.10f else 0.28f), Color.Transparent)))
            }
            .border(0.8.dp, Brush.linearGradient(listOf(Color.White.copy(0.65f), Color.White.copy(0.08f), Color.White.copy(0.35f))), shape),
        content = content,
    )
}

@Composable
fun AppIconImage(entry: AppEntry, size: Dp, modifier: Modifier = Modifier, small: Boolean = false) {
    val s = LocalSettings.current
    val repo = LocalRepo.current
    val bmp by produceState<ImageBitmap?>(null, entry.pkg) { value = withContext(Dispatchers.Default) { repo.icon(entry) } }
    val shape = RoundedCornerShape(size * s.cornerPct)
    Box(modifier.size(size).clip(shape).then(
        if (s.iconRim && !small) Modifier.border(0.6.dp, Brush.verticalGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.05f))), shape) else Modifier
    )) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize()) }
    }
}

@Composable
fun FolderIcon(folder: HomeItem.Folder, entries: Map<String, AppEntry>, size: Dp) {
    val s = LocalSettings.current
    GlassSurface(Modifier.size(size), RoundedCornerShape(size * s.cornerPct), strength = 0.45f) {
        Column(Modifier.fillMaxSize().padding(size * 0.1f), verticalArrangement = Arrangement.SpaceEvenly) {
            folder.pkgs.take(9).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { pkg -> entries[pkg]?.let { AppIconImage(it, size * 0.24f, small = true) } ?: Spacer(Modifier.size(size * 0.24f)) }
                }
            }
        }
    }
}

@Composable
fun WhiteLabel(text: String, modifier: Modifier = Modifier, size: Int = 11) {
    Text(text, modifier, color = Color.White, fontSize = size.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = LocalTextStyle.current.copy(shadow = Shadow(Color.Black.copy(0.45f), Offset(0f, 1f), 4f)))
}

/** Opaque-ish full screen container with dimmed blurred wallpaper; swallows touches so nothing leaks to the home screen. */
@Composable
fun FullScreenPanel(dim: Float = 0.45f, content: @Composable BoxScope.() -> Unit) {
    val wp = LocalWallpaper.current
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }
        .drawBehind {
            if (wp != null) drawImage(wp.blurred, dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Medium)
            else drawRect(Color(0xFF0B1020))
            drawRect(Color.Black.copy(alpha = dim))
        }, content = content)
}

@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        kotlinx.coroutines.delay(60_000 - value % 60_000 + 50)
    }
}
