# RAIN Browser — Network Policy Findings (Days 40–45)

**Plan:** Phase 1, Round C  
**Platform:** Android 16 / API 36+, Android System WebView  
**Status:** Implemented to the extent supported by the approved platform and security constraints.

## Executive decisions

- RAIN continues to use Android's system DNS resolver by default.
- RAIN does not force Cloudflare, Google Public DNS, NextDNS, or any third-party resolver.
- No app-level DNS-over-HTTPS (DoH) implementation is enabled. The Android platform exposes system-managed encrypted DNS (Private DNS / DNS-over-TLS), but there is no supported Android API in this project that lets an ordinary app set the device's Private DNS provider or guarantee that all WebView DNS lookups use an app-owned DoH resolver.
- Optional proxy support is implemented only as a process-scoped AndroidX WebKit `ProxyController` integration. It is disabled unless an explicit proxy endpoint is supplied by a user-authorized caller.
- No public/free proxy, hard-coded proxy endpoint, proxy credentials, secondary proxy, or automatic direct fallback is configured.
- The approved bank, government, Gmail, and Outlook domains are bypass rules: they connect directly rather than through an explicitly configured proxy.

## Day 40–41 — DoH feasibility

Android's networking documentation describes the platform DNS resolver and the `DnsResolver` API. Android security guidance recommends allowing the OS to handle DNS traffic and warns that custom app DNS transports can bypass platform DNS protections.

Android Private DNS on supported Android versions uses DNS-over-TLS (DoT), not an app-controlled DoH switch. An app cannot silently set the user's system Private DNS hostname. Implementing DoH only for a helper's own HTTP requests would not cover WebView's full browser traffic and would create split-resolution behavior. Replacing the WebView network stack is outside the approved architecture.

Provider endpoints reviewed for future reference only:

- Cloudflare public DoH: `https://cloudflare-dns.com/dns-query`
- Google Public DNS RFC 8484 DoH: `https://dns.google/dns-query`
- NextDNS DoH requires a user-specific configuration identifier in its endpoint; no RAIN-owned identifier exists or is assumed.

**Decision:** no DoH code, resolver fallback, or provider selection UI is added. Users may configure Android Private DNS themselves in Android Settings; RAIN leaves that system setting unchanged.

## Day 42–43 — Proxy feasibility and implementation

AndroidX WebKit 1.13.0 is already present. Its `ProxyController` supports process-scoped proxy overrides for WebView-initiated requests when the installed WebView provider reports `WebViewFeature.PROXY_OVERRIDE`.

The new `WebViewProxyManager`:

- Detects runtime support before calling ProxyController.
- Accepts only explicit `http://`, `https://`, or `socks://` proxy endpoints with a valid host and optional valid port.
- Rejects embedded credentials, paths, queries, fragments, unsupported schemes, malformed endpoints, and invalid ports.
- Applies one explicitly supplied proxy and waits for the provider callback before the caller should load/reload a page.
- Has no default proxy and is not automatically invoked by app startup.
- Has no automatic secondary proxy or direct fallback for ordinary proxied destinations.
- Can clear the override and return WebView to Android's system proxy configuration.
- Is process-scoped and does not configure the Android device globally.

Important platform behavior: setting a WebView proxy override causes existing system-wide proxy settings to be ignored for WebView requests in that process while the override is active. Callers must obtain explicit user consent and explain that effect. Proxy authentication is not implemented; credential-bearing endpoints are rejected.

The implementation is a safe integration point for a future approved Settings UI. No UI, persistent proxy profile, or active proxy endpoint is added in this workstream.

## Day 44 — Proxy bypass candidates

AndroidX `ProxyConfig.Builder.addBypassRule` supports wildcard bypass patterns. RAIN includes the following roots and their subdomains in the bypass list:

- `scb.co.th` and `*.scb.co.th`
- `kbank.com` and `*.kbank.com`
- `go.th` and `*.go.th`
- `gmail.com` and `*.gmail.com`
- `outlook.com` and `*.outlook.com`

These rules mean **direct connection**, not proxy routing. A pure policy matcher is covered by unit tests for root domains, nested subdomains, case/trailing-dot normalization, and lookalike/suffix-attack rejection.

The bypass list has no network effect while no user proxy is configured. It does not certify the destination site's legitimacy or change DNS.

## Day 45 — Combined verification and limitations

- Unit tests cover proxy endpoint validation and bypass-domain policy.
- The existing Android 16 CI emulator smoke test verifies installation and app launch.
- CI does not prove real proxy traffic routing or DoH operation. No physical-device network test is claimed.
- Runtime proxy availability depends on the installed Android System WebView provider. Unsupported providers return an explicit unsupported result without attempting to set an override.
- ProxyController covers WebView-initiated requests, not every network operation made by unrelated Android components or other app processes.
- DoH remains unimplemented by design until a supported end-to-end WebView DNS strategy is approved and can be verified.
- No RAIN account, cloud sync, external DNS default, or architecture/engine change is introduced.

## Source references

- Android network operations / DNS resolver: https://developer.android.com/develop/connectivity/network-ops/connecting
- Android insecure DNS setup guidance: https://developer.android.com/privacy-and-security/risks/bad-dns
- AndroidX WebKit ProxyController: https://developer.android.com/reference/androidx/webkit/ProxyController
- AndroidX WebKit ProxyConfig.Builder: https://developer.android.com/reference/androidx/webkit/ProxyConfig.Builder
- Cloudflare DoH endpoint: https://developers.cloudflare.com/1.1.1.1/infrastructure/network-operators/
- Google Public DNS DoH: https://developers.google.com/speed/public-dns/docs/doh
- NextDNS setup guidance: https://help.nextdns.io/t/m1hmv0k/which-setup-type-to-use
