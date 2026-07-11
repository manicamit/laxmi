package com.laxmi.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.laxmi.app.ui.theme.laxmi

/** Track 2 surface: the cloud specialists Gemma delegates to. */
@Composable
fun AgentsScreen(vm: AppViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bahar ki duniya ke agents", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Laxmi aapke data pe phone pe hi kaam karti hai. Yeh agents BAHAR ki duniya " +
                "se cheezein laate hain — live bhav, credit schemes — jo phone pe hai hi " +
                "nahi. Sirf ek approved summary jaati hai; aapka raw data phone pe rehta hai.",
            style = MaterialTheme.typography.bodyMedium,
        )

        // Open-goal planner: say anything, the agent figures out the plan.
        var goal by remember { mutableStateOf("") }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🧠 Laxmi se kuch bhi karwao", style = MaterialTheme.typography.titleMedium)
                Text(
                    "\"dhandha slow hai, kya karun?\" · \"50 hazar ka loan chahiye\" — " +
                        "Laxmi khud plan banati hai.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = goal, onValueChange = { goal = it },
                    label = { Text("Apna goal likho…") }, modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if (goal.isNotBlank()) vm.requestPlanner(goal) }, enabled = goal.isNotBlank()) {
                        Text("Plan banao")
                    }
                    RecordButton { wav -> vm.requestPlannerFromAudio(wav) }
                }
                Text(
                    "Agent kar sakta: web research, hisaab, planning, draft. " +
                        "Nahi kar sakta: paisa bhejna, message bhejna, ya aapki taraf se " +
                        "commitment — woh aap khud karte ho.",
                    style = MaterialTheme.typography.labelSmall,
                    color = laxmi().muted,
                )
            }
        }
        WorkflowCard(
            "🛒 Bazaar bhav — bachat agent",
            "Researcher (web se aaj ke market rate) → Analyst (code se compare) → " +
                "Advisor (kahan overpay, sasta kahan). Live bhav bahar hai — phone akele nahi kar sakta.",
        ) { vm.requestWorkflow(AppViewModel.Workflow.MARKET) }
        WorkflowCard(
            "🏦 Credit dossier",
            "Analyst (code se metrics) → Researcher (web se live loan schemes) → " +
                "Advisor (loan-readiness summary). Sirf aggregates jaate hain, naam nahi.",
        ) { vm.requestWorkflow(AppViewModel.Workflow.DOSSIER) }
        WorkflowCard(
            "🏛️ Sarkari scheme finder",
            "Researcher (web se aapke liye eligible govt schemes/subsidy) → Advisor. " +
                "Muft ka paisa jo log claim hi nahi karte.",
        ) { vm.requestWorkflow(AppViewModel.Workflow.SCHEMES) }
        WorkflowCard(
            "🎉 Festival demand forecast",
            "Researcher (web se upcoming festivals + demand) → Advisor (kya, kab stock karo). " +
                "Season ka data bahar hai.",
        ) { vm.requestWorkflow(AppViewModel.Workflow.DEMAND) }
        WorkflowCard(
            "📋 Udyam (MSME) registration guide",
            "Researcher (web se aaj ka process) → Planner (step-by-step checklist).",
        ) { vm.requestGuide("Udyam (MSME) registration") }
        WorkflowCard(
            "📋 GST registration guide",
            "Researcher → Planner: GST registration ke steps, documents, portal.",
        ) { vm.requestGuide("GST registration for a small business") }

        // Standing background agent
        var watching by remember { mutableStateOf(false) }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔔 Bazaar bhav watch (background)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Har hafte Laxmi khud market rate check karke batayegi agar sasta mil " +
                        "sakta hai — app khole bina.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = {
                    watching = !watching
                    if (watching) com.laxmi.app.PriceWatchWorker.enable(ctx)
                    else com.laxmi.app.PriceWatchWorker.disable(ctx)
                }) { Text(if (watching) "Watch ON — band karo" else "Weekly watch chalu karo") }
            }
        }
    }
}

@Composable
private fun WorkflowCard(title: String, desc: String, onRun: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRun) { Text("Laxmi se karwao") }
        }
    }
}

@Composable
fun AgentRunOverlay(vm: AppViewModel) {
    val state by vm.agentRun.collectAsState()
    val context = LocalContext.current

    when (val s = state) {
        AppViewModel.AgentRun.Idle -> Unit

        is AppViewModel.AgentRun.Consent -> AlertDialog(
            onDismissRequest = { vm.dismissAgent() },
            title = { Text("${s.title}: yeh brief cloud pe jayega") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(s.payload, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Sirf yeh derived summary. Awaaz, transcript, baaki kuch nahi.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { Button(onClick = { vm.approveWorkflow() }) { Text("Bhejo") } },
            dismissButton = { OutlinedButton(onClick = { vm.dismissAgent() }) { Text("Nahi") } },
        )

        is AppViewModel.AgentRun.Running -> AlertDialog(
            onDismissRequest = {},
            title = { Text(s.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    s.steps.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {},
        )

        is AppViewModel.AgentRun.Done -> AlertDialog(
            onDismissRequest = { vm.dismissAgent() },
            title = { Text(s.title) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (val r = s.result) {
                        is AppViewModel.AgentResult.Report -> {
                            // Voice-first: speak the finding aloud (audience may not read).
                            LaunchedEffect(r.text) { LaxmiTts.speak(context, r.text) }
                            Text(r.text, style = MaterialTheme.typography.bodyLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { LaxmiTts.speak(context, r.text) }) { Text("🔊 Sun lo") }
                                OutlinedButton(onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, r.text)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Bhejo"))
                                }) { Text("WhatsApp bhejo") }
                                OutlinedButton(onClick = { FileShare.shareText(context, s.title, r.text) }) {
                                    Text("📄 File")
                                }
                            }
                            // Follow-up: continues the SAME agent session (sandbox + context).
                            var followText by remember(s) { mutableStateOf("") }
                            OutlinedTextField(
                                value = followText,
                                onValueChange = { followText = it },
                                label = { Text("Aur poocho (usi agent se)…") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { if (followText.isNotBlank()) vm.followUp(followText) },
                                enabled = followText.isNotBlank(),
                            ) { Text("↪️ Aage poocho") }
                        }
                        is AppViewModel.AgentResult.Messages -> r.items.forEach { m ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${m.party} · ₹%,d".format(m.amountInr),
                                        style = MaterialTheme.typography.titleSmall)
                                    Text(m.text, style = MaterialTheme.typography.bodyMedium)
                                    OutlinedButton(onClick = {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, m.text)
                                        }
                                        context.startActivity(Intent.createChooser(send, "Bhejo"))
                                    }) { Text("WhatsApp bhejo") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { vm.dismissAgent() }) { Text("Ho gaya") } },
        )

        is AppViewModel.AgentRun.Failed -> AlertDialog(
            onDismissRequest = { vm.dismissAgent() },
            title = { Text("${s.title} fail") },
            text = { Text(s.msg, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Start) },
            confirmButton = { Button(onClick = { vm.dismissAgent() }) { Text("Theek hai") } },
        )
    }
}
