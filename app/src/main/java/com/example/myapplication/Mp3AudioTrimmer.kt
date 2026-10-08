package com.example.myapplication

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

object Mp3AudioTrimmer {

    /**
     * 获取音频时长（毫秒）
     */
    fun getDuration(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    /**
     * 从 Content URI 获取文件名
     */
    fun getFileName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    return it.getString(nameIndex) ?: "unknown.mp3"
                }
            }
        }
        return uri.lastPathSegment ?: "unknown.mp3"
    }

    /**
     * 使用 MediaExtractor + MediaMuxer 裁剪音频
     *
     * @param context   上下文
     * @param inputUri  输入音频 URI
     * @param startMs   起始时间（毫秒）
     * @param endMs     结束时间（毫秒）
     * @param outputFile 输出文件
     * @throws Exception 裁剪失败时抛出具体错误信息
     */
    @Throws(Exception::class)
    fun trim(
        context: Context,
        inputUri: Uri,
        startMs: Long,
        endMs: Long,
        outputFile: File
    ) {
        // 确保输出目录存在
        val parentDir = outputFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw Exception("无法创建输出目录: ${parentDir.absolutePath}")
            }
        }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            // 尝试 setDataSource — 对 content:// URI 优先用 FileDescriptor 兜底
            try {
                extractor.setDataSource(context, inputUri, null)
            } catch (e1: Exception) {
                // 降级：通过 ContentResolver 打开 FileDescriptor
                try {
                    val pfd = context.contentResolver.openFileDescriptor(inputUri, "r")
                        ?: throw Exception("无法打开文件: $inputUri")
                    extractor.setDataSource(pfd.fileDescriptor)
                    pfd.close()
                } catch (e2: Exception) {
                    throw Exception("无法读取音频文件: ${e2.message ?: e1.message}")
                }
            }

            // 查找音频轨道
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }

            if (audioTrackIndex == -1) {
                throw Exception("文件中未找到音频轨道")
            }

            extractor.selectTrack(audioTrackIndex)
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val trackFormat = extractor.getTrackFormat(audioTrackIndex)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrackIndex: Int
            try {
                outputTrackIndex = muxer.addTrack(trackFormat)
            } catch (e: Exception) {
                throw Exception("音频编码格式不被输出容器支持: ${e.message}")
            }
            muxer.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val buffer = ByteBuffer.allocate(256 * 1024)
            val endTimeUs = endMs * 1000L
            var done = false

            while (!done) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)

                when {
                    sampleSize < 0 -> done = true  // 流末尾
                    extractor.sampleTime >= endTimeUs -> done = true  // 超过结束时间
                    else -> {
                        bufferInfo.size = sampleSize
                        bufferInfo.offset = 0
                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        bufferInfo.flags = extractor.sampleFlags
                        buffer.limit(sampleSize)
                        buffer.position(0)
                        muxer.writeSampleData(outputTrackIndex, buffer, bufferInfo)
                        extractor.advance()
                    }
                }
            }

            muxer.stop()
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            // 重新抛出，保留原始异常链
            throw Exception("裁剪失败: ${e.message}", e)
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    /**
     * 在应用私有音乐目录下创建输出文件（无需存储权限）。
     * 路径: getExternalFilesDir("音乐")
     */
    fun createOutputFile(context: Context): File {
        val dir = context.getExternalFilesDir("音乐") ?: File(context.filesDir, "音乐")
        if (!dir.exists()) dir.mkdirs()
        val timestamp = System.currentTimeMillis()
        return File(dir, "trimmed_$timestamp.mp3")
    }

    /**
     * 以自定义文件名在应用私有音乐目录下创建输出文件。
     * 若文件已存在，自动追加序号。
     */
    fun createOutputFile(context: Context, name: String): File {
        val dir = context.getExternalFilesDir("音乐") ?: File(context.filesDir, "音乐")
        if (!dir.exists()) dir.mkdirs()
        val baseName = if (name.endsWith(".mp3", ignoreCase = true)) name.removeSuffix(".mp3") else name
        var file = File(dir, "$baseName.mp3")
        var counter = 1
        while (file.exists()) {
            file = File(dir, "${baseName}_($counter).mp3")
            counter++
        }
        return file
    }
}