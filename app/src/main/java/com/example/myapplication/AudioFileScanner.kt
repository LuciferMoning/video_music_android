package com.example.myapplication

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File

object AudioFileScanner {

    data class AudioFileInfo(
        val name: String,
        val absolutePath: String,
        val durationMs: Long,
        val sizeBytes: Long,
        val lastModified: Long
    )

    private val AUDIO_EXTENSIONS = setOf("amr", "aac", "mp3", "wav", "m4a", "3gp")

    private val RECORDING_KEYWORDS = arrayOf("录音", "record", "call", "rec", "语音")

    private val PRIORITY_RELATIVE_PATH = "%Android/music_myself%录音%"

    /**
     * 默认扫描：优先扫描录音目录，若无结果则全量扫描。
     * 不包含输出目录（getExternalFilesDir），输出目录由 MusicPlayerScreen 独立扫描。
     */
    fun scan(context: Context): List<AudioFileInfo> {
        val priority = scanPriorityDirectory(context)
        if (priority.isNotEmpty()) {
            return applyRecordingPriority(priority)
        }
        return scanAll(context)
    }

    /**
     * 全量扫描所有音频文件（MediaStore），不含输出目录。
     */
    fun scanAll(context: Context): List<AudioFileInfo> {
        val results = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanAllViaMediaStore(context)
        } else {
            scanAllViaFileApi(context)
        }
        return applyRecordingPriority(results)
    }

    /**
     * 仅扫描 Android/录音/ 目录。
     */
    fun scanPriorityDirectory(context: Context): List<AudioFileInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanDirViaMediaStore(context)
        } else {
            val dir = File("/storage/emulated/0/Android/music_myself/录音")
            val results = mutableListOf<AudioFileInfo>()
            if (dir.exists() && dir.isDirectory) {
                collectFilesFromDir(dir, results)
            }
            applyRecordingPriority(results)
        }
    }

    /**
     * 扫描应用私有输出目录 (getExternalFilesDir("音乐"))。
     * 该目录不在 MediaStore 索引范围内，需通过 File API 直接访问。
     */
    fun scanOutputDirectory(context: Context): List<AudioFileInfo> {
        val dir = context.getExternalFilesDir("音乐") ?: return emptyList()
        val results = mutableListOf<AudioFileInfo>()
        if (dir.exists() && dir.isDirectory) {
            collectFilesFromDir(dir, results)
        }
        return results.sortedByDescending { it.lastModified }
    }

    /**
     * 通过 SAF 目录授权扫描指定目录。
     */
    fun scanSafDirectory(context: Context, treeUri: Uri): List<AudioFileInfo> {
        val results = mutableListOf<AudioFileInfo>()
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return results
        collectSafAudioFiles(context, rootDoc, results)
        return applyRecordingPriority(results)
    }

    /**
     * 构建指向 Android/录音/ 的 SAF tree URI。
     */
    fun buildSafTreeUri(): Uri {
        return Uri.parse(
            "content://com.android.externalstorage.documents/tree/" +
            "primary%3AAndroid%2Fmusic_myself%2F%E5%BD%95%E9%9F%B3"
        )
    }

    // ── MediaStore 指定目录查询 ───────────────────────────

    private fun scanDirViaMediaStore(context: Context): List<AudioFileInfo> {
        val results = mutableListOf<AudioFileInfo>()

        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(PRIORITY_RELATIVE_PATH)

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        queryAndCollect(context, selection, selectionArgs, projection, results)
        return results
    }

    private fun scanAllViaMediaStore(context: Context): List<AudioFileInfo> {
        val results = mutableListOf<AudioFileInfo>()

        val selection = AUDIO_EXTENSIONS.joinToString(" OR ") {
            "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
        }
        val selectionArgs = AUDIO_EXTENSIONS.map { "%.$it" }.toTypedArray()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        queryAndCollect(context, selection, selectionArgs, projection, results)
        return results
    }

    private fun queryAndCollect(
        context: Context,
        selection: String,
        selectionArgs: Array<String>,
        projection: Array<String>,
        results: MutableList<AudioFileInfo>
    ) {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                results.add(
                    AudioFileInfo(
                        name = cursor.getString(nameCol) ?: "unknown",
                        absolutePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            MediaStore.Audio.Media.getContentUri(
                                MediaStore.VOLUME_EXTERNAL, id
                            ).toString()
                        } else {
                            ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                            ).toString()
                        },
                        durationMs = cursor.getLong(durationCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        lastModified = cursor.getLong(dateCol)
                    )
                )
            }
        }
    }

    // ── SAF 递归收集 ──────────────────────────────────────

    private fun collectSafAudioFiles(
        context: Context,
        dir: DocumentFile,
        results: MutableList<AudioFileInfo>
    ) {
        dir.listFiles().forEach { doc ->
            if (doc.isDirectory) {
                collectSafAudioFiles(context, doc, results)
            } else if (doc.isFile && doc.name != null) {
                val ext = doc.name!!.substringAfterLast('.', "").lowercase()
                if (ext in AUDIO_EXTENSIONS) {
                    results.add(
                        AudioFileInfo(
                            name = doc.name!!,
                            absolutePath = doc.uri.toString(),
                            durationMs = getUriDuration(context, doc.uri),
                            sizeBytes = doc.length(),
                            lastModified = doc.lastModified()
                        )
                    )
                }
            }
        }
    }

    // ── File API 回退 ─────────────────────────────────────

    private fun collectFilesFromDir(dir: File, results: MutableList<AudioFileInfo>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                collectFilesFromDir(file, results)
            } else if (file.isFile && file.extension.lowercase() in AUDIO_EXTENSIONS) {
                results.add(
                    AudioFileInfo(
                        name = file.name,
                        absolutePath = file.absolutePath,
                        durationMs = getFileDuration(file.absolutePath),
                        sizeBytes = file.length(),
                        lastModified = file.lastModified()
                    )
                )
            }
        }
    }

    private fun scanAllViaFileApi(context: Context): List<AudioFileInfo> {
        val results = mutableListOf<AudioFileInfo>()
        val scanDirs = mutableListOf(
            "/storage/emulated/0/Android/music_myself/录音",
            "/storage/emulated/0/MIUI/sound_recorder",
            "/storage/emulated/0/Recordings"
        )
        for (dirPath in scanDirs) {
            val dir = File(dirPath)
            if (dir.exists() && dir.isDirectory) {
                collectFilesFromDir(dir, results)
            }
        }
        return results
    }

    // ── 优先级排序 ────────────────────────────────────────

    private fun applyRecordingPriority(files: List<AudioFileInfo>): List<AudioFileInfo> {
        val (priority, rest) = files.partition { info ->
            val lower = info.name.lowercase()
            RECORDING_KEYWORDS.any { lower.contains(it) }
        }
        return priority.sortedByDescending { it.lastModified } +
                rest.sortedByDescending { it.lastModified }
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

    private fun getUriDuration(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L }
        finally { retriever.release() }
    }
}