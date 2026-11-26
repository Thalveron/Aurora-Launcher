# Aurora Launcher

面向 Android TV 的可定制桌面，包含原生极简桌面、网页主题层及局域网 Web 控制台。

## 下载

在 [Releases](https://github.com/Thalveron/Aurora-Launcher/releases/latest) 下载 APK。当前版本 v0.4.59，已在雷鸟 65S515C PRO（Android 9）和 Android 11 电视盒子验证；其他型号需自行确认系统能力。

## 功能

- 原生桌面与网页主题，独立主题设置、共享背景库。
- 图片/视频背景（支持 4K，SurfaceView 硬件视频层）、屏保、背景音乐、歌词与频谱。
- 局域网 Web 后台：应用管理、截图、设备控制（部分能力需要额外系统权限）。
- 时间天气服务、中英文界面、USB 音乐关联。
- 全局无操作待机，可选 15/20/30 分钟，最后一分钟倒计时。
- 流量监控等设备功能，兼容性以实际设备为准。
- 电视“桌面信息”：动态后台地址、离线二维码、桌面更新。
- GitHub HTTPS 在线更新：测速、校验和、失败切换；自动检查默认开启，自动安装默认关闭。
- 全新安装会写入内置的桌面、屏保、全屏音乐设置及三套背景；已有安装保留现有设置。

## 首次打开后台

安装并启动桌面后，在同一局域网访问 `http://电视IP:8899/`，首次自行创建用户名和密码。登录后可在安全设置修改账号、密码、端口及路径。不要把管理后台直接开放到公网。

## 编译

需要 JDK 17 和 Android SDK 34。配置 `ANDROID_HOME`（或 `launcher/local.properties`）后执行：

    cd launcher
    ./gradlew testDebugUnitTest assembleDebug

输出：`launcher/app/build/outputs/apk/debug/app-debug.apk`。

发布者需自行保管签名密钥；仓库不包含签名私钥、ADB 密钥、位置或账号密码。当前构建使用本机 debug 签名，自行编译的 APK 通常不能覆盖其他发布者的 APK。

## 设备权限与主页接管

普通界面不依赖 root；系统控制、静默安装、跨应用按键、媒体读取等能力按设备权限开放。主页接管包含特定设备适配，不保证所有电视安装后都能设为系统 HOME，相关桥接源码位于 `launcher/tools/home-bridge-source/`。

## 源码与协议

基于 [Gathin23/CleanYourTV](https://github.com/Gathin23/cleanyourtv) 继续开发，保留原作者版权声明和 MIT 许可证，详见 [LICENSE](LICENSE)。字体许可见 `launcher/FONT-LICENSE-OFL.txt`。

此公开副本不包含私人开发提交历史、内部测试记录、用户上传的壁纸/音乐、Web 凭据或已登录设备数据。API 文档见 [docs/api.md](docs/api.md)。

## 发布更新

提交源码并完成设备测试后，使用 `launcher/tools/publish-release.py` 发布 APK 与校验文件，再更新 `updates/latest.json`。需要官方 GitHub CLI 授权；脚本不保存凭据。后续版本必须保持签名一致并递增 versionCode。
