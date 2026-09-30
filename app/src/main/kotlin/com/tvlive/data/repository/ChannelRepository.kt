package com.tvlive.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.tvlive.data.model.Channel
import com.tvlive.data.model.ChannelCategory
import com.tvlive.data.model.StreamSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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

        /**
         * 本地源文件路径：
         *   /sdcard/Android/data/com.tvlive/files/sources.txt
         * 支持 IPTV txt（频道名,URL）和 m3u 两种格式，放入即生效，无需改代码。
         */
        fun localSourceFile(context: Context): File =
            File(context.getExternalFilesDir(null), "sources.txt")

        /**
         * 频道合并用的规范化名：CCTV-1 综合 / CCTV-1 / cctv1 → "CCTV1"
         */
        fun canonicalName(name: String): String {
            val m = Regex("CCTV[- ]?(\\d+\\+?)", RegexOption.IGNORE_CASE).find(name)
            if (m != null) return "CCTV${m.groupValues[1]}"
            return name.replace(" ", "").replace("-", "").uppercase()
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ============================================================
    // 内置频道列表
    // ============================================================
    //
    // ⚠️ 本项目不包含任何直播源 URL（版权合规）。开箱后通过以下方式添加：
    //   1. 远程自动更新：在 PlayerViewModel.kt 的 REMOTE_SOURCE_URL
    //      填入你信任的公共 IPTV 列表地址（支持 m3u / txt），
    //      App 每 3 天自动拉取并入；
    //   2. 本地源文件：把列表放到
    //      /sdcard/Android/data/com.tvlive/files/sources.txt
    //   3. 直接编辑：在对应频道的 sources 里添加
    //      StreamSource("http://你的直播源地址/xxx.m3u8", "主源")
    //
    // ============================================================

    private val builtInChannels = listOf(
        Channel(id = "cctv1", name = "CCTV-1 综合", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv2", name = "CCTV-2 财经", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv3", name = "CCTV-3 综艺", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv4", name = "CCTV-4 国际", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv5", name = "CCTV-5 体育", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv5plus", name = "CCTV-5+ 体育赛事", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv6", name = "CCTV-6 电影", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv7", name = "CCTV-7 军事农业", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv8", name = "CCTV-8 电视剧", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源（这是默认频道）
        )),
        Channel(id = "cctv9", name = "CCTV-9 纪录", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv10", name = "CCTV-10 科教", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv11", name = "CCTV-11 戏曲", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv12", name = "CCTV-12 社会与法", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv13", name = "CCTV-13 新闻", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv14", name = "CCTV-14 少儿", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv15", name = "CCTV-15 音乐", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv16", name = "CCTV-16 奥林匹克", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cctv17", name = "CCTV-17 农业农村", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "cgtn", name = "CGTN 英语新闻", category = ChannelCategory.CCTV, sources = listOf(
            // TODO: 在此填入直播源
        )),
        // =====================================================
        // 卫视频道 — 远程源自动更新会填充；也可手动添加
        // =====================================================
        Channel(id = "satellite_hn", name = "湖南卫视", category = ChannelCategory.SATELLITE, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "satellite_zj", name = "浙江卫视", category = ChannelCategory.SATELLITE, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "satellite_js", name = "江苏卫视", category = ChannelCategory.SATELLITE, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "satellite_df", name = "东方卫视", category = ChannelCategory.SATELLITE, sources = listOf(
            // TODO: 在此填入直播源
        )),
        Channel(id = "satellite_bj", name = "北京卫视", category = ChannelCategory.SATELLITE, sources = listOf(
            // TODO: 在此填入直播源
        )),
    )

    // ============================================================
    // 以下为核心逻辑，无需修改
    // ============================================================

    /** 内存缓存：避免每次换台都重新读文件/解析 */
    private var mergedCache: List<Channel>? = null

    private var fileCache: Pair<Pair<String, Long>, List<Channel>>? = null

    fun getChannels(): List<Channel> {
        mergedCache?.let { return it }
        val merged = LinkedHashMap<String, Channel>() // key: canonicalName

        // 1. 内置频道打底
        for (channel in builtInChannels) {
            merged[canonicalName(channel.name)] = channel
        }

        // 2. 本地文件源覆盖同名频道（用户手工指定优先），新频道直接加入
        for (channel in loadLocalFileChannels()) {
            val key = canonicalName(channel.name)
            val existing = merged[key]
            merged[key] = when {
                existing == null -> channel
                channel.sources.isNotEmpty() -> channel
                else -> existing
            }
        }

        // 3. 远程缓存源：并入已有频道（源并集去重、主机多样化，上限 8 个），新频道直接加入
        for (channel in loadCachedRemoteChannels()) {
            val key = canonicalName(channel.name)
            val existing = merged[key]
            when {
                existing == null -> merged[key] = channel
                else -> {
                    val urls = existing.sources.map { it.url }.toSet()
                    val hostOf = { url: String ->
                        url.substringAfter("://").substringBefore('/').substringBefore(':')
                    }
                    val hosts = existing.sources.map { hostOf(it.url) }.toSet()
                    val extras = channel.sources.filter { it.url !in urls }
                    // 优先补不同主机的源（同一机房挂掉不至于全灭），再用同主机源填满
                    val diverse = extras.filter { hostOf(it.url) !in hosts }
                    val rest = extras.filter { it !in diverse }
                    val slots = (8 - existing.sources.size).coerceAtLeast(0)
                    val picked = (diverse + rest).take(slots)
                    if (picked.isNotEmpty()) {
                        merged[key] = existing.copy(sources = existing.sources + picked)
                    }
                }
            }
        }

        val list = merged.values.toList()
        mergedCache = list
        return list
    }

    /** 本地 sources.txt 文件中的频道（按文件最后修改时间做内存缓存） */
    private fun loadLocalFileChannels(): List<Channel> {
        val file = localSourceFile(context)
        if (!file.exists()) return emptyList()
        val cacheKey = file.absolutePath to file.lastModified()
        fileCache?.let { if (it.first == cacheKey) return it.second }
        val parsed = try {
            parseSourceText(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "读取本地源文件失败: ${e.message}")
            emptyList()
        }
        fileCache = cacheKey to parsed
        return parsed
    }

    private fun loadCachedRemoteChannels(): List<Channel> {
        return try {
            val rawText = prefs.getString(KEY_SOURCES, null) ?: return emptyList()
            parseSourceText(rawText)
        } catch (e: Exception) {
            Log.e(TAG, "加载缓存源失败: ${e.message}")
            emptyList()
        }
    }

    /**
     * 解析 IPTV 源文本，自动识别两种格式：
     *  - txt: '频道名,URL' 每行一个；'分类名,#genre#' 为分类行
     *  - m3u: '#EXTINF:... group-title="央视",频道名' + 下一行 URL
     */
    private fun parseSourceText(text: String): List<Channel> {
        if (text.isBlank()) return emptyList()
        val channelsMap = mutableMapOf<String, Channel>()
        var currentCategory = ChannelCategory.CCTV
        var pendingName: String? = null
        var pendingCategory: ChannelCategory? = null

        fun addSource(name: String, category: ChannelCategory, url: String) {
            if (name.isEmpty() || !url.startsWith("http")) return
            val sourceQuality = if (url.contains("myalicdn")) "CDN" else "自动"
            val existing = channelsMap[name]
            if (existing != null) {
                if (existing.sources.any { it.url == url }) return
                channelsMap[name] = existing.copy(sources = existing.sources + StreamSource(url, sourceQuality))
            } else {
                channelsMap[name] = Channel(
                    id = "remote_${canonicalName(name).hashCode()}",
                    name = name,
                    category = category,
                    sources = listOf(StreamSource(url, sourceQuality))
                )
            }
        }

        fun categoryFromGroup(group: String): ChannelCategory? = when {
            group.contains("央视") || group.contains("CCTV") -> ChannelCategory.CCTV
            group.contains("卫视") -> ChannelCategory.SATELLITE
            group.contains("电影") || group.contains("影视") -> ChannelCategory.MOVIE
            group.contains("纪录") -> ChannelCategory.DOCUMENTARY
            group.contains("体育") -> ChannelCategory.SPORTS
            group.contains("少儿") || group.contains("卡通") || group.contains("动画") -> ChannelCategory.KIDS
            group.contains("音乐") -> ChannelCategory.MUSIC
            else -> ChannelCategory.OTHER
        }

        for (rawLine in text.split("\n")) {
            val line = rawLine.trim()
            when {
                line.contains("#genre#") -> {
                    currentCategory = when {
                        line.contains("央视频道") -> ChannelCategory.CCTV
                        line.contains("卫视频道") -> ChannelCategory.SATELLITE
                        line.contains("电影频道") -> ChannelCategory.MOVIE
                        line.contains("数字频道") -> ChannelCategory.DIGITAL
                        line.contains("儿童频道") -> ChannelCategory.KIDS
                        line.contains("地方频道") -> ChannelCategory.LOCAL
                        line.contains("纪录频道") -> ChannelCategory.DOCUMENTARY
                        line.contains("体育频道") -> ChannelCategory.SPORTS
                        line.contains("解说频道") -> ChannelCategory.COMMENTARY
                        line.contains("音乐频道") -> ChannelCategory.MUSIC
                        line.contains("春晚频道") -> ChannelCategory.GALA
                        line.contains("直播中国") -> ChannelCategory.LIVE_CHINA
                        else -> ChannelCategory.OTHER
                    }
                }
                line.startsWith("#EXTINF") -> {
                    // 频道名 = 最后一个逗号之后的内容
                    val name = line.substringAfterLast(',').trim()
                    val group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""
                    pendingName = name
                    pendingCategory = categoryFromGroup(group)
                        ?: if (name.startsWith("CCTV") || name.startsWith("CGTN")) ChannelCategory.CCTV else null
                }
                line.startsWith("#") -> { /* 其他 EXT 标记行，跳过 */ }
                line.contains(",") && line.contains("http") -> {
                    // txt 格式: 频道名,URL
                    val parts = line.split(",")
                    if (parts.size >= 2) {
                        val name = parts[0].trim()
                        val url = parts[1].trim()
                        val category = if (name.startsWith("CCTV") || name.startsWith("CGTN")) {
                            ChannelCategory.CCTV
                        } else {
                            currentCategory
                        }
                        addSource(name, category, url)
                    }
                }
                line.startsWith("http") -> {
                    // m3u 格式: URL 紧跟在 EXTINF 之后
                    val name = pendingName
                    if (name != null) {
                        val category = pendingCategory
                            ?: if (name.startsWith("CCTV") || name.startsWith("CGTN")) ChannelCategory.CCTV
                            else currentCategory
                        addSource(name, category, line)
                    }
                    pendingName = null
                    pendingCategory = null
                }
                else -> {
                    pendingName = null
                    pendingCategory = null
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
            mergedCache = null
            Log.d(TAG, "远程源已更新: ${lines.size} 行")
        } catch (e: Exception) {
            Log.e(TAG, "保存远程源失败: ${e.message}")
        }
    }

    fun getChannelById(id: String): Channel? = getChannels().find { it.id == id }

    fun getChannelsByCategory(category: ChannelCategory): List<Channel> =
        getChannels().filter { it.category == category }

    /** 只返回有频道的分类，避免频道列表里出现一排空分类 */
    fun getAllCategories(): List<ChannelCategory> =
        ChannelCategory.entries
            .filter { cat -> getChannels().any { it.category == cat } }
            .sortedBy { it.order }

    /** 默认频道：优先 CCTV-8（有源的），其次任意有源的频道 */
    fun getDefaultChannel(): Channel {
        val all = getChannels()
        return all.firstOrNull { it.name.contains("CCTV-8") && it.sources.isNotEmpty() }
            ?: all.firstOrNull { it.sources.isNotEmpty() }
            ?: all.firstOrNull { it.id == "cctv8" }
            ?: all.first()
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
