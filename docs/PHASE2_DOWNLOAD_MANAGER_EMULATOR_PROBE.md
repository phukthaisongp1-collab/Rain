# RAIN Phase 2 — DownloadManager Emulator Probe

Status: instrumentation probe prepared; results pending Android 16 CI.

## Purpose

Exercise the Android platform DownloadManager independently from RAIN's production generic download flow. The probe uses a controlled loopback HTTP server and synthetic MP4-shaped bytes only.

## Coverage

- Enqueue a synthetic video transfer into the app-specific Movies directory.
- Query terminal status and transferred/total byte counts.
- Observe non-zero partial progress while a response is intentionally paused.
- Cancel an active request and verify that DownloadManager removes its request record.
- Run only on the Android 16 CI emulator before Day 35.

## Isolation and safety

- No production DownloadManager code is changed.
- No ordinary file or APK download behavior is changed.
- No database schema, repository, worker, service, video UI or media detection code is changed.
- No third-party websites or media files are accessed.
- Synthetic test files are removed during test cleanup.
- The debug-only network security configuration allows cleartext only for the loopback hosts used by this fixture; other cleartext hosts remain disallowed. The main/release manifest and network policy are unchanged.

## Interpretation

A passing probe demonstrates only that the tested Android 16 emulator's DownloadManager can enqueue, report progress, complete and cancel a simple direct HTTP transfer into app-specific storage.

It does not establish:
- real website media source visibility or permission to save it;
- behavior for redirects, signed URLs, authentication, cookies or CDN restrictions;
- reliable resume after network loss, process death, force-stop or device reboot;
- HLS/DASH, MSE, Blob or DRM support;
- offline playback readiness or full media integrity.

Those cases remain separate gates. A completed DownloadManager request must still be validated before a future video can become VIDEO_READY.
