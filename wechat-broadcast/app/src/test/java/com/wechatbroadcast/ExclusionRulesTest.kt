package com.wechatbroadcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusionRulesTest {
    private val defaults = ExclusionRules.defaultKeywords

    @Test
    fun excludesWhenKeywordAppearsInsideDisplayName() {
        assertTrue(ExclusionRules.isExcluded("张三😈", defaults))
        assertTrue(ExclusionRules.isExcluded("备注小恶魔同学", listOf("小恶魔")))
        assertTrue(ExclusionRules.isExcluded("Hello小恶魔", listOf("小恶魔")))
    }

    @Test
    fun doesNotExcludeWhenNoKeywordMatches() {
        assertFalse(ExclusionRules.isExcluded("张三", defaults))
        assertFalse(ExclusionRules.isExcluded("同事", listOf("小恶魔")))
        assertFalse(ExclusionRules.isExcluded("小小", listOf("小恶魔")))
    }

    @Test
    fun defaultKeywordsExcludeDevilMarksOnly() {
        assertEquals(listOf("😈", "👿", "小恶魔"), defaults)
        assertTrue(ExclusionRules.isExcluded("小恶魔", defaults))
        assertTrue(ExclusionRules.isExcluded("朋友👿", defaults))
        assertFalse(ExclusionRules.isExcluded("普通好友", defaults))
    }

    @Test
    fun emptyKeywordListExcludesNobody() {
        assertFalse(ExclusionRules.isExcluded("小恶魔", emptyList()))
        assertFalse(ExclusionRules.isExcluded("张三😈", emptyList()))
    }

    @Test
    fun blankKeywordsAreIgnored() {
        assertFalse(ExclusionRules.isExcluded("李四", listOf("", "  ", "\n")))
        assertTrue(ExclusionRules.isExcluded("李四小恶魔", listOf("", "  小恶魔  ")))
    }

    @Test
    fun emojiKeywordMatchesEmojiInName() {
        assertTrue(ExclusionRules.isExcluded("夜猫😈", listOf("😈")))
        assertTrue(ExclusionRules.isExcluded("👿", listOf("👿")))
        assertFalse(ExclusionRules.isExcluded("夜猫", listOf("😈")))
        assertFalse(ExclusionRules.isExcluded("😊", listOf("😈", "👿")))
    }

    @Test
    fun trimsDisplayNameAndKeywordButKeepsInnerSpaces() {
        assertTrue(ExclusionRules.isExcluded("  小恶魔  ", listOf("小恶魔")))
        assertTrue(ExclusionRules.isExcluded("\n小恶魔\n", listOf("小恶魔")))
        assertTrue(ExclusionRules.isExcluded("\t张三😈 ", listOf("😈")))
        assertTrue(ExclusionRules.isExcluded("朋友小恶魔", listOf("  小恶魔  ")))
        assertFalse(ExclusionRules.isExcluded("小 恶魔", listOf("小恶魔")))
        assertTrue(ExclusionRules.isExcluded("小 恶魔", listOf("小 恶魔")))
        assertFalse(ExclusionRules.isExcluded("小恶魔", listOf("小 恶魔")))
    }

    @Test
    fun matchesAsciiWithoutCase() {
        assertTrue(ExclusionRules.isExcluded("AbC好友", listOf("abc")))
        assertTrue(ExclusionRules.isExcluded("abc", listOf("ABC")))
        assertFalse(ExclusionRules.isExcluded("abd", listOf("ABC")))
    }

    @Test
    fun emptyDisplayNameIsNotExcluded() {
        assertFalse(ExclusionRules.isExcluded("", listOf("小恶魔")))
        assertFalse(ExclusionRules.isExcluded("   ", listOf("小恶魔")))
    }

    @Test
    fun classifySplitsAndPreservesOrder() {
        val result = ExclusionRules.classify(
            listOf("同事甲", "小恶魔同事", "同事乙", "夜猫😈"),
            defaults,
        )
        assertEquals(listOf("同事甲", "同事乙"), result.included)
        assertEquals(listOf("小恶魔同事", "夜猫😈"), result.excluded)
    }

    @Test
    fun classifyDedupesTrimmedNamesAndDropsBlanks() {
        val result = ExclusionRules.classify(
            listOf(" 张三 ", "张三", "", "   ", "张三😈", " 张三😈 "),
            defaults,
        )
        assertEquals(listOf("张三"), result.included)
        assertEquals(listOf("张三😈"), result.excluded)
    }
}
