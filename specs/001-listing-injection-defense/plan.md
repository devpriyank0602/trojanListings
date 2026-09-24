# Implementation Plan: Trojan Listings — Listing-Borne Injection Measurement & Defence

**Branch**: `001-listing-injection-defense` | **Date**: 2026-09-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-listing-injection-defense/spec.md`

## Summary

A locally hosted web application that does two things: it measures how often an AI shopping agent obeys a hostile seller listing, and it screens listing content for those hostile instructions before an agent ever sees them.

A React front end lets a reviewer paste or upload a seller listing — title, description, item specifics, and photo — and get back a **TROJAN** or **CLEAN** verdict with the exact span responsible, the field it came from, and the named technique that concealed it. A Spring Boot backend owns everything else: a four-layer detector, an offline OCR path for instructions rendered into photos, and a harness that replays 25+ adversarial fixtures against a vision-capable agent and persists every trial so the before/after demo never depends on a live call.

Technical approach: ONNX Runtime Java runs [Horizon-Labs/prompt-injection-guard-small](https://huggingface.co/Horizon-Labs/prompt-injection-guard-small) in-process, loaded once at startup — matching the established internal pattern used by `nsfw-classifier-server` and `cpcguidesvc`. Ahead of it sits a hand-written structural layer that catches the obfuscation classes (zero-width, homoglyph, base64, chat-template delimiters, invisible HTML) that no classifier reliably sees, because those are string problems rather than semantic ones. Compliance judging is deterministic — no LLM-as-judge — so every reported number can be audited line by line.

## Technical Context

**Language/Version**: Java 17 (backend), TypeScript 5.x / React 18 (frontend), Python 3.11 (fixture-image rendering script only)

**Primary Dependencies**:
- Backend: Spring Boot 3.2, `com.microsoft.onnxruntime:onnxruntime` 1.19.x, `ai.djl.huggingface:tokenizers` 0.30.x (tokenizer only — **not** the DJL engine), `net.sourceforge.tess4j:tess4j` 5.x, Jackson
- Frontend: React 18, Vite 5, TypeScript, plain CSS (no component library — nothing to learn under deadline)
- Model: `Horizon-Labs/prompt-injection-guard-small`, Apache-2.0, `onnx/model_quantized.onnx` (int8)

**Storage**: Append-only JSONL files on local disk (`results/trials-<runId>.jsonl`). No database. Chosen because FR-039 requires per-trial durability on completion, FR-043 requires auditability, and a JSONL file is both by construction — you can `grep` a verdict in front of a judge.

**Testing**: JUnit 5 + Spring Boot Test (backend), Vitest + React Testing Library (frontend). Detector tests run against the corpus itself, so the evaluation set doubles as the test suite.

**Target Platform**: Local laptop — macOS arm64 and Linux x86_64. Browser front end on `localhost:5173`, backend on `localhost:8080`.

**Project Type**: Web application (React frontend + Spring Boot backend)

**Performance Goals**: Single-listing screening verdict in under 3 seconds (SC-009); realistically 100–400ms with the int8 model warm. Startup model load under 10 seconds.

**Constraints**:
- **No call to any internal organisational network or service** (FR-024) — hard constraint, not a preference
- Screening fully functional with the network disconnected (SC-013)
- Model and OCR language data cached on disk before demo time (FR-025)
- Service must start and remain useful even if the classifier fails to load (FR-030)

**Scale/Scope**: 25+ adversarial fixtures plus ~10 benign controls; 4 attack techniques × 4 attacker goals; single user, single concurrent request; ~4 screens of UI.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` is present but **unpopulated** — it is the verbatim Spec Kit template with `[PRINCIPLE_1_NAME]`-style placeholders and no ratified principles.

**Gate result: PASS (vacuously).** There are no project principles to check this design against. No violations can be asserted and none are claimed.

