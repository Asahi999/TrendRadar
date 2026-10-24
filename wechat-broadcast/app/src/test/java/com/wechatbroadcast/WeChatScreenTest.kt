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
    fun contactsTabIsTheSecondBottomSlot() {
        val (x, y) = WeChatScreen.contactsTabPoint(left = 0, top = 0, width = 1080, height = 2400)
        assertEquals(405f, x)
        assertEquals(2220f, y)
    }
}
