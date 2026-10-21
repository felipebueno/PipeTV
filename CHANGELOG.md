# Changelog

PipeTV's changes, newest first. PipeTV is based on PipePipe 5.5.0.

PipeTV's version moves by hand, `appVersionName` in
`PipePipeClient/app/build.gradle`, along with this file. Every push to `main`
is built and published on [GitHub Releases](https://github.com/felipebueno/PipeTV/releases)
as `PipeTV <version> (build <n>)`, with its version's section below as the
release's "what's new".

## [0.6.0] - 2026-10-21

### Changed

- Settings show only what PipeTV's TV interface uses. Hidden on TVs: the touch
  gestures, screen rotation, the details page and its tablet layout, the popup
  player (Android TV doesn't let apps draw over others), the tab bar the side
  rail replaced, Android Auto, the mobile data limit, the player notification,
  the portrait grid columns and the experimental UI. The fullscreen start is
  always on there. The remote's seek step is under "Remote control".
- On TVs a settings screen starts on its first item, and on the one it opened
  when coming back, instead of on the toolbar's back arrow.
- PipeTV has its own version, and every build a higher version code, so a newer
  build always installs over an older one.

### Added

- Every push to `main` is built, signed and published on GitHub Releases, with
  release notes, checksums and download links that always point to the latest
  build.
- A README that shows PipeTV: what it does, screenshots, how to install it and
  how to use it with the remote.

### Fixed

- The home's "Recently watched" and "More like" rows follow what you watch;
  they used to stay as they were when the app started. Back from the player
  still returns to the video you were on, even when it moved, and "More like"
  stays as it is while you watch something you chose in it.
- Closing a player opened from a link returns to the home's last focused card,
  instead of its first one, or of the side rail when the home was scrolled.

### Removed

- PipePipe's update checker, which offered PipePipe's own releases.
- PipePipe's GitHub workflows (its CI, its FFmpeg-Kit build and its issue
  labeler), and its README, release script, translation guide and F-Droid
  listing.

## [0.5.0] - 2026-10-21

### Added

- A home of rows on TVs, first in the side rail: recently watched, more like
  the last of them, the live videos and what's new from the subscriptions.
  Each row loads on its own, and the focused row comes up to the top.
- The focused card grows a little and rises above its neighbors.

### Fixed

- Search: once the on-screen keyboard is closed (Back), Down goes from the
  search box to the suggestions, or to the results. The keyboard used to keep
  Down from leaving the box.

## [0.4.0] - 2026-10-21

### Added

- A side rail in place of the toolbar, the drawer and the tab bar, as in the
  YouTube TV app. Left from the content opens it on the current page; Up and
  Down move along it, OK goes there, Right returns to where you were, and Back
  from the home opens it before leaving.

### Changed

- On TVs, following the device's theme means the dark theme, and the search,
  history and settings toolbars blend into it.
- The focus is a white outline with a faint glow that glides from one item to
  the next, and the cards' own highlight is subtler.

## [0.3.0] - 2026-10-21

### Changed

- As in the TV apps of the big video sites, the player has no way out of
  fullscreen on TVs (Back closes it), and no touch lock button.
- Faster video starts on TVs: a link opens the player page right away, and the
  YouTube decoder no longer warms up at start, which took 10 to 25 s of a TV
  stick's CPU as the first video started.
- Faster related videos: the extractor compiled a regular expression per phrase
  for every date of the list; it now compiles each once. The extractor became a
  subtree of the repository, like the client.

### Added

- The running version, build and channel (dev, R8 or prod) in the bottom right
  corner.
- A `minified` build type: the release build (R8) signed with the debug key.

### Fixed

- A stream already loaded once autoplays again after the player was closed.

## [0.2.0] - 2026-10-21

### Added

- Down in the fullscreen player opens a row of related videos over the video:
  OK plays one, Up goes back to the controls, Back closes it.

### Changed

- Choosing a video goes straight to the fullscreen player; a black loading
  cover hides the details page while the stream loads.
- Back in the player returns to the list, on the video it was opened from.
- YouTube links open straight in the player on TVs, without the chooser.
- A thinner, softer focus ring, and none left over the video once the player
  controls fade out.

## [0.1.0] - 2026-10-21

### Added

- PipeTV's name, id (`io.github.felipebueno.pipetv`) and banner, next to
  PipePipe.
- A focus ring that shows where the focus is on any background.
- In the player, Left and Right seek right away and keep seeking while held.

### Changed

- Videos open in fullscreen on TVs.

### Removed

- The changelog, donation, announcement and update dialogs on start.
