# Burpcord Release Notes

Developer-oriented notes: internal behavior, APIs, filenames, and rationale. User-facing summaries live in [`CHANGELOG.md`](../CHANGELOG.md).

## [v2.7.1] - 2026-05-21

### Hover tooltip version string

- **`DiscordRPCManager`**: `setLargeImageWithTooltip` uses `burpSuiteInfo.fullVersionString()` so hover shows edition + version (e.g. `Burp Suite Professional 2026.1.2`), not edition name alone.

---

## [v2.7.0] - 2026-05-21

### Edition-aware presence

- **Added** `BurpSuiteInfo` — snapshot from `api.burpSuite().version()` at startup (`name()`, `toString()`, `edition()`).
- **`DiscordRPCManager`**: idle fallback details use `burpSuiteInfo.fullVersionString()`; large-image tooltip uses edition name via `productName()` (superseded in v2.7.1).
- **`BurpcordExtension`**: logs detected Burp version on load.

### Status rotation

- **`DiscordRPCManager.updatePresence()`**: collects all active `ActivityProvider`s (already sorted by priority), round-robins with `rotationIndex` each scheduler tick, resets index on `shutdown()` / `reloadRPC()`.
- Replaces prior `break`-on-first-active behavior that prevented documented rotation.

### Scanner TTL

- **`BurpcordScannerListener`**: `lastScanActivityTime` + `SCAN_ACTIVITY_TTL_MS = 60_000`; `isActive()` no longer keyed on permanent `issueCount > 0`. Issue count still shown while provider is active.

---

## [v2.6.0] - 2026-03-22

### Settings UI — `registerSettingsPanel`

- **Removed** `BurpcordExtension` → `UserInterface.registerSuiteTab("Burpcord", …)`.
- **Added** `BurpcordBurpSettingsPanel` implementing `burp.api.montoya.ui.settings.SettingsPanel` (FQCN in source to avoid clashing with the Swing form class). Wraps existing `BurpcordSettingsTab` root; `keywords()` for Settings search (`Burpcord`, `Discord`, `Rich Presence`, `RPC`, …).
- **`BurpcordExtension`**: stores `Registration` from `registerSettingsPanel`, calls `deregister()` in `extensionUnloaded()` alongside `BurpcordSiteMapProvider.shutdown()`.
- **Persistence**: unchanged — still `BurpcordConfig` / Montoya `Preferences`; no `SettingsPanelBuilder` duplicate store.
- **BApp / review**: aligns with reviewer request to use main Settings instead of a suite tab; see [`docs/bapp-store-reviewer-response.md`](bapp-store-reviewer-response.md).

### Site map — `BurpcordSiteMapProvider`

- **Problem**: Prior code refreshed `api.siteMap().requestResponses().size()` on a ~60s TTL from the Discord scheduler path, materializing the full list (BApp criterion 9 / large projects).
- **Primary path**: Same class implements `ProxyRequestHandler` + `BurpComponent`; `handleRequestReceived` records `interceptedRequest.url()` into `ConcurrentHashMap.newKeySet()` with **`UNIQUE_URL_CAP = 8192`**; `isActive()` / `updatePresence()` read only that bounded set (no `requestResponses()` on hot path).
- **Fallback / true count**: `ScheduledExecutorService` thread **`Burpcord-SiteMap-TrueCount`**, `scheduleWithFixedDelay`: initial delay **`45` seconds**, period **`30` minutes** (in seconds). `refreshTrueSiteMapCount()` → `requestResponses().size()`; failures → `api.logging().logToError(...)`.
- **Display**: If `lastTrueCountMs` within **`TRUE_COUNT_DISPLAY_STALE_MS`** (34 min), prefer `trueSiteMapCount` string; else proxy estimate (`~`, `≥8192`, or stale full-count suffix).
- **Lifecycle**: `shutdown()` deregisters proxy handler and `shutdownNow()` the executor.
- **Montoya gap**: no `SiteMap` size API without list materialization; upstream issue template in [`portswigger/montoya-upstream-site-map-count.md`](portswigger/montoya-upstream-site-map-count.md).

