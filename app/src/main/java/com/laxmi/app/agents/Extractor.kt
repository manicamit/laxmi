package com.laxmi.app.agents

import android.content.Context
import com.laxmi.app.data.Direction
import com.laxmi.app.data.EventKind
import com.laxmi.app.data.EventStatus
import com.laxmi.app.data.LedgerEvent
import com.laxmi.app.data.LedgerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

enum class EngineState { NO_MODEL, LOADING, READY, ERROR }

/**
 * Process-level owner of the single Gemma engine. One instance for the whole app
 * (the model is ~4 GB — never load it twice). Warmed at Application start so the
 * assist overlay and the main UI share one warm engine; the mutex serializes all
 * inference (PLAN.md).
 */
object Extractor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var engine: ExtractionEngine? = null

    val state = MutableStateFlow(EngineState.NO_MODEL)
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    lateinit var modelFile: File
        private set

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        modelFile = File(File(context.filesDir, "models").apply { mkdirs() }, "gemma-4-E4B-it.litertlm")
        if (modelFile.exists()) warm(context)
    }

    // Audio captured before the engine was ready — drained once it warms.
    private val pending = mutableListOf<Pair<ByteArray, String>>()

    fun warm(context: Context) {
        if (state.value == EngineState.READY || state.value == EngineState.LOADING) return
        state.value = EngineState.LOADING
        scope.launch {
            try {
                val e = GemmaExtractionEngine(modelFile, context.cacheDir)
                e.initialize()
                engine = e
                state.value = EngineState.READY
                // Pay the one-time graph-compile cost NOW (background), so the user's
                // first real question isn't the one that waits ~15s for it. MUST hold
                // the mutex — concurrent inference on the single native engine hangs.
                runCatching { mutex.withLock { e.query("test", "(empty)") } }
                drainPending()
            } catch (t: Throwable) {
                error.value = t.message
                state.value = EngineState.ERROR
            }
        }
    }

    val isReady: Boolean get() = state.value == EngineState.READY

    /** Cold-start safety: stash audio now, extract when the engine is ready. */
    fun enqueue(audio: ByteArray, sourceTag: String) {
        synchronized(pending) { pending.add(audio to sourceTag) }
        if (isReady) drainPending()
    }

    private fun drainPending() {
        val batch = synchronized(pending) { pending.toList().also { pending.clear() } }
        batch.forEach { (audio, tag) -> ingest(audio = audio, sourceTag = tag) }
    }

    /** Fire-and-forget: extract in the background, append results to the ledger,
     *  invoke [onDone] on completion (for a notification/toast). Never blocks the
     *  caller — recording is already finished by the time this is called. */
    fun ingest(
        audio: ByteArray? = null,
        text: String? = null,
        sourceTag: String,
        partyOverride: String? = null,
        flipDirection: Boolean = false,
        onDone: (Int) -> Unit = {},
    ) {
        scope.launch {
            busy.value = true
            // Cold process (e.g. a share right after launch): wait for the engine
            // to finish warming rather than filing "engine not ready".
            if (engine == null) {
                warm(appContext)
                withTimeoutOrNull(120_000) {
                    state.first { it == EngineState.READY || it == EngineState.ERROR }
                }
            }
            mutex.withLock {
                val e = engine
                if (e == null) {
                    LedgerStore.append(unfiled(audio, text, sourceTag, "engine not ready"))
                    onDone(0)
                    busy.value = false
                    return@withLock
                }
                when (val result = e.extract(audio = audio, text = text)) {
                    is ExtractionResult.Success -> {
                        if (result.items.isEmpty()) {
                            LedgerStore.append(unfiled(audio, text, sourceTag, "no commitments"))
                            com.laxmi.app.Notifier.show(appContext, "Laxmi", "Kuch samajh nahi aaya — dobara bolo (Inbox mein hai)")
                            onDone(0)
                        } else {
                            val created = result.items.map {
                                LedgerStore.fromExtraction(it, sourceTag, audio, text, partyOverride, flipDirection)
                                    .also { ev -> LedgerStore.append(ev) }
                            }
                            notifyResult(created)
                            onDone(created.size)
                        }
                    }
                    is ExtractionResult.ParseFailure -> {
                        LedgerStore.append(unfiled(audio, text, sourceTag, result.error))
                        com.laxmi.app.Notifier.show(appContext, "Laxmi", "Samajhne mein dikkat — Inbox mein daal diya")
                        onDone(0)
                    }
                    is ExtractionResult.EngineFailure -> {
                        LedgerStore.append(unfiled(audio, text, sourceTag, result.error))
                        com.laxmi.app.Notifier.show(appContext, "Laxmi", "Engine error — Inbox mein daal diya")
                        onDone(0)
                    }
                }
            }
            busy.value = false
        }
    }

    suspend fun query(question: String, ledgerContext: String): String =
        mutex.withLock { engine?.query(question, ledgerContext) ?: "Engine not ready" }

    /** Background share-in: decode a copied audio file, extract, notify. The UI
     *  (share picker) has already dismissed — this runs fire-and-forget. */
    fun ingestSharedAudioFile(path: String, partyOverride: String?, fromMe: Boolean) {
        scope.launch {
            val wav = withContext(Dispatchers.IO) { com.laxmi.app.audio.AudioDecoder.decodeToWav(path) }
            runCatching { java.io.File(path).delete() }
            if (wav == null) {
                com.laxmi.app.Notifier.show(appContext, "Laxmi", "Voice note samajh nahi aaya")
                return@launch
            }
            // Counterparty's note -> flip direction for the user's ledger.
            ingest(audio = wav, sourceTag = "whatsapp-audio", partyOverride = partyOverride, flipDirection = !fromMe)
        }
    }

    /** Background image share-in: bill/receipt/UPI screenshot -> extract -> notify. */
    fun ingestSharedImageFile(path: String, partyOverride: String?, fromMe: Boolean) {
        scope.launch {
            busy.value = true
            if (engine == null) {
                warm(appContext)
                withTimeoutOrNull(120_000) { state.first { it == EngineState.READY || it == EngineState.ERROR } }
            }
            mutex.withLock {
                val e = engine
                if (e == null) {
                    com.laxmi.app.Notifier.show(appContext, "Laxmi", "Engine ready nahi — dobara try karo")
                } else when (val r = e.extractImage(path)) {
                    is ExtractionResult.Success -> {
                        if (r.items.isEmpty()) {
                            com.laxmi.app.Notifier.show(appContext, "Laxmi", "Image mein koi hisaab nahi mila")
                        } else {
                            val created = r.items.map {
                                LedgerStore.fromExtraction(it, "whatsapp-image", null, "shared image", partyOverride, !fromMe)
                                    .also { ev -> LedgerStore.append(ev) }
                            }
                            notifyResult(created)
                        }
                    }
                    else -> com.laxmi.app.Notifier.show(appContext, "Laxmi", "Image samajh nahi aayi")
                }
            }
            runCatching { java.io.File(path).delete() }
            busy.value = false
        }
    }

    /** Streaming query: [onToken] receives the cumulative answer as it grows. */
    suspend fun streamQuery(question: String, ledgerContext: String, onToken: (String) -> Unit) {
        val e = engine ?: run { onToken("Engine ready ho rahi hai…"); return }
        mutex.withLock {
            e.queryStream(question, ledgerContext).collect { onToken(it) }
        }
    }

    /** Spoken-question streaming answer. */
    suspend fun streamQueryAudio(audio: ByteArray, onToken: (String) -> Unit) {
        val e = engine ?: run { onToken("Engine ready ho rahi hai…"); return }
        mutex.withLock {
            e.queryAudioStream(audio, ledgerContext()).collect { onToken(it) }
        }
    }

    fun ledgerContext(): String =
        LedgerStore.events.value
            .filter { it.status != EventStatus.REJECTED && it.type != "unfiled" }
            .joinToString("\n") { ev ->
                val amount = ev.amountPaise?.let { "₹${it / 100}" }
                    ?: listOfNotNull(ev.quantity?.toString(), ev.item).joinToString(" ")
                val dir = if (ev.direction == Direction.OWED_TO_ME) "unse lene hain" else "unko dene hain"
                "- ${ev.party}: $amount $dir" + (ev.duePhrase?.let { ", due $it" } ?: "")
            }.ifBlank { "(ledger khaali hai)" }

    /** Unified assistant turn (auto-detect record vs ask). [onRecorded] fires with
     *  how many entries were added; [onAnswer] fires with a spoken answer. */
    /** Streaming unified assistant turn. [onPartialAnswer] fires repeatedly as an
     *  ASK answer types out; the others fire once at completion. */
    fun assistStreaming(
        audio: ByteArray,
        onPartialAnswer: (String) -> Unit,
        onRecorded: (Int) -> Unit,
        onAction: (String, String) -> Unit,
    ) {
        scope.launch {
            busy.value = true
            mutex.withLock {
                val e = engine
                if (e == null) {
                    LedgerStore.append(unfiled(audio, null, "assist", "engine not ready")); onRecorded(0)
                    busy.value = false; return@withLock
                }
                var full = ""
                var isAsk = false
                e.assistStream(audio, ledgerContext()).collect { acc ->
                    full = acc
                    if (!isAsk && acc.contains("INTENT: ASK", ignoreCase = true)) isAsk = true
                    if (isAsk) {
                        val answer = acc.substringAfter("INTENT: ASK", "").substringAfter("\n").trim()
                        if (answer.isNotEmpty()) onPartialAnswer(answer)
                    }
                }
                // Finalize on the completed text.
                when {
                    full.contains("INTENT: ACTION", ignoreCase = true) -> {
                        val obj = com.laxmi.app.util.extractJsonObject(full)?.let { org.json.JSONObject(it) }
                        onAction(obj?.optString("action", "remind") ?: "remind", obj?.optString("party", "") ?: "")
                    }
                    isAsk -> {
                        val answer = full.substringAfter("INTENT: ASK", "").substringAfter("\n").trim()
                        onPartialAnswer(answer.ifBlank { full.trim() })
                    }
                    else -> {
                        // RECORD: reuse the router's own JSON — no second inference.
                        val parsed = (e as? GemmaExtractionEngine)?.parseExtraction(full)
                        val items = (parsed as? ExtractionResult.Success)?.items.orEmpty()
                        if (items.isEmpty()) {
                            LedgerStore.append(unfiled(audio, null, "assist", "no commitments")); onRecorded(0)
                        } else {
                            items.forEach { LedgerStore.append(LedgerStore.fromExtraction(it, "assist", audio, null)) }
                            onRecorded(items.size)
                        }
                    }
                }
            }
            busy.value = false
        }
    }

    fun assist(
        audio: ByteArray,
        onRecorded: (Int) -> Unit,
        onAnswer: (String) -> Unit,
        onAction: (String, String) -> Unit = { _, _ -> },
    ) {
        scope.launch {
            busy.value = true
            mutex.withLock {
                val e = engine
                if (e == null) {
                    LedgerStore.append(unfiled(audio, null, "assist", "engine not ready")); onRecorded(0)
                } else {
                    when (val r = e.assist(audio, ledgerContext())) {
                        is AssistResult.Answer -> onAnswer(r.text)
                        is AssistResult.Action -> onAction(r.action, r.party)
                        is AssistResult.Commitments -> {
                            val items = (r.result as? ExtractionResult.Success)?.items.orEmpty()
                            if (items.isEmpty()) {
                                LedgerStore.append(unfiled(audio, null, "assist", "no commitments")); onRecorded(0)
                            } else {
                                items.forEach { LedgerStore.append(LedgerStore.fromExtraction(it, "assist", audio, null)) }
                                onRecorded(items.size)
                            }
                        }
                        is AssistResult.Failure -> {
                            LedgerStore.append(unfiled(audio, null, "assist", r.error)); onRecorded(0)
                        }
                    }
                }
            }
            busy.value = false
        }
    }

    private fun notifyResult(created: List<LedgerEvent>) {
        val pending = created.count { it.status == EventStatus.PENDING_REVIEW }
        val confirmed = created.count { it.status == EventStatus.CONFIRMED }
        val parties = created.map { it.party }.distinct().joinToString(", ")
        val msg = when {
            pending > 0 && confirmed > 0 -> "$parties: ${created.size} entry — $pending review ke liye Inbox mein"
            pending > 0 -> "$parties: $pending entry Inbox mein — check kar lo"
            else -> "$parties: $confirmed entry ledger mein add hui ✓"
        }
        com.laxmi.app.Notifier.show(appContext, "Laxmi", msg)
    }

    private fun unfiled(audio: ByteArray?, text: String?, sourceTag: String, reason: String) =
        LedgerEvent(
            party = "Unfiled", kind = EventKind.COMMITMENT, direction = Direction.OWED_TO_ME,
            type = "unfiled", amountPaise = null, quantity = null, item = null, duePhrase = null,
            firmness = "tentative", confidence = 0.0, quote = text ?: reason,
            sourceTag = sourceTag, sourceAudio = audio, sourceText = text,
            status = EventStatus.PENDING_REVIEW,
        )
}
