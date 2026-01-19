plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.blackcoffeecoding.mycameraapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.blackcoffeecoding.mycameraapp"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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

    // включаем ViewBinding, чтобы обращаться к кнопкам не через findViewById, а напрямую через объект binding.
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // добавляем стандартные библиотеки
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // добавляем основную библиотеку для камеры (CameraX)
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    // Lifecycle библиотека, чтобы камера сама знала, когда включаться/выключаться
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    // VideoCapture для записи видео (потребуется для задания )
    implementation("androidx.camera:camera-video:${cameraxVersion}")
    // View класс, который упрощает отображение превью камеры (PreviewView)
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    implementation("androidx.camera:camera-extensions:${cameraxVersion}")

    // добавляем glide для отображения картинок в галерее
    implementation("com.github.bumptech.glide:glide:4.16.0")
}