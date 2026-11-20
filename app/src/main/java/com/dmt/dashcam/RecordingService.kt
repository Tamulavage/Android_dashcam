package com.dmt.dashcam

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale

class RecordingService : LifecycleService() {

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    private val binder = LocalBinder()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive.asStateFlow()

    private val _selectedBackCameraIndex = MutableStateFlow(0)
    val selectedBackCameraIndex: StateFlow<Int> = _selectedBackCameraIndex.asStateFlow()

    private val _backCameraCount = MutableStateFlow(0)
    val backCameraCount: StateFlow<Int> = _backCameraCount.asStateFlow()

    private var currentSurfaceProvider: Preview.SurfaceProvider? = null
    private var lastRecordAudio: Boolean = false
    private var pendingRestartRecording: Boolean = false

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val recordAudio = intent.getBooleanExtra(EXTRA_RECORD_AUDIO, false)
                startRecording(recordAudio)
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
        }
        return START_STICKY
    }

    fun startCamera(surfaceProvider: Preview.SurfaceProvider? = null) {
        currentSurfaceProvider = surfaceProvider
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            bindCameraUseCases()
            _isCameraActive.value = true
        }, ContextCompat.getMainExecutor(this))
    }

    fun stopCamera() {
        pendingRestartRecording = false
        if (_isRecording.value) {
            stopRecording()
        }
        cameraProvider?.unbindAll()
        _isCameraActive.value = false
        currentSurfaceProvider = null
        stopForegroundAndSelfIfNeeded()
    }

    fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider?) {
        currentSurfaceProvider = surfaceProvider
        preview?.setSurfaceProvider(surfaceProvider)
    }

    fun switchCamera() {
        val count = _backCameraCount.value
        if (count > 1) {
            val wasRecording = _isRecording.value || activeRecording != null || pendingRestartRecording
            if (wasRecording) {
                pendingRestartRecording = true
                stopRecording()
            }
            _selectedBackCameraIndex.value = (_selectedBackCameraIndex.value + 1) % count
            bindCameraUseCases()
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return

        val backCameras = provider.availableCameraInfos.filter {
            it.lensFacing == CameraSelector.LENS_FACING_BACK
        }
        _backCameraCount.value = backCameras.size

        val cameraSelector = if (backCameras.isNotEmpty()) {
            val targetInfo = backCameras[_selectedBackCameraIndex.value % backCameras.size]
            CameraSelector.Builder()
                .addCameraFilter { cameraInfos -> cameraInfos.filter { it == targetInfo } }
                .build()
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        preview = Preview.Builder().build().also {
            it.setSurfaceProvider(currentSurfaceProvider)
        }

        if (videoCapture == null) {
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
        }

        try {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                videoCapture
            )

            val camera2Info = Camera2CameraInfo.from(camera.cameraInfo)
            val afModes = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            val optionsBuilder = CaptureRequestOptions.Builder()
            if (afModes.contains(CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
                optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
                optionsBuilder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f)
            } else if (afModes.contains(CameraCharacteristics.CONTROL_AF_MODE_EDOF)) {
                optionsBuilder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraCharacteristics.CONTROL_AF_MODE_EDOF)
            }
            val camera2Control = Camera2CameraControl.from(camera.cameraControl)
            camera2Control.setCaptureRequestOptions(optionsBuilder.build())
        } catch (_: Exception) {
        }
    }

    fun startRecording(recordAudio: Boolean) {
        val capture = videoCapture ?: return
        if (_isRecording.value || activeRecording != null) return

        lastRecordAudio = recordAudio

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Dashcam")
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val prepareRecording = capture.output.prepareRecording(this, outputOptions)
        val pendingRecording = if (recordAudio && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            prepareRecording.withAudioEnabled()
        } else {
            prepareRecording
        }

        // Promote to foreground service before starting recording
        startForegroundNotification(recordAudio)

        try {
            activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        _isRecording.value = true
                        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
                    }
                    is VideoRecordEvent.Finalize -> {
                        _isRecording.value = false
                        activeRecording?.close()
                        activeRecording = null
                        val isRestartPending = pendingRestartRecording
                        val hasError = recordEvent.hasError()
                        val isInactiveFromSwitch = isRestartPending && recordEvent.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE

                        if (!hasError || isInactiveFromSwitch) {
                            Toast.makeText(this, "Video saved to Movies/Dashcam", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Video capture failed: ${recordEvent.error}", Toast.LENGTH_SHORT).show()
                        }

                        if (isRestartPending) {
                            pendingRestartRecording = false
                            startRecording(lastRecordAudio)
                        } else {
                            stopForegroundAndSelfIfNeeded()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _isRecording.value = false
            pendingRestartRecording = false
            stopForegroundAndSelfIfNeeded()
            Toast.makeText(this, "Failed to start recording: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording() {
        pendingRestartRecording = false
        val recording = activeRecording
        if (recording != null) {
            recording.stop()
            activeRecording = null
        }
        _isRecording.value = false
    }

    private fun startForegroundNotification(recordAudio: Boolean) {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dashcam Recording")
            .setContentText("Recording video in background...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(0, "Stop Recording", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        var foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (recordAudio && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            foregroundType = foregroundType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }

        startForeground(NOTIFICATION_ID, notification, foregroundType)
    }

    private fun stopForegroundAndSelfIfNeeded() {
        if (!_isRecording.value) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (!_isCameraActive.value) {
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dashcam Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification shown while dashcam is recording video"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "dashcam_recording_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_RECORDING = "com.dmt.dashcam.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.dmt.dashcam.action.STOP_RECORDING"
        const val EXTRA_RECORD_AUDIO = "com.dmt.dashcam.extra.RECORD_AUDIO"
    }
}
