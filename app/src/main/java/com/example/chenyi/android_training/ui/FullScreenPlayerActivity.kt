package com.example.chenyi.android_training.ui

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.RemoteException
import android.os.SystemClock
import android.support.v4.content.ContextCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.*
import android.text.format.DateUtils
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import com.example.chenyi.android_training.AlbumArtCache
import com.example.chenyi.android_training.MusicService

import com.example.chenyi.android_training.R
import com.example.chenyi.android_training.util.LogHelper
import org.jetbrains.anko.find
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * 一个完整的屏幕播放器，显示当前播放音乐的背景图像，描绘了专辑。该活动还具有寻找/暂停/播放音频的控件。
 */
class FullScreenPlayerActivity : ActionBarActivity() {

    private lateinit var mSkipPrev: ImageView
    private lateinit var mSkipNext: ImageView
    private lateinit var mPlayPause: ImageView
    private lateinit var mStart: TextView
    private lateinit var mEnd: TextView
    private lateinit var mSeekbar: SeekBar
    private lateinit var mLine1: TextView
    private lateinit var mLine2: TextView
    private lateinit var mLine3: TextView
    private lateinit var mLoading: ProgressBar
    private lateinit var mControllers: View
    private lateinit var mPauseDrawable: Drawable
    private lateinit var mPlayDrawable: Drawable
    private lateinit var mBackgroundImage: ImageView

    private var mCurrentArtUrl: String? = null
    private val mHandler = Handler()
    private lateinit var mMediaBrowser: MediaBrowserCompat

    private val mUpdateProgressTask = Runnable { updateProgress() }

    private val mExecutorService = Executors.newSingleThreadScheduledExecutor()

    private var mScheduleFuture: ScheduledFuture<*>? = null
    private lateinit var mLastPlaybackState: PlaybackStateCompat

