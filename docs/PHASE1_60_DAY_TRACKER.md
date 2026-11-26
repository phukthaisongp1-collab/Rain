# RAIN Browser — Phase 1: 60-Day Work Tracker

> **Project:** RAIN Browser  
> **Plan:** v3.2  
> **Repository:** `phukthaisongp1-collab/Rain`  
> **Schedule:** 60 calendar days from the formally approved start date  
> **Current status:** In progress  
> **Progress:** 31/60 days (51.67%)

## Approved platform baseline change (2026-11-25)

- The owner approved Android 16 (API 36) and newer only. `minSdk = 36`; Android 15 and older are unsupported.
- `compileSdk` and `targetSdk` remain 36. Phase 1 emulator CI must use API 36.
- The original Day 3 `minSdk = 29` configuration is superseded by this approved decision and was applied on Day 31.

## Tracking rules

1. A day is marked `[x]` only after its stated work is actually completed and verified against the repository, build/test output, or other concrete evidence.
2. Never mark work complete based only on an intention, an attempted change, or an unverified claim.
3. When a day is complete, update this file with its checkbox, completion date, concise result, and evidence (commit SHA, file path, GitHub Actions run, test result, or APK verification result as applicable).
4. Before doing work, check this tracker and the recorded evidence. If a task is already complete, do not repeat it; inspect its evidence and move to the next incomplete task.
5. If a task fails verification, leave it unchecked and record the blocker. Fix the blocker before marking it complete.
6. Work is sequential within each active round. Deferred Phase 2 work and reserved Phase 1 capacity do not block unrelated Phase 1 tasks; do not start the next round until the active round has been reviewed and accepted.
7. The 60 days are a planning baseline, not a promise. Waiting for user input, credentials, Keystore setup, platform limitations, or unresolved defects may move the schedule. Do not reduce security or acceptance requirements to meet the target.
8. Do not add Phase 2 features to Phase 1 without approval.
9. Private Browsing is Phase 2 work and is excluded from Phase 1 round acceptance; its incomplete status and M0–M4 gates must remain visible in the Phase 2 workstream below.
10. Phase 2 progress is tracked separately and never changes the 60-day Phase 1 denominator or completion percentage.

## Progress summary

| Workstream | Planned days | Completed | Status |
|---|---:|---:|---|
| Round A — Android foundation and build | 10 | 10 | Accepted |
| Round B — Browser features | 18 | 18 | Accepted (cloud verification) |
| Round C — Privacy controls and network (excluding Private Browsing) | 13 | 2 | In progress |
| Phase 1 reserve / reallocation capacity | 5 | 1 | Day 31 completed; Days 32–35 remain reserved and non-blocking |
| Round D — Data and settings | 10 | 0 | Not started |
| Final verification and release | 4 | 0 | Not started |
| **Total** | **60** | **31** | **51.67%** |

> Progress calculation: completed days ÷ 60 × 100. Update the summary whenever a daily checkbox changes. A round is not accepted until its review checkpoint is explicitly recorded.

---

## Phase 2 Workstream — Private Browsing (tracked separately from Phase 1)

**Plan reference:** `docs/RAIN_BROWSER_PLAN_V3.md`, Section 7.1 (v3.2)  
**Status:** Planned; not started; requires explicit authorization to begin M0.  
**Scope rule:** This workstream is independent of Phase 1. It does not block Phase 1, does not count toward the 60-day Phase 1 progress, and must not be used to mark any Phase 1 day complete.

### Fixed constraints
- Android 16 / API 36+, Android WebView only; retain process `:private` and data-directory suffix `rain_private`.
- Set the suffix before any WebView API in the Private Process.
- No RAIN account, no Cloud Sync, and no architecture/engine change without separate approval.
- Do not claim complete trace removal or cleanup beyond verified Provider capabilities.
- No Source Code, Manifest, Gradle, or Workflow changes are authorized merely by adding this plan to the tracker.

