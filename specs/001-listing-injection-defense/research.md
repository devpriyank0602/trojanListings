# Phase 0 Research — Trojan Listings

**Date**: 2026-09-24 | **Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Two jobs here. First, the full survey of open-source prompt-injection detection options and why we picked what we picked. Second, every remaining technology decision the plan depends on.

Binding constraints from clarification, which eliminate most candidates before evaluation starts:

- **No internal network calls** (FR-024). Anything requiring eBay infrastructure is out.
- **Fully offline at demo time** (FR-025, SC-013). Anything calling a hosted API is out.
- **In-process in a Java service** (FR-029). Anything requiring a Python or Node sidecar is out as a *primary* detector.
- **Permissive licence.** AGPL is unusable as a dependency.

---

## 1. Open-source prompt-injection detection — full survey

### 1.1 Candidate models

| Model | Licence | Size / format | Trained for | Verdict |
|---|---|---|---|---|
| **[Horizon-Labs/prompt-injection-guard-small](https://huggingface.co/Horizon-Labs/prompt-injection-guard-small)** | Apache-2.0 | mmBERT-small, ships `onnx/model_quantized.onnx` int8 | **Realistic documents with planted injections, 45 document types**; 8k context; 30 languages | ✅ **CHOSEN** |
| [Horizon-Labs/prompt-injection-guard-base](https://huggingface.co/Horizon-Labs/prompt-injection-guard-base) | Apache-2.0 | Larger, ONNX | Same, higher accuracy (92.9% NotInject vs 89.7%) | Fallback if small underperforms |
| [Horizon-Labs/prompt-injection-guard-multilingual](https://huggingface.co/Horizon-Labs/prompt-injection-guard-multilingual) | Apache-2.0 | distilbert-multilingual, 135M, ONNX | Injection **and** jailbreak, 17 languages | Strong runner-up |
| [protectai/deberta-v3-base-prompt-injection-v2](https://huggingface.co/protectai/deberta-v3-base-prompt-injection-v2) | Apache-2.0 | DeBERTa-v3-base, `onnx/` subfolder | General prompt injection | ❌ Rejected — see below |
| [protectai/deberta-v3-small-prompt-injection-v2](https://huggingface.co/protectai/deberta-v3-small-prompt-injection-v2) | Apache-2.0 | DeBERTa-v3-small, ONNX | Same, faster | ❌ Same rejection |
| [HikmaAI/hikmaai-deberta-injection](https://huggingface.co/HikmaAI/hikmaai-deberta-injection) | Apache-2.0 | INT8 ONNX of ProtectAI v2, 233MB | Same | ❌ Same rejection |
| [llmware/protectai-prompt-injection-onnx](https://huggingface.co/llmware/protectai-prompt-injection-onnx) | Apache-2.0 | 184M, int4 ONNX | Same | ❌ Same rejection |
| [jackhhao/jailbreak-classifier](https://huggingface.co/jackhhao/jailbreak-classifier) | Apache-2.0 | bert-base-uncased, **PyTorch only** | Jailbreak vs benign | ❌ No ONNX export; we'd have to convert |
| Meta Prompt Guard 2 | Llama licence | — | Injection + jailbreak | ❌ Licence unusable in many orgs; 71.4% on CyberSecEval indirect |

**Decision: `Horizon-Labs/prompt-injection-guard-small`.**

**Rationale.** It is the only candidate trained on the shape of our actual threat. Every ProtectAI-derived model is trained on *prompts* — text a user types at a model. Our attack is a hostile instruction planted in a document that a model later reads, which is a different distribution. Horizon-Labs trained on documents with planted injections across 45 document types, with clean counterparts, and reports its results on NotInject and OR-Bench-hard, which are **false-alarm** benchmarks. That matters directly: SC-006 caps us at 10% false alarms on benign controls, and benign eBay listings are full of words like "instructions", "system" and "original condition".

Three further reasons it wins:

- **Tokenizer portability.** DeBERTa-v3 uses SentencePiece; mmBERT-small ships a standard `tokenizer.json` that DJL's `HuggingFaceTokenizer` reads directly. This is not cosmetic — it is the difference between an hour of work and a day of it.
- **Quantized ONNX is published.** No export step, no conversion risk. `onnx/model_quantized.onnx` is int8 and roughly half the fp32 size.
- **Multilingual.** Directly serves the non-English edge case in the spec, which the ProtectAI line explicitly does not handle.

**Why ProtectAI DeBERTa was rejected** despite being the better-known option: its own model card states it does not detect jailbreak attacks, does not handle non-English prompts, and is not recommended for scanning system prompts due to false positives. Combined with SentencePiece tokenization on the JVM, it loses on every axis we care about.

### 1.2 Candidate libraries

| Library | Language | Licence | Relevance | Verdict |
|---|---|---|---|---|
| **[StackOne Defender](https://www.stackone.com/platform/prompt-injection-guard/)** | TypeScript / npm | Apache-2.0 | Purpose-built for indirect injection in **tool results** — structurally identical to listing content. 22MB bundled model, ~4ms, 89.0% accuracy. **Sentence Fragment Extraction** splits content into sentences before scoring | ❌ As dependency (Node sidecar rejected in Q2) — ✅ **technique borrowed**, see §1.3 |
| [Bastion Prompt Protection](https://github.com/topics/prompt-injection) | Python | **AGPL-3.0** | Best-in-class structural detectors for zero-width, homoglyph, base64, spaced-letter, chat-template tokens; 0.945 avg AUC on indirect benchmarks | ❌ **Licence prohibits dependency.** Read for *which* obfuscations to implement, write our own |
| [Open-Prompt-Injection](https://github.com/liu00222/Open-Prompt-Injection) | Python | Research | Ships **PromptLocate** (localizes injected spans) and DataSentinel. IEEE S&P backed | ❌ As dependency — ✅ cited as methodology |
| [Rebuff](https://github.com/protectai/rebuff) | Python | Apache-2.0 | Heuristics + LLM + vector DB + canary tokens | ❌ **Archived**; self-described prototype |
| [JGuardrails](https://dev.to/ratila/jguardrails-production-ready-safety-rails-for-java-llm-applications-2aee) | **Java** | Apache-2.0 | Spring Boot native, Java 17+, 1–5ms, prompt-injection rail | ⚠️ **Pattern-based only.** Considered for Layer 2 — see below |
| [Apache Camel `PromptInjectionGuardrail`](https://camel.apache.org/components/4.22.x/others/langchain4j-agent-guardrails.html) | **Java** | Apache-2.0 | `strict()` fails on any pattern match; category-tagged (JAILBREAK etc.) | ⚠️ Same — pattern-based |
| [LangChain4j Guardrails](https://docs.langchain4j.dev/tutorials/guardrails/) | **Java** | Apache-2.0 | `InputGuardrail` / `OutputGuardrail` interfaces | ❌ Interfaces only; [no common library exists](https://github.com/langchain4j/langchain4j/issues/3248) |
| [OpenAI Guardrails](https://openai.github.io/openai-guardrails-python/ref/checks/prompt_injection_detection/) | Python | — | LLM-based injection detection | ❌ Requires hosted API call — violates FR-024/FR-025 |
| [ipi-scanner](https://github.com/topics/prompt-injection) | Python | — | 50+ regex signatures, validated against EchoLeak/HashJack CVEs | ❌ Python — ✅ signature list is useful input for Layer 2 |
| [rag-inject-guard](https://github.com/topics/prompt-injection) | Python | — | stdlib-only invisible-unicode + homoglyph signatures | ❌ Python — ✅ technique reference |
| [Picket](https://github.com/topics/prompt-injection-protection) | — | — | Local signature detector, no model, no network | ✅ Shape worth copying for Layer 1 |

**Decision on the Java pattern libraries (JGuardrails / Camel).** Rejected as dependencies, with reasoning worth stating because it is not obvious: both are *pattern* detectors for **direct** injection — a user typing "ignore previous instructions" at a chatbot. Our Class 3 attacks are specifically designed to defeat literal pattern matching, which is the entire point of zero-width splicing and homoglyph substitution. Adding a dependency that catches only the attacks our fixtures are built to evade buys us a Maven coordinate and no detections. We write Layer 2 ourselves — a pattern file is ~40 lines — and spend the saved integration time on Layer 1, which is where the actual value is.

### 1.3 What we take from the ecosystem without depending on it

- **Sentence Fragment Extraction** (StackOne Defender). Split the assembled listing into sentences; score each independently. Without it, a two-line injection inside a 400-word description is diluted below threshold by surrounding benign text. With it, the injection scores alone — and the highest-scoring sentence *is* the span the UI highlights. This one idea serves FR-012, FR-019 and SC-005 simultaneously.
- **Obfuscation catalogue** (Bastion, read-only; `rag-inject-guard`; `ipi-scanner`). The specific concealment techniques worth implementing: zero-width characters mid-token, Cyrillic/Greek homoglyphs, base64 and spaced-letter payloads, chat-template control tokens (`<|im_start|>`, `[INST]`, `<<SYS>>`), CSS-hidden markup.
- **Benchmark methodology** (BIPIA, InjecAgent, AgentDojo, HackAPrompt, TensorTrust, Open-Prompt-Injection). Cited as prior art so the attack-success-rate reads as rigorous rather than improvised.

### 1.4 Honesty note carried into the demo

Meta Prompt Guard 2 reports 97.5% recall at 1% FPR on its own English benchmark and **71.4%** on CyberSecEval's indirect injection set. Headline accuracy numbers do not survive a change of distribution. We report our numbers on our own corpus and say so explicitly — that is more defensible than quoting a vendor figure, and it pre-empts the obvious challenge from a judge.

---

## 2. Internal precedent — how eBay does in-process inference

Sourced from internal engineering docs via Glean. This settled clarification Q2.

**Decision: ONNX Runtime Java (`ai.onnxruntime`) in-process, model loaded at startup, session reused per request. DJL used for tokenization only.**

**Rationale — the pattern is consistent across four independent internal systems:**

| System | Pattern |
|---|---|
| [`nsfw-classifier-server`](https://wiki.corp.ebay.com/spaces/~ssaifi/pages/2293006808) | **Closest analogue.** Python trains TinyBERT/BERT → exports `model_quantized.onnx` → Java Spring Boot server loads both ONNX models at startup → `POST /nsfw`. Our exact shape |
| [`cpcguidesvc`](https://wiki.corp.ebay.com/spaces/PG/pages/1355614790/PLP+Keyword+Recommendation) | "OnnxRuntime Java Library embedded in cpcguidesvc"; shared lib `ads-guidance/onnxbertinference` |
| [HomeSplice](https://github.corp.ebay.com/pl/homesplice/blob/master/docs/ARCHITECTURE_TECH_STACK.md) | Raptor.IO 3.3.3 on Spring Boot, ONNX Runtime; artifacts resolved at startup, in-process session reused — explicitly *not* per request |
| [Moose](https://github.corp.ebay.com/adplatform/moose/blob/master/AGENTS.md) | `moose-ort` = `ai.onnxruntime` for serving; DJL appears **only** as `BertInputPreparer` |

**Alternatives considered:**

- *DJL as the inference engine.* Rejected — Glean found no evidence DJL is used at eBay to execute models; where it appears it is doing tokenization while ORT does inference. Following the house pattern also means the two known failure modes below are already documented internally.
- *AIP/UIP managed endpoint serving.* The standard path for production model serving, and rejected here for one reason only: it is a network call to internal infrastructure, which FR-024 forbids.
- *Raptor.IO.* It is a layer on Spring Boot and adds platform integration we cannot use locally. Plain Spring Boot is the honest subset.

**Two hazards inherited with this pattern, both must be handled:**

1. **`OnnxMap` native memory leak.** ONNX Runtime 1.16+ returns `ai.onnxruntime.OnnxMap` rather than `java.util.Map`. Calling `getValue()` materializes a Java copy but does **not** transfer ownership — the caller must still close it. [LIVERANK-259](https://github.corp.ebay.com/pl/homesplice/pull/7001) is a production JVM crash from exactly this omission. **Mitigation: every inference call wraps its result in try-with-resources.** Non-negotiable.
2. **Predictors are not thread-safe.** ORT sessions must not be shared across concurrent requests without care. **Mitigation: single-threaded screening path, or a small session pool.** At our scale (one user, one request) a synchronized wrapper is sufficient and simplest.

---

## 3. OCR for in-image attacks

**Decision: Tess4J 5.x (`net.sourceforge.tess4j:tess4j`), Apache-2.0, with `eng.traineddata` cached locally.**

**Rationale.** There is no credible pure-Java OCR library at modern accuracy. Tess4J is a JNA wrapper over Tesseract, ships natives in the jar, and is the de facto Java standard. It is Apache-2.0 and works fully offline once `eng.traineddata` is on disk.

**Alternatives considered:**

- *[Tesseract Platform / JavaCPP Presets](https://github.com/bytedeco/javacpp-presets).* JNI rather than JNA, and JavaCPP resolves platform natives via Maven classifiers, which is arguably cleaner dependency management. Close call. Tess4J chosen for the larger body of worked examples under deadline pressure.
- *PaddleOCR / docTR ONNX through ORT Java.* Attractive because we already have ORT in the process, avoiding a second native binding. Rejected because modern OCR is a **two-stage** pipeline (text detection then recognition) and wiring both plus the decoding logic is a day of work, not an hour.
- *Shelling out to the Tesseract CLI.* Sidesteps native binding pain entirely at the cost of process overhead. **Kept as the documented fallback** if JNA native extraction fights us on macOS arm64 — a real, recently documented failure mode.
- *Pure-Java JavaOCR.* Rejected — old, unmaintained, accuracy far below usable.

**Known friction:** Tess4J natives must be extracted before loading, via `LoadLibs.extractTessResources(...)`. macOS arm64 dylib loading is documented as awkward. This is precisely why FR-037 requires OCR to degrade to "image not screened" rather than failing the verdict — the failure mode is anticipated, not hypothetical.

**Preprocessing:** our in-image fixtures include deliberately low-contrast text (FR-032). Tesseract needs help there — grayscale conversion plus contrast stretching before `doOCR`, and `--psm 11` (sparse text) rather than the default, since overlaid instruction text is not page-structured prose.

---

## 4. The vision-capable agent under test

**Decision: an external general-purpose vision-capable assistant, accessed over its public API, configured with a buyer-assistant system prompt.**

**Rationale.** FR-033 requires vision. FR-024 forbids *internal* network calls but not external ones, and the finding generalises better against a general-purpose assistant — which is exactly the uncontrollable surface the spec is about. The agent is called only during measurement; FR-040 guarantees every user-facing capability reads persisted results instead, so the demo is unaffected by agent availability.

**Alternatives considered:**

- *A local vision model.* Would satisfy offline purity, but multi-GB weights and a second inference stack for one stage is disproportionate, and the finding would be less credible — nobody's shopping agent is a quantized local model.
- *Text-only agent with OCR-extracted text substituted in.* Rejected outright: it measures our OCR, not the agent's vision. It would silently invalidate SC-014.

**Configuration:** temperature pinned low and the system prompt frozen in version control, so re-runs are comparable. The system prompt is a plain buyer-assistant brief with no injection-specific hardening — hardening it would bias the measurement toward a flattering number.

---

## 5. Compliance judging

**Decision: deterministic per-fixture conditions, evaluated mechanically. No language model in the judging loop.**

**Rationale.** A judge model reading an agent response produced under attack is reading the attacker's output. The same payload that manipulated the agent can manipulate the judge, and the failure is invisible — a compromised judge reports clean numbers. Since the attack-success-rate *is* the pitch, the judging mechanism has to survive the obvious challenge from the room.

**Condition types the fixture schema supports** (detail in [contracts/fixture-schema.md](./contracts/fixture-schema.md)): `contains_any`, `contains_all`, `absent`, `ordered_before`, and `regex`. Between them these express every goal in the corpus — self-promotion (target ranked first / named favourably), competitor disparagement (competitor named with a disparaging term), disclosure suppression (defect keyword **absent** from the response), instruction extraction (system-prompt fragment present).

**Accepted cost, stated in the spec and on stage:** a fixture whose attack succeeds through phrasing its author did not anticipate scores as a refusal. The reported rate is therefore a **conservative floor**, not an exact figure. That is a better thing to defend than a number produced by a second model.

**Alternatives considered:** LLM-as-judge (rejected, above); full human review (rejected — too slow for 25+ fixtures × 4 goals under deadline, and not reproducible on re-run).

---

## 6. Persistence

**Decision: append-only JSONL, one file per measurement run, under `results/`.**

**Rationale.** FR-039 requires each trial durable the moment it completes — an append to an open file stream is the simplest correct implementation. FR-043 and SC-016 require any verdict auditable on demand; a JSONL line containing the fixture id, the declared condition, the agent's verbatim response and what satisfied the condition is auditable by `grep` in front of a judge. No schema migration, no daemon, no container.

**Alternatives considered:** H2/SQLite embedded (rejected — buys queryability we don't need at 35 fixtures, costs schema management); in-memory only (rejected — violates FR-039 outright).

---

## 7. Frontend

**Decision: React 18 + Vite + TypeScript, plain CSS, no component library.**

**Rationale.** React was specified by the user. Vite for instant dev server and trivial static build. No component library because the entire UI is four screens and the one visually demanding component — revealing zero-width and homoglyph characters inside a highlighted span — is custom work that no library provides.

**The span-highlighting approach**, which is the UI's whole job: the backend returns character offsets into the exact string it screened, plus a `revealedText` variant where invisible characters are substituted with visible sentinels (`U+200B` → `␣ZWSP␣`, Cyrillic `а` → `а⟨CYR⟩`). The frontend renders the original by default with a toggle to the revealed form. This is what makes "a human buyer sees nothing, the model sees everything" land as a demo beat rather than a claim.

---

## 8. Decisions summary

| # | Decision | Key reason |
|---|---|---|
| 1 | `Horizon-Labs/prompt-injection-guard-small` | Only candidate trained on documents-with-planted-injections; Apache-2.0; quantized ONNX shipped; portable tokenizer |
| 2 | ONNX Runtime Java in-process, DJL tokenizer only | Established internal pattern across 4 systems |
| 3 | Write Layers 1+2 ourselves | Java pattern libs catch only the attacks our fixtures evade by design |
| 4 | Sentence-level scoring (StackOne technique) | Serves detection, span highlighting and false-alarm rate at once |
| 5 | Tess4J for OCR | Only credible offline Java OCR; degrades per FR-037 |
| 6 | External vision-capable agent | FR-033 needs vision; finding generalises; demo insulated by FR-040 |
| 7 | Deterministic judging | Survives the "how do you know it complied?" challenge |
| 8 | JSONL persistence | Durable-on-completion and auditable by construction |
| 9 | React + Vite, no component library | Custom span rendering is the hard part; libraries don't help |

**No NEEDS CLARIFICATION markers remain.** Every Technical Context field in [plan.md](./plan.md) is resolved.
