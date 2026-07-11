package com.laxmi.app.ui

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

private val SUGGESTIONS = listOf(
    "Sharma ji se kitna aana hai?",
    "Aaj kya due hai?",
    "Kis kis se paisa lena hai?",
)

@Composable
fun AskScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val answer by vm.queryAnswer.collectAsState()
    val busy by vm.queryBusy.collectAsState()
    var question by remember { mutableStateOf("") }

    // Best-effort TTS: speaks the answer if a Hindi/any engine is available.
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val t = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
            }
        }
        tts = t
        onDispose { t.shutdown() }
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Laxmi se poocho", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text("Sawaal likho ya niche se chuno") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { if (question.isNotBlank()) vm.ask(question) }) { Text("Poocho") }
        SUGGESTIONS.forEach { s ->
            OutlinedButton(onClick = { question = s; vm.ask(s) }) { Text(s) }
        }

        if (busy) {
            CircularProgressIndicator()
            Text("Ledger dekh rahi hoon…")
        }
        answer?.let { a ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(a, style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = {
                        tts?.speak(a, TextToSpeech.QUEUE_FLUSH, null, "laxmi-answer")
                    }) { Text("🔊 Bolo") }
                }
            }
        }
    }
}
