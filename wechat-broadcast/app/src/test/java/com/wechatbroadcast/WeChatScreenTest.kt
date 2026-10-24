package com.wechatbroadcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatScreenTest {
    @Test
    fun matchesTabLabelWithUnreadSuffix() {
        assertTrue(WeChatScreen.matchesLabel("通讯录", "通讯录"))
        assertTrue(WeChatScreen.matchesLabel("通讯录，2条新消息", "通讯录"))
        assertFalse(WeChatScreen.matchesLabel("我的通讯录", "通讯录"))
        assertFalse(WeChatScreen.matchesLabel("我们", "我"))
    }

    @Test
    fun detectsContactsPageFromEntryLabels() {
        assertTrue(
            WeChatScreen.looksLikeContactsPage(listOf("通讯录", "新的朋友", "群聊", "标签", "公众号")),
        )
        assertTrue(WeChatScreen.looksLikeContactsPage(listOf("群聊", "标签")))
        assertFalse(WeChatScreen.looksLikeContactsPage(listOf("微信", "通讯录", "发现", "我")))
        assertFalse(WeChatScreen.looksLikeContactsPage(listOf("新的朋友")))
    }

    @Test
    fun subpageNeedsBackAndMustNotBeMainTabs() {
        assertTrue(WeChatScreen.looksLikeSubpage(listOf("返回", "发送")))
        assertFalse(WeChatScreen.looksLikeSubpage(listOf("返回", "通讯录", "发现")))
        assertFalse(WeChatScreen.looksLikeSubpage(listOf("微信", "通讯录", "发现", "我")))
    }

    @Test
    fun chromeLabelsKeepOnlyInterfaceWords() {
        assertEquals(
            listOf("微信", "通讯录", "发现"),
            WeChatScreen.chromeLabels(listOf("张三", "微信", "通讯录，1", "发现", "周末愉快")),
        )
    }

    @Test
    fun bottomBandExcludesChatRowsAboveTheTabBar() {
        val density = 3f
        val screenBottom = 2400
        assertTrue(WeChatScreen.isInBottomTabBand(centerY = 2400 - (60 * 3), screenBottom, density))
        assertFalse(WeChatScreen.isInBottomTabBand(centerY = 2400 - (140 * 3), screenBottom, density))
    }

    @Test
    fun doesNotTapASingleButtonOrAWideChatRow() {
        val ad = listOf(WeChatScreen.TabSpot("广告", centerX = 540, centerY = 2200))
        assertEquals(null, WeChatScreen.chooseContactsTap(ad, minColumnGap = 120))
        val labeled = listOf(WeChatScreen.TabSpot("通讯录", centerX = 270, centerY = 2300))
        assertEquals(270 to 2300, WeChatScreen.chooseContactsTap(labeled, minColumnGap = 120))
    }

    @Test
    fun tapsSecondOfFourBottomTabsWhenLabelsAreMissing() {
        val spots = listOf(
            WeChatScreen.TabSpot("", 135, 2300),
            WeChatScreen.TabSpot("", 405, 2300),
            WeChatScreen.TabSpot("", 675, 2300),
            WeChatScreen.TabSpot("", 945, 2300),
        )
        assertEquals(405 to 2300, WeChatScreen.chooseContactsTap(spots, minColumnGap = 120))
    }

    @Test
    fun wideRowIsNotATab() {
        assertFalse(WeChatScreen.isTabSized(width = 1000, height = 180, screenWidth = 1080, density = 3f))
        assertTrue(WeChatScreen.isTabSized(width = 200, height = 140, screenWidth = 1080, density = 3f))
    }
}
