# AGENTS.md

This file provides guidance to AI agents and WARP (warp.dev) when working with code in this repository.

## Documentation References

- See `@docs/portswigger/CLAUDE.md` for official Montoya API guidance
- See `@docs/portswigger/bapp-store-requirements.md` for BApp Store submission criteria
- See `@docs/portswigger/development-best-practices.md` for development guidelines
- See `@docs/portswigger/montoya-api-examples.md` for API code patterns
- See `@docs/portswigger/resources.md` for external links

## Project Overview

Burpcord is a Burp Suite extension that integrates Discord Rich Presence functionality. It displays real-time Burp Suite activity on Discord profiles, including security testing status, scan results, tool usage, and advanced metrics via the Montoya API. The extension is built using Java 21 and the Burp Suite Montoya API.

**Current Version:** 2.7.1

## Build System

This project uses **Gradle** with Java 21. Key build files:

- `build.gradle` - Main build configuration
- `settings.gradle` - Project settings
- `Makefile` - Windows-specific convenience wrapper for Gradle commands

### Common Commands

**Build the JAR:**

```powershell
.\gradlew.bat jar
```

Or via Makefile:

```powershell
make build
```

The compiled JAR will be located at: `build/libs/Burpcord-X.X.jar`

**Clean build artifacts:**

```powershell
.\gradlew.bat clean
```

Or via Makefile:

```powershell
make clean
```

**Note:** The `gradlew.bat` wrapper script must be used on Windows (not `./gradlew`).

## Architecture

### Extension Entry Point

`BurpcordExtension` (implements `BurpExtension`) is the main entry point:

- Initializes the Discord RPC manager
- Registers handlers for Proxy, Scanner, Repeater, Intruder, and WebSockets
- Registers the Settings UI in Burp’s main Settings dialog (`registerSettingsPanel`)
- Implements proper cleanup via `ExtensionUnloadingHandler` and a JVM shutdown hook

### Core Components

**DiscordRPCManager** - Central coordinator managing:

- Discord IPC connection lifecycle using the DiscordIPC library
- Periodic status updates via `ScheduledExecutorService`
- `ActivityProvider` registry sorted by `getPriority()` (lower = higher priority)
- Round-robin rotation across all active providers on each update tick
- Edition-aware large-image tooltip via `BurpSuiteInfo` from `api.burpSuite().version()` (edition + version string)
- Idempotent shutdown with `AtomicBoolean` guard and explicit presence clearing

**BurpcordConfig** - Configuration persistence layer:

- Uses Burp's `Preferences` API for storage
- Manages Discord App ID, update interval, and feature toggles
- Provides default values when preferences are unset
- v1.3 features: site map, scope, collaborator, websockets toggles

**Event Handlers:**

- `BurpcordProxyHandler` - Tracks proxy requests/responses (30s activity window)
- `BurpcordScannerListener` - Monitors audit issues; active for 60s after last new issue
- `BurpcordRepeaterListener` - Detects Repeater tool activity via `ToolSource`
- `BurpcordIntruderListener` - Tracks Intruder attack requests via `HttpHandler`
- `BurpcordWebSocketListener` - Monitors WebSocket messages via `WebSocketCreatedHandler`

**BurpcordSettingsTab** - Root Swing UI embedded in Burp Settings (`BurpcordBurpSettingsPanel`):

- Reload RPC button for quick reconnection
- App ID and interval configuration
- Custom state text field
- Feature toggles for all status types
- Built-in log viewer with timestamps

**BurpcordConfigurationForm** - Connection and feature-toggle form inside the Settings tab

### Threading Model

- Discord RPC updates run on a single-threaded `ScheduledExecutorService`
- Event handlers execute on Burp's threads (must be fast to avoid blocking)
- All shared state uses `AtomicInteger` and `AtomicBoolean` for thread safety
- Scheduler is properly terminated in `extensionUnloaded()` to prevent resource leaks
- JVM shutdown hook as safety net; `AtomicBoolean` guard prevents double-shutdown
- UI logging uses `SwingUtilities.invokeLater()` for thread-safe updates

### Status Priority Logic

Providers implement `ActivityProvider` with `getPriority()` (lower = higher priority). Registration order in `BurpcordExtension`:

