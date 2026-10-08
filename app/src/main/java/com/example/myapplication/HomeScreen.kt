package com.example.myapplication

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ColorBgDeep = Color(0xFF0D1520)
private val ColorBgMid = Color(0xFF1A2332)
private val ColorBlue = Color(0xFF4A90D9)
private val ColorLightBlue = Color(0xFF67B8F7)
private val ColorPurple = Color(0xFF7B68EE)
private val ColorTextSecondary = Color(0xFF9A9AB0)

private val GradientBg = Brush.verticalGradient(listOf(ColorBgMid, ColorBgDeep))
private val GradientBlueCard = Brush.linearGradient(
    listOf(Color(0xFF2E5A88), Color(0xFF3A6FA0))
)
private val GradientPurpleCard = Brush.linearGradient(
    listOf(Color(0xFF5B4FCF), Color(0xFF8B5FBF))
)
private val GradientTealCard = Brush.linearGradient(
    listOf(Color(0xFF0C5D52), Color(0xFF1B8A7A))
)
private val GradientIndigoCard = Brush.linearGradient(
    listOf(Color(0xFF3D3278), Color(0xFF5B4FCF))
)

@Composable
fun HomeScreen(
    onNavigateToTrimmer: () -> Unit,
    onNavigateToVideoSeparator: () -> Unit,
    onNavigateToMusic: () -> Unit,
    onNavigateToBrowseSeparated: () -> Unit,
    onNavigateToBilibiliCache: () -> Unit,
    onNavigateToBilibiliFiles: () -> Unit,
    onNavigateToVideoMerge: () -> Unit,
    onNavigateToVideoList: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "notes")

    val note1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.12f, targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "n1"
    )
    val note2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.18f, targetValue = 0.38f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "n2"
    )
    val note3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.10f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(3500), RepeatMode.Reverse),
        label = "n3"
    )
    val note4Alpha by infiniteTransition.animateFloat(
        initialValue = 0.08f, targetValue = 0.22f,
        animationSpec = infiniteRepeatable(tween(2800), RepeatMode.Reverse),
        label = "n4"
    )

    var cardsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { cardsVisible = true }

    val trimInteraction = remember { MutableInteractionSource() }
    val trimPressed by trimInteraction.collectIsPressedAsState()
    val trimScale by animateFloatAsState(
        targetValue = if (trimPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "trimScale"
    )

    val musicInteraction = remember { MutableInteractionSource() }
    val musicPressed by musicInteraction.collectIsPressedAsState()
    val musicScale by animateFloatAsState(
        targetValue = if (musicPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "musicScale"
    )

    val separatorInteraction = remember { MutableInteractionSource() }
    val separatorPressed by separatorInteraction.collectIsPressedAsState()
    val separatorScale by animateFloatAsState(
        targetValue = if (separatorPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "separatorScale"
    )

    val browseInteraction = remember { MutableInteractionSource() }
    val browsePressed by browseInteraction.collectIsPressedAsState()
    val browseScale by animateFloatAsState(
        targetValue = if (browsePressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "browseScale"
    )

    val bilibiliInteraction = remember { MutableInteractionSource() }
    val bilibiliPressed by bilibiliInteraction.collectIsPressedAsState()
    val bilibiliScale by animateFloatAsState(
        targetValue = if (bilibiliPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "bilibiliScale"
    )

    val bilibiliFilesInteraction = remember { MutableInteractionSource() }
    val bilibiliFilesPressed by bilibiliFilesInteraction.collectIsPressedAsState()
    val bilibiliFilesScale by animateFloatAsState(
        targetValue = if (bilibiliFilesPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "bilibiliFilesScale"
    )

    val videoListInteraction = remember { MutableInteractionSource() }
    val videoListPressed by videoListInteraction.collectIsPressedAsState()
    val videoListScale by animateFloatAsState(
        targetValue = if (videoListPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "videoListScale"
    )

    val mergeInteraction = remember { MutableInteractionSource() }
    val mergePressed by mergeInteraction.collectIsPressedAsState()
    val mergeScale by animateFloatAsState(
        targetValue = if (mergePressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "mergeScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GradientBg)
    ) {
        // 装饰音符
        Text(
            "\u266A", modifier = Modifier.padding(start = 310.dp, top = 90.dp),
            fontSize = 72.sp, color = ColorBlue.copy(alpha = note1Alpha)
        )
        Text(
            "\u266B", modifier = Modifier.padding(start = 30.dp, top = 320.dp),
            fontSize = 52.sp, color = ColorPurple.copy(alpha = note2Alpha)
        )
        Text(
            "\u266C", modifier = Modifier.padding(start = 280.dp, top = 500.dp),
            fontSize = 60.sp, color = ColorLightBlue.copy(alpha = note3Alpha)
        )
        Text(
            "\u266A", modifier = Modifier.padding(start = 60.dp, top = 620.dp),
            fontSize = 48.sp, color = ColorBlue.copy(alpha = note4Alpha)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(72.dp))

            // 发光标题
            Text(
                "音乐工作室",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = Shadow(
                        color = ColorBlue.copy(alpha = 0.5f),
                        blurRadius = 24f,
                        offset = Offset(0f, 0f)
                    )
                )
            )

            Spacer(Modifier.height(10.dp))
            Text("管理你的音频文件", color = ColorTextSecondary, fontSize = 15.sp)
            Spacer(Modifier.height(52.dp))

            // 行一：裁剪录音 | 我的音乐
            AnimatedVisibility(
                visible = cardsVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(500, delayMillis = 100)
                ) + fadeIn(tween(500, delayMillis = 100))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .scale(trimScale)
                            .clip(RoundedCornerShape(20.dp))
                            .background(GradientBlueCard)
                            .clickable(
                                interactionSource = trimInteraction,
                                indication = null,
                                onClick = onNavigateToTrimmer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) { Text("\u2702", fontSize = 24.sp) }
                            Spacer(Modifier.height(10.dp))
                            Text("裁剪录音", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("精确裁剪、试听片段", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .scale(musicScale)
                            .clip(RoundedCornerShape(20.dp))
                            .background(GradientPurpleCard)
                            .clickable(
                                interactionSource = musicInteraction,
                                indication = null,
                                onClick = onNavigateToMusic
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) { Text("\u266B", fontSize = 24.sp) }
                            Spacer(Modifier.height(10.dp))
                            Text("我的音乐", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("浏览已裁剪的音乐", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 行二：音视频分离 | 我的视频与音频
            AnimatedVisibility(
                visible = cardsVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(500, delayMillis = 200)
                ) + fadeIn(tween(500, delayMillis = 200))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .scale(separatorScale)
                            .clip(RoundedCornerShape(20.dp))
                            .background(GradientTealCard)
                            .clickable(
                                interactionSource = separatorInteraction,
                                indication = null,
                                onClick = onNavigateToVideoSeparator
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) { Text("\uD83C\uDFAC", fontSize = 22.sp) }
                            Spacer(Modifier.height(10.dp))
                            Text("音视频分离", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("提取音频、分离视频轨道", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .scale(browseScale)
                            .clip(RoundedCornerShape(20.dp))
                            .background(GradientIndigoCard)
                            .clickable(
                                interactionSource = browseInteraction,
                                indication = null,
                                onClick = onNavigateToBrowseSeparated
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) { Text("\uD83D\uDCC1", fontSize = 22.sp) }
                            Spacer(Modifier.height(10.dp))
                            Text("我的视频与音频", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("浏览分离后的文件", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 行三：B站缓存提取 | 音视频合成 | B站导出文件（三个并排）
            AnimatedVisibility(
                visible = cardsVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(500, delayMillis = 300)
                ) + fadeIn(tween(500, delayMillis = 300))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .scale(bilibiliScale)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFFFB7299), Color(0xFFFC8CAC)))
                            )
                            .clickable(
                                interactionSource = bilibiliInteraction,
                                indication = null,
                                onClick = onNavigateToBilibiliCache
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) { Text("\uD83C\uDFAC", fontSize = 22.sp) }
                            Spacer(Modifier.height(10.dp))
                            Text("B站缓存提取", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("提取缓存的视频音频", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .scale(mergeScale)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFFE88D20), Color(0xFFF7B84A)))
                            )
                            .clickable(
                                interactionSource = mergeInteraction,
                                indication = null,
                                onClick = onNavigateToVideoMerge
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) { Text("\uD83C\uDFA5", fontSize = 22.sp) }
                            Spacer(Modifier.height(10.dp))
                            Text("音视频合成", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("合并视频与音频轨道", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .scale(bilibiliFilesScale)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF23B8E8), Color(0xFF4AD4F7)))
                            )
                            .clickable(
                                interactionSource = bilibiliFilesInteraction,
                                indication = null,
                                onClick = onNavigateToBilibiliFiles
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) { Text("\uD83D\uDCC2", fontSize = 22.sp) }
                            Spacer(Modifier.height(10.dp))
                            Text("B站导出文件", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("浏览已导出的文件", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 行四：视频目录浏览（全宽一行）
            AnimatedVisibility(
                visible = cardsVisible,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(500, delayMillis = 400)
                ) + fadeIn(tween(500, delayMillis = 400))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .scale(videoListScale)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2D5F8A), Color(0xFF4A90D9))
                            )
                        )
                        .clickable(
                            interactionSource = videoListInteraction,
                            indication = null,
                            onClick = onNavigateToVideoList
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) { Text("\uD83C\uDFAC", fontSize = 24.sp) }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "视频目录浏览",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "浏览 Android/music_myself/video/ 视频文件",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
