# 楼记 · 测试与缺陷检测报告

日期：2026-11-02
版本：1.0.8
设备：Android Emulator `RiderClimbApi35`，API 35，x86_64，1080×2400 / 420dpi，后置模拟相机
主机：Windows 10，JDK 17.0.20.1，Gradle 8.5，AGP 8.2.2，compileSdk/targetSdk 34，minSdk 24

## 1. 结果摘要

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| JVM 单元测试 | 19 / 19 通过 | `app/build/reports/tests/testDebugUnitTest/` |
| 仪器化测试（API 35） | 17 / 17 通过 | `app/build/reports/androidTests/connected/debug/` |
| Android Lint | 0 error（仅依赖更新等 warning） | `app/build/reports/lint-results-debug.html` |
| Mimosa 深度安全扫描 | 0 findings，63 packages | `scan-2026-11-02T07-17-11.135Z-3458fa7664f7` |
| Release APK | 已签名，3.9 MB（v1.0.8，versionCode 9） | `apksigner verify` 通过 |
| 应用内更新 E2E | 通过（v1.0.0 → v1.0.2 → v1.0.3 → v1.0.4 → v1.0.5 → v1.0.6 → v1.0.7 → v1.0.8） | 见第 6 节、`docs/screenshots/update-*.png` |

## 2. 单元测试（19）

命令：

```bash
./gradlew :app:testDebugUnitTest
```

覆盖：

- `AppVersionTest`：`v1.0.1` / 预发布后缀 / 位数补齐 / 数字比较 / 非法输入。
- `RecordValidatorTest`：位置必填、4 位尾号、媒体上限、完整表单通过、默认值不算有效内容、只填爬楼说明也算有效内容。
- `FormattersTest`：楼层显示（7 → 7F、B1）、空值占位、时长、摘要包含全部关键字段（含爬楼说明）。
- `BackupJsonTest`：`BackupRecord` / `BackupManifest` 序列化往返、缺省字段兼容（含 `climbNote`）。
- `InstallTargetTest`：Ready 与 NeedsPermission 都能解析出安装目标，其他状态返回 null（权限重试回归测试）。

> Windows 中文项目路径下，Gradle 的 JVM 测试 worker 可能无法加载测试类。通过 ASCII 目录联接运行（`D:\louji-build` → 项目目录）后 19 / 19 通过；APK、Lint、仪器化测试不受影响。详见 README「Windows 中文路径说明」。

## 3. 仪器化测试（17，API 35 模拟器）

命令：

```bash
./gradlew :app:connectedDebugAndroidTest
```

覆盖：

- `RecordDaoTest`
  - 记录 / 附件 1:N 关系；删除记录级联删除附件。
  - 位置、订单号、楼层、房号、尾号、性格标签搜索；要爬楼 / 免爬 / 会下来 / 有媒体筛选。
  - 本月统计、最近位置聚合。
- `BackupManagerTest`
  - 导出 → 导入往返：字段、标签、媒体文件均恢复。
  - 合并导入对相同签名的记录去重。
  - 合并去重不会把含 `|` 的不同字段误判为同一条记录。
  - 覆盖导入清空旧记录。
  - 拒绝 zip 路径穿越（`../escape.txt`），不写出临时目录。
  - 拒绝超过 16MB 的 `records.json`，在 JSON 解析前终止。
- `DatabaseMigrationTest`
  - 用 Room `MigrationTestHelper` 创建 v2 数据库，插入旧记录，执行 `MIGRATION_2_3` 并校验 `climbNote` 自动补为空串、旧字段保留。
- `RecordFlowTest`
  - 通过真实表单创建含 11 个字段的记录，保存后进入详情并逐项断言。
  - 搜索、筛选、删除记录回空状态。
  - 编辑位置后保存，详情更新。
  - 条目默认进入「现场」媒体页；无附件时显示空状态；顶部「详细信息」进入完整详情。
  - 独立「备注」写入、详情展示、列表单行摘要、搜索命中。
  - 「爬楼说明」随决策填写、保存后详情展示，列表显示说明摘要，搜索「电梯直达」命中。
  - 条目右侧图片缩略图：点击打开全屏放大层，不误进「现场」；点击放大图片关闭并回到缩略图。
