plugins {
    id("com.android.application") version "8.5.2"
}

android {
    namespace = "ru.peregruz"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.peregruz"
        minSdk = 29
        targetSdk = 34
        versionCode = 13
        versionName = "1.9"
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

    packaging {
        resources.excludes += setOf("META-INF/versions/**", "META-INF/*.kotlin_module", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("com.github.MuntashirAkon:sun-security-android:1.1")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
}
