package com.wechatbroadcast

/** 把通讯录上读到的原始文字收成「待发送 / 排除」两份名单。 */
object BroadcastPlan {
    fun prepare(rawLabels: List<String>, keywords: List<String>): ExclusionRules.Classification {
        val candidates = rawLabels.filter { ContactEntries.isBroadcastCandidate(it) }
        return ExclusionRules.classify(candidates, keywords)
    }
}
