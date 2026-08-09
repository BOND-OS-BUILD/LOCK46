plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.lock46.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lock46.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("en")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        viewBinding = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*"
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

/**
 * Copies the assembled debug APK to a stable, release-ready filename so local builds and
 * CI produce an identically named artifact.
 *
 * The copy lands in its own directory rather than back into `outputs/apk/debug`: writing
 * into a directory AGP already declares as a task output makes Gradle (correctly) reject
 * the build for an undeclared inter-task dependency.
 */
val apkOutputName = "LOCK46-v1.apk"

tasks.register<Copy>("packageV1Apk") {
    group = "build"
    description = "Copies the debug APK to build/outputs/lock46/$apkOutputName"
    dependsOn("assembleDebug")
    from(layout.buildDirectory.dir("outputs/apk/debug")) {
        include("*-debug.apk")
        rename { apkOutputName }
    }
    into(layout.buildDirectory.dir("outputs/lock46"))
}

/**
 * Refreshes the APK the website serves.
 *
 * The download button points at `/LOCK46-v1.apk` on our own domain, which Vercel serves
 * as a static file from the repository root — so that file is a build output that has to
 * be committed. Run this after changing app code, then commit the result.
 */
tasks.register<Copy>("refreshSiteApk") {
    group = "build"
    description = "Copies the built APK to the repository root for the website to serve"
    dependsOn("packageV1Apk")
    from(layout.buildDirectory.file("outputs/lock46/$apkOutputName"))
    into(rootProject.layout.projectDirectory)
}
