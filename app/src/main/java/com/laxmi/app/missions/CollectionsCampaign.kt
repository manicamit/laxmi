package com.laxmi.app.missions

import com.laxmi.app.util.extractJsonArray
import com.laxmi.app.util.extractJsonObject
import org.json.JSONArray
import org.json.JSONObject

/**
 * Multi-agent collection drive (Track 2 — Managed Agents / Antigravity).
 * Three cloud agents collaborate with handoff + conflict resolution:
 *   Strategist -> Composer -> Auditor (-> Composer again if a message is flagged).
 * Each agent sees ONLY the derived summary the user approved — never raw ledger.
 */
object CollectionsCampaign {

    data class Debtor(val party: String, val amountInr: Long, val duePhrase: String?)

    data class PlannedParty(val party: String, val priority: Int, val tone: String, val reason: String)
    data class CampaignMessage(val party: String, val amountInr: Long, val text: String, val toneOk: Boolean)

    /** The exact payload shown on the consent sheet and sent to the cloud. */
    fun briefJson(debtors: List<Debtor>): String = JSONArray().apply {
        debtors.forEach {
            put(JSONObject().put("party", it.party).put("amount_inr", it.amountInr)
                .put("due", it.duePhrase ?: "pending"))
        }
    }.toString()

    // ---- Agent 1: Strategist ----
    fun strategistPrompt(brief: String) = """
You are the STRATEGIST agent in a small-business collections team. Given this list
of parties who owe the shopkeeper money (derived summary only), produce a
prioritized plan: who to chase first and the right tone for each. Consider amount
and how overdue. Output ONLY JSON:
[{"party":"..","priority":1,"tone":"gentle|firm|final","reason":".."}]

DEBTORS: $brief
""".trimIndent()

    fun parseStrategy(out: String): List<PlannedParty> {
        val arr = extractJsonArray(out)?.let { JSONArray(it) } ?: return emptyList()
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PlannedParty(o.optString("party"), o.optInt("priority", 99),
                o.optString("tone", "gentle"), o.optString("reason", ""))
        }.sortedBy { it.priority }
    }

    // ---- Agent 2: Composer ----
    fun composerPrompt(plan: List<PlannedParty>, brief: String) = """
You are the COMPOSER agent. For each party below, write a short, WhatsApp-ready
payment reminder in warm, respectful Hinglish, matching the assigned tone. Output
ONLY JSON: [{"party":"..","text":".."}]

PLAN: ${JSONArray().apply {
        plan.forEach { put(JSONObject().put("party", it.party).put("tone", it.tone)) }
    }}
AMOUNTS: $brief
""".trimIndent()

    // ---- Agent 3: Auditor (conflict resolution) ----
    fun auditorPrompt(messagesJson: String) = """
You are the AUDITOR agent enforcing respectful, fair collection practices (no
threats, no shaming). Review each message. Output ONLY JSON:
[{"party":"..","tone_ok":true|false,"fixed_text":"<if not ok, a respectful rewrite; else repeat original>"}]

MESSAGES: $messagesJson
""".trimIndent()

    /** Merge composer output + auditor verdicts into final messages. */
    fun finalize(
        composerOut: String,
        auditorOut: String,
        debtors: List<Debtor>,
    ): List<CampaignMessage> {
        val composed = extractJsonArray(composerOut)?.let { JSONArray(it) } ?: JSONArray()
        val audits = extractJsonArray(auditorOut)?.let { JSONArray(it) } ?: JSONArray()
        val auditByParty = (0 until audits.length()).associate {
            val o = audits.getJSONObject(it); o.optString("party") to o
        }
        val amountByParty = debtors.associate { it.party to it.amountInr }
        return (0 until composed.length()).map {
            val o = composed.getJSONObject(it)
            val party = o.optString("party")
            val audit = auditByParty[party]
            val ok = audit?.optBoolean("tone_ok", true) ?: true
            val text = if (!ok) audit?.optString("fixed_text").orEmpty().ifBlank { o.optString("text") }
            else o.optString("text")
            CampaignMessage(party, amountByParty[party] ?: 0, text, ok)
        }
    }
}
