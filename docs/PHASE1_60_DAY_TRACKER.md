# RAIN Browser — Phase 1: 60-Day Work Tracker

> **Project:** RAIN Browser  
> **Plan:** v3.1  
> **Repository:** `phukthaisongp1-collab/Rain`  
> **Schedule:** 60 calendar days from the formally approved start date  
> **Current status:** In progress  
> **Progress:** 7/60 days (11.67%)

## Tracking rules

1. A day is marked `[x]` only after its stated work is actually completed and verified against the repository, build/test output, or other concrete evidence.
2. Never mark work complete based only on an intention, an attempted change, or an unverified claim.
3. When a day is complete, update this file with its checkbox, completion date, concise result, and evidence (commit SHA, file path, GitHub Actions run, test result, or APK verification result as applicable).
4. Before doing work, check this tracker and the recorded evidence. If a task is already complete, do not repeat it; inspect its evidence and move to the next incomplete task.
5. If a task fails verification, leave it unchecked and record the blocker. Fix the blocker before marking it complete.
6. Work is sequential. Do not start the next round until the current round has been reviewed and accepted.
7. The 60 days are a planning baseline, not a promise. Waiting for user input, credentials, Keystore setup, platform limitations, or unresolved defects may move the schedule. Do not reduce security or acceptance requirements to meet the target.
8. Do not add Phase 2 features to Phase 1 without approval.

## Progress summary

| Workstream | Planned days | Completed | Status |
|---|---:|---:|---|
| Round A — Android foundation and build | 10 | 7 | In progress |
| Round B — Browser features | 18 | 0 | Not started |
| Round C — Privacy and network | 18 | 0 | Not started |
| Round D — Data and settings | 10 | 0 | Not started |
| Final verification and release | 4 | 0 | Not started |
| **Total** | **60** | **7** | **11.67%** |

> Progress calculation: completed days ÷ 60 × 100. Update the summary whenever a daily checkbox changes. A round is not accepted until its review checkpoint is explicitly recorded.

---

## Round A — Android foundation and build (Days 1–10)

**Round acceptance:** Android project builds; CI checks run; release signing is configured using the approved Android Keystore; signed APK verification succeeds. No unsigned release fallback.

