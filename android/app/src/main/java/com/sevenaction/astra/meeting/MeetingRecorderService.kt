package com.sevenaction.astra.meeting

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.sevenaction.astra.MainActivity
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

class MeetingRecorderService : Service() {
    companion object {
        const val CHANNEL_ID = "astra_meeting"
        const val NOTIFICATION_ID = 7001
        const val SAMPLE_RATE = 16000
        const val SEGMENT_SECONDS = 600
    }

    @Volatile private var recording = false
    @Volatile private var paused = false
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var folder: File? = null
    private var segmentIndex = 0

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MeetingState.ACTION_STOP -> stopRecording()
            MeetingState.ACTION_PAUSE -> { paused = !paused; updateNotification() }
            MeetingState.ACTION_MARK -> mark()
            else -> if (!recording) startRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE * 2)

        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )
        folder = File(
            filesDir,
            "meetings/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
        ).apply { mkdirs() }

        getSharedPreferences(MeetingState.PREFS, MODE_PRIVATE).edit()
            .putBoolean(MeetingState.KEY_ACTIVE, true)
            .putLong(MeetingState.KEY_STARTED_AT, System.currentTimeMillis())
            .putString(MeetingState.KEY_FOLDER, folder!!.absolutePath)
            .apply()

        recording = true
        paused = false
        startForeground(NOTIFICATION_ID, buildNotification())
        recorder!!.startRecording()

        worker = thread(name = "astra-meeting-recorder") {
            val buffer = ByteArray(bufferSize)
            var out: BufferedOutputStream? = null
            var bytesInSegment = 0L
            val maxBytes = SAMPLE_RATE * 2L * SEGMENT_SECONDS
            try {
                while (recording) {
                    if (paused) {
                        Thread.sleep(100)
                        continue
                    }
                    if (out == null || bytesInSegment >= maxBytes) {
                        out?.close()
                        segmentIndex += 1
                        bytesInSegment = 0
                        val f = File(folder, "audio_%03d.pcm".format(segmentIndex))
                        out = BufferedOutputStream(FileOutputStream(f))
                    }
                    val n = recorder?.read(buffer, 0, buffer.size) ?: -1
                    if (n > 0) {
                        out.write(buffer, 0, n)
                        bytesInSegment += n
                    }
                }
            } finally {
                out?.close()
            }
        }
    }

    private fun stopRecording() {
        recording = false
        try { recorder?.stop() } catch (_: Exception) {}
        worker?.join(1500)
        recorder?.release()
        recorder = null
        getSharedPreferences(MeetingState.PREFS, MODE_PRIVATE).edit()
            .putBoolean(MeetingState.KEY_ACTIVE, false)
            .putBoolean(MeetingState.KEY_ANALYSIS_PENDING, true)
            .apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun mark() {
        val dir = folder ?: return
        File(dir, "markers.txt").appendText(System.currentTimeMillis().toString() + "\n")
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Réunions Astra", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Enregistrement de réunion en cours"
            }
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 1, Intent(this, MeetingRecorderService::class.java).setAction(MeetingState.ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val mark = PendingIntent.getService(this, 2, Intent(this, MeetingRecorderService::class.java).setAction(MeetingState.ACTION_MARK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 3, Intent(this, MeetingRecorderService::class.java).setAction(MeetingState.ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setContentTitle("Astra — réunion en cours")
            .setContentText(if (paused) "En pause" else "Enregistrement local actif")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, if (paused) "Reprendre" else "Pause", pause)
            .addAction(0, "Marquer", mark)
            .addAction(0, "Terminer", stop)
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        if (recording) stopRecording()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
