# Aurora Launcher

面向 Android TV 的可定制桌面，包含原生极简桌面、网页主题层及局域网 Web 控制台。

## 下载

在 [Releases](https://github.com/Thalveron/Aurora-Launcher/releases) 下载 APK。当前为 v0.1.0 预览版本，主要在雷鸟 65S515C PRO（Android 9）验证，其他型号需自行验证系统能力。

## 已有功能

- 原生桌面与网页主题、独立主题设置、共享背景库。
- 图片/视频背景、全局屏保与背景音乐、歌词与频谱。
- 局域网 Web 后台、应用管理、截图与设备控制（部分能力需要额外系统权限）。
- 时间天气服务、中文/英文界面、USB 音乐关联。
- 全局无操作待机，可选15/20/30分钟，最后一分钟倒计时；播放检测与遥控按键取消。
- 流量监控等设备功能；具体兼容情况以实际设备为准。

在线更新、多线路自动测速尚未实现。此仓库先提供源码与手动下载，不代表已启用远程自动安装。

## 首次打开后台

安装并启动桌面后，在同一局域网访问 `http://电视IP:8899/`（把“电视IP”替换成实际地址）。
新安装默认端口8899、访问路径为空，首次自行创建用户名与密码（密码输入两次）。
登录后可在安全设置修改账号、密码、端口及路径。覆盖更新保留配置；清除数据或普通卸载重装通常重新初始化。
不要把电视管理后台直接开放到公网。

## 编译

需要 JDK 17、Android SDK 34。配置本机 Android SDK（ANDROID_HOME 或 launcher/local.properties），然后执行：

    cd launcher
    ./gradlew testDebugUnitTest assembleDebug

输出：launcher/app/build/outputs/apk/debug/app-debug.apk。

发布者必须自行保管签名密钥，仓库不包含签名私钥、ADB密钥、设备配置或账号密码。
当前构建使用本机 debug 签名；自行编译的 APK 签名通常不同，不能直接覆盖其他发布者的 APK。
发布构建的签名配置应在发布者本地或受保护的 CI 凭据中管理。

## 设备权限与主页接管

普通界面不依赖 root；系统控制、静默安装、跨应用按键、媒体读取等能力按设备权限开放。
主页接管包含针对特定设备的适配，不保证所有电视安装后即可设为系统 HOME。相关桥接源码位于 launcher/tools/home-bridge-source/。
不要照搬其他型号的系统组件停用方案。

## 源码与协议

基于 [Gathin23/CleanYourTV](https://github.com/Gathin23/cleanyourtv) 继续开发，保留原作者版权声明和 MIT 许可证，详见 [LICENSE](LICENSE)。
字体许可见 launcher/FONT-LICENSE-OFL.txt。此仓库不包含 Aurora 音乐（Chora 衍生版）源码或安装包；音乐软件将按其自身许可证另行整理。

此公开副本不包含私人开发提交历史、内部测试记录、用户上传的壁纸/音乐、Web凭据或已登录设备数据。
API 文档见 [docs/api.md](docs/api.md)。
