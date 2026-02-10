# Android built-in extension integration (LinguaSurf)

This document records the automation added for Android integration of
`tranlation-overlay-extension` into `LinguaSurf/Android-app`.

## What was added

1. `version-lock/extension.lock.json`
- Source artifact strategy and path.
- Extension id and Android built-in resource url.
- Android app root/package defaults.

2. `build-tools/lib/ExtensionTools.ps1`
- Shared helpers to parse lock config and resolve the selected `.xpi`.

3. `build-tools/Resolve-LocalExtensionXpi.ps1`
- Resolves which `.xpi` will be used (latest-local or pinned).

4. `build-tools/Integrate-AndroidBuiltInExtension.ps1`
- Copies selected `.xpi` into:
`Android-app/app/src/main/assets/extensions/linguasurf/translation-overlay.xpi`.
- Unpacks `.xpi` contents into:
`Android-app/app/src/main/assets/extensions/linguasurf/` (must include `manifest.json` at folder root).
- Also supports direct directory source (recommended):
copy from extension build output folder into `Android-app/app/src/main/assets/extensions/linguasurf/`.
- Writes metadata:
`Android-app/app/src/main/assets/extensions/linguasurf/extension-bundle.json`.

5. `build-tools/Apply-AndroidExtensionBootstrap.ps1`
- Adds installer Kotlin file:
`app/src/main/java/<appPackage>/extension/linguasurf/LinguaSurfBuiltInExtensionInstaller.kt`.
- Patches `FenixApplication.kt` to install built-in extension at app startup.
- Appends `app/build.gradle` preBuild hook so every build auto-runs integration script.

## Expected directory layout

```text
LinguaSurf/
  Android-app/               # copied from iceraven (your product app)
  build-tools/
  docs/
  version-lock/
```

## Usage

Run from `E:\git\goog_trans\LinguaSurf`.

1. One-time bootstrap after copying `Android-app`
```powershell
powershell -ExecutionPolicy Bypass -File .\build-tools\Apply-AndroidExtensionBootstrap.ps1
```

2. Test xpi resolution (optional, only for xpi strategy)
```powershell
powershell -ExecutionPolicy Bypass -File .\build-tools\Resolve-LocalExtensionXpi.ps1
```

3. Manual integration run (optional, build also runs this after bootstrap)
```powershell
powershell -ExecutionPolicy Bypass -File .\build-tools\Integrate-AndroidBuiltInExtension.ps1
```

4. Build Android app
```powershell
cd .\Android-app
.\gradlew.bat :app:assembleForkRelease
```

## Strategy currently configured

Lock file default strategy is `pinned-dir`:
- Source dir:
`E:\git\goog_trans\tranlation-overlay-extension\.output\firefox-mv2`

Recommended workflow:
1. In extension repo run:
```powershell
npm run build
```
2. Build Android app (preBuild runs integration automatically).

Optional xpi workflow:
- Use `source.strategy = pinned` or `latest-local`.
- Keep `source.pinnedXpiPath` / `source.artifactDir`.

## Notes

1. Android path to built-in extension uses a resource URL:
`resource://android/assets/extensions/linguasurf/`
This is required by current GeckoView built-in installation path (`ensureBuiltIn` expects a folder URL).

2. This setup is Android-only for now.
iOS packaging is intentionally deferred and should use a separate adapter strategy.

3. If you change package name from `org.mozilla.fenix`, rerun bootstrap with:
```powershell
powershell -ExecutionPolicy Bypass -File .\build-tools\Apply-AndroidExtensionBootstrap.ps1 -AppPackage "<your.new.package>"
```

4. Re-running scripts is safe:
- bootstrap checks for existing import/call markers.
- Gradle hook block uses explicit markers to avoid duplicate append.

5. Current environment note:
- `:app:assembleForkRelease` is confirmed passing.
- `:app:assembleDebug` currently fails in upstream code path due `addons_sideload` resource mismatch in debug source set.

6. Built-in extension visibility in Add-ons page:
- Android Components `AddonManager.getAddons()` filters out built-in extensions by default.
- LinguaSurf adds a UI-side append in:
`Android-app/app/src/main/java/org/mozilla/fenix/addons/AddonsManagementFragment.kt`
to include the built-in translation extension for management/verification.
- The logic reads from `WebExtensionSupport.installedExtensions` and matches extension ID with/without braces.
- Do not call Gecko `listInstalledWebExtensions` from background threads (`Dispatchers.IO`), otherwise Gecko may throw
`IllegalThreadStateException: Must have a Handler`.
- If not found in support map, LinguaSurf performs main-thread runtime fallback:
`listInstalledWebExtensions` -> if still missing `installBuiltInWebExtension` -> list again.
- Runtime install fallback now tries multiple combinations for compatibility:
`id` with/without braces using directory URL, with explicit error logs per attempt.

7. Recommended add-ons source (performance mode):
- LinguaSurf now uses a local add-ons provider:
`Android-app/app/src/main/java/com/planktonfly/linguasurf/components/LinguaSurfRecommendedAddonsProvider.kt`
- Only one recommended add-on is exposed by default: `uBlock Origin`.
- This removes AMO collection fetches on every open of the add-ons screen and avoids collection 404 delays.