### Milestones and acceptance gates

| Milestone | Scope | Gate / evidence required | Status |
|---|---|---|---|
| M0 — Dedicated CI & Harness Proof | Dedicated Workflow, KVM/acceleration gate, ADB/Shell timeouts, same-job App/Test APK build, diagnostics and artifact upload | 3 consecutive stable runs; no unresolved signing/parser/quoting/timeout failure; KVM acceleration proven | Not started |
| M1 — IPC & Process Readiness | Messenger-based allow-listed Test Control Interface, Ping/Pong, Request ID, Timeout, Process Name/PID and readiness handshake | 3 consecutive IPC/readiness passes; no BadParcelableException, ClassNotFoundException, Looper error, deadlock or unresolved race | Blocked on M0 |
| M2 — WebView Isolation Proof | Cookie, localStorage, Session Storage, IndexedDB, HTTP Cache, Cache Storage and Service Worker according to Provider capability | Real WebView page tests prove both directions of Normal/Private isolation; evidence per data type | Blocked on M1 |
| M3 — Cleanup, Lifecycle & Soak | Close/Reopen, Process Death, Force-stop, Low Memory, Unsupported API, Exception, Timeout and repeated sessions | Cleanup Report accurately records SUCCESS/PARTIAL/UNSUPPORTED/TIMEOUT/FAILED; new Session clean within supported scope; Normal data preserved | Blocked on M2 |
| M4 — Security Review & Real Device Validation | Application metadata/security review and real-device testing on Android 16+ during Day 57–60 | Security review passed; device evidence recorded; limitations disclosed | Blocked on M3 |

### Stop conditions
- Fail Fast when KVM/Hardware Acceleration is unavailable; no TCG or `-accel off` fallback.
- Do not begin M1 before M0 passes; do not begin M2 before M1 passes.
- Stop on unresolved IPC crash/deadlock/race, confirmed cross-profile data leakage, unsafe cleanup race, or unsupported Provider capability needed for a claimed guarantee.
- Harness failure is not Product Isolation pass or failure. Retry only a classified infrastructure flake; never retry to hide a product failure.
- Any architecture, Manifest contract, or product-behavior change requires separate approval.

### Timeline and reporting
- Planning estimate: 7–11 weeks after authorization; refine after M0 based on measured CI stability and actual defects.
- Real-device validation remains scheduled for Day 57–60; no device pass may be claimed before evidence exists.
- Report every milestone with commit, workflow/run, test reports, logs and artifacts. Keep secrets and private browsing data out of artifacts.
- Phase 2 progress is reported separately from Phase 1's 31/60 (51.67%) and never alters that figure.

### Current evidence / history
- Run #80: App/Test APK debug signing mismatch across jobs.
- Run #81: CookieManager.setCookie called without a Looper on the test thread.
- Run #82: Test-APK ResultReceiver class failed cross-process deserialization before WebView initialization; therefore Run #82 does not prove Product Isolation pass or failure.
- Existing implementation is retained as unaccepted Phase 2 work. No new run is authorized by this plan update.

---

## Round A — Android foundation and build (Days 1–10)

**Round acceptance:** Android project builds; CI checks run; release signing is configured using the approved Android Keystore; signed APK verification succeeds. No unsigned release fallback.

