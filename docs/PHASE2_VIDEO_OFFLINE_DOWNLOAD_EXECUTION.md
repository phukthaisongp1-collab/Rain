# RAIN Phase 2 — Video Offline Download Execution Control

> Updated: 2026-11-28
> Repository: `phukthaisongp1-collab/Rain`
> Current application baseline: `com.rain.browser` / `0.1.4` / `versionCode 5`

## 1. Mandatory execution rules

### 1.1 Physical-device testing is Day 35 only

- Days 1–34: source review, implementation, unit tests, static analysis, builds, CI and Android emulator tests only.
- Do not schedule or report any physical-device test during Days 1–34.
- Day 35 is the single consolidated physical-device test day on the owner's Android 16 device. It covers the complete approved test matrix, update-over-install, data preservation, downloads, background behavior, offline playback and retention.
- Android emulator instrumentation in GitHub Actions is CI, not a physical-device test, and may run before Day 35.

### 1.2 Update-over-install compatibility is a release blocker

Every release must preserve all of the following:

1. `applicationId = com.rain.browser`.
2. The original Android signing certificate, Keystore, key alias and signing identity. Never generate a replacement key for an ordinary update.
3. A strictly increasing `versionCode`. The current installed baseline is `versionCode 5`; the next app release must use at least `versionCode 6`.
4. The existing user data and app-private storage. Never clear app data as an update workaround.
5. Additive database migrations only. Never drop or recreate existing user tables to introduce Video Library metadata.
6. Release verification must check application ID, version metadata and the pinned signing certificate before an APK is described as update-compatible.

The current signed-release workflow pins the certificate SHA-256 to:

`d1b9587fd636c1e0e92a5718576381aadbc72190f5c927f45e7d4ebe0eadb592`

The current release workflow also pins `versionName=0.1.4` and `versionCode=5`. When the next application release is authorized, update the Gradle version metadata and the workflow's expected metadata together, with `versionCode >= 6`, while keeping the certificate pin unchanged. A test-only PR must not increment the app version or produce a new release APK.

### 1.3 Existing data must remain intact

Source audit baseline:

- SQLite database: `rain_browser.db`
- Current schema version: `2`
- Existing tables: `bookmarks`, `history`, `resume`, `settings`
- Existing download flow: WebView `DownloadListener` → `DownloadPolicy` → Android `DownloadManager`
- Existing normal file downloads and APK downloads must continue to work.

If Video Library metadata is added later, migration must preserve every existing table and row. Any schema bump must have explicit upgrade tests from the current version 2 database. A migration failure must not silently wipe user data.

## 2. Thirty-five-day work plan

| Day | Work | Current status |
|---:|---|---|
| 1 | Audit WebView and media callbacks | Done — existing WebView has a DownloadListener; no media interception implementation was found in the factory |
| 2 | Audit download policy and DownloadManager | Done — existing generic file download path identified; no video library integration found |
| 3 | Audit storage, dependencies and release identity | Done — SQLite v2 and current signing/version constraints recorded |
| 4 | HTML5 video DOM feasibility probe | Done — WebView can expose source and player metadata when present |
| 5 | Multiple video and no-source cases | Done — multiple elements are reported; missing source remains empty rather than being invented |
| 6 | Explicit ad UI signal probe | Done for exposed DOM markers only; not a universal ad classifier |
| 7 | Direct MP4 resource visibility | Done for one public MP4 sample on Android 16 emulator; WebViewClient observed the resource request |
| 8 | Detection feasibility acceptance gate | Partial / blocked — Existing emulator CI passed. Added a read-only real-site WebView probe for the three owner-authorized URLs on branch `phase2/day8-real-site-feasibility-probe`; it has not yet been executed. Real-site ad classification and source-permission evidence remain unproven |
| 9 | Download core foundation | Blocked by Day 8 gate |
| 10 | Direct MP4 download integration | Blocked by Day 8 gate |
| 11 | Direct WebM download integration | Blocked by Day 8 gate |
| 12 | Real source quality options | Blocked by Day 8 gate |
| 13 | Download progress and status | Blocked by Day 8 gate |
| 14 | Cancel and retry behavior | Blocked by Day 8 gate |
| 15 | Background download lifecycle | Blocked by Day 8 gate |
| 16 | Network recovery and safe resume | Blocked by Day 8 gate |
| 17 | Process recovery and reconciliation | Blocked by Day 8 gate |
| 18 | Notifications | Not started |
| 19–23 | Video Library and offline playback | Not started |
| 24–26 | Like, Unlike and 30-day cleanup | Not started |
| 27–30 | Integrity, security and storage recovery | Not started |
| 31–33 | Integration, regression and fixes | Not started |
| 34 | Pre-acceptance CI and test evidence review | Not started; no physical-device test |
| 35 | All physical-device testing and final acceptance | Reserved exclusively for the owner's Android 16 device |

