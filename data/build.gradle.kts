plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "fr.jarodkohler.antiscroll.data"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain"))
    testImplementation(libs.junit)
}
