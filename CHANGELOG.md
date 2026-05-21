# Burpcord Changelog

High-level changes for users and release highlights. For implementation detail (classes, APIs, refactors), see [`docs/release_notes.md`](docs/release_notes.md).

## [v2.7.1] - 2026-05-21

### 🐛 Bug Fixes

- **Version in hover tooltip**: The large-image hover text now includes the full Burp version string (e.g. `Burp Suite Professional 2026.1.2` or `Burp Suite Community Edition 2026.x`) for both Community and Professional editions.

---

## [v2.7.0] - 2026-05-21

### 🐛 Bug Fixes

- **Correct Burp edition on Discord**: Idle status and the large-image tooltip now reflect your actual Burp edition (Community, Professional, or Enterprise) instead of always saying "Burp Suite Professional".
- **Stuck scanner status**: Scanner presence no longer stays on "Scanning Targets" forever after passive scan findings. It now expires 60 seconds after the last new issue, matching Repeater/Intruder behavior.
- **Status rotation restored**: When multiple tools are active, Discord presence now rotates through them on each update interval instead of freezing on the highest-priority provider.

---

## [v2.6.0] - 2026-03-22

### ✨ What's new

- **Settings moved into Burp** — Open **Burp Suite → Settings** (gear icon) and search for **Burpcord** or **Discord**. All options, the log viewer, and Help/About are there. The old top-level **Burpcord** window tab is gone, so configuration matches how other extensions are expected to live in Burp.
- **Site map status without slowing huge projects** — Your Discord line for site map now leans on **unique URLs seen through the proxy** (capped so memory stays predictable). Occasionally, Burpcord still updates the **full** site map total in the **background** so you can see an exact count when that refresh is recent. You may see wording like “unique URLs (proxy)” vs “site map items” depending on what’s freshest.

---

## [v2.5.4] - 2026-02-13

### 🐛 Bug Fixes

- **Shutdown After Settings Save**: Fixed an issue where the extension could not cleanly shut down after saving connection settings. The shutdown guard was not being reset, causing the cleanup to silently skip on exit.
- **Scheduler Leak on Settings Save**: Fixed a resource leak where saving connection settings created an orphaned background thread that was never cleaned up.
- **Reload Button Shutdown**: Fixed the same shutdown guard issue in the "Reload Discord RPC" button.

---

## [v2.5.3] - 2026-02-13

### 🧹 Code Cleanup

- **Modernized Settings Panel**: Refactored feature toggle checkboxes to use a data-driven pattern, reducing boilerplate and making it easier to add new features.
- **Fixed Reset Defaults**: The "Reset All Settings" button was using an incorrect App ID. It now correctly resets to the official Burpcord default.
- **Exposed Config Defaults**: Default values for App ID and update interval are now shared constants, preventing future mismatches.

---

## [v2.5.2] - 2026-02-13

### 🔧 Improvements

- **Updated Discord Library**: Upgraded to DiscordIPC 0.11.3, which includes upstream fixes for two crash bugs we reported. Connection failures when Discord is slow to start and presence update crashes are now handled by the library itself.
- **Cleaner Codebase**: Removed several workarounds that were previously needed to avoid crashes in the Discord library. The extension is now simpler and more maintainable.
- **Simplified Connection Logic**: Reduced connection retries from 5 to 3 with shorter delays, since the main reason for aggressive retries has been resolved upstream.

---

## [v2.5.1] - 2026-02-10

### 🐛 Bug Fixes

- **Feature Log Completeness**: The "Enabled Features" log now correctly shows all 9 features, including Site Map, Scope, Collaborator, and WebSocket (previously only listed 5).

---

## [v2.5.0] - 2026-02-10

### ✨ Enhancements

- **Real Collaborator Tracking**: The Collaborator feature now uses the real Montoya Collaborator API to track DNS, HTTP, and SMTP interactions with live counts and type breakdowns. Gracefully disables on Community Edition.
- **Real Scope Tracking**: The Scope feature now tracks actual scope modifications in real-time, showing how many times the target scope has been changed during your session.

---

## [v2.4.1] - 2026-02-10

### 🧹 Code Cleanup

- **Removed Hardcoded Version Tags**: Version information is now managed in a single place, eliminating the need to manually update 15+ files on every release.

---

## [v2.4.0] - 2026-02-10

### 🐛 Bug Fixes

- **RPC Persists After Close**: Fixed a critical issue where Discord Rich Presence continued displaying on the user's profile after Burp Suite was closed. The extension now explicitly clears the Discord activity before closing the IPC connection.

### 🔧 Improvements

- **JVM Shutdown Hook**: Added a JVM shutdown hook as a safety net to ensure the Discord presence is always cleared, even during abnormal application exits.
- **Idempotent Shutdown**: Shutdown logic is now thread-safe and idempotent — safe to call from both the extension unload handler and the JVM shutdown hook without double-execution.

### 🧹 Code Cleanup

- **Removed Dead Code**: Removed unused `isConnected` field from `DiscordRPCManager` that was being set but never read.

---

## [v2.3.0] - 2026-02-09

### 🐛 Bug Fixes

