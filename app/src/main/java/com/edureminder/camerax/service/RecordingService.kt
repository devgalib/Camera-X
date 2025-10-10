package com.edureminder.camerax.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class RecordingService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "bg_camera_channel"
        const val NOTIF_ID = 101
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundAndRecord()
            ACTION_STOP -> stopRecordingAndStopService()
        }

        // ✅ Always call super at the end
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startForegroundAndRecord() {
        val notif = buildNotification("Recording…")
        startForeground(NOTIF_ID, notif)

        acquireWakeLock()
        startCameraAndRecord()
    }

    private fun stopRecordingAndStopService() {
        serviceScope.launch {
            stopActiveRecording()
            releaseWakeLock()
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        serviceScope.cancel()
    }

    // ---------------------------
    // Helper Methods
    // ---------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Camera",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BG Camera")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bgcamera:recording")
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes, adjust as needed
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun startCameraAndRecord() {
        serviceScope.launch {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(this@RecordingService).get()
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                val preview = Preview.Builder().build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this@RecordingService,
                    cameraSelector,
                    preview,
                    videoCapture
                )

                val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(System.currentTimeMillis())
                val outputFile = File(
                    externalMediaDirs.first(),
                    "$name.mp4"
                )

                val outputOptions = FileOutputOptions.Builder(outputFile).build()

                // ✅ Check permission before enabling audio
                val hasAudioPermission = ContextCompat.checkSelfPermission(
                    this@RecordingService,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                val pendingRecording = videoCapture!!.output
                    .prepareRecording(this@RecordingService, outputOptions)
                    .apply {
                        if (hasAudioPermission) withAudioEnabled()
                    }

                activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(this@RecordingService)) {
                    // Optional: listen to recording events
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


//    private fun startRecordingToFile() {
//        val videoCaptureLocal = videoCapture ?: return
//
//        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date()) + ".mp4"
//        val file = File(getExternalFilesDir(null), name)
//
//        val outputOptions = FileOutputOptions.Builder(file).build()
//
//        serviceScope.launch {
//            try {
//                activeRecording = videoCaptureLocal.output
//                    .prepareRecording(this@RecordingService, outputOptions)
//                    .apply { withAudioEnabled() }
//                    .start(ContextCompat.getMainExecutor(this@RecordingService)) { event ->
//                        when (event) {
//                            is VideoRecordEvent.Start -> {
//                                // Optional: update notification or log
//                            }
//                            is VideoRecordEvent.Finalize -> {
//                                // Optional: handle completion
//                            }
//                        }
//                    }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//    }

    private suspend fun stopActiveRecording() {
        activeRecording?.stop()
        activeRecording = null
    }
}