This is worth stating plainly rather than silently skipping: the plan below is therefore constrained only by the spec and by the internal engineering precedent documented in `research.md`, not by a ratified project constitution. If the team wants gates (test-first, library-first, simplicity limits), run `/speckit-constitution` before `/speckit-implement` — after implementation starts, a new constitution is retroactive and will generate churn rather than guidance.

**Post-Phase 1 re-check: PASS (unchanged).** No constitution exists to re-evaluate against.

## Project Structure

### Documentation (this feature)

```text
specs/001-listing-injection-defense/
├── plan.md              # This file
├── research.md          # Phase 0 — library survey + every technology decision
├── data-model.md        # Phase 1 — entities, fields, validation, state
├── quickstart.md        # Phase 1 — how to run and validate end to end
├── contracts/           # Phase 1 — REST API, fixture schema, UI contract
│   ├── rest-api.md
│   ├── fixture-schema.md
│   └── ui-contract.md
├── checklists/
│   └── requirements.md  # Spec quality checklist (16/16 passing)
└── tasks.md             # Phase 2 — NOT created by /speckit-plan
```

### Source Code (repository root)

```text
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/ebay/trojanlistings/
    │   │   ├── TrojanListingsApplication.java
    │   │   ├── api/                      # REST controllers + DTOs
    │   │   │   ├── ScreeningController.java
    │   │   │   ├── MeasurementController.java
    │   │   │   └── dto/
    │   │   ├── detector/                 # the four screening layers
    │   │   │   ├── Detector.java                  # orchestrates all layers
    │   │   │   ├── structural/                    # Layer 1 — obfuscation
    │   │   │   │   ├── ZeroWidthDetector.java
    │   │   │   │   ├── HomoglyphDetector.java
    │   │   │   │   ├── EncodedPayloadDetector.java
    │   │   │   │   ├── ChatTemplateDetector.java
    │   │   │   │   └── InvisibleMarkupDetector.java
    │   │   │   ├── pattern/                       # Layer 2 — known phrasings
    │   │   │   │   └── InstructionPatternDetector.java
    │   │   │   ├── classifier/                    # Layer 3 — ONNX model
    │   │   │   │   ├── OnnxInjectionClassifier.java
    │   │   │   │   ├── ModelLoader.java
    │   │   │   │   └── SentenceSplitter.java
    │   │   │   └── ocr/                           # Layer 4 — image text
    │   │   │       └── ImageTextExtractor.java
    │   │   ├── harness/                  # exposure measurement
    │   │   │   ├── MeasurementRunner.java
    │   │   │   ├── AgentClient.java               # vision-capable agent
    │   │   │   ├── ComplianceJudge.java           # deterministic, no LLM
    │   │   │   └── TrialStore.java                # JSONL persistence
    │   │   └── corpus/
    │   │       ├── FixtureLoader.java
    │   │       └── Fixture.java
    │   └── resources/
    │       ├── application.yml
    │       └── patterns/instruction-patterns.txt
    └── test/java/com/ebay/trojanlistings/
        ├── detector/                     # per-layer unit tests
        ├── harness/                      # judge + store tests
        └── EvaluationSetTest.java        # catch rate / false-alarm rate

frontend/
├── package.json
├── vite.config.ts
└── src/
    ├── App.tsx
    ├── api/client.ts
    ├── components/
    │   ├── ListingInput.tsx          # paste listing + upload photo
    │   ├── VerdictPanel.tsx          # TROJAN / CLEAN + confidence
    │   ├── HighlightedSpan.tsx       # marks the span, reveals hidden chars
    │   ├── EvidenceList.tsx          # why it was called TROJAN
    │   ├── ImageEvidence.tsx         # photo + extracted text
    │   └── ExposureTable.tsx         # recorded before/after results
    └── pages/
        ├── ScreenListing.tsx
        └── ExposureReport.tsx

corpus/                               # data, not code (existing dir)
├── fixtures/*.json                   # 25+ hostile + ~10 benign
└── images/*.png                      # rendered in-image fixtures

scripts/                              # existing dir
├── download-model.sh                 # fetch ONNX + tokenizer.json
├── download-tessdata.sh              # fetch eng.traineddata
└── render_fixture_images.py          # render instructions into photos

models/                               # gitignored, populated by scripts
results/                              # gitignored, JSONL trial records
```

