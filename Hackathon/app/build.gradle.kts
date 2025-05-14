plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
    kotlin("kapt")
}

// local.properties dosyasını yüklemek için (Gradle 7.0+ için bu genellikle otomatik yapılır ama ekleyebiliriz)
// import java.util.Properties
// val localProperties = Properties()
// val localPropertiesFile = rootProject.file("local.properties")
// if (localPropertiesFile.exists()) {
//    localProperties.load(localPropertiesFile.inputStream())
//}


android {
    namespace = "com.example.hackathon" // <<--- BU SİZİN GERÇEK PAKET ADINIZ MI KONTROL EDİN!
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.hackathon" // <<--- BU SİZİN GERÇEK PAKET ADINIZ MI KONTROL EDİN!
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // project.findProperty(...) Kotlin DSL'de local.properties'e güvenli erişim sağlar
            val geminiApiKey = project.findProperty("GEMINI_API_KEY")?.toString() ?: ""
            println("Gradle Config (release): GEMINI_API_KEY read as '$geminiApiKey'") // LOG EKLEMESİ
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        }
        debug {
            // project.findProperty(...) Kotlin DSL'de local.properties'e güvenli erişim sağlar
            val geminiApiKey = project.findProperty("GEMINI_API_KEY")?.toString() ?: ""
            println("Gradle Config (debug): GEMINI_API_KEY read as '$geminiApiKey'") // LOG EKLEMESİ
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
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
    // AndroidX ve diğer temel bağımlılıklar
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Navigation Components
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.15.1")
    kapt("com.github.bumptech.glide:compiler:4.15.1") // Glide için kapt

    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.recyclerview)

    // Test bağımlılıkları
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Lottie Animasyon
    implementation("com.airbnb.android:lottie:5.0.3")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp Logging Interceptor
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Lifecycle Components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")

    // Room Persistence Library
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Azure TTS için
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Google Generative AI (Gemini) SDK
    implementation("com.google.ai.client.generativeai:generativeai:0.5.0")// En son sürümü kontrol edin

    // pie chart için
    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")

    //json
    implementation("com.google.code.gson:gson:2.10.1")
}