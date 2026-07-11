package com.laxmi.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.laxmi.app.agents.Extractor
import com.laxmi.app.data.EventStatus
import com.laxmi.app.data.LedgerEvent
import com.laxmi.app.data.LedgerStore
import com.laxmi.app.data.PartyBalance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Thin UI-state wrapper over the process-level [Extractor] singleton (which owns
 * the one shared engine). No engine here — that would double-load the model.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val modelFile get() = Extractor.modelFile

    val engineState = Extractor.state
    val engineError = Extractor.error
    val busy = Extractor.busy

    val events: StateFlow<List<LedgerEvent>> = LedgerStore.events
    val balances: StateFlow<List<PartyBalance>> =
        LedgerStore.events.map { LedgerStore.balances(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun initEngine() = Extractor.warm(getApplication())

    fun onModelImported() = Extractor.warm(getApplication())

    /** Capture path: audio or text in, results land in the ledger/inbox. */
    fun ingest(audio: ByteArray? = null, text: String? = null, sourceTag: String) {
        Extractor.ingest(audio = audio, text = text, sourceTag = sourceTag)
    }

    fun confirm(id: String) = LedgerStore.setStatus(id, EventStatus.CONFIRMED)
    fun reject(id: String) = LedgerStore.setStatus(id, EventStatus.REJECTED)

    /** Post the "aaj ka hisaab" dues digest now (demo trigger for the daily worker). */
    fun sendDigestNow() {
        val owed = balances.value.filter { it.netPaise > 0 }
        val ctx = getApplication<Application>()
        if (owed.isEmpty()) {
            com.laxmi.app.Notifier.show(ctx, "Aaj ka hisaab", "Koi due pending nahi — sab clear!")
            return
        }
        val total = owed.sumOf { it.netPaise } / 100
        val who = owed.take(3).joinToString(", ") { it.party }
        com.laxmi.app.Notifier.show(
            ctx, "Aaj ka hisaab",
            "₹%,d aana hai — $who%s".format(total, if (owed.size > 3) " +${owed.size - 3}" else ""),
        )
    }

    // ---- Collections mission (Antigravity cloud plane) ----

    sealed interface MissionState {
        data object Idle : MissionState
        /** Consent gate: [briefJson] is EXACTLY what will leave the phone. */
        data class AwaitingConsent(val party: String, val briefJson: String) : MissionState
        data object Running : MissionState
        data class Done(val party: String, val ladder: List<com.laxmi.app.missions.ReminderStep>) : MissionState
        data class Failed(val error: String) : MissionState

        // ---- Multi-agent collection drive (Track 2) ----
        data class CampaignConsent(val briefJson: String, val partyCount: Int) : MissionState
        data class CampaignRunning(val currentAgent: String, val log: List<String>) : MissionState
        data class CampaignDone(val messages: List<com.laxmi.app.missions.CollectionsCampaign.CampaignMessage>) : MissionState
    }

    val missionState = MutableStateFlow<MissionState>(MissionState.Idle)

    fun requestCollections(party: String) {
        val balance = balances.value.firstOrNull { it.party == party } ?: return
        val due = events.value
            .firstOrNull { it.party == party && it.duePhrase != null }?.duePhrase
        val brief = com.laxmi.app.missions.CollectionsMission.brief(
            party = party,
            amountRupees = kotlin.math.abs(balance.netPaise) / 100,
            duePhrase = due,
        )
        // On-device — no consent gate needed, nothing leaves the phone.
        missionState.value = MissionState.Running
        viewModelScope.launch {
            try {
                val output = com.laxmi.app.agents.Extractor.generateOnDevice(
                    com.laxmi.app.missions.CollectionsMission.prompt(brief))
                val ladder = com.laxmi.app.missions.CollectionsMission.parseLadder(output)
                missionState.value = if (ladder.isEmpty())
                    MissionState.Failed("Reminder nahi ban paya:\n${output.take(200)}")
                else MissionState.Done(party, ladder)
            } catch (t: Throwable) {
                missionState.value = MissionState.Failed(t.message ?: "reminder failed")
            }
        }
    }

    fun approveMission() {
        val consent = missionState.value as? MissionState.AwaitingConsent ?: return
        missionState.value = MissionState.Running
        viewModelScope.launch {
            try {
                // ON-DEVICE: composing a reminder is language only — no data leaves.
                val output = com.laxmi.app.agents.Extractor.generateOnDevice(
                    com.laxmi.app.missions.CollectionsMission.prompt(consent.briefJson)
                )
                val ladder = com.laxmi.app.missions.CollectionsMission.parseLadder(output)
                missionState.value = if (ladder.isEmpty()) {
                    MissionState.Failed("Reminder nahi ban paya:\n${output.take(200)}")
                } else {
                    MissionState.Done(consent.party, ladder)
                }
            } catch (t: Throwable) {
                missionState.value = MissionState.Failed(t.message ?: "reminder failed")
            }
        }
    }

    fun dismissMission() {
        missionState.value = MissionState.Idle
    }

    // ---- Cloud specialists Gemma delegates to (Track 2 — Antigravity) ----

    // Outward (cloud) workflows only — inward jobs (reminders, insights) are on-device.
    enum class Workflow { DOSSIER, MARKET, GUIDE, SCHEMES, DEMAND }

    sealed interface AgentResult {
        data class Messages(val items: List<com.laxmi.app.missions.CollectionsCampaign.CampaignMessage>) : AgentResult
        data class Report(val text: String) : AgentResult
    }

    sealed interface AgentRun {
        data object Idle : AgentRun
        data class Consent(
            val wf: Workflow, val title: String, val payload: String,
            val debtors: List<com.laxmi.app.missions.CollectionsCampaign.Debtor>,
        ) : AgentRun
        data class Running(val title: String, val steps: List<String>) : AgentRun
        data class Done(val title: String, val result: AgentResult) : AgentRun
        data class Failed(val title: String, val msg: String) : AgentRun
    }

    val agentRun = MutableStateFlow<AgentRun>(AgentRun.Idle)

    fun requestWorkflow(wf: Workflow) {
        val owed = balances.value.filter { it.netPaise > 0 }
        val debtors = owed.map { b ->
            val due = events.value.firstOrNull { it.party == b.party && it.duePhrase != null }?.duePhrase
            com.laxmi.app.missions.CollectionsCampaign.Debtor(b.party, b.netPaise / 100, due)
        }
        val title: String
        val payload: String
        when (wf) {
            Workflow.DOSSIER -> { title = "Credit dossier"; payload = com.laxmi.app.agents.Extractor.creditSummary() }
            Workflow.MARKET -> { title = "Bazaar bhav"; payload = com.laxmi.app.agents.Extractor.purchaseContext() }
            Workflow.SCHEMES -> { title = "Sarkari schemes"; payload = com.laxmi.app.agents.Extractor.creditSummary() }
            Workflow.DEMAND -> { title = "Festival demand"; payload = com.laxmi.app.agents.Extractor.goodsContext() }
            Workflow.GUIDE -> { title = "Guide"; payload = "" } // set via requestGuide
        }
        agentRun.value = AgentRun.Consent(wf, title, payload, debtors)
    }

    /** Step-by-step guidance (outward: needs the current real-world process). The
     *  payload is just the topic — almost nothing private leaves. */
    fun requestGuide(topic: String) {
        agentRun.value = AgentRun.Consent(Workflow.GUIDE, "Guide: $topic", topic, emptyList())
    }

    private fun pushStep(s: String) {
        (agentRun.value as? AgentRun.Running)?.let { agentRun.value = it.copy(steps = it.steps + s) }
    }

    fun approveWorkflow() {
        val c = agentRun.value as? AgentRun.Consent ?: return
        agentRun.value = AgentRun.Running(c.title, listOf("Laxmi specialists ko brief bhej rahi hai…"))
        viewModelScope.launch {
            try {
                val wf = com.laxmi.app.missions.AgentWorkflows
                val mc = com.laxmi.app.missions.MissionClient
                when (c.wf) {
                    Workflow.DOSSIER -> {
                        pushStep("📊 Analyst code se metrics compute kar raha hai…")
                        val metrics = mc.run(wf.analystPrompt(c.payload))
                        pushStep("🌐 Researcher web se schemes dhoond raha hai…")
                        val research = mc.run(wf.researcherPrompt(metrics))
                        pushStep("🧑‍⚖️ Advisor loan-readiness summary bana raha hai…")
                        val advice = mc.run(wf.advisorPrompt(metrics, research))
                        agentRun.value = AgentRun.Done(c.title, AgentResult.Report(advice))
                    }
                    Workflow.MARKET -> {
                        pushStep("🌐 Researcher live market bhav dhoond raha hai…")
                        val prices = mc.run(wf.marketResearcherPrompt(c.payload))
                        pushStep("📊 Analyst aapke rate vs bazaar compare kar raha hai…")
                        val analysis = mc.run(wf.marketAnalystPrompt(c.payload, prices))
                        pushStep("🧑‍⚖️ Advisor bachat ke tarike bata raha hai…")
                        val advice = mc.run(wf.marketAdvisorPrompt(analysis))
                        agentRun.value = AgentRun.Done(c.title, AgentResult.Report(advice))
                    }
                    Workflow.GUIDE -> {
                        pushStep("🌐 Researcher aaj ka process web se nikaal raha hai…")
                        val research = mc.run(wf.guideResearcherPrompt(c.payload))
                        pushStep("📋 Planner step-by-step checklist bana raha hai…")
                        val steps = mc.run(wf.guidePlannerPrompt(c.payload, research))
                        agentRun.value = AgentRun.Done(c.title, AgentResult.Report(steps))
                    }
                    Workflow.SCHEMES -> {
                        pushStep("🌐 Researcher sarkari schemes dhoond raha hai…")
                        val research = mc.run(wf.schemeResearcherPrompt(c.payload))
                        pushStep("🧑‍⚖️ Advisor eligible schemes chun raha hai…")
                        val advice = mc.run(wf.schemeAdvisorPrompt(research))
                        agentRun.value = AgentRun.Done(c.title, AgentResult.Report(advice))
                    }
                    Workflow.DEMAND -> {
                        pushStep("🌐 Researcher upcoming festivals/season dekh raha hai…")
                        val research = mc.run(wf.demandResearcherPrompt(c.payload))
                        pushStep("🧑‍⚖️ Advisor stock-up plan bana raha hai…")
                        val advice = mc.run(wf.demandAdvisorPrompt(c.payload, research))
                        agentRun.value = AgentRun.Done(c.title, AgentResult.Report(advice))
                    }
                }
            } catch (t: Throwable) {
                agentRun.value = AgentRun.Failed(c.title, t.message ?: "fail ho gaya")
            }
        }
    }

    fun dismissAgent() { agentRun.value = AgentRun.Idle }

    /** Insights are INWARD (only your ledger) → on-device Gemma, no consent, offline. */
    fun runInsightsOnDevice() {
        agentRun.value = AgentRun.Running("Business insights", listOf("Laxmi phone pe hi analyze kar rahi hai…"))
        viewModelScope.launch {
            try {
                val out = com.laxmi.app.agents.Extractor.generateOnDevice(
                    com.laxmi.app.missions.AgentWorkflows.insightsPrompt(
                        com.laxmi.app.agents.Extractor.ledgerContext()))
                agentRun.value = AgentRun.Done("Business insights",
                    AgentResult.Report(out.ifBlank { "Abhi analyze nahi kar payi." }))
            } catch (t: Throwable) {
                agentRun.value = AgentRun.Failed("Business insights", t.message ?: "fail")
            }
        }
    }

    // ---- Ask the ledger (offline query) ----

    val queryAnswer = MutableStateFlow<String?>(null)
    val queryBusy = MutableStateFlow(false)

    fun ask(question: String) {
        queryBusy.value = true
        queryAnswer.value = null
        viewModelScope.launch {
            queryAnswer.value = Extractor.query(question, Extractor.ledgerContext())
            queryBusy.value = false
        }
    }

    fun askAudio(wav: ByteArray) {
        queryBusy.value = true
        queryAnswer.value = null
        Extractor.assist(
            audio = wav,
            onRecorded = { queryBusy.value = false },
            onAnswer = { ans -> queryAnswer.value = ans; queryBusy.value = false },
            onAction = { _, _ -> queryBusy.value = false },
        )
    }
}
