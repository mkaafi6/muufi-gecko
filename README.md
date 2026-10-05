# muufi (GeckoView edition)

A tiny Android reader that runs the **full Gecko engine** with the **real uBlock
Origin** extension built in — same ad-blocking power as Firefox for Android.

This supersedes the WebView edition (which could only approximate uBO). It uses:

- **GeckoView** (Mozilla's embeddable engine) — full WebExtension support.
- **uBlock Origin 1.75.0** — the official signed `.xpi`, installed at startup.
- Native **top + bottom bars**; system status/navigation bars stay visible.
- **Full-canvas fullscreen video** (immersive, no letterboxing/padding).

## Home page

**https://mkaafi6.github.io/muufi/** — edit `frontend/sites.json` there and the
launcher updates with no app rebuild.

## Build / download

Push to `main` → GitHub Actions **Build Android APK** → artifact **`muufi-gecko-apk`**.

- App size is large (~60–80 MB) because GeckoView bundles the Gecko engine for arm64-v8a.
- Release APK is signed with the debug key (sideload-ready).

## Layout

```
app/src/main/java/com/mkaafi6/muufi/MainActivity.kt   GeckoView + uBO install
app/src/main/assets/ublock_origin.xpi                 uBlock Origin 1.75.0
app/src/main/res/                                     layout, theme, icons
.github/workflows/build-apk.yml                        cloud build
```

## How uBO is installed

`MainActivity` calls `webExtensionController.ensureBuiltIn("resource://android/assets/ublock_origin.xpi", "uBlock0@raymondhill.net")`
with a `PromptDelegate` that auto-approves its permissions. uBO then manages its
own filter lists and updates.

## Notes

- Update uBO by replacing `app/src/main/assets/ublock_origin.xpi` with a newer
  signed build from <https://addons.mozilla.org/firefox/addon/ublock-origin/>.
- Target SDK is 34 on purpose so the app is **not** forced edge-to-edge; system
  bars stay respected. Fullscreen video goes immersive on purpose.
