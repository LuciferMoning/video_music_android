package com.example.myapplication

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ═══════════════════════════════════════════════════════════
// 配色 — 靛蓝 / 紫调
// ═══════════════════════════════════════════════════════════

private val BFBgDeep = Color(0xFF0D1520)
private val BFBgMid = Color(0xFF1A2332)
private val BFPrimary = Color(0xFF7B68EE)
private val BFLight = Color(0xFF9B8FFF)
private val BFTextSecondary = Color(0xFF9A9AB0)
private val BFSurface = Color(0xFF1E2D3D)

private val BFGradientBg = Brush.verticalGradient(listOf(BFBgMid, BFBgDeep))
private val BFGradientHeader = Brush.linearGradient(
    listOf(Color(0xFF3D3278), Color(0xFF5B4FCF))
)

// ═══════════════════════════════════════════════════════════
// 数据模型
// ═══════════════════════════════════════════════════════════

private data class SeparatedFileItem(
    val name: String,
    val file: File,
    val isVideo: Boolean,
    val sizeBytes: Long
)

// ═══════════════════════════════════════════════════════════

@Composable
fun BrowseSeparatedScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var files by remember { mutableStateOf<List<SeparatedFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var videoCount by remember { mutableIntStateOf(0) }
    var audioCount by remember { mutableIntStateOf(0) }

    // 加载文件
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val items = mutableListOf<SeparatedFileItem>()
            val root = File(context.getExternalFilesDir(null), "视频分离")

            val videoDir = File(root, "video")
            if (videoDir.exists() && videoDir.isDirectory) {
                videoDir.listFiles()?.forEach { f ->
                    if (f.isFile) items.add(SeparatedFileItem(f.name, f, isVideo = true, f.length()))
                }
                videoCount = items.count { it.isVideo }
            }

            val audioDir = File(root, "audio")
            if (audioDir.exists() && audioDir.isDirectory) {
                audioDir.listFiles()?.forEach { f ->
                    if (f.isFile) items.add(SeparatedFileItem(f.name, f, isVideo = false, f.length()))
                }
                audioCount = items.count { !it.isVideo }
            }

            files = items.sortedByDescending { it.file.lastModified() }
        }
        isLoading = false
    }

    // ── 动画 ──
    val infiniteTransition = rememberInfiniteTransition(label = "bfAnim")
    val pulseScale by infiniteTransition.animateFloat(
        1f, 1.04f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse"
    )
    var itemsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) { if (!isLoading) { itemsVisible = true } }

    // ═══════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════
    Box(modifier = Modifier.fillMaxSize().background(BFGradientBg)) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("← 返回", color = BFTextSecondary, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "我的视频与音频",
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(color = BFLight.copy(alpha = 0.3f), blurRadius = 12f)
                    )
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(16.dp))

            // 统计卡片
            AnimatedVisibility(visible = !isLoading && files.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("\uD83C\uDFAC", "视频", videoCount, BFPrimary)
                    StatCard("\uD83C\uDFB5", "音频", audioCount, BFLight)
                    StatCard("\uD83D\uDDC2", "总计", files.size, Color(0xFF67B8F7))
                }
            }
            Spacer(Modifier.height(16.dp))

            // 内容区
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = BFLight, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("加载中...", color = BFTextSecondary, fontSize = 14.sp)
                    }
                }
            } else if (files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83D\uDCC2", fontSize = 56.sp, modifier = Modifier.scale(pulseScale))
                        Spacer(Modifier.height(16.dp))
                        Text("暂无分离文件", color = BFTextSecondary, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("在「音视频分离」中提取文件后，产物会出现在这里",
                            color = BFTextSecondary.copy(alpha = 0.5f), fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(files, key = { _, item -> item.file.absolutePath }) { _, item ->
                        AnimatedVisibility(
                            visible = itemsVisible,
                            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }
                        ) {
                            FileItemRow(item) {
                                // 重命名/删除后重新加载列表
                                val items = mutableListOf<SeparatedFileItem>()
                                val root = File(context.getExternalFilesDir(null), "视频分离")
                                val videoDir = File(root, "video")
                                if (videoDir.exists() && videoDir.isDirectory) {
                                    videoDir.listFiles()?.forEach { f ->
                                        if (f.isFile) items.add(SeparatedFileItem(f.name, f, isVideo = true, f.length()))
                                    }
                                }
                                val audioDir = File(root, "audio")
                                if (audioDir.exists() && audioDir.isDirectory) {
                                    audioDir.listFiles()?.forEach { f ->
                                        if (f.isFile) items.add(SeparatedFileItem(f.name, f, isVideo = false, f.length()))
                                    }
                                }
                                files = items.sortedByDescending { it.file.lastModified() }
                                videoCount = items.count { it.isVideo }
                                audioCount = items.count { !it.isVideo }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 统计卡片
// ═══════════════════════════════════════════════════════════

@Composable
private fun RowScope.StatCard(emoji: String, label: String, count: Int, accent: Color) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(BFSurface)
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.height(4.dp))
            Text("$count", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(label, color = BFTextSecondary, fontSize = 11.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 文件列表项
// ═══════════════════════════════════════════════════════════

@Composable
private fun FileItemRow(item: SeparatedFileItem, onChanged: () -> Unit) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val itemScale by animateFloatAsState(
        if (pressed) 0.97f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f)
    )

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val accent = if (item.isVideo) Color(0xFF67B8F7) else BFLight
    val emoji = if (item.isVideo) "\uD83C\uDFAC" else "\uD83C\uDFB5"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(itemScale)
            .clip(RoundedCornerShape(12.dp))
            .background(BFSurface)
            .clickable(interactionSource = interaction, indication = null) {
                // 打开文件
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        item.file
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, if (item.isVideo) "video/*" else "audio/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
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
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row {
                    Text(
                        formatFileSize(item.sizeBytes),
                        color = BFTextSecondary, fontSize = 12.sp
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (item.isVideo) "视频轨道" else "音频轨道",
                        color = accent, fontWeight = FontWeight.Bold, fontSize = 10.sp
                    )
                }
            }
            // 操作按钮：重命名 → 删除 → 箭头
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { showRenameDialog = true },
                contentAlignment = Alignment.Center
            ) { Text("✏️", fontSize = 16.sp) }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { showDeleteDialog = true },
                contentAlignment = Alignment.Center
            ) { Text("🗑️", fontSize = 16.sp) }
            Spacer(Modifier.width(4.dp))
            // 箭头
            Text("›", color = BFTextSecondary.copy(alpha = 0.4f), fontSize = 24.sp)
        }
    }

    // 重命名对话框
    if (showRenameDialog) {
        val dot = item.name.lastIndexOf('.')
        val baseName = if (dot > 0) item.name.substring(0, dot) else item.name
        val ext = if (dot > 0) item.name.substring(dot) else ""
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
                    val target = File(item.file.parent, trimmed + ext)
                    if (target.exists()) {
                        android.widget.Toast.makeText(context, "文件名已存在", android.widget.Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    if (item.file.renameTo(target)) {
                        android.widget.Toast.makeText(context, "已重命名", android.widget.Toast.LENGTH_SHORT).show()
                        showRenameDialog = false
                        onChanged()
                    } else {
                        android.widget.Toast.makeText(context, "重命名失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("确定", color = BFLight) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消", color = BFTextSecondary) }
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
                        focusedBorderColor = BFLight,
                        unfocusedBorderColor = BFTextSecondary,
                        focusedLabelColor = BFLight,
                        unfocusedLabelColor = BFTextSecondary,
                        cursorColor = BFLight
                    )
                )
            },
            containerColor = BFSurface
        )
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    if (item.file.delete()) {
                        android.widget.Toast.makeText(context, "已删除", android.widget.Toast.LENGTH_SHORT).show()
                        showDeleteDialog = false
                        onChanged()
                    } else {
                        android.widget.Toast.makeText(context, "删除失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("删除", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消", color = BFTextSecondary) }
            },
            title = { Text("删除文件", color = Color.White) },
            text = { Text("确定要删除 ${item.name} 吗？", color = BFTextSecondary) },
            containerColor = BFSurface
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))}MB"
    else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))}GB"
}
