# R8's rules for the release build (and the qa build QA drives), on top of proguard-android-optimize.txt and the rules
# the libraries bring in their jars and AARs: kotlinx.serialization (every @Serializable class's serializer, its
# Companion and INSTANCE), WorkManager (the workers' names and constructors, made by reflection), Media3, OkHttp,
# CameraX, DataStore, Compose, coroutines. The manifest's activity, services and receivers keep their names through
# AAPT's rules. What is left is what only this app finds by name, each rule with what reads it.

# Crash reports: the lines of a stack trace, each file named "SourceFile"; the release's mapping (beside its APK,
# lani-<versionCode>-mapping.txt.gz) turns them back into the source's classes, methods, files and lines.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# The offline voice (audio/Piper.kt): sherpa-onnx's native library finds its classes, their fields and constructors by
# name (JNI's FindClass, GetFieldID and GetMethodID: the configs it reads, the GeneratedAudio it makes), which R8 can't
# see: the whole package as it is.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# An exercise's type as its class's simple name, lowercased ("gaptext", "choice"): in the results sent to the tutor
# (ui/PlayerScreen.kt, ui/game/GrammarPractice.kt, app/SchoolController.kt) and the stage's "did the kind change?"
# (ui/stage/Director.kt). The exercise classes keep their names, and so does Exercise: Android reads a nested class's
# simple name from its InnerClasses attribute, which R8 keeps only while the class it's nested in keeps its name too
# (else "Exercise$GapText" would come out of "si.lanisce.lani.data.Exercise$GapText").
-keepnames class si.lanisce.lani.data.Exercise
-keepnames class si.lanisce.lani.data.Exercise$* implements si.lanisce.lani.data.Exercise
-keepattributes InnerClasses,EnclosingMethod

# The bridge in the app (phone/PhoneBridge): QuickJS through quickjs-kt, whose native library calls back into its Kotlin
# by name (JNI's FindClass and GetMethodID: the bindings it invokes, the MemoryUsage and the exceptions it makes, the
# JsObject maps it hands over). Its AAR keeps only some of them: the whole package as it is.
-keep class com.dokar.quickjs.** { *; }
