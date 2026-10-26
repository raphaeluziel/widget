import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Base URL and token live in the git-ignored local.properties and reach the code via BuildConfig.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun configField(key: String): String {
    val value = localProperties.getProperty(key, "").trim()
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "com.raphaeluziel.noteswidget"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.raphaeluziel.noteswidget"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "NOTES_BASE_URL", configField("notes.baseUrl"))
        buildConfigField("String", "NOTES_WIDGET_TOKEN", configField("notes.widgetToken"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
}
