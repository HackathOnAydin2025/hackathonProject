plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id ("androidx.navigation.safeargs.kotlin")
    id ("com.google.gms.google-services")
}

android {
    namespace = "com.example.bookgenie"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bookgenie"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // AndroidX ve diğer bağımlılıklar
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Firebase BoM (Bağımlılık yönetimi için)
    implementation (platform("com.google.firebase:firebase-bom:32.0.0"))

    // Firebase modülleri
    implementation ("com.google.firebase:firebase-auth-ktx")
    implementation ("com.google.firebase:firebase-firestore-ktx")

    // Glide
    implementation ("com.github.bumptech.glide:glide:4.15.1")
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.recyclerview)
    annotationProcessor ("com.github.bumptech.glide:compiler:4.15.1")

    // Test bağımlılıkları
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //lottie animasyon
    implementation ("com.airbnb.android:lottie:5.0.3")

    //material design
    implementation ("com.google.android.material:material:1.12.0")

    //retrofit
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")

    // (Opsiyonel) OkHttp Logging Interceptor - Network isteklerini logcat'te görmek için (Debug için çok faydalı)
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0") // Güncel versiyonu kontrol edin

    // Kotlin Coroutines (Asenkron işlemler için)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3") // Güncel versiyonu kontrol edin
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2") // ViewModelScope için
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2") // lifecycleScope için

    // Azure TTS için
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
