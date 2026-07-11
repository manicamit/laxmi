package com.laxmi.app.agents

/**
 * Kept in sync by hand with /validation/extraction-prompt.md — that file is what
 * gets pasted into AI Studio / Edge Gallery for prompt iteration; this is the
 * same text embedded for the on-device call. Update both together.
 */
object Prompts {

    val EXTRACTION_PROMPT = """
You extract business commitments from informal Indian voice notes (Hindi/English/Hinglish
mix, often noisy). Output ONLY a JSON array — no prose, no markdown fences.

One object per commitment or settlement found:

{
 "kind": "commitment" | "settlement" | "correction",
 "party": "<name of the other person/business>",
 "direction": "owed_to_me" | "i_owe",
 "type": "payment" | "delivery" | "service",
 "firmness": "firm" | "tentative",
 "amount_phrase": "<verbatim money words, e.g. '5 hazar'>" | null,
 "amount_guess": <number in rupees> | null,
 "quantity": <number> | null,
 "item": "<goods/service name>" | null,
 "due_phrase": "<verbatim time words, e.g. '5 tarikh tak'>" | null,
 "refers_to_existing": <true if it references a prior balance/hisaab, e.g. "purana
   hisaab clear kar dena", instead of stating a new amount>,
 "confidence": <0.0-1.0>,
 "quote": "<verbatim source phrase>"
}

Rules:
- "kind": a promise of future payment/delivery/service = "commitment"; money or goods
  already given/received = "settlement"; a statement disputing/amending an earlier amount
  ("nahi, 4000 hua tha") = "correction".
- "firmness": specific amount/date/item stated = "firm". Vague politeness, reassurance,
  or social talk ("de dunga yaar, tension mat le") = "tentative". Most friendly chatter
  contains NO firm commitments.
- "direction" is from the speaker's point of view: they will pay/deliver to the speaker =
  "owed_to_me"; the speaker will pay/deliver = "i_owe". Think carefully — a flipped
  direction is the worst possible error.
- Extract only concrete obligations. Greetings, chatter, and vague talk ("kabhi aa jana")
  produce NOTHING. An input may yield an empty array [].
- Copy amount_phrase and due_phrase verbatim; put your numeric interpretation only in
  amount_guess. Do not resolve dates to calendar dates.
- Lower confidence when the party is unclear, the amount is mumbled, or the obligation is
  implied rather than stated.

Example 1:
Input: "Haan Rajesh bhai, theek hai, 5 tarikh ko 5000 de dena. Aur suno, woh 20 cement
bags Sharma ji ko Friday tak pahunchane hain mujhe."
Output: [{"kind":"commitment","party":"Rajesh","direction":"owed_to_me","type":"payment",
"firmness":"firm","amount_phrase":"5000","amount_guess":5000,"quantity":null,"item":null,
"due_phrase":"5 tarikh ko","refers_to_existing":false,"confidence":0.9,
"quote":"5 tarikh ko 5000 de dena"},
{"kind":"commitment","party":"Sharma ji","direction":"i_owe","type":"delivery",
"firmness":"firm","amount_phrase":null,"amount_guess":null,"quantity":20,
"item":"cement bags","due_phrase":"Friday tak","refers_to_existing":false,
"confidence":0.85,"quote":"20 cement bags Sharma ji ko Friday tak pahunchane hain"}]

Example 2:
Input: "Arre haan haan, de dunga yaar, tension mat le. Chhota bhai hai tu mera."
Output: []

Input:
""".trimIndent()
}
