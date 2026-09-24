# Trojan Listings

**Measuring and defending against prompt injection in seller listings.**

eBay Bengaluru AI Hackathon 2026 · Trust, risk & customer confidence

> ⚠️ **All content in this repository is synthetic adversarial research material.**
> No real seller, buyer or live item is referenced. No code path publishes to any live
> marketplace surface. Nothing here runs against production systems.

---

## The problem

Listings are written by sellers and increasingly read by AI shopping agents — eBay's
own assistants today, ChatGPT and Perplexity next. A language model cannot reliably
separate "content to summarise" from "instructions to obey", so **every seller-authored
field becomes untrusted input to a model eBay often does not control**.

Unlike most injection scenarios: the attacker is the content author *by design*, the
motive is revenue rather than mischief, and the vulnerable model is frequently not
ours to patch.

## What this is

Two things, both running locally with no dependency on any internal eBay service.

1. **A measurement harness** — 25 adversarial listing fixtures across 4 attack
   techniques and 4 attacker goals, replayed against a vision-capable shopping agent,
   producing an attack-success-rate per technique. This number does not currently
   exist at eBay.
2. **A four-layer detector** — screens a listing and returns **TROJAN** or **CLEAN**
   with the responsible span, the field it came from, and the technique that concealed
   it.

## Results

From `mvn test` on the 38-fixture evaluation set (25 hostile, 13 benign controls):

| Metric | Result | Bar |
|---|---|---|
| Catch rate, text fixtures | **100%** (21/21) | ≥80% |
| Catch rate, in-image fixtures | **75%** (3/4) | ≥70% |
| False-alarm rate, benign controls | **0%** (0/13) | ≤10% |
| Single-listing screening latency | **<5 ms** | <3000 ms |

### The gap against general-purpose screening

| Technique | Listing-aware | General-purpose |
|---|---|---|
| Free text | 100% | 100% |
| Structured field | 100% | 100% |
| **Obfuscated** | **100%** | **25%** |
| **In image** | **75%** | **0%** |

General-purpose prompt-injection tooling handles plain-text attacks perfectly well and
leaves the listing-specific ones on the table. That gap is the project.

## Quick start

```bash
# One-time setup
./scripts/download-tessdata.sh        # OCR language data
brew install tesseract                # OCR binary (macOS; apt install tesseract-ocr on Linux)
./scripts/download-model.sh           # optional — see "Degraded mode" below
pip install -r requirements.txt && python scripts/render_fixture_images.py

# Run
cd backend  && mvn spring-boot:run    # :8080
cd frontend && npm install && npm run dev   # :5173
```

Open <http://localhost:5173>.

```bash
# Verify
cd backend && mvn test                # 94 tests, prints the evaluation numbers
```

Full validation walkthrough: [`specs/001-listing-injection-defense/quickstart.md`](specs/001-listing-injection-defense/quickstart.md)

## How screening works

Four layers, in this order:

1. **OCR** — text rendered into the listing photo joins the assembled listing, so it
   is screened by everything downstream rather than by a special case.
2. **Structural** — zero-width characters spliced mid-word, Cyrillic/Greek homoglyphs,
   base64 and spaced-letter payloads, forged chat delimiters, white-on-white markup.
   These are *string* problems, not semantic ones, and a short scanner beats a
   classifier at them outright. Running first also lets this layer hand normalised text
   forward, so the classifier sees real words instead of the attacker's mangled tokens.
3. **Pattern** — known instruction phrasings. Every pattern requires an imperative
   *and* a target, never a bare keyword, or a cookbook called *"Ignore All Previous
   Diets"* gets flagged.
4. **Classifier** — an ONNX transformer scored per sentence, so a two-line injection
   inside a 400-word description is not diluted below threshold.

Structural findings are **not** thresholded — a legitimate listing does not contain a
zero-width character spliced mid-word, so presence is proof. Classifier findings are.

## Degraded mode

The service starts and stays useful when optional subsystems are missing, and says so
rather than hiding it:

| Missing | Behaviour |
|---|---|
| ONNX classifier | Structural + pattern layers still screen. `mode: DEGRADED_NO_CLASSIFIER`, amber banner in the UI. **The 100% text catch rate above was measured in this mode** — the structural layer does the work. |
| Tesseract | Text still screened. Images reported `NOT_SCREENED`, explicitly **not** clean. |
| `AGENT_API_KEY` | Screening and all recorded results unaffected. Only starting a *new* measurement run is unavailable (503, not 500). |

## Layout

```
backend/    Java 17 · Spring Boot 3.2 · ONNX Runtime (in-process) · Tesseract CLI
frontend/   React 18 · Vite · TypeScript
corpus/     38 fixtures (JSON) + rendered attack images
scripts/    model + OCR asset download, fixture image rendering
specs/      spec, plan, research, data model, contracts, tasks
```

## Design decisions

Every technology choice and its rejected alternatives:
[`specs/001-listing-injection-defense/research.md`](specs/001-listing-injection-defense/research.md)

Two worth knowing up front:

- **No LLM-as-judge.** Compliance is graded by mechanically checkable per-fixture
  conditions. A judge model reading a response produced under attack is reading the
  attacker's output — and a compromised judge reports clean numbers. The cost is that
  attacks succeeding through unanticipated phrasing score as refusals, making the
  reported rate a **conservative floor**. That is a better thing to defend than a
  number produced by a second model.
- **ONNX Runtime in-process, not an inference endpoint.** Matches the established
  internal pattern (`nsfw-classifier-server`, `cpcguidesvc`, HomeSplice) and keeps the
  system free of any internal network dependency.

## Safety

1. **Synthetic fixtures only.** Enforced by `CorpusSafetyTest` — every seller name must
   carry a `_fictional` suffix, no 12-digit item ids, no links to live marketplaces.
2. **Sandboxed agents only.** Nothing runs against production or live buyer traffic.
3. **No real party targeted.** Competitor-disparagement fixtures name fabricated
   sellers exclusively.
4. **Responsible disclosure.** A live exploitable path against a real assistant goes to
   `gis-ai@ebay.com` privately — not into the slides.
