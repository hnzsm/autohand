package com.quqi.autohand.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 通知栏「暂停 / 停止」按钮的落点，转交给正在运行的服务实例。 */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoHandService.instance?.handleControl(intent.getStringExtra(AutoHandService.EXTRA_ACTION))
    }
}
