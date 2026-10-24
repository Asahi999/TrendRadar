package com.wechatbroadcast

/**
 * 什么时候允许按系统返回。
 * 前台不是微信时不能按，否则返回会落到本应用的进度页，被当成用户点了停止。
 */
object WeChatNavigation {
    const val MAX_BACKS_INSIDE_WECHAT = 2

    fun shouldPressBack(
        foregroundPackage: String?,
        weChatPackage: String,
        backsAlready: Int,
        onMainTabs: Boolean,
    ): Boolean {
        if (foregroundPackage != weChatPackage) return false
        if (onMainTabs) return false
        return backsAlready < MAX_BACKS_INSIDE_WECHAT
    }
}
