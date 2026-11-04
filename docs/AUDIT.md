# 楼记 · 安全性 / 稳定性 / 可靠性 / 逻辑性审计

日期：2026-11-02
审计对象：楼记 v1.0.7（versionCode 8）及 v1.0.8 修复（versionCode 9，提交 `26ef529`）。v1.1.0（versionCode 10）只调整图标与页面布局；v1.1.1（versionCode 11）修复定位请求与权限处理并移除「有媒体」筛选 chip，不新增第三方定位 SDK 或网络目标；v1.2.0（versionCode 12）新增本地剪贴板订单识别，仅在用户点击时读取剪贴板，不联网、不写日志、不新增权限；v1.2.1（versionCode 13）为剪贴板读取异常兜底与 API 26 测试线程修复；v1.3.0（versionCode 14）新增可选应用锁，锁状态存 DataStore、使用系统 Keyguard 设备凭据、不自行保存密码；v1.4.0（versionCode 15）新增记录归档与列表排序，Room v3→v4 迁移新增 `archived` 列（31 JVM + 20 instrumentation）；v1.5.0（versionCode 16）新增回收站（软删除 + 7 天自动清理），Room v4→v5 迁移新增 `deletedAt` 列（31 JVM + 22 instrumentation + API 26 核心 8 项）；v1.6.0（versionCode 17）新增加密备份（zip4j AES-256，密码派生密钥、不落盘）与统计洞察页，无数据库结构变化（31 JVM + 27 instrumentation + API 26 核心 18 项）。均沿用本报告结论。
环境：Android Emulator `RiderClimbApi35`，API 35；JDK 17；Gradle 8.5；compileSdk/targetSdk 34

## 1. 结论摘要

本轮审计覆盖清单、组件与权限、GitHub 更新、zip 备份、媒体文件、Room 数据层、隐私文案和主要状态流。未发现可直接远程执行代码或直接外传记录的漏洞；发现并修复了 2 个高影响可用性问题、5 个中影响问题及若干低风险问题。

- 19 个 JVM 单元测试通过。
- 17 个仪器化测试通过（含新增的 Room v2→v3 迁移测试、超大 JSON 拒绝测试、合并去重回归测试）。
- Android Lint：0 error，20 个 warning（均为依赖版本、Manifest API 属性或库自身提示）。
- Mimosa 深度静态扫描：0 findings，63 packages；证据边界为 `static_only_no_runtime_execution`。
- v1.0.7 → v1.0.8 应用内更新已在模拟器完成，`versionName=1.0.8`、`versionCode=9`，旧记录保留。

> 本报告只证明本轮检查与测试覆盖到的范围。Mimosa 是静态扫描，未做运行时渗透或模糊测试；不宣称应用绝对无漏洞。

## 2. 检查清单

### 2.1 安全性

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 签名密钥 / 密码是否入库 | 通过 | `.gitignore` 忽略 `keystore/`、`keystore.properties`、`*.jks`；仓库无可用凭据字面量 |
| 组件导出面 | 通过 | 只有 `MainActivity`（launcher）导出；FileProvider `exported=false`，只暴露 `cache/updates/` |
| 明文网络 | 通过 | `android:usesCleartextTraffic="false"`；更新只使用 HTTPS |
| 更新下载地址 | 通过 | 初始与最终响应 URL 都校验 GitHub 域名；不允许非 HTTPS |
| 更新完整性 | 通过 | 有 `.sha256` 资产时必须成功取得并校验 SHA-256；缺失/无效则中止；安装包上限 250MB |
| 更新文件路径 | 通过 | 版本名过滤为字母、数字、`.`、`-`、`_`，限制 64 字符；写入 App 私有 cache |
| 覆盖安装 | 通过 | 交给系统安装器；Android 强制同签名覆盖；本地实测同签名升级 |
| 备份 zip 路径穿越 | 通过 | 拒绝 `..`、绝对路径、反斜杠归一化、NUL、越界 canonical path |
| 备份资源耗尽 | 通过 | 最多 20,000 entries、单媒体 500MB、JSON 16MB、总解压 8GB；JSON 在解析前拒绝 |
| 媒体路径删除 | 通过 | 只删除 canonical path 位于 App `media/` 或 `thumbs/` 下的文件 |
| 数据库查询 | 通过 | Room 参数化查询；筛选值来自枚举名；无拼接 SQL |
| 系统备份 / 云同步 | 通过 | `allowBackup=false`，backup rules 排除 root/file/database/sharedpref/external |
| 导出数据敏感性 | 部分通过 | zip 未加密，设置页和 README 已明确提示包含客户信息，需用户自行保管 |
| 隐私文案准确性 | 通过（已修） | 原文声称联网仅更新，但“使用当前位置”可能调用系统地理编码；v1.0.8 已补充说明 |
| 剪贴板读取 | 通过 | 仅用户点击「从剪贴板填入」时读取；本地正则解析、不写日志、不联网、无新增权限；覆盖已有字段前确认 |
| 硬编码密钥 / Token | 通过 | 源码只读取 BuildConfig 的 GitHub 仓库地址，无密钥 |

