package com.wechatbroadcast

import org.junit.Assert.assertEquals
import org.junit.Test

class BroadcastPlanTest {
    @Test
    fun dropsHeadersAndIndexesThenSplitsByKeyword() {
        val raw = listOf(
            "新的朋友",
            "群聊",
            "标签",
            "公众号",
            "企业微信联系人",
            "星标朋友",
            "A",
            "张三",
            "张三",
            " 张三 ",
            "张三😈",
            "小恶魔同事",
            "王",
            "#",
            "我的群聊",
        )
        val plan = BroadcastPlan.prepare(raw, ExclusionRules.defaultKeywords)
        assertEquals(listOf("张三", "王", "我的群聊"), plan.included)
        assertEquals(listOf("张三😈", "小恶魔同事"), plan.excluded)
    }

    @Test
    fun emptyRulesStillDropFunctionEntries() {
        val plan = BroadcastPlan.prepare(
            listOf("群聊", "同事甲", "A", "小恶魔"),
            emptyList(),
        )
        assertEquals(listOf("同事甲", "小恶魔"), plan.included)
        assertEquals(emptyList<String>(), plan.excluded)
    }
}
