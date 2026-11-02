buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:9.3.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // FCL 按键系统使用的手柄映射库只发布在 JitPack
        maven("https://jitpack.io") {
            content { includeGroup("com.github.Mathias-Boulay") }
        }
    }
}

tasks.register("clean").configure {
    delete("build")
}