### 2.2 稳定性

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 相机权限拒绝 / 无相机 | 通过 | 权限门与错误提示；CameraX 绑定失败提示 |
| 相机拍照 / 录像初始化异常 | 通过（已修） | v1.0.8 对 `takePicture`、`prepareRecording`、`start` 加 try/catch，失败删临时文件并提示 |
| 录像最长时长 | 通过 | 2 分钟自动 stop；Finalize 清理/生成缩略图 |
| 页面退出时录制清理 | 通过 | `DisposableEffect` 停止录制、解绑 CameraX、释放屏幕常亮 |
| 保存中返回 / 放弃 | 通过（已修） | 保存中禁止放弃；`onCleared` 不在保存中删除待提交媒体 |
| 下载取消 | 通过（已修） | generation token 忽略取消后的回调；`CancellationException` 不转成错误弹层 |
| 编辑记录加载失败 | 通过（已修） | `initialize` 捕获异常并进入 `loadFailed`，不再无限转圈 |
| 启动时孤儿媒体 | 通过（已修） | 启动后清理 5 分钟前的未引用媒体，避免误删刚拍摄但未保存的文件 |
| 坏图片 / 坏视频文件 | 通过 | 查看器检查文件存在；缩略图生成失败返回 null；播放器释放 |
| 大字体 / 横屏 | 通过 | 1.3× 与横屏截图实际查看；无字段截断（长爬楼说明按正常换行） |

### 2.3 可靠性

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| Room 迁移 v1→v2→v3 | 通过 | `DatabaseMigrationTest` 验证 v2→v3；v1.0.4→v1.0.5、v1.0.6→v1.0.7 模拟器升级实测 |
| 备份往返 | 通过 | `BackupManagerTest` 导出/导入字段、媒体、标签 |
| 备份合并去重 | 通过 | 相同签名跳过；新增含 `|` 字段的不同记录不会被误判 |
| 备份失败不破坏旧数据 | 部分通过 | 导入在 Room 事务内；新复制媒体在提交失败时删除；导出到 SAF 失败仍可能留下部分目标文件 |
| 更新检查/下载/安装 | 通过 | GitHub Release API、SHA-256、系统安装器、同签名覆盖 |
| 附件顺序 | 通过（已修） | 统一按 `createdAt`、`id` 排序，缩略图/媒体页/备份顺序稳定 |
| 清除数据 | 通过 | 事务删除记录与附件，随后清理媒体目录 |
| 搜索 / 筛选 / 统计 | 通过 | DAO 参数化查询与仪器化测试 |

### 2.4 逻辑性

| 检查项 | 结果 | 证据 |
| --- | --- | --- |
| 楼层/房号第一眼 | 通过 | 列表大号 `placeDisplay`，空值占位 |
| 爬楼说明随决策变化 | 通过 | 标题 `为什么不用爬楼 / 为什么要爬楼 / 爬楼情况说明` |
| 备注与性格备注分离 | 通过 | 独立字段、独立 UI、独立备份/搜索 |
| 搜索覆盖 | 通过 | 位置、订单、尾号、楼层、房号、备注、爬楼说明、性格 |
| 合并去重键 | 通过（已修） | 由 `location|order|createdAt` 字符串改为 `Triple`，消除分隔符碰撞 |
| 导入字段截断 | 通过 | 位置 120、订单 60、备注/说明 300、标签 12×30、附件 20 |
| 附件数量上限 | 通过 | UI 与校验器均限制 20；备份导入截断 |
| 表单校验 | 通过 | 位置必填、尾号 4 位、字段长度、媒体上限 |

## 3. 发现与修复

