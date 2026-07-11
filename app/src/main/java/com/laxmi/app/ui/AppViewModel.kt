package com.laxmi.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.laxmi.app.agents.ExtractionEngine
import com.laxmi.app.agents.ExtractionResult
import com.laxmi.app.agents.GemmaExtractionEngine
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
import java.io.File

enum class EngineState { NO_MODEL, LOADING, READY, ERROR }

/**
 * One queue, one engine, per PLAN.md: all inference serialized (the engine call
 * itself is suspend + single conversation), pipeline is fire-and-forget from the
 * UI's point of view — results land in the review inbox.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val modelFile = File(File(app.filesDir, "models").apply { mkdirs() }, "gemma-4-E4B-it.litertlm")

    private var engine: ExtractionEngine? = null

    val engineState = MutableStateFlow(if (modelFile.exists()) EngineState.LOADING else EngineState.NO_MODEL)
    val engineError = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    val events: StateFlow<List<LedgerEvent>> = LedgerStore.events
    val balances: StateFlow<List<PartyBalance>> =
        LedgerStore.events.map { LedgerStore.balances(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        if (modelFile.exists()) initEngine()
    }

    fun initEngine() {
        engineState.value = EngineState.LOADING
        viewModelScope.launch {
            try {
                val e = GemmaExtractionEngine(modelFile, getApplication<Application>().cacheDir)
                e.initialize()
                engine = e
                engineState.value = EngineState.READY
            } catch (t: Throwable) {
                engineError.value = t.message
                engineState.value = EngineState.ERROR
            }
        }
    }

    fun onModelImported() {
        engineState.value = EngineState.LOADING
        initEngine()
    }

    /** Capture path: audio or text in, review-inbox items out. */
    fun ingest(audio: ByteArray? = null, text: String? = null, sourceTag: String) {
        val e = engine ?: return
        busy.value = true
        viewModelScope.launch {
            when (val result = e.extract(audio = audio, text = text)) {
                is ExtractionResult.Success -> {
                    if (result.items.isEmpty()) {
                        // Nothing extractable — keep the artifact anyway (unfiled).
                        LedgerStore.append(unfiledEvent(audio, text, sourceTag, "no commitments found"))
                    } else {
                        result.items.forEach { item ->
                            LedgerStore.append(LedgerStore.fromExtraction(item, sourceTag, audio, text))
                        }
                    }
                }
                is ExtractionResult.ParseFailure ->
                    LedgerStore.append(unfiledEvent(audio, text, sourceTag, "parse: ${result.error}"))
                is ExtractionResult.EngineFailure ->
                    LedgerStore.append(unfiledEvent(audio, text, sourceTag, "engine: ${result.error}"))
            }
            busy.value = false
        }
    }

    fun confirm(id: String) = LedgerStore.setStatus(id, EventStatus.CONFIRMED)
    fun reject(id: String) = LedgerStore.setStatus(id, EventStatus.REJECTED)

    // ---- Collections mission (Antigravity cloud plane) ----

    sealed interface MissionState {
        data object Idle : MissionState
        /** Consent gate: [briefJson] is EXACTLY what will leave the phone. */
        data class AwaitingConsent(val party: String, val briefJson: String) : MissionState
        data object Running : MissionState
        data class Done(val party: String, val ladder: List<com.laxmi.app.missions.ReminderStep>) : MissionState
        data class Failed(val error: String) : MissionState
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
        val e = engine ?: return
        queryBusy.value = true
        queryAnswer.value = null
        viewModelScope.launch {
            val context = events.value
                .filter { it.status != EventStatus.REJECTED && it.type != "unfiled" }
                .joinToString("\n") { ev ->
                    val amount = ev.amountPaise?.let { "₹${it / 100}" }
                        ?: listOfNotNull(ev.quantity?.toString(), ev.item).joinToString(" ")
                    val dir = if (ev.direction == com.laxmi.app.data.Direction.OWED_TO_ME)
                        "unse lene hain" else "unko dene hain"
                    "- ${ev.party}: $amount $dir (${ev.kind.name.lowercase()}" +
                        (ev.duePhrase?.let { ", due $it" } ?: "") + ")"
                }
                .ifBlank { "(ledger khaali hai)" }
            queryAnswer.value = e.query(question, context)
            queryBusy.value = false
        }
    }

    /** The app never eats a note: failed extractions become playable unfiled items. */
    private fun unfiledEvent(
        audio: ByteArray?,
        text: String?,
        sourceTag: String,
        reason: String,
    ) = LedgerEvent(
        party = "Unfiled",
        kind = com.laxmi.app.data.EventKind.COMMITMENT,
        direction = com.laxmi.app.data.Direction.OWED_TO_ME,
        type = "unfiled",
        amountPaise = null,
        quantity = null,
        item = null,
        duePhrase = null,
        firmness = "tentative",
        confidence = 0.0,
        quote = text ?: reason,
        sourceTag = sourceTag,
        sourceAudio = audio,
        sourceText = text,
        status = EventStatus.PENDING_REVIEW,
    )
}
