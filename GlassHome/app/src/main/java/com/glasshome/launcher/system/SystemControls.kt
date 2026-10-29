package com.glasshome.launcher.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

data class CtlState(
    val wifi: Boolean, val bluetooth: Boolean, val airplane: Boolean, val rotate: Boolean,
    val dnd: Boolean, val brightness: Float, val volume: Float,
    val battery: Int, val charging: Boolean, val canWrite: Boolean,
)

/** Only official APIs. Anything Android forbids us to toggle opens the proper system page instead. */
class SystemControls(context: Context) {
    private val ctx = context.applicationContext
    private val cr = ctx.contentResolver
    private val cam = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val torchOn = mutableStateOf(false)
    private val torchId: String? = try {
        cam.cameraIdList.firstOrNull { id ->
            val ch = cam.getCameraCharacteristics(id)
            ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    } catch (e: Throwable) { null }
    val hasTorch get() = torchId != null

    private val cb = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) torchOn.value = enabled
        }
    }
    fun startTorchWatch() { try { cam.registerTorchCallback(cb, Handler(Looper.getMainLooper())) } catch (e: Throwable) {} }
    fun stopTorchWatch() { try { cam.unregisterTorchCallback(cb) } catch (e: Throwable) {} }
    fun toggleTorch(): Boolean {
        val id = torchId ?: return false
        return try { cam.setTorchMode(id, !torchOn.value); true } catch (e: Throwable) { false }
    }

    fun snapshot(): CtlState {
        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = b?.getIntExtra("level", -1) ?: -1
        val scale = b?.getIntExtra("scale", 100) ?: 100
        val st = b?.getIntExtra("status", -1) ?: -1
        return CtlState(
            wifi = try { wifi.isWifiEnabled } catch (e: Throwable) { false },
            bluetooth = Settings.Global.getInt(cr, "bluetooth_on", 0) == 1,
            airplane = Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            rotate = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1,
            dnd = nm.currentInterruptionFilter.let {
                it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            },
            brightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f,
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1),
            battery = if (lvl >= 0) lvl * 100 / scale.coerceAtLeast(1) else -1,
            charging = st == 2 || st == 5,
            canWrite = Settings.System.canWrite(ctx),
        )
    }

    fun canWrite() = Settings.System.canWrite(ctx)

    fun setBrightness(f: Float) {
        try {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (f * 255).roundToInt().coerceIn(1, 255))
        } catch (e: Throwable) {}
    }
    fun setAutoRotate(on: Boolean) {
        try { Settings.System.putInt(cr, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0) } catch (e: Throwable) {}
    }
    fun setVolume(f: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (f * max).roundToInt(), 0) } catch (e: Throwable) {}
    }
}

/** Tries each intent in order; returns true if one opened. */
fun openFirst(ctx: Context, vararg intents: Intent): Boolean {
    for (i in intents) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true } catch (e: Throwable) {}
    }
    Toast.makeText(ctx, "Could not open this settings page on your ROM", Toast.LENGTH_SHORT).show()
    return false
}

fun openHomeSettings(ctx: Context) = openFirst(
    ctx,
    Intent(Settings.ACTION_HOME_SETTINGS),
    Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
    Intent(Settings.ACTION_SETTINGS),
)

@Composable
fun rememberBattery(): State<Pair<Int, Boolean>> {
    val ctx = LocalContext.current
    fun parse(i: Intent?): Pair<Int, Boolean> {
        val l = i?.getIntExtra("level", -1) ?: -1
        val s = i?.getIntExtra("scale", 100) ?: 100
        val st = i?.getIntExtra("status", -1) ?: -1
        return Pair(if (l >= 0) l * 100 / s.coerceAtLeast(1) else -1, st == 2 || st == 5)
    }
    val state = remember { mutableStateOf(parse(ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { state.value = parse(i) }
        }
        ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { try { ctx.unregisterReceiver(r) } catch (e: Throwable) {} }
    }
    return state
}
