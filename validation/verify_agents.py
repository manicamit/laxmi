#!/usr/bin/env python3
"""Verify the outward cloud-agent chains against the live Gemini Interactions API,
mirroring MissionClient. Uses the app's key from local.properties."""
import json, re, sys, urllib.request

KEY = None
for line in open("/home/manicguy/Projects/g1/local.properties"):
    if line.startswith("laxmi.geminiApiKey"):
        KEY = line.split("=", 1)[1].strip()
assert KEY, "no key in local.properties"

ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"


def run(prompt):
    body = json.dumps({
        "agent": "antigravity-preview-05-2026",
        "input": [{"type": "text", "text": prompt}],
        "environment": {"type": "remote"},
    }).encode()
    req = urllib.request.Request(ENDPOINT, data=body, headers={
        "Content-Type": "application/json", "x-goog-api-key": KEY})
    try:
        d = json.load(urllib.request.urlopen(req, timeout=180))
    except Exception as e:
        return f"[API ERROR: {e}]"
    if d.get("status") != "completed":
        return f"[STATUS {d.get('status')}: {json.dumps(d.get('error',''))[:200]}]"
    out = []
    for s in d.get("steps", []):
        if s.get("type") == "model_output":
            out.append("".join(p.get("text", "") for p in s.get("content", [])))
    return "\n".join(out).strip() or "[empty]"


PURCHASES = "- 20 cement bags, paid ₹6000\n- 5 litre paint, paid ₹1800"

print("=" * 20, "BAZAAR 1/3 Researcher (web)", "=" * 20)
prices = run('You are the MARKET RESEARCHER agent. This Indian shopkeeper buys the '
             'goods below. Use WEB SEARCH to find the CURRENT typical wholesale/market '
             'price in India for each distinct item (with a unit). Output ONLY JSON: '
             '[{"item":"..","market_price":"..","source":".."}]\n\nPURCHASES:\n' + PURCHASES)
print(prices)

print("\n" + "=" * 20, "BAZAAR 2/3 Analyst (code)", "=" * 20)
analysis = run("You are the PRICE ANALYST agent. Using CODE EXECUTION, compare what the "
               "shopkeeper PAID vs MARKET prices; compute the gap. Output ONLY JSON: "
               '[{"item":"..","paid":"..","market":"..","overpaying":true,"note":".."}]\n\n'
               "PAID:\n" + PURCHASES + "\nMARKET: " + prices)
print(analysis)

print("\n" + "=" * 20, "BAZAAR 3/3 Advisor", "=" * 20)
print(run("You are the BAZAAR ADVISOR. From this price analysis, tell the shopkeeper in "
          "warm Hinglish where they overpay and 1-2 concrete moves. Under 150 words.\n\n"
          "ANALYSIS: " + analysis))

print("\n" + "=" * 20, "SCHEMES Researcher (web)", "=" * 20)
print(run("You are the RESEARCHER agent. Use WEB SEARCH to find CURRENT Indian government "
          "schemes/subsidies/credit for a small retail business (turnover ~₹1L/month). "
          "List 3 with name, benefit, eligibility, official portal."))

print("\nDONE.")

# --- Gap verification: grounding + graceful failure ---
print("\n" + "=" * 20, "GROUNDING: advisor with EMPTY research (must not invent)", "=" * 20)
print(run("You are the ADVISOR. This will be read aloud in simple spoken Hinglish, no "
          "markdown/URL/citations, max 3 sentences. GROUNDING: only use the research below; "
          "do NOT invent scheme names, numbers, rates or dates. If not in research, don't say it.\n\n"
          "RESEARCH: (none available)"))

print("\n" + "=" * 20, "SPOKEN CHECK: scheme advisor on real research is short & speakable", "=" * 20)
print(run("You are the ADVISOR. Read aloud, simple spoken Hinglish, NO markdown/URL/citations, "
          "max 3 sentences. Name the 2 best schemes and first step each.\n\n"
          "SCHEMES: Mudra Shishu up to 50000 collateral-free; PM SVANidhi up to 10000 for street vendors."))
print("\nGAP CHECKS DONE.")