**Structure Decision**: Web application layout (`backend/` + `frontend/`), matching the clarified decision that the deliverable is a locally hosted web app with a single backend service owning both screening and stored results.

The existing empty `detector/`, `harness/` and `ui/` directories at repo root are **superseded** — they were scaffolding for the original Python/Streamlit plan and those concerns now live as Java packages under `backend/` and React components under `frontend/`. `corpus/` and `scripts/` are reused as-is, since fixtures and helper scripts are genuinely repo-level data rather than backend code. The root `requirements.txt` narrows to serving only `scripts/render_fixture_images.py`.

## Implementation Sequencing

Ordered by the spec's drop order, not by architectural tidiness. Scope exceeds the original ~10h estimate, so the ordering is load-bearing.

| Stage | Work | FRs | If we stop here |
|---|---|---|---|
| **1** | Corpus: 25+ text fixtures across techniques 1–3, all 4 goals, + benign controls | FR-001→006, 042 | Corpus exists, nothing runs |
| **2** | Harness: agent client, deterministic judge, JSONL store, text-only measurement | FR-007→011, 039→043 | **Bank the P1 recording here.** Headline number exists |
| **3** | Detector layers 1+2 (structural + pattern), no model | FR-012→017, 030 | Catches every obfuscation class. Demo-viable alone |
| **4** | Detector layer 3 (ONNX classifier in-process) | FR-029 | Catches plain-language attacks too |
| **5** | Frontend: listing input, verdict, highlighted span, evidence | FR-018→021 | Presentable product |
| **6** | In-image: render fixtures, OCR extraction, vision agent measurement | FR-031→038 | Fourth technique complete |
| **7** | Comparison baseline + rehearsal | FR-022, 023 | Gap analysis |

**Hard rule carried from the spec**: stage 2's recording is banked before stage 3 starts. Stage 6 is the heaviest item and the first to shed after stage 7.

## Key Design Decisions

Full reasoning and alternatives in [research.md](./research.md). Summary:

1. **Four-layer detector, structural first.** The obfuscation attacks (Class 3) are string problems, not semantic ones — a classifier reading text with zero-width characters spliced through it sees mangled tokens, while a 20-line scanner sees the attack immediately. Running structural first also means the classifier scores *normalised* text, which materially improves its hit rate.
2. **ONNX Runtime Java in-process, DJL for tokenization only.** The internal house pattern. `ai.onnxruntime` owns inference; `ai.djl.huggingface.tokenizers` reads `tokenizer.json` and solves the tokenizer-porting problem without dragging in a second inference framework.
3. **Sentence-level scoring.** Borrowed from StackOne Defender's Sentence Fragment Extraction: split the assembled listing into sentences and score each one separately, so a two-line injection buried in a 400-word description isn't diluted to nothing by the surrounding benign text. This is also what produces the highlighted span the UI needs — the span is simply the highest-scoring sentence.
4. **Deterministic compliance judging.** No model in the judging loop. Each fixture declares checkable full- and partial-compliance conditions; the judge evaluates them mechanically and records which condition fired and what text satisfied it.
5. **Graceful degradation everywhere.** Classifier fails to load → structural and pattern layers still return verdicts, mode reported as degraded (FR-030). OCR unavailable → text verdict still returned, image reported as not screened (FR-037). Neither is a startup failure.

## Traceability

All 43 functional requirements map to a component. The mapping lives in [contracts/rest-api.md](./contracts/rest-api.md) per endpoint and in [data-model.md](./data-model.md) per entity. Requirements with no code home: none.

## Complexity Tracking

No constitution exists, so no violations can be raised against one. Recording two genuine complexity costs anyway, because they are real and were accepted deliberately during clarification:

