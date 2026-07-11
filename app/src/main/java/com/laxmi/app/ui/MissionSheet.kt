package com.laxmi.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Renders the whole mission lifecycle over whatever screen is showing.
 * The consent dialog is THE privacy moment: the payload text shown IS the
 * payload sent, byte for byte.
 */
@Composable
fun MissionOverlay(vm: AppViewModel) {
    val state by vm.missionState.collectAsState()
    val context = LocalContext.current

    when (val s = state) {
        AppViewModel.MissionState.Idle -> Unit

        is AppViewModel.MissionState.AwaitingConsent -> AlertDialog(
            onDismissRequest = { vm.dismissMission() },
            title = { Text("Yeh data phone se bahar jayega:") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            s.briefJson,
                            Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "Sirf itna. Awaaz, ledger, baaki parties — kuch nahi jaata.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { vm.approveMission() }) { Text("Bhejo") }
            },
            dismissButton = {
                OutlinedButton(onClick = { vm.dismissMission() }) { Text("Nahi") }
            },
        )

        AppViewModel.MissionState.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Agent team kaam par hai…") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Composer reminder ladder bana raha hai")
                }
            },
            confirmButton = {},
        )

        is AppViewModel.MissionState.Done -> AlertDialog(
            onDismissRequest = { vm.dismissMission() },
            title = { Text("${s.party} ke liye reminders") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.ladder.forEach { step ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(step.tone.uppercase(), style = MaterialTheme.typography.labelSmall)
                                Text(step.text, style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = {
                                    WhatsAppSend.toParty(context, s.party, step.text)
                                }) {
                                    Text(if (com.laxmi.app.data.LedgerStore.phoneFor(s.party) != null)
                                        "WhatsApp chat →" else "WhatsApp par bhejo")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.dismissMission() }) { Text("Ho gaya") }
            },
        )

        is AppViewModel.MissionState.Failed -> AlertDialog(
            onDismissRequest = { vm.dismissMission() },
            title = { Text("Mission fail ho gaya") },
            text = { Text(s.error, color = MaterialTheme.colorScheme.error) },
            confirmButton = {
                Button(onClick = { vm.dismissMission() }) { Text("Theek hai") }
            },
        )

        // Multi-agent campaign (Track 2) — UI wired in the Track 2 step.
        is AppViewModel.MissionState.CampaignConsent,
        is AppViewModel.MissionState.CampaignRunning,
        is AppViewModel.MissionState.CampaignDone -> Unit
    }
}
