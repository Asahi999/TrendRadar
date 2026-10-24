package com.wechatbroadcast

/** 从屏幕文字判断微信现在停在哪一页。不依赖具体控件 id。 */
object WeChatScreen {
    private val chromeWords = listOf(
        "微信",
        "通讯录",
        "发现",
        "搜索",
        "新的朋友",
        "群聊",
        "标签",
        "公众号",
        "服务号",
        "返回",
        "登录",
        "注册",
    )

    fun matchesLabel(raw: String, expected: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty() || expected.isEmpty()) return false
        if (text == expected) return true
        if (expected.length <= 1) return false
        return text.startsWith(expected)
    }

    fun looksLikeContactsPage(labels: List<String>): Boolean {
        val hasNewFriend = labels.any { matchesLabel(it, "新的朋友") }
        val hasGroup = labels.any { matchesLabel(it, "群聊") }
        val hasTag = labels.any { matchesLabel(it, "标签") }
        val hasOfficial = labels.any { matchesLabel(it, "公众号") || matchesLabel(it, "服务号") }
        val hasContactsTab = labels.any { matchesLabel(it, "通讯录") }
        if (hasNewFriend && (hasGroup || hasTag || hasOfficial || hasContactsTab)) return true
        return hasGroup && hasTag
    }

    fun looksLikeLogin(labels: List<String>): Boolean {
        return labels.any { matchesLabel(it, "登录") } && labels.any { matchesLabel(it, "注册") }
    }

    fun looksLikeSubpage(labels: List<String>): Boolean {
        val hasBack = labels.any { matchesLabel(it, "返回") }
        val onMainTabs = labels.any { matchesLabel(it, "通讯录") } || labels.any { matchesLabel(it, "发现") }
        return hasBack && !onMainTabs
    }

    fun chromeLabels(labels: List<String>): List<String> {
        return chromeWords.filter { word -> labels.any { matchesLabel(it, word) } }
    }

    fun contactsEntryError(seenChrome: List<String>): String {
        if (seenChrome.isEmpty()) {
            return "没有进入微信通讯录，屏幕上的文字也没读到。请到系统无障碍里重新打开「微信群发」，并确认它可以读取屏幕内容。"
        }
        return "没有进入微信通讯录。当前能读到：${seenChrome.joinToString("、")}。请把微信停在主界面后再点开始。"
    }

    /** 底部四个标签从左到右是微信、通讯录、发现、我。返回通讯录那一格的点击位置。 */
    fun contactsTabPoint(left: Int, top: Int, width: Int, height: Int): Pair<Float, Float> {
        val x = left + width * 0.375f
        val y = top + height * 0.925f
        return x to y
    }
}
