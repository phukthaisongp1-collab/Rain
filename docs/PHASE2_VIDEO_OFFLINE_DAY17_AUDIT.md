# Phase 2 — Video Offline Download Day 17 Source Audit

Audit date: 2026-11-28
Audit base: `phase2/video-library-ui` (PR #39 head `724068a858f2f7a06bf7dc5d0548e85cc50be47a`)
Working branch: `phase2/video-offline-day17`

## Executive finding

The repository currently contains the retention policy, MP4/WebM signature helper, managed-path guard, schema-v3 metadata repository, a basic Video Library screen, and an Android 16 emulator probe for the platform DownloadManager. It does **not** yet contain a production video transfer coordinator or a production media-detection/permission pipeline. The existing WebView download callback is the normal generic file/APK path and must remain unchanged.

The Day 17 target is implementation completion plus automated validation where the platform and evidence permit. It is not permission to bypass the A1 real-site feasibility gate, DRM/site restrictions, or the Day 35 physical-device restriction.

## Confirmed existing implementation

| Area | Existing code | Current boundary |
|---|---|---|
| Persistence | `Storage.kt`, schema v3, `VideoRepository.kt` | Metadata lifecycle only; no transfer coordinator |
| Retention | `VideoRetentionPolicy.kt` | Pure 30-day/Like/Unlike calculations; no scheduled file cleanup |
| File validation | `VideoContainerValidator.kt`, `ManagedVideoPathPolicy.kt` | Initial signatures/path containment only; not full decode/integrity validation |
| Library | `VideoLibraryActivity.kt` | Lists metadata, Like/unlike, manual delete, READY local playback |
| WebView | `WebViewFactory.kt`, `WebViewCallbacks` | Generic `DownloadListener`; no video DOM signal or resource observer |
| Generic downloads | `MainActivity.onDownloadRequested/enqueueDownload` | Existing Downloads folder flow; preserve unchanged |
| Platform probe | `VideoDownloadManagerInstrumentedTest.kt` | Synthetic loopback direct HTTP tests for completion/progress/cancel only |
| CI | `.github/workflows/android-build.yml` | Lint/unit/build, Android 16 emulator probes and app smoke |

## Required code still missing

1. Native video transfer orchestration around Android `DownloadManager`: enqueue, persist request ID/destination, query status/progress, cancel, retry, reconcile after process restart, and safely promote a completed file to READY.
2. Production transfer tests: persistence/reconciliation, failed/canceled requests, unsafe URLs, content/container mismatch, duplicate request handling, and recovery after Activity/process recreation where Android permits.
3. Video Library integration: live progress/status, cancel/retry actions, refresh/reconcile, thumbnails and metadata where safely extractable, and robust manual deletion ordering.
4. Scheduled retention cleanup using the approved persistent background-work mechanism; delete only expired, unliked files inside the managed directory, then remove metadata. Liked and unrelated files must never be deleted.
5. Nearly-full-storage warning based on Android storage statistics, without introducing a hard quota or automatic deletion outside the retention rule.
6. WebView media observation and native final decision. JS-provided fields remain untrusted; only a main-frame, user-initiated direct HTTP(S) media source can be considered. Reject Blob/MSE opaque sources, DRM, inaccessible content, authentication/token/paywall restrictions, and uncertain/ad-classification states.
7. Integration and regression tests proving normal file/APK downloads and existing browsing behavior remain unchanged.

## A1 gate — still blocking real-site production detection claims

The prior Detection Feasibility PoC tested synthetic fixtures and a public direct MP4 request. It did not establish reliable real-site ad/main-content separation, iframe/MSE/Blob behavior, legitimate download permission, or token/session behavior. Therefore implementation may add defensive, fail-closed plumbing and controlled tests, but it must not claim universal or real-site-ready video detection/download until the A1 evidence gate is satisfied.

## Security and compatibility constraints

- Keep the existing Android WebView engine and `TabManager`.
- Reuse `rain_browser.db`, `Storage`, `VideoRepository`, and Android `DownloadManager`; no duplicate manager/database.
- Do not change the normal file/APK download path.
- Do not forward WebView cookies, Authorization headers, or cross-origin credentials to a media CDN.
- Validate URL scheme/host, redirect behavior, content type, container signature, file containment, and final file integrity natively.
- No DRM bypass/decryption, paywall/authentication/token/site-restriction bypass, or HLS/DASH downloading in this scope.
- Preserve application ID, signing identity, versioning rules, existing data and additive-only migrations.
- No physical TECNO device test before Day 35.
- Keep all PRs draft/unmerged unless the user explicitly approves merging.

## Completion evidence required by Day 17

- Android lint, unit tests, Debug APK build, and Android 16 emulator instrumentation all pass.
- Controlled local-server tests cover production coordinator behavior, not just raw platform DownloadManager behavior.
- A clean code review confirms the generic download flow is unchanged.
- Remaining real-site/A1 and physical-device limitations are reported explicitly; no unsupported pass claims.