    private val mCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            LogHelper.d(TAG, "onPlaybackstate changed", state)
            updatePlaybackState(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            if (metadata != null) {
                updateMediaDescription(metadata.description)
                updateDuration(metadata)
            }
        }
    }

    private val mConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            LogHelper.d(TAG, "onConnected")
            try {
                connectToSession(mMediaBrowser.sessionToken)
            } catch (e: RemoteException) {
                LogHelper.e(TAG, e, "could not connect media controller")
            }

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_player)
        initializeToolbar()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        mBackgroundImage = find(R.id.background_image)
        mPauseDrawable = ContextCompat.getDrawable(this, R.mipmap.uamp_ic_pause_white_48dp)
        mPlayDrawable = ContextCompat.getDrawable(this, R.mipmap.uamp_ic_play_arrow_white_48dp)
        mPlayPause = find(R.id.play_pause)
        mSkipNext = find(R.id.next)
        mSkipPrev = find(R.id.prev)
        mStart = find(R.id.startText)
        mEnd = find(R.id.endText)
        mSeekbar = find(R.id.seekBar1)
        mLine1 = find(R.id.line1)
        mLine2 = find(R.id.line2)
        mLine3 = find(R.id.line3)
        mLoading = find(R.id.progressBar1)
        mControllers = find(R.id.controllers)

        mSkipNext.setOnClickListener {
            supportMediaController.transportControls.skipToNext()
        }

        mSkipPrev.setOnClickListener {
            supportMediaController.transportControls.skipToPrevious()
        }

        mPlayPause.setOnClickListener {
            val controls = supportMediaController.transportControls
            when (supportMediaController.playbackState.state) {
                STATE_PLAYING, STATE_BUFFERING -> {
                    controls.pause()
                    stopSeekbarUpdate()
                }
                STATE_PAUSED, STATE_STOPPED -> {
                    controls.play()
                    scheduleSeekbarUpdate()
                }
                else -> LogHelper.e(TAG, "onClick with state ",
                        supportMediaController.playbackState.state)
            }
        }

        mSeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                mStart.text = DateUtils.formatElapsedTime(progress / 1000L)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (seekBar != null) {
                    supportMediaController.transportControls.seekTo(seekBar.progress.toLong())
                }
                scheduleSeekbarUpdate()
            }

        })

        savedInstanceState ?: updateFromParams(intent)

        mMediaBrowser = MediaBrowserCompat(this,
                ComponentName(this, MusicService::class.java), mConnectionCallback, null)

    }

    override fun initializeToolbar() {
        mToolbarInitialized = true
    }

    @Throws(RemoteException::class)
    private fun connectToSession(token: MediaSessionCompat.Token) {
        val mediaController = MediaControllerCompat(this, token)
        if (mediaController.metadata == null) {
            finish()
            return
        }
        supportMediaController = mediaController
        mediaController.registerCallback(mCallback)
        val state = mediaController.playbackState
        updatePlaybackState(state)
        val metadata = mediaController.metadata
        if (metadata != null) {
            updateMediaDescription(metadata.description)
            updateDuration(metadata)
        }
        updateProgress()
        if (state != null && (state.state == PlaybackStateCompat.STATE_PLAYING || state.state == PlaybackStateCompat.STATE_BUFFERING)) {
            scheduleSeekbarUpdate()
        }
    }

    private fun updateFromParams(intent: Intent?) {
        if (intent != null) {
            val description: MediaDescriptionCompat =
                    intent.getParcelableExtra(MusicPlayerActivity.EXTRA_CURRENT_MEDIA_DESCRIPTION)
            updateMediaDescription(description)
        }
    }

    private fun scheduleSeekbarUpdate() {
        stopSeekbarUpdate()
        if (!mExecutorService.isShutdown) {
            mScheduleFuture = mExecutorService.scheduleAtFixedRate(
                    { mHandler.post(mUpdateProgressTask) }, PROGRESS_UPDATE_INITIAL_INTERVAL,
                    PROGRESS_UPDATE_INTERNAL, TimeUnit.MILLISECONDS)
        }
    }

    private fun stopSeekbarUpdate() {
        mScheduleFuture?.cancel(false)
    }

    override fun onStart() {
        super.onStart()
        mMediaBrowser.connect()

    }

    override fun onStop() {
        super.onStop()

        mMediaBrowser.disconnect()

        supportMediaController?.unregisterCallback(mCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSeekbarUpdate()
        mExecutorService.shutdown()
    }

    private fun fetchImageAsync(description: MediaDescriptionCompat) {
        if (description.iconUri == null) {
            return
        }
        val artUrl = description.iconUri!!.toString()
        mCurrentArtUrl = artUrl

        val art = AlbumArtCache.getBigImage(artUrl) ?: description.iconBitmap

        if (art != null) {
            // if we have the art cached or from the MediaDescription, use it:
            mBackgroundImage.setImageBitmap(art)
        } else {
            // otherwise, fetch a high res version and update:
            AlbumArtCache.fetch(artUrl, object : AlbumArtCache.FetchListener() {
                override fun onFetched(artUrl: String, bigImage: Bitmap, iconImage: Bitmap) {
                    // sanity check, in case a new fetch request has been done while
                    // the previous hasn't yet returned:
                    if (artUrl == mCurrentArtUrl) {
                        mBackgroundImage.setImageBitmap(bigImage)
                    }
                }
            })
        }
    }

    private fun updateMediaDescription(description: MediaDescriptionCompat?) {
        if (description == null) {
            return
        }
        LogHelper.d(TAG, "updateMediaDescription called ")
        mLine1.text = description.title
        mLine2.text = description.subtitle
        fetchImageAsync(description)
    }

    private fun updateDuration(metadata: MediaMetadataCompat?) {
        if (metadata == null) {
            return
        }
        LogHelper.d(TAG, "updateDuration called ")
        val duration = metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
        mSeekbar.max = duration.toInt()
        mEnd.text = DateUtils.formatElapsedTime((duration / 1000))
    }

    private fun updatePlaybackState(state: PlaybackStateCompat?) {
        if (state == null) {
            return
        }
        mLastPlaybackState = state
        if (supportMediaController != null && supportMediaController.extras != null) {
            val castName = supportMediaController
                    .extras.getString(MusicService.EXTRA_CONNECTED_CAST)
            val line3Text = if (castName == null)
                ""
            else
                resources.getString(R.string.casting_to_device, castName)
            mLine3.text = line3Text
        }

        when (state.state) {
            PlaybackStateCompat.STATE_PLAYING -> {
                mLoading.visibility = View.INVISIBLE
                mPlayPause.visibility = View.VISIBLE
                mPlayPause.setImageDrawable(mPauseDrawable)
                mControllers.visibility = View.VISIBLE
                scheduleSeekbarUpdate()
            }
            PlaybackStateCompat.STATE_PAUSED -> {
                mControllers.visibility = View.VISIBLE
                mLoading.visibility = View.INVISIBLE
                mPlayPause.visibility = View.VISIBLE
                mPlayPause.setImageDrawable(mPlayDrawable)
                stopSeekbarUpdate()
            }
            PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.STATE_STOPPED -> {
                mLoading.visibility = View.INVISIBLE
                mPlayPause.visibility = View.VISIBLE
                mPlayPause.setImageDrawable(mPlayDrawable)
                stopSeekbarUpdate()
            }
            PlaybackStateCompat.STATE_BUFFERING -> {
                mPlayPause.visibility = View.INVISIBLE
                mLoading.visibility = View.VISIBLE
                mLine3.setText(R.string.loading)
                stopSeekbarUpdate()
            }
            else -> LogHelper.d(TAG, "Unhandled state ", state.state)
        }

        mSkipNext.visibility = if (state.actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT == 0L)
            View.INVISIBLE
        else
            View.VISIBLE
        mSkipPrev.visibility = if (state.actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS == 0L)
            View.INVISIBLE
        else
            View.VISIBLE
    }

    private fun updateProgress() {
        var currentPosition = mLastPlaybackState.position
        if (mLastPlaybackState.state == PlaybackStateCompat.STATE_PLAYING) {
            // Calculate the elapsed time between the last position update and now and unless
            // paused, we can assume (delta * speed) + current position is approximately the
            // latest position. This ensure that we do not repeatedly call the getPlaybackState()
            // on MediaControllerCompat.
            val timeDelta = SystemClock.elapsedRealtime() - mLastPlaybackState.lastPositionUpdateTime
            currentPosition += (timeDelta * mLastPlaybackState.playbackSpeed).toLong()
        }
        mSeekbar.progress = currentPosition.toInt()
    }

    companion object {
        val TAG = LogHelper.makeLogTag(FullScreenPlayerActivity::class.java)

        val PROGRESS_UPDATE_INTERNAL = 1000L
        val PROGRESS_UPDATE_INITIAL_INTERVAL = 100L
    }
}
