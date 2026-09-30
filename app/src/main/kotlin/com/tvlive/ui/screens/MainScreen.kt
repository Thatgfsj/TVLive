package com.tvlive.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvlive.data.model.Channel
import com.tvlive.data.model.ChannelCategory
import com.tvlive.player.PlayerViewModel
import com.tvlive.ui.components.CategoryTabs
import com.tvlive.ui.components.ChannelCard
import com.tvlive.ui.components.SpeedTestDialog
import com.tvlive.ui.components.VideoPlayer
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val view = LocalView.current

    var showChannelList by remember { mutableStateOf(false) }
    // 初始分类跟随默认频道，避免列表打开时停在空分类上
    var selectedCategory by remember { mutableStateOf(viewModel.getDefaultChannel().category) }
    var selectedChannelIndex by remember { mutableIntStateOf(0) }

    val categories = remember { viewModel.getAllCategories() }

    // 只显示有直播源的频道（无源频道无法播放，列出只会造成困惑）
    val channels = remember(selectedCategory) {
        viewModel.getChannelsByCategory(selectedCategory)
    }

    // 分类或数据变化时，修正越界的选中索引
    LaunchedEffect(channels) {
        if (selectedChannelIndex >= channels.size) selectedChannelIndex = 0
    }

    // 让列表选中项跟随正在播放的频道（启动默认频道、遥控换台后保持一致）
    LaunchedEffect(playerState.currentChannel?.id, channels) {
        val playingId = playerState.currentChannel?.id ?: return@LaunchedEffect
        val idx = channels.indexOfFirst { it.id == playingId }
        if (idx >= 0) selectedChannelIndex = idx
    }

    // 初始化播放器 - 有缓存直接播，没缓存再测速
    LaunchedEffect(Unit) {
        viewModel.initializePlayer()
        delay(300)
        viewModel.quickStartOrTest()
    }

    // 频道列表打开时，网格自动滚动到选中的频道
    val gridState = rememberLazyGridState()
    LaunchedEffect(selectedChannelIndex, showChannelList) {
        if (showChannelList && selectedChannelIndex < channels.size) {
            gridState.scrollToItem(selectedChannelIndex)
        }
    }

    // 处理遥控器按键：注册到 Activity 层的 KeyEventHub，在焦点系统之前接管，
    // 不依赖 ComposeView 是否持有焦点（触摸设备上它经常不持有）
    val latestChannels by rememberUpdatedState(channels)
    val latestCategories by rememberUpdatedState(categories)
    val latestCategory by rememberUpdatedState(selectedCategory)
    DisposableEffect(view) {
        val onKeyEvent = { event: KeyEvent ->
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    when (event.keyCode) {
                        // 上方向键 - 列表中向上选择；全屏时上一频道
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (showChannelList && latestChannels.isNotEmpty()) {
                                selectedChannelIndex = (selectedChannelIndex - 1).coerceAtLeast(0)
                            } else if (latestChannels.isNotEmpty()) {
                                selectedChannelIndex = (selectedChannelIndex - 1 + latestChannels.size) % latestChannels.size
                                viewModel.playChannel(latestChannels[selectedChannelIndex])
                            }
                            true
                        }
                        // 下方向键 - 列表中向下选择；全屏时下一频道
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (showChannelList && latestChannels.isNotEmpty()) {
                                selectedChannelIndex = (selectedChannelIndex + 1).coerceAtMost(latestChannels.size - 1)
                            } else if (latestChannels.isNotEmpty()) {
                                selectedChannelIndex = (selectedChannelIndex + 1) % latestChannels.size
                                viewModel.playChannel(latestChannels[selectedChannelIndex])
                            }
                            true
                        }
                        // CH+/CH- 实体换台键
                        KeyEvent.KEYCODE_CHANNEL_UP -> {
                            if (latestChannels.isNotEmpty()) {
                                selectedChannelIndex = (selectedChannelIndex - 1 + latestChannels.size) % latestChannels.size
                                viewModel.playChannel(latestChannels[selectedChannelIndex])
                            }
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            if (latestChannels.isNotEmpty()) {
                                selectedChannelIndex = (selectedChannelIndex + 1) % latestChannels.size
                                viewModel.playChannel(latestChannels[selectedChannelIndex])
                            }
                            true
                        }
                        // 返回键 - 关闭列表或显示列表
                        KeyEvent.KEYCODE_BACK -> {
                            showChannelList = !showChannelList
                            true
                        }
                        // 设置/菜单键 - 显示/隐藏列表
                        KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_MENU -> {
                            showChannelList = !showChannelList
                            true
                        }
                        // 确认键 - 播放选中的频道
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            when {
                                showChannelList && latestChannels.isNotEmpty() &&
                                    selectedChannelIndex < latestChannels.size -> {
                                    viewModel.playChannel(latestChannels[selectedChannelIndex])
                                    showChannelList = false
                                }
                                !showChannelList -> showChannelList = true
                            }
                            true
                        }
                        // 列表中左右键切换分类
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (showChannelList && latestCategories.isNotEmpty()) {
                                val idx = latestCategories.indexOf(latestCategory).coerceAtLeast(0)
                                selectedCategory = latestCategories[(idx - 1 + latestCategories.size) % latestCategories.size]
                                selectedChannelIndex = 0
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (showChannelList && latestCategories.isNotEmpty()) {
                                val idx = latestCategories.indexOf(latestCategory).coerceAtLeast(0)
                                selectedCategory = latestCategories[(idx + 1) % latestCategories.size]
                                selectedChannelIndex = 0
                            }
                            true
                        }
                        // 数字键 0-9 - 快速切换当前分类的频道
                        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2,
                        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5,
                        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8,
                        KeyEvent.KEYCODE_9 -> {
                            val num = event.keyCode - KeyEvent.KEYCODE_0
                            if (num < latestChannels.size) {
                                selectedChannelIndex = num
                                viewModel.playChannel(latestChannels[num])
                                showChannelList = false
                            }
                            true
                        }
                        // 直播键 (部分电视机的"直播"键) - 打开频道列表
                        KeyEvent.KEYCODE_TV -> {
                            showChannelList = true
                            true
                        }
                        else -> false
                    }
                }
                else -> false
            }
        }

        val dispatchOwner = view.context as? com.tvlive.KeyDispatchOwner
        dispatchOwner?.keyHub?.handler = onKeyEvent
        onDispose {
            dispatchOwner?.keyHub?.handler = null
        }
    }

    // 显示测速弹窗（实时刷新进度）
    if (playerState.isSpeedTesting) {
        SpeedTestDialog(
            items = playerState.speedTestItems,
            progress = playerState.speedTestProgress,
            currentTesting = playerState.currentTesting,
            bestChannel = playerState.bestChannel,
            bestSpeed = playerState.bestSpeed,
            onSkip = { viewModel.skipSpeedTest() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
    ) {
        // 全屏播放器
        VideoPlayer(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // 频道信息（换台/加载时短暂显示，4 秒后自动隐藏，不遮挡画面）
        var infoVisible by remember { mutableStateOf(true) }
        LaunchedEffect(playerState.currentChannel?.id, playerState.isAutoSwitching, playerState.isLoading) {
            infoVisible = true
            if (!playerState.isLoading) {
                delay(4000)
                infoVisible = false
            }
        }
        if ((infoVisible || playerState.isLoading || playerState.isAutoSwitching)) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                playerState.currentChannel?.let { channel ->
                    Box(
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                shape = MaterialTheme.shapes.medium
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                            color = Color.White
                        )
                    }
                }

                if (playerState.isLoading) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "正在加载...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }

                if (playerState.isAutoSwitching && playerState.switchReason != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                Color(0xFFFFA000),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "正在切换: ${playerState.switchReason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 错误提示
        playerState.error?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.retry() }
                    ) {
                        Text("重试")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showChannelList = true }
                    ) {
                        Text("选择频道")
                    }
                }
            }
        }

        // 频道列表覆盖层
        if (showChannelList) {
            ChannelListOverlay(
                categories = categories,
                channels = channels,
                playingChannelId = playerState.currentChannel?.id,
                selectedCategory = selectedCategory,
                selectedChannelIndex = selectedChannelIndex,
                gridState = gridState,
                onCategoryChange = {
                    selectedCategory = it
                    selectedChannelIndex = 0
                },
                onChannelSelected = { index ->
                    selectedChannelIndex = index
                    viewModel.playChannel(channels[index])
                    showChannelList = false
                }
            )
        }
    }
}

@Composable
fun ChannelListOverlay(
    categories: List<ChannelCategory>,
    channels: List<Channel>,
    playingChannelId: String?,
    selectedCategory: ChannelCategory,
    selectedChannelIndex: Int,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onCategoryChange: (ChannelCategory) -> Unit,
    onChannelSelected: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧分类（只列出有频道的分类）
            CategoryTabs(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelected = onCategoryChange
            )

            // 右侧频道网格
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedCategory.displayName}频道 · ${channels.size}个",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = "确认键播放 · 返回键关闭",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                if (channels.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "该分类暂无可播放的频道",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "请添加直播源后重启应用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(channels) { index, channel ->
                            ChannelCard(
                                channel = channel,
                                isSelected = index == selectedChannelIndex,
                                isPlaying = channel.id == playingChannelId,
                                onClick = { onChannelSelected(index) }
                            )
                        }
                    }
                }
            }
        }
    }
}
