package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ═══════════════════════════════════════════════════════════
// 配色
// ═══════════════════════════════════════════════════════════

private val VSBgDeep = Color(0xFF0D1520)
private val VSBgMid = Color(0xFF1A2332)
private val VSTeal = Color(0xFF0F7B6E)
private val VSLightTeal = Color(0xFF2EA699)
private val VSTextSecondary = Color(0xFF9A9AB0)
private val VSSurface = Color(0xFF1E2D3D)
private val VSAccent = Color(0xFF67B8F7)

private val VSGradientBg = Brush.verticalGradient(listOf(VSBgMid, VSBgDeep))
private val VSGradientCard = Brush.linearGradient(
    listOf(Color(0xFF0C5D52), Color(0xFF1B8A7A))
)
private val VSGradientBtn = Brush.horizontalGradient(listOf(VSTeal, VSLightTeal))

// ═══════════════════════════════════════════════════════════

@Composable
fun VideoSeparatorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var videoFiles by remember { mutableStateOf<List<VideoFileScanner.VideoFileInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("点击扫描设备视频") }
    var selectedFile by remember { mutableStateOf<VideoFileScanner.VideoFileInfo?>(null) }
    var separationTarget by remember { mutableStateOf<VideoFileScanner.VideoFileInfo?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "manual.mp4"
            val file = VideoFileScanner.VideoFileInfo(
                name = name, uri = uri, durationMs = 0L, sizeBytes = 0L, lastModified = 0L
            )
            selectedFile = file
            separationTarget = file
            statusMessage = "已选择: $name"
        }
    }

    // Android 11+ MANAGE_EXTERNAL_STORAGE 运行时授权
    var storagePermissionGranted by remember { mutableStateOf(false) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        storagePermissionGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
    }

    // ── 动画 ──
    val infiniteTransition = rememberInfiniteTransition(label = "vsAnim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )
    val scanInteraction = remember { MutableInteractionSource() }
    val scanPressed by scanInteraction.collectIsPressedAsState()
    val scanScale by animateFloatAsState(
        targetValue = if (scanPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f), label = "scanScale"
    )

    val manualInteraction = remember { MutableInteractionSource() }
    val manualPressed by manualInteraction.collectIsPressedAsState()
    val manualScale by animateFloatAsState(
        targetValue = if (manualPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f), label = "manualScale"
    )

    // 初次进入：先检查权限，再自动扫描
    LaunchedEffect(Unit) {
        // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 才能访问 Android/data、Android/obb 之外的 Android/ 子目录
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            statusMessage = "需要「所有文件访问」权限以扫描视频目录"
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
            storagePermissionLauncher.launch(intent)
            return@LaunchedEffect
        }
        if (!isScanning && videoFiles.isEmpty()) {
            isScanning = true
            statusMessage = "正在扫描视频文件..."
            try {
                val files = withContext(Dispatchers.IO) { VideoFileScanner.scan(context) }
                videoFiles = files
                statusMessage = if (files.isNotEmpty()) "找到 ${files.size} 个视频" else "未找到视频文件"
            } catch (e: Exception) { statusMessage = "扫描失败: ${e.message}" }
            finally { isScanning = false }
        }
    }

    // 权限授权后自动触发扫描
    LaunchedEffect(storagePermissionGranted) {
        if (storagePermissionGranted && !isScanning && videoFiles.isEmpty()) {
            isScanning = true
            statusMessage = "正在扫描视频文件..."
            try {
                val files = withContext(Dispatchers.IO) { VideoFileScanner.scan(context) }
                videoFiles = files
                statusMessage = if (files.isNotEmpty()) "找到 ${files.size} 个视频" else "未找到视频文件"
            } catch (e: Exception) { statusMessage = "扫描失败: ${e.message}" }
            finally { isScanning = false }
        }
    }

    // ── UI ──
    Box(modifier = Modifier.fillMaxSize().background(VSGradientBg)) {
        if (separationTarget != null) {
            VideoSeparationDetailScreen(
                file = separationTarget!!,
                onBack = { separationTarget = null; selectedFile = null },
                onDone = { updatedFiles ->
                    videoFiles = updatedFiles
                    separationTarget = null
                    selectedFile = null
                }
            )
        } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // ═══════════════════════════════════════════════════
            // 顶栏
            // ═══════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, top = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("← 返回", color = VSTextSecondary, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "音视频分离",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(color = VSLightTeal.copy(alpha = 0.3f), blurRadius = 12f)
                    )
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(16.dp))

            // ═══════════════════════════════════════════════════
            // 操作按钮行
            // ═══════════════════════════════════════════════════
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f).height(50.dp)
                        .scale(scanScale)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VSGradientBtn)
                        .clickable(interactionSource = scanInteraction, indication = null) {
                            if (!isScanning) {
                                isScanning = true; statusMessage = "正在扫描..."
                                scope.launch {
                                    try {
                                        val files = withContext(Dispatchers.IO) { VideoFileScanner.scan(context) }
                                        videoFiles = files
                                        statusMessage = if (files.isNotEmpty()) "找到 ${files.size} 个视频" else "未找到视频"
                                    } catch (e: Exception) { statusMessage = "扫描失败" }
                                    finally { isScanning = false }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning)
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    else
                        Text("扫描视频", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Box(
                    modifier = Modifier
                        .weight(1f).height(50.dp)
                        .scale(manualScale)
                        .clip(RoundedCornerShape(14.dp))
                        .background(VSSurface)
                        .clickable(interactionSource = manualInteraction, indication = null) {
                            filePickerLauncher.launch(arrayOf("video/*"))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("手动选择", color = VSTextSecondary, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(statusMessage, modifier = Modifier.padding(horizontal = 24.dp), color = VSTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))

            // ═══════════════════════════════════════════════════
            // 选中文件 → 跳转分离详情页
            // ═══════════════════════════════════════════════════
            LaunchedEffect(selectedFile) {
                selectedFile?.let { separationTarget = it }
            }

            // ═══════════════════════════════════════════════════
            // 视频文件列表
            // ═══════════════════════════════════════════════════
            if (videoFiles.isNotEmpty()) {
                Text(
                    "视频列表 (${videoFiles.size})",
                    modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 6.dp),
                    color = VSTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(videoFiles) { _, file ->
                        val isSelected = selectedFile?.uri == file.uri
                        VideoFileItem(file, isSelected, onFileChanged = {
                            scope.launch {
                                val files = withContext(Dispatchers.IO) { VideoFileScanner.scan(context) }
                                videoFiles = files
                                if (isSelected && files.none { it.uri == selectedFile?.uri })
                                    selectedFile = null
                                statusMessage = if (files.isNotEmpty()) "找到 ${files.size} 个视频" else "未找到视频文件"
                            }
                        }, onSelect = {
                            selectedFile = if (isSelected) null else file
                            separationTarget = if (isSelected) null else file
                            statusMessage = if (isSelected) "点击扫描设备视频" else "已选择: ${file.name}"
                        })
                    }
                }
            } else if (!isScanning) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83C\uDFAC", fontSize = 56.sp, modifier = Modifier.scale(pulseScale))
                        Spacer(Modifier.height(16.dp))
                        Text("没有找到视频文件", color = VSTextSecondary, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("点击「手动选择」从文件管理器挑选", color = VSTextSecondary.copy(alpha = 0.5f), fontSize = 13.sp)
                    }
                }
            }
        }
        } // end else
    }
}

