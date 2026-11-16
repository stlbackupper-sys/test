"""Runs the Buffett screening prompt through the Claude API and writes data/picks.json.

Environment:
  ANTHROPIC_API_KEY  required
  MODEL              optional, defaults to claude-opus-5-5
  MAX_ROUNDS         optional, max continuation rounds for long tool-use runs (default 25)

NOTE: the tool type strings below (web_search_20250305, web_fetch_20250910,
code_execution_20250522) and their beta flags were correct when this was written.
Check https://docs.claude.com for newer versions if the API rejects them.
"""
import datetime
import json
import os
import sys
from pathlib import Path

import anthropic

ROOT = Path(__file__).resolve().parent.parent
PROMPT_FILE = ROOT / "prompts" / "buffett_screen.md"
OUT_FILE = ROOT / "data" / "picks.json"

MODEL = os.environ.get("MODEL", "claude-opus-5-5")
MAX_ROUNDS = int(os.environ.get("MAX_ROUNDS", "25"))
BETAS = ["web-fetch-2025-09-10", "code-execution-2025-05-22"]
TOOLS = [
    {"type": "web_search_20250305", "name": "web_search", "max_uses": 80},
    {"type": "web_fetch_20250910", "name": "web_fetch", "max_uses": 80},
    {"type": "code_execution_20250522", "name": "code_execution"},
]
DISCLAIMER = (
    "Educational screening output, not personalized financial advice or a prediction "
    "of returns. Verify every figure against company filings."
)
REQUIRED = ["ticker", "name", "price", "intrinsic_base", "margin_of_safety_pct", "growth_base_pct"]


def run_model(prompt: str) -> str:
    client = anthropic.Anthropic()
    messages = [{"role": "user", "content": prompt}]
    for round_no in range(1, MAX_ROUNDS + 1):
        with client.beta.messages.stream(
            model=MODEL,
            max_tokens=32000,
            tools=TOOLS,
            betas=BETAS,
            messages=messages,
        ) as stream:
            resp = stream.get_final_message()
        print(f"round {round_no}: stop_reason={resp.stop_reason}", file=sys.stderr)
        if resp.stop_reason == "pause_turn":
            # Server-side tool loop hit its iteration cap; send the turn back to continue.
            messages.append({"role": "assistant", "content": resp.content})
            continue
        return "".join(b.text for b in resp.content if b.type == "text")
    raise RuntimeError("model did not finish within MAX_ROUNDS")


def extract_json(text: str) -> dict:
    start, end = text.find("{"), text.rfind("}")
    if start == -1 or end == -1:
        raise ValueError("no JSON object in model output")
    return json.loads(text[start : end + 1])


def validate(doc: dict) -> None:
    picks = doc.get("picks")
    if not isinstance(picks, list) or len(picks) != 10:
        raise ValueError(f"expected exactly 10 picks, got {len(picks) if isinstance(picks, list) else picks!r}")
    tickers = set()
    for i, p in enumerate(picks, 1):
        missing = [k for k in REQUIRED if p.get(k) in (None, "")]
        if missing:
            raise ValueError(f"pick {i} ({p.get('ticker')}) missing {missing}")
        tickers.add(p["ticker"])
    if len(tickers) != 10:
        raise ValueError("duplicate tickers in picks")


def main() -> int:
    today = datetime.date.today().isoformat()
    prompt = PROMPT_FILE.read_text().replace("{{DATE}}", today)
    text = run_model(prompt)
    doc = extract_json(text)
    validate(doc)  # on failure we exit non-zero and the previous picks.json stays in place
    doc["sample"] = False
    doc["as_of"] = today
    doc["generated_at"] = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    doc["disclaimer"] = DISCLAIMER
    OUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    OUT_FILE.write_text(json.dumps(doc, indent=2) + "\n")
    print(f"wrote {OUT_FILE} with {len(doc['picks'])} picks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
