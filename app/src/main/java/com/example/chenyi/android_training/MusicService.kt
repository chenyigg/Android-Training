package com.example.chenyi.android_training

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserServiceCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v7.media.MediaRouter
import com.example.chenyi.android_training.model.MusicProvider
import com.example.chenyi.android_training.playback.LocalPlayback
import com.example.chenyi.android_training.playback.PlaybackManager
import com.example.chenyi.android_training.playback.QueueManager
import com.example.chenyi.android_training.util.LogHelper
import com.example.chenyi.android_training.util.MediaIDHelper
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import java.lang.ref.WeakReference

/**
 * 这个类通过服务提供了一个媒体浏览器。它通过 Get Root 和 Load Children 方法将媒体库暴露给一个浏览客户端。
 * 它还创建了一个媒体会话，并通过其媒体会话公开它。Token，它允许客户端创建一个媒体控制器，
 * 它可以远程连接和发送控制命令到媒体会话。这对于需要与您的媒体会话(如Android Auto)交互的用户界面非常有用。
 * 你也可以从你的应用程序的UI中使用相同的服务，它给用户提供了一个无缝的回放体验。
 * Created by chenyi on 17-8-16.
 */
class MusicService : MediaBrowserServiceCompat(), PlaybackManager.PlaybackServiceCallback {

    private val mMusicProvider by lazy { MusicProvider() }
    private lateinit var mPlaybackManager: PlaybackManager

    private val mSession by lazy { MediaSessionCompat(this, "MusicService") }
//    private var mMediaNotificationManager: MediaNotificationManager? = null
//    private var mSessionExtras: Bundle? = null
    private val mDelayedStopHandler = DelayedStopHandler(this)
//    private var mMediaRouter: MediaRouter? = null
//    private var mPackageValidator: PackageValidator? = null
//    private var mCastSessionManager: SessionManager? = null
//    private var mCastSessionManagerListener: SessionManagerListener<CastSession>? = null

//    private var mIsConnectedToCar: Boolean = false
//    private var mCarConnectionReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        LogHelper.d(TAG, "onCreate")

        // 为了使应用程序更有响应性，现在可以获取和缓存目录信息。
        // 这有助于提高方法的响应时间
        mMusicProvider.retrieveMediaAsync{}

        val queueManager = QueueManager(mMusicProvider, resources,
                object : QueueManager.MetadataUpdateListener {
                    override fun onMetadataChanged(metadata: MediaMetadataCompat) {
                        mSession.setMetadata(metadata)
                    }

                    override fun onMetadataRetrieveError() {
                        mPlaybackManager.updatePlaybackState(
                                getString(R.string.error_no_metadata))
                    }

                    override fun onCurrentQueueIndexUpdated(queueIndex: Int) {
                        mPlaybackManager.handlePlayRequest()
                    }

                    override fun onQueueUpdated(title: String,
                                                newQueue: List<MediaSessionCompat.QueueItem>) {
                        mSession.setQueue(newQueue)
                        mSession.setQueueTitle(title)
                    }
                })

        val playback = LocalPlayback(mMusicProvider, this)
        mPlaybackManager = PlaybackManager(resources, mMusicProvider, queueManager, playback, this)

        setSessionToken(mSession.sessionToken)
        mSession.setCallback(mPlaybackManager.mediaSessionCallback)
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

//        val context = applicationContext
//        val intent = Intent(context, NowPlayingActivity::class.java)
//        val pi = PendingIntent.getActivity(context, 99 /*request code*/,
//                intent, PendingIntent.FLAG_UPDATE_CURRENT)
//        mSession.setSessionActivity(pi)

        mPlaybackManager.updatePlaybackState(null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (ACTION_CMD == intent.action && CMD_PAUSE == intent.getStringExtra(CMD_NAME)) {
                mPlaybackManager.handlePauseRequest()
            } else {
                // 将 intent 当做 media button 事件，发送给 MediaButtonReceiver
                MediaButtonReceiver.handleIntent(mSession, intent)
            }

        }

