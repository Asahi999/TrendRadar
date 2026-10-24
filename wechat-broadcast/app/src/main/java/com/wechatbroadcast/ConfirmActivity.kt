package com.wechatbroadcast

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.wechatbroadcast.databinding.ActivityConfirmBinding

class ConfirmActivity : BaseActivity() {
    private lateinit var binding: ActivityConfirmBinding
    private var closing = false

    private val listener: (SessionStore.Snapshot) -> Unit = { snapshot ->
        runOnUiThread { render(snapshot) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars(binding.root)
        binding.backToEdit.setOnClickListener { closeWithoutSending() }
        binding.confirmSend.setOnClickListener { confirm() }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = closeWithoutSending()
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
        val scanned = snapshot.phase as? SessionStore.Phase.Scanned
        if (scanned == null) {
            if (!closing) finish()
            return
        }
        binding.previewBanner.visibility = if (scanned.preview) View.VISIBLE else View.GONE
        binding.partialWarning.visibility = if (scanned.mayBePartial && !scanned.preview) View.VISIBLE else View.GONE
        binding.sendCount.text = scanned.included.size.toString()
        binding.excludeCount.text = scanned.excluded.size.toString()
        binding.intervalPreview.text = getString(R.string.interval_preview, scanned.intervalSeconds)
        binding.messagePreview.text = scanned.message
        binding.includedNames.text = formatNames(scanned.included)
        binding.excludedNames.text = formatNames(scanned.excluded)
        binding.confirmSend.isEnabled = scanned.included.isNotEmpty() || scanned.preview
    }

    private fun confirm() {
        val scanned = SessionStore.current().phase as? SessionStore.Phase.Scanned ?: return
        if (scanned.preview) {
            Toast.makeText(this, R.string.preview_no_send, Toast.LENGTH_LONG).show()
            return
        }
        if (scanned.included.isEmpty()) {
            Toast.makeText(this, R.string.nobody_to_send, Toast.LENGTH_SHORT).show()
            return
        }
        closing = true
        if (!SessionStore.confirmSend()) {
            closing = false
            Toast.makeText(this, R.string.nobody_to_send, Toast.LENGTH_SHORT).show()
            return
        }
        finish()
    }

    private fun closeWithoutSending() {
        if (closing) return
        closing = true
        SessionStore.cancel()
        finish()
    }

    private fun formatNames(names: List<String>): String {
        if (names.isEmpty()) return getString(R.string.none)
        return names.joinToString("\n") { "· $it" }
    }
}
