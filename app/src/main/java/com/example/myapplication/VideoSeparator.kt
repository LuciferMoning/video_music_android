package com.example.myapplication

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import java.io.File

/**
 * 音视频分离工具 — 使用 MediaExtractor + MediaMuxer 把视频文件中的
 * 视频轨和音频轨分别写到独立文件中。
 *
 * 输出目录结构:
 *   getExternalFilesDir("视频分离")
 *     ├── video/<name>.mp4   // 视频轨
 *     └── audio/<name>.m4a   // 音频轨 (AAC)
 *
 * 同源产物的文件名相同（仅扩展名不同）。
 */
object VideoSeparator {

    data class OutputFiles(
        val video: File?,
        val audio: File?
    )

    data class Result(
        val success: Boolean,
        val message: String,
        val outputs: OutputFiles? = null
    )

    /**
     * 执行音视频分离。
     * @param exportMode "both"(默认) / "video_only" / "audio_only"
     */
    fun separate(
        context: Context,
        inputUri: Uri,
        sourceName: String,
        exportMode: String = "both"
    ): Result {
        val baseName = stripExtension(sourceName)
        val videoDir = File(context.getExternalFilesDir(null), "视频分离/video")
        val audioDir = File(context.getExternalFilesDir(null), "视频分离/audio")
        if (!videoDir.exists()) videoDir.mkdirs()
        if (!audioDir.exists()) audioDir.mkdirs()

        val wantVideo = exportMode != "audio_only"
        val wantAudio = exportMode != "video_only"

        var videoFile: File? = null
        var audioFile: File? = null
        var wroteVideo = false
        var wroteAudio = false

        val extractor = MediaExtractor()

        try {
            // ── 1. 打开输入源 ──
            try {
                extractor.setDataSource(context, inputUri, null)
            } catch (e: Exception) {
                // 降级 FileDescriptor
                val pfd = context.contentResolver.openFileDescriptor(inputUri, "r")
                    ?: return Result(false, "无法打开文件: $sourceName")
                extractor.setDataSource(pfd.fileDescriptor)
                pfd.close()
            }

            // ── 2. 定位音视频轨道 ──
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    mime.startsWith("video/") && videoTrackIndex == -1 -> videoTrackIndex = i
                    mime.startsWith("audio/") && audioTrackIndex == -1 -> audioTrackIndex = i
                }
            }

            if (videoTrackIndex == -1 && audioTrackIndex == -1) {
                return Result(false, "未找到音视频轨道")
            }

            // ── 3. 提取视频轨 ──
            if (wantVideo && videoTrackIndex != -1) {
                videoFile = uniqueFile(videoDir, baseName, "mp4")
                try {
                    extractTrack(extractor, videoTrackIndex, videoFile, "video")
                    wroteVideo = true
                } catch (e: Exception) {
                    if (videoFile.exists()) videoFile.delete()
                    videoFile = null
                }
            }

            // ── 4. 提取音频轨 ──
            if (wantAudio && audioTrackIndex != -1) {
                audioFile = uniqueFile(audioDir, baseName, "m4a")
                try {
                    extractTrack(extractor, audioTrackIndex, audioFile, "audio")
                    wroteAudio = true
                } catch (e: Exception) {
                    if (audioFile.exists()) audioFile.delete()
                    audioFile = null
                }
            }

            val msg = buildString {
                append("分离完成: ")
                if (wroteVideo) append("视频 → ${videoFile?.name} ")
                if (wroteAudio) append("音频 → ${audioFile?.name}")
                if (!wroteVideo && !wroteAudio) append("未能生成任何文件")
            }

            return Result(
                success = wroteVideo || wroteAudio,
                message = msg,
                outputs = if (wroteVideo || wroteAudio) OutputFiles(videoFile, audioFile) else null
            )
        } catch (e: Exception) {
            // 清理可能的部分产物
            if (wroteVideo) videoFile?.delete()
            if (wroteAudio) audioFile?.delete()
            return Result(false, "分离失败: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            extractor.release()
        }
    }

    /**
     * 从 extractor 抽出一条轨道写到 outputFile。
     */
    private fun extractTrack(
        extractor: MediaExtractor,
        trackIndex: Int,
        outputFile: File,
        trackKind: String
    ) {
        extractor.selectTrack(trackIndex)
        extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val trackFormat = extractor.getTrackFormat(trackIndex)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val outTrackIndex: Int = try {
            muxer.addTrack(trackFormat)
        } catch (e: Exception) {
            muxer.release()
            throw Exception("$trackKind 格式不被支持: ${e.message}")
        }
        muxer.start()

        val buffer = java.nio.ByteBuffer.allocate(256 * 1024)
        val info = android.media.MediaCodec.BufferInfo()

        try {
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.size = size
                info.offset = 0
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(outTrackIndex, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            muxer.release()
            // 重新选择轨道,避免影响后续读取
            try { extractor.unselectTrack(trackIndex) } catch (_: Exception) {}
        }
    }

    private fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    private fun uniqueFile(dir: File, baseName: String, ext: String): File {
        var file = File(dir, "$baseName.$ext")
        var counter = 1
        while (file.exists()) {
            file = File(dir, "${baseName}_($counter).$ext")
            counter++
        }
        return file
    }

    /**
     * 应用私有分离目录根路径(只读展示用)。
     */
    fun getOutputRoot(context: Context): String {
        return File(context.getExternalFilesDir(null), "视频分离").absolutePath
    }
}
