import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

// Release signing details stay out of version control. Without keystore.properties
// the release build is unsigned, so the project still builds from public source.
val keystoreProperties = Properties().apply {
  val file = rootProject.file("keystore.properties")
  if (file.isFile) file.inputStream().use(::load)
}
val releaseStoreFile = keystoreProperties.getProperty("storeFile").orEmpty()

android {
  namespace = "com.plaintxtoffice.plainrss"
  compileSdk {
    version = release(37) {
      minorApiLevel = 1
    }
  }
  defaultConfig {
    applicationId = "com.plaintxtoffice.plainrss"
    minSdk = 26
    targetSdk = 37
    versionCode = 2
    versionName = "1.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (releaseStoreFile.isNotBlank()) {
      create("release") {
        storeFile = rootProject.file(releaseStoreFile)
        storePassword = keystoreProperties.getProperty("storePassword")
        keyAlias = keystoreProperties.getProperty("keyAlias")
        keyPassword = keystoreProperties.getProperty("keyPassword")
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
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }
  testOptions {
    unitTests.isReturnDefaultValues = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

// Names outputs after the app and version, such as plain-rss-1.0.0-1-release.aab,
// instead of app-release.aab. Must come after android { } because it reads
// defaultConfig.
base {
  archivesName = "plain-rss-${android.defaultConfig.versionName}-${android.defaultConfig.versionCode}"
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: JUnit, coroutines, org.json
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.json)

  // Instrumented tests: JUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
