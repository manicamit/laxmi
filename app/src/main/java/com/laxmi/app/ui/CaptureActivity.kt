package com.laxmi.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.laxmi.app.agents.Extractor
import com.laxmi.app.audio.VoiceRecorder
import java.util.Locale

private sealed interface AssistUi {
    data object Recording : AssistUi
    data object Thinking : AssistUi
    data class Answer(val text: String) : AssistUi
}

/**
 * Assistant overlay (assist gesture target). Translucent, records instantly, and
 * auto-routes: a stated commitment lands in the ledger (toast + close); a question
 * is answered aloud. Never opens the full app.
 */
class CaptureActivity : ComponentActivity() {

    private val recorder = VoiceRecorder()
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasMic = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            Toast.makeText(this, "Pehli baar app kholo aur mic permission do", Toast.LENGTH_LONG).show()
            startActivity(packageManager.getLaunchIntentForPackage(packageName))
            finish(); return
        }

        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("hi", "IN") }
        recorder.start()

        setContent {
            var ui by remember { mutableStateOf<AssistUi>(AssistUi.Recording) }

            Surface(color = Color(0xE6000000), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                    when (val s = ui) {
                        AssistUi.Recording -> RecordingView(
                            onStop = {
                                val wav = recorder.stop()
                                ui = AssistUi.Thinking
                                Extractor.assist(
                                    audio = wav,
                                    onRecorded = { count ->
                                        runOnUiThread {
                                            Toast.makeText(
                                                applicationContext,
                                                if (count > 0) "Laxmi: $count entry add hui" else "Laxmi: kuch commitment nahi mila",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                            finish()
                                        }
                                    },
                                    onAnswer = { ans -> runOnUiThread { ui = AssistUi.Answer(ans) } },
                                    onAction = { action, party -> runOnUiThread { launchActionShare(action, party) } },
                                )
                            },
                            onCancel = { recorder.stop(); finish() },
                        )
                        AssistUi.Thinking -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Text("Laxmi soch rahi hai…", color = Color.White, modifier = Modifier.padding(top = 12.dp))
                        }
                        is AssistUi.Answer -> {
                            LaunchedEffect(s.text) {
                                tts?.speak(s.text, TextToSpeech.QUEUE_FLUSH, null, "laxmi-answer")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(s.text, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                                OutlinedButton(onClick = { finish() }) { Text("Theek hai", color = Color.White) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchActionShare(action: String, party: String) {
        val store = com.laxmi.app.data.LedgerStore
        val text = if (action == "receipt") {
            val latest = store.events.value
                .filter { it.party.equals(party, ignoreCase = true) && it.type != "unfiled" }
                .maxByOrNull { it.createdAt }
            latest?.let { store.receiptText(it) } ?: store.reminderText(party)
        } else {
            store.reminderText(party)
        }
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        val label = if (action == "receipt") "receipt" else "reminder"
        Toast.makeText(this, "Laxmi: $party ko $label", Toast.LENGTH_SHORT).show()
        startActivity(android.content.Intent.createChooser(send, "Kahan bhejein?"))
        finish()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
private fun RecordingView(onStop: () -> Unit, onCancel: () -> Unit) {
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(1000); seconds++ }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("🎤 Bolo…", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Text("kaam bhi likhwao, ya sawaal bhi poocho", color = Color(0xBBFFFFFF), textAlign = TextAlign.Center)
        Text("%02d:%02d".format(seconds / 60, seconds % 60), color = Color.White)
        Button(
            onClick = onStop,
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9E2F23)),
        ) { Text("■", style = MaterialTheme.typography.headlineMedium, color = Color.White) }
        OutlinedButton(onClick = onCancel) { Text("Band karo", color = Color.White) }
    }
}
