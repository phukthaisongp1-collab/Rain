# Android WebView Privacy and Session Isolation Findings

**Project:** RAIN Browser  
**Scope:** Phase 1, Days 29–35  
**Platform baseline:** Android minSdk 29, Kotlin, Android WebView  
**Research date:** 2026-11-25

## Day 29 — Verified platform constraints

### WebView data directories and processes

Android WebView provides `WebView.setDataDirectorySuffix(String)` from API 28. The suffix selects the data and cache directory for the current process. A directory may be used by only one process in the application. If multiple processes use WebView, each process must use a unique suffix; only one process may use the default directory. The method must run before any WebView instance is created and before any other `android.webkit` API is called in that process.

Consequences for RAIN:

- RAIN's minimum API 29 supports the platform API.
- A suffix is process-scoped, not a per-WebView or per-tab switch.
- A process cannot safely switch between normal and private suffixes after WebView initialization.
- Separate WebView data directories imply separate process storage and do not share cookies automatically.
- Implementing true separation requires process/Activity lifecycle design and startup ordering, not merely creating another WebView or disabling history.

Official reference: https://developer.android.com/reference/android/webkit/WebView#setDataDirectorySuffix(java.lang.String)

### Cookies

`CookieManager.getInstance()` returns a singleton manager for the application's WebView cookie store in that process. `removeAllCookies` is asynchronous. Removing cookies in the normal WebView process would also affect normal browsing sessions; it is not a safe per-tab private-session cleanup method.

Official reference: https://developer.android.com/reference/android/webkit/CookieManager

### Web Storage

`WebStorage.getInstance()` is a singleton. `deleteAllData()` clears JavaScript storage data (including Web SQL and HTML5 Web Storage) across origins for that WebStorage instance. It is not scoped to one WebView tab. Running it in the normal browser process would erase data for ordinary browsing too.

Official reference: https://developer.android.com/reference/android/webkit/WebStorage

### Service Workers

`ServiceWorkerController.getInstance()` returns the default controller. The Android reference currently describes one controller instance for all WebView instances. Its API exposes settings and request interception, not a simple per-WebView service-worker storage deletion operation.

Official reference: https://developer.android.com/reference/android/webkit/ServiceWorkerController

### AndroidX WebKit browsing-data API

AndroidX WebKit's `WebStorageCompat.deleteBrowsingData` is available in newer AndroidX WebKit versions when the corresponding WebView feature is supported. Its documented operation deletes website data including network cache, cookies, and JavaScript-readable storage for the supplied WebStorage instance. It is an all-data operation for that instance, not a per-tab isolation boundary, and deletion is not atomic.

Official reference: https://developer.android.com/reference/androidx/webkit/WebStorageCompat

## Day 30 — Approved separate-process architecture

### Required security property

A Private Browsing session must not read or write the normal browser's cookies, cache, Web Storage, or other persistent website data. Closing the private session must clean its data to the extent supported and verified. RAIN must not claim RAM-only operation or complete cleanup unless it has been demonstrated.

### Candidate architecture

Use a dedicated Android process for private WebView work, with a unique WebView data-directory suffix configured at process startup before any WebView API is initialized. Keep normal browsing in the existing default process. The private process must own its own WebView instances and session lifecycle.

### Required validation before implementation

1. Verify that normal and private processes cannot read each other's cookies and Web Storage.
2. Verify cache and Service Worker behavior using controlled test origins.
3. Verify cleanup after private-session closure and after process termination/restart.
4. Verify crash recovery and ensure a stale private process cannot keep a session alive.
5. Determine how the private data directory is safely cleaned without deleting data while WebView is using it.
6. Verify behavior against the actual Android System WebView provider on the target device.
7. Document any data types that cannot be isolated or reliably cleared.

### Owner decision and initial implementation

The owner approved the dedicated Android process approach on 2026-11-25.

Initial implementation committed to the repository:

- PrivateBrowsingActivity runs in the manifest process :private.
- `RainApplication.attachBaseContext` detects the dedicated `:private` process and configures `WebView.setDataDirectorySuffix("rain_private")` before the process creates or accesses a WebView instance.
- Normal browsing remains in the default process and default WebView data directory.
- The private activity is single-task to avoid overlapping private activity instances in the same process.
- A Private entry point launches the isolated activity.
- On each new private activity start, the private process clears Web Storage and cookies before creating the WebView, then clears its WebView cache before loading content.
- On explicit session close, it stops and destroys the WebView, clears history/form data/cache, deletes Web Storage data, removes cookies, and flushes the cookie store.

### Verified limitations and acceptance requirements

- The process and data-directory separation is implemented in source. GitHub Actions compilation, lint, and unit-test verification are still required; lifecycle and real process-isolation checks also remain pending.
- Service Worker storage does not have a simple, documented per-session deletion API in the WebView API used here. The dedicated process/data directory isolates its storage from normal browsing, but complete deletion of Service Worker data on close is not yet proven.
- Android may terminate the private process without calling Activity cleanup. Startup cleanup is therefore performed before the next private WebView is created; cache cleanup occurs immediately after WebView creation.
- The implementation does not claim RAM-only operation, guaranteed zero traces, or complete erasure of every WebView-managed artifact.
- The private browsing screen currently provides a single private WebView. Full parity with normal tab management, download handling, permission prompts, and comprehensive cleanup tests remain pending.
- No tests on the owner's physical phone have been performed; device validation remains scheduled for the final phase.
## Current conclusion

The owner-approved separate-process design has been committed and an initial implementation exists. It is not yet accepted as a verified Private Browsing feature: GitHub Actions compilation, automated tests, process/data isolation checks, cookie and Web Storage lifecycle checks, cache behavior, and Service Worker limitations still require validation. RAIN must not advertise complete private-session cleanup, RAM-only operation, or zero traces until the relevant behavior is demonstrated. The separate process is the implementation direction, while feature acceptance remains pending Days 31–35.
