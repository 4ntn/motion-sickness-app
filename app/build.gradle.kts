// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.davidv.motionsickness"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.davidv.motionsickness"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            vcsInfo {
                include = false
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    // The app is English-only; drop library translations from resources.arsc.
    androidResources {
      localeFilters += "en"
    }

    // Don't embed the (Google-signed) dependency metadata block.
    dependenciesInfo {
      includeInApk = false
      includeInBundle = false
    }

    packaging {
      resources {
        excludes += listOf(
          "/META-INF/{AL2.0,LGPL2.1}",
          "/META-INF/*.version",
          "/kotlin/**",
          "kotlin-tooling-metadata.json",
          // kotlinx-coroutines debug agent
          "DebugProbesKt.bin",
        )
      }
    }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)

  testImplementation(libs.junit)
}
