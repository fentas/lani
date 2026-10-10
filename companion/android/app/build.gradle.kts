import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.kotlinSerialization)
    // Kotlin itself comes from AGP 9's built-in Kotlin support.
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

/**
 * The festival word packs (companion/packs/praznik-*.json, Primorska's; and the packs of each culture pack's packs
 * directory, e.g. friuli's festa-*.json), bundled as Java resources under packs/: the
 * calendar (game/Calendar.kt) reads each festival's words from its pack, so the app and the bridge share one copy.
 */
abstract class FestivalPacks : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val packs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val out: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = out.get().asFile.resolve("packs")
        dir.deleteRecursively()
        dir.mkdirs()
        packs.files.forEach { it.copyTo(dir.resolve(it.name)) }
    }
}

val festivalPacks = tasks.register<FestivalPacks>("festivalPacks") {
    packs.from(fileTree("../../packs") { include("praznik-*.json") })
    packs.from(fileTree("../../cultures") { include("*/packs/*.json") })
}

/**
 * The culture packs (the JSON files of each companion/cultures/<id>, lani.culture/v0, its readings/ and its projects' steps,
 * project-steps/), bundled as
 * Java resources under cultures/<id>, with cultures/index.json listing their ids: the game's words (game/culture/Cultures.kt), so the
 * village reads the same without the bridge. A pack's cast (its villagers directory), its voice cast (voice-cast.json)
 * and its word packs (packs/: festivalPacks bundles the festivals') stay with the bridge, which serves them.
 */
abstract class CulturePacks : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val cultures: DirectoryProperty

    @get:OutputDirectory
    abstract val out: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = out.get().asFile.resolve("cultures")
        dir.deleteRecursively()
        dir.mkdirs()
        val ids = cultures.get().asFile.listFiles { f -> f.isDirectory && f.resolve("culture.json").isFile }.orEmpty().map { it.name }.sorted()
        for (id in ids) {
            val to = dir.resolve(id).apply { mkdirs() }
            val from = cultures.get().asFile.resolve(id)
            from.listFiles { f -> f.isFile && f.name.endsWith(".json") && f.name != "voice-cast.json" }.orEmpty()
                .forEach { it.copyTo(to.resolve(it.name)) }
            // what the chest's things hold to read (readings/<id>.json: Micka's recipes, Janez's books), and the reading
            // corner's others, with readings/index.json listing their ids (game/culture/Cultures.library)
            val readings = from.resolve("readings").listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
            readings.forEach { it.copyTo(to.resolve("readings").apply { mkdirs() }.resolve(it.name)) }
            if (readings.isNotEmpty()) to.resolve("readings/index.json").writeText(readings.joinToString(", ", "[", "]\n") { "\"${it.nameWithoutExtension}\"" })
            // its projects' steps as short scenes (project-steps/<project>.json, by the project's id: game/culture/Cultures.projectSteps)
            from.resolve("project-steps").listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty()
                .forEach { it.copyTo(to.resolve("project-steps").apply { mkdirs() }.resolve(it.name)) }
        }
        dir.resolve("index.json").writeText(ids.joinToString(", ", "[", "]\n") { "\"$it\"" })
    }
}

val culturePacks = tasks.register<CulturePacks>("culturePacks") {
    cultures.set(layout.projectDirectory.dir("../../cultures"))
}

/**
 * The grammar book's curated pages (companion/grammar/<language>/<id>.json, lani.grammar/v0), bundled as Java resources
 * under grammar/<language>/, with grammar/index.json listing each language's page ids in the book's order: the chapter
 * has them without the bridge (data/Grammar.kt), which serves them with the tutor's.
 */
abstract class GrammarPages : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val grammar: DirectoryProperty

    @get:OutputDirectory
    abstract val out: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = out.get().asFile.resolve("grammar")
        dir.deleteRecursively()
        dir.mkdirs()
        val root = grammar.get().asFile
        val langs = root.listFiles { f -> f.isDirectory }.orEmpty().map { it.name }.sorted()
        val index = langs.map { lang ->
            val pages = root.resolve(lang).listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
            val to = dir.resolve(lang).apply { mkdirs() }
            pages.forEach { it.copyTo(to.resolve(it.name)) }
            "\"$lang\": " + pages.joinToString(", ", "[", "]") { "\"${it.nameWithoutExtension}\"" }
        }
        dir.resolve("index.json").writeText(index.joinToString(",\n  ", "{\n  ", "\n}\n"))
    }
}

