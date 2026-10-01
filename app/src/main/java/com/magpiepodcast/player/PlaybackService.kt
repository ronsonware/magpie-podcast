package com.magpiepodcast.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.magpiepodcast.MagpieApp
import com.magpiepodcast.data.ApiConfig
import com.magpiepodcast.data.Formatters
import com.magpiepodcast.data.PlaybackProgress
import com.magpiepodcast.ui.EpisodeDetailActivity

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    private var currentEpisodeId: Int = -1
    private var currentTitle: String = ""
    private var currentDescription: String = ""
    private var currentDate: String = ""
    private var currentImage: String? = null
    private var currentAudioPath: String = ""
    private var currentDurationLabel: String = ""
    private var knownDurationMs: Long = 0L

    private val progressListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            persistProgress()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_READY) {
                val real = exoPlayer?.duration ?: C.TIME_UNSET
                if (real > 0 && knownDurationMs <= 0L) {
                    knownDurationMs = real
                }
                persistProgress()
                updateSessionActivity()
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            persistProgress()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player
        player.addListener(progressListener)

        val sessionPlayer = object : ForwardingPlayer(player) {
            override fun getDuration(): Long {
                val fromPlayer = super.getDuration()
                return when {
                    fromPlayer > 0 -> fromPlayer
                    knownDurationMs > 0 -> knownDurationMs
                    else -> C.TIME_UNSET
                }
            }
        }

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setId("magpie_podcast_playback")
            .setSessionActivity(buildSessionActivity())
            .build()
        instance = this
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        persistProgress()
        instance = null
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.removeListener(progressListener)
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        persistProgress()
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    fun playEpisode(
        episodeId: Int,
        title: String,
        description: String = "",
        date: String = "",
        image: String?,
        audioPath: String,
        durationLabel: String,
        startPositionMs: Long,
        autoPlay: Boolean = true
    ) {
        val player = exoPlayer ?: return
        currentEpisodeId = episodeId
        currentTitle = title
        currentDescription = description
        currentDate = date
        currentImage = image
        currentAudioPath = audioPath
        currentDurationLabel = durationLabel
        knownDurationMs = Formatters.parseDurationLabelToMs(durationLabel)

        val saved = MagpieApp.instance.progressRepository.get(episodeId)
        val resumeAt = when {
            saved != null && !saved.isFinished && saved.positionMs > startPositionMs ->
                saved.positionMs
            startPositionMs > 0L -> startPositionMs
            saved != null && !saved.isFinished -> saved.positionMs
            else -> 0L
        }
        if (saved?.durationMs != null && saved.durationMs > 0) {
            knownDurationMs = maxOf(knownDurationMs, saved.durationMs)
        }

        val audioUrl = ApiConfig.mediaUrl(audioPath) ?: return
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist("Magpie Podcast")
            .setAlbumTitle("Magpie Podcast")
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .apply {
                if (knownDurationMs > 0) {
                    setDurationMs(knownDurationMs)
                }
                ApiConfig.mediaUrl(image)?.let { setArtworkUri(android.net.Uri.parse(it)) }
            }
            .build()

        val item = MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaId("magpie_ep_$episodeId")
            .setMediaMetadata(metadata)
            .build()

        updateSessionActivity()

        player.playWhenReady = false
        player.setMediaItem(item)
        player.prepare()
        if (resumeAt > 0L) {
            player.seekTo(resumeAt)
        }
        player.playWhenReady = autoPlay
        persistProgress()
    }

    private fun updateSessionActivity() {
        mediaSession?.setSessionActivity(buildSessionActivity())
    }

    private fun buildSessionActivity(): PendingIntent {
        val intent = if (currentEpisodeId > 0) {
            Intent(this, EpisodeDetailActivity::class.java).apply {
                putExtra(EpisodeDetailActivity.EXTRA_ID, currentEpisodeId)
                putExtra(EpisodeDetailActivity.EXTRA_TITLE, currentTitle)
                putExtra(EpisodeDetailActivity.EXTRA_DESCRIPTION, currentDescription)
                putExtra(EpisodeDetailActivity.EXTRA_DATE, currentDate)
                putExtra(EpisodeDetailActivity.EXTRA_DURATION, currentDurationLabel)
                putExtra(EpisodeDetailActivity.EXTRA_AUDIO, currentAudioPath)
                putExtra(EpisodeDetailActivity.EXTRA_IMAGE, currentImage)
                putExtra(
                    EpisodeDetailActivity.EXTRA_RESUME_MS,
                    exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
                )
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(this, com.magpiepodcast.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }

        return PendingIntent.getActivity(
            this,
            currentEpisodeId.coerceAtLeast(0),
            intent,
            flags
        )
    }

    fun persistProgress() {
        val player = exoPlayer ?: return
        if (currentEpisodeId < 0) return
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = when {
            player.duration > 0 -> player.duration
            knownDurationMs > 0 -> knownDurationMs
            else -> 0L
        }
        MagpieApp.instance.progressRepository.save(
            PlaybackProgress(
                episodeId = currentEpisodeId,
                title = currentTitle,
                image = currentImage,
                audioUrl = currentAudioPath,
                durationLabel = currentDurationLabel,
                positionMs = position,
                durationMs = duration,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun playerOrNull(): Player? = mediaSession?.player

    companion object {
        @Volatile
        var instance: PlaybackService? = null
            private set
    }
}
