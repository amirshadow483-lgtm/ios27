package com.glasshome.launcher.data

import java.util.UUID

const val COLS = 4
fun pageCapacity(page: Int) = if (page == 0) 16 else 24

data class AppEntry(val pkg: String, val cls: String, val label: String, val category: Int)

sealed class HomeItem {
    abstract val key: String
    data class App(val pkg: String) : HomeItem() { override val key get() = "a:$pkg" }
    data class Folder(val id: String, val name: String, val pkgs: List<String>) : HomeItem() {
        override val key get() = "f:$id"
    }
}

fun HomeItem.allPkgs(): List<String> = when (this) {
    is HomeItem.App -> listOf(pkg)
    is HomeItem.Folder -> pkgs
}

data class HomeLayout(
    val pages: List<List<HomeItem>> = listOf(emptyList()),
    val dock: List<String> = emptyList(),
    val known: Set<String> = emptySet(),
)

data class SettingsData(
    val theme: Int = 0,               // 0 system, 1 light, 2 dark
    val glassOpacity: Float = 0.30f,
    val dockOpacity: Float = 0.38f,
    val iconScale: Float = 1f,
    val cornerPct: Float = 0.2237f,   // iOS-like continuous corner approximation
    val showLabels: Boolean = true,
    val iconRim: Boolean = true,
    val animLevel: Int = 2,           // 0 off, 1 reduced, 2 full
    val reducedEffects: Boolean = false,
    val wallpaper: String = "preset:0",
    val lockBold: Boolean = false,
)

/**
 * Keeps layout in sync with installed apps.
 * [existing] = every package still installed (so apps being updated are never dropped),
 * [launchable] = packages that currently have a launcher icon.
 */
fun reconcile(l: HomeLayout, launchable: List<String>, existing: Set<String>): HomeLayout {
    fun clean(items: List<HomeItem>): List<HomeItem> = items.mapNotNull { item ->
        when (item) {
            is HomeItem.App -> if (item.pkg in existing) item else null
            is HomeItem.Folder -> {
                val p = item.pkgs.filter { it in existing }
                when {
                    p.size >= 2 -> item.copy(pkgs = p)
                    p.size == 1 -> HomeItem.App(p[0])
                    else -> null
                }
            }
        }
    }
    val pages = l.pages.map(::clean).toMutableList()
    if (pages.isEmpty()) pages.add(emptyList())
    val dock = l.dock.filter { it in existing }.take(4)
    val placed = pages.flatten().flatMap { it.allPkgs() }.toSet() + dock
    val fresh = launchable.filter { it !in l.known && it !in placed }
    for (p in fresh) {
        var idx = pages.indices.firstOrNull { pages[it].size < pageCapacity(it) } ?: -1
        if (idx < 0) { pages.add(emptyList()); idx = pages.lastIndex }
        pages[idx] = pages[idx] + HomeItem.App(p)
    }
    while (pages.size > 1 && pages.last().isEmpty()) pages.removeAt(pages.lastIndex)
    return HomeLayout(pages, dock, l.known + launchable)
}

fun newFolderId(): String = UUID.randomUUID().toString().take(8)
