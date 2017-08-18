package com.example.chenyi.android_training.ui

import android.app.Fragment
import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.example.chenyi.android_training.AlbumArtCache
import com.example.chenyi.android_training.R
import com.example.chenyi.android_training.util.LogHelper
import org.jetbrains.anko.find
import org.jetbrains.anko.longToast

class PlaybackControlsFragment : Fragment() {

    private lateinit var mPlayPause: ImageButton
    private lateinit var mTitle: TextView
    private lateinit var mSubtitle: TextView
    private lateinit var mExtraInfo: TextView
    private lateinit var mAlbumArt: ImageView
    private lateinit var mArtUrl: String

    private val mCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            LogHelper.d(TAG, "Received playback state change to state ", state.state)
            this@PlaybackControlsFragment.onPlaybackStateChanged(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if (metadata == null) {
                return
            }
            LogHelper.d(TAG, "Received metadata state change to mediaId=${metadata.description.mediaId}",
                    " song=${metadata.description.title}")
            this@PlaybackControlsFragment.onMetadataChanged(metadata)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_playback_controls, container, false)

        mPlayPause = view.find(R.id.play_pause)
        mPlayPause.isEnabled = true
        mPlayPause.setOnClickListener {
            val controller = (activity as FragmentActivity).supportMediaController
            val state = controller.playbackState?.state ?: PlaybackStateCompat.STATE_NONE
            LogHelper.d(TAG, "Play Button pressed, in state " + state)
            when (state) {
                PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.STATE_STOPPED,
                PlaybackStateCompat.STATE_NONE -> playMedia()
                PlaybackStateCompat.STATE_PLAYING,
                PlaybackStateCompat.STATE_BUFFERING,
                PlaybackStateCompat.STATE_CONNECTING -> pauseMedia()
            }
        }

        mTitle = view.find(R.id.title)
        mSubtitle = view.find(R.id.artist)
        mExtraInfo = view.find(R.id.extra_info)
        mAlbumArt = view.find(R.id.album_art)

        view.setOnClickListener {
            //            val intent = Intent(activity, FullScreenPlayerActivity::class.java)
//            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
//            val controller = (activity as FragmentActivity)
//                    .supportMediaController
//            val metadata = controller.metadata
//            if (metadata != null) {
//                intent.putExtra(MusicPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION,
//                        metadata.description)
//            }
//            startActivity(intent)
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        LogHelper.d(TAG, "fragment.onStart")

        (activity as FragmentActivity).supportMediaController?.let { onConnected() }
    }

    override fun onStop() {
        super.onStop()
        LogHelper.d(TAG, "fragment.onStop")
        (activity as FragmentActivity).supportMediaController?.unregisterCallback(mCallback)
    }

    fun onConnected() {
        val controller = (activity as FragmentActivity).supportMediaController
        LogHelper.d(TAG, "onConnected, mediaController==null? ", controller == null)
        controller?.let {
            onMetadataChanged(controller.metadata)
            onPlaybackStateChanged(controller.playbackState)
            controller.registerCallback(mCallback)
        }
    }

    private fun onMetadataChanged(metadata: MediaMetadataCompat?) {
        LogHelper.d(TAG, "onMetadataChanged $metadata")
        if (activity == null) {
            LogHelper.w(TAG, "onMetadataChanged called when getActivity null," +
                    "this should not happen if the callback was properly unregistered. Ignoring.")
            return
        }
        if (metadata == null) {
            return
        }

        mTitle.text = metadata.description.title
        mSubtitle.text = metadata.description.subtitle
        val artUrl = metadata.description.iconUri.toString()

        if (artUrl != mArtUrl) {
            mArtUrl = artUrl
            val art = metadata.description?.iconBitmap ?: AlbumArtCache.getIconImage(mArtUrl)
            if (art != null) {
                mAlbumArt.setImageBitmap(art)
            } else {
                AlbumArtCache.fetch(artUrl, object : AlbumArtCache.FetchListener() {
                    override fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap) {
                        LogHelper.d(TAG, "album art icon of w=${iconImage.width}", " h=${iconImage.height}")
                        if (isAdded) {
                            mAlbumArt.setImageBitmap(iconImage)
                        }
                    }
                })
            }
        }
    }

    fun setExtraInfo(extraInfo: String?) {
        if (extraInfo == null) {
            mExtraInfo.visibility = View.GONE
        } else {
            mExtraInfo.text = extraInfo
            mExtraInfo.visibility = View.VISIBLE
        }
    }

    private fun onPlaybackStateChanged(state: PlaybackStateCompat) {
        LogHelper.d(TAG, "onPlaybackStateChanged ", state)
        if (activity == null) {
            LogHelper.w(TAG, "onPlaybackStateChanged called when getActivity null," +
                    "this should not happen if the callback was properly unregistered. Ignoring.")
            return
        }
        var enablePlay = false
        when (state.state) {
            PlaybackStateCompat.STATE_PAUSED,
            PlaybackStateCompat.STATE_STOPPED -> enablePlay = true
            PlaybackStateCompat.STATE_ERROR -> {
                LogHelper.e(TAG, "error playbackstate: ", state.errorMessage)
                longToast(state.errorMessage)
            }
        }

        if (enablePlay) {
            mPlayPause.setImageDrawable(
                    ContextCompat.getDrawable(activity, R.mipmap.ic_play_arrow_black_36dp))
        } else {
            mPlayPause.setImageDrawable(
                    ContextCompat.getDrawable(activity, R.mipmap.ic_pause_black_36dp))
        }
    }

    private fun playMedia() {
        (activity as FragmentActivity).supportMediaController?.transportControls?.play()
    }

    private fun pauseMedia() {
        (activity as FragmentActivity).supportMediaController?.transportControls?.pause()
    }

    companion object {
        val TAG = LogHelper.makeLogTag(PlaybackControlsFragment::class.java)
    }
}
