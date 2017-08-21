package com.example.chenyi.android_training.model

import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.os.AsyncTask
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import com.example.chenyi.android_training.R
import com.example.chenyi.android_training.util.LogHelper
import com.example.chenyi.android_training.util.MediaIDHelper
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import com.example.chenyi.android_training.util.MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE
import com.example.chenyi.android_training.util.MediaIDHelper.MEDIA_ID_ROOT
import kotlin.collections.HashMap

/**
 * 简单的音乐数据提供者，真实的音乐数据委托给 MusicProviderSource 来获取
 * Created by chenyi on 17-8-9.
 */
class MusicProvider(private var mSource: MusicProviderSource = RemoteJSONSource()) {

    // Categorized caches for music track data:
    private var mMusicListByGenre = HashMap<String, List<MediaMetadataCompat>>()
    private val mMusicListById by lazy { ConcurrentHashMap<String, MutableMediaMetadata>() }
    private val mFavoriteTracks by lazy { Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>()) }

    internal enum class State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    @Volatile private var mCurrentState = State.NON_INITIALIZED

    /**
     * Get an iterator over the list of genres

     * @return genres
     */
    fun getGenres(): Iterable<String> {
        if (mCurrentState != State.INITIALIZED) {
            return emptyList()
        }
        return mMusicListByGenre.keys
    }

    /**
     * Get an iterator over a shuffled collection of all songs
     */
    fun getShuffledMusic(): Iterable<MediaMetadataCompat> {
        if (mCurrentState != State.INITIALIZED) {
            return emptyList()
        }
        val shuffled = ArrayList<MediaMetadataCompat>(mMusicListById.size)
        mMusicListById.values.mapTo(shuffled) { it.metadata }
        Collections.shuffle(shuffled)
        return shuffled
    }

    /**
     * Get music tracks of the given genre
     */
    fun getMusicsByGenre(genre: String): Iterable<MediaMetadataCompat> {
        if (mCurrentState != State.INITIALIZED || !mMusicListByGenre.containsKey(genre)) {
            return emptyList()
        }
        return mMusicListByGenre[genre]?: emptyList()
    }

    /**
     * Very basic implementation of a search that filter music tracks with title containing
     * the given query.
     */
    fun searchMusicBySongTitle(query: String): Iterable<MediaMetadataCompat> {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_TITLE, query)
    }

    /**
     * Very basic implementation of a search that filter music tracks with album containing
     * the given query.
     */
    fun searchMusicByAlbum(query: String): Iterable<MediaMetadataCompat> {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_ALBUM, query)
    }

    /**
     * Very basic implementation of a search that filter music tracks with artist containing
     * the given query.
     */
    fun searchMusicByArtist(query: String): Iterable<MediaMetadataCompat> {
        return searchMusic(MediaMetadataCompat.METADATA_KEY_ARTIST, query)
    }

    private fun searchMusic(metadataField: String, query1: String): Iterable<MediaMetadataCompat> {
        if (mCurrentState != State.INITIALIZED) {
            return emptyList()
        }

        val query = query1.toLowerCase(Locale.US)
        val result = mMusicListById.values.filter {
            it.metadata.getString(metadataField).toLowerCase(Locale.US).contains(query)
        }
        return result.map { it.metadata }
    }

    /**
     * Return the MediaMetadataCompat for the given musicID.
     * @param musicId The unique, non-hierarchical music ID.
     */
    fun getMusic(musicId: String): MediaMetadataCompat? {
        return mMusicListById[musicId]?.metadata
    }

    @Synchronized fun updateMusicArt(musicId: String, albumArt: Bitmap, icon: Bitmap) {
        var metadata = getMusic(musicId)
        metadata = MediaMetadataCompat.Builder(metadata)

                // set high resolution bitmap in METADATA_KEY_ALBUM_ART. This is used, for
                // example, on the lockscreen background when the media session is active.
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)

                // set small version of the album art in the DISPLAY_ICON. This is used on
                // the MediaDescription and thus it should be small to be serialized if
                // necessary
                .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, icon)

                .build()

        val mutableMetadata = mMusicListById[musicId] ?:
                throw IllegalStateException("Unexpected error: Inconsistent data structures in MusicProvider")

        mutableMetadata.metadata = metadata
    }

    fun setFavorite(musicId: String, favorite: Boolean) {
        if (favorite) {
            mFavoriteTracks.add(musicId)
        } else {
            mFavoriteTracks.remove(musicId)
        }
    }

    fun isInitialized(): Boolean = mCurrentState == State.INITIALIZED

    fun isFavorite(musicId: String): Boolean = mFavoriteTracks.contains(musicId)

    /**
     * Get the list of music tracks from a server and caches the track information
     * for future reference, keying tracks by musicId and grouping by genre.
     */
    fun retrieveMediaAsync(callback: (Boolean) -> Unit) {
        LogHelper.d(TAG, "retrieveMediaAsync called")
        if (mCurrentState == State.INITIALIZED) {
            // Nothing to do, execute callback immediately
            callback.invoke(true)
            return
        }

        // Asynchronously load the music catalog in a separate thread
        object : AsyncTask<Void, Void, State>() {
            override fun doInBackground(vararg params: Void): State {
                retrieveMedia()
                return mCurrentState
            }

            override fun onPostExecute(current: State) {
                callback.invoke(current == State.INITIALIZED)
            }
        }.execute()
    }

    @Synchronized private fun buildListsByGenre() {
        val newMusicListByGenre = mMusicListById.values
                .map { it.metadata }
                .groupBy { it.getString(MediaMetadataCompat.METADATA_KEY_GENRE) }

        mMusicListByGenre = newMusicListByGenre as HashMap<String, List<MediaMetadataCompat>>
    }

    @Synchronized private fun retrieveMedia() {
        try {
            if (mCurrentState == State.NON_INITIALIZED) {
                mCurrentState = State.INITIALIZING

                mSource.iterator().forEach {
                    val musicId = it.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                    mMusicListById[musicId] = MutableMediaMetadata(musicId, it)
                }

                buildListsByGenre()
                mCurrentState = State.INITIALIZED
            }
        } finally {
            if (mCurrentState != State.INITIALIZED) {
                // Something bad happened, so we reset state to NON_INITIALIZED to allow
                // retries (eg if the network connection is temporary unavailable)
                mCurrentState = State.NON_INITIALIZED
            }
        }
    }

    fun getChildren(mediaId: String, resources: Resources): MutableList<MediaBrowserCompat.MediaItem> {
        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()

        if (!MediaIDHelper.isBrowseable(mediaId)) {
            return mediaItems
        }

        when {
            mediaId == MEDIA_ID_ROOT -> mediaItems.add(createBrowsableMediaItemForRoot(resources))
            mediaId == MEDIA_ID_MUSICS_BY_GENRE -> getGenres().mapTo(mediaItems) { createBrowsableMediaItemForGenre(it, resources) }
            mediaId.startsWith(MEDIA_ID_MUSICS_BY_GENRE) -> {
                val genre = MediaIDHelper.getHierarchy(mediaId)[1]
                getMusicsByGenre(genre).mapTo(mediaItems) { createMediaItem(it) }
            }
            else -> LogHelper.w(TAG, "Skipping unmatched mediaId: ", mediaId)
        }

        return mediaItems
    }

    private fun createBrowsableMediaItemForRoot(resources: Resources): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
                .setMediaId(MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE)
                .setTitle(resources.getString(R.string.browse_genres))
                .setSubtitle(resources.getString(R.string.browse_genre_subtitle))
                .setIconUri(Uri.parse("android.resource://" + "com.example.android.uamp/drawable/ic_by_genre"))
                .build()
        return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createBrowsableMediaItemForGenre(genre: String,
                                                 resources: Resources): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
                .setMediaId(MediaIDHelper.createMediaID(null, MEDIA_ID_MUSICS_BY_GENRE, genre))
                .setTitle(genre)
                .setSubtitle(resources.getString(
                        R.string.browse_musics_by_genre_subtitle, genre))
                .build()
        return MediaBrowserCompat.MediaItem(description,
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    private fun createMediaItem(metadata: MediaMetadataCompat): MediaBrowserCompat.MediaItem {
        // Since mediaMetadata fields are immutable, we need to create a copy, so we
        // can set a hierarchy-aware mediaID. We will need to know the media hierarchy
        // when we get a onPlayFromMusicID call, so we can create the proper queue based
        // on where the music was selected from (by artist, by genre, random, etc)
        val genre = metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE)
        val hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                metadata.description.mediaId, MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE, genre)
        val copy = MediaMetadataCompat.Builder(metadata)
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                .build()
        return MediaBrowserCompat.MediaItem(copy.description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
    }

    companion object {
        val TAG = LogHelper.makeLogTag(MusicProvider::class.java)
    }
}