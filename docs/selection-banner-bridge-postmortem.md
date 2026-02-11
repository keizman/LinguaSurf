# LinguaSurf Android 文本菜单抑制问题复盘与防回归手册

本文记录本次“扩展开关控制 Android 系统文本菜单横幅（Copy/Search/...）”的完整问题链路、根因和最终修复。
目标是后续开发者和 LLM 不再重复踩坑。

## 1. 最终成功标准

- 扩展开关切换后，日志出现：
`LinguaSurfExtension: Selection banner preference updated: disabled=true/false`
- 长按文本时，系统横幅可按开关状态稳定显示/隐藏。
- 开关失效时，扩展其他功能不受影响（降级为仅扩展侧逻辑）。

## 2. 问题现象（按出现顺序）

1. 内置扩展安装成功，但扩展开关无效。
2. 日志只出现 `releasePendingMessages ... nativeApp=linguasurfAppBridge`，没有 `App bridge received message`。
3. Add-ons 页面早期实现曾触发崩溃：`IllegalThreadStateException: Must have a Handler`。
4. 文本菜单在特定手势场景下出现“毫秒级闪烁后消失”。

## 3. 根因总结

1. 扩展桥接权限不完整。
- 仅有 `nativeMessaging` 不够。
- GeckoView 内置扩展场景下，桥接稳定需要：
`nativeMessaging` + `geckoViewAddons` + `nativeMessagingFromContent`。

2. 运行线程使用不当。
- 在 `Dispatchers.IO` 背景线程调用 Gecko `listInstalledWebExtensions`，会触发 `Must have a Handler`。

3. 抑制时机不完整导致闪烁。
- 只在 `onActionModeStarted` 结束模式，某些短按场景仍会先显示再消失。
- 需要在 `onWindowStartingActionMode` 预先阻断，并在 `onActionModeStarted` 二次兜底。

4. 消息通道兼容性不足（历史阶段）。
- 扩展侧需要兼容 `connectNative` 与 `sendNativeMessage`。
- App 侧 `MessageHandler` 需要同时处理端口消息与普通消息。

## 4. 已落地修复

1. Android 集成脚本补权限（关键修复）。
- 文件：`build-tools/Integrate-AndroidBuiltInExtension.ps1`
- 集成时强制确保 `manifest.json` 含：
`nativeMessaging`、`geckoViewAddons`、`nativeMessagingFromContent`。

2. App Bridge 处理器完善。
- 文件：`Android-app/app/src/main/java/org/mozilla/fenix/extension/linguasurf/LinguaSurfBuiltInExtensionInstaller.kt`
- 处理 `SET_SELECTION_BANNER_DISABLED` 与 `GET_SELECTION_BANNER_DISABLED`。
- 打印关键日志：
`App bridge received message...`
`Selection banner preference updated: disabled=...`

3. 开关持久化。
- 文件：`Android-app/app/src/main/java/org/mozilla/fenix/extension/linguasurf/LinguaSurfAppSettings.kt`
- 使用 `SharedPreferences` 保存开关状态。

4. 文本菜单抑制策略完善。
- 文件：`Android-app/app/src/main/java/org/mozilla/fenix/HomeActivity.kt`
- 在 `onWindowStartingActionMode` 预阻断。
- 在 `onActionModeStarted` 再次兜底。
- 结合 `TYPE_FLOATING`/`TYPE_PRIMARY` 与菜单项判定，减少误杀。

5. 扩展侧桥接调用兼容与降级。
- 文件：`tranlation-overlay-extension/src/modules/architecture/adapters/app/AndroidAppNativeControlAdapter.ts`
- 优先 `connectNative`，失败后回退 `sendNativeMessage`。
- 失败只告警，不影响扩展核心功能。

## 5. 本次成功验证日志（判定依据）

- 成功联动日志：
`LinguaSurfExtension: Selection banner preference updated: disabled=true`
`LinguaSurfExtension: Selection banner preference updated: disabled=false`

## 6. 每次发版前检查清单（必须执行）

1. 构建扩展：
`cd E:\git\goog_trans\tranlation-overlay-extension`
`npm run build:firefox`

2. 集成扩展到 Android 资源目录：
`cd E:\git\goog_trans\LinguaSurf`
`powershell -ExecutionPolicy Bypass -File .\build-tools\Integrate-AndroidBuiltInExtension.ps1 -SourceDir E:\git\goog_trans\tranlation-overlay-extension\.output\firefox-mv2`

3. 检查内置 manifest 权限是否齐全：
- 文件：`Android-app/app/src/main/assets/extensions/linguasurf/manifest.json`
- 必须同时包含：
`nativeMessaging`、`geckoViewAddons`、`nativeMessagingFromContent`

4. 构建 APK：
`cd E:\git\goog_trans\LinguaSurf\Android-app`
`.\gradlew.bat :app:assembleForkRelease`

5. 安装后手测两次开关：
- 开启后长按文本不显示系统横幅。
- 关闭后恢复显示。

6. Logcat 关键字检查：
- `LinguaSurfExtension`
- `Selection banner preference updated`
- `LinguaSurfSelection`

## 7. 明确禁止事项（防止回归）

1. 禁止删除或覆盖集成脚本中的权限补丁逻辑。
2. 禁止在后台线程调用 Gecko 的扩展列表 API。
3. 禁止只保留单一抑制入口（只在 started 或只在 pre-start 都不行）。
4. 禁止把“桥接失败”升级为抛异常中断扩展功能。

## 8. 相关代码入口索引

- `build-tools/Integrate-AndroidBuiltInExtension.ps1`
- `Android-app/app/src/main/java/org/mozilla/fenix/extension/linguasurf/LinguaSurfBuiltInExtensionInstaller.kt`
- `Android-app/app/src/main/java/org/mozilla/fenix/extension/linguasurf/LinguaSurfAppSettings.kt`
- `Android-app/app/src/main/java/org/mozilla/fenix/HomeActivity.kt`
- `tranlation-overlay-extension/src/modules/architecture/adapters/app/AndroidAppNativeControlAdapter.ts`
- `tranlation-overlay-extension/entrypoints/options/components/basic/BasicSettings.vue`

