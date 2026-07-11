# Voice Ledger — 24-Hour Hackathon Build Plan

## 0. TRACK-FUSION REVISION (2026-07-10, supersedes where it conflicts)

**Tracks entered:** Track 2 (Managed Agents / Antigravity) as primary + Special Prize
(Gemma 4 Local-First Agents). Builder knows Antigravity — tooling risk retired.

**Concept: the ledger with a back office — a device–cloud agent federation.**

- **Device plane (Gemma 4, special track):** everything already planned — capture
  surfaces, extractor→critic→resolver, append-only evidence ledger, review queue, offline
  query. Owns ALL raw data (audio/transcripts/parties) forever. **Sole writer to the
  ledger.** Full sense-decide-act-check offline.
- **Cloud plane (Antigravity, Track 2):** acts only when connectivity exists AND the user
  approves a **mission** whose derived-summary payload is shown verbatim ("this is what
  leaves your phone"). Cloud agents return **proposals, never writes**.

**Missions (build 1–2, pitch all 3):**
1. **Collections** — Composer (reminder ladder in party's language) + Strategist
  (timing/tone escalation) + Compliance (respectful) → WhatsApp-ready messages, user
  one-tap sends (human-in-loop boundary).
2. **Reconciliation (the showstopper)** — both parties' apps share consented summaries
  (amounts/dates only); Reconciler matches entries, flags discrepancies ("yours ₹5,000,
  his ₹4,000, 3 July"); Mediator proposes resolution; the phone plays the evidence.
  Two-phone demo.
3. **Credit dossier (roadmap-made-real)** — local agent computes anonymized metrics →
  user reviews exact payload → Researcher (scheme/lender eligibility) + Assembler
  (loan-readiness PDF).

**Track 2 hard questions → structural answers:** context handoff = signed, versioned
mission briefs + shared mission state; safe tools = per-mission whitelists, and the
device data plane is not a tool cloud agents possess; predictability = proposals-only +
single-writer ledger + user-visible payloads.

**Demo arc:** ① airplane mode: voice note → extraction → evidence ledger (special track)
② offline query, spoken answer ③ Wi-Fi on: collections mission — agent team visibly
splits work in Antigravity → reminder ladder ④ second phone: reconciliation catches the
₹1,000 discrepancy → play the original audio → settled ⑤ close: "raw audio never left
either phone."

**Schedule delta:** ledger core hours 0–14 as planned (model spike still first). Hours
14–20 = Antigravity layer: mission-brief schema + consent screen (14–15), collections
mission (15–17), reconciliation mission + second-device flow (17–20). Cut from old plan
to make room: assistant gesture, image OCR, post-call nudge, WhatsApp bulk import → all
roadmap. Hour-10 checkpoint unchanged. **Reconciliation is cut before collections if
behind; the pitch still covers all three missions.**

**Rubric re-estimate:** Creativity ~8.5 (federation + evidence ledger, no incumbent) ·
Demo ~8 (two-phone reconciliation moment) · Depth ~9.5 (two agent runtimes + federation
protocol) · India ~9 → **≈8.6, two prize tracks.**

**Tonight's validation: unchanged** (extraction quality + latency is still the core
bet) **plus:** verify Antigravity/iAPI access + hello-world mission from a laptop; draft
the mission-brief JSON schema.

Sections below remain valid except where this section overrides (old demo script §2 and
schedule §6 are superseded by the arcs above; architecture §3, engineering notes §3b,
competitive/pitch/critic sections all stand).

**AI That Understands India's Informal Economy** — an offline, private, voice-first ledger
for shopkeepers, contractors, electricians, drivers, and everyone whose business runs on
voice notes and verbal commitments.

- **Event:** Google DeepMind hackathon, Bangalore (Gemma on-device / Gemini track)
- **Builder:** Solo · Kotlin + Jetpack Compose · 24 hours
- **Goal:** Demo-first, but architecture sound enough to keep building after

---

## 1. Non-negotiable constraints

1. **Privacy is a feature, not a footnote.** All inference on-device (Gemma via Google AI
   Edge / MediaPipe LLM Inference). Audio, transcripts, and ledger never leave the phone.
   **Ship without the `INTERNET` permission** — judges can verify in the manifest, and the
   demo runs in airplane mode. This is the single strongest proof of the privacy claim.
   (Model files are bundled/sideloaded at install time, not downloaded by the app.)
2. **Fully offline.** No cloud fallback paths, even "temporary" ones — they poison the story.
3. **Everyone can use it.** Icon-first, low-text UI: big mic button, party avatars, large ₹
   amounts, red = they owe you, green = you owe them. Voice in, voice out (Android TTS).

### Rejected on purpose (decision record)

- **Always-on passive listening:** battery + OEM service-kills, permanent green mic
  indicator, other-party consent (trust-toxic in the target community), and segmentation
  compute cost — and it contradicts "nothing enters unless you share it." Legitimate
  descendants: **shop mode** (explicit, visibly-indicated listening session; VAD segments;
  non-commitment audio discarded on-device) = roadmap pitch only; **post-call nudge**
  (see §5) = built instead.
- **PQC-encrypted cloud storage of audio (S3 etc.), transcripts-only on device:** inverts
  the thesis — audio is the evidence (the crown jewels), transcripts are derived; shipping
  it to a server kills the "no INTERNET permission / airplane mode" proof at an on-device
  hackathon. PQC solves a harvest-now-decrypt-later threat model no shopkeeper has.
  Durability worry is answered by encrypted local export now, opt-in E2E-encrypted sync
  on the roadmap.

## 2. The demo (build backwards from this)

3-minute script, one narrative thread, mixed-persona ledger visible in one screen:

1. **Airplane mode on, shown to judges.**
2. Phone is on the home screen. **Long-press power (assistant gesture)** — *our* recorder
   opens instead of Google Assistant. Speak a messy Hinglish note live:
   *"Haan Rajesh, 5 tarikh ko 5000 de dena, aur woh 20 cement bags Sharma ji ko Friday
   tak pahunchane hain."*
3. Watch two structured commitments appear: `Rajesh → owes you ₹5,000, due 5th` and
   `You → deliver 20 cement bags to Sharma ji, Friday` — each with a confidence chip and a
   play button that replays the exact source audio.
4. **Share a WhatsApp voice note into the app** via the share sheet — it gets extracted too.
5. Ask aloud: *"Rajesh se kitna paisa aana hai?"* — spoken + on-screen answer, computed by
   Gemma from local Room data.
6. Show the ledger home: shopkeeper udhaar, electrician job advance, driver trip payment —
   one app, every trade. Close on: *"No internet permission. Nothing ever leaves this phone."*

Pre-seeded data covers steps 5–6 so only steps 2–4 are live-risk. Record a backup screen
capture of the full flow the night before.

## 3. Architecture

Single-module Android app. MVVM-lite (ViewModel + StateFlow), no DI framework (manual
singletons — it's 24 hours).

```
┌─ Capture ──────────────┐   ┌─ Pipeline (WorkManager/coroutine queue) ─┐
│ • Mic (foreground svc) │   │ audio → PCM 16k → transcribe → Gemma      │
│ • QS tile trigger      ├──▶│ extraction prompt → JSON commitments      │
│ • Assistant gesture    │   │ → validate → Room (+confidence, +offsets) │
│ • WhatsApp share-in    │   └──────────────────┬───────────────────────┘
└────────────────────────┘                      ▼
┌─ Query ────────────────┐   ┌─ Room DB ────────────────────────────────┐
│ NL question → retrieve │◀──│ recordings · transcripts · parties ·      │
│ party rows → Gemma →   │   │ commitments(party, dir, type, amount,     │
│ answer + TTS           │   │  dueDate, status, confidence, recId, t0t1)│
└────────────────────────┘   └──────────────────────────────────────────┘
```

### Model strategy (decide in hour 0 at the venue)

- **Plan A — single-model, audio-native (VERIFIED FEASIBLE 2026-07-10):** Gemma 4
  E2B/E4B run on Android via LiteRT-LM with a built-in audio encoder (multilingual ASR,
  no whisper stage). E2B: ~1.5 GB RAM, any Android 12+ device w/ 3+ GB (≈₹10k phone),
  weights ~0.8 GB, 5–15 tok/s midrange. E4B: ~4 GB RAM, 6+ GB device (Pixel 6+/S21+),
  15–30 tok/s on Pixel 8 Pro. 128k context. Both support native **function calling** —
  use it for the query planner instead of parsed JSON. Tonight's question is Hinglish
  QUALITY + measured latency, not feasibility. Test via AI Edge Gallery "Audio Scribe".
- **Plan B — two-stage fallback:** whisper.cpp (small, multilingual) → noisy Hinglish
  transcript → Gemma text-mode extraction. More moving parts but battle-tested.
- Wrap both behind one interface (`ExtractionEngine.process(audioFile): List<Commitment>`)
  so switching is a one-line change. Get whatever model you choose loaded and generating
  **before writing any UI** — model integration is the highest-variance task.

### Extraction prompt contract

Gemma returns strict JSON: `[{party, direction: owed_to_me|i_owe, type: payment|delivery|
service, amount?, quantity?, item?, due_date?, confidence: 0-1, quote}]`. The `quote` field
(verbatim source phrase) lets you locate the audio offset for the play-source button and
grounds the confidence score. Reject/repair malformed JSON with one retry, then queue for
manual review. Few-shot the prompt with 3–4 real Hinglish examples covering all personas.

### Multi-agent harness (on-device, novelty + accuracy)

One warm Gemma instance playing specialized roles under a deterministic Kotlin
orchestrator — real agentic architecture sized for a phone, matching the hackathon's
"native AI agents" track:

1. **Extractor** — audio/text → candidate commitments JSON (the existing prompt).
2. **Critic** — re-reads each candidate against the source and challenges it: direction
   flipped? actually a commitment or just chatter? amount misheard? Adjusts/vetoes and
   sets final confidence. Directly attacks the two worst failure modes (hallucination,
   direction flip) — measure its accuracy lift in tonight's validation.
3. **Resolver** — party identity: match "Rajesh bhai" to existing party or propose new.
4. **Query planner** — user question → structured retrieval spec → answer composition.

Cost: one extra inference per note (critic). Latency measurement decides if the critic
runs on everything or only on extractions below a confidence threshold. Pitch line: "a
multi-agent team that fits in your pocket, no cloud."

### Query answering

Question → cheap retrieval (fuzzy party-name match + status filter on Room) → compact
ledger rows as context → Gemma answers in the language of the question → render + TTS.
Do **not** attempt full function-calling/SQL generation in 24h; retrieval-then-answer is
robust and demos identically.

## 3b. Engineering review notes (senior pass — bake these in from the start)

1. **Latency budget:** measure tokens/sec in hour 0 and design to it. Extraction prompt
   ≤ ~800 tokens (2 terse few-shot examples, not 4 verbose ones); cap output length. Load
   the model ONCE into a warm singleton — never per-request. Show a determinate
   "listening → thinking" state sized to measured latency.
2. **Memory:** on Plan B, never hold whisper + Gemma resident together — run whisper,
   release, then Gemma. Serialize ALL inference behind a Mutex.
3. **Audio evidence = whole-clip playback.** No quote→offset seeking — token timestamps
   don't exist on the audio-native path and fuzzy transcript matching is a 4-hour trap.
   Voice notes are short; play the clip.
4. **Android 14+ mic policy:** QS tile can't start a mic foreground service from
   background. Tile → `startActivityAndCollapse` (near-transparent activity) → start
   `FOREGROUND_SERVICE_MICROPHONE` service. Power-button gesture is OEM-dependent — verify
   the actual gesture on the demo phone; never promise it in the pitch.
5. **Append-only ledger:** settlements and corrections are events; balances/status are
   derived, never mutated. Less edge-case code, and it IS the evidence story. Settlements
   apply at party level (running balance) — no per-commitment FIFO allocation.
6. **No WorkManager:** in-process coroutine queue (Channel) inside the foreground service.
   Persistence-across-process-death buys nothing in a demo.
7. **Party identity:** normalize names (strip bhai/ji honorifics, lowercase) for matching;
   keep seed-data names unambiguous so merge problems can't surface on stage.
8. **Money/dates in code, not the model:** amounts as integer paise; Gemma returns raw
   phrase + best guess, Kotlin normalizes ("5 hazar", "2.5k") and resolves relative dates
   against the recording timestamp; keep raw phrase for ambiguous display.
9. **JSON hardening:** extract first balanced JSON array by bracket matching (models wrap
   output in prose/fences), coerce enums, tolerate missing optionals, one repair retry.
10. **Golden-set regression screen:** ~10 canned inputs with expected extractions + hidden
    debug screen running the suite pass/fail. Every late-night prompt tweak gets checked
    against it. Highest-leverage 2 hours in this plan.
11. **Privacy claim verification:** check the MERGED manifest for INTERNET (transitive deps
    re-add it). On stage say "never leaves the device", not "encrypted" — no SQLCipher in 24h.
12. **Storage: compress mic recordings from day one.** Opus/AAC ~16 kbps ≈ 120 KB/min
    (heavy user < 1 GB/yr — fine); raw PCM/WAV is 16× worse (~15 GB/yr) — decode to PCM
    transiently for inference only, never persist it. Model file (2–4 GB) dominates
    regardless. Trust feature: storage dashboard in settings + user-controlled retention
    ("clear audio for settled entries > 6 months old; keep the text record").
13. **Dedupe ingestion:** SHA-256 incoming files (and normalized shared text), unique index
    on hash, silently skip known content. Double entries in a money app are trust-fatal.
14. **Bulk-import backpressure:** batch 5–10 texts per prompt (amortize prefill); audio
    newest-first with a "12 of 50 processed" progress notification. No unbounded inference
    loops.
15. **First-run flow (schedule it, ~30 min):** model-file presence check with a clear
    error, mic permission, POST_NOTIFICATIONS permission (Android 13+ — reminders silently
    fail without it).
16. **Unfiled inbox — capture never depends on extraction succeeding.** Failed/vetoed/
    empty extractions land as raw playable items in an "Unfiled" inbox: retry, or manually
    file under a party. "The AI missed one" is forgivable; "my note vanished" is an
    uninstall.
17. **Judge-handoff demo mode:** seeded, labeled demo ledger safe to hand over — judges
    can query, play evidence, tap a review without touching real test data.
18. **Pitch the measurements:** tonight's scoring table becomes a slide ("91% extraction,
    97% direction, 0 hallucinations, 6s/note — measured on this phone"). Measured numbers
    convert "cool demo" into "engineered product".
19. **Room indices** on party, status, dueDate. **Date anchor:** resolve relative dates
    against the recording's timestamp (file metadata when shared), not ingestion time;
    mark ambiguous when no anchor exists.

## 4. Activation surfaces (in build order)

1. **Foreground recording service + notification action** — needed anyway. (baseline)
2. **Quick Settings tile** (`TileService`) — record from anywhere in ~1–2h. (baseline)
3. **Assistant gesture** — register as assist app (`ACTION_ASSIST` intent filter; upgrade to
   `VoiceInteractionService` only if time allows). User selects the app under Settings →
   Default digital assistant; long-press power then opens our recorder. (stretch, high wow)
4. ~~Volume-button combo~~ — requires AccessibilityService; fragile, scary permission,
   OEM-dependent. Cut. The assistant gesture is the same magic done right.

## 5. WhatsApp share-in (audio + text)

Intent filter: `ACTION_SEND` (+ `ACTION_SEND_MULTIPLE`) for both `audio/*` and
`text/plain`.

- **Audio:** WhatsApp voice notes arrive as `.opus`; decode with
  `MediaExtractor`/`MediaCodec` → mono 16 kHz PCM (same normalize step the mic path uses —
  one shared function). ~2–3h including decode plumbing.
- **Text:** skips transcription, straight to the Gemma extraction prompt — ~1h extra, and
  typically *higher* confidence than speech. The verbatim message is stored as the
  evidence (one "source" abstraction: audio clip | text quote).
  **UX reality:** WhatsApp-Android gives text messages only Copy/Forward — NO system
  share. Low-friction text ingestion stack (in priority order):
  1. **`ACTION_PROCESS_TEXT` selection-toolbar entry:** "Add to Ledger" in Android's
     text-selection popup, works in EVERY app (SMS, Telegram, email too). 2 gestures,
     in-place. Verify tonight whether WhatsApp's "select text" summons the standard
     toolbar on the demo phone. Not a nuisance: appears only inside the user-initiated
     selection toolbar (usually its ⋮ overflow) — same mechanism as Google Translate's
     entry. Settings toggle to remove it system-wide (disable the activity component via
     PackageManager) for anyone who objects.
  2. **QS tile clipboard grab:** copy → swipe down → tap tile → transparent activity
     (foreground+focused = allowed to read clipboard) → extract → toast. 3 gestures,
     always works since Copy always exists.
  3. **Catch-basins:** auto-offer banner on app open when fresh clipboard text detected;
     launcher-icon long-press shortcut "Add from clipboard"; same action on the
     persistent notification.
  Ruled out: background clipboard listening (OS-blocked since Android 10), accessibility
  text scraping (rejected on principle), floating-bubble focus tricks (OEM-fragile).
- **Share-path reality check (verify on the demo phone TONIGHT):** Forward (arrow) ≠
  Share — Forward is WhatsApp-internal. System share sheet for media = long-press → ⋮
  (top-right) → Share. Fallbacks that always work: chat media gallery share button,
  Export chat (always system share sheet), file-picker import of voice-note files.
  WhatsApp moves these menus between versions — re-verify before the demo.
- **Stretch — image share-in (`image/*`), one code path, two prompts:** (a) UPI payment
  screenshots → settlements ("₹2,000 from Rajesh → decrement dues"); (b) bill/parchi
  photos → purchase/settlement events (supplier, amount, items). Try Gemma vision first;
  fallback ML Kit Text Recognition (free, on-device, offline) → existing text extractor.
  Attempt only after audio + text are solid. Honest note: Dukaan AI has bill scanning —
  this is parity, not differentiation; demo emphasis stays on conversation mining.
- **Post-call nudge (build — cheap, nobody has it):** call recording is OS-blocked, but
  `READ_PHONE_STATE` lets us notice a call ended → notification "Rajesh se baat hui — koi
  hisaab? 🎤" → one tap, 15s voice summary into the normal pipeline. Captures at the
  moment of commitment, records only the user's own voice.
  **Anti-invasiveness rules (mandatory):** (1) nudge ONLY for callers matching ledger
  parties — ideally with open commitments (a WHERE clause kills ~90% of noise); (2)
  silent low-priority notification, no sound/vibration/heads-up, auto-dismiss in 10 min;
  (3) skip calls < 30s; (4) self-muting — 3 ignored nudges for a party disables that
  party (counter, not ML) + per-party toggle, quiet hours, global off; (5) OPT-IN at
  onboarding — needs READ_PHONE_STATE + number matching, so never default-on (protects
  the minimal-permission story). Zero-notification fallback: "recent calls with ledger
  parties" strip on next app open.

**Frictionless share-in (feels like never leaving WhatsApp):**

- Receiving activity is translucent/no-UI: copy the `EXTRA_STREAM` URI to app storage
  IMMEDIATELY in `onCreate` (grant is transient), enqueue extraction, toast "✓ Added to
  ledger", `finish()`. User stays in the WhatsApp chat; total visible footprint = a toast.
- **Sharing Shortcuts (Direct Share):** `ShortcutManagerCompat` + `shortcuts.xml` share
  target so "Add to Ledger" appears in the share sheet's top row.
- **Per-party share targets (file under a tag at share time):** publish top ledger
  parties as dynamic direct-share shortcuts — share sheet shows "→ Rajesh", "→ Housemaid"
  like WhatsApp contacts. One tap gives (1) ground-truth party attribution (resolver's
  hardest problem gone for shared content), (2) an extraction hint in the prompt ("this
  note concerns Rajesh, existing party, owes ₹5,000") boosting accuracy, (3) lower review
  burden — party pre-confirmed, more entries auto-accept. Generic "Add to Ledger" target
  stays for new/unknown parties.
- **Group labels on parties:** optional labels (`household`, `suppliers`, `customers`,
  `site-A`) — one column + filter chips ("all household staff dues"). Parties stay the
  unit; a party can be unnamed ("Housemaid"). Quietly demos the expansion rings.
- Extraction completes in background → notification: "₹5,000 due from Rajesh — tap to
  review."
- **Bulk import (stretch):** handle WhatsApp Export Chat (`ACTION_SEND_MULTIPLE`: .txt +
  media) — onboard an entire chat history in one share.

**Source provenance tags:** every source row carries a channel tag — `whatsapp-audio` /
`whatsapp-text` / `mic` / `post-call-note` / `image-bill` / `image-upi` / `bulk-import` —
plus received-at timestamp. Detect the sharing app via the share intent's referrer/calling
package where available; default to "shared-in" otherwise. (Note: the share sheet doesn't
reveal the WhatsApp *sender* — party attribution comes from extraction + review, not the
tag.) UI: chip on each entry ("via WhatsApp · 3 Jul, 14:22"), ledger filterable by source.
Strengthens evidence: provenance is part of the audit trail.

**Principle:** manual share-in only. No NotificationListenerService auto-reading of
WhatsApp — fragile, invasive, and it breaks the privacy story. Pitch line: "Nothing enters
the ledger unless you share it; nothing leaves the phone, ever."

## 6. 24-hour schedule (solo, serialized)

| Hours | Task | Cut-line notes |
|---|---|---|
| 0–1 | Confirm model plan (A/B) with mentors; get model file on device | Highest-variance item goes first |
| 1–4 | Model spike: load Gemma, run extraction prompt on a canned Hinglish clip, parse JSON | **Nothing else matters until this works** |
| 4–6 | Project skeleton: Room schema, nav, theme; pipeline wiring | |
| 6–8 | Mic capture: foreground service, PCM normalize, save + auto-run pipeline | |
| 8–10 | Ledger home + party detail screens (aggregated balances, pending items) | |
| 10–12 | Review queue: commitment cards, confidence chips, play-source-audio, confirm/edit | |
| 12–14 | Query screen: retrieval + Gemma answer + TTS | |
| 14–16 | WhatsApp share-in (opus decode → shared pipeline) | First cut if behind |
| 16–17 | Quick Settings tile | |
| 17–19 | Assistant-gesture activation (`ACTION_ASSIST`) | Second cut if behind |
| 19–20 | Due-date reminder notification (WorkManager) | Third cut |
| 20–22 | Seed mixed-persona demo data; polish home screen; record backup demo video | Never cut |
| 22–24 | Rehearse demo ×3 in airplane mode; sleep buffer | Never cut |

**Cut order if behind: reminders → assistant gesture → WhatsApp share-in.** Never cut:
model spike, review queue (it's your credibility story), seed data, rehearsal.

## 6b. Cut-order revision (post-grill)

**WhatsApp share-in outranks the assistant gesture.** Share-in is the adoption argument
(voice notes already exist; no new behavior needed) — the gesture is theater. Revised cut
order if behind: **reminders → assistant gesture → QS tile → WhatsApp share-in (last)**.

## 7. Nice-to-haves (only if ahead of schedule)

- **Share-back to WhatsApp:** generate a polite Hinglish dues-summary text for a party.
- **Confidence-tiered auto-accept:** high-confidence items skip the review queue.
- Weekly voice summary; multi-language UI strings.

## 8. Risks

| Risk | Mitigation |
|---|---|
| Gemma audio modality unavailable/slow on your device | Plan B (whisper.cpp) behind the same interface; decide by hour 1 |
| Extraction JSON flaky on real speech | Few-shot prompt, one repair retry, review queue catches the rest — the confidence UI turns errors into a *feature* |
| Model too slow for live demo | Use smallest Gemma variant; show a "listening → thinking" progress state; keep live clips under 20s |
| Live demo fails | Pre-seeded ledger + backup screen recording |
| Device constraints | Test on your actual demo phone from hour 1; 8GB+ RAM device strongly preferred |

## 9. Pitch & judge Q&A (rehearse these)

**One-liner:** *"India's informal economy runs on spoken promises. We turn them into a
ledger — with the audio as evidence, fully offline, on-device."* The product is an
**evidence-backed ledger**; extraction is merely the how.

### Judging rubric map (Demo 25 · Depth 15 · Creativity 35 · India 25)

- **Creativity 35% (heaviest — won in the first 30 seconds):** open with the
  category-creating claim (mine conversations that already happened; commitments not
  transactions; audio as evidence), NEVER "a voice ledger app" (files us with Dukaan-like
  voice-entry). Multi-agent-on-a-phone + "never speaks first" feed this too.
- **Demo 25%:** airplane mode → gesture → live Hinglish → share-in → evidence playback →
  spoken query answer. Protect with rehearsal ×3, seeded data, backup recording, stats
  overlay as the "well-engineered" proof.
- **Depth 15%:** ablation slide + no-INTERNET manifest + on-device multimodal = plenty.
  Weight is low — do NOT spend more hours on engineering flourish.
- **India 25%:** ₹10k-phone floor (E2B, 3 GB), offline = rural, Hinglish→Indic, expansion
  rings, credit-rail-for-the-unbanked arc, real-user testimony from prep visit.
- **Weighted estimate ≈ 8.4 → levers: opening framing + demo smoothness (the 60%), not
  features.**

| Judge question | Your answer |
|---|---|
| "Why not Khatabook/OkCredit with a mic?" | Their entries are one party's typed claim. Ours carry the audio of the commitment itself — dispute-proof. Stage the comparison yourself: time typing an entry vs. speaking one, on screen. |
| "Will people really record conversations?" | Concede in-person is a new habit; WhatsApp voice notes already exist at massive scale — share-in mines existing behavior. |
| "It got a ₹ amount wrong — now what?" | Low-confidence entries are never silently committed; every entry replays its source audio in one tap. We claim checkability, not perfection. |
| "Phone lost = ledger lost?" | Encrypted local export now; opt-in E2E-encrypted sync on roadmap. "No INTERNET permission" stays true for v1. |
| "Say that in Kannada." | Test one Kannada/Tamil clip before demo. Works → planted wow moment. Doesn't → pre-empt: "Hinglish first, Indic next." |

Also: pick a name before the event (candidates: **Zubaani**, **BoliKhata**). Include one
South-Indian-language clip in seed data.

### Stand-out moves (visibility layer — make the differentiation undeniable)

1. **Live inference stats overlay** (~1h): corner readout during extraction — "gemma-4 ·
   on-device · prefill 1.2s · 14 tok/s". Proof-of-work for DeepMind judges; doubles as a
   debug tool.
2. **Publish the eval set** as the first open Hinglish commitment-extraction benchmark
   (validation/ scripts + ground truth, grown to 20–30 items). Contributing an artifact
   the judges' community can reuse > any feature.
3. **Ablation slide** from tonight's critic on/off runs: "extractor alone 84%, 3
   hallucinations → with critic 93%, zero." Multi-agent justified by measurement, not
   fashion.
4. **Permissions comparison slide** (zero engineering): Play Store data-safety sections
   of Khatabook / Dukaan AI vs ours — their contacts/location/third-party sharing vs our
   mic-only + no INTERNET permission.
5. **Cheap-phone demo (conditional on latency numbers):** ₹10–15k device — "this runs on
   the phone our users actually own." Decide after measurement, never on principle.
6. **Recovery path if accuracy disappoints:** LoRA fine-tune of Gemma on synthetic
   Hinglish commitment data (pre-event Colab; MediaPipe LLM API supports Gemma LoRA
   adapters) — turns a weak base model into the "domain-tuned Gemma" story.

### Competitive landscape (researched 2026-07-10) — name these before judges do

| Player | What it is | Capture model | Gap we fill |
|---|---|---|---|
| Khatabook / OkCredit | Typed digital khata, 8M/5M merchants, stalled | Manual typing | See post-mortem below |
| **Dukaan AI** (closest) | Free voice billing + khata, 24 languages, offline voice billing, udhaar, UPI links | **Deliberate dictation at the counter** — voice as a keyboard for bookkeeping commands (880+ item grammar) | No conversation mining, no commitments (transactions only), no audio evidence, cloud backup (not device-only) |
| Pilloo AI (2025) | Voice billing/accounting agent for MSMEs, GST-oriented | Dictation + document upload | Cloud-based, formal accounting, not the informal promise layer |
| Pocket Ledger, KashVox | Voice-entry khata apps | Dictation | Same gaps as Dukaan |
| ToHands | ₹2,999 hardware smart calculator, Cash In/Out buttons | Captures at an existing behavior point (the calculator) — right insight, different artifact | Transactions only; no commitments, no evidence, no language understanding |
| KhataApp AI ("financial memory") | Unknown depth — **check at the event** | ? | ? |

**The sharpened novelty claim:** every existing player built a **voice keyboard for a
ledger** — the merchant still performs a deliberate bookkeeping act, just by speaking.
Nobody mines the **conversations that already happened** (WhatsApp voice notes, texts),
nobody records **commitments** (future promises: payments due, deliveries, services) as
opposed to transactions, and nobody attaches **audio evidence** to entries. That
three-way intersection — communication exhaust in, promise graph out, evidence attached,
all on-device — is unoccupied. Pitch it as: *"Dukaan AI is a voice keyboard. We're a
memory. You don't do bookkeeping at all — you just talk to people like you always did."*

### Expansion rings (market-size slide — same primitive, zero code change)

The primitive is a **memory for verbal contracts**, not a shopkeeper tool. Domain never
touches the extractor, so each ring is a pitch claim, not an engineering cost:

- **Ring 1 — trades (launch):** shopkeepers, contractors, electricians, plumbers,
  wholesalers, drivers.
- **Ring 2 — households:** domestic-help payroll (maid advances, cook's leave, driver
  overtime, milkman/presswala hisaab) + **family lending** ("shaadi ke liye 50k diye
  the") — evidence-hungry, completely unserved. Judge-relatability: *the judges are
  users* — everyone in the room tracks their maid's advances in their head.
- **Ring 3 — earners (direction flipped):** gig workers/freelancers tracking client
  promises, tuition teachers' per-student dues, tailors, beauticians, photographers.
  Schema already handles it — direction is a field.
- **Ring 4 — groups & seasons:** SHGs/committees (millions under NRLM, run verbally by
  one secretary), housing-society vendor commitments, wedding-season event vendors
  (bookings = commitment graphs: advance/date/deliverable), agriculture (mandi deals,
  input credit, harvest labor).
- **Vectors:** two-sided handshake (both phones hold the same evidence → network effect
  Khatabook lacked) · credit underwriting from commitment history (Ring 3/4 users are
  invisible to banks).
- **Deliberate exclusion (say it on stage):** corporate meeting-promise tracking —
  crowded, cloud-shaped, and off-thesis. Informal-economy-first IS the positioning.

### "Why won't you stall like Khatabook?" (they scaled to 8M merchants, then ₹100Cr+
losses, layoffs, lending pivot)

1. **Their friction, our capture** — they required typing after every transaction; our
   entry *is* the voice note that already exists (WhatsApp share-in = no new behavior).
2. **We live where UPI can't** — UPI self-records payments and gutted their use case;
   verbal commitments/udhaar are structurally invisible to payment rails. They competed
   with UPI; we complement it.
3. **Evidence beats claims** — their typed entry is a one-party claim; ours carries the
   audio of the promise (dispute weight, real lock-in).
4. **Why now** — on-device LLMs able to mine noisy Hinglish speech didn't exist when they
   were built.
5. **Monetization (own it honestly)** — the ledger is the wedge, not the business.
   Long-term: evidence-backed cashflow history → consented credit underwriting for people
   with no formal records. Privacy architecture makes consent real: raw audio never leaves
   the device; user opts in to share a derived summary. Privacy = the monetization enabler.

### Hostile-critic register (the four with no good answer yet — don't get surprised)

1. **Capturable subset:** calls & face-to-face commitments leave no artifact — we mine
   voice notes/texts only. Honest answer: start where the artifact exists; post-call
   nudge + (roadmap) shop mode chase the rest.
2. **Dev-authored test set:** accuracy measured on our own voice/dialect. Honest answer:
   say "measured on our seed set" not "91% accurate"; pilot data is the real test.
3. **Distribution:** no channel; incumbents can clone the feature. Honest answer: wedge →
   evidence corpus → two-sided handshake; and a hackathon is not a GTM exam.
4. **Review-queue automation bias:** low-literacy users may blind-confirm. Honest answer:
   evidence always one tap away limits damage; pilot metric #2 watches for it.
   Soften on stage: "evidence-linked", not "dispute-proof"; confidence = "model's own
   uncertainty", never implied statistics.

### Critic responses — design measures adopted (2026-07-10)

- **Mic = "speak your note"** (self-dictation, not conversation recording) — closes the
  in-person capture gap AND the counterparty-consent hole. UX copy change only.
- **Schema adds:** `firmness: firm|tentative` (tentative never auto-accepts; critic agent
  asks "obligation or social talk?") · `refers_to_existing` flag (old-balance references
  flagged for one-tap resolution, not guessed) · `kind: correction` ("nahi, 4000 hua
  tha" → correction event; append-only ledger built for it).
- **Golden set adds T11 brush-off script** ("de dunga yaar, tension mat le" → nothing
  firm) — second hallucination tripwire beside T5. **Diversify voices tonight:** 2–3
  friends/family record half the scripts; shopkeeper prep-visit notes = held-out set.
- **Confidence:** calibrate buckets against tonight's scorecard; display words ("pakka" /
  "check kar lo"), never numbers; drop display if buckets uninformative.
- **Automation-bias guards:** auto-accept only high-confidence; weekly TTS spoken audit
  of new entries (no literacy needed).
- **Slow phones:** ingestion is async by design (share → pocket → notification); latency
  only binds on live query — keep query prompt minimal; model tier per device.
- **Model delivery:** Play Asset Delivery (≤4 GB, install-time) — app keeps zero INTERNET
  permission. **Build-time merged-manifest check** fails CI if INTERNET appears; bundled
  ML Kit variant only.
- **Growth in v1:** share-back reminder footer "sent via [name]" (Khatabook's proven
  hack, through our most natural action).
- **Hour-10 checkpoint:** if behind → minimum demoable product (mic → extract → ledger →
  review → query), everything else cut without renegotiation.
- **Roadmap:** evidence-pack export (audio + timestamps + hashes).

### Post-hackathon pilot (the "what next" answer — tests the two weak links)

5 shopkeepers/contractors, 2 weeks, 3 numbers: (1) items shared into the app per week
(tests habit formation — the biggest unknown), (2) reminder/digest-driven opens (tests
daily-value loop), (3) disputes or collections resolved via evidence playback (the
jackpot moment). These three numbers answer "will people use this" — nothing else does.

### Post-analysis feature adds (all cheap, all on existing data)

- **Settlement tracking:** "Rajesh ne 2,000 de diye" decrements dues — partial-payment math.
- **Dispute mode:** per-party chronological playback of commitments + settlements.
- **WhatsApp reminder share-back** with evidence attached (their stickiest feature, improved).
- **Morning digest notification:** "aaj ₹7,000 collect karna hai: Rajesh, Sharma ji."

## 10. Pre-hackathon prep (do before the event)

- Install Android Studio, create empty Compose project, confirm it runs on the demo phone.
- Download candidate model files (Gemma on-device variants + whisper small) — venue Wi-Fi
  will be saturated.
- Try Google's **AI Edge Gallery** sample app on your phone to verify Gemma runs and gauge
  tokens/sec.
- Write and test the extraction prompt against 5 recorded Hinglish samples (can do in
  AI Studio / Gallery before the event — prompt design ports directly).
- Collect 6–8 realistic voice-note scripts across personas for seed data.
- **Validate the core bet (highest leverage):** record 10 realistic Hinglish samples, run
  the extraction prompt against them in AI Studio, and measure tokens/sec in AI Edge
  Gallery on the demo phone. Know accuracy % and latency BEFORE hour 0 — adapt the plan if
  either disappoints.
- **Get real users (unclonable):** 2 hours with 2–3 actual shopkeepers/contractors —
  consented real voice notes, their real udhaar as seed data, a quotable reaction.
  "Here's a kirana owner's actual ledger" beats any feature built in the same 2 hours.
- Pre-build the boring 30%: project scaffold, Room schema, share-in plumbing, screens with
  fake data — event hours go to the model pipeline and polish.
