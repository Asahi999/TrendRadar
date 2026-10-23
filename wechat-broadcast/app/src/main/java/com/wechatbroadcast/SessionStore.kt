package com.wechatbroadcast

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * 同进程里的一次群发任务。名单只放在内存中。
 * generation 用来丢掉已经被新任务取代的旧结果。
 */
object SessionStore {
    enum class Control { RUN, PAUSE, STOP }

    data class Snapshot(
        val phase: Phase = Phase.Idle,
        val control: Control = Control.RUN,
        val statusLine: String = "",
    )

    sealed class Phase {
        data object Idle : Phase()

        data object Scanning : Phase()

        data class Scanned(
            val included: List<String>,
            val excluded: List<String>,
            val message: String,
            val intervalSeconds: Int,
            val mayBePartial: Boolean,
            val preview: Boolean = false,
        ) : Phase()

        data class Sending(
            val index: Int,
            val total: Int,
            val currentName: String,
            val sent: Int,
            val failed: Int,
            val excludedCount: Int,
            val failures: List<String>,
        ) : Phase()

        data class Finished(
            val sent: Int,
            val failed: Int,
            val excludedCount: Int,
            val stopped: Boolean,
            val failures: List<String>,
        ) : Phase()

        data class Failed(val message: String) : Phase()
    }

    sealed class Job {
        data class Scan(
            val token: Int,
            val message: String,
            val intervalSeconds: Int,
            val keywords: List<String>,
        ) : Job()

        data class Send(
            val token: Int,
            val names: List<String>,
            val message: String,
            val intervalSeconds: Int,
            val excludedCount: Int,
        ) : Job()
    }

    private val listeners = CopyOnWriteArrayList<(Snapshot) -> Unit>()
    private val pending = AtomicReference<Job?>(null)

    @Volatile
    private var snapshot = Snapshot()

    @Volatile
    var generation: Int = 0
        private set

    @Volatile
    var control: Control = Control.RUN
        private set

    fun current(): Snapshot = snapshot

    fun addListener(listener: (Snapshot) -> Unit) {
        listeners.add(listener)
        listener(snapshot)
    }

    fun removeListener(listener: (Snapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun startScan(message: String, intervalSeconds: Int, keywords: List<String>) {
        val value = synchronized(this) {
            generation += 1
            control = Control.RUN
            pending.set(
                Job.Scan(
                    token = generation,
                    message = message,
                    intervalSeconds = intervalSeconds,
                    keywords = keywords.toList(),
                ),
            )
            snapshot = Snapshot(Phase.Scanning, Control.RUN, "正在打开微信通讯录")
            snapshot
        }
        dispatch(value)
    }

    fun showPreview() {
        val value = synchronized(this) {
            generation += 1
            control = Control.RUN
            pending.set(null)
            snapshot = Snapshot(
                phase = Phase.Scanned(
                    included = listOf("同事甲", "同事乙"),
                    excluded = listOf("小恶魔同事"),
                    message = "这是一条示例文案，仅用于预览确认页。",
                    intervalSeconds = DEFAULT_PREVIEW_INTERVAL,
                    mayBePartial = false,
                    preview = true,
                ),
                statusLine = "界面预览，没有读取通讯录",
            )
            snapshot
        }
        dispatch(value)
    }

    fun confirmSend(): Boolean {
        val value = synchronized(this) {
            val scanned = snapshot.phase as? Phase.Scanned ?: return false
            if (scanned.preview || scanned.included.isEmpty()) return false
            control = Control.RUN
            pending.set(
                Job.Send(
                    token = generation,
                    names = scanned.included,
                    message = scanned.message,
                    intervalSeconds = scanned.intervalSeconds,
                    excludedCount = scanned.excluded.size,
                ),
            )
            snapshot = Snapshot(
                phase = Phase.Sending(
                    index = 0,
                    total = scanned.included.size,
                    currentName = "",
                    sent = 0,
                    failed = 0,
                    excludedCount = scanned.excluded.size,
                    failures = emptyList(),
                ),
                statusLine = "准备逐个发送",
            )
            snapshot
        }
        dispatch(value)
        return true
    }

    fun cancel() {
        val value = synchronized(this) {
            generation += 1
            control = Control.STOP
            pending.set(null)
            snapshot = Snapshot(Phase.Idle, Control.STOP, "")
            snapshot
        }
        dispatch(value)
    }

    fun pause() = setControl(Control.PAUSE, "已暂停")

    fun resume() = setControl(Control.RUN, "继续")

    fun stop() = setControl(Control.STOP, "正在停止")

    fun pollJob(): Job? = pending.getAndSet(null)

    fun isCurrent(token: Int): Boolean = token == generation && control != Control.STOP

    fun awaitContinue(token: Int): Boolean {
        while (true) {
            if (token != generation) return false
            when (control) {
                Control.STOP -> return false
                Control.RUN -> return token == generation
                Control.PAUSE -> {
                    try {
                        Thread.sleep(200)
                    } catch (_: InterruptedException) {
                        return false
                    }
                }
            }
        }
    }

    fun publish(token: Int, phase: Phase, statusLine: String) {
        val value = synchronized(this) {
            if (token != generation) return
            snapshot = Snapshot(phase, control, statusLine)
            snapshot
        }
        dispatch(value)
    }

    fun updateStatus(token: Int, statusLine: String) {
        val value = synchronized(this) {
            if (token != generation) return
            snapshot = snapshot.copy(statusLine = statusLine)
            snapshot
        }
        dispatch(value)
    }

    private fun setControl(next: Control, line: String) {
        val value = synchronized(this) {
            control = next
            snapshot = snapshot.copy(control = next, statusLine = line)
            snapshot
        }
        dispatch(value)
    }

    private fun dispatch(value: Snapshot) {
        for (listener in listeners) {
            try {
                listener(value)
            } catch (_: Exception) {
            }
        }
    }

    private const val DEFAULT_PREVIEW_INTERVAL = 5
}