- `CameraFlowTest`
  - 拍照：CameraX 绑定、快门、回调、pending 附件出现在表单。
  - 录像：开始 / 停止、Finalize、pending 附件出现。
  - 修复：CameraX 绑定必须在应用主线程完成；测试环境下 `LaunchedEffect` 恢复线程不是主线程，已用 `Dispatchers.Main.immediate` 包裹绑定逻辑。

## 4. 手动 / adb E2E（模拟器实测）

脚本：`tools/e2e/ui.mjs`（adb + uiautomator dump + 截图）。

| 用例 | 结果 | 证据 |
| --- | --- | --- |
| 新建记录、保存、详情显示全部字段 | 通过 | `docs/screenshots/detail.png` |
| 相册导入截图（系统 Photo Picker） | 通过 | `docs/screenshots/detail-media.png` |
| 应用内拍照并生成缩略图 | 通过 | 手动流程；缩略图出现在表单/详情 |
| 应用内录像（4 秒）并在 Media3 播放 | 通过 | `docs/screenshots/viewer-video.png` |
| 照片全屏查看 | 通过 | `docs/screenshots/viewer-photo.png` |
| 搜索、筛选、删除 | 通过 | instrumented `RecordFlowTest` |
| 备注写入/详情/列表/搜索 | 通过 | instrumented `RecordFlowTest`、`RecordDaoTest` |
| 爬楼说明写入/详情/列表/搜索/备份 | 通过 | instrumented `RecordFlowTest`、`RecordDaoTest`、`BackupManagerTest` |
| 订单截屏缩略图 → 放大 → 点图关闭 | 通过 | instrumented `RecordFlowTest`；`list-thumbnail.png`、`thumbnail-zoom.png` |
| 点击条目 → 现场网格 → 全屏媒体 | 通过 | `docs/screenshots/detail-media.png`、`media-empty.png` |
| 无附件现场 → 添加入口 | 通过 | `docs/screenshots/media-empty.png` |
| 导出备份 zip 并检查 manifest / records.json | 通过 | `records.json` 内容已核对 |
| 导入备份（合并）并跳过重复 | 通过 | Snackbar：`已导入 0 条记录、0 个附件，跳过 1 条重复` |
| 覆盖导入 | 通过 | instrumented `BackupManagerTest` |
| 深浅色切换并持久化 | 通过 | `home-dark.png`、`settings-dark.png` |
| 1.3× 字体 | 通过 | `home-font13.png` |
| 横屏 / 平板宽度（1600×2560 @240dpi） | 通过 | `home-landscape.png`；内容 760dp 居中，FAB 跟随内容列 |
| Release APK 安装、建记录、备份导出/导入 | 通过 | R8 后序列化、Room、FileProvider 正常 |

### 相机说明

模拟器使用软件 GPU（`swiftshader_indirect`），`adb screencap` 中相机预览为黑屏；但 CameraX 日志显示首帧与拍照/录像文件正常，`CameraFlowTest` 两项均通过，详情缩略图和 Media3 播放可见。真机预览不受此限制。

## 5. 缺陷检测与修复

### Android Lint

- 初次运行：5 个 error，全部为 Media3 `UnstableApi` opt-in；25 个 warning（依赖可更新、Manifest API 属性、未使用资源）。
- 修复：为 `VideoPage` 增加 `@OptIn(UnstableApi::class)`；删除未使用图标；`StatusStamp` 的 `modifier` 参数顺序调整；`LinearProgressIndicator` 改为 lambda progress；`KeyboardArrowRight` 改为 AutoMirrored。
- 复跑：**0 error**，20 个 warning（均为依赖可更新 / Manifest API 属性 / 库自身提示）。

### Mimosa 深度安全扫描

