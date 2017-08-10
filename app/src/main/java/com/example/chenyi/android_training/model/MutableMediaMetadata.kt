package com.example.chenyi.android_training.model

import android.support.v4.media.MediaMetadataCompat
import android.text.TextUtils

/**
 * Holder 类，封装了一个媒体元数据，允许实际的元数据被修改而无需重建元数据的集合
 * Created by chenyi on 17-8-9.
 */
class MutableMediaMetadata(val trackId: String, var metadata: MediaMetadataCompat) {

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || other.javaClass != MutableMediaMetadata::class.java) {
            return false
        }

        val that = other as MutableMediaMetadata

        return TextUtils.equals(trackId, that.trackId)
    }

    override fun hashCode(): Int {
        return trackId.hashCode()
    }
}