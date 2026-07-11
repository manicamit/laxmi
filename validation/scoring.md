# Scoring Sheet & Decision Tree

## How to score

For each test (T1–T10), compare model output to ground truth. Count per commitment:

| Metric | What counts |
|---|---|
| **Detected** | The commitment/settlement was found at all |
| **Hallucinated** | Model invented an obligation not in the input (worst on T5) |
| **Party** | Correct name (honorific variants OK: "Sharma" = "Sharma ji") |
| **Direction** | owed_to_me vs i_owe correct — a flip is a critical error |
| **Amount** | amount_guess within the right value |
| **Kind** | commitment vs settlement correct (T3, T7) |
| **Due captured** | due_phrase verbatim-ish present when input had one |
| **Confidence sane** | Low (<0.6) on T8; ≥0.8 on clean cases |
| **JSON valid** | Parseable array, correct fields (note every malformed output) |

Also record, per run: **prefill time, decode speed (tok/s), total wall-clock** — from AI
Edge Gallery on the demo phone.

## Pass bars (against ~20 ground-truth items across T1–T10)

- Detection ≥ 85% · Hallucinations = 0 on T5 (and ≤1 overall)
- Direction accuracy ≥ 95% (flips are money errors)
- Amount accuracy ≥ 90% on clean audio
- JSON parse failures ≤ 10% (the app's repair-retry covers occasional ones)
- Wall-clock per 30s voice note ≤ 15s (≤ 8s = demo feels magical)

## Decision tree for tonight

- **Detection low / fields wrong** → iterate the prompt: add ONE few-shot example shaped
  like the failing case (don't stack examples — prefill cost). Re-run whole suite (that's
  what the golden set is for).
- **Direction flips** → add an explicit worked rule to the prompt: "X de dena (to speaker)
  = owed_to_me; X ko dena hai (speaker gives) = i_owe" with one example of each.
- **Hallucinates on T5** → add: "Most voice notes contain NO commitments; empty array is
  the common correct answer."
- **Audio-native path weak but text path strong** → Plan B (whisper → text extraction) is
  the architecture. Test whisper-small on the same recordings tonight if possible.
- **Both paths weak on audio, strong on clean text** → pivot the demo weight toward
  text/WhatsApp share-in as primary, mic as secondary. The product story survives intact.
- **Latency > 30s per note** → smaller model variant, shorter prompt, or cut few-shot to
  one example; if still bad, demo with pre-processed queue + one short live clip.
- **T10 (Kannada) works** → planted wow moment in the demo. Doesn't → pre-empt in pitch.

## Record results here

| Test | Detected | Party | Direction | Amount | Kind | Due | JSON OK | Notes |
|---|---|---|---|---|---|---|---|---|
| T1 | | | | | | | | |
| T2 | | | | | | | | |
| T3 | | | | | | | | |
| T4 | | | | | | | | |
| T5 | | | | | | | | |
| T6 | | | | | | | | |
| T7 | | | | | | | | |
| T8 | | | | | | | | |
| T9 | | | | | | | | |
| T10 | | | | | | | | |
| T11 | | | | | | | | speaker: |

Latency: prefill ____ s · decode ____ tok/s · wall-clock per 30s note ____ s
Device: ____________ · Model + variant: ____________ · Prompt version: v1 / v__
