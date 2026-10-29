package com.glasshome.launcher.ui

import android.content.Intent
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasshome.launcher.system.GlassNotificationListener
import com.glasshome.launcher.system.NotificationStore
import com.glasshome.launcher.system.isNotificationAccessGranted
import com.glasshome.launcher.system.openFirst
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationPanel(vm: LauncherViewModel, embedded: Boolean = false) {
    val ctx = LocalContext.current
    val items by NotificationStore.items.collectAsState()
    val granted = remember(vm.tick) { isNotificationAccessGranted(ctx) }
    val content: @Composable ColumnScope.() -> Unit = {
        if (!granted) {
            GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Notification access is off", color = textPrimary(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("To show your notifications in this glass panel Android needs your explicit permission. " +
                        "Contents stay in memory on this phone only — they are never saved, uploaded or shared " +
                        "(this app has no internet permission). You can revoke it any time.",
                        color = textSecondary(), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Grant access ↗", color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable {
                        openFirst(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    })
                }
            }
        } else if (items.isEmpty()) {
            Text("No notifications", color = if (embedded) Color.White.copy(0.8f) else textSecondary(),
                modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center)
        } else {
            items.forEach { n ->
                GlassSurface(Modifier.fillMaxWidth().clickable { vm.close(); vm.launchPkg(ctx, n.pkg) }, RoundedCornerShape(22.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(n.app.uppercase(), color = textSecondary(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            if (n.title.isNotBlank()) Text(n.title, color = textPrimary(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (n.text.isNotBlank()) Text(n.text, color = textPrimary().copy(0.85f), fontSize = 14.sp, maxLines = 2)
                        }
                        Icon(Icons.Rounded.Close, "Dismiss", tint = textSecondary(), modifier = Modifier.size(20.dp).clickable {
                            GlassNotificationListener.instance?.dismiss(n.key)
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (embedded) { Column(content = content); return }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.35f)).pointerInput(Unit) { detectTapGestures { vm.close() } }
        .pointerInput(Unit) {
            var t = 0f
            detectVerticalDragGestures(onDragStart = { t = 0f }, onDragEnd = { if (t < -60f) vm.close() }, onVerticalDrag = { _, d -> t += d })
        }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).pointerInput(Unit) { detectTapGestures { } }.verticalScroll(rememberScrollState())) {
            Text("Notifications", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

/** Visual-only lock screen preview. It does NOT replace or weaken Android's real lock screen. */
@Composable
fun LockPreview(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val s = LocalSettings.current
    val now by rememberNow()
    val torch by vm.controls.torchOn
    DisposableEffect(Unit) { vm.controls.startTorchWatch(); onDispose { vm.controls.stopTorchWatch() } }
    val is24 = DateFormat.is24HourFormat(ctx)
    val time = SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now))
    FullScreenPanel(dim = 0.15f) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            var t = 0f
            detectVerticalDragGestures(onDragStart = { t = 0f }, onDragEnd = { if (t < -120f) vm.close() }, onVerticalDrag = { _, d -> t += d })
        }) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(36.dp))
                Text(date, color = Color.White.copy(0.9f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(time, color = Color.White, fontSize = 96.sp, fontWeight = if (s.lockBold) FontWeight.Bold else FontWeight.Thin)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { NotificationPanel(vm, embedded = true) }
                Text("Preview only — your real PIN / fingerprint lock screen is unchanged.\nSwipe up to close.",
                    color = Color.White.copy(0.7f), fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 28.dp, start = 24.dp, end = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    GlassSurface(Modifier.size(56.dp).clickable { vm.controls.toggleTorch() }, CircleShape, strength = if (torch) 0.8f else null) {
                        Icon(Icons.Rounded.FlashlightOn, "Flashlight", Modifier.align(Alignment.Center), tint = if (torch) Color.Black else Color.White)
                    }
                    GlassSurface(Modifier.size(56.dp).clickable { vm.close(); openFirst(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }, CircleShape) {
                        Icon(Icons.Rounded.CameraAlt, "Camera", Modifier.align(Alignment.Center), tint = Color.White)
                    }
                }
            }
        }
    }
}
