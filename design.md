# LinguaSurf Design v4 (Execution Ready)
更新时间: 2026-02-09

## 0. 设计基线（先定死，不再摇摆）

### 0.1 不变原则
- `听 / 说 / 读 / 写 / 翻译` 是产品能力 Core，不随平台变化。
- Core 只负责业务语义、状态机、策略和协议。
- Adapter 只负责平台差异: 引擎、音频设备、存储、消息总线、权限、网络通道。

### 0.2 你的关键判断（确认）
- 你说的方向正确: 音频播放/录音等设备能力应在 Adapter 层。
- 补充一点: “播放策略、重试、fallback 决策、句子切片、高亮时序”属于 Core；“真正调用 AudioContext/AVAudioEngine/MediaRecorder”属于 Adapter。

## 1. 基于现有代码的事实盘点

以下是当前 `tranlation-overlay-extension` 的真实情况（用于落地，不是理想化）:

### 1.1 已有能力
- `翻译`: `src/modules/core/translation/*`, `src/modules/processing/*`, `src/modules/content/*`
- `听`: `src/modules/listen/*`, `src/modules/content/services/ParagraphTTSService.ts`
- `读`: `src/modules/read/wordCard/*`
- `基础设施`: `src/modules/core/storage/*`, `src/modules/core/messaging/*`, `src/modules/auth/*`, `entrypoints/background.ts`

### 1.2 占位/未实装
- `说`: `entrypoints/options/components/speak/SpeakSettings.vue` 目前是 Coming Soon
- `写`: `entrypoints/options/components/write/WriteSettings.vue` 目前是 Coming Soon
- `src/modules/speak`、`src/modules/write` 当前基本为空

### 1.3 当前耦合问题（后续拆分重点）
- Core 中仍混有 `browser/chrome/document/window` 直接调用。
- `TextReplacerService` / `ParagraphTranslationApi` 仍直接读取 DOM 语言信息。
- `StorageService`、`MessagingService` 在 Core 下但强依赖 Extension API。
- TTS 逻辑中设备播放与业务状态机混写。

结论: 你的模块划分方向是对的，当前工作重点是“把已有实现从混合态拆成 Core + Adapter”。

## 2. 目标架构（同构，不同码）

### 2.1 仓库与边界
- `linguasurf-spec`
  - 领域模型 schema
  - 消息协议 (CoreEvent / CoreCommand)
  - 同步 API 契约
- `linguasurf-core`
  - `core-translate`
  - `core-listen`
  - `core-read`
  - `core-speak`
  - `core-write`
  - `core-session` (设置、账号、策略、限流)
  - `core-logger` (日志抽象)
- `linguasurf-android`
  - `gecko-adapters` + app shell
- `linguasurf-ios`
  - `wk-adapters` + app shell
- `linguasurf-chrome`
  - `mv3-adapters` + extension shell

### 2.2 平台引擎策略（保持 v3 结论）
- Android: Gecko 主线
- iOS: WKWebView 主线（可补 Safari Extension）
- Windows: Chrome MV3
- 统一点: Core/协议/数据，不是引擎代码。

## 3. 五大 Core 细分设计（可直接开工）

## 3.1 Translate Core（翻译域）

### 职责
- 文本分段策略（段长、最小长度、合并、去重）。
- 翻译模式状态机（Word / Paragraph / Lazy）。
- 语言路由策略（native/target 自动切换）。
- 替换计划生成（replacement plan，不直接触 DOM）。
- 失败处理策略（重试、降级、跳过）。

### 输入
- `PageSnapshot`（页面可翻译片段、语言 hint、元素指纹）
- `TranslateSettings`
- `CoreCommand.TranslatePage | TranslateSegment | ToggleVisibility`

### 输出
- `ReplacementPlan[]`
- `CoreEvent.SegmentTranslated | SegmentSkipped | SegmentFailed`

### 依赖 Port
- `PageDomPort`（读取文本、计算 range、应用 patch）
- `AIInferencePort`（LLM 翻译）
- `CachePort`
- `LanguageDetectPort`

## 3.2 Listen Core（听域）

### 职责
- 全文朗读状态机 (`IDLE/LOADING/PLAYING/PAUSED`)。
- 句子切片、时间点估算、词高亮时序。
- TTS Provider fallback 策略（主 -> 备 -> 失败）。
- 音频缓存策略（slice cache / TTL / eviction）。

### 输入
- `CoreCommand.PlayFullText | Pause | Resume | Stop | PlayWord`
- `ReadModel`（段落、句子、offset）
- `ListenSettings`

### 输出
- `CoreEvent.PlaybackStateChanged`
- `CoreEvent.PlaybackProgress`
- `CoreEvent.AudioError`

### 依赖 Port
- `AudioPlaybackPort`（播放/暂停/停止/当前位置）
- `TTSProviderPort`（文本->音频）
- `HighlightPort`（高亮应用）
- `VisibilityPort`（前后台切换）

## 3.3 Read Core（读域）

