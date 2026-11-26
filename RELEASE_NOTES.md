Aurora Launcher v0.4.76

- 从 HDMI 切回电视后，把 MediaPlayer 重新绑定到当前 SurfaceView 的 Surface 并 seek 到当前位置，修复“解码在播但画面全黑”。
- Surface 重新创建时也重新 setDisplay，不再继续渲染到已销毁的旧 Surface。
