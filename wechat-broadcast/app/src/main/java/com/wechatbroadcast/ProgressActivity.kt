package com.wechatbroadcast

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import com.wechatbroadcast.databinding.ActivityProgressBinding

class ProgressActivity : BaseActivity() {
    private lateinit var binding: ActivityProgressBinding
    private var openedConfirm = false
    private var leaving = false

    private val listener: (SessionStore.Snapshot) -> Unit = { snapshot ->
        runOnUiThread { render(snapshot) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars(binding.root)
        binding.pauseButton.setOnClickListener {
            if (SessionStore.control == SessionStore.Control.PAUSE) {
                SessionStore.resume()
            } else {
                SessionStore.pause()
            }
        }
        binding.stopButton.setOnClickListener { SessionStore.stop() }
        binding.doneButton.setOnClickListener { leave() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val phase = SessionStore.current().phase
                    if (phase is SessionStore.Phase.Finished || phase is SessionStore.Phase.Failed || phase is SessionStore.Phase.Idle) {
                        leave()
                    } else {
                        SessionStore.stop()
                    }
                }
            },
        )
    }

    override fun onStart() {
        super.onStart()
        SessionStore.addListener(listener)
    }

    override fun onStop() {
        SessionStore.removeListener(listener)
        super.onStop()
    }

    private fun render(snapshot: SessionStore.Snapshot) {
        val paused = snapshot.control == SessionStore.Control.PAUSE
        binding.pauseButton.text = getString(if (paused) R.string.resume else R.string.pause)
        when (val phase = snapshot.phase) {
            SessionStore.Phase.Idle -> if (!leaving) finish()
            SessionStore.Phase.Scanning -> showRunning(
                status = snapshot.statusLine.ifBlank { getString(R.string.scanning_hint) },
                counts = getString(R.string.scanning_hint),
                current = "",
                failures = "",
                indeterminate = true,
                progress = 0,
                max = 100,
            )
            is SessionStore.Phase.Scanned -> {
                if (!openedConfirm) {
                    openedConfirm = true
                    startActivity(android.content.Intent(this, ConfirmActivity::class.java))
                }
            }
            is SessionStore.Phase.Sending -> showRunning(
                status = if (paused) getString(R.string.pause) else snapshot.statusLine,
                counts = getString(
                    R.string.send_counts,
                    phase.sent,
                    phase.failed,
                    (phase.total - phase.sent - phase.failed).coerceAtLeast(0),
                ),
                current = if (phase.currentName.isBlank()) "" else getString(R.string.current_name, phase.currentName),
                failures = formatFailures(phase.failures),
                indeterminate = false,
                progress = phase.sent + phase.failed,
                max = phase.total.coerceAtLeast(1),
            )
            is SessionStore.Phase.Finished -> showDone(
                status = if (phase.stopped) getString(R.string.summary_stopped) else getString(R.string.summary_done),
                counts = getString(R.string.summary_counts, phase.sent, phase.failed, phase.excludedCount),
                failures = formatFailures(phase.failures),
            )
            is SessionStore.Phase.Failed -> showDone(
                status = phase.message,
                counts = "",
                failures = "",
            )
        }
    }

    private fun showRunning(
        status: String,
        counts: String,
        current: String,
        failures: String,
        indeterminate: Boolean,
        progress: Int,
        max: Int,
    ) {
        binding.statusText.text = status
        binding.countsText.text = counts
        binding.currentName.text = current
        binding.failureText.text = failures
        binding.progress.isIndeterminate = indeterminate
        binding.progress.max = max
        if (!indeterminate) binding.progress.progress = progress
        binding.runningActions.visibility = View.VISIBLE
        binding.doneButton.visibility = View.GONE
    }

    private fun showDone(status: String, counts: String, failures: String) {
        binding.statusText.text = status
        binding.countsText.text = counts
        binding.currentName.text = ""
        binding.failureText.text = failures
        binding.progress.isIndeterminate = false
        binding.progress.progress = binding.progress.max
        binding.runningActions.visibility = View.GONE
        binding.doneButton.visibility = View.VISIBLE
    }

    private fun formatFailures(failures: List<String>): String {
        if (failures.isEmpty()) return ""
        return getString(R.string.failure_detail) + "\n" + failures.joinToString("\n")
    }

    private fun leave() {
        if (leaving) return
        leaving = true
        SessionStore.cancel()
        finish()
    }
}
