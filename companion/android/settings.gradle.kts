rootProject.name = "lani"

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
        // sherpa-onnx (the phone's offline voice, Piper: data/Piper.kt): its AAR from the project's GitHub releases, as no
        // Maven repository has it. Only that module comes from there, checked against its SHA-256
        // (gradle/verification-metadata.xml).
        exclusiveContent {
            forRepository {
                ivy {
                    name = "sherpa-onnx releases"
                    url = uri("https://github.com/k2-fsa/sherpa-onnx/releases/download")
                    patternLayout { artifact("v[revision]/[module]-[revision].[ext]") }
                    metadataSources { artifact() }
                }
            }
            filter { includeModule("com.k2fsa.sherpa.onnx", "sherpa-onnx-static-link-onnxruntime") }
        }
    }
}

include(":app")
