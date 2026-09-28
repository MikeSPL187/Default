package com.metrolist.music.video

import android.content.Context
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.metrolist.innertube.YouTube
import com.metrolist.music.playback.ChunkedDataSource
import com.metrolist.music.utils.InnerTubeXPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import timber.log.Timber
import kotlin.math.abs

/**
 * Shows a music video in time with the song playing in [audio]. The song never stops being played
 * from its own audio stream, with its cache, equalizer and background playback; this only draws
 * the picture, from a stream without sound, and keeps it on the song's beat: small drifts are
 * evened out by running the picture a touch faster or slower, large ones by a seek.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoPlayback(
    context: Context,
    private val audio: Player,
) {
    enum class Status { LOADING, PLAYING, FAILED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val video: ExoPlayer =
        ExoPlayer.Builder(context).build().apply {
            // The stream has no sound, and the song's own player must keep the audio focus.
            volume = 0f
            trackSelectionParameters =
                trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            repeatMode = Player.REPEAT_MODE_OFF
        }

    private val _status = MutableStateFlow(Status.LOADING)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** True once the first picture of the current clip is on screen, so the cover can give way. */
    private val _showing = MutableStateFlow(false)
    val showing: StateFlow<Boolean> = _showing.asStateFlow()

    /** Width over height of the picture; 16:9 until the stream says otherwise. */
    private val _aspectRatio = MutableStateFlow(16f / 9f)
    val aspectRatio: StateFlow<Float> = _aspectRatio.asStateFlow()

    private var clip: VideoClip? = null
    private var songId: String? = null
    private var maxHeight = 720
    private var load: Job? = null
    private var retried = false

    /** How long the picture took to be ready after its last seek; seeks aim that far ahead. */
    private var seekLeadMs = 0L
    private var seekStartedAt = 0L

    private val videoListener =
        object : Player.Listener {
            override fun onRenderedFirstFrame() {
                _showing.value = true
                _status.value = Status.PLAYING
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    _aspectRatio.value = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && seekStartedAt > 0) {
                    seekLeadMs = (System.currentTimeMillis() - seekStartedAt).coerceIn(0L, MAX_SEEK_LEAD_MS)
                    seekStartedAt = 0
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Timber.tag(TAG).w(error, "Video stream failed")
                // A signed address can expire while a long video is watched; one fresh one is tried.
                val current = clip
                if (!retried && current != null) {
                    retried = true
                    start(current, fresh = true)
                } else {
                    _status.value = Status.FAILED
                    _showing.value = false
                }
            }
        }

    private val audioListener =
        object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) seekToSong()
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                // A song repeating keeps its picture, from the top.
                val current = clip
                if (current != null && mediaItem?.mediaId == songId) {
                    seekToSong()
                    return
                }
                // The next song's picture comes with [show]; the last one must not run over it meanwhile.
                load?.cancel()
                clip = null
                songId = null
                video.stop()
                video.clearMediaItems()
                _showing.value = false
                _status.value = Status.LOADING
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = follow()

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = follow()
        }

    init {
        video.addListener(videoListener)
        audio.addListener(audioListener)
        scope.launch {
            while (isActive) {
                follow()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    fun attach(view: TextureView) = video.setVideoTextureView(view)

    fun detach(view: TextureView) = video.clearVideoTextureView(view)

    /** Shows [clip] for the song [songId] now playing, from a stream at most [maxHeight] lines tall. */
    fun show(
        songId: String,
        clip: VideoClip,
        maxHeight: Int,
    ) {
        if (songId == this.songId && clip == this.clip && maxHeight == this.maxHeight && _status.value != Status.FAILED) return
        this.songId = songId
        this.maxHeight = maxHeight
        retried = false
        start(clip, fresh = false)
    }

    private fun start(
        clip: VideoClip,
        fresh: Boolean,
    ) {
        val sameClip = clip == this.clip
        this.clip = clip
        if (!sameClip || fresh) _showing.value = false
        _status.value = Status.LOADING
        load?.cancel()
        load =
            scope.launch {
                val stream =
                    InnerTubeXPlayer.videoStream(clip.videoId, maxHeight).getOrElse {
                        Timber.tag(TAG).w(it, "No video for ${clip.videoId}")
                        _status.value = Status.FAILED
                        return@launch
                    }
                video.setMediaSource(ProgressiveMediaSource.Factory(dataSource(stream)).createMediaSource(MediaItem.fromUri(stream.url)))
                video.prepare()
                seekToSong()
                follow()
            }
    }

    /** Puts the picture where the song is now, a little ahead by the time the seek itself takes. */
    private fun seekToSong() {
        val clip = clip ?: return
        if (video.playbackState == Player.STATE_IDLE) return
        val lead = if (audio.isPlaying) (seekLeadMs * audio.playbackParameters.speed).toLong() else 0L
        seekStartedAt = System.currentTimeMillis()
        video.seekTo(clip.videoPositionFor(audio.currentPosition + lead))
    }

    private fun follow() {
        val clip = clip ?: return
        if (video.playbackState == Player.STATE_IDLE) return
        val songSpeed = audio.playbackParameters.speed
        val target = clip.videoPositionFor(audio.currentPosition)
        val drift = video.currentPosition - target
        if (!audio.isPlaying) {
            video.playWhenReady = false
            if (abs(drift) > PAUSED_TOLERANCE_MS && video.playbackState != Player.STATE_BUFFERING) seekToSong()
            return
        }
        video.playWhenReady = true
        if (video.playbackState != Player.STATE_READY) return
        val speed =
            when {
                abs(drift) > SEEK_THRESHOLD_MS -> {
                    seekToSong()
                    songSpeed
                }
                // Closes about half the gap each second, too gently to be seen.
                abs(drift) > IN_SYNC_MS -> (songSpeed * (1f - drift / 2000f)).coerceIn(songSpeed * 0.85f, songSpeed * 1.15f)
                else -> songSpeed
            }
        if (abs(video.playbackParameters.speed - speed) > 0.004f) video.setPlaybackSpeed(speed)
    }

    fun release() {
        scope.cancel()
        audio.removeListener(audioListener)
        video.removeListener(videoListener)
        video.release()
    }

    private fun dataSource(stream: InnerTubeXPlayer.VideoStream): DataSource.Factory {
        val http = OkHttpDataSource.Factory(httpClient).setDefaultRequestProperties(stream.headers)
        val chunkSize = stream.rangeChunkSizeBytes.takeIf { it > 0 } ?: ChunkedDataSource.DEFAULT_CHUNK_SIZE
        return DataSource.Factory {
            // A known length lets the stream be read in bounded ranges, which YouTube does not slow down.
            ResolvingDataSource(ChunkedDataSource(http.createDataSource(), chunkSize)) { spec ->
                val total = stream.contentLength
                if (spec.length == C.LENGTH_UNSET.toLong() && total != null && total > spec.position) {
                    spec.subrange(0, total - spec.position)
                } else {
                    spec
                }
            }
        }
    }

    private companion object {
        const val TAG = "VideoPlayback"
        const val SYNC_INTERVAL_MS = 200L
        const val IN_SYNC_MS = 35L
        const val SEEK_THRESHOLD_MS = 900L
        const val PAUSED_TOLERANCE_MS = 60L
        const val MAX_SEEK_LEAD_MS = 2_000L

        val httpClient: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .proxy(YouTube.proxy)
                .proxyAuthenticator { _, response ->
                    YouTube.proxyAuth?.let { auth -> response.request.newBuilder().header("Proxy-Authorization", auth).build() }
                }.build()
        }
    }
}
