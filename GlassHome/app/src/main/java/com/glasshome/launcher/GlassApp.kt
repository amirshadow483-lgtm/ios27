package com.glasshome.launcher

import android.app.Application

/** Records crashes (locally only) so the next launch can show the Recovery screen first. */
class GlassApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val sp = getSharedPreferences("glass_crash", MODE_PRIVATE)
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                sp.edit().putLong("last", System.currentTimeMillis())
                    .putInt("count", sp.getInt("count", 0) + 1).commit()
            } catch (x: Throwable) { /* ignore */ }
            old?.uncaughtException(t, e)
        }
    }
}
