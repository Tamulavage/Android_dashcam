package com.dmt.dashcam

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dmt.dashcam.ui.theme.DashcamTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DashcamTheme {
                DashcamApp()
            }
        }
    }
}

@Composable
fun DashcamApp() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        CameraScreen(modifier = Modifier.padding(innerPadding))
    }
}

@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("dashcam_prefs", Context.MODE_PRIVATE)
    }
    var isAudioEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("record_audio", false))
    }

    var recordingService by remember { mutableStateOf<RecordingService?>(null) }
    var isBound by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val intent = Intent(context, RecordingService::class.java)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? RecordingService.LocalBinder
                recordingService = binder?.getService()
                isBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                recordingService = null
                isBound = false
            }
        }
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            if (isBound) {
                context.unbindService(connection)
            }
        }
    }

    val isRecording by recordingService?.isRecording?.collectAsState() ?: remember { mutableStateOf(false) }
    val isCameraActive by recordingService?.isCameraActive?.collectAsState() ?: remember { mutableStateOf(false) }
    val backCameraCount by recordingService?.backCameraCount?.collectAsState() ?: remember { mutableStateOf(0) }
    val selectedBackCameraIndex by recordingService?.selectedBackCameraIndex?.collectAsState() ?: remember { mutableStateOf(0) }

    val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS
    )

    var hasPermissions by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = requiredPermissions.all { permissions[it] == true }
    }

    DisposableEffect(isCameraActive) {
        val window = (context as? Activity)?.window
        if (isCameraActive) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isCameraActive && hasPermissions) {
            CameraPreview(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                recordingService = recordingService
            )
        } else {
            Greeting(
                name = if (hasPermissions) "Dashcam" else "Permissions Required",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "Record Sound (${if (isAudioEnabled) "ON" else "OFF"})")
                    Switch(
                        checked = isAudioEnabled,
                        onCheckedChange = { newValue ->
                            isAudioEnabled = newValue
                            sharedPreferences.edit().putBoolean("record_audio", newValue).apply()
                        }
                    )
                }
            } else {
                Text(
                    text = if (isAudioEnabled) "Sound Recording: ON" else "Sound Recording: OFF",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (isCameraActive && hasPermissions) {
                IconButton(
                    onClick = {
                        if (backCameraCount > 1) {
                            recordingService?.switchCamera()
                            Toast.makeText(
                                context,
                                "Switched to Lens ${selectedBackCameraIndex + 1} of $backCameraCount",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (backCameraCount == 1) {
                            Toast.makeText(
                                context,
                                "Only 1 back lens available",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "No back lens found",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    LensIcon(tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (hasPermissions) {
                        val service = recordingService
                        if (service != null) {
                            if (isCameraActive) {
                                service.stopCamera()
                            } else {
                                val serviceIntent = Intent(context, RecordingService::class.java)
                                ContextCompat.startForegroundService(context, serviceIntent)
                                service.startCamera()
                            }
                        }
                    } else {
                        permissionLauncher.launch(requiredPermissions)
                    }
                }
            ) {
                Text(
                    text = if (!hasPermissions) {
                        "Grant Permissions"
                    } else if (isCameraActive) {
                        "Turn Off Camera"
                    } else {
                        "Turn On Camera"
                    }
                )
            }

            if (!isCameraActive && hasPermissions) {
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*")
                        }
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "No video viewer found", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(text = "View Saved Videos")
                }
            }

            if (isCameraActive && hasPermissions) {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                    ),
                    onClick = {
                        val service = recordingService ?: return@Button
                        if (isRecording) {
                            service.stopRecording()
                        } else {
                            val intent = Intent(context, RecordingService::class.java).apply {
                                action = RecordingService.ACTION_START_RECORDING
                                putExtra(RecordingService.EXTRA_RECORD_AUDIO, isAudioEnabled)
                            }
                            ContextCompat.startForegroundService(context, intent)
                        }
                    }
                ) {
                    Text(text = if (isRecording) "Stop Recording" else "Record Video")
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    recordingService: RecordingService?
) {
    var previewView: PreviewView? by remember { mutableStateOf(null) }

    DisposableEffect(previewView, recordingService) {
        val pView = previewView
        if (pView != null && recordingService != null) {
            recordingService.setSurfaceProvider(pView.surfaceProvider)
        }
        onDispose {
            recordingService?.setSurfaceProvider(null)
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also {
                previewView = it
            }
        },
        modifier = modifier
    )
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name,
        modifier = modifier
    )
}

@Composable
fun LensIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier = modifier.size(28.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val outerRadius = size.minDimension / 2 * 0.9f
        val innerRadius1 = outerRadius * 0.7f
        val innerRadius2 = outerRadius * 0.42f
        val dotRadius = outerRadius * 0.15f

        // Outer camera lens housing ring
        drawCircle(
            color = tint,
            radius = outerRadius,
            center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )
        // Inner glass element ring
        drawCircle(
            color = tint,
            radius = innerRadius1,
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )
        // Center aperture lens glass
        drawCircle(
            color = tint,
            radius = innerRadius2,
            center = center,
            style = Fill
        )
        // Lens glare / reflection highlight
        val highlightCenter = Offset(center.x + innerRadius2 * 0.35f, center.y - innerRadius2 * 0.35f)
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = dotRadius,
            center = highlightCenter,
            style = Fill
        )
    }
}
