package me.javayhu.poetry.ui.about

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import me.javayhu.poetry.BuildConfig
import me.javayhu.poetry.R
import me.javayhu.poetry.databinding.ActivityAboutBinding

/** 关于页：版本、数据来源、隐私说明与离线能力 */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.versionText.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
    }
}