- 命令：Mimosa `security_scan`（deep）。
- 结果（v1.0.8 最终复扫）：`findingCount: 0`，`packagesScanned: 63`，scanId `scan-2026-11-02T07-17-11.135Z-3458fa7664f7`，seal `sha256:c39332c8da899e88aa7269cf9cc259d4011de9bdb1fd1d1b2bcd9f17e42a788a`。
- 证据边界：Mimosa 为 `static_only_no_runtime_execution`，library source / callgraph 部分不可用；因此只报告「本次静态深度扫描 0 findings」，不宣称应用绝对无漏洞。
- 人工复核重点：`allowBackup=false`、FileProvider 仅暴露 `cache/updates/`、导出/导入路径穿越防护、APK 下载仅信任 `github.com` / `objects.githubusercontent.com` / `release-assets.githubusercontent.com`、SHA-256 校验、无明文流量。

### 视觉与交互缺陷（已修）

| 缺陷 | 修复 |
| --- | --- |
| 首次图标像柱状图，圆形启动遮罩裁切台阶 | 改为连体楼梯 + 门洞；adaptive foreground 缩进 66dp 安全区并居中；48px 实测可辨 |
| 浅色保存按钮是厚重深棕块 | 改为安全琥珀底 + 近黑文字 |
| 选中 chip 一律薄荷绿，未知状态语义错误 | 统一为琥珀选中色，状态语义交给印章 |
| 空记录详情出现 48sp「未填」 | 无楼层/房号时改为「—」+ 小号「楼层 / 房号未填」 |
| 拍照返回表单后滚动位置丢失 | 把 `ScrollState` 提升到相机覆盖层之外 |
| 相机绑定在测试线程恢复，CameraX 崩溃 | `Dispatchers.Main.immediate` 包裹绑定 |
| 横屏/大字体下 FAB 遮挡卡片文字 | 改为图标 FAB；印章移到卡片左侧；时间移到左侧；FAB 锚定内容列 |
| 列表「上面不能开」详情用红色，像错误 | 改为琥珀色事实提示；只有门损坏/删除用红色 |
| 设置里清除记录/隐私误用门图标 | 换成垃圾桶 / 信息 / 链接 vector |
| 最终 finish review：列表门有问题不够醒目 | 列表门有问题使用错误色；v1.0.4 截图已验证浅色/深色 |
| 最终 finish review：FAB 超出卡片边距 | FAB 改为与内容列 20dp 边距对齐 |
| 最终 finish review：详情滚动后丢失记录身份 | app bar 标题改为当前位置名，滚动后仍可识别 |
| 最终 finish review：设置「存储占用」与记录数矛盾 | 改为「媒体占用」，明确只统计照片/视频 |
| 最终 finish review：客户标签占用信号绿 | 标签改为中性表面色；绿色只保留给免爬/会下来 |
| 最终 finish review：更新说明像开发者日志 | v1.0.3/v1.0.4 Release notes 改写为骑手可读语言 |
| 最终 finish review：按钮圆角/图标方向不一致 | 更新弹层、详情次要按钮统一 12dp；导出/导入图标区分 |
| 启动瞬间误显示「还没有记录」 | 列表增加加载态，数据库首帧前显示进度 |
| 1.3× 字体下「详细信息」被 FAB 遮挡 | 详情入口向右留 64dp 安全区，避开 FAB |
| 1.3× 字体下台账字段名换行（门是否完/好） | 字段名列从 82dp 扩到 104dp，5 字标签单行显示 |
| 现场空状态 CTA 用深棕主色 | 改为安全琥珀 primaryContainer |
| 全屏放大层顶部残留灰色系统栏 | 改为应用内全屏覆盖层，放大时状态栏/导航栏图标转白，关闭后恢复 |
| 全量仪器化运行时相机绑定偶发超时 | 相机就绪等待从 30s 放宽到 60s（慢速模拟器），全量 14/14 通过 |

### 设计 finish review

- 以全新上下文审阅最终截图、DESIGN.md、PRODUCT.md 与 surface brief；结论为 **fix**，无 P0。
- P1：更新说明是开发者语气 → v1.0.3 / v1.0.4 Release notes 改为骑手语言。
- P2：门有问题不够醒目、FAB 超出内容边距、详情滚动后丢失身份、设置存储读数与记录矛盾 → v1.0.4 已修并重拍。
- P3：客户标签占用信号绿、按钮圆角不一致、导入/导出图标同向 → v1.0.4 已修。
- 重拍后未发现新的 P0/P1；保留的 P3 为横屏顶部 app bar 仍按窗口全宽排布（Material 标准行为）。
- `impeccable detect` 对 native 源码返回空数组（该检测器只读 HTML/CSS）；实际门禁为模拟器截图验收 + native audit 清单。

