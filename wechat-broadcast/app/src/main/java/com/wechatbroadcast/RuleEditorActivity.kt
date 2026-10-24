package com.wechatbroadcast

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import com.wechatbroadcast.databinding.ActivityRulesBinding
import com.wechatbroadcast.databinding.ItemKeywordBinding

class RuleEditorActivity : BaseActivity() {
    private lateinit var binding: ActivityRulesBinding
    private lateinit var settings: SettingsStore
    private val keywords = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBars(binding.root)
        settings = SettingsStore(this)
        keywords += settings.keywords()
        render()
        binding.addKeyword.setOnClickListener { addKeyword() }
        binding.keywordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addKeyword()
                true
            } else {
                false
            }
        }
        binding.restoreDefaults.setOnClickListener {
            keywords.clear()
            keywords += ExclusionRules.defaultKeywords
            render()
        }
        binding.saveKeywords.setOnClickListener {
            settings.saveKeywords(keywords)
            Toast.makeText(this, R.string.keywords_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun addKeyword() {
        val word = binding.keywordInput.text?.toString()?.trim().orEmpty()
        when {
            word.isEmpty() -> toast(R.string.keyword_blank)
            word.length > 40 -> toast(R.string.keyword_too_long)
            keywords.any { it.equals(word, ignoreCase = true) } -> toast(R.string.keyword_duplicate)
            keywords.size >= 30 -> toast(R.string.keyword_too_many)
            else -> {
                keywords += word
                binding.keywordInput.text = null
                render()
            }
        }
    }

    private fun render() {
        binding.keywordContainer.removeAllViews()
        binding.emptyKeywords.visibility = if (keywords.isEmpty()) View.VISIBLE else View.GONE
        keywords.forEachIndexed { index, word ->
            val row = ItemKeywordBinding.inflate(layoutInflater, binding.keywordContainer, false)
            row.keywordText.text = word
            row.deleteKeyword.setOnClickListener {
                keywords.removeAt(index)
                render()
            }
            binding.keywordContainer.addView(row.root)
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