        // 重置 delay handler，如果没有音乐播放将发送信息停止服务
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY)
        return Service.START_STICKY
    }

    override fun onDestroy() {
        LogHelper.d(TAG, "onDestroy")
        // 服务被杀死，确保已经释放所有资源
        mPlaybackManager.handleStopRequest(null)

        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mSession.release()
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        LogHelper.d(TAG, "OnLoadChildren: parentMediaId=", parentId)
        if (MediaIDHelper.MEDIA_ID_EMPTY_ROOT == parentId) {
            result.sendResult(ArrayList())
        } else if (mMusicProvider.isInitialized()) {
            // 如果音乐库已经准备好，请立即返回
            result.sendResult(mMusicProvider.getChildren(parentId, resources))
        } else {
            // 否则，只在检索音乐库时返回结果
            result.detach()
            mMusicProvider.retrieveMediaAsync {
                result.sendResult(mMusicProvider.getChildren(parentId, resources))
            }
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        LogHelper.d(TAG, "OnGetRoot: clientPackageName=$clientPackageName" ,
                "; clientUid=$clientUid ; rootHints=$rootHints")
        //为了确保你不允许任意的应用程序浏览你的应用程序的内容，你需要检查来源
//        if (!mPackageValidator.isCallerAllowed(this, clientPackageName, clientUid)) {
//            // If the request comes from an untrusted package, return an empty browser root.
//            // If you return null, then the media browser will not be able to connect and
//            // no further calls will be made to other media browsing methods.
//            LogHelper.i(TAG, "OnGetRoot: Browsing NOT ALLOWED for unknown caller. "
//                    + "Returning empty browser root so all apps can use MediaController."
//                    + clientPackageName)
//            return MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_EMPTY_ROOT, null)
//        }
        return MediaBrowserServiceCompat.BrowserRoot(MediaIDHelper.MEDIA_ID_ROOT, null)
    }

    /**
     * 当音乐即将播放时，回调
     */
    override fun onPlaybackStart() {
        mSession.isActive = true

        mDelayedStopHandler.removeCallbacksAndMessages(null)
        // 服务需要继续运行，即使在绑定客户端(通常是一个 MediaController )断开连接之后， 音乐回放也会停止。
        // 调用 start Service(Intent) 将保持服务运行，直到它被显式地杀死。
        startService(Intent(applicationContext, MusicService::class.java))
    }

    override fun onNotificationRequired() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    /**
     * 当音乐停止播放时，回调
     */
    override fun onPlaybackStop() {
        mSession.isActive = false
        // 重置 delay handler，因此在停止延迟后将再次执行，有可能停止服务。
        mDelayedStopHandler.removeCallbacksAndMessages(null)
        mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY.toLong())
        stopForeground(true)
    }

    override fun onPlaybackStateUpdated(newState: PlaybackStateCompat) {
        mSession.setPlaybackState(newState)
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(MusicService::class.java)

        // 在包含当前连接到的 Cast 设备名称的媒体会话上的额外工作
        val EXTRA_CONNECTED_CAST = "com.example.android.uamp.CAST_NAME"
        // 表明一个将会被执行的命令
        val ACTION_CMD = "com.example.android.uamp.ACTION_CMD"
        // 将会被执行的命令的 key
        val CMD_NAME = "CMD_NAME"
        // 将会被执行的命令的 key 值
        val CMD_PAUSE = "CMD_PAUSE"
        // 表明音乐播放从投影播放切换到本地播放的 key 值
        val CMD_STOP_CASTING = "CMD_STOP_CASTING"
        // 通过使用 handler 来延迟自停。
        private val STOP_DELAY = 30000L
    }

    /**
     * 一个简单的 handler 在 playback 停止播放时停止服务
     */
    private class DelayedStopHandler internal constructor(service: MusicService) : Handler() {
        private val mWeakReference = WeakReference(service)

        override fun handleMessage(msg: Message) {
            val service = mWeakReference.get()

            if (service?.mPlaybackManager?.playback?.isPlaying ?: false) {
                LogHelper.d(TAG, "Ignoring delayed stop since the media player is in use.")
                return
            }
            LogHelper.d(TAG, "Stopping service with delay handler.")
            service?.stopSelf()
        }
    }

}