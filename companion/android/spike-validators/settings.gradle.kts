// A spike, not part of the app (docs/plans/05-phase-0.md, check 4): the bridge's content validators in a JavaScript
// engine on Android. Its own Gradle build beside the app's (the app's build is untouched), with the app's version catalog.
// A tiny app per engine (flavors none, quickjs, jsengine: what each adds to a release APK) and an instrumented test that
// runs the conformance corpus (companion/bridge/test/conformance) in both engines.
//
// Run from companion/android: ./gradlew -p spike-validators …   (see ConformanceTest.kt)
rootProject.name = "spike-validators"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
