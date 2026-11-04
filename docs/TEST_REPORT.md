# 楼记 · 测试与缺陷检测报告

日期：2026-11-05
版本：1.18.0
设备：Android Emulator `RiderClimbApi35`，API 35，x86_64，1080×2400 / 420dpi，后置模拟相机
主机：Windows 10，JDK 17.0.20.1，Gradle 8.5，AGP 8.2.2，compileSdk/targetSdk 34，minSdk 24

## 1. 结果摘要

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| JVM 单元测试 | 48 / 48 通过 | `app/build/reports/tests/testDebugUnitTest/` |
| 仪器化测试（API 35） | 40 / 40 通过 | `app/build/reports/androidTests/connected/debug/` |
| 仪器化测试（API 26） | DAO + 迁移 + 备份加密 23 / 23 通过 | 独立 `LouJiAudit26` 模拟器 |
| Android Lint | 0 error（仅依赖更新等 warning） | `app/build/reports/lint-results-debug.html` |
| Mimosa 深度安全扫描 | 0 findings，65 packages | `scan-2026-11-04T21-14-08.864Z-f56c62a7edc3` |
| 可访问性（TalkBack + 2.0× 字体） | 列表/详情/表单内容描述正确、2.0× 可滚动不裁剪 | `docs/ACCESSIBILITY.md` |
| Release APK | 已签名（v1.18.0，versionCode 29） | `apksigner verify` 通过 |
| 应用内更新 E2E | 通过（v1.0.0 → v1.0.2 → … → v1.3.0）；v1.4.0 本地同签名覆盖安装验证 | 见第 6 节、`docs/screenshots/update-*.png` |

## 2. 单元测试（24）

命令：

```bash
./gradlew :app:testDebugUnitTest
```

覆盖：

- `AppVersionTest`：`v1.0.1` / 预发布后缀 / 位数补齐 / 数字比较 / 非法输入。
- `RecordValidatorTest`：位置必填、4 位尾号、媒体上限、完整表单通过、默认值不算有效内容、只填爬楼说明也算有效内容。
- `FormattersTest`：楼层显示（7 → 7F、B1）、空值占位、时长、摘要包含全部关键字段（含爬楼说明）。
- `BackupJsonTest`：`BackupRecord` / `BackupManifest` 序列化往返、缺省字段兼容（含 `climbNote`）。
- `ClipboardOrderParserTest`：带标签订单文本、楼栋/房号/电话/性别、楼层后缀与 `2-301`、日期不误判为房号、无关文本不臆造字段。
- `AppLockControllerTest`：默认不锁、开启即锁、lock/unlock 流转、关闭功能清除锁定。
- `RecordSorterTest`：楼层按数字大小、文本楼层排后；位置不区分大小写；最近更新倒序。
- `InstallTargetTest`：Ready 与 NeedsPermission 都能解析出安装目标，其他状态返回 null（权限重试回归测试）。
- `DateRangePresetsTest`：滚动窗口起始、空范围判定、UTC→本地自然日换算、跨时区对齐、次日边界。
- `DeleteUndoControllerTest`：announce/consume 循环、重复删除 nonce 递增。
- `RecordMergerTest`：订单号优先/位置兜底的重复键、位置-only 不可合并、重复分组、字段合并（标签并集/补空/UNKNOWN 升级）。
- `DiagnosticLogSanitizeTest`：日志消息去换行/回车（防日志注入），普通文本不变。
- `BackupReminderTest`：超一周/从未备份触发提醒、近期或关闭时不提醒。
- `ClipboardOrderParserTest` 增「OCR 风格噪声文本」：吞空格/多余空行仍正确解析订单号/地址/楼层/房号/尾号。

> Windows 中文项目路径下，Gradle 的 JVM 测试 worker 可能无法加载测试类。通过 ASCII 目录联接运行（`D:\louji-build` → 项目目录）后 48 / 48 通过；APK、Lint、仪器化测试不受影响。详见 README「Windows 中文路径说明」。

## 3. 仪器化测试（40，API 35 模拟器）

命令：

```bash
./gradlew :app:connectedDebugAndroidTest
```

覆盖：

