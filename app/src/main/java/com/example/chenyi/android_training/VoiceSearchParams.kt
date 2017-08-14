/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.chenyi.android_training

import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils

/**
 * For more information about voice search parameters,
 * check https://developer.android.com/guide/components/intents-common.html#PlaySearch
 */
class VoiceSearchParams
/**
 * Creates a simple object describing the search criteria from the query and extras.
 * @param query the query parameter from a voice search
 * *
 * @param extras the extras parameter from a voice search
 */
(val query: String, extras: Bundle?) {
    var isAny: Boolean = false
    var isUnstructured: Boolean = false
    var isGenreFocus: Boolean = false
    var isArtistFocus: Boolean = false
    var isAlbumFocus: Boolean = false
    var isSongFocus: Boolean = false
    lateinit var genre: String
    lateinit var artist: String
    lateinit var album: String
    lateinit var song: String

    init {

        if (TextUtils.isEmpty(query)) {
            // A generic search like "Play music" sends an empty query
            isAny = true
        } else {
            if (extras == null) {
                isUnstructured = true
            } else {
                val genreKey: String
                if (Build.VERSION.SDK_INT >= 21) {
                    genreKey = MediaStore.EXTRA_MEDIA_GENRE
                } else {
                    genreKey = "android.intent.extra.genre"
                }

                when (extras.getString(MediaStore.EXTRA_MEDIA_FOCUS)) {
                    MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> {
                        // for a Genre focused search, only genre is set:
                        isGenreFocus = true
                        genre = extras.getString(genreKey)
                        if (genre.none()) {
                            // Because of a bug on the platform, genre is only sent as a query, not as
                            // the semantic-aware extras. This check makes it future-proof when the
                            // bug is fixed.
                            genre = query
                        }
                    }
                    MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> {
                        // for an Artist focused search, both artist and genre are set:
                        isArtistFocus = true
                        genre = extras.getString(genreKey)
                        artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                    }
                    MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> {
                        // for an Album focused search, album, artist and genre are set:
                        isAlbumFocus = true
                        album = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM)
                        genre = extras.getString(genreKey)
                        artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                    }
                    MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> {
                        // for a Song focused search, title, album, artist and genre are set:
                        isSongFocus = true
                        song = extras.getString(MediaStore.EXTRA_MEDIA_TITLE)
                        album = extras.getString(MediaStore.EXTRA_MEDIA_ALBUM)
                        genre = extras.getString(genreKey)
                        artist = extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)
                    }
                    else -> {
                        // If we don't know the focus, we treat it is an unstructured query:
                        isUnstructured = true
                    }
                }
            }
        }
    }

    override fun toString(): String {
        return "query=" + query + " isAny=" + isAny +
                " isUnstructured=" + isUnstructured +
                " isGenreFocus=" + isGenreFocus +
                " isArtistFocus=" + isArtistFocus +
                " isAlbumFocus=" + isAlbumFocus +
                " isSongFocus=" + isSongFocus +
                " genre=" + genre +
                " artist=" + artist +
                " album=" + album +
                " song=" + song
    }

}
