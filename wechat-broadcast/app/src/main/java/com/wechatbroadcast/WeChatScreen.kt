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
        return hasBack && !onMainTabs(labels)
    }

    fun looksLikeChat(labels: List<String>): Boolean {
        val hasSend = labels.any { matchesLabel(it, "发送") }
        return hasSend && !onMainTabs(labels)
    }

    private fun onMainTabs(labels: List<String>): Boolean {
        return labels.any { matchesLabel(it, "通讯录") } || labels.any { matchesLabel(it, "发现") }
    }

    /** 只认贴着屏幕底边的标签栏，不认它上面的聊天列表。 */
    fun isInBottomTabBand(centerY: Int, screenBottom: Int, density: Float): Boolean {
        if (density <= 0f) return false
        val fromBottom = screenBottom - centerY
        val min = (12f * density).toInt()
        val max = (96f * density).toInt()
        return fromBottom in min..max
    }

    fun isTabSized(width: Int, height: Int, screenWidth: Int, density: Float): Boolean {
        if (density <= 0f || screenWidth <= 0) return false
        val min = (16f * density).toInt()
        val maxHeight = (88f * density).toInt()
        val maxWidth = (screenWidth * 0.34f).toInt()
        return width in min..maxWidth && height in min..maxHeight
    }

    data class TabSpot(val label: String, val centerX: Int, val centerY: Int)

    /**
     * 有「通讯录」字样就点那个字。否则只有底部正好排出 3 到 5 个标签时，才点左起第二个。
     * 单独一个按钮（广告、一条群聊）不会点。
     */
    fun chooseContactsTap(spots: List<TabSpot>, minColumnGap: Int): Pair<Int, Int>? {
        spots.firstOrNull { matchesLabel(it.label, "通讯录") }?.let { return it.centerX to it.centerY }
        if (minColumnGap <= 0) return null
        val columns = distinctColumns(spots.map { it.centerX }, minColumnGap)
        if (columns.size !in 3..5) return null
        val targetX = columns[1]
        val near = spots.filter { kotlin.math.abs(it.centerX - targetX) < minColumnGap }
        if (near.isEmpty()) return null
        return targetX to near.sumOf { it.centerY } / near.size
    }

    fun distinctColumns(xs: List<Int>, minGap: Int): List<Int> {
        val sorted = xs.filter { it > 0 }.sorted()
        if (sorted.isEmpty() || minGap <= 0) return emptyList()
        val columns = ArrayList<Int>()
        columns.add(sorted.first())
        for (x in sorted.drop(1)) {
            if (x - columns.last() >= minGap) columns.add(x)
        }
        return columns
    }

    fun chromeLabels(labels: List<String>): List<String> {
        return chromeWords.filter { word -> labels.any { matchesLabel(it, word) } }
    }

    fun contactsEntryError(seenChrome: List<String>): String {
        val prefix = "没有点进通讯录。为避免点进群聊或广告，已停止点击。"
        if (seenChrome.isEmpty()) {
            return prefix + "屏幕最底部没有读到「通讯录」标签。"
        }
        return prefix + "能读到：${seenChrome.joinToString("、")}。"
    }
}
