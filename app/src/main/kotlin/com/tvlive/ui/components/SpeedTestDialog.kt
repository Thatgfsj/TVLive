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

@Composable
fun SpeedTestDialog(
    items: List<SpeedTestItem>,
    progress: Float,
    currentTesting: String?,
    bestChannel: String?,
    bestSpeed: Long?,
    onSkip: () -> Unit
) {
    Dialog(
        onDismissRequest = { },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight()
                .background(Color(0xFF141414), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column {
                // 标题 + 进度
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "正在测速选源...",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = Color.Cyan,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 当前正在测试的源
                Text(
                    text = currentTesting?.let { "正在测试: $it" } ?: " ",
                    color = Color.Yellow,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.Cyan,
                    trackColor = Color.DarkGray
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 测试结果网格
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false
                ) {
                    itemsIndexed(items) { _, item ->
                        SpeedTestItemCard(item = item)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 汇总 + 跳过按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val successCount = items.count { it.status == TestStatus.SUCCESS }
                    val doneCount = items.count { it.status != TestStatus.PENDING }
                    Text(
                        text = "可用: $successCount  |  已测: $doneCount/${items.size}  |  超时: 3秒",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = onSkip,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE53935)
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text("跳过测速")
                    }
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
            .aspectRatio(1.4f)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.quality,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            when (item.status) {
                TestStatus.PENDING -> {
                    Text(
                        text = "等待",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                TestStatus.TESTING -> {
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
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TestStatus.SKIPPED -> {
                    Text(
                        text = "跳过",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
