package com.wechatbroadcast

/**
 * 备注 / 显示名的排除规则。不依赖 Android，便于单元测试。
 *
 * 显示名去掉首尾空白后，只要包含任一有效排除词就排除。
 * 排除词自身也去掉首尾空白；空白项忽略。词中间的空格保留。
 */
object ExclusionRules {
    val defaultKeywords: List<String> = listOf("😈", "👿", "小恶魔")

    data class Classification(
        val included: List<String>,
        val excluded: List<String>,
    )

    fun isExcluded(displayName: String, keywords: List<String>): Boolean {
        val name = displayName.trim()
        if (name.isEmpty()) return false
        return keywords.any { keyword ->
            val needle = keyword.trim()
            needle.isNotEmpty() && name.contains(needle, ignoreCase = true)
        }
    }

    fun classify(names: List<String>, keywords: List<String>): Classification {
        val seen = LinkedHashSet<String>()
        val included = ArrayList<String>()
        val excluded = ArrayList<String>()
        for (raw in names) {
            val name = raw.trim()
            if (name.isEmpty() || !seen.add(name)) continue
            if (isExcluded(name, keywords)) excluded += name else included += name
        }
        return Classification(included, excluded)
    }
}
