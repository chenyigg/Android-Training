package com.example.chenyi.android_training


import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.RemoteException
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.app.NotificationCompat

import com.example.chenyi.android_training.ui.MusicPlayerActivity
import com.example.chenyi.android_training.util.LogHelper

/**
 * 跟踪一个通知，并自动为一个给定的 MediaSession 更新它。
 * 保持一个可见的通知(通常)保证音乐服务不会在播放过程中被杀死。
 * Created by chenyi on 17-8-18.
 */
class MediaNotificationManager(private val mService: MusicService) : BroadcastReceiver() {

    private var mSessionToken: MediaSessionCompat.Token? = null
    private lateinit var mController: MediaControllerCompat
    private lateinit var mTransportControls: MediaControllerCompat.TransportControls

    private lateinit var mPlaybackState: PlaybackStateCompat
    private var mMetadata: MediaMetadataCompat? = null

    private val mNotificationManager = NotificationManagerCompat.from(mService)

    private val mPauseIntent = getPendingIntent(REQUEST_CODE, ACTION_PAUSE)
    private val mPlayIntent = getPendingIntent(REQUEST_CODE, ACTION_PLAY)
    private val mPreviousIntent = getPendingIntent(REQUEST_CODE, ACTION_PREV)
    private val mNextIntent = getPendingIntent(REQUEST_CODE, ACTION_NEXT)

    private val mStopCastIntent = getPendingIntent(REQUEST_CODE, ACTION_STOP_CASTING)

    private val mNotificationColor = ResourceHelper.getThemeColor(mService, R.attr.colorPrimary, Color.DKGRAY)

    private var mStarted = false

    init {
        updateSessionToken()
        mNotificationManager.cancelAll()
    }

    /**
     * 发布通知并开始跟踪 session 以保持更新. 如果 session 在此之前被销毁，则通知将自动删除。调用停止通知。
     */
    fun startNotification() {
        if (!mStarted) {
            mMetadata = mController.metadata
            mPlaybackState = mController.playbackState

            // The notification must be updated after setting started to true
            val notification = createNotification()
            if (notification != null) {
                mController.registerCallback(mCb)
                val filter = IntentFilter()
                filter.addAction(ACTION_NEXT)
                filter.addAction(ACTION_PAUSE)
                filter.addAction(ACTION_PLAY)
                filter.addAction(ACTION_PREV)
                filter.addAction(ACTION_STOP_CASTING)
                mService.registerReceiver(this, filter)

                mService.startForeground(NOTIFICATION_ID, notification)
                mStarted = true
            }
        }
    }

