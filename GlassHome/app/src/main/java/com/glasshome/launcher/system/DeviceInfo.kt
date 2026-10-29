package com.glasshome.launcher.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Runtime facts about the phone we are actually running on (nothing assumed). */
fun deviceFacts(ctx: Context): List<Pair<String, String>> {
    val dm = ctx.resources.displayMetrics
    @Suppress("DEPRECATION")
    val hz = try { (ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.refreshRate } catch (e: Throwable) { 0f }
    fun prop(n: String): String = try {
        Runtime.getRuntime().exec(arrayOf("getprop", n)).inputStream.bufferedReader().readText().trim()
    } catch (e: Throwable) { "" }
    val pm = ctx.packageManager
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val def = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    val homes = pm.queryIntentActivities(homeIntent, 0).joinToString { it.loadLabel(pm).toString() + " (" + it.activityInfo.packageName + ")" }
    return listOf(
        "Manufacturer / model" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "Device codename" to "${Build.DEVICE}   (POCO X3 GT is expected to be \"chopin\")",
        "Android" to "${Build.VERSION.RELEASE}  (API ${Build.VERSION.SDK_INT})",
        "Security patch" to (Build.VERSION.SECURITY_PATCH ?: ""),
        "MIUI version" to prop("ro.miui.ui.version.name").ifBlank { "not detected" },
        "MIUI build" to prop("ro.build.version.incremental").ifBlank { "not detected" },
        "ABIs" to Build.SUPPORTED_ABIS.joinToString(),
        "Screen" to "${dm.widthPixels} x ${dm.heightPixels} px  •  ${dm.densityDpi} dpi  •  ${"%.0f".format(hz)} Hz",
        "Screen (dp)" to "${(dm.widthPixels / dm.density).toInt()} x ${(dm.heightPixels / dm.density).toInt()} dp",
        "Current default Home" to (def ?: "none"),
        "Installed Home apps" to homes,
        "This app is default Home" to (def == ctx.packageName).toString(),
    )
}

fun defaultHomePackage(ctx: Context): String? {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
}