val grammarPages = tasks.register<GrammarPages>("grammarPages") {
    grammar.set(layout.projectDirectory.dir("../../grammar"))
}

/**
 * The car's audio drills (companion/drills/<language>/<id>.json, lani.drill/v0), bundled as Java resources under
 * drills/<language>/, with drills/index.json listing each language's drill ids: the road has them without the bridge
 * (data/Drills.kt), which serves them too (GET /drills).
 */
abstract class DrillFiles : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val drills: DirectoryProperty

    @get:OutputDirectory
    abstract val out: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = out.get().asFile.resolve("drills")
        dir.deleteRecursively()
        dir.mkdirs()
        val root = drills.get().asFile
        val langs = root.listFiles { f -> f.isDirectory }.orEmpty().map { it.name }.sorted()
        val index = langs.map { lang ->
            val files = root.resolve(lang).listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
            val to = dir.resolve(lang).apply { mkdirs() }
            files.forEach { it.copyTo(to.resolve(it.name)) }
            "\"$lang\": " + files.joinToString(", ", "[", "]") { "\"${it.nameWithoutExtension}\"" }
        }
        dir.resolve("index.json").writeText(index.joinToString(",\n  ", "{\n  ", "\n}\n"))
    }
}

val drillFiles = tasks.register<DrillFiles>("drillFiles") {
    drills.set(layout.projectDirectory.dir("../../drills"))
}

/**
 * «Vidim, vidim» (I spy, companion/SCENES.md): the child's lines in each language (companion/ispy/<language>.json,
 * lani.ispy-lines/v0) and each scene's clues (companion/ispy/scenes/<scene>.json, lani.ispy/v0), bundled as Java resources
 * under ispy/ (app/ISpyController.kt). The bridge doesn't serve them (its voice-build reads them for the voices).
 */
abstract class ISpyFiles : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val ispy: DirectoryProperty

    @get:OutputDirectory
    abstract val out: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = out.get().asFile.resolve("ispy")
        dir.deleteRecursively()
        dir.mkdirs()
        val root = ispy.get().asFile
        root.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().forEach { it.copyTo(dir.resolve(it.name)) }
        root.resolve("scenes").listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().forEach { it.copyTo(dir.resolve("scenes/${it.name}")) }
    }
}

