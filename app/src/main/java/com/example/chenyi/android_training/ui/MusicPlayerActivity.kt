package com.example.chenyi.android_training.ui

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaBrowserCompat
import android.util.Log
import com.example.chenyi.android_training.BuildConfig
import com.example.chenyi.android_training.R
import com.example.chenyi.android_training.util.LogHelper


class MusicPlayerActivity : BaseActivity(), MediaBrowserFragment.MediaFragmentListener {
    private var mVoiceSearchParams: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d(TAG, "Activity onCreate")

        setContentView(R.layout.activity_player)

        initializeToolbar()
        initializeFromParams(savedInstanceState, intent)

        // 只需要检查第一次是否需要一个完整的屏幕播放器:
        if (savedInstanceState == null) {
//            startFullScreenActivityIfNeeded(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        getMediaId()?.let { outState?.putString(SAVED_MEDIA_ID, it) }

        super.onSaveInstanceState(outState)
    }

    override fun onMediaItemSelected(item: MediaBrowserCompat.MediaItem) {
        LogHelper.d(TAG, "onMediaItemSelected, mediaId=${item.mediaId}")
        when {
            item.isPlayable -> supportMediaController.transportControls.playFromMediaId(item.mediaId, null)
            item.isBrowsable -> navigateToBrowser(item.mediaId)
            else -> LogHelper.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: ", "mediaId=${item.mediaId}")
        }
    }

    override fun setToolbarTitle(title: CharSequence?) {
        LogHelper.d(TAG, "Setting toolbar title to $title")
        setTitle(title ?: getString(R.string.app_name))
    }

    override fun onNewIntent(intent: Intent?) {
        LogHelper.d(TAG, "onNewIntent, intent=" + intent)
        initializeFromParams(null, intent)
        startFullScreenActivityIfNeeded(intent)
    }

    private fun startFullScreenActivityIfNeeded(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_FULLSCREEN, false) == true) {
//            val fullScreenIntent = Intent(this, FullScreenPlayerActivity::class.java)
//                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
//                    .putExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION,
//                            intent.getParcelableExtra<Parcelable>(EXTRA_CURRENT_MEDIA_DESCRIPTION))
//            startActivity(fullScreenIntent)
        }
    }

    protected fun initializeFromParams(savedInstanceState: Bundle?, intent: Intent?) {
        var mediaId: String? = null
        // 如果是通过语音搜索启动的 activity，保存它的 extras
        // 当 MediaSession 连接后，传人 extras 的信息
        if (intent?.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            mVoiceSearchParams = intent.extras
            LogHelper.d(TAG, "Starting from voice search query=${mVoiceSearchParams?.getString(SearchManager.QUERY)}")
        } else {
            // 使用它已经有保存的媒体ID
            mediaId = savedInstanceState?.getString(SAVED_MEDIA_ID)
        }
        navigateToBrowser(mediaId)
    }

    private fun navigateToBrowser(mediaId: String?) {
        LogHelper.d(TAG, "navigateToBrowser, mediaId=$mediaId")
        var fragment = getBrowseFragment()

        if (fragment == null || fragment.getMediaId() != mediaId) {
            fragment = MediaBrowserFragment()
            fragment.setMediaId(mediaId)
            val transaction = fragmentManager.beginTransaction()
            transaction.setCustomAnimations(
                    R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                    R.animator.slide_in_from_left, R.animator.slide_out_to_right)
            transaction.replace(R.id.container, fragment, FRAGMENT_TAG)
            // If this is not the top level media (root), we add it to the fragment back stack,
            // so that actionbar toggle and Back will work appropriately:
            mediaId?.let { transaction.addToBackStack(null) }
            transaction.commit()
        }
    }

    fun getMediaId(): String? {
        val fragment = getBrowseFragment() ?: return null
        return fragment.getMediaId()
    }

    private fun getBrowseFragment() =
            fragmentManager.findFragmentByTag(FRAGMENT_TAG) as MediaBrowserFragment?

    override fun onMediaControllerConnected() {
        // If there is a bootstrap parameter to start from a search query, we
        // send it to the media session and set it to null, so it won't play again
        // when the activity is stopped/started or recreated:
        val query = mVoiceSearchParams?.getString(SearchManager.QUERY)
        supportMediaController.transportControls
                .playFromSearch(query, mVoiceSearchParams)
        mVoiceSearchParams = null
        getBrowseFragment()?.onConnected()
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(MusicPlayerActivity::class.java)
        private val SAVED_MEDIA_ID = "com.example.chenyi.MEDIA_ID"
        private val FRAGMENT_TAG = "chenyi_list_container"

        val EXTRA_START_FULLSCREEN = "com.example.chenyi.EXTRA_START_FULLSCREEN"
        val EXTRA_CURRENT_MEDIA_DESCRIPTION = "com.example.chenyi.CURRENT_MEDIA_DESCRIPTION"
    }
}
