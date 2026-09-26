plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "cn.rehab.trainer"
    compileSdk = 35
    defaultConfig { applicationId = "cn.rehab.trainer"; minSdk = 26; targetSdk = 35; versionCode = 3; versionName = "0.3.0-prototype" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies { implementation(project(":core")) }