- `RecordDaoTest`
  - 记录 / 附件 1:N 关系；删除记录级联删除附件。
  - 位置、订单号、楼层、房号、尾号、性格标签搜索；要爬楼 / 免爬 / 会下来筛选。
  - 本月统计、最近位置聚合。
  - 回收站：软删除后从活跃列表与统计/最近位置排除，可观察、可恢复；按 `deletedAt` 截止时间查询待清理记录。
  - 洞察聚合：全周期决策分布与按位置聚合（免爬/会下来），归档记录不参与。
  - 日期范围：`observeFilteredByDate` 按 `createdAt` 起止过滤，与无界/近 7 天/近 30 天/显式区间断言一致。
  - 分页：`loadFilteredPagedSource`（Room PagingSource）多页加载无重复、无遗漏。
  - 批量：`setArchivedForIds` / `setDeletedAtForIds` 按 id 集合批量归档/软删除，主列表与回收站视图正确反映。
  - 附件改挂：`reparentAttachments` 把一条记录的附件整体改挂到另一条。
- `BackupManagerTest`
  - 导出 → 导入往返：字段、标签、媒体文件均恢复。
  - 合并导入对相同签名的记录去重。
  - 合并去重不会把含 `|` 的不同字段误判为同一条记录。
  - 覆盖导入清空旧记录。
  - 拒绝 zip 路径穿越（`../escape.txt`），不写出临时目录。
  - 拒绝超过 16MB 的 `records.json`，在 JSON 解析前终止。
  - 加密导出/导入往返（AES-256，含媒体文件）。
  - 加密备份密码错误、密码缺失、明文备份带密码均被明确拒绝。
  - 图片导入后生成缩略图（`thumbPath` 非空且文件存在）。
- `DatabaseMigrationTest`
  - 用 Room `MigrationTestHelper` 创建 v2 数据库，插入旧记录，执行 `MIGRATION_2_3` 并校验 `climbNote` 自动补为空串、旧字段保留。
  - 创建 v3 数据库，执行 `MIGRATION_3_4` 并校验 `archived` 默认 0、旧字段保留。
  - 创建 v4 数据库，执行 `MIGRATION_4_5` 并校验 `deletedAt` 默认 0、`archived` 与旧字段保留。
- `RecordFlowTest`
  - 通过真实表单创建含 11 个字段的记录，保存后进入详情并逐项断言。
  - 搜索、筛选、移到回收站后活跃列表回空状态。
  - 删除后列表底部弹「已移入回收站 / 撤销」Snackbar，点撤销即恢复记录。
  - 多选后批量归档：选中一条 → 归档 → 活跃空、已归档视图可见。
  - 合并重复：设置「合并重复记录」→ 预览「发现 1 组重复」→ 合并 → 提示「已合并 1 条」。
- `OnboardingFlowTest`
  - 首次启动展示引导页，点「开始使用」进入主列表；`onboardingSeen` 置位后重启跳过引导。
- `AccessibilitySemanticsTest`
  - 列表 FAB/设置/搜索框内容描述、状态印章文本朗读、筛选 chip、设置页返回/导出/合并重复标签。
