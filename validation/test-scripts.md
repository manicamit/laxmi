# Validation Test Scripts (ground truth)

Record each as a natural voice note on your phone (don't read robotically — add the ums,
pauses, and background noise of real life; a couple with TV/street noise behind). Test
twice: (a) audio directly into the model if the audio modality works, (b) your own rough
transcript pasted as text.

**Voice diversity (mandatory):** have 2–3 other people (different accents/dialect mixes —
family, friends) record at least half the scripts. A test set voiced only by the
developer measures nothing about the field. Log speaker per run in scoring.md.

Score each against the expected extraction using scoring.md.

---

**T1 — Kirana udhaar (baseline Hinglish)**
"Ramesh bhaiya ko bol diya hai, 1200 ka saaman le gaye hain, agle somvaar tak de denge."
→ 1 commitment: Ramesh / owed_to_me / payment / ₹1200 / "agle somvaar tak"

**T2 — Wholesaler delivery (i_owe direction)**
"Haan ji Gupta traders se maal aa gaya, ab mujhe unko pachees hazar bhejne hain do din
mein, warna agli baar udhaar nahi denge."
→ 1 commitment: Gupta traders / i_owe / payment / ₹25,000 / "do din mein"

**T3 — Electrician job advance (mixed commitment + settlement)**
"Verma sahab ke ghar ka wiring ka kaam pakka ho gaya, unhone paanch hazar advance de diya,
baaki das hazar kaam khatam hone pe milega, Sunday tak khatam karna hai."
→ 1 settlement: Verma / owed_to_me→received / ₹5,000
→ 1 commitment: Verma / owed_to_me / payment / ₹10,000 / "kaam khatam hone pe"
→ 1 commitment: Verma / i_owe / service / wiring / "Sunday tak"

**T4 — Driver trip (numbers in English, rest Hindi)**
"Sharma ji ka Mysore trip done ho gaya, four thousand five hundred banta hai, bole
month-end pe kar denge payment."
→ 1 commitment: Sharma ji / owed_to_me / payment / ₹4,500 / "month-end pe"

**T5 — NEGATIVE CASE (must return [])**
"Arre haan bhai, kal match dekha? Kya khela Kohli ne yaar. Chal Sunday ko milte hain,
chai peete hain. Ghar pe sab theek?"
→ [] — ZERO extractions. Any commitment found here is a hallucination. Critical test.

**T6 — Multi-party, single note**
"Suno, Anil ko bolna 2000 laut de is hafte, aur Meena madam ka order ready hai, unhe
kal deliver karna hai, 15 kurtis, unse 6000 lene hain delivery pe."
→ Anil / owed_to_me / payment / ₹2000 / "is hafte"
→ Meena / i_owe / delivery / 15 kurtis / "kal"
→ Meena / owed_to_me / payment / ₹6000 / "delivery pe"

**T7 — Settlement only, partial payment**
"Rajesh aaya tha dukaan pe, teen hazar de gaya, bola baaki agle mahine."
→ 1 settlement: Rajesh / ₹3,000 received
→ 1 commitment: Rajesh / owed_to_me / payment / remainder / "agle mahine"

**T8 — Ambiguous/mumbled amount (confidence test)**
"Woh plumber ka number do na... haan usko bola tha shayad dedh-do hazar lega bathroom
theek karne ka, parso aayega."
→ 1 commitment: plumber / i_owe / service+payment / ~₹1500–2000 / "parso" —
confidence MUST be low (< 0.6). High confidence here = scoring failure.

**T9 — Pure Hindi, formal-ish**
"Sharma ji, aapka pichhla hisaab baaki hai, saat hazar teen sau rupaye. Kripya is
shukravaar tak bhijwa dijiye."
→ 1 commitment: Sharma ji / owed_to_me / payment / ₹7,300 / "is shukravaar tak"

**T11 — Brush-off / social talk (second hallucination tripwire)**
"Arre haan haan, de dunga yaar, tension mat le. Chhota bhai hai tu mera. Kabhi bhi aa ja
ghar pe, bhabhi ke haath ka khana kha ke ja."
→ Either [] or a single item with firmness="tentative", confidence < 0.5. Any FIRM
commitment extracted here is a failure — this is politeness, not obligation.

**T10 — South Indian language (Kannada or Tamil — the Bangalore test)**
Kannada example: "Ravi ge heli, naanu naale aidu saavira kodteeni, mattu avanu nanage
cement bags Friday olage kodabeku."
→ Ravi / i_owe / payment / ₹5,000 / "naale" + Ravi / owed_to_me / delivery / cement bags /
"Friday olage"
If this works: planted demo moment. If not: pre-empt in pitch ("Hinglish first, Indic
languages next").
