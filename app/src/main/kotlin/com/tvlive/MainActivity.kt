package com.tvlive

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.tvlive.player.PlayerViewModel
import com.tvlive.ui.screens.MainScreen
import com.tvlive.ui.theme.TVLiveTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 保持屏幕常亮，防止CPU被系统降频
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 隐藏系统栏，全屏播放
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        enableEdgeToEdge()

        setContent {
            TVLiveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: PlayerViewModel = hiltViewModel()

                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    // 拦截所有按键，让应用完全控制
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("MainActivity", "onKeyDown: keyCode=$keyCode, action=${event?.action}")
        // 拦截所有按键，不传递给系统
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        Log.d("MainActivity", "dispatchKeyEvent: keyCode=${event?.keyCode}, action=${event?.action}")
        // 拦截所有按键事件
        return super.dispatchKeyEvent(event)
    }
}
