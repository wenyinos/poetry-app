package me.javayhu.poetry.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import me.javayhu.poetry.R
import me.javayhu.poetry.databinding.ActivityWidgetConfigBinding

/**
 * 「定制微件」的配置页：配色、字号、是否显示出处，带实时预览。
 *
 * 微件配置页必须返回 RESULT_OK 并带上 EXTRA_APPWIDGET_ID，
 * 否则系统不会把微件添加到桌面。
 */
class CustomPoetryWidgetActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var config = WidgetConfig.Config()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 先置为取消：用户中途返回则不会添加微件
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            config = WidgetConfig.load(this, widgetId)
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.sourceSwitch.isChecked = config.showSource
        binding.sourceSwitch.setOnCheckedChangeListener { _, checked ->
            config = config.copy(showSource = checked)
            renderPreview()
        }
        binding.doneButton.setOnClickListener { saveAndFinish() }

        buildPresets()
        buildSizes()
        renderPreview()
    }

    private fun buildPresets() {
        binding.presetGroup.removeAllViews()
        val size = (40 * resources.displayMetrics.density).toInt()
        val gap = (12 * resources.displayMetrics.density).toInt()
        WidgetConfig.PRESETS.forEachIndexed { index, preset ->
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = gap }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(preset.background)
                    // 选中的加品牌色描边
                    setStroke(
                        (if (index == config.preset) 4 else 1) * resources.displayMetrics.density.toInt(),
                        if (index == config.preset) Color.parseColor("#03A9F4")
                        else Color.parseColor("#33000000"),
                    )
                }
                setOnClickListener {
                    config = config.copy(preset = index)
                    buildPresets()
                    renderPreview()
                }
            }
            binding.presetGroup.addView(swatch)
        }
    }

    private fun buildSizes() {
        binding.sizeGroup.removeAllViews()
        WidgetConfig.TEXT_SIZES.forEach { size ->
            val chip = Chip(this).apply {
                text = getString(R.string.widget_config_size_label, size.toInt())
                isCheckable = true
                isChecked = size == config.textSize
                setOnClickListener {
                    config = config.copy(textSize = size)
                    buildSizes()
                    renderPreview()
                }
            }
            binding.sizeGroup.addView(chip)
        }
    }

    private fun renderPreview() {
        val colors = config.colors
        binding.previewBox.setBackgroundColor(colors.background)
        binding.previewContent.setTextColor(colors.content)
        binding.previewContent.textSize = config.textSize
        binding.previewSource.setTextColor(colors.source)
        binding.previewSource.visibility = if (config.showSource) View.VISIBLE else View.GONE
    }

    private fun saveAndFinish() {
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetConfig.save(this, widgetId, config)
            WidgetSupport.refreshAll(this, CustomPoetryWidgetProvider::class.java)
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
            )
        }
        finish()
    }
}
