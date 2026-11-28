plugins {
    alias(libs.plugins.android.application)
}

val releaseKeystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
val releaseTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true)
}

if (releaseTaskRequested) {
    val missingSigningValues = buildList {
        if (releaseKeystorePath.isNullOrBlank()) add("ANDROID_KEYSTORE_PATH")
        if (releaseKeystorePassword.isNullOrBlank()) add("ANDROID_KEYSTORE_PASSWORD")
        if (releaseKeyAlias.isNullOrBlank()) add("ANDROID_KEY_ALIAS")
        if (releaseKeyPassword.isNullOrBlank()) add("ANDROID_KEY_PASSWORD")
    }
    check(missingSigningValues.isEmpty()) {
        "Release signing configuration is incomplete. Missing: ${missingSigningValues.joinToString()}"
    }
}

android {
    namespace = "com.rain.browser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rain.browser"
        minSdk = 36
        targetSdk = 36
        versionCode = 5
        versionName = "0.1.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (!releaseKeystorePath.isNullOrBlank()) {
                storeFile = file(releaseKeystorePath)
            }
            storePassword = releaseKeystorePassword.orEmpty()
            keyAlias = releaseKeyAlias.orEmpty()
            keyPassword = releaseKeyPassword.orEmpty()
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.webkit)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
}
