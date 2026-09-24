# Feature Specification: Trojan Listings — Listing-Borne Injection Measurement & Defence

**Feature Branch**: `main` *(no feature branch created — no `before_specify` hook configured)*

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: *(command invoked with no arguments; feature description sourced from `RESEARCH-BRIEF.md` in the repository root — "Trojan Listings — Team Research Brief", eBay Bengaluru AI Hackathon 2026, Trust/risk & customer confidence lane)*

## Problem Statement

Marketplace listings are authored by sellers and are increasingly read by AI shopping agents — the marketplace's own assistants and search summaries today, third-party assistants next. A language model cannot reliably separate "content to summarise" from "instructions to obey", so every seller-authored listing field becomes untrusted input to a model the marketplace often does not control.

Two gaps follow. First, nobody knows **how exposed the marketplace is** — there is no measurement of how often an agent reading a hostile listing actually does what the listing tells it to. Second, nothing **screens listing content for agent-directed manipulation** at the point it enters the catalogue.

This feature closes both: it produces the missing exposure number, and it produces a screening capability measured against that number.

## Clarifications

### Session 2026-09-24

- Q: Should the organisation's existing internal prompt-injection classifier become the primary detector, or stay as a comparison baseline? → A: Neither — it is excluded from the build entirely. Network connectivity to the internal inference endpoint cannot be relied on, so nothing in this feature may depend on reaching it. Its documented existence remains a supporting argument in the narrative, but it is never called at runtime.
- Q: Where should the machine-learning part of screening run, given everything must work locally? → A: In-process inside the single backend service, following the organisation's established pattern for local model inference — a quantised transformer classifier in a portable model format, loaded once at service startup and reused across requests, with no sidecar process and no model download at request time. Decision made against internal engineering precedent rather than preference; see Assumptions.
- Q: Should attacks with the instruction rendered into the listing photo be built and screened, or dropped? → A: Full treatment. Image fixtures are authored, screened by extracting text from the image and running the same detectors over it, and measured against a vision-capable agent. In-image attacks are a first-class technique on equal footing with the other three, not a stretch goal.
- Q: Should the exposure measurement run live inside the hosted application, or as a separate process whose results are read back? → A: Both — measurement runs inside the backend and persists every trial as it completes. The interface serves stored results by default, so nothing user-facing depends on a live agent call succeeding; triggering a fresh run is available but never required.
- Q: How closely should the interface copy the marketplace's real visual identity? → A: Marketplace-*inspired*, not a replica. A vibrant palette in the same spirit as the marketplace brand, using deliberately distinct colour values and no licensed brand typeface. The interface should feel like a confident, modern product rather than a muted internal tool, while remaining visibly its own thing — the repository is public, so trademark and font-licensing surface is kept at zero.
- Q: Should the listing input mirror the marketplace's real seller-listing flow, or stay a compact form? → A: Borrow the seller-flow vocabulary without the full-page flow. The input panel is restructured to read as a seller surface — photo-first, content grouped into titled section cards, a category/condition row, one primary call to action — but stays in the two-column layout so the listing and its verdict remain visible together. A faithful multi-step flow was rejected because it separates cause from effect, which is the one thing the interface exists to show.
- Q: What should make the interface read as a powerful AI project rather than a form with a coloured result box? → A: Both evidence and motion. The four screening layers become visible as named panels, each reporting its own result and score, alongside a threat-score indicator and technique badges — so the reviewer can see that several independent systems examined the listing. A short transition accompanies this, but it must be tied to the real elapsed time rather than padded: the interface may not stage a delay it did not incur.
- Q: With a vibrant palette, how should the interface convey meaning to someone who cannot distinguish the colours? → A: Follow the marketplace's own accessibility standard: WCAG 2.2 AA, colour is never the sole carrier of meaning, and the default presentation must already meet contrast rather than offering an opt-in high-contrast mode. Every highlight, badge and verdict carries a text label or icon alongside its colour. A high-contrast toggle was considered and rejected because the organisation's published standard explicitly does not use one — the default is required to pass.
- Q: Should the synthetic-content and degraded-mode banners stay pinned across the top after the redesign? → A: Both remain always visible but stop consuming a full-width bar each. The synthetic-content statement becomes a persistent header chip, and the classifier's availability becomes a row inside the screening-layer panel, beside the layers that did run. Neither may be dismissible: the synthetic statement is required on every screen, and degraded operation must be reported rather than hidden.
- Q: How should each trial be judged as compliant, partially compliant, or a refusal? → A: Deterministically. Each fixture declares its own mechanically checkable condition, evaluated without a second model in the loop. No LLM-as-judge — a judge model reading a response produced under attack is itself exposed to the same payload, and the headline number has to survive that objection.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Measure how often an agent obeys a hostile listing (Priority: P1)