### Refactor

- **Renamed** `tech.chron0.burpcord.ui.SettingsPanel` → **`BurpcordConfigurationForm`** (`JPanel`) to disambiguate from `burp.api.montoya.ui.settings.SettingsPanel`.

---

## [v2.5.4] - 2026-02-13

### 🐛 Bug Fixes

- **`saveConnectionSettings()` shutdownCalled leak**: The method called `rpcManager.shutdown()` then `rpcManager.restartScheduler()` then `rpcManager.initialize()` manually. `shutdown()` sets `shutdownCalled` to `true` via `compareAndSet`, but the flag was never reset — so all subsequent `shutdown()` calls (extension unload, JVM hook) would no-op, leaving the Discord presence stuck on the user's profile. Additionally, `restartScheduler()` created a new `ScheduledExecutorService` before `initialize()` reconnected, leaking an orphaned scheduler thread. Fixed by replacing the 3-line sequence with `rpcManager.reloadRPC()`, which properly resets the flag.
- **Reload button same issue**: `BurpcordSettingsTab.createStatusBar()` called `rpcManager.shutdown()` then `rpcManager.initialize()` without resetting `shutdownCalled`. Same fix — replaced with `rpcManager.reloadRPC()`.

---

## [v2.5.3] - 2026-02-13

### 🧹 Code Cleanup

- **Data-Driven Feature Toggles**: Introduced a `FeatureToggle` record (Java 21) in `SettingsPanel` that declaratively binds each checkbox's label, tooltip, log name, config getter (`BooleanSupplier`), and config setter (`Consumer<Boolean>`). The old 9 individual `JCheckBox` fields and repetitive `addActionListener` wiring blocks were replaced with two `List<FeatureToggle>` definitions and a single generic `createFeaturePanel()` method. Reduced `SettingsPanel.java` from 345 to 298 lines (-47 lines).
- **Fixed Reset App ID Bug**: `resetSettings()` hardcoded `"1457789738753965206"` but `BurpcordConfig.DEFAULT_APP_ID` is `"1457789708753965206"` (digit `738` vs `708`). Reset was sending users to a non-existent Discord application. Now references `BurpcordConfig.DEFAULT_APP_ID` and `BurpcordConfig.DEFAULT_UPDATE_INTERVAL` directly.
- **Public Config Defaults**: Changed `DEFAULT_APP_ID` and `DEFAULT_UPDATE_INTERVAL` in `BurpcordConfig` from `private` to `public` so `SettingsPanel` (and any future consumer) can reference them instead of hardcoding values.

---

## [v2.5.2] - 2026-02-13

### 🔧 Improvements

- **DiscordIPC 0.11.2 → 0.11.3**: Upgraded to the version that includes CDAGaming's upstream merge of our null-safety fixes ([commit `3e07425`](https://github.com/CDAGaming/DiscordIPC/commit/3e07425)). The two bugs we reported — null `data` in `Pipe.openPipe()` handshake and missing `StatusDisplayType` default in `RichPresence.Builder` — are now resolved in the library itself.
- **Removed `connectWithTimeout()`**: The `CompletableFuture`-based timeout wrapper around `client.connect()` was a downstream workaround for the null handshake data bug causing indefinite blocking. With the upstream fix, `Pipe.openPipe()` now throws a descriptive `IOException` and tries the next pipe index, so the timeout wrapper is no longer needed. Removed along with the `CONNECT_TIMEOUT_MS` constant.
- **Removed `isHandshakeNullDataError()`**: This method detected the specific `NullPointerException` from the library's `Pipe.openPipe()` when `data` was null. The upstream fix converts this to a proper `IOException`, so the NPE detection heuristic is obsolete.
- **Removed `StatusDisplayType`/`ActivityType` workaround**: The explicit `builder.setActivityType(ActivityType.Playing)` and `builder.setStatusDisplayType(StatusDisplayType.Name)` calls were required to prevent an NPE in `RichPresence.toJson()` because the `Builder` class had no defaults. CDAGaming resolved this by adding defaults on the `Builder` class. Removed the workaround and the now-unused `ActivityType`/`StatusDisplayType` imports.
- **Simplified Retry Logic**: Reduced `MAX_CONNECT_RETRIES` from 5 → 3 and `MAX_RETRY_DELAY_MS` from 30s → 15s. The aggressive retry count was primarily needed to handle the null-data race condition on slower machines. With the upstream fix, 3 retries with 3s–15s backoff is sufficient for the remaining "Discord not started yet" scenario.

