# 楼记 · 测试与缺陷检测报告

日期：2026-11-02
版本：1.0.3
设备：Android Emulator `RiderClimbApi35`，API 35，x86_64，1080×2400 / 420dpi，后置模拟相机
主机：Windows 10，JDK 17.0.20.1，Gradle 8.5，AGP 8.2.2，compileSdk/targetSdk 34，minSdk 24

## 1. 结果摘要

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| JVM 单元测试 | 17 / 17 通过 | `app/build/reports/tests/testDebugUnitTest/` |
| 仪器化测试（API 35） | 12 / 12 通过 | `app/build/reports/androidTests/connected/debug/` |
| Android Lint | 0 error（仅依赖更新等 warning） | `app/build/reports/lint-results-debug.html` |
| Mimosa 深度安全扫描 | 0 findings，63 packages | `scan-2026-11-01T20-54-01.461Z-dd8d97b7a711` |
| Release APK | 已签名，3.8 MB | `apksigner verify` 通过 |
| 应用内更新 E2E | 待 v1.0.1 发布后补充 | 见第 6 节 |

## 2. 单元测试（17）

命令：

```bash
./gradlew :app:testDebugUnitTest
```

覆盖：

- `AppVersionTest`：`v1.0.1` / 预发布后缀 / 位数补齐 / 数字比较 / 非法输入。
- `RecordValidatorTest`：位置必填、4 位尾号、媒体上限、完整表单通过、默认值不算有效内容。
- `FormattersTest`：楼层显示（7 → 7F、B1）、空值占位、时长、摘要包含全部关键字段。
- `BackupJsonTest`：`BackupRecord` / `BackupManifest` 序列化往返、缺省字段兼容。

> Windows 中文项目路径下，Gradle 的 JVM 测试 worker 可能无法加载测试类。通过 ASCII 目录联接运行（`D:\louji-build` → 项目目录）后 17 / 17 通过；APK、Lint、仪器化测试不受影响。详见 README「Windows 中文路径说明」。

## 3. 仪器化测试（12，API 35 模拟器）

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
  - 覆盖导入清空旧记录。
  - 拒绝 zip 路径穿越（`../escape.txt`），不写出临时目录。
- `RecordFlowTest`
  - 通过真实表单创建含 11 个字段的记录，保存后进入详情并逐项断言。
  - 搜索、筛选、删除记录回空状态。
  - 编辑位置后保存，详情更新。
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
- 结果：`findingCount: 0`，`packagesScanned: 63`，seal `sha256:a56e23ee0f59f803ceb0bb03e1746611889e3424abef3f4d34780f793d6c199d`。
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

## 6. 应用内更新 E2E

（v1.0.1 发布后补充：安装 v1.0.0 → 设置检查更新 → 发现 v1.0.1 → 下载 → 校验 → 系统安装器 → 覆盖安装成功。附截图与版本信息。）

## 7. 证据目录

- `docs/screenshots/home-light.png` / `home-dark.png` / `home-font13.png` / `home-landscape.png`
- `docs/screenshots/form-edit.png`
- `docs/screenshots/detail.png` / `detail-dark.png` / `detail-media.png`
- `docs/screenshots/settings.png` / `settings-dark.png`
- `docs/screenshots/viewer-photo.png` / `viewer-video.png`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`

> 说明：全局规则要求网页/应用自动化优先 Playwright MCP；Playwright 只驱动浏览器 DOM，无法驱动原生 Android APK。本项目使用 Android 官方等价栈（Compose instrumentation + adb/uiautomator + 模拟器截图）做真实点击与断言。所有 UI 截图均实际查看并修复后复拍。

## 8. 已知限制

- 首版无云同步、账号、应用锁、Play 商店上架。
- 相机预览在软件 GPU 的 `screencap` 中为黑屏，但文件与播放正常。
- GitHub Actions 发布工作流需要用户配置签名 Secrets；v1.0.0 / v1.0.1 使用本地签名发布。
- 签名密钥丢失后无法覆盖安装新版本；README 已标注备份要求。
