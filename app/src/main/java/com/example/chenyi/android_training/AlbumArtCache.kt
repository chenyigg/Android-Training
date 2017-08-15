package com.example.chenyi.android_training

import android.graphics.Bitmap
import android.os.AsyncTask
import android.util.LruCache
import com.example.chenyi.android_training.util.BitmapHelper
import com.example.chenyi.android_training.util.LogHelper
import java.io.IOException

/**
 * Created by chenyi on 17-8-15.
 */
object AlbumArtCache {

    private val TAG = LogHelper.makeLogTag(AlbumArtCache::class.java)

    private val MAX_ALBUM_ART_CACHE_SIZE = 12 * 1024 * 1024 // 12 MB
    private val MAX_ART_WIDTH = 800 // 像素
    private val MAX_ART_HEIGHT = 480 // 像素

    private val MAX_ART_WIDTH_ICON = 128 // 像素
    private val MAX_ART_HEIGHT_ICON = 128 // 像素

    private val BIG_BITMAP_INDEX = 0
    private val ICON_BITMAP_INDEX = 1

    private val mCache: LruCache<String, Array<Bitmap>>

    init {
        // Holds no more than MAX_ALBUM_ART_CACHE_SIZE bytes, bounded by maxmemory/4 and
        // Integer.MAX_VALUE:
        val maxSize = Math.min(MAX_ALBUM_ART_CACHE_SIZE,
                Math.min(Integer.MAX_VALUE.toLong(), Runtime.getRuntime().maxMemory() / 4).toInt())
        mCache = object : LruCache<String, Array<Bitmap>>(maxSize) {
            override fun sizeOf(key: String, value: Array<Bitmap>): Int {
                return value[BIG_BITMAP_INDEX].byteCount + value[ICON_BITMAP_INDEX].byteCount
            }
        }
    }

    fun getBigImage(artUrl: String): Bitmap? {
        val result = mCache[artUrl]
        return result?.get(BIG_BITMAP_INDEX)
    }

    fun getIconImage(artUrl: String): Bitmap? {
        val result = mCache.get(artUrl)
        return result?.get(ICON_BITMAP_INDEX)
    }

    fun fetch(artUrl: String, listener: FetchListener) {
        val bitmap = mCache[artUrl]

        bitmap?.let {
            LogHelper.d(TAG, "getOrFetch: album art is in cache, using it", artUrl)
            listener.onFetched(artUrl, bitmap[BIG_BITMAP_INDEX], bitmap[ICON_BITMAP_INDEX])
            return
        }
        LogHelper.d(TAG, "getOrFetch: starting asynctask to fetch ", artUrl)

        object : AsyncTask<Void, Void, Array<Bitmap>>() {
            override fun doInBackground(objects: Array<Void>): Array<Bitmap>? {
                val bitmaps: Array<Bitmap>
                try {
                    val bm = BitmapHelper.fetchAndRescaleBitmap(artUrl, MAX_ART_WIDTH, MAX_ART_HEIGHT)
                    val icon = BitmapHelper.scaleBitmap(bm, MAX_ART_WIDTH_ICON, MAX_ART_HEIGHT_ICON)
                    bitmaps = arrayOf(bm, icon)
                    mCache.put(artUrl, bitmaps)
                } catch (e: IOException) {
                    return null
                }

                LogHelper.d(TAG, "doInBackground: putting bitmap in cache. cache size=" + mCache.size())
                return bitmaps
            }

            override fun onPostExecute(bitmaps: Array<Bitmap>?) {
                if (bitmaps == null) {
                    listener.onError(artUrl, IllegalArgumentException("got null bitmaps"))
                } else {
                    listener.onFetched(artUrl, bitmaps[BIG_BITMAP_INDEX], bitmaps[ICON_BITMAP_INDEX])
                }
            }
        }.execute()

    }

    abstract class FetchListener {
        abstract fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap)
        fun onError(artUrl: String, e: Exception) {
            LogHelper.e(TAG, e, "AlbumArtFetchListener: error while downloading " + artUrl)
        }
    }
}