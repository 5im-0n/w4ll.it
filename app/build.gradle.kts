import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Local release builds read the ignored keystore.properties file. CI supplies the
// corresponding values as environment variables so credentials never enter the repository.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")

if (keystorePropertiesFile.isFile) {
    keystorePropertiesFile.inputStream().use { input ->
        keystoreProperties.load(input)
    }
}

fun signingProperty(name: String, environmentName: String): String =
    keystoreProperties.getProperty(name)
        ?: System.getenv(environmentName)
        ?: throw GradleException(
            "Missing release signing value '$name'. Configure keystore.properties or $environmentName."
        )

// AGP 9 uses ApplicationExtension rather than the deprecated android {} accessor.
extensions.configure<ApplicationExtension> {
    namespace = "it.w4ll"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.w4ll"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "1.2.2"
    }

    signingConfigs {
        create("release") {
            storeFile = file(signingProperty("storeFile", "ANDROID_KEYSTORE_FILE"))
            storePassword = signingProperty("storePassword", "ANDROID_KEYSTORE_PASSWORD")
            keyAlias = signingProperty("keyAlias", "ANDROID_KEY_ALIAS")
            keyPassword = signingProperty("keyPassword", "ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    buildFeatures { viewBinding = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil:2.7.0")
}
