---
name: 楼记 LouJi
description: 给骑手用的私人爬楼台账
colors:
  ink: "#121517"
  paper: "#F4F1EA"
  paper-raised: "#FBF9F4"
  safety-amber: "#FFC83D"
  amber-deep: "#6A4B00"
  signal-green: "#1F6B45"
  signal-green-night: "#6FE3A1"
  concrete: "#5A656B"
  alert-red: "#B3261E"
  night-bg: "#101214"
  night-surface: "#15181A"
  rule-light: "#CBC6B9"
  rule-dark: "#3F4143"
typography:
  display:
    fontFamily: "Roboto, Noto Sans CJK SC, sans-serif"
    fontSize: "48sp"
    fontWeight: 900
    lineHeight: "52sp"
    letterSpacing: "-1.2sp"
  headline:
    fontFamily: "Roboto, Noto Sans CJK SC, sans-serif"
    fontSize: "28sp"
    fontWeight: 600
    lineHeight: "34sp"
  title:
    fontFamily: "Roboto, Noto Sans CJK SC, sans-serif"
    fontSize: "20sp"
    fontWeight: 600
    lineHeight: "26sp"
  body:
    fontFamily: "Roboto, Noto Sans CJK SC, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  label:
    fontFamily: "Roboto, Noto Sans CJK SC, sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
    letterSpacing: "0.3sp"
rounded:
  stamp: "6px"
  chip: "8px"
  media: "10px"
  field: "12px"
  card: "16px"
  sheet: "24px"
spacing:
  xxs: "4px"
  xs: "8px"
  sm: "12px"
  md: "16px"
  lg: "20px"
  xl: "24px"
  huge: "32px"
components:
  status-stamp:
    backgroundColor: "{colors.signal-green}"
    textColor: "{colors.paper}"
    rounded: "{rounded.stamp}"
    padding: "6px 10px"
    height: "32px"
  primary-button:
    backgroundColor: "{colors.safety-amber}"
    textColor: "{colors.ink}"
    rounded: "{rounded.field}"
    padding: "12px 20px"
    height: "52px"
  record-card:
    backgroundColor: "{colors.paper-raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: "16px"
  filter-chip:
    backgroundColor: "{colors.safety-amber}"
    textColor: "{colors.ink}"
    rounded: "{rounded.chip}"
    padding: "8px 14px"
    height: "40px"
---

# Design System: 楼记 LouJi

## Overview

**Creative North Star: "楼道台账"**

楼记不是一张通用笔记卡片，而是一本骑手随身携带的楼道台账：一枚印章先告诉你「免爬 / 要爬」，一个巨大的楼层房号告诉你爬到哪，剩下的字段像运单一样一行行排好。Material 3 提供结构与交互，视觉取自骑手每天真正接触的东西——运单、楼层牌、手写记录和门口的安全标记。

整体气质是 **实用、直接、有一点工业感**。没有渐变、玻璃、光晕或装饰性阴影；信息靠字重、尺度和一条 1dp 的台账线分层。安全琥珀是唯一的行动色，信号绿只属于「免爬 / 会下来拿」，红色只用于删除和门锁异常。深色不是浅色的反相，而是夜班场景下的独立方案：墨色底、低对比边框、亮琥珀。

**Key Characteristics:**
- 一枚状态印章先于一切文字被看到。
- 巨型楼层/房号数字，tabular numerals，骑手在楼梯口一眼可读。
- 台账横线、方正的卡片、6dp 印章圆角；没有胶囊状的过度圆角。
- 一个页面只有一个主行动：新建在右下，表单保存固定在底部。
- 深浅色、1.3× 字体、横屏和平板宽度都要成立。

## Icon

**Mark: "楼梯尽头的门"**

深墨圆角方块上，琥珀色三级楼梯向右上升，顶端是一个纸色门洞。楼梯是骑手每天走的路，门洞是订单最终要到达的地方；纸色取自台账和运单，避免把门做成普通建筑图标。没有文字、箭头或钥匙孔，48px 下仍能读出「向上走 + 到达门口」。

