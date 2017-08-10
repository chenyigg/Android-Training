package com.example.chenyi.android_training.model

import android.support.v4.media.MediaMetadataCompat

/**
 * Created by chenyi on 17-8-9.
 */
interface MusicProviderSource {

    operator fun iterator(): Iterator<MediaMetadataCompat>

    companion object {
        val CUSTOM_METADATA_TRACK_SOURCE = "__SOURCE__"
    }
}