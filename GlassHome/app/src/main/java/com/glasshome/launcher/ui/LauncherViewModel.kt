package com.glasshome.launcher.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.glasshome.launcher.data.*
import com.glasshome.launcher.system.SystemControls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Overlay { None, Library, Control, Notifs, Settings, Recovery, Lock, Diagnostics }
enum class Where { Page, Dock, Folder, Library }

sealed class Menu {
    data class ForApp(val pkg: String, val where: Where, val page: Int = 0, val folderId: String? = null) : Menu()
    data class ForFolder(val id: String) : Menu()
    object Home : Menu()
}

class LauncherViewModel(private val app: Application) : AndroidViewModel(app) {
    val repo = AppRepository(app)
    val controls = SystemControls(app)
    private val store = Store(app)

    var apps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var byPkg by mutableStateOf<Map<String, AppEntry>>(emptyMap()); private set
    var settings by mutableStateOf(store.loadSettings()); private set
    var layout by mutableStateOf(store.loadLayout()); private set
    var recents by mutableStateOf(store.loadRecents()); private set
    var overlay by mutableStateOf(Overlay.None); private set
    var libraryFocus by mutableStateOf(false); private set
    var menu by mutableStateOf<Menu?>(null)
    var openFolder by mutableStateOf<String?>(null)
    var crashRecovery by mutableStateOf(store.crashedRecently()); private set
    var tick by mutableIntStateOf(0)
    var homeSignal by mutableIntStateOf(0); private set
    var wallpaperVersion by mutableIntStateOf(0); private set

    init {
        if (crashRecovery) overlay = Overlay.Recovery
        if (store.crashCount() >= 2 && !settings.reducedEffects) edit { it.copy(reducedEffects = true) }
        refresh()
    }

    // ---------- settings ----------
    fun edit(f: (SettingsData) -> SettingsData) { settings = f(settings); store.saveSettings(settings) }

    // ---------- navigation ----------
    fun show(o: Overlay) { menu = null; openFolder = null; overlay = o }
    fun openLibrary(focus: Boolean) { libraryFocus = focus; show(Overlay.Library) }
    fun close() { overlay = Overlay.None }
    fun goHome() {
        if (crashRecovery) return
        menu = null; openFolder = null; overlay = Overlay.None; homeSignal++
    }
    fun back() {
        when {
            menu != null -> menu = null
            openFolder != null -> openFolder = null
            overlay != Overlay.None -> { if (overlay == Overlay.Recovery && crashRecovery) return; overlay = Overlay.None }
        }
    }
    fun dismissCrash() { crashRecovery = false; store.clearCrash(); if (overlay == Overlay.Recovery) overlay = Overlay.None }