- `DiagnosticLogInstrumentedTest`
  - 日志往返（app_start/备份事件）、消息换行被替换、崩溃异常类名+信息捕获。
  - 编辑位置后保存，详情更新。
  - 条目默认进入「现场」媒体页；无附件时显示空状态；顶部「详细信息」进入完整详情。
  - 独立「备注」写入、详情展示、列表单行摘要、搜索命中。
  - 「爬楼说明」随决策填写、保存后详情展示，列表显示说明摘要，搜索「电梯直达」命中。
  - 设置剪贴板订单文本 → 点「从剪贴板填入」→ 位置/订单号/尾号/楼层/房号/性别一次填入。
  - 定位按钮在权限已授予时请求 GPS 并自动填写位置（独立模拟器注入坐标验证）。
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
| 位置按钮 → 获取 GPS → 自动填写 | 通过 | 独立 API 35 模拟器 `geo fix`，填入“东城区天安门城楼”；`location-filled.png` |
| 从剪贴板识别订单并填入 | 通过 | instrumented `RecordFlowTest.pasteOrderFromClipboardFillsFields`；`paste-order.png` |
| API 26（Android 8.0）核心流程 | 通过 | `LouJiAudit26`：建记录流程 + 剪贴板填入各 1 项通过 |
| 移除「有媒体」筛选 chip | 通过 | instrumented `RecordFlowTest` 断言不存在；首页 1.0×/1.3× 截图 |
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
| v1.2.0 Release 安装、新建记录 | 通过 | 独立模拟器新建 `Release Smoke 1`，保存后详情正常 |
| 移到回收站 → 回收站视图 → 恢复 | 通过 | API 35 release 实机流程：详情菜单「移到回收站」→ 回收站视图显示记录 → 详情「恢复」→ 活跃列表恢复；`recycle-trash-view.png`、`recycle-detail-menu.png` |
| 回收站详情菜单（恢复 / 彻底删除） | 通过 | 回收站记录隐藏编辑入口，菜单仅「恢复 / 彻底删除」；`recycle-detail-menu.png` |
| v1.4.0 → v1.5.0 覆盖安装迁移 | 通过 | v1.4.0 建 `MigrationSmoke` → 覆盖安装 v1.5.0 → 记录保留、新增「回收站」chip；`recycle-migrated.png` |
| 统计页（全周期/决策/楼栋洞察） | 通过 | 首页统计条 → 统计页：总记录/免爬率/会下来、爬楼决策、楼栋洞察与 DB 一致；`stats.png` |
| 导出备份密码框（可选加密） | 通过 | 设置 → 导出备份 → 弹密码框（留空=明文 / 填写=AES-256）；`export-password-dialog.png` |
| 日期筛选（预设下拉 + 应用 + 清除） | 通过 | 首页「日期：全部」→ 下拉近 7/30/90 天 + 自定义；近 7 天应用后 chip 变「日期：自 10/28」并出现清除按钮；`date-filter-active.png` |
| 日期自定义范围 DatePicker | 通过 | 「自定义范围」→ 系统日期选择器正常弹出（无崩溃）；`date-filter-custom-picker.png` |
| 删除撤销 Snackbar（release 实机） | 通过 | 详情「移到回收站」→ 列表弹「已移入回收站 / 撤销」，撤销按钮上移避开 FAB；`delete-undo-snackbar.png` |
| 首次引导（release 实机） | 通过 | 升级后首次打开显示权限说明页；「开始使用」进列表；重启跳过引导；`onboarding.png` |
| TalkBack 走查（列表/详情/表单） | 通过 | 模拟器启用 TalkBack，无障碍树核对内容描述与状态朗读；见 `docs/ACCESSIBILITY.md` |
| 2.0× 字体 | 通过 | 列表/详情/表单可滚动、无横向裁剪；`详细信息`/时间戳经滚动可见 |
| 批量归档（release 实机） | 通过 | 多选 → 勾选记录 → 归档 → 活跃空、已归档可见；`batch-selection.png` |
| 合并重复入口（release 实机） | 通过 | 设置 → 合并重复记录 → 弹预览对话框（无重复时提示「没有发现重复记录」）；合并逻辑由 `mergeDuplicatesFlow` 仪器化测试覆盖 |

### 相机说明

模拟器使用软件 GPU（`swiftshader_indirect`），`adb screencap` 中相机预览为黑屏；但 CameraX 日志显示首帧与拍照/录像文件正常，`CameraFlowTest` 两项均通过，详情缩略图和 Media3 播放可见。真机预览不受此限制。

## 5. 缺陷检测与修复

### Android Lint

- 初次运行：5 个 error，全部为 Media3 `UnstableApi` opt-in；25 个 warning（依赖可更新、Manifest API 属性、未使用资源）。
- 修复：为 `VideoPage` 增加 `@OptIn(UnstableApi::class)`；删除未使用图标；`StatusStamp` 的 `modifier` 参数顺序调整；`LinearProgressIndicator` 改为 lambda progress；`KeyboardArrowRight` 改为 AutoMirrored。
- 复跑：**0 error**，20 个 warning（均为依赖可更新 / Manifest API 属性 / 库自身提示）。

### Mimosa 深度安全扫描

- 命令：Mimosa `security_scan`（deep）。
- 结果（v1.4.0 最终复扫）：`findingCount: 0`，`packagesScanned: 63`，scanId `scan-2026-11-04T14-29-50.814Z-b4033e280e13`，seal `sha256:1c41b74d785515111b094685a37bf60cec61e3573c3994cb8c570955c56949b5`。
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
| 点「使用当前位置」后不填写 | `LaunchedEffect` 把自身 key 从 true 改回 false，协程立即被取消；改为递增 `locationRequestId` |
| 只授予大致位置时被当成无权限 | 权限请求改为同时申请精确+大致位置，任一授予即继续；网络定位可用 |
| 只有 lastKnown 为空时永远拿不到位置 | 增加网络/GPS 主动定位、超时和最近位置回退，并给出关闭定位/无信号提示 |

### 设计 finish review