### 职责
- 选词捕获策略（select/dblclick/single/touch 去重窗口）。
- 词卡状态机（hidden/loading/loaded/error/pinned）。
- 词典查询、防抖和 in-flight dedupe。
- 生词操作事件（收藏、发音、二次查询）。

### 输入
- `CoreCommand.CaptureWord | ShowWordCard | HideWordCard`
- `PointerSelectionEvent`
- `ReadSettings`

### 输出
- `CoreEvent.WordCardShown | WordCardFailed | WordStarred`
- `CoreEvent.WordSpeakRequested`

### 依赖 Port
- `SelectionPort`
- `DictionaryPort`
- `AudioPlaybackPort`
- `OverlayUiPort`

## 3.4 Speak Core（说域，预留但现在就定义契约）

### 当前状态
- UI 已有入口，业务未落地。

### 职责（第一期）
- 朗读任务管理（目标句、重录次数、打分结果）。
- 音频上传与评分编排（可先调用后端评分）。
- 学习记录入库（发音分、错误类型）。

### 依赖 Port
- `AudioRecordPort`
- `SpeechAssessmentPort`
- `StoragePort`

## 3.5 Write Core（写域，预留但现在就定义契约）

### 当前状态
- UI 已有入口，业务未落地。

### 职责（第一期）
- 写作任务（原句->改写/纠错）工作流。
- 错误标注（语法、拼写、搭配）统一格式。
- 复习计划事件输出。

### 依赖 Port
- `WritingAssistantPort`（LLM/规则后端）
- `StoragePort`

## 4. Adapter 设计（你关心的“哪些必须适配”）

## 4.1 必需 Adapter 列表
- `EnginePort`: 打开、回退、前进、刷新、导航事件
- `PageDomPort`: 读取可翻译片段、定位文本、安全 patch
- `RuntimeMessagePort`: 前台/后台/页面三方消息
- `StoragePort`: settings/state/cache 持久化
- `AudioPlaybackPort`: 音频输出
- `AudioRecordPort`: 麦克风录制（给 speak）
- `TTSProviderPort`: Google/Youdao/WebSpeech/Native TTS
- `NetworkPort`: fetch、超时、重试、鉴权注入
- `PermissionPort`: 麦克风、通知等
- `LifecyclePort`: visibility/background/resume/terminate

## 4.2 平台映射

### Android (Gecko)
- `EnginePort`: GeckoSession / GeckoView
- `RuntimeMessagePort`: WebExtension message + app bridge
- `StoragePort`: Gecko storage + app local db
- `Audio*Port`: ExoPlayer/AudioTrack + Recorder

### iOS (WKWebView)
- `EnginePort`: WKWebView navigation delegate
- `RuntimeMessagePort`: WKScriptMessageHandler
- `StoragePort`: app group + local storage bridge
- `Audio*Port`: AVAudioEngine / AVSpeechSynthesizer / AVAudioRecorder

### Windows Chrome (MV3)
- `EnginePort`: tab/runtime API
- `RuntimeMessagePort`: runtime/tabs messaging
- `StoragePort`: chrome.storage.sync/local
- `Audio*Port`: Web Audio + Web Speech + extension fetch

## 5. 关键接口契约（冻结后再开发）

```ts
export interface CoreCommandBus {
  dispatch(command: CoreCommand): Promise<void>;
}

export type CoreCommand =
  | { type: 'TranslatePage'; mode: 'word' | 'paragraph' }
  | { type: 'ToggleTranslationVisibility' }
  | { type: 'PlayFullText' }
  | { type: 'PauseFullText' }
  | { type: 'CaptureWord'; source: 'select' | 'dblclick' | 'single' | 'touch' }
  | { type: 'SpeakStart'; text: string }
  | { type: 'WriteCheck'; text: string };

export interface CoreEventBus {
  publish(event: CoreEvent): void;
  subscribe(handler: (event: CoreEvent) => void): () => void;
}

export type CoreEvent =
  | { type: 'TranslationApplied'; segmentId: string; replacementCount: number }
  | { type: 'PlaybackStateChanged'; state: 'IDLE' | 'LOADING' | 'PLAYING' | 'PAUSED' }
  | { type: 'WordCardShown'; word: string }
  | { type: 'Error'; domain: 'translate' | 'listen' | 'read' | 'speak' | 'write'; code: string; message: string };
```

## 6. 状态机（避免后续逻辑分裂）

## 6.1 Translate 状态机
- `IDLE -> PREPARE -> PROCESSING -> APPLYING -> IDLE`
- 错误转移:
  - `PROCESSING -> DEGRADED`（单段失败可跳过继续）
  - `APPLYING -> RECOVER`（range失配回退纯文本）

## 6.2 Listen 状态机
- `IDLE -> LOADING -> PLAYING -> PAUSED -> PLAYING -> IDLE`
- 边界:
  - 前后台切换触发 `AudioContext suspended` 时自动 `resume`。
  - 同词重复点击: 去重（当前代码已有窗口控制思想，保留为 Core 策略）。

