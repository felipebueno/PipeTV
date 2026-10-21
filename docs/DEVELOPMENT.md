# Developing PipeTV

## Layout

The app lives in `PipePipeClient/` and the extractor in `PipePipeExtractor/`,
both squashed git subtrees of [PipePipe](https://github.com/InfinityLoop1308/PipePipe)'s
repositories. PipeTV's own changes are marked `PipeTV:` in the code, which keeps
bringing upstream changes over easy. Most of them only apply on TVs
(`DeviceUtils.isTv`), so phones keep PipePipe's behavior.

## Building

The code targets Java 25.

```sh
cd PipePipeClient
JAVA_HOME=/path/to/jdk-25 ./gradlew assembleDebug      # debug build, .debug
JAVA_HOME=/path/to/jdk-25 ./gradlew assembleMinified   # release build (R8) with the debug key, .minified
```

APKs come per ABI; 32-bit TV sticks need `armeabi-v7a`.

Judge speed on the `minified` build: the debug build is debuggable, so Android
doesn't compile it ahead of time, and it is several times slower on a TV stick.
After `adb install`, Android compiles only the app's start-up code ahead of time,
and the rest when the device is idle; to get there right away (34 s on a Mi TV
Stick, and videos start about 5 s sooner):

```sh
adb shell cmd package compile -m speed -f io.github.felipebueno.pipetv.minified
```

The bottom right corner of the home shows the running version, build number
(the commit count, with a `+` for uncommitted changes) and channel: `dev`, `R8`
or `prod`.

## Releases

Every push to `main` runs [`.github/workflows/build.yml`](../.github/workflows/build.yml):
it builds the release APKs, signs them and publishes a GitHub release named
`PipeTV <version> (build <n>)`, tagged `build-<n>`. The release carries the APKs
under stable names (`PipeTV-armeabi-v7a.apk` and so on), `SHA256SUMS`,
`CHANGELOG.md`, and notes made by
[`.github/scripts/release-notes.sh`](../.github/scripts/release-notes.sh): the
version's section of `CHANGELOG.md`, the commits since the previous build,
install steps and checksums.

- The version is `appVersionName` in `PipePipeClient/app/build.gradle`. Move it
  by hand, with a new section in `CHANGELOG.md`, when a version is complete.
- The build number is the commit count, and the version code grows with it, so
  each build installs over the one before.
- The signing key is in the repository's Actions secrets
  (`PIPETV_KEYSTORE_BASE64`, `PIPETV_KEYSTORE_PASSWORD`, `PIPETV_KEY_ALIAS`,
  `PIPETV_KEY_PASSWORD`). Keep a safe copy of it: Android only installs an
  update signed with the same key. A local release build reads it from
  `KEY_PATH`, `KEY_STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

## README screenshots

The screenshots in [`screenshots/`](screenshots) come from an Android TV
emulator. A TV stick's screen captures leave the video out, since its decoder
draws straight to a hardware video plane, and the emulator starts in English,
with US content.

```sh
sdkmanager 'system-images;android-36;android-tv;x86_64'
avdmanager create avd -n PipeTV_Screens -k 'system-images;android-36;android-tv;x86_64' -d tv_1080p
emulator -avd PipeTV_Screens -no-window -gpu swiftshader_indirect -no-audio &
adb install PipeTV_<version>-x86_64-release.apk
adb exec-out screencap -p > screen.png
```

[Blender's open movies](https://studio.blender.org/films/) make good history:
their license (CC BY) lets them be shown, with credit, which the README gives.

## Following upstream

`upstream` is PipePipe, `upstream-client` its client repository and
`upstream-extractor` its extractor. Watch them and bring over what's good for
TVs:

```sh
git fetch upstream-client
git log --oneline upstream-client/dev          # what's new upstream
git cherry-pick -X subtree=PipePipeClient <commit>
git subtree pull --prefix=PipePipeClient upstream-client <tag> --squash   # a whole release

git fetch upstream-extractor                   # the same for the extractor
git cherry-pick -X subtree=PipePipeExtractor <commit>
git subtree pull --prefix=PipePipeExtractor upstream-extractor main --squash
```
