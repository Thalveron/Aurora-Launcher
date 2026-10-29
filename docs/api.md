# Aurora Launcher API v1

本文是主题和控制台唯一的原生能力契约。只通过HTTP/WebSocket调用，不依赖Android桥、ADB命令或私有文件。
core/contract.json是本文的机器可读副本。六套正式主题尚未开始制作。

## 协议与鉴权
基址：http://<TV-IP>:8899/api/v1/；设备内：http://127.0.0.1:8899/api/v1/。
成功示例：{"ok":true,"data":{...}}；数组也直接放data中。
失败示例：{"ok":false,"error":{"code":"INVALID_ARGUMENT","message":"说明"}}。
字段只增不改，客户端忽略未知字段；改名、类型/语义不兼容升/api/v2/。
二进制素材、图标、封面、截图、MJPEG和WS握手不套JSON。
SDK AuroraAPI.request解包data；mockResponse提供原始信封以便验收。

LAN读取也需登录。本机127.0.0.1免密读展示数据；所有POST、系统能力、services、动作表需授权。
原生壳给WebView注入仅本机有效的HttpOnly会话，因此主题按钮可控制，普通本机进程不能控制。
控制台会话/兼容Basic；Origin须匹配Host，非LAN拒绝。
写请求（含本机）记IP/时间/路径/结果；不记录密码、凭据、朗读正文。WS预览写入也记日志。
占位先鉴权再返回HTTP501 NOT_IMPLEMENTED；已实现系统控制、BGM、services保留真实功能。
错误：401 AUTH_REQUIRED、400 INVALID_ARGUMENT、404 NOT_FOUND、409 CONFIRM_REQUIRED、
429 STREAM_LIMIT/RATE_LIMIT、501 NOT_IMPLEMENTED/UNSUPPORTED、503 UNAVAILABLE。

## 正式路由
| 方法 | /api/v1/后路径 | 参数/返回 |
|---|---|---|
| GET | time | 时间/日期/农历/节日/时区 |
| GET | weather | 统一天气和缓存状态 |
| GET | media | MediaSession信息 |
| POST | media/{play|pause|next|prev|seek|mode} | seek:{positionMs}；mode:{mode:0单曲/1顺序/2随机} |
| GET | media/queue | 当前队列 |
| GET | media/cover | JPEG二进制 |
| GET | apps | package/name/icon数组 |
| POST | apps/launch | {package}，专用启动兼容入口 |
| GET | status | 存储/内存/网络/蓝牙 |
| GET/POST | config | 写字段：theme/locale/consoleLocale/icon_fallback/system_backends |
| GET | themes | 主题及available |
| POST | themes/switch | {theme}，校验已安装清单 |
| GET | backgrounds | 只返回背景素材数组 |
| POST | backgrounds/upload | 原始图片/视频流；Content-Length、X-File-Name |
| POST | backgrounds/delete | {id,confirm:true}，保护正在用的素材 |
| GET/POST | config/appearance | 外观；POST局部更新 |
| POST | config/appearance/preview | 外观内存预览，30秒过期 |
| GET/POST | config/appearance/presets | {presets:[{name,values}]}，最多20项 |
| GET/POST | themes/custom-css | {css}，校验后保存 |
| GET | assets | 全部素材数组 |
| POST | assets/upload | 原始流，按类型限制大小并校验 |
| POST | assets/delete | {id,confirm:true} |
| GET | assets/file/{id} | 二进制，支持Range206 |
| GET | icons/{package} | 真实应用图标PNG |
| POST | apps/install | {id}，后台安装上传的APK |
| GET | apps/jobs | 安装任务数组 |
| GET | actions | 动作注册表；需登录 |
| POST | actions | {action,params} |
| GET | bootstrap | 初始化快照 |
| WS | events | snapshot及增量事件 |
| GET | system/capabilities | 后端及可用状态 |
| POST | system/connect | 一次ADB授权请求 |
| POST | system/key | {key:up/down/left/right/ok/back/home/volume_up/volume_down} |
| POST | system/standby/confirm | 一次性60秒令牌 |
| POST | system/standby | {confirmationToken}，二次确认 |
| GET | system/screenshot | JPEG单帧，需登录 |
| GET | system/screen.mjpeg | fps查询0.2～2；最多两名观看者 |
| POST | system/{wifi|bluetooth|display} | 打开系统设置 |
| POST | themes/open | 打开主题容器 |
| GET | spectrum | 真实bins数组，静音返回零 |
| GET | saver | {active} |
| POST | saver/{enter|exit} | 进入/退出屏保 |
| GET | inputs | 输入状态；unknown不等于断开 |
| POST | inputs/switch | {id}，已枚举或TCL白名单 |
| GET/POST | music/local/settings | coverEnabled/lyricsEnabled/lyricsUrls，Chora签名桥 |
| POST | music/local/scan | 后台扫描 |
| GET | bgm | 独立背景音乐状态 |
| GET | bgm/library | 音频数组 |
| POST | bgm/upload | 原始音频流；Content-Length、X-File-Name |
| POST | bgm/{play|pause|stop|volume|settings|delete} | 现有BGM保留；volume:{volume:0～100} |
| GET/POST | services | 天气/时间设置；需登录 |
| POST | services/{test|reset} | 测试/恢复默认 |
| GET/POST | voice | 朗读引擎/设置 |
| POST | voice/{speak|test} | {text}，限500字；test为明确试听 |
| POST | voice/stop | 停止朗读 |
| POST | auth/login | {username,password}，HttpOnly会话 |
| POST | auth/logout | 撤销会话 |
| POST | themes/upload | 501 NOT_IMPLEMENTED |
| POST | ai/chat | 501 NOT_IMPLEMENTED |
| GET/POST | icons | 图标包管理501 NOT_IMPLEMENTED |

system/debug-theme仅debug诊断，不是主题依赖。

## 动作注册与校验
GET actions返回action、parameters、permission、requiresConfirmation。
permission是能力类别，不是客户端可自报的权限；当前control/settings都要求登录。
注册表拒绝未知动作、缺失/额外参数、错误类型。第一批均可恢复，不需要二次确认。
待机仍走专用一次性令牌。未来声明需要确认的动作必须实现令牌验证，不可只改布尔值。

