plugins { alias(libs.plugins.android.application) }
android {
    namespace = "com.relaximus.dreamchords"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.relaximus.dreamchords"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = false; aidl = false; shaders = false }
}
