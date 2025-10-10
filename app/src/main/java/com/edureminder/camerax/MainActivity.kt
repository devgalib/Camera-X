package com.edureminder.camerax

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.edureminder.camerax.service.RecordingService
import com.edureminder.camerax.ui.theme.CameraXTheme

class MainActivity : ComponentActivity() {
    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
// No explicit repeat: just handle result
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            var recording by remember { mutableStateOf(false) }


            LaunchedEffect(Unit) {
                requestPerms.launch(arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ))
            }



            CameraXTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Background Camera Recorder", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = {
                        if (!recording) {
                            val startIntent = Intent(ctx, RecordingService::class.java).apply {
                                action = RecordingService.ACTION_START
                            }
                            ctx.startForegroundService(startIntent)
                            recording = true
                        } else {
                            val stopIntent = Intent(ctx, RecordingService::class.java).apply {
                                action = RecordingService.ACTION_STOP
                            }
                            ctx.startService(stopIntent)
                            recording = false
                        }
                    }) {
                        Text(if (!recording) "Start Recording (screen off style)" else "Stop Recording")
                    }


                    Spacer(Modifier.height(16.dp))
                    Text("Notes:\n- The app uses a foreground service and a wakelock to keep recording when the screen appears off.")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CameraXTheme {
        Greeting("Android")
    }
}