---

## [v2.5.1] - 2026-02-10

### 🐛 Bug Fixes

- **Feature Log Completeness**: `logEnabledFeatures()` in `BurpcordExtension.java` now includes `isShowSiteMap()`, `isShowScope()`, `isShowCollaborator()`, and `isShowWebSockets()` checks. Previously only 5 of 9 features were logged.

---

## [v2.5.0] - 2026-02-10

### ✨ Enhancements

- **Real Collaborator API Integration**: Rewrote `BurpcordCollaboratorProvider` to use the Montoya `CollaboratorClient` API. Creates a client on registration, polls `getAllInteractions()` with 60s cache TTL, shows interaction count with DNS/HTTP/SMTP type breakdown. Throws are caught for Community Edition (Pro-only feature) — provider stays permanently inactive. Implements `BurpComponent` for proper API registration lifecycle.
- **Real Scope API Integration**: Rewrote `BurpcordScopeProvider` to implement `ScopeChangeHandler`. Registers via `api.scope().registerScopeChangeHandler()` for event-driven scope tracking. Counts modifications with `AtomicInteger`. Note: Montoya Scope API has no list/count method — only `isInScope(url)` and change events.

---

## [v2.4.1] - 2026-02-10

### 🧹 Code Cleanup

- **Removed `@version` Javadoc Tags**: Stripped hardcoded `@version` annotations from all 13 Java source files. Version is now single-sourced from `BurpcordConstants.VERSION` (runtime) and `build.gradle` (build-time). The `@version` Javadoc tag is a legacy convention that doesn't auto-update and added unnecessary maintenance burden on every release.

---

## [v2.4.0] - 2026-02-10

### 🐛 Bug Fixes

- **RPC Persists After Close (BApp Store Blocker)**: Discord Rich Presence continued displaying after Burp Suite exited because `client.close()` only closes the IPC pipe — it does not send a clear-activity command. Fixed by calling `client.sendRichPresence(null)` before `client.close()` in `DiscordRPCManager.shutdown()`, which sends `SET_ACTIVITY` with a `null` payload to Discord.

### 🔧 Improvements

- **JVM Shutdown Hook**: Registered `Runtime.getRuntime().addShutdownHook()` in `BurpcordExtension.initialize()` as a safety net for abnormal JVM exits where `extensionUnloaded()` might not fire. The hook is removed on clean unload via `removeShutdownHook()`.
- **Idempotent Shutdown**: `DiscordRPCManager.shutdown()` is guarded by `AtomicBoolean.compareAndSet()`, making it safe to invoke from both `extensionUnloaded()` and the JVM shutdown hook without double-execution. `reloadRPC()` resets the flag for subsequent init cycles.

### 🧹 Code Cleanup

- **Removed `isConnected` Field**: The `volatile boolean isConnected` field in `DiscordRPCManager` was assigned in `onReady`, `onDisconnect`, `onClose`, and `shutdown` but never read anywhere. Removed as dead code. Connection status is already tracked via `client.getStatus() == PipeStatus.CONNECTED` checks and the UI status indicator updated through `BurpcordSettingsTab.updateConnectionStatusStatic()`.

