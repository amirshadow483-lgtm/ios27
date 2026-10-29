package com.glasshome.launcher.system

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow

data class NotifItem(val key: String, val pkg: String, val app: String, val title: String, val text: String, val time: Long)

/** In-memory only. Never written to disk, never sent anywhere (the app has no INTERNET permission). */
object NotificationStore {
    val items = MutableStateFlow<List<NotifItem>>(emptyList())
}

fun isNotificationAccessGranted(ctx: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

class GlassNotificationListener : NotificationListenerService() {
    companion object { @Volatile var instance: GlassNotificationListener? = null }

    override fun onListenerConnected() { instance = this; refresh() }
    override fun onListenerDisconnected() { instance = null; NotificationStore.items.value = emptyList() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refresh() }

    private fun refresh() {
        try {
            val pm = packageManager
            NotificationStore.items.value = (activeNotifications ?: emptyArray())
                .filter { !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 && it.packageName != packageName }
                .map { n ->
                    val ex = n.notification.extras
                    val label = try { pm.getApplicationLabel(pm.getApplicationInfo(n.packageName, 0)).toString() } catch (e: Throwable) { n.packageName }
                    NotifItem(n.key, n.packageName, label,
                        ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
                        ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "", n.postTime)
                }.sortedByDescending { it.time }
        } catch (e: Throwable) { /* ignore */ }
    }

    fun dismiss(key: String) { try { cancelNotification(key) } catch (e: Throwable) {} }
}