1. **Proxy** (10) - Active when proxy traffic seen in last 30s
2. **Scanner** (20) - Active when a new audit issue arrived in last 60s
3. **Intruder** (30) - Active when Intruder used in last 60s
4. **Repeater** (40) - Active when Repeater used in last 60s
5. **WebSocket** (50) - Active when WebSocket messages in last 30s
6. **Site Map** (60) - Bounded unique URLs via Proxy + periodic background reconciliation
7. **Scope** (70) - Active when scope changes recorded
8. **Collaborator** (80) - Pro only; polls interaction count

When multiple providers are active, `DiscordRPCManager.updatePresence()` round-robins through them each update interval (default 30s).

**Note:** The "Show Intercept" config toggle exists but no intercept `ActivityProvider` is registered yet.

## Dependencies

**Compile-only:**

- `montoya-api:2025.12` - Burp Suite extension API (not bundled in JAR)

**Implementation (bundled):**

- `DiscordIPC:0.11.3` - Discord Rich Presence client library
- `gson:2.10.1` - JSON serialization for Discord IPC

The `jar` task creates a fat JAR with all runtime dependencies using `DuplicatesStrategy.EXCLUDE`.

## BApp Store Compliance

When modifying this extension, ensure compliance with BApp Store acceptance criteria (see `docs/BApp-Store-Acceptance-Criteria.md`):

**Critical requirements:**

- All slow operations must run in background threads (never block Swing EDT)
- Use `Extension.registerUnloadingHandler()` to clean up resources (especially the scheduler)
- Prefer Burp's `Http.issueHttpRequest()` over direct HTTP libraries
- Avoid keeping long-term references to `HttpRequestResponse` objects
- GUI elements should use `SwingUtils.suiteFrame()` as parent
- Extension must handle large projects efficiently

**Threading considerations:**

- Wrap background threads in try/catch and log exceptions to extension error stream
- Burp does not catch exceptions in background threads automatically
- The scheduler must be properly terminated in `extensionUnloaded()`

## Discord Integration

The extension requires a Discord Application ID to function. Users can:

- Use the default App ID (`1457789708753965206`)
- Create their own via Discord Developer Portal for custom branding

**Rich Presence assets:**

- Large image key must be named `burp` in Discord Developer Portal
- Start timestamp persists across status updates for accurate session time tracking

## Testing Notes

There is no automated test suite. Manual testing requires:

1. Loading the JAR in Burp Suite via Extensions → Installed → Add
2. Verifying Discord is running and Game Activity is enabled
3. Testing each feature (Intercept, Scanner, Proxy, Repeater, Intruder) individually
4. Testing v1.3 features (Site Map, Scope, Collaborator, WebSockets)
5. Opening **Settings** and confirming the Burpcord panel appears; settings persist
6. Verifying proper cleanup when unloading the extension
7. Testing the log viewer displays connection events

## Common Development Scenarios

**Adding a new status type:**

1. Create a class implementing `ActivityProvider` and `BurpComponent`
2. Implement `isActive()`, `updatePresence()`, and `getPriority()`
3. Register in `BurpcordExtension.initializeComponents()`
4. Add configuration toggle in `BurpcordConfig` if user-configurable
5. Add checkbox to `BurpcordConfigurationForm`

**Modifying status priority:**
Edit `getPriority()` on the relevant `ActivityProvider` implementation.

**Changing update frequency:**
Users configure this via Settings tab. Default is 30 seconds.

**Debugging Discord IPC issues:**

1. Check the built-in log viewer in the Burpcord Settings panel
2. Check Burp's extension output/error streams
3. The `IPCListener` logs connection events

**Adding log entries:**
Call `BurpcordSettingsTab.log("message")` from anywhere in the codebase.

## File Structure

```tree
src/main/java/tech/chron0/burpcord/
├── core/BurpcordExtension.java       # Entry point, registers all handlers
├── core/BurpSuiteInfo.java            # Burp edition/version snapshot
├── config/BurpcordConfig.java       # Configuration persistence
├── discord/DiscordRPCManager.java     # Core RPC logic and provider rotation
├── discord/ActivityProvider.java    # Provider interface
├── ui/BurpcordSettingsTab.java        # Settings UI and log viewer
├── listeners/handlers/BurpcordProxyHandler.java
├── listeners/BurpcordScannerListener.java
├── listeners/BurpcordRepeaterListener.java
├── listeners/BurpcordIntruderListener.java
└── listeners/BurpcordWebSocketListener.java
```