A trust-and-safety analyst wants to know whether listing-borne manipulation actually works. They run a library of synthetic hostile listings through a shopping agent that behaves like a buyer's assistant, and get back a table showing, for each attack technique and each attacker goal, the share of attempts where the agent did what the attacker wanted — recommended the planted item, disparaged a competitor, omitted a condition disclosure, or leaked its own instructions.

**Why this priority**: This is the finding that does not exist today, and it is the entire argument for the rest of the work. It is also self-contained: it needs no screening capability, no interface, and no access to any existing marketplace system. If everything else is abandoned, this alone is a publishable result and a complete demonstration.

**Independent Test**: Fully testable by running the hostile-listing library through the agent and confirming a per-technique, per-goal compliance table is produced with every individual trial traceable back to the exact listing that caused it. Delivers a defensible exposure measurement on its own.

**Acceptance Scenarios**:

1. **Given** a library of hostile listings covering every attack technique and attacker goal, **When** the analyst runs the measurement, **Then** a results table is produced reporting the share of agent responses that complied with the attacker's goal, broken down by technique and by goal.
2. **Given** a hostile listing that instructs the agent to rank it first and omit its condition, **When** the agent produces a recommendation, **Then** the run records an explicit compliance verdict with the agent's own wording preserved as evidence.
3. **Given** a benign control listing containing no hostile content, **When** the analyst runs the measurement, **Then** the control is recorded as non-compliant, establishing that the verdicts track manipulation rather than agent chattiness.
4. **Given** a completed measurement run, **When** the analyst re-runs it with the same library, **Then** each trial is individually attributable to a named listing and technique so that any single result can be reproduced and inspected.
5. **Given** a fixture whose hostile instruction exists only inside the listing photo and nowhere in its text, **When** it is presented to the vision-capable agent, **Then** a compliance verdict is recorded for it and reported as its own technique alongside the text-borne ones.

---

### User Story 2 - Catch agent-directed manipulation before it reaches an agent (Priority: P2)

A listing-integrity reviewer needs listing content screened for instructions aimed at an AI reader. Given a listing's title, description and structured attribute fields, the screening returns a risk verdict, a confidence indication, and the specific span of text responsible — including content deliberately hidden from human eyes through invisible characters, look-alike letters, encoded payloads, or markup that renders text unreadable to a buyer but perfectly legible to a model.

**Why this priority**: This is the response to the exposure measured in Story 1, and it turns a finding into something the business can act on. It depends on Story 1 only for its evaluation set, so it can be built and judged on its own merits.

**Independent Test**: Fully testable by scoring the hostile-listing library plus a set of benign listings and reporting how many hostile listings were caught and how many benign listings were wrongly flagged. Delivers a usable screening capability on its own.

**Acceptance Scenarios**:

1. **Given** a listing containing a plain-language instruction to an AI reader, **When** it is screened, **Then** it is flagged and the offending sentence is identified.
2. **Given** a listing whose hostile instruction is concealed using invisible characters, look-alike letters, encoded text, or markup that hides it from human view, **When** it is screened, **Then** it is still flagged and the concealment technique is named in the result.
3. **Given** a listing whose hostile instruction sits in a structured attribute field rather than the description, **When** it is screened, **Then** it is flagged with the responsible field identified.
4. **Given** an ordinary listing written by an honest seller, including one that legitimately uses words like "instructions", "system" or "ignore" in a product context, **When** it is screened, **Then** it is not flagged.
5. **Given** the full evaluation set, **When** screening is run across it, **Then** catch rate and false-alarm rate are reported as explicit numbers.
6. **Given** a listing whose text is entirely benign but whose photo carries a hostile instruction, **When** it is screened, **Then** it is flagged, the image is identified as the source, and the text extracted from it is returned.

