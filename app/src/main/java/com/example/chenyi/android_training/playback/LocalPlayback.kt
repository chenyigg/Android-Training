package com.example.chenyi.android_training.playback

import android.support.v4.media.session.MediaSessionCompat
import com.example.chenyi.android_training.util.LogHelper

/**
 * Created by chenyi on 17-8-11.
 */
class LocalPlayback() : Playback {
    override var state: Int
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}
    override val isConnected: Boolean
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override val isPlaying: Boolean
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
    override var currentStreamPosition: Long
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}
    override var currentMediaId: String?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

    override fun start() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun stop(notifyListeners: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun play(item: MediaSessionCompat.QueueItem) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun pause() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun seekTo(position: Long) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override var callback: Playback.Callback?
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
        set(value) {}

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