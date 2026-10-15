package com.bilen.callrec

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.CallLog
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.util.concurrent.Executors

/**
 * Mikrofondan m4a (AAC) kayıt alır, arama bitince dosyayı doğru isimle
 * Dahili depolama/Recordings/CallRec klasörüne kaydeder.
 */
class RecorderService : Service() {

    companion object {
        private const val TAG = "CallRec"
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_DIR = "dir"          // 1 gelen, 0 giden, -1 bilinmiyor
        private const val EXTRA_CALLLOG = "calllog"  // bitince arama kaydından numara/yön oku
        private const val NOTIF_CHANNEL = "recording"
        private const val NOTIF_ID = 42

        @Volatile var isRecording = false
            private set
        @Volatile var currentChannel: String? = null
            private set

        fun start(ctx: Context, channel: String, name: String?, number: String?, incoming: Boolean?) {
            val i = Intent(ctx, RecorderService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CHANNEL, channel)
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_NUMBER, number)
                putExtra(EXTRA_DIR, when (incoming) { true -> 1; false -> 0; null -> -1 })
            }
            try {
                ctx.startForegroundService(i)
            } catch (e: Exception) {
                Log.e(TAG, "Servis başlatılamadı", e)
            }
        }

        fun stop(ctx: Context, channel: String, resolveFromCallLog: Boolean) {
            val i = Intent(ctx, RecorderService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_CHANNEL, channel)
                putExtra(EXTRA_CALLLOG, resolveFromCallLog)
            }
            try {
                ctx.startService(i)
            } catch (e: Exception) {
                Log.e(TAG, "Durdurma gönderilemedi", e)
            }
        }
    }

    private val worker = Executors.newSingleThreadExecutor()
    private var recorder: MediaRecorder? = null
    private var tmpFile: File? = null
    private var startMs = 0L
    private var channel = ""
    private var name: String? = null
    private var number: String? = null
    private var incoming: Boolean? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop(intent)
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val ch = intent.getStringExtra(EXTRA_CHANNEL) ?: "phone"
        startForeground(NOTIF_ID, buildNotification(ch), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (isRecording) return

        channel = ch
        name = intent.getStringExtra(EXTRA_NAME)
        number = intent.getStringExtra(EXTRA_NUMBER)
        incoming = when (intent.getIntExtra(EXTRA_DIR, -1)) { 1 -> true; 0 -> false; else -> null }
        startMs = System.currentTimeMillis()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Mikrofon izni yok")
            stopSelfClean()
            return
        }

        val file = File(cacheDir, "rec_$startMs.m4a")
        val sources = linkedSetOf(
            Prefs.audioSource(this),
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (src in sources) {
            val r = tryStart(src, file)
            if (r != null) {
                recorder = r
                tmpFile = file
                isRecording = true
                currentChannel = channel
                Log.i(TAG, "Kayıt başladı: $channel kaynak=$src")
                return
            }
        }
        Log.e(TAG, "Hiçbir ses kaynağı açılamadı")
        stopSelfClean()
    }

    @Suppress("DEPRECATION")
    private fun tryStart(source: Int, file: File): MediaRecorder? {
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        return try {
            r.setAudioSource(source)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(96000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            r
        } catch (e: Exception) {
            Log.w(TAG, "Kaynak $source açılamadı: ${e.message}")
            try { r.release() } catch (_: Exception) {}
            null
        }
    }

    private fun handleStop(intent: Intent) {
        val ch = intent.getStringExtra(EXTRA_CHANNEL)
        if (!isRecording) {
            stopSelfClean()
            return
        }
        if (ch != null && ch != channel) return  // başka bir kanalın bitişi, bu kaydı etkilemez

        val useCallLog = intent.getBooleanExtra(EXTRA_CALLLOG, false)
        val endMs = System.currentTimeMillis()
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop hatası (çok kısa kayıt olabilir)", e)
        }
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        isRecording = false
        currentChannel = null

        val file = tmpFile
        tmpFile = null
        val snapshot = Finalize(file, startMs, channel, name, number, incoming, useCallLog, endMs - startMs)
        worker.execute {
            try {
                finalizeRecording(snapshot)
            } catch (e: Exception) {
                Log.e(TAG, "Kayıt kaydedilemedi", e)
            } finally {
                stopSelfClean()
            }
        }
    }

    private data class Finalize(
        val file: File?, val startMs: Long, val channel: String,
        var name: String?, var number: String?, var incoming: Boolean?,
        val useCallLog: Boolean, val durationMs: Long
    )

    private fun finalizeRecording(f: Finalize) {
        val file = f.file ?: return
        if (!file.exists() || file.length() < 1024 || f.durationMs < 1500) {
            file.delete()
            return
        }

        if (f.useCallLog) {
            Thread.sleep(2000) // arama kaydının yazılmasını bekle
            readCallLog(f.startMs)?.let { (num, inc, cachedName) ->
                if (!num.isNullOrBlank()) f.number = num
                if (inc != null) f.incoming = inc
                if (f.name.isNullOrBlank()) f.name = cachedName
            }
        }

        val prettyNumber = Naming.formatNumber(this, f.number)
        val contactName = f.name?.takeIf { it.isNotBlank() && !Naming.looksLikeNumber(it) }
            ?: ContactsHelper.nameForNumber(this, f.number)

        val displayName = Naming.fileName(f.startMs, f.channel, contactName, prettyNumber, f.incoming)
        saveToRecordings(file, displayName)
        file.delete()
        Log.i(TAG, "Kaydedildi: $displayName")
    }

    /** Son aramanın numarası, yönü ve rehber ismi. */
    private fun readCallLog(startMs: Long): Triple<String?, Boolean?, String?>? {
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf((startMs - 120_000).toString()),
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                if (!c.moveToFirst()) return null
                val num = c.getString(0)
                val inc = when (c.getInt(1)) {
                    CallLog.Calls.INCOMING_TYPE -> true
                    CallLog.Calls.OUTGOING_TYPE -> false
                    else -> null
                }
                Triple(num, inc, c.getString(2))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveToRecordings(src: File, displayName: String) {
        val relPath = if (Build.VERSION.SDK_INT >= 31)
            Environment.DIRECTORY_RECORDINGS + "/CallRec"
        else
            Environment.DIRECTORY_MUSIC + "/CallRec"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = contentResolver.insert(collection, values) ?: throw IllegalStateException("insert başarısız")
        contentResolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
    }

    private fun stopSelfClean() {
        if (isRecording) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(ch: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(NOTIF_CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(NOTIF_CHANNEL, "Kayıt", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, NOTIF_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Arama kaydediliyor")
            .setContentText(ch)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        isRecording = false
        currentChannel = null
        super.onDestroy()
    }
}
