package com.example.myapplication

import android.os.Environment
import org.json.JSONObject
import java.io.File

object BilibiliCacheScanner {

    data class BilibiliCacheInfo(
        val title: String,
        val folder: File,
        val coverFile: File?,
        val videoFile: File?,
        val audioFile: File?
    )

    private val BASE_DIR = File(Environment.getExternalStorageDirectory(), "Android/music_myself/哔哩哔哩/源文件")

    /** 扫描根目录，供外部清理空父目录时使用 */
    val scanBaseDir: File get() = BASE_DIR

    fun scan(): List<BilibiliCacheInfo> {
        val results = mutableListOf<BilibiliCacheInfo>()
        if (!BASE_DIR.exists() || !BASE_DIR.isDirectory) return results
        // Android 11+ 无「所有文件访问」权限时 listFiles() 返回 null
        val rootFiles = BASE_DIR.listFiles() ?: return results
        for (file in rootFiles) {
            if (file.isDirectory) findEntries(file, results)
        }
        return results.sortedBy { it.title }
    }

    /** 目录是否可读（权限检查） */
    fun isAccessible(): Boolean = BASE_DIR.exists() && BASE_DIR.listFiles() != null

    /** 递归搜索 entry.json（最深 5 层），不受目录层级变化影响 */
    private fun findEntries(dir: File, results: MutableList<BilibiliCacheInfo>, depth: Int = 0) {
        if (depth > 5) return
        dir.listFiles()?.forEach { file ->
            if (!file.isDirectory) return@forEach
            val entryFile = File(file, "entry.json")
            if (entryFile.exists()) {
                val title = parseTitle(entryFile) ?: file.name
                val coverFile = File(file, "cover.jpg").takeIf { it.exists() }
                val (videoFile, audioFile) = findMediaFiles(file)
                if (videoFile != null || audioFile != null || coverFile != null) {
                    results.add(BilibiliCacheInfo(title, file, coverFile, videoFile, audioFile))
                }
            }
            findEntries(file, results, depth + 1)
        }
    }

    /** 在当前目录的所有子目录中查找 video.m4s / audio.m4s */
    private fun findMediaFiles(dir: File): Pair<File?, File?> {
        var videoFile: File? = null
        var audioFile: File? = null
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                val vf = File(child, "video.m4s")
                val af = File(child, "audio.m4s")
                if (vf.exists()) videoFile = vf
                if (af.exists()) audioFile = af
            }
        }
        return videoFile to audioFile
    }

    private fun parseTitle(entryFile: File): String? {
        return try {
            val json = JSONObject(entryFile.readText())
            json.optString("title", null)?.replace("\\", "")
        } catch (_: Exception) {
            null
        }
    }
}
