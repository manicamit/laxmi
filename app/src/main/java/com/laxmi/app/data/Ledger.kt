package com.laxmi.app.data

import com.laxmi.app.agents.ExtractionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

enum class EventKind { COMMITMENT, SETTLEMENT, CORRECTION }
enum class Direction { OWED_TO_ME, I_OWE }
enum class EventStatus { PENDING_REVIEW, CONFIRMED, REJECTED }

/**
 * Counterparty acknowledgement — the answer to "how does the other person
 * validate?". NONE → receipt SENT via WhatsApp → they reply → CONFIRMED_BY_THEM
 * or DISPUTED (marked manually from their reply in v0).
 */
enum class AckStatus { NONE, SENT, CONFIRMED_BY_THEM, DISPUTED }

/**
 * Append-only: settlements/corrections never mutate earlier events; balances are
 * derived (PLAN.md §3b). In-memory for the hackathon build — swap for Room later
 * without changing the shape.
 */
data class LedgerEvent(
    val id: String = UUID.randomUUID().toString(),
    val party: String,
    val kind: EventKind,
    val direction: Direction,
    val type: String,                 // payment | delivery | service
    val amountPaise: Long?,           // integer paise, never floats
    val quantity: Int?,
    val item: String?,
    val duePhrase: String?,
    val firmness: String,
    val confidence: Double,
    val quote: String,
    val sourceTag: String,            // mic | whatsapp-text | ...
    val sourceAudio: ByteArray?,      // WAV bytes; the evidence
    val sourceText: String?,          // verbatim text; the evidence for text inputs
    val status: EventStatus,
    val ackStatus: AckStatus = AckStatus.NONE,
    val createdAt: Long = System.currentTimeMillis(),
)

data class PartyBalance(
    val party: String,
    val netPaise: Long,               // >0: they owe me; <0: I owe them
    val pendingCount: Int,
)

object LedgerStore {

    private val _events = MutableStateFlow<List<LedgerEvent>>(emptyList())
    val events: StateFlow<List<LedgerEvent>> = _events

    private var storageDir: java.io.File? = null

