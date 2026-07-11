package com.laxmi.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.laxmi.app.audio.VoiceRecorder

/**
 * Tap to start, tap to stop. On stop, hands the finished WAV bytes to [onRecorded].
 * Requests RECORD_AUDIO on first use.
 */
@Composable
fun RecordButton(onRecorded: (ByteArray) -> Unit) {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder() }
    var recording by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            recorder.start()
            recording = true
        }
    }

    Button(
        onClick = {
            if (recording) {
                recording = false
                onRecorded(recorder.stop())
            } else {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    recorder.start()
                    recording = true
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        },
        colors = if (recording) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(if (recording) "■ Stop & extract" else "🎤 Record & extract (audio)")
    }
}
