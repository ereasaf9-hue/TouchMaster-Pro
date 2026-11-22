# TouchMaster Pro v1.1.0

The target-app controller has been redesigned. It now opens without a made-up center tap, lets you add numbered targets, drag them into place, remove them with a long press, move or collapse the panel, and adjust the click interval. Start, pause, resume, stop, and current-state feedback are available from the panel.

App launch and overlay failures now show actionable messages and clean up instead of leaving a broken panel behind. Leaving the target app stops the clicker and clears the session, while a slow cold start no longer closes the panel prematurely.

Stopping prevents future repetitions. Android does not provide a public AccessibilityService API to cancel a gesture already dispatched, so a tap already in progress may finish before stopping.
