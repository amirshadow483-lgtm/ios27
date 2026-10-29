package com.glasshome.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize

@Composable
fun LauncherRoot(vm: LauncherViewModel, onPickWallpaper: () -> Unit) {
    val ctx = LocalContext.current
    val s = vm.settings
    val dark = when (s.theme) { 1 -> false; 2 -> true; else -> androidx.compose.foundation.isSystemInDarkTheme() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val wp by produceState<WallpaperState?>(null, s.wallpaper, vm.wallpaperVersion) { value = Wallpapers.load(ctx, s.wallpaper) }
    val lvl = if (s.reducedEffects) minOf(s.animLevel, 1) else s.animLevel

    BackHandler(enabled = vm.menu != null || vm.openFolder != null || vm.overlay != Overlay.None) { vm.back() }

    CompositionLocalProvider(
        LocalSettings provides s, LocalDark provides dark, LocalWallpaper provides wp,
        LocalRootSize provides rootSize, LocalRepo provides vm.repo,
    ) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            Box(Modifier.fillMaxSize().onSizeChanged { rootSize = it }) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = wp
                    if (w != null) drawImage(w.sharp, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Medium)
                    else drawRect(Color(0xFF0B1020))
                    if (dark) drawRect(Color.Black.copy(alpha = 0.28f))
                }
                HomeScreen(vm)

                @Composable
                fun Layer(o: Overlay, content: @Composable () -> Unit) {
                    AnimatedVisibility(vm.overlay == o, Modifier.fillMaxSize(), enter = enterT(lvl), exit = exitT(lvl)) { content() }
                }
                Layer(Overlay.Library) { LibraryScreen(vm) }
                Layer(Overlay.Notifs) { NotificationPanel(vm) }
                Layer(Overlay.Control) { ControlCenter(vm) }
                Layer(Overlay.Settings) { SettingsScreen(vm, onPickWallpaper) }
                Layer(Overlay.Diagnostics) { DiagnosticsScreen(vm) }
                Layer(Overlay.Lock) { LockPreview(vm) }
                Layer(Overlay.Recovery) { RecoveryScreen(vm) }
                MenuHost(vm)
            }
        }
    }
}