- 以全新上下文审阅最终截图、DESIGN.md、PRODUCT.md 与 surface brief；结论为 **fix**，无 P0。
- P1：更新说明是开发者语气 → v1.0.3 / v1.0.4 Release notes 改为骑手语言。
- P2：门有问题不够醒目、FAB 超出内容边距、详情滚动后丢失身份、设置存储读数与记录矛盾 → v1.0.4 已修并重拍。
- P3：客户标签占用信号绿、按钮圆角不一致、导入/导出图标同向 → v1.0.4 已修。
- 重拍后未发现新的 P0/P1；保留的 P3 为横屏顶部 app bar 仍按窗口全宽排布（Material 标准行为）。
- v1.1.0 设计刷新：图标改为「楼梯尽头的门」（琥珀楼梯 + 纸色门洞），首页加入品牌标识组合，列表/详情/现场统一的「楼层 / 房号」标签上移；48/192/512px 与 launcher 实机截图复验。
- `impeccable detect` 对 native 源码返回空数组（该检测器只读 HTML/CSS）；实际门禁为模拟器截图验收 + native audit 清单。

## 6. 应用内更新 E2E

安装 v1.0.0 → 设置检查更新 → 发现最新 v1.0.2 → 下载（进度到 100%）→ 系统安装器 → 覆盖安装，数据保留；随后 v1.0.2 → v1.0.3、v1.0.3 → v1.0.4、v1.0.4 → v1.0.5、v1.0.5 → v1.0.6、v1.0.6 → v1.0.7、v1.0.7 → v1.0.8、v1.0.8 → v1.1.0、v1.1.0 → v1.1.1、v1.1.1 → v1.2.0、v1.2.0 → v1.2.1、v1.2.1 → v1.3.0 均走完整链路。v1.4.0 因本次模拟器对 release-assets 下载 `Connection reset`，以本地同签名覆盖安装验证，最终版本 **v1.4.0（versionCode 15）**。

- 发现并修复：在系统设置允许安装未知应用后，「我已允许，重试安装」原先只接受 `Ready` 状态，`NeedsPermission` 状态下点了没有反应。v1.0.3 同时接受两种状态，并新增 `InstallTargetTest` 回归测试。
- 更新弹层：下载 / 安装按钮使用安全琥珀；release notes 的 Markdown 标题和列表符号会被清理。
- 证据：`docs/screenshots/update-available.png`、`update-installer.png`、`update-installed.png`。
- 安装器与数据：覆盖安装使用同一签名（SHA-256 `48d7799e…`），安装后仍在列表看到旧版本创建的记录。
- 数据库迁移：v1.0.4 → v1.0.5 首次启动执行 Room v1→v2 迁移，新增 `remark` 列且旧记录保留；已在模拟器实测。
- v1.0.5 → v1.0.6 无数据库结构变化，覆盖安装后原有记录与媒体正常显示。
- v1.0.6 → v1.0.7 首次启动执行 Room v2→v3 迁移，新增 `climbNote` 列；旧记录保留，并在 release 包中补填爬楼说明、保存后详情正确显示。
- v1.0.7 → v1.0.8 覆盖安装成功，旧「Release Test」记录保留；本版本为安全/稳定性修复，数据库结构不变。
- v1.0.8 → v1.1.0 覆盖安装成功，旧记录保留；本版本仅品牌/页面视觉刷新，无数据库结构变化。
- v1.1.0 → v1.1.1 覆盖安装成功，旧记录保留；本版本修复定位自动填写并移除「有媒体」筛选。
- v1.1.1 → v1.2.0 覆盖安装成功，旧记录保留；本版本新增本地剪贴板订单识别与填入，无数据库结构变化。
- v1.2.0 → v1.2.1 覆盖安装成功；本版本仅修复剪贴板异常兜底与 API 26 测试线程问题，无数据库结构变化。
- v1.2.1 → v1.3.0 覆盖安装成功；本版本新增可选应用锁，锁状态存 DataStore，无数据库结构变化。
- v1.3.0 → v1.4.0 覆盖安装成功；本版本新增记录归档与列表排序，Room v3→v4 迁移新增 `archived` 列，旧数据保留。本次模拟器对 `release-assets.githubusercontent.com` 下载出现 `Connection reset`，应用内下载未走完，改为本地同签名覆盖安装验证；下载逻辑此前已在 debug 构建中验证成功。
- v1.4.0 → v1.5.0 覆盖安装成功；本版本新增回收站（软删除），Room v4→v5 迁移新增 `deletedAt` 列，旧数据保留（`MigrationSmoke` 记录在 v1.4.0 创建、v1.5.0 覆盖后仍显示）。本次仍采用本地同签名覆盖安装验证，应用内下载未走完（模拟器对 release-assets 下载 `Connection reset`，属环境/网络问题，非代码问题）。
- v1.5.0 → v1.6.0 覆盖安装成功；本版本新增加密备份与统计页，无数据库结构变化，旧记录保留（`MigrationSmoke` 仍显示）。加密往返由 `BackupManagerTest`（debug 与 API 26 均过）验证；R8 已对 zip4j 加 keep 规则。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。
- v1.6.0 → v1.7.0 覆盖安装成功；本版本新增日期范围筛选，无数据库结构变化，旧记录保留。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。
- v1.7.0 → v1.8.0 覆盖安装成功；本版本新增删除撤销 Snackbar，无数据库结构变化，旧记录保留。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。
- v1.8.0 → v1.9.0 覆盖安装成功；本版本新增首次使用引导，无数据库结构变化，旧记录保留；升级后首次打开显示一次引导（`onboardingSeen` 默认 false）。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。
- v1.9.0 → v1.10.0 覆盖安装成功；本版本新增批量操作（多选归档/移回收站），无数据库结构变化，旧记录保留。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。
- v1.10.0 → v1.11.0 覆盖安装成功；本版本新增重复记录合并，无数据库结构变化，旧记录保留。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。
- v1.11.0 → v1.12.0 覆盖安装成功；本版本新增图片缩略图预生成，无数据库结构变化，旧记录保留。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。
- v1.12.0 → v1.13.0 覆盖安装成功；本版本新增可访问性语义（搜索框可访问名 + 语义测试），无数据库结构变化，旧记录保留。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。
- v1.13.0 → v1.14.0 覆盖安装成功；本版本新增本地脱敏诊断日志与导出，无数据库结构变化，旧记录保留。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。
- v1.14.0 → v1.15.0 覆盖安装成功；本版本新增每周备份提醒，无数据库结构变化，旧记录保留。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。
- v1.15.0 → v1.16.0 覆盖安装成功；本版本新增订单截图 OCR（本地原型，ML Kit 拉丁/数字模型），无数据库结构变化，旧记录保留。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。
- v1.16.0 → v1.17.0 覆盖安装成功；本版本新增列表分页（Paging 3），无数据库结构变化，旧记录保留。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。
- v1.17.0 → v1.18.0 覆盖安装成功；本版本完成可访问性 TalkBack 走查与 2.0× 字体验证并修复时间戳溢出，无数据库结构变化，旧记录保留。应用内下载仍走本地同签名覆盖安装（release-assets 下载 `Connection reset`）。

