package com.example.myapplication

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MusicFileInfo(
    val name: String,
    val absolutePath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val lastModified: Long,
    val artist: String = ""
)

private val ColorBg = Color(0xFF0D1520)
private val ColorCard = Color(0xFF1E2D3D)
private val ColorBlue = Color(0xFF4A90D9)
private val ColorLightBlue = Color(0xFF67B8F7)
private val ColorTextSecondary = Color(0xFF9A9AB0)
private val ColorAccent = Color(0xFF1E3A5F)

private val AUDIO_EXTENSIONS = setOf("mp3", "aac", "m4a", "wav", "amr", "3gp", "ogg")

private fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val min = totalSecs / 60
    val sec = totalSecs % 60
    return "%d:%02d".format(min, sec)
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun getDuration(filePath: String): Long {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(filePath)
        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
        0L
    } finally {
        retriever.release()
    }
}

private fun getArtist(filePath: String): String {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(filePath)
        val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
        if (artist.isEmpty()) {
            android.util.Log.w("Lyrics", "artist提取为空，文件: $filePath")
        }
        artist
    } catch (e: Exception) {
        android.util.Log.w("Lyrics", "artist提取异常: ${e.message}，文件: $filePath")
        ""
    } finally {
        retriever.release()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicPlayerScreen(onBack: () -> Unit, onNavigateToPlayer: () -> Unit) {
    val context = LocalContext.current
    var musicFiles by remember { mutableStateOf<List<MusicFileInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // ── 播放状态（由 Service 回调驱动） ──
    var currentIndex by remember { mutableStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(0L) }

    val handler = remember { Handler(Looper.getMainLooper()) }

    // ── 服务引用 ──
    var service by remember { mutableStateOf<MusicPlaybackService?>(null) }

    // ── 删除 ──
    var deleteTarget by remember { mutableStateOf<MusicFileInfo?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val deleteInteraction = remember { MutableInteractionSource() }
    val deletePressed by deleteInteraction.collectIsPressedAsState()
    val deleteScale by animateFloatAsState(
        targetValue = if (deletePressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "deleteScale"
    )

    // ── 重命名 ──
    var renameTarget by remember { mutableStateOf<MusicFileInfo?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val renameInteraction = remember { MutableInteractionSource() }
    val renamePressed by renameInteraction.collectIsPressedAsState()
    val renameScale by animateFloatAsState(
        targetValue = if (renamePressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "renameScale"
    )

    // ═══════════════════════════════════════════════════════
    // 自动扫描：仅扫描裁剪输出目录
    // ═══════════════════════════════════════════════════════

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val allFiles = mutableListOf<MusicFileInfo>()

            // 只扫描应用私有输出目录 getExternalFilesDir("音乐")
            // 不扫描录音源目录，那是录音列表的职责
            val outDir = context.getExternalFilesDir("音乐")
            if (outDir != null && outDir.exists() && outDir.isDirectory) {
                outDir.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
                    ?.forEach { file ->
                        val id3Artist = getArtist(file.absolutePath)
                        val artist = if (id3Artist.isNotEmpty()) id3Artist
                                     else LyricsManager.extractArtistFromFilename(file.name)
                        allFiles.add(
                            MusicFileInfo(
                                name = file.name,
                                absolutePath = file.absolutePath,
                                durationMs = getDuration(file.absolutePath),
                                sizeBytes = file.length(),
                                lastModified = file.lastModified(),
                                artist = artist
                            )
                        )
                    }
            }

            musicFiles = allFiles.sortedByDescending { it.lastModified }
            isLoading = false
        }
    }

    // ═══════════════════════════════════════════════════════
    // 绑定 MusicPlaybackService
    // ═══════════════════════════════════════════════════════

    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val svc = (binder as MusicPlaybackService.LocalBinder).getService()
                service = svc
                svc.setPlaylist(musicFiles)
                svc.setStateListener(object : MusicPlaybackService.PlaybackStateListener {
                    override fun onStateChanged(
                        playing: Boolean, position: Long, dur: Long, index: Int
                    ) {
                        isPlaying = playing
                        if (!isSeeking) currentPosition = position
                        duration = dur
                        currentIndex = index
                    }
                })
                // 同步当前服务状态
                currentIndex = svc.getCurrentIndex()
                isPlaying = svc.isPlaying()
                currentPosition = svc.getCurrentPosition()
                duration = svc.getDuration()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }

        val intent = Intent(context, MusicPlaybackService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onDispose {
            context.unbindService(connection)
        }
    }

    // 当 musicFiles 变化时同步到 Service
    LaunchedEffect(musicFiles) {
        service?.setPlaylist(musicFiles)
    }

    DisposableEffect(Unit) {
        onDispose {
            handler.removeCallbacksAndMessages(null)
        }
    }

    // ═══════════════════════════════════════════════════════
    // 播放控制函数 → 委托给 Service
    // ═══════════════════════════════════════════════════════

    fun play(index: Int) {
        service?.play(index)
    }

    fun togglePlay() {
        service?.togglePlayPause()
    }

    fun playPrev() {
        service?.playPrev()
    }

    fun playNext() {
        service?.playNext()
    }

    fun deleteAndRefresh(target: MusicFileInfo) {
        if (currentIndex >= 0 && currentIndex < musicFiles.size &&
            musicFiles[currentIndex].absolutePath == target.absolutePath
        ) {
            service?.stopPlayback()
            currentIndex = -1
            isPlaying = false
            currentPosition = 0L
            duration = 0L
        }
        // 删除文件
        val result = deleteAudioFile(context, target.absolutePath)
        if (result.needsIntentSender != null) {
            // 需要系统确认的情况：简化处理，直接 File.delete 兜底
            val f = File(target.absolutePath)
            if (f.exists()) f.delete()
        }
        // 重新扫描
        val dir = context.getExternalFilesDir("音乐")
        val updated = mutableListOf<MusicFileInfo>()
        if (dir != null && dir.exists() && dir.isDirectory) {
            dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file ->
                    updated.add(
                        MusicFileInfo(
                            name = file.name,
                            absolutePath = file.absolutePath,
                            durationMs = getDuration(file.absolutePath),
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            artist = getArtist(file.absolutePath)
                        )
                    )
                }
        }
        musicFiles = updated
    }

    fun renameAndRefresh(target: MusicFileInfo, newName: String): Boolean {
        val oldFile = File(target.absolutePath)
        val ext = oldFile.extension
        val finalName = if (newName.endsWith(".$ext")) newName else "$newName.$ext"
        val newFile = File(oldFile.parentFile, finalName)

        if (newFile.exists()) return false
        if (currentIndex >= 0 && currentIndex < musicFiles.size &&
            musicFiles[currentIndex].absolutePath == target.absolutePath
        ) {
            service?.stopPlayback()
            currentIndex = -1
            isPlaying = false
            currentPosition = 0L
            duration = 0L
        }
        val ok = oldFile.renameTo(newFile)
        if (!ok) return false

        val dir = context.getExternalFilesDir("音乐")
        val updated = mutableListOf<MusicFileInfo>()
        if (dir != null && dir.exists() && dir.isDirectory) {
            dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file ->
                    updated.add(
                        MusicFileInfo(
                            name = file.name,
                            absolutePath = file.absolutePath,
                            durationMs = getDuration(file.absolutePath),
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            artist = getArtist(file.absolutePath)
                        )
                    )
                }
        }
        musicFiles = updated
        return true
    }

    // ═══════════════════════════════════════════════════════
    // 动画
    // ═══════════════════════════════════════════════════════

    val infinite = rememberInfiniteTransition(label = "spin")
    val spinAngle by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )

    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (playPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "playBtn"
    )

    val currentFile = if (currentIndex in musicFiles.indices) musicFiles[currentIndex] else null

    // ═══════════════════════════════════════════════════════
    // UI
    // ═══════════════════════════════════════════════════════

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
    ) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "\u2190", fontSize = 24.sp, color = Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(8.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text("我的音乐", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // 列表
        if (isLoading) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("扫描中...", color = ColorTextSecondary, fontSize = 15.sp)
            }
        } else if (musicFiles.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("暂无音乐文件", color = ColorTextSecondary, fontSize = 15.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(musicFiles.size) { index ->
                    val file = musicFiles[index]
                    val isActive = index == currentIndex

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (isActive)
                                    Modifier.border(1.5.dp, ColorBlue, RoundedCornerShape(14.dp))
                                else Modifier
                            )
                            .background(if (isActive) ColorAccent else ColorCard)
                            .combinedClickable(
                                onClick = {
                                    if (index != currentIndex) play(index)
                                    onNavigateToPlayer()
                                },
                                onLongClick = {
                                    deleteTarget = file
                                    showDeleteDialog = true
                                }
                            )
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) ColorBlue.copy(alpha = 0.25f)
                                        else ColorBlue.copy(alpha = 0.1f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isActive && isPlaying) "\u266B" else "\u266A",
                                    fontSize = 20.sp,
                                    color = if (isActive) ColorLightBlue else ColorTextSecondary
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    file.name,
                                    color = if (isActive) ColorLightBlue else Color.White,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Row {
                                    Text(formatDuration(file.durationMs), color = ColorTextSecondary, fontSize = 12.sp)
                                    Text(" \u00B7 ${formatSize(file.sizeBytes)}", color = ColorTextSecondary, fontSize = 12.sp)
                                    Text(" \u00B7 ${formatDate(file.lastModified)}", color = ColorTextSecondary, fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            // 重命名按钮
                            Text(
                                "\u270E", color = ColorBlue, fontSize = 16.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        renameTarget = file
                                        renameText = file.name.substringBeforeLast('.')
                                        showRenameDialog = true
                                    }
                                    .padding(4.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            // 删除按钮
                            Text(
                                "\u2715", color = ColorBlue, fontSize = 18.sp,
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
                item { Spacer(Modifier.height(100.dp)) }
            }
        }

        // 底部播放控制
        AnimatedVisibility(
            visible = currentFile != null,
            enter = fadeIn(tween(300)) + slideInVertically(animationSpec = tween(300), initialOffsetY = { it }),
            exit = fadeOut(tween(300))
        ) {
            currentFile?.let { file ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ColorCard.copy(alpha = 0.95f))
                        .clickable { onNavigateToPlayer() }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    if (duration > 0) {
                        val displayPosition = if (isSeeking) seekPosition else currentPosition
                        Slider(
                            value = (displayPosition.toFloat() / duration).coerceIn(0f, 1f),
                            onValueChange = { fraction ->
                                isSeeking = true
                                seekPosition = (fraction * duration).toLong()
                            },
                            onValueChangeFinished = {
                                service?.seekTo(seekPosition)
                                currentPosition = seekPosition
                                isSeeking = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = ColorBlue,
                                activeTrackColor = ColorBlue,
                                inactiveTrackColor = ColorBlue.copy(alpha = 0.2f)
                            )
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatDuration(displayPosition), color = ColorTextSecondary, fontSize = 11.sp)
                            Text(formatDuration(duration), color = ColorTextSecondary, fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        file.name,
                        color = ColorLightBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 上一首
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ColorBlue.copy(alpha = 0.15f))
                                .clickable(enabled = currentIndex > 0) { playPrev() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("\u23EE", fontSize = 22.sp,
                                color = if (currentIndex > 0) Color.White else ColorTextSecondary)
                        }

                        Spacer(Modifier.width(32.dp))

                        // 播放/暂停
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .scale(playScale)
                                .clip(CircleShape)
                                .background(ColorBlue)
                                .then(if (isPlaying) Modifier.rotate(spinAngle) else Modifier)
                                .clickable(
                                    interactionSource = playInteraction,
                                    indication = null
                                ) { togglePlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isPlaying) "\u23F8" else "\u25B6",
                                fontSize = 26.sp, color = Color.White
                            )
                        }

                        Spacer(Modifier.width(32.dp))

                        // 下一首
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ColorBlue.copy(alpha = 0.15f))
                                .clickable(enabled = currentIndex < musicFiles.size - 1) { playNext() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("\u23ED", fontSize = 22.sp,
                                color = if (currentIndex < musicFiles.size - 1) Color.White else ColorTextSecondary)
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
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4A4A5A).copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("\u2715", color = ColorBlue, fontSize = 24.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("确认删除", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        deleteTarget!!.name,
                        color = ColorLightBlue,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("文件将被移至回收站，确认删除？", color = ColorTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ColorAccent.copy(alpha = 0.5f))
                                .clickable { showDeleteDialog = false }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("取消", color = ColorTextSecondary, fontWeight = FontWeight.Medium)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .scale(deleteScale)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ColorBlue)
                                .clickable(interactionSource = deleteInteraction, indication = null) {
                                    val target = deleteTarget!!
                                    showDeleteDialog = false
                                    deleteTarget = null
                                    deleteAndRefresh(target)
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
    // 重命名对话框
    // ═══════════════════════════════════════════════════════

    if (showRenameDialog && renameTarget != null) {
        Dialog(
            onDismissRequest = { showRenameDialog = false },
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
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4A4A5A).copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("\u270E", color = ColorBlue, fontSize = 24.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("重命名", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ColorBg)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 15.sp
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (renameText.isEmpty()) {
                                        Text("输入新文件名", color = ColorTextSecondary, fontSize = 15.sp)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "扩展名 .${renameTarget!!.name.substringAfterLast('.')} 自动保留",
                        color = ColorTextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ColorAccent.copy(alpha = 0.5f))
                                .clickable { showRenameDialog = false }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("取消", color = ColorTextSecondary, fontWeight = FontWeight.Medium)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .scale(renameScale)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ColorBlue)
                                .clickable(interactionSource = renameInteraction, indication = null) {
                                    val target = renameTarget!!
                                    val name = renameText.trim()
                                    showRenameDialog = false
                                    renameTarget = null
                                    if (name.isNotEmpty()) {
                                        renameAndRefresh(target, name)
                                    }
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("确认", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
