package com.example.chenyi.android_training.model

import android.support.v4.media.MediaMetadataCompat
import com.example.chenyi.android_training.util.LogHelper
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * 简单的音乐数据提供者，真实的音乐数据委托给 MusicProviderSource 来获取
 * Created by chenyi on 17-8-9.
 */
class MusicProvider(mSource: MusicProviderSource = RemoteJSONSource()) {

    // Categorized caches for music track data:
    private var mMusicListByGenre: ConcurrentMap<String, List<MediaMetadataCompat>>
                                = ConcurrentHashMap<String, List<MediaMetadataCompat>>()
    private val mMusicListById: ConcurrentMap<String, MutableMediaMetadata>
                                by lazy { ConcurrentHashMap<String, MutableMediaMetadata>() }
    private val mFavoriteTracks: Set<String>
                                by lazy { Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>()) }

    internal enum class State {
        NON_INITIALIZED, INITIALIZING, INITIALIZED
    }

    @Volatile private var mCurrentState = State.NON_INITIALIZED

    interface Callback {
        fun onMusicCatalogReady(success: Boolean)
    }

    companion object {
        val TAG = LogHelper.makeLogTag(MusicProvider::class.java)
    }
}