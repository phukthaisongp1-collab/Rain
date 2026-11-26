# RAIN Browser — Round C Review (Day 46)

**Review date:** 2026-11-26  
**Review scope:** Phase 1 Round C, Days 32–45; Private Browsing is excluded and remains a separate Phase 2 workstream.  
**Review decision:** **Accepted with documented platform and verification limitations.**  
**Source baseline:** PR #13 merge commit `7707492e679df68234129431412d47ff64e9231e`.

## 1. Review method

Reviewed the merged implementation and tests for Web Protection, Permission Manager, Downloads, browser navigation/security policy, and optional WebView proxy support. Checked the Android manifest, app Gradle configuration, AndroidX WebKit dependency version, network-policy findings, and Phase 1 CI evidence.

No source, Manifest, Gradle, workflow, engine, or architecture changes were made during this review. No reproducible defect requiring a code change was confirmed, so no speculative patch was introduced.

## 2. Acceptance checklist

| Area | Review result | Evidence / boundary |
|---|---|---|
| Web Protection | Pass within implemented scope | Safe Browsing interstitial retained; decision policy has confidence/freshness/source/corroboration checks; user-facing protection details and origin-only false-positive sharing exist. |
| Site permissions | Pass within implemented scope | Explicit origin-scoped camera, microphone, and geolocation consent; Android runtime permissions; review/revoke support. |
| Downloads | Pass within implemented scope | Every download requires confirmation; URL/name/extension/MIME checks; executable/script and obvious mismatches blocked; APK is saved only, never installed by RAIN. |
| SSL and HTTPS | Pass | SSL errors are cancelled; no certificate proceed path; automatic HTTPS-to-HTTP downgrade without gesture is blocked. |
| WebView content security | Pass | Mixed content is blocked; Safe Browsing enabled; file/content and file-URL cross-origin access are disabled. |
| Navigation / external intents | Pass with documented limits | Main-frame cross-site navigation without gesture requires confirmation; dangerous schemes and unsolicited popup creation are blocked; external allowlist is restricted and confirmed. |
| DNS / DoH | Platform limitation documented | Android system DNS remains default. No app-owned DoH is claimed or enabled because complete WebView coverage is not available within the approved architecture. |
| Proxy | Pass as optional integration point | AndroidX WebKit ProxyController is feature-detected and process-scoped. No proxy is active by default; user-authorized endpoint must be supplied explicitly. |
| Bypass domains | Policy pass | SCB, KBank, .go.th, Gmail, and Outlook root/subdomain rules are direct-connection bypasses only when a proxy override is active. Unit tests reject lookalike/suffix-attack hosts. |

## 3. Verified CI evidence

PR #13 CI completed successfully on commit `286d38626e34598c861cf51853b10731ff7b333e`:

- Gradle Wrapper validation
- Lint
- Unit tests
- Debug APK assembly
- KVM acceleration gate
- Android 16 Emulator install and launch smoke test
- Build/test artifact upload

Actions run: [45761567129](https://github.com/phukthaisongp1-collab/Rain/actions/runs/45761567129)  
Artifact ID: `15089598403`  
Artifact SHA-256: `99354ce2abec5855e2cd3d6547e4e4424e75a60b61d973e1a444fbe288fcb940`

The CI evidence is cloud/emulator evidence only. It is not a physical-device test and does not prove live proxy traffic routing or DoH behavior.

## 4. Remaining limitations (not hidden or treated as passes)

1. No external threat-intelligence feed or automatic false-positive report upload is connected. Safe Browsing remains the live provider signal; the evidence evaluator is not itself a threat-feed source.
2. Android WebView does not expose per-origin website `Notification.requestPermission` control through the implemented API. The app only explains the limitation and links to Android app-notification settings.
3. Download validation uses source URL, filename, extension, and reported MIME type. It is not content-signature malware scanning; user confirmation remains mandatory.
4. The navigation guard uses a curated public-suffix list, not the complete Public Suffix List; WebView does not call `shouldOverrideUrlLoading` for every navigation type (including some POST flows); legitimate popup/`target=_blank` behavior is also blocked.
5. DoH is not implemented. Android system DNS remains the default; users retain control of Android Private DNS settings.
6. Proxy support has no Settings UI or persistent profile, no authentication support, and is provider-dependent. Applying an override supersedes system-wide proxy settings for WebView requests in the app process until cleared. No live proxy traffic test is claimed.
7. The listed bypass domains connect directly rather than through the proxy. The rules do not verify a site's legitimacy or change DNS.
8. No physical-device testing was performed during Day 46. Physical-device validation remains scheduled for the final verification window (Days 57–60).
9. Private Browsing implementation and verification remain Phase 2; this review does not imply isolation or cleanup acceptance.

## 5. Review conclusion

Round C's approved Phase 1 scope (excluding Private Browsing) is accepted with the limitations above explicitly recorded. The review found no confirmed defect that justified a source change. No unsupported DoH, proxy-routing, notification, malware-scanning, or physical-device guarantee is claimed.

Next scheduled workstream: **Round D — Data and Settings (Day 47)**.
