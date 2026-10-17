package com.quqi.autohand

import android.app.Application
import android.content.Context
import android.os.Bundle
import com.quqi.autohand.util.Prefs
import com.umeng.commonsdk.UMConfigure
import com.umeng.umcrash.UMCrash

const val UMENG_APPKEY = "6ad25da6863270068ca4066d"
const val UMENG_CHANNEL = "official"

class UmengApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 同意前的预初始化：不采集、不上报，仅为同意后的 init 做准备
        UMConfigure.setLogEnabled(true)
        UMConfigure.preInit(this, UMENG_APPKEY, UMENG_CHANNEL)

        if (Prefs.privacyConsented(this)) UmengInit.start(this)
    }
}

/** 友盟正式初始化入口：UMCrash.initConfig 必须先于 UMConfigure.init。 */
object UmengInit {

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        Thread {
            val bundle = Bundle().apply {
                putBoolean(UMCrash.KEY_ENABLE_CRASH_JAVA, true)
                putBoolean(UMCrash.KEY_ENABLE_CRASH_NATIVE, true)
                putBoolean(UMCrash.KEY_ENABLE_ANR, true)
                putBoolean(UMCrash.KEY_ENABLE_PA, true)
                putBoolean(UMCrash.KEY_ENABLE_LAUNCH, true)
                putBoolean(UMCrash.KEY_ENABLE_MEM, true)
                putBoolean(UMCrash.KEY_ENABLE_NET, true)
                putBoolean(UMCrash.KEY_ENABLE_PAGE, true)
                putBoolean(UMCrash.KEY_ENABLE_POWER, true)
                putBoolean(UMCrash.KEY_ENABLE_CODE_LOG, true)
                putBoolean(UMCrash.KEY_ENABLE_MEMLEAK, true)
                putLong(UMCrash.KEY_PA_TIMEOUT_TIME, 2000L)
            }
            UMCrash.initConfig(bundle)

            UMConfigure.init(
                app,
                UMENG_APPKEY,
                UMENG_CHANNEL,
                UMConfigure.DEVICE_TYPE_PHONE,
                null
            )
        }.start()
    }
}
