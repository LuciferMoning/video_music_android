package com.example.myapplication

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import android.provider.DocumentsContract
import android.os.StrictMode
import android.provider.MediaStore
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ═══════════════════════════════════════════════════════════
// 导航
// ═══════════════════════════════════════════════════════════

private enum class Screen { HOME, TRIMMER, MUSIC_PLAYER, PLAYER, VIDEO_SEPARATOR, BROWSE_SEPARATED, BILIBILI_CACHE, BILIBILI_FILES, VIDEO_MERGE, SYNTHESIZED_FILES, VIDEO_LIST }

/** deleteAudioFile 返回值：success=true=已删除；needsIntentSender!=null 需系统确认弹窗 */
data class DeleteResult(
    val success: Boolean,
    val message: String? = null,
    val needsIntentSender: IntentSender? = null
)

// ═══════════════════════════════════════════════════════════
// 配色常量 — 浅蓝色系
// ═══════════════════════════════════════════════════════════

private val ColorPrimary = Color(0xFF4A90D9)
private val ColorPrimaryDark = Color(0xFF3A7BC8)
private val ColorBg = Color(0xFF1A2332)
private val ColorCard = Color(0xFF1E2D3D)
private val ColorAccent = Color(0xFF67B8F7)
private val ColorTextPrimary = Color(0xFFEAEAEA)
private val ColorTextSecondary = Color(0xFF9A9AB0)
private val ColorSurface = Color(0xFF2A3A4A)

private val GradientBlue = Brush.horizontalGradient(listOf(ColorPrimary, ColorAccent))
private val GradientCard = Brush.linearGradient(
    listOf(ColorCard, Color(0xFF243344)),
    start = Offset(0f, 0f),
    end = Offset(1000f, 1000f)
)

// ═══════════════════════════════════════════════════════════
// 时间工具
// ═══════════════════════════════════════════════════════════

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = ms % 1000
    return if (millis > 0) "%d:%02d.%03d".format(minutes, seconds, millis)
           else "%d:%02d".format(minutes, seconds)
}

private fun parseTimeToMs(str: String): Long? {
    val trimmed = str.trim()
    val parts = trimmed.split(":")
    return when (parts.size) {
        2 -> {
            val min = parts[0].toLongOrNull() ?: return null
            val secPart = parts[1].split(".")
            val sec = secPart[0].toLongOrNull() ?: return null
            val ms = if (secPart.size > 1) {
                val msStr = secPart[1].padEnd(3, '0').take(3)
                msStr.toLongOrNull() ?: return null
            } else 0L
            if (sec >= 60 || ms >= 1000) return null
            (min * 60 + sec) * 1000 + ms
        }
        3 -> {
            val h = parts[0].toLongOrNull() ?: return null
            val m = parts[1].toLongOrNull() ?: return null
            val sPart = parts[2].split(".")
            val s = sPart[0].toLongOrNull() ?: return null
            val ms = if (sPart.size > 1) {
                val msStr = sPart[1].padEnd(3, '0').take(3)
                msStr.toLongOrNull() ?: return null
            } else 0L
            if (m >= 60 || s >= 60 || ms >= 1000) return null
            (h * 3600 + m * 60 + s) * 1000 + ms
        }
        else -> null
    }
}

