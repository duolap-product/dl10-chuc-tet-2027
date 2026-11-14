import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.crashlytics)
    alias(libs.plugins.hilt)
}

// Google's public AdMob test IDs: safe for debug builds, never earn revenue.
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"

val admobTestRewardedId = "ca-app-pub-3940256099942544/5224354917"
val admobTestInterstitialId = "ca-app-pub-3940256099942544/1033173712"
val admobTestBannerId = "ca-app-pub-3940256099942544/9214589741"
val admobTestAppOpenId = "ca-app-pub-3940256099942544/9257395921"
val admobTestPublisher = "ca-app-pub-3940256099942544"

// Real IDs come from Gradle properties (gradle.properties, ~/.gradle/gradle.properties or -P).
val admobReleaseKeys = listOf("ADMOB_APP_ID", "ADMOB_REWARDED_ID", "ADMOB_INTERSTITIAL_ID", "ADMOB_BANNER_ID", "ADMOB_APP_OPEN_ID")
fun admobProperty(key: String): String = (findProperty(key) as String?)?.trim().orEmpty()
// Each banner placement has its own ad unit so AdMob reports revenue per screen.
// Optional: a key left empty falls back to ADMOB_BANNER_ID, so units can be created gradually.
val admobBannerPlacementKeys = listOf(
    "ADMOB_BANNER_TET_CUSTOMS_ID",
    "ADMOB_BANNER_TET_PHOTOS_ID",
    "ADMOB_BANNER_MUSIC_SELECTION_ID"
)
fun admobBannerId(key: String): String = admobProperty(key).ifEmpty { admobProperty("ADMOB_BANNER_ID") }
val invalidAdmobKeys = admobReleaseKeys.filter {
    val value = admobProperty(it)
    value.isEmpty() || value.startsWith(admobTestPublisher)
} + admobBannerPlacementKeys.filter { admobProperty(it).startsWith(admobTestPublisher) }

android {
    namespace = "online.duolap.tetviet"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "online.duolap.tetviet"
        minSdk = 24
        targetSdk = 36
        versionCode = 8
        versionName = "1.0.0-8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["admobAppId"] = admobTestAppId
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"$admobTestRewardedId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobTestInterstitialId\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobTestBannerId\"")
        admobBannerPlacementKeys.forEach { buildConfigField("String", it, "\"$admobTestBannerId\"") }
        buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"$admobTestAppOpenId\"")

        // Mua trong ứng dụng (gói VIP, phông chữ VIP). Tắt cho đến khi cấu hình xong sản phẩm trên Play Console.
        buildConfigField("boolean", "IAP_ENABLED", "false")
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            storeFile = file(keystoreProperties["storeFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["admobAppId"] = admobProperty("ADMOB_APP_ID")
            buildConfigField("String", "ADMOB_REWARDED_ID", "\"${admobProperty("ADMOB_REWARDED_ID")}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${admobProperty("ADMOB_INTERSTITIAL_ID")}\"")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${admobProperty("ADMOB_BANNER_ID")}\"")
            admobBannerPlacementKeys.forEach { buildConfigField("String", it, "\"${admobBannerId(it)}\"") }
            buildConfigField("String", "ADMOB_APP_OPEN_ID", "\"${admobProperty("ADMOB_APP_OPEN_ID")}\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

// Fail release builds (not configuration) when real AdMob IDs are missing or still test IDs.
tasks.configureEach {
    if (name == "preReleaseBuild") {
        val missingKeys = invalidAdmobKeys
        doFirst {
            if (missingKeys.isNotEmpty()) {
                throw GradleException(
                    "Release build needs real AdMob IDs. Missing or test values: ${missingKeys.joinToString()}. " +
                        "Set them in gradle.properties or ~/.gradle/gradle.properties."
                )
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Firebase (Analytics, Crashlytics, Firestore)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.firestore)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Lottie
    implementation(libs.lottie)

    // Glide (Image Loading & Caching)
    implementation(libs.glide)

    // Dagger Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Google Mobile Ads (AdMob)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    // Google Play In-App Billing
    implementation(libs.billing.ktx)

    // Google Play In-App Update
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)
}