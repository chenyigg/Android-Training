package com.example.chenyi.android_training.playback

import android.content.res.AssetFileDescriptor
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import com.example.chenyi.android_training.AlbumArtCache
import com.example.chenyi.android_training.model.MusicProvider
import com.example.chenyi.android_training.util.LogHelper
import com.example.chenyi.android_training.util.MediaIDHelper
import com.example.chenyi.android_training.util.QueueHelper
import java.util.*

import com.example.chenyi.android_training.R



/**
 * 简单的数据提供给队列，跟踪当前队列和队列中的位置，还提供了基于通用查询的方法来设置当前队列，依靠一个给定的 MusicProvider
 * 提供实际的媒体元数据
 * Created by chenyi on 17-8-14.
 */
class QueueManager(val mMusicProvider: MusicProvider,
                   val mListener: MetadataUpdateListener,
                   val mResources: Resources) {

    var mPlayingQueue = Collections.synchronizedList(ArrayList<MediaSessionCompat.QueueItem>())
    var mCurrentIndex = 0

    fun isSameBrowsingCategory(mediaId: String): Boolean {
        val newBrowseHierarchy = MediaIDHelper.getHierarchy(mediaId)

        return getCurrentMusic()?.description?.mediaId?.let {
            val currentBrowseHierarchy = MediaIDHelper.getHierarchy(it)
            newBrowseHierarchy == currentBrowseHierarchy
        } ?: false
    }

    fun setCurrentQueueIndex(index: Int) {
        if (index in 0..mPlayingQueue.size) {
            mCurrentIndex = index
            mListener.onCurrentQueueIndexUpdated(mCurrentIndex)
        }
    }

    fun setCurrentQueueItem(queueId: Long): Boolean {
        // set the current index on queue from the queue Id:
        val index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, queueId)
        setCurrentQueueIndex(index)
        return index >= 0
    }

    fun setCurrentQueueItem(mediaId: String): Boolean {
        // set the current index on queue from the music Id:
        val index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, mediaId)
        setCurrentQueueIndex(index)
        return index >= 0
    }

    fun skipQueuePosition(amount: Int): Boolean {
        val index = if (mCurrentIndex + amount < 0) 0 else mCurrentIndex + amount % mPlayingQueue.size
        return if (!QueueHelper.isIndexPlayable(index, mPlayingQueue)) {
            LogHelper.e(TAG, "Cannot increment queue index by ", amount,
                    ". Current=", mCurrentIndex, " queue length=", mPlayingQueue.size)
            false
        } else {
            mCurrentIndex = index
            true
        }
    }

    fun setQueueFromSearch(query: String, extras: Bundle): Boolean {
        val queue = QueueHelper.getPlayingQueueFromSearch(query, extras, mMusicProvider)
        setCurrentQueue(mResources.getString(R.string.search_queue_title), queue)
        updateMetadata()
        return !queue.isEmpty()
    }

    fun setRandomQueue() {
        setCurrentQueue(mResources.getString(R.string.random_queue_title),
                QueueHelper.getRandomQueue(mMusicProvider))
        updateMetadata()
    }

    fun setQueueFromMusic(mediaId: String) {
        LogHelper.d(TAG, "setQueueFromMusic", mediaId)

        // The mediaId used here is not the unique musicId. This one comes from the
        // MediaBrowser, and is actually a "hierarchy-aware mediaID": a concatenation of
        // the hierarchy in MediaBrowser and the actual unique musicID. This is necessary
        // so we can build the correct playing queue, based on where the track was
        // selected from.
        var canReuseQueue = false
        if (isSameBrowsingCategory(mediaId)) {
            canReuseQueue = setCurrentQueueItem(mediaId)
        }
        if (!canReuseQueue) {
            val queueTitle = mResources.getString(R.string.browse_musics_by_genre_subtitle,
                    MediaIDHelper.extractBrowseCategoryValueFromMediaID(mediaId))
            setCurrentQueue(queueTitle,
                    QueueHelper.getPlayingQueue(mediaId, mMusicProvider), mediaId)
        }
        updateMetadata()
    }

    fun getCurrentMusic(): MediaSessionCompat.QueueItem? {
        return if (!QueueHelper.isIndexPlayable(mCurrentIndex, mPlayingQueue)) {
            null
        } else {
            mPlayingQueue[mCurrentIndex]
        }
    }

    fun getCurrentQueueSize(): Int {
        return if (mPlayingQueue == null) {
            0
        } else {
            mPlayingQueue.size
        }
    }

    protected fun setCurrentQueue(title: String, newQueue: List<MediaSessionCompat.QueueItem>) {
        setCurrentQueue(title, newQueue, null)
    }

    protected fun setCurrentQueue(title: String, newQueue: List<MediaSessionCompat.QueueItem>,
                                  initialMediaId: String?) {
        mPlayingQueue = newQueue
        val index = initialMediaId?.let { QueueHelper.getMusicIndexOnQueue(mPlayingQueue, initialMediaId) }
                ?: 0
        mCurrentIndex = Math.max(index, 0)
        mListener.onQueueUpdated(title, newQueue)
    }

    fun updateMetadata() {
        val currentMusic = getCurrentMusic()
        if (currentMusic == null) {
            mListener.onMetadataRetrieveError()
            return
        }
        val musicId = currentMusic.description?.mediaId?.let {
            MediaIDHelper.extractMusicIDFromMediaID(it)
        }
        val metadata = musicId?.let { mMusicProvider.getMusic(musicId) }
                ?: throw IllegalArgumentException("Invalid musicId " + musicId)
        mListener.onMetadataChanged(metadata)

        // Set the proper album artwork on the media session, so it can be shown in the
        // locked screen and in other places.
        if (metadata.description.iconBitmap == null && metadata.description?.iconUri != null) {
            val albumUri = metadata.description.iconUri.toString()

            AlbumArtCache.fetch(albumUri, object : AlbumArtCache.FetchListener() {
                override fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap) {
                    mMusicProvider.updateMusicArt(musicId, bigImage, iconImage)

                    // If we are still playing the same music, notify the listeners:
                    getCurrentMusic()?.description?.mediaId?.let {
                        val currentPlayingId = MediaIDHelper.extractMusicIDFromMediaID(it)

                        if (musicId == currentPlayingId) {
                            mMusicProvider.getMusic(currentPlayingId)?.let { mListener.onMetadataChanged(it) }
                        }
                    }
                }
            })
        }
    }

        companion object {
            val TAG = LogHelper.makeLogTag(QueueManager::class.java)
        }

        interface MetadataUpdateListener {
            fun onMetadataChanged(metadata: MediaMetadataCompat)
            fun onMetadataRetrieveError()
            fun onCurrentQueueIndexUpdated(queueIndex: Int)
            fun onQueueUpdated(title: String, newQueue: List<MediaSessionCompat.QueueItem>)
        }
    }