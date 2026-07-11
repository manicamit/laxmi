package com.laxmi.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.laxmi.app.agents.Extractor
import com.laxmi.app.audio.AudioDecoder
import com.laxmi.app.data.AckStatus
import com.laxmi.app.data.LedgerStore
import com.laxmi.app.ui.theme.LaxmiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share target for WhatsApp content:
 *  - text/plain : ack replies ("haan/nahi") update a pending receipt; other text
 *    becomes an unfiled note. Invisible, auto-handled.
 *  - audio      : decode to WAV, ask which party it's for (ground-truth tag),
 *    then extract into the ledger.
 */
class ShareReceiverActivity : ComponentActivity() {

    private val yesWords = listOf("haan", "han", "sahi", "thik", "theek", "ok", "yes", "👍")
    private val noWords = listOf("nahi", "nhi", "galat", "wrong", "kam hai", "zyada hai")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent?.type.orEmpty()
        if (intent?.action != Intent.ACTION_SEND) { finish(); return }

        when {
            type == "text/plain" -> {
                handleText(intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty())
                finish()
            }
            type.startsWith("audio/") || type.startsWith("image/") -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri == null) { finish(); return }
                val isImage = type.startsWith("image/")
                setContent { LaxmiTheme { PartyPickerFlow(uri, isImage) } }
            }
            else -> finish()
        }
    }

    private fun handleText(original: String) {
        if (original.isEmpty()) return
        val lower = original.lowercase()
        val sent = LedgerStore.events.value
            .filter { it.ackStatus == AckStatus.SENT }
            .sortedByDescending { it.createdAt }
        val verdict = when {
            noWords.any { lower.contains(it) } -> AckStatus.DISPUTED
            yesWords.any { lower.contains(it) } -> AckStatus.CONFIRMED_BY_THEM
            else -> null
        }
        if (verdict != null && sent.isNotEmpty()) {
            val target = sent.firstOrNull { lower.contains(it.party.lowercase()) } ?: sent.first()
            LedgerStore.setAck(target.id, verdict)
            val label = if (verdict == AckStatus.CONFIRMED_BY_THEM)
                "✓ ${target.party} ne haan bola" else "⚠ ${target.party} ne dispute kiya"
            Toast.makeText(this, "Laxmi: $label", Toast.LENGTH_LONG).show()
        } else {
            Extractor.ingest(text = original, sourceTag = "whatsapp-text")
            Toast.makeText(this, "Laxmi: note save ho gaya — Inbox mein", Toast.LENGTH_LONG).show()
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun PartyPickerFlow(uri: Uri, isImage: Boolean) {
        var working by remember { mutableStateOf(false) }
        var newName by remember { mutableStateOf("") }
        // Voice notes: who spoke decides the direction. Default: the other person
        // sent it (the common case). Images derive direction from their content.
        var fromMe by remember { mutableStateOf(false) }
        val parties = remember { LedgerStore.partyNames() }

        fun submit(party: String?) {
            working = true
            lifecycleScope.launch {
                // Copy the shared file NOW (the URI grant dies with this activity),
                // then dismiss and let extraction run in the background.
                val ext = if (isImage) "img" else "audio"
                val copied = withContext(Dispatchers.IO) {
                    runCatching {
                        val f = java.io.File(cacheDir, "share_${System.nanoTime()}.$ext")
                        contentResolver.openInputStream(uri)?.use { input ->
                            f.outputStream().use { input.copyTo(it) }
                        }
                        f.absolutePath
                    }.getOrNull()
                }
                if (copied == null) {
                    Toast.makeText(applicationContext, "Laxmi: file nahi mili", Toast.LENGTH_LONG).show()
                    finish(); return@launch
                }
                if (isImage) Extractor.ingestSharedImageFile(copied, party, fromMe = true)
                else Extractor.ingestSharedAudioFile(copied, party, fromMe = fromMe)
                Toast.makeText(
                    applicationContext,
                    "Laxmi: ${party ?: "auto"} ke liye ho gaya — ledger mein aa jayega",
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
        }

        Surface(color = Color(0xF2000000), modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Kiske liye?", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                if (working) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Ledger mein add ho raha hai…", color = Color.White)
                } else {
                    if (!isImage) {
                        Text("Kisne bola?", color = Color(0xCCFFFFFF))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { fromMe = false },
                                label = { Text(if (!fromMe) "● Unhone bola" else "Unhone bola") },
                            )
                            AssistChip(
                                onClick = { fromMe = true },
                                label = { Text(if (fromMe) "● Maine bola" else "Maine bola") },
                            )
                        }
                    }
                    Text(
                        if (isImage) "Yeh bill/screenshot kis party ka hai?"
                        else "Voice note kis party ka hai?",
                        color = Color(0xCCFFFFFF),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { submit(null) }, label = { Text("Auto-detect") })
                        parties.forEach { p ->
                            AssistChip(onClick = { submit(p) }, label = { Text(p) })
                        }
                    }
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Naya naam") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { submit(newName.ifBlank { null }) },
                        enabled = newName.isNotBlank(),
                    ) { Text("Is naam se add karo") }
                }
            }
        }
    }
}
