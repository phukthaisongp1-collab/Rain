# RAIN Phase 2 — Video Offline Download Preparation

> Updated: 2026-11-27  
> Branch: `phase2/video-offline-preparation`  
> Scope: source audit and implementation preparation only; no production application code changed.

## 1. Purpose and boundaries

This document records work that can proceed in parallel while the A1 video-detection feasibility gate is being evaluated.

Allowed now:
- Audit and document the existing storage and generic download implementations.
- Define a non-binding Video Library information architecture and UI behavior.
- Identify integration points, data lifecycle requirements, risks and tests.

Not authorized by this preparation:
- Production video detection, download popup or media capture.
- Changes to the existing generic file/APK download flow.
- Database schema changes, migrations, WorkManager/Foreground Service dependencies or new download implementations.
- HLS/DASH downloading, DRM handling or bypassing site restrictions.
- Any physical-device testing before Day 35.

The current WebView engine, TabManager, existing repositories and generic DownloadManager path remain the baseline. A1 must be accepted before implementation of the video download pipeline begins.

## 2. Storage source audit

### 2.1 Current baseline

Audited source:
- `app/src/main/java/com/rain/browser/data/Storage.kt`
- `app/src/main/java/com/rain/browser/data/ResumeRepository.kt`

Findings:
- The application uses `SqliteStorage` backed by `rain_browser.db`.
- Current schema version is 2.
- Existing tables are `bookmarks`, `history`, `resume` and `settings`.
- `Storage` is a small generic interface with `get`, `set`, `query` and `delete`.
- `SqliteStorage.requireTable` uses an explicit table allowlist. A future video table must not be used until its table name is intentionally added to that allowlist.
- Current `onUpgrade` migration is additive; it adds an index for older schemas and does not drop existing user data.
- `ResumeRepository` demonstrates the current repository pattern and uses persisted timestamps, but it is not a Video Library and must not be repurposed to store downloaded video records.

### 2.2 Requirements for a future additive migration

Before implementation is authorized, the design and tests must cover:
1. Preserve all existing bookmark, history, resume and settings rows.
2. Upgrade a real schema-v2 fixture to the new schema without destructive operations.
3. Keep the migration transactional and safe to retry after process interruption.
4. Persist Like state and the exact unlike timestamp so closing/restarting the app does not reset the 30-day countdown.
5. Treat a missing video file separately from a missing metadata row; recover or mark the item unavailable without deleting unrelated data.
6. Do not store cookies, authorization headers, signed query tokens or other session credentials in the Video Library metadata.
7. Store only the minimum source attribution needed for display/troubleshooting; avoid retaining a full signed media URL unless a later security review justifies it.

### 2.3 Candidate metadata model — proposal, not schema approval

A future repository may need fields equivalent to:
- Stable local video ID.
- DownloadManager request ID, if that backend is selected.
- Display title, MIME/container type and duration when known.
- App-managed media file location and optional thumbnail location.
- Byte size and transfer/validation status.
- Download timestamp.
- Like state.
- Liked timestamp and unlike timestamp (or a single explicit expiry timestamp with equivalent semantics).
- Last recoverable error and integrity-validation result.

This is a logical field inventory only. Field names, SQL types, indexes and schema version must be decided after the backend audit and before any migration is written.

### 2.4 Retention state rules

The accepted product policy is:
- An unliked completed video expires 30 days after download.
- Liking a video removes it from automatic expiry.
- Unliking starts a fresh 30-day countdown from the unlike action, not from the original download date.
- Manual deletion is always available.
- App restart and device reboot must not reset Like or expiry state.
- Low-storage warnings must not delete liked videos or unrelated user files.

The future cleanup operation must be idempotent, skip liked items, verify that a target path is inside RAIN's managed video directory, and reconcile file/metadata mismatches safely. It must never recursively delete arbitrary paths.

## 3. Existing download implementation audit

### 3.1 Current production path

