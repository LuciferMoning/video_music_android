package com.example.myapplication

import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ═══════════════════════════════════════════════════════════
// 配色
// ═══════════════════════════════════════════════════════════

private val VLBg = Color(0xFF1A2332)
private val VLCard = Color(0xFF1E2D3D)
private val VLBlue = Color(0xFF4A90D9)
private val VLAccent = Color(0xFF67B8F7)
private val VLTextPrimary = Color(0xFFEAEAEA)
private val VLTextSecondary = Color(0xFF9A9AB0)
private val VLDanger = Color(0xFFE85D5D)
private val VLSuccess = Color(0xFF4CAF50)
private val VLWarning = Color(0xFFF5A623)

private val VLGradientBlue = Brush.horizontalGradient(listOf(VLBlue, VLAccent))

// ═══════════════════════════════════════════════════════════
// 批量重命名相关数据类
// ═══════════════════════════════════════════════════════════

data class RenameItem(
    val file: File,
    val oldName: String,
    val newName: String
)

// ═══════════════════════════════════════════════════════════
// 公共前缀算法
// ═══════════════════════════════════════════════════════════

/**
 * 给定文件名列表，找到所有文件的公共最长前缀（频率投票算法）。
 * 对每个位置统计字符频率，最高频字符占比 >= 50% 则纳入前缀，否则停止。
 * 返回空字符串表示没有足够共识的公共前缀。
 */
private fun findCommonPrefix(names: List<String>): String {
    if (names.size < 2) return ""
    val n = names.size
    val minLen = names.minOf { it.length }
    val threshold = n * 0.5  // 50% 阈值

    var i = 0
    while (i < minLen) {
        // 统计该位置每个字符的出现次数
        val freq = mutableMapOf<Char, Int>()
        for (name in names) {
            val ch = name[i]
            freq[ch] = (freq[ch] ?: 0) + 1
        }
        // 找到最高频字符
        val (topChar, topCount) = freq.maxBy { it.value }
        if (topCount < threshold) break
        i++
    }
    if (i == 0) return ""

    val first = names.first()
    val dotIndex = first.lastIndexOf('.')
    return if (dotIndex > 0 && i >= dotIndex) {
        first.substring(0, dotIndex)
    } else {
        first.substring(0, i)
    }
}

/**
 * 在指定目录下递归查找文件名匹配的文件，返回 File 对象。
 */
private fun resolveFile(name: String): File? {
    val baseDirs = listOf(
        File(Environment.getExternalStorageDirectory(), "Android/music_myself/video"),
        File(Environment.getExternalStorageDirectory(), "Android/music_myself/视频")
    )
    for (base in baseDirs) {
        val found = findFileRecursive(base, name)
        if (found != null) return found
    }
    return null
}

private fun findFileRecursive(dir: File, targetName: String): File? {
    dir.listFiles()?.forEach { file ->
        if (file.isFile && file.name == targetName) return file
        if (file.isDirectory) {
            val found = findFileRecursive(file, targetName)
            if (found != null) return found
        }
    }
    return null
}

// ═══════════════════════════════════════════════════════════
// VideoListScreen
// ═══════════════════════════════════════════════════════════

