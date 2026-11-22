# TouchMaster Pro v1.1.4

This release fixes the floating bubble disappearing after a delay in selected apps.

## Fix

- Removed the timer-based automatic teardown triggered by a non-target window-state event. Such events can report a launcher or overlay package instead of the actual selected app.
- When a non-target app/window is reported after the target was foreground, active clicking is paused immediately. The settings panel and target markers are hidden; the round bubble and session remain available.
- When the selected target app returns, its target markers are shown again. Known Android permission/installer windows still hide the bubble temporarily and restore it when the target app returns.
- To explicitly end a session, open the bubble menu and tap ×; selecting another app also replaces the previous session.

## Verification

Automated JVM tests, lint, and debug/release builds pass. No Android device or emulator was attached for interactive verification. Release signing is not configured; the release APK asset is unsigned, while the debug APK is signed with a debug key.
