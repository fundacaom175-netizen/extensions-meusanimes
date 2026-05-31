plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "eu.kanade.tachiyomi.anime.br.meusanimes"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Aniyomi extension API
    compileOnly("com.github.aniyomiorg:aniyomi:r5271:lib")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    compileOnly("org.jsoup:jsoup:1.17.2")
    compileOnly("io.reactivex:rxjava:1.3.8")
    compileOnly("io.reactivex:rxandroid:1.2.1")
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}
