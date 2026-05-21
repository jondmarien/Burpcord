# Burpcord - Discord Rich Presence for Burp Suite

[![GitHub Package](https://img.shields.io/badge/GitHub-Packages-blue)](https://github.com/jondmarien/Burpcord/packages)
[![Version](https://img.shields.io/badge/v2.7.1-blue.svg)](https://github.com/jondmarien/Burpcord/releases/tag/v2.7.1)
[![Changelog](https://img.shields.io/badge/Changelog-View-purple)](CHANGELOG.md)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**Burpcord v2.7.1** is a Burp Suite extension that integrates Discord Rich Presence, displaying your real-time security testing activity on your Discord profile in real-time, built with a robust, modular architecture and resilient IPC connection handling. Whether you're intercepting traffic, running scans, fuzzing with Intruder, or testing in Repeater, Burpcord keeps your Discord status updated automatically. Features include customizable status toggles, configurable update intervals, custom state text, site map and scope tracking, Collaborator integration, and configuration plus live logging inside Burp’s **Settings** dialog. Built with the Montoya API and Java 21.

## ✨ Look & Feel

#### Proxy Example
<img width="284" height="120" alt="image" src="https://github.com/user-attachments/assets/3e824a7a-d44d-42a4-939b-3267588256a3" />

#### Collaborator Example
<img width="554" height="247" alt="image" src="https://github.com/user-attachments/assets/db9edb45-3007-45fe-9799-8cd27b1d960a" />


## 🚀 Features

### Core Activity Tracking (Real-Time)

- **Intercept**: Shows when you are actively intercepting traffic (Requests captured).
- **Scanner**: Displays active/passive scan status, issue counts, and severity (High/Medium).
- **Proxy**: Tracks request history count directly from the HTTP Proxy.
- **Repeater**: Indicates manual testing sessions.
- **Intruder**: Displays active active attacks and request counts.

### Advanced Metrics

- **Burp Version**: Displays edition & version (e.g., "Burp Suite Professional 2026.1.2").
- **Project Stats**:
  - **Site Map**: Bounded unique URLs seen via Proxy (with periodic full site map reconciliation on a long interval).
  - **Scope**: Counts unique in-scope targets.
  - **WebSockets**: Monitors active WebSocket message flow.
- **Burp Collaborator**: (Pro Only) Real-time tracking of Out-of-Band (OOB) interactions.

### User Experience

- **Smart Status System**:
  - **Priority Queue**: Intelligently rotates status based on what you are *actually* doing (e.g., Intercept > Scanning > Idle).
  - **Custom States**: Set your own status message (e.g., "Bug Bounty Hunting").
- **Robust Connection**:
  - **Retry with Backoff**: 3 automatic retries with capped exponential backoff (3s → 15s) on startup.
  - **App ID Validation**: Invalid or unregistered App IDs fail fast with a clear error message.
  - **Auto-Reconnect**: Built-in "Reload RPC" button to fix connection issues instantly.
  - **Status Indicator**: Visual feedback in the UI showing connection state (Connected/Disconnected).
  - **Clean Shutdown**: Presence is explicitly cleared on exit — no ghost activity on your Discord profile after closing Burp Suite.
- **Embedded Logging**:
  - **Dual Logging**: Logs to the **Burpcord** log panel (inside Burp **Settings**) and Burp's native **Output** tab.
  - **Verbose Debugging**: Detailed connection events and error traces.

## 🛠️ Installation

1. **Download**: Get the latest `Burpcord-2.7.1.jar` from the [Releases](https://github.com/jondmarien/burpcord/releases) page.
2. **Load in Burp Suite**:
   - Go to **Extensions** → **Installed**.
   - Click **Add**.
   - Select **Extension type**: Java.
   - Select the downloaded `.jar` file.
3. **Verify**: Open **Settings** (gear) and find **Burpcord** (or search for “Burpcord” / “Discord”). The status bar at the top of the panel should turn **Green** (“Connected to Discord”).

## ⚙️ Configuration

Open **Burp Suite → Settings**, then open the **Burpcord** extension panel (search works):

### General

- **Discord App ID**: Your unique Discord Application ID (Defaults to the official Burpcord ID).
- **Update Interval**: How often the status refreshes (Default: 30s).
- **Custom State Text**: Your personal tagline.
- **Reload RPC**: Use this button if your status gets stuck or Discord restarts.

### Feature Toggles

Enable or disable specific tracking modules:

- [x] Show Intercept
- [x] Show Scan Status
- [x] Show Proxy History
- [x] Show Repeater
- [x] Show Intruder
- [x] Show Site Map / Scope
- [x] Show Collaborator Hits (Pro)
- [x] Show WebSockets

## 🏗️ Building from Source

**Prerequisites**:

- JDK 21+
- Gradle 8.0+

Burpcord v2.7.1 uses the **ShadowJar** plugin to bundle dependencies and prevent runtime conflicts with Burp Suite.

```bash
git clone https://github.com/jondmarien/burpcord.git
cd burpcord

# Build the Fat JAR
./gradlew clean shadowJar
```

The compiled artifact will be located at:
`build/libs/Burpcord-2.7.1.jar`

## 🔧 Troubleshooting

- **"Disconnected" Status**: Click the **Reload Discord RPC** button in the top right.
- **Connection Retries**: The extension retries 3 times with increasing delays. If all fail, check the log for hints.
- **Invalid App ID**: If you see a 404 error, verify your Discord App ID in Settings or reset to the default.
- **No Status on Discord**:
  - Check **User Settings** → **Activity Privacy** → "Display current activity as a status message".
  - Ensure no other RPC apps are conflicting.
- **Logs**: Check the **Burpcord** panel’s **Log** tab (under **Settings**) or Burp's **Extensions** → **Output** tab for errors.

## 📝 License

MIT
