package com.glasshome.launcher.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasshome.launcher.data.AppEntry

private val order = listOf("Social", "Games", "Entertainment", "Productivity & Info", "Utilities")
private fun categoryName(c: Int) = when (c) {
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_GAME -> "Games"
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> "Entertainment"
    ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_NEWS, ApplicationInfo.CATEGORY_MAPS -> "Productivity & Info"
    else -> "Utilities"
}

@Composable
fun LibraryScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (vm.libraryFocus) try { focus.requestFocus() } catch (e: Throwable) {} }
    val iconSize = 52.dp * LocalSettings.current.iconScale

    FullScreenPanel(dim = 0.4f) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            GlassSurface(Modifier.fillMaxWidth().height(46.dp), CircleShape, strength = 0.35f) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = textSecondary())
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text("Search apps", color = textSecondary(), fontSize = 16.sp)
                        BasicTextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                            cursorBrush = SolidColor(Accent), textStyle = TextStyle(color = textPrimary(), fontSize = 16.sp))
                    }
                    if (q.isNotEmpty()) Icon(Icons.Rounded.Close, null, Modifier.clickable { q = "" }, tint = textSecondary())
                }
            }
            Spacer(Modifier.height(12.dp))
            if (q.isBlank()) {
                val groups = vm.apps.groupBy { categoryName(it.category) }
                val recents = vm.recents.mapNotNull { vm.byPkg[it] }.take(4)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                    if (recents.isNotEmpty()) item { Section("Recents", recents, vm, iconSize) }
                    order.forEach { name -> groups[name]?.let { list -> item(name) { Section(name, list, vm, iconSize) } } }
                }
            } else {
                val res = vm.apps.filter { it.label.contains(q.trim(), ignoreCase = true) }.take(60)
                LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                    if (res.isEmpty()) item { Text("No apps match “$q”", color = textSecondary(), modifier = Modifier.padding(16.dp)) }
                    else item {
                        GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(22.dp), strength = 0.35f) {
                            Column {
                                res.forEach { e ->
                                    Row(Modifier.fillMaxWidth().pointerInput(e.pkg) {
                                        detectTapGestures(onTap = { vm.launch(ctx, e) }, onLongPress = { vm.menu = Menu.ForApp(e.pkg, Where.Library) })
                                    }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AppIconImage(e, 40.dp)
                                        Spacer(Modifier.width(14.dp))
                                        Text(e.label, color = textPrimary(), fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, apps: List<AppEntry>, vm: LauncherViewModel, iconSize: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp), strength = 0.33f) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = textPrimary(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            apps.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    row.forEach { e ->
                        Column(Modifier.weight(1f).pointerInput(e.pkg) {
                            detectTapGestures(onTap = { vm.launch(ctx, e) }, onLongPress = { vm.menu = Menu.ForApp(e.pkg, Where.Library) })
                        }, horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIconImage(e, iconSize)
                            Spacer(Modifier.height(4.dp))
                            Text(e.label, color = textPrimary(), fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
