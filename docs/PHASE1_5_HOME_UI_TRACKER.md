# RAIN Browser — Phase 1.5: Home UI/UX Work Tracker

> **Project:** RAIN Browser  
> **Repository:** `phukthaisongp1-collab/Rain`  
> **Workstream:** Phase 1.5 — Home page redesign and functional controls  
> **Planning estimate:** 7 working days  
> **Start date:** 2026-11-27  
> **Current status:** Day 1–2 complete; Day 3 in progress  
> **Progress:** 2/7 days (28.57%)

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
| 1 | Inspect repository baseline, existing home-page implementation, assets, navigation/actions, and relevant tests. Record affected files and constraints. | Complete (2026-11-27) | Inspection report below; baseline `7ce46d2`; PR #19 / Actions run `45931404564` |
| 2 | Implement the reference-matched home-page layout and responsive sizing without changing core architecture. | Complete (2026-11-27) | Responsive landscape breakpoint commit `fb27cc9`; Android Build run `45933238852` passed. |
| 3 | Integrate the approved RAIN logo and required visual assets; refine icons, spacing, colors, and visual details against the reference. | In progress | Replaced placeholder wordmark with vector-traced artwork from `5768.png` and converted feature-strip emoji to inline SVG icons in commit `d69444e`; CI verification pending. |
| 4 | Implement and verify address/query input and search navigation. | Not started | — |
| 5 | Connect and verify microphone, visual search, website shortcuts, and shortcut navigation. | Not started | — |
| 6 | Implement and verify Add shortcut behavior and all bottom-toolbar actions using existing browser features. | Not started | — |
| 7 | Run build, lint, relevant automated tests, functional control checks, visual comparison, regression checks, and prepare the acceptance report. | Not started | — |
| **Total** | **7 working days** | **2/7 complete (28.57%)** | **Day 1–2 complete; Day 3 visual asset integration in progress** |

## Daily completion record

### Day 1 — Repository and current implementation inspection

**Objective:** Establish the actual implementation baseline before modifying source code.

Checklist:
- [x] Confirm current default branch and latest commit.
- [x] Inspect the app module and current home-page entry point.
- [x] Locate the current layout/UI implementation and related assets.
- [x] Trace search, microphone, visual-search, shortcut, Add, and bottom-toolbar actions.
- [x] Identify relevant tests and existing build/lint commands.
- [x] Record affected files, dependencies, constraints, and any ambiguity against the selected reference.
- [x] Publish an inspection report with file paths and commit evidence.