| Cost | Why accepted | Simpler alternative rejected because |
|---|---|---|
| Native OCR dependency (Tess4J/JNA) added to a service already carrying ONNX native bindings | In-image attacks were chosen as a fully built and measured technique (clarification Q3, Option D) | Skipping OCR would leave technique 4 authored but unscreened, contradicting FR-035 and SC-015. Pure-Java OCR exists but accuracy is far below usable |
| Two model-ish runtimes in one JVM (ORT + Tesseract natives) | Both are required by spec and both must run offline | A sidecar per capability was explicitly rejected in clarification Q2 in favour of the internal in-process precedent |

---

# Addendum: Reliable local acquisition of the classifier model

**Date**: 2026-09-24 | **Scope**: maintenance delta on stage 4, not a new feature

## Why this addendum exists

The model finally downloaded and the classifier was evaluated for the first time — see [research.md §9.2](./research.md). Getting there exposed a set of defects in how the model is acquired and loaded locally. None of them are design errors in stage 4; they are gaps that only surface on a slow or filtered network, which is exactly the condition the demo machine was in.

Phase 0 and Phase 1 artifacts for this feature are already complete and are **not** regenerated here. `research.md`, `data-model.md`, `contracts/` and `quickstart.md` remain the source of truth; this addendum adds an implementation delta and one quickstart edit.

**Constitution Check: PASS (vacuously, unchanged).** `.specify/memory/constitution.md` is still the unpopulated template. No gates exist to evaluate this delta against.

## The false lead, recorded so nobody re-investigates it

The natural first hypothesis was missing HuggingFace credentials. It is wrong, and the plan should say so permanently: `Horizon-Labs/prompt-injection-guard-small` reports `gated: false, private: false`, the download is an unauthenticated `curl`, and no code path in the repository reads a HuggingFace token. `ModelLoader` opens two local files and nothing else. **A failure to acquire this model is always a network or transfer problem, never an authentication one.**

## Defects to fix

| # | Defect | Evidence | Severity |
|---|---|---|---|
| D1 | `download-model.sh` cannot resume a partial transfer | `curl -fSL` with no `-C -`; observed aborting at 222 MB of 256 MB with `curl: (18)`, and a re-run restarts from zero | **High** — on a slow link the 256 MB file may never complete |
| D2 | No stall detection | The same transfer degraded to ~3 KB/s for 40 s before dying; `--retry` does not fire on a live-but-stalled socket | **High** — pairs with D1 to waste whole download attempts |
| D3 | Integrity is checked by file size only | `fetch()` compares against a hardcoded `min_bytes`; a truncated-but-large file passes | **Medium** — a corrupt model reaches `ModelLoader` |
| D4 | `ModelLoader` cannot distinguish missing from corrupt | Missing files produce a clear message; a truncated `.onnx` produces a raw `OrtException` string as `unavailableReason` | **Medium** — misdiagnosis on demo morning |
| D5 | `trojan.classifier.threshold` has no environment override | `application.yml` line 27 is a bare `0.5` while every sibling key uses `${VAR:default}` | **Low** — but it blocked the §9.2 threshold sweep, which needed `SPRING_APPLICATION_JSON` as a workaround |
| D6 | Classifier findings in item-specifics carry no source field (FR-014) | `EvaluationSetTest.findingsCarryProvenance` fails whenever the classifier is active | **Medium** — real spec violation, only reachable once the model loads |

## Design

### Integrity data comes from HuggingFace, not from hardcoded constants

The `resolve/main` endpoint returns the authoritative size and hash in response headers before any body transfer:

```text
x-linked-size: 268409234
x-linked-etag: "541471ab9de1af3e0db63b8208ec7fdc3ef74202b76c5470115e153c052dae02"
```

A `curl -sI` preflight captures both. This fixes D3 without embedding a checksum that goes stale the moment the upstream repo is updated, and it gives the resume loop (D1) a real target size to compare against rather than a guessed `min_bytes` floor.

### Resume loop replaces single-shot fetch

