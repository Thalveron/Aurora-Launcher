# Aurora Launcher

Android TV 可定制桌面：原生极简桌面、网页主题层和局域网 Web 控制台。

## 下载

[最新版本](https://github.com/Thalveron/Aurora-Launcher/releases/latest) · 当前 v0.4.70

支持图片/视频背景（含 4K）、屏保、背景音乐、歌词与频谱、时间天气、应用管理和在线更新。全新安装会内置一套桌面、屏保、全屏音乐配置及三套背景；已有安装保留自己的设置。

## 使用

安装后按电视提示设为默认桌面。同一局域网访问 `http://电视IP:8899/` 打开控制台，首次使用自行设置账号和密码。

> 控制台只建议在家庭局域网使用，不要直接开放到公网。

## 编译

需要 JDK 17 和 Android SDK 34：

    cd launcher
    ./gradlew testDebugUnitTest assembleDebug

输出：`launcher/app/build/outputs/apk/debug/app-debug.apk`。

## 协议

基于 [Gathin23/CleanYourTV](https://github.com/Gathin23/cleanyourtv) 继续开发，保留原作者版权和 MIT 许可证，详见 [LICENSE](LICENSE)。接口文档见 [docs/api.md](docs/api.md)。
