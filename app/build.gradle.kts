plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
apply(plugin = "com.efs.sdk.plugin")

setProperty("archivesBaseName", "AutoHand")

android {
    namespace = "com.quqi.autohand"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.quqi.autohand"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// efs 扩展的三个字段是包级私有且只暴露 getter；Gradle 还会给扩展生成装饰子类，
// 所以字段必须到原始类上找，再反射写到实例上
extensions.getByName("efs").let { ext ->
    val declared = Class.forName("com.efs.sdk.plugin.EfsExtension")
    fun setField(name: String, value: Any?) {
        declared.getDeclaredField(name).also { it.isAccessible = true }.set(ext, value)
    }
    setField("enable", true)
    setField("whiteList", listOf("com.quqi.autohand"))
    setField("blackList", emptyList<String>())
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    implementation(libs.umeng.common)
    implementation(libs.umeng.asms)
    implementation(libs.umeng.apm)
}
