package com.example.chenyi.android_training

import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.os.Bundle
import android.os.IBinder
import android.service.media.MediaBrowserService
import com.example.chenyi.android_training.util.LogHelper

/**
 * Created by chenyi on 17-8-8.
 */
class MusicService : MediaBrowserService() {

    val mSession: MediaSession by lazy { MediaSession(this, TAG) }

    override fun onCreate() {
        super.onCreate()

        mSession.setCallback(MediaSessionCallback())
        mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                            MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        sessionToken = mSession.sessionToken
    }

    override fun onLoadChildren(p0: String?, p1: Result<MutableList<MediaBrowser.MediaItem>>?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onGetRoot(p0: String?, p1: Int, p2: Bundle?): BrowserRoot {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    companion object {
        val TAG: String = LogHelper.makeLogTag(MusicService::class.java)
    }

    class MediaSessionCallback : MediaSession.Callback()
}