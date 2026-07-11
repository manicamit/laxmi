package com.laxmi.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.laxmi.app.audio.EvidencePlayer
import com.laxmi.app.data.AckStatus
import com.laxmi.app.data.Direction
import com.laxmi.app.data.EventStatus
import com.laxmi.app.data.LedgerEvent
import com.laxmi.app.data.LedgerStore

private fun paiseToRupees(paise: Long): String {
    val rupees = paise / 100
    return "₹%,d".format(rupees)
}

@Composable
fun LedgerScreen(vm: AppViewModel) {
    val balances by vm.balances.collectAsState()
    val owedToMe = balances.filter { it.netPaise > 0 }.sumOf { it.netPaise }
    val iOwe = balances.filter { it.netPaise < 0 }.sumOf { -it.netPaise }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Laxmi", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { vm.runInsightsOnDevice() }) { Text("📊") }
                OutlinedButton(onClick = { vm.sendDigestNow() }) { Text("🔔 Aaj ka hisaab") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("Aane hain", style = MaterialTheme.typography.labelMedium)
                Text(
                    paiseToRupees(owedToMe),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color(0xFF2E6B45),
                )
            }
            Column {
                Text("Dene hain", style = MaterialTheme.typography.labelMedium)
                Text(
                    paiseToRupees(iOwe),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (balances.isEmpty()) {
            Text(
                "Ledger khaali hai. 🎤 se pehla voice note record karo.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(balances, key = { it.party }) { b ->
                PartyCard(b, vm)
            }
        }
    }
}

@Composable
private fun PartyCard(b: com.laxmi.app.data.PartyBalance, vm: AppViewModel) {
    val context = LocalContext.current
    val events by vm.events.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val partyEvents = events.filter {
        it.party == b.party && it.status != EventStatus.REJECTED && it.type != "unfiled"
    }

    Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(b.party, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${partyEvents.size} promises · tap karo",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    paiseToRupees(kotlin.math.abs(b.netPaise)),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (b.netPaise >= 0) Color(0xFF2E6B45)
                    else MaterialTheme.colorScheme.error,
                )
            }
            if (expanded) {
                partyEvents.sortedByDescending { it.createdAt }.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.padding(end = 8.dp)) {
                            Text(
                                (if (e.direction == Direction.OWED_TO_ME) "🟢 " else "🔴 ") +
                                    (e.amountPaise?.let { paiseToRupees(it) }
                                        ?: listOfNotNull(e.quantity?.toString(), e.item)
                                            .joinToString(" ").ifBlank { e.type }) +
                                    (e.duePhrase?.let { " · ⏰ $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "\"${e.quote}\"",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (e.sourceAudio != null) {
                            OutlinedButton(onClick = { EvidencePlayer.play(context, e.sourceAudio) }) {
                                Text("▶")
                            }
                        }
                    }
                }
                if (b.netPaise > 0) {
                    OutlinedButton(
                        onClick = { vm.requestCollections(b.party) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Reminder bhejo") }
                }
            }
        }
    }
}

@Composable
fun InboxScreen(vm: AppViewModel) {
    val events by vm.events.collectAsState()
    val pending = events.filter { it.status == EventStatus.PENDING_REVIEW }
    val recent = events
        .filter { it.status == EventStatus.CONFIRMED }
        .sortedByDescending { it.createdAt }
        .take(10)

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Inbox — check kar lo", style = MaterialTheme.typography.headlineSmall)
        if (pending.isEmpty()) {
            Text("Sab clear! Naye entries yahan aayenge.", style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pending, key = { it.id }) { e ->
                ReviewCard(e, vm)
            }
            if (recent.isNotEmpty()) {
                item { Text("Recent — receipt status", style = MaterialTheme.typography.titleMedium) }
                items(recent, key = { "r-" + it.id }) { e ->
                    AckRow(e)
                }
            }
        }
    }
}

/**
 * Confirmed entries with their acknowledgement lifecycle. Tapping the chip cycles
 * SENT → CONFIRMED_BY_THEM → DISPUTED → SENT (v0: their WhatsApp reply is recorded
 * manually).
 */
@Composable
private fun AckRow(e: LedgerEvent) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "${e.party} · ${e.amountPaise?.let { paiseToRupees(it) } ?: e.item.orEmpty()}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    when (e.ackStatus) {
                        AckStatus.NONE -> "receipt nahi bheja"
                        AckStatus.SENT -> "⏳ bheja — jawab ka intezaar"
                        AckStatus.CONFIRMED_BY_THEM -> "✓ unhone haan bola"
                        AckStatus.DISPUTED -> "⚠ unhone nahi bola — suno aur suljhao"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.then(Modifier),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (e.ackStatus == AckStatus.NONE) {
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, LedgerStore.receiptText(e))
                        }
                        context.startActivity(Intent.createChooser(send, "Receipt bhejo"))
                        LedgerStore.setAck(e.id, AckStatus.SENT)
                    }) { Text("Receipt") }
                } else {
                    OutlinedButton(onClick = {
                        LedgerStore.setAck(
                            e.id,
                            when (e.ackStatus) {
                                AckStatus.SENT -> AckStatus.CONFIRMED_BY_THEM
                                AckStatus.CONFIRMED_BY_THEM -> AckStatus.DISPUTED
                                else -> AckStatus.SENT
                            },
                        )
                    }) { Text("Jawab") }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(e: LedgerEvent, vm: AppViewModel) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(e.party, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (e.type) {
                        "payment" -> "💰"
                        "delivery" -> "📦"
                        "unfiled" -> "❓"
                        else -> "🔧"
                    } + "  " + (e.amountPaise?.let { paiseToRupees(it) } ?: e.item.orEmpty()),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (e.direction == Direction.OWED_TO_ME) Color(0xFF2E6B45)
                    else MaterialTheme.colorScheme.error,
                )
            }
            e.duePhrase?.let { Text("⏰ $it", style = MaterialTheme.typography.bodyMedium) }
            Text(
                "\"${e.quote}\"",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (e.sourceAudio != null) {
                    OutlinedButton(onClick = { EvidencePlayer.play(context, e.sourceAudio) }) {
                        Text("▶ Suno")
                    }
                }
                Spacer(Modifier.width(4.dp))
                Button(onClick = { vm.confirm(e.id) }) { Text("✓ Sahi hai") }
                OutlinedButton(onClick = { vm.reject(e.id) }) { Text("✗") }
            }
            Text(
                "via ${e.sourceTag} · ${if (e.confidence >= 0.85) "pakka" else "check karo"}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