- [x] **Day 1:** Inspect the RAIN repository, default branch, existing plan, and current GitHub project structure. Record the verified baseline and avoid overwriting existing work. **Completed: 2026-11-25.** Verified: default branch `main`; repository is public and accessible with read/write permission; root contains `README.md` and `docs/`; `docs/` contains only `RAIN_BROWSER_PLAN_V3.md` and `PHASE1_60_DAY_TRACKER.md`; no Android source/project scaffold or `.github/workflows` files are present in the inspected root/docs listings. Existing plan confirmed as Android-only v3.1, Kotlin + Android SDK + Gradle + WebView, applicationId `com.rain.browser`, initial versionName `0.1.0`. Baseline commit: `e40dc87e6f4e9ea2d24bfe40d2e22fd5c6f6d4a6`; plan blob: `e44f963ad9eccf1c036df133ffa4a679dd552127`. No application code changed on Day 1.
- [x] **Day 2:** Prepare the Android project structure in the existing repository, following the approved Android-only architecture. **Completed: 2026-11-25.** Created the approved directory skeleton with `.gitkeep` placeholders for `.github/workflows`, `app/src/main/java/com/rain/browser` and its `browser`, `privacy`, `network`, `data`, `downloads`, and `settings` packages; `app/src/main/res/layout`, `drawable`, `values`, and `values-th`; `app/src/test`; and `gradle/wrapper`. No Kotlin source, Gradle configuration, Android manifest, or workflow implementation was added because those belong to later scheduled tasks. Evidence commit: `a6fea91e7cdcd492ca6750a09ce2739d27198262`. 
- [x] **Day 3:** Configure Kotlin and Android SDK settings. **Completed: 2026-11-25.** Configured Kotlin compilation through AGP 9 built-in Kotlin support, compileSdk/targetSdk 36, and minSdk 29. Evidence: `app/build.gradle.kts`, `gradle/libs.versions.toml`, commit `8e4fde1c224cc2f1e03ee075b1560cf82d7b016a`.
- [x] **Day 4:** Configure Gradle and Gradle Wrapper, pin compatible versions, and verify the build environment. **Completed: 2026-11-25.** Pinned Gradle 9.6.0 and AGP 9.5.2; added the official Gradle 9.6.0 wrapper scripts and JAR. Gradle Wrapper validation passed in Actions run `45565157678`; the later build step exposed a separate Kotlin plugin configuration issue that was corrected. Evidence: `gradle/wrapper/`, `gradlew`, `gradlew.bat`, commit `15fe31c3e457b8f3abd64d233e16454587ef9017`.
- [x] **Day 5:** Set the application identity: app name RAIN, applicationId `com.rain.browser`, initial versionName `0.1.0`, and initial versionCode policy. **Completed: 2026-11-25.** Set app label/name RAIN, applicationId `com.rain.browser`, versionName `0.1.0`, and initial versionCode `1`. Evidence: `app/build.gradle.kts`, `app/src/main/res/values/strings.xml`, commit `15fe31c3e457b8f3abd64d233e16454587ef9017`.
- [x] **Day 6:** Create the starting Activity and initial application screen using the approved theme. **Completed: 2026-11-25.** Added `MainActivity.kt`, `activity_main.xml`, and the approved RAIN theme colors. Evidence: `app/src/main/java/com/rain/browser/MainActivity.kt`, `app/src/main/res/layout/activity_main.xml`, `app/src/main/res/values/`, commit `15fe31c3e457b8f3abd64d233e16454587ef9017`.
- [x] **Day 7:** Add the basic Android WebView and implement the agreed security baseline. **Completed: 2026-11-25.** Added Android WebView with JavaScript/DOM storage/file/content access disabled, mixed content blocked, Safe Browsing enabled, HTTP(S)-only navigation policy, and SSL certificate errors cancelled. Evidence: `MainActivity.kt`, `NetworkPolicyManager.kt`, `AndroidManifest.xml`, commit `15fe31c3e457b8f3abd64d233e16454587ef9017`.
- [ ] **Day 8:** Configure GitHub Actions for Android build, lint, tests, and build artifacts.
- [ ] **Day 9:** Configure release signing with Android Keystore and GitHub Actions Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Never commit or expose the Keystore or passwords.
- [ ] **Day 10:** Build the APK, verify its signature with `apksigner`, review CI results, record evidence, and obtain Round A acceptance.

**Round A review record**
- Review status: Not reviewed
- Review date:
- Evidence / commit / Actions run:
- Blockers or approved deviations:

---

## Round B — Browser features (Days 11–28)

**Round acceptance:** Core browsing, tabs, navigation controls, search, zoom, and per-site Mobile/Desktop Mode behavior are tested.

- [ ] **Day 11:** Design the Browser screen structure within the approved product scope.
- [ ] **Day 12:** Implement the address bar.
- [ ] **Day 13:** Implement URL and scheme validation.
- [ ] **Day 14:** Implement DuckDuckGo as the default search engine.
- [ ] **Day 15:** Add Google as a selectable search engine.
- [ ] **Day 16:** Implement Back and Forward navigation.
- [ ] **Day 17:** Implement Reload and Stop controls.
- [ ] **Day 18:** Implement Home and New Tab controls.
- [ ] **Day 19:** Implement browser tab creation and management.
- [ ] **Day 20:** Implement switching between tabs.
- [ ] **Day 21:** Handle WebView lifecycle and tab state safely.
- [ ] **Day 22:** Implement website loading and page display behavior.
- [ ] **Day 23:** Implement page zoom controls.
- [ ] **Day 24:** Implement text scaling controls.
- [ ] **Day 25:** Implement Desktop Mode using the supported WebView settings and user-agent behavior.
- [ ] **Day 26:** Implement Mobile Mode and switching between Mobile/Desktop modes.
- [ ] **Day 27:** Persist mode separately per website and reload the page when the mode changes; show the active mode clearly.
- [ ] **Day 28:** Test the Browser feature set, record evidence, fix verified defects, and obtain Round B acceptance.

