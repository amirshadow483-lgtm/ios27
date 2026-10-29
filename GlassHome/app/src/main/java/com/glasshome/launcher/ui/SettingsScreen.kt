package com.glasshome.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasshome.launcher.data.SettingsData
import com.glasshome.launcher.system.deviceFacts
import com.glasshome.launcher.system.defaultHomePackage
import com.glasshome.launcher.system.isNotificationAccessGranted
import com.glasshome.launcher.system.openFirst
import com.glasshome.launcher.system.openHomeSettings

@Composable
fun SettingsScreen(vm: LauncherViewModel, onPickWallpaper: () -> Unit) {
    val ctx = LocalContext.current
    val s = vm.settings
    FullScreenPanel(dim = 0.5f) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(30.dp).clickable { vm.close() })
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {

                GlassSurface(Modifier.fillMaxWidth().clickable { vm.show(Overlay.Recovery) }, RoundedCornerShape(22.dp), strength = 0.5f) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Return to MIUI / Restore Original Home", color = Danger, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("Switches the Home app back to the original POCO/MIUI launcher. Open recovery help ›", color = textSecondary(), fontSize = 13.sp)
                    }
                }

                Group("Appearance") {
                    Segment("Theme", listOf("System", "Light", "Dark"), s.theme) { v -> vm.edit { it.copy(theme = v) } }
                    Divider()
                    SliderRow("Glass transparency", s.glassOpacity, 0.10f..0.60f) { v -> vm.edit { it.copy(glassOpacity = v) } }
                    Divider()
                    SliderRow("Dock transparency", s.dockOpacity, 0.10f..0.60f) { v -> vm.edit { it.copy(dockOpacity = v) } }
                }
                Group("Icons") {
                    SliderRow("Icon size", s.iconScale, 0.8f..1.15f) { v -> vm.edit { it.copy(iconScale = v) } }
                    Divider()
                    SliderRow("Corner roundness", s.cornerPct, 0.12f..0.5f) { v -> vm.edit { it.copy(cornerPct = v) } }
                    Divider()
                    SwitchRow("Show app names", s.showLabels) { v -> vm.edit { it.copy(showLabels = v) } }
                    Divider()
                    SwitchRow("Glass rim on icons", s.iconRim) { v -> vm.edit { it.copy(iconRim = v) } }
                }
                Group("Wallpaper") {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Wallpapers.names.forEachIndexed { i, n ->
                            val thumb = remember(i) { Wallpapers.preset(i, 90, 195).asImageBitmap() }
                            val sel = s.wallpaper == "preset:$i"
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(thumb, n, Modifier.fillMaxWidth().aspectRatio(0.46f).clip(RoundedCornerShape(14.dp))
                                    .border(if (sel) 2.dp else 0.dp, Accent, RoundedCornerShape(14.dp))
                                    .clickable { vm.edit { it.copy(wallpaper = "preset:$i") } }, contentScale = ContentScale.Crop)
                                Text(n, color = textSecondary(), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    Divider()
                    ActionRow("Choose a photo… (stored privately in the app)", onClick = onPickWallpaper)
                }
                Group("Animations & performance") {
                    Segment("Animation intensity", listOf("Off", "Reduced", "Full"), s.animLevel) { v -> vm.edit { it.copy(animLevel = v) } }
                    Divider()
                    SwitchRow("Reduced effects (no backdrop blur — best battery)", s.reducedEffects) { v -> vm.edit { it.copy(reducedEffects = v) } }
                }
                Group("Lock screen (visual preview only)") {
                    SwitchRow("Bold clock", s.lockBold) { v -> vm.edit { it.copy(lockBold = v) } }
                    Divider()
                    ActionRow("Preview lock screen") { vm.show(Overlay.Lock) }
                }
                Group("Permissions overview") {
                    val home = remember(vm.tick) { defaultHomePackage(ctx) == ctx.packageName }
                    val notif = remember(vm.tick) { isNotificationAccessGranted(ctx) }
                    val write = remember(vm.tick) { Settings.System.canWrite(ctx) }
                    PermRow("Default Home app", home, "Needed for the home button to open Glass Home.") { openHomeSettings(ctx) }
                    Divider()
                    PermRow("Notification access (optional)", notif, "Shows notification cards in the glass panel. Kept in memory only.") {
                        openFirst(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    Divider()
                    PermRow("Modify system settings (optional)", write, "Lets the brightness slider and rotation tile act directly.") {
                        openFirst(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                    }
                    Divider()
                    Info("Not requested at all: Internet, location, contacts, storage, camera, microphone, accounts.")
                }
                Group("Privacy") {
                    Info("Glass Home works fully offline. Layout and settings are stored only on this phone. " +
                        "Notification text is never saved or transmitted. There is no analytics, no ads and no network access.")
                }
                Group("Help") {
                    ActionRow("Troubleshooting & recovery (Persian)") { vm.show(Overlay.Recovery) }
                    Divider()
                    ActionRow("Device diagnostics") { vm.show(Overlay.Diagnostics) }
                }
                Group("About") {
                    Info("Glass Home is an independent Android launcher inspired by the Liquid Glass design language. " +
                        "It is not Apple iOS and is not affiliated with Apple. It restyles the Home screen, App Library, Control Center " +
                        "and notification panel — it cannot restyle the interior of other apps such as Instagram.")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title.uppercase(), color = Color.White.copy(0.65f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), strength = 0.38f) { Column(content = content) }
    }
}
@Composable private fun Divider() = HorizontalDivider(color = Color.White.copy(0.1f), modifier = Modifier.padding(horizontal = 14.dp))
@Composable private fun Info(t: String) = Text(t, color = textSecondary(), fontSize = 13.sp, modifier = Modifier.padding(14.dp))

@Composable private fun SwitchRow(label: String, v: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = textPrimary(), fontSize = 15.sp, modifier = Modifier.weight(1f).padding(end = 10.dp))
        Switch(v, onChange)
    }
}
@Composable private fun SliderRow(label: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(label, color = textPrimary(), fontSize = 15.sp)
        Slider(v, onChange, valueRange = range, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent))
    }
}
@Composable private fun Segment(label: String, options: List<String>, sel: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(14.dp)) {
        Text(label, color = textPrimary(), fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(0.12f)).padding(3.dp)) {
            options.forEachIndexed { i, o ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (i == sel) Color.White.copy(0.9f) else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(o, color = if (i == sel) Color.Black else textPrimary(), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
@Composable private fun ActionRow(label: String, onClick: () -> Unit) {
    Text(label, color = Accent, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp))
}
@Composable private fun PermRow(name: String, ok: Boolean, why: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp)) {
        Row {
            Text(name, color = textPrimary(), fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(if (ok) "Granted ✓" else "Not granted ›", color = if (ok) Color(0xFF30D158) else Accent, fontSize = 13.sp)
        }
        Text(why, color = textSecondary(), fontSize = 12.sp)
    }
}

@Composable
fun DiagnosticsScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val facts = remember { deviceFacts(ctx) }
    FullScreenPanel(dim = 0.5f) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Device diagnostics", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(30.dp).clickable { vm.show(Overlay.Settings) })
            }
            Spacer(Modifier.height(12.dp))
            GlassSurface(Modifier.fillMaxWidth().weight(1f, false), RoundedCornerShape(22.dp), strength = 0.38f) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    facts.forEach { (k, v) ->
                        Column { Text(k, color = textSecondary(), fontSize = 12.sp); Text(v, color = textPrimary(), fontSize = 14.sp) }
                    }
                }
            }
        }
    }
}
