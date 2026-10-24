package com.wechatbroadcast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactEntriesTest {
    @Test
    fun skipsFunctionEntries() {
        listOf(
            "新的朋友",
            "群聊",
            "标签",
            "公众号",
            "服务号",
            "企业微信联系人",
            "企业微信",
            "星标朋友",
            "文件传输助手",
        ).forEach { label ->
            assertFalse(label, ContactEntries.isBroadcastCandidate(label))
        }
    }

    @Test
    fun skipsLetterIndexButKeepsSingleChineseCharacter() {
        assertFalse(ContactEntries.isBroadcastCandidate("A"))
        assertFalse(ContactEntries.isBroadcastCandidate("z"))
        assertFalse(ContactEntries.isBroadcastCandidate("#"))
        assertFalse(ContactEntries.isBroadcastCandidate("  B  "))
        assertTrue(ContactEntries.isBroadcastCandidate("王"))
        assertTrue(ContactEntries.isBroadcastCandidate("😈"))
    }

    @Test
    fun keepsNormalRemarksIncludingThoseWithDevilMarks() {
        assertTrue(ContactEntries.isBroadcastCandidate("张三"))
        assertTrue(ContactEntries.isBroadcastCandidate("张三😈"))
        assertTrue(ContactEntries.isBroadcastCandidate("小恶魔"))
    }

    @Test
    fun trimsBeforeCheckingFunctionEntry() {
        assertFalse(ContactEntries.isBroadcastCandidate("  群聊  "))
        assertFalse(ContactEntries.isBroadcastCandidate("\n公众号\n"))
    }

    @Test
    fun doesNotSkipNameThatOnlyContainsFunctionWord() {
        assertTrue(ContactEntries.isBroadcastCandidate("我的群聊"))
        assertTrue(ContactEntries.isBroadcastCandidate("公众号编辑"))
        assertTrue(ContactEntries.isBroadcastCandidate("AA"))
    }

    @Test
    fun skipsChromeLabelsAndVeryLongText() {
        assertFalse(ContactEntries.isBroadcastCandidate("搜索"))
        assertFalse(ContactEntries.isBroadcastCandidate("通讯录"))
        assertFalse(ContactEntries.isBroadcastCandidate("啊".repeat(81)))
        assertTrue(ContactEntries.isBroadcastCandidate("啊".repeat(80)))
    }

    @Test
    fun chatTitleMatchesExactOrTruncatedPrefix() {
        assertTrue(ContactEntries.chatTitleMatches("同事甲", "同事甲"))
        assertTrue(ContactEntries.chatTitleMatches("同事甲…", "同事甲同学"))
        assertTrue(ContactEntries.chatTitleMatches("同事甲...", "同事甲同学"))
        assertFalse(ContactEntries.chatTitleMatches("同事乙", "同事甲"))
        assertFalse(ContactEntries.chatTitleMatches("同事", "同事甲同学"))
        assertFalse(ContactEntries.chatTitleMatches("同", "同事甲"))
        assertFalse(ContactEntries.chatTitleMatches("", "同事甲"))
    }
}
