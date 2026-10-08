plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.graviton94.todayletters"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.graviton94.todayletters"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = System.getenv("TL_VERSION_NAME") ?: "0.1.0"
        // 개발자 도구(캡처용 시험 데이터 · 장면 열기): debug 빌드에만
        buildConfigField("boolean", "DEV_TOOLS", "true")
    }

    // 직접 설치용 (내 폰 시험용) 고정 키: 저장소에 있는 시험 키라 비밀이 아님 (android/keystore/README.md)
    signingConfigs {
        create("sideload") {
            storeFile = file("../keystore/sideload.jks")
            storePassword = "android"; keyAlias = "sideload"; keyPassword = "android"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("sideload"); versionNameSuffix = "-dev" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("sideload")
            buildConfigField("boolean", "DEV_TOOLS", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    // 낭독은 assets 에서 바로 연다 (압축하면 openFd 가 안 됨)
    androidResources { noCompress += listOf("m4a") }
    // 화면 사진 (Robolectric + Roborazzi): .github/workflows/screens.yml 에서만 돌린다
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.39.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.39.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
