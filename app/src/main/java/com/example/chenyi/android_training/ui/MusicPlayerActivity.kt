package com.example.chenyi.android_training.ui

import android.content.Intent
import android.os.Bundle
import com.example.chenyi.android_training.R
import com.example.chenyi.android_training.util.LogHelper


class MusicPlayerActivity : BaseActivity() {

    private lateinit var mVoiceSearchParams: Bundle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogHelper.d(TAG, "Activity onCreate")

        setContentView(R.layout.activity_player)

        initializeToolbar()
        initializeFromParams(savedInstanceState, intent)

    }

    override fun onSaveInstanceState(outState: Bundle?) {

        super.onSaveInstanceState(outState)
    }

//    private fun startFullScreenActivityIfNeeded(intent: Intent?) {
//        if (intent?.getBooleanExtra(EXTRA_START_FULLSCREEN, false) == true) {
//            val fullScreenIntent = Intent(this, FullScreenPlayerActivity::class.java)
//                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
//                    .putExtra(EXTRA_CURRENT_MEDIA_DESCRIPTION,
//                            intent.getParcelableExtra<Parcelable>(EXTRA_CURRENT_MEDIA_DESCRIPTION))
//            startActivity(fullScreenIntent)
//        }
//    }

    protected fun initializeFromParams(savedInstanceState: Bundle?, intent: Intent?) {

    }

    companion object {
        private val TAG = LogHelper.makeLogTag(MusicPlayerActivity::class.java)

        private val SAVED_MEDIA_ID = "com.example.chenyi.MEDIA_ID"
        private val FRAGMENT_TAG = "chenyi_list_container"

        val EXTRA_START_FULLSCREEN = "com.example.chenyi.EXTRA_START_FULLSCREEN"
        val EXTRA_CURRENT_MEDIA_DESCRIPTION = "com.example.chenyi.CURRENT_MEDIA_DESCRIPTION"
    }
}
