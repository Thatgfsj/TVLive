package com.tvlive.data.model

data class Channel(
    val id: String,
    val name: String,
    val category: ChannelCategory,
    val logo: String? = null,
    val sources: List<StreamSource> = emptyList(),
    val currentSourceIndex: Int = 0
) {
    val currentSource: StreamSource?
        get() = sources.getOrNull(currentSourceIndex)

    val nextSource: StreamSource?
        get() = sources.getOrNull((currentSourceIndex + 1) % sources.size)

    fun nextSourceIndex(): Int = (currentSourceIndex + 1) % sources.size.coerceAtLeast(1)

    fun withSourceIndex(index: Int): Channel = copy(currentSourceIndex = index)
}

data class StreamSource(
    val url: String,
    val quality: String = "原画",
    val referer: String? = null,
    val userAgent: String? = null
)

enum class ChannelCategory(val displayName: String, val order: Int) {
    CCTV("央视", 0),        // 央视频道 - 排第一
    SATELLITE("卫视", 1),   // 卫视频道
    MOVIE("电影", 2),       // 电影频道
    DIGITAL("数字", 3),     // 数字频道
    KIDS("儿童", 4),        // 儿童频道
    LOCAL("地方", 5),       // 地方频道
    DOCUMENTARY("纪录", 6), // 纪录频道
    SPORTS("体育", 7),      // 体育频道
    COMMENTARY("解说", 8),   // 解说频道
    MUSIC("音乐", 9),       // 音乐频道
    GALA("春晚", 10),       // 春晚频道
    LIVE_CHINA("直播中国", 11), // 直播中国
    OTHER("其他", 99)
}

enum class DecoderType(val displayName: String) {
    HARDWARE("硬解"),
    SOFTWARE("软解"),
    AUTO("自动")
}
