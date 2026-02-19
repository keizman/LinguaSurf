# ADB Shortcuts And Extension Navigation

Last verified: 2026-02-19
Project: `LinguaSurf/Android-app`
Primary verified package: `com.planktonfly.linguasurf`
Primary verified device: `192.168.8.100:5555`

## 1) App Launch

```bash
adb shell am start -W --user 0 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n com.planktonfly.linguasurf/.App
```

## 2) Built-in Shortcut Actions (IntentReceiverActivity)

```bash
adb shell am start -W --user 0 -n com.planktonfly.linguasurf/org.mozilla.fenix.IntentReceiverActivity -a org.mozilla.fenix.OPEN_TAB
adb shell am start -W --user 0 -n com.planktonfly.linguasurf/org.mozilla.fenix.IntentReceiverActivity -a org.mozilla.fenix.OPEN_PRIVATE_TAB
adb shell am start -W --user 0 -n com.planktonfly.linguasurf/org.mozilla.fenix.IntentReceiverActivity -a org.mozilla.fenix.OPEN_PASSWORD_MANAGER
```

## 3) Extension Deep Links (New 5-entry set)

```bash
# Get into extension manager
adb shell am start -W --user 0 -a android.intent.action.VIEW -d "linguasurf://settings_addon/manager" com.planktonfly.linguasurf
# Get installed extension name -> id list popup
adb shell am start -W --user 0 -a android.intent.action.VIEW -d "linguasurf://settings_addon/list" com.planktonfly.linguasurf
# Get into extension details (lands on "Settings / Details / Permissions" section)
adb shell am start -W --user 0 -a android.intent.action.VIEW -d "linguasurf://settings_addon/details/ec1a757b-3969-4e9a-86e9-c9cd54028a1f" com.planktonfly.linguasurf
# Get into extension settings page
adb shell am start -W --user 0 -a android.intent.action.VIEW -d "linguasurf://settings_addon/settings/ec1a757b-3969-4e9a-86e9-c9cd54028a1f" com.planktonfly.linguasurf
# Get into extension options(popup) page directly
adb shell am start -W --user 0 -a android.intent.action.VIEW -d "linguasurf://settings_addon/options/ec1a757b-3969-4e9a-86e9-c9cd54028a1f" com.planktonfly.linguasurf
```

## 4) Get Installed `name -> id` Without UI XML

One by one execute to get ALL existing extension ID
```bash
adb logcat -c
adb shell am start -W --user 0 -a android.intent.action.VIEW -d "linguasurf://settings_addon/list" com.planktonfly.linguasurf
adb logcat -d | rg -n "Installed extensions \(name -> id\)|Side Translation|AddonsManagementFragment"
```

## 5) Upgrade Extension Via Command Line

```bash
# 1) Build XPI
cd E:\git\goog_trans\tranlation-overlay-extension
npm run build:firefox:xpi

# 2) Push new verison of XPI to device (recommended path: /data/local/tmp)
adb push E:\git\goog_trans\tranlation-overlay-extension\dist\side-translation-1.8.15.xpi /data/local/tmp/side-translation-1.8.15.xpi

# 3) Debug automation install (no manual ADD needed)
# Adds automation flag: --ez linguasurf.auto_confirm_install true
adb shell am start -W --user 0 -a android.intent.action.VIEW --ez linguasurf.auto_confirm_install true -d "file:///data/local/tmp/side-translation-1.8.15.xpi" -t application/x-xpinstall com.planktonfly.linguasurf
```

Machine-checkable logs:

- `AddonInstallIntentProcessor: Automation mode enabled ...`
- `AddonInstallAutomationGate: Auto-confirm requested ...`
- `WebExtensionPromptFeature: Automation mode: auto-approved required permissions ...`
- `WebExtensionPromptFeature: Automation mode: skipped post-install dialog ...`
- `AddonInstallIntentProcessor: Install success: id=..., version=...`