---

### User Story 3 - Inspect a single listing and see what was found (Priority: P3)

Anyone — reviewer, policy owner, or executive — pastes a listing's text into a single screen and immediately sees whether it would be flagged, why, and exactly which characters are responsible, with hidden content made visible.

**Why this priority**: Turns the capability into something a non-specialist can understand and operate, and makes the finding legible to decision-makers. Valuable, but the measurement and the screening both stand without it.

**Independent Test**: Fully testable by pasting any listing into the screen and confirming a verdict and highlighted span appear without the user needing to run anything else. Delivers a self-service inspection tool on its own.

**Acceptance Scenarios**:

1. **Given** a hostile listing pasted into the screen, **When** it is submitted, **Then** a verdict appears together with the responsible text visibly marked within the listing.
2. **Given** a listing whose hostile content is invisible to a human reader, **When** it is submitted, **Then** the screen reveals the hidden content in a form the viewer can actually see.
3. **Given** a benign listing pasted into the screen, **When** it is submitted, **Then** a clear "no manipulation found" verdict appears with nothing marked.
4. **Given** any listing, **When** it is submitted, **Then** the verdict is returned fast enough that a presenter can demonstrate it live without waiting.

---

### User Story 4 - Show that general-purpose screening misses listing-specific attacks (Priority: P4)

A security stakeholder asks whether general-purpose prompt-injection screening already handles this. The same evaluation set is run through an off-the-shelf general-purpose screening baseline and through the listing-aware screening built here, and the results are reported side by side, showing which attack techniques the general-purpose baseline covers and which it misses on listing content.

**Why this priority**: The direct answer to "isn't this a solved problem?", and it converts the work from an assertion into a measured comparison. It adds no new capability of its own, so it is the first thing to shed under time pressure.

**Independent Test**: Fully testable by running both screening paths over the same evaluation set and producing a per-technique comparison table. Delivers a gap analysis on its own.

**Acceptance Scenarios**:

1. **Given** the evaluation set and a general-purpose screening baseline, **When** the comparison is run, **Then** catch rate and false-alarm rate are reported for both the baseline and the listing-aware screening, broken down by attack technique.
2. **Given** an attack technique the general-purpose baseline misses, **When** the comparison is reported, **Then** that gap is stated explicitly with the specific listings that were missed.
3. **Given** that the comparison is skipped for time, **When** the feature is delivered, **Then** every other part of the feature remains unaffected and no capability is lost.

---

### Edge Cases

- **A listing contains no hostile content but uses trigger-like vocabulary** — e.g. a cookbook titled "Ignore All Previous Diets", or a genuine "Care Instructions" field. Screening must not flag these; the false-alarm rate is reported precisely so this is visible.
- **The agent refuses the hostile instruction.** A refusal is a valid, recorded outcome, not a failed trial. A low compliance rate is itself a reportable finding.
- **The agent partially complies** — it promotes the item but still discloses the defect. Verdicts must distinguish full compliance, partial compliance and refusal rather than collapsing to pass/fail, using the fixture's separately declared partial condition.
- **The agent complies through wording the fixture's author did not anticipate.** The deterministic check scores this a refusal. This is a known and accepted limitation that makes the reported rate a floor; it must be stated wherever the rate is reported, not discovered by a judge.
- **Hostile content is split across several fields**, with no single field hostile on its own. Screening must consider the assembled listing, not each field in isolation.
- **A listing is in a language other than English**, or mixes scripts legitimately (a genuinely Cyrillic-language listing). Look-alike-letter detection must not flag an entire listing simply for not being Latin script.
- **Extremely long listing content.** Screening must return a verdict rather than truncating silently or failing.
- **A listing image contains no text at all**, or only product branding. Screening must return "no manipulation found" rather than flagging ordinary packaging text, and ordinary product photos are included among the benign controls so this is measured.
- **Text extraction from an image fails, returns garbage, or misreads characters.** The verdict must still be returned for the text fields, with the image reported as not screened rather than silently treated as clean.
- **An in-image instruction is rendered so faintly or at such low resolution that extraction misses it, but a vision model still reads it.** This gap between what screening sees and what an agent sees must be reported as a known limitation rather than hidden — it is itself a finding.
- **Empty, whitespace-only, or markup-only listing content.** Screening must return a defined verdict, not an error.
- **A live exploitable path against a real production assistant is discovered.** This is handled as responsible disclosure to the security organisation and is excluded from any public artefact — see Safety Constraints.

