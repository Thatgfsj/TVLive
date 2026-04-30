package com.tvlive.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.tvlive.data.model.Channel
import com.tvlive.data.model.ChannelCategory
import com.tvlive.data.model.StreamSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ChannelRepository"
        private const val PREFS_NAME = "tvlive_channels"
        private const val KEY_SOURCES = "cached_sources"
        private const val KEY_LAST_UPDATE = "last_update"
        private const val KEY_LAST_CHANNEL_ID = "last_channel_id"
        private const val KEY_BEST_SOURCE = "best_source_"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ============================================================
    // 内置频道列表 — 请在此处添加你的直播源
    // ============================================================
    //
    // 直播源格式: StreamSource("URL", "标签")
    // 支持的格式: .m3u8 (HLS直播流), .mp4
    // 每个频道可配置多个源，播放失败时自动切换
    //
    // 示例:
    //   StreamSource("http://example.com/live/cctv1.m3u8", "主源"),
    //   StreamSource("http://example.com/live/cctv1_bak.m3u8", "备用"),
    //
    // ============================================================

    private val builtInChannels = listOf(
        Channel(id = "cctv1", name = "CCTV-1 综合", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-1 的直播源
        )),
        Channel(id = "cctv2", name = "CCTV-2 财经", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-2 的直播源
        )),
        Channel(id = "cctv3", name = "CCTV-3 综艺", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-3 的直播源
        )),
        Channel(id = "cctv4", name = "CCTV-4 国际", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-4 的直播源
        )),
        Channel(id = "cctv5", name = "CCTV-5 体育", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-5 的直播源
        )),
        Channel(id = "cctv5plus", name = "CCTV-5+ 体育赛事", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-5+ 的直播源
        )),
        Channel(id = "cctv6", name = "CCTV-6 电影", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-6 的直播源
        )),
        Channel(id = "cctv7", name = "CCTV-7 军事农业", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-7 的直播源
        )),
        Channel(id = "cctv8", name = "CCTV-8 电视剧", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-8 的直播源（这是默认频道）
        )),
        Channel(id = "cctv9", name = "CCTV-9 纪录", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-9 的直播源
        )),
        Channel(id = "cctv10", name = "CCTV-10 科教", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-10 的直播源
        )),
        Channel(id = "cctv11", name = "CCTV-11 戏曲", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-11 的直播源
        )),
        Channel(id = "cctv12", name = "CCTV-12 社会与法", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-12 的直播源
        )),
        Channel(id = "cctv13", name = "CCTV-13 新闻", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-13 的直播源
        )),
        Channel(id = "cctv14", name = "CCTV-14 少儿", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-14 的直播源
        )),
        Channel(id = "cctv15", name = "CCTV-15 音乐", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-15 的直播源
        )),
        Channel(id = "cctv16", name = "CCTV-16 奥林匹克", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-16 的直播源
        )),
        Channel(id = "cctv17", name = "CCTV-17 农业农村", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CCTV-17 的直播源
        )),
        Channel(id = "cgtn", name = "CGTN 英语新闻", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入 CGTN 的直播源
        )),
        // =====================================================
        // 卫视频道 — 按需添加直播源
        // =====================================================
        Channel(id = "satellite_hn", name = "湖南卫视", category = ChannelCategory.SATELLITE, sources = listOf(
            // TODO: 在此填入湖南卫视的直播源
        )),
        Channel(id = "satellite_zj", name = "浙江卫视", category = ChannelCategory.SATELLITE, sources = listOf(
            // TODO: 在此填入浙江卫视的直播源
        )),
        Channel(id = "satellite_js", name = "江苏卫视", category = ChannelCategory.SATELLITE, sources = listOf(
            // TODO: 在此填入江苏卫视的直播源
        )),
        Channel(id = "satellite_df", name = "东方卫视", category = ChannelCategory.SATELLITE, sources = listOf(
            // TODO: 在此填入东方卫视的直播源
        )),
        Channel(id = "satellite_bj", name = "北京卫视", category = ChannelCategory.SATELLITE, sources = listOf(
            // TODO: 在此填入北京卫视的直播源
        )),
    )

    // ============================================================
    // 以下为核心逻辑，无需修改
    // ============================================================

    fun getChannels(): List<Channel> {
        val remoteChannels = loadCachedRemoteChannels()
        if (remoteChannels.isNotEmpty()) {
            val remoteNames = remoteChannels.map { it.name }.toSet()
            val uniqueBuiltIn = builtInChannels.filter { it.name !in remoteNames }
            return remoteChannels + uniqueBuiltIn
        }
        return builtInChannels
    }

    private fun loadCachedRemoteChannels(): List<Channel> {
        return try {
            val rawText = prefs.getString(KEY_SOURCES, null) ?: return emptyList()
            parseIptvText(rawText)
        } catch (e: Exception) {
            Log.e(TAG, "加载缓存源失败: ${e.message}")
            emptyList()
        }
    }

    private fun parseIptvText(text: String): List<Channel> {
        if (text.isBlank()) return emptyList()
        val channelsMap = mutableMapOf<String, Channel>()
        val lines = text.split("\n")
        var currentCategory = ChannelCategory.CCTV

        for (line in lines) {
            val trimmedLine = line.trim()
            when {
                trimmedLine.contains("#genre#") -> {
                    currentCategory = when {
                        trimmedLine.contains("央视频道") -> ChannelCategory.CCTV
                        trimmedLine.contains("卫视频道") -> ChannelCategory.SATELLITE
                        trimmedLine.contains("电影频道") -> ChannelCategory.MOVIE
                        trimmedLine.contains("数字频道") -> ChannelCategory.DIGITAL
                        trimmedLine.contains("儿童频道") -> ChannelCategory.KIDS
                        trimmedLine.contains("地方频道") -> ChannelCategory.LOCAL
                        trimmedLine.contains("纪录频道") -> ChannelCategory.DOCUMENTARY
                        trimmedLine.contains("体育频道") -> ChannelCategory.SPORTS
                        trimmedLine.contains("解说频道") -> ChannelCategory.COMMENTARY
                        trimmedLine.contains("音乐频道") -> ChannelCategory.MUSIC
                        trimmedLine.contains("春晚频道") -> ChannelCategory.GALA
                        trimmedLine.contains("直播中国") -> ChannelCategory.LIVE_CHINA
                        else -> ChannelCategory.OTHER
                    }
                }
                trimmedLine.contains(".m3u8") || trimmedLine.contains(".mp4") -> {
                    val parts = trimmedLine.split(",")
                    if (parts.size >= 2) {
                        val name = parts[0].trim()
                        val url = parts[1].trim()
                        val category = if (name.startsWith("CCTV") || name.startsWith("CGTN")) {
                            ChannelCategory.CCTV
                        } else {
                            currentCategory
                        }
                        val sourceQuality = if (url.contains("myalicdn")) "CDN" else "自动"
                        val existingChannel = channelsMap[name]
                        if (existingChannel != null) {
                            val mergedSources = existingChannel.sources + StreamSource(url, sourceQuality)
                            channelsMap[name] = existingChannel.copy(sources = mergedSources)
                        } else {
                            channelsMap[name] = Channel(
                                id = "remote_${name.hashCode()}",
                                name = name,
                                category = category,
                                sources = listOf(StreamSource(url, sourceQuality))
                            )
                        }
                    }
                }
            }
        }
        return channelsMap.values.toList()
    }

    fun updateRemoteSources(lines: List<String>) {
        try {
            val text = lines.joinToString("\n")
            prefs.edit()
                .putString(KEY_SOURCES, text)
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "远程源已更新: ${lines.size} 行")
        } catch (e: Exception) {
            Log.e(TAG, "保存远程源失败: ${e.message}")
        }
    }

    fun getChannelById(id: String): Channel? = getChannels().find { it.id == id }

    fun getChannelsByCategory(category: ChannelCategory): List<Channel> =
        getChannels().filter { it.category == category }

    fun getAllCategories(): List<ChannelCategory> =
        ChannelCategory.entries.sortedBy { it.order }

    fun getDefaultChannel(): Channel {
        return getChannelById("cctv8") ?: builtInChannels.first()
    }

    fun getChannelCount(): Int = getChannels().size

    fun getLastUpdateTime(): Long = prefs.getLong(KEY_LAST_UPDATE, 0)

    fun saveLastChannel(channelId: String) {
        prefs.edit().putString(KEY_LAST_CHANNEL_ID, channelId).apply()
    }

    fun saveBestSourceIndex(channelId: String, sourceIndex: Int) {
        prefs.edit().putInt(KEY_BEST_SOURCE + channelId, sourceIndex).apply()
    }

    fun getBestSourceIndex(channelId: String): Int {
        return prefs.getInt(KEY_BEST_SOURCE + channelId, -1)
    }
}
