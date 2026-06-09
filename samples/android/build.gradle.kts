import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP and the Kotlin plugin are already on the build classpath via the
    // `convention-plugins` included build, so they are applied without a version.
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.monkopedia.kodemirror.samples.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.monkopedia.kodemirror.samples.android"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":state"))
    implementation(project(":view"))
    implementation(project(":basic-setup"))
    implementation(project(":lang-markdown"))

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.material3)
    implementation(libs.androidx.activity.compose)
}