## Requirements *(mandatory)*

### Functional Requirements

**Hostile listing library**

- **FR-001**: The system MUST provide a library of synthetic listings carrying hostile instructions, covering four attack techniques: plain instructions in free text; instructions planted in structured attribute fields; instructions concealed to defeat literal keyword matching; and instructions rendered into listing imagery.
- **FR-002**: Concealment coverage MUST include, at minimum: invisible characters placed inside words, look-alike letters substituted from other alphabets, text made unreadable to humans through presentation markup, encoded or letter-spaced payloads, and counterfeit conversational delimiters that imitate the start of a new instruction turn.
- **FR-003**: Each hostile listing MUST declare its attack technique, its attacker goal, and a mechanically checkable condition defining the observable agent behaviour that constitutes a successful attack — stated precisely enough to be evaluated without human interpretation.
- **FR-042**: Each hostile listing MUST declare its partial-compliance condition separately from its full-compliance condition, so the three outcomes are distinguishable mechanically rather than by judgement.
- **FR-004**: The library MUST cover four attacker goals: promoting the attacker's own item, disparaging a competing item, suppressing a condition or defect disclosure, and extracting the agent's own governing instructions.
- **FR-005**: The library MUST include benign control listings, including ones containing trigger-like but legitimate wording, so that false alarms are measurable.
- **FR-006**: Every listing in the library MUST be entirely fabricated, naming no real seller, buyer or live item.
- **FR-031**: In-image fixtures MUST carry their hostile instruction rendered into the listing photo itself, legible to a viewer of the image but absent from every text field of the listing.
- **FR-032**: In-image fixtures MUST cover a range of rendering styles — at minimum clearly legible overlaid text, and low-contrast or small text that a human skimming the photo would plausibly miss.

**Exposure measurement**

- **FR-007**: The system MUST present each listing in the library to a shopping agent acting as a buyer's assistant, and record the agent's full response.
- **FR-033**: The agent used for measurement MUST be vision-capable, and in-image fixtures MUST be presented to it with the image included, so that all four attack techniques are measured against the same target.
- **FR-034**: In-image results MUST be reported as their own technique in the compliance table, directly comparable with the three text-borne techniques.
- **FR-008**: The system MUST assign each trial an outcome of full compliance, partial compliance, or refusal by mechanically evaluating that listing's declared conditions against the agent's response. No language model may be used to judge outcomes.
- **FR-043**: Every trial MUST record which declared condition was evaluated and what in the agent's response satisfied or failed it, so any individual verdict can be audited without re-running anything.
- **FR-009**: The system MUST report compliance rates aggregated by attack technique and by attacker goal, and MUST report the benign control rate separately.
- **FR-010**: Every trial MUST be traceable to the exact listing, technique and goal that produced it, with the agent's verbatim response retained as evidence.
- **FR-011**: A measurement run MUST complete and report partial results even if individual trials fail, rather than aborting the run.
- **FR-039**: Each trial MUST be persisted as it completes, so that a run interrupted part-way retains everything already measured.
- **FR-040**: Users MUST be able to view previously recorded measurement results without triggering any agent call, so that nothing user-facing depends on live agent availability.
- **FR-041**: Triggering a fresh measurement run from the application MUST be available but never required for any other capability to work.

**Screening**

