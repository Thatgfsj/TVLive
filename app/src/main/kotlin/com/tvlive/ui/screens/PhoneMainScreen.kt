package com.tvlive.ui.screens

import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvlive.data.model.Channel
import com.tvlive.data.model.ChannelCategory
import com.tvlive.player.PlayerViewModel
import com.tvlive.ui.components.SpeedTestDialog
import com.tvlive.ui.components.VideoPlayer
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun PhoneMainScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val view = LocalView.current

    var showChannelList by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(ChannelCategory.CCTV) }
    var selectedChannelIndex by remember { mutableIntStateOf(0) }

    val categories = remember { viewModel.getAllCategories() }

    // 根据分类过滤频道
    // 不再用playerState作为key，避免每次播放状态变化都重新计算频道列表
    val channels = remember(selectedCategory) {
        viewModel.getChannelsByCategory(selectedCategory)
    }

    // 初始化播放器 - 有缓存直接播，没缓存再测速
    LaunchedEffect(Unit) {
        viewModel.initializePlayer()
        kotlinx.coroutines.delay(300)
        viewModel.quickStartOrTest()
    }

    // 自动隐藏控制按钮
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    // 手机按键处理
    BackHandler(enabled = showChannelList) {
        showChannelList = false
    }

    DisposableEffect(view) {
        val onKeyEvent = { event: KeyEvent ->
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            if (channels.isNotEmpty()) {
                                selectedChannelIndex = (selectedChannelIndex - 1 + channels.size) % channels.size
                                viewModel.playChannel(channels[selectedChannelIndex])
                            }
                            true
                        }
                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            if (channels.isNotEmpty()) {
                                selectedChannelIndex = (selectedChannelIndex + 1) % channels.size
                                viewModel.playChannel(channels[selectedChannelIndex])
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MENU -> {
                            showChannelList = !showChannelList
                            true
                        }
                        else -> false
                    }
                }
                else -> false
            }
        }

        val callback = object : android.view.View.OnKeyListener {
            override fun onKey(v: android.view.View?, keyCode: Int, event: KeyEvent?): Boolean {
                return if (event != null) onKeyEvent(event) else false
            }
        }

        view.setOnKeyListener(callback)
        onDispose {
            view.setOnKeyListener(null)
        }
    }

    // 显示可视化测速弹窗
    if (playerState.isSpeedTesting) {
        SpeedTestDialog(
            title = "智能测速选源中...",
            items = playerState.speedTestItems,
            progress = playerState.speedTestProgress,
            currentTesting = playerState.currentTesting,
            bestChannel = playerState.bestChannel,
            bestSpeed = playerState.bestSpeed,
            onCancel = {
                viewModel.cancelSpeedTest()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .pointerInput(Unit) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        totalDragY = 0f
                    },
                    onDragCancel = {
                        totalDragY = 0f
                    },
                    onVerticalDrag = { _, dragAmount ->
                        totalDragY += dragAmount
                        // 滑动超过100px才触发
                        if (abs(totalDragY) > 100) {
                            if (channels.isNotEmpty()) {
                                if (totalDragY < 0) {
                                    // 向上滑 = 下一个频道
                                    selectedChannelIndex = (selectedChannelIndex + 1) % channels.size
                                } else {
                                    // 向下滑 = 上一个频道
                                    selectedChannelIndex = (selectedChannelIndex - 1 + channels.size) % channels.size
                                }
                                viewModel.playChannel(channels[selectedChannelIndex])
                            }
                            totalDragY = 0f // 重置
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = {
                        // 双击打开频道列表
                        showChannelList = true
                    }
                )
            }
    ) {
        // 全屏播放器
        VideoPlayer(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // 控制按钮层
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 顶部信息
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(16.dp)
                ) {
                    Column {
                        playerState.currentChannel?.let { channel ->
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                                color = Color.White
                            )
                        }

                        if (playerState.isLoading) {
                            Text(
                                text = "正在加载...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 底部提示
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "上下滑动切换频道 | 双击频道列表",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // 频道列表覆盖层
        if (showChannelList) {
            PhoneChannelListOverlay(
                categories = categories,
                channels = channels,
                selectedCategory = selectedCategory,
                selectedChannelIndex = selectedChannelIndex,
                onCategoryChange = {
                    selectedCategory = it
                    selectedChannelIndex = 0
                },
                onChannelSelected = { index ->
                    selectedChannelIndex = index
                    viewModel.playChannel(channels[index])
                    showChannelList = false
                },
                onBack = { showChannelList = false }
            )
        }
    }
}

@Composable
fun PhoneChannelListOverlay(
    categories: List<ChannelCategory>,
    channels: List<Channel>,
    selectedCategory: ChannelCategory,
    selectedChannelIndex: Int,
    onCategoryChange: (ChannelCategory) -> Unit,
    onChannelSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择频道",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "音量键/上下滑切换",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // 分类标签 - 横向滚动
            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color.White,
                edgePadding = 8.dp
            ) {
                categories.forEach { category ->
                    Tab(
                        selected = category == selectedCategory,
                        onClick = { onCategoryChange(category) },
                        text = {
                            Text(
                                text = category.displayName,
                                color = if (category == selectedCategory) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                }
                            )
                        }
                    )
                }
            }

            // 频道列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(channels) { index, channel ->
                    PhoneChannelItem(
                        channel = channel,
                        isSelected = index == selectedChannelIndex,
                        onClick = { onChannelSelected(index) }
                    )
                }
            }
        }

        // 返回提示
        Box(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(20.dp)
                )
                .clickable { onBack() }
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "点击返回播放",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
        }
    }
}

@Composable
fun PhoneChannelItem(
    channel: Channel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${channel.sources.size}个源",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
    }
}
