package com.wechatbroadcast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatNavigationTest {
    private val weChat = "com.tencent.mm"

    @Test
    fun doesNotPressBackWhenForegroundIsNotWeChat() {
        assertFalse(WeChatNavigation.shouldPressBack(null, weChat, 0, onMainTabs = false))
        assertFalse(WeChatNavigation.shouldPressBack("com.wechatbroadcast", weChat, 0, onMainTabs = false))
    }

    @Test
    fun doesNotPressBackOnWeChatMainTabs() {
        assertFalse(WeChatNavigation.shouldPressBack(weChat, weChat, 0, onMainTabs = true))
    }

    @Test
    fun pressesBackOnlyInsideWeChatSubpageAndOnlyTwice() {
        assertTrue(WeChatNavigation.shouldPressBack(weChat, weChat, 0, onMainTabs = false))
        assertTrue(WeChatNavigation.shouldPressBack(weChat, weChat, 1, onMainTabs = false))
        assertFalse(WeChatNavigation.shouldPressBack(weChat, weChat, 2, onMainTabs = false))
    }
}
