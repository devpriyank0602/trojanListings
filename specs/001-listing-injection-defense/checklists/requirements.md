# Specification Quality Checklist: Trojan Listings — Listing-Borne Injection Measurement & Defence

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-24
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Validation Notes

### `/speckit-specify` — iteration 1 (2 failures, fixed)

- *Success criteria are measurable* — SC-003 read "a compliance rate high enough to be demonstrated live and believed on sight" and SC-009 read "quickly enough to be shown live without an awkward pause". Neither was verifiable. Rewritten with explicit thresholds (50% compliance; under 3 seconds). SC-008 was also tightened from "understand the verdict without explanation" to an observable action the tester can check.

### `/speckit-specify` — iteration 2

All 16 items passing. Zero `[NEEDS CLARIFICATION]` markers raised; one scope assumption (in-image attacks) deliberately recorded in Assumptions rather than blocking, flagged for clarification.

### `/speckit-clarify` — session 2026-09-24 (5 questions, all answered)

Re-validated after integrating all five clarifications. **16/16 → 16/16 items passing**; no newly-passing items and no regressions, since the spec was already at full pass. One genuine contradiction was introduced by the clarifications and fixed before re-validation:

- *Scope is clearly bounded* — the Effort Budget assumption still claimed ~10 hours of work, but Q3 (full in-image treatment) and Q4 (measurement inside the application) materially expanded scope beyond it. Left as-is, the spec would have asserted a budget its own requirements no longer fit. Rewritten to state plainly that scope now exceeds the original estimate, that this is a deliberate accepted choice, and that the P1→P4 priority ordering is therefore load-bearing rather than advisory.

Requirement count grew from 26 to 43 functional requirements and from 12 to 16 success criteria. FR-024 appears twice by grep, but the second occurrence is a cross-reference from the Dependencies section, not a duplicate definition.

**Borderline call on "No implementation details":** the Assumptions section now records architectural constraints the user mandated — a locally hosted web application, a single backend service, in-process model inference following internal precedent. These are genuine project constraints and belong in a spec, so the item is marked passing. They are described by shape only; no language, framework, library or product name appears anywhere in `spec.md`. All concrete technology selection is deferred to `/speckit-plan`, where the supporting research lives.

### Decisions settled during clarification

1. **Internal classifier excluded entirely** (not demoted to P4) — connectivity to the internal inference endpoint cannot be relied on. It remains a narrative citation, never a runtime call. Drove FR-024, FR-025, SC-013 and a new Out of Scope entry.
2. **In-process inference, chosen against internal engineering precedent** rather than preference — the user asked for the decision to be made on organisational standards, and Glean research found a consistent internal pattern for exactly this shape. Drove FR-029, FR-030.
3. **In-image attacks fully built and measured** (Option D) — the assumption flagged as open during `/speckit-specify` is now resolved in the widest direction. Drove FR-031 to FR-038 and SC-014/SC-015. This is the heaviest item in the build and is explicitly sequenced behind P1.
4. **Measurement lives in the backend and persists every trial** — the interface serves recorded results, so no user-facing capability depends on a live agent call. Drove FR-039 to FR-041, and tightened FR-021 and SC-012.
5. **Deterministic compliance judging, no LLM-as-judge** — a judge model grading a response produced under attack is exposed to the same payload. Drove FR-003, FR-008, FR-042, FR-043, SC-016, and an honest limitation note that the reported rate is a conservative floor.

## Notes

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
