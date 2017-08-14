package com.example.chenyi.android_training.util

import android.content.Context
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.text.TextUtils
import com.example.chenyi.android_training.VoiceSearchParams
import com.example.chenyi.android_training.model.MusicProvider
import java.util.ArrayList

/**
 * 关于队列相关任务的工具类
 * Created by chenyi on 17-8-14.
 */
object QueueHelper {

    private val TAG = LogHelper.makeLogTag(QueueHelper::class.java)

    private val RANDOM_QUEUE_SIZE = 10

    fun getPlayingQueue(mediaId: String,
                        musicProvider: MusicProvider): List<MediaSessionCompat.QueueItem> {

        // 从媒体 ID 中获取浏览层次
        val hierarchy = MediaIDHelper.getHierarchy(mediaId)

        if (hierarchy.size != 2) {
            LogHelper.e(TAG, "Could not build a playing queue for this mediaId: ", mediaId)
            return ArrayList()
        }

        val categoryType = hierarchy[0]
        val categoryValue = hierarchy[1]
        LogHelper.d(TAG, "Creating playing queue for ", categoryType, ",  ", categoryValue)

        val tracks = if (categoryType == MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE) {
            musicProvider.getMusicsByGenre(categoryValue)
        } else {
            musicProvider.searchMusicBySongTitle(categoryValue)
        }

        return if (tracks.none()) {
            LogHelper.e(TAG, "Unrecognized category type: ", categoryType, " for media ", mediaId)
            ArrayList()
        } else {
            convertToQueue(tracks, hierarchy[0], hierarchy[1])
        }
    }

    fun getPlayingQueueFromSearch(query: String, queryParams: Bundle,
                                  musicProvider: MusicProvider): List<MediaSessionCompat.QueueItem> {

        LogHelper.d(TAG, "Creating playing queue for musics from search: ", query, " params=", queryParams)

        val params = VoiceSearchParams(query, queryParams)

        LogHelper.d(TAG, "VoiceSearchParams: ", params)


        if (params.isAny) {
            // If isAny is true, we will play anything. This is app-dependent, and can be,
            // for example, favorite playlists, "I'm feeling lucky", most recent, etc.
            return getRandomQueue(musicProvider)
        }

        val result = when {
            params.isAlbumFocus -> musicProvider.searchMusicByAlbum(params.album)
            params.isGenreFocus -> musicProvider.getMusicsByGenre(params.genre)
            params.isArtistFocus -> musicProvider.searchMusicByArtist(params.artist)
            params.isSongFocus -> musicProvider.searchMusicBySongTitle(params.song)
            else -> musicProvider.searchMusicBySongTitle(query)
        }

        return convertToQueue(result, MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH, query)
    }

    fun getMusicIndexOnQueue(queue: Iterable<MediaSessionCompat.QueueItem>,
                             mediaId: String): Int {
        return queue.indexOfFirst { mediaId == it.description.mediaId }
    }

    fun getMusicIndexOnQueue(queue: Iterable<MediaSessionCompat.QueueItem>,
                             queueId: Long): Int {
        return queue.indexOfFirst { queueId == it.queueId }
    }

    private fun convertToQueue(tracks: Iterable<MediaMetadataCompat>,
                               vararg categories: String): List<MediaSessionCompat.QueueItem> {
        val queue = ArrayList<MediaSessionCompat.QueueItem>()
        for ((count, track) in tracks.withIndex()) {

            // We create a hierarchy-aware mediaID, so we know what the queue is about by looking
            // at the QueueItem media IDs.
            val hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                    track.description.mediaId, *categories)

            val trackCopy = MediaMetadataCompat.Builder(track)
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                    .build()

            // We don't expect queues to change after created, so we use the item index as the
            // queueId. Any other number unique in the queue would work.
            val item = MediaSessionCompat.QueueItem(
                    trackCopy.description, count.toLong())
            queue.add(item)
        }
        return queue

    }

    /**
     * Create a random queue with at most [.RANDOM_QUEUE_SIZE] elements.

     * @param musicProvider the provider used for fetching music.
     * *
     * @return list containing [MediaSessionCompat.QueueItem]'s
     */
    fun getRandomQueue(musicProvider: MusicProvider): List<MediaSessionCompat.QueueItem> {
        val result = ArrayList<MediaMetadataCompat>(RANDOM_QUEUE_SIZE)
        val shuffled = musicProvider.getShuffledMusic()
        for (metadata in shuffled) {
            if (result.size == RANDOM_QUEUE_SIZE) {
                break
            }
            result.add(metadata)
        }
        LogHelper.d(TAG, "getRandomQueue: result.size=", result.size)

        return convertToQueue(result, MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH, "random")
    }

    fun isIndexPlayable(index: Int, queue: List<MediaSessionCompat.QueueItem>?): Boolean {
        return queue != null && index >= 0 && index < queue.size
    }

    /**
     * Determine if two queues contain identical media id's in order.

     * @param list1 containing [MediaSessionCompat.QueueItem]'s
     * *
     * @param list2 containing [MediaSessionCompat.QueueItem]'s
     * *
     * @return boolean indicating whether the queue's match
     */
    fun equals(list1: List<MediaSessionCompat.QueueItem>?,
               list2: List<MediaSessionCompat.QueueItem>?): Boolean {
        if (list1 === list2) {
            return true
        }
        if (list1 == null || list2 == null) {
            return false
        }
        if (list1.size != list2.size) {
            return false
        }
        for (i in list1.indices) {
            if (list1[i].queueId != list2[i].queueId) {
                return false
            }
            if (!TextUtils.equals(list1[i].description.mediaId,
                    list2[i].description.mediaId)) {
                return false
            }
        }
        return true
    }

    /**
     * Determine if queue item matches the currently playing queue item

     * @param context for retrieving the [MediaControllerCompat]
     * *
     * @param queueItem to compare to currently playing [MediaSessionCompat.QueueItem]
     * *
     * @return boolean indicating whether queue item matches currently playing queue item
     */
    fun isQueueItemPlaying(context: Context,
                           queueItem: MediaSessionCompat.QueueItem): Boolean {
        // Queue item is considered to be playing or paused based on both the controller's
        // current media id and the controller's active queue item id
        val controller = (context as FragmentActivity).supportMediaController

        val currentPlayingQueueId = controller?.playbackState?.activeQueueItemId
        val currentPlayingMediaId = controller.metadata.description.mediaId
        val itemMusicId = queueItem.description.mediaId?.let { MediaIDHelper.extractMusicIDFromMediaID(it) }

        if (queueItem.queueId == currentPlayingQueueId
                && currentPlayingMediaId != null && currentPlayingMediaId == itemMusicId) {
            return true
        }
        return false
    }

}