package com.wechatbroadcast

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 只通过无障碍操作已经打开的微信界面。
 * 读名单时不点进聊天；发送时只点联系人结果，不点群聊、聊天记录和公众号。
 */
class WeChatAutomator(private val service: AccessibilityService) {
    sealed class NameScan {
        data class Ok(val names: List<String>, val mayBePartial: Boolean) : NameScan()
        data object Stopped : NameScan()
        data class Error(val message: String) : NameScan()
    }

    fun launchWeChat() {
        val intent = service.packageManager.getLaunchIntentForPackage(WeChatStatus.PACKAGE_NAME) ?: return
        intent.addFlags(
            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
        )
        try {
            service.startActivity(intent)
        } catch (error: Exception) {
            Log.i(TAG, "打开微信失败")
        }
    }

    fun waitUntilWeChat(token: Int, timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        var seenAt = 0L
        while (System.currentTimeMillis() < end) {
            if (!SessionStore.awaitContinue(token)) return false
            if (foregroundPackage() == WeChatStatus.PACKAGE_NAME) {
                if (seenAt == 0L) seenAt = System.currentTimeMillis()
                if (System.currentTimeMillis() - seenAt >= 800) return true
            } else {
                seenAt = 0L
            }
            if (!waitMs(token, 300)) return false
        }
        return false
    }

    fun scanNames(token: Int): NameScan {
        val openError = openContacts(token)
        if (openError == STOPPED) return NameScan.Stopped
        if (openError != null) return NameScan.Error(openError)
        if (!scrollToTop(token)) return NameScan.Stopped

        val labels = LinkedHashSet<String>()
        var previous = ""
        var stuck = 0
        var hardEnd = false
        var mayBePartial = false
        for (page in 0 until MAX_PAGES) {
            if (!SessionStore.awaitContinue(token)) return NameScan.Stopped
            if (page == 0 && withRoot { isContactsPage(it) } != true) {
                val again = openContacts(token)
                if (again == STOPPED) return NameScan.Stopped
                if (again != null) return NameScan.Error(again)
            }
            val root = service.rootInActiveWindow ?: return NameScan.Error("微信窗口不可用")
            var scrolled = false
            try {
                val visible = try {
                    visibleRowLabels(root)
                } catch (_: Exception) {
                    emptyList()
                }
                visible.map { it.trim() }.filter { it.isNotEmpty() }.forEach { labels.add(it) }
                val fingerprint = visible.joinToString("\n")
                if (page > 0 && fingerprint == previous) stuck += 1 else if (page > 0) stuck = 0
                previous = fingerprint
                SessionStore.updateStatus(token, "正在读取通讯录，已看到 ${labels.size} 行")
                if (stuck >= 3) {
                    mayBePartial = true
                    break
                }
                scrolled = try {
                    scroll(root, forward = true)
                } catch (_: Exception) {
                    false
                }
                if (!scrolled) hardEnd = true
            } finally {
                root.recycle()
            }
            if (!scrolled) break
            if (!waitMs(token, 700)) return NameScan.Stopped
        }
        if (!hardEnd && stuck < 3) mayBePartial = true
        if (labels.isEmpty() && mayBePartial) {
            return NameScan.Error("没有读到好友。请确认微信已打开到通讯录。")
        }
        return NameScan.Ok(labels.toList(), mayBePartial && !hardEnd)
    }