## 3. Source audit findings

### Existing download path

- `MainActivity.onDownloadRequested` obtains a safe filename through Android `URLUtil`.
- `DownloadPolicy.evaluate` checks URL scheme/host, credentials, executable types, MIME/extension mismatches and filename safety.
- Accepted files are queued through the existing Android `DownloadManager`.
- The current path saves normal files to the public Downloads directory and displays the Android download notification.
- This is a generic file-download path, not a Video Library, retention manager or media detector.

### Existing WebView path

- `WebViewFactory` configures the existing Android WebView and its navigation/security callbacks.
- It installs a `DownloadListener`.
- No production `shouldInterceptRequest` media observer or video-detection bridge was found in the audited factory.
- The new probe uses an isolated test WebView. It does not alter production WebView behavior.

### Storage and dependencies

- Existing storage is `SqliteStorage`, database version 2.
- No video metadata table or WorkManager integration was found in the audited source.
- Existing dependencies include AndroidX Activity and AndroidX WebKit; WorkManager is not currently declared.
- Do not add a second download manager or introduce new dependencies before the approved architecture gap is demonstrated.

## 4. Detection PoC evidence

The Android 16 emulator CI runs:

- Run #241: https://github.com/phukthaisongp1-collab/Rain/actions/runs/46013946186 (head `a9926216aaec1eaaafec6b8e74003e7bf29ae5a0`); all workflow steps completed successfully, including lint/unit/build, Android 16 instrumentation, install-launch smoke test and artifact upload.
- Run #240: https://github.com/phukthaisongp1-collab/Rain/actions/runs/46012304095 (head `a331fea24d9c6ca731088a343e19a3a83ee62a3e`); this is the run whose downloaded XML reports were inspected for exact test counts.
- Test artifact: https://github.com/phukthaisongp1-collab/Rain/actions/runs/46012304095/artifacts/15204458462
- Run #241 result: lint, unit tests, Android 16 emulator instrumentation, install/launch smoke test and artifact upload all passed.
- Run #240 parsed artifact reports: 96 unit tests across 10 test classes and 12 Android 16 emulator instrumentation tests; 0 failures, 0 errors and 0 skipped tests. Run #241 used the same application/test sources; its commit changed documentation only. Its artifact has been retained, but exact XML counts are not separately asserted here.
- Instrumentation cases cover exposed HTML5 metadata, missing sources, explicit ad markers, fail-closed payload semantics, one public direct MP4 request, synthetic WebM request visibility, dynamic source changes, Blob URL opacity, iframe document boundary, and synthetic pre-roll/mid-roll marker transitions.
- Artifact contents were inspected directly. This is emulator CI evidence only, not a physical-device result or proof of real-site ad classification/download permission.

The four PoC cases are:

1. Read multiple HTML5 video elements and exposed MP4/WebM URLs, poster and player state.
2. Return an empty source when a video element exposes no source.
3. Observe one direct MP4 resource request through a test WebViewClient on Android 16 emulator.
4. Collect explicit `Advertisement` and `Skip Ad` DOM markers when the player exposes them.

