package com.tvlive.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.tvlive.data.model.Channel
import com.tvlive.data.model.ChannelCategory
import com.tvlive.data.model.DecoderType
import com.tvlive.data.model.StreamSource
import com.tvlive.data.repository.ChannelRepository
import com.tvlive.ui.components.SpeedTestItem
import com.tvlive.ui.components.SpeedTestResult
import com.tvlive.ui.components.TestStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class PlayerState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val currentChannel: Channel? = null,
    val currentSourceIndex: Int = 0,
    val retryCount: Int = 0,
    val decoderType: DecoderType = DecoderType.AUTO,
    val isAutoSwitching: Boolean = false,
    val switchReason: String? = null,
    val isSpeedTesting: Boolean = false,
    val speedTestProgress: Float = 0f,
    val speedTestLogs: List<String> = emptyList(),
    val speedTestResults: List<SpeedTestResult> = emptyList(),
    // 新增：可视化测速
    val speedTestItems: List<SpeedTestItem> = emptyList(),
    val currentTesting: String? = null,
    val bestChannel: String? = null,
    val bestSpeed: Long? = null,
    val isBackgroundTesting: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private val maxAutoRetries = 10
    private var isSourceSwitching = false
    private var playbackCheckJob: kotlinx.coroutines.Job? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _playerState.value = _playerState.value.copy(isLoading = true, error = null)
                }
                Player.STATE_READY -> {
                    _playerState.value = _playerState.value.copy(
                        isLoading = false,
                        isPlaying = true,
                        error = null,
                        retryCount = 0,
                        isAutoSwitching = false,
                        switchReason = null
                    )
                    isSourceSwitching = false
                }
                Player.STATE_ENDED -> {
                    // 直播流结束通常是卡顿，尝试重新播放
                    viewModelScope.launch {
                        handlePlaybackError()
                    }
                }
                Player.STATE_IDLE -> {
                    _playerState.value = _playerState.value.copy(isPlaying = false)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            viewModelScope.launch {
                handlePlaybackError()
            }
        }
    }

    private suspend fun handlePlaybackError() {
        if (isSourceSwitching) return

        val currentState = _playerState.value
        val currentChannel = currentState.currentChannel ?: return

        // 多源自动切换
        if (currentChannel.sources.size > 1 && currentState.retryCount < maxAutoRetries) {
            isSourceSwitching = true
            val nextIndex = (currentState.currentSourceIndex + 1) % currentChannel.sources.size
            val nextSource = currentChannel.sources[nextIndex]

            _playerState.value = currentState.copy(
                retryCount = currentState.retryCount + 1,
                currentSourceIndex = nextIndex,
                isAutoSwitching = true,
                switchReason = "源${nextIndex + 1}: ${nextSource.quality}"
            )

            // 如果切换超过一次成功，保存这个源索引
            if (nextIndex > 0) {
                channelRepository.saveBestSourceIndex(currentChannel.id, nextIndex)
            }

            playSource(nextSource, currentChannel.copy(currentSourceIndex = nextIndex))
        } else {
            _playerState.value = currentState.copy(
                isLoading = false,
                error = "播放失败，请尝试其他频道"
            )
            isSourceSwitching = false
        }
    }

    fun initializePlayer() {
        if (exoPlayer == null) {
            // 针对电视硬件的负载控制 - 更大的缓冲区减少卡顿
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30000,    // minBufferMs: 最少缓冲30秒才开始播放，减少启动卡顿
                    120000,   // maxBufferMs: 最大缓冲120秒，电视内存够用
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                .build()

            exoPlayer = ExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setUseLazyPreparation(false)  // 立即准备，不延迟
                .build()
                .also {
                    it.addListener(playerListener)
                    it.playWhenReady = true
                    it.setHandleAudioBecomingNoisy(true)
                    // 针对HLS直播低延迟优化
                    it.trackSelectionParameters = it.trackSelectionParameters
                        .buildUpon()
                        .setMaxVideoSize(1920, 1080)  // 限制分辨率减少解码压力
                        .build()
                }
        }
    }

    fun releasePlayer() {
        playbackCheckJob?.cancel()
        playbackCheckJob = null
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        _playerState.value = PlayerState()
    }

    fun playChannel(channel: Channel) {
        isSourceSwitching = false
        // 使用缓存的最佳源索引
        val bestIdx = channelRepository.getBestSourceIndex(channel.id)
        val sourceIndex = if (bestIdx in channel.sources.indices) bestIdx else 0
        val channelWithSource = channel.copy(currentSourceIndex = sourceIndex)
        _playerState.value = _playerState.value.copy(
            currentChannel = channelWithSource,
            currentSourceIndex = sourceIndex,
            retryCount = 0,
            isLoading = true,
            error = null,
            isAutoSwitching = false,
            switchReason = null
        )
        // 保存最后观看频道
        channelRepository.saveLastChannel(channel.id)
        channelWithSource.currentSource?.let { playSource(it, channelWithSource) }
    }

    private fun playSource(source: StreamSource, channel: Channel) {
        exoPlayer?.let { player ->
            val mediaSource = createMediaSource(source)
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
            // 开始保活检查
            startPlaybackWatchdog(channel, source)
        }
    }

    // 智能保活 - 只在播放真正卡死时才重新加载
    // 不再每15秒重启播放，避免因网络波动导致的反复重连
    private fun startPlaybackWatchdog(channel: Channel, source: StreamSource) {
        playbackCheckJob?.cancel()
        playbackCheckJob = viewModelScope.launch {
            var stallCount = 0
            while (true) {
                delay(10000) // 10秒检查一次

                exoPlayer?.let { player ->
                    val state = player.playbackState
                    // 只在IDLE或ENDED状态（真正停止）时才重新播放
                    // STATE_BUFFERING是正常的网络缓冲，不干预
                    when (state) {
                        Player.STATE_IDLE -> {
                            // 空闲状态 - 尝试恢复
                            if (!isSourceSwitching) {
                                player.prepare()
                                player.playWhenReady = true
                            }
                        }
                        Player.STATE_ENDED -> {
                            // 直播流结束才重新加载
                            if (!isSourceSwitching) {
                                stallCount++
                                if (stallCount >= 2) {
                                    // 连续两次ENDED才切换源
                                    handlePlaybackError()
                                    return@launch
                                }
                                playSource(source, channel)
                                return@launch
                            }
                        }
                        Player.STATE_READY -> {
                            // 恢复正常，重置计数
                            stallCount = 0
                        }
                    }
                }
            }
        }
    }

    private fun createMediaSource(source: StreamSource): MediaSource {
        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent("Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
            setConnectTimeoutMs(20000)
            setReadTimeoutMs(60000)  // 增加读超时，HLS分段下载需要更长时间
            if (source.referer != null) {
                setDefaultRequestProperties(mapOf("Referer" to source.referer))
            }
        }

        val uri = Uri.parse(source.url)
        val mediaItem = MediaItem.fromUri(uri)

        return if (source.url.contains(".m3u8")) {
            HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)  // 允许无chunk准备，加速启动
                .createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
        }
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun switchToNextChannel() {
        val channels = channelRepository.getChannels()
        val currentChannel = _playerState.value.currentChannel
        val currentIndex = channels.indexOfFirst { it.id == currentChannel?.id }
        if (currentIndex >= 0 && currentIndex < channels.size - 1) {
            playChannel(channels[currentIndex + 1])
        } else if (channels.isNotEmpty()) {
            playChannel(channels[0])
        }
    }

    fun switchToPreviousChannel() {
        val channels = channelRepository.getChannels()
        val currentChannel = _playerState.value.currentChannel
        val currentIndex = channels.indexOfFirst { it.id == currentChannel?.id }
        if (currentIndex > 0) {
            playChannel(channels[currentIndex - 1])
        } else if (channels.isNotEmpty()) {
            playChannel(channels[channels.size - 1])
        }
    }

    fun retry() {
        isSourceSwitching = false
        val channel = _playerState.value.currentChannel ?: return
        _playerState.value = _playerState.value.copy(retryCount = 0)
        channel.currentSource?.let { playSource(it, channel) }
    }

    fun switchToNextSource() {
        val currentState = _playerState.value
        val channel = currentState.currentChannel ?: return
        if (channel.sources.size <= 1) return

        isSourceSwitching = true
        val nextIndex = (currentState.currentSourceIndex + 1) % channel.sources.size
        val nextSource = channel.sources[nextIndex]

        _playerState.value = currentState.copy(
            currentSourceIndex = nextIndex,
            retryCount = 0,
            isAutoSwitching = false,
            switchReason = "切换: ${nextSource.quality}"
        )

        playSource(nextSource, channel.copy(currentSourceIndex = nextIndex))
    }

    fun cycleDecoderType() {
        val current = _playerState.value.decoderType
        val next = when (current) {
            DecoderType.AUTO -> DecoderType.HARDWARE
            DecoderType.HARDWARE -> DecoderType.SOFTWARE
            DecoderType.SOFTWARE -> DecoderType.AUTO
        }
        _playerState.value = _playerState.value.copy(decoderType = next)
    }

    fun playDefaultChannel() {
        val defaultChannel = channelRepository.getDefaultChannel()
        playChannel(defaultChannel)
    }

    // 快速启动：有缓存最佳源就直接播放，不跑测速
    fun quickStartOrTest() {
        val defaultChannel = channelRepository.getDefaultChannel()
        val bestIdx = channelRepository.getBestSourceIndex(defaultChannel.id)
        if (bestIdx in defaultChannel.sources.indices) {
            // 有缓存，直接播放
            val channelWithSource = defaultChannel.copy(currentSourceIndex = bestIdx)
            playChannel(channelWithSource)
            // 后台检查是否需要更新远程源（3天一更新）
            viewModelScope.launch(Dispatchers.IO) {
                if (shouldUpdateSources()) {
                    val result = fetchRemoteSources()
                    if (result.isSuccess) {
                        channelRepository.updateRemoteSources(result.getOrNull()!!)
                    }
                }
            }
        } else {
            // 没有缓存，跑测速（测速内部也会更新远程源）
            speedTestAllSources { _ -> }
        }
    }

    // 检查是否需要更新远程源（距上次更新超过3天）
    private fun shouldUpdateSources(): Boolean {
        val lastUpdate = channelRepository.getLastUpdateTime()
        val threeDaysMs = 3L * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - lastUpdate > threeDaysMs
    }

    // 获取所有频道
    fun getChannels(): List<Channel> = channelRepository.getChannels()

    // 获取所有分类
    fun getAllCategories(): List<ChannelCategory> = channelRepository.getAllCategories()

    // 根据分类获取频道
    fun getChannelsByCategory(category: ChannelCategory): List<Channel> =
        channelRepository.getChannelsByCategory(category)

    fun clearError() {
        _playerState.value = _playerState.value.copy(error = null)
    }

    // 测速所有源 - 先测CCTV-8播放，后台继续测CCTV-1和CCTV-6
    fun speedTestAllSources(onComplete: (SpeedTestResult) -> Unit) {
        viewModelScope.launch {
            _playerState.value = _playerState.value.copy(
                isSpeedTesting = true,
                speedTestProgress = 0f,
                speedTestItems = emptyList(),
                currentTesting = null,
                bestChannel = null,
                bestSpeed = null,
                isBackgroundTesting = false
            )

            // 先获取远程源
            val remoteResult = fetchRemoteSources()
            if (remoteResult.isSuccess) {
                channelRepository.updateRemoteSources(remoteResult.getOrNull()!!)
            }

            val channels = channelRepository.getChannels()

            // 第一步：只测CCTV-8的源（静默测速，不更新UI）
            val cctv8Sources = mutableListOf<Pair<Channel, StreamSource>>()
            for (channel in channels) {
                if (channel.name.contains("CCTV-8") || channel.name == "CCTV-8") {
                    for (source in channel.sources) {
                        cctv8Sources.add(Pair(channel, source))
                    }
                }
            }

            // 静默测CCTV-8 - 所有源测完再播放
            var bestCctv8: Pair<Channel, StreamSource>? = null
            var bestCctv8Speed: Long = Long.MAX_VALUE
            val testItems = mutableListOf<SpeedTestItem>()

            for ((index, pair) in cctv8Sources.withIndex()) {
                val (channel, source) = pair
                val speedMs = measureSourceSpeedFast(source.url)

                val status = if (speedMs != null && speedMs <= 200) {
                    if (speedMs < bestCctv8Speed) {
                        bestCctv8Speed = speedMs
                        bestCctv8 = pair
                    }
                    TestStatus.SUCCESS
                } else {
                    TestStatus.FAILED
                }

                testItems.add(SpeedTestItem(
                    channelName = channel.name,
                    sourceUrl = source.url,
                    quality = source.quality,
                    status = status,
                    speedMs = speedMs,
                    message = if (speedMs == null) "超时" else if (speedMs > 200) "${speedMs}ms太慢" else ""
                ))

                delay(50) // 测完一个稍作延迟
            }

            // 测完后更新一次UI，然后立即播放
            _playerState.value = _playerState.value.copy(
                speedTestItems = testItems.toList(),
                bestChannel = bestCctv8?.first?.name,
                bestSpeed = if (bestCctv8Speed < Long.MAX_VALUE) bestCctv8Speed else null
            )

            // 找到CCTV-8的最快源，立即播放并缓存
            if (bestCctv8 != null) {
                val channel = bestCctv8.first
                val sourceIdx = channel.sources.indexOf(bestCctv8.second)
                channelRepository.saveBestSourceIndex(channel.id, sourceIdx)
                _playerState.value = _playerState.value.copy(
                    isSpeedTesting = false,
                    currentChannel = channel.copy(currentSourceIndex = sourceIdx),
                    currentSourceIndex = sourceIdx
                )
                playSource(bestCctv8.second, channel.copy(currentSourceIndex = sourceIdx))
            } else {
                _playerState.value = _playerState.value.copy(isSpeedTesting = false)
            }

            // 第二步：后台继续测CCTV-1和CCTV-6并保存最佳源
            async(Dispatchers.IO) {
                val backgroundChannels = listOf("CCTV-1", "CCTV-6")

                for (priorityName in backgroundChannels) {
                    for (channel in channels) {
                        if (channel.name.contains(priorityName)) {
                            var bestIdx = -1
                            var bestSpeed = Long.MAX_VALUE
                            for ((idx, source) in channel.sources.withIndex()) {
                                val speedMs = measureSourceSpeedFast(source.url)
                                if (speedMs != null && speedMs < bestSpeed && speedMs <= 300) {
                                    bestSpeed = speedMs
                                    bestIdx = idx
                                }
                                delay(500)
                            }
                            if (bestIdx >= 0) {
                                channelRepository.saveBestSourceIndex(channel.id, bestIdx)
                            }
                        }
                    }
                }
            }

            // 返回结果
            if (bestCctv8 != null) {
                val result = SpeedTestResult(
                    channelName = bestCctv8.first.name,
                    sourceUrl = bestCctv8.second.url,
                    quality = bestCctv8.second.quality,
                    speedMs = bestCctv8Speed,
                    isSuccess = true
                )
                onComplete(result)
            }
        }
    }

    // 快速测速 - 200ms超时
    private suspend fun measureSourceSpeedFast(url: String): Long? = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val urlObj = URL(url)
            val conn = urlObj.openConnection() as HttpURLConnection
            conn.connectTimeout = 200
            conn.readTimeout = 200
            conn.requestMethod = "HEAD"
            conn.instanceFollowRedirects = true

            val responseCode = conn.responseCode
            val endTime = System.currentTimeMillis()

            conn.disconnect()

            if (responseCode == HttpURLConnection.HTTP_OK ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == 301 || responseCode == 302) {
                return@withContext endTime - startTime
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // 获取远程源 — 用户可在 ChannelRepository 中配置远程源 URL
    // 远程源格式: 频道名,URL (每行一个，支持 #genre# 分类标记)
    private suspend fun fetchRemoteSources(): Result<List<String>> = withContext(Dispatchers.IO) {
        // 如果你有远程 IPTV 源地址，在这里填入 URL:
        // val url = URL("https://你的远程源地址/iptv.txt")
        // 然后取消下面注释:
        /*
        try {
            val url = URL("https://example.com/iptv.txt")  // 替换为你的远程源地址
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP ${conn.responseCode}"))
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
            val lines = reader.readLines()
            reader.close()
            conn.disconnect()

            Result.success(lines)
        } catch (e: Exception) {
            Result.failure(e)
        }
        */
        // 默认不启用远程源，使用内置源即可
        Result.failure(Exception("未配置远程源"))
    }

    fun cancelSpeedTest() {
        _playerState.value = _playerState.value.copy(
            isSpeedTesting = false,
            speedTestLogs = _playerState.value.speedTestLogs + "测速已取消"
        )
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
