# Console component migration

The console uses shared `shell.css`, `components.css` and `components.js`.
The same assets are mounted into authenticated, same-origin native category
frames before those frames become visible. No alternative public login or API
namespace is introduced.

## Navigation

| Group | Pages |
| --- | --- |
| 外观 | 主题管理、当前主题设置、背景库、全局屏保 |
| 内容 | 天气与时间、U 盘歌曲、语言与朗读 |
| 设备 | 应用管理、连接与输入源、遥控与设备、桌面预览、应用流量监控 |
| 系统 | 桌面更新、安全设置、操作日志 |

All 15 entry IDs and existing API routes are retained. Current theme settings
keep their appearance/layout/music/compatibility subcategories. The device
group intentionally has five items, following the approved revised grouping.

## Components and tokens

Setting rows preserve their actual input/button nodes and event listeners.
Dynamic field insertion is enhanced by an idempotent observer; controls are not
cloned. Existing native field rows are adapted to the same copy/control slots.
Cards, headings, folds, status badges, editor rows, command toolbars and media
galleries share one visual system. Screen/music displays retain their own
content previews, not a separate page stylesheet.

Typography: page heading 20px, card heading 16px, row title 14px/600,
description 12px. Standard control widths: text 224px, number 88px, select
184px, with shared smaller tokens on narrow screens. Bottom reserve is 144px
(160px on narrow screens), beyond the fixed save bar.

## Save boundaries

The shared draft manager handles weather/time, appearance, layout, backgrounds,
screensaver presentation, music binding, music configuration, language, voice,
USB metadata services, input shortcut and security forms. Embedded native
setting fields use the same manager. Drafts survive category navigation within
the console; the active frame takes over rendering retained native drafts.

Editing does not write these settings until Save. Undo restores the model and
controls and reverts temporary appearance preview. All groups validate before
the first write. Successful groups are acknowledged independently, so a retry
does not repeat earlier successful writes. Security saves last because port or
path changes can restart the server. Passwords remain memory-only and are
cleared after successful security saving.

Audition volume, player/remote commands, input switching, monitoring controls,
and installation/capture/delete actions retain their existing immediate action
and confirmation behavior. They are not disguised as deferred settings. No
installation, uninstall, rollback or clean-install confirmation was removed.

## Verification and limits

The shell, weather schema/validation, city/timezone, system-time permission,
v1 contract, serialized volume, APK upload confirmation and login-state checks
pass. Shell regression checks include security-last ordering and restoration
of permission-disabled controls after failed saving.

Local browser checks covered all 15 routes, shared row creation, light/dark
display, screen-saver Save/Undo and language Undo. Computed widths and title
sizes match the tokens, with no viewport horizontal overflow. Application,
USB and log pages depend on real TV data; the computer mock explicitly says
so rather than presenting an empty page or pretending to install software.

This source change has not been packaged into an APK, deployed to either
device, or published. Actual native-category controls and device operations
still require on-device verification with the packaged build.

## Second and third refinement rounds

The control column is now 280px. Text/select fields are 240px, numeric controls
88px, sliders 152px; units are separate spans so numeric input values remain
valid numbers. Help text describes effects; constraints remain in min/max and
tooltips. Colors display six-digit RGB, with alpha retained independently in
the existing eight-digit stored format. No config/schema migration is needed.

Appearance is composed into font, color, focus and ambient folds. Presets use a
dropdown, Save as preset and overflow menu (import/export/confirmed reset).
Theme cards have local SVG layout thumbnails, usage badges and button-like
settings links. These thumbnails are layout illustrations, not captured TV
screenshots.

Theme settings use a sticky right preview on wide screens and a collapsible
sticky top preview on narrow screens. The existing real TV screenshot stream
is stopped while collapsed/hidden. The computer's card-theme iframe receives
the same appearance messages, and preview-only draft pinning prevents its
periodic API refresh from overwriting unsaved slider edits. Normal desktop
rendering is unchanged. The native theme has a labelled illustration in the
computer mock; real native TV frames still require a screenshot-capable
connection.

Backgrounds retain one asset library with separate desktop/saver tabs. A shared
upload drop zone delegates to the original sequential upload route and progress
handler. Upload never selects a background automatically. Multi-selection,
manual apply, drag/keyboard ordering and confirmed deletion reuse the existing
appearance and asset endpoints. Successfully deleted pending selections are
removed from the draft even if a later item in a batch fails; server protection
for in-use assets remains authoritative.

Screensaver settings are greyed/disabled when switched off; prior disabled
capability states are preserved when re-enabled. The heading explicitly says
all themes share these settings, and folds show compact summaries.

Verification: nine regression scripts pass, including selection/order,
cancelled deletion, upload sequencing/progress/failure restoration, RGB/alpha
separation and preview source/origin gating. Local browser checks covered
1440px sticky and 390px mobile layouts, no horizontal overflow, saver
disable/re-enable/undo, background tabs/multi-selection/order using temporary
fixtures, theme cards, colors and unsaved preview persistence (31px) followed
by Undo. Temporary fixtures were restored. Browser viewport overrides were
reset after checking. This round is source-only, not an APK/device update.


## 设备页第四轮
- device-page.js 移动原有控件到连接、遥控与画面、诊断三个分页签，保留原ID与默认桌面/待机处理。
- 遥控按键仅在遥控分页签响应方向键与回车；编辑输入框、分页签和其他按钮时不截获按键。按键请求串行防重入，待机始终使用独立确认票据。
- 画面使用原有共享MJPEG采集任务；切换分页签、离开设备页、隐藏页面和卸载页面都会移除画面源。截图复用桌面预览图库，提供下载及全屏。
- apps-page.js 网格调用正式 apps/manage 接口；名称和图标为桌面覆盖值，显示与顺序按当前主题存储。安装、卸载与模块卡片保留在折叠工具区，按需挂载原界面。
- 模拟数据仅保存在浏览器；设备控制置灰并以浅黄色说明原因。状态徽章、圆形按键、应用网格均使用 components.css 中的公共令牌，支持明暗和窄屏。


## 流量监控第五轮
- 首屏保留状态与主开关、四张等宽汇总卡、双线趋势图和应用排行。采样/历史保留/反向解析/导出/清空移至右侧设置抽屉，设备采集及 v1 接口保持不变。
- drawers.js 提供共用侧栏抽屉和页面内危险操作确认，支持关闭、返回焦点和 Escape。清空仍需第二次确认；取消绝不发请求，确认后发送原 confirm:true。不再依赖浏览器原生确认框。
- traffic-model.js 统一单位、未知值、筛选、排序与历史速度换算；缺失统计显示灰色破折号，有效零值保留单位。列头和筛选下拉使用同一排序状态。
- line-chart.js 提供坐标轴、图例、指针/键盘提示和断点绘制；真实采样间断不补线。详情历史请求使用序号校验，避免旧应用的迟到结果覆盖当前应用。
- 应用图标与行元素复用，避免每次轮询重新请求所有图标。应用详情保留全部包名、峰值、共享 UID 提示、历史及 IPv4/IPv6 连接、公网 IP 数和反向解析信息。
- 模拟模式提供9个应用、连续曲线、共享 UID、空闲/上传线索及连接示例。示例 IP 使用文档保留地址；不执行真实解析。反向解析默认关闭，暂停后快照冻结，清空仅重置模拟历史与采集累计。
- 旧 day.css 流量页样式已移除，统一使用 components.css 的设计令牌与共用组件。
