package com.example.chenyi.android_training.playback

import android.support.v4.media.session.MediaSessionCompat.QueueItem

/**
 * 代表本地或远程播放控制的接口，由 the Playback object 的实例直接工作来完成各种调用，如播放、暂停等
 * Created by chenyi on 17-8-11.
 */
interface Playback {
    /**
     * Get the current [android.media.session.PlaybackState.getState]
     * Set the latest playback state as determined by the caller.
     */
    var state: Int

    /**
     * @return boolean that indicates that this is ready to be used.
     */
    val isConnected: Boolean

    /**
     * @return boolean indicating whether the player is playing or is supposed to be
     * * playing when we gain audio focus.
     */
    val isPlaying: Boolean

    /**
     * @return pos if currently playing an item
     */
    var currentStreamPosition: Long

    var currentMediaId: String?

    /**
     * Start/setup the playback.
     * Resources/listeners would be allocated by implementations.
     */
    fun start()

    /**
     * Stop the playback. All resources can be de-allocated by implementations here.
     * @param notifyListeners if true and a callback has been set by setCallback,
     *                        callback.onPlaybackStatusChanged will be called after changing
     *                        the state.
     */
    fun stop(notifyListeners: Boolean)

    fun play(item: QueueItem)

    fun pause()

    fun seekTo(position: Long)

    var callback: Callback?

    interface Callback {

        /**
         * On current music completed.
         */
        fun onCompletion()

        /**
         * on Playback status changed
         * Implementations can use this callback to update
         * playback state on the media sessions.
         */
        fun onPlaybackStatusChanged(state: Int)

        /**
         * @param error to be added to the PlaybackState
         */
        fun onError(error: String)
        /**
         * @param mediaId being currently played
         */
        fun setCurrentMediaId(mediaId: String)

    }
}