- **Default App ID**: Fixed an incorrect default Discord Application ID that caused the extension to silently fail on first launch.
- **Presence Crash**: Fixed a crash that occurred when sending presence updates after upgrading to the latest Discord IPC library.
- **Presence Update Safety**: Presence updates no longer crash the status scheduler if an unexpected error occurs.

### 🔧 Improvements

- **App ID Validation**: Invalid or unregistered App IDs now show a clear error message instead of silently hanging.
- **Connect Timeout**: Connection attempts now time out after 10 seconds instead of hanging indefinitely if Discord is unresponsive.
- **Retry Logic**: Increased connection retries from 3 to 5 with smarter backoff delays for more resilience on slower machines.

---

## [v2.2.1] - 2026-02-09

### 🐛 Bug Fixes

- **Extension Loading**: Fixed an issue where the extension would fail to load with a missing class error after installation.
- **Character Encoding**: Fixed a warning on Windows caused by special characters in the source code.

### 🔧 Improvements

- **Build System**: Simplified the publishing process — GPG signing is now optional for contributors.

---

## [v2.2.0] - 2026-02-09

### 🐛 Bug Fixes

- **Connection Crash**: Fixed a crash when connecting to Discord before it was fully loaded. The extension now retries automatically with increasing delays.
- **Clean Disconnect**: Fixed an issue where Discord's recent update broke the ability to cleanly disconnect the presence.

### 🔧 Improvements

- **Updated Discord Library**: Upgraded to the latest Discord IPC library with better compatibility and new features.
- **Connection Resilience**: The extension now connects on a background thread with automatic retries, so it no longer blocks Burp Suite from loading if Discord is slow to start.

---

## [v2.1.0] - 2026-01-28

### 🔧 Improvements

- **Smarter Status Priority**: Status updates now follow a clear priority order — the most relevant activity is always shown first.
- **Cleaner Architecture**: Refactored how activity tracking components are registered, making the extension more reliable and easier to extend.

### 🐛 Bug Fixes

- **Extension Loading Crash**: Fixed an issue that prevented the extension from loading in some environments.
- **RPC Stability**: Fixed a bug where the Discord connection would immediately disconnect after connecting.
- **Status Indicator**: Fixed the UI status indicator sometimes showing "Disconnected" even when connected.

---

## [v2.0.0] - 2026-01-28

### ✨ New Features

- **Redesigned Settings UI**: The settings tab is now split into four clean panels — Settings, Log, About, and Help.
- **Site Map Tracking**: Displays how many endpoints have been mapped in your project.
- **Scope Tracking**: Shows the number of unique targets in scope.
- **Collaborator Tracking**: Displays Out-of-Band (OOB) interaction hits (Pro only).

### 🔧 Improvements

- **Improved Logging**: New startup banner and cleaner console output formatting.
- **Documentation**: Comprehensive Javadocs across the entire codebase.

---

## [v1.5.2] - 2026-01-27

### ✨ New Features

- **Global RPC Switch**: Added a master toggle to enable/disable Discord Rich Presence without unloading the extension.
- **Feature Toggles**: Choose exactly which tools appear in your Discord status.

### 🐛 Bug Fixes

- **Clean Disconnect**: Fixed the Discord connection not closing properly when unloading the extension.

---

## [v1.4.0] - 2026-01-27

### ⚡ Performance

- **Site Map Caching**: Large projects no longer cause UI freezes when querying site map size.

---

## [v1.3.2] - 2026-01-27

### 🎨 UI & Logging

- **Improved Logs**: Added more context and detail to the built-in log viewer.
- **UI Polish**: Improved overall styling and log formatting.

---

## [v1.3.1] - 2026-01-27

### 🐛 Bug Fixes

- **Presence Cleanup**: Fixed an issue where the Discord status wasn't cleared properly on disconnect.

---

## [v1.3.0] - 2026-01-27

### ✨ New Features

- **Expanded Tracking**: Now shows Burp Version, Edition, Scope stats, Proxy history, and Site Map size on your Discord profile.
- **WebSocket Support**: Monitor WebSocket traffic activity in your Discord status.
- **Collaborator**: Initial support for tracking Collaborator interactions.

---

## [v1.2.0] - 2026-01-27

### ✨ New Features

- **RPC Toggle**: Enable or disable Discord RPC without unloading the extension.
- **Intruder Tracking**: See your Intruder attacks (fuzzing/brute-forcing) in your Discord status.
- **Custom State**: Set your own status message (e.g., "Bug Bounty Hunting").

---

## [v1.1.0] - 2026-01-27

### 🔧 Improvements

- **Better Popups**: Dialog windows now appear on the correct monitor in multi-display setups.
- **Stability**: Improved background thread error handling to prevent silent crashes.
- **License**: Added MIT license.

---

## [v1.0.0] - 2026-01-05

### 🎉 Initial Release

- **Discord Rich Presence** for Burp Suite — show your security testing activity on your Discord profile.
- **Activity Tracking**: Proxy, Scanner, Repeater, and Intruder activity displayed in real-time.
- **Settings Tab**: Configure your App ID, update interval, and preferences directly in Burp Suite.
- **Persistent Settings**: Your configuration is saved between sessions.

---

## 📋 Detailed Release Notes

Looking for more technical detail? Check out the full [Release Notes](docs/release_notes.md) for in-depth implementation details, internal changes, and developer-oriented context for each release.
