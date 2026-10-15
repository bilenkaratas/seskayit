package com.bilen.callrec

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.Locale

/**
 * WhatsApp (ve benzeri) uygulamaların arama bildirimlerinden aramanın
 * başladığını/bittiğini, kiminle olduğunu ve yönünü anlar.
 */
class CallNotificationListener : NotificationListenerService() {

    companion object {
        /** paket adı → dosya adında görünecek etiket */
        val APPS = mapOf(
            "com.whatsapp" to "whatsapp",
            "com.whatsapp.w4b" to "whatsapp business",
            "org.telegram.messenger" to "telegram",
            "org.thoughtcrime.securesms" to "signal",
            "com.viber.voip" to "viber"
        )
        private val INCOMING_WORDS = listOf("gelen", "incoming")
        private val APP_TITLES = setOf("whatsapp", "whatsapp business", "telegram", "signal", "viber")
        private const val CALL_TYPE_INCOMING = 1   // Notification.CallStyle (Android 12+)
        private const val CALL_TYPE_ONGOING = 2
    }

    private class CallState {
        var incoming = false
        var title: String? = null
        var recording = false
        val keys = mutableSetOf<String>()
        var pendingEnd: Runnable? = null
    }

    private val states = mutableMapOf<String, CallState>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val label = APPS[sbn.packageName] ?: return
        val n = sbn.notification ?: return
        if (!isCallNotification(n)) return

        val st = states.getOrPut(sbn.packageName) { CallState() }
        st.pendingEnd?.let { handler.removeCallbacks(it) }
        st.pendingEnd = null
        st.keys.add(sbn.key)

        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        if (!title.isNullOrBlank() && title.lowercase(Locale.ROOT) !in APP_TITLES) st.title = title

        val callType = extras.getInt("android.callType", 0)
        val lower = "${title.orEmpty()} ${text.orEmpty()}".lowercase(Locale("tr"))
        val ringingIncoming = when (callType) {
            CALL_TYPE_INCOMING -> true
            CALL_TYPE_ONGOING -> false
            else -> n.fullScreenIntent != null || INCOMING_WORDS.any { lower.contains(it) }
        }
        val ongoing = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0 || callType == CALL_TYPE_ONGOING

        if (ringingIncoming) {
            st.incoming = true      // çalıyor; açılınca kayıt başlar
            return
        }
        if (ongoing && !st.recording && !RecorderService.isRecording) {
            st.recording = true
            val (name, number) = resolve(st.title)
            RecorderService.start(this, label, name, number, st.incoming)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val label = APPS[sbn.packageName] ?: return
        val st = states[sbn.packageName] ?: return
        st.keys.remove(sbn.key)
        if (st.keys.isNotEmpty()) return

        // Gelen arama bildirimi kapanıp "devam eden arama" bildirimi gelebilir; kısa bekle.
        val end = Runnable {
            if (st.keys.isEmpty()) {
                if (st.recording) RecorderService.stop(this, label, resolveFromCallLog = false)
                states.remove(sbn.packageName)
            }
        }
        st.pendingEnd = end
        handler.postDelayed(end, 2500)
    }

    private fun isCallNotification(n: Notification): Boolean {
        if (n.category == Notification.CATEGORY_CALL) return true
        val ch = n.channelId?.lowercase(Locale.ROOT) ?: return false
        return ch.contains("call") || ch.contains("voip")
    }

    /** Bildirim başlığı isim ya da numara olabilir; eksik olanı rehberden tamamla. */
    private fun resolve(title: String?): Pair<String?, String?> {
        if (title.isNullOrBlank()) return null to null
        return if (Naming.looksLikeNumber(title)) {
            ContactsHelper.nameForNumber(this, title) to title
        } else {
            title to ContactsHelper.numberForName(this, title)
        }
    }
}
