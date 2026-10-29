package com.glasshome.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Discovers real launchable apps and renders their real icons. No hard-coded apps. */
class AppRepository(private val app: Context) {
    private val pm = app.packageManager
    private val cache = LruCache<String, ImageBitmap>(300)

    fun load(): List<AppEntry> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            pm.queryIntentActivities(i, 0).mapNotNull { ri ->
                val ai = ri.activityInfo ?: return@mapNotNull null
                if (ai.packageName == app.packageName) return@mapNotNull null
                AppEntry(ai.packageName, ai.name, ri.loadLabel(pm).toString(), ai.applicationInfo?.category ?: -1)
            }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
        } catch (e: Throwable) { emptyList() }
    }

    fun isInstalled(pkg: String): Boolean = try { pm.getPackageInfo(pkg, 0); true } catch (e: Throwable) { false }

    @Synchronized
    fun icon(e: AppEntry): ImageBitmap {
        cache.get(e.pkg)?.let { return it }
        val d = try { pm.getActivityIcon(ComponentName(e.pkg, e.cls)) } catch (t: Throwable) { pm.defaultActivityIcon }
        val ib = render(d).asImageBitmap()
        cache.put(e.pkg, ib)
        return ib
    }

    fun clearCache() = cache.evictAll()

    private fun render(d: Drawable): Bitmap {
        if (d is AdaptiveIconDrawable) {
            // draw both 108dp layers then crop the 72dp safe area so the icon fills our rounded tile
            val s = 288
            val big = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val c = Canvas(big)
            d.background?.let { it.setBounds(0, 0, s, s); it.draw(c) }
            d.foreground?.let { it.setBounds(0, 0, s, s); it.draw(c) }
            return Bitmap.createBitmap(big, 48, 48, 192, 192)
        }
        val b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(0xFFF2F2F7.toInt())
        val inset = 22
        d.setBounds(inset, inset, 192 - inset, 192 - inset)
        d.draw(c)
        return b
    }
}
