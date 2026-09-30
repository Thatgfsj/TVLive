package com.tvlive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            // 电视设备启动 TV 版（横屏遥控器适配），其他设备启动手机版
            val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            val target = if (isTv) MainActivity::class.java else PhoneMainActivity::class.java
            val mainIntent = Intent(context, target).apply {
                // CLEAR_TOP 防止在某些系统（如模拟器的模拟开机广播）上重复堆叠实例
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(mainIntent)
        }
    }
}
