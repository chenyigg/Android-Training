package com.example.chenyi.android_training.ui

import android.app.Activity
import android.app.Fragment
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.example.chenyi.android_training.R
import com.example.chenyi.android_training.util.LogHelper
import com.example.chenyi.android_training.util.MediaIDHelper
import com.example.chenyi.android_training.util.NetworkHelper
import org.jetbrains.anko.find
import java.util.*

/**
 * 显示媒体数据的 fragment
 * Created by chenyi on 2017/8/17.
 */
class MediaBrowserFragment : Fragment() {

    private val mBrowserAdapter by lazy { BrowseAdapter(activity) }
    private lateinit var mErrorView: View
    private lateinit var mErrorMessage: TextView

    private var mMediaFragmentListener: MediaFragmentListener? = null
    var mMediaId: String?
        get() = arguments?.getString(ARG_MEDIA_ID)
        set(value) {
            val args = Bundle(1)
            args.putString(MediaBrowserFragment.ARG_MEDIA_ID, value)
            arguments = args
        }

    private val mConnectivityChangeReceiver = object : BroadcastReceiver() {
        private var oldOnline = false
        override fun onReceive(context: Context, intent: Intent) {
            // 我们不关心网络的变化，而这个片段与媒体ID无关 (for example, while it is being initialized)
            if (mMediaId != null) {
                val isOnline = NetworkHelper.isOnline(context)
                if (isOnline != oldOnline) {
                    oldOnline = isOnline
                    checkForUserVisibleErrors(false)
                    if (isOnline) {
                        mBrowserAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    // Receive callbacks from the MediaController. Here we update our state such as which queue
    // is being shown, the current title and description and the PlaybackState.
    private val mMediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            super.onMetadataChanged(metadata)
            if (metadata == null) {
                return
            }
            LogHelper.d(TAG, "Received metadata change to media ${metadata.description.mediaId}")
            mBrowserAdapter.notifyDataSetChanged()
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat) {
            super.onPlaybackStateChanged(state)
            LogHelper.d(TAG, "Received state change: ", state)
            checkForUserVisibleErrors(false)
            mBrowserAdapter.notifyDataSetChanged()
        }
    }

    private val mSubscriptionCallback = object : MediaBrowserCompat.SubscriptionCallback() {
        override fun onChildrenLoaded(parentId: String, children: List<MediaBrowserCompat.MediaItem>) {
            try {
                LogHelper.d(TAG, "fragment onChildrenLoaded, parentId=" + parentId +
                        "  count=" + children.size)
                checkForUserVisibleErrors(children.isEmpty())
                mBrowserAdapter.clear()
                children.forEach { mBrowserAdapter.add(it) }
                mBrowserAdapter.notifyDataSetChanged()
            } catch (t: Throwable) {
                LogHelper.e(TAG, "Error on childrenloaded", t)
            }

        }

        override fun onError(id: String) {
            LogHelper.e(TAG, "browse fragment subscription onError, id=" + id)
            Toast.makeText(activity, R.string.error_loading_media, Toast.LENGTH_LONG).show()
            checkForUserVisibleErrors(true)
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        mMediaFragmentListener = context as MediaFragmentListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        LogHelper.d(TAG, "fragment.onCreateView")
        val view = inflater.inflate(R.layout.fragment_list, container, false)

        mErrorView = view.find(R.id.playback_error)
        mErrorMessage = mErrorView.find(R.id.error_message)

        val listView: ListView = view.find(R.id.list_view)
        listView.adapter = mBrowserAdapter
        listView.setOnItemClickListener { _, _, position, _ ->
            checkForUserVisibleErrors(false)
            val item = mBrowserAdapter.getItem(position)
            mMediaFragmentListener?.onMediaItemSelected(item)
        }

        return view
    }

    override fun onStart() {
        super.onStart()

        // 获取浏览信息以填充 listView
        val mediaBrowser = mMediaFragmentListener?.mediaBrowser

        LogHelper.d(TAG, "fragment.onStart, mediaId=$mMediaId",
                "  onConnected=${mediaBrowser?.isConnected}")

        if (mediaBrowser?.isConnected == true) onConnected()

        // 注册广播接收器来跟踪网络连接的变化
        activity.registerReceiver(mConnectivityChangeReceiver,
                IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }

    override fun onStop() {
        super.onStop()
        val mediaBrowser = mMediaFragmentListener?.mediaBrowser
        if (mediaBrowser?.isConnected == true && mMediaId != null) {
            mediaBrowser.unsubscribe(mMediaId!!)
        }
        val controller = (activity as FragmentActivity).supportMediaController
        controller?.unregisterCallback(mMediaControllerCallback)
        this.activity.unregisterReceiver(mConnectivityChangeReceiver)
    }

    override fun onDetach() {
        super.onDetach()
        mMediaFragmentListener = null
    }

    fun onConnected() {
        if (isDetached) return

        if (mMediaId == null) {
            mMediaId = mMediaFragmentListener?.mediaBrowser?.root
        }
        updateTitle()

        mMediaFragmentListener?.mediaBrowser?.unsubscribe(mMediaId!!)

        mMediaFragmentListener?.mediaBrowser?.subscribe(mMediaId!!, mSubscriptionCallback)

        // 添加 MediaController callback 更新媒体数据列表:
        val controller = (activity as FragmentActivity).supportMediaController
        controller?.registerCallback(mMediaControllerCallback)
    }

    private fun checkForUserVisibleErrors(forceError: Boolean) {
        var showError = forceError
        // 如果脱机，消息是关于缺乏网络连接:
        if (!NetworkHelper.isOnline(activity)) {
            mErrorMessage.setText(R.string.error_no_connection)
            showError = true
        } else {
            // 否则，如果状态是错误和元数据!= null，使用播放状态错误信息:
            val controller = (activity as FragmentActivity).supportMediaController
            if (controller.metadata != null
                    && controller.playbackState != null
                    && controller.playbackState.state == PlaybackStateCompat.STATE_ERROR
                    && controller.playbackState.errorMessage != null) {
                mErrorMessage.text = controller.playbackState.errorMessage
                showError = true
            } else if (forceError) {
                // 最后，如果调用者请求显示错误，则显示一个通用消息:
                mErrorMessage.setText(R.string.error_loading_media)
                showError = true
            }
        }
        mErrorView.visibility = if (showError) View.VISIBLE else View.GONE
        LogHelper.d(TAG, "checkForUserVisibleErrors. forceError=", forceError,
                " showError=", showError,
                " isOnline=", NetworkHelper.isOnline(activity))
    }

    private fun updateTitle() {
        if (MediaIDHelper.MEDIA_ID_ROOT == mMediaId) {
            mMediaFragmentListener?.setToolbarTitle(null)
            return
        }

        val mediaBrowser = mMediaFragmentListener?.mediaBrowser
        mediaBrowser?.getItem(mMediaId!!, object : MediaBrowserCompat.ItemCallback() {
            override fun onItemLoaded(item: MediaBrowserCompat.MediaItem) {
                mMediaFragmentListener?.setToolbarTitle(
                        item.description.title)
            }
        })
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(MediaBrowserFragment::class.java)
        private val ARG_MEDIA_ID = "media_id"
    }

    // 显示浏览的媒体项列表的适配器
    private class BrowseAdapter(context: Activity) : ArrayAdapter<MediaBrowserCompat.MediaItem>(context, R.layout.media_list_item, ArrayList()) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = getItem(position)
            return MediaItemViewHolder.setupListView(context as Activity, convertView, parent,
                    item)
        }
    }

    interface MediaFragmentListener : MediaBrowserProvider {
        fun onMediaItemSelected(item: MediaBrowserCompat.MediaItem)
        fun setToolbarTitle(title: CharSequence?)
    }
}