    /** Call once from Application.onCreate. Loads persisted events + evidence. */
    fun init(filesDir: java.io.File) {
        val dir = java.io.File(filesDir, "ledger").apply { mkdirs() }
        storageDir = dir
        val index = java.io.File(dir, "events.json")
        if (!index.exists()) return
        runCatching {
            val arr = org.json.JSONArray(index.readText())
            val loaded = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val audioFile = java.io.File(dir, "${o.getString("id")}.wav")
                    add(
                        LedgerEvent(
                            id = o.getString("id"),
                            party = o.getString("party"),
                            kind = EventKind.valueOf(o.getString("kind")),
                            direction = Direction.valueOf(o.getString("direction")),
                            type = o.getString("type"),
                            amountPaise = if (o.isNull("amountPaise")) null else o.getLong("amountPaise"),
                            quantity = if (o.isNull("quantity")) null else o.getInt("quantity"),
                            item = if (o.isNull("item")) null else o.getString("item"),
                            duePhrase = if (o.isNull("duePhrase")) null else o.getString("duePhrase"),
                            firmness = o.optString("firmness", "firm"),
                            confidence = o.optDouble("confidence", 0.5),
                            quote = o.optString("quote", ""),
                            sourceTag = o.optString("sourceTag", "mic"),
                            sourceAudio = if (audioFile.exists()) audioFile.readBytes() else null,
                            sourceText = if (o.isNull("sourceText")) null else o.getString("sourceText"),
                            status = EventStatus.valueOf(o.getString("status")),
                            ackStatus = AckStatus.valueOf(o.optString("ackStatus", "NONE")),
                            createdAt = o.optLong("createdAt", 0L),
                        )
                    )
                }
            }
            _events.value = loaded
        }
    }

    private fun persist() {
        val dir = storageDir ?: return
        runCatching {
            val arr = org.json.JSONArray()
            _events.value.forEach { e ->
                arr.put(
                    org.json.JSONObject()
                        .put("id", e.id)
                        .put("party", e.party)
                        .put("kind", e.kind.name)
                        .put("direction", e.direction.name)
                        .put("type", e.type)
                        .put("amountPaise", e.amountPaise ?: org.json.JSONObject.NULL)
                        .put("quantity", e.quantity ?: org.json.JSONObject.NULL)
                        .put("item", e.item ?: org.json.JSONObject.NULL)
                        .put("duePhrase", e.duePhrase ?: org.json.JSONObject.NULL)
                        .put("firmness", e.firmness)
                        .put("confidence", e.confidence)
                        .put("quote", e.quote)
                        .put("sourceTag", e.sourceTag)
                        .put("sourceText", e.sourceText ?: org.json.JSONObject.NULL)
                        .put("status", e.status.name)
                        .put("ackStatus", e.ackStatus.name)
                        .put("createdAt", e.createdAt)
                )
                e.sourceAudio?.let { audio ->
                    val f = java.io.File(dir, "${e.id}.wav")
                    if (!f.exists()) f.writeBytes(audio)
                }
            }
            java.io.File(dir, "events.json").writeText(arr.toString())
        }
    }

    fun append(event: LedgerEvent) {
        _events.value = _events.value + event
        persist()
    }

    fun setStatus(id: String, status: EventStatus) {
        _events.value = _events.value.map { if (it.id == id) it.copy(status = status) else it }
        persist()
    }

    fun setAck(id: String, ack: AckStatus) {
        _events.value = _events.value.map { if (it.id == id) it.copy(ackStatus = ack) else it }
        persist()
    }

    /** Voice-action reminder text for a party, from their outstanding balance. */
    fun reminderText(party: String): String {
        val all = _events.value.filter {
            it.party.equals(party, ignoreCase = true) &&
                it.status != EventStatus.REJECTED && it.type != "unfiled"
        }
        val net = balances(all).firstOrNull()?.netPaise ?: 0
        val amount = "₹%,d".format(kotlin.math.abs(net) / 100)
        val due = all.firstOrNull { it.duePhrase != null }?.duePhrase
        return "🙏 $party, ek chhota sa reminder: $amount ka hisaab pending hai" +
            (due?.let { " ($it)" } ?: "") + ". Jab sahulat ho, clear kar dijiyega. Dhanyawad!"
    }

    /** Human-first WhatsApp receipt; the counterparty needs no app to validate. */
    fun receiptText(e: LedgerEvent): String {
        val what = e.amountPaise?.let { "₹%,d".format(it / 100) }
            ?: listOfNotNull(e.quantity?.toString(), e.item).joinToString(" ").ifBlank { e.type }
        val direction = if (e.direction == Direction.OWED_TO_ME) "aap denge" else "hum denge"
        val due = e.duePhrase?.let { ", $it" } ?: ""
        return "🙏 ${e.party}, hisaab likh liya hai: $what $direction$due.\n" +
            "Aapne kaha tha: \"${e.quote}\"\n" +
            "Sahi hai? Haan ya Nahi reply kar dena. — Laxmi"
    }

    fun fromExtraction(
        item: ExtractionItem,
        sourceTag: String,
        sourceAudio: ByteArray?,
        sourceText: String?,
        partyOverride: String? = null,
    ): LedgerEvent {
        val autoAccept = item.confidence >= 0.85 && item.firmness == "firm"
        return LedgerEvent(
            party = partyOverride?.takeIf { it.isNotBlank() }
                ?: item.party.trim().removeSuffix(" ji").removeSuffix(" bhai").trim()
                    .ifBlank { "Unknown" },
            kind = when (item.kind) {
                "settlement" -> EventKind.SETTLEMENT
                "correction" -> EventKind.CORRECTION
                else -> EventKind.COMMITMENT
            },
            direction = if (item.direction == "i_owe") Direction.I_OWE else Direction.OWED_TO_ME,
            type = item.type,
            amountPaise = item.amountGuess?.let { it * 100 },
            quantity = item.quantity,
            item = item.item,
            duePhrase = item.duePhrase,
            firmness = item.firmness,
            confidence = item.confidence,
            quote = item.quote,
            sourceTag = sourceTag,
            sourceAudio = sourceAudio,
            sourceText = sourceText,
            status = if (autoAccept) EventStatus.CONFIRMED else EventStatus.PENDING_REVIEW,
        )
    }

    /** Signed contribution of one confirmed event to its party's net balance. */
    private fun signedAmount(e: LedgerEvent): Long {
        val amount = e.amountPaise ?: return 0
        val sign = if (e.direction == Direction.OWED_TO_ME) 1 else -1
        return when (e.kind) {
            EventKind.COMMITMENT -> sign * amount
            // A settlement records money actually moving, reducing what's owed
            // in that direction.
            EventKind.SETTLEMENT -> -sign * amount
            EventKind.CORRECTION -> 0 // corrections adjust via review, not math, in v0
        }
    }

    /** Distinct known party names, most-recent first — for the share-in picker. */
    fun partyNames(): List<String> =
        _events.value
            .filter { it.type != "unfiled" && it.party.isNotBlank() && it.party != "Unfiled" }
            .sortedByDescending { it.createdAt }
            .map { it.party }
            .distinct()

    fun balances(all: List<LedgerEvent>): List<PartyBalance> =
        // Count everything not explicitly rejected, so the ledger updates the moment
        // a note is recorded; the Inbox refines (reject) rather than gates.
        all.filter { it.status != EventStatus.REJECTED && it.type != "unfiled" }
            .groupBy { it.party }
            .map { (party, events) ->
                PartyBalance(
                    party = party,
                    netPaise = events.sumOf(::signedAmount),
                    pendingCount = events.count { it.kind == EventKind.COMMITMENT },
                )
            }
            .sortedByDescending { kotlin.math.abs(it.netPaise) }
}