| 编号 | 严重度 | 发现 | 修复 | 验证 |
| --- | --- | --- | --- | --- |
| F-01 | 高 | 备份导入允许单个 `records.json` 达到 500MB，`readText()` 解析可能 OOM | JSON 条目限制 16MB，解压阶段提前拒绝，`readJson` 二次校验 | `BackupManagerTest.rejectsOversizedJsonEntryBeforeParsing` 通过 |
| F-02 | 高 | 更新取消后阻塞下载回调可能把弹层重新拉回；校验文件获取失败会静默跳过校验；重定向最终 host 未复核；旧目标 APK 可能被误用 | generation token、`CancellationException` 透传、最终 URL 校验、校验文件必需、250MB 上限、sanitize 文件名、删除旧目标 | 代码审查 + v1.0.7→v1.0.8 实测下载/安装 |
| F-03 | 中 | 相机录像 setup 抛异常会导致页面崩溃（存储/相机状态异常） | 拍照与录像 setup 全部 try/catch，失败删除临时文件并提示 | `CameraFlowTest` 2 项通过 |
| F-04 | 中 | 保存进行中仍可放弃编辑，可能在数据库提交后删除已引用媒体 | 保存中禁止放弃；`onCleared` 保存中不删 pending media；启动孤儿清理有 5 分钟宽限 | 代码审查 + 全量测试 |
| F-05 | 中 | 备份导入提交成功后，清理/进度回调异常会进入 catch 并删除已入库媒体 | 只在复制/插入失败时删除新文件；提交后清理使用 `runCatching` | 代码审查 + 备份测试 |
| F-06 | 中 | 合并去重使用 `location|order|createdAt` 字符串，字段含 `|` 时可能误跳记录 | 改用 `Triple<String,String,Long>` | 新增合并回归测试 |
| F-07 | 中 | 隐私文案称联网仅用于更新，但定位按钮可能使用地理编码网络 | 设置页对话框、README 明确说明定位/地理编码可能解析坐标 | 1.0×/1.3× 对话框截图 |
| F-08 | 低 | Room `@Relation` 未排序，附件展示顺序不保证 | 数据层统一按 `createdAt,id` 排序 | 代码审查 + UI 测试 |
| F-09 | 低 | 进程被杀后的临时媒体可能长期残留 | 启动后台清理超过 5 分钟的孤儿文件 | 代码审查 |
| F-10 | 低 | 编辑页读取数据库异常时可能一直显示加载 | `initialize` 捕获异常并显示记录不存在/失败对话框 | 代码审查 |

## 4. 验证证据

```text
JVM 单元测试:          19 / 19 通过
仪器化测试 (API 35):   17 / 17 通过
Android Lint:          0 errors, 20 warnings
Mimosa deep scan:      0 findings, 63 packages
scanId:                scan-2026-11-02T07-17-11.135Z-3458fa7664f7
seal:                  sha256:c39332c8da899e88aa7269cf9cc259d4011de9bdb1fd1d1b2bcd9f17e42a788a
Release APK:           v1.0.8, versionCode 9, 3.9 MB, 已签名
签名证书 SHA-256:      48d7799eeff1bba3c3da9f63102365431ca6272042343ac9d8197113276525bd
应用内更新:            v1.0.7 → v1.0.8 安装成功，旧记录保留
```

自动化覆盖：

- `RecordFlowTest`：新建/详情/编辑/删除/搜索/筛选/备注/爬楼说明/缩略图放大。
- `CameraFlowTest`：CameraX 拍照、录像。
- `RecordDaoTest`：关系、级联、搜索筛选、统计。
- `BackupManagerTest`：往返、合并去重、覆盖、zip-slip、超大 JSON、分隔符碰撞。
- `DatabaseMigrationTest`：Room v2→v3 迁移。
- `BackupJsonTest` / `RecordValidatorTest` / `FormattersTest` / `AppVersionTest` / `InstallTargetTest`：JVM 层逻辑与序列化。

## 5. 残余风险与未覆盖项

1. **备份未加密**：导出的 zip 是明文，含客户信息；这是产品选择，已在 UI/README 提示。
2. **备份总量上限较高**：总解压上限 8GB，恶意或超大备份可能造成磁盘压力；已被上限截断，但未做压缩比检测。
3. **未做运行时渗透/模糊测试**：Mimosa 只做静态扫描，`library_source` / `callgraph` 证据边界仍在；未在真机、多 Android 版本、弱网、恶意媒体文件上做 fuzz。
4. **更新信任链**：依赖 GitHub TLS、仓库发布权限和 Android 同签名安装；未做证书 pinning，也未在应用内做 APK 签名校验（系统安装器负责）。
5. **导出原子性**：SAF 写入失败时可能留下部分 zip 文件，未实现临时文件+原子提交。
6. **错误反馈**：删除记录/附件的数据库异常没有统一用户提示；属于低频错误路径。
7. **CI**：GitHub Actions 因账号计费被锁定，当前 CI 工作流禁用；本轮验证为本地执行。
8. **Playwright**：原生 Android 无法由 Playwright 驱动，使用 Compose instrumentation + adb/uiautomator 替代。

## 6. 结论

v1.0.8 修复了本轮审计发现的 2 个高影响可用性问题和 5 个中影响问题，补充了备份上限、更新下载、迁移和去重的回归测试。当前自动化门禁全部通过；在上述残余风险明确的前提下，可以发布 v1.0.8。该结论不是“绝对安全”的证明。
