package com.glasshome.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WallpaperState(val sharp: ImageBitmap, val blurred: ImageBitmap)

/** Original, procedurally generated wallpapers (no third-party artwork, no licensing issues). */
object Wallpapers {
    val names = listOf("Aurora", "Sunset", "Meadow", "Midnight")

    private class Blob(val x: Float, val y: Float, val r: Float, val c: Long)
    private class Pal(val a: Long, val b: Long, val blobs: List<Blob>)

    private val pals = listOf(
        Pal(0xFF0B1B4D, 0xFF3B1E6E, listOf(Blob(.2f, .2f, .75f, 0xCC2D7BFF), Blob(.85f, .35f, .65f, 0xCCB14BFF), Blob(.3f, .85f, .8f, 0xCC00D2C8), Blob(.9f, .95f, .6f, 0xCCFF4F9A))),
        Pal(0xFF3A1C71, 0xFFD76D77, listOf(Blob(.2f, .15f, .75f, 0xCCFFAF7B), Blob(.9f, .5f, .65f, 0xCCFF5E8A), Blob(.3f, .9f, .8f, 0xCC7F5BFF))),
        Pal(0xFF0F3D3E, 0xFF2E7D5B, listOf(Blob(.8f, .15f, .75f, 0xCC9BE15D), Blob(.15f, .55f, .65f, 0xCC00B3A4), Blob(.7f, .95f, .8f, 0xCC2F80ED))),
        Pal(0xFF05060F, 0xFF1B1F4B, listOf(Blob(.5f, .1f, .75f, 0xCC3A3FBF), Blob(.1f, .7f, .65f, 0xCC6A2FB5), Blob(.9f, .85f, .65f, 0xCC1F6FEB))),
    )

    fun preset(id: Int, w: Int = 720, h: Int = 1560): Bitmap {
        val pal = pals[id.coerceIn(0, pals.lastIndex)]
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), pal.a.toInt(), pal.b.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        for (bl in pal.blobs) {
            val col = bl.c.toInt()
            p.shader = RadialGradient(bl.x * w, bl.y * h, bl.r * w, col, col and 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        return b
    }

    private fun customFile(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    /** Copies a user-picked image (via the system picker, no storage permission) into app-private storage, cropped to screen ratio. */
    fun saveCustom(ctx: Context, uri: Uri): Boolean = try {
        val dm = ctx.resources.displayMetrics
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > 2200 || opts.outHeight / sample > 3200) sample *= 2
        val src = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException()
        val ratio = dm.widthPixels.toFloat() / dm.heightPixels
        var cw = src.width; var ch = (cw / ratio).toInt()
        if (ch > src.height) { ch = src.height; cw = (ch * ratio).toInt() }
        val crop = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
        val outW = minOf(1080, crop.width)
        val scaled = Bitmap.createScaledBitmap(crop, outW, (outW / ratio).toInt(), true)
        customFile(ctx).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    } catch (e: Throwable) { false }

    suspend fun load(ctx: Context, id: String): WallpaperState = withContext(Dispatchers.Default) {
        val bmp = if (id == "custom") {
            try { BitmapFactory.decodeFile(customFile(ctx).absolutePath) } catch (e: Throwable) { null } ?: preset(0)
        } else preset(id.substringAfter(":").toIntOrNull() ?: 0)
        WallpaperState(bmp.asImageBitmap(), blur(bmp).asImageBitmap())
    }

    /** Cheap software frosted-glass source: downscale 10x then box-blur. Computed once per wallpaper. */
    fun blur(src: Bitmap): Bitmap {
        val w = (src.width / 10).coerceAtLeast(8)
        val h = (src.height / 10).coerceAtLeast(8)
        val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(px.size)
        repeat(3) { pass(px, tmp, w, h, 3, true); pass(tmp, px, w, h, 3, false) }
        small.setPixels(px, 0, w, 0, 0, w, h)
        return small
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val n = 2 * r + 1
        for (l in 0 until lines) for (i in 0 until len) {
            var a = 0; var rr = 0; var g = 0; var b = 0
            for (k in -r..r) {
                val j = (i + k).coerceIn(0, len - 1)
                val p = if (horizontal) src[l * w + j] else src[j * w + l]
                a += p ushr 24; rr += (p shr 16) and 255; g += (p shr 8) and 255; b += p and 255
            }
            val o = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
            if (horizontal) dst[l * w + i] = o else dst[i * w + l] = o
        }
    }
}