    // ---------- apps ----------
    fun refresh() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.Default) { repo.load() }
            if (list.isEmpty()) return@launch   // never wipe layout on a failed query
            val launchable = list.map { it.pkg }
            val existing = withContext(Dispatchers.Default) {
                val inLayout = layout.pages.flatten().flatMap { it.allPkgs() } + layout.dock
                (launchable + inLayout.filter { it !in launchable && repo.isInstalled(it) }).toSet()
            }
            apps = list
            byPkg = list.associateBy { it.pkg }
            var l = layout
            if (!store.hasLayout()) l = l.copy(dock = defaultDock(app))
            setLayout(reconcile(l, launchable, existing))
        }
    }

    private fun defaultDock(ctx: Context): List<String> {
        val pm = ctx.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")).addCategory(Intent.CATEGORY_BROWSABLE),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        return intents.mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
            .filter { it != "android" && it != ctx.packageName }.distinct()
    }

    fun launch(ctx: Context, e: AppEntry) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(e.pkg, e.cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
            recents = (listOf(e.pkg) + recents.filter { it != e.pkg }).take(8)
            store.saveRecents(recents)
        } catch (t: Throwable) {
            Toast.makeText(ctx, "Can't open ${e.label}", Toast.LENGTH_SHORT).show()
        }
    }
    fun launchPkg(ctx: Context, pkg: String) {
        val e = byPkg[pkg]
        if (e != null) launch(ctx, e)
        else try { ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } catch (t: Throwable) {}
    }
    fun open(ctx: Context, item: HomeItem) {
        when (item) {
            is HomeItem.App -> byPkg[item.pkg]?.let { launch(ctx, it) }
            is HomeItem.Folder -> openFolder = item.id
        }
    }

    // ---------- layout operations ----------
    private fun setLayout(l: HomeLayout) { layout = l; store.saveLayout(l) }
    private fun pagesM() = layout.pages.map { it.toMutableList() }.toMutableList()
    private fun cleanup(pages: MutableList<MutableList<HomeItem>>): List<List<HomeItem>> {
        while (pages.size > 1 && pages.last().isEmpty()) pages.removeAt(pages.lastIndex)
        return if (pages.isEmpty()) listOf(emptyList()) else pages
    }

    fun reorder(page: Int, from: Int, to: Int) {
        val pages = pagesM(); val items = pages.getOrNull(page) ?: return
        if (from !in items.indices) return
        val it = items.removeAt(from); items.add(to.coerceIn(0, items.size), it)
        setLayout(layout.copy(pages = pages))
    }

    fun mergeIntoFolder(page: Int, from: Int, target: Int) {
        val pages = pagesM(); val items = pages.getOrNull(page) ?: return
        if (from !in items.indices || target !in items.indices || from == target) return
        val dragged = items[from] as? HomeItem.App ?: return
        val t = items[target]
        items[target] = when (t) {
            is HomeItem.App -> HomeItem.Folder(newFolderId(), "Folder", listOf(t.pkg, dragged.pkg))
            is HomeItem.Folder -> t.copy(pkgs = t.pkgs + dragged.pkg)
        }
        items.removeAt(from)
        setLayout(layout.copy(pages = pages))
    }

    fun dropToDock(page: Int, from: Int) {
        val pages = pagesM(); val items = pages.getOrNull(page) ?: return
        val dragged = items.getOrNull(from) as? HomeItem.App ?: return
        if (layout.dock.size >= 4 || dragged.pkg in layout.dock) return
        items.removeAt(from)
        setLayout(layout.copy(pages = cleanup(pages), dock = layout.dock + dragged.pkg))
    }

    fun addToDock(pkg: String) {
        if (layout.dock.size >= 4 || pkg in layout.dock) return
        val pages = pagesM()
        pages.forEach { p -> p.removeAll { it is HomeItem.App && it.pkg == pkg } }
        setLayout(layout.copy(pages = cleanup(pages), dock = layout.dock + pkg))
    }

    fun removeFromDock(pkg: String) {
        setLayout(layout.copy(dock = layout.dock - pkg))
        addToHome(pkg)
    }

    fun addToHome(pkg: String) {
        if (layout.pages.flatten().any { pkg in it.allPkgs() }) return
        val pages = pagesM()
        var idx = pages.indices.firstOrNull { pages[it].size < pageCapacity(it) } ?: -1
        if (idx < 0) { pages.add(mutableListOf()); idx = pages.lastIndex }
        pages[idx].add(HomeItem.App(pkg))
        setLayout(layout.copy(pages = pages))
    }

    /** Removes from the Home Screen only; the app stays installed and in the App Library. */
    fun removeFromHome(pkg: String) {
        val pages = pagesM()
        pages.forEach { p -> p.removeAll { it is HomeItem.App && it.pkg == pkg } }
        setLayout(layout.copy(pages = cleanup(pages), dock = layout.dock - pkg))
    }

    fun moveToNextPage(page: Int, pkg: String) {
        val pages = pagesM()
        val i = pages[page].indexOfFirst { it is HomeItem.App && it.pkg == pkg }
        if (i < 0) return
        val item = pages[page].removeAt(i)
        var target = page + 1
        while (target < pages.size && pages[target].size >= pageCapacity(target)) target++
        if (target >= pages.size) pages.add(mutableListOf())
        pages[target].add(item)
        setLayout(layout.copy(pages = cleanup(pages)))
    }

    fun renameFolder(id: String, name: String) {
        setLayout(layout.copy(pages = layout.pages.map { p -> p.map { if (it is HomeItem.Folder && it.id == id) it.copy(name = name) else it } }))
    }

    fun removeFromFolder(id: String, pkg: String) {
        val pages = pagesM()
        for (p in pages) {
            val i = p.indexOfFirst { it is HomeItem.Folder && it.id == id }
            if (i < 0) continue
            val f = p[i] as HomeItem.Folder
            val rest = f.pkgs - pkg
            if (rest.size >= 2) p[i] = f.copy(pkgs = rest)
            else { p.removeAt(i); rest.forEachIndexed { k, r -> p.add(i + k, HomeItem.App(r)) } }
            setLayout(layout.copy(pages = pages))
            addToHome(pkg)
            return
        }
    }

    fun dissolveFolder(id: String) {
        val pages = pagesM()
        for (p in pages) {
            val i = p.indexOfFirst { it is HomeItem.Folder && it.id == id }
            if (i < 0) continue
            val f = p.removeAt(i) as HomeItem.Folder
            f.pkgs.forEachIndexed { k, pkg -> p.add(i + k, HomeItem.App(pkg)) }
            break
        }
        // overflow protection
        for (idx in pages.indices) while (pages[idx].size > pageCapacity(idx)) {
            val extra = pages[idx].removeAt(pages[idx].lastIndex)
            if (idx + 1 >= pages.size) pages.add(mutableListOf())
            pages[idx + 1].add(0, extra)
        }
        setLayout(layout.copy(pages = cleanup(pages)))
    }

    // ---------- wallpaper ----------
    fun setCustomWallpaper(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Wallpapers.saveCustom(app, uri) }
            if (ok) { wallpaperVersion++; edit { it.copy(wallpaper = "custom") } }
            else Toast.makeText(app, "Could not read that image", Toast.LENGTH_SHORT).show()
        }
    }
}