## 6. 应用内更新 E2E

安装 v1.0.0 → 设置检查更新 → 发现最新 v1.0.2 → 下载（进度到 100%）→ 系统安装器 → 覆盖安装，数据保留；随后 v1.0.2 → v1.0.3、v1.0.3 → v1.0.4、v1.0.4 → v1.0.5、v1.0.5 → v1.0.6、v1.0.6 → v1.0.7、v1.0.7 → v1.0.8 均走完整链路，最终版本 **v1.0.8（versionCode 9）**。

- 发现并修复：在系统设置允许安装未知应用后，「我已允许，重试安装」原先只接受 `Ready` 状态，`NeedsPermission` 状态下点了没有反应。v1.0.3 同时接受两种状态，并新增 `InstallTargetTest` 回归测试。
- 更新弹层：下载 / 安装按钮使用安全琥珀；release notes 的 Markdown 标题和列表符号会被清理。
- 证据：`docs/screenshots/update-available.png`、`update-installer.png`、`update-installed.png`。
- 安装器与数据：覆盖安装使用同一签名（SHA-256 `48d7799e…`），安装后仍在列表看到旧版本创建的记录。
- 数据库迁移：v1.0.4 → v1.0.5 首次启动执行 Room v1→v2 迁移，新增 `remark` 列且旧记录保留；已在模拟器实测。
- v1.0.5 → v1.0.6 无数据库结构变化，覆盖安装后原有记录与媒体正常显示。
- v1.0.6 → v1.0.7 首次启动执行 Room v2→v3 迁移，新增 `climbNote` 列；旧记录保留，并在 release 包中补填爬楼说明、保存后详情正确显示。
- v1.0.7 → v1.0.8 覆盖安装成功，旧「Release Test」记录保留；本版本为安全/稳定性修复，数据库结构不变。

## 7. 证据目录

- `docs/screenshots/home-light.png` / `home-dark.png` / `home-font13.png` / `home-landscape.png`
- `docs/screenshots/form-edit.png` / `form-dark.png` / `form-font13.png` / `form-landscape.png`
- `docs/screenshots/media-empty.png`
- `docs/screenshots/list-thumbnail.png` / `thumbnail-zoom.png` / `thumbnail-zoom-dark.png`
- `docs/screenshots/detail.png` / `detail-dark.png` / `detail-media.png`
- `docs/screenshots/settings.png` / `settings-dark.png`
- `docs/screenshots/viewer-photo.png` / `viewer-video.png`
- `docs/screenshots/update-available.png` / `update-installer.png` / `update-installed.png`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

> 说明：全局规则要求网页/应用自动化优先 Playwright MCP；Playwright 只驱动浏览器 DOM，无法驱动原生 Android APK。本项目使用 Android 官方等价栈（Compose instrumentation + adb/uiautomator + 模拟器截图）做真实点击与断言。所有 UI 截图均实际查看并修复后复拍。

安全性、稳定性、可靠性和逻辑性的检查清单、发现与残余风险见 [`docs/AUDIT.md`](AUDIT.md)。

## 8. 已知限制

- 首版无云同步、账号、应用锁、Play 商店上架。
- 相机预览在软件 GPU 的 `screencap` 中为黑屏，但文件与播放正常。
- GitHub Actions 发布工作流需要用户配置签名 Secrets；v1.0.0 / v1.0.1 / v1.0.2 / v1.0.3 / v1.0.4 / v1.0.5 / v1.0.6 / v1.0.7 / v1.0.8 使用本地签名发布。
- 当前 GitHub 账号 Actions 因计费被锁定，CI 工作流已在仓库中禁用；本地测试、Lint、Mimosa 与发布已全部完成。
- 签名密钥丢失后无法覆盖安装新版本；README 已标注备份要求。
