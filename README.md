# TVLive — 免费开源电视直播软件

给我奶做的电视直播 App。市面上的电视直播软件要么收费，要么操作太复杂老人不会用。这个 App 打开就能看，默认播放 CCTV-8 电视剧频道（因为奶奶只看这个台）。

**支持 Android TV 和手机，完全免费，永远开源。**

## 功能

- 央视 17 个频道 + CGTN + 卫视
- 多源自动切换（一个频道配多个直播源，卡顿自动换）
- TV 遥控器完美适配（方向键换台、数字键跳转、菜单键列表）
- 手机触屏手势（上下滑换台、双击选频道）
- 智能测速选源（启动时自动测速选最快源）
- 最佳源记忆（下次启动直接用最快的源，秒开）
- 深色主题，大字体，适合电视远距离观看

## 为什么默认是 CCTV-8？

我奶只会按一个键——电视机电源键。打开电视后 App 自启动，直接播放 CCTV-8 电视剧频道。不需要任何操作。

如果你要改默认频道，编辑 `ChannelRepository.kt` 中的 `getDefaultChannel()` 方法。

## 如何添加直播源

> **为规避版权风险，本项目不包含任何直播源 URL，也不内置任何公共列表地址。播放器框架开箱即用，源请自行准备。**

### 方法一：配置远程自动更新（推荐）

在 `PlayerViewModel.kt` 的 `REMOTE_SOURCE_URL` 填入你信任的 IPTV 列表地址（支持 m3u / txt 格式），App 每 3 天自动拉取，新源与内置频道按名称合并、主机去重。

### 方法二：本地源文件（无需改代码）

把 IPTV 列表放到 `/sdcard/Android/data/com.tvlive/files/sources.txt`（支持 `频道名,URL` txt 和 m3u 两种格式），重启 App 即生效，优先级最高。

### 方法三：直接编辑内置源

打开 `app/src/main/kotlin/com/tvlive/data/repository/ChannelRepository.kt`，在对应频道的 `sources` 列表中添加：

```kotlin
Channel(id = "cctv8", name = "CCTV-8 电视剧", category = ChannelCategory.CCTV, sources = listOf(
    StreamSource("http://你的直播源地址/cctv8.m3u8", "主源"),
    StreamSource("http://你的备用源地址/cctv8.m3u8", "备用"),
)),
```

## 版权声明

本项目只是一个开源播放器框架，**不提供、不内置、不链接任何受版权保护的直播内容或源列表**。用户需自行准备合法的观看途径，因使用本项目产生的任何版权问题与项目作者无关。

## 编译

需要 Android Studio 或命令行 Android SDK。

```bash
# 设置 Android SDK 路径
export ANDROID_HOME=/path/to/android-sdk

# 编译 debug APK
./gradlew assembleDebug

# APK 在 app/build/outputs/apk/debug/app-debug.apk
```

**最低要求：** Android 7.0+ (API 24)

## 技术栈

- Kotlin + Jetpack Compose
- Media3 ExoPlayer (HLS 播放)
- Hilt 依赖注入
- MVVM 架构 + StateFlow

## 为什么开源

电视直播软件不该收钱。尤其不该向老人收钱。

这个 App 最初是为我家 2019 年买的一台长虹电视写的。电视配置不高（2GB RAM），但跑这个 App 完全够用。我奶每天打开电视就能看剧，不需要学习任何操作。

如果你家里也有不太会用智能电视的老人，拿走直接用。改改频道列表就行。

## License

MIT
