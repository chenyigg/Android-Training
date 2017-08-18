package com.example.chenyi.android_training.model

import android.support.v4.media.MediaMetadataCompat
import com.example.chenyi.android_training.util.LogHelper
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.ArrayList

/**
 * 从服务器获取音乐信息的工具类
 * Created by chenyi on 17-8-9.
 */
class RemoteJSONSource : MusicProviderSource {

    override fun iterator(): Iterator<MediaMetadataCompat> {
        try {
            val slashPos = CATALOG_URL.lastIndexOf('/')
            val path = CATALOG_URL.substring(0, slashPos + 1)
            val jsonObj = fetchJSONFromUrl(CATALOG_URL)
            val tracks = ArrayList<MediaMetadataCompat>()

            val jsonTracks = jsonObj?.getJSONArray(JSON_MUSIC)

            jsonTracks?.let {
                (0 until jsonTracks.length()).mapTo(tracks) { buildFromJSON(jsonTracks.getJSONObject(it), path) }
            }

            return tracks.iterator()
        } catch (e: JSONException) {
            LogHelper.e(TAG, e, "Could not retrieve music list")
            throw RuntimeException("Could not retrieve music list", e)
        }

    }

    @Throws(JSONException::class)
    private fun buildFromJSON(json: JSONObject, basePath: String): MediaMetadataCompat {
        val title = json.getString(JSON_TITLE)
        val album = json.getString(JSON_ALBUM)
        val artist = json.getString(JSON_ARTIST)
        val genre = json.getString(JSON_GENRE)
        var source = json.getString(JSON_SOURCE)
        var iconUrl = json.getString(JSON_IMAGE)
        val trackNumber = json.getInt(JSON_TRACK_NUMBER)
        val totalTrackCount = json.getInt(JSON_TOTAL_TRACK_COUNT)
        val duration = json.getInt(JSON_DURATION) * 1000 // ms

        LogHelper.d(TAG, "Found music track: ", json)

        // Media is stored relative to JSON file
        if (!source.startsWith("http")) {
            source = basePath + source
        }
        if (!iconUrl.startsWith("http")) {
            iconUrl = basePath + iconUrl
        }
        // Since we don't have a unique ID in the server, we fake one using the hashcode of
        // the music source. In a real world app, this could come from the server.
        val id = source.hashCode().toString()

        // Adding the music source to the MediaMetadata (and consequently using it in the
        // mediaSession.setMetadata) is not a good idea for a real world music app, because
        // the session metadata can be accessed by notification listeners. This is done in this
        // sample for convenience only.

        // 添加音乐数据到媒体元数据中
        return MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)
                .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, source)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration.toLong())
                .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, iconUrl)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber.toLong())
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, totalTrackCount.toLong())
                .build()
    }

    /**
     * 从服务器下载一个JSON文件,解析内容并返回的JSON Object。
     */
    @Throws(JSONException::class)
    private fun fetchJSONFromUrl(urlString: String): JSONObject? {
        var reader: BufferedReader? = null
        try {
            val urlConnection = URL(urlString).openConnection()
            reader = BufferedReader(InputStreamReader(
                    urlConnection.getInputStream(), "iso-8859-1"))
            val sb = StringBuilder()
            var line: String? = null
            while ({ line = reader?.readLine(); line != null}() ) {
                sb.append(line)
            }
            return JSONObject(sb.toString())
        } catch (e: JSONException) {
            throw e
        } catch (e: Exception) {
            LogHelper.e(TAG, "解析媒体数据列表失败", e)
            return null
        } finally {
            try { reader?.close() } catch (e: IOException) { /* ignore */ }
        }
    }

    private companion object {
        val TAG = LogHelper.makeLogTag(RemoteJSONSource::class.java)

        val CATALOG_URL = "http://storage.googleapis.com/automotive-media/music.json"

        val JSON_MUSIC = "music"
        val JSON_TITLE = "title"
        val JSON_ALBUM = "album"
        val JSON_ARTIST = "artist"
        val JSON_GENRE = "genre"
        val JSON_SOURCE = "source"
        val JSON_IMAGE = "image"
        val JSON_TRACK_NUMBER = "trackNumber"
        val JSON_TOTAL_TRACK_COUNT = "totalTrackCount"
        val JSON_DURATION = "duration"
    }
}
