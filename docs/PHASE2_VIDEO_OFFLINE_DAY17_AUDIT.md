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

## Implementation added on the Day 17 branch

- Added `VideoDownloadCoordinator` to enqueue direct MP4/WebM transfers with Android DownloadManager, persist the managed destination/request ID, query progress, reconcile requests, cancel, retry, and verify the completed container before READY.
- Connected the RAIN Video Library to periodic status refresh, progress display, cancel, and retry controls.
- Added repository state transitions and unit tests for managed destinations, cancel/retry, and preservation of unrelated files during retention cleanup.
- Added `VideoRetentionCleanup` and a persistent daily WorkManager worker. It only removes expired, unliked records whose files are inside the managed directory; missing-file metadata can be cleaned, while outside-path files are left untouched.
- Added a low-storage notification with a 500 MiB / 10% free-space threshold and a 24-hour notification cooldown. It is skipped if Android notification permission is not granted.
- Added a native fail-closed `VideoDetectionPolicy` and unit tests. It requires a user gesture, main-frame signal, stable direct source, explicit non-ad state, MP4/WebM MIME, and corroborating metadata; duration alone is insufficient.
- Added `VideoSourceValidator` to check DNS/IP safety, redirects, HTTPS downgrade, token/credential-bearing URLs, response status, content type, byte-range metadata, entity validators, and container signatures without sending cookies or authorization.
- Added `VideoMediaInspector` to require Android MediaMetadataRetriever duration and a decodable video frame before a production download can become READY; an API 36 instrumentation test uses a tiny valid MP4 fixture.

## Required code/evidence still missing

1. A production WebView video-signal bridge and user-facing Download/Close popup are not yet connected. This remains gated by the A1 real-site feasibility evidence; the current policy is isolated and is not yet a production capture path.
2. The source preflight is implemented for the direct URL and each observed redirect, but the Android DownloadManager may independently re-resolve the final host or follow a later redirect after preflight. A strict guarantee against DNS rebinding or post-validation redirect changes is not yet established.
3. Android decoder validation and duration extraction are implemented. Thumbnail extraction/persistence and real-device codec coverage remain outstanding.
4. Additional Android 16 instrumentation must cover retry, process restart/recovery, retention worker behavior, and generic download/APK regression.
5. Integration and regression evidence must confirm existing browsing behavior and normal file/APK downloads remain unchanged.

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
