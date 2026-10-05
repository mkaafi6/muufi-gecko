# Session Notes — muufi (GeckoView edition)

**Created:** 2026-10-05
**Local path:** `/root/muufi-gecko`
**Repo:** https://github.com/mkaafi6/muufi-gecko
**Supersedes:** `muufi` (WebView + adblock-rust — hit its ceiling vs uBO/Cromite)

## Why this exists

Android WebView has no WebExtension API, so the WebView edition could only
*approximate* uBO. Running the **real uBlock Origin** in our own app requires
**GeckoView + the uBO WebExtension**.

## Decisions (as built today)

- Engine: `org.mozilla.geckoview:geckoview-omni:147.0.20260212191108` (Mozilla Maven).
  GeckoView **157 needs compileSdk 37 / AGP 9.1** (bleeding edge) → stayed on **147**.
- Toolchain: compileSdk **36**, minSdk **26** (147 requires it), targetSdk **34**,
  AGP **8.10.1**, Gradle **8.11.1**, Kotlin **2.2.0** (GeckoView ships the Kotlin 2.2 stdlib;
  1.9.x can't read its metadata).
- ABI: **arm64-v8a only**.
- Native libs: **uncompressed + page-aligned** (`useLegacyPackaging = false`,
  `android:extractNativeLibs="false"`). This is Mozilla's default and the config
  GeckoView's child processes are actually tested with. APK is large (~190 MB).
- No R8 (GeckoView is huge; minify saves little, risks stripping).

## uBlock Origin install (the part that was broken)

- uBO is **unpacked into the APK assets at `app/src/main/assets/ublock_origin/`**
  (manifest.json at the folder root), installed with:
  `ensureBuiltIn("resource://android/assets/ublock_origin/", "uBlock0@raymondhill.net")`.
- **An `.xpi` cannot be used directly** — `ensureBuiltIn`/`installBuiltIn` need a
  *folder* and fail with **"This URL does not point to a folder."**
- **AAPT strips every file/folder whose name starts with `_` by default.** uBO ships
  `_locales/`, so without a fix the extension's localized manifest is rejected.
  Fixed with Mozilla's relaxed pattern in `app/build.gradle.kts`:
  `androidResources.ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~"`
- `PromptDelegate` auto-approves uBO's permissions.
- Source of truth for updates: `tools/ublock_origin-1.75.0.xpi`
  (unzip into `app/src/main/assets/ublock_origin/` to bump).

## Blank-page / "content process was KILLED"

- Symptom: a page renders, then the body goes blank; toast **"Gecko content
  process was KILLED"**. Root cause is the Gecko **content process dying**
  (memory pressure — the sites are heavy and, with uBO broken, nothing was
  blocking their scripts). It is **not** uBO over-blocking.
- Mitigations in this build:
  - uBO now actually installs (blocks the heavy ad/script payloads).
  - Conservative process settings: `appZygoteProcessEnabled(false)`,
    `isolatedProcessEnabled(false)`, `fissionEnabled(false)` — some OEM ROMs kill
    Gecko's app-zygote / isolated child process.
  - **Recovery**: `ContentDelegate.onKill`/`onCrash` re-open the session and reload
    the last URL instead of leaving a permanent blank page.

## API pinned (GeckoView 147)

- `WebExtensionController.ensureBuiltIn(uri, id)` → `GeckoResult<WebExtension>` (uri = folder).
- `WebExtensionController.enable(ext, int source)` / `disable(ext, int source)` —
  need `WebExtensionController.EnableSource.USER` (=1); a 1-arg call does not compile.
- `onInstallPromptRequest(extension, permissions, origins, dataCollectionPermissions)`
  → `GeckoResult<PermissionPromptResponse>`; `PermissionPromptResponse(true, true, true)`.
- `ContentDelegate.onFullScreen(GeckoSession, boolean)`; `onKill`/`onCrash`.
- `PromptDelegate.onOptionalPrompt(...)` → `GeckoResult<AllowOrDeny>`.
- Verified against the 147 AAR that `appZygoteProcessEnabled`, `isolatedProcessEnabled`,
  `fissionEnabled`, `extensionsProcessEnabled`, `lowMemoryDetection` all exist in
  `GeckoRuntimeSettings.Builder`.

## Status / TODO

- [x] CI APK build green with GeckoView 147.
- [x] uBO shipped **unpacked** (656 files) with `_locales` preserved.
- [ ] Confirm on device: launch toast says **"uBO: installed"** (not FAILED).
- [ ] Confirm uBO blocks (About → uBlock Origin: installed; ads disappear).
- [ ] Confirm blank-page goes away / recovers via the kill toast.
- [ ] Measure APK size (>150 MB expected, uncompressed libs).
- [ ] Optional: release keystore; auto-update uBO.
