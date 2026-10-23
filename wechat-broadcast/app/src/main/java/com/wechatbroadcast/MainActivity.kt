package com.wechatbroadcast

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wechatbroadcast.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: SettingsStore

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars(binding.root)
        settings = SettingsStore(this)
        if (binding.messageInput.text.isNullOrEmpty()) {
            binding.messageInput.setText(settings.message())
        }
        if (binding.intervalInput.text.isNullOrEmpty()) {
            binding.intervalInput.setText(settings.intervalSeconds().toString())
        }
        binding.editKeywords.setOnClickListener {
            saveDraft()
            startActivity(Intent(this, RuleEditorActivity::class.java))
        }
        binding.openA11y.setOnClickListener { WeChatStatus.openAccessibilitySettings(this) }
        binding.startButton.setOnClickListener { onStartClicked() }
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_PREVIEW_CONFIRM, false)) {
            SessionStore.showPreview()
            startActivity(Intent(this, ConfirmActivity::class.java))
        }
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_OPEN_RULES, false)) {
            startActivity(Intent(this, RuleEditorActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderKeywords()
    }

    override fun onPause() {
        saveDraft()
        super.onPause()
    }

    private fun renderStatus() {
        val a11yOn = WeChatStatus.isServiceEnabled(this)
        val wechatOn = WeChatStatus.isInstalled(this)
        binding.a11yStatus.text = getString(if (a11yOn) R.string.status_a11y_on else R.string.status_a11y_off)
        binding.wechatStatus.text = getString(if (wechatOn) R.string.status_wechat_on else R.string.status_wechat_off)
        binding.a11yStatus.setTextColor(color(if (a11yOn) R.color.ok else R.color.danger))
        binding.wechatStatus.setTextColor(color(if (wechatOn) R.color.ok else R.color.danger))
        binding.openA11y.visibility = if (a11yOn) View.GONE else View.VISIBLE
    }

    private fun renderKeywords() {
        val words = settings.keywords()
        binding.keywordPreview.text = if (words.isEmpty()) {
            getString(R.string.keywords_empty_preview)
        } else {
            words.joinToString("、")
        }
    }

    private fun onStartClicked() {
        val phase = SessionStore.current().phase
        if (phase is SessionStore.Phase.Scanning || phase is SessionStore.Phase.Sending) {
            startActivity(Intent(this, ProgressActivity::class.java))
            Toast.makeText(this, R.string.task_running, Toast.LENGTH_SHORT).show()
            return
        }
        val message = binding.messageInput.text?.toString()?.trim().orEmpty()
        if (message.isEmpty()) {
            showGuide(R.string.empty_message_title, R.string.empty_message_body, null, null)
            return
        }
        val interval = binding.intervalInput.text?.toString()?.trim()?.toIntOrNull()
        if (interval == null || interval !in SettingsStore.MIN_INTERVAL_SECONDS..SettingsStore.MAX_INTERVAL_SECONDS) {
            showGuide(R.string.interval_title, R.string.interval_body, null, null)
            return
        }
        if (!WeChatStatus.isInstalled(this)) {
            showGuide(R.string.wechat_missing_title, R.string.wechat_missing_body, null, null)
            return
        }
        if (!WeChatStatus.isServiceEnabled(this)) {
            showGuide(R.string.a11y_missing_title, R.string.a11y_missing_body, R.string.go_enable) {
                WeChatStatus.openAccessibilitySettings(this)
            }
            return
        }
        if (!WeChatStatus.serviceConnected()) {
            showGuide(R.string.a11y_missing_title, R.string.service_not_connected, R.string.go_enable) {
                WeChatStatus.openAccessibilitySettings(this)
            }
            return
        }
        saveDraft()
        requestNotificationPermission()
        SessionStore.startScan(message, interval, settings.keywords())
        startActivity(Intent(this, ProgressActivity::class.java))
        openWeChat()
    }

    private fun openWeChat() {
        val intent = packageManager.getLaunchIntentForPackage(WeChatStatus.PACKAGE_NAME) ?: return
        startActivity(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (granted != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun saveDraft() {
        settings.saveMessage(binding.messageInput.text?.toString().orEmpty())
        val interval = binding.intervalInput.text?.toString()?.trim()?.toIntOrNull()
        if (interval != null && interval in SettingsStore.MIN_INTERVAL_SECONDS..SettingsStore.MAX_INTERVAL_SECONDS) {
            settings.saveInterval(interval)
        }
    }

    private fun showGuide(title: Int, message: Int, action: Int?, onAction: (() -> Unit)?) {
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.know, null)
        if (action != null && onAction != null) {
            builder.setPositiveButton(action) { _, _ -> onAction() }
        }
        builder.show()
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

    companion object {
        const val EXTRA_PREVIEW_CONFIRM = "preview_confirm"
        const val EXTRA_OPEN_RULES = "open_rules"
    }
}