// ═══════════════════════════════════════════════════════════
// 分离详情页 — 选中文件后独立页面
// ═══════════════════════════════════════════════════════════

@Composable
private fun VideoSeparationDetailScreen(
    file: VideoFileScanner.VideoFileInfo,
    onBack: () -> Unit,
    onDone: (updatedFiles: List<VideoFileScanner.VideoFileInfo>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var exportMode by remember { mutableStateOf("both") }
    var isSeparating by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<VideoSeparator.Result?>(null) }
    var showResultCard by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    // 动画
    val infiniteTransition = rememberInfiniteTransition(label = "vsSepDetail")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(VSGradientBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(start = 12.dp, top = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("← 返回", color = VSTextSecondary, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "分离设置",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(color = VSLightTeal.copy(alpha = 0.3f), blurRadius = 12f)
                    )
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(24.dp))

            // 状态信息
            if (statusMessage.isNotEmpty()) {
                Text(
                    statusMessage,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = VSTextSecondary, fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))
            }

            // 文件信息卡片
            Box(
                modifier = Modifier
                    .fillMaxWidth().padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(VSGradientCard)
                    .padding(20.dp)
            ) {
                Column {
                    // 头部标签行
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(8.dp).clip(CircleShape).background(VSLightTeal)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("已选择", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            VideoFileScanner.formatDuration(file.durationMs),
                            color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    // 文件名 + 图标
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) { Text("\uD83C\uDFAC", fontSize = 22.sp) }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                file.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            if (file.sizeBytes != 0L) {
                                Text(
                                    VideoFileScanner.formatSize(file.sizeBytes),
                                    color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 导出模式三选一
                    val exportOptions = listOf(
                        Triple("video_only", "仅视频", "🎥"),
                        Triple("audio_only", "仅音频", "🎵"),
                        Triple("both", "都保留", "🎬")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        exportOptions.forEach { (mode, text, emoji) ->
                            val selected = exportMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f).height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) VSLightTeal.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                    .clickable { exportMode = mode }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(emoji, fontSize = 14.sp)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text,
                                        color = if (selected) VSLightTeal else VSTextSecondary,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 分离按钮
                    val sepInteraction = remember { MutableInteractionSource() }
                    val sepPressed by sepInteraction.collectIsPressedAsState()
                    val sepScale by animateFloatAsState(
                        if (sepPressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(46.dp)
                            .scale(if (!isSeparating) sepScale * pulseScale else sepScale)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSeparating) Brush.horizontalGradient(listOf(VSLightTeal, VSTeal))
                                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.15f)))
                            )
                            .clickable(interactionSource = sepInteraction, indication = null) {
                                if (!isSeparating) {
                                    isSeparating = true; statusMessage = "正在分离音视频..."
                                    showResultCard = false
                                    scope.launch {
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                VideoSeparator.separate(context, file.uri, file.name, exportMode)
                                            }
                                            lastResult = result
                                            showResultCard = result.success
                                            statusMessage = if (result.success) "分离成功" else "分离失败"
                                        } catch (e: Exception) {
                                            lastResult = VideoSeparator.Result(false, e.message ?: "未知错误")
                                            showResultCard = false
                                            statusMessage = "分离异常"
                                        } finally {
                                            isSeparating = false
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSeparating) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(10.dp))
                                Text("分离中...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        } else {
                            Text("分离音视频", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 分离结果卡片
            AnimatedVisibility(
                visible = showResultCard && lastResult != null,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 },
                exit = fadeOut(tween(200))
            ) {
                lastResult?.let { result ->
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(32.dp).clip(CircleShape)
                                    .background(if (result.success) VSLightTeal.copy(alpha = 0.2f) else Color(0xFFFF6B6B).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (result.success) "✓" else "✗",
                                    color = if (result.success) VSLightTeal else Color(0xFFFF6B6B),
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (result.success) "分离完成" else "分离失败",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        result.outputs?.let { outputs ->
                            outputs.video?.let {
                                OutputFileCard("🎥", "视频", it, isVideo = true)
                            }
                            outputs.audio?.let {
                                OutputFileCard("🎵", "音频", it, isVideo = false)
                            }
                        } ?: run {
                            Text(result.message, color = VSTextSecondary, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // 分离完成后刷新文件列表
    LaunchedEffect(showResultCard) {
        if (showResultCard && lastResult?.success == true) {
            try {
                val files = withContext(Dispatchers.IO) { VideoFileScanner.scan(context) }
                onDone(files)
            } catch (_: Exception) {}
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 产物文件卡片
// ═══════════════════════════════════════════════════════════

@Composable
private fun OutputFileCard(emoji: String, label: String, file: File, isVideo: Boolean) {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "output")
    val glowAlpha by infiniteTransition.animateFloat(0.05f, 0.15f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "glow")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(VSSurface)
            .clickable {
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, if (isVideo) "video/*" else "audio/*")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: android.content.ActivityNotFoundException) {
                        android.widget.Toast.makeText(context, "未找到可打开该文件的播放器", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "打开失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VSLightTeal.copy(alpha = glowAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 标签 + 大小
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = VSLightTeal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatFileSize(file.length()),
                        color = VSTextSecondary, fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                // 文件名（突出显示）
                Text(
                    file.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                // 路径折叠
                Text(
                    file.parent ?: "",
                    color = VSTextSecondary.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))}MB"
    else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))}GB"
}

// ═══════════════════════════════════════════════════════════
// 视频文件列表项
// ═══════════════════════════════════════════════════════════

@Composable
private fun VideoFileItem(
    file: VideoFileScanner.VideoFileInfo,
    isSelected: Boolean,
    onFileChanged: () -> Unit,
    onSelect: () -> Unit
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val itemScale by animateFloatAsState(
        if (pressed) 0.97f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f)
    )

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 将 Uri 解析为 File 用于重命名/删除及播放
    val resolvedFile = remember(file.uri) {
        when {
            file.uri.scheme == "file" -> {
                try { File(file.uri.path!!) } catch (_: Exception) { null }
            }
            file.uri.scheme == "content" -> {
                // 从 MediaStore 反向查询真实文件路径
                try {
                    val proj = arrayOf(android.provider.MediaStore.Video.Media.DATA)
                    context.contentResolver.query(file.uri, proj, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val path = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA))
                            if (!path.isNullOrEmpty()) File(path) else null
                        } else null
                    }
                } catch (_: Exception) { null }
            }
            else -> null
        }
    }

    // 播放视频
    val playVideo: () -> Unit = {
        try {
            val uri: Uri = if (file.uri.scheme == "content") file.uri
            else if (resolvedFile != null) {
                androidx.core.content.FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", resolvedFile
                )
            } else file.uri
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: android.content.ActivityNotFoundException) {
                android.widget.Toast.makeText(context, "未找到可播放该视频的应用", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "播放失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(itemScale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) VSSurface.copy(alpha = 0.9f) else VSSurface.copy(alpha = 0.5f))
            .clickable(interactionSource = interaction, indication = null, onClick = playVideo)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(VSTeal.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) { Text("\uD83C\uDFAC", fontSize = 16.sp) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        if (file.durationMs != 0L) append(VideoFileScanner.formatDuration(file.durationMs))
                        if (file.sizeBytes != 0L) {
                            if (file.durationMs != 0L) append(" · ")
                            append(VideoFileScanner.formatSize(file.sizeBytes))
                        }
                    },
                    color = VSTextSecondary, fontSize = 12.sp
                )
            }

            // 操作按钮：重命名 → 删除 → 选择
            Box(
                modifier = Modifier
                    .size(32.dp).clip(CircleShape)
                    .clickable { showRenameDialog = true },
                contentAlignment = Alignment.Center
            ) { Text("✏️", fontSize = 16.sp) }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(32.dp).clip(CircleShape)
                    .clickable { showDeleteDialog = true },
                contentAlignment = Alignment.Center
            ) { Text("🗑️", fontSize = 16.sp) }
            Spacer(Modifier.width(4.dp))
            // 选择按钮（用于音视频分离）
            Box(
                modifier = Modifier
                    .size(28.dp).clip(CircleShape)
                    .background(if (isSelected) VSLightTeal else VSTextSecondary.copy(alpha = 0.25f))
                    .clickable { onSelect() },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) Text("✓", color = Color.White, fontSize = 14.sp)
                else Text("+", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.Light)
            }
        }
    }

    // ── 重命名对话框 ──
    if (showRenameDialog && resolvedFile != null) {
        val dot = file.name.lastIndexOf('.')
        val baseName = if (dot > 0) file.name.substring(0, dot) else file.name
        val ext = if (dot > 0) file.name.substring(dot) else ""
        var newName by remember { mutableStateOf(baseName) }

        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isEmpty()) {
                        android.widget.Toast.makeText(context, "文件名不能为空", android.widget.Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val target = File(resolvedFile.parent, trimmed + ext)
                    if (target.exists()) {
                        android.widget.Toast.makeText(context, "文件名已存在", android.widget.Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    if (resolvedFile.renameTo(target)) {
                        android.widget.Toast.makeText(context, "已重命名", android.widget.Toast.LENGTH_SHORT).show()
                        showRenameDialog = false
                        onFileChanged()
                    } else {
                        android.widget.Toast.makeText(context, "重命名失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("确定", color = VSLightTeal) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消", color = VSTextSecondary) }
            },
            title = { Text("重命名", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("新文件名（不含扩展名）") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = VSLightTeal,
                        unfocusedBorderColor = VSTextSecondary,
                        focusedLabelColor = VSLightTeal,
                        unfocusedLabelColor = VSTextSecondary,
                        cursorColor = VSLightTeal
                    )
                )
            },
            containerColor = VSSurface
        )
    }

    // ── 删除确认对话框 ──
    if (showDeleteDialog && resolvedFile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    if (resolvedFile.delete()) {
                        android.widget.Toast.makeText(context, "已删除", android.widget.Toast.LENGTH_SHORT).show()
                        showDeleteDialog = false
                        onFileChanged()
                    } else {
                        android.widget.Toast.makeText(context, "删除失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("删除", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消", color = VSTextSecondary) }
            },
            title = { Text("确认删除", color = Color.White) },
            text = { Text("确定要删除「${file.name}」吗？此操作不可撤销。", color = VSTextSecondary) },
            containerColor = VSSurface
        )
    }
}
