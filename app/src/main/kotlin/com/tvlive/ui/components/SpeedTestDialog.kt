package com.tvlive.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class SpeedTestItem(
    val channelName: String,
    val sourceUrl: String,
    val quality: String,
    val speedMs: Long? = null,
    val status: TestStatus = TestStatus.PENDING,
    val message: String = ""
)

enum class TestStatus {
    PENDING, TESTING, SUCCESS, FAILED, SKIPPED
}

// 兼容旧代码
data class SpeedTestResult(
    val channelName: String,
    val sourceUrl: String,
    val quality: String,
    val speedMs: Long,
    val isSuccess: Boolean,
    val message: String = ""
)

@Composable
fun SpeedTestDialog(
    title: String,
    items: List<SpeedTestItem>,
    progress: Float,
    currentTesting: String?,
    bestChannel: String?,
    bestSpeed: Long?,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标题
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = Color.Cyan,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 当前状态
                if (currentTesting != null) {
                    Text(
                        text = "正在测试: $currentTesting",
                        color = Color.Yellow,
                        fontSize = 14.sp
                    )
                }

                // 最佳源
                if (bestChannel != null && bestSpeed != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A3A1A), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✓",
                            color = Color.Green,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "最佳: $bestChannel",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "${bestSpeed}ms",
                            color = Color.Green,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 进度条
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.Cyan,
                    trackColor = Color.DarkGray
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 测试结果网格 - 4x5布局
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0A0A0A), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(items) { index, item ->
                            SpeedTestItemCard(item = item)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 汇总信息
                val successCount = items.count { it.status == TestStatus.SUCCESS }
                val failedCount = items.count { it.status == TestStatus.FAILED }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "成功: $successCount  |  失败: $failedCount  |  共: ${items.size}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = "超时: 200ms",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 关闭按钮
                Button(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935)
                    )
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
fun SpeedTestItemCard(item: SpeedTestItem) {
    val borderColor = when (item.status) {
        TestStatus.PENDING -> Color.Gray.copy(alpha = 0.3f)
        TestStatus.TESTING -> Color.Yellow
        TestStatus.SUCCESS -> Color.Green
        TestStatus.FAILED -> Color.Red.copy(alpha = 0.5f)
        TestStatus.SKIPPED -> Color.Gray.copy(alpha = 0.3f)
    }

    val backgroundColor = when (item.status) {
        TestStatus.PENDING -> Color(0xFF1A1A1A)
        TestStatus.TESTING -> Color(0xFF2A2A1A)
        TestStatus.SUCCESS -> Color(0xFF1A2A1A)
        TestStatus.FAILED -> Color(0xFF2A1A1A)
        TestStatus.SKIPPED -> Color(0xFF151515)
    }

    // 闪烁动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1.3f)
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(
                width = 2.dp,
                color = if (item.status == TestStatus.TESTING) borderColor.copy(alpha = alpha) else borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 频道名称
            Text(
                text = item.channelName,
                color = when (item.status) {
                    TestStatus.SUCCESS -> Color.Green
                    TestStatus.FAILED -> Color.Red.copy(alpha = 0.6f)
                    TestStatus.TESTING -> Color.Yellow
                    else -> Color.White.copy(alpha = 0.6f)
                },
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 状态/速度
            when (item.status) {
                TestStatus.PENDING -> {
                    Text(
                        text = "等待",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
                TestStatus.TESTING -> {
                    // 旋转指示器
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.Yellow,
                        strokeWidth = 2.dp
                    )
                }
                TestStatus.SUCCESS -> {
                    Text(
                        text = "${item.speedMs}ms",
                        color = Color.Green,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                TestStatus.FAILED -> {
                    Text(
                        text = item.message.ifEmpty { "失败" },
                        color = Color.Red.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TestStatus.SKIPPED -> {
                    Text(
                        text = "跳过",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
