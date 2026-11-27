#!/usr/bin/env bash
set -euo pipefail

adb devices
test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = "36"
test "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1"

# Run deterministic fixtures on every CI build.
./gradlew --no-daemon --stacktrace connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rain.browser.browser.Html5VideoDetectionProbeTest

# Run the approved read-only real-site probe only for its dedicated Day 8 branch.
if [[ "${GITHUB_HEAD_REF:-}" == "phase2/day8-real-site-feasibility-probe" ]]; then
  mkdir -p app/build/reports
  adb logcat -c

  probe_status=0
  if ./gradlew --no-daemon --stacktrace connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rain.browser.browser.RealSiteVideoFeasibilityProbeTest; then
    :
  else
    probe_status=$?
  fi

  # Preserve probe evidence even when the instrumentation test fails.
  adb logcat -d -s RAIN_DAY8_SITE_PROBE:I > app/build/reports/day8-real-site-probe.log || true

  if [[ "$probe_status" -ne 0 ]]; then
    exit "$probe_status"
  fi
fi

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.rain.browser
adb shell monkey -p com.rain.browser 1
sleep 2
adb shell pidof com.rain.browser
