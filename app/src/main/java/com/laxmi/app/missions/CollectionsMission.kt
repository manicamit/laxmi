package com.laxmi.app.missions

import com.laxmi.app.util.extractJsonObject
import org.json.JSONObject

data class ReminderStep(val step: Int, val tone: String, val text: String)

/**
 * Collections mission: derived summary in (the consent-sheet payload, verbatim),
 * WhatsApp-ready reminder ladder out. Sending stays human-in-the-loop — the app
 * never messages anyone by itself.
 */
object CollectionsMission {

    /** The EXACT payload shown on the consent sheet and sent to the cloud. */
    fun brief(party: String, amountRupees: Long, duePhrase: String?): String =
        JSONObject()
            .put("party_display", party)
            .put("language", "Hinglish")
            .put("amount_inr", amountRupees)
            .put("due_phrase", duePhrase ?: "pending")
            .put("relationship", "regular customer")
            .toString()

    fun prompt(briefJson: String): String = """
You are the Composer agent in a collections workflow for an Indian small-business
ledger app. Mission brief (derived summary only — you never see raw records):
$briefJson
Produce JSON only: {"messages":[{"step":1,"tone":"gentle","text":"..."},
{"step":2,"tone":"firm","text":"..."},{"step":3,"tone":"final","text":"..."}]}
Each text: a short WhatsApp-ready reminder in respectful, natural Hinglish
(informal rapport, not formal Hindi).
""".trimIndent()

    fun parseLadder(modelOutput: String): List<ReminderStep> {
        val json = extractJsonObject(modelOutput) ?: return emptyList()
        val arr = JSONObject(json).optJSONArray("messages") ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    ReminderStep(
                        step = o.optInt("step", i + 1),
                        tone = o.optString("tone", ""),
                        text = o.optString("text", ""),
                    )
                )
            }
        }
    }
}
