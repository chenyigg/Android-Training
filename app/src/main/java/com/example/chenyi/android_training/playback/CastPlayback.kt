package com.example.chenyi.android_training.playback

import android.content.Context
import android.net.Uri
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.example.chenyi.android_training.model.MusicProvider
import com.example.chenyi.android_training.model.MusicProviderSource
import com.example.chenyi.android_training.util.LogHelper
import com.example.chenyi.android_training.util.MediaIDHelper
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import org.json.JSONException
import org.json.JSONObject


/**
 * 一个关于类型的 Playback 的实现
 * Created by chenyi on 17-8-11.
 */
class CastPlayback(private val mMusicProvider: MusicProvider, context: Context) : Playback {

    private val mAppContext: Context = context.applicationContext

    private val mRemoteMediaClient = CastContext.getSharedInstance(mAppContext).sessionManager
            .currentCastSession.remoteMediaClient

    private val mRemoteMediaClientListener = CastMediaClientListener()

    override var state = 0

    override val isConnected: Boolean
        get() {
            val castSession = CastContext.getSharedInstance(mAppContext).sessionManager.currentCastSession
            return castSession != null && castSession.isConnected
        }

    override val isPlaying: Boolean
        get() = isConnected && mRemoteMediaClient.isPlaying

    override var currentStreamPosition: Long = 0
        get() = if (isConnected) field else mRemoteMediaClient.approximateStreamPosition

    override var currentMediaId: String? = null

    override var callback: Playback.Callback? = null

    override fun start() {
        mRemoteMediaClient.addListener(mRemoteMediaClientListener)
    }

    override fun stop(notifyListeners: Boolean) {
        mRemoteMediaClient.removeListener(mRemoteMediaClientListener)
        state = PlaybackStateCompat.STATE_STOPPED
        if (notifyListeners) {
            callback?.onPlaybackStatusChanged(state)
        }
    }

    override fun play(item: MediaSessionCompat.QueueItem) {
        try {
            loadMedia(item.description.mediaId, true)
            state = PlaybackStateCompat.STATE_BUFFERING
            callback?.onPlaybackStatusChanged(state)
        } catch (e: JSONException) {
            LogHelper.e(TAG, "Exception loading media ", e)
            callback?.onError(e.message ?: "Exception loading media")
        }
    }

    override fun pause() {
        try {
            if (mRemoteMediaClient.hasMediaSession()) {
                mRemoteMediaClient.pause()
                currentStreamPosition = mRemoteMediaClient.approximateStreamPosition
            } else {
                loadMedia(currentMediaId, false)
            }
        } catch (e: JSONException) {
            LogHelper.e(TAG, e, "Exception pausing cast playback")
            callback?.onError(e.message ?: "Exception pausing cast playback")
        }
    }

    override fun seekTo(position: Long) {
        if (currentMediaId == null) {
            currentStreamPosition = position
            return
        }
        try {
            if (mRemoteMediaClient.hasMediaSession()) {
                mRemoteMediaClient.seek(position)
                currentStreamPosition = position
            } else {
                currentStreamPosition = position
                loadMedia(currentMediaId, false)
            }
        } catch (e: JSONException) {
            LogHelper.e(TAG, e, "Exception pausing cast playback")
            callback?.onError(e.message ?: "Exception pausing cast playback")
        }
    }

    @Throws(JSONException::class)
    private fun loadMedia(mediaId: String?, autoPlay: Boolean) {
        val musicId = mediaId?.let { MediaIDHelper.extractMusicIDFromMediaID(mediaId) }
        val track = musicId?.let { mMusicProvider.getMusic(musicId) } ?: throw IllegalArgumentException("Invalid mediaId " + mediaId)
        if (mediaId != currentMediaId) {
            currentMediaId = mediaId
            currentStreamPosition = 0
        }
        val customData = JSONObject()
        customData.put(ITEM_ID, mediaId)
        val media = toCastMediaMetadata(track, customData)
        mRemoteMediaClient.load(media, autoPlay, currentStreamPosition, customData)
    }

