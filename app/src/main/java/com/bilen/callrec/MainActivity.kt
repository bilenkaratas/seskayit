package com.bilen.callrec

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    private val permissions: Array<String>
        get() {
            val list = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS
            )
            if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
            return list.toTypedArray()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Arama Kaydedici"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        })
        status = TextView(this).apply { textSize = 15f; setPadding(0, pad / 2, 0, pad / 2) }
        root.addView(status)

        fun button(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
            })
        }

        button("1. İzinleri ver") { requestPermissions(permissions, 1) }
        button("2. Erişilebilirlik servisini aç") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("3. Bildirim erişimini aç (WhatsApp için)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        button("4. Pil kısıtlamasını kaldır") {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                openAppDetails()
            }
        }
        button("5. Xiaomi: Otomatik başlatmayı aç") {
            try {
                startActivity(Intent().setComponent(
                    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                ))
            } catch (e: Exception) {
                openAppDetails()
            }
        }
        button("Uygulama ayarları (kısıtlanmış ayarlar)") { openAppDetails() }

        root.addView(TextView(this).apply {
            text = "\nSes kaynağı (karşı taraf duyulmuyorsa değiştirip deneyin):"
            setTypeface(typeface, Typeface.BOLD)
        })
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "Ses tanıma (önerilen)",
            MediaRecorder.AudioSource.MIC to "Mikrofon",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "İletişim",
            MediaRecorder.AudioSource.VOICE_CALL to "Hat (yalnızca root/sistem)"
        )
        val group = RadioGroup(this)
        val current = Prefs.audioSource(this)
        sources.forEach { (src, label) ->
            group.addView(RadioButton(this).apply {
                id = 1000 + src
                text = label
                isChecked = src == current
            })
        }
        group.setOnCheckedChangeListener { _, checkedId -> Prefs.setAudioSource(this, checkedId - 1000) }
        root.addView(group)

        root.addView(TextView(this).apply {
            text = "\nKayıtlar: Dahili depolama › Recordings › CallRec\n\n" +
                "Örnek: 2026-10-15 15-39-37 (phone) Pasaliahmet +90 542 433 72 95 ↗.m4a\n" +
                "↙ gelen   ↗ giden\n\n" +
                "Not: Erişilebilirlik anahtarı gri ve tıklanamıyorsa önce 'Uygulama ayarları' › sağ üst ⋮ › " +
                "'Kısıtlanmış ayarlara izin ver' seçin."
            textSize = 13f
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        val perms = permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val acc = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains("$packageName/")
        val notif = getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(this, CallNotificationListener::class.java))
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

        fun mark(ok: Boolean) = if (ok) "✅" else "❌"
        status.text = buildString {
            appendLine("${mark(perms)} İzinler (mikrofon, telefon, rehber, arama kaydı)")
            appendLine("${mark(acc)} Erişilebilirlik servisi")
            appendLine("${mark(notif)} Bildirim erişimi (WhatsApp)")
            appendLine("${mark(battery)} Pil kısıtlaması kaldırıldı")
            if (perms && acc && notif) append("\nHazır. Aramalar otomatik kaydedilecek.")
        }
    }

    private fun openAppDetails() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
}
