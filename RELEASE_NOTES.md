Aurora Launcher v0.4.82

- 视频背景改用 TextureView 渲染，彻底绕开电视 HDMI 切换后 SurfaceView 视频层不恢复的问题。
- 从盒子/HDMI 切回电视后，背景视频不再黑屏，不需要重新选背景或重启电视。
- 纹理缓冲限制为 960×540，降低 4K 背景的 GPU 合成开销。