## 6.3 Read 状态机
- `HIDDEN -> LOADING -> LOADED | ERROR`
- `LOADED <-> PINNED`
- 触摸与 selectionchange 双触发冲突: Core 去重窗口。

## 7. 边缘 Case 设计清单（首版必须覆盖）

## 7.1 页面与导航
- SPA 导航 URL 变更但不刷新: 清理旧翻译状态并重建 session。
- DOM 频繁变化: 防抖 + 批处理 + 避免 observer 自激。
- 无法注入页面（系统页/受限页）: 记录并跳过，不抛全局错误。

## 7.2 翻译链路
- API 超时/429/5xx: 指数退避 + 单段降级。
- replacement 位置失配: 重新定位一次，失败则跳过该 replacement。
- 懒加载 observer 不可用: 自动降级全量处理。
- 语言检测失败: 回落用户配置目标语言。

## 7.3 音频链路
- AudioContext 挂起: visibility 恢复时 resume。
- 自动播放受限: 需要手势触发后解锁音频通道。
- 并发播放冲突（word tts 与 paragraph tts）: 全局 audio session 仲裁。
- blob URL 泄漏: 统一释放策略（onended/onerror）。

## 7.4 交互冲突
- touchend + selectionchange 双触发: 去重窗口。
- 双击识别误触: 时间窗与目标元素一致性校验。
- 悬浮层遮挡页面点击: pointer-events 策略白名单化。

## 7.5 数据与设置
- 旧设置格式: 明确 `settingsVersion` 迁移器，不再“模糊重置”。
- 多端冲突: last-write-wins + 字段级 merge（apiConfigs 例外）。
- activeConfig 丢失: 自动回退第一可用配置并上报事件。

## 8. 数据模型与版本策略

### 8.1 Settings 增加版本号
- `settingsVersion: number`（当前建议从 `4` 起）
- 每次 schema 变化新增 migration 函数:
  - `v1->v2`, `v2->v3`, ...

### 8.2 Core 统一模型（示例）
- `TranslationTask`
- `PlaybackTask`
- `WordCardEntry`
- `SpeakAttempt`
- `WriteAttempt`

## 9. 从现有代码到新架构的迁移计划（不剪枝版）

## Phase 1: 先抽协议和 Port（1-2 周）
- 冻结 `CoreCommand/CoreEvent`。
- 抽离 `StoragePort/MessagePort/AudioPlaybackPort` 接口。
- 现有实现先包一层 Adapter，不改业务逻辑。

## Phase 2: 抽 Translate/Listen/Read Core（2-4 周）
- `TextProcessorService`、`ProcessingCoordinator` 拆成:
  - 纯策略核心
  - DOM 应用 adapter
- `FullTextTTSService` 拆成:
  - 播放状态机 Core
  - Audio/WebSpeech adapter
- `WordCardManager` 拆成:
  - 状态机 Core
  - DOM/UI adapter

## Phase 3: Speak/Write 最小可用（2-3 周）
- 先完成契约和占位业务流（不追求复杂算法）。
- 让五大模块在 Core 层全部“可运行”。

## Phase 4: 平台接入（并行）
- Android Gecko adapter 接入
- iOS WK adapter 接入
- Windows Chrome adapter 对齐协议

## 10. 验收门槛（Definition of Done）

### 10.1 架构验收
- Core 包内不得直接 import `browser/chrome/window/document/AudioContext`。
- 平台差异仅出现在 adapters 包。

### 10.2 功能验收
- 听/读/翻译全链路通过。
- 说/写至少完成最小业务闭环（有任务、有结果、有存储）。

### 10.3 稳定性指标
- 翻译成功率 >= 98%（可翻译片段）
- 前台音频可恢复率 >= 99%
- SPA 导航后可继续翻译率 >= 98%
- 设置迁移成功率 100%（不可崩溃）

---

## 最终结论（给你的直接建议）
- 你的“5 大模块做 Core”是正确方向，并且应该锁定不变。
- 当前最重要的不是加功能，而是先把 Port 和协议冻结，把已实现的 `读/听/翻译` 从混合代码拆成真正 Core。
- 这样后续做 iOS（WK）和 Windows Chrome 时，只换 Adapter，不动业务核心；这和你要的长期稳定路线一致。

## 11. Android 集成落地记录（2026-02-10）

本文件保留策略，具体执行脚本与步骤记录在:
- `docs/android-extension-integration.md`

已落地文件:
- `version-lock/extension.lock.json`
- `build-tools/Resolve-LocalExtensionXpi.ps1`
- `build-tools/Integrate-AndroidBuiltInExtension.ps1`
- `build-tools/Apply-AndroidExtensionBootstrap.ps1`
- `build-tools/lib/ExtensionTools.ps1`

当前 Android 路线:
- 构建时自动从 `tranlation-overlay-extension/web-ext-artifacts` 选取 XPI（默认 latest-local）。
- 自动复制到 `Android-app` assets 作为 built-in extension。
- App 启动安装 built-in extension；后续可继续叠加远端更新机制。
