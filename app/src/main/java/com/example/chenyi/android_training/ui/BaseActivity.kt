package com.example.chenyi.android_training.ui

import android.content.ComponentName
import android.os.Bundle
import android.os.RemoteException
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.chenyi.android_training.MusicService
import com.example.chenyi.android_training.R
import com.example.chenyi.android_training.util.LogHelper
import com.example.chenyi.android_training.util.NetworkHelper

/**
 * 当媒体播放时需要显示播放控制 Fragment 的活动的 BaseActivity
 * Created by chenyi on 17-8-16.
 */
abstract class BaseActivity : ActionBarActivity(), MediaBrowserProvider {

    private lateinit var mMediaBrowser: MediaBrowserCompat
    private lateinit var mControlsFragment: PlaybackControlsFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d(TAG, "Activity onCreate")

        mMediaBrowser = MediaBrowserCompat(this,
                ComponentName(this, MusicService::class.java), mConnectionCallback, null)
    }

    override fun onStart() {
        super.onStart()
        LogHelper.d(TAG, "Activity onStart")

        mControlsFragment = fragmentManager
                .findFragmentById(R.id.fragment_playback_controls) as PlaybackControlsFragment

        hidePlaybackControls()

        mMediaBrowser.connect()
    }

    override fun onStop() {
        super.onStop()
        LogHelper.d(TAG, "Activity onStop")

        supportMediaController?.unregisterCallback(mMediaControllerCallback)
        mMediaBrowser.disconnect()
    }

    override fun getMediaBrowser(): MediaBrowserCompat = mMediaBrowser

    protected fun onMediaControllerConnected() {
        // empty implementation, can be overridden by clients.
    }

    protected fun showPlaybackControls() {
        LogHelper.d(TAG, "showPlaybackControls")
        if (NetworkHelper.isOnline(this)) {
            fragmentManager.beginTransaction()
                    .setCustomAnimations(
                            R.animator.slide_in_from_bottom, R.animator.slide_out_to_bottom,
                            R.animator.slide_in_from_bottom, R.animator.slide_out_to_bottom)
                    .show(mControlsFragment)
                    .commit()
        }
    }

    protected fun hidePlaybackControls() {
        LogHelper.d(TAG, "hidePlaybackControls")
        fragmentManager.beginTransaction()
                .hide(mControlsFragment)
                .commit()
    }

    /**
     * 检查 MediaSession 是否处于活动状态，是否处于“可回放”状态 (not NONE and not STOPPED).
     *
     * @return true if the MediaSession's state requires playback controls to be visible.
     */
    protected fun shouldShowControls(): Boolean {

        if (supportMediaController?.metadata == null ||
                supportMediaController?.playbackState == null) {
            return false
        }
        return when (mediaController.playbackState.state) {
            PlaybackStateCompat.STATE_ERROR,
            PlaybackStateCompat.STATE_NONE,
            PlaybackStateCompat.STATE_STOPPED -> false
            else -> true
        }
    }

    @Throws(RemoteException::class)
    private fun connectToSession(token: MediaSessionCompat.Token) {
        val mediaController = MediaControllerCompat(this, token)
        supportMediaController = mediaController
        mediaController.registerCallback(mMediaControllerCallback)

        if (shouldShowControls()) {
            showPlaybackControls()
        } else {
            LogHelper.d(TAG, "connectionCallback.onConnected: " + "hiding controls because metadata is null")
            hidePlaybackControls()
        }

        mControlsFragment.onConnected()

        onMediaControllerConnected()
    }

    // 确保正在显示控制的 view 的回调
    private val mMediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            if (shouldShowControls()) {
                showPlaybackControls()
            } else {
                LogHelper.d(TAG, "mediaControllerCallback.onPlaybackStateChanged: " +
                        "hiding controls because state is ", state.state)
                hidePlaybackControls()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if (shouldShowControls()) {
                showPlaybackControls()
            } else {
                LogHelper.d(TAG, "mediaControllerCallback.onMetadataChanged: " +
                        "hiding controls because metadata is null")
                hidePlaybackControls()
            }
        }
    }

    private val mConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            LogHelper.d(TAG, "onConnected")
            try {
                connectToSession(mMediaBrowser.sessionToken)
            } catch (e: RemoteException) {
                LogHelper.e(TAG, e, "could not connect media controller")
                hidePlaybackControls()
            }

        }
    }

    companion object {
        val TAG = LogHelper.makeLogTag(BaseActivity::class.java)
    }
}