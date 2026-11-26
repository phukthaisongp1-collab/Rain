# RAIN Browser — Physical Device Test Checklist (Day 57)

**Target:** Owner's Android 16 / API 36+ TECNO device  
**Purpose:** Capture evidence for the final Phase 1 acceptance. Do not mark a test passed unless it was actually performed on the device.

## Before testing

- [ ] Download the signed Production APK from the successful **Android Signed Release Verification** Actions run.
- [ ] Confirm the artifact is `rain-signed-release-apk` and the run reports signature, certificate, application ID, and version checks as passed.
- [ ] Confirm the app identity is `com.rain.browser`, versionName `0.1.0`, versionCode `1`.
- [ ] Install or update RAIN. If Android reports a signature mismatch, stop and report it; do not bypass the signature check.
- [ ] Record device model, Android version/API level, WebView provider/version, date, and APK SHA-256.

## Core browsing and tabs

- [ ] Launch RAIN and confirm the browser screen appears without crashing.
- [ ] Open a normal HTTPS website and confirm title/address/progress update.
- [ ] Try an invalid or unsupported scheme; confirm it is not opened as a website.
- [ ] Open several tabs, switch between them, close one, and confirm the remaining tabs still work.
- [ ] Use Back, Forward, Reload/Stop, Home, and New Tab.
- [ ] Change one host to Desktop mode, revisit it, and confirm its preference remains for that host.
- [ ] Visit a different host and confirm its mode remains independent.
- [ ] Test HTTP separately and record the exact result. The Manifest cleartext policy may prevent HTTP even though URL/download policy has HTTP handling; do not assume success.

## Bookmarks, History, and Resume

- [ ] Add a normal page to Bookmarks.
- [ ] Search for the bookmark, open it, and delete it.
- [ ] Visit multiple normal pages and confirm they appear in History.
- [ ] Search History, open an entry, delete one entry, and test Clear All with confirmation.
- [ ] Set automatic History retention to an available value and confirm the setting persists after reopening RAIN.
- [ ] Open multiple normal tabs, send RAIN to the background, then reopen it; confirm the saved session is restored with the previously active tab first.
- [ ] Confirm Resume contains no more than 10 entries and that entries expire after 30 days according to the configured policy.
- [ ] Do not enter passwords or sensitive form data for this test.

## New Tab, Settings, and localization

- [ ] Open New Tab and confirm recent pages and bookmarks are displayed.
- [ ] Open Settings and change language to Thai; confirm the toolbar, New Tab, Settings, protection, and download safety text use Thai where translated.
- [ ] Change language to English and confirm the corresponding English resources appear.
- [ ] Change the default search engine and confirm searches use the selected engine.
- [ ] Change default display mode and confirm it applies to hosts without an explicit per-host override.
- [ ] Reopen RAIN and confirm Settings values persist.

## Security, permissions, and downloads

- [ ] Open an HTTPS site with a valid certificate.
- [ ] Visit a known TLS certificate error test page (for example, `https://expired.badssl.com`) and confirm RAIN does not offer a proceed option.
- [ ] Trigger a cross-site navigation without a direct user gesture and confirm the confirmation prompt appears where WebView exposes the navigation callback.
- [ ] Test camera, microphone, and location permission requests only on a trusted test page; confirm the origin is shown and permission can be denied, granted, reviewed, and revoked.
- [ ] Download a benign PDF and confirm the source/name/type information and confirmation prompt appear.
- [ ] Test a trusted APK download only if needed; confirm RAIN saves it but does not install it automatically. Do not install the downloaded APK as part of this test.
- [ ] Confirm obvious executable/script downloads are blocked.
- [ ] Do not visit real malware/phishing pages or download unsafe files. Use safe test resources only.

## Evidence to return

For each test, record **PASS / FAIL / NOT TESTED**, the exact steps, observed result, and (where useful) a screenshot. Include:
- Device model and Android/API version
- Android System WebView provider and version
- APK version and SHA-256
- Any crash text or Android notification
- Whether HTTP navigation succeeded or was blocked
- Whether Resume restored the expected active tab and ordering

Private Browsing is Phase 2 and is not part of Phase 1 acceptance. Do not count a Private Browsing test as a Phase 1 pass.
