package com.laxmi.app.agents

data class ExtractionItem(
    val kind: String,          // commitment | settlement | correction
    val party: String,
    val direction: String,     // owed_to_me | i_owe
    val type: String,          // payment | delivery | service
    val firmness: String,      // firm | tentative
    val amountPhrase: String?,
    val amountGuess: Long?,    // rupees
    val quantity: Int?,
    val item: String?,
    val duePhrase: String?,
    val refersToExisting: Boolean,
    val confidence: Double,
    val quote: String,
)

sealed interface ExtractionResult {
    data class Success(val items: List<ExtractionItem>, val rawResponse: String) : ExtractionResult
    data class ParseFailure(val rawResponse: String, val error: String) : ExtractionResult
    data class EngineFailure(val error: String) : ExtractionResult
}

/**
 * One interface, two implementations possible (Plan A audio-native / Plan B
 * whisper->text) — see PLAN.md §"Model strategy". Nothing upstream should
 * depend on which one is wired in.
 */
interface ExtractionEngine {
    suspend fun initialize()
    suspend fun extract(audio: ByteArray? = null, text: String? = null): ExtractionResult

    /** Extract from a bill/receipt photo or UPI payment screenshot (by file path). */
    suspend fun extractImage(imagePath: String): ExtractionResult

    /** Free-form question over a pre-serialized ledger context; answers in the
     * question's language. */
    suspend fun query(question: String, ledgerContext: String): String

    /** Raw prompt completion (no wrapper) — for on-device language tasks like
     * composing reminders locally. */
    suspend fun generate(prompt: String): String

    /** On-device: turn a spoken goal (audio) into a short text goal. Keeps the raw
     * audio on the phone — only the derived text goes onward. */
    suspend fun generateFromAudio(audio: ByteArray, prompt: String): String

    /** Streaming variant: emits the cumulative answer text as tokens arrive. */
    fun queryStream(question: String, ledgerContext: String): kotlinx.coroutines.flow.Flow<String>

    /** Spoken-question streaming answer (always the ASK path). */
    fun queryAudioStream(audio: ByteArray, ledgerContext: String): kotlinx.coroutines.flow.Flow<String>

    /** Unified assistant turn: decides whether the audio states commitments or
     *  asks a question, and returns one or the other. */
    suspend fun assist(audio: ByteArray, ledgerContext: String): AssistResult

    /** Streaming unified turn: emits cumulative raw router text (intent line +
     *  payload) so the caller can show an ASK answer as it types out. */
    fun assistStream(audio: ByteArray, ledgerContext: String): kotlinx.coroutines.flow.Flow<String>
}

sealed interface AssistResult {
    data class Commitments(val result: ExtractionResult) : AssistResult
    data class Answer(val text: String) : AssistResult
    /** Voice-triggered action, e.g. "Rajesh ko reminder WhatsApp pe bhejo". */
    data class Action(val action: String, val party: String) : AssistResult
    data class Failure(val error: String) : AssistResult
}
