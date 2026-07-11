package com.laxmi.app.agents

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.laxmi.app.util.extractJsonArray
import com.laxmi.app.util.extractJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val TAG = "GemmaExtractionEngine"

/**
 * LiteRT-LM implementation of ExtractionEngine — Plan A, audio-native Gemma 4 E4B.
 *
 * NOTE: the com.google.ai.edge.litertlm.* class/method names below are ported from
 * fetched docs at build time and are UNVERIFIED against a real compile. If they don't
 * match the actual artifact, this is the ONLY file that should need fixing — that
 * isolation is the point of the ExtractionEngine interface. Check
 * https://developers.google.com/edge/litert-lm/android if this doesn't compile.
 */
class GemmaExtractionEngine(
    private val modelFile: File,
    private val cacheDir: File,
) : ExtractionEngine {

    private var engine: Engine? = null

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        // GPU first for speed; CPU fallback so a missing OpenCL driver can never
        // strand the demo (some devices hide/lack it even with the manifest entry).
        val backends = listOf(Backend.GPU(), Backend.CPU())
        var lastError: Throwable? = null
        for (backend in backends) {
            try {
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = backend,
                    // Encoders must be explicitly assigned or their executors never
                    // load (audio prompts failed with "Audio executor null"; images
                    // need the vision executor the same way). CPU pairs with both.
                    visionBackend = Backend.CPU(),
                    audioBackend = Backend.CPU(),
                    cacheDir = cacheDir.path,
                )
                val e = Engine(config)
                e.initialize()
                engine = e
                Log.i(TAG, "engine initialized with backend=${backend.name}")
                return@withContext
            } catch (t: Throwable) {
                Log.w(TAG, "backend ${backend.name} failed: ${t.message}")
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("no backend initialized")
    }

    override suspend fun extract(audio: ByteArray?, text: String?): ExtractionResult =
        withContext(Dispatchers.IO) {
            val e = engine
                ?: return@withContext ExtractionResult.EngineFailure(
                    "Engine not initialized — call initialize() first"
                )
            try {
                // Fresh conversation per call: extraction context must not
                // accumulate across notes (drift + context growth).
                e.createConversation().use { conv ->
                    val parts = buildList {
                        add(Content.Text(Prompts.EXTRACTION_PROMPT))
                        audio?.let { add(Content.AudioBytes(it)) }
                        text?.let { add(Content.Text(it)) }
                    }
                    val response = conv.sendMessage(Contents.of(*parts.toTypedArray()))
                    val responseText = response.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                    Log.i(TAG, "raw extract response: ${responseText.take(500)}")
                    parseResponse(responseText)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "extraction call failed", t)
                ExtractionResult.EngineFailure(t.message ?: "unknown engine error")
            }
        }

    override suspend fun query(question: String, ledgerContext: String): String =
        withContext(Dispatchers.IO) {
            val e = engine ?: return@withContext "Engine not ready"
            try {
                e.createConversation().use { conv ->
                    val prompt = """
You are Laxmi, a ledger assistant for an Indian small business. Below is the
user's ledger. Answer their question briefly and directly, in the same language
the question is asked in (Hinglish stays Hinglish). Use ₹ amounts from the
ledger only — never invent numbers. If the ledger doesn't answer it, say so.

LEDGER:
$ledgerContext

QUESTION: $question
""".trimIndent()
                    val response = conv.sendMessage(Contents.of(Content.Text(prompt)))
                    response.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                        .trim()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "query failed", t)
                "Query failed: ${t.message}"
            }
        }

    private fun queryPrompt(question: String, ledgerContext: String) = """
You are Laxmi, a ledger assistant for an Indian small business. Below is the
user's ledger. Answer their question briefly and directly, in the same language
the question is asked in (Hinglish stays Hinglish). Use ₹ amounts from the
ledger only — never invent numbers. If the ledger doesn't answer it, say so.

LEDGER:
$ledgerContext

QUESTION: $question
""".trimIndent()

    override suspend fun generateFromAudio(audio: ByteArray, prompt: String): String =
        withContext(Dispatchers.IO) {
            val e = engine ?: return@withContext ""
            try {
                e.createConversation().use { conv ->
                    conv.sendMessage(Contents.of(Content.Text(prompt), Content.AudioBytes(audio)))
                        .contents.contents.filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }.trim()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "generateFromAudio failed", t); ""
            }
        }

    override suspend fun extractImage(imagePath: String): ExtractionResult =
        withContext(Dispatchers.IO) {
            val e = engine ?: return@withContext ExtractionResult.EngineFailure("Engine not ready")
            try {
                e.createConversation().use { conv ->
                    val prompt = """
This image is from an Indian small business: either a bill/receipt/parchi, or a
UPI payment screenshot (PhonePe/GPay/Paytm). Extract money commitments or
settlements as a JSON array — no prose, no fences. Use this schema per object:
{"kind":"commitment|settlement","party":"<shop/person name>","direction":"owed_to_me|i_owe",
"type":"payment|delivery|service","firmness":"firm","amount_phrase":"<verbatim>",
"amount_guess":<number>,"quantity":null,"item":"<goods if any>","due_phrase":null,
"refers_to_existing":false,"confidence":<0-1>,"quote":"<text seen in image>"}

Rules:
- UPI screenshot = "settlement" (money already moved). "Paid to X" => direction
  i_owe (we paid them). "Received from X" => owed_to_me (they paid us).
- Supplier bill/receipt we have to pay = "commitment", direction i_owe, party =
  the shop/supplier.
- Empty array [] if no money info is visible.
""".trimIndent()
                    val response = conv.sendMessage(
                        Contents.of(Content.Text(prompt), Content.ImageFile(imagePath))
                    )
                    val text = response.contents.contents
                        .filterIsInstance<Content.Text>().joinToString("") { it.text }
                    Log.i(TAG, "raw image response: ${text.take(400)}")
                    parseResponse(text)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "image extraction failed", t)
                ExtractionResult.EngineFailure(t.message ?: "image error")
            }
        }

    override suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        val e = engine ?: return@withContext ""
        try {
            e.createConversation().use { conv ->
                conv.sendMessage(Contents.of(Content.Text(prompt))).contents.contents
                    .filterIsInstance<Content.Text>().joinToString("") { it.text }.trim()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "generate failed", t); ""
        }
    }

    override fun queryStream(question: String, ledgerContext: String) =
        kotlinx.coroutines.flow.flow {
            val e = engine ?: run { emit("Engine not ready"); return@flow }
            e.createConversation().use { conv ->
                var acc = ""
                conv.sendMessageAsync(Contents.of(Content.Text(queryPrompt(question, ledgerContext))))
                    .collect { msg ->
                        val t = msg.contents.contents
                            .filterIsInstance<Content.Text>().joinToString("") { it.text }
                        // Robust to cumulative-vs-delta streaming semantics.
                        acc = if (t.startsWith(acc)) t else acc + t
                        emit(acc.trim())
                    }
            }
        }.flowOn(Dispatchers.IO)

    override fun queryAudioStream(audio: ByteArray, ledgerContext: String) =
        kotlinx.coroutines.flow.flow {
            val e = engine ?: run { emit("Engine not ready"); return@flow }
            val prompt = """
You are Laxmi, a ledger assistant for an Indian small business. The user's spoken
question is in the audio. Answer briefly and directly in the question's language
(Hinglish stays Hinglish), using only the ledger below. Never invent numbers.

LEDGER:
$ledgerContext
""".trimIndent()
            e.createConversation().use { conv ->
                var acc = ""
                conv.sendMessageAsync(Contents.of(Content.Text(prompt), Content.AudioBytes(audio)))
                    .collect { msg ->
                        val t = msg.contents.contents
                            .filterIsInstance<Content.Text>().joinToString("") { it.text }
                        acc = if (t.startsWith(acc)) t else acc + t
                        emit(acc.trim())
                    }
            }
        }.flowOn(Dispatchers.IO)

    private fun routerPrompt(ledgerContext: String) = """
You are Laxmi, an Indian small-business ledger assistant. Listen to the audio.
Decide the user's intent and reply starting with exactly one intent line:

- "INTENT: RECORD" — they are stating a new commitment/payment/delivery. Follow
  it with the extraction JSON array (schema below).
- "INTENT: ASK" — they are asking about their existing ledger. Follow it with a
  short direct answer in the question's language (Hinglish stays Hinglish), using
  only the ledger.
- "INTENT: ACTION" — they are telling you to DO something for a party. Follow it
  with a JSON object {"action":"remind"|"receipt","party":"<name>"}.
  "remind" = send a payment reminder. "receipt" = send a confirmation.

LEDGER:
$ledgerContext

EXTRACTION SCHEMA (for RECORD): a JSON array of objects with keys kind, party,
direction (owed_to_me|i_owe), type, firmness, amount_phrase, amount_guess,
quantity, item, due_phrase, refers_to_existing, confidence, quote.
""".trimIndent()

    /** Streams the cumulative raw router output (intent line + payload). */
    override fun assistStream(audio: ByteArray, ledgerContext: String) =
        kotlinx.coroutines.flow.flow {
            val e = engine ?: run { emit("INTENT: ASK\nEngine not ready"); return@flow }
            e.createConversation().use { conv ->
                var acc = ""
                conv.sendMessageAsync(
                    Contents.of(Content.Text(routerPrompt(ledgerContext)), Content.AudioBytes(audio))
                ).collect { msg ->
                    val t = msg.contents.contents
                        .filterIsInstance<Content.Text>().joinToString("") { it.text }
                    acc = if (t.startsWith(acc)) t else acc + t
                    emit(acc)
                }
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun assist(audio: ByteArray, ledgerContext: String): AssistResult =
        withContext(Dispatchers.IO) {
            val e = engine ?: return@withContext AssistResult.Failure("Engine not ready")
            try {
                e.createConversation().use { conv ->
                    val router = """
You are Laxmi, an Indian small-business ledger assistant. Listen to the audio.
Decide the user's intent and reply starting with exactly one intent line:

- "INTENT: RECORD" — they are stating a new commitment/payment/delivery. Follow
  it with the extraction JSON array (schema below).
- "INTENT: ASK" — they are asking about their existing ledger. Follow it with a
  short direct answer in the question's language (Hinglish stays Hinglish), using
  only the ledger.
- "INTENT: ACTION" — they are telling you to DO something for a party. Follow it
  with a JSON object {"action":"remind"|"receipt","party":"<name>"}.
  "remind" = send a payment reminder ("Rajesh ko reminder bhejo").
  "receipt" = send a confirmation of what was agreed ("Rajesh ko receipt bhejo",
  "confirmation bhej do").

LEDGER:
$ledgerContext

EXTRACTION SCHEMA (for RECORD): a JSON array of objects with keys kind, party,
direction (owed_to_me|i_owe), type, firmness, amount_phrase, amount_guess,
quantity, item, due_phrase, refers_to_existing, confidence, quote.
""".trimIndent()
                    val response = conv.sendMessage(
                        Contents.of(Content.Text(router), Content.AudioBytes(audio))
                    )
                    val text = response.contents.contents
                        .filterIsInstance<Content.Text>().joinToString("") { it.text }
                    when {
                        text.contains("INTENT: ACTION", ignoreCase = true) -> {
                            val obj = extractJsonObject(text)?.let { org.json.JSONObject(it) }
                            AssistResult.Action(
                                action = obj?.optString("action", "remind") ?: "remind",
                                party = obj?.optString("party", "") ?: "",
                            )
                        }
                        text.contains("INTENT: ASK", ignoreCase = true) -> {
                            val answer = text.substringAfter("INTENT: ASK", "")
                                .substringAfter("\n").trim().ifBlank { text.trim() }
                            AssistResult.Answer(answer)
                        }
                        else -> AssistResult.Commitments(parseResponse(text))
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "assist failed", t)
                AssistResult.Failure(t.message ?: "assist error")
            }
        }

    /** Public so the streaming router can reuse its own output without re-inferring. */
    fun parseExtraction(raw: String): ExtractionResult = parseResponse(raw)

    private fun parseResponse(raw: String): ExtractionResult {
        val jsonText = extractJsonArray(raw)
            ?: return ExtractionResult.ParseFailure(raw, "no JSON array found in response")
        return try {
            val arr = JSONArray(jsonText)
            val items = buildList {
                for (i in 0 until arr.length()) {
                    add(arr.getJSONObject(i).toExtractionItem())
                }
            }
            ExtractionResult.Success(items, raw)
        } catch (e: Exception) {
            ExtractionResult.ParseFailure(raw, e.message ?: "JSON parse error")
        }
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

    private fun JSONObject.longOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.intOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.toExtractionItem() = ExtractionItem(
        kind = optString("kind", "commitment"),
        party = optString("party", ""),
        direction = optString("direction", "owed_to_me"),
        type = optString("type", "payment"),
        firmness = optString("firmness", "firm"),
        amountPhrase = stringOrNull("amount_phrase"),
        amountGuess = longOrNull("amount_guess"),
        quantity = intOrNull("quantity"),
        item = stringOrNull("item"),
        duePhrase = stringOrNull("due_phrase"),
        refersToExisting = optBoolean("refers_to_existing", false),
        confidence = optDouble("confidence", 0.5),
        quote = optString("quote", ""),
    )
}
