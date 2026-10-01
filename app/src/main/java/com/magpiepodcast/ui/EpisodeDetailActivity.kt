package com.magpiepodcast.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.magpiepodcast.MagpieApp
import com.magpiepodcast.R
import com.magpiepodcast.data.ApiConfig
import com.magpiepodcast.data.Formatters
import com.magpiepodcast.databinding.ActivityEpisodeDetailBinding
import com.magpiepodcast.player.PlaybackService

class EpisodeDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEpisodeDetailBinding
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private var episodeId: Int = -1
    private var title: String = ""
    private var description: String = ""
    private var date: String = ""
    private var durationLabel: String = ""
    private var audioUrl: String = ""
    private var image: String? = null
    private var resumeMs: Long = 0L
    private var userSeeking = false
    private var didStart = false
    private var restoredAfterProcessDeath = false

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateProgressUi()
            PlaybackService.instance?.persistProgress()
            handler.postDelayed(this, 1000L)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            binding.btnPlayPause.text =
                if (isPlaying) getString(R.string.pause) else getString(R.string.play)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateProgressUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpisodeDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        restoredAfterProcessDeath = savedInstanceState != null

        episodeId = intent.getIntExtra(EXTRA_ID, -1)
        title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        date = intent.getStringExtra(EXTRA_DATE).orEmpty()
        durationLabel = intent.getStringExtra(EXTRA_DURATION).orEmpty()
        audioUrl = intent.getStringExtra(EXTRA_AUDIO).orEmpty()
        image = intent.getStringExtra(EXTRA_IMAGE)
        resumeMs = resolveResumeMs(
            episodeId,
            intent.getLongExtra(EXTRA_RESUME_MS, 0L)
        )

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.title = title

        binding.title.text = title
        binding.meta.text = buildString {
            if (date.isNotBlank()) append(Formatters.formatDate(date))
            if (durationLabel.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(durationLabel)
            }
        }
        binding.description.text = description
        binding.cover.load(ApiConfig.mediaUrl(image)) { crossfade(true) }

        binding.btnPlayPause.setOnClickListener { togglePlayPause() }
        binding.btnStop.setOnClickListener { stopPlayback() }
        binding.btnRewind.setOnClickListener { seekBy(-15_000) }
        binding.btnForward.setOnClickListener { seekBy(30_000) }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val player = controller ?: return
                val duration = player.duration.takeIf { it > 0 } ?: return
                val pos = duration * progress / 1000
                binding.positionLabel.text =
                    "${Formatters.formatMs(pos)} / ${Formatters.formatMs(duration)}"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                val player = controller ?: return
                val duration = player.duration.takeIf { it > 0 } ?: return
                val pos = duration * (seekBar?.progress ?: 0) / 1000
                player.seekTo(pos)
                PlaybackService.instance?.persistProgress()
            }
        })

        connectController()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        episodeId = intent.getIntExtra(EXTRA_ID, episodeId)
        title = intent.getStringExtra(EXTRA_TITLE) ?: title
        description = intent.getStringExtra(EXTRA_DESCRIPTION) ?: description
        date = intent.getStringExtra(EXTRA_DATE) ?: date
        durationLabel = intent.getStringExtra(EXTRA_DURATION) ?: durationLabel
        audioUrl = intent.getStringExtra(EXTRA_AUDIO) ?: audioUrl
        image = intent.getStringExtra(EXTRA_IMAGE) ?: image
        binding.toolbar.title = title
        binding.title.text = title
        updateProgressUi()
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(playerListener)
            startEpisodeIfNeeded()
            handler.post(tick)
        }, MoreExecutors.directExecutor())
    }

    private fun resolveResumeMs(episodeId: Int, intentResumeMs: Long): Long {
        val saved = MagpieApp.instance.progressRepository.get(episodeId) ?: return intentResumeMs
        if (saved.isFinished) return 0L
        return maxOf(saved.positionMs, intentResumeMs)
    }

    private fun startEpisodeIfNeeded() {
        if (didStart) return
        didStart = true
        resumeMs = resolveResumeMs(episodeId, resumeMs)
        fun tryPlay(attempt: Int) {
            val service = PlaybackService.instance
            if (service != null) {
                val player = service.playerOrNull()
                val sameItemLoaded =
                    player?.currentMediaItem?.mediaId == "magpie_ep_$episodeId" &&
                        (player.playbackState == Player.STATE_READY ||
                            player.playbackState == Player.STATE_BUFFERING)
                if (sameItemLoaded) {
                    if (resumeMs > 5_000L && player!!.currentPosition < 3_000L) {
                        player.seekTo(resumeMs)
                    }
                } else {
                    service.playEpisode(
                        episodeId = episodeId,
                        title = title,
                        description = description,
                        date = date,
                        image = image,
                        audioPath = audioUrl,
                        durationLabel = durationLabel,
                        startPositionMs = resumeMs,
                        autoPlay = !restoredAfterProcessDeath
                    )
                }
                updateProgressUi()
            } else if (attempt < 10) {
                handler.postDelayed({ tryPlay(attempt + 1) }, 150L)
            }
        }
        tryPlay(0)
    }

    private fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause() else player.play()
        PlaybackService.instance?.persistProgress()
    }

    private fun stopPlayback() {
        val player = controller ?: return
        player.pause()
        player.seekTo(0)
        PlaybackService.instance?.persistProgress()
        updateProgressUi()
    }

    private fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        val duration = player.duration
        player.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
        PlaybackService.instance?.persistProgress()
        updateProgressUi()
    }

    private fun updateProgressUi() {
        val player = controller ?: return
        if (userSeeking) return
        val position = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration.takeIf { it > 0 }
            ?: Formatters.parseDurationLabelToMs(durationLabel)
        binding.positionLabel.text =
            "${Formatters.formatMs(position)} / ${Formatters.formatMs(duration)}"
        if (duration > 0) {
            binding.seekBar.progress = ((position * 1000L) / duration).toInt().coerceIn(0, 1000)
        }
        binding.btnPlayPause.text =
            if (player.isPlaying) getString(R.string.pause) else getString(R.string.play)
    }

    override fun onPause() {
        PlaybackService.instance?.persistProgress()
        super.onPause()
    }

    override fun onStop() {
        PlaybackService.instance?.persistProgress()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        PlaybackService.instance?.persistProgress()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        PlaybackService.instance?.persistProgress()
        outState.putLong(EXTRA_RESUME_MS, resolveResumeMs(episodeId, resumeMs))
        outState.putInt(EXTRA_ID, episodeId)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_DATE = "date"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_AUDIO = "audio"
        const val EXTRA_IMAGE = "image"
        const val EXTRA_RESUME_MS = "resume_ms"

        fun start(
            context: Context,
            episodeId: Int,
            title: String,
            description: String,
            date: String,
            duration: String,
            audioUrl: String,
            image: String?,
            resumeMs: Long
        ) {
            context.startActivity(
                Intent(context, EpisodeDetailActivity::class.java).apply {
                    putExtra(EXTRA_ID, episodeId)
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_DESCRIPTION, description)
                    putExtra(EXTRA_DATE, date)
                    putExtra(EXTRA_DURATION, duration)
                    putExtra(EXTRA_AUDIO, audioUrl)
                    putExtra(EXTRA_IMAGE, image)
                    putExtra(EXTRA_RESUME_MS, resumeMs)
                }
            )
        }
    }
}
