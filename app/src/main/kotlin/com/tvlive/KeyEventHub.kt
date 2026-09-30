package com.tvlive

import android.view.KeyEvent

/**
 * Activity 层按键分发中心：按键在进入 View 焦点系统之前先交给 handler 处理。
 * 触摸设备（手机/模拟器）上 ComposeView 可能不持有焦点，View 级 OnKeyListener
 * 会收不到按键、或被系统焦点导航抢先消费；从 dispatchKeyEvent 接管则完全
 * 不依赖焦点状态。
 */
class KeyEventHub {
    var handler: ((KeyEvent) -> Boolean)? = null

    fun dispatch(event: KeyEvent): Boolean = handler?.invoke(event) ?: false
}

interface KeyDispatchOwner {
    val keyHub: KeyEventHub
}
