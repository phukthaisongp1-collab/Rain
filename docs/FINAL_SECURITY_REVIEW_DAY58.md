# RAIN Browser — Final Security Review (Day 58)

**Date:** 2026-11-26  
**Review basis:** Source and configuration review on main commit `7c33e4714c110c7af3bfe7ef0ff8e0aa2e22a69e`, plus PR CI run `45771644136`  
**Result:** Static review completed; no confirmed critical source-level defect found in the reviewed Phase 1 paths. Device-dependent checks and documented limitations remain open.

## Areas reviewed

### Secrets and release signing

- Current source search found no `BEGIN PRIVATE KEY` or `ghp_` token matches.
- Release workflow reads the four signing values from GitHub Actions Secrets; no Keystore or password value is embedded in source.
- Release workflow restores the Keystore into the runner's temporary directory, validates the alias, and removes the temporary file in an `if: always()` step.
- The workflow pins the expected signing-certificate SHA-256 and checks APK metadata before artifact upload.
- Day 59 Production APK verification passed in [Actions run 45772070104](https://github.com/phukthaisongp1-collab/Rain/actions/runs/45772070104).
- Artifact: `rain-signed-release-apk`, ID `15094990948`, 1,823,647 bytes.
- APK SHA-256: `c03373c059fd72c01d5f1abcf1db028c52bc4817a2d5c7888fff8a52ce4fbedb`.
- Signing certificate SHA-256 matched the pinned original certificate: `d1b9587fd636c1e0e92a5718576381aadbc72190f5c927f45e7d4ebe0eadb592`.
- Verified metadata: applicationId `com.rain.browser`, versionName `0.1.0`, versionCode `1`.
- The temporary Keystore cleanup step passed.

### Application and data handling

- Manifest sets `android:allowBackup="false"`.
- Private Browsing Activity is non-exported and runs in `:private`.
- `RainApplication` sets WebView data-directory suffix `rain_private` in the private process during `attachBaseContext`, before Private Browsing creates a WebView.
- Normal History and Resume use app-managed SQLite. The Private Browsing Activity does not call the normal History or Resume repositories.
- History accepts HTTP(S) URLs only and is capped at 5,000 entries.
- Resume accepts HTTP(S) URLs without userinfo, is capped at 10 entries, and removes entries older than 30 days asynchronously. It stores URL/title metadata only; no password or form fields are persisted.
- No RAIN account or cloud synchronization is present.

### WebView and navigation

- JavaScript and DOM storage are enabled for normal browser compatibility.
- File access, content access, file-URL cross-origin access, and universal file-URL access are disabled.
- Mixed content is set to `MIXED_CONTENT_NEVER_ALLOW`; Safe Browsing is enabled.
- SSL certificate errors are cancelled; there is no proceed/exception path.
- JavaScript-created windows and multiple windows are disabled; popup creation is rejected.
- The app URL policy accepts HTTP(S) URLs with a host and rejects credential-bearing URLs.
- Cross-site navigation and external schemes use confirmation policy; unsafe schemes are blocked.

### Permissions and downloads

- Normal site permissions are stored by normalized HTTPS origin and require Android runtime permission where applicable; grants can be reviewed and revoked.
- Private Browsing denies WebView permission and geolocation requests.
- Downloads require user confirmation. Executables/scripts and clear extension/MIME mismatches are blocked. APK downloads are saved only; RAIN has no auto-install path.
- Download source, filename, MIME type, and risk reason are presented before confirmation.

## Known limitations and required disclosure

1. **Private Browsing is Phase 2.** Runs #80–#82 did not pass the isolation acceptance gate. This review does not claim Private Browsing isolation or cleanup is accepted.
2. **HTTP behavior needs physical-device verification.** The app URL policy and download policy accept HTTP with warnings in relevant flows, while the Manifest sets `android:usesCleartextTraffic="false"`. Do not claim general HTTP page/download behavior until the actual Android 16 device/provider behavior is verified and this policy interaction is resolved or explicitly documented.
3. **Navigation interception is not universal.** WebView does not expose every navigation type (including some POST flows) through the same callback. The registrable-domain comparison uses a curated suffix list rather than a complete Public Suffix List.
4. **Popups are blocked**, including legitimate `target=_blank` flows.
5. **Web Protection has no external threat-intelligence feed or report backend** in this release scope; Safe Browsing provider interstitials remain the authoritative hard-block path.
6. **ProxyController is provider-dependent and process-scoped.** No live proxy traffic test, saved proxy profile, or proxy credential UI is claimed. Android system DNS remains the default; no forced third-party DoH resolver is used.
7. Website notification permission is not exposed per origin by the current WebView implementation; the UI directs users to Android app notification settings.
8. The Android 16 emulator smoke test verifies install and launch, not all interactive flows. Physical-device testing on the owner's TECNO device is still required.

## Conclusion

The reviewed Phase 1 source and configuration show the documented security controls in place, and no confirmed critical defect requiring an immediate source patch was found in this static review. Release signing and metadata verification are complete. Final Phase 1 acceptance remains conditional on Day 57 physical-device testing and Day 60 owner review. No unsupported privacy, isolation, HTTP, or network guarantees should be advertised.
