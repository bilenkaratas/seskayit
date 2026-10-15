package com.bilen.callrec

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.TelephonyManager
import android.view.accessibility.AccessibilityEvent

/**
 * Android 10+ arama sırasında mikrofona yalnızca erişilebilirlik servisi açık
 * uygulamaların erişmesine izin veriyor. Bu servis o izni sağlar ve normal
 * GSM aramalarını takip eder. Ekran içeriği okunmaz.
 */
class CallAccessibilityService : AccessibilityService() {

    private var receiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val r = PhoneStateReceiver()
        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(r, filter)
        receiver = r
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        receiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        super.onDestroy()
    }
}

class PhoneStateReceiver : BroadcastReceiver() {
    companion object {
        private var wasRinging = false
        private var incomingNumber: String? = null
        private var active = false
    }

    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!active) {
                    wasRinging = true
                    if (!number.isNullOrBlank()) incomingNumber = number
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (!active) {
                    active = true
                    // Numara ve yön, arama bitince arama kaydından kesinleştirilir.
                    RecorderService.start(ctx, "phone", null, incomingNumber, wasRinging)
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (active) RecorderService.stop(ctx, "phone", resolveFromCallLog = true)
                active = false
                wasRinging = false
                incomingNumber = null
            }
        }
    }
}