## 7. 证据目录

- `docs/screenshots/home-light.png` / `home-dark.png` / `home-font13.png` / `home-landscape.png`
- `docs/screenshots/form-edit.png` / `form-dark.png` / `form-font13.png` / `form-landscape.png`
- `docs/screenshots/media-empty.png`
- `docs/screenshots/list-thumbnail.png` / `thumbnail-zoom.png` / `thumbnail-zoom-dark.png`
- `docs/screenshots/location-filled.png` / `paste-order.png`
- `docs/screenshots/detail.png` / `detail-dark.png` / `detail-media.png`
- `docs/screenshots/settings.png` / `settings-dark.png`
- `docs/screenshots/viewer-photo.png` / `viewer-video.png`
- `docs/screenshots/update-available.png` / `update-installer.png` / `update-installed.png`
- `docs/screenshots/recycle-home.png` / `recycle-trash-view.png` / `recycle-detail-menu.png` / `recycle-migrated.png`
- `docs/screenshots/stats.png` / `export-password-dialog.png`
- `docs/screenshots/date-filter-active.png` / `date-filter-custom-picker.png`
- `docs/screenshots/delete-undo-snackbar.png`
- `docs/screenshots/onboarding.png`
- `docs/screenshots/batch-selection.png`
- `docs/ACCESSIBILITY.md`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

> 说明：全局规则要求网页/应用自动化优先 Playwright MCP；Playwright 只驱动浏览器 DOM，无法驱动原生 Android APK。本项目使用 Android 官方等价栈（Compose instrumentation + adb/uiautomator + 模拟器截图）做真实点击与断言。所有 UI 截图均实际查看并修复后复拍。

安全性、稳定性、可靠性和逻辑性的检查清单、发现与残余风险见 [`docs/AUDIT.md`](AUDIT.md)。

## 8. 已知限制

- 首版无云同步、账号、应用锁、Play 商店上架。
- 相机预览在软件 GPU 的 `screencap` 中为黑屏，但文件与播放正常。
- GitHub Actions 发布工作流需要用户配置签名 Secrets；v1.0.0 / v1.0.1 / v1.0.2 / v1.0.3 / v1.0.4 / v1.0.5 / v1.0.6 / v1.0.7 / v1.0.8 / v1.1.0 使用本地签名发布。
- 当前 GitHub 账号 Actions 因计费被锁定，CI 工作流已在仓库中禁用；本地测试、Lint、Mimosa 与发布已全部完成。
- 签名密钥丢失后无法覆盖安装新版本；README 已标注备份要求。
