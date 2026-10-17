buildscript {
    dependencies {
        classpath("com.umeng.umsdk:apm-plugin:2.0.0")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