    /**
     * 删除通知并停止跟踪 session 。如果 session 被摧毁，这没有效果。
     */
    fun stopNotification() {
        if (mStarted) {
            mStarted = false
            mController.unregisterCallback(mCb)
            try {
                mNotificationManager.cancel(NOTIFICATION_ID)
                mService.unregisterReceiver(this)
            } catch (ex: IllegalArgumentException) {
                // ignore if the receiver is not registered.
            }

            mService.stopForeground(true)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        LogHelper.d(TAG, "Received intent with action ${intent?.action}")
        when (intent?.action) {
            ACTION_PAUSE -> mTransportControls.pause()
            ACTION_PLAY -> mTransportControls.play()
            ACTION_NEXT -> mTransportControls.skipToNext()
            ACTION_PREV -> mTransportControls.skipToPrevious()
            ACTION_STOP_CASTING -> {
                val i = Intent(context, MusicService::class.java)
                i.action = MusicService.ACTION_CMD
                i.putExtra(MusicService.CMD_NAME, MusicService.CMD_STOP_CASTING)
                mService.startService(i)
            }
            else -> LogHelper.w(TAG, "Unknown intent ignored. Action=${intent?.action}")
        }
    }

    /**
     * Update the state based on a change on the session token. Called either when
     * we are running for the first time or when the media session owner has destroyed the session
     * (see [android.media.session.MediaController.Callback.onSessionDestroyed])
     */
    @Throws(RemoteException::class)
    private fun updateSessionToken() {
        val freshToken = mService.sessionToken
        if (freshToken == null) {
            mSessionToken = freshToken
            return
        }
        if (mSessionToken != freshToken) {
            if (mSessionToken != null) {
                mController.unregisterCallback(mCb)
            }
            mSessionToken = freshToken
            mController = MediaControllerCompat(mService, mSessionToken)
            mTransportControls = mController.transportControls
            if (mStarted) {
                mController.registerCallback(mCb)
            }
        }

    }

    private fun createContentIntent(description: MediaDescriptionCompat?): PendingIntent {
        val openUI = Intent(mService, MusicPlayerActivity::class.java)
        openUI.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        openUI.putExtra(MusicPlayerActivity.EXTRA_START_FULLSCREEN, true)
        if (description != null) {
            openUI.putExtra(MusicPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION, description)
        }
        return PendingIntent.getActivity(mService, REQUEST_CODE, openUI,
                PendingIntent.FLAG_CANCEL_CURRENT)
    }

    private fun getPendingIntent(requestCode: Int, action: String): PendingIntent {
        return PendingIntent.getBroadcast(mService, requestCode,
                Intent(action).setPackage(mService.packageName), PendingIntent.FLAG_CANCEL_CURRENT)
    }

    private val mCb = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            mPlaybackState = state
            LogHelper.d(TAG, "Received new playback state", state)
            when (state.state) {
                PlaybackStateCompat.STATE_STOPPED,
                PlaybackStateCompat.STATE_NONE -> stopNotification()
                else -> createNotification()?.let { mNotificationManager.notify(NOTIFICATION_ID, it) }
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if (metadata == null) return
            mMetadata = metadata
            LogHelper.d(TAG, "Received new metadata $metadata")
            createNotification()?.let { mNotificationManager.notify(NOTIFICATION_ID, it) }
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            LogHelper.d(TAG, "Session was destroyed, resetting to the new session token")
            try {
                updateSessionToken()
            } catch (e: RemoteException) {
                LogHelper.e(TAG, e, "could not connect media controller")
            }

        }
    }

    private fun createNotification(): Notification? {
        LogHelper.d(TAG, "updateNotificationMetadata. mMetadata=$mMetadata")

        if (mMetadata == null) return null

        val notificationBuilder = NotificationCompat.Builder(mService)
        var playPauseButtonPosition = 0

        // If skip to previous action is enabled
        if (mPlaybackState.actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS != 0L) {
            notificationBuilder.addAction(R.mipmap.ic_skip_previous_white_24dp,
                    mService.getString(R.string.label_previous), mPreviousIntent)

            // If there is a "skip to previous" button, the play/pause button will
            // be the second one. We need to keep track of it, because the MediaStyle notification
            // requires to specify the index of the buttons (actions) that should be visible
            // when in compact view.
            playPauseButtonPosition = 1
        }

        addPlayPauseAction(notificationBuilder)

        // If skip to next action is enabled
        if (mPlaybackState.actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L) {
            notificationBuilder.addAction(R.mipmap.ic_skip_next_white_24dp,
                    mService.getString(R.string.label_next), mNextIntent)
        }

        val description = mMetadata?.description ?: return null

        var fetchArtUrl: String? = null
        var art: Bitmap? = null
        if (description.iconUri != null) {
            // This sample assumes the iconUri will be a valid URL formatted String, but
            // it can actually be any valid Android Uri formatted String.
            // async fetch the album art icon
            val artUrl = description.iconUri.toString()
            art = AlbumArtCache.getBigImage(artUrl)
            if (art == null) {
                fetchArtUrl = artUrl
                // use a placeholder art while the remote art is being downloaded
                art = BitmapFactory.decodeResource(mService.resources, R.mipmap.ic_default_art)
            }
        }

        notificationBuilder
                .setStyle(NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(playPauseButtonPosition)  // show only play/pause in compact view
                        .setMediaSession(mSessionToken))
                .setColor(mNotificationColor)
                .setSmallIcon(R.mipmap.ic_notification)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setUsesChronometer(true)
                .setContentIntent(createContentIntent(description))
                .setContentTitle(description.title)
                .setContentText(description.subtitle)
                .setLargeIcon(art)

        if (mController.extras != null) {
            val castName = mController.extras.getString(MusicService.EXTRA_CONNECTED_CAST)
            if (castName != null) {
                val castInfo = mService.resources
                        .getString(R.string.casting_to_device, castName)
                notificationBuilder.setSubText(castInfo)
                notificationBuilder.addAction(R.mipmap.ic_close_black_24dp,
                        mService.getString(R.string.stop_casting), mStopCastIntent)
            }
        }

        setNotificationPlaybackState(notificationBuilder)
        if (fetchArtUrl != null) {
            fetchBitmapFromURLAsync(fetchArtUrl, notificationBuilder)
        }

        return notificationBuilder.build()
    }

