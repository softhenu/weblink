# WebLink Mobile

WebLink Mobile是WebLink项目的Android移动客户端，专为解决Android手机浏览器无法进行屏幕共享的问题而设计。通过原生应用，它可以与WebLink网页端建立连接，实现屏幕共享和视频通话功能。

## 功能特点

- **屏幕共享**：Android设备可以将屏幕内容共享给WebLink网页端用户
- **视频通话**：支持与WebLink网页端进行视频通话
- **邀请码系统**：通过6位数字邀请码快速建立连接
- **二维码分享**：生成二维码方便快速连接
- **WebRTC技术**：使用WebRTC实现P2P连接，确保低延迟和高质量的视频传输

## 技术架构

- 使用WebRTC实现P2P连接和媒体流传输
- 使用Socket.IO与信令服务器通信
- 使用MediaProjection API实现屏幕共享
- 使用前台服务确保屏幕共享在后台也能正常运行

## 如何使用

### 屏幕共享

1. 点击主页面的"屏幕共享"按钮
2. 系统会生成一个6位数字邀请码
3. 在WebLink网页端输入该邀请码或扫描二维码
4. 连接建立后，点击"开始共享"按钮
5. 授予屏幕录制权限
6. 开始共享屏幕

### 视频通话

1. 点击主页面的"视频通话"按钮
2. 系统会生成一个6位数字邀请码
3. 在WebLink网页端输入该邀请码或扫描二维码
4. 连接建立后，即可开始视频通话

## 连接到信令服务器

WebLink Mobile需要连接到信令服务器以建立WebRTC连接。默认情况下，应用配置为连接到与WebLink网页端相同的信令服务器。

要修改信令服务器地址，请在以下文件中更新`SIGNALING_SERVER_URL`变量：

- `com.weblink.mobile.webrtc.SignalingClient.java`
- `com.weblink.mobile.ScreenShareActivity.java`
- `com.weblink.mobile.VideoCallActivity.java`

## 系统要求

- Android 7.0 (API级别24)或更高版本
- 摄像头和麦克风（用于视频通话）
- 网络连接

## 构建项目

1. 使用Android Studio打开项目
2. 配置Gradle构建系统
3. 构建APK或直接在设备上运行

## 许可证

本项目遵循与WebLink项目相同的许可证。 