package com.wechatbroadcast

import android.content.Context

/** 只保存排除词、文案草稿和间隔。不把联系人名单写入磁盘。 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun keywords(): List<String> {
        if (!prefs.getBoolean(KEY_READY, false)) return ExclusionRules.defaultKeywords
        return decode(prefs.getString(KEY_WORDS, "").orEmpty())
    }

    fun saveKeywords(words: List<String>) {
        prefs.edit()
            .putBoolean(KEY_READY, true)
            .putString(KEY_WORDS, words.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"))
            .apply()
    }

    fun message(): String = prefs.getString(KEY_MESSAGE, "").orEmpty()

    fun saveMessage(value: String) {
        prefs.edit().putString(KEY_MESSAGE, value).apply()
    }

    fun intervalSeconds(): Int {
        return prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL_SECONDS)
            .coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS)
    }

    fun saveInterval(seconds: Int) {
        prefs.edit().putInt(KEY_INTERVAL, seconds).apply()
    }

    private fun decode(raw: String): List<String> {
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    companion object {
        const val DEFAULT_INTERVAL_SECONDS = 5
        const val MIN_INTERVAL_SECONDS = 3
        const val MAX_INTERVAL_SECONDS = 60

        private const val PREFS = "wechat_broadcast"
        private const val KEY_READY = "keywords_ready"
        private const val KEY_WORDS = "keywords"
        private const val KEY_MESSAGE = "message"
        private const val KEY_INTERVAL = "interval"
    }
}
