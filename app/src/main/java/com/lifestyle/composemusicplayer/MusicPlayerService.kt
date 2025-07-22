package com.lifestyle.composemusicplayer

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val PREV = "prev"
const val NEXT = "next"
const val PLAY_PAUSE = "play_pause"

class MusicPlayerService : Service() {

    private var mediaPlayer = MediaPlayer()
    private val binder = MusicBinder()
    private val currentTrack = MutableStateFlow(Track())
    private var musicList = mutableListOf<Track>()
    private val maxDuration = MutableStateFlow(0f)
    private val currentDuration = MutableStateFlow(0f)
    private val isPlaying = MutableStateFlow(false)
    private var job: Job? = null

    private val scope = CoroutineScope(Dispatchers.Main)

    inner class MusicBinder : Binder() {
        fun getService() = this@MusicPlayerService

        fun setMusicList(list: List<Track>) {
            this@MusicPlayerService.musicList = list.toMutableList()
        }

        fun currentDuration() = this@MusicPlayerService.currentDuration

        fun maxDuration() = this@MusicPlayerService.maxDuration

        fun isPlaying() = this@MusicPlayerService.isPlaying

        fun getCurrentTract() = this@MusicPlayerService.currentTrack

    }

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (intent.action) {
                PREV -> {
                    prev()
                }

                PLAY_PAUSE -> {
                    playPause()
                }

                NEXT -> {
                    next()
                }

                else -> {
                    currentTrack.update { songs.get(0) }
                    play(currentTrack.value)
                }
            }
        }
        return START_STICKY
    }

    private fun updateDuration() {
        job = scope.launch {
            try {
                if (mediaPlayer.isPlaying.not()) return@launch
                maxDuration.update { mediaPlayer.duration.toFloat() }
                while (true) {
                    currentDuration.update {
                        mediaPlayer.currentPosition.toFloat()
                    }
                    delay(1000)
                }
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    private fun play(track: Track) {
        Log.d("check_hello", "Inside play")
        mediaPlayer.reset()
        mediaPlayer = MediaPlayer()
        mediaPlayer.setDataSource(this, getRawUri(track.id))
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            mediaPlayer.start()
            Log.d("check_hello", "Inside play - Before Notification Trigger")
            sendNotification(track)
            updateDuration()
        }
    }

    fun next() {
        job?.cancel()
        mediaPlayer.reset()
        mediaPlayer = MediaPlayer()
        val index = musicList.indexOf(currentTrack.value)
        val nextIndex = index.plus(1).mod(musicList.size)
        val nextItem = musicList[nextIndex]
        currentTrack.update { nextItem }
        mediaPlayer.setDataSource(this, getRawUri(currentTrack.value.id))
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            mediaPlayer.start()
            sendNotification(currentTrack.value)
            updateDuration()
        }
    }

    fun prev() {
        job?.cancel()
        mediaPlayer.reset()
        mediaPlayer = MediaPlayer()
        val index = musicList.indexOf(currentTrack.value)
        val prevIndex = if (index > 0) musicList.size else index.minus(1)
        val prevItem = musicList[prevIndex]
        currentTrack.update { prevItem }
        mediaPlayer.setDataSource(this, getRawUri(currentTrack.value.id))
        mediaPlayer.prepareAsync()
        mediaPlayer.setOnPreparedListener {
            mediaPlayer.start()
            sendNotification(currentTrack.value)
            updateDuration()
        }
    }

    fun playPause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        } else {
            mediaPlayer.start()
        }
        sendNotification(currentTrack.value)

    }

    private fun getRawUri(id: Int) = "android.resource://${packageName}/$id".toUri()

    private fun sendNotification(track: Track) {
        val session = MediaSessionCompat(this, "music")
        isPlaying.update { mediaPlayer.isPlaying }
        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setStyle(style)
            .setContentTitle(track.name)
            .setContentText(track.desc)
            .addAction(R.drawable.ic_prev, "prev", createPrevPendingIntent())
            .addAction(
                if (mediaPlayer.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                "play_pause",
                createPlayPausePendingIntent()
            )
            .addAction(R.drawable.ic_next, "next", createNextPendingIntent())
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.big_image))
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startForeground(1, notification)
            } else {
                startForeground(1, notification)
            }
        }
    }

    private fun createPrevPendingIntent(): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = PREV
        }
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createNextPendingIntent(): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = NEXT
        }
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun createPlayPausePendingIntent(): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = PLAY_PAUSE
        }
        return PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }


}