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
        missionState.value = MissionState.AwaitingConsent(party, brief)
    }

    fun approveMission() {
        val consent = missionState.value as? MissionState.AwaitingConsent ?: return
        missionState.value = MissionState.Running
        viewModelScope.launch {
            try {
                val output = com.laxmi.app.missions.MissionClient.run(
                    com.laxmi.app.missions.CollectionsMission.prompt(consent.briefJson)
                )
                val ladder = com.laxmi.app.missions.CollectionsMission.parseLadder(output)
                missionState.value = if (ladder.isEmpty()) {
                    MissionState.Failed("Agent returned no ladder:\n${output.take(200)}")
                } else {
                    MissionState.Done(consent.party, ladder)
                }
            } catch (t: Throwable) {
                missionState.value = MissionState.Failed(t.message ?: "mission failed")
            }
        }
    }

    fun dismissMission() {
        missionState.value = MissionState.Idle
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
