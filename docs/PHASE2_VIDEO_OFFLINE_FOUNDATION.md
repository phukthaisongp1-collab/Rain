# RAIN Phase 2 — Video Offline Foundation

> Status: isolated foundation only; not wired into production downloads or UI.

## Purpose

This work advances implementation tasks that do not depend on A1 real-site detection approval. It is additive and keeps the current WebView engine, TabManager, SQLite schema, generic DownloadManager path, package identity and release signing unchanged.

## Implemented in this branch

- A pure Kotlin retention policy for the already-approved 30-day lifecycle.
- Unit tests for initial expiry, Like exclusion, fresh expiry after Unlike, repeated Unlike, and timestamp overflow.
- The policy has no database, filesystem, Android service, or UI side effects. This prevents accidental deletion or persistence behavior before the storage integration is reviewed.

## Retention semantics

- An unliked video expires 30 days after download completion timestamp.
- A liked video has no automatic expiry.
- An Unlike action starts a new 30-day window at the action timestamp.
- Repeated explicit Unlike actions reset the window from the latest action.
- Manual deletion remains a separate user action and is not implemented by this policy.
- Persistence across process/device restarts remains the responsibility of the future repository and must be tested with an additive migration.

## Still deliberately not implemented

- No SQLite schema changes or migrations.
- No DownloadManager request mapping, worker, foreground service, notifications, or transfer queue.
- No video source extraction, popup, media download, or playback UI.
- No filesystem deletion or retention scheduler.
- No HLS/DASH handling and no DRM, paywall, authentication, or site-restriction bypass.
- No changes to normal file/APK downloads.
- No physical-device tests before Day 35.

## Required next work before production integration

1. Add Android 16 emulator tests around a controlled local HTTP server for DownloadManager enqueue, query/progress, cancellation, network interruption, and process recreation where the platform permits.
2. Define a persisted transfer state machine and reconcile DownloadManager terminal states with file validation.
3. Implement bounded media container validation (MP4/WebM) before marking a file ready; a file extension alone is insufficient.
4. Finalize a managed app-private video directory and path containment checks before retention cleanup.
5. Finalize an additive schema migration preserving all existing schema-v2 rows.
6. Implement repository and UI only after A1 and the backend tests establish supported behavior.
7. Keep all physical-device validation consolidated on Day 35.

## Validation boundary

Passing unit tests for this pure policy proves only the retention calculations. It does not prove persistence, scheduled cleanup, DownloadManager behavior, offline playback, media legality, or A1 detection feasibility.
