# PipeTV

PipeTV is a fork of [PipePipe](https://github.com/InfinityLoop1308/PipePipe)
made for TVs: it opens straight to its content, shows clearly where the focus
is, plays videos in fullscreen and answers the remote the way TV players do.
It installs next to PipePipe (id `io.github.felipebueno.pipetv`) and keeps
PipePipe's GPLv3 license.

**First changes (2026-10):**

- No changelog, donation, upstream announcement or update-checker dialogs on
  start, and no announcement fetch slowing it down.
- A rounded focus ring (`FocusOverlayView`) that shows on white lists, the
  red toolbar and the black player alike.
- Videos open in fullscreen on TVs (the setting still exists; it defaults to
  on through `values-television`).
- In the player, Left and Right seek right away and hand the focus to the seek
  bar, so holding them keeps seeking.
- PipeTV name and banner.

**Second round (2026-10):**

- Choosing a video goes straight to the fullscreen player: a black loading
  cover hides the details page while the stream loads.
- Back in the player returns to the list, on the video it was opened from.
- Down in the fullscreen player opens a row of related videos over the video
  (`TvRelatedRow`): OK plays one, Up goes back to the controls, Back closes it.
- YouTube links open straight in the player on TVs, without the chooser.
- No focus ring left over the video once the player controls fade out, and a
  thinner, softer ring.

**Third round (2026-10):**

- As in the TV apps of the big video sites, the player has no way out of
  fullscreen on TVs (Back closes it), and no touch lock button.
- Faster video starts on TVs: a link opens the player page right away instead
  of after its stream loaded, and the YouTube decoder no longer warms up at
  start, which took 10 to 25 s of a TV stick's CPU right as the first video
  started.
- Faster related videos: the extractor compiled a regular expression per
  phrase for every date of the list, seconds per video on a TV stick; it now
  compiles each once (`patches/PipePipeExtractor/`).
- A stream already loaded once autoplays again after the player was closed.
- The running version, build and channel (dev or R8) in the bottom right
  corner.

Measured on a Mi TV Stick (1 GB, Android 9):

| | debug | R8 before | R8 now | R8 now, fully compiled |
|---|---|---|---|---|
| Cold start to the first screen | 17 s | 2.6 s | 2.7 s | 2.2 s |
| YouTube link, app closed, to playing | 45 s | 20 s | 17.5 s | 12 s |
| YouTube link, app open, to playing | | didn't play | 8 s | 7 s |

## Building

The app lives in `PipePipeClient/` (a squashed git subtree of upstream's
client); the extractor is upstream's submodule, with PipeTV's fixes to it in
`patches/PipePipeExtractor/`. The code targets Java 25.

```sh
git submodule update --init PipePipeExtractor
git -C PipePipeExtractor apply ../patches/PipePipeExtractor/*.patch
cd PipePipeClient
JAVA_HOME=/path/to/jdk-25 ./gradlew assembleDebug
```

APKs come per ABI; 32-bit TV sticks need `armeabi-v7a`.

For the TV, use the optimized build: `./gradlew assembleMinified` makes the
release build (R8) signed with the debug key, which installs next to the
debug one (`.minified`). The debug build runs without ahead-of-time
compilation and is several times slower on a TV stick.

After `adb install`, Android compiles only the app's start-up code ahead of
time, and the rest when the device is idle. To get there right away (34 s on a
Mi TV Stick, and videos start about 5 s sooner):

```sh
adb shell cmd package compile -m speed -f io.github.felipebueno.pipetv.minified
```

## Following upstream

`upstream` is PipePipe and `upstream-client` its client repository. Watch them
and bring over what's good for TVs:

```sh
git fetch upstream-client
git log --oneline upstream-client/dev          # what's new upstream
git cherry-pick -X subtree=PipePipeClient <commit>
git subtree pull --prefix=PipePipeClient upstream-client <tag> --squash   # a whole release
```

Upstream's README follows.

<hr>
<p align="center"><img src="assets/logo.png" width="150"></p> 
<h2 align="center"><b>PipePipe</b></h2>
<h4 align="center">
NewPipe, reimagined: faster, more stable, and packed with more features.</h4>
<p align="center"><a href="https://f-droid.org/packages/InfinityLoop1309.NewPipeEnhanced/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid"  width="207" /></a>
<a href="https://apt.izzysoft.de/fdroid/index/apk/InfinityLoop1309.NewPipeEnhanced"><img src="assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" width="207" /></a></p>
<hr>

## Beyond NewPipe

#### YouTube Enhancements
* Integrate SponsorBlock for skipping sponsored segments (YouTube & BiliBili) 
* Restore YouTube dislikes with ReturnYouTubeDislike 
* Show original titles on YouTube (non-localized) 
* Log in to access restricted or premium content 

#### Media Features
* Display live chats in danmaku-style overlays
* Support AV1 and VP9 codecs for efficient, high-quality playback 
* Enable music player mode with background playback 

#### Filtering
* Apply advanced search filters for better discovery 
* Filter out unwanted items by keywords or channels 
* Block shorts and paid videos for a cleaner feed 

#### Playback Controls
* Use swipe-to-seek and fullscreen gestures for intuitive navigation 
* Long-press to speed up playback 
* Set a sleep timer for bedtime listening 

#### Enhanced Playlists
* Download full playlists at once 
* Search and sort within local playlists and histories

... and many more improvements!


## Screenshots

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/00-v2.png" width=640>](fastlane/metadata/android/en-US/images/phoneScreenshots/00-v1.png)

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/01-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/02-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/03-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/04-v3.png)
<br/>
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/05-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/06-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/06-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/07-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/07-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/08-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/08-v3.png)
<br/>
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/09-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/09-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/10-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/11-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/11-v3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/12-v3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/12-v3.png)


## About this fork

Due to differences in development philosophy, I forked NewPipe in early 2022 and began independent development based on it.

This means that PipePipe neither receives updates from NewPipe nor pushes updates to NewPipe. They have become two separate projects. Issues that occur in NewPipe don't necessarily happen in PipePipe, and changes made in NewPipe may not be adopted by PipePipe. In contrast, forks like Tubular track the latest version of NewPipe and develop based on it.

Making a hard fork allows us to effectively address issues with quick fixes and maintain frequent feature updates.

## About sign in

PipePipe will ONLY use the login cookie for the specified scenarios you set. You can configure it in "Cookie Functions."

For YouTube, the cookie will only be used when retrieving playback streams.

## Contribute

Issues and PRs are welcomed. Please note that I will **NOT** accept service requests. 

Anyone interested in creating their own service is encouraged to fork this repository.

## Donation

If you find PipePipe useful, please consider becoming a supporter on Ko-Fi. Your support is important to me and helps me add more exciting new features. Every bit counts! 😇

Liberapay: https://liberapay.com/PipePipe

Ko-fi: https://ko-fi.com/pipepipe

## Community

[PipePipe Wiki](https://priveetee.github.io/Docs-PipePipe) maintained by [@Priveetee](https://github.com/Priveetee)

## Special Thanks

[Priveetee](https://github.com/Priveetee) for [researching SABR](https://priveetee.github.io/Docs-PipePipe/developer-guide/introduction.html) and implementing support for it.

[AioiLight](https://github.com/AioiLight) for providing some code of NicoNico service.
