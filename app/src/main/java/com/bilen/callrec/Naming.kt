package com.bilen.callrec

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dosya adı:  2026-10-15 15-39-37 (phone) Pasaliahmet +90 542 433 72 95 ↗.m4a
 * Rehberde yoksa isim kısmı atlanır:  2026-10-15 15-39-37 (phone) +90 533 131 04 29 ↙.m4a
 * ↙ gelen arama, ↗ giden arama.
 */
object Naming {
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US)

    fun fileName(
        startMs: Long,
        channel: String,
        name: String?,
        number: String?,
        incoming: Boolean?
    ): String {
        val sb = StringBuilder()
        sb.append(timeFormat.format(Date(startMs)))
        sb.append(" (").append(channel).append(")")
        val n = name?.trim().orEmpty()
        val num = number?.trim().orEmpty()
        if (n.isNotEmpty()) sb.append(' ').append(n)
        if (num.isNotEmpty() && num != n) sb.append(' ').append(num)
        if (n.isEmpty() && num.isEmpty()) sb.append(" Gizli numara")
        if (incoming != null) sb.append(' ').append(if (incoming) "↙" else "↗")
        return sanitize(sb.toString()) + ".m4a"
    }

    /** Dosya adında yasak olan karakterleri temizler. */
    fun sanitize(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_").take(200)

    /** Numarayı "+90 533 131 04 29" biçimine getirir. Diğer ülkeler uluslararası biçimde yazılır. */
    fun formatNumber(ctx: Context, raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val iso = countryIso(ctx)
        val e164 = try {
            PhoneNumberUtils.formatNumberToE164(trimmed, iso)
        } catch (e: Exception) {
            null
        } ?: return trimmed

        val digits = e164.drop(1)
        if (digits.startsWith("90") && digits.length == 12) {
            val n = digits.drop(2)
            return "+90 ${n.substring(0, 3)} ${n.substring(3, 6)} ${n.substring(6, 8)} ${n.substring(8, 10)}"
        }
        // Yabancı numaralar: ülke koduna göre uluslararası biçim
        return try {
            PhoneNumberUtils.formatNumber(e164, "ZZ") ?: e164
        } catch (e: Exception) {
            e164
        }
    }

    private fun countryIso(ctx: Context): String {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        val iso = tm?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: tm?.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: "tr"
        return iso.uppercase(Locale.US)
    }

    fun looksLikeNumber(s: String): Boolean =
        Regex("^[+0-9 ()\\-‪-‮]{6,}$").matches(s.trim()) && s.count { it.isDigit() } >= 6
}