    private fun setMetadataFromRemote() {
        // Sync: We get the customData from the remote media information and update the local
        // metadata if it happens to be different from the one we are currently using.
        // This can happen when the app was either restarted/disconnected + connected, or if the
        // app joins an existing session while the Chromecast was playing a queue.
        try {
            val mediaInfo = mRemoteMediaClient.mediaInfo
            mediaInfo ?: return
            val customData = mediaInfo.customData
            if (customData?.has(ITEM_ID)?:false) {
                val remoteMediaId = customData.getString(ITEM_ID)
                if (currentMediaId != remoteMediaId) {
                    currentMediaId = remoteMediaId
                    callback?.setCurrentMediaId(remoteMediaId)
                }
            }
        } catch (e: JSONException) {
            LogHelper.e(TAG, e, "Exception processing update metadata")
        }
    }

    private fun updatePlaybackState() {
        val status = mRemoteMediaClient.playerState
        val idleReason = mRemoteMediaClient.idleReason

        LogHelper.d(TAG, "onRemoteMediaPlayerStatusUpdated ", status)

        // Convert the remote playback states to media playback states.
        when (status) {
            MediaStatus.PLAYER_STATE_IDLE -> if (idleReason == MediaStatus.IDLE_REASON_FINISHED) {
                callback?.onCompletion()
            }
            MediaStatus.PLAYER_STATE_BUFFERING -> {
                state = PlaybackStateCompat.STATE_BUFFERING
                callback?.onPlaybackStatusChanged(state)
            }
            MediaStatus.PLAYER_STATE_PLAYING -> {
                state = PlaybackStateCompat.STATE_PLAYING
                setMetadataFromRemote()
                callback?.onPlaybackStatusChanged(state)
            }
            MediaStatus.PLAYER_STATE_PAUSED -> {
                state = PlaybackStateCompat.STATE_PAUSED
                setMetadataFromRemote()
                callback?.onPlaybackStatusChanged(state)
            }
            else -> LogHelper.d(TAG, "State default : ", status)
        }
    }

    private inner class CastMediaClientListener : RemoteMediaClient.Listener {

        override fun onMetadataUpdated() {
            LogHelper.d(TAG, "RemoteMediaClient.onMetadataUpdated")
            setMetadataFromRemote()
        }

        override fun onStatusUpdated() {
            LogHelper.d(TAG, "RemoteMediaClient.onStatusUpdated")
            updatePlaybackState()
        }

        override fun onSendingRemoteMediaRequest() {}

        override fun onAdBreakStatusUpdated() {}

        override fun onQueueStatusUpdated() {}

        override fun onPreloadStatusUpdated() {}
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(CastPlayback::class.java)

        private val MIME_TYPE_AUDIO_MPEG = "audio/mpeg"
        private val ITEM_ID = "itemId"

        /**
         * Helper method to convert a [android.media.MediaMetadata] to a
         * [com.google.android.gms.cast.MediaInfo] used for sending media to the receiver app.

         * @param track [com.google.android.gms.cast.MediaMetadata]
         * *
         * @param customData custom data specifies the local mediaId used by the player.
         * *
         * @return mediaInfo [com.google.android.gms.cast.MediaInfo]
         */
        private fun toCastMediaMetadata(track: MediaMetadataCompat,
                                        customData: JSONObject): MediaInfo {
            val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
            mediaMetadata.putString(MediaMetadata.KEY_TITLE,
                    track.description.title?.toString() ?: "")
            mediaMetadata.putString(MediaMetadata.KEY_SUBTITLE,
                    track.description.subtitle?.toString() ?: "")
            mediaMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST,
                    track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST))
            mediaMetadata.putString(MediaMetadata.KEY_ALBUM_TITLE,
                    track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM))
            val image = WebImage(
                    Uri.Builder().encodedPath(
                            track.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI))
                            .build())
            // First image is used by the receiver for showing the audio album art.
            mediaMetadata.addImage(image)
            // Second image is used by Cast Companion Library on the full screen activity that is shown
            // when the cast dialog is clicked.
            mediaMetadata.addImage(image)

            return MediaInfo.Builder(track.getString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE))
                    .setContentType(MIME_TYPE_AUDIO_MPEG)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setMetadata(mediaMetadata)
                    .setCustomData(customData)
                    .build()
        }
    }

}