These results establish limited feasibility only. Follow-up instrumentation probes cover dynamic source changes, Blob URL handling and the current document/iframe boundary.

### Read-only real-site probe added on 2026-11-28

- Added `RealSiteVideoFeasibilityProbeTest.kt` on branch `phase2/day8-real-site-feasibility-probe`.
- It opens only the three owner-authorized top-level pages: `037-hdd.com`, `fwip-tv.com/visionquest/`, and `nunghd4k.com`.
- It records visible HTML5 video metadata, exposed source URLs with query strings/fragments removed, iframe URLs, visible ad-label markers, Blob/MSE indicators, and a count of media-like resource entries.
- It does not click controls, start playback, download media, inspect protected media responses, or infer permission from a visible URL. The probe runs in an isolated test WebView and does not change production browsing behavior.
- This test is intentionally not part of normal CI because third-party pages are mutable and may be unavailable. Run manually on an Android 16 emulator with: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rain.browser.browser.RealSiteVideoFeasibilityProbeTest`.
- CI captures log output using `adb logcat -d -s RAIN_DAY8_SITE_PROBE:I` and uploads it as `day8-real-site-probe.log`. Review the report for each site and retain only redacted output; never publish cookies, query tokens, or user session data. A timeout or navigation error is evidence of an inaccessible/unsupported test page, not a successful detection result.
- Status: implementation committed. The dedicated Day 8 branch now runs this probe on the Android 16 CI emulator and uploads the redacted probe log with the test reports. A successful workflow run proves only that the probe executed; Day 8 remains blocked until all three site reports are reviewed against the A1 criteria below. This is not, by itself, a Day 8 pass. A synthetic WebM request-visibility test has now been added; it must pass CI before being counted as verified. The synthetic response is intentionally not a playable asset and proves neither real-site media visibility nor permission to download. The existing evidence does not prove that all websites expose media URLs, that all media requests are visible, or that absence of an ad marker proves a video is main content. The PoC deliberately does not classify a video as safe to download and does not show a popup.

## 5. Gate before Days 9–17

The approved addendum requires A1 detection feasibility evidence before full product implementation. The current PoC has not yet established:

- Reliable ad/main-content distinction on real websites, including pre-roll and mid-roll transitions.
- Behavior for cross-origin iframes, MSE/Blob sources and source changes.
- WebM request visibility on Android WebView from a synthetic fixture (verified by Run #240); real-site WebM visibility remains unproven.
- A defensible fail-closed decision for ambiguous pages.
- That the observed resource is legitimately downloadable and not DRM-, token-, authentication- or site-restricted.

Therefore Days 9–17 remain blocked. Do not create a production popup, mark a video downloadable merely because it plays, or build the Video Library around an unverified media source. Continue with targeted feasibility tests and source evidence first. If the gate cannot be satisfied for a media class, mark that class unsupported/partially supported rather than bypassing restrictions.

## 6. Change log

### 2026-11-28

- Added an isolated, read-only Android WebView instrumentation probe for the three owner-authorized real-site URLs. It records only DOM-level signals and redacts URL query strings/fragments; it does not play or download media. Probe execution is still pending, so Day 8 remains blocked.

### 2026-11-27

- Consolidated all physical-device testing into Day 35.
- Recorded source audit findings and Android 16 emulator PoC evidence.
- Added Android 16 emulator probes for a synthetic WebM request, fail-closed ad-signal semantics, and synthetic pre-roll-to-main-source / mid-roll-marker transitions. Run #240's inspected reports show 12 instrumentation tests and 96 unit tests with no failures, errors or skips; Run #241 also completed successfully after a documentation-only update. These tests verify probe behavior and signal changes only; they do not establish real-site ad classification or download permission.
- Added mandatory update-over-install invariants: stable application ID, signing certificate, strictly increasing versionCode and non-destructive data migrations.
- Kept Days 9–17 gated until real-site detection and ad-classification feasibility is demonstrated.
