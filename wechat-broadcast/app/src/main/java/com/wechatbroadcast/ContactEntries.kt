package com.wechatbroadcast

/**
 * 通讯录里哪些文字是好友显示名，哪些是分组标题或功能入口。
 * 字母索引只排除单个英文字母和 #，单个汉字姓名要保留。
 */
object ContactEntries {
    val functionEntries: Set<String> = setOf(
        "新的朋友",
        "群聊",
        "标签",
        "公众号",
        "服务号",
        "企业微信联系人",
        "企业微信",
        "仅聊天的朋友",
        "星标朋友",
        "文件传输助手",
        "微信团队",
        "微信支付",
        "微信运动",
    )

    private val chromeLabels: Set<String> = setOf(
        "微信",
        "通讯录",
        "发现",
        "我",
        "搜索",
        "返回",
        "更多",
        "更多功能",
        "头像",
    )

    fun isBroadcastCandidate(raw: String): Boolean {
        val name = raw.trim()
        if (name.isEmpty() || name.length > 80) return false
        if (name in functionEntries || name in chromeLabels) return false
        if (isLetterIndex(name)) return false
        return true
    }

    fun isLetterIndex(raw: String): Boolean {
        val name = raw.trim()
        if (name.length != 1) return false
        val ch = name[0]
        return ch in 'A'..'Z' || ch in 'a'..'z' || ch == '#'
    }

    /** 聊天标题可能被截断。完整相等，或标题是显示名的前缀且不少于 3 个字，才算打开了对的人。 */
    fun chatTitleMatches(title: String, displayName: String): Boolean {
        val name = displayName.trim()
        val compact = title.trim().removeSuffix("…").removeSuffix("...")
        if (name.isEmpty() || compact.isEmpty()) return false
        if (compact == name) return true
        return compact.length >= 3 && name.startsWith(compact)
    }
}