`fetch()` becomes: preflight for expected size and hash → loop while local size is below expected, issuing `curl -C -` each pass → verify SHA-256 on completion → delete and fail loudly on mismatch. Bounded attempt count so a genuinely blocked network still terminates with the existing "restricted egress, drop the files in manually" guidance rather than looping forever.

Stall detection (D2) is `--speed-limit`/`--speed-time`, which aborts a socket that falls below a floor for a sustained window; because the loop resumes, an aborted stall costs seconds rather than the whole transfer.

### Load-time validation is a precheck, not a catch block

`ModelLoader.load()` gains a size sanity check between the existing `Files.isRegularFile` test and session creation, so `unavailableReason` distinguishes *absent* from *truncated* and names the remedy. The FR-030 contract is unchanged: every one of these states still starts the service in degraded mode.

## Work items

| Stage | Work | Fixes | Files |
|---|---|---|---|
| **A1** | Preflight `curl -sI` for `x-linked-size` / `x-linked-etag`; fall back to the current `min_bytes` behaviour if headers are absent | D3 | `scripts/download-model.sh` |
| **A2** | Bounded resume loop with `-C -`, `--retry-all-errors`, `--retry-delay`, `--speed-limit`/`--speed-time` | D1, D2 | `scripts/download-model.sh` |
| **A3** | Verify SHA-256 after transfer; on mismatch delete the file and exit non-zero with the actual vs expected hash | D3 | `scripts/download-model.sh` |
| **A4** | Add a comment block stating the model is public and ungated and that no token is required; keep the existing restricted-egress note | — | `scripts/download-model.sh` |
| **A5** | Apply the same resume/verify treatment to `download-tessdata.sh` for consistency | D1, D2 | `scripts/download-tessdata.sh` |
| **A6** | Size precheck in `load()`; distinct `unavailableReason` for absent vs truncated, each naming the remedy | D4 | `ModelLoader.java` |
| **A7** | `threshold: ${TROJAN_CLASSIFIER_THRESHOLD:0.5}` | D5 | `application.yml` |
| **A8** | Fix field attribution for classifier findings so `sourceField` resolves for item-specifics | D6 | `OnnxInjectionClassifier.java` |
| **A9** | Expand the `classifierLoaded: false` troubleshooting row to separate "never downloaded", "partial download" and "checksum mismatch" | — | `quickstart.md` |

A8 is included because it is only reachable once the model loads locally, so it belongs to the same change set — but it is independent of A1–A7 and can be dropped without affecting them.

## Explicitly out of scope

**Rehabilitating the classifier.** A7 raises the default threshold to 0.85, which is a decision about the *operating point*, not an attempt to make the layer useful. [research.md §9.2](./research.md) records why: at 0.5 the running application flags an ordinary listing as hostile (`Condition: Used` scores 0.63), so 0.5 is not a defensible shipped default regardless of what it demonstrates. At 0.85 the classifier still contributes zero unique detections and still false-alarms on `bn-trigger-03`. The measured finding is unchanged and stays documented in §9.2, the sweep table, the `application.yml` comment and `README.md` — it simply no longer lives in a deliberately failing test.

## Acceptance

1. `scripts/download-model.sh` run against an empty `models/` completes both files and passes SHA-256 verification.
2. The same script, interrupted mid-transfer and re-run, resumes rather than restarting, and still verifies.
3. A deliberately truncated `model_quantized.onnx` causes the script to fail with a hash mismatch, and causes `ModelLoader` to report a "truncated" reason while the service still starts (FR-030).
4. `TROJAN_CLASSIFIER_THRESHOLD=0.85 mvn test` reproduces the §9.2 sweep row without `SPRING_APPLICATION_JSON`.
5. `EvaluationSetTest.findingsCarryProvenance` passes with the classifier active (A8 only).
6. `EvaluationSetTest` passes in full at the shipped 0.85 default, and `TROJAN_CLASSIFIER_THRESHOLD=0.5` reproduces the 38.5% false-alarm rate that §9.2 records.
7. A hand-run of the application at the default correctly returns CLEAN for ordinary listings and TROJAN for planted injections.
