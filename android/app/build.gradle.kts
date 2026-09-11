plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Upload signing. The four properties live in `~/.gradle/gradle.properties` (or the
 * environment, for CI), never in this repo — see `docs/release.md`. When any of them is
 * missing the release build stays unsigned instead of failing, so a fresh clone still
 * builds `:app:bundleRelease`.
 */
fun secret(name: String): String? =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

val uploadStoreFile = secret("QUOTA_UPLOAD_STORE_FILE")?.let(::File)?.takeIf { it.isFile }
val uploadStorePassword = secret("QUOTA_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = secret("QUOTA_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = secret("QUOTA_UPLOAD_KEY_PASSWORD")
val canSignRelease =
    uploadStoreFile != null && uploadStorePassword != null &&
        uploadKeyAlias != null && uploadKeyPassword != null

android {
    namespace = "com.gorisse.thomas.quota"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gorisse.thomas.quota"
        minSdk = 26
        targetSdk = 36
        // Play refuses a versionCode it has already seen; CI passes -PVERSION_CODE=<n>.
        versionCode = (project.findProperty("VERSION_CODE") as String?)?.toInt() ?: 4
        // Overridable so a tag build can stamp its own name; 1.0.0 is the real default.
        versionName = (project.findProperty("VERSION_NAME") as String?) ?: "1.0.0"
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = uploadStoreFile
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets.configureEach { java.srcDirs("src/$name/kotlin") }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.gms.code.scanner)
    implementation(libs.kotlinx.serialization.json)
}
