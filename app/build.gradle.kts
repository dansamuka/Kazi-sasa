plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.kazisasa.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kazisasa.app"
        // minSdk 26 deliberately: gives java.time.Instant natively (used for ISO-8601
        // feed timestamps in data/mapper/OpportunityMappers.kt) with no desugaring
        // library needed. Lower if you need to support older devices, but add
        // coreLibraryDesugaring below if you do.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // flip on with proguard-rules.pro once the designer's build is close to shipping
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room schema export: writes a versioned JSON snapshot of the DB schema to
// app/schemas/ on every build. Commit these - they're what makes real Room
// migrations (Migration objects, addMigrations(...)) possible later instead of
// destructive fallback. Currently version = 1 with no migrations yet, so this
// starts producing history from day one rather than only once it's needed.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

// Debug and release each run their own KSP task (kspDebugKotlin, kspReleaseKotlin),
// and both process the same @Database class and write to the same room.schemaLocation
// folder above. Gradle is free to run independent tasks concurrently, so without this
// they can race: one task truncates the schema JSON to write it while the other reads
// it mid-write, and Room's SchemaBundle.deserialize blows up with "Empty schema file".
// Forcing release to run after debug serializes the two writes and removes the race.
afterEvaluate {
    tasks.findByName("kspReleaseKotlin")?.mustRunAfter(tasks.findByName("kspDebugKotlin"))
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // v4 design spec §3.2: Fraunces/Inter/IBM Plex Mono via the Downloadable
    // Fonts API - fetched from Google Play Services at runtime, cached
    // on-device, no binary font files bundled in this repo. Requires
    // res/values/font_certs.xml (added alongside Font.kt) declaring which
    // signing certs are trusted to serve the Google Fonts provider.
    implementation(libs.compose.ui.text.google.fonts)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
