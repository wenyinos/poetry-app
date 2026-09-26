package me.javayhu.poetry.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import me.javayhu.poetry.databinding.ActivitySplashBinding
import me.javayhu.poetry.ui.home.HomeActivity

/**
 * 启动页：展示 logo 后进入首页。
 *
 * 首页要解析 7 万条索引，启动页正好把这 1 秒左右的时间用起来，
 * 避免用户直接看到空白或转圈。
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val main = Handler(Looper.getMainLooper())

    private val goHome = Runnable {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        main.postDelayed(goHome, SPLASH_DELAY_MS)
    }

    override fun onDestroy() {
        main.removeCallbacks(goHome)
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_DELAY_MS = 900L
    }
}