- [x] **Day 1:** Inspect the RAIN repository, default branch, existing plan, and current GitHub project structure. Record the verified baseline and avoid overwriting existing work. **Completed: 2026-11-25.** Verified: default branch `main`; repository is public and accessible with read/write permission; root contains `README.md` and `docs/`; `docs/` contains only `RAIN_BROWSER_PLAN_V3.md` and `PHASE1_60_DAY_TRACKER.md`; no Android source/project scaffold or `.github/workflows` files are present in the inspected root/docs listings. Existing plan confirmed as Android-only v3.1, Kotlin + Android SDK + Gradle + WebView, applicationId `com.rain.browser`, initial versionName `0.1.0`. Baseline commit: `e40dc87e6f4e9ea2d24bfe40d2e22fd5c6f6d4a6`; plan blob: `e44f963ad9eccf1c036df133ffa4a679dd552127`. No application code changed on Day 1.
- [x] **Day 2:** Prepare the Android project structure in the existing repository, following the approved Android-only architecture. **Completed: 2026-11-25.** Created the approved directory skeleton with `.gitkeep` placeholders for `.github/workflows`, `app/src/main/java/com/rain/browser` and its `browser`, `privacy`, `network`, `data`, `downloads`, and `settings` packages; `app/src/main/res/layout`, `drawable`, `values`, and `values-th`; `app/src/test`; and `gradle/wrapper`. No Kotlin source, Gradle configuration, Android manifest, or workflow implementation was added because those belong to later scheduled tasks. Evidence commit: `a6fea91e7cdcd492ca6750a09ce2739d27198262`. 
- [x] **Day 3:** Configure Kotlin and Android SDK settings. **Completed: 2026-11-25.** Configured Kotlin compilation through AGP 9 built-in Kotlin support, compileSdk/targetSdk 36, and initially minSdk 29. The owner later approved Android 16+ only; see the platform baseline change above and Day 31 evidence. Evidence: `app/build.gradle.kts`, `gradle/libs.versions.toml`, commit `8e4fde1c224cc2f1e03ee075b1560cf82d7b016a`.
- [x] **Day 4:** Configure Gradle and Gradle Wrapper, pin compatible versions, and verify the build environment. **Completed: 2026-11-25.** Pinned Gradle 9.6.0 and AGP 9.5.2; added the official Gradle 9.6.0 wrapper scripts and JAR. Gradle Wrapper validation passed in Actions run `45565157678`; the later build step exposed a separate Kotlin plugin configuration issue that was corrected. Evidence: `gradle/wrapper/`, `gradlew`, `gradlew.bat`, commit `15fe31c3e457b8f3abd64d233e16454587ef9017`.
- [x] **Day 5:** Set the application identity: app name RAIN, applicationId `com.rain.browser`, initial versionName `0.1.0`, and initial versionCode policy. **Completed: 2026-11-25.** Set app label/name RAIN, applicationId `com.rain.browser`, versionName `0.1.0`, and initial versionCode `1`. Evidence: `app/build.gradle.kts`, `app/src/main/res/values/strings.xml`, commit `15fe31c3e457b8f3abd64d233e16454587ef9017`.
- [x] **Day 6:** Create the starting Activity and initial application screen using the approved theme. **Completed: 2026-11-25.** Added `MainActivity.kt`, `activity_main.xml`, and the approved RAIN theme colors. Evidence: `app/src/main/java/com/rain/browser/MainActivity.kt`, `app/src/main/res/layout/activity_main.xml`, `app/src/main/res/values/`, commit `15fe31c3e457b8f3abd64d233e16454587ef9017`.
- [x] **Day 7:** Add the basic Android WebView and implement the agreed security baseline. **Completed: 2026-11-25.** Added Android WebView with JavaScript/DOM storage/file/content access disabled, mixed content blocked, Safe Browsing enabled, HTTP(S)-only navigation policy, and SSL certificate errors cancelled. Kotlin compilation and lint passed in Actions run `45565610942`. Evidence: `MainActivity.kt`, `NetworkPolicyManager.kt`, `AndroidManifest.xml`, commit `15fe31c3e457b8f3abd64d233e16454587ef9017`.
- [x] **Day 8:** Configure GitHub Actions for Android build, lint, tests, and build artifacts. **Completed: 2026-11-25.** GitHub Actions run `45565610942` succeeded: JDK 17 and Android SDK setup, pinned SDK packages, Gradle Wrapper validation, `clean lint testDebugUnitTest assembleDebug` all passed, and artifact `rain-android-build` (ID `14999508764`, 823,759 bytes) was uploaded. Evidence: [Actions run](https://github.com/phukthaisongp1-collab/Rain/actions/runs/45565610942), `.github/workflows/android-build.yml`.
- [x] **Day 9:** Configure release signing with Android Keystore and GitHub Actions Secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Never commit or expose the Keystore or passwords. **Completed: 2026-11-25.** Created the RAIN release Keystore and protected backup locally in Termux; uploaded all four required repository Actions Secrets successfully. The upload script received successful GitHub API responses for each secret and printed all four `Uploaded` confirmations. Keystore and passwords were not committed or shared in chat. Day 10 signed APK build, `apksigner` verification, and Round A acceptance completed successfully.
- [x] **Day 10:** Build the APK, verify its signature with `apksigner`, review CI results, record evidence, and obtain Round A acceptance. **Completed: 2026-11-25.** GitHub Actions signed release run `45572970312` completed successfully. All 12 workflow steps passed, including required signing-secret validation, Keystore restoration/alias validation, signed Release APK build, `apksigner verify`, artifact upload, and temporary Keystore cleanup. Artifact: `rain-signed-release-apk` (ID `15002972322`, 670,109 bytes; expires 2026-12-09). SHA-256: `bd935363e251661631e81115dc700b214db18f03401ea59a744ae83ce033872e`. [Actions run](https://github.com/phukthaisongp1-collab/Rain/actions/runs/45572970312). Owner explicitly accepted Round A on 2026-11-25.

**Round A review record**
- Review status: **Accepted**
- Review date: 2026-11-25
- Evidence / commit / Actions run: [Signed Release Actions run 45572970312](https://github.com/phukthaisongp1-collab/Rain/actions/runs/45572970312); artifact `rain-signed-release-apk` (ID `15002972322`); SHA-256 `bd935363e251661631e81115dc700b214db18f03401ea59a744ae83ce033872e`.
- Blockers or approved deviations: None recorded. APK has not yet been installed/tested on the owner's phone.

---

## Round B — Browser features (Days 11–28)

**Round acceptance:** Core browsing, tabs, navigation controls, search, zoom, and per-site Mobile/Desktop Mode behavior are tested.

- [x] **Day 11:** Design the Browser screen structure within the approved product scope. **Completed: 2026-11-25. Implemented the RAIN browser screen structure and controls. Evidence: `MainActivity.kt`, `activity_main.xml`; integrated source verified at commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`.**
- [x] **Day 12:** Implement the address bar. **Completed: 2026-11-25. Added address input, Go action, keyboard submission, and address display updates. Evidence: `MainActivity.kt`, `activity_main.xml`; commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`.**
- [x] **Day 13:** Implement URL and scheme validation. **Completed: 2026-11-25. Added URL resolution and HTTP(S)-only navigation policy; rejects unsupported schemes and credential-bearing URLs. Evidence: `BrowserUrlResolver.kt`, `NetworkPolicyManager.kt`, related unit tests; Actions run `45575054677`.**
- [x] **Day 14:** Implement DuckDuckGo as the default search engine. **Completed: 2026-11-25. DuckDuckGo is the default search engine. Evidence: `BrowserUrlResolver.kt`, `MainActivity.kt`, `BrowserUrlResolverTest.kt`.**
- [x] **Day 15:** Add Google as a selectable search engine. **Completed: 2026-11-25. Added Google selection through the search-engine control. Evidence: `BrowserUrlResolver.kt`, `MainActivity.kt`, `BrowserUrlResolverTest.kt`.**
- [x] **Day 16:** Implement Back and Forward navigation. **Completed: 2026-11-25. Added WebView back/forward controls and enabled-state updates. Evidence: `MainActivity.kt`, `activity_main.xml`.**
- [x] **Day 17:** Implement Reload and Stop controls. **Completed: 2026-11-25. Reload control switches to Stop while a page is loading. Evidence: `MainActivity.kt`, `activity_main.xml`.**
- [x] **Day 18:** Implement Home and New Tab controls. **Completed: 2026-11-25. Added Home welcome page and New Tab creation. Evidence: `MainActivity.kt`, `activity_main.xml`.**
- [x] **Day 19:** Implement browser tab creation and management. **Completed: 2026-11-25. Added tab model, tab manager, create/close operations, and tab list dialog. Evidence: `BrowserTab.kt`, `TabManager.kt`, `MainActivity.kt`.**
- [x] **Day 20:** Implement switching between tabs. **Completed: 2026-11-25. Added tab selection and active WebView display. Evidence: `TabManager.kt`, `MainActivity.kt`.**
- [x] **Day 21:** Handle WebView lifecycle and tab state safely. **Completed: 2026-11-25. Added WebView pause/resume, instance-state save/restore, and cleanup on tab/activity destruction. Evidence: `MainActivity.kt`, `WebViewFactory.kt`.**
- [x] **Day 22:** Implement website loading and page display behavior. **Completed: 2026-11-25. Added HTTP(S) navigation, loading progress, title/address updates, SSL-error cancellation, and blocked unsupported schemes. Evidence: `MainActivity.kt`, `WebViewFactory.kt`, `NetworkPolicyManager.kt`.**
- [x] **Day 23:** Implement page zoom controls. **Completed: 2026-11-25. Added WebView zoom in/out controls. Evidence: `MainActivity.kt`, `activity_main.xml`.**
- [x] **Day 24:** Implement text scaling controls. **Completed: 2026-11-25. Added text zoom controls with a 50–200% bound. Evidence: `MainActivity.kt`, `activity_main.xml`.**
- [x] **Day 25:** Implement Desktop Mode using the supported WebView settings and user-agent behavior. **Completed: 2026-11-25. Added desktop user-agent and viewport settings. Evidence: `MainActivity.kt`, `DesktopModeManager.kt`.**
- [x] **Day 26:** Implement Mobile Mode and switching between Mobile/Desktop modes. **Completed: 2026-11-25. Added mode toggle, restoration of default mobile user-agent, and reload after switching. Evidence: `MainActivity.kt`, `DesktopModeManager.kt`.**
- [x] **Day 27:** Persist mode separately per website and reload the page when the mode changes; show the active mode clearly. **Completed: 2026-11-25. Persisted desktop preference by website host and display active mode. Evidence: `DesktopModeManager.kt`, `MainActivity.kt`.**
- [x] **Day 28:** Test the Browser feature set, record evidence, fix verified defects, and obtain Round B acceptance. **Completed: 2026-11-25. GitHub Actions run `45575054677` completed successfully: Gradle Wrapper validation, lint, `testDebugUnitTest`, `assembleDebug`, and artifact upload all passed. Artifact `rain-android-build` ID `15003498586`, 852,812 bytes; SHA-256 `e08afde4d599e9887c068b2d43709cb7674d3f821f7d71fcf50fbd8a94dabd55`. Cloud verification accepted; physical-device end-to-end testing remains scheduled for Day 57 and has not been claimed.**

**Round B review record**
- Review status: **Accepted for cloud-verified implementation and CI**
- Review date: 2026-11-25
- Evidence / commit / Actions run: Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; successful Android Build run [45575054677](https://github.com/phukthaisongp1-collab/Rain/actions/runs/45575054677); artifact `rain-android-build` (ID `15003498586`), SHA-256 `e08afde4d599e9887c068b2d43709cb7674d3f821f7d71fcf50fbd8a94dabd55`.
- Blockers or approved deviations: Manual end-to-end testing on the owner's TECNO phone is intentionally deferred to Day 57 as requested. Cloud build, lint, and unit-test tasks passed; no claim of physical-device verification is made.

---

## Round C — Privacy controls and network (Days 29–46; excludes Private Browsing)

**Round acceptance:** Phase 1 privacy controls and network behavior other than Private Browsing are verified on Android WebView. Private Browsing is tracked independently in Phase 2 and is not a Round C or Phase 1 acceptance gate. Unsupported behavior must be documented and submitted for approval; never claim unsupported guarantees.

- [x] **Day 29:** Inspect Android WebView storage behavior and document constraints relevant to privacy and session isolation. **Completed: 2026-11-25.** Documented process-scoped WebView data directories, cookie/WebStorage scope, Service Worker limitations, and cleanup API constraints in `docs/ANDROID_WEBVIEW_PRIVACY_FINDINGS.md`.
- [x] **Day 30:** Define the approved Private Browsing implementation approach based on verified platform capabilities. **Completed: 2026-11-25.** Owner approved a dedicated `:private` Android process and unique `rain_private` WebView data-directory suffix. Architecture and limitations recorded in `docs/ANDROID_WEBVIEW_PRIVACY_FINDINGS.md`; any implementation and verification work is now tracked in Phase 2 under `docs/RAIN_BROWSER_PLAN_V3.md`, Section 7.1.
**Schedule adjustment (owner-approved 2026-11-26):** The former Private Browsing tasks for Days 31–35 were transferred to Phase 2 (see `docs/RAIN_BROWSER_PLAN_V3.md`, Section 7.1). Day 31 has now been allocated to the already-approved Android 16+ platform baseline and is complete. Days 32–35 remain reserved for later approved, non-Private Phase 1 work; they are not completed days and do not block Day 36. The existing Private Browsing source work and Run #82 evidence are retained; no security acceptance is implied.

### Phase 1 reserve / reallocation capacity

- [x] **Day 31 (reallocated):** Enforce the approved Android 16+ baseline across the app and Phase 1 CI. **Completed: 2026-11-26.** Set `minSdk = 36`; changed the Phase 1 emulator to API 36; removed the forced TCG override; added a KVM acceleration gate and Android 16 install/launch smoke check. Excluded `PrivateBrowsingIsolationTest` from Phase 1 CI because it is deferred Phase 2 work; no Private Browsing acceptance is implied. Build, lint, unit tests, KVM check, Android 16 emulator boot, app install, and app process launch all passed. Evidence: PR #5; merge commit `18f94670b0f2f95a47a9f7bdb1b4dac51abc27f6`; Actions run `45728905806`; artifact `15076530135`; APK SHA-256 `b5e582f2347db11bb13b0914dac5aa3f19bba57e1df2f245155f404665cc546d`.
- [ ] **Days 32–35:** Reserved for future allocation to already-approved non-Private Phase 1 work. Not completed; do not count toward progress until separately implemented and verified.
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
- Review status: Not reviewed; acceptance scope excludes Private Browsing (tracked in Phase 2)
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
- [ ] **Day 52:** Implement cleanup of Resume entries older than 30 days. Private Browsing exclusion remains a Phase 2 integration requirement.
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
| 3 | 2026-11-25 | Configured Kotlin through AGP built-in Kotlin, Android API 36, and initially minimum API 29; Android 16+ baseline was applied later on Day 31. | `8e4fde1c224cc2f1e03ee075b1560cf82d7b016a` |
| 4 | 2026-11-25 | Pinned AGP 9.5.2 / Gradle 9.6.0; official Wrapper validation passed. Full build validation remains part of Day 8 CI. | `15fe31c3e457b8f3abd64d233e16454587ef9017`; Actions run `45565157678` |
| 5 | 2026-11-25 | Set RAIN identity, applicationId, versionName 0.1.0, and versionCode 1. | `15fe31c3e457b8f3abd64d233e16454587ef9017` |
| 6 | 2026-11-25 | Added initial Activity and RAIN-themed screen. | `15fe31c3e457b8f3abd64d233e16454587ef9017` |
| 7 | 2026-11-25 | Added WebView and agreed initial security baseline; Kotlin compilation and lint verified by successful Actions run `45565610942`. | `15fe31c3e457b8f3abd64d233e16454587ef9017`; run `45565610942` |
| 8 | 2026-11-25 | Android CI passed: Gradle Wrapper validation, Kotlin compile, lint, unit-test task, debug APK assembly; build artifact uploaded. | Actions run `45565610942`; artifact `14999508764` |
| 9 | 2026-11-25 | Created and backed up the RAIN release Keystore locally; uploaded all four required GitHub Actions Secrets, each confirmed by successful API response. | Termux upload script; four successful GitHub secret updates |
| 10 | 2026-11-25 | Signed Release APK built; all 12 workflow steps passed; `apksigner verify` succeeded; owner accepted Round A. | Actions run `45572970312`; artifact `15002972322`; SHA-256 `bd935363e251661631e81115dc700b214db18f03401ea59a744ae83ce033872e` |
| 11 | 2026-11-25 | Implemented the RAIN browser screen structure and controls. Evidence: `MainActivity.kt`, `activity_main.xml`; integrated source verified at commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 12 | 2026-11-25 | Added address input, Go action, keyboard submission, and address display updates. Evidence: `MainActivity.kt`, `activity_main.xml`; commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 13 | 2026-11-25 | Added URL resolution and HTTP(S)-only navigation policy; rejects unsupported schemes and credential-bearing URLs. Evidence: `BrowserUrlResolver.kt`, `NetworkPolicyManager.kt`, related unit tests; Actions run `45575054677`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 14 | 2026-11-25 | DuckDuckGo is the default search engine. Evidence: `BrowserUrlResolver.kt`, `MainActivity.kt`, `BrowserUrlResolverTest.kt`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 15 | 2026-11-25 | Added Google selection through the search-engine control. Evidence: `BrowserUrlResolver.kt`, `MainActivity.kt`, `BrowserUrlResolverTest.kt`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 16 | 2026-11-25 | Added WebView back/forward controls and enabled-state updates. Evidence: `MainActivity.kt`, `activity_main.xml`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 17 | 2026-11-25 | Reload control switches to Stop while a page is loading. Evidence: `MainActivity.kt`, `activity_main.xml`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 18 | 2026-11-25 | Added Home welcome page and New Tab creation. Evidence: `MainActivity.kt`, `activity_main.xml`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 19 | 2026-11-25 | Added tab model, tab manager, create/close operations, and tab list dialog. Evidence: `BrowserTab.kt`, `TabManager.kt`, `MainActivity.kt`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 20 | 2026-11-25 | Added tab selection and active WebView display. Evidence: `TabManager.kt`, `MainActivity.kt`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 21 | 2026-11-25 | Added WebView pause/resume, instance-state save/restore, and cleanup on tab/activity destruction. Evidence: `MainActivity.kt`, `WebViewFactory.kt`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 22 | 2026-11-25 | Added HTTP(S) navigation, loading progress, title/address updates, SSL-error cancellation, and blocked unsupported schemes. Evidence: `MainActivity.kt`, `WebViewFactory.kt`, `NetworkPolicyManager.kt`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 23 | 2026-11-25 | Added WebView zoom in/out controls. Evidence: `MainActivity.kt`, `activity_main.xml`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 24 | 2026-11-25 | Added text zoom controls with a 50–200% bound. Evidence: `MainActivity.kt`, `activity_main.xml`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 25 | 2026-11-25 | Added desktop user-agent and viewport settings. Evidence: `MainActivity.kt`, `DesktopModeManager.kt`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 26 | 2026-11-25 | Added mode toggle, restoration of default mobile user-agent, and reload after switching. Evidence: `MainActivity.kt`, `DesktopModeManager.kt`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 27 | 2026-11-25 | Persisted desktop preference by website host and display active mode. Evidence: `DesktopModeManager.kt`, `MainActivity.kt`. | Integrated source commit `64c4f3efa87930c6a543d53a3deedde81871f7d8`; Actions run `45575054677` |
| 28 | 2026-11-25 | GitHub Actions run `45575054677` completed successfully: Gradle Wrapper validation, lint, `testDebugUnitTest`, `assembleDebug`, and artifact upload all passed. Artifact `rain-android-build` ID `15003498586`, 852,812 bytes; SHA-256 `e08afde4d599e9887c068b2d43709cb7674d3f821f7d71fcf50fbd8a94dabd55`. Cloud verification accepted; physical-device end-to-end testing remains scheduled for Day 57 and has not been claimed. | Actions run `45575054677`; artifact `15003498586`; commit `64c4f3efa87930c6a543d53a3deedde81871f7d8` |
| 29 | 2026-11-25 | Inspected WebView process/data-directory isolation, cookie and WebStorage scope, Service Worker limitations, and cleanup API constraints. | `docs/ANDROID_WEBVIEW_PRIVACY_FINDINGS.md`; commit `3942596fe1706865de8d8141eb147f4493bd11aa` |
| 30 | 2026-11-25 | Defined and recorded the owner-approved separate-process Private Browsing architecture; implementation and verification were subsequently deferred to Phase 2 under `docs/RAIN_BROWSER_PLAN_V3.md`, Section 7.1. | Owner approval in conversation; `docs/ANDROID_WEBVIEW_PRIVACY_FINDINGS.md`; commit `3942596fe1706865de8d8141eb147f4493bd11aa` |
| 31 | 2026-11-26 | Enforced Android 16+ platform baseline (`minSdk = 36`), aligned Phase 1 CI with API 36, verified KVM acceleration, and confirmed RAIN installs and launches on the Android 16 emulator. Private Browsing instrumentation was excluded from Phase 1 CI because it is deferred to Phase 2. | PR #5; merge commit `18f94670b0f2f95a47a9f7bdb1b4dac51abc27f6`; Actions run `45728905806`; artifact `15076530135`; APK SHA-256 `b5e582f2347db11bb13b0914dac5aa3f19bba57e1df2f245155f404665cc546d` |

## Blockers and approved changes

Record blockers, platform limitations, and user-approved changes here. Do not silently change the scope or mark blocked work as complete.

| Date | Related day | Blocker / limitation | Decision / approval | Resolution evidence |
|---|---:|---|---|---|
| 2026-11-25 | 8 | Initial CI attempts exposed an obsolete Android SDK setup action and a separate Kotlin plugin conflict with AGP 9. | Updated to setup-android v4 and AGP built-in Kotlin; final CI succeeded with build, lint, unit-test task, APK assembly, and artifact upload. | [Successful Actions run](https://github.com/phukthaisongp1-collab/Rain/actions/runs/45565610942) |
| 2026-11-26 | 31–35 | Private Browsing Instrumentation Run #82 failed before completing the isolation assertions. Diagnostics showed a cross-process `BadParcelableException` / `ClassNotFoundException` involving a Test-APK callback class; this is a confirmed test-harness communication failure, not proof that product isolation passed or failed. Day 31 was reallocated to the Android 16+ baseline; Days 32–35 remain reserved. | Owner directed all Private Browsing implementation and verification to Phase 2, with a dedicated independent Workflow; exclude it from Phase 1 acceptance and keep Phase 1 moving. | [Run #82](https://github.com/phukthaisongp1-collab/Rain/actions/runs/45723328182); artifact ID `15073318212`; Day 31 PR #5 / main run `45728905806`; see Phase 2 backlog in `docs/RAIN_BROWSER_PLAN_V3.md` Section 7.1. |

## Progress update protocol

Whenever work is completed:
1. Verify the actual code, configuration, test, or build result.
2. Update the matching day from `[ ]` to `[x]`.
3. Add its completion date and evidence to the Daily completion log.
4. Recalculate the completed-day count and percentage in the Progress summary.
5. Update the round status only when its acceptance checkpoint is passed.
6. Commit the tracker update to the repository and report the commit and current progress to the project owner.

**Important:** A checkbox means verified completion, not merely that work was attempted. Never check off a day without evidence.
