package com.example.myapplication

import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ═══════════════════════════════════════════════════════════
// 配色
// ═══════════════════════════════════════════════════════════
private val BCBgMid = Color(0xFF1A2332)
private val BCBgDeep = Color(0xFF0D1520)
private val BCTeal = Color(0xFF0F7B6E)
private val BCLightTeal = Color(0xFF2EA699)
private val BCTextSecondary = Color(0xFF9A9AB0)
private val BCSurface = Color(0xFF1E2D3D)
private val BCPink = Color(0xFFFB7299)
private val BCOrange = Color(0xFFFF8C42)

private val BCGradientBg = Brush.verticalGradient(listOf(BCBgMid, BCBgDeep))
private val BCGradientPink = Brush.linearGradient(listOf(BCPink, Color(0xFFFC8CAC)))
private val BCGradientOrange = Brush.linearGradient(listOf(BCOrange, Color(0xFFFFA940)))
private val BCGradientCard = Brush.linearGradient(listOf(Color(0xFF0C5D52), Color(0xFF1B8A7A)))

// ═══════════════════════════════════════════════════════════

@Composable
fun BilibiliCacheManagerScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var cacheList by remember { mutableStateOf<List<BilibiliCacheScanner.BilibiliCacheInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("点击扫描哔哩哔哩缓存") }
    var selectedItem by remember { mutableStateOf<BilibiliCacheScanner.BilibiliCacheInfo?>(null) }
    var renameTarget by remember { mutableStateOf<BilibiliCacheScanner.BilibiliCacheInfo?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteConfirmTarget by remember { mutableStateOf<BilibiliCacheScanner.BilibiliCacheInfo?>(null) }

    // 提取扫描逻辑，避免重复
    val doScan: suspend () -> Unit = {
        isScanning = true
        statusMessage = "正在扫描..."
        try {
            cacheList = withContext(Dispatchers.IO) { BilibiliCacheScanner.scan() }
            statusMessage = if (cacheList.isNotEmpty()) "找到 ${cacheList.size} 个缓存项"
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !BilibiliCacheScanner.isAccessible())
                "权限不足，请点击按钮授权「所有文件访问」"
            else "未找到缓存——请将缓存放入 music_myself/哔哩哔哩/源文件/"
        } catch (e: Exception) {
            statusMessage = "扫描失败: ${e.message}"
        } finally { isScanning = false }
    }

    // Android 11+ 所有文件访问权限启动器
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            scope.launch { doScan() }
        } else {
            statusMessage = "需要「所有文件访问」权限才能扫描缓存目录"
        }
    }

    // 检查并请求权限，返回 true 表示可以继续扫描
    fun ensureAllFilesPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            allFilesLauncher.launch(intent)
            return false
        }
        return true
    }

    val scanInteraction = remember { MutableInteractionSource() }
    val scanPressed by scanInteraction.collectIsPressedAsState()
    val scanScale by animateFloatAsState(
        if (scanPressed) 0.94f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "bcScan"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "bcAnim")
    val pulseScale by infiniteTransition.animateFloat(
        1f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(BCGradientBg)) {
        if (selectedItem != null) {
            BilibiliCacheDetailScreen(
                item = selectedItem!!,
                onBack = {
                    selectedItem = null
                    scope.launch { doScan() }
                }
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(start = 12.dp, top = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Text("← 返回", color = BCTextSecondary, fontSize = 15.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "哔哩哔哩缓存", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        style = Shadow(color = BCPink.copy(alpha = 0.3f), blurRadius = 12f).let {
                            androidx.compose.ui.text.TextStyle(shadow = it)
                        }
                    )
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(48.dp))
                }

                Spacer(Modifier.height(16.dp))

                // 扫描按钮
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(48.dp)
                        .scale(scanScale)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BCGradientPink)
                        .clickable(interactionSource = scanInteraction, indication = null) {
                            if (!isScanning && ensureAllFilesPermission()) {
                                scope.launch { doScan() }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("扫描中...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    } else {
                        Text("扫描缓存", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(statusMessage, modifier = Modifier.padding(horizontal = 24.dp), color = BCTextSecondary, fontSize = 13.sp)

                Spacer(Modifier.height(14.dp))

                // ── 浏览导出文件入口 ──
                BrowseExportSection()

                Spacer(Modifier.height(14.dp))

                // 缓存列表
                if (cacheList.isNotEmpty()) {
                    Text(
                        "缓存列表 (${cacheList.size})",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                        color = BCTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(cacheList) { _, item ->
                            CacheListItem(
                                item,
                                onClick = { selectedItem = item },
                                onRename = {
                                    renameTarget = item
                                    renameText = item.title
                                },
                                onDelete = { deleteConfirmTarget = item }
                            )
                        }
                    }
                } else if (!isScanning) {
                    Box(modifier = Modifier.weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📦", fontSize = 56.sp, modifier = Modifier.scale(pulseScale))
                            Spacer(Modifier.height(16.dp))
                            Text("没有找到哔哩哔哩缓存", color = BCTextSecondary, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("将缓存文件夹放入 music_myself/哔哩哔哩/源文件/", color = BCTextSecondary.copy(alpha = 0.5f), fontSize = 13.sp)
                        }
                    }
                }

                // ── 重命名弹窗 ──
                if (renameTarget != null) {
                    AlertDialog(
                        onDismissRequest = { renameTarget = null },
                        containerColor = BCBgMid,
                        title = { Text("重命名", color = Color.White, fontWeight = FontWeight.Bold) },
                        text = {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = BCPink,
                                    focusedBorderColor = BCPink,
                                    unfocusedBorderColor = BCTextSecondary.copy(alpha = 0.3f)
                                )
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val target = renameTarget ?: return@Button
                                    val newName = renameText.trim()
                                    if (newName.isNotEmpty() && newName != target.title) {
                                        val newFolder = File(target.folder.parentFile, newName)
                                        if (target.folder.renameTo(newFolder)) {
                                            cacheList = cacheList.map {
                                                if (it == target) it.copy(folder = newFolder, title = newName) else it
                                            }
                                        }
                                    }
                                    renameTarget = null
                                },
                                enabled = renameText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = BCPink)
                            ) {
                                Text("确定", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { renameTarget = null }) {
                                Text("取消", color = BCTextSecondary)
                            }
                        }
                    )
                }

                // ── 删除确认弹窗 ──
                if (deleteConfirmTarget != null) {
                    AlertDialog(
                        onDismissRequest = { deleteConfirmTarget = null },
                        containerColor = BCBgMid,
                        title = { Text("删除缓存", color = Color.White, fontWeight = FontWeight.Bold) },
                        text = {
                            Text(
                                "确定删除「${deleteConfirmTarget!!.title}」吗？\n此操作不可恢复。",
                                color = BCTextSecondary
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val target = deleteConfirmTarget ?: return@Button
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            target.folder.deleteRecursively()
                                            // 向上清理空父目录，直到扫描根目录
                                            val baseDir = BilibiliCacheScanner.scanBaseDir
                                            var parent = target.folder.parentFile
                                            while (parent != null && parent != baseDir && parent.listFiles()?.isEmpty() == true) {
                                                parent.delete()
                                                parent = parent.parentFile
                                            }
                                        }
                                        cacheList = cacheList.filter { it !== target }
                                        deleteConfirmTarget = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BCPink)
                            ) {
                                Text("删除", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { deleteConfirmTarget = null }) {
                                Text("取消", color = BCTextSecondary)
                            }
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 缓存列表项
// ═══════════════════════════════════════════════════════════

@Composable
private fun CacheListItem(
    item: BilibiliCacheScanner.BilibiliCacheInfo,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val itemScale by animateFloatAsState(
        if (pressed) 0.97f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "itemScale"
    )

    Box(
        modifier = Modifier.fillMaxWidth().scale(itemScale)
            .clip(RoundedCornerShape(14.dp))
            .background(BCSurface.copy(alpha = 0.7f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 图标 + 标题区域，点击进入详情
            Row(
                modifier = Modifier.weight(1f)
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(BCPink.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (item.coverFile != null) "🖼" else "🎬", fontSize = 22.sp)
                }
                Spacer(Modifier.width(14.dp))

                Column {
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.coverFile != null) TagChip("封面", true)
                        if (item.videoFile != null) TagChip("视频", true)
                        if (item.audioFile != null) TagChip("音频", true)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // 操作按钮
            IconButton(
                onClick = onRename,
                modifier = Modifier.size(36.dp)
            ) {
                Text("✏️", fontSize = 16.sp)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Text("🗑️", fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun TagChip(label: String, active: Boolean) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (active) BCPink.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = if (active) BCPink else BCTextSecondary, fontSize = 11.sp)
    }
}

// ═══════════════════════════════════════════════════════════
// 浏览导出文件
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseExportSection() {
    val context = LocalContext.current
    val videoDir = File(context.getExternalFilesDir(null)!!, "哔哩哔哩/视频")
    val audioDir = File(context.getExternalFilesDir(null)!!, "哔哩哔哩/音频")
    var browseTarget by remember { mutableStateOf<File?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BrowseDirButton(
            label = "导出视频",
            dir = videoDir,
            onClick = { browseTarget = videoDir },
            modifier = Modifier.weight(1f)
        )
        BrowseDirButton(
            label = "导出音频",
            dir = audioDir,
            onClick = { browseTarget = audioDir },
            modifier = Modifier.weight(1f)
        )
    }

    browseTarget?.let { dir ->
        BrowseDirDialog(dir = dir, onDismiss = { browseTarget = null })
    }
}

@Composable
private fun BrowseDirButton(
    label: String,
    dir: File,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val fileCount = if (dir.exists()) dir.listFiles()?.size ?: 0 else 0

    Box(
        modifier = modifier.height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BCSurface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("${fileCount}个 →", color = BCTextSecondary, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowseDirDialog(dir: File, sortKey: Int = 0, onDismiss: () -> Unit) {
    var currentDir by remember { mutableStateOf(dir) }
    val context = LocalContext.current
    val folders = remember(currentDir, sortKey) {
        currentDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.toIntOrNull() ?: Int.MAX_VALUE }
            ?: emptyList()
    }
    val files = remember(currentDir, sortKey) {
        currentDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name.toIntOrNull() ?: Int.MAX_VALUE }
            ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentDir != dir) {
                    TextButton(onClick = { currentDir = currentDir.parentFile ?: dir }) {
                        Text("← 上级", color = BCTeal, fontSize = 14.sp)
                    }
                }
                Text(
                    currentDir.name, color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
            }
        },
        text = {
            if (folders.isEmpty() && files.isEmpty()) {
                Text("此文件夹为空", color = BCTextSecondary, fontSize = 14.sp)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    if (folders.isNotEmpty()) {
                        item {
                            Text(
                                "${folders.size} 个子文件夹", color = BCTextSecondary,
                                fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(folders.size) { index ->
                            val folder = folders[index]
                            val subCount = folder.listFiles()?.size ?: 0
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .clickable { currentDir = folder }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row {
                                    Text("\uD83D\uDCC1 ", fontSize = 14.sp)
                                    Text(
                                        folder.name, color = Color.White,
                                        fontWeight = FontWeight.Medium, fontSize = 13.sp
                                    )
                                }
                                Text(
                                    "${subCount} 项",
                                    color = BCTextSecondary, fontSize = 12.sp
                                )
                            }
                        }
                    }
                    if (files.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "${files.size} 个文件", color = BCTextSecondary,
                                fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(files.size) { index ->
                            val file = files[index]
                            val sizeStr = when {
                                file.length() < 1024 -> "${file.length()} B"
                                file.length() < 1024 * 1024 -> "${file.length() / 1024} KB"
                                else -> "%.1f MB".format(file.length().toFloat() / (1024 * 1024))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .clickable {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val ext = file.extension.lowercase()
                                        val mime = when (ext) {
                                            "m4s" -> "video/mp4"
                                            else -> MimeTypeMap.getSingleton()
                                                .getMimeTypeFromExtension(ext) ?: "*/*"
                                        }
                                        val isVideo = mime.startsWith("video/")
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mime)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            if (isVideo) setPackage("com.miui.video")
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            if (isVideo) {
                                                intent.setPackage(null)
                                                try { context.startActivity(Intent.createChooser(intent, "选择播放器")) } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    file.name, color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    sizeStr,
                                    color = BCTextSecondary, fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = BCPink)
            }
        },
        containerColor = Color(0xFF1E2D3D)
    )
}

// ═══════════════════════════════════════════════════════════
// 缓存详情 / 导出页
// ═══════════════════════════════════════════════════════════

@Composable
private fun BilibiliCacheDetailScreen(
    item: BilibiliCacheScanner.BilibiliCacheInfo,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    var exportSuccess by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var exportFileName by remember { mutableStateOf(item.title) }

    // 使用 FileProvider 打开文件（系统记住默认应用）
    fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.extension.lowercase()
        val mime = when (ext) {
            "m4s" -> "video/mp4"   // B站视频分片，手动映射为非标准扩展名
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        }
        val isVideo = mime.startsWith("video/")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (isVideo) setPackage("com.miui.video")
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            if (isVideo) {
                intent.setPackage(null)
                try { context.startActivity(Intent.createChooser(intent, "选择播放器")) } catch (_: Exception) {}
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bcDetailAnim")
    val pulseScale by infiniteTransition.animateFloat(
        1f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse"
    )

    val safeTitle = exportFileName.replace(Regex("[/\\\\:*?\"<>|]"), "_")

    Box(modifier = Modifier.fillMaxSize().background(BCGradientBg)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding()
                    .padding(start = 12.dp, top = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("← 返回", color = BCTextSecondary, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "导出", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    style = Shadow(color = BCPink.copy(alpha = 0.3f), blurRadius = 12f).let {
                        androidx.compose.ui.text.TextStyle(shadow = it)
                    }
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(20.dp))

            // 文件信息卡片
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BCGradientCard)
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(BCPink))
                        Spacer(Modifier.width(8.dp))
                        Text("缓存文件", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(item.folder.name, color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    if (item.videoFile != null) Row(
                        modifier = Modifier.clickable { openFile(item.videoFile!!) }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("视频: video.m4s (${formatSize(item.videoFile!!.length())})  ▶ 预览", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    if (item.audioFile != null) Row(
                        modifier = Modifier.clickable { openFile(item.audioFile!!) }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("音频: audio.m4s (${formatSize(item.audioFile!!.length())})  ▶ 预览", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                    if (item.coverFile != null) Row(
                        modifier = Modifier.clickable { openFile(item.coverFile!!) }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("封面: cover.jpg (${formatSize(item.coverFile!!.length())})  ▶ 预览", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 导出成功 → 删除源文件按钮
            AnimatedVisibility(
                visible = exportSuccess && exportResult != null,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF6B6B).copy(alpha = 0.12f))
                        .clickable { if (!isDeleting) showDeleteConfirm = true }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isDeleting) "删除中..." else "🗑 删除源文件",
                        color = Color(0xFFFF6B6B),
                        fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("选择导出模式", modifier = Modifier.padding(horizontal = 24.dp), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))

            // 导出文件名输入
            OutlinedTextField(
                value = exportFileName,
                onValueChange = { exportFileName = it },
                singleLine = true,
                label = { Text("导出文件名", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = BCPink,
                    unfocusedBorderColor = BCPink.copy(alpha = 0.3f),
                    cursorColor = BCPink
                ),
                textStyle = TextStyle(fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )

            Spacer(Modifier.height(12.dp))

            // ── 导出选项 1：视频 + 音频 ──
            ExportButton(
                label = "导出视频 + 音频",
                subtitle = "保存 video.m4s 和 audio.m4s 到 哔哩哔哩/视频/{N}/",
                icon = "🎬",
                gradient = BCGradientPink,
                enabled = item.videoFile != null && item.audioFile != null && !isExporting,
                onExport = {
                    isExporting = true
                    exportResult = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            exportTwoFiles(
                                item, safeTitle,
                                File(context.getExternalFilesDir(null), "哔哩哔哩/视频"),
                                listOfNotNull(item.videoFile, item.audioFile)
                            )
                        }
                        exportResult = result.first
                        exportSuccess = result.second
                        isExporting = false
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            // ── 导出选项 2：封面 + 音频 ──
            ExportButton(
                label = "导出封面 + 音频",
                subtitle = "保存 cover.jpg 和 audio.m4s 到 哔哩哔哩/音频/{N}/",
                icon = "🖼",
                gradient = BCGradientOrange,
                enabled = item.coverFile != null && item.audioFile != null && !isExporting,
                onExport = {
                    isExporting = true
                    exportResult = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            exportTwoFiles(
                                item, safeTitle,
                                File(context.getExternalFilesDir(null), "哔哩哔哩/音频"),
                                listOfNotNull(item.coverFile, item.audioFile)
                            )
                        }
                        exportResult = result.first
                        exportSuccess = result.second
                        isExporting = false
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            // ── 导出选项 3：仅音频 ──
            ExportButton(
                label = "仅导出音频",
                subtitle = "保存 audio.m4s 到 哔哩哔哩/音频/{N}/",
                icon = "🎵",
                gradient = BCGradientOrange,
                enabled = item.audioFile != null && !isExporting,
                onExport = {
                    isExporting = true
                    exportResult = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            exportTwoFiles(
                                item, safeTitle,
                                File(context.getExternalFilesDir(null), "哔哩哔哩/音频"),
                                listOf(item.audioFile!!)
                            )
                        }
                        exportResult = result.first
                        exportSuccess = result.second
                        isExporting = false
                    }
                }
            )

            // 导出结果
            AnimatedVisibility(
                visible = exportResult != null,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }
            ) {
                exportResult?.let { msg ->
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (exportSuccess) BCLightTeal.copy(alpha = 0.15f)
                                else Color(0xFFFF6B6B).copy(alpha = 0.15f)
                            )
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                if (exportSuccess) "✓" else "✗",
                                color = if (exportSuccess) BCLightTeal else Color(0xFFFF6B6B),
                                fontSize = 16.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(msg, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // 删除确认弹窗
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除源文件", color = Color.White) },
                text = { Text("确定删除「${item.title}」的源文件吗？\n将删除整个缓存目录，不可恢复。", color = Color.White.copy(alpha = 0.7f)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        isDeleting = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                item.folder.deleteRecursively()
                                val baseDir = BilibiliCacheScanner.scanBaseDir
                                var parent = item.folder.parentFile
                                while (parent != null && parent != baseDir && parent.listFiles()?.isEmpty() == true) {
                                    parent.delete()
                                    parent = parent.parentFile
                                }
                            }
                            onBack()
                        }
                    }) { Text("删除", color = Color(0xFFFF6B6B)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("取消", color = BCTextSecondary)
                    }
                },
                containerColor = BCBgMid,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // 导出中遮罩
        if (isExporting) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BCPink, strokeWidth = 3.dp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 导出按钮组件
// ═══════════════════════════════════════════════════════════

@Composable
private fun ExportButton(
    label: String,
    subtitle: String,
    icon: String,
    gradient: Brush,
    enabled: Boolean,
    onExport: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val btnScale by animateFloatAsState(
        if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f)
    )

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            .scale(btnScale)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) gradient else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.06f))))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onExport)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 28.sp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = if (enabled) Color.White.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.25f),
                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 导出逻辑
// ═══════════════════════════════════════════════════════════

private fun exportTwoFiles(
    item: BilibiliCacheScanner.BilibiliCacheInfo,
    safeTitle: String,
    targetDir: File,
    files: List<File>
): Pair<String, Boolean> {
    if (!targetDir.exists()) targetDir.mkdirs()

    // 找到下一个可用编号
    val existingNumbers = targetDir.listFiles()
        ?.mapNotNull { it.name.toIntOrNull() }
        ?.sortedDescending()
        ?: emptyList()
    val nextNumber = if (existingNumbers.isEmpty()) 1 else existingNumbers.first() + 1

    val folder = File(targetDir, nextNumber.toString())
    if (!folder.exists()) folder.mkdirs()

    // 阻止系统媒体扫描，避免导出文件出现在相册
    File(folder, ".nomedia").createNewFile()
    File(targetDir, ".nomedia").createNewFile()  // 同时保护父目录

    try {
        files.forEach { src ->
            val prefix = if (files.size == 1) safeTitle else "${safeTitle}_${src.nameWithoutExtension}"
            val dest = File(folder, "$prefix.${src.extension}")
            src.copyTo(dest, overwrite = true)
            // 保留原始时间戳，避免文件出现在"最近访问"
            dest.setLastModified(src.lastModified())
        }
        return "${item.title} → ${targetDir.name}/${nextNumber}/ (${files.size} 个文件)" to true
    } catch (e: Exception) {
        return "导出失败: ${e.message}" to false
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))}MB"
}

// ═══════════════════════════════════════════════════════════
// B站导出文件浏览页
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BilibiliFilesScreen(onBack: () -> Unit, onNavigateToSynthesized: () -> Unit) {
    val context = LocalContext.current
    val videoDir = File(context.getExternalFilesDir(null)!!, "哔哩哔哩/视频")
    val audioDir = File(context.getExternalFilesDir(null)!!, "哔哩哔哩/音频")
    var browseTarget by remember { mutableStateOf<File?>(null) }
    var sortKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D1520))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.statusBarsPadding())
            Spacer(Modifier.height(12.dp))

            // 顶部栏
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("← 返回", color = BCTextSecondary, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "B站导出文件", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(color = Color(0xFF23B8E8).copy(alpha = 0.3f), blurRadius = 12f)
                    )
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(32.dp))

            // 说明文字
            Text(
                "浏览从哔哩哔哩缓存中已导出的文件",
                color = BCTextSecondary, fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // 视频导出目录
            BrowseDirCard(
                label = "导出视频",
                dir = videoDir,
                color1 = Color(0xFFFB7299),
                color2 = Color(0xFFFC8CAC),
                onClick = { browseTarget = videoDir }
            )

            Spacer(Modifier.height(14.dp))

            // 音频导出目录
            BrowseDirCard(
                label = "导出音频",
                dir = audioDir,
                color1 = Color(0xFF23B8E8),
                color2 = Color(0xFF4AD4F7),
                onClick = { browseTarget = audioDir }
            )

            Spacer(Modifier.height(14.dp))

            // 排序按钮
            OutlinedButton(
                onClick = { sortKey++ },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = BCTeal),
                border = BorderStroke(1.dp, BCTeal.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("按序号排序", color = BCTeal, fontSize = 14.sp)
            }

            Spacer(Modifier.height(14.dp))

            // 跳转到已合成音视频
            OutlinedButton(
                onClick = onNavigateToSynthesized,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4CAF50)),
                border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.3f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("已合成音视频", color = Color(0xFF4CAF50), fontSize = 14.sp)
            }

        }
    }

    browseTarget?.let { dir ->
        BrowseDirDialog(dir = dir, sortKey = sortKey, onDismiss = { browseTarget = null })
    }
}

@Composable
private fun BrowseDirCard(
    label: String,
    dir: File,
    color1: Color,
    color2: Color,
    onClick: () -> Unit
) {
    val fileCount = remember(dir) {
        dir.listFiles()?.filter { it.isDirectory }?.size ?: 0
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(color1, color2)))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (fileCount > 0) "${fileCount} 个子文件夹" else "暂无文件",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
                )
            }
            Text("→", color = Color.White, fontSize = 20.sp)
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 音视频合成页
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMergeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val unsynthesizedDir = File(context.getExternalFilesDir(null), "哔哩哔哩/视频/未合成音视频")
    val synthesizedDir = File(context.getExternalFilesDir(null), "哔哩哔哩/视频/已合成音视频")
    val scope = rememberCoroutineScope()

    var sourceFolders by remember { mutableStateOf<List<VideoMergeItem>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanMsg by remember { mutableStateOf("") }
    var deleteTargetIndex by remember { mutableStateOf<Int?>(null) }
    val pendingDeleteItem = deleteTargetIndex?.let { sourceFolders.getOrNull(it) }

    val scan = {
        isScanning = true
        scope.launch(Dispatchers.IO) {
            try {
                val items = mutableListOf<VideoMergeItem>()
                if (unsynthesizedDir.exists() && unsynthesizedDir.isDirectory) {
                    val subDirs = unsynthesizedDir.listFiles()
                        ?.filter { it.isDirectory }
                        ?.sortedBy { it.name.toIntOrNull() ?: Int.MAX_VALUE }
                        ?: emptyList()
                    for (dir in subDirs) {
                        val files = dir.listFiles() ?: continue
                        val videoFile = files.find { f ->
                            val name = f.nameWithoutExtension.lowercase()
                            name.endsWith("_video") || name == "video"
                        }
                        val audioFile = files.find { f ->
                            val name = f.nameWithoutExtension.lowercase()
                            name.endsWith("_audio") || name == "audio"
                        }
                        if (videoFile != null && audioFile != null) {
                            val title = extractCommonTitle(videoFile, audioFile, dir.name)
                            items.add(VideoMergeItem(
                                folderName = dir.name,
                                title = title,
                                videoFile = videoFile,
                                audioFile = audioFile
                            ))
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    sourceFolders = items
                    scanMsg = if (items.isEmpty()) "未找到可合成的视频/音频文件" else "共 ${items.size} 个待合成项"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { scanMsg = "扫描失败: ${e.message}" }
            } finally {
                withContext(Dispatchers.Main) { isScanning = false }
            }
        }
    }

    LaunchedEffect(Unit) { scan() }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0D1520))
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.statusBarsPadding())
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text("← 返回", color = BCTextSecondary, fontSize = 15.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("音视频合成", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "将「未合成音视频」中的视频与音频轨道合并为完整 MP4",
                color = BCTextSecondary, fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(scanMsg, color = BCTextSecondary.copy(alpha = 0.6f), fontSize = 12.sp)

            Spacer(Modifier.height(16.dp))

            if (isScanning) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BCTeal)
                }
            } else if (sourceFolders.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📂", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "暂无待合成项目",
                            color = BCTextSecondary, fontSize = 15.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "请先将导出视频移至「未合成音视频」文件夹",
                            color = BCTextSecondary.copy(alpha = 0.5f), fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sourceFolders.size) { index ->
                        val item = sourceFolders[index]
                        MergeItemCard(
                            item = item,
                            onClick = {
                                val fileToPlay = when (item.status) {
                                    MergeStatus.PENDING -> item.videoFile
                                    MergeStatus.DONE -> File(synthesizedDir, "${item.title}.mp4")
                                    else -> null
                                }
                                fileToPlay?.let { file ->
                                    if (file.exists()) {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                                            ?: "video/*"
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mimeType)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                }
                            },
                            onMerge = {
                                scope.launch(Dispatchers.IO) {
                                    withContext(Dispatchers.Main) {
                                        sourceFolders = sourceFolders.toMutableList().also {
                                            it[index] = it[index].copy(status = MergeStatus.MERGING)
                                        }
                                    }
                                    val result = mergeVideoAudio(
                                        videoFile = item.videoFile,
                                        audioFile = item.audioFile,
                                        outputDir = synthesizedDir,
                                        outputName = item.title
                                    )
                                    withContext(Dispatchers.Main) {
                                        sourceFolders = sourceFolders.toMutableList().also {
                                            it[index] = it[index].copy(
                                                status = if (result.success) MergeStatus.DONE else MergeStatus.FAILED,
                                                resultMsg = result.message
                                            )
                                        }
                                    }
                                    if (result.success) {
                                        item.videoFile.delete()
                                        item.audioFile.delete()
                                        item.videoFile.parentFile?.let { parent ->
                                            if (parent.listFiles()?.isEmpty() == true) parent.delete()
                                        }
                                    }
                                }
                            },
                            onDelete = {
                                deleteTargetIndex = index
                            }
                        )
                    }
                }
            }
        }

        // 删除确认弹窗
        if (pendingDeleteItem != null) {
            AlertDialog(
                onDismissRequest = { deleteTargetIndex = null },
                title = { Text("确认删除") },
                text = { Text("确定删除「${pendingDeleteItem!!.title}」的源文件吗？\n删除后无法恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        val idx = deleteTargetIndex ?: return@TextButton
                        val item = sourceFolders[idx]
                        scope.launch(Dispatchers.IO) {
                            item.videoFile.delete()
                            item.audioFile.delete()
                            item.videoFile.parentFile?.let { parent ->
                                if (parent.listFiles()?.isEmpty() == true) parent.delete()
                            }
                        }
                        sourceFolders = sourceFolders.toMutableList().also {
                            it.removeAt(idx)
                        }
                        deleteTargetIndex = null
                    }) {
                        Text("删除", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTargetIndex = null }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

private fun extractCommonTitle(videoFile: File, audioFile: File, fallback: String): String {
    val vName = videoFile.nameWithoutExtension
    val aName = audioFile.nameWithoutExtension

    val vCore = if (vName.endsWith("_video")) vName.removeSuffix("_video") else vName
    val aCore = if (aName.endsWith("_audio")) aName.removeSuffix("_audio") else aName

    return if (vCore == aCore && vCore.isNotEmpty()) vCore else fallback
}

private data class VideoMergeItem(
    val folderName: String,
    val title: String,
    val videoFile: File,
    val audioFile: File,
    val status: MergeStatus = MergeStatus.PENDING,
    val resultMsg: String? = null
)

private enum class MergeStatus { PENDING, MERGING, DONE, FAILED }

private data class MergeResult(val success: Boolean, val message: String)

private fun mergeVideoAudio(
    videoFile: File,
    audioFile: File,
    outputDir: File,
    outputName: String
): MergeResult {
    if (!outputDir.exists()) outputDir.mkdirs()
    val outputFile = File(outputDir, "$outputName.mp4")

    val videoExtractor = MediaExtractor()
    val audioExtractor = MediaExtractor()
    var muxer: MediaMuxer? = null

    return try {
        videoExtractor.setDataSource(videoFile.absolutePath)
        audioExtractor.setDataSource(audioFile.absolutePath)

        // 打印轨道格式信息，方便排查不兼容的编码格式
        android.util.Log.i("VideoMerge", "=== 视频文件: ${videoFile.name} (${videoFile.length()} bytes) ===")
        for (i in 0 until videoExtractor.trackCount) {
            val fmt = videoExtractor.getTrackFormat(i)
            android.util.Log.i("VideoMerge", "  视频轨道$i: ${fmt.getString(MediaFormat.KEY_MIME)}, ${fmt}")
        }
        android.util.Log.i("VideoMerge", "=== 音频文件: ${audioFile.name} (${audioFile.length()} bytes) ===")
        for (i in 0 until audioExtractor.trackCount) {
            val fmt = audioExtractor.getTrackFormat(i)
            android.util.Log.i("VideoMerge", "  音频轨道$i: ${fmt.getString(MediaFormat.KEY_MIME)}, ${fmt}")
        }

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val videoTrackIndex = selectTrack(videoExtractor, muxer, "video/")
        val audioTrackIndex = selectTrack(audioExtractor, muxer, "audio/")

        if (videoTrackIndex < 0 || audioTrackIndex < 0) {
            return MergeResult(false, "合成失败: 未找到视频或音频轨道")
        }

        muxer.start()

        writeTrack(videoExtractor, muxer, videoTrackIndex)
        writeTrack(audioExtractor, muxer, audioTrackIndex)

        muxer.stop()
        MergeResult(true, "合成成功: $outputName.mp4")
    } catch (e: Exception) {
        val msg = e.message ?: "${e.javaClass.simpleName}"
        android.util.Log.e("VideoMerge", "合成失败: $msg", e)
        MergeResult(false, "合成失败: $msg")
    } finally {
        try { muxer?.stop() } catch (_: Exception) {}
        muxer?.release()
        videoExtractor.release()
        audioExtractor.release()
    }
}

/** 选取指定 MIME 前缀的轨道并注册到 muxer，返回轨道索引，未找到返回 -1 */
private fun selectTrack(
    extractor: MediaExtractor,
    muxer: MediaMuxer,
    mimePrefix: String
): Int {
    for (i in 0 until extractor.trackCount) {
        val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith(mimePrefix)) {
            extractor.selectTrack(i)
            return muxer.addTrack(extractor.getTrackFormat(i))
        }
    }
    return -1
}

/** 将 extractor 的所有 sample 写入 muxer 的指定轨道 */
private fun writeTrack(
    extractor: MediaExtractor,
    muxer: MediaMuxer,
    trackIndex: Int
) {
    // 动态分配缓冲区：先尝试 2MB，若不够则按需增长
    var bufferSize = 2 * 1024 * 1024 // 2MB
    var buffer = java.nio.ByteBuffer.allocate(bufferSize)
    val sampleInfo = android.media.MediaCodec.BufferInfo()

    while (true) {
        sampleInfo.offset = 0
        sampleInfo.size = extractor.readSampleData(buffer, 0)
        if (sampleInfo.size < 0) break

        // 如果缓冲区不够，重新分配更大的缓冲区
        if (sampleInfo.size > bufferSize) {
            bufferSize = sampleInfo.size + 1024 // 多留 1KB 余量
            buffer = java.nio.ByteBuffer.allocate(bufferSize)
            android.util.Log.w("VideoMerge", "缓冲区扩容至 ${bufferSize} bytes")
        }

        sampleInfo.presentationTimeUs = extractor.sampleTime
        sampleInfo.flags = extractor.sampleFlags
        muxer.writeSampleData(trackIndex, buffer, sampleInfo)
        extractor.advance()
    }
}

@Composable
private fun MergeItemCard(item: VideoMergeItem, onClick: () -> Unit, onMerge: () -> Unit, onDelete: () -> Unit) {
    val icon = when (item.status) {
        MergeStatus.PENDING -> "⏳"
        MergeStatus.MERGING -> "🔄"
        MergeStatus.DONE -> "✅"
        MergeStatus.FAILED -> "❌"
    }
    val borderColor = when (item.status) {
        MergeStatus.PENDING -> Color(0xFFE88D20).copy(alpha = 0.4f)
        MergeStatus.MERGING -> BCTeal.copy(alpha = 0.4f)
        MergeStatus.DONE -> Color(0xFF4CAF50).copy(alpha = 0.4f)
        MergeStatus.FAILED -> Color(0xFFF44336).copy(alpha = 0.4f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "视频: ${item.videoFile.name}  |  音频: ${item.audioFile.name}",
                    color = BCTextSecondary,
                    fontSize = 11.sp
                )
                if (item.resultMsg != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.resultMsg,
                        color = if (item.status == MergeStatus.DONE) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontSize = 11.sp
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (item.status == MergeStatus.PENDING) {
                    Text("▶ 合成", color = Color(0xFFE88D20), fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { onMerge() })
                } else if (item.status == MergeStatus.MERGING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp, color = BCTeal
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "删除",
                    color = Color(0xFFF44336).copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onDelete() }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 已合成音视频页面
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynthesizedFilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val synthesizedDir = File(context.getExternalFilesDir(null), "哔哩哔哩/视频/已合成音视频")
    var files by remember { mutableStateOf(listOf<File>()) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }

    fun refreshFiles() {
        files = if (synthesizedDir.exists()) {
            synthesizedDir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".mp4", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } else emptyList()
    }

    LaunchedEffect(Unit) { refreshFiles() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已合成音视频", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无已合成音视频", color = Color.Gray, fontSize = 15.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(files, key = { it.absolutePath }) { file ->
                    SynthesizedFileCard(
                        file = file,
                        onRename = {
                            renameTarget = file
                            renameText = file.nameWithoutExtension
                        },
                        onDelete = { deleteTarget = file },
                        onPlay = {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }

    // 重命名对话框
    renameTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("新名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = renameText.trim()
                    if (trimmed.isNotEmpty() && trimmed != file.nameWithoutExtension) {
                        val newFile = File(file.parent, "$trimmed.mp4")
                        if (!newFile.exists()) {
                            file.renameTo(newFile)
                            refreshFiles()
                        }
                    }
                    renameTarget = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }

    // 删除确认对话框
    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${file.name}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    file.delete()
                    refreshFiles()
                    deleteTarget = null
                }) { Text("删除", color = Color(0xFFF44336)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
fun SynthesizedFileCard(
    file: File,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onPlay() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16213E)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatFileSize(file.length()),
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "重命名",
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "$bytes B"
    }
}