    /** 成功返回 null。停止返回 [STOPPED]。其余字符串是给用户看的失败原因。 */
    fun sendTo(token: Int, name: String, message: String): String? {
        if (!SessionStore.awaitContinue(token)) return STOPPED
        val openError = openContacts(token)
        if (openError != null) return openError
        if (!clickRetry(token, "搜索")) {
            return if (SessionStore.isCurrent(token)) "没有找到通讯录里的搜索" else STOPPED
        }
        if (!waitMs(token, 700)) return STOPPED
        if (!setEditText(name, preferBottom = false)) {
            return if (SessionStore.isCurrent(token)) "无法在搜索框输入名字" else STOPPED
        }
        if (!waitMs(token, 900)) return STOPPED
        val clicked = withRoot { root -> clickSearchResult(root, name) } == true
        if (!clicked) {
            backTowardContacts(token)
            return if (SessionStore.isCurrent(token)) {
                "没有在联系人结果里点开这个显示名。同名群不会发送"
            } else {
                STOPPED
            }
        }
        if (!waitForChat(token, name)) {
            backTowardContacts(token)
            return if (SessionStore.isCurrent(token)) "打开的页面不是这个好友的单聊，已跳过" else STOPPED
        }
        if (!setEditText(message, preferBottom = true)) {
            backTowardContacts(token)
            return if (SessionStore.isCurrent(token)) "没有找到聊天输入框" else STOPPED
        }
        if (!waitMs(token, 500)) return STOPPED
        if (!clickSendWithRetry(token)) {
            backTowardContacts(token)
            return if (SessionStore.isCurrent(token)) "没有找到发送按钮" else STOPPED
        }
        if (!waitMs(token, 400)) return STOPPED
        backTowardContacts(token)
        return null
    }

    private fun openContacts(token: Int): String? {
        val deadline = System.currentTimeMillis() + 18_000
        var lastRelaunch = 0L
        var sawWeChat = false
        var sample = emptyList<String>()
        var backs = 0
        var taps = 0
        var misses = 0
        while (System.currentTimeMillis() < deadline) {
            if (!SessionStore.awaitContinue(token)) return STOPPED
            if (!isWeChatForeground()) {
                val now = System.currentTimeMillis()
                if (!sawWeChat || now - lastRelaunch > 8_000) {
                    lastRelaunch = now
                    SessionStore.updateStatus(token, "正在打开微信")
                    launchWeChat()
                }
                if (!waitMs(token, 500)) return STOPPED
                continue
            }
            sawWeChat = true
            val labels = withRoot { collectLabels(it) }.orEmpty()
            if (labels.isNotEmpty()) sample = WeChatScreen.chromeLabels(labels)
            if (WeChatScreen.looksLikeContactsPage(labels)) return null
            if (WeChatScreen.looksLikeLogin(labels)) {
                return "微信似乎停在登录页，请先登录后再试"
            }
            if (
                (WeChatScreen.looksLikeSubpage(labels) || WeChatScreen.looksLikeChat(labels)) &&
                backs < WeChatNavigation.MAX_BACKS_INSIDE_WECHAT
            ) {
                backs += 1
                SessionStore.updateStatus(token, "正在从聊天页返回，不再往下点")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                if (!waitMs(token, 700)) return STOPPED
                continue
            }
            if (taps >= 2) {
                return WeChatScreen.contactsEntryError(sample)
            }
            val tap = findContactsTap()
            if (tap == null) {
                misses += 1
                SessionStore.updateStatus(token, "还没找到最底部的通讯录标签，先不点屏幕")
                if (misses >= 4) return WeChatScreen.contactsEntryError(sample)
                if (!waitMs(token, 700)) return STOPPED
                continue
            }
            SessionStore.updateStatus(token, "正在点最底部的通讯录")
            clickPoint(tap.first.toFloat(), tap.second.toFloat())
            taps += 1
            if (!waitMs(token, 1200)) return STOPPED
        }
        return WeChatScreen.contactsEntryError(sample)
    }

    private fun findContactsTap(): Pair<Int, Int>? {
        val screen = realScreen()
        val density = service.resources.displayMetrics.density
        val spots = ArrayList<WeChatScreen.TabSpot>()
        forEachWeChatRoot { root ->
            collectTabSpots(root, screen, density, spots)
        }
        val gap = (48f * density).toInt().coerceAtLeast(1)
        return WeChatScreen.chooseContactsTap(spots, gap)
    }

