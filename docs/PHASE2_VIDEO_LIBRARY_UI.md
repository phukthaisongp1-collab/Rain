# Phase 2 – Offline Video Library UI

## Scope

This change adds the first user-facing RAIN Video Library screen on top of the existing `VideoRepository` and `rain_browser.db` schema-v3 foundation.

- Adds an Offline videos entry to the existing browser menu.
- Lists persisted video records and their current lifecycle status.
- Displays saved file size, duration, save date, Like state, expiry date, and failure reason when available.
- Allows offline playback only for records in `READY` state whose file exists inside RAIN's managed Movies directory.
- Allows Like/unlike for ready videos and reflects the repository's existing 30-day expiry metadata.
- Allows manual deletion only for non-active records; file deletion is restricted by `ManagedVideoPathPolicy` before metadata is removed.
- Uses the existing database and repository. No second database or download manager is introduced.

## Storage location

The library expects RAIN-managed video files under:

```text
<app-specific external Movies directory>/RAIN/
```

If Android does not provide the app-specific external Movies directory, the activity uses the app's private files directory fallback with the same `movies/RAIN` suffix.

## Safety behavior

- Files outside the managed directory are never played or deleted by this screen.
- A missing file cannot be played; the library entry remains available for user cleanup.
- Active `QUEUED`, `DOWNLOADING`, and `VERIFYING` entries cannot be deleted from this screen, avoiding orphaning an active system transfer.
- Deleting a record removes the managed file first (when present), then removes its metadata.
- The screen does not access WebView cookies, credentials, page JavaScript, or arbitrary media URLs.

## Evidence boundary / remaining work

This is a library and playback UI only. It does not yet provide:
- Production media detection or permission decisions.
- Production DownloadManager enqueue/progress/cancel/retry integration.
- Background transfer recovery.
- Automated 30-day expiry cleanup or nearly-full-storage notifications.
- Thumbnail extraction or video metadata probing.
- A guarantee that any third-party streaming site can be downloaded.

Only a `READY` record created by a separately validated transfer can be played here. Existing generic file and APK downloads remain unchanged. No physical-device test is performed before Day 35.