请求示例：
    {"action":"set_appearance","params":{"patch":{"focusStyle":"neon","focusColor":"#77e8efff"}}}
响应data为完整外观配置。launch_app响应data={}；switch_theme响应data为配置对象。

| action | params类型 | 权限 | 确认 |
|---|---|---|---|
| launch_app | {"package": "string"} | control | 否 |
| set_appearance | {"patch": "object"} | settings | 否 |
| switch_theme | {"theme": "string"} | settings | 否 |
| media_play | {} | control | 否 |
| media_pause | {} | control | 否 |
| media_next | {} | control | 否 |
| media_prev | {} | control | 否 |
| media_seek | {"positionMs": "integer"} | control | 否 |
| media_mode | {"mode": "integer"} | control | 否 |
| enter_saver | {} | control | 否 |
| exit_saver | {} | control | 否 |

launch_app校验包名和安装状态。set_appearance与配置接口共享白名单/范围校验。
switch_theme检查安装和清单。外观字段/默认值见core/appearance-defaults.json。
主题按钮用actions；纯网页弹窗关闭不属于原生动作；只读队列仍用GET。

## 代表性数据
时间：
    {"ok":true,"data":{"epochMs":1792800000000,"time":"12:00","date":"2026-10-24","weekday":"星期六","lunar":"农历九月十五","festival":"","timezone":"Asia/Shanghai"}}
没有播放器：
    {"ok":true,"data":{"available":false,"playing":false,"title":"","artist":"","positionMs":0,"durationMs":0,"lyrics":[],"lyricIndex":0,"lyricProgress":0,"reason":"没有活动会话"}}
有播放器可增加package/cover/state/mode/controls，lyrics为字符串数组、lyricIndex为当前行、
lyricProgress为0～1。controls声明play/pause/prev/next/seek/mode/queue/lyrics是否可用。
weather.available=false是有效业务状态；成功时current/daily保留统一天气字段。
status包含storage(total/free)、memory(total/available)、network(connected/wifi/ethernet)、
bluetooth(known，受权限影响其他字段可缺)。apps每项package/name/icon。
config.locale=zh-CN/en-US：电视桌面语言，作用于原生极简桌面和 WebView 主题；config.consoleLocale=zh-CN/en-US：网页后台语言。两项独立，不改电视系统及第三方应用。

## WebSocket
旧snapshot完整保留，事件只增不改；sequence递增但不保证断线无遗漏，重连读完整快照。
    {"type":"media.changed","sequence":20,"data":{"available":true,"playing":false,"title":"示例歌曲"}}
    {"type":"config.changed","sequence":21,"data":{"theme":"foundation","locale":"en-US"}}
    {"type":"apps.changed","sequence":22,"data":{"apps":[],"added":[],"removed":[]}}
media/config约1秒检查，apps约10秒扫描；安装/卸载最终推送差异，不承诺毫秒实时。
预览消息：{"type":"appearance-preview","patch":{"glassBlur":20}}。客户端50ms节流，
不等于电视延迟保证。内存预览30秒过期，松手POST config/appearance写入config.json。
本机只读连接不可发送预览控制。

## 语言和朗读
voice.config：enabled、language(zh-CN/en-US/en-GB)、engine、voice、rate(.5～2)、pitch(.5～2)、
volume(0～1)、readFocus。选择返回的引擎和离线声音；默认关闭自动朗读。
speak遵守开关，test是明确试听；不使用麦克风，不改系统音量，不隐式联网合成。
缺少引擎、声音或语言数据时展示原因。AI/语音识别仍属后续阶段。