    private fun collectTabSpots(
        root: AccessibilityNodeInfo,
        screen: Rect,
        density: Float,
        spots: MutableList<WeChatScreen.TabSpot>,
    ) {
        fun walk(node: AccessibilityNodeInfo) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (
                WeChatScreen.isInBottomTabBand(bounds.centerY(), screen.bottom, density) &&
                WeChatScreen.isTabSized(bounds.width(), bounds.height(), screen.width(), density)
            ) {
                spots.add(
                    WeChatScreen.TabSpot(
                        label = labelOf(node),
                        centerX = bounds.centerX(),
                        centerY = bounds.centerY(),
                    ),
                )
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    walk(child)
                } finally {
                    child.recycle()
                }
            }
        }
        walk(root)
    }

    private fun realScreen(): Rect {
        val windowManager = service.getSystemService(android.view.WindowManager::class.java)
        return if (android.os.Build.VERSION.SDK_INT >= 30 && windowManager != null) {
            Rect(windowManager.currentWindowMetrics.bounds)
        } else {
            val metrics = service.resources.displayMetrics
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun forEachWeChatRoot(block: (AccessibilityNodeInfo) -> Unit) {
        val active = service.rootInActiveWindow
        if (active != null) {
            try {
                if (active.packageName?.toString() == WeChatStatus.PACKAGE_NAME) block(active)
            } finally {
                active.recycle()
            }
        }
        val windows = service.windows ?: return
        for (window in windows) {
            val root = window.root ?: continue
            try {
                if (root.packageName?.toString() == WeChatStatus.PACKAGE_NAME) block(root)
            } finally {
                root.recycle()
            }
        }
    }

    private fun foregroundPackage(): String? {
        val root = service.rootInActiveWindow ?: return null
        return try {
            root.packageName?.toString()
        } finally {
            root.recycle()
        }
    }

    private fun isWeChatForeground(): Boolean {
        if (foregroundPackage() == WeChatStatus.PACKAGE_NAME) return true
        val windows = service.windows ?: return false
        var found = false
        for (window in windows) {
            val root = window.root ?: continue
            if (!found && root.packageName?.toString() == WeChatStatus.PACKAGE_NAME) found = true
            root.recycle()
        }
        return found
    }

    private fun collectLabels(root: AccessibilityNodeInfo): List<String> {
        val labels = ArrayList<String>()
        fun walk(node: AccessibilityNodeInfo) {
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) labels.add(text)
            if (desc.isNotEmpty() && desc != text) labels.add(desc)
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    walk(child)
                } finally {
                    child.recycle()
                }
            }
        }
        walk(root)
        return labels
    }

    private fun scrollToTop(token: Int): Boolean {
        repeat(40) {
            if (!SessionStore.awaitContinue(token)) return false
            val root = service.rootInActiveWindow ?: return true
            val moved = try {
                scroll(root, forward = false)
            } catch (_: Exception) {
                false
            } finally {
                root.recycle()
            }
            if (!moved) return true
            if (!waitMs(token, 220)) return false
        }
        return true
    }

    private fun isContactsPage(root: AccessibilityNodeInfo): Boolean {
        return WeChatScreen.looksLikeContactsPage(collectLabels(root))
    }

    private fun clickRetry(token: Int, label: String): Boolean {
        repeat(3) {
            if (!SessionStore.awaitContinue(token)) return false
            if (withRoot { root -> clickLabeled(root, label) } == true) return true
            if (!waitMs(token, 400)) return false
        }
        return false
    }

    private fun waitForChat(token: Int, name: String): Boolean {
        repeat(5) {
            if (!SessionStore.awaitContinue(token)) return false
            if (withRoot { root -> chatTitleMatches(root, name) } == true) return true
            if (!waitMs(token, 400)) return false
        }
        return false
    }

    private fun clickSendWithRetry(token: Int): Boolean {
        repeat(3) {
            if (!SessionStore.awaitContinue(token)) return false
            if (clickSend()) return true
            if (!waitMs(token, 400)) return false
        }
        return false
    }

    private fun backTowardContacts(token: Int) {
        repeat(WeChatNavigation.MAX_BACKS_INSIDE_WECHAT) {
            if (!SessionStore.awaitContinue(token)) return
            val labels = withRoot { collectLabels(it) }.orEmpty()
            if (WeChatScreen.looksLikeContactsPage(labels)) return
            if (!WeChatScreen.looksLikeSubpage(labels)) return
            if (!isWeChatForeground()) return
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            if (!waitMs(token, 400)) return
        }
    }

    private fun chatTitleMatches(root: AccessibilityNodeInfo, name: String): Boolean {
        val screen = Rect()
        root.getBoundsInScreen(screen)
        val topLimit = screen.top + (screen.height() * 0.25f).toInt()
        var matched = false
        fun walk(node: AccessibilityNodeInfo) {
            if (matched) return
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty() && bounds.top <= topLimit && bounds.height() < screen.height() / 4) {
                if (ContactEntries.chatTitleMatches(text, name)) matched = true
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    walk(child)
                } finally {
                    child.recycle()
                }
            }
        }
        walk(root)
        return matched
    }

    private fun clickSearchResult(root: AccessibilityNodeInfo, name: String): Boolean {
        val hasContactHeader = containsExact(root, "联系人")
        var section = ""
        return anyNode(root) { node ->
            val label = labelOf(node)
            if (label in RESULT_SECTIONS) section = label
            val allowed = if (hasContactHeader) section == "联系人" else section !in BLOCKED_SECTIONS
            label == name && allowed && clickNode(node)
        }
    }

    private fun visibleRowLabels(root: AccessibilityNodeInfo): List<String> {
        val screen = Rect()
        root.getBoundsInScreen(screen)
        if (screen.width() <= 0 || screen.height() <= 0) return emptyList()
        val maxHeight = (screen.height() / 5).coerceAtLeast(160)
        val labels = ArrayList<String>()
        fun walk(node: AccessibilityNodeInfo) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val rowLike = node.isClickable &&
                bounds.width() >= screen.width() * 0.55f &&
                bounds.height() in 36..maxHeight
            if (rowLike) {
                firstLabel(node)?.let { labels.add(it) }
                return
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    walk(child)
                } finally {
                    child.recycle()
                }
            }
        }
        walk(root)
        return labels
    }

    private fun firstLabel(node: AccessibilityNodeInfo): String? {
        val own = node.text?.toString()?.trim().orEmpty()
        if (own.isNotEmpty()) return own
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (desc.isNotEmpty()) return desc
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                val found = firstLabel(child)
                if (found != null) return found
            } finally {
                child.recycle()
            }
        }
        return null
    }

    private fun containsExact(root: AccessibilityNodeInfo, expected: String): Boolean {
        return anyNode(root) { node -> labelOf(node) == expected }
    }

    private fun clickLabeled(root: AccessibilityNodeInfo, label: String): Boolean {
        return anyNode(root) { node ->
            labelOf(node) == label && clickNode(node)
        }
    }

    private fun labelOf(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) return text
        return node.contentDescription?.toString()?.trim().orEmpty()
    }

    private fun anyNode(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (predicate(node)) return true
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                if (anyNode(child, predicate)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            val clicked = parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            val next = if (clicked) null else parent.parent
            parent.recycle()
            if (clicked) return true
            parent = next
            depth += 1
        }
        return clickBounds(node)
    }

    private fun clickBounds(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return false
        val path = Path().apply { moveTo(rect.centerX().toFloat(), rect.centerY().toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatch(gesture)
    }

    private fun clickPoint(x: Float, y: Float): Boolean {
        if (x <= 0f || y <= 0f) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatch(gesture)
    }

    private fun setEditText(value: String, preferBottom: Boolean): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val copies = ArrayList<AccessibilityNodeInfo>()
        return try {
            val screen = Rect()
            root.getBoundsInScreen(screen)
            val tops = ArrayList<Int>()
            fun walk(node: AccessibilityNodeInfo) {
                val className = node.className?.toString().orEmpty()
                if (node.isEditable || className.contains("EditText")) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    copies.add(AccessibilityNodeInfo.obtain(node))
                    tops.add(rect.top)
                }
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    try {
                        walk(child)
                    } finally {
                        child.recycle()
                    }
                }
            }
            walk(root)
            if (copies.isEmpty()) return false
            var chosen = 0
            for (index in tops.indices) {
                val better = if (preferBottom) tops[index] >= tops[chosen] else tops[index] <= tops[chosen]
                if (better) chosen = index
            }
            if (preferBottom && screen.height() > 0 && tops[chosen] < screen.height() * 0.45f) return false
            val target = copies[chosen]
            if (setText(target, value)) true else paste(target, value)
        } catch (_: Exception) {
            false
        } finally {
            copies.forEach { copy ->
                try {
                    copy.recycle()
                } catch (_: Exception) {
                }
            }
            try {
                root.recycle()
            } catch (_: Exception) {
            }
        }
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun paste(node: AccessibilityNodeInfo, value: String): Boolean {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("broadcast", value))
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun clickSend(): Boolean {
        val root = service.rootInActiveWindow ?: return false
        var best: AccessibilityNodeInfo? = null
        return try {
            val screen = Rect()
            root.getBoundsInScreen(screen)
            var bestTop = Int.MIN_VALUE
            fun walk(node: AccessibilityNodeInfo) {
                val className = node.className?.toString().orEmpty()
                val editable = node.isEditable || className.contains("EditText")
                val label = node.text?.toString()?.trim().orEmpty()
                val desc = node.contentDescription?.toString()?.trim().orEmpty()
                if (!editable && (label == "发送" || desc == "发送")) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.top > screen.height() * 0.4f && rect.top >= bestTop) {
                        best?.recycle()
                        best = AccessibilityNodeInfo.obtain(node)
                        bestTop = rect.top
                    }
                }
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    try {
                        walk(child)
                    } finally {
                        child.recycle()
                    }
                }
            }
            walk(root)
            val target = best ?: return false
            clickNode(target)
        } catch (_: Exception) {
            false
        } finally {
            try {
                best?.recycle()
            } catch (_: Exception) {
            }
            try {
                root.recycle()
            } catch (_: Exception) {
            }
        }
    }

    private fun scroll(root: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val target = largestScrollable(root) ?: return false
        return try {
            val action = if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            target.performAction(action)
        } finally {
            target.recycle()
        }
    }

    private fun largestScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        fun walk(node: AccessibilityNodeInfo) {
            if (node.isScrollable) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val area = rect.width() * rect.height()
                if (area > bestArea) {
                    best?.recycle()
                    best = AccessibilityNodeInfo.obtain(node)
                    bestArea = area
                }
            }
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                try {
                    walk(child)
                } finally {
                    child.recycle()
                }
            }
        }
        walk(root)
        return best
    }

    private fun dispatch(gesture: GestureDescription): Boolean {
        val latch = CountDownLatch(1)
        val accepted = booleanArrayOf(false)
        val sent = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    accepted[0] = true
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    latch.countDown()
                }
            },
            null,
        )
        if (!sent) return false
        latch.await(2, TimeUnit.SECONDS)
        return accepted[0]
    }

    private fun waitMs(token: Int, ms: Long): Boolean {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            if (!SessionStore.awaitContinue(token)) return false
            val slice = (end - System.currentTimeMillis()).coerceAtMost(150)
            if (slice <= 0) break
            try {
                Thread.sleep(slice)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return SessionStore.awaitContinue(token)
    }

    private inline fun <T> withRoot(block: (AccessibilityNodeInfo) -> T): T? {
        val root = service.rootInActiveWindow ?: return null
        return try {
            block(root)
        } catch (_: Exception) {
            null
        } finally {
            try {
                root.recycle()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val STOPPED = "已停止"
        private const val TAG = "WechatBroadcast"
        private const val MAX_PAGES = 800
        private val RESULT_SECTIONS = setOf("联系人", "群聊", "聊天记录", "公众号", "服务号", "最常使用")
        private val BLOCKED_SECTIONS = setOf("群聊", "聊天记录", "公众号", "服务号")
    }
}