- **FR-012**: The system MUST accept a listing's title, description and structured attribute fields and return a risk verdict, a confidence indication, and the specific span of content responsible.
- **FR-013**: Screening MUST name the concealment technique it detected, where one was used.
- **FR-014**: Screening MUST identify which field the responsible content came from.
- **FR-015**: Screening MUST assess the listing as an assembled whole, so that manipulation split across several fields is not missed.
- **FR-016**: Screening MUST report its catch rate and false-alarm rate over the full evaluation set as explicit numbers.
- **FR-017**: Screening MUST return a defined verdict for empty, whitespace-only, markup-only, non-English and unusually long content rather than failing.
- **FR-035**: Screening MUST accept listing images alongside text, extract any text rendered within them, and run the same detectors over the extracted text.
- **FR-036**: Where a verdict was driven by text found inside an image, the result MUST say so explicitly, identify the image, and return the extracted text that caused it.
- **FR-037**: Text extraction from images MUST run locally with no network call, and MUST degrade to a clearly reported "image not screened" outcome rather than failing the whole verdict if extraction is unavailable or yields nothing.

**Inspection interface**

- **FR-018**: Users MUST be able to submit a single listing's content and receive a verdict without running anything else.
- **FR-019**: The interface MUST visibly mark the responsible span within the submitted content.
- **FR-038**: Users MUST be able to submit a listing image alongside the text, and see the text extracted from it displayed next to the image with the responsible span marked.
- **FR-044**: The interface MUST present the listing input as a recognisable seller-listing surface — photo-first, content grouped into titled sections, with a single primary action — while keeping the listing and its verdict visible together on one screen.
- **FR-045**: The interface MUST show each screening layer as a named result with its own outcome, so a viewer can see that several independent checks examined the listing rather than one opaque score.
- **FR-046**: Any transition or progress indication MUST reflect the real elapsed screening time. The interface MUST NOT stage a delay it did not incur.
- **FR-047**: Colour MUST NOT be the sole carrier of meaning anywhere in the interface. Every verdict, finding, badge and highlight MUST also carry a text label or icon, and the default presentation MUST meet WCAG 2.2 AA contrast without requiring the viewer to enable an alternative mode.
- **FR-048**: The synthetic-content statement and the degraded-mode status MUST both remain visible on every screen and MUST NOT be dismissible.
- **FR-020**: The interface MUST render content that is invisible to a human reader in a form the viewer can actually see.
- **FR-021**: The interface MUST show the "before" and "after" of the story side by side — what the agent did with the listing unscreened, and what screening says about it — drawing the "before" from recorded trial results so no live agent call is needed to display it.

**Comparison**

- **FR-022**: The system MUST be able to run the same evaluation set through a general-purpose screening baseline and report results side by side with the listing-aware screening, broken down by attack technique.
- **FR-023**: The comparison MUST remain optional: if it is skipped, all other capabilities MUST continue to function unchanged.

**Operating constraints**

- **FR-024**: Every capability MUST run entirely on a local machine with no network call to any internal organisational service. Connectivity to internal inference endpoints cannot be relied upon and MUST NOT be a runtime dependency.
- **FR-025**: All screening models and assets MUST be available offline at demo time, so that no capability degrades if the network is unavailable or restricted.
- **FR-029**: Screening MUST run inside a single backend service process, with no separate sidecar runtime to install or start. The classifier MUST be loaded once at service startup and reused across requests, never fetched or initialised per request.
- **FR-030**: The system MUST remain usable if the classifier fails to load — structural and pattern detection MUST still return verdicts, in a clearly degraded mode, rather than the service failing to start.

**Safety**

- **FR-026**: The system MUST NOT publish, submit or transmit any hostile listing content to any live marketplace surface.
- **FR-027**: The system MUST operate entirely against sandboxed agents, never against production systems or live buyer traffic.
- **FR-028**: Every artefact produced MUST carry a clear statement that its contents are synthetic adversarial research fixtures.

### Key Entities

