package com.example.chenyi.android_training.playback

import android.content.res.Resources
import android.os.AsyncTask
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.chenyi.android_training.MusicService
import com.example.chenyi.android_training.R
import com.example.chenyi.android_training.model.MusicProvider
import com.example.chenyi.android_training.util.LogHelper
import com.example.chenyi.android_training.util.MediaIDHelper

/**
 * Created by chenyi on 17-8-15.
 */
class PlaybackManager(val serviceCallback: PlaybackServiceCallback,
                      val resources: Resources,
                      val musicProvider: MusicProvider,
                      val queueManager: QueueManager,
                      var playback: Playback)
    : Playback.Callback {

    private val mediaSessionCallback = MusicService.MediaSessionCallback()

    /**
     * Handle a request to play music
     */
    fun handlePlayRequest() {
        LogHelper.d(TAG, "handlePlayRequest: mState=" + playback.state)
        queueManager.getCurrentMusic()?.let {
            serviceCallback.onPlaybackStart()
            playback.play(it)
        }
    }

    /**
     * Handle a request to pause music
     */
    fun handlePauseRequest() {
        LogHelper.d(TAG, "handlePauseRequest: mState=" + playback.state)
        if (playback.isPlaying) {
            playback.pause()
            serviceCallback.onPlaybackStop()
        }
    }

    /**
     * Handle a request to stop music

     * @param withError Error message in case the stop has an unexpected cause. The error
     * *                  message will be set in the PlaybackState and will be visible to
     * *                  MediaController clients.
     */
    fun handleStopRequest(withError: String?) {
        LogHelper.d(TAG, "handleStopRequest: mState=" + playback.state + " error=" + withError)
        playback.stop(true)
        serviceCallback.onPlaybackStop()
        updatePlaybackState(withError)
    }

    /**
     * Update the current media player state, optionally showing an error message.
     *
     * @param error if not null, error message to present to the user.
     */
    fun updatePlaybackState(error: String?) {
        LogHelper.d(TAG, "updatePlaybackState, playback state=" + playback.state)
        val position = if (playback.isConnected) {
            playback.currentStreamPosition
        } else {
            PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN
        }

        // 不检查资源类型
        val stateBuilder = PlaybackStateCompat.Builder().setActions(getAvailableActions())

        setCustomAction(stateBuilder)
        var state = playback.state

        // If there is an error message, send it to the playback state:
        if (error != null) {
            // Error states are really only supposed to be used for errors that cause playback to
            // stop unexpectedly and persist until the user takes action to fix it.
            stateBuilder.setErrorMessage(error)
            state = PlaybackStateCompat.STATE_ERROR
        }
        //noinspection ResourceType
        stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime())

        // Set the activeQueueItemId if the current index is valid.
        queueManager.getCurrentMusic()?.let { stateBuilder.setActiveQueueItemId(it.queueId) }

        serviceCallback.onPlaybackStateUpdated(stateBuilder.build())

        if (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_PAUSED) {
            serviceCallback.onNotificationRequired()
        }
    }

    private fun setCustomAction(stateBuilder: PlaybackStateCompat.Builder) {
        val currentMusic = queueManager.getCurrentMusic() ?: return
        // Set appropriate "Favorite" icon on Custom action:
        val mediaId = currentMusic.description.mediaId ?: return
        val musicId = MediaIDHelper.extractMusicIDFromMediaID(mediaId) ?: return
        val favoriteIcon = if (musicProvider.isFavorite(musicId))
            R.mipmap.ic_star_on
        else
            R.mipmap.ic_star_off
        LogHelper.d(TAG, "updatePlaybackState, setting Favorite custom action of music ",
                musicId, " current favorite=", musicProvider.isFavorite(musicId))
        val customActionExtras = Bundle()
//        WearHelper.setShowCustomActionOnWear(customActionExtras, true)
        stateBuilder.addCustomAction(PlaybackStateCompat.CustomAction.Builder(
                CUSTOM_ACTION_THUMBS_UP, resources.getString(R.string.favorite), favoriteIcon)
                .setExtras(customActionExtras)
                .build())
    }

    private fun getAvailableActions(): Long {
        val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        return if (playback.isPlaying) {
            actions or PlaybackStateCompat.ACTION_PAUSE
        } else {
            actions or PlaybackStateCompat.ACTION_PLAY
        }
    }

    override fun onCompletion() {
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        if (queueManager.skipQueuePosition(1)) {
            handlePauseRequest()
            queueManager.updateMetadata()
        } else {
            // If skipping was not possible, we stop and release the resources:
            handleStopRequest(null)
        }
    }

    override fun onPlaybackStatusChanged(state: Int) {
        updatePlaybackState(null)
    }

    override fun onError(error: String) {
        updatePlaybackState(error)
    }

    override fun setCurrentMediaId(mediaId: String) {
        LogHelper.d(TAG, "setCurrentMediaId", mediaId)
        queueManager.setQueueFromMusic(mediaId)
    }

    companion object {
        val TAG = LogHelper.makeLogTag(PlaybackManager::class.java)
        // Action to thumbs up a media item
        val CUSTOM_ACTION_THUMBS_UP = "com.example.android.uamp.THUMBS_UP"
    }

    private inner class MediaSessionCallback : MediaSessionCompat.Callback() {
        override fun onPlay() {
            LogHelper.d(TAG, "play")
            if (queueManager.getCurrentMusic() == null) {
                queueManager.setRandomQueue()
            }
            handlePlayRequest()
        }

        override fun onSkipToQueueItem(queueId: Long) {
            LogHelper.d(TAG, "OnSkipToQueueItem:" + queueId)
            queueManager.setCurrentQueueItem(queueId)
            queueManager.updateMetadata()
        }

        override fun onSeekTo(position: Long) {
            LogHelper.d(TAG, "onSeekTo:", position)
            playback.seekTo(position)
        }

        override fun onPlayFromMediaId(mediaId: String, extras: Bundle) {
            LogHelper.d(TAG, "playFromMediaId mediaId:", mediaId, "  extras=", extras)
            queueManager.setQueueFromMusic(mediaId)
            handlePlayRequest()
        }

        override fun onPause() {
            LogHelper.d(TAG, "pause. current state=" + playback.state)
            handlePauseRequest()
        }

        override fun onStop() {
            LogHelper.d(TAG, "stop. current state=" + playback.state)
            handleStopRequest(null)
        }

        override fun onSkipToNext() {
            LogHelper.d(TAG, "skipToNext")
            if (queueManager.skipQueuePosition(1)) {
                handlePlayRequest()
            } else {
                handleStopRequest("Cannot skip")
            }
            queueManager.updateMetadata()
        }

        override fun onSkipToPrevious() {
            if (queueManager.skipQueuePosition(-1)) {
                handlePlayRequest()
            } else {
                handleStopRequest("Cannot skip")
            }
            queueManager.updateMetadata()
        }

        override fun onCustomAction(action: String, extras: Bundle?) {
            if (CUSTOM_ACTION_THUMBS_UP == action) {
                LogHelper.i(TAG, "onCustomAction: favorite for current track")

                val mediaId = queueManager.getCurrentMusic()?.description?.mediaId
                if (mediaId != null) {
                    MediaIDHelper.extractMusicIDFromMediaID(mediaId)?.let {
                        musicProvider.setFavorite(it, !musicProvider.isFavorite(it))
                    }
                }
                // playback state needs to be updated because the "Favorite" icon on the
                // custom action will change to reflect the new favorite state.
                updatePlaybackState(null)
            } else {
                LogHelper.e(TAG, "Unsupported action: ", action)
            }
        }

        /**
         * Handle free and contextual searches.
         *
         *
         * All voice searches on Android Auto are sent to this method through a connected
         * [android.support.v4.media.session.MediaControllerCompat].
         *
         *
         * Threads and async handling:
         * Search, as a potentially slow operation, should run in another thread.
         *
         *
         * Since this method runs on the main thread, most apps with non-trivial metadata
         * should defer the actual search to another thread (for example, by using
         * an [AsyncTask] as we do here).
         */
        override fun onPlayFromSearch(query: String, extras: Bundle) {
            LogHelper.d(TAG, "playFromSearch  query=", query, " extras=", extras)

            playback.state = PlaybackStateCompat.STATE_CONNECTING
            val successSearch = queueManager.setQueueFromSearch(query, extras)
            if (successSearch) {
                handlePlayRequest()
                queueManager.updateMetadata()
            } else {
                updatePlaybackState("Could not find music")
            }
        }
    }

    interface PlaybackServiceCallback {
        fun onPlaybackStart()

        fun onNotificationRequired()

        fun onPlaybackStop()

        fun onPlaybackStateUpdated(newState: PlaybackStateCompat)
    }
}