package com.glasshome.launcher.ui

import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasshome.launcher.system.CtlState
import com.glasshome.launcher.system.openFirst
import kotlinx.coroutines.delay

/**
 * Honesty rule: a tile with the ↗ mark only OPENS the matching Android panel/page
 * (Android forbids third-party apps from toggling it). Tiles without it act directly.
 */
@Composable
fun ControlCenter(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val c = vm.controls
    var st by remember { mutableStateOf(c.snapshot()) }
    DisposableEffect(Unit) { c.startTorchWatch(); onDispose { c.stopTorchWatch() } }
    LaunchedEffect(vm.tick) { while (true) { st = c.snapshot(); delay(1000) } } // only runs while visible
    var bright by remember { mutableFloatStateOf(st.brightness) }
    var vol by remember { mutableFloatStateOf(st.volume) }
    val torch by c.torchOn

    fun needWrite(): Boolean {
        if (c.canWrite()) return false
        Toast.makeText(ctx, "Allow “Modify system settings” for Glass Home to control this", Toast.LENGTH_LONG).show()
        openFirst(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")))
        return true
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.35f)).pointerInput(Unit) {
        detectTapGestures { vm.close() }
    }.pointerInput(Unit) {
        var t = 0f
        detectVerticalDragGestures(onDragStart = { t = 0f }, onDragEnd = { if (t < -60f) vm.close() }, onVerticalDrag = { _, d -> t += d })
    }) {
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp).pointerInput(Unit) { detectTapGestures { } },
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Row(Modifier.fillMaxWidth().height(168.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassSurface(Modifier.weight(1f).fillMaxHeight(), RoundedCornerShape(30.dp)) {
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Round(Icons.Rounded.AirplanemodeActive, "Airplane", st.airplane, true) { openFirst(ctx, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
                            Round(Icons.Rounded.Wifi, "Wi-Fi", st.wifi, true) {
                                openFirst(ctx, Intent("android.settings.panel.action.WIFI"), Intent(Settings.ACTION_WIFI_SETTINGS))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Round(Icons.Rounded.Bluetooth, "Bluetooth", st.bluetooth, true) { openFirst(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                            Round(Icons.Rounded.Language, "Internet", false, true) {
                                openFirst(ctx, Intent("android.settings.panel.action.INTERNET_CONNECTIVITY"), Intent(Settings.ACTION_WIRELESS_SETTINGS))
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Tile(Modifier.weight(1f).fillMaxHeight(), Icons.Rounded.ScreenRotation, "Rotation", if (st.rotate) "Auto" else "Locked", st.rotate, !st.canWrite) {
                            if (st.canWrite) c.setAutoRotate(!st.rotate) else openFirst(ctx, Intent(Settings.ACTION_DISPLAY_SETTINGS))
                        }
                        Tile(Modifier.weight(1f).fillMaxHeight(), Icons.Rounded.DoNotDisturbOn, "Focus", if (st.dnd) "On" else "Off", st.dnd, true) {
                            openFirst(ctx, Intent(Settings.ACTION_SOUND_SETTINGS))
                        }
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Tile(Modifier.weight(1f).fillMaxHeight(), Icons.Rounded.FlashlightOn, "Torch",
                            if (!c.hasTorch) "None" else if (torch) "On" else "Off", torch, false) {
                            if (!c.toggleTorch()) Toast.makeText(ctx, "Flashlight unavailable", Toast.LENGTH_SHORT).show()
                        }
                        Tile(Modifier.weight(1f).fillMaxHeight(), Icons.Rounded.BatteryFull, "Battery",
                            if (st.battery >= 0) "${st.battery}%" + if (st.charging) " ⚡" else "" else "--", st.charging, true) {
                            openFirst(ctx, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().height(176.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VSlider(Modifier.weight(1f).fillMaxHeight(), bright, Icons.Rounded.Brightness6) { v ->
                    if (!needWrite()) { bright = v; c.setBrightness(v) }
                }
                VSlider(Modifier.weight(1f).fillMaxHeight(), vol, Icons.Rounded.VolumeUp) { v -> vol = v; c.setVolume(v) }
                GlassSurface(Modifier.weight(2f).fillMaxHeight(), RoundedCornerShape(30.dp)) {
                    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Round(Icons.Rounded.CameraAlt, "Camera", false, false) {
                                vm.close(); openFirst(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                            }
                            Round(Icons.Rounded.Lock, "Lock view", false, false) { vm.show(Overlay.Lock) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Round(Icons.Rounded.Settings, "Settings", false, false) { vm.show(Overlay.Settings) }
                            Round(Icons.Rounded.Home, "MIUI Home", false, false) { vm.show(Overlay.Recovery) }
                        }
                    }
                }
            }
            Text("↗ opens the system panel (Android doesn’t let apps toggle it)  •  no mark = controlled directly",
                color = Color.White.copy(0.7f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun Round(icon: ImageVector, label: String, active: Boolean, opens: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(58.dp).clickable(onClick = onClick).background(if (active) Accent else Color.White.copy(0.18f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (active) Color.White else textPrimary(), modifier = Modifier.size(26.dp))
            if (opens) Text("↗", color = (if (active) Color.White else textSecondary()), fontSize = 9.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp))
        }
        Text(label, color = textPrimary(), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun Tile(modifier: Modifier, icon: ImageVector, title: String, status: String, active: Boolean, opens: Boolean, onClick: () -> Unit) {
    GlassSurface(modifier.clickable(onClick = onClick), RoundedCornerShape(26.dp), strength = if (active) 0.15f else null) {
        Box(Modifier.fillMaxSize().background(if (active) Accent.copy(0.75f) else Color.Transparent)) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(icon, title, tint = if (active) Color.White else textPrimary(), modifier = Modifier.size(24.dp))
                Column {
                    Text(title + if (opens) " ↗" else "", color = if (active) Color.White else textPrimary(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(status, color = if (active) Color.White.copy(0.85f) else textSecondary(), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun VSlider(modifier: Modifier, value: Float, icon: ImageVector, onChange: (Float) -> Unit) {
    var h by remember { mutableFloatStateOf(1f) }
    GlassSurface(modifier.onSizeChanged { h = it.height.toFloat().coerceAtLeast(1f) }
        .pointerInput(Unit) { detectVerticalDragGestures { change, _ -> change.consume(); onChange((1f - change.position.y / h).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) { detectTapGestures { onChange((1f - it.y / h).coerceIn(0f, 1f)) } },
        RoundedCornerShape(30.dp)) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0f, 1f)).align(Alignment.BottomCenter).background(Color.White.copy(0.9f)))
        Icon(icon, null, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), tint = Color(0xFF3A3A3C))
    }
}