**Round B review record**
- Review status: Not reviewed
- Review date:
- Evidence / commit / Actions run:
- Blockers or approved deviations:

---

## Round C — Privacy and network (Days 29–46)

**Round acceptance:** Privacy and network behavior is verified on Android WebView. Unsupported behavior must be documented and submitted for approval; never claim unsupported guarantees.

- [ ] **Day 29:** Inspect Android WebView storage behavior and document constraints relevant to privacy and session isolation.
- [ ] **Day 30:** Define the approved Private Browsing implementation approach based on verified platform capabilities.
- [ ] **Day 31:** Implement Private Browsing data/session separation to the extent supported by the approved design.
- [ ] **Day 32:** Handle Private Browsing cookies and verify their lifecycle.
- [ ] **Day 33:** Handle Private Browsing cache and Web Storage.
- [ ] **Day 34:** Inspect and handle Service Worker and other persistent website data relevant to private sessions.
- [ ] **Day 35:** Test closing Private Browsing and cleanup of its data; do not claim RAM-only behavior unless proven.
- [ ] **Day 36:** Verify HTTPS and SSL error handling; never bypass certificate errors.
- [ ] **Day 37:** Configure and verify Mixed Content handling and Safe Browsing.
- [ ] **Day 38:** Verify file access and content access are disabled unless a documented requirement justifies otherwise.
- [ ] **Day 39:** Validate external intents and URL schemes; restrict unsafe launches.
- [ ] **Day 40:** Research and test DoH feasibility on the supported Android/WebView stack; record findings.
- [ ] **Day 41:** Implement DoH only if a supported, secure approach is verified and remains within the approved specification; otherwise document the limitation and request a decision.
- [ ] **Day 42:** Research and test Proxy feasibility and limitations on the supported Android/WebView stack.
- [ ] **Day 43:** Implement Proxy only to the extent supported by a verified, approved approach; otherwise document the limitation and request a decision.
- [ ] **Day 44:** Verify the planned network whitelist candidates: `*.scb.co.th`, `*.kbank.com`, `*.go.th`, `*.gmail.com`, and `*.outlook.com`. Record actual behavior and limitations.
- [ ] **Day 45:** Run combined privacy and network tests; record reproducible results.
- [ ] **Day 46:** Resolve verified defects, document remaining platform limitations, record evidence, and obtain Round C acceptance.

**Round C review record**
- Review status: Not reviewed
- Review date:
- Evidence / commit / Actions run:
- Blockers or approved deviations:

---

## Round D — Data and settings (Days 47–56)

**Round acceptance:** Data operations, History, Resume, New Tab, Settings, and Thai/English localization work and are tested against the agreed limits.

- [ ] **Day 47:** Implement the agreed Data Layer structure and data access boundaries.
- [ ] **Day 48:** Implement the required data operations: get, set, query, and delete.
- [ ] **Day 49:** Implement browsing History persistence and retrieval.
- [ ] **Day 50:** Enforce the History maximum of 5,000 entries and test cleanup behavior.
- [ ] **Day 51:** Implement Resume persistence with a maximum of 10 entries.
- [ ] **Day 52:** Implement cleanup of Resume entries older than 30 days and ensure Private Browsing sessions are excluded.
- [ ] **Day 53:** Implement the New Tab page.
- [ ] **Day 54:** Implement the Settings page and the settings defined in the approved plan.
- [ ] **Day 55:** Implement Thai and English localization.
- [ ] **Day 56:** Test the data layer and all Round D features, record evidence, and obtain Round D acceptance.

**Round D review record**
- Review status: Not reviewed
- Review date:
- Evidence / commit / Actions run:
- Blockers or approved deviations:

---

## Final verification and release (Days 57–60)

