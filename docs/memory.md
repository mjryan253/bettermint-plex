# Project memory — bettermint-plex

Read this at the start of every session. Record every accepted change (code, wiring, docs, config, decisions) here: update the status sections and add a dated change-log entry as part of the same piece of work.

## What this project is

A third-party Plex client for **Android phone/tablet and Amazon Fire TV (Fire OS)**, built because first-party clients are weak at browsing/discovering large libraries. MVP is the full table-stakes client feature set; v2 adds the discovery differentiators (saved filter views, advanced filtering, random picker, smart rows, continue-watching management). Owner has lifetime Plex Pass.

Full approved plan: `~/.claude/plans/https-github-com-morluto-rea-use-the-rea-ticklish-brooks.md` (research findings, milestones M0–M7, risks, API notes).

## Key decisions (2026-10-18)

- **Stack**: Kotlin + Jetpack Compose (+ Compose for TV) + Media3/ExoPlayer. 3 Gradle modules: `:core:api` (pure JVM, Ktor, all Plex protocol), `:core:data` (Android lib, repositories, DataStore), `:app` (single Activity, dual LAUNCHER/LEANBACK_LAUNCHER, ui/mobile + ui/tv + ui/shared split, manual DI via AppGraph).
- **Toolchain**: Kotlin 2.4.21, AGP 9.4.1 (JDK 21), Compose BOM 2026.09.00, tv-material 1.1.0, Media3 1.11.1, Ktor 3.6.0, Coil 3.6.3, ass-media 0.5.1. minSdk 25, targetSdk 36, compileSdk 37.
- **Auth**: classic PIN flow (not the newer JWT flow). All X-Plex-* headers centralized in one Ktor plugin, never empty.
- **MVP scope**: table stakes + promoted set (skip intro/credits, Plex Home switching, multi-version, collections/playlists, quality presets, chapter thumbnails, Watch Next, trailers/extras). Out: downloads, music, Live TV, Watch Together, Trakt, photos, casting.
- **Targets**: Fire OS stick/cube (pre-2026) + Android phone/tablet. Vega OS out of scope.
- **References (study, never copy — GPL)**: jellyfin/jellyfin-androidtv, edde746/plezy; python-plexapi + developer.plex.tv OpenAPI spec for API behavior; REA MCP for HAR/APK analysis when docs fall short.
- **Design**: M0–M6 plain functional defaults; M7 visual design pass guided by `frontend-design-skill.md` + `frontend-design-skill-path.md` (direction approved by user before implementing).

## Environment

- Flatpak sandbox (Freedesktop SDK, no apt/JDK); host is Linux Mint 22.3 reached via `flatpak-spawn --host`.
- Host has adb at `~/Android/platform-tools/adb`; JDK 21 + sdkmanager installed via apt at M0; SDK in `~/Android/Sdk`.
- Builds run on the host: `flatpak-spawn --host bash -lc 'cd ~/Github/bettermint-plex && ./gradlew …'`. Device loop via host adb (`adb connect <firestick-ip>:5555`).
- GitHub: public repo `mjryan253/bettermint-plex`; remote wired but **never push unless directly instructed**. Repo-local git identity: mjryan253 <mjryan253@users.noreply.github.com>.

## Process rules

- Every step gates on `git add` + `git commit` (relevant message, no AI attribution — repo rule). One commit per milestone minimum.
- HITL Gate A after M2 (user tests sign-in + browse on their devices). HITL Gate B after M5 (user runs full playback suite on Fire Stick). M7 design direction needs user approval.

## Status

- **Milestone**: M0 COMPLETE (2026-10-18). Next: M1 (auth + server connect + Plex Home).
- Build verified on host: `./gradlew :core:api:test :app:assembleDebug` green (Gradle 9.8.1 / AGP 9.4.1 / JDK 21.0.12.1, first try).
- Deployed to Fire TV Stick 4K (AFTMM "mantis", Fire OS 6 / API 25 — the minSdk floor device) at 192.168.1.87:5555 via host adb; renders "Bettermint — TV" (screenshot-verified), launched via LEANBACK_LAUNCHER intent.
- **Pending**: mobile form-factor check on a real phone (no phone connected yet); second device at 192.168.1.125:5555 (another Fire TV) still shows "unauthorized" — user hasn't accepted the ADB prompt there.
- GitHub repo created and pushed by user: github.com/mjryan253/bettermint-plex.

### SDK install gotchas (host)
- Ubuntu/Mint's apt `sdkmanager` package is broken — reports installs it never performs (its real SDK lives root-owned at /usr/lib/android-sdk). Do not use.
- Working setup: Google cmdline-tools (commandlinetools-linux-16111833) at `~/Android/Sdk/cmdline-tools/latest`; sdkmanager is deprecated in it — use `android sdk install/list` (new Android CLI).
- **Platform packages now use minor versions**: `platforms;android-37` does not exist; `platforms;android-37.0` is what satisfies compileSdk 37. Installed: platform-tools 37.0.1, build-tools;37.0.0, platforms;android-37.0.

## Change log

- **2026-10-18** — Project planned and approved (research: REA capabilities, top-requested Plex features, client landscape; decisions above). Git repo initialized with repo-local identity; docs/memory.md created.
- **2026-10-18** — M0 scaffold: Gradle 9.8.1 wrapper, version catalog (AGP 9.4.1 / Kotlin 2.4.21 / Compose BOM 2026.09.00 / Media3 1.11.1 / Ktor 3.6.0 — cross-checked against jellyfin-androidtv master and Maven metadata), modules :core:api (pure JVM) / :core:data / :app, AGP 9 built-in Kotlin pattern (kotlin-gradle on buildscript classpath, kotlin.compose plugin in :app), dual LAUNCHER/LEANBACK_LAUNCHER manifest, local.properties → ~/Android/Sdk.
- **2026-10-18** — M0 complete: build verified on host (test + assembleDebug green); hello APK deployed to Fire TV Stick 4K (AFTMM, Fire OS 6) via host adb, TV form factor + leanback launcher confirmed by screenshot. SDK gotchas documented above.
