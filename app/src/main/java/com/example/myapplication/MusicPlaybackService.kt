package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat

class MusicPlaybackService : Service() {

    companion object {
        const val TAG = "MusicPlayback"
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 1

        const val ACTION_PLAY_PAUSE = "com.example.myapplication.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.myapplication.ACTION_NEXT"
        const val ACTION_PREV = "com.example.myapplication.ACTION_PREV"
        const val ACTION_CLOSE = "com.example.myapplication.ACTION_CLOSE"
        const val ACTION_PLAYLIST_CHANGED = "com.example.myapplication.ACTION_PLAYLIST_CHANGED"
    }

    // ── Binder ──
    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    // ── 状态 ──
    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var playlist: List<MusicFileInfo> = emptyList()
    private var currentIndex: Int = -1
    private var isPlaying: Boolean = false
    private var stateListener: PlaybackStateListener? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // ── 接口 ──
    interface PlaybackStateListener {
        fun onStateChanged(isPlaying: Boolean, position: Long, duration: Long, index: Int)
    }

    override fun onCreate() {
        super.onCreate()
        Log.e(TAG, "Service onCreate")
        createNotificationChannel()
        createMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrev()
            ACTION_CLOSE -> stopPlayback()
            ACTION_PLAYLIST_CHANGED -> notifyStateChanged()
        }
        return START_STICKY
    }

    // ═══════════════════════════════════════════════════════════
    // 公开 API
    // ═══════════════════════════════════════════════════════════

    fun setStateListener(listener: PlaybackStateListener?) {
        stateListener = listener
    }

    fun setPlaylist(files: List<MusicFileInfo>) {
        playlist = files
    }

    fun play(index: Int) {
        if (index < 0 || index >= playlist.size) return
        Log.e(TAG, "play index=$index -> ${playlist[index].name}")

        // 先清除旧播放器的回调，避免 stop 触发 playNext 竞态
        mediaPlayer?.setOnCompletionListener(null)
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null

        val file = playlist[index]
        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                prepare()
                start()
            } catch (e: Exception) {
                Log.e(TAG, "play failed: ${e.message}")
                release()
                mediaPlayer = null
                return
            }
            setOnCompletionListener {
                Log.e(TAG, "complete -> next")
                playNext()
            }
        }

        currentIndex = index
        isPlaying = true
        updateMediaSessionState()
        startForegroundWithNotification()
        startProgressUpdater()
        notifyStateChanged()
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            isPlaying = false
            handler.removeCallbacksAndMessages(null)
        } else {
            mp.start()
            isPlaying = true
            startProgressUpdater()
        }
        updateMediaSessionState()
        updateNotification()
        notifyStateChanged()
    }

    fun playNext() {
        if (currentIndex + 1 < playlist.size) {
            play(currentIndex + 1)
        } else if (playlist.isNotEmpty()) {
            play(0)
        }
    }

    fun playPrev() {
        if (currentIndex > 0) {
            play(currentIndex - 1)
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        notifyStateChanged()
    }

    fun getCurrentPosition(): Long =
        mediaPlayer?.currentPosition?.toLong() ?: 0L

    fun getDuration(): Long =
        mediaPlayer?.duration?.toLong() ?: 0L

    fun isPlaying(): Boolean = isPlaying

    fun getCurrentIndex(): Int = currentIndex

    fun getPlaylist(): List<MusicFileInfo> = playlist

    fun stopPlayback() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        isPlaying = false
        currentIndex = -1
        handler.removeCallbacksAndMessages(null)
        updateMediaSessionState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifyStateChanged()
    }

    fun stopAndShutdown() {
        stopPlayback()
        mediaSession?.release()
        mediaSession = null
        stopSelf()
    }

    // ═══════════════════════════════════════════════════════════
    // MediaSession
    // ═══════════════════════════════════════════════════════════

    @Suppress("DEPRECATION")
    private fun createMediaSession() {
        val session = MediaSessionCompat(this, TAG)
        session.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                if (!isPlaying && playlist.isNotEmpty()) {
                    if (currentIndex < 0) play(0) else togglePlayPause()
                }
            }
            override fun onPause() {
                if (isPlaying) togglePlayPause()
            }
            override fun onSkipToNext() { playNext() }
            override fun onSkipToPrevious() { playPrev() }
            override fun onStop() { stopPlayback() }
            override fun onSeekTo(pos: Long) { seekTo(pos) }
        })
        session.isActive = true
        mediaSession = session
        updateMediaSessionState()
    }

    private fun updateMediaSessionState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING
        else PlaybackStateCompat.STATE_PAUSED

        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, mediaPlayer?.currentPosition?.toLong() ?: 0L, 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_STOP or
                            PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )

        if (currentIndex in playlist.indices) {
            val file = playlist[currentIndex]
            mediaSession?.setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, file.name)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "My Music")
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, file.durationMs)
                    .build()
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 通知
    // ═══════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback control"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): Notification {
        val currentFile =
            if (currentIndex in playlist.indices) playlist[currentIndex] else null

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val flag = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)

        val playPauseIntent = PendingIntent.getBroadcast(
            this, 1, Intent(ACTION_PLAY_PAUSE).setPackage(packageName), flag
        )
        val prevIntent = PendingIntent.getBroadcast(
            this, 2, Intent(ACTION_PREV).setPackage(packageName), flag
        )
        val nextIntent = PendingIntent.getBroadcast(
            this, 3, Intent(ACTION_NEXT).setPackage(packageName), flag
        )
        val closeIntent = PendingIntent.getBroadcast(
            this, 4, Intent(ACTION_CLOSE).setPackage(packageName), flag
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(currentFile?.name ?: "My Music")
            .setContentText(if (isPlaying) "Playing" else "Paused")
            .setContentIntent(openPending)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        // close
        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel, "Close", closeIntent
            )
        )

        if (isPlaying) {
            builder.addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause, "Pause", playPauseIntent
                )
            )
        } else {
            builder.addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_play, "Play", playPauseIntent
                )
            )
        }

        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_next, "Next", nextIntent
            )
        )
        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_media_previous, "Prev", prevIntent
            )
        )

        return builder.build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    // ═══════════════════════════════════════════════════════════
    // 进度更新
    // ═══════════════════════════════════════════════════════════

    private fun startProgressUpdater() {
        handler.removeCallbacksAndMessages(null)
        val runnable = object : Runnable {
            override fun run() {
                if (mediaPlayer?.isPlaying == true) {
                    notifyStateChanged()
                    handler.postDelayed(this, 500)
                }
            }
        }
        handler.postDelayed(runnable, 500)
    }

    private fun notifyStateChanged() {
        val mp = mediaPlayer
        val pos = mp?.currentPosition?.toLong() ?: 0L
        val dur = if (mp != null && mp.duration > 0) mp.duration.toLong()
        else if (currentIndex in playlist.indices) playlist[currentIndex].durationMs
        else 0L
        stateListener?.onStateChanged(isPlaying, pos, dur, currentIndex)
    }

    override fun onDestroy() {
        Log.e(TAG, "Service onDestroy")
        mediaPlayer?.release()
        mediaPlayer = null
        handler.removeCallbacksAndMessages(null)
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