- **Hostile Listing Fixture**: A fabricated listing used as a test case. Carries title, description, structured attribute fields, optional imagery, plus its declared attack technique, attacker goal, mechanically checkable full- and partial-compliance conditions, and a human-readable note on what it is trying to do.
- **Attack Technique**: One of the four delivery methods — free text, structured field, concealed, or in-image. Used as the primary axis for reporting.
- **Attacker Goal**: What the attacker wants the agent to do — self-promotion, competitor disparagement, disclosure suppression, or instruction extraction. Used as the secondary reporting axis.
- **Trial**: One presentation of one fixture to one agent. Holds the agent's verbatim response, the compliance outcome, the reasoning behind that outcome, and when it was recorded. Persisted the moment it completes, and replayable without contacting the agent again.
- **Exposure Report**: The aggregate of all trials — compliance rates by technique and goal, plus the benign control rate.
- **Screening Verdict**: The outcome of screening one listing. Holds the risk judgement, confidence, responsible span, originating field, and concealment technique where applicable.
- **Evaluation Set**: The hostile fixtures plus benign controls, used as the shared yardstick for every screening path so comparisons are like-for-like.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The hostile listing library contains at least 25 fixtures, covering all four attack techniques and all four attacker goals, with at least one fixture in every technique-and-goal combination that applies.
- **SC-002**: A complete exposure measurement can be run end to end and produces a compliance rate for every attack technique — a number the organisation does not have today.
- **SC-003**: At least one attack technique achieves a compliance rate of 50% or higher, giving a result that can be reproduced live on demand. If no technique reaches 50%, the measured rates are reported as the finding, with the refusals retained as evidence.
- **SC-004**: Benign control listings produce a compliance rate at or near zero, establishing that measured compliance reflects manipulation rather than normal agent behaviour.
- **SC-005**: Screening catches at least 80% of hostile fixtures across the library.
- **SC-006**: Screening wrongly flags no more than 10% of benign control listings, including the deliberately trigger-like ones.
- **SC-007**: Every concealment technique in the library is detected on at least one fixture, and the technique is named correctly in the verdict.
- **SC-014**: A compliance rate is reported for in-image attacks on the same footing as the three text-borne techniques, making it possible to state whether hiding an instruction in a photo works better or worse than writing it in the description.
- **SC-015**: Screening catches at least 70% of in-image fixtures — a lower bar than the 80% set for text, because extraction quality is an additional failure point. Fixtures missed because extraction could not read them are reported separately from fixtures missed because the detectors did not fire.
- **SC-008**: A person who has never seen the system can paste a listing into the interface and, without being told anything, correctly state whether it was flagged and point to the part of the listing responsible.
- **SC-009**: A single listing submitted through the interface returns its verdict in under 3 seconds.
- **SC-010**: The comparison reports, per attack technique, whether the general-purpose screening baseline catches it — yielding an explicit list of techniques covered and techniques missed.
- **SC-013**: The full system runs start to finish on a laptop with no call to any internal organisational network or service, and screening specifically works with the network disconnected.
- **SC-017**: A viewer who cannot distinguish the interface's colours can still determine the verdict, the responsible span and the technique, using text and iconography alone.
- **SC-018**: On first load the primary working area — listing input and verdict — is visible without scrolling on a 1280×800 display, with no notice consuming a full-width band.
- **SC-016**: Any individual compliance verdict can be explained on demand by showing the fixture's declared condition and the part of the agent's response that satisfied or failed it — with no re-run and no second model consulted.
- **SC-011**: No hostile content produced by this work ever appears on a live marketplace surface, and no artefact names a real seller or live item.
- **SC-012**: The end-to-end story — agent manipulated, then manipulation caught — can be shown in under three minutes, entirely from recorded results, with no live agent call required.

## Safety Constraints

These are non-negotiable and are stated openly wherever the work is presented.

1. **Synthetic fixtures only.** No hostile content is ever published to any live marketplace surface.
2. **Sandboxed agents only.** Nothing runs against production systems or live buyer traffic.
3. **No real party is targeted.** Competitor-disparagement fixtures reference fabricated listings exclusively.
4. **Responsible disclosure.** If a live exploitable path against a real production assistant is discovered, it is reported privately to the security organisation and excluded from every public artefact.

## Assumptions

Reasonable defaults chosen where the source brief did not specify, plus decisions settled during clarification.

