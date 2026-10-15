package com.bilen.callrec

import android.content.Context
import android.media.MediaRecorder

object Prefs {
    private const val FILE = "callrec"
    private const val KEY_SOURCE = "audio_source"

    fun audioSource(ctx: Context): Int =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getInt(KEY_SOURCE, MediaRecorder.AudioSource.VOICE_RECOGNITION)

    fun setAudioSource(ctx: Context, source: Int) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SOURCE, source).apply()
    }
}