@Composable
fun VideoListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var videoList by remember { mutableStateOf<List<VideoFileScanner.VideoFileInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var scanMessage by remember { mutableStateOf("正在扫描...") }

    // ── 批量重命名状态 ──
    var renamePreview by remember { mutableStateOf<List<RenameItem>>(emptyList()) }
    var isRenaming by remember { mutableStateOf(false) }
    var renameMessage by remember { mutableStateOf("") }

    // ── 自动扫描 ──
    LaunchedEffect(Unit) {
        val results = withContext(Dispatchers.IO) {
            VideoFileScanner.scan(context)
        }
        videoList = results
        isScanning = false
        scanMessage = if (results.isEmpty()) {
            "未找到视频文件，请确认 video/ 或 视频/ 目录下存在视频文件"
        } else {
            "共找到 ${results.size} 个视频文件"
        }
    }

    // 打开视频
    fun openVideo(info: VideoFileScanner.VideoFileInfo) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(info.uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            scanMessage = "无法打开视频: ${e.message}"
        }
    }

    // ── 批量重命名入口 ──
    fun prepareRename() {
        Toast.makeText(context, "prepareRename 被调用, videoList.size=${videoList.size}", Toast.LENGTH_SHORT).show()
        if (videoList.isEmpty()) return
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                val files = videoList.mapNotNull { info ->
                    resolveFile(info.name)?.let { f -> info to f }
                }
                if (files.isEmpty()) return@withContext emptyList<RenameItem>()

                val names = files.map { it.first.name }
                val prefix = findCommonPrefix(names)
                if (prefix.isEmpty()) return@withContext emptyList<RenameItem>()

                files.map { (info, file) ->
                    val newName = info.name.removePrefix(prefix)
                    val finalName = if (newName.isEmpty() || newName == "." + info.name.substringAfterLast('.')) {
                        info.name
                    } else if (newName.startsWith(".") && newName.lastIndexOf('.') == 0) {
                        info.name
                    } else {
                        newName.trimStart('.')
                    }
                    RenameItem(file = file, oldName = info.name, newName = finalName)
                }.filter { it.oldName != it.newName }
            }
            renamePreview = items
            renameMessage = if (items.isEmpty()) {
                "未检测到可去除的公共前缀"
            } else {
                "找到公共前缀，${items.size} 个文件可重命名"
            }
        }
    }

    // ── 执行重命名 ──
    fun executeRename() {
        isRenaming = true
        scope.launch {
            var success = 0
            var failed = 0
            withContext(Dispatchers.IO) {
                for (item in renamePreview) {
                    val newFile = File(item.file.parentFile, item.newName)
                    if (!newFile.exists() && item.file.renameTo(newFile)) {
                        success++
                    } else {
                        failed++
                    }
                }
            }
            renameMessage = "重命名完成：$success 成功, $failed 失败"
            renamePreview = emptyList()
            isRenaming = false
            val results = withContext(Dispatchers.IO) { VideoFileScanner.scan(context) }
            videoList = results
            scanMessage = "共找到 ${results.size} 个视频文件"
        }
    }

    // ── UI ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VLBg)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 顶部标题栏
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(VLGradientBlue)
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "\u2190  返回",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .clickable { onBack() }
                    )
                    Text(
                        "视频目录浏览",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 状态消息
            item {
                if (isScanning) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = VLAccent
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(scanMessage, color = VLTextSecondary, fontSize = 14.sp)
                    }
                } else if (scanMessage.isNotEmpty() && renamePreview.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        scanMessage,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = if (videoList.isEmpty()) VLWarning else VLTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            // ── 批量重命名预览 ──
            if (renamePreview.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2A38))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "批量重命名预览",
                                        color = VLAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "将去除文件名公共前缀，共 ${renamePreview.size} 个文件",
                                        color = VLTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            renamePreview.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        item.oldName,
                                        color = VLDanger,
                                        fontSize = 12.sp,
                                        textDecoration = TextDecoration.LineThrough,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "  \u2192  ",
                                        color = VLTextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        item.newName,
                                        color = VLSuccess,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            if (renameMessage.isNotEmpty()) {
                                Text(
                                    renameMessage,
                                    color = VLWarning,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(8.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        renamePreview = emptyList()
                                        renameMessage = ""
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isRenaming,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VLTextSecondary)
                                ) {
                                    Text("取消")
                                }
                                Button(
                                    onClick = { executeRename() },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isRenaming,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = VLSuccess,
                                        contentColor = Color.White
                                    )
                                ) {
                                    if (isRenaming) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text("确认重命名")
                                }
                            }
                        }
                    }
                }
            }

            // 视频列表标题
            if (videoList.isNotEmpty() && renamePreview.isEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "视频列表",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = VLTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // 视频卡片列表
            if (videoList.isNotEmpty() && renamePreview.isEmpty()) {
                itemsIndexed(videoList) { index, video ->
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = tween(300, delayMillis = index * 40)
                        ) + fadeIn(animationSpec = tween(300, delayMillis = index * 40))
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable { openVideo(video) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = VLCard)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(VLGradientBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "\u25B6",
                                        color = Color.White,
                                        fontSize = 18.sp
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        video.name,
                                        color = VLTextPrimary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text(
                                            VideoFileScanner.formatDuration(video.durationMs),
                                            color = VLAccent,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            VideoFileScanner.formatSize(video.sizeBytes),
                                            color = VLTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 批量重命名 —— 底部操作栏
            if (videoList.isNotEmpty() && renamePreview.isEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(VLBlue.copy(alpha = 0.12f))
                                .clickable { prepareRename() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "\u270E",
                                    fontSize = 16.sp,
                                    color = VLAccent
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "批量重命名",
                                    color = VLAccent,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // 空状态
            if (videoList.isEmpty() && !isScanning) {
                item {
                    Spacer(Modifier.height(80.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "\uD83C\uDFAC",
                                fontSize = 48.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "暂无视频文件",
                                color = VLTextSecondary,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "请将视频放入 Android/music_myself/video/ 目录",
                                color = VLTextSecondary.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
