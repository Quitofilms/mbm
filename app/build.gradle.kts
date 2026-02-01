import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.mbm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mbm"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "2.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

tasks.register("copyApkToYDrive") {
    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val apkFile = File(buildDir, "outputs/apk/debug/app-debug.apk")
        val destinationDir = File("Y:/apps/mbm")
        val destinationFile = File(destinationDir, "mbm.apk")

        if (apkFile.exists()) {
            if (!destinationDir.exists()) {
                destinationDir.mkdirs()
            }
            Files.copy(apkFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            println("APK copied and renamed to: ${destinationFile.absolutePath}")
        } else {
            println("Source APK not found at: ${apkFile.absolutePath}")
        }
    }
}

afterEvaluate {
    tasks.findByName("assembleDebug")?.finalizedBy("copyApkToYDrive")
}

dependencies {
    // Core AndroidX Components
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // UI & Seeker Components (Standardized versions)
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.android.material:material:1.11.0")

    // Media3 Pipeline (1.2.0 is stable on SDK 35)
    implementation("androidx.media3:media3-transformer:1.2.0")
    implementation("androidx.media3:media3-common:1.2.0")
    implementation("androidx.media3:media3-effect:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-exoplayer:1.2.0")

    // Image Handling
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
