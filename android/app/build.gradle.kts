import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("rust")
}

// 手机版自己的版本号（android/version.properties），不使用电脑版的版本号
val rclVersion = Properties().apply {
    val propFile = rootProject.file("version.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

val tauriProperties = Properties().apply {
    val propFile = file("tauri.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

android {
    compileSdk = 36
    namespace = "com.redstonest.redcraft"
    defaultConfig {
        manifestPlaceholders["usesCleartextTraffic"] = "false"
        applicationId = "com.redstonest.redcraft"
        minSdk = 26
        targetSdk = 34
        versionCode = rclVersion.getProperty("versionCode", "1").toInt()
        versionName = rclVersion.getProperty("versionName", "0.0.0")
    }
    signingConfigs {
        // 测试签名：密钥公开在仓库中，保证每次构建的签名一致、可以覆盖安装。
        // 正式发布时通过环境变量 RCL_KEYSTORE 等提供私有密钥。
        create("rcl") {
            val ks = System.getenv("RCL_KEYSTORE")
            if (!ks.isNullOrEmpty() && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("RCL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RCL_KEY_ALIAS")
                keyPassword = System.getenv("RCL_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("test-key.jks")
                storePassword = "rcltest"
                keyAlias = "rcl"
                keyPassword = "rcltest"
            }
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("rcl")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            isDebuggable = true
            isJniDebuggable = true
            isMinifyEnabled = false
            packaging {
                jniLibs.keepDebugSymbols.add("*/arm64-v8a/*.so")
                jniLibs.keepDebugSymbols.add("*/armeabi-v7a/*.so")
                jniLibs.keepDebugSymbols.add("*/x86/*.so")
                jniLibs.keepDebugSymbols.add("*/x86_64/*.so")
            }
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("rcl")
            optimization {
               enable = true
            }
            proguardFiles(
                *fileTree(".") {
                  include("**/*.pro")
                  exclude("build/**")
                }.files.toTypedArray()
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

rust {
    rootDirRel = "../../../"
}

dependencies {
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.0")
}

apply(from = file("tauri.build.gradle.kts"))
