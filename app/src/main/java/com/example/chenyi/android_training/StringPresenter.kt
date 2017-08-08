package com.example.chenyi.android_training

import android.support.v17.leanback.widget.Presenter
import android.view.ViewGroup
import android.widget.TextView
import com.example.chenyi.android_training.util.LogHelper

/**
 * Created by chenyi on 17-8-8.
 */
class StringPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val text: TextView = TextView(parent.context)
        text.isFocusable = true
        text.isFocusableInTouchMode = true
        text.background = parent.context.resources.getDrawable(R.mipmap.ic_launcher)
        return Presenter.ViewHolder(text)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder?, item: Any?) {
        (viewHolder?.view as TextView).text = item.toString()
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder?) {
        TODO()
    }

    companion object {
        private val TAG = LogHelper.makeLogTag(StringPresenter::class.java)
    }
}