Audited source:
- `app/src/main/java/com/rain/browser/MainActivity.kt`
- `app/src/main/java/com/rain/browser/downloads/DownloadPolicy.kt`
- `app/src/test/java/com/rain/browser/downloads/DownloadPolicyTest.kt`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`

Current flow:
1. `WebViewFactory` installs a WebView `DownloadListener`.
2. `MainActivity.onDownloadRequested` obtains a suggested filename and evaluates it with `DownloadPolicy`.
3. Blocked file types and unsafe URLs are rejected; other files require user confirmation.
4. `enqueueDownload` creates an Android `DownloadManager.Request`.
5. The generic download is saved under the public Downloads directory and Android displays a completion notification.

### 3.2 Existing capabilities and gaps

| Capability | Existing evidence | Video Library implication |
|---|---|---|
| Generic file download | Existing DownloadManager enqueue path | Preserve unchanged for PDFs, APKs and other files |
| URL and filename policy | DownloadPolicy validates scheme, host, credentials, type and filename | Reuse only after a separate media-specific policy is designed |
| Transfer notification | DownloadManager completion notification is configured | Existing path does not provide a RAIN video-library notification flow |
| Persisted request-to-video mapping | Not found in the audited MainActivity path | A future repository must persist the request ID and recover it |
| Progress UI | No app-level DownloadManager query/progress UI found in the audited path | Requires a separately scoped UI/repository integration |
| Cancel/retry controls | No app-level video cancel/retry implementation found | Must be designed against DownloadManager's real status semantics |
| Managed video retention | Current generic files use public Downloads | Video files need a RAIN-managed location if RAIN is to enforce the 30-day policy safely |
| WorkManager | Not present in the audited version catalog/dependencies | Do not add until recovery/cleanup design and need are approved |
| Foreground service | No video-specific service found in audited source | Add only if Android behavior proves DownloadManager insufficient |

### 3.3 Integration principles

- Keep ordinary downloads and APK downloads on their existing path.
- Do not silently redirect generic downloads into the Video Library.
- A future video transfer may reuse Android DownloadManager only if it can meet the required destination, request tracking, notification, retry and recovery behavior.
- Persist a backend request ID before relying on it for reconciliation; test missing, stale and terminal request IDs.
- A transfer reaching a completed state is not enough to mark a video playable. Validate the downloaded container and file before transitioning to `VIDEO_READY`.
- For HTTP Range resume, verify the server response and entity validator. Never append bytes if the server returns a full response or the entity changed.
- Never assume a playing WebView resource is downloadable. Do not forward credentials to an unrelated CDN origin.
- Do not bypass DRM, encryption, paywalls, authentication or website restrictions.

## 4. Video Library UI preparation

### 4.1 Visual direction

- Use RAIN logo, colors and product identity.
- Use YouTube's library experience only as a usability reference; do not use YouTube branding, logos or copied assets.
- Keep the library focused on videos stored on this device and available offline.
- Design for portrait and landscape layouts and Android system insets.

### 4.2 Screen structure proposal

1. **Top app bar:** RAIN identity, screen title (for example, “Videos”), search action and overflow/manage action.
2. **Filter row:** All, Liked and optionally Downloading/Unavailable states when those states exist.
3. **Video list:** Thumbnail on the left, title and duration, file size, download date, status/expiry detail, and a clear overflow action.
4. **Per-item actions:** Play offline, Like/Unlike and Delete. Do not expose Play for incomplete, missing or corrupt files.
5. **Empty state:** Explain that downloaded videos will appear here and provide a return-to-browser action.
6. **Storage warning:** A non-destructive warning when device storage is nearly full. It must not automatically remove liked videos or unrelated files.
7. **Unavailable/corrupt state:** Explain that the file cannot currently be played and offer retry or delete only when those actions are valid.

### 4.3 Item information hierarchy

- Thumbnail and title are primary.
- Duration and file size are secondary.
- Download date and expiry/liked status are tertiary.
- Like state must be visible and easy to toggle.
- For an unliked item, show the remaining retention period or expiry date when known.
- For a liked item, show that it is kept until the user deletes it.
- Do not display fabricated duration, quality or file size when metadata is unavailable.

### 4.4 UI state model

The eventual UI should distinguish at least:
- Downloading.
- Paused/waiting for network, if the backend exposes this state.
- Transfer completed but validation pending.
- Ready for offline playback.
- Failed/retryable.
- Corrupt/unplayable.
- Missing file/unavailable.

These are presentation states to map from the selected backend; they are not yet implementation enums.

## 5. Next technical gates

Before production implementation:
1. Complete A1 detection feasibility and record supported/unsupported media classes.
2. Confirm which direct MP4/WebM sources can be validated as legitimately downloadable.
3. Finish DownloadManager capability tests for progress, cancel, request recovery, destination and resume behavior on Android 16 emulator CI.
4. Select the managed storage location and document uninstall/backup implications.
5. Finalize the additive database migration and test schema-v2 preservation.
6. Approve the Video Library UI structure and state mapping.
7. Only then implement the smallest compatible integration with existing RAIN components.

## 6. Test preparation matrix (emulator/CI before Day 35)

| Area | Required evidence |
|---|---|
| Database migration | Upgrade schema v2 fixture; verify all old rows remain unchanged |
| Retention | 30-day boundary, Like exclusion, Unlike fresh 30-day window, manual delete |
| Restart/recovery | Persist Like/expiry/request ID across process recreation and reboot simulation where CI supports it |
| File safety | Reject paths outside managed directory; handle missing, truncated and corrupt files |
| Download backend | Query status, progress, cancel, retry and recover stale request IDs |
| Normal downloads | Regression tests prove APK and generic downloads retain existing behavior |
| UI state | Empty, downloading, ready, liked, expiring, failed, corrupt and missing-file states |
| Offline playback | Playback uses local managed file and does not require network |

All physical-device tests remain reserved for Day 35. No physical-device test is part of this preparation.

## 7. Audit conclusion

The repository already has a generic DownloadManager flow and a small SQLite storage abstraction that can inform the future implementation. It does not yet have a Video Library, video-specific persistence, app-level transfer tracking, retention cleanup or video playback validation.

The safest path is to preserve the existing generic download behavior, add a separate video domain only after A1 and backend feasibility are accepted, and use additive migrations with explicit recovery tests. This preparation changes documentation only and does not authorize or implement the production feature.