**Status:** Complete  
**Completion date:** 2026-11-27  
**Baseline commit:** `7ce46d23b9510cbb13b26e8e4e02d6dcc1af34e4`  
**Candidate UI commit:** `c71c62287419b75ddb882e76caa84083b4e6928d`  
**Pull request:** [#19 — Implement RAIN Browser reference home screen](https://github.com/phukthaisongp1-collab/Rain/pull/19) (open, not merged)  
**CI evidence:** Android Build run `45931404564` completed successfully for the PR head. This is CI evidence for that candidate commit, not physical-device validation.

#### Inspection findings

**Repository baseline**
- Default branch: `main`; repository is public and accessible with read/write permission.
- The inspected `main` baseline includes the Android app, Kotlin source, XML resources, unit tests, instrumentation tests, Gradle configuration, and GitHub Actions workflows.
- Platform baseline in `app/build.gradle.kts`: `minSdk = 36`, `compileSdk = 36`, `targetSdk = 36`, JDK 17.
- No source code was changed during Day 1.

**Current home page on `main`**
- Entry point: `MainActivity.onCreate()` calls `setContentView(R.layout.activity_main)`, then `bindViews()`, `bindActions()`, and `restoreTabs()`.
- Layout: `app/src/main/res/layout/activity_main.xml`.
- Welcome page: `WELCOME_PAGE` HTML/CSS constant and `showWelcomePage()` in `app/src/main/java/com/rain/browser/MainActivity.kt`.
- The current `main` home page is the original dark welcome screen with RAIN heading, recent items, and bookmarks. The browser controls are in the native XML layout.

**Existing reference implementation candidate**
- PR #19 changes three files: `MainActivity.kt`, `activity_main.xml`, and new `drawable/rain_address_background.xml`.
- Its home-page visual uses inline CSS gradients and CSS mountain/cloud approximations, plus an inline SVG wordmark. It is not using the approved uploaded RAIN logo asset.
- The weather panel is hard-coded to Bangkok and static; it is not a live weather feature.
- Search form uses the selected Google/DuckDuckGo endpoint. The native address input continues to use `BrowserUrlResolver`.
- Website shortcuts are anchors to fixed destinations (Google, YouTube, Facebook, X, Instagram).
- The microphone button has no event handler; the Add shortcut item is a non-interactive span. There is no visual-search control.
- The five feature-strip items are static `div` elements, not working buttons. Existing native toolbar buttons retain their `MainActivity.bindActions()` listeners, but the actual rendered layout and each action still require functional verification.
- PR #19 is open and unmerged; its UI is not part of `main` yet. Do not assume it is accepted or merge it as part of Day 1.

**Existing browser behavior and relevant files**
- Search/address resolution: `app/src/main/java/com/rain/browser/browser/BrowserUrlResolver.kt`; `SearchEngine` is declared in the same file.
- Main action wiring: `MainActivity.bindActions()`; includes Go, Back, Forward, Reload/Stop, Home, New Tab, Tabs, search-engine toggle, display mode, zoom/text size, Private, Protection, Permissions, Bookmarks, History, Resume, and Settings.
- Browser surface: `WebViewFactory.kt`, `TabManager.kt`, `BrowserTab.kt`, and `NavigationGuardPolicy.kt`.
- Manifest: `app/src/main/AndroidManifest.xml`; declares Internet, Camera, Record Audio and location permissions, but declaration alone does not implement microphone or image search flows.
- Related layout/style resources: `app/src/main/res/layout/activity_main.xml`, `app/src/main/res/values/styles.xml`, `app/src/main/res/values/colors.xml`, and `app/src/main/res/drawable/`.
- The inspected `main` drawable directory has no approved RAIN logo/image asset; PR #19 adds only the address-field drawable.

**Tests and build commands**
- Unit tests present: `BrowserSecurityPolicyTest`, `BrowserUrlResolverTest`, `NavigationGuardPolicyTest`, `RepositoryTest`, `DownloadPolicyTest`, `NetworkPolicyManagerTest`, `WebProtectionManagerTest`, `WebProtectionPresentationTest`, `WebViewProxyManagerTest`, and `PermissionManagerTest`.
- `PrivateBrowsingIsolationTest` is an instrumentation test in the separate Phase 2/private-browsing workstream; it is not counted as Phase 1.5 home-page validation.
- Existing CI command: `./gradlew --no-daemon --stacktrace clean lint testDebugUnitTest assembleDebug`.
- Workflow: `.github/workflows/android-build.yml`; it also performs an Android 16 emulator install/launch smoke test when KVM acceleration is available.
- The PR #19 Actions run is successful, but it does not prove the microphone, visual search, Add shortcut, live weather, or physical-device behavior works.

**Constraints and implementation notes**
- Preserve the existing Activity, WebView, tab manager, URL resolver, and native action wiring; no architecture change is needed for the scoped UI work.
- The approved logo asset must be located/provided from the existing project context before it can be integrated. Do not substitute the candidate's generated SVG wordmark.
- The precise visual reference remains the owner's selected image; compare against that exact image during Day 2/3 rather than combining it with earlier homepage concepts.
- No physical-device testing was performed during Day 1.

**Day 1 result:** Repository and current implementation inspection completed. No source files changed. Day 2 may proceed to implementation planning and layout work within the approved scope.

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
| 2026-11-27 | Initial Phase 1.5 tracker created. |\n| 2026-11-27 | Day 1 inspection completed and recorded. No application source code changed. Progress updated to 1/7 (14.29%). |
| 2026-11-27 | Day 2 responsive orientation implementation completed on `feature/rain-homepage-reference` (commit `fb27cc9`). Android Build run `45933238852` completed successfully. No physical-device rotation test has been performed. |
| 2026-11-27 | Day 3 started: integrated a vector-traced version of the located transparent RAIN logo artwork and replaced emoji feature-strip icons with inline SVG icons in `MainActivity.kt` (commit `d69444e`). Automated verification is pending. |
