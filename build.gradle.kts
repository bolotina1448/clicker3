plugins {
    id("com.android.application") version "8.5.2"
}

android {
    namespace = "ru.peregruz"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.peregruz"
        minSdk = 29
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }

    signingConfigs {
        create("main") {
            storeFile = file("peregruz.jks")
            storePassword = "peregruz"
            keyAlias = "peregruz"
            keyPassword = "peregruz"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("main")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
