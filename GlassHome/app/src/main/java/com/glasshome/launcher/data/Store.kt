package com.glasshome.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** All persistence is local SharedPreferences. Nothing leaves the device. */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("glass_prefs", Context.MODE_PRIVATE)
    private val crash = ctx.getSharedPreferences("glass_crash", Context.MODE_PRIVATE)

    fun loadSettings(): SettingsData {
        val d = SettingsData()
        return SettingsData(
            theme = sp.getInt("theme", d.theme),
            glassOpacity = sp.getFloat("glassOpacity", d.glassOpacity),
            dockOpacity = sp.getFloat("dockOpacity", d.dockOpacity),
            iconScale = sp.getFloat("iconScale", d.iconScale),
            cornerPct = sp.getFloat("cornerPct", d.cornerPct),
            showLabels = sp.getBoolean("showLabels", d.showLabels),
            iconRim = sp.getBoolean("iconRim", d.iconRim),
            animLevel = sp.getInt("animLevel", d.animLevel),
            reducedEffects = sp.getBoolean("reducedEffects", d.reducedEffects),
            wallpaper = sp.getString("wallpaper", d.wallpaper) ?: d.wallpaper,
            lockBold = sp.getBoolean("lockBold", d.lockBold),
        )
    }

    fun saveSettings(s: SettingsData) {
        sp.edit()
            .putInt("theme", s.theme).putFloat("glassOpacity", s.glassOpacity)
            .putFloat("dockOpacity", s.dockOpacity).putFloat("iconScale", s.iconScale)
            .putFloat("cornerPct", s.cornerPct).putBoolean("showLabels", s.showLabels)
            .putBoolean("iconRim", s.iconRim).putInt("animLevel", s.animLevel)
            .putBoolean("reducedEffects", s.reducedEffects).putString("wallpaper", s.wallpaper)
            .putBoolean("lockBold", s.lockBold).apply()
    }

    fun hasLayout() = sp.contains("layout")

    fun saveLayout(l: HomeLayout) {
        val pages = JSONArray()
        for (page in l.pages) {
            val arr = JSONArray()
            for (it in page) {
                val o = JSONObject()
                when (it) {
                    is HomeItem.App -> { o.put("t", "a"); o.put("p", it.pkg) }
                    is HomeItem.Folder -> {
                        o.put("t", "f"); o.put("i", it.id); o.put("n", it.name)
                        val pk = JSONArray(); it.pkgs.forEach { p -> pk.put(p) }; o.put("a", pk)
                    }
                }
                arr.put(o)
            }
            pages.put(arr)
        }
        val root = JSONObject()
        root.put("pages", pages)
        root.put("dock", JSONArray().also { a -> l.dock.forEach { a.put(it) } })
        root.put("known", JSONArray().also { a -> l.known.forEach { a.put(it) } })
        sp.edit().putString("layout", root.toString()).apply()
    }

    fun loadLayout(): HomeLayout {
        val s = sp.getString("layout", null) ?: return HomeLayout()
        return try {
            val root = JSONObject(s)
            val pages = ArrayList<List<HomeItem>>()
            val pa = root.getJSONArray("pages")
            for (i in 0 until pa.length()) {
                val arr = pa.getJSONArray(i)
                val items = ArrayList<HomeItem>()
                for (j in 0 until arr.length()) {
                    val o = arr.getJSONObject(j)
                    if (o.getString("t") == "a") items.add(HomeItem.App(o.getString("p")))
                    else {
                        val a = o.getJSONArray("a")
                        items.add(HomeItem.Folder(o.getString("i"), o.getString("n"),
                            (0 until a.length()).map { a.getString(it) }))
                    }
                }
                pages.add(items)
            }
            fun strs(k: String): List<String> {
                val a = root.getJSONArray(k); return (0 until a.length()).map { a.getString(it) }
            }
            HomeLayout(pages.ifEmpty { listOf(emptyList()) }, strs("dock"), strs("known").toSet())
        } catch (e: Throwable) {
            HomeLayout() // corrupted prefs: rebuild from installed apps, nothing else is touched
        }
    }

    fun loadRecents(): List<String> {
        val s = sp.getString("recents", "") ?: ""
        return if (s.isBlank()) emptyList() else s.split("|")
    }
    fun saveRecents(l: List<String>) { sp.edit().putString("recents", l.joinToString("|")).apply() }

    // crash bookkeeping (local only)
    fun crashedRecently(): Boolean =
        crash.getInt("count", 0) > 0 && System.currentTimeMillis() - crash.getLong("last", 0) < 10 * 60_000
    fun crashCount() = crash.getInt("count", 0)
    fun clearCrash() { crash.edit().putInt("count", 0).apply() }
}
