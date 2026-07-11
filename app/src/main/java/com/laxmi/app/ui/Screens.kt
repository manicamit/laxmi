package com.laxmi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import com.laxmi.app.audio.EvidencePlayer
import com.laxmi.app.data.AckStatus
import com.laxmi.app.data.Direction
import com.laxmi.app.data.EventStatus
import com.laxmi.app.data.LedgerEvent
import com.laxmi.app.data.LedgerStore
import com.laxmi.app.ui.theme.laxmi

private fun paiseToRupees(paise: Long): String {
    val rupees = paise / 100
    return "₹%,d".format(rupees)
}

@Composable
fun LedgerScreen(vm: AppViewModel) {
    val balances by vm.balances.collectAsState()
    val c = laxmi()
    val owedToMe = balances.filter { it.netPaise > 0 }.sumOf { it.netPaise }
    val iOwe = balances.filter { it.netPaise < 0 }.sumOf { -it.netPaise }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🪔", style = MaterialTheme.typography.headlineMedium)
                Text("Laxmi", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { vm.runInsightsOnDevice() }, contentPadding = PaddingValues(12.dp)) { Text("📊") }
                FilledTonalButton(onClick = { vm.sendDigestNow() }) { Text("🔔") }
            }
        }

        // Hero balance card
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                BalanceStat("Aane hain", paiseToRupees(owedToMe), c.getGreen, "▲")
                Box(Modifier.width(1.dp).height(46.dp).background(MaterialTheme.colorScheme.outline))
                BalanceStat("Dene hain", paiseToRupees(iOwe), c.oweRed, "▼")
            }
        }

        if (balances.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("📖", style = MaterialTheme.typography.displaySmall)
                Text("Ledger khaali hai", style = MaterialTheme.typography.titleMedium)
                Text("🎤 Bolo tab se pehla note record karo", color = c.muted)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(balances, key = { it.party }) { b ->
                PartyCard(b, vm)
            }
        }
    }
}

@Composable
private fun BalanceStat(label: String, amount: String, color: Color, arrow: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = laxmi().muted)
        Text(
            amount,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFeatureSettings = "tnum", fontWeight = FontWeight.ExtraBold),
            color = color,
        )
        Text(arrow, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

private val avatarTints = listOf(
    Color(0xFFCB8A2E), Color(0xFF1E7A4D), Color(0xFF9E2F23),
    Color(0xFF3E6B8A), Color(0xFF7A4DA0), Color(0xFFB2662A),
)

@Composable
private fun PartyCard(b: com.laxmi.app.data.PartyBalance, vm: AppViewModel) {
    val context = LocalContext.current
    val c = laxmi()
    val events by vm.events.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val partyEvents = events.filter {
        it.party == b.party && it.status != EventStatus.REJECTED && it.type != "unfiled"
    }
    val tint = avatarTints[(b.party.hashCode() and 0x7fffffff) % avatarTints.size]
    val amountColor = if (b.netPaise >= 0) c.getGreen else c.oweRed

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        b.party.trim().take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = tint,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(b.party, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${partyEvents.size} entries · tap for detail",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        paiseToRupees(kotlin.math.abs(b.netPaise)),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        color = amountColor,
                    )
                    Text(
                        if (b.netPaise >= 0) "aana hai" else "dena hai",
                        style = MaterialTheme.typography.labelSmall,
                        color = amountColor,
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                partyEvents.sortedByDescending { it.createdAt }.forEach { e ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.padding(end = 8.dp).weight(1f)) {
                            Text(
                                (if (e.direction == Direction.OWED_TO_ME) "🟢 " else "🔴 ") +
                                    (e.amountPaise?.let { paiseToRupees(it) }
                                        ?: listOfNotNull(e.quantity?.toString(), e.item)
                                            .joinToString(" ").ifBlank { e.type }) +
                                    (e.duePhrase?.let { " · ⏰ $it" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text("\"${e.quote}\"", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                        if (e.sourceAudio != null) {
                            FilledTonalButton(
                                onClick = { EvidencePlayer.play(context, e.sourceAudio) },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text("▶ Suno") }
                        }
                    }
                }
                if (b.netPaise > 0) {
                    Button(
                        onClick = { vm.requestCollections(b.party) },
                        modifier = Modifier.padding(top = 10.dp),
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
