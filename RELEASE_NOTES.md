Aurora Launcher v0.4.74

- 从 HDMI/盒子信号源切回电视桌面时，窗口重新获得焦点后强制重建背景 SurfaceView，修复视频背景不显示。
- 重建做了 1.5 秒节流，避免普通焦点变化造成闪烁。
- 保留诊断接口 /api/v1/debug/background。
