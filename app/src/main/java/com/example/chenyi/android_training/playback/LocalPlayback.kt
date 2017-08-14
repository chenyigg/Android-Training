package com.example.chenyi.android_training.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.chenyi.android_training.model.MusicProvider
import com.example.chenyi.android_training.model.MusicProviderSource
import com.example.chenyi.android_training.util.LogHelper
import com.example.chenyi.android_training.util.MediaIDHelper
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util

/**
 * Created by chenyi on 17-8-11.
 */
class LocalPlayback(private val mMusicProvider: MusicProvider, context: Context) : Playback {

    private val mContext = context.applicationContext
    private val mWifiLock = (mContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL, "uAmp_lock")
    private val mAudioManager = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var mPlayOnFocusGain = false
    @Volatile private var mExoPlayer: SimpleExoPlayer? =  ExoPlayerFactory.newSimpleInstance(
            mContext, DefaultTrackSelector(), DefaultLoadControl())
    private var mExoPlayerNullIsStopped = false
    private val mEventListener = ExoPlayerEventListener()

    private var mAudioNoisyReceiverRegistered: Boolean = false
    private var mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
    private val mAudioNoisyIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private val mAudioNoisyReceiver = { _: Context, intent: Intent ->
        if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
            LogHelper.d(TAG, "Headphones disconnected.")
                if (isPlaying) {
//                    val i = Intent(context, MusicService::class.java)
//                    i.action = MusicService.ACTION_CMD
//                    i.putExtra(MusicService.CMD_NAME, MusicService.CMD_PAUSE)
//                    mContext.startService(i)
                }
        }
    } as BroadcastReceiver

    override var state: Int = 0
        get() {
            if (mExoPlayer != null) {
                when (mExoPlayer?.playbackState) {
                    ExoPlayer.STATE_IDLE -> return PlaybackStateCompat.STATE_PAUSED
                    ExoPlayer.STATE_BUFFERING -> return PlaybackStateCompat.STATE_BUFFERING
                    ExoPlayer.STATE_READY ->
                        return if (mExoPlayer?.playWhenReady ?: false)
                            PlaybackStateCompat.STATE_PLAYING
                        else
                            PlaybackStateCompat.STATE_PAUSED
                    ExoPlayer.STATE_ENDED -> return PlaybackStateCompat.STATE_PAUSED
                    else -> return PlaybackStateCompat.STATE_NONE
                }
            } else {
                return if (mExoPlayerNullIsStopped)
                    PlaybackStateCompat.STATE_STOPPED
                else
                    PlaybackStateCompat.STATE_NONE
            }
        }

    override val isConnected: Boolean = true

    override val isPlaying: Boolean
        get() = mPlayOnFocusGain || mExoPlayer?.playWhenReady ?: false

    override var currentStreamPosition: Long = 0
        get() = mExoPlayer?.currentPosition ?: field

    override var currentMediaId: String? = ""

    override var callback: Playback.Callback? = null

    override fun start() {
        // Nothing to do
    }

    override fun stop(notifyListeners: Boolean) {
        giveUpAudioFocus()
        unregisterAudioNoisyReceiver()
        releaseResources(true)
    }

    override fun play(item: MediaSessionCompat.QueueItem) {
        mPlayOnFocusGain = true
        tryToGetAudioFocus()
        registerAudioNoisyReceiver()
        val mediaId  = item.description.mediaId
        val mediaHasChanged = mediaId != currentMediaId
        if (mediaHasChanged) {
            currentMediaId = mediaId
        }

        if (mediaHasChanged || mExoPlayer == null) {
            releaseResources(false)  // release everything except the player
            val musicId = mediaId?.let { MediaIDHelper.extractMusicIDFromMediaID(mediaId) }
            val track = musicId?.let { mMusicProvider.getMusic(musicId) }
            val source = track?.getString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE)?.replace(" ", "%20")

            if (mExoPlayer == null) {
                mExoPlayer = ExoPlayerFactory.newSimpleInstance(
                        mContext, DefaultTrackSelector(), DefaultLoadControl())
                mExoPlayer?.addListener(mEventListener)
            }

            mExoPlayer?.audioStreamType = AudioManager.STREAM_MUSIC

            // Produces DataSource instances through which media data is loaded.
            val dataSourceFactory = DefaultDataSourceFactory(
                    mContext, Util.getUserAgent(mContext, "uamp"), null)
            // Produces Extractor instances for parsing the media data.
            val extractorsFactory = DefaultExtractorsFactory()
            // The MediaSource represents the media to be played.
            val mediaSource = ExtractorMediaSource(
                    Uri.parse(source), dataSourceFactory, extractorsFactory, null, null)

            // Prepares media to play (happens on background thread) and triggers
            // {@code onPlayerStateChanged} callback when the stream is ready to play.
            mExoPlayer?.prepare(mediaSource)

            // If we are streaming from the internet, we want to hold a
            // Wifi lock, which prevents the Wifi radio from going to
            // sleep while the song is playing.
            mWifiLock.acquire()
        }

        configurePlayerState()
    }

    override fun pause() {

        // Pause player and cancel the 'foreground service' state.
        mExoPlayer?.playWhenReady = false

        // While paused, retain the player instance, but give up audio focus.
        releaseResources(false)
        unregisterAudioNoisyReceiver()
    }

    override fun seekTo(position: Long) {
        LogHelper.d(TAG, "seekTo called with ", position)
        if (mExoPlayer != null) {
            registerAudioNoisyReceiver()
            mExoPlayer?.seekTo(position)
        }
    }

    private fun tryToGetAudioFocus() {
        LogHelper.d(TAG, "tryToGetAudioFocus")
        val result = mAudioManager.requestAudioFocus(
                mOnAudioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mCurrentAudioFocusState = AUDIO_FOCUSED
        } else {
            mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
        }
    }

    private fun giveUpAudioFocus() {
        LogHelper.d(TAG, "giveUpAudioFocus")
        if (mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
    }

    /**
     * Reconfigures the player according to audio focus settings and starts/restarts it. This method
     * starts/restarts the ExoPlayer instance respecting the current audio focus state. So if we
     * have focus, it will play normally; if we don't have focus, it will either leave the player
     * paused or set it to a low volume, depending on what is permitted by the current focus
     * settings.
     */
    private fun configurePlayerState() {
        LogHelper.d(TAG, "configurePlayerState. mCurrentAudioFocusState=", mCurrentAudioFocusState)
        if (mCurrentAudioFocusState == AUDIO_NO_FOCUS_NO_DUCK) {
            // We don't have audio focus and can't duck, so we have to pause
            pause()
        } else {
            registerAudioNoisyReceiver()

            if (mCurrentAudioFocusState == AUDIO_NO_FOCUS_CAN_DUCK) {
                // We're permitted to play, but only if we 'duck', ie: play softly
                mExoPlayer?.volume = VOLUME_DUCK
            } else {
                mExoPlayer?.volume = VOLUME_NORMAL
            }

            // If we were playing when we lost focus, we need to resume playing.
            if (mPlayOnFocusGain) {
                mExoPlayer?.playWhenReady = true
                mPlayOnFocusGain = false
            }
        }
    }

    private val mOnAudioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange: Int ->
        LogHelper.d(TAG, "onAudioFocusChange. focusChange=", focusChange)
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> mCurrentAudioFocusState = AUDIO_FOCUSED
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                // Audio focus was lost, but it's possible to duck (i.e.: play quietly)
                mCurrentAudioFocusState = AUDIO_NO_FOCUS_CAN_DUCK
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Lost audio focus, but will gain it back (shortly), so note whether
                // playback should resume
                mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
                mPlayOnFocusGain = mExoPlayer?.playWhenReady ?:false
            }
            AudioManager.AUDIOFOCUS_LOSS ->
                // Lost audio focus, probably "permanently"
                mCurrentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
        }

        // Update the player state based on the change
        mExoPlayer?.let { configurePlayerState() }
    }

    /**
     * Releases resources used by the service for playback, which is mostly just the WiFi lock for
     * local playback. If requested, the ExoPlayer instance is also released.

     * @param releasePlayer Indicates whether the player should also be released
     */
    private fun releaseResources(releasePlayer: Boolean) {
        LogHelper.d(TAG, "releaseResources. releasePlayer=", releasePlayer)

        // Stops and releases player (if requested and available).
        if (releasePlayer && mExoPlayer != null) {
            mExoPlayer?.release()
            mExoPlayer?.removeListener(mEventListener)
            mExoPlayer = null
            mExoPlayerNullIsStopped = true
            mPlayOnFocusGain = false
        }

        if (mWifiLock.isHeld) {
            mWifiLock.release()
        }
    }

    private fun registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            mContext.registerReceiver(mAudioNoisyReceiver, mAudioNoisyIntentFilter)
            mAudioNoisyReceiverRegistered = true
        }
    }

    private fun unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            mContext.unregisterReceiver(mAudioNoisyReceiver)
            mAudioNoisyReceiverRegistered = false
        }
    }

    private inner class ExoPlayerEventListener : ExoPlayer.EventListener {
        override fun onTimelineChanged(timeline: Timeline, manifest: Any) {
            // Nothing to do.
        }

        override fun onTracksChanged(
                trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {
            // Nothing to do.
        }

        override fun onLoadingChanged(isLoading: Boolean) {
            // Nothing to do.
        }

        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
            when (playbackState) {
                ExoPlayer.STATE_IDLE, ExoPlayer.STATE_BUFFERING, ExoPlayer.STATE_READY ->
                    callback?.onPlaybackStatusChanged(state)
                ExoPlayer.STATE_ENDED ->
                    // The media player finished playing the current song.
                    callback?.onCompletion()
            }
        }

        override fun onPlayerError(error: ExoPlaybackException) {
            val what: String?
            when (error.type) {
                ExoPlaybackException.TYPE_SOURCE -> what = error.sourceException.message
                ExoPlaybackException.TYPE_RENDERER -> what = error.rendererException.message
                ExoPlaybackException.TYPE_UNEXPECTED -> what = error.unexpectedException.message
                else -> what = "Unknown: " + error
            }

            LogHelper.e(TAG, "ExoPlayer error: what=" + what)
            callback?.onError("ExoPlayer error " + what)
        }

        override fun onPositionDiscontinuity() {
            // Nothing to do.
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            // Nothing to do.
        }
    }

    companion object {
        val TAG = LogHelper.makeLogTag(LocalPlayback::class.java)

        // The volume we set the media player to when we lose audio focus, but are
        // allowed to reduce the volume instead of stopping playback.
        val VOLUME_DUCK = 0.2f
        // The volume we set the media player when we have audio focus.
        val VOLUME_NORMAL = 1.0f

        // we don't have audio focus, and can't duck (play at a low volume)
        private val AUDIO_NO_FOCUS_NO_DUCK = 0
        // we don't have focus, but can duck (play at a low volume)
        private val AUDIO_NO_FOCUS_CAN_DUCK = 1
        // we have full audio focus
        private val AUDIO_FOCUSED = 2
    }
}