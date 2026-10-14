// The validators' engine spike (settings.gradle.kts). Flavors, each a tiny app that runs one script in its engine, so a
// release build of each says what the engine adds to an APK:
//   none      no engine (the baseline)
//   quickjs   QuickJS through quickjs-kt (io.github.dokar3, Bellard's QuickJS, Kotlin, coroutines; arm64-v8a, armeabi-v7a,
//             x86, x86_64)
//   jsengine  Jetpack JavaScriptEngine (androidx.javascriptengine: the WebView's V8 in a sandboxed process)
// The release builds are the app's (R8 here, as the app's release will have it; arm64-v8a only, the native libraries
// compressed); -Pabis=arm64-v8a,x86_64 builds both processor types. The instrumented test (src/androidTest, both engines
// in the test APK) reads the bundle (companion/bridge/validators/dist) and the corpus (companion/bridge/test/conformance/out)
// as its assets: build them first (bun validators/build.ts; bun test/conformance/build-corpus.ts).
plugins {
    // Kotlin 2.4 on the build's classpath, as the app has it through its Compose plugin: AGP's built-in Kotlin compiles
    // with it (quickjs-kt is built with Kotlin 2.4)
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.androidApplication)
}

val quickjsKt = "io.github.dokar3:quickjs-kt:1.0.15"
val javascriptEngine = "androidx.javascriptengine:javascriptengine:1.1.1"
val abis = (findProperty("abis") as String?)?.split(',')?.map { it.trim() } ?: listOf("arm64-v8a")

android {
    namespace = "si.lanisce.lani.spike.validators"
    compileSdk = 36

    defaultConfig {
        applicationId = "si.lanisce.lani.spike.validators"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "engine"
    productFlavors {
        create("none") { dimension = "engine" }
        create("quickjs") { dimension = "engine" }
        create("jsengine") { dimension = "engine" }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            ndk { abiFilters += abis }
        }
        getByName("debug") {
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        jniLibs.useLegacyPackaging = true
    }
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("../../bridge/validators/dist", "../../bridge/test/conformance/out")
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    // every flavor: the app has coroutines already (Compose, lifecycle), so the baseline does too
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    add("quickjsImplementation", quickjsKt)
    add("jsengineImplementation", javascriptEngine)

    androidTestImplementation(quickjsKt)
    androidTestImplementation(javascriptEngine)
    androidTestImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation(libs.junit)
}