// ═══════════════════════════════════════════════════════════
// MainActivity
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 允 file:// URI 供外部应用打开（避免每次重新选择打开方式）
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().penaltyLog().build())
        // 启动音乐播放后台服务
        val serviceIntent = Intent(this, MusicPlaybackService::class.java)
        startService(serviceIntent)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf(Screen.HOME) }

                // 系统返回键：非首页时回到对应上级页面
                BackHandler(enabled = currentScreen != Screen.HOME) {
                    when (currentScreen) {
                        Screen.PLAYER -> currentScreen = Screen.MUSIC_PLAYER
                        Screen.BROWSE_SEPARATED -> currentScreen = Screen.HOME
                        Screen.BILIBILI_CACHE -> currentScreen = Screen.HOME
                        Screen.BILIBILI_FILES -> currentScreen = Screen.HOME
                        Screen.VIDEO_MERGE -> currentScreen = Screen.HOME
                        Screen.VIDEO_LIST -> currentScreen = Screen.HOME
                        else -> currentScreen = Screen.HOME
                    }
                }

                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        onNavigateToTrimmer = { currentScreen = Screen.TRIMMER },
                        onNavigateToVideoSeparator = { currentScreen = Screen.VIDEO_SEPARATOR },
                        onNavigateToMusic = { currentScreen = Screen.MUSIC_PLAYER },
                        onNavigateToBrowseSeparated = { currentScreen = Screen.BROWSE_SEPARATED },
                        onNavigateToBilibiliCache = { currentScreen = Screen.BILIBILI_CACHE },
                        onNavigateToBilibiliFiles = { currentScreen = Screen.BILIBILI_FILES },
                        onNavigateToVideoMerge = { currentScreen = Screen.VIDEO_MERGE },
                        onNavigateToVideoList = { currentScreen = Screen.VIDEO_LIST }
                    )
                    Screen.TRIMMER -> Mp3CutterScreen(
                        onBack = { currentScreen = Screen.HOME }
                    )
                    Screen.MUSIC_PLAYER -> MusicPlayerScreen(
                        onBack = { currentScreen = Screen.HOME },
                        onNavigateToPlayer = { currentScreen = Screen.PLAYER }
                    )
                    Screen.PLAYER -> PlayerScreen(
                        onBack = { currentScreen = Screen.MUSIC_PLAYER }
                    )
                    Screen.VIDEO_SEPARATOR -> VideoSeparatorScreen(
                        onBack = { currentScreen = Screen.HOME }
                    )
                    Screen.BROWSE_SEPARATED -> BrowseSeparatedScreen(
                        onBack = { currentScreen = Screen.HOME }
                    )
                    Screen.BILIBILI_CACHE -> BilibiliCacheManagerScreen(
                        onBack = { currentScreen = Screen.HOME }
                    )
                    Screen.BILIBILI_FILES -> BilibiliFilesScreen(
                        onBack = { currentScreen = Screen.HOME },
                        onNavigateToSynthesized = { currentScreen = Screen.SYNTHESIZED_FILES }
                    )
                    Screen.VIDEO_MERGE -> VideoMergeScreen(
                        onBack = { currentScreen = Screen.HOME }
                    )
                    Screen.SYNTHESIZED_FILES -> SynthesizedFilesScreen(
                        onBack = { currentScreen = Screen.HOME }
                    )
                    Screen.VIDEO_LIST -> VideoListScreen(
                        onBack = { currentScreen = Screen.HOME }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 裁剪界面
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Mp3CutterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── 状态 ──
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var durationMs by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(0L) }
    var startText by remember { mutableStateOf("0:00") }
    var endText by remember { mutableStateOf("0:00") }
    var isPlaying by remember { mutableStateOf(false) }
    var isSegmentPlaying by remember { mutableStateOf(false) }
    var segmentPositionMs by remember { mutableLongStateOf(0L) }
    var segmentProgressText by remember { mutableStateOf("") }
    var isSegmentSeeking by remember { mutableStateOf(false) }
    var segmentSeekPositionMs by remember { mutableLongStateOf(0L) }
    var statusMessage by remember { mutableStateOf("点击扫描按钮开始") }
    var isTrimming by remember { mutableStateOf(false) }
    var lastOutputFile by remember { mutableStateOf<File?>(null) }
    var lastError by remember { mutableStateOf<String?>(null) }

    var recordingFiles by remember { mutableStateOf<List<AudioFileScanner.AudioFileInfo>>(emptyList()) }
    var safFiles by remember { mutableStateOf<List<AudioFileScanner.AudioFileInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var showRecordingList by remember { mutableStateOf(false) }
    var safAuthorized by remember { mutableStateOf(false) }
    var fullScanTriggered by remember { mutableStateOf(false) }

    // 重命名对话框
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    // 删除
    var deleteTarget by remember { mutableStateOf<AudioFileScanner.AudioFileInfo?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // 动画：删除对话框内按钮弹簧效果
    val deleteInteraction = remember { MutableInteractionSource() }
    val deletePressed by deleteInteraction.collectIsPressedAsState()
    val deleteScale by animateFloatAsState(
        targetValue = if (deletePressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "deleteScale"
    )

    // ── MediaPlayer ──
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var segmentMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val segmentHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            segmentMediaPlayer?.release()
            handler.removeCallbacksAndMessages(null)
            segmentHandler.removeCallbacksAndMessages(null)
        }
    }

    // 进入裁剪页面自动扫描录音文件
    LaunchedEffect(Unit) {
        if (!isScanning && recordingFiles.isEmpty() && safFiles.isEmpty()) {
            isScanning = true
            statusMessage = "正在扫描录音目录和已裁剪文件..."
            doScan(context, scope, recordingFiles, safFiles, false) { files, msg, triggered ->
                recordingFiles = files
                statusMessage = msg
                fullScanTriggered = triggered
                showRecordingList = true
                isScanning = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 动画状态
    // ═══════════════════════════════════════════════════════

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )
    val playRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing)),
        label = "rotate"
    )
    val segmentRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "segmentRotate"
    )

    val scanInteraction = remember { MutableInteractionSource() }
    val scanPressed by scanInteraction.collectIsPressedAsState()
    val scanScale by animateFloatAsState(
        targetValue = if (scanPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "scanScale"
    )

    val manualInteraction = remember { MutableInteractionSource() }
    val manualPressed by manualInteraction.collectIsPressedAsState()
    val manualScale by animateFloatAsState(
        targetValue = if (manualPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "manualScale"
    )

    // ═══════════════════════════════════════════════════════
    // Launcher
    // ═══════════════════════════════════════════════════════

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            doScan(context, scope, recordingFiles, safFiles, fullScanTriggered) { files, msg, triggered ->
                recordingFiles = files
                statusMessage = msg
                fullScanTriggered = triggered
                showRecordingList = true
                isScanning = false
            }
        } else {
            isScanning = false
            statusMessage = "需要存储权限才能扫描"
        }
    }

    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            safAuthorized = true
            isScanning = true
            statusMessage = "正在扫描授权目录..."
            scope.launch {
                val files = withContext(Dispatchers.IO) {
                    AudioFileScanner.scanSafDirectory(context, uri)
                }
                safFiles = files
                isScanning = false
                statusMessage = if (files.isEmpty()) "授权目录中未找到音频文件"
                else "授权目录中找到 ${files.size} 个音频文件"
                showRecordingList = true
            }
        }
    }

    // Android 11+ 系统删除确认对话框（RecoverableSecurityException）
    val deleteIntentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isScanning = true
            statusMessage = "已删除"
            doScan(context, scope, emptyList(), safFiles, fullScanTriggered) { files, msg, triggered ->
                recordingFiles = files
                statusMessage = msg
                fullScanTriggered = triggered
                showRecordingList = true
                isScanning = false
            }
        } else {
            statusMessage = "取消删除"
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            loadSelectedFile(context, it) { name, dur ->
                selectedFileUri = it; selectedFileName = name; durationMs = dur
                startMs = 0L; endMs = dur
                startText = formatTime(0); endText = formatTime(dur)
                statusMessage = "已选择: $name"; showRecordingList = false
            }
            mediaPlayer?.release(); mediaPlayer = null; isPlaying = false
            segmentMediaPlayer?.release(); segmentMediaPlayer = null; isSegmentPlaying = false
            segmentPositionMs = 0L; segmentProgressText = ""
            handler.removeCallbacksAndMessages(null)
            segmentHandler.removeCallbacksAndMessages(null)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 功能函数
    // ═══════════════════════════════════════════════════════

    fun onScan() {
        isScanning = true
        fullScanTriggered = false
        statusMessage = "正在扫描录音目录和已裁剪文件..."
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            doScan(context, scope, recordingFiles, safFiles, false) { files, msg, triggered ->
                recordingFiles = files; statusMessage = msg; fullScanTriggered = triggered
                showRecordingList = true; isScanning = false
            }
        } else permissionLauncher.launch(needed)
    }

    fun onPickFile(info: AudioFileScanner.AudioFileInfo) {
        val uri = if (info.absolutePath.startsWith("content://"))
            Uri.parse(info.absolutePath)
        else Uri.fromFile(File(info.absolutePath))
        selectedFileUri = uri; selectedFileName = info.name; durationMs = info.durationMs
        startMs = 0L; endMs = info.durationMs
        startText = formatTime(0); endText = formatTime(info.durationMs)
        statusMessage = "已选择: ${info.name}"; showRecordingList = false
        mediaPlayer?.release(); mediaPlayer = null; isPlaying = false
        segmentMediaPlayer?.release(); segmentMediaPlayer = null; isSegmentPlaying = false
        segmentPositionMs = 0L; segmentProgressText = ""
        handler.removeCallbacksAndMessages(null)
        segmentHandler.removeCallbacksAndMessages(null)
    }

    val stopPreview: () -> Unit = {
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.apply { if (isPlaying) { pause(); seekTo(startMs.toInt()) } }
        isPlaying = false
    }

    fun stopSegmentPreview() {
        segmentHandler.removeCallbacksAndMessages(null)
        segmentMediaPlayer?.apply {
            if (isPlaying || isSegmentPlaying) {
                pause()
                seekTo(startMs.toInt())
            }
        }
        isSegmentPlaying = false
        segmentPositionMs = 0L
        segmentProgressText = ""
    }

    fun startPreview() {
        val uri = selectedFileUri ?: return
        val mp = mediaPlayer ?: MediaPlayer().also { mediaPlayer = it }
        if (!isPlaying) {
            stopSegmentPreview()
            try {
                mp.reset(); mp.setDataSource(context, uri); mp.prepare()
                mp.seekTo(startMs.toInt()); mp.start(); isPlaying = true
                handler.postDelayed({
                    mp.pause(); mp.seekTo(startMs.toInt()); isPlaying = false
                }, endMs - startMs)
            } catch (e: Exception) { statusMessage = "预览失败: ${e.message}"; isPlaying = false }
        } else stopPreview()
    }

    fun startSegmentPreview() {
        val uri = selectedFileUri ?: return
        if (endMs <= startMs) { statusMessage = "结束时间必须大于开始时间"; return }
        val smp = segmentMediaPlayer ?: MediaPlayer().also { segmentMediaPlayer = it }

        if (!isSegmentPlaying) {
            stopPreview()
            try {
                smp.reset(); smp.setDataSource(context, uri); smp.prepare()
                smp.seekTo(startMs.toInt()); smp.start()
                isSegmentPlaying = true
                segmentPositionMs = startMs
                segmentProgressText = "${formatTime(startMs)} / ${formatTime(endMs)}"

                val updateInterval = 150L
                val endTime = endMs
                val progressRunnable = object : Runnable {
                    override fun run() {
                        if (!isSegmentPlaying) return
                        try {
                            val pos = smp.currentPosition.toLong()
                            segmentPositionMs = pos.coerceAtMost(endTime)
                            segmentProgressText = "${formatTime(segmentPositionMs)} / ${formatTime(endTime)}"
                        } catch (_: Exception) {}
                        if (segmentPositionMs < endTime) {
                            segmentHandler.postDelayed(this, updateInterval)
                        } else {
                            smp.pause()
                            smp.seekTo(startMs.toInt())
                            isSegmentPlaying = false
                            segmentPositionMs = endTime
                            segmentProgressText = "片段播放完毕"
                        }
                    }
                }

                segmentHandler.postDelayed({
                    try {
                        smp.pause()
                        smp.seekTo(startMs.toInt())
                    } catch (_: Exception) {}
                    isSegmentPlaying = false
                    segmentPositionMs = endMs
                    segmentProgressText = "片段播放完毕"
                }, endMs - startMs)

                segmentHandler.postDelayed(progressRunnable, updateInterval)
            } catch (e: Exception) {
                statusMessage = "片段预览失败: ${e.message}"
                isSegmentPlaying = false
            }
        } else {
            stopSegmentPreview()
        }
    }

    // 点击导出 → 弹出重命名对话框
    fun requestExport() {
        val uri = selectedFileUri
        if (uri == null) {
            statusMessage = "请先选择音频文件"
            return
        }
        if (endMs <= startMs) {
            statusMessage = "结束时间必须大于开始时间"
            return
        }
        // 默认文件名：原文件名_cut
        val dotIndex = selectedFileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) selectedFileName.substring(0, dotIndex)
            else selectedFileName
        renameText = "${baseName}_cut"
        showRenameDialog = true
    }

    fun doTrimWithName(fileName: String) {
        val uri = selectedFileUri ?: return
        isTrimming = true
        statusMessage = "正在裁剪..."
        stopPreview(); stopSegmentPreview()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val out = Mp3AudioTrimmer.createOutputFile(context, fileName)
                    Mp3AudioTrimmer.trim(context, uri, startMs, endMs, out)
                    out  // 成功，返回文件
                } catch (e: Exception) {
                    e.printStackTrace()
                    lastError = e.message ?: "未知错误"
                    null
                }
            }
            isTrimming = false
            lastOutputFile = result
            statusMessage = if (result != null) "裁剪完成! 已保存"
            else "裁剪失败: ${lastError ?: "请重试"}"
        }
    }

    // ═══════════════════════════════════════════════════════
    // 重命名对话框
    // ═══════════════════════════════════════════════════════

    if (showRenameDialog) {
        Dialog(
            onDismissRequest = { showRenameDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ColorCard.copy(alpha = 0.97f))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "命名导出文件",
                        color = ColorTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "将保存到应用私有音乐目录",
                        color = ColorTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ColorSurface.copy(alpha = 0.3f),
                            unfocusedContainerColor = ColorSurface.copy(alpha = 0.3f),
                            focusedTextColor = ColorTextPrimary,
                            unfocusedTextColor = ColorTextPrimary,
                            focusedBorderColor = ColorPrimary,
                            unfocusedBorderColor = ColorSurface
                        ),
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("文件名", color = ColorTextSecondary) }
                    )

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 取消
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ColorSurface)
                                .clickable { showRenameDialog = false }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("取消", color = ColorTextSecondary, fontWeight = FontWeight.Medium)
                        }

                        // 确认导出
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(GradientBlue)
                                .clickable {
                                    showRenameDialog = false
                                    val name = renameText.trim()
                                    doTrimWithName(if (name.isEmpty()) renameText else name)
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("确认导出", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // 删除确认对话框
    // ═══════════════════════════════════════════════════════

    if (showDeleteDialog && deleteTarget != null) {
        Dialog(
            onDismissRequest = { showDeleteDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(ColorCard.copy(alpha = 0.85f))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 图标
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4A4A5A).copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("\u2715", color = ColorPrimary, fontSize = 24.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "确认删除",
                        color = ColorTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        deleteTarget!!.name,
                        color = ColorPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "文件将被移至回收站，确认删除？",
                        color = ColorTextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 取消
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ColorSurface)
                                .clickable { showDeleteDialog = false }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("取消", color = ColorTextSecondary, fontWeight = FontWeight.Medium)
                        }

                        // 确认删除
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .scale(deleteScale)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ColorPrimary)
                                .clickable(interactionSource = deleteInteraction, indication = null) {
                                    val target = deleteTarget!!
                                    showDeleteDialog = false
                                    deleteTarget = null

                                    val result = deleteAudioFile(context, target.absolutePath)
                                    if (result.needsIntentSender != null) {
                                        // 需要系统确认弹窗
                                        deleteIntentLauncher.launch(
                                            IntentSenderRequest.Builder(result.needsIntentSender!!).build()
                                        )
                                    } else {
                                        isScanning = true
                                        statusMessage = if (result.success) "已删除: ${target.name}" else (result.message ?: "删除失败")
                                        doScan(context, scope, emptyList(), safFiles, fullScanTriggered) { files, msg, triggered ->
                                            recordingFiles = files
                                            statusMessage = msg
                                            fullScanTriggered = triggered
                                            showRecordingList = true
                                            isScanning = false
                                        }
                                    }
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("确认删除", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════

    val allFiles = remember(recordingFiles, safFiles) { recordingFiles + safFiles }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── 顶部标题栏 ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GradientBlue)
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 返回按钮左侧
                    Text(
                        "\u2190  返回",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                            .clickable { onBack() }
                    )
                    Text("录音裁剪", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── 功能按钮区 ──
            item {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 扫描按钮
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .scale(scanScale)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GradientBlue)
                            .clickable(
                                interactionSource = scanInteraction,
                                indication = null,
                                onClick = { if (!isScanning) onScan() }
                            )
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isScanning && !fullScanTriggered) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp, color = Color.White
                            )
                        } else {
                            Text("扫描录音文件", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    // 手动选择
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .scale(manualScale)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = manualInteraction,
                                indication = null,
                                onClick = { filePickerLauncher.launch(arrayOf("audio/mpeg", "audio/*")) }
                            )
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("手动选择文件", color = ColorTextSecondary)
                    }
                }

                // SAF 按钮
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ColorSurface.copy(alpha = 0.4f))
                            .clickable {
                                safLauncher.launch(AudioFileScanner.buildSafTreeUri())
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (safAuthorized) "已授权录音目录 (点击重新授权)" else "授权录音私有目录",
                            color = ColorTextSecondary, fontSize = 13.sp
                        )
                    }
                }

                if (fullScanTriggered) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "music_myself/录音 目录为空，已自动切换全量扫描",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = ColorTextSecondary, fontSize = 12.sp
                    )
                }
            }

            // ── 文件列表 ──
            if (showRecordingList && allFiles.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "共找到 ${allFiles.size} 个音频文件",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = ColorTextSecondary, fontSize = 13.sp
                    )
                }
                itemsIndexed(allFiles) { index, file ->
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
                                .combinedClickable(
                                    onClick = { onPickFile(file) },
                                    onLongClick = {
                                        deleteTarget = file
                                        showDeleteDialog = true
                                    }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = ColorCard)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(GradientBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("\u266A", color = Color.White, fontSize = 18.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, color = ColorTextPrimary,
                                        fontWeight = FontWeight.Medium, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis)
                                    Text(formatTime(file.durationMs), color = ColorTextSecondary, fontSize = 12.sp)
                                }
                                Text(
                                    "\u2715",
                                    color = ColorPrimary,
                                    fontSize = 18.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            deleteTarget = file
                                            showDeleteDialog = true
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── 裁剪区域 ──
            if (selectedFileUri != null) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorCard)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(selectedFileName, color = ColorPrimary,
                                fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("总时长: ${formatTime(durationMs)}", color = ColorTextSecondary, fontSize = 13.sp)
                        }
                    }
                }

                // 裁剪范围卡片
                item {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorCard)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("裁剪范围", color = ColorTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("开始", color = ColorTextSecondary, fontSize = 12.sp)
                                    OutlinedTextField(
                                        value = startText,
                                        onValueChange = { text ->
                                            startText = text
                                            parseTimeToMs(text)?.let { if (it in 0 until durationMs) startMs = it }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = ColorSurface.copy(alpha = 0.3f),
                                            unfocusedContainerColor = ColorSurface.copy(alpha = 0.3f),
                                            focusedTextColor = ColorTextPrimary,
                                            unfocusedTextColor = ColorTextPrimary,
                                            focusedBorderColor = ColorPrimary,
                                            unfocusedBorderColor = ColorSurface
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        listOf(-100L, -10L, 10L, 100L).forEach { delta ->
                                            val label = if (delta > 0) "+${delta}ms" else "${delta}ms"
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(ColorSurface.copy(alpha = 0.5f))
                                                    .clickable {
                                                        val newVal = (startMs + delta).coerceIn(0, durationMs)
                                                        startMs = newVal
                                                        startText = formatTime(newVal)
                                                    }
                                                    .padding(vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(label, color = ColorTextSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("结束", color = ColorTextSecondary, fontSize = 12.sp)
                                    OutlinedTextField(
                                        value = endText,
                                        onValueChange = { text ->
                                            endText = text
                                            parseTimeToMs(text)?.let { if (it in 1..durationMs) endMs = it }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = ColorSurface.copy(alpha = 0.3f),
                                            unfocusedContainerColor = ColorSurface.copy(alpha = 0.3f),
                                            focusedTextColor = ColorTextPrimary,
                                            unfocusedTextColor = ColorTextPrimary,
                                            focusedBorderColor = ColorPrimary,
                                            unfocusedBorderColor = ColorSurface
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        listOf(-100L, -10L, 10L, 100L).forEach { delta ->
                                            val label = if (delta > 0) "+${delta}ms" else "${delta}ms"
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(ColorSurface.copy(alpha = 0.5f))
                                                    .clickable {
                                                        val newVal = (endMs + delta).coerceIn(0, durationMs)
                                                        endMs = newVal
                                                        endText = formatTime(newVal)
                                                    }
                                                    .padding(vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(label, color = ColorTextSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            if (durationMs > 0) {
                                Spacer(Modifier.height(16.dp))
                                RangeSlider(
                                    value = (startMs.toFloat() / durationMs)..(endMs.toFloat() / durationMs),
                                    onValueChange = { range ->
                                        startMs = (range.start * durationMs).toLong()
                                        endMs = (range.endInclusive * durationMs).toLong()
                                        startText = formatTime(startMs); endText = formatTime(endMs)
                                    },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = ColorPrimary,
                                        activeTrackColor = ColorPrimary,
                                        inactiveTrackColor = ColorSurface
                                    )
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "片段时长: ${formatTime(endMs - startMs)}",
                                    color = ColorTextSecondary, fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // ── 预览 + 播放 + 导出 ──
                item {
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSegmentPlaying) GradientBlue
                                    else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                )
                                .then(
                                    if (!isSegmentPlaying)
                                        Modifier.background(Color.Transparent, RoundedCornerShape(14.dp))
                                    else Modifier
                                )
                                .clickable { startSegmentPreview() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .then(if (isSegmentPlaying) Modifier.rotate(segmentRotation) else Modifier),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (isSegmentPlaying) "\u23F8" else "\u25B6",
                                        color = if (isSegmentPlaying) Color.White else ColorPrimary,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isSegmentPlaying) "停止" else "试听片段",
                                    color = if (isSegmentPlaying) Color.White else ColorPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPlaying) GradientBlue
                                    else Brush.linearGradient(listOf(ColorSurface, ColorSurface))
                                )
                                .then(if (isPlaying) Modifier.rotate(playRotation) else Modifier)
                                .clickable { startPreview() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (isPlaying) "\u23F8" else "\u25B6", color = Color.White, fontSize = 20.sp)
                        }
                    }

                    // 试听进度条
                    if (isSegmentPlaying) {
                        Spacer(Modifier.height(10.dp))
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                segmentProgressText,
                                color = ColorAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(6.dp))
                            val segmentDuration = endMs - startMs
                            val displaySegmentPos = if (isSegmentSeeking) segmentSeekPositionMs else segmentPositionMs
                            val displayProgress = if (segmentDuration > 0)
                                ((displaySegmentPos - startMs).toFloat() / segmentDuration).coerceIn(0f, 1f)
                            else 0f
                            Slider(
                                value = displayProgress,
                                onValueChange = { fraction ->
                                    isSegmentSeeking = true
                                    segmentSeekPositionMs = (fraction * segmentDuration + startMs).toLong()
                                    segmentProgressText = "${formatTime(segmentSeekPositionMs)} / ${formatTime(endMs)}"
                                },
                                onValueChangeFinished = {
                                    segmentMediaPlayer?.seekTo(segmentSeekPositionMs.toInt())
                                    segmentPositionMs = segmentSeekPositionMs
                                    isSegmentSeeking = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = ColorAccent,
                                    activeTrackColor = ColorAccent,
                                    inactiveTrackColor = ColorSurface
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 导出按钮（脉冲 — 现在触发重命名对话框）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .scale(if (!isTrimming) pulseScale else 1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GradientBlue)
                            .clickable { requestExport() }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isTrimming) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp, color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("裁剪中...", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("裁剪并导出", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── 状态消息 ──
            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ColorCard)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(statusMessage, color = ColorTextSecondary, fontSize = 13.sp)
                        if (lastOutputFile != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(lastOutputFile!!.absolutePath, color = ColorPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 辅助函数
// ═══════════════════════════════════════════════════════════

/**
 * 删除音频文件。返回 DeleteResult：success=true 已删除；needsIntentSender 非空需系统对话框。
 * 支持三种路径：普通文件路径、MediaStore content URI、SAF document URI。
 *
 * 删除降级链：
 *   1. contentResolver.delete(uri, null, null) — 仅对本应用创建的文件有效
 *   2. MediaStore.createDeleteRequest() — Android 11+ 系统确认弹窗
 *   3. _data → 构造 SAF document URI → DocumentsContract.deleteDocument()
 *   4. _data → File.delete() 兜底
 */
fun deleteAudioFile(context: android.content.Context, path: String): DeleteResult {
    val TAG = "DELETE"
    return try {
        Log.e(TAG, "========== 开始删除: $path ==========")

        if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            Log.e(TAG, "URI authority=${uri.authority}")

            // ── 1. SAF document URI → DocumentsContract.deleteDocument ──
            if (uri.authority?.contains("com.android.externalstorage") == true) {
                Log.e(TAG, "→ SAF document URI 分支")
                try {
                    if (DocumentsContract.deleteDocument(context.contentResolver, uri)) {
                        Log.e(TAG, "SAF deleteDocument 成功")
                        return DeleteResult(true, "已删除")
                    }
                    Log.e(TAG, "SAF deleteDocument 返回 false")
                } catch (e: SecurityException) {
                    Log.e(TAG, "SAF SecurityException: ${e.message}")
                    return DeleteResult(false, "权限不足: ${e.message}")
                }
                // 提取真实路径后 File.delete 兜底
                val realPath = extractPathFromSafUri(uri)
                Log.e(TAG, "SAF 提取路径: $realPath")
                if (realPath != null && File(realPath).delete()) {
                    Log.e(TAG, "SAF→File.delete 成功")
                    return DeleteResult(true, "已删除 (SAF→File)")
                }
                return DeleteResult(false, "SAF 删除失败: 权限不足或文件不存在")
            }

            // ── 2. MediaStore URI → 四级降级删除 ──
            Log.e(TAG, "→ MediaStore URI 分支")

            // 2a. 直接 contentResolver.delete（本应用创建的文件）
            try {
                val rows = context.contentResolver.delete(uri, null, null)
                Log.e(TAG, "contentResolver.delete → $rows")
                if (rows > 0) {
                    context.applicationContext.contentResolver.notifyChange(uri, null)
                    Log.e(TAG, "直接删除成功")
                    return DeleteResult(true, "已删除")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "contentResolver.delete SecurityException: ${e.message}")
                // Android 10 (Q): RecoverableSecurityException 内含 IntentSender
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
                    e is RecoverableSecurityException
                ) {
                    Log.e(TAG, "Android 10 → 返回 IntentSender")
                    return DeleteResult(
                        false, "需用户确认",
                        needsIntentSender = e.userAction.actionIntent.intentSender
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Log.e(TAG, "Android 11+ SecurityException → 继续降级")
                } else {
                    return DeleteResult(false, "权限不足: ${e.message}")
                }
            }

            // 2b. Android 11+：MediaStore.createDeleteRequest → 系统对话框
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    Log.e(TAG, "尝试 MediaStore.createDeleteRequest")
                    val pendingIntent = MediaStore.createDeleteRequest(
                        context.contentResolver, listOf(uri)
                    )
                    Log.e(TAG, "createDeleteRequest 成功 → IntentSender")
                    return DeleteResult(
                        false, "需用户确认",
                        needsIntentSender = pendingIntent.intentSender
                    )
                } catch (e: SecurityException) {
                    Log.e(TAG, "createDeleteRequest SecurityException: ${e.message}")
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "createDeleteRequest IllegalArgumentException: ${e.message}")
                }
                Log.e(TAG, "createDeleteRequest 失败 → 降级到 _data 路径")
            }

            // 2c. 查询 _data 列获取真实文件路径
            var filePath: String? = null
            try {
                context.contentResolver.query(
                    uri, arrayOf("_data"), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex("_data")
                        if (idx >= 0) filePath = cursor.getString(idx)
                    }
                }
                Log.e(TAG, "_data 列: $filePath")
            } catch (ex: Exception) {
                Log.e(TAG, "_data 查询异常: ${ex.message}")
            }

            if (filePath != null) {
                val resolvedPath = filePath!!
                // 2c-i. 构造 SAF document URI → DocumentsContract.deleteDocument
                val safUri = convertFilePathToSafDocumentUri(resolvedPath)
                if (safUri != null) {
                    Log.e(TAG, "构造 SAF URI: $safUri")
                    try {
                        if (DocumentsContract.deleteDocument(context.contentResolver, safUri)) {
                            Log.e(TAG, "SAF deleteDocument(_data) 成功")
                            return DeleteResult(true, "已删除")
                        }
                        Log.e(TAG, "SAF deleteDocument(_data) 返回 false")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SAF(_data) SecurityException: ${e.message}")
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "SAF(_data) IllegalArgumentException: ${e.message}")
                    }
                }
                // 2c-ii. File.delete 最终兜底
                val deleted = File(resolvedPath).delete()
                Log.e(TAG, "File.delete(_data) → $deleted")
                if (deleted) return DeleteResult(true, "已删除 (File)")
            }

            return DeleteResult(
                false,
                "删除失败: 系统拒绝删除此文件\n(Android 11+ Scoped Storage 限制)"
            )

        } else {
            // ── 3. 普通文件路径 ──
            Log.e(TAG, "→ 普通文件路径: $path")
            val f = File(path)
            if (!f.exists()) {
                Log.e(TAG, "文件不存在")
                return DeleteResult(false, "文件不存在")
            }
            if (f.delete()) {
                Log.e(TAG, "File.delete 成功")
                return DeleteResult(true, "已删除")
            }
            Log.e(TAG, "File.delete 失败 → 尝试 SAF")
            // 构造 SAF document URI 尝试删除
            val safUri = convertFilePathToSafDocumentUri(path)
            if (safUri != null) {
                Log.e(TAG, "构造 SAF URI: $safUri")
                try {
                    if (DocumentsContract.deleteDocument(context.contentResolver, safUri)) {
                        Log.e(TAG, "SAF deleteDocument(path) 成功")
                        return DeleteResult(true, "已删除")
                    }
                    Log.e(TAG, "SAF deleteDocument(path) 返回 false")
                } catch (e: SecurityException) {
                    Log.e(TAG, "SAF(path) SecurityException: ${e.message}")
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "SAF(path) IllegalArgumentException: ${e.message}")
                }
            }
            return DeleteResult(
                false,
                "删除失败: 文件被系统保护或占用\n(Android 11+ Scoped Storage)"
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "未预期异常: ${e.javaClass.simpleName}: ${e.message}", e)
        DeleteResult(false, "删除异常: ${e.message}")
    }
}

/** 从 SAF externalstorage document URI 中提取真实文件路径 */
private fun extractPathFromSafUri(uri: Uri): String? {
    return try {
        val docId = DocumentsContract.getDocumentId(uri)
        if (docId.startsWith("primary:")) {
            "/storage/emulated/0/" + java.net.URLDecoder.decode(
                docId.removePrefix("primary:"), "UTF-8"
            )
        } else null
    } catch (_: Exception) { null }
}

/**
 * 将真实文件路径转换为 SAF document URI，用于 DocumentsContract.deleteDocument。
 * /storage/emulated/0/Android/music_myself/录音/xxx.amr
 * → content://com.android.externalstorage.documents/document/primary:Android%2Fmusic_myself%2F...
 */
private fun convertFilePathToSafDocumentUri(filePath: String): Uri? {
    return try {
        val normalized = filePath.replace('\\', '/')
        val prefix = "/storage/emulated/0/"
        if (!normalized.startsWith(prefix, ignoreCase = true)) return null
        val relativePath = normalized.substring(prefix.length)
        val encoded = "primary:" + java.net.URLEncoder.encode(relativePath, "UTF-8")
            .replace("+", "%20")
        Uri.parse("content://com.android.externalstorage.documents/document/$encoded")
    } catch (_: Exception) { null }
}

private fun doScan(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    currentFiles: List<AudioFileScanner.AudioFileInfo>,
    currentSafFiles: List<AudioFileScanner.AudioFileInfo>,
    forceFull: Boolean,
    onResult: (List<AudioFileScanner.AudioFileInfo>, String, Boolean) -> Unit
) {
    scope.launch {
        val files = withContext(Dispatchers.IO) {
            if (forceFull) AudioFileScanner.scanAll(context)
            else AudioFileScanner.scan(context)
        }
        val didFull = !forceFull && files.size > 10 &&
            files.none {
                it.absolutePath.contains("Android/music_myself") ||
                it.absolutePath.contains("com.example.myapplication") ||
                it.name.contains("录音")
            }
        val msg = when {
            files.isEmpty() -> "未找到音频文件"
            didFull -> "共找到 ${files.size} 个音频文件 (全量扫描)"
            else -> "共找到 ${files.size} 个音频文件"
        }
        onResult(files, msg, didFull)
    }
}

private fun loadSelectedFile(
    context: android.content.Context,
    uri: Uri,
    onResult: (name: String, durationMs: Long) -> Unit
) {
    val name = Mp3AudioTrimmer.getFileName(context, uri)
    val duration = Mp3AudioTrimmer.getDuration(context, uri)
    onResult(name, duration)
}