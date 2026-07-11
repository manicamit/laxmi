package com.laxmi.app.missions

/**
 * Prompts for the Antigravity (managed-agents) cloud brain — Track 2. These
 * explicitly drive the sandbox's tools (code execution, web search, file
 * generation) so the agents genuinely plan → use tools → produce artifacts,
 * not just chat. Each agent receives ONLY the consented derived summary.
 */
object AgentWorkflows {

    // ---- Credit-readiness dossier: 3-agent pipeline (code + web + synthesis) ----

    /** Agent 1 — ANALYST: uses code execution to compute metrics. */
    fun analystPrompt(ledgerSummary: String) = """
You are the ANALYST agent. Using CODE EXECUTION, compute from this ledger summary:
total receivables, total payables, net position, active counterparties, and a
"reliability score" (settled vs pending share, 0-100). Output ONLY compact JSON:
{"receivables":..,"payables":..,"net":..,"counterparties":..,"reliability":..}

LEDGER SUMMARY:
$ledgerSummary
""".trimIndent()

    /** Agent 2 — RESEARCHER: uses web search for current credit options. */
    fun researcherPrompt(metrics: String) = """
You are the RESEARCHER agent. Given these business metrics, use WEB SEARCH to find
2-3 currently relevant Indian micro-business credit options (e.g. Mudra
Shishu/Kishore/Tarun limits, typical interest ranges, eligibility). Output a short
bulleted list with the scheme name, limit, and one eligibility note each.

METRICS: $metrics
""".trimIndent()

    /** Agent 3 — ADVISOR: synthesizes a loan-readiness recommendation. */
    fun advisorPrompt(metrics: String, research: String) = """
You are the ADVISOR agent. $SPOKEN
Say the shopkeeper's standing in one line, which ONE scheme fits best, and one
thing that would strengthen the case. End by offering to send details on WhatsApp.

METRICS: $metrics

SCHEMES: $research
""".trimIndent()

    // ---- Bazaar / procurement agent: reach the live market (web + code) ----

    /** Agent 1 — MARKET RESEARCHER: web-searches today's prices for bought goods. */
    fun marketResearcherPrompt(purchaseSummary: String) = """
You are the MARKET RESEARCHER agent. This Indian shopkeeper buys the goods below.
Use WEB SEARCH to find the CURRENT typical wholesale/market price in India for each
distinct item (with a unit). Output ONLY JSON:
[{"item":"..","market_price":"..","source":".."}]

PURCHASES (what they bought and paid):
$purchaseSummary
""".trimIndent()

    /** Agent 2 — PRICE ANALYST: code-compares paid vs market, computes savings. */
    fun marketAnalystPrompt(purchaseSummary: String, marketPrices: String) = """
You are the PRICE ANALYST agent. Using CODE EXECUTION, compare what the shopkeeper
PAID against the MARKET prices. For each item where they overpay, compute the gap
and estimated monthly saving. Output ONLY JSON:
[{"item":"..","paid":"..","market":"..","overpaying":true|false,"note":".."}]

PAID: $purchaseSummary
MARKET: $marketPrices
""".trimIndent()

    /** Agent 3 — BAZAAR ADVISOR: actionable recommendation. */
    fun marketAdvisorPrompt(analysis: String) = """
You are the BAZAAR ADVISOR. This will be READ ALOUD to a shopkeeper who may not
read English. So: simple SPOKEN Hinglish only, NO markdown, NO URLs, NO citations,
NO headings. Max 3 short sentences. Lead with the biggest saving and one action.

ANALYSIS: $analysis
""".trimIndent()

    // Shared instruction: speakable + grounded (no invented facts).
    private const val SPOKEN = "Yeh shopkeeper ko ZOR SE (TTS) padha jayega — simple bole" +
        "-jaane wala Hinglish, NO markdown, NO URL, NO citations, NO headings, max 3-4 " +
        "chhote sentences. GROUNDING: sirf diye gaye research/facts use karo — koi scheme " +
        "ka naam, number, rate ya date KHUD SE mat banao. Agar research mein nahi hai, mat kaho."

