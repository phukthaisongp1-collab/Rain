# RAIN Browser — Phase 1.5: Home UI/UX Work Tracker

> **Project:** RAIN Browser  
> **Repository:** `phukthaisongp1-collab/Rain`  
> **Workstream:** Phase 1.5 — Home page redesign and functional controls  
> **Planning estimate:** 7 working days  
> **Start date:** To be recorded when Day 1 work begins formally  
> **Current status:** Day 1 in progress  
> **Progress:** 0/7 days (0%)

## Purpose

Rework the RAIN Browser home page to match the visual reference selected by the owner, while ensuring that every visible control performs its intended action. This is a dedicated Phase 1.5 workstream. It is separate from Phase 1 and Phase 2 and must not change either phase's completion totals.

## Scope

- Match the selected reference's layout, spacing, proportions, colors, imagery, logo placement, search field, shortcut area, and bottom toolbar.
- Use the approved RAIN logo asset; do not replace or redesign it without approval.
- Make search accept both web addresses and search queries and navigate correctly.
- Connect microphone and visual-search controls to their supported Android/app flows.
- Make website shortcuts open their configured destinations.
- Make the Add shortcut control perform its intended add/manage action.
- Make each bottom toolbar control open its corresponding existing browser feature.
- Preserve existing browser behavior and project architecture.

## Tracking rules

1. Mark a day complete only after its work is actually completed and verified with repository evidence, build/test output, or other concrete evidence.
2. An intention, code change without verification, or untested UI is not completion evidence.
3. For each completed day, record the completion date, concise result, changed file paths, commit SHA, and relevant build/test evidence.
4. If a task is blocked or fails verification, leave it unchecked and record the blocker. Do not silently move the work or claim completion.
5. Do not change core architecture, important file/class names, or existing browser behavior unless the owner approves the change.
6. Do not add features outside this Phase 1.5 scope.
7. Keep Phase 1.5 progress separate from Phase 1's 60-day tracker and all Phase 2 work. No Phase 1 or Phase 2 completion totals may be changed by this tracker.
8. The seven days are an initial estimate, not a guarantee. Update the estimate only when repository inspection or verified implementation work shows a material reason.
9. Real-device testing must be reported separately with the device, Android version, APK/build identifier, steps, results, and evidence. Do not claim a device test without running it.

## Progress summary

| Day | Planned work | Status | Evidence |
|---|---|---|---|
| 1 | Inspect repository baseline, existing home-page implementation, assets, navigation/actions, and relevant tests. Record affected files and constraints. | In progress | Pending inspection report |
| 2 | Implement the reference-matched home-page layout and responsive sizing without changing core architecture. | Not started | — |
| 3 | Integrate the approved RAIN logo and required visual assets; refine icons, spacing, colors, and visual details against the reference. | Not started | — |
| 4 | Implement and verify address/query input and search navigation. | Not started | — |
| 5 | Connect and verify microphone, visual search, website shortcuts, and shortcut navigation. | Not started | — |
| 6 | Implement and verify Add shortcut behavior and all bottom-toolbar actions using existing browser features. | Not started | — |
| 7 | Run build, lint, relevant automated tests, functional control checks, visual comparison, regression checks, and prepare the acceptance report. | Not started | — |
| **Total** | **7 working days** | **0/7 complete (0%)** | **No day is accepted yet** |

## Daily completion record

### Day 1 — Repository and current implementation inspection

**Objective:** Establish the actual implementation baseline before modifying source code.

Checklist:
- [ ] Confirm current default branch and latest commit.
- [ ] Inspect the app module and current home-page entry point.
- [ ] Locate the current layout/UI implementation and related assets.
- [ ] Trace search, microphone, visual-search, shortcut, Add, and bottom-toolbar actions.
- [ ] Identify relevant tests and existing build/lint commands.
- [ ] Record affected files, dependencies, constraints, and any ambiguity against the selected reference.
- [ ] Publish an inspection report with file paths and commit evidence.

**Status:** In progress  
**Completion date:** —  
**Commit / evidence:** Pending

## Acceptance criteria

Phase 1.5 may be presented for owner acceptance only when all applicable criteria below have evidence:

- [ ] Home page visually matches the selected reference at the supported phone viewport(s).
- [ ] Approved RAIN logo is used without unauthorized alteration.
- [ ] Search correctly distinguishes and opens valid HTTP(S) URLs versus search queries.
- [ ] Microphone control launches a real supported voice-input flow or clearly reports an actual platform limitation.
- [ ] Visual-search control launches a real supported image-selection/search flow or clearly reports an actual platform limitation.
- [ ] Each configured website shortcut opens its intended destination.
- [ ] Add shortcut control performs the intended action and handles invalid/duplicate input safely.
- [ ] Every bottom-toolbar control opens the correct existing feature.
- [ ] Build and relevant automated checks pass, or failures are documented without being represented as passes.
- [ ] Existing browser functionality has no newly introduced regression in the tested scope.
- [ ] Real-device test results and remaining platform limitations are recorded separately.

## Change control and boundaries

- This document tracks planning and evidence only; adding it does not authorize source-code changes by itself.
- No Phase 1 or Phase 2 task is moved, reopened, or marked complete by Phase 1.5.
- No RAIN account, cloud sync, browser-engine replacement, or unrelated feature is included.
- Any unresolved mismatch between multiple previously discussed home-page concepts must be clarified against the owner's selected reference before implementation.

## Updates

| Date | Update |
|---|---|
| 2026-11-27 | Initial Phase 1.5 seven-working-day tracker created. Day 1 marked in progress; no completion claimed. |