## 兼容别名
- appearance → config/appearance，内部映射，不重定向。
- appearance/* → config/appearance/*，内部映射，不重定向。
- custom-css → themes/custom-css，内部映射，不重定向。
- library → assets，内部映射，不重定向。
- library/* → assets/*，内部映射，不重定向。
- apps/icon/* → icons/*，内部映射，不重定向。

其余原有路径继续保留，配置中的旧library素材URL保持可读。

## Mock检查
电脑预览共用v1信封、动作元数据、路由和核心字段类型。
tools/aurora-contract-check.cjs检查mock与可选实机快照；
tools/aurora-contract-device.py检查权限、动作、别名并恢复配置。
mock不执行真实播放/安装/待机。硬件能力差异属于业务状态，字段结构仍须符合契约。

## 本轮实机验收
正式路由及兼容别名、动作参数拒绝、占位错误码、本机只读与控制鉴权已验证。
语音使用F-Droid发行的eSpeak 1.52.0离线引擎，语音数据由其官方安装入口解压；
该引擎是独立应用，未打包进Aurora APK，不需要谷歌服务。
voice额外返回utterance.state/id/error，状态为idle/queued/speaking/done/error/stopped，
供客户端区分“请求已接收”和“实际合成完成”。不持久记录朗读内容。


## 静态主题资源
/themes/{id}/index.html、theme.css、theme.json、icons/以及/core/和/console/是静态文件，
不套JSON信封。控制台可以读取当前主题的theme.json声明，但不得访问Android桥或私有目录。
新返回的素材URL为/api/v1/assets/file/{id}，应用图标为/api/v1/icons/{package}；
旧URL仍可读。静态theme.json不包含服务凭据。
电视界面使用 locale，后台使用 consoleLocale；歌曲、歌词、用户自填文本及应用原始名称保持其内容。


## 验收结果（本次）
- 13组核心返回结构：mock与实机均通过同一契约；动作注册表完全对齐。
- 正式路径/兼容别名、白名单/参数拒绝、501占位、本机免密读和拒绝未授权控制均通过。
- WebSocket命名config.changed事件已实测；旧snapshot和预览继续保留。
- 原有52项个性化设备回归通过；debug/release编译和release vital lint通过。
- eSpeak离线引擎列出10个中英等声音，en-US和zh-CN均收到实际合成done回调；测试增益0。
- 界面中英切换已在电脑预览验证；默认保留中文、自动朗读关闭。测试恢复了原设置。
- 旧原生桌面尚待主题迁移；此语言层覆盖Aurora网页主题容器和控制台，不修改第三方软件。


### 固定原生主题（v1 增补）
GET /themes 的 legacy 已可用，附加 renderer:native、builtin:true、readOnly:true。主题 manifest 为 APK 固定资源，不接受外部覆盖。switch_theme 可选择 legacy；POST /themes/open 打开当前主题，legacy 进入现有 MainActivity，其他主题进入 WebView 容器。原生主题专属配置仍由原有接口管理，不把 WebView 外观设置错误套用到原生主题。后台统一入口 /console/ 转到 /console/settings.html#themes，旧分类 hash 保留。


### 日志清空
POST /api/v1/logs/clear：需登录，请求 {"confirm":true}；未明确确认返回 INVALID_ARGUMENT。返回 {"ok":true,"data":{"cleared":true}}。删除当前及轮转历史日志，并记录清空操作。清空不可撤销，控制台先弹窗确认，可先导出。应用管理集中 APK 上传安装；安全设置单独管理上传地址与密码。


### 电视桌面语言
原生桌面设置和桌面设置菜单提供 Language / 语言入口。配置 locale 持久化后通知前台桌面重建，后台语言不会被连带修改。英语下日期、农历数字、天气、设置及播放控制均本地化；应用名、歌曲、歌词和自定义问候语保留原文。


### 极简桌面背景库行为
原有背景库 image/video 上传只保存文件和缩略图，不修改桌面或屏保选择及 revision。library 列表按桌面选用项、独立屏保选用项、其余上传时间倒序返回；同一文件只出现一次。新增 ambientSelected 布尔字段，selected 原字段保留。手动 select/ambient-background 后下次读取立即反映置顶顺序。


3.9 历史行为：根路径曾自动跳转。4.0 已由下方统一 LAN 入口规则替代，固定根入口不再开放。背景库视频缩略图显示播放角标。


### 统一 LAN 入口（4.0）
局域网仅接受 /<安全设置路径>/ 下的 console/、api/v1/、core/、themes/、desktop/。旧根路径 /console/、/api/v1/ 等在 LAN 返回 404，127.0.0.1 原生主题路由不变。/<路径>/ 转入主控制台；desktop/ 仅接受主登录颁发的会话，不接受 Basic 独立登录。会话 HttpOnly、SameSite=Strict、Path 限定此前缀；路径或密码修改会撤销全部会话。路径在路由前拒绝百分号编码、反斜杠、重复斜杠、点段和绝对 URI，资源文件保留 canonical containment 校验。旧桌面 UI 需携带当前 theme 上下文，不匹配返回 409；非 legacy 主题不得访问其专属设置或写入原生配置。实时画面与 WebSocket 在会话失效后停止。

设备验证：launcher/tools/aurora-security-device.py 从设备读取现有凭据（不输出、不保存），检查统一入口、路径拒绝、会话、主题上下文和注销。运行需要已授权 ADB，测试最后恢复原主题。

### 桌面预览与截图库（4.3）
- GET /api/v1/system/screenshot：既有 JPEG 小预览，手动请求；记录操作。
- GET /api/v1/system/screenshots：主控制台登录后返回截图列表；每项 id、url、createdAt、width、height、sourceWidth、sourceHeight、upscaled、bytes。
- POST /api/v1/system/screenshots/capture：{"resolution":"4k"} 或 {"resolution":"native"}。返回保存的截图条目。4k 固定3840×2160，保持比例补黑；原生画面不足时 upscaled=true，绝不修改电视显示分辨率。
- GET /api/v1/system/screenshots/file/<id>：登录后读取 PNG；?download=1 单独记录下载日志。无此参数记录查看日志。
- POST /api/v1/system/screenshots/delete：{"id":"32位十六进制.png"}；仅删除私有截图库内对应图片与元数据，不接受路径。
截图管理接口要求主控制台会话，原生主题会话不可代替。最多50张或512MiB后拒绝新增，不自动删除图片。通过已有 ADB 截图，无新增Android权限；HDMI/受保护视频层可能黑屏。
所有读取、预览、拍摄、下载、删除及设置写入记录现有WebAudit（IP、时间、动作、结果）。
原生极简桌面：WebSettings 中 launcher:desktop_info_mode=0完整、1极简（时间、日期、今天天气），与 ambient:info_mode 屏保设置独立。

### 独立音乐显示（4.4）
原生主题 WebSettings schema：
- launcher:desktop_card_visible、launcher:desktop_lyrics_visible：桌面卡片和歌词独立布尔开关，未设置时继承旧 desktop_lyrics 值。隐藏音乐区保留布局占位，避免 RecyclerView 重新测量把应用移出视口；卡片隐藏时不接管遥控器焦点。
- ambient:spectrum_visible、ambient:lyrics_visible：屏保频谱和单行歌词独立开关。歌词居中位于频谱上方，颜色跟随全局文字。关闭频谱时停止其可视化刷新。
- ambient:lyrics：当前选择的屏保背景音乐的LRC文本，最多100000字符，保存到当前曲目私有配置；支持LRC文件导入（UTF-8，乱码时尝试GB18030）。无歌词则不显示，不读取音乐App的歌词。
屏保歌词位置基于AudioTrack已播放帧数，包含无缝循环区间映射；支持offset、多时间戳和增强LRC去标签（屏保仍只显示一行）。
这些设置沿用登录保护、字段校验和操作日志。电视端卡片设置同步使用两项独立选择。


### 独立主题 CSS 与自定义主题
所有接口保持 v1 的 ok/data 或 ok/error 包装，沿用控制台登录与操作日志。

- GET /api/v1/themes/css/{id}：读取指定主题 CSS，data 为 {theme, css}。
- POST /api/v1/themes/css/{id}：登录后提交 {css}，返回 {theme, css, applicable}。每个主题独立保存。极简桌面 legacy 仍为原生布局，applicable=false，保存的 CSS 暂不应用。
- POST /api/v1/themes/custom：提交 {name, base, css}，创建独立自定义主题，返回包含 id/name/base/custom 的主题声明。基础必须是已完成的网页主题；最多保存50项。
- POST /api/v1/themes/custom/delete：提交 {id, confirm:true}。当前正在使用的主题须先切换后删除。
- GET /api/v1/themes：固定主题不再包含 sidebar；末尾包含用户自定义主题。
- 原 themes/custom-css 与 custom-css 别名保留，读写当前主题 CSS。

示例：POST themes/custom 的请求为 {"name":"冰蓝","base":"foundation","css":":root { --accent: #55ddff; }"}。创建后使用 actions 的 switch_theme，再通过 themes/open 打开。CSS 不允许脚本或外部导入；原生主题不能作为 CSS 自定义主题的基础。

主题列表仅返回已可用主题，不再展示未制作占位。控制台将极简桌面与全部自定义 CSS 主题统一显示在上方卡片列表；foundation 保留在开发预览中作为自定义主题基础。

### 简约卡片主题
内置 cards 现为真实可用网页主题，含封面、歌词、媒体控制、四个应用快捷入口、全部应用弹层、天气、内存/存储占用与快捷设置。
新增动作 open_settings：参数 {kind: wifi|bluetooth|display}，权限 control，登录和日志规则不变；打开对应设置页面，不直接切换系统开关。
media_play 在无活动会话且已安装 Aurora 音乐时连接其 MediaBrowser 服务，再发送已有 DESKTOP_PLAY 请求；返回 pending=true，后续 media 事件提供结果。未安装时仍返回不支持。
播放器未提供队列或封面时显示明确的空状态，不模拟曲库、封面或百分比。电源按钮仍使用 standby/confirm 与 standby 的一次性确认凭据。

### 原背景库兼容
assets/backgrounds 列表同时包含原生桌面 backgrounds 目录中的已知图片与视频；id 使用 native- 前缀，source=legacy。不复制或搬动原文件，素材读取、轮播选择及 Range 视频播放沿用现有接口。原背景仅能作为 background 使用，路径严格限制为 BackgroundStore 已登记的文件。删除仍需登录确认，并保护桌面和屏保正在使用的素材。

背景素材增加 thumbnail 地址：GET /api/v1/assets/thumbnail/{id}，返回 JPEG 缩略图，沿用登录权限。控制台先显示缩略图，视频需点击预览才加载。所有 JSON 响应中的本地资源 URL 通过 JSON 结构处理添加安全路径，兼容 Android JSON 对斜线的转义；外部 URL 不变。

### 全主题共用背景管理
控制台 background 分类固定使用同一页面，不按当前主题切换 iframe；桌面/屏保上传、预览、删除和轮播选择集中于此。config/appearance 的 desktopSlides、saverSlides、slideSeconds、saverSlideSeconds 为统一配置；保存后原生极简桌面和网页主题都读取同一播放列表。saverSlides 为空时跟随桌面。旧背景分类入口重定向至主控制台 background 分类。


## 当前主题独立设置

- config/appearance 的读取、预览及保存均作用于当前主题。外观、焦点、布局、播放卡片、歌词、频谱及屏保显示配置随主题保存，切回主题时恢复。
- config/presets 和 bgm 播放配置（含独立音量、所选曲目）也按主题隔离。背景、音频、字体等素材文件仍共用一个素材库；背景选择与轮播顺序按主题保存。
- 初次进入主题时从原有设置迁移；原有配置保留。主题声明 defaultLayout 时使用该主题的组件布局，支持 layoutLabels、layoutPresets。
- 控制台写请求携带 X-Aurora-Theme: <页面加载时的主题ID>。配置、BGM 与动作请求发现主题已变化时返回 HTTP 409、THEME_CHANGED，避免旧页面覆盖新主题。兼容客户端可不传此头。
- WebSocket 外观预览消息可携带 theme 字段；主题切换后旧预览不继续作用于新主题。
- 公共配置响应只含当前生效的 appearance，不返回内部 themeProfiles。配置变动仍沿用既有事件格式。
- 控制台的“当前主题设置”集中提供外观与焦点、自由布局、音乐与频谱、屏保及 CSS 子菜单。原生极简桌面的原生专属配置仍由其现有设置页面管理；网页主题使用对应的独立配置。


### 简约卡片拖动布局
仅内置 cards 主题允许 layout.freeform。对象以区域名为键，每项包含 x/y/w/h，数值为整个电视画面的百分比，宽高至少 8%，且区块须位于画面内。其他主题提交该字段会被拒绝。省略 freeform 时沿用原网格布局。控制台拖动时预览、松手保存；恢复网格布局只移除此字段。


## 全局屏保（覆盖上文的屏保按主题说明）
所有 saver* 与 greeting* 外观字段统一存入 sharedSaver，兼容原 config/appearance 读写路径。首次迁移以当时正在使用主题的设置为准，不删除其他主题的历史数据。桌面背景、外观、布局、桌面频谱仍按主题保存。屏保背景为空时使用纯色，不再跟随各主题桌面背景。BGM 的所选曲目、音量、淡化及歌词绑定也统一保存；音频素材库共用。控制台统一使用“全局屏保”菜单。原生极简桌面读取共享的屏保配置与背景播放列表。
背景轮播保留当前画面直至新素材可显示；单段视频循环不按轮播间隔重新创建播放器。加载失败保留旧画面并延迟重试。

电视上的网页主题屏保背景由原生 PersonalBackground 渲染，支持原系统解码能力；WebView 仅叠加屏保信息，不再同时创建视频解码器。离开屏保释放原生播放器。电脑预览继续使用网页背景播放器。

### 桌面背景原生渲染
电视端桌面与屏保均由原生背景层渲染，网页只叠加内容和背景压暗。进入/退出屏保切换对应播放列表，同一素材不重新创建播放器；退到其他应用时暂停并释放解码资源。修改背景配置会刷新当前原生背景层。电脑浏览器预览仍使用 HTML 媒体。

### 屏保音乐上传限制
POST bgm/upload 接收原始音频文件，X-File-Name 为百分号编码文件名，需明确 Content-Length。单首上限256 MiB；音乐库仍限30首、合计500 MiB。支持ogg/flac/wav/mp3/m4a/aac/opus，电视端验证实际音频轨道。操作日志记录成功文件名及大小，失败记录文件名和具体原因。控制台上传提示独立保留，上传成功同时刷新试听与屏保选曲列表。


## 统一屏保页面
POST /api/v1/saver/preview（需登录）从任意主题打开内部 AuroraSaverActivity，返回 active 或 pending；调用方轮询 GET saver 确认就绪。enter_saver 动作同样使用统一入口。原生极简桌面的闲置计时也走此入口，停止使用旧的独立屏保呈现和播放器。当前所选主题不改变，任意键退出后返回原桌面。
共同屏保使用内置 foundation 文档和原生背景层，读取 sharedSaver 与共享 BGM；不加载当前主题的自定义 CSS，避免覆盖问候语及频谱。saverSpectrum.size 同时调整绘制区域宽高。开发诊断附带实际问候语与已应用的频谱参数，正式版仍禁止该诊断接口。


### 屏保控件与轻量页面
控制台保存结果合并到既有模型，保留各输入控件所绑定的对象，连续保存后继续修改仍有效。屏保呈现使用 themes/saver 专用文档，不启动桌面主题脚本；禁用系统窗口截图预览。绘制上限约25fps，字号、字体风格、位置、信息模式及频谱尺寸在运行时应用。BGM volume 写入后立即设置正在播放实例的实际增益；响应增加 actualGain 字段以验证音量应用。


### 屏保背景音乐的软件音量
BGM 音量由 PCM 处理器在送入音频输出前应用（16位PCM，不支持的格式停止播放，不绕过处理器）。ExoPlayer 音量仅用于淡入淡出包络，不再叠乘同一音量值。0 会产生全零采样；非零调节带约10ms平滑。bgm 状态附加 softwareGain、inputRms、outputRms、renderedSamples，便于验证实际采样数据。控制台拖动滑杆节流发送，串行合并到最后一个目标；保存音乐设置会等待音量更新。


## 屏保音量百分比（0.2.6）
bgm.volume 为 0～100 的整数百分比，volumeMax 固定为100，volumeUnit 为 percent，volumeMode 保持 software。softwareGain=volume/100，只衰减播放器 PCM 数据，不写入系统音量。systemVolume 保留真实电视音量用于诊断。
旧 volume_index 首次迁移为 round(index/原设备最大档位*100)，保存到 volume_percent 后不再随设备最大档位变化；新安装内置5%。电视静音时不会绕过静音。旧版遗留的系统音量恢复记录只做一次清理恢复。控制台支持滑杆及直接输入数字。



### Shared screensaver clock (v1 additive fields)
config/appearance: saverClockFont (native, audiowide, monoton, bungee, or an uploaded font asset ID), saverClockColor (HEX RGB/RGBA), saverClockSize (32–100 CSS px). These saver* fields are shared by all themes. Fonts are local assets with bundled licenses; changing them does not request a cloud font.

### BGM loop diagnostics (v1 additive fields)
GET bgm also returns loopMode (crossfade or gapless), loopCount, crossfading, loopPrepared. fadeMs > 0 prebuffers a second local decoder and overlaps the tail/head with equal-power fades; the overlap is capped to one quarter of a short track. fadeMs = 0 uses the player’s native repeat-one path. Exit stops/releases both outputs before the prior real TV volume is restored.


### Global weather/time and default fallback
All native/Web themes and screensavers read the shared services config. Empty weather.urls restores the default Open-Meteo adapter, headers, mapping and key while retaining the selected city/coordinates. Empty time.ntp or time.http restores that address list's built-in defaults. Config changes remain authenticated and audited.
GET /api/v1/services/cities?name=<city> returns a list of {name,region,country,lat,lon} candidates; choosing one updates the global city and its coordinates. Until a changed city's forecast arrives, no previous city's temperature is relabelled as the new city.


### Native minimal-theme music spectrum
The native music card has its own desktop_appearance card_style (0 bars / 1 wave / 2 mirror / 3 dots / 4 ring), card_color, card_color2, card_opacity (0–100), card_size (30–200), and card_angle (0–360). These are available in the existing authenticated desktop settings schema under the music section and never write shared saverSpectrum. Color/geometry changes apply on settings notification, without rebuilding the activity.

### 简约卡片应用区
GET/POST /api/v1/config/appearance 新增 appGrid（按当前主题保存）：

    {"appGrid":{"limit":8,"order":["com.example.music","com.example.video"],"hidden":["com.example.hidden"]}}

limit 为 1–60；order 为包名顺序，未列出的已安装应用随后显示；hidden 为隐藏的包名。每行数量使用 layout.columns（2–10）。简约卡片读取这些字段；不更改其他主题的应用显示。

### 原生桌面时钟
config/appearance 新增 desktopClockFont（native/audiowide/monoton/bungee 或上传字体 id）、desktopClockColor（HEX RGBA）、desktopClockSize（32–100 sp）。按主题保存，仅 legacy 原生桌面读取，与全局屏保 saverClock* 完全独立。POST 修改后原生头部即时应用。

### Moonlight 固定快捷方式
原生桌面通过既有默认主页桥接组件接受 Android CONFIRM_PIN_SHORTCUT 请求，需电视确认添加。仅保存系统接受的包名与 shortcut id，点击由宿主 LauncherApps.startShortcut 启动；不请求无障碍或设备管理员。源码镜像在 launcher/tools/home-bridge-source，当前仅原生主题显示快捷方式。

### 自动屏保动作
POST /api/v1/actions 的 enter_saver_auto 接收空 params；权限同 enter_saver。额外校验原生遥控空闲时间，最近有操作时拒绝进入；enter_saver 仍供手动试用，不受空闲时间限制。

### 全局自动待机
GET /api/v1/system/idle-standby：登录后读取 enabled、minutes（固定15）、available、idleSeconds、remainingSeconds、reason、wake。POST 同路径：{\"enabled\":true/false}，需登录，写入操作日志。默认关闭，各主题共用；读取系统最后操作时间，与屏保计时独立；调试连接不可用时暂停，失败不执行待机。启用或服务重启后重新计满15分钟。

自动待机范围修订：仅 Aurora 桌面/屏保在前台时累计空闲时间。其他应用与 HDMI 输入源使用期间暂停并重置计时。返回新增 scope=desktop_screensaver、paused 布尔值；不会因视频播放期间没有按键而待机。

### 应用流量监控（全局，需登录）
- GET /api/v1/network/traffic：enabled、intervalSeconds、historyEnabled、available、hasSample、rateReady、sampleAt、sampleAgeMs、sampleDurationMs、source、error、failures；total、interfaces[]、apps[]、connections[]、series[]。
- apps：uid、name、packages[{package,name,system,enabled}]、sharedUid；rxBytes/txBytes（系统本次启动累计，可能随网络服务重置）；rxPackets/txPackets；rxRate/txRate（字节/秒，初次或采样断档为 null）；sessionRx/sessionTx、peakRx/peakTx、lastActiveAt；connections/publicConnections/publicIpCount（不同公网IP数）、sustainedUploadSeconds、uploadHint。
- connections：uid、protocol、local/localPort、remote/remotePort、scope、state、txQueueBytes/rxQueueBytes。排队字节不是已传输流量。附加 firstSeenAt/lastSeenAt/observedSeconds（本次连续采样观测时间），reverseName/domainSource（可选PTR反查，不是原请求域名）；不读取请求正文或包内容。
- POST /api/v1/network/traffic/config：可写 enabled:boolean、intervalSeconds:2|5|10|30、historyEnabled:boolean、retentionDays:1|3|7|30、resolveDomains:boolean（默认false，显式开启后通过系统DNS反查，最多2个异步查询，缓存最多512项）。默认关闭；开启后独立后台采集，关闭网页不停止。首次采样建立基线，第二次开始计算速率。
- GET /api/v1/network/traffic/history?uid=-1&hours=24：uid=-1 是网卡总量；hours 支持1/24/168/720；返回 points[{time,rxBytes,txBytes,seconds}]，历史从开始监控后记录，默认保留7天，可选1/3/7/30天，约60秒批量落盘。
- POST /api/v1/network/traffic/export：空对象，返回当前快照并记录导出操作。
- POST /api/v1/network/traffic/clear：confirm:true，异步清空本功能历史、曲线、会话累计；不更改系统网络计数。
- 所有路径必须登录，包括局域网读取；配置、清空、导出写入操作日志。只读内核/系统网络统计，不使用 VPN，不修改防火墙，不执行封禁。
- 共享UID只能合并统计；排除lo。UID统计只汇总未打tag的总量，避免重复叠加tag明细；网卡与UID总量因采样口径不同不保证相等。连续≥32 KB/s上传≥60秒且有公网连接会显示待核查线索，绝不是PCDN判定。上传速率同时包含局域网通信。

监控记录每小时自动按 retentionDays 清理（采集运行时），手动清理同时清空本次连接观测与域名缓存，不清空操作日志。采样使用带随机令牌的本机只读辅助进程，闲置60秒退出。DNS查询不参与采样关键路径。公网IP是快照去重值，短连接可能漏过；共享UID不能据此识别具体子进程。

### 原生输入源快捷方式
GET /api/v1/inputs/shortcut 返回 {id,remoteHint,visible}，默认 EN_TCL_HDMI1、true、false。visible 为布尔值，仅控制应用区快捷方式，默认隐藏，不影响右上角菜单。POST 同路径接受部分更新，id 限已注册直通输入或 TCL HDMI1–4/AV1–2，remoteHint 为布尔值；需登录并写操作日志。保存不切换输入，下次点击原生应用区“输入源”一键切换；右上角选择菜单独立保留。切换在工作线程进行，成功后显示系统 Toast 遥控器图片提示（无需悬浮窗权限），可关闭；电视端成功/失败切换也记录日志。不同固件可能限制 HDMI 上的 Toast 显示。


### 控制台首次设置与安全设置
新安装默认 LAN 端口 8899、空路径，访问根地址进入登录页。未初始化时创建账号，两次密码须一致（8～64位）。默认桌面为 legacy（极简桌面）。旧安装沿用已有账号、路径、主题。

- GET /api/v1/auth/status（公开）：data.initialized。
- POST /api/v1/auth/setup（仅首次）：{username,password,passwordConfirm}，成功设置 HttpOnly 会话，data.needsPath 提示补充路径；已初始化返回 ALREADY_INITIALIZED。
- POST /api/v1/auth/login：{username,password}，data.needsPath。
- GET /api/v1/security（需网页登录或已开启免登录）：{username,port,path,initialized,openAccess}，不返回密码。
- POST /api/v1/security（需网页登录）：{username,port,path,password,passwordConfirm,openAccess}；密码留空保留，路径留空使用根路径；端口1024～65535，排除5555。返回 loginRequired、restartPending、loginPath。修改登录信息立即撤销旧会话，端口延迟重绑定；失败回退旧端口。
内部 WebView 固定使用 127.0.0.1:8899；自定义 LAN 端口后8899只监听回环。写操作记录操作日志，不记录密码。


### 全局无操作待机
GET /api/v1/system/idle-standby 返回 enabled、minutes（15/20/30）、available、idleSeconds、remainingSeconds、countdown、warningSeconds（60）、paused、reason、scope=global_except_playback、wake=power_key。
POST 同路径接受 {enabled,minutes}（均可选，沿用未传值）。修改后重新计时。任意全局遥控按键重置计时；其他应用空闲也计时；检测到外部播放暂停计时，桌面自身背景音乐/视频不豁免。借助已启用的主页辅助服务显示跨应用倒计时，无需额外悬浮窗权限。
POST /api/v1/system/idle-standby/preview 接受 {}，显示60秒预览，绝不执行待机。上述写操作需登录并记录日志。
最后一分钟显示计时、剩余进度环，最后10秒橙色，最后2秒淡出。有效按键取消且消耗本次按键，不改变下层应用画面/焦点。真正待机仍需电源键唤醒。


### 桌面信息与更新
- GET /api/v1/console/addresses：当前设备有效局域网管理地址列表，组合实时IP、当前端口和路径。二维码本机离线生成，不包含密码。
- actions.desktop_info：打开电视原生“桌面信息”，含“桌面设置”“桌面更新”。
- GET /api/v1/updates：当前版本、自动检测/安装开关、进度、维护权限、最新与上一版本信息。
- POST /api/v1/updates/config：{autoCheck:boolean,automatic:boolean}。自动安装仅在可维护、桌面空闲且未播放外部媒体时进行。关闭自动检测会同时关闭自动安装。
- POST /api/v1/updates/check：{}，检查固定GitHub仓库的HTTPS版本信息，原地址与加速源并行，选择有效的最高版本。
- POST /api/v1/updates/download：{}，下载并校验新版；phase=ready 后等待用户选择安装或取消，不直接安装。
- POST /api/v1/updates/cancel：{}，取消待安装版本，同时防止该版本被自动更新策略立即安装。
- POST /api/v1/updates/install：{}，确认安装已下载版本；没有待安装文件时兼容旧流程。再次验证 SHA-256、包名、SDK及签名，普通设备可能需要系统确认。
- POST /api/v1/updates/rollback：{confirmed:true}，恢复上一版本和更新前设置；没有可用恢复包或权限时拒绝。
- POST /api/v1/updates/clean/confirm：{}，获得60秒、当前会话/IP/清空操作绑定的一次性confirmationToken。
- POST /api/v1/updates/clean：{confirmationToken}。网页显示一次警告确认后获取并消费一次性票据；兼容旧 confirmationText 字段，不要求输入文字。先验证恢复快照空间和权限，再覆盖安装并清空本桌面数据。不删除U盘或其他App。完成后默认8899空路径，需要重新创建账号。
- GET /api/v1/updates/health：仅127.0.0.1且X-Aurora-Update-Token匹配短期私有握手才可访问，返回versionCode、ready。普通LAN与Web会话不能读取该握手。
更新接口（健康检查除外）要求主Web登录，写操作记录日志，不记录凭据和恢复快照内容。
安装或启动健康检查失败可回退，不等于能检测所有功能异常；掉电等系统级中断不能仅靠主APK保证恢复。

独立维护组件 com.gathin.tvhome.updater 没有界面、常驻服务或联网/录音权限；授权ADB按需启动维护进程。APK和设置快照保存在组件私有目录，保留当前回退点并清理不再使用的已完成任务。调试构建允许已授权ADB通过run-as访问恢复数据。实机验证覆盖正常安装、启动检查失败自动回退及账号/ADB密钥一致性；全新清空仅验证确认拦截，未清空生产设备。


## 0.2.1 内置配置迁移

首次启动按templateId应用一次内置桌面模板，新安装和旧版升级使用同一路径；旧done标记不会跳过新模板。初始化期间桌面显示准备提示，后台等待素材和设置初始化完成。账号密码、后台地址、城市经纬度、服务密钥和既有时区保留。之后配置读写仍使用本文档已有路由，无新增原生直连入口。

安全设置 openAccess 默认 false；主动开启后局域网免账号密码，但仍校验访问路径和来源。原账号保留，关闭开关恢复密码保护。

### 手动上传桌面更新（0.2.6）
POST /api/v1/updates/upload：已登录（或主动开启免登录）的局域网请求；原始 APK 请求体，必须有 Content-Length，1KB～512MB。验证包名、系统要求、签名、文件完整性和版本，允许同版本重装，拒绝降级。返回 {version,versionCode,sha256,bytes,uploadToken,expiresAt}，有效期15分钟。上传本身不安装。
POST /api/v1/updates/install-upload：{uploadToken,confirmed:true}，再次校验并消费上传票据，以普通 update 模式保留数据安装，不执行 clean。无维护权限时打开电视系统安装确认，有权限时沿用独立备份回退流程。POST updates/cancel 可取消待安装上传包。待确认上传包不被自动更新任务抢占。上传、安装和取消均记录日志。

## 0.3.1 测试版补充
- GET /api/v1/system/default-home：返回 isDefault、currentPackage、previousPackage、originalDisabled、canManage、canRestore、reason。控制类接口仍需登录（或主动开启的免登录模式）。
- POST /api/v1/system/default-home：{confirmed:true,disableOriginal:true}。验证 HOME 已设置成功才尝试 pm disable-user；仅限系统登记的原 HOME 包，保护本应用与系统设置/UI。使用当前 Android 用户，不写死用户0。返回实际状态和message。无ADB则打开系统HOME选择，不声称已停用。
- POST /api/v1/system/default-home/restore：{confirmed:true}，仅恢复此前记录且仍通过安全检查的原HOME包；默认主页仍为 Aurora。所有操作记录日志。
- time.syncSystem 的内置默认值为 false。升级初始化一次关闭旧模板引入的开启值；未获得 SET_TIME 或具备 CAP_SYS_TIME 的 Root 时，返回值强制false且UI禁用；保存true会拒绝。
- updates/check 仅查版本；updates/download 才下载，ready后确认取消或安装。缺少安装权限返回 install_permission 和 hasPendingInstaller，授权后再次调用 updates/install 复用已校验文件。
- network/traffic 新增 uidAvailable、connectionsAvailable、compatibilityNote、diagnostics。不可用的UID计数/速率返回null。无ADB也可开启基础监控，实际仅总量及本应用数据；完整数据需要ADB和系统支持。


### 应用管理（控制台第四轮）
- GET /api/v1/apps/manage：需登录；返回 data={apps:[],theme,mock}。每项包含 package、name、originalName、icon、customIcon、version、launchable、visible、rank、system、enabled、removable、canToggle、toggleReason。
- POST /api/v1/apps/manage：需登录及当前主题上下文；请求 package 加 **一个**属性：name（最多60字，空白恢复原名）、icon（128/256像素PNG的base64，解码后最多40000字节）、resetIcon:true、visible:boolean、direction:-1/1 或 enabled:boolean。enabled 还须 confirm:true。
- 名称、图标只更改桌面呈现，不更改安装包；隐藏与顺序沿用当前主题存储。禁用只对可控系统应用开放，保护主页、维护组件、系统UID、持久进程、设置、安装器、输入法与连接核心组件；重新启用是恢复入口。未连接本机调试时拒绝操作。
- 写入纳入操作日志。返回统一信封，data 与 GET 相同。原有安装、卸载和模块卡片接口继续保留。
- 电脑模拟返回同一字段结构；修改只作用于浏览器模拟数据，不能向电视发指令。
- 网页遥控器只新增 volume_up / volume_down 两个具名按键；任意数值、power 等依旧拒绝，待机继续使用独立确认票据。


## 新控制台接管接口（兼容迁移）

正式入口为 `/console/app.html`，加在安全设置的访问前缀后。根地址、`/console/`、`/console/settings.html`、旧 `desktop/?section=...` 在有效访问前缀内由服务端 302 跳转。配置了非空安全路径时，路径外的请求仍被拒绝，不能靠旧地址绕过路径保护。旧控制台 API 不再执行操作，返回 501 / NOT_IMPLEMENTED。

HTML 使用 Cache-Control: no-cache；console/core 的 JS、CSS 由内容摘要生成文件名，资源之间的引用同步替换。未带摘要的资源地址 302 到当前摘要地址。摘要资源使用私有 immutable 缓存，接口和媒体状态不缓存。未登录时先在服务端判断登录态，不下发控制台页面。生产控制台不加载模拟数据脚本；本机开发服务器仅在显式 mock=1 时注入。

所有以下 JSON 路由遵循 v1 的 ok/data 或 ok/error 结构。控制类 POST 需要登录（或用户已主动开启局域网免密），记录操作日志。未支持的能力返回 NOT_IMPLEMENTED，不能替换成模拟结果。

| 方法 | 路径 | 参数/返回 |
|---|---|---|
| GET | /api/v1/config/native | 仅极简桌面：fields 为明确设置白名单，包含 id/title/type/value/min/max/options；不返回安全配置 |
| POST | /api/v1/config/native | {changes:{"launcher:columns":5}}；先完整验证，保存、逐项回读，再广播刷新；返回 fields 和 verified:true |
| POST | /api/v1/config/native/reset-color | {id:"appearance:text"}；重置已声明的颜色，返回当前设置 |
| GET | /api/v1/apps/layout | 极简桌面的完整应用显示/固定/顺序列表 |
| POST | /api/v1/apps/layout | {apps:[{package,visible,pinned,...}]}，完整白名单列表；应用列表变化则拒绝 |
| GET | /api/v1/apps/cards | 应用模块卡片 package/label/caption/shown；其他主题返回 NOT_IMPLEMENTED |
| POST | /api/v1/apps/cards | {package,shown}，返回最新卡片列表 |
| POST | /api/v1/apps/uninstall | {package,confirm:true}；系统、设置、桌面、维护组件受保护；返回最新应用列表 |
| GET | /api/v1/music/usb/volumes | 已挂载 U 盘 id/label/total/free；字节单位；不接受任意目录 |
| POST | /api/v1/music/usb/upload | 原始音频请求体，Content-Length、X-USB-Volume、URL 编码 X-File-Name；单首≤1GB；成功为 {volume,name,bytes,verified:true} |
| POST | /api/v1/inputs/name | {id,name}；仅已枚举输入口，名称≤60字，留空清除；返回输入源列表 |
| GET | /api/v1/logs | 最近500条，time 为毫秒，含 ip/action/detail/status |
| GET | /api/v1/logs/export | 登录后返回 NDJSON 完整保留日志，二进制下载例外，不包 JSON envelope |
| POST | /api/v1/logs/clear | {confirm:true}；清空后保留清空动作本身的审计记录 |
| POST | /api/v1/bgm/builtin | {}；显式导入并选中内置钢琴，不隐式开始播放 |
| POST | /api/v1/bgm/delete | {id,confirm:true}；当前使用曲目不能删除 |

USB 上传采用临时文件、音频轨验证、同步落盘、原子改名；重复名称自动编号；中断清理未完成文件。返回前检查保存大小。原屏保 .audio 文件通过受限 ID 兼容读取，仍保存在原目录，不搬动、不丢失。

天气服务 time 配置新增可选 hour24 布尔值；旧配置缺少此字段时沿用原设备的时间格式，保存后统一控制各主题显示。syncSystem 权限要求保持不变，hour24 不依赖系统校时权限。

写入确认：原生字段由服务端逐项回读；控制台对配置、外观、背景音乐、语音、输入源快捷方式、服务地址、待机和流量设置另发 GET 校验。安装仅返回任务排队时显示等待，通过 apps/jobs 的 done/failed 才显示最终结果。安全设置改变路径/端口/登录态后必须跳转新地址重新认证，不复用旧路径会话。

bootstrap 新增 console（正式入口）；legacyConsole 字段继续保留为兼容别名，指向同一正式入口，新页面不依赖该别名。


## 1.0.0 首次访问、城市搜索与更新策略

- POST /api/v1/auth/open-setup：仅首次未初始化访问方式，局域网且同源；参数 {"confirmed":true}（标准 JSON）。成功 data={openAccess:true,needsPath:boolean}；已有账号或免密配置返回 ALREADY_INITIALIZED，缺少确认返回 CONFIRM_REQUIRED。写入操作日志，不产生默认密码。
- 安全设置在首次免密状态允许留空用户名；关闭免密前必须同时设置用户名及8～64位确认密码。旧账号不受首次免密接口影响。
- GET /api/v1/services/cities?name=上海：先匹配内置中国城市别名与地区、经纬度、时区，无本地结果再查询原全球在线源；响应结构不变。GeoNames 数据归属见 assets/geography/NOTICE.txt。
- POST /api/v1/updates/config：autoCheck/automatic 为布尔值。网页通过统一保存栏提交，保存后 GET /updates 回读；关闭检查会同时关闭自动安装，开启自动安装会开启检查。设备不具备维护能力时，自动安装禁用并说明原因，手动下载与系统确认安装仍可用。
- 更新来源保持 GitHub 原地址与 https://gh-proxy.com/ 加速地址。元数据并行请求并选最高有效版本，安装包线路测速择优，失败自动尝试另一条；校验包名、SHA-256、大小与原签名。
- 登录页由服务端直接输出正确表单，无中间“正在读取登录状态”画面；HTML no-cache，JS/CSS 内容版本化。


## 1.2.6 日志保留与语音参数

- GET/POST /api/v1/logs/settings：{retentionDays:0|1|3|7|15|30}，0永久保存（默认保留已有日志，不自动覆盖）；有限期限按记录时间定期清理，修改需登录且写审计。列表仍只显示最近500条，导出流式发送全部保留记录，避免整份日志装入内存。
- voice.volume：允许0～3，0静音、1原始幅度，超过1通过独立TTS音频会话的LoudnessEnhancer增益，最高约9.54dB。不修改系统音量；设备不支持时明确返回暂不支持。
- POST /api/v1/voice/test：{text,parameters?:{volume,rate,pitch,language}}。可直接试听未保存参数，不写入或覆盖正式语音配置；语音引擎和指定声音仍使用已保存设置。
