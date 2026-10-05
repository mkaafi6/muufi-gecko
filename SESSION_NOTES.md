# Session Notes — muufi (GeckoView edition)

**Created:** 2026-10-05
**Local path:** `/root/muufi-gecko`
**Repo:** https://github.com/mkaafi6/muufi-gecko
**Supersedes:** `muufi` (WebView + adblock-rust — hit its ceiling vs uBO/Cromite)

## Why this exists

The WebView edition could not match uBO/Cromite: Android WebView has no
WebExtension API, and Cromite is a full Chromium fork with a C++ AdBlock Plus
engine + CNAME uncloaking. The only way to run the **real uBO** in our own app
is **GeckoView + the uBO WebExtension**.

## Decisions

- Engine: `org.mozilla.geckoview:geckoview-omni:157.0.20260924084938` (Mozilla Maven).
- ABI: **arm64-v8a only** (`abiFilters`), `useLegacyPackaging = true`.
- minSdk 24, compileSdk 35, **targetSdk 34** (avoid forced edge-to-edge).
- No R8 (GeckoView is huge; minify saves little, risks stripping).
- uBO installed via `ensureBuiltIn("resource://android/assets/ublock_origin.xpi", "uBlock0@raymondhill.net")`
  with a `PromptDelegate` auto-approving permissions (the earlier `installBuiltIn`
  folder approach rejects uBO's manifest — `menus`/`commands`).
- Fullscreen: `ContentDelegate.onFullScreen(session, fullScreen: Boolean)` → hide
  bars + immersive + landscape. **No `fitsSystemWindows`** so video uses the full
  canvas (that was the "weird frame" bug in the WebView edition).

## API pinned (GeckoView 157)

- `onInstallPromptRequest(extension, permissions, origins, dataCollectionPermissions)`
  → `GeckoResult<PermissionPromptResponse>`
- `PermissionPromptResponse(Boolean isPermissionsGranted, Boolean isPrivateModeGranted,
  Boolean isTechnicalAndInteractionDataGranted)`
- `ContentDelegate.onFullScreen(GeckoSession, boolean)`
- `PromptDelegate.onOptionalPrompt(...)` → `GeckoResult<AllowOrDeny>`

## Status / TODO

- [ ] First CI build green.
- [ ] Confirm uBO installs and blocks popups/redirects on device.
- [ ] Confirm fullscreen video fills the whole canvas.
- [ ] Measure APK size (expect ~60–80 MB).
- [ ] Optional: release keystore; auto-update uBO xpi.
