package com.example.chenyi.android_training.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Created by chenyi on 17-8-15.
 */
object BitmapHelper {
    private val TAG = LogHelper.makeLogTag(BitmapHelper::class.java)

    // 最大的读取限制，允许对输入流进行标记和重置
    private val MAX_READ_LIMIT_PER_IMG = 1024 * 1024

    fun scaleBitmap(src: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val scaleFactor = Math.min(
                maxWidth.toDouble() / src.width, maxHeight.toDouble() / src.height)
        return Bitmap.createScaledBitmap(src,
                (src.width * scaleFactor).toInt(), (src.height * scaleFactor).toInt(), false)
    }

    fun scaleBitmap(scaleFactor: Int, inputStream: InputStream): Bitmap {
        // 获取 bitmap 的尺寸
        val bmOptions = BitmapFactory.Options()

        // 解码一个图像文件按 bitmap 的大小填充到 View 中
        bmOptions.inJustDecodeBounds = false
        bmOptions.inSampleSize = scaleFactor

        return BitmapFactory.decodeStream(inputStream, null, bmOptions)
    }

    fun findScaleFactor(targetW: Int, targetH: Int, inputStream: InputStream): Int {
        // 获取 bitmap 的尺寸
        val bmOptions = BitmapFactory.Options()
        bmOptions.inJustDecodeBounds = true
        BitmapFactory.decodeStream(inputStream, null, bmOptions)
        val actualW = bmOptions.outWidth
        val actualH = bmOptions.outHeight

        // 确定图像的缩放比例
        return Math.min(actualW/targetW, actualH/targetH)
    }

    @Throws(IOException::class)
    fun fetchAndRescaleBitmap(uri: String, width: Int, height: Int): Bitmap {
        val url = URL(uri)
        val urlConnection = url.openConnection() as HttpURLConnection

        BufferedInputStream(urlConnection.inputStream).use {
            it.mark(MAX_READ_LIMIT_PER_IMG)
            val scaleFactor = findScaleFactor(width, height, it)
            LogHelper.d(TAG, "Scaling bitmap ", uri, " by factor ", scaleFactor, " to support ",
                    width, "x", height, "requested dimension")
            it.reset()
            return scaleBitmap(scaleFactor, it)
        }
    }

}