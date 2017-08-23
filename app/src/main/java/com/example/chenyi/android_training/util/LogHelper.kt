/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.chenyi.android_training.util

import android.util.Log
import com.example.chenyi.android_training.BuildConfig

object LogHelper {

    private val LOG_PREFIX = "cy_"
    private val LOG_PREFIX_LENGTH = LOG_PREFIX.length
    private val MAX_LOG_TAG_LENGTH = 23

    fun makeLogTag(str: String): String {
        return if (str.length > MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH) {
            LOG_PREFIX + str.substring(0, MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH - 1)
        } else LOG_PREFIX + str

    }

    /**
     * Don't use this when obfuscating class names!
     */
    fun makeLogTag(cls: Class<*>): String = makeLogTag(cls.simpleName)


    fun v(tag: String, vararg messages: Any) {
        // Only log VERBOSE if build type is DEBUG
        if (BuildConfig.DEBUG) {
            Log.v(tag, msg(*messages))
        }
    }

    fun d(tag: String, vararg messages: Any) {
        // Only log DEBUG if build type is DEBUG
        if (BuildConfig.DEBUG) {
            Log.d(tag, msg(*messages))
        }
    }

    fun i(tag: String, vararg messages: Any) {
        Log.i(tag, msg(*messages))
    }

    fun w(tag: String, vararg messages: Any) {
        Log.w(tag, msg(*messages))
    }

    fun w(tag: String, t: Throwable, vararg messages: Any) {
        Log.w(tag, msg(*messages, t))
    }

    fun e(tag: String, vararg messages: Any) {
        Log.e(tag, msg(*messages))
    }

    fun e(tag: String, t: Throwable, vararg messages: Any) {
        Log.e(tag, msg(*messages, t))
    }

    private fun msg(vararg messages: Any, t: Throwable? = null): String {
        val sb = StringBuilder()
        messages.forEach { sb.append(it) }
        t?.let { sb.append("\n").append(Log.getStackTraceString(t)) }
        return sb.toString()
    }
}
