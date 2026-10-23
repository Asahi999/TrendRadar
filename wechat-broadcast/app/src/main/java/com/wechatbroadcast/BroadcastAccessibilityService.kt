package com.wechatbroadcast

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍服务平时空闲。只有用户在应用里点了开始，并在确认页同意之后，才会操作微信。
 */
class BroadcastAccessibilityService : AccessibilityService() {
    @Volatile
    private var running = false
    private var worker: Thread? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        running = true
        worker = Thread {
            while (running) {
                val job = SessionStore.pollJob()
                if (job == null) {
                    try {
                        Thread.sleep(200)
                    } catch (_: InterruptedException) {
                        break
                    }
                    continue
                }
                try {
                    when (job) {
                        is SessionStore.Job.Scan -> runScan(job)
                        is SessionStore.Job.Send -> runSend(job)
                    }
                } catch (error: Exception) {
                    Log.i(TAG, "任务中断")
                    if (jobToken(job).let { it == SessionStore.generation }) {
                        SessionStore.publish(
                            jobToken(job),
                            SessionStore.Phase.Failed("操作中断，请回到微信主界面后重试"),
                            "操作中断",
                        )
                    }
                }
            }
        }.also {
            it.name = "wechat-broadcast-worker"
            it.start()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun runScan(job: SessionStore.Job.Scan) {
        showNotice("正在读取通讯录", ongoing = true)
        val automator = WeChatAutomator(this)
        if (!SessionStore.awaitContinue(job.token)) {
            publishStopped(job.token, 0, 0, 0, emptyList())
            return
        }
        automator.launchWeChat()
        if (!automator.waitUntilWeChat(job.token, 15_000)) {
            if (!stillCurrent(job.token)) {
                publishStopped(job.token, 0, 0, 0, emptyList())
            } else {
                fail(job.token, "微信没有留在前台。请解锁屏幕，允许微信打开后再试。")
            }
            return
        }
        when (val scan = automator.scanNames(job.token)) {
            WeChatAutomator.NameScan.Stopped -> publishStopped(job.token, 0, 0, 0, emptyList())
            is WeChatAutomator.NameScan.Error -> fail(job.token, scan.message)
            is WeChatAutomator.NameScan.Ok -> {
                val plan = BroadcastPlan.prepare(scan.names, job.keywords)
                SessionStore.publish(
                    job.token,
                    SessionStore.Phase.Scanned(
                        included = plan.included,
                        excluded = plan.excluded,
                        message = job.message,
                        intervalSeconds = job.intervalSeconds,
                        mayBePartial = scan.mayBePartial,
                    ),
                    "请确认名单",
                )
                showNotice("名单已读完，请回到微信群发确认", ongoing = false)
            }
        }
    }

    private fun runSend(job: SessionStore.Job.Send) {
        val automator = WeChatAutomator(this)
        var sent = 0
        var failed = 0
        val failures = ArrayList<String>()
        job.names.forEachIndexed { index, name ->
            if (!SessionStore.awaitContinue(job.token)) {
                publishStopped(job.token, sent, failed, job.excludedCount, failures)
                return
            }
            SessionStore.publish(
                job.token,
                SessionStore.Phase.Sending(
                    index = index + 1,
                    total = job.names.size,
                    currentName = name,
                    sent = sent,
                    failed = failed,
                    excludedCount = job.excludedCount,
                    failures = failures.toList(),
                ),
                "正在发送",
            )
            showNotice("正在发送 ${index + 1}/${job.names.size}", ongoing = true)
            val error = automator.sendTo(job.token, name, job.message)
            if (error == WeChatAutomator.STOPPED || !SessionStore.isCurrent(job.token)) {
                publishStopped(job.token, sent, failed, job.excludedCount, failures)
                return
            }
            if (error == null) {
                sent += 1
            } else {
                failed += 1
                failures += "$name：$error"
            }
            if (index < job.names.lastIndex && !sleepInterval(job.token, job.intervalSeconds)) {
                publishStopped(job.token, sent, failed, job.excludedCount, failures)
                return
            }
        }
        SessionStore.publish(
            job.token,
            SessionStore.Phase.Finished(
                sent = sent,
                failed = failed,
                excludedCount = job.excludedCount,
                stopped = false,
                failures = failures.toList(),
            ),
            "发送结束",
        )
        showNotice("发送结束：成功 $sent，失败 $failed", ongoing = false)
    }

    private fun sleepInterval(token: Int, seconds: Int): Boolean {
        val end = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < end) {
            if (!SessionStore.awaitContinue(token)) return false
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                return false
            }
        }
        return SessionStore.awaitContinue(token)
    }

    private fun publishStopped(
        token: Int,
        sent: Int,
        failed: Int,
        excludedCount: Int,
        failures: List<String>,
    ) {
        if (token != SessionStore.generation) return
        SessionStore.publish(
            token,
            SessionStore.Phase.Finished(
                sent = sent,
                failed = failed,
                excludedCount = excludedCount,
                stopped = true,
                failures = failures,
            ),
            "已停止",
        )
        showNotice("已停止。成功 $sent，失败 $failed", ongoing = false)
    }

    private fun fail(token: Int, message: String) {
        SessionStore.publish(token, SessionStore.Phase.Failed(message), message)
        showNotice(message, ongoing = false)
    }

    private fun stillCurrent(token: Int): Boolean {
        return token == SessionStore.generation && SessionStore.control != SessionStore.Control.STOP
    }

    private fun showNotice(text: String, ongoing: Boolean) {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
            val open = PendingIntent.getActivity(
                this,
                0,
                Intent(this, ProgressActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_send)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing)
                .build()
            manager.notify(NOTICE_ID, notification)
        } catch (_: SecurityException) {
            Log.i(TAG, "没有通知权限，进度仍显示在应用内")
        }
    }

    private fun jobToken(job: SessionStore.Job): Int {
        return when (job) {
            is SessionStore.Job.Scan -> job.token
            is SessionStore.Job.Send -> job.token
        }
    }

    companion object {
        private const val TAG = "WechatBroadcast"
        private const val CHANNEL_ID = "broadcast"
        private const val NOTICE_ID = 1001

        @Volatile
        private var instance: BroadcastAccessibilityService? = null

        fun isConnected(): Boolean = instance != null
    }
}