val ispyFiles = tasks.register<ISpyFiles>("ispyFiles") {
    ispy.set(layout.projectDirectory.dir("../../ispy"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.resources?.addGeneratedSourceDirectory(ispyFiles, ISpyFiles::out)
        variant.sources.resources?.addGeneratedSourceDirectory(festivalPacks, FestivalPacks::out)
        variant.sources.resources?.addGeneratedSourceDirectory(culturePacks, CulturePacks::out)
        variant.sources.resources?.addGeneratedSourceDirectory(grammarPages, GrammarPages::out)
        variant.sources.resources?.addGeneratedSourceDirectory(drillFiles, DrillFiles::out)
    }
}

// The version follows the commit count on main, so every commit can ship as an update (see bin/release-app). The
// repository started anew as Lani (the app was si.lanisce.fluent, up to 0.1.629 = versionCode 629), so the count
// restarted: versionName 0.2.<count>, versionCode 1000 + <count>, always above the old app's codes.
val gitCommitCount: Int = providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
    .standardOutput.asText.get().trim().toInt()
val laniVersionCode = 1000 + gitCommitCount
val laniVersionName = "0.2.$gitCommitCount"

/**
 * The release key, never in the repository: the keystore LANI_KEYSTORE names, with LANI_KEYSTORE_PASSWORD, LANI_KEY_ALIAS
 * (lani) and LANI_KEY_PASSWORD (the keystore's), as the release workflow sets them from its secrets; else the
 * signing.properties of ~/.config/lani (LANI_CONFIG_DIR): storeFile (relative to it), storePassword, keyAlias and
 * keyPassword, as companion/bin/lani-keystore writes it. Without either, the release build is signed with the debug key,
 * as the sideloaded app always was. A phone takes an update only with the key its app was installed with.
 */
class ReleaseKey(val store: File, val storePassword: String, val alias: String, val keyPassword: String)

val releaseKey: ReleaseKey? = run {
    fun env(name: String) = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
    env("LANI_KEYSTORE")?.let { store ->
        val password = env("LANI_KEYSTORE_PASSWORD") ?: error("LANI_KEYSTORE is set but LANI_KEYSTORE_PASSWORD is not")
        return@run ReleaseKey(file(store), password, env("LANI_KEY_ALIAS") ?: "lani", env("LANI_KEY_PASSWORD") ?: password)
    }
    val config = env("LANI_CONFIG_DIR")?.let(::File)
        ?: File(env("HOME") ?: System.getProperty("user.home"), ".config/lani")
    val file = config.resolve("signing.properties")
    val text = providers.fileContents(layout.projectDirectory.file(file.absolutePath)).asText.orNull ?: return@run null
    val props = Properties().apply { load(text.reader()) }
    fun prop(name: String) = props.getProperty(name)?.takeIf { it.isNotBlank() } ?: error("$file has no $name")
    val password = prop("storePassword")
    ReleaseKey(
        config.resolve(prop("storeFile").replaceFirst(Regex("^~(?=/)"), env("HOME") ?: System.getProperty("user.home"))),
        password, props.getProperty("keyAlias") ?: "lani", props.getProperty("keyPassword") ?: password,
    )
}

android {
    namespace = "si.lanisce.lani"
    compileSdk = 36

    defaultConfig {
        applicationId = "si.lanisce.lani"
        minSdk = 26
        targetSdk = 36
        versionCode = laniVersionCode
        versionName = laniVersionName
    }

    signingConfigs {
        releaseKey?.let { key ->
            create("release") {
                storeFile = key.store
                storePassword = key.storePassword
                keyAlias = key.alias
                keyPassword = key.keyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // sideloaded: the release key when one is set (releaseKey), else the debug key
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // The offline voice's runtime (sherpa-onnx, about 24 MB a processor type) only for the phones' 64-bit ARM: the
            // APK stays small. A 32-bit or x86 phone can't install it then.
            ndk { abiFilters += listOf("arm64-v8a") }
        }
        getByName("debug") {
            // and for the x86_64 emulator QA runs on
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // the native libraries compressed in the APK (the offline voice's runtime: 24 MB, 9 MB compressed), so an update
        // downloads less; the phone unpacks them once when it installs it
        jniLibs.useLegacyPackaging = true
    }
    // The unit tests read the curated content said to the learner it was written for before its placeholders (a man named
    // Jan): every text reads as it always did (l10n/Learner.kt; a test of another learner sets its own).
    testOptions {
        unitTests.all {
            it.systemProperty("lani.learner", "Jan")
            // the renderers' time budgets, scaled on a slow runner (FrameBudget in the tests; CI sets LANI_PERF_SCALE)
            it.systemProperty("lani.perfScale", System.getenv("LANI_PERF_SCALE") ?: "1")
        }
    }
    // No dependency report in the signing block: it is encrypted for Google Play, which only Google can read, and
    // differs on every build. Without it a release APK is the same, byte for byte, wherever it is built (on GitHub, in
    // Docker, with a local Gradle), so its SHA-256 can be checked against a build of the same commit.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    // The phone's offline voice: Piper models run by sherpa-onnx (data/Piper.kt); the voice itself comes through the bridge.
    implementation("com.k2fsa.sherpa.onnx:sherpa-onnx-static-link-onnxruntime:${libs.versions.sherpaOnnx.get()}@aar")
    testImplementation(libs.junit)
    testImplementation(libs.icu4j)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.media3.test.utils)
    testImplementation(libs.media3.test.utils.robolectric)
}
