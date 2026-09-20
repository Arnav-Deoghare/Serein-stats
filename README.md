# Serein Stats

A native Android screen-time and usage-analytics app, built with Kotlin and Jetpack Compose. Serein reads on-device usage stats to show daily/weekly/monthly/lifetime app usage, session and unlock counts, hourly usage heatmaps, and trends — and persists a local snapshot every day so history survives Android's own usage-data pruning.

Serein is designed as a companion to **Zen Launcher**, syncing app-limit state via broadcast intents.

All usage data stays on your device — no accounts, no servers. See [PRIVACY.md](PRIVACY.md).

## Install

Serein isn't on the Play Store yet — grab the signed APK directly from [Releases](https://github.com/Arnav-Deoghare/Serein-stats/releases/latest):

1. Download `app-release.apk` from the latest release
2. Android will likely block the install at first since it's from outside the Play Store — when prompted, go to **Settings → allow installs from this source** (the exact wording depends on whether you downloaded it via Chrome, Files, etc.) and try installing again
3. Open Serein — it'll ask for the **Usage Access** permission, since that's how it reads app usage stats. Grant it under **Settings → Usage access → Serein**, then return to the app
4. That's it — Serein starts tracking from that point on

Every release is signed with the same key, so future updates install cleanly over old ones without needing to uninstall first.

## Features

- **Today / Stats / Trends / Heat / All Apps** tabs covering usage from a single day up to lifetime totals
- Per-app detail screens: session count, average/longest session, unlock count, hourly breakdown
- 30-day trend charts and day-by-day drill-down
- Local Room database snapshot of daily usage, so numbers remain available even after Android discards its own `UsageStatsManager` history
- Four built-in color schemes (Dark Tan, Ink on Paper, Slate Blue, Forest)
- Broadcast receiver for `com.zen.launcher.LIMIT_UPDATE`, to sync app-limit state with Zen Launcher

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Room (persistence)
- `UsageStatsManager` / `UsageEvents` for on-device usage data
- Gradle (Kotlin DSL), KSP for Room's annotation processing

## Requirements

- Android 8.0 (API 26) or newer
- The **Usage Access** permission (`PACKAGE_USAGE_STATS`), granted manually from Settings — Serein prompts for this on first launch
- JDK 17 and the Android SDK to build

## Building

```bash
git clone https://github.com/Arnav-Deoghare/Serein-stats.git
cd Serein-stats
./gradlew installDebug
```

On first launch, Serein will ask you to grant Usage Access under **Settings → Usage access**. Enable it there, then return to the app.

## Project structure

```
app/src/main/kotlin/com/serein/stats/
├── ui/         MainActivity + all Compose screens/tabs, theming, the UsageHelper
│               that reads UsageStatsManager/UsageEvents into app-level models
├── data/       Room database: daily usage snapshots and app limits
└── receiver/   ZenBroadcastReceiver — syncs limit state from Zen Launcher
```

## CI

`.github/workflows/release.yml` builds a signed release APK and publishes it as a GitHub Release whenever a version tag (`v*`) is pushed.

## License

Not yet specified.