---

## [v2.3.0] - 2026-02-09

### 🐛 Bug Fixes

- **Default App ID**: Fixed incorrect default Discord Application ID (`1328087961230639207` → `1457789708753965206`). The old ID returned HTTP 404, causing silent handshake timeouts.
- **StatusDisplayType NPE**: Fixed `Cannot invoke "StatusDisplayType.ordinal()" because "this.statusDisplayType" is null` crash when sending presence updates. The new `statusDisplayType` field in DiscordIPC 0.11.2 was not being initialized.
- **Presence Update Safety**: Wrapped `sendRichPresence` in try/catch to prevent unhandled exceptions from crashing the scheduler.

### 🔧 Improvements

- **App ID Validation**: Invalid or unregistered App IDs now fail fast with a clear error message and hint instead of silently hanging.
- **Connect Timeout**: Each IPC connect attempt now has a 10-second timeout to prevent indefinite blocking when Discord's pipe is open but unresponsive.
- **Retry Logic**: Increased retries from 3 to 5 with capped exponential backoff (3s → 30s max) for more resilience on slower machines.

---

## [v2.2.1] - 2026-02-09

### 🐛 Bug Fixes

- **Shadow JAR Packaging**: Fixed `ClassNotFoundException: com.jagrosh.discordipc.IPCListener` caused by the plain `jar` task overwriting the fat shadow JAR during publish. Ensured `shadowJar` always runs last.
- **UTF-8 Encoding**: Fixed unmappable character warning on Windows by setting `compileJava` and `javadoc` encoding to UTF-8.

### 🔧 Improvements

- **Build System**: Made GPG signing conditional (only when keys are available), allowing GitHub Packages publish without GPG configuration.

---

## [v2.2.0] - 2026-02-09

### 🐛 Bug Fixes

- **IPC Handshake NPE**: Fixed `Cannot invoke "JsonObject.getAsJsonObject(String)" because "data" is null` crash on connect when Discord is not fully ready. Added retry-with-backoff (3 attempts, exponential delay) on a background thread.
- **IPC Shutdown**: Upgraded DiscordIPC library which re-adds null support for `sendRichPresence` calls, required for clean IPC shutdown due to recent Discord breakage.

### 🔧 Improvements

- **Dependency Upgrade**: Upgraded DiscordIPC from `0.10.2` → `0.11.2` (new Maven group ID `io.github.cdagaming`), picking up updated Gson (2.13.1), SLF4J (2.0.17), new RPC fields (`status_display_type`, `name`, URL support for images/state/details), and null-safe presence cleanup.
- **API Migration**: Migrated all `setLargeImage`/`setSmallImage` calls to new `setLargeImageWithTooltip`/`setSmallImageWithTooltip` API to match DiscordIPC 0.11.x breaking changes.
- **Connection Resilience**: IPC connection now runs on a dedicated daemon thread (`Burpcord-IPC-Connect`) with exponential backoff retries, preventing extension load failures when Discord is slow to initialize.

---

## [v2.1.0] - 2026-01-28

### 🚀 Major Refactoring & Improvements

- **Component Priority System**: Introduced a robust priority system (`getPriority()`) for `ActivityProvider`s from level 10 to 80, ensuring deterministic and logical component registration order.
- **OOP Registration Logic**: Refactored all listeners (`Proxy`, `Scanner`, `Intruder`, `Repeater`, `WebSocket`) to implement a common `BurpComponent` interface, allowing self-contained registration via `register(MontoyaApi)`.
- **Streamlined Initialization**: Rewrote `BurpcordExtension` to use a clean list-based initialization pattern, significantly reducing boilerplate code and improving maintainability.

### 🐛 Bug Fixes

- **Runtime Crash**: Resolved `ClassNotFoundException` wrapper issues by implementing correct ShadowJar configuration with dependency relocation.
- **RPC Stability**: Fixed an issue where invalid `ActivityType` defaults caused immediate RPC disconnections.
- **UI Sync**: Fixed a race condition where the UI connection status indicator would remain "Disconnected" despite a successful RPC connection.

