package com.example.adaptivewakeworddetectionapplication

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class WordDetectionService : Service() {

    private var energyThreshold = 0.03f
    private var confidenceThreshold = 0.8f
    private var smallThresh = 40.0f
    private var mediumThresh = 80.0f
    private var audioBufferSize = 0
    private var audioRecord: AudioRecord? = null
    private var audioRecordingThread: Thread? = null
    private var isRecording: Boolean = false
    private var notificationBuilder: NotificationCompat.Builder? = null

    private lateinit var modelController: ModelController

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val initialNotification = createNotification()
        startForeground(NOTIFICATION_ID, initialNotification)

        audioBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AUDIO_CHANNELS, AUDIO_ENCODING)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        audioRecord = AudioRecord(AUDIO_INPUT, SAMPLE_RATE, AUDIO_CHANNELS, AUDIO_ENCODING, audioBufferSize)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                R.string.channel_id.toString(),
                R.string.notification_channel_name.toString(),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager: NotificationManager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val builder = NotificationCompat.Builder(this, R.string.channel_id.toString())
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.baseline_mic_24)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)

        val resultIntent = Intent(this, MainActivity::class.java)
        resultIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.setContentIntent(resultPendingIntent)

        notificationBuilder = builder

        return builder.build()
    }

    fun updateNotification(detection: DetectionResult) {
        if (notificationBuilder == null) {
            return
        } else {
            notificationBuilder?.setContentText(getText(R.string.new_detection).toString() + " " + detection.topClassLabels[0])
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder?.build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val action = it.getStringExtra("ACTION")
            energyThreshold = it.getFloatExtra("ENERGY_THRESHOLD", 0.01f)
            confidenceThreshold = it.getFloatExtra("MIN_CONFIDENCE_THRESHOLD", 80.0f) / 100.0f
            smallThresh = it.getFloatExtra("SMALL_TO_MEDIUM_THRESHOLD", 40.0f)
            mediumThresh = it.getFloatExtra("MEDIUM_TO_LARGE_THRESHOLD", 80.0f)

            modelController = ModelController(this, smallThresh, mediumThresh)

            when (action) {
                "START" -> startRecording()
                "STOP" -> stopRecording()
                else -> Log.e(TAG, "Unknown action: $action")
            }
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Audio recording permission not granted")
            return
        }

        isRecording = true
        audioRecordingThread = Thread(
            Runnable {
                run {
                    record()
                }
            }
        )
        audioRecordingThread?.start()
    }

    private fun stopRecording() {
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                    isRecording = false
                }
                it.release()
                audioRecord = null
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecordingThread?.interrupt()
        stopForeground(true)
        stopSelf()
    }


    private fun record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Audio Record can't initialize!")
            return
        }

        audioRecord?.startRecording()

        val recordingBuffer = DoubleArray(RECORDING_LENGTH)
        val audioBuffer = ShortArray(audioBufferSize)
        val tempBufferSize = SAMPLE_RATE
        val tempBuffer = DoubleArray(tempBufferSize)
        var totalSamplesRead = 0
        var tempBufferHasData = false

        var lastClassificationTime = System.currentTimeMillis() - 3000


        while (isRecording) {

            if (tempBufferHasData) {
                for (i in tempBuffer.indices) {
                    recordingBuffer[i] = tempBuffer[i]
                }
                totalSamplesRead = tempBufferSize
                tempBufferHasData = false
            }
            val readDataSize = if ((RECORDING_LENGTH - totalSamplesRead) < audioBuffer.size) RECORDING_LENGTH - totalSamplesRead else audioBuffer.size
            val read = audioRecord?.read(audioBuffer, 0, readDataSize) ?: 0
            if (read > 0) {
                for (i in 0 until read) {
                    recordingBuffer[totalSamplesRead] = audioBuffer[i].toDouble() / Short.MAX_VALUE
                    totalSamplesRead++
                }
                if (totalSamplesRead == RECORDING_LENGTH) {
                    totalSamplesRead = 0
                    val currentTime = System.currentTimeMillis()
                    val nrg = calculateEnergy(recordingBuffer)
                    Log.d(TAG, "$nrg")
                    if ((nrg > energyThreshold) && currentTime - lastClassificationTime > 3000) {

                        val detectionResult = modelController.processShiftedWindows(recordingBuffer)

                        if (detectionResult != null && detectionResult.confidences[0] > confidenceThreshold) {
                            modelController.updateLastKperformances(detectionResult.confidences[0])
                            lastClassificationTime = currentTime
                            sendDetectionResult(detectionResult)
                        }
                        else
                            modelController.updateLastKperformances(0.0f)
                    }
                    for (i in 0 until tempBufferSize) {
                        tempBuffer[i] = recordingBuffer[RECORDING_LENGTH - tempBufferSize + i]
                    }
                    tempBufferHasData = true
                    recordingBuffer.fill(0.0)
                }
            } else {
                Log.e(TAG, "AudioRecord read error: $read")
                break
            }
        }
        stopRecording()
    }

    private fun calculateEnergy(audioData: DoubleArray): Double {
        var sum = 0.0
        for (sample in audioData) {
            sum += sample * sample
        }

        return sum / audioData.size
    }

    private fun sendDetectionResult(result: DetectionResult) {
        updateNotification(result)
        val intent = Intent(BROADCAST_ACTION).apply {
            putExtra(EXTRA_WAKE_WORD, result)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    companion object {
        const val BROADCAST_ACTION = "com.example.adaptivewakeworddetectionapplication.DETECTION_RESULT"
        const val EXTRA_WAKE_WORD = "EXTRA_WAKE_WORD"

        private const val SAMPLE_RATE = 16000
        private const val AUDIO_CHANNELS = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_INPUT = MediaRecorder.AudioSource.MIC
        private const val DESIRED_LENGTH_SECONDS = 2
        private const val RECORDING_LENGTH = SAMPLE_RATE * DESIRED_LENGTH_SECONDS

        private const val NOTIFICATION_ID = 1
        private val TAG = WordDetectionService::class.simpleName
    }
}