    // ---- Step-by-step guidance (web: current real-world process) ----

    fun guideResearcherPrompt(topic: String) = """
You are the RESEARCHER agent. Use WEB SEARCH to find the CURRENT official process
for this task for an Indian small business: "$topic". Capture required documents,
the official portal/website, fees, and the real sequence of steps as of now.
Output concise notes with any official URLs.
""".trimIndent()

    fun guidePlannerPrompt(topic: String, research: String) = """
You are the PLANNER agent. $SPOKEN
Give at most 4 short numbered steps to complete "$topic" — each step one line, what
to do + what's needed. Name the official portal once. End by offering to send the
full guide + link on WhatsApp.

RESEARCH: $research
""".trimIndent()

    // ---- Open-goal Planner: autonomous plan → delegate → execute ----
    fun plannerPrompt(brief: String) = """
You are Laxmi's PLANNER — an autonomous agent for an Indian micro-business owner.

YOU CAN: search the web for live info (prices, schemes, processes), run code to compute
from the given numbers, plan multi-step actions, and draft messages/checklists.
YOU CANNOT: send any message, pay anyone, or make a binding commitment on the user's
behalf (the user does that themselves after seeing your plan). You do NOT have their raw
data — only the anonymized summary below. If you lack a needed slice, ask for it by
replying with a single line "NEED_DATA: <credit|purchases|goods>" and nothing else.

Work autonomously — figure out what the goal needs, do those steps, return a short,
concrete ACTION PLAN they can act on today.

$SPOKEN

$brief
""".trimIndent()

    // ---- Verify / self-correct: a critic checks the final output ----
    fun criticPrompt(output: String) = """
You are the CRITIC. Check the plan below: (1) is it grounded — no invented scheme names,
numbers, rates, dates, or URLs beyond what was researched? (2) is it actually actionable
for a small shopkeeper? If it passes both, reply EXACTLY "OK". Otherwise reply
"REVISE: <one line on what to fix>".

PLAN: $output
""".trimIndent()

    // ---- Sarkari scheme finder (web) ----
    fun schemeResearcherPrompt(profile: String) = """
You are the RESEARCHER agent. Use WEB SEARCH to find CURRENT Indian government
schemes, subsidies, credit guarantees, and micro-insurance a small/micro business
could use (e.g. PM Vishwakarma, Mudra, CGTMSE, state MSME schemes). Given this
coarse profile, list 3-4 with: name, benefit, eligibility gist, official portal.
PROFILE: $profile
""".trimIndent()

    fun schemeAdvisorPrompt(research: String) = """
You are the ADVISOR. $SPOKEN
Name the 2 best schemes for them and the very first step for each. End by offering
to send full details on WhatsApp.
SCHEMES: $research
""".trimIndent()

    // ---- Festival / demand forecast (web) ----
    fun demandResearcherPrompt(goods: String) = """
You are the RESEARCHER agent. Use WEB SEARCH for upcoming Indian festivals and
seasonal events in the next 4-6 weeks, and which of these goods see higher demand:
$goods. Note approximate dates and the demand driver. Concise notes.
""".trimIndent()

    fun demandAdvisorPrompt(goods: String, research: String) = """
You are the ADVISOR. $SPOKEN
Lead with the nearest festival opportunity: what to stock up on and roughly when.
Goods: $goods.
RESEARCH: $research
""".trimIndent()

    // ---- Business insights (ON-DEVICE Gemma — inward, private) ----
    // NOTE: on-device Gemma, spoken aloud — plain sentences, NO markdown/*/#/bullets.
    fun insightsPrompt(ledgerSummary: String) = """
You are Laxmi analysing a shopkeeper's ledger. $SPOKEN
Look at the ledger and say the 2-3 most useful things: kaun sabse zyada dega, kiska
paisa atka hai, aur is hafte ka ek kaam. Plain spoken Hinglish sentences only.

LEDGER:
$ledgerSummary
""".trimIndent()
}
