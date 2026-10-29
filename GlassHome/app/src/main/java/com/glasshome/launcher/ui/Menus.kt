package com.glasshome.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasshome.launcher.data.HomeItem
import com.glasshome.launcher.system.openFirst
import com.glasshome.launcher.system.openHomeSettings

private fun loadShortcuts(ctx: Context, pkg: String): List<ShortcutInfo> = try {
    val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
    la.getShortcuts(q, Process.myUserHandle())?.filter { it.isEnabled }?.take(4) ?: emptyList()
} catch (e: Throwable) { emptyList() } // only the default Home app may query shortcuts

@Composable
private fun Scrim(onDismiss: () -> Unit, alpha: Float = 0.35f, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha)).pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter, content = content)
}

@Composable
private fun MenuRow(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    Text(text, color = if (destructive) Danger else textPrimary(), fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp))
}

@Composable
fun MenuHost(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val menu = vm.menu
    val folderId = vm.openFolder
    if (folderId != null) FolderDialog(vm, folderId)
    if (menu == null) return
    Scrim({ vm.menu = null }) {
        GlassSurface(Modifier.padding(16.dp).navigationBarsPadding().fillMaxWidth().pointerInput(Unit) { detectTapGestures { } },
            RoundedCornerShape(26.dp), strength = 0.55f) {
            Column {
                when (menu) {
                    is Menu.Home -> {
                        MenuRow("Settings") { vm.show(Overlay.Settings) }
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        MenuRow("Wallpaper & Appearance") { vm.show(Overlay.Settings) }
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        MenuRow("Preview Lock Screen") { vm.show(Overlay.Lock) }
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        MenuRow("Return to MIUI / Restore Original Home") { vm.show(Overlay.Recovery) }
                    }
                    is Menu.ForFolder -> {
                        MenuRow("Open") { vm.menu = null; vm.openFolder = menu.id }
                        HorizontalDivider(color = Color.White.copy(0.1f))
                        MenuRow("Dissolve Folder", destructive = true) { vm.dissolveFolder(menu.id); vm.menu = null }
                    }
                    is Menu.ForApp -> {
                        val e = vm.byPkg[menu.pkg]
                        val shortcuts = remember(menu.pkg) { loadShortcuts(ctx, menu.pkg) }
                        Text(e?.label ?: menu.pkg, color = textSecondary(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp))
                        if (e != null) MenuRow("Open") { vm.menu = null; vm.launch(ctx, e) }
                        shortcuts.forEach { sc ->
                            MenuRow(sc.shortLabel?.toString() ?: "Shortcut") {
                                vm.menu = null
                                try { (ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).startShortcut(sc, null, null) } catch (t: Throwable) {}
                            }
                        }
                        val inDock = menu.pkg in vm.layout.dock
                        val onHome = vm.layout.pages.flatten().any { menu.pkg in it.let { i -> i.allPkgsLocal() } } || inDock
                        when (menu.where) {
                            Where.Page -> {
                                if (vm.layout.dock.size < 4) MenuRow("Add to Dock") { vm.addToDock(menu.pkg); vm.menu = null }
                                MenuRow("Move to Next Page") { vm.moveToNextPage(menu.page, menu.pkg); vm.menu = null }
                                MenuRow("Remove from Home Screen") { vm.removeFromHome(menu.pkg); vm.menu = null }
                            }
                            Where.Dock -> {
                                MenuRow("Remove from Dock") { vm.removeFromDock(menu.pkg); vm.menu = null }
                            }
                            Where.Folder -> {
                                MenuRow("Remove from Folder") { menu.folderId?.let { vm.removeFromFolder(it, menu.pkg) }; vm.menu = null }
                            }
                            Where.Library -> {
                                if (!onHome) MenuRow("Add to Home Screen") { vm.addToHome(menu.pkg); vm.menu = null }
                            }
                        }
                        MenuRow("App Info") {
                            vm.menu = null
                            openFirst(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${menu.pkg}")))
                        }
                        MenuRow("Uninstall", destructive = true) {
                            vm.menu = null
                            openFirst(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:${menu.pkg}"))) // system shows its own confirmation
                        }
                    }
                }
            }
        }
    }
}

private fun HomeItem.allPkgsLocal(): List<String> = when (this) {
    is HomeItem.App -> listOf(pkg)
    is HomeItem.Folder -> pkgs
}

@Composable
private fun FolderDialog(vm: LauncherViewModel, id: String) {
    val ctx = LocalContext.current
    val folder = vm.layout.pages.flatten().firstOrNull { it is HomeItem.Folder && it.id == id } as? HomeItem.Folder
    if (folder == null) { vm.openFolder = null; return }
    val iconSize = 60.dp * LocalSettings.current.iconScale
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)).pointerInput(Unit) { detectTapGestures { vm.openFolder = null } },
        contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp).pointerInput(Unit) { detectTapGestures { } }, horizontalAlignment = Alignment.CenterHorizontally) {
            var name by remember(id) { mutableStateOf(folder.name) }
            BasicTextField(name, { name = it; vm.renameFolder(id, it) }, singleLine = true, cursorBrush = SolidColor(Color.White),
                textStyle = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center))
            Spacer(Modifier.height(14.dp))
            GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(34.dp), strength = 0.4f) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    folder.pkgs.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { pkg ->
                                val e = vm.byPkg[pkg]
                                Column(Modifier.width(84.dp).pointerInput(pkg) {
                                    detectTapGestures(
                                        onTap = { if (e != null) { vm.openFolder = null; vm.launch(ctx, e) } },
                                        onLongPress = { vm.menu = Menu.ForApp(pkg, Where.Folder, folderId = id) })
                                }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (e != null) { AppIconImage(e, iconSize); Spacer(Modifier.height(4.dp)); WhiteLabel(e.label) }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.width(84.dp)) }
                        }
                    }
                }
            }
        }
    }
}
