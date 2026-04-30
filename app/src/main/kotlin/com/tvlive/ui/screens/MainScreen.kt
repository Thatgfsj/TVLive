package com.tvlive.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
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

@Composable
fun MainScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val view = LocalView.current

    var showChannelList by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(ChannelCategory.CCTV) }
    var selectedChannelIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchMode by remember { mutableStateOf(false) }

    val categories = remember { viewModel.getAllCategories() }

    // 根据分类或搜索过滤频道
    // 不再用playerState作为key，避免每次播放状态变化都重新计算频道列表
    val channels = remember(selectedCategory, searchQuery, isSearchMode) {
        if (isSearchMode && searchQuery.isNotBlank()) {
            viewModel.getChannels().filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        } else {
            viewModel.getChannelsByCategory(selectedCategory)
        }
    }

    // 初始化播放器 - 有缓存直接播，没缓存再测速
    LaunchedEffect(Unit) {
        viewModel.initializePlayer()
        kotlinx.coroutines.delay(300)
        viewModel.quickStartOrTest()
    }

    // 处理按键事件
    DisposableEffect(view) {
        val onKeyEvent = { event: KeyEvent ->
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    when (event.keyCode) {
                        // 上方向键 - 列表中向上选择
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (showChannelList && channels.isNotEmpty()) {
                                // 在列表中向上选择
                                selectedChannelIndex = (selectedChannelIndex - 1).coerceAtLeast(0)
                            } else {
                                // 全屏时上一频道
                                selectedChannelIndex = (selectedChannelIndex - 1 + channels.size) % channels.size
                                viewModel.playChannel(channels[selectedChannelIndex])
                            }
                            true
                        }
                        // 下方向键 - 列表中向下选择
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (showChannelList && channels.isNotEmpty()) {
                                // 在列表中向下选择
                                selectedChannelIndex = (selectedChannelIndex + 1).coerceAtMost(channels.size - 1)
                            } else {
                                // 全屏时下一频道
                                selectedChannelIndex = (selectedChannelIndex + 1) % channels.size
                                viewModel.playChannel(channels[selectedChannelIndex])
                            }
                            true
                        }
                        // 返回键 - 关闭列表或显示列表
                        KeyEvent.KEYCODE_BACK -> {
                            when {
                                isSearchMode -> {
                                    isSearchMode = false
                                    searchQuery = ""
                                }
                                showChannelList -> {
                                    showChannelList = false
                                }
                                else -> {
                                    showChannelList = true
                                }
                            }
                            true
                        }
                        // 设置键 - 显示/隐藏列表
                        KeyEvent.KEYCODE_SETTINGS -> {
                            showChannelList = !showChannelList
                            true
                        }
                        // 主页键 - 显示频道列表
                        KeyEvent.KEYCODE_HOME -> {
                            showChannelList = true
                            true
                        }
                        // 确认键 - 播放选中的频道
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (showChannelList && channels.isNotEmpty() && selectedChannelIndex < channels.size) {
                                viewModel.playChannel(channels[selectedChannelIndex])
                                showChannelList = false
                            } else if (!showChannelList) {
                                showChannelList = true
                            }
                            true
                        }
                        // Info键 - 切换解码
                        KeyEvent.KEYCODE_INFO -> {
                            viewModel.cycleDecoderType()
                            true
                        }
                        // 左方向键 - 频道列表里向左
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (showChannelList && channels.isNotEmpty()) {
                                selectedChannelIndex = (selectedChannelIndex - 1).coerceAtLeast(0)
                            }
                            true
                        }
                        // 右方向键 - 频道列表里向右
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (showChannelList && channels.isNotEmpty()) {
                                selectedChannelIndex = (selectedChannelIndex + 1).coerceAtMost(channels.size - 1)
                            }
                            true
                        }
                        // 数字键 0-9 - 快速切换
                        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2,
                        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5,
                        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8,
                        KeyEvent.KEYCODE_9 -> {
                            val num = event.keyCode - KeyEvent.KEYCODE_0
                            if (num < channels.size) {
                                selectedChannelIndex = num
                                viewModel.playChannel(channels[num])
                                showChannelList = false
                            }
                            true
                        }
                        // 搜索键
                        KeyEvent.KEYCODE_SEARCH -> {
                            isSearchMode = !isSearchMode
                            if (!isSearchMode) searchQuery = ""
                            showChannelList = isSearchMode
                            true
                        }
                        // 直播键 (长虹电视蓝色"直播"键) - 打开频道列表
                        KeyEvent.KEYCODE_TV -> {
                            showChannelList = true
                            true
                        }
                        // 静音键 - 切换静音
                        KeyEvent.KEYCODE_MUTE -> {
                            // 系统处理
                            false
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
    ) {
        // 全屏播放器
        FullScreenPlayer(
            viewModel = viewModel,
            showChannelList = showChannelList,
            onShowChannelList = { showChannelList = it }
        )

        // 频道列表覆盖层
        if (showChannelList) {
            ChannelListOverlay(
                categories = categories,
                channels = channels,
                selectedCategory = selectedCategory,
                selectedChannelIndex = selectedChannelIndex,
                searchQuery = searchQuery,
                isSearchMode = isSearchMode,
                onCategoryChange = {
                    selectedCategory = it
                    selectedChannelIndex = 0
                },
                onChannelSelected = { index ->
                    selectedChannelIndex = index
                    viewModel.playChannel(channels[index])
                    showChannelList = false
                },
                onSearchQueryChange = { searchQuery = it },
                onBack = { showChannelList = false }
            )
        }
    }
}