**Release acceptance:** Production APK is built and signed with the same protected Keystore, signature verification passes, required tests pass, and delivery evidence is recorded.

- [ ] **Day 57:** Run end-to-end tests for all Phase 1 Browser features on the target Android device/environment.
- [ ] **Day 58:** Perform the final security review: secrets exposure, private data handling, WebView configuration, permissions, URL handling, and known limitations.
- [ ] **Day 59:** Build the Production Release APK, verify the APK signature with `apksigner`, and confirm the expected applicationId and version metadata.
- [ ] **Day 60:** Review the complete acceptance checklist, record test/build/release evidence, document known issues, and obtain final Phase 1 acceptance.

**Final release record**
- Release status: Not released
- Release date:
- Version name:
- Version code:
- APK artifact / GitHub Actions run:
- Signature verification result:
- Final acceptance:

---

## Daily completion log

Add one entry for each completed day. Keep the evidence precise enough that a future review can confirm the work without repeating it.

| Day | Completion date | Result / evidence | Commit SHA or Actions run |
|---:|---|---|---|
| 1 | 2026-11-25 | Repository baseline inspected; Android scaffold and workflow not yet present. Existing Android v3.1 plan and tracker confirmed. | `e40dc87e6f4e9ea2d24bfe40d2e22fd5c6f6d4a6` (baseline) |
| 2 | 2026-11-25 | Created approved Android directory skeleton with `.gitkeep` placeholders; no source/config implementation added. | `a6fea91e7cdcd492ca6750a09ce2739d27198262` |
| 3 | 2026-11-25 | Configured Kotlin through AGP built-in Kotlin, Android API 36, and minimum API 29. | `8e4fde1c224cc2f1e03ee075b1560cf82d7b016a` |
| 4 | 2026-11-25 | Pinned AGP 9.5.2 / Gradle 9.6.0; official Wrapper validation passed. Full build validation remains part of Day 8 CI. | `15fe31c3e457b8f3abd64d233e16454587ef9017`; Actions run `45565157678` |
| 5 | 2026-11-25 | Set RAIN identity, applicationId, versionName 0.1.0, and versionCode 1. | `15fe31c3e457b8f3abd64d233e16454587ef9017` |
| 6 | 2026-11-25 | Added initial Activity and RAIN-themed screen. | `15fe31c3e457b8f3abd64d233e16454587ef9017` |
| 7 | 2026-11-25 | Added WebView and agreed initial security baseline; final compilation is awaiting the corrected CI run. | `15fe31c3e457b8f3abd64d233e16454587ef9017` |

## Blockers and approved changes

Record blockers, platform limitations, and user-approved changes here. Do not silently change the scope or mark blocked work as complete.

| Date | Related day | Blocker / limitation | Decision / approval | Resolution evidence |
|---|---:|---|---|---|
| 2026-11-25 | 8 | CI attempt 1 failed because setup-android v3 requested the removed SDK package `tools`; corrected to v4. CI attempt 2 passed SDK setup and wrapper validation but failed because the separate Kotlin Android plugin conflicts with AGP 9 built-in Kotlin; migrated to AGP built-in Kotlin. Latest run `45565359747` is pending. | Use AGP built-in Kotlin and setup-android v4; do not mark Day 8 complete until lint, unit tests, and APK build pass. | `8e4fde1c224cc2f1e03ee075b1560cf82d7b016a`; [Actions run](https://github.com/phukthaisongp1-collab/Rain/actions/runs/45565359747) |

## Progress update protocol

Whenever work is completed:
1. Verify the actual code, configuration, test, or build result.
2. Update the matching day from `[ ]` to `[x]`.
3. Add its completion date and evidence to the Daily completion log.
4. Recalculate the completed-day count and percentage in the Progress summary.
5. Update the round status only when its acceptance checkpoint is passed.
6. Commit the tracker update to the repository and report the commit and current progress to the project owner.

**Important:** A checkbox means verified completion, not merely that work was attempted. Never check off a day without evidence.
