Aurora Launcher v0.4.92

- WebSocket 设置快照从每 50ms 序列化一次改为每 1 秒一次，外观更新最多延迟 1 秒，降低桌面常驻 CPU。
- 频谱/桌面播放判断优先使用 media.state===3，比 playing 布尔更准确。
- 保留 0.4.91 的 rAF 按需续帧。
