package com.tvlive.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.tvlive.data.model.Channel
import com.tvlive.data.model.ChannelCategory
import com.tvlive.data.model.StreamSource
import com.tvlive.data.repository.ChannelRepository
import com.tvlive.ui.components.SpeedTestItem
import com.tvlive.ui.components.TestStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlin.math.abs

data class PlayerState(
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val currentChannel: Channel? = null,
    val currentSourceIndex: Int = 0,
    val isAutoSwitching: Boolean = false,
    val switchReason: String? = null,
    val isSpeedTesting: Boolean = false,
    val speedTestProgress: Float = 0f,
    val speedTestItems: List<SpeedTestItem> = emptyList(),
    val currentTesting: String? = null,
    val bestChannel: String? = null,
    val bestSpeed: Long? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    companion object {
        private const val TAG = "TVLivePlayer"
        private const val SWITCH_DEBOUNCE_MS = 2000L
        private const val CHANNEL_SWITCH_DEBOUNCE_MS = 500L
        private const val WATCHDOG_INTERVAL_MS = 4000L
        private const val SPEED_TEST_TIMEOUT_MS = 3000

        /** 连续 N 次检测到画面无进展（约 8 秒）判定为卡死 */
        private const val STALL_CHECKS = 2

        /** 连续 N 次检测到缓冲中（约 8 秒）判定为源不可用 */
        private const val BUFFERING_CHECKS_LIMIT = 2

        /** 全部源失败后自动跳频道的最大连续次数（防止全站皆死时无限循环） */
        private const val MAX_AUTO_ADVANCE = 5

        /**
         * 远程 IPTV 源地址（支持 m3u / txt 格式），每 3 天自动拉取一次，
         * 新源并入内置频道（内置源优先）。留空则不启用远程更新。
         * 为规避版权风险，本项目默认留空；请自行填入你信任的公共列表地址。
         */
        private const val REMOTE_SOURCE_URL = ""
    }

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var playbackCheckJob: Job? = null
    private var speedTestJob: Job? = null
    private var errorHandlingJob: Job? = null
    private var autoRetryJob: Job? = null
    private var lastSwitchTime = 0L
    private var lastChannelSwitchTime = 0L

    /** 距离上次成功播放（STATE_READY）以来自动跳频道的次数 */
    private var autoAdvanceCount = 0

    /** 当前源开始播放的时刻：用于判断失败前是否给过它足够的机会（8 秒） */
    private var currentSourceStartedAt = 0L

    /** 当前源这次尝试是否成功播出过：没播出过的死源不值得原位重载 */
    private var currentSourceEverReady = false

    /** 本频道本次进入后是否成功播出过 */
    private var everReadyThisChannel = false

    /** 最近一次成功播出（STATE_READY）的源索引：重新轮询时优先从它开始 */
    private var lastReadySourceIndex = 0

    /** 全部源失败后重新轮询的轮数 */
    private var fullCycles = 0

    /** 一个源至少要跑过这么久才允许被标记为"失败"（几秒内就挂的多半是网络抖动） */
    private val MIN_TRIAL_MS = 8000L

    /** 源健康度评分（本会话内）：成功 +2，确认失败 -3。换源时优先切高分源 */
    private val sourceScores = mutableMapOf<String, Int>()

    /** 本次会话中已确认失败的源 URL，自动换源时跳过；换频道时清空 */
    private val sessionFailedSources = mutableSetOf<String>()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> update { it.copy(isLoading = true, error = null) }
                Player.STATE_READY -> {
                    // 播放成功才把当前源记为最佳源，供下次启动秒开
                    autoAdvanceCount = 0
                    everReadyThisChannel = true
                    currentSourceEverReady = true
                    lastReadySourceIndex = _playerState.value.currentSourceIndex
                    val st = _playerState.value
                    st.currentChannel?.sources?.getOrNull(st.currentSourceIndex)?.url?.let { url ->
                        sourceScores[url] = (sourceScores[url] ?: 0) + 2
                    }
                    st.currentChannel?.let { channel ->
                        channelRepository.saveBestSourceIndex(channel.id, st.currentSourceIndex)
                    }
                    update {
                        it.copy(
                            isLoading = false,
                            isPlaying = true,
                            error = null,
                            isAutoSwitching = false,
                            switchReason = null
                        )
                    }
                }
                Player.STATE_IDLE -> update { it.copy(isPlaying = false) }
                // STATE_ENDED 不在此处理：HLS 直播 playlist 轮换时会短暂触发，
                // 干预会造成换源风暴。由 watchdog 判断真卡死后先重试再换源。
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "播放出错: ${error.errorCodeName}")
            errorHandlingJob?.cancel()
            errorHandlingJob = viewModelScope.launch {
                // 换源后 2 秒内报的错可能来自旧源，先正常处理；
                // 若因防抖被跳过，稍后复查一次，避免新源快速失败时没人管
                if (!handlePlaybackError("播放出错")) {
                    delay(SWITCH_DEBOUNCE_MS)
                    handlePlaybackError("播放出错复查")
                }
            }
        }
    }

    /**
     * 播放失败时的自动恢复。返回 false 表示因防抖跳过（调用方需复查）。
     *
     * @param force 看门狗发起的强制恢复：绕过"正在播放/缓冲"保护。
     *   该保护只适用于 onPlayerError（报错的可能已是被废弃的旧源）；
     *   而看门狗正是因为 READY 但画面停住 / BUFFERING 超时才调用的，
     *   如果也被这个保护挡掉，就会永远卡住不再换源。
     */
    private suspend fun handlePlaybackError(reason: String, force: Boolean = false): Boolean {
        val player = exoPlayer ?: return true
        if (!force) {
            val state = player.playbackState
            if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                // 已经在正常播放/缓冲，报错的是已废弃的旧源，忽略
                update { it.copy(isLoading = state == Player.STATE_BUFFERING, error = null) }
                return true
            }
        }

        val st = _playerState.value
        val channel = st.currentChannel ?: return true

        val now = System.currentTimeMillis()

        // 跑够 8 秒才失败的源才记为"坏源"；几秒内就挂的可能是网络抖动，不标记
        // 注意：当前源以顶层 currentSourceIndex 为准（currentChannel.currentSourceIndex 是惰性副本）
        val currentUrl = channel.sources.getOrNull(st.currentSourceIndex)?.url
        if (currentUrl != null && now - currentSourceStartedAt >= MIN_TRIAL_MS) {
            sessionFailedSources.add(currentUrl)
            sourceScores[currentUrl] = (sourceScores[currentUrl] ?: 0) - 3
            Log.d(TAG, "标记失败源（跑了 ${now - currentSourceStartedAt}ms）: $currentUrl")
        }

        if (now - lastSwitchTime < SWITCH_DEBOUNCE_MS) return false

        // 找下一个没失败过的源：健康度评分高的优先（本会话成功过的源先试），
        // 同分时按轮转顺序，保证公平覆盖所有源
        var candidate = -1
        var bestScore = Int.MIN_VALUE
        if (channel.sources.size > 1) {
            var idx = (st.currentSourceIndex + 1) % channel.sources.size
            var attempts = 0
            while (attempts < channel.sources.size) {
                if (channel.sources[idx].url !in sessionFailedSources) {
                    val score = sourceScores[channel.sources[idx].url] ?: 0
                    if (candidate == -1 || score > bestScore) {
                        candidate = idx
                        bestScore = score
                    }
                }
                idx = (idx + 1) % channel.sources.size
                attempts++
            }
        }

        if (candidate >= 0) {
            lastSwitchTime = now
            // 自动换源静默化：不弹"正在切换"提示（只用加载圈表达），
            // 避免观看时频繁被打扰；跨频道跳转才提示
            update {
                it.copy(
                    currentChannel = channel.withSourceIndex(candidate),
                    currentSourceIndex = candidate,
                    isLoading = true,
                    error = null,
                    isAutoSwitching = true,
                    switchReason = null
                )
            }
            if (candidate == st.currentSourceIndex) {
                Log.d(TAG, "其余源均已标记失败，重试当前源${candidate + 1}（$reason）")
            } else {
                Log.d(TAG, "换源: ${channel.name} 源${st.currentSourceIndex + 1} → 源${candidate + 1}（$reason）")
            }
            playSource(channel.sources[candidate], channel.withSourceIndex(candidate))
            return true
        }

        // 本频道所有源都被标记失败：
        // - 播出过的（源只是暂时病了）：清标记，从最近成功源快速重试
        // - 从未播出过（如晚高峰集体饿死）：并发测速全部源，直接跳到实测最快的，
        //   避免逐个盲试造成长时间黑屏
        fullCycles++
        if (everReadyThisChannel && fullCycles < 2) {
            sessionFailedSources.clear()
            lastSwitchTime = now
            val resumeIdx = if (lastReadySourceIndex in channel.sources.indices) lastReadySourceIndex else 0
            Log.w(TAG, "「${channel.name}」全部源失败，第 $fullCycles 轮重新轮询（从源${resumeIdx + 1}开始）")
            update {
                it.copy(
                    currentChannel = channel.withSourceIndex(resumeIdx),
                    currentSourceIndex = resumeIdx,
                    isLoading = true,
                    error = null,
                    isAutoSwitching = true
                )
            }
            playSource(channel.sources[resumeIdx], channel.withSourceIndex(resumeIdx))
            return true
        }

        // 并发测速（迷你竞速）：全部源同时探测，约 5 秒出结果
        sessionFailedSources.clear()
        Log.w(TAG, "「${channel.name}」从未播出/多轮失败，并发测速 ${channel.sources.size} 个源")
        val ranked = channel.sources.map { src ->
            viewModelScope.async(Dispatchers.IO) {
                src to (measureSourceSpeedFast(src.url) ?: Long.MAX_VALUE)
            }
        }.awaitAll()
            .filter { it.second != Long.MAX_VALUE }
            .sortedByDescending { it.second }
        if (ranked.isNotEmpty()) {
            val best = ranked.first()
            val idx = channel.sources.indexOf(best.first)
            lastSwitchTime = now
            Log.w(TAG, "测速完成，跳到最快源${idx + 1}（${best.second}ms）: ${best.first.url}")
            update {
                it.copy(
                    currentChannel = channel.withSourceIndex(idx),
                    currentSourceIndex = idx,
                    isLoading = true,
                    error = null,
                    isAutoSwitching = true
                )
            }
            playSource(best.first, channel.withSourceIndex(idx))
            return true
        }

        // 测速也全部超时：自动跳到下一个有源的频道（老人场景下比报错停在黑屏更好）
        if (autoAdvanceCount < MAX_AUTO_ADVANCE) {
            val channels = playableChannels()
            val currentIdx = channels.indexOfFirst { it.id == channel.id }
            if (channels.size > 1 && currentIdx >= 0) {
                val next = channels[(currentIdx + 1) % channels.size]
                if (next.id != channel.id && next.sources.isNotEmpty()) {
                    autoAdvanceCount++
                    lastSwitchTime = now
                    Log.w(TAG, "「${channel.name}」连续 $fullCycles 轮全部源失败，自动跳频道 → ${next.name}（第 $autoAdvanceCount 次）")
                    update {
                        it.copy(
                            isAutoSwitching = true,
                            switchReason = "源全部失败，切换到 ${next.name}",
                            error = null
                        )
                    }
                    playChannelInternal(next)
                    return true
                }
            }
        }

        Log.e(TAG, "「${channel.name}」所有源均失败，60 秒后自动重新寻源")
        update { it.copy(isLoading = false, isAutoSwitching = false, error = "所有源均失败，正在自动重试...") }
        // 无人值守：不永久躺平。高峰期全站皆死时每 60 秒重置计数全量重新寻源，
        // 任何一个源恢复，电视就自动亮起来
        autoRetryJob?.cancel()
        autoRetryJob = viewModelScope.launch {
            delay(60_000)
            if (_playerState.value.error == null) return@launch // 已恢复播放
            Log.d(TAG, "自动重新寻源：重置全部失败标记，全量轮询")
            autoAdvanceCount = 0
            fullCycles = 0
            sessionFailedSources.clear()
            playChannelInternal(_playerState.value.currentChannel ?: channel)
        }
        return true
    }

    fun initializePlayer() {
        if (exoPlayer != null) return

        // 针对低配电视的网络抖动调优：min=max 让缓冲始终保持在最满状态
        // （Akamai 直播缓冲最佳实践），源要"真死"很久才会断流
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                40000,   // minBufferMs
                40000,   // maxBufferMs（直播场景与 min 相等，保持常满）
                4000,    // bufferForPlaybackMs: 缓冲 4s 起播
                6000     // bufferForPlaybackAfterRebufferMs: 卡后攒 6s 再播
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 异步 MediaCodec 队列，降低低配电视解码压力；
        // 解码器回退：硬解个别流失败时自动换解码器，避免"有流无画面"
        val renderersFactory = DefaultRenderersFactory(context)
            .forceEnableMediaCodecAsynchronousQueueing()
            .setEnableDecoderFallback(true)

        // OkHttp 数据源：连接池 keep-alive 复用 TCP 连接，分片间少握手；
        // 跨协议重定向 + 5 秒超时快速失败
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0")
        val defaultDataSourceFactory = DefaultDataSource.Factory(context, dataSourceFactory)

        // HLS 分片加载失败先在源内部重试 5 次（指数退避），
        // 短暂网络抖动不升级为换源
        val mediaSourceFactory = DefaultMediaSourceFactory(defaultDataSourceFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(5))

        exoPlayer = ExoPlayer.Builder(context, renderersFactory, mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .also { player ->
                player.addListener(playerListener)
                player.playWhenReady = true
                player.setHandleAudioBecomingNoisy(true)
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(1920, 1080)
                    .build()
            }
    }

    fun releasePlayer() {
        playbackCheckJob?.cancel()
        playbackCheckJob = null
        speedTestJob?.cancel()
        speedTestJob = null
        errorHandlingJob?.cancel()
        errorHandlingJob = null
        autoRetryJob?.cancel()
        autoRetryJob = null
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        _playerState.value = PlayerState()
    }

    /** 用户主动选台：重置自动跳频道预算后播放 */
    fun playChannel(channel: Channel) {
        autoAdvanceCount = 0
        playChannelInternal(channel)
    }

    private fun playChannelInternal(channel: Channel) {
        // 防抖：短时间内重复调用直接忽略
        val now = System.currentTimeMillis()
        if (now - lastChannelSwitchTime < CHANNEL_SWITCH_DEBOUNCE_MS) return
        lastChannelSwitchTime = now
        autoRetryJob?.cancel()

        if (channel.sources.isEmpty()) {
            update {
                it.copy(
                    currentChannel = channel,
                    isLoading = false,
                    isAutoSwitching = false,
                    error = "「${channel.name}」暂无直播源，请先添加"
                )
            }
            return
        }

        sessionFailedSources.clear()
        everReadyThisChannel = false
        fullCycles = 0
        // 使用缓存的最佳源索引
        val bestIdx = channelRepository.getBestSourceIndex(channel.id)
        val idx = if (bestIdx in channel.sources.indices) bestIdx else 0
        lastReadySourceIndex = idx
        update {
            it.copy(
                currentChannel = channel.withSourceIndex(idx),
                currentSourceIndex = idx,
                isLoading = true,
                error = null,
                isAutoSwitching = false,
                switchReason = null
            )
        }
        channelRepository.saveLastChannel(channel.id)
        playSource(channel.sources[idx], channel.withSourceIndex(idx))
    }

    private fun playSource(source: StreamSource, channel: Channel) {
        exoPlayer?.let { player ->
            currentSourceStartedAt = System.currentTimeMillis()
            currentSourceEverReady = false
            // setMediaItem() 是高层 API，内部正确处理跨流解码器过渡，不会双重音频
            player.setMediaItem(buildMediaItem(source))
            player.prepare()
            player.playWhenReady = true
            startPlaybackWatchdog()
        }
    }

    /**
     * 播放看门狗：每 4 秒检查一次播放器真实状态，卡顿的最后一道防线。
     * 全部使用 force 调用——看门狗就是因为状态不对才触发的，
     * 不能被"正在播放/缓冲"保护挡掉。
     * - READY 但进度约 8 秒不动（画面停住）→ 先原位重试当前源一次，仍卡则标记失败换源
     * - BUFFERING 超 16 秒（源基本死了）→ 换源
     * - ENDED（直播 playlist 结束）→ 先重试当前源，反复出现则换源
     * - IDLE（异常中断）→ 换源
     */
    private fun startPlaybackWatchdog() {
        playbackCheckJob?.cancel()
        playbackCheckJob = viewModelScope.launch {
            var lastPosition = -1L
            var stalledChecks = 0
            var stallRetries = 0
            var bufferingChecks = 0
            var bufferRetried = false
            var endedRetried = false
            var heartbeatTicks = 0
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val player = exoPlayer ?: continue

                // 心跳日志：每分钟记录一次播放状态，便于事后排查
                heartbeatTicks++
                if (heartbeatTicks % 15 == 0) {
                    val st = _playerState.value
                    Log.d(
                        TAG,
                        "心跳: ${st.currentChannel?.name} 源${st.currentSourceIndex + 1}/${st.currentChannel?.sources?.size} " +
                            "state=${player.playbackState} pos=${player.currentPosition / 1000}s"
                    )
                }
                when (player.playbackState) {
                    Player.STATE_READY -> {
                        bufferingChecks = 0
                        bufferRetried = false
                        endedRetried = false
                        val pos = player.currentPosition
                        if (player.playWhenReady && abs(pos - lastPosition) < 500) {
                            stalledChecks++
                        } else {
                            stalledChecks = 0
                            stallRetries = 0
                        }
                        lastPosition = pos
                        if (stalledChecks >= STALL_CHECKS && player.playWhenReady) {
                            stalledChecks = 0
                            val st = _playerState.value
                            val channel = st.currentChannel ?: continue
                            val source = channel.sources.getOrNull(st.currentSourceIndex) ?: continue
                            if (stallRetries < 1) {
                                // 画面停住但没报错，多半是 playlist 断了：原位重试一次
                                stallRetries++
                                Log.w(TAG, "检测到画面停滞（${source.url}），原位重试")
                                player.setMediaItem(buildMediaItem(source))
                                player.prepare()
                                player.playWhenReady = true
                            } else {
                                // 重试过了还卡，这个源确实不行
                                stallRetries = 0
                                sessionFailedSources.add(source.url)
                                if (!handlePlaybackError("持续卡顿", force = true)) {
                                    delay(SWITCH_DEBOUNCE_MS)
                                    handlePlaybackError("持续卡顿复查", force = true)
                                }
                            }
                        }
                    }
                    Player.STATE_BUFFERING -> {
                        bufferingChecks++
                        if (bufferingChecks >= BUFFERING_CHECKS_LIMIT) {
                            bufferingChecks = 0
                            val st = _playerState.value
                            val source = st.currentChannel?.sources?.getOrNull(st.currentSourceIndex)
                            if (!bufferRetried && currentSourceEverReady && source != null) {
                                // 播出过才中断的：先原位重载 playlist，比换源恢复更快；
                                // 从没播出过的死源，重载同一个 URL 纯属浪费黑屏时间
                                bufferRetried = true
                                Log.w(TAG, "缓冲超时，原位重载重试")
                                player.setMediaItem(buildMediaItem(source))
                                player.prepare()
                                player.playWhenReady = true
                            } else {
                                bufferRetried = false
                                Log.w(TAG, "缓冲超时，换源")
                                if (!handlePlaybackError("缓冲超时", force = true)) {
                                    delay(SWITCH_DEBOUNCE_MS)
                                    handlePlaybackError("缓冲超时复查", force = true)
                                }
                            }
                        }
                    }
                    Player.STATE_ENDED -> {
                        if (!endedRetried) {
                            endedRetried = true
                            Log.w(TAG, "直播流结束，原位重试")
                            val st = _playerState.value
                            val source = st.currentChannel?.sources?.getOrNull(st.currentSourceIndex)
                            if (source != null) {
                                player.setMediaItem(buildMediaItem(source))
                                player.prepare()
                                player.playWhenReady = true
                            }
                        } else {
                            endedRetried = false
                            if (!handlePlaybackError("直播流结束", force = true)) {
                                delay(SWITCH_DEBOUNCE_MS)
                                handlePlaybackError("直播流结束复查", force = true)
                            }
                        }
                    }
                    Player.STATE_IDLE -> {
                        if (!handlePlaybackError("播放中断", force = true)) {
                            delay(SWITCH_DEBOUNCE_MS)
                            handlePlaybackError("播放中断复查", force = true)
                        }
                    }
                }
            }
        }
    }

    private fun buildMediaItem(source: StreamSource): MediaItem {
        return MediaItem.Builder()
            .setUri(Uri.parse(source.url))
            // 直播边缘"深蹲"策略（官方文档 + androidx#1852 实践）：
            // 播放位置蹲在直播边缘后 15 秒，瞬时抖动 15 秒内根本饿不着；
            // 倍速锁 1.0-1.1：不追边（追边是卡顿元凶），只允许缓慢回追保持深度
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(15000)
                    .setMinOffsetMs(8000)
                    .setMaxOffsetMs(25000)
                    .setMinPlaybackSpeed(1.0f)
                    .setMaxPlaybackSpeed(1.1f)
                    .build()
            )
            .build()
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun switchToNextChannel() {
        val channels = playableChannels()
        if (channels.isEmpty()) return
        val current = _playerState.value.currentChannel
        val index = channels.indexOfFirst { it.id == current?.id }
        playChannel(if (index in 0 until channels.size - 1) channels[index + 1] else channels[0])
    }

    fun switchToPreviousChannel() {
        val channels = playableChannels()
        if (channels.isEmpty()) return
        val current = _playerState.value.currentChannel
        val index = channels.indexOfFirst { it.id == current?.id }
        playChannel(if (index > 0) channels[index - 1] else channels[channels.size - 1])
    }

    fun retry() {
        val st = _playerState.value
        val channel = st.currentChannel ?: return
        val source = channel.currentSource ?: return
        sessionFailedSources.remove(source.url)
        update { it.copy(isLoading = true, error = null, isAutoSwitching = false, switchReason = null) }
        playSource(source, channel)
    }

    /** 手动切换到下一个源（不标记失败） */
    fun switchToNextSource() {
        val st = _playerState.value
        val channel = st.currentChannel ?: return
        if (channel.sources.size <= 1) return

        val nextIndex = (st.currentSourceIndex + 1) % channel.sources.size
        update {
            it.copy(
                currentChannel = channel.withSourceIndex(nextIndex),
                currentSourceIndex = nextIndex,
                isAutoSwitching = false,
                switchReason = null,
                isLoading = true,
                error = null
            )
        }
        playSource(channel.sources[nextIndex], channel.withSourceIndex(nextIndex))
    }

    fun playDefaultChannel() {
        playChannel(channelRepository.getDefaultChannel())
    }

    fun getDefaultChannel(): Channel = channelRepository.getDefaultChannel()

    /** 快速启动：有缓存的最佳源就直接播放，否则测速选源 */
    fun quickStartOrTest() {
        val defaultChannel = channelRepository.getDefaultChannel()
        val bestIdx = channelRepository.getBestSourceIndex(defaultChannel.id)
        if (bestIdx in defaultChannel.sources.indices) {
            playChannel(defaultChannel.withSourceIndex(bestIdx))
            viewModelScope.launch(Dispatchers.IO) { refreshRemoteSourcesIfNeeded() }
        } else {
            speedTestAndPlay()
        }
    }

    // ============================================================
    // 测速
    // ============================================================

    /**
     * 测速默认频道的所有源并播放最快的。逐个测、逐个刷新 UI，
     * 单源频道不弹测速窗直接播。
     */
    private fun speedTestAndPlay() {
        speedTestJob?.cancel()
        speedTestJob = viewModelScope.launch {
            val defaultChannel = channelRepository.getDefaultChannel()
            val sources = defaultChannel.sources

            if (sources.isEmpty()) {
                update {
                    it.copy(isSpeedTesting = false, error = "暂无可播放的频道，请先添加直播源")
                }
                return@launch
            }

            val showUi = sources.size > 1
            update { st ->
                st.copy(
                    isSpeedTesting = showUi,
                    speedTestProgress = 0f,
                    speedTestItems = sources.map { source ->
                        SpeedTestItem(
                            channelName = defaultChannel.name,
                            sourceUrl = source.url,
                            quality = source.quality
                        )
                    },
                    currentTesting = null,
                    bestChannel = null,
                    bestSpeed = null
                )
            }

            val remoteJob = launch(Dispatchers.IO) { refreshRemoteSourcesIfNeeded() }

            val items = sources.map { source ->
                SpeedTestItem(defaultChannel.name, source.url, source.quality)
            }.toMutableList()
            var bestIdx = 0
            var bestSpeed = Long.MAX_VALUE

            for ((i, source) in sources.withIndex()) {
                if (showUi) {
                    update {
                        it.copy(
                            currentTesting = "源${i + 1} · ${source.quality}",
                            speedTestItems = it.speedTestItems.mapIndexed { j, item ->
                                if (j == i) item.copy(status = TestStatus.TESTING) else item
                            }
                        )
                    }
                }
                val speedMs = measureSourceSpeedFast(source.url)
                val ok = speedMs != null && speedMs <= SPEED_TEST_TIMEOUT_MS
                if (ok && speedMs != null && speedMs < bestSpeed) {
                    bestSpeed = speedMs
                    bestIdx = i
                }
                items[i] = items[i].copy(
                    status = if (ok) TestStatus.SUCCESS else TestStatus.FAILED,
                    speedMs = speedMs,
                    message = when {
                        speedMs == null -> "超时"
                        !ok -> "太慢(${speedMs}ms)"
                        else -> ""
                    }
                )
                if (showUi) {
                    update {
                        it.copy(
                            speedTestItems = items.toList(),
                            speedTestProgress = (i + 1) / sources.size.toFloat()
                        )
                    }
                }
                delay(100)
            }

            val hasResult = bestSpeed < Long.MAX_VALUE
            if (showUi) {
                update {
                    it.copy(
                        isSpeedTesting = false,
                        bestChannel = if (hasResult) defaultChannel.name else null,
                        bestSpeed = if (hasResult) bestSpeed else null
                    )
                }
            }
            channelRepository.saveBestSourceIndex(defaultChannel.id, bestIdx)
            playChannel(defaultChannel.withSourceIndex(bestIdx))
        }
    }

    /** 跳过测速，直接用缓存/第一个源播放 */
    fun skipSpeedTest() {
        speedTestJob?.cancel()
        speedTestJob = null
        update { it.copy(isSpeedTesting = false) }
        playDefaultChannel()
    }

    /**
     * 快速测速：真实下载 m3u8 并验证首个分片可达，返回总耗时（毫秒），失败返回 null
     */
    private suspend fun measureSourceSpeedFast(url: String): Long? = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return@withContext null
            }

            // 读取 m3u8 内容，找到第一个分片地址
            var tsPath: String? = null
            conn.inputStream.bufferedReader().use { reader ->
                var lineCount = 0
                while (lineCount < 50) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("#") && (line.contains(".ts") || line.contains(".m3u8"))) {
                        tsPath = line.trim()
                        break
                    }
                    lineCount++
                }
            }
            conn.disconnect()

            // 验证首个分片可达
            if (tsPath != null) {
                val baseUrl = url.substringBeforeLast("/")
                val tsUrl = if (tsPath!!.startsWith("http")) tsPath else "$baseUrl/$tsPath"
                val tsConn = URL(tsUrl).openConnection() as HttpURLConnection
                tsConn.connectTimeout = 3000
                tsConn.readTimeout = 3000
                tsConn.requestMethod = "HEAD"
                tsConn.instanceFollowRedirects = true
                val code = tsConn.responseCode
                tsConn.disconnect()
                if (code == HttpURLConnection.HTTP_OK || code == 206) {
                    return@withContext System.currentTimeMillis() - startTime
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // ============================================================
    // 远程源更新
    // ============================================================

    private fun shouldUpdateSources(): Boolean {
        val lastUpdate = channelRepository.getLastUpdateTime()
        val threeDaysMs = 3L * 24 * 60 * 60 * 1000
        return System.currentTimeMillis() - lastUpdate > threeDaysMs
    }

    private suspend fun refreshRemoteSourcesIfNeeded() {
        if (REMOTE_SOURCE_URL.isBlank()) return
        if (!shouldUpdateSources()) return
        fetchRemoteSources().onSuccess { channelRepository.updateRemoteSources(it) }
    }

    /** 拉取远程 IPTV 源列表（仅支持 http/https） */
    private suspend fun fetchRemoteSources(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(REMOTE_SOURCE_URL)
            require(url.protocol == "http" || url.protocol == "https") { "仅支持 http/https" }
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                error("HTTP ${conn.responseCode}")
            }
            val lines = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readLines() }
            conn.disconnect()
            lines
        }
    }

    // ============================================================
    // 数据透传
    // ============================================================

    fun getChannels(): List<Channel> = channelRepository.getChannels()

    fun getAllCategories(): List<ChannelCategory> = channelRepository.getAllCategories()

    fun getChannelsByCategory(category: ChannelCategory): List<Channel> =
        channelRepository.getChannelsByCategory(category).filter { it.sources.isNotEmpty() }

    /** 有源可播的频道（播放器内部换台用） */
    private fun playableChannels(): List<Channel> = getChannels().filter { it.sources.isNotEmpty() }

    fun clearError() {
        update { it.copy(error = null) }
    }

    private fun update(transform: (PlayerState) -> PlayerState) {
        _playerState.value = transform(_playerState.value)
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
