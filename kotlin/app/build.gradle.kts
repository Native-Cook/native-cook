plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ngocdevv.nativecook"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ngocdevv.nativecook.free.kotlin"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Blocks. Each block's manifest in catalog/ lists the ones it needs under deps.kotlin.
    // reward-card-reveal, expanding-bottom-tabs
    implementation(libs.androidx.navigation.compose)
    // time-target-dial, gravity-tags-onboarding, expanding-bottom-tabs
    implementation(libs.androidx.compose.material.icons.extended)
    // card-carousel-onboarding, orbit-photo-ring, shatter-glass-transition: remote images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // gravity-tags-onboarding: 2D rigid-body physics
    implementation(libs.jbox2d)
    // glowknob-console (lifted 3D object), choose-your-ball (glTF ball): Filament
    implementation(libs.filament.android)
    implementation(libs.filament.utils.android)
    implementation(libs.filamat.android)
    implementation(libs.filament.gltfio.android)
    // particle-dispersion: real camera capture, image orientation and subject segmentation.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.play.services.base)
    implementation(libs.mlkit.subject.segmentation)
    implementation(libs.kotlinx.coroutines.android)
}
