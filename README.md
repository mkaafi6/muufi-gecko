# muufi (GeckoView edition)

A tiny Android reader that runs the **full Gecko engine** with the **real uBlock
Origin** extension built in — same ad-blocking power as Firefox for Android.

This supersedes the WebView edition (which could only approximate uBO). It uses:

- **GeckoView** (Mozilla's embeddable engine) — full WebExtension support.
- **uBlock Origin 1.75.0** — bundled unpacked in assets and installed at startup.
- Native **top + bottom bars**; system status/navigation bars stay visible.
- **Full-canvas fullscreen video** (immersive, no letterboxing/padding).

## Home page

**https://mkaafi6.github.io/muufi/** — edit `frontend/sites.json` there and the
launcher updates with no app rebuild.

## Build / download

Push to `main` → GitHub Actions **Build Android APK** → artifact **`muufi-gecko-apk`**.

- App size is large (~190 MB) because GeckoView bundles the whole Gecko engine for
  arm64-v8a, stored uncompressed so the content process loads reliably.
- Release APK is signed with the debug key (sideload-ready).

## Layout

```
app/src/main/java/com/mkaafi6/muufi/MainActivity.kt   GeckoView + uBO install
app/src/main/assets/ublock_origin.xpi                 uBlock Origin 1.75.0
app/src/main/res/                                     layout, theme, icons
.github/workflows/build-apk.yml                        cloud build
```

## How uBO is installed

uBO is unpacked into `app/src/main/assets/ublock_origin/` (with `manifest.json` at
the root). `MainActivity` calls
`ensureBuiltIn("resource://android/assets/ublock_origin/", "uBlock0@raymondhill.net")`
with a `PromptDelegate` that auto-approves its permissions. uBO then manages its
own filter lists and updates.

Two GeckoView gotchas are handled here:

- Built-in extensions must be a **folder**, not an `.xpi` file — an `.xpi` fails
  with *"This URL does not point to a folder."*
- AAPT drops files/folders starting with `_` by default, which would strip uBO's
  `_locales/` and make its manifest invalid. `androidResources.ignoreAssetsPattern`
  is relaxed to keep them.

## Notes

- Update uBO by downloading a newer signed build from
  <https://addons.mozilla.org/firefox/addon/ublock-origin/>, then unzipping the
  `.xpi` over `app/src/main/assets/ublock_origin/` (keep `_locales/`).
- Target SDK is 34 on purpose so the app is **not** forced edge-to-edge; system
  bars stay respected. Fullscreen video goes immersive on purpose.
