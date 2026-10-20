# Bettermint

**A better way to browse your Plex library on Android phones and Fire TV.**

If you have a big Plex library, you probably know the feeling. You open the app and see the same Continue Watching and Recently Added rows, and the rest of your collection is buried. Finding something you forgot you owned means scrolling a giant grid or searching for a title you already have to remember.

Bettermint is a free, open-source Plex app being built to fix that. The plan has two parts:

1. **Do everything a good Plex app should do.** Sign-in, browsing, playback, resume, subtitles, skip intro, autoplay: all the basics you'd expect from the official app or the popular third-party ones.
2. **Then add what Plex users have been asking for.** Better ways to dig through a large library, based on the most-requested features on the Plex forums, Reddit and other user communities.

> Bettermint is an independent project. It is not made by, affiliated with, or endorsed by Plex Inc. You need your own Plex Media Server (or access to someone's) to use it.

## Status: early, but working

The basics are built and in daily testing on a Fire TV Stick and an Android phone. There are no ready-made downloads yet; for now you have to build it yourself (see [Building it yourself](#building-it-yourself)). Expect rough edges.

> **Bottom line up front**
> - **What:** a free, open-source Plex app for Android phones and Fire TV.
> - **Why:** big Plex libraries get buried behind the same few home rows.
> - **Now:** the basics work (sign-in, browsing, playback, resume, subtitles, skip intro, autoplay).
> - **Next:** the features Plex users keep asking for: saved filters, real filtering on TV, a random picker and fresh home rows.
> - **Catch:** early stage, no download yet (build it yourself), and you need your own Plex server.

## What works today

**Getting started**
- Sign in with a short code at plex.tv/link, the same as the official TV app
- Finds your servers automatically and picks the fastest connection (home network first, then remote, then Plex's relay)
- Plex Home: switch between users, including PIN-protected profiles

**Browsing**
- Home screen with your Continue Watching, On Deck and Recently Added rows
- Full library grids for Movies and TV Shows that load as you scroll, so large libraries stay smooth
- Search across your libraries
- Show, season and episode pages
- Collections and playlists, plus adding items to a playlist
- Trailers and extras on detail pages
- Fixes ugly filename titles: if a movie shows up as `Coraline.2009.1080p.BluRay...`, Bettermint shows the proper title instead, using the same Plex data that Plex Web uses
- Music is hidden, so the app stays focused on movies and TV (Plexamp already handles music well)

**Watching**
- Plays files directly when your device can handle them, and falls back to Plex's transcoding automatically when it can't
- Picks up where you left off, and keeps your progress in sync with every other Plex app
- Marks things watched when you finish them
- Choose the audio track, subtitles, and the version of a movie (for example 4K or 1080p)
- Quality settings, with separate limits for home and away
- Chapter thumbnails you can jump to
- Skip Intro button (needs Plex Pass on the server)
- Autoplay the next episode, with a countdown you can cancel

**On the TV**
- Built for the remote: every screen opens with something selected, and focus doesn't jump around while you scroll
- Fast and light: it starts in under a second and uses about 106 MB of memory while browsing on a Fire TV Stick 4K

**The look**
- A visual style inspired by Palm's webOS (the Palm Pre's software): content floats on light cards over a wallpaper, with a row of glossy round icons along the bottom

## What's coming next

These are the features Plex users keep asking for that most apps still don't offer. They're the reason this project exists:

- **Saved filters as shortcuts.** Save a combination like "unwatched sci-fi from the 1980s" and open it from the home screen with one click.
- **Proper filtering on the TV.** Combine genre, decade, rating, watched status and more, the way Plex Web's advanced filters work.
- **"Pick something for me."** A random pick that respects your filters, and starts shows at the first episode you haven't seen.
- **Home rows that change.** Fresh rows like "decades", "top rated and unwatched" or "from this studio", rotating so more of your library gets seen.
- **Better Continue Watching control.** Remove things without marking them watched, and hide shows you've given up on.
- **Faster navigation in huge grids.** Jump straight to a letter, and choose how dense the grid is.

The order may change as the basics get polished.

## Devices

| Device | Supported |
|---|---|
| Android phones and tablets (Android 7.1 or newer) | Yes |
| Amazon Fire TV Stick, Cube and Fire TV Edition TVs running Fire OS | Yes |
| Other Android TV / Google TV devices | Should work, but not tested yet |
| Fire TV Stick 4K Select and other devices running Vega OS | No (Vega OS can't run Android apps) |

## Good to know

- **Watching away from home:** Plex now requires a Plex Pass or Remote Watch Pass to stream from outside your home network in third-party apps. Playback on your home network is unaffected.
- **Privacy:** Bettermint talks only to Plex (plex.tv and your own server). There are no ads, no analytics and no tracking.
- **Not included (for now):** downloads for offline viewing, music, Live TV and DVR, and Watch Together.

## Building it yourself

There's no prebuilt download yet. If you're comfortable with developer tools, you'll need:

- JDK 21 and the Android SDK (Android Studio includes both)
- `adb` to install it on your device, with developer options and ADB debugging turned on (on Fire TV: Settings > My Fire TV > Developer options)

Then, from the project folder:

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

For a Fire TV, connect over your network first with `adb connect <your-fire-tv-ip>:5555`.

## For developers

Bettermint is written in Kotlin with Jetpack Compose and Media3 (ExoPlayer). The code is split into three parts:

- `core/api`: a plain Kotlin Plex client (no Android code), tested against recorded server responses
- `core/data`: sign-in storage, library browsing, title fixing and the playback logic
- `app`: the screens and the video player

Tests run at three levels: fast unit tests (`./gradlew :core:api:test :core:data:test`), on-device tests, and hands-on checks on real hardware. Project notes and the full history of decisions are in [docs/memory.md](docs/memory.md).

## License

No license has been chosen yet. Until one is added, all rights are reserved by the author.
