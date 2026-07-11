package com.laxmi.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.laxmi.app.data.AckStatus
import com.laxmi.app.data.LedgerStore

/**
 * Invisible share target: "share the reply into Laxmi and it understands".
 * Consent-preserving automation — the user hands over exactly one message via
 * the share sheet; nothing is ever read in the background.
 *
 * v0 heuristics: yes/no keywords update the most recent SENT receipt (party
 * name match wins if the text contains one). Anything else is stored as an
 * unfiled text note for the pipeline.
 */
class ShareReceiverActivity : Activity() {

    private val yesWords = listOf("haan", "han", "ha ", "sahi", "thik", "theek", "ok", "yes", "👍")
    private val noWords = listOf("nahi", "nhi", "galat", "no ", "wrong", "kam hai", "zyada hai")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (intent?.action == Intent.ACTION_SEND && text.isNotEmpty()) {
            handleShared(text.lowercase(), text)
        }
        finish()
    }

    private fun handleShared(lower: String, original: String) {
        val sent = LedgerStore.events.value
            .filter { it.ackStatus == AckStatus.SENT }
            .sortedByDescending { it.createdAt }

        val verdict = when {
            noWords.any { lower.contains(it) } -> AckStatus.DISPUTED
            yesWords.any { lower.contains(it) } -> AckStatus.CONFIRMED_BY_THEM
            else -> null
        }

        if (verdict != null && sent.isNotEmpty()) {
            // Prefer a receipt whose party name appears in the reply; else newest.
            val target = sent.firstOrNull { lower.contains(it.party.lowercase()) } ?: sent.first()
            LedgerStore.setAck(target.id, verdict)
            val label = if (verdict == AckStatus.CONFIRMED_BY_THEM)
                "✓ ${target.party} ne haan bola" else "⚠ ${target.party} ne dispute kiya — suno aur suljhao"
            Toast.makeText(this, "Laxmi: $label", Toast.LENGTH_LONG).show()
        } else {
            // Not an ack reply — keep it as an unfiled note so nothing is lost.
            LedgerStore.append(
                com.laxmi.app.data.LedgerEvent(
                    party = "Unfiled",
                    kind = com.laxmi.app.data.EventKind.COMMITMENT,
                    direction = com.laxmi.app.data.Direction.OWED_TO_ME,
                    type = "unfiled",
                    amountPaise = null, quantity = null, item = null, duePhrase = null,
                    firmness = "tentative", confidence = 0.0,
                    quote = original,
                    sourceTag = "whatsapp-text",
                    sourceAudio = null, sourceText = original,
                    status = com.laxmi.app.data.EventStatus.PENDING_REVIEW,
                )
            )
            Toast.makeText(this, "Laxmi: note save ho gaya — Inbox mein hai", Toast.LENGTH_LONG).show()
        }
    }
}