    private fun addPlayPauseAction(builder: NotificationCompat.Builder) {
        LogHelper.d(TAG, "updatePlayPauseAction")
        val label: String
        val icon: Int
        val intent: PendingIntent
        if (mPlaybackState.state == PlaybackStateCompat.STATE_PLAYING) {
            label = mService.getString(R.string.label_pause)
            icon = R.mipmap.uamp_ic_pause_white_24dp
            intent = mPauseIntent
        } else {
            label = mService.getString(R.string.label_play)
            icon = R.mipmap.uamp_ic_play_arrow_white_24dp
            intent = mPlayIntent
        }
        builder.addAction(android.support.v4.app.NotificationCompat.Action(icon, label, intent))
    }

    private fun setNotificationPlaybackState(builder: NotificationCompat.Builder) {
        LogHelper.d(TAG, "updateNotificationPlaybackState. mPlaybackState=" + mPlaybackState)
        if (!mStarted) {
            LogHelper.d(TAG, "updateNotificationPlaybackState. cancelling notification!")
            mService.stopForeground(true)
            return
        }
        if (mPlaybackState.state == PlaybackStateCompat.STATE_PLAYING && mPlaybackState.position >= 0) {
            LogHelper.d(TAG, "updateNotificationPlaybackState. updating playback position to ",
                    (System.currentTimeMillis() - mPlaybackState.position) / 1000, " seconds")
            builder
                    .setWhen(System.currentTimeMillis() - mPlaybackState.position)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
        } else {
            LogHelper.d(TAG, "updateNotificationPlaybackState. hiding playback position")
            builder
                    .setWhen(0)
                    .setShowWhen(false)
                    .setUsesChronometer(false)
        }

        // Make sure that the notification can be dismissed by the user when we are not playing:
        builder.setOngoing(mPlaybackState.state == PlaybackStateCompat.STATE_PLAYING)
    }

    private fun fetchBitmapFromURLAsync(bitmapUrl: String,
                                        builder: NotificationCompat.Builder) {
        AlbumArtCache.fetch(bitmapUrl, object : AlbumArtCache.FetchListener() {
            override fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap) {
                if (mMetadata?.description?.iconUri.toString() == artUrl) {
                    // If the media is still the same, update the notification:
                    LogHelper.d(TAG, "fetchBitmapFromURLAsync: set bitmap to ", artUrl)
                    builder.setLargeIcon(bigImage)
                    mNotificationManager.notify(NOTIFICATION_ID, builder.build())
                }
            }
        })
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(MediaNotificationManager::class.java)

        private val NOTIFICATION_ID = 412
        private val REQUEST_CODE = 100

        val ACTION_PAUSE = "com.example.android.chenyi.pause"
        val ACTION_PLAY = "com.example.android.chenyi.play"
        val ACTION_PREV = "com.example.android.chenyi.prev"
        val ACTION_NEXT = "com.example.android.chenyi.next"
        val ACTION_STOP_CASTING = "com.example.android.chenyi.stop_cast"
    }
}