- **Legacy / round:** 深墨底 + 琥珀楼梯 + Paper (#F4F1EA) 门洞。
- **Adaptive foreground:** 标记缩到 108dp 安全区中央（约 64dp 宽），背景使用 `ic_launcher_background` 深墨。
- **Monochrome:** 单色楼梯 + 门洞镂空，跟随 Android 13 主题图标染色；不能把纸色门洞画成实心，否则会变成一块白色。
- **In-app lockup:** 首页 app bar 使用 30dp 深墨圆角徽标 + 22×14dp 标记 + 「楼记」标题；设置页不重复品牌标识。
- **Files:** `design/icon/louji-master.svg`、`louji-round-1024.svg`、`louji-foreground-108.svg`、`louji-monochrome-108.svg`；`tools/icon/render.mjs` 输出全部密度。
- **Verification:** 48 / 192 / 512px 与模拟器 launcher 实机截图检查；小尺寸不得出现细线门框或糊成一团。

## Colors

混凝土纸色与夜墨色承担大部分面积，安全琥珀负责行动和注意，信号绿负责「免爬 / 会下来拿」。

### Primary
- **Safety Amber** (#FFC83D): 新建 FAB、表单保存按钮、选中 chip、决策印章。浅色下配近黑文字，深色下同样配近黑文字；它是页面上最亮的一块，面积受到控制。
- **Amber Deep** (#6A4B00): 浅色主题的 Material primary 文字色和描边色，用于文本按钮、开关边框和需要 4.5:1 对比度的小字。

### Secondary
- **Signal Green** (#1F6B45): 浅色下的「免爬 / 会下来拿」文字与图标。
- **Signal Green Night** (#6FE3A1): 深色下的同义色；确保在墨色底上可读。

### Tertiary
- **Concrete** (#5A656B): 次要说明、图标和三级文字；不抢状态色。

### Neutral
- **Ink** (#121517): 图标底色、深色主题基础色、正文。
- **Paper** (#F4F1EA): 浅色页面底色，带一点混凝土的暖灰。
- **Paper Raised** (#FBF9F4): 浅色卡片和表单表面。
- **Night BG / Night Surface** (#101214 / #15181A): 深色页面与卡片。
- **Rule Light / Rule Dark** (#CBC6B9 / #3F4143): 台账分隔线、未选中描边。

### Named Rules
**The One Action Rule.** 每屏最多一个高饱和琥珀行动；其余行动使用文字按钮或描边按钮。

**The Status Is Spoken Rule.** 状态永远同时有文字和图形/颜色；不允许只靠颜色表达「免爬 / 要爬 / 门有问题」。

## Typography

**Display Font:** Roboto Black（系统 Roboto，中文落到 Noto Sans CJK）
**Body Font:** Roboto Regular / Medium
**Label Font:** Roboto Medium，字距略开

**Character:** 数字要像楼层牌一样有分量，中文要像台账一样安静。数字使用 `tnum` 等宽数字特性，楼层、尾号、订单号在垂直方向上对齐。

### Hierarchy
- **Display (Black, 48sp/52sp, -1.2sp):** 详情页的楼层/房号读数；只有它是真正的「大数字」。
- **Headline (SemiBold, 28sp/34sp):** 页面标题与详情区块标题。
- **Title (SemiBold, 20sp/26sp):** 卡片位置名、表单标题、主要按钮文字。
- **Body (Regular, 16sp/24sp):** 台账字段值与备注；正文宽度由 760dp 容器控制。
- **Label (Medium, 12sp/16sp, 0.3sp):** 字段名、时间、状态印章、筛选 chip。

### Named Rules
**The Floor-First Rule.** 楼层/房号永远是页面上最大的数字；其他数字（订单号、尾号、时间）不超过 Title 级别。

## Layout

- 页面左右 20dp 边距；内容最大宽度 760dp 并居中，横屏 / 平板不拉伸。
- 首页是一个可滚动台账：品牌标识组合（30dp 徽标 + 楼记）→ 统计条 → 搜索 → 筛选 chips → 记录卡片；点击卡片进入「现场」媒体页，卡片右下「详细信息」进入完整字段；FAB 锚定在内容列的右下角，而不是窗口右边缘。
- 记录卡片的楼层区先显示「楼层 / 房号」小标签，再显示大号读数；标签在上、数字在下，像一块楼层牌，而不是把说明挂在数字下面。
- 有图片的记录在卡片头部右侧显示 64×84dp 订单截屏缩略图（10dp 圆角、1dp 描边）；点开是全屏深色放大层，点图片或返回键收起，不改变列表滚动位置。
- 现场页是 2 列媒体网格：头部重复状态印章、位置、大号楼层/房号、爬楼说明和备注；无附件时显示空状态和添加按钮。
- 表单是一个纵向滚动列：顶部爬楼决策 → 爬楼说明 → 订单（含「从剪贴板填入」）→ 楼层与门 → 客户 → 现场；保存按钮固定在底部并避开 IME 和系统导航栏。
- 详情是一页记录单：头部大数字 + 状态印章 → 台账行分组（含爬楼说明）→ 媒体网格 → 操作。
- 间距节奏 4 / 8 / 12 / 16 / 20 / 24 / 32dp；卡片间距 12dp，区块间距 20-24dp。
- 所有屏幕使用 edge-to-edge，并处理状态栏、导航栏、IME 和刘海 insets；不锁定方向。

## Elevation & Depth

**Flat by default.** 页面和卡片之间靠表面色差 + 1dp 边框区分，不用投影。FAB 使用 Material 默认的轻投影，因为它是悬浮于台账之上的唯一行动。弹层（bottom sheet、dialog）使用 Material 的 tonal elevation，不额外叠加光晕。

### Named Rules
**The One Surface Rule.** 卡片只声明一种边界：1dp 描边（或表面色差），不同时叠加大阴影。

## Shapes

- **卡片**: 16dp 圆角，1dp 描边；像一张压在桌面上的运单。
- **输入框 / 主按钮**: 12dp 圆角，高度至少 52dp。
- **状态印章**: 6dp 圆角 + 1.5dp 描边；不是胶囊，保留一点硬边。
- **筛选 chip**: 8dp 圆角；选中时用安全琥珀，未选中用表面色 + outline。
- **媒体缩略图**: 10dp 圆角；删除按钮为圆形浮层。
- **底部弹层**: 24dp 顶部圆角。

## Components

### Buttons
- **Shape:** 12dp 圆角，高 52dp。
- **Primary:** 安全琥珀底 + 近黑文字；表单保存和空状态 CTA。
- **Secondary / Ghost:** OutlinedButton 用于「编辑记录 / 再来一单」；TextButton 用于取消和次要选择。
- **Hover / Focus:** 触摸态用 Material ripple；不添加自定义悬停。

### Chips
- **Style:** 选中底为 Safety Amber (#FFC83D)，文字为 Ink (#121517)；未选中为表面色 + outline 描边。
- **State:** 只表达选择，不单独表达「成功/失败」；状态语义交给印章和字段值。

### Cards / Containers
- **Corner Style:** 16dp。
- **Background:** Paper Raised (#FBF9F4) / Night Surface (#15181A)。
- **Border:** 1dp outlineVariant，透明度约 65%。
- **Internal Padding:** 16dp；卡片之间 12dp。
- **Signature:** 列表卡片左上角是状态印章，接着是大号位置名；中部是巨型楼层/房号；下部是门/开门事实和时间。

### Inputs / Fields
- **Style:** OutlinedTextField，12dp 圆角，表面色填充；label 在边框上。
- **Focus:** Material 默认的 2dp 琥珀描边，不加 glow。
- **Error:** `isError` + 红色 supporting text；错误文案说明怎么改，而不是只说「无效」。
- **Helper:** 备注显示 `0/200`；位置显示常用位置 chips。

### Navigation
- 单 Activity + Navigation Compose；顶部 app bar 显示上下文，返回使用系统返回 / 预测性返回。
- 没有底部导航栏：首页和设置是仅有的两个一级目的地，避免为两项做底栏。
- 列表 → 详情用 slide + fade；表单 → 详情用 fade；尊重系统动画缩放。

### Signature Components
- **品牌标识组合 (Brand lockup):** 首页 app bar 的 30dp 墨色圆角徽标 + 22×14dp 楼梯门洞标记 + 「楼记」标题；图标与实际 launcher 图标同源，不另画一套。
- **爬楼决策 (ClimbDecisionSelector):** 三张等宽大卡片「不用爬楼 / 要爬楼 / 待确认」；选中后出现绿色 / 琥珀 / 中性印章色，并带轻触觉反馈。
- **状态印章 (StatusStamp):** 1.5dp 描边 + 6dp 圆角，文字 + 图标；列表里紧凑显示「免爬 / 要爬 / 待确认」，详情里显示完整词。
- **台账行 (LedgerRow):** 104dp 字段名 + 值（为 1.3× 字体预留）；分组之间是 1dp 横线和标题；字段值为空时显示「未填写」，不隐藏字段。
- **现场媒体页:** 点击列表条目后的默认落点；2 列网格、视频播放标识与时长；顶部「详细信息」图标进入完整记录。
- **剪贴板填入:** 「订单」区块右上角的文字按钮；只在用户点击时读取剪贴板，本地识别位置、订单号、尾号、楼层、房号和性别。已有字段不受影响，覆盖前弹窗确认；不联网、不写日志、不新增权限。
- **应用锁:** 设置页「隐私」区的可选开关；开启/关闭都需通过系统设备凭据确认。锁定页居中品牌徽标 + 解锁按钮，交给系统 Keyguard 界面，应用不保存密码。
- **归档:** 详情菜单的「归档/取消归档」；归档后从活跃列表移出，进入「已归档」视图。列表顶部「活跃/已归档」chip 切换。
- **回收站:** 列表顶部「活跃 / 已归档 / 回收站」三 chip 切换；删除记录改为软删除进回收站，回收站视图顶部有 7 天保留提示。回收站记录详情隐藏编辑入口，菜单提供「恢复 / 彻底删除」，底部提供「恢复记录」按钮；彻底删除为红色破坏性二次确认。回收站记录不参与搜索、筛选与排序，也不写入备份。软删除后列表底部弹 Snackbar「已移入回收站 / 撤销」，「撤销」即恢复，Snackbar 上移避开右下角 FAB。
- **排序:** 列表命令区「排序」文字按钮 + 下拉；最近更新 / 楼层 / 位置三种排序，楼层按数字大小、文本楼层排后。
- **分页:** 「最近更新」视图用 Paging 3 + Room `PagingSource` 按 `createdAt DESC, id DESC` 分页（每页 20 条）；楼层/位置排序与回收站/多选仍走全量列表。
- **日期范围筛选:** 列表筛选栏「日期」chip；点开下拉选近 7 / 30 / 90 天或「自定义范围」（系统日期选择器选起止日期，跨时区对齐到本地自然日）。选中后 chip 显示「日期：自 MM/dd」或「MM/dd – MM/dd」，旁边小叉一键清除；只作用于活跃/已归档，回收站不受影响。
- **加密备份:** 设置页「导出备份」先弹密码框（留空=明文 zip，填写则 AES-256 加密）；「导入备份」弹层含可选密码框。加密/明文互斥校验，密码只用于当次加解密、不落盘、不写入备份。加密使用 zip4j（AES-256，PBKDF2 派生密钥）。
- **统计:** 首页顶部统计条可点进「统计」页：全周期三数字（总记录 / 免爬率 / 会下来比例）、爬楼决策分布三格（要爬楼 / 免爬 / 待确认）、楼栋洞察卡片（位置 + 记录数 + 免爬 + 会下来）。归档与回收站记录不参与统计。
- **首次使用引导:** 首启显示一页说明：品牌 + 一句定位；「它会用到哪些权限，为什么」三行（位置 / 相机与麦克风 / 不打扰）；主按钮「开始使用」，次按钮「允许所需权限」（一次性预授权）。说明只出现一次（DataStore `onboardingSeen`），权限拒绝也不阻塞进入。
- **批量操作:** 列表右上角「多选」进入选择模式：卡片前置复选框、点击切换选中，顶栏变「已选 N 项」+「全选」，底部操作条「归档 / 取消归档」+「移到回收站」+「取消」，FAB 隐藏。回收站视图不提供批量操作。
- **合并重复:** 设置「数据」区「合并重复记录」；点击后弹预览（列出每组重复的位置+订单/楼层房号+条数），「合并」保留信息最全的一条、合并标签/备注/说明/附件，其余移入回收站；无重复时提示「没有发现重复记录」。
- **诊断日志:** 设置「数据」区「导出诊断日志」；本地脱敏日志（启动/备份/崩溃异常，消息去换行、不含客户字段），经 SAF 导出为 txt。未捕获异常经全局兜底写异常类名+堆栈，随后仍交系统崩溃。
- **备份提醒:** 设置「数据」区「每周备份提醒」开关（默认开）；超一周未导出备份时启动弹「备份提醒」（去备份 / 稍后），导出成功重置计时，仅本机判断。备份导出/导入前自动清理上次会话残留的临时文件（超 1 小时），启动时也清理一次。
- **订单截图 OCR:** 表单「订单」区「识别订单截图」文字按钮（识别中显示「识别中…」）；相册选图 → 本地 ML Kit 中文+数字模型（`text-recognition-chinese`）OCR → `ClipboardOrderParser` 解析 → 填入位置/订单号/尾号/楼层/房号/性别；已有字段先确认再覆盖。完全离线、不上传，中文模型加载失败回退拉丁模型。
- **爬楼说明:** 紧贴爬楼决策的自由文本，标题随决策变成「为什么要爬楼 / 为什么不用爬楼 / 爬楼情况说明」；最多 300 字，列表显示单行摘要，详情与现场页展示，搜索与备份包含。
- **备注:** 与「客户性格备注」分开的独立自由文本；在表单中单独分组，列表显示单行摘要，详情完整展示，搜索与备份包含。
- **订单截屏缩略图:** 列表卡片右上角的首张图片，ContentScale Crop，像夹在运单上的一张回执；点击打开全屏查看，放大层只有图片、右上关闭和底部「点图片关闭」提示。

## Do's and Don'ts

### Do:
- **Do** 让状态印章成为列表卡片第一眼看到的东西。
- **Do** 用 48dp 以上的触摸目标和至少 8dp 的间隔。
- **Do** 同时提供浅色和深色方案，并在 1.3× 字体下检查换行和遮挡。
- **Do** 在横屏 / 平板上把内容限制在 760dp 内并居中；FAB 跟随内容列。
- **Do** 用文字 + 图标 + 颜色共同表达状态。
- **Do** 为可交互控件提供读屏可读标签（`contentDescription`/文字），状态用文字而非仅颜色表达；搜索框、图标按钮、状态印章均已覆盖。
- **Do** 大字体（至 2.0×）下让次要文字单行省略（`weight` + `maxLines` + `Ellipsis`），保证操作按钮不横向溢出、内容可滚动。
- **Do** 使用 tabular numerals 处理楼层、尾号、订单号和时间。

### Don't:
- **Don't** 用纯色块或颜色单独表示「免爬 / 门有问题」。
- **Don't** 在卡片上同时使用宽阴影和 1dp 描边。
- **Don't** 把「未填」做成 48sp 的巨型标题；空值要降级显示。
- **Don't** 在表单里加入第二个悬浮主按钮；保存固定在底部。
- **Don't** 使用渐变文字、玻璃拟态、装饰性光晕或渐变背景。
- **Don't** 用 emoji 代替图标；图标统一使用 Material core 或同笔画的自绘 vector。
- **Don't** 把应用做成通用笔记卡片流；每张卡片都必须围绕「爬楼决策 + 楼层房号」组织。
- **Don't** 把「备注」和「客户性格备注」合成一个字段；前者是楼栋/交付信息，后者是沟通特点。
