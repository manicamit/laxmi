#!/usr/bin/env bash
# Verify the outward cloud-agent chains against the live Gemini Interactions API,
# mirroring what MissionClient sends. Uses the same key the app builds with.
set -euo pipefail
KEY=$(grep -m1 'laxmi.geminiApiKey' /home/manicguy/Projects/g1/local.properties | cut -d= -f2-)
: "${KEY:?no key in local.properties}"

run() { # $1 = prompt text -> prints model_output text
  local body
  body=$(MISSION_TEXT="$1" python - <<'PY'
import json, os
print(json.dumps({
  "agent": "antigravity-preview-05-2026",
  "input": [{"type": "text", "text": os.environ["MISSION_TEXT"]}],
  "environment": {"type": "remote"},
}))
PY
)
  curl -sS -X POST "https://generativelanguage.googleapis.com/v1beta/interactions" \
    -H "Content-Type: application/json" -H "x-goog-api-key: $KEY" -d "$body" \
  | python - <<'PY'
import json,sys
d=json.load(sys.stdin)
if d.get("status")!="completed": print("STATUS:",d.get("status"),d.get("error","")); sys.exit(0)
for s in d.get("steps",[]):
    if s.get("type")=="model_output":
        print("".join(p.get("text","") for p in s.get("content",[])))
PY
}

echo "==================== BAZAAR: Researcher ===================="
R=$(run 'You are the MARKET RESEARCHER agent. This Indian shopkeeper buys the goods below. Use WEB SEARCH to find the CURRENT typical wholesale/market price in India for each distinct item (with a unit). Output ONLY JSON: [{"item":"..","market_price":"..","source":".."}]

PURCHASES (what they bought and paid):
- 20 cement bags, paid ₹6000
- 5 kg paint, paid ₹1800')
echo "$R"
echo
echo "==================== BAZAAR: Analyst (code) ===================="
run "You are the PRICE ANALYST agent. Using CODE EXECUTION, compare what the shopkeeper PAID against the MARKET prices. For each item where they overpay, compute the gap and estimated monthly saving. Output ONLY JSON: [{\"item\":\"..\",\"paid\":\"..\",\"market\":\"..\",\"overpaying\":true,\"note\":\"..\"}]

PAID:
- 20 cement bags, paid ₹6000
- 5 kg paint, paid ₹1800
MARKET: $R"
echo
echo "==================== SCHEMES: Researcher (web) ===================="
run 'You are the RESEARCHER agent. Use WEB SEARCH to find CURRENT Indian government schemes/subsidies/credit guarantees for a small/micro business. Profile: Receivables ₹23000, Payables ₹8000, 6 relationships, small retail. List 3 with name, benefit, eligibility, portal.'
echo
echo "DONE."
