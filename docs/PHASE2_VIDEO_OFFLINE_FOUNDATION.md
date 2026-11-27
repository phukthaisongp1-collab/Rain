# RAIN Phase 2 — Video Offline Foundation

Status: isolated foundation only; not wired into production downloads or UI.

## Implemented

- Pure Kotlin retention policy for the approved 30-day lifecycle.
- Unit tests for expiry boundaries, Like exclusion, fresh expiry after Unlike, repeated Unlike, and timestamp overflow.
- Bounded MP4/WebM container signature recognition and unit tests. A filename extension is not accepted as evidence.

## Retention rules

- Unliked videos expire 30 days after download completion.
- Liked videos are excluded from automatic expiry.
- Unlike starts a fresh 30-day window at the Unlike action timestamp.
- Manual deletion is separate from automatic retention.
- Persistence across restarts requires a future repository and additive migration.

## Deliberately not implemented

- No SQLite schema/migration, DownloadManager integration, worker, foreground service, UI, JS bridge, media source capture, or filesystem deletion.
- No changes to generic file/APK downloads.
- No HLS/DASH download, DRM bypass, paywall/authentication bypass, or site restriction bypass.
- No physical-device tests before Day 35.

## Next parallel work before Day 35

1. Android 16 emulator tests using a controlled local HTTP server for DownloadManager enqueue, query/progress, cancellation, network interruption, and process recreation where supported.
2. Persisted transfer state model and DownloadManager reconciliation.
3. Full file/container validation before VIDEO_READY; signature detection alone is only an initial gate.
4. Managed app-private video directory and strict path containment before cleanup.
5. Additive schema migration preserving all existing schema-v2 rows.
6. Repository and UI integration after A1 and backend feasibility gates.

Passing the unit tests proves only the pure retention calculations and initial container signature recognition. It does not prove persistence, full media integrity, DownloadManager behavior, offline playback, or real-site detection.