@Composable
fun FullScreenPlayer(
    viewModel: PlayerViewModel,
    showChannelList: Boolean,
    onShowChannelList: (Boolean) -> Unit
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        VideoPlayer(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // 顶部频道信息
        Column(
            modifier = Modifier
                .padding(24.dp)
                .align(Alignment.TopCenter)
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

            // 加载中
            if (playerState.isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
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

            // 自动切换提示
            if (playerState.isAutoSwitching && playerState.switchReason != null) {
                Spacer(modifier = Modifier.height(8.dp))
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
                        onClick = { onShowChannelList(true) }
                    ) {
                        Text("选择频道")
                    }
                }
            }
        }

        // 底部提示
        Row(
            modifier = Modifier
                .padding(24.dp)
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "上/下 换台",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "Menu/设置 频道列表",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "Info 解码:${playerState.decoderType.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun ChannelListOverlay(
    categories: List<ChannelCategory>,
    channels: List<Channel>,
    selectedCategory: ChannelCategory,
    selectedChannelIndex: Int,
    searchQuery: String,
    isSearchMode: Boolean,
    onCategoryChange: (ChannelCategory) -> Unit,
    onChannelSelected: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧分类
            if (!isSearchMode) {
                CategoryTabs(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategoryChange
                )
            }

            // 右侧频道列表
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSearchMode) "搜索频道" else "${selectedCategory.displayName}频道",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )

                    // 搜索框
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        modifier = Modifier.width(280.dp)
                    )
                }

                // 搜索结果数
                if (isSearchMode && searchQuery.isNotBlank()) {
                    Text(
                        text = "找到 ${channels.size} 个频道",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // 操作提示
                Row(
                    modifier = Modifier.padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "CH+/CH-换台",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "方向键选择",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "确认播放",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }

                // 频道网格
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(channels) { index, channel ->
                        ChannelCard(
                            channel = channel,
                            isSelected = index == selectedChannelIndex,
                            onClick = { onChannelSelected(index) }
                        )
                    }
                }
            }
        }

        // 返回提示
        Box(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomStart)
        ) {
            Text(
                text = "按返回键关闭列表",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🔍",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "输入频道名...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
    }
}