### 🛠 Technical Changes

- **Project Structure**: Organized listener classes into logical `providers` and `handlers` subpackages for better codebase navigation.
- **Initialization**: Decoupled component instantiation from registration logic using the new priority sorting mechanism.

---

## [v2.0.0] - 2026-01-28

### ✨ New Features

- **Modular UI Architecture**: Decomposed the monolithic `BurpcordSettingsTab` into distinct, maintainable panels:
  - `SettingsPanel`: General configuration options.
  - `LogPanel`: Dedicated scrolling log viewer.
  - `AboutPanel`: Version and author information.
  - `HelpPanel`: Support and documentation links.
- **New Activity Providers**: Added support for tracking:
  - **Site Map**: Tracks project site map size with caching performance optimizations.
  - **Scope**: Monitors target scope configuration status.
  - **Collaborator**: Tracks Burp Collaborator interactions.
- **Provider Pattern**: Introduced the `ActivityProvider` architecture to decentralize status logic from the main RPC manager.

### 🔧 Improvements

- **Package Migration**: Migrated root package from `com.burpcord` to `tech.chron0.burpcord` to adhere to standard Java naming conventions.
- **Enhanced Logging**: Implemented a new ASCII art banner start-up log and improved console output formatting.
- **Documentation**: Restored and standardized Javadocs across the entire codebase.

---

## [v1.5.2] - 2026-01-27

### 🚀 New Features

- **Global RPC Switch**: Added a master toggle to enable/disable Discord RPC entirely without unloading the extension.
- **Rich Presence Toggles**: Added granular control to show/hide specific tools in Discord status.

### 🐛 Bug Fixes

- **Connection Lifecycle**: Fixed issues where the Discord IPC connection would not close cleanly upon extension unload.

---

## [v1.4.0] - 2026-01-27

### ⚡ Performance

- **Site Map Caching**: Added caching mechanisms for large project performance, preventing UI freezes when querying site map size.
- **Documentation**: Updated project documentation to include PortSwigger-provided BApp info.

## [v1.3.2] - 2026-01-27

### 🎨 UI & Logging

- **Enhanced Logs**: Added more context to the custom logging section of the extension tab.
- **Styling**: Improved overall UI styling and log formatting.

## [v1.3.1] - 2026-01-27

### 🐛 Bug Fixes

- **RPC Cleanup**: Fixed issues with sending empty presence updates before disconnection.

## [v1.3.0] - 2026-01-27

### ✨ New Features

- **Expanded Tracking**: Added tracking for Burp Version, Edition, Scope stats, Proxy history, and Site Map size.
- **WebSocket Support**: Added support for monitoring WebSocket traffic activity.
- **Collaborator**: Added initial stats tracking for Collaborator.

## [v1.2.0] - 2026-01-27

### 🚀 Features

- **RPC Toggle**: Added the ability to toggle RPC without unloading the extension.
- **Intruder Tracking**: Added support for tracking Intruder attacks (Fuzzing/Brute-forcing).
- **Custom State**: Allowed users to set custom text for their status state.

## [v1.1.0] - 2026-01-27

### 🛠 Technical

- **GUI Handling**: Implemented usage of Burp's GUI Parent Frame for better dialog modal handling.
- **Threading**: Improved background thread exception handling.
- **License**: Added project LICENSE file.

---

## [v1.0.0] - 2026-01-05

### 🎉 Initial Release

- **Core Functionality**:
  - Integration of Discord Rich Presence into Burp Suite.
  - Support for tracking Proxy, Scanner, Repeater, and Intruder activity.
- **Basic UI**: Integrated settings tab within Burp Suite.
- **Configuration**: Persistence for App ID and basic settings.
- **Foundation**: Basic implementation of the Discord IPC connection.
