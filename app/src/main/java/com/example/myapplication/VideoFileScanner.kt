package com.example.myapplication

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object VideoFileScanner {

    data class VideoFileInfo(
        val name: String,
        val uri: Uri,
        val durationMs: Long,
        val sizeBytes: Long,
        val lastModified: Long
    )

    private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "mkv", "avi", "3gp", "wmv", "webm", "flv")

    private val PRIORITY_RELATIVE_PATH_CN = "%Android/music_myself%视频%"
    private val PRIORITY_RELATIVE_PATH_EN = "%Android/music_myself/video%"

    /**
     * 默认扫描：扫描 Android/music_myself/视频/ 和 Android/music_myself/video/ 目录。
     */
    fun scan(context: Context): List<VideoFileInfo> {
        return scanPriorityDirectory(context)
    }

    /**
     * 全量扫描所有视频文件（MediaStore）。
     */
    fun scanAll(context: Context): List<VideoFileInfo> {
        val results = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanAllViaMediaStore(context)
        } else {
            scanAllViaFileApi()
        }
        return results.sortedByDescending { it.lastModified }
    }

    /**
     * 扫描 Android/music_myself/视频/ 和 Android/music_myself/video/ 目录（递归子目录）。
     * MediaStore 有结果直接返回；为空时 File API 遍历兜底。
     */
    fun scanPriorityDirectory(context: Context): List<VideoFileInfo> {
        val results = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val msResults = scanDirViaMediaStore(context)
            if (msResults.isNotEmpty()) {
                msResults
            } else {
                // File API 兜底（文件可能由外部直接复制，不在 MediaStore 索引中）
                val fileResults = mutableListOf<VideoFileInfo>()
                val scanDirs = listOf(
                    File(Environment.getExternalStorageDirectory(), "Android/music_myself/视频"),
                    File(Environment.getExternalStorageDirectory(), "Android/music_myself/video")
                )
                for (dir in scanDirs) {
                    if (dir.exists() && dir.isDirectory) {
                        collectFilesFromDir(dir, fileResults)
                    }
                }
                fileResults
            }
        } else {
            val fileResults = mutableListOf<VideoFileInfo>()
            val scanDirs = listOf(
                File("/storage/emulated/0/Android/music_myself/视频"),
                File("/storage/emulated/0/Android/music_myself/video")
            )
            for (dir in scanDirs) {
                if (dir.exists() && dir.isDirectory) {
                    collectFilesFromDir(dir, fileResults)
                }
            }
            fileResults
        }
        return results.sortedByDescending { it.lastModified }
    }

    // ── MediaStore 指定目录查询 ───────────────────────────

    private fun scanDirViaMediaStore(context: Context): List<VideoFileInfo> {
        val results = mutableListOf<VideoFileInfo>()

        val selection = "(${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?)"
        val selectionArgs = arrayOf(PRIORITY_RELATIVE_PATH_CN, PRIORITY_RELATIVE_PATH_EN)

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        queryAndCollect(context, selection, selectionArgs, projection, results)
        return results
    }

    private fun scanAllViaMediaStore(context: Context): List<VideoFileInfo> {
        val results = mutableListOf<VideoFileInfo>()

        val selection = VIDEO_EXTENSIONS.joinToString(" OR ") {
            "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        }
        val selectionArgs = VIDEO_EXTENSIONS.map { "%.$it" }.toTypedArray()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        queryAndCollect(context, selection, selectionArgs, projection, results)
        return results
    }

    private fun queryAndCollect(
        context: Context,
        selection: String,
        selectionArgs: Array<String>,
        projection: Array<String>,
        results: MutableList<VideoFileInfo>
    ) {
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                results.add(
                    VideoFileInfo(
                        name = cursor.getString(nameCol) ?: "unknown",
                        uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL, id)
                        } else {
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        },
                        durationMs = cursor.getLong(durationCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        lastModified = cursor.getLong(dateCol)
                    )
                )
            }
        }
    }

    // ── File API 回退 ─────────────────────────────────────

    private fun collectFilesFromDir(dir: File, results: MutableList<VideoFileInfo>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                collectFilesFromDir(file, results)
            } else if (file.isFile && file.extension.lowercase() in VIDEO_EXTENSIONS) {
                results.add(
                    VideoFileInfo(
                        name = file.name,
                        uri = Uri.fromFile(file),
                        durationMs = getFileDuration(file.absolutePath),
                        sizeBytes = file.length(),
                        lastModified = file.lastModified()
                    )
                )
            }
        }
    }

    private fun scanAllViaFileApi(): List<VideoFileInfo> {
        val results = mutableListOf<VideoFileInfo>()
        val scanDirs = listOf(
            "/storage/emulated/0/Android/music_myself/视频",
            "/storage/emulated/0/Android/music_myself/video",
            "/storage/emulated/0/DCIM",
            "/storage/emulated/0/Movies",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Android/media"
        )
        for (dirPath in scanDirs) {
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                collectFilesFromDir(dir, results)
            }
        }
        return results
    }

    // ── 时长获取 ──────────────────────────────────────────

    private fun getFileDuration(filePath: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
        finally { retriever.release() }
    }

    // ── 格式化工具 ────────────────────────────────────────

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))}MB"
            else -> "${"%.2f".format(bytes.toDouble() / (1024 * 1024 * 1024))}GB"
        }
    }
}
