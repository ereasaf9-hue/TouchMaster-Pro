# TouchMaster Pro v1.1.3

This release addresses reports that the floating bubble disappears shortly after opening a selected app.

## Fix

- The target-app exit grace period is extended from 900 ms to 5 seconds to tolerate transient window-state events during app launch and transitions.
- Non-system window-state events during the first 10 seconds after the target app first becomes foreground no longer tear down the bubble session; this filters common launcher/activity transition noise at startup.
- The clicker is paused immediately when a non-target app reports foreground, preventing gestures from continuing against an unexpected app while the transition is being confirmed.
- If the selected target app reports foreground again before teardown, the pending teardown is cancelled and the overlay session remains available.

## Verification

Automated JVM tests, lint, and debug/release builds pass. No Android device or emulator was attached for interactive verification. Release signing is not configured; the release APK asset is unsigned, while the debug APK is signed with a debug key.
