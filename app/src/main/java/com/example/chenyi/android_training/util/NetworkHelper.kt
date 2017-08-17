package com.example.chenyi.android_training.util

import android.content.Context
import android.net.ConnectivityManager

/**
 * 通用可重用网络方法
 * Created by chenyi on 17-8-16.
 */
object NetworkHelper {

    /**
     * @param context 用于检查网络连接
     * @return true if connected, false otherwise.
     */
    fun isOnline(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }
}