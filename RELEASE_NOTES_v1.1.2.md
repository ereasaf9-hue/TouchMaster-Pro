# TouchMaster Pro v1.1.2

This release contains fixes and usability improvements identified during QA of v1.1.1.

## Fixes

- Taps outside the open settings panel now pass through to the game.
- Per-app visual settings reset to defaults when an app has no saved profile; switching games no longer carries the previous game's bubble/panel appearance into a new profile.
- Target position, interval, press duration, and repeat-count changes are applied to the next gesture while the clicker is running. A gesture already dispatched is not retroactively changed or cancelled.
- Multi-target cycles now respect the total press-stroke duration as their minimum effective interval, preventing unrealistic faster-than-possible scheduling.
- Compact screens get panel dimensions and stepper controls clamped to the available display size.
- Pending marker long-press callbacks are cancelled when markers are rebuilt; service state collectors are cancelled when the accessibility service disconnects.
- Known Android system permission/installer dialogs pause automation and temporarily hide overlays rather than immediately destroying the game session; returning to the game restores the bubble and targets in a paused state.
- Shizuku is no longer a launch prerequisite for click automation, which uses AccessibilityService. Shizuku remains available as an optional connection in Settings.
- Battery-optimization guidance no longer implies that exemption guarantees the OS will keep the service alive.
- Release documentation now matches the workflow: the release APK is unsigned until signing is configured.

## Verification

`testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleRelease` pass in the sandbox with the Android SDK configured. The new engine test covers live target updates and multi-target cadence. No emulator or physical device was attached for interactive overlay testing.

Release signing remains unconfigured, so `app-release-unsigned.apk` is not a directly installable distribution build. The `app-debug.apk` asset is signed with the debug key.