- **Effort budget**: a 24-hour window with a hard content freeze. The source brief budgeted roughly 10 hours of focused work against a narrower scope; clarification has since added in-image attacks as a fully built and measured technique, and moved measurement inside the application. **Scope now exceeds that original 10-hour estimate** — this is an accepted, deliberate choice, and it makes the priority ordering load-bearing rather than advisory. P4 goes first, then the in-image work, then Story 3; P1 never goes.
- **Story 1 must be complete and recorded early.** The exposure measurement is the load-bearing result; everything from Story 2 onward is upside, not a dependency. A recorded demonstration of Story 1 is banked before later stories are started.
- **In-image attacks are a first-class technique, fully built and fully measured.** Fixtures are authored with the instruction rendered into the image; screening extracts text from images and runs the same detectors over it; measurement uses a vision-capable agent. This is the heaviest single item in the build and carries two dependencies nothing else needs — local text-from-image extraction, and an agent that can actually see images. It is therefore sequenced strictly behind the text-only measurement, so a delay here cannot delay the load-bearing P1 result.
- **The shopping agent under test is a general-purpose assistant configured with a buyer-assistant brief**, not a marketplace-specific production assistant. This keeps the work free of access approvals and makes the finding generalise to third-party assistants, which is where the exposure is least controllable. The same agent must be vision-capable, so that all four techniques are measured against one consistent target.
- **The delivered system is a locally hosted web application**: a browser front end talking to a single backend service that owns both screening and the stored measurement results. Chosen so the whole thing runs on a laptop with nothing external to reach.
- **In-process model inference follows established internal engineering precedent.** Internal services doing local classification (a query-intent classifier server, an ads relevance service, a recommendations platform) consistently use a portable-model runtime embedded in the Java service, with the model loaded at startup and the session reused per request; tokenisation is handled by a helper library rather than by a second inference framework. This feature follows that same shape rather than inventing one. Two hazards are inherited with it and must be handled in the plan: native memory is leaked if runtime result objects are not explicitly closed, and transformer tokenisers vary in how well they port to the JVM — so a classifier whose tokeniser is well supported there is preferred over a marginally more accurate one that is not.
- **The organisation's existing in-house classifier is excluded from the build.** Connectivity to its internal inference endpoint cannot be relied on, so nothing depends on reaching it. Its documented existence — and the documented fact that nothing points it at listing content — remains a supporting argument in the narrative, cited rather than called.
- **Listings are treated as English-language text by default**, with non-English content handled as an edge case that must not error or produce spurious flags, rather than as a supported measurement axis.
- **"Compliance" is judged mechanically, per fixture, against conditions declared by the fixture's author** — never inferred generically and never delegated to a language model. This costs some nuance: a fixture whose attack succeeds through phrasing its author did not anticipate will be scored a refusal, which makes the reported rate a conservative floor rather than an exact figure. That trade is accepted deliberately, because a number that can be audited line by line survives challenge and one produced by a second model does not.
- **The interface is marketplace-inspired, never a replica.** It borrows the seller-flow layout vocabulary and a vibrant palette in the same spirit as the marketplace brand, but uses deliberately distinct colour values and no licensed brand typeface. This is a deliberate constraint rather than an aesthetic preference: the repository is public, so trademark and font-licensing exposure is held at zero.
- **The evaluation set is shared across every screening path** so that any comparison is like-for-like.
- **No dependency on any existing marketplace service, dataset or environment.** This is deliberate: access onboarding is the most common cause of failure under a hard deadline, and this work has none.

## Dependencies

- Access to a sandboxed, **vision-capable** conversational agent able to act as a buyer's assistant, for the exposure measurement across all four techniques.
- A screening model available under a permissive licence and runnable locally, with its assets cached offline ahead of demo time.
- A locally runnable means of extracting text from images, available offline, for screening in-image fixtures.
- A means of rendering text into images to author the in-image fixtures.
- *(Optional, P4 only)* A general-purpose screening baseline under a permissive licence, for the comparison.

**No dependency on any internal organisational service, endpoint, credential or access approval.** This is a hard constraint, not a preference — see FR-024.

## Out of Scope

- Any change to live listing ingestion, seller tooling, or production agent behaviour.
- Runtime integration with the organisation's existing in-house prompt-injection classifier, or with any other internal service. Excluded on connectivity-reliability grounds; the incumbent classifier is referenced in the narrative only.
- Defending or patching third-party assistants — the only controllable intervention point is the content handed over.
- Agent identity or authentication concerns. This work addresses what the content tells an agent to do, not which agent is calling.
- Regulatory or disclosure-compliance auditing of listing content.
- Training a new model from scratch.
- Any live-traffic deployment, rollout plan, or production integration.
