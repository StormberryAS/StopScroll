import java.io.File
import java.io.FileInputStream
import java.util.Properties
import javax.inject.Inject

// StopScroll for Android, 0.0.1 (spec 15, stage 3). The Labs WebView template (GDPRLetterBuilder,
// EclipseTracker):
//   - The web app at the repository root (index.html and what it loads) is copied into the APK's
//     assets byte for byte, with one injected line that loads the Android bridge; verifyWebAssets
//     proves it. rules/screen-rules.json is copied beside it, and the licence texts go into
//     assets/licences/ (prepareLicenceAssets).
//   - check<Variant>MergedManifest: no permission at all, allowBackup false, only MainActivity and
//     the Accessibility Service (behind BIND_ACCESSIBILITY_SERVICE) exported.
//   - check<Variant>ApkPermissions: aapt's permission list of the built APK equals
//     android/expected-permissions.txt, both directions.
//   - The sovereign release APK is built UNSIGNED (-PunsignedRelease=true) and signed afterwards by
//     the release workflow with apksigner (.github/workflows/android-release.yml), never by Gradle
//     on CI. The Google Play bundle is a separate package, built with -PplayBuild=true and the Play
//     upload key (below).
plugins {
    alias(libs.plugins.android.application)
}

// Signing credentials never live in this repo. android/keystore.properties (gitignored) may name the
// key file, its type and the alias, and holds NO passwords: those come from Gradle properties or the
// environment (RELEASE_STORE_PASSWORD, RELEASE_KEY_PASSWORD, PLAY_UPLOAD_STORE_PASSWORD,
// PLAY_UPLOAD_KEY_PASSWORD), which a local build passes from the keyring. Missing credentials never
// break assembleDebug or the unsigned release. The release key is
// ~/.android-keys/stormberry-stopscroll-release.p12 (created 29 October 2026, PKCS12, alias release,
// certificate SHA-256 1e4aa9ee...591a); the release workflow signs with it from the "release"
// environment's secrets.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) FileInputStream(keystorePropertiesFile).use { load(it) }
}

fun signingValue(propKey: String, gradleKey: String): String? =
    keystoreProperties.getProperty(propKey)
        ?: providers.gradleProperty(gradleKey).orNull
        ?: providers.environmentVariable(gradleKey).orNull

val releaseStoreFile = signingValue("storeFile", "RELEASE_STORE_FILE")
val releaseStoreType = signingValue("storeType", "RELEASE_STORE_TYPE")
val releaseStorePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
val canSignRelease = releaseStoreFile != null && file(releaseStoreFile).exists() &&
    releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

// THE GOOGLE PLAY EDITION (EclipseTracker's switch of 29 October 2026, itself MoonApp's and SunApp's
// pattern). Google Play is a SEPARATE package, deliberately: `no.stormberry.stopscroll` stays the
// sovereign build (our key, our certificate, GitHub Releases and Zapstore, with the NIP-C1 certificate
// link vouching for it), and `-PplayBuild=true` builds `no.stormberry.stopscroll.play` instead, which
// Google re-signs with an app signing key it holds. Two package names are two apps to Android: they
// install side by side and neither can ever update the other, so nothing that happens on Play can
// reach a Zapstore install. The Play edition has the SAME features as the sovereign one (YouTube
// only, no permission at all): only the application ID, the versionCode and the signing key differ.
// No product flavours (a flavour-level signingConfig is overridden by the build type's, and a flavour
// dimension renames every task and breaks the workflow's APK path and the Zapstore pattern).
val playBuild = when (providers.gradleProperty("playBuild").orNull?.lowercase()) {
    null -> false
    // A bare `-PplayBuild` arrives as an empty string. Treating that as false would hand back a
    // sovereign artefact from a command that plainly meant Play.
    "", "true", "1", "yes" -> true
    "false", "0", "no" -> false
    else -> error(
        "-PplayBuild=${providers.gradleProperty("playBuild").orNull} is not a recognised " +
            "value. Use -PplayBuild=true, or omit the property for the sovereign build.",
    )
}

// The Play upload key is a separate key with a separate job: it proves who uploaded the bundle and
// never reaches a device, so it must never be the release key. If it is ever compromised it can be
// reset from the Play Console without touching a single install. The key is
// ~/.android-keys/stormberry-stopscroll-play-upload.p12 (created 29 October 2026, PKCS12, alias
// upload, certificate SHA-256 08f36e5e...e2fd), its password in the keyring as
// app=stopscroll-play-upload.
val uploadStoreFile = signingValue("playUploadStoreFile", "PLAY_UPLOAD_STORE_FILE")
val uploadStoreType = signingValue("playUploadStoreType", "PLAY_UPLOAD_STORE_TYPE")
val uploadStorePassword = signingValue("playUploadStorePassword", "PLAY_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = signingValue("playUploadKeyAlias", "PLAY_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = signingValue("playUploadKeyPassword", "PLAY_UPLOAD_KEY_PASSWORD")
val canSignPlayUpload = uploadStoreFile != null && file(uploadStoreFile).exists() &&
    uploadStorePassword != null && uploadKeyAlias != null && uploadKeyPassword != null

// The two keys must be two different files. A Play upload config pointing at the release keystore
// (or a byte-for-byte copy of it) would enrol the sovereign certificate in Play App Signing, which
// cannot be undone. Refused before anything is built.
if (releaseStoreFile != null && uploadStoreFile != null) {
    val rel = file(releaseStoreFile)
    val upl = file(uploadStoreFile)
    if (rel.exists() && upl.exists() &&
        (rel.canonicalFile == upl.canonicalFile || rel.readBytes().contentEquals(upl.readBytes()))
    ) {
        error(
            "The Play upload keystore and the release keystore are the same key " +
                "($uploadStoreFile). The release key signs the Zapstore APK only and must never " +
                "reach Play; the upload key signs the Play bundle only.",
        )
    }
}

// The release APK is built UNSIGNED, in a job that never sees the key, and signed afterwards with
// apksigner (GDPRLetterBuilder's SEC-6). That unsigned APK is asked for explicitly with
// -PunsignedRelease=true; without the switch a release task that would produce an unsigned APK fails
// (below), and with it a build that has a release signing password in reach fails here.
val unsignedRelease = providers.gradleProperty("unsignedRelease").orNull == "true"
if (unsignedRelease && (releaseStorePassword != null || releaseKeyPassword != null)) {
    error(
        "-PunsignedRelease=true builds the release unsigned, for CI's separate signing job; " +
            "unset RELEASE_STORE_PASSWORD and RELEASE_KEY_PASSWORD first.",
    )
}

// The passwords are read while Gradle configures the build, and gradle.properties turns the
// configuration cache on, which would store them. So the build refuses to configure whenever a
// password is set and the cache is in use (run such a build with --no-configuration-cache).
abstract class BuildFeaturesHolder @Inject constructor(val features: org.gradle.api.configuration.BuildFeatures)

val configurationCacheRequested =
    objects.newInstance<BuildFeaturesHolder>().features.configurationCache.requested.getOrElse(false)
if (configurationCacheRequested && (releaseStorePassword != null || releaseKeyPassword != null)) {
    error(
        "Signing passwords are set, so this build must run with " +
            "--no-configuration-cache; otherwise Gradle would store them in its configuration cache.",
    )
}
if (configurationCacheRequested && (uploadStorePassword != null || uploadKeyPassword != null)) {
    error(
        "A Play upload password is set, so this build must run with --no-configuration-cache; " +
            "otherwise Gradle would store it in its configuration cache.",
    )
}

// Guards on the RESOLVED TASK GRAPH, not on the command line: `build`, `assemble` and Gradle's `bR`
// abbreviation reach a release artefact without containing "bundleRelease" or "assembleRelease".
// packageRelease (APK) and signReleaseBundle (AAB) are the two tasks that consume a signing config,
// so the checks belong on them.
//   - A release BUNDLE is built only with -PplayBuild=true and a complete upload key. Without the
//     flag it would carry the SOVEREIGN application ID signed with the release key; uploading it
//     would permanently claim no.stormberry.stopscroll on Play and enrol the release certificate in
//     Play App Signing, which cannot be undone.
//   - A release APK with -PplayBuild=true is refused: Play takes the bundle, and an installable .play
//     APK has no destination and must never be mistaken for the Zapstore one.
//   - The sovereign release APK that would silently come out unsigned is refused unless asked for.
tasks.matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }.configureEach {
    val isBundle = name == "signReleaseBundle"
    val signed = canSignRelease
    val unsigned = unsignedRelease
    val play = playBuild
    val haveUploadKey = canSignPlayUpload
    inputs.property("unsignedRelease", unsigned)
    inputs.property("playBuild", play)
    doFirst {
        if (isBundle && !play) {
            error(
                "Refusing to build a release bundle without -PplayBuild=true. This AAB would carry " +
                    "the sovereign application ID signed with the release key, and uploading it " +
                    "would permanently claim that package on Play. The sovereign channel ships " +
                    "APKs; Play takes the bundle, with -PplayBuild=true.",
            )
        }
        if (!isBundle && play) {
            error(
                "Refusing to build a release APK with -PplayBuild=true. Play takes the app bundle: " +
                    "run :app:bundleRelease -PplayBuild=true. An installable .play APK has no " +
                    "destination and must never be mistaken for the Zapstore one.",
            )
        }
        if (isBundle) {
            if (!haveUploadKey) {
                error(
                    "playBuild=true but the Play upload keystore is not fully configured. " +
                        "playUploadStoreFile and playUploadKeyAlias (android/keystore.properties, or " +
                        "PLAY_UPLOAD_STORE_FILE and PLAY_UPLOAD_KEY_ALIAS) and the passwords " +
                        "PLAY_UPLOAD_STORE_PASSWORD and PLAY_UPLOAD_KEY_PASSWORD (from the keyring) " +
                        "must all be set, and the store file must exist.",
                )
            }
            return@doFirst
        }
        if (!signed && !unsigned) {
            error(
                "Release signing is not configured. Pass -PunsignedRelease=true for an unsigned build, " +
                    "signed afterwards with apksigner (the release workflow does this). Use " +
                    "assembleDebug for a test build.",
            )
        }
    }
}

// The Play versionCode offset (see defaultConfig): 1000, as CheckLink, SunApp and MoonApp, so the
// sovereign versionCode must stay below 1000 (spec 9.2).
val PLAY_VERSION_CODE_OFFSET = 1000

// Under -PplayBuild=true the artefact is named for what it is, so a Play bundle can never be mistaken
// for a sovereign one on disk. Deliberately NOT set for the sovereign build, whose file name
// app-release-unsigned.apk is hardcoded in .github/workflows/android-release.yml.
if (playBuild) {
    base { archivesName = "stopscroll-play" }
}

android {
    namespace = "no.stormberry.stopscroll"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        // Sovereign build: no.stormberry.stopscroll, permanent from the first Zapstore release.
        // Google Play build (-PplayBuild=true): no.stormberry.stopscroll.play (NAME-2).
        applicationId = if (playBuild) "no.stormberry.stopscroll.play" else "no.stormberry.stopscroll"
        // MIN-1 at its default: getRootInActiveWindow(0) and getInstallSourceInfo().packageSource
        // exist only from API 33 (spec 6.2, 10.2).
        minSdk = 33
        targetSdk = 36
        // ONE VISIBLE VERSION, TWO CODE LINES. The sovereign versionCode and versionName on the next
        // lines are the only numbers bumped by hand; Zapstore codes stay below 1000. The Play build
        // derives its code from them and shows the same name:
        //   Play versionCode = 1000 + sovereign versionCode   (0.0.1 code 1 -> code 1001)
        //   Play versionName = sovereign versionName          (0.0.1 -> 0.0.1)
        // Every Play upload is built from a sovereign release tag that shipped on GitHub Releases.
        versionCode = 1
        versionName = "0.0.1"
        if (playBuild) {
            versionCode = PLAY_VERSION_CODE_OFFSET + versionCode!!
        }
        vectorDrawables.generatedDensities()
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                if (releaseStoreType != null) storeType = releaseStoreType
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
        if (canSignPlayUpload) {
            create("playUpload") {
                storeFile = file(uploadStoreFile!!)
                if (uploadStoreType != null) storeType = uploadStoreType
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
                // v1 stays ON here (MoonApp's reason): an app bundle is signed like a JAR, and this
                // signature exists only so Play can verify who uploaded it. Play strips it and
                // re-signs the split APKs it generates with the app signing key it holds.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The Play build never touches the release key and the sovereign build never touches the
            // upload key. Both stay conditional, so a clean checkout with no keystore can still run
            // assembleDebug and the unsigned assembleRelease.
            if (playBuild) {
                if (canSignPlayUpload) signingConfig = signingConfigs.getByName("playUpload")
            } else if (canSignRelease && !unsignedRelease) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = null
            }
            vcsInfo {
                include = false
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        buildConfig = false
        resValues = false
        shaders = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    androidResources {
        // The fonts are already compressed (woff2) and the page reads every file through the asset
        // loader; leaving them stored keeps the copy byte-identical and cheap to open.
        noCompress += listOf("woff2")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf(
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "ObsoleteLintCustomCheck",
            "NewerVersionAvailable",
            "OldTargetApi",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = false
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

object WebAssets {
    const val INJECTED_TAG = "<script src=\"android-bridge.js\"></script>"
    const val BRIDGE_FILE = "android-bridge.js"
    val OVERLAY_FILES = setOf("android-bridge.ts", BRIDGE_FILE, "tsconfig.json")

    /**
     * What the APK bundles from the repository root: the pages and what they load, nothing else. The
     * Inter font: inter.css and the two variable files only, the faces every page uses
     * ("InterVariable" first in style.css and disclaimer.css); the static faces in fonts/inter/ are
     * there for the website and are never requested. The release workflow lists the same set.
     */
    val INCLUDES = listOf(
        "index.html", "privacy.html", "disclaimer.html", "disclaimer.css", "app.js", "core/*.js", "style.css",
        "favicon.svg", "apple-touch-icon.png", "fonts/inter/inter.css", "fonts/inter/InterVariable.woff2",
        "fonts/inter/InterVariable-Italic.woff2", "licences/*.txt",
    )
    val ALLOWED_EXTENSIONS = setOf("html", "js", "css", "svg", "png", "woff2", "txt")

    /** Inserts the bridge tag on its own line directly before the first script tag. */
    fun inject(source: String): String {
        val first = source.indexOf("<script")
        require(first >= 0) { "index.html has no <script> tag to inject the Android bridge before" }
        require(!source.contains(BRIDGE_FILE)) { "index.html already mentions $BRIDGE_FILE" }
        val lineStart = source.lastIndexOf('\n', first) + 1
        val indent = source.substring(lineStart, first)
        require(indent.isBlank()) { "the first <script> in index.html does not start its own line" }
        return source.substring(0, lineStart) + indent + INJECTED_TAG + "\n" + source.substring(lineStart)
    }

    /** The copied index.html with the injected line taken out again, or null if the injection is wrong. */
    fun removeInjected(text: String): String? {
        if (Regex(Regex.escape(INJECTED_TAG)).findAll(text).count() != 1) return null
        val at = text.indexOf(INJECTED_TAG)
        val lineStart = text.lastIndexOf('\n', at) + 1
        val lineEnd = text.indexOf('\n', at)
        if (lineEnd < 0 || text.substring(lineStart, at).isNotBlank() || text.substring(at + INJECTED_TAG.length, lineEnd).isNotEmpty()) return null
        return text.substring(0, lineStart) + text.substring(lineEnd + 1)
    }
}

val webRootDir: File = rootProject.projectDir.parentFile
val webOverlayDir: File = rootProject.file("web-overlay")
val rulesFile: File = webRootDir.resolve("rules/screen-rules.json")
val webSourceTree = fileTree(webRootDir) { include(WebAssets.INCLUDES) }

abstract class PrepareWebAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val overlay: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rules: RegularFileProperty

    @get:Internal
    abstract val webRoot: DirectoryProperty

    @get:Internal
    abstract val overlayRoot: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

val prepareWebAssets = tasks.register<PrepareWebAssets>("prepareWebAssets") {
    description = "Copies the web app and the screen rules into generated assets and injects the Android bridge."
    group = "build"
    sources.from(webSourceTree)
    overlay.from(webOverlayDir)
    rules.set(rulesFile)
    webRoot.set(webRootDir)
    overlayRoot.set(webOverlayDir)
    outputDir.set(layout.buildDirectory.dir("generated/webAssets"))
    doLast {
        val root = webRoot.get().asFile
        val outRoot = outputDir.get().asFile
        outRoot.deleteRecursively()
        val web = outRoot.resolve("web").apply { mkdirs() }
        for (src in sources.files.sortedBy { it.relativeTo(root).invariantSeparatorsPath }) {
            val rel = src.relativeTo(root).invariantSeparatorsPath
            require(src.extension.lowercase() in WebAssets.ALLOWED_EXTENSIONS) { "$rel: a type the app does not serve" }
            val dest = web.resolve(rel)
            dest.parentFile.mkdirs()
            if (rel == "index.html") dest.writeText(WebAssets.inject(src.readText())) else src.copyTo(dest)
        }
        val overlayDir = overlayRoot.get().asFile
        val strays = (overlayDir.listFiles() ?: emptyArray()).map { it.name }.filter { it !in WebAssets.OVERLAY_FILES }
        require(strays.isEmpty()) { "android/web-overlay/ holds unexpected files: $strays" }
        val bridge = overlayDir.resolve(WebAssets.BRIDGE_FILE)
        require(bridge.isFile) { "missing ${bridge.path} (run tools/build-ts.sh)" }
        bridge.copyTo(web.resolve(WebAssets.BRIDGE_FILE))
        require(web.resolve("index.html").isFile) { "index.html was not copied" }
        rules.get().asFile.copyTo(outRoot.resolve("rules/screen-rules.json").apply { parentFile.mkdirs() })
    }
}

abstract class VerifyWebAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val webRoot: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val bridgeSource: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rules: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val copied: DirectoryProperty

    @get:OutputFile
    abstract val report: RegularFileProperty
}

val verifyWebAssets = tasks.register<VerifyWebAssets>("verifyWebAssets") {
    description = "Asserts every bundled web file and the rules file are byte-identical to their sources, except the one injected line in index.html."
    group = "verification"
    sources.from(webSourceTree)
    webRoot.set(webRootDir)
    bridgeSource.set(webOverlayDir.resolve(WebAssets.BRIDGE_FILE))
    rules.set(rulesFile)
    copied.set(prepareWebAssets.flatMap { it.outputDir })
    report.set(layout.buildDirectory.file("reports/webAssets/verify.txt"))
    doLast {
        val root = webRoot.get().asFile
        val out = copied.get().asFile
        val web = out.resolve("web")
        val problems = mutableListOf<String>()
        val expected = sources.files.associateBy { it.relativeTo(root).invariantSeparatorsPath }
        val actual = web.walkTopDown().filter { it.isFile }.associateBy { it.relativeTo(web).invariantSeparatorsPath }
        (actual.keys - expected.keys - WebAssets.BRIDGE_FILE).forEach { problems += "in the APK copy but not in the web root: $it" }
        (expected.keys - actual.keys).forEach { problems += "missing from the APK copy: $it" }
        val lines = StringBuilder()
        for ((rel, src) in expected.toSortedMap()) {
            val dst = actual[rel] ?: continue
            if (rel == "index.html") {
                val restored = WebAssets.removeInjected(dst.readText())
                if (restored == null || !restored.toByteArray().contentEquals(src.readBytes())) problems += "index.html differs by more than the injected line"
                lines.append("MODIFIED  index.html (one injected line)\n")
            } else if (!src.readBytes().contentEquals(dst.readBytes())) {
                problems += "$rel: bytes differ from the source"
            } else {
                lines.append("IDENTICAL ").append(rel).append('\n')
            }
        }
        if (actual[WebAssets.BRIDGE_FILE]?.readBytes()?.contentEquals(bridgeSource.get().asFile.readBytes()) != true) {
            problems += "${WebAssets.BRIDGE_FILE} missing or differs from android/web-overlay"
        }
        if (!out.resolve("rules/screen-rules.json").readBytes().contentEquals(rules.get().asFile.readBytes())) {
            problems += "rules/screen-rules.json differs from the repository's"
        }
        if (problems.isNotEmpty()) error("Bundled web app does not match its source:\n  " + problems.joinToString("\n  "))
        lines.append("ADDED     android-bridge.js (android/web-overlay)\nIDENTICAL rules/screen-rules.json\n")
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(lines.toString())
        logger.lifecycle("verifyWebAssets: ${expected.size} web files and the rules file match their sources")
    }
}

// Licences (EclipseTracker's task, 2026-10-16). What only the app adds lives outside assets/web/,
// under assets/licences/: the Apache-2.0 text for the AndroidX and Kotlin libraries
// (android/licences/), the app's own MIT licence copied from LICENSE at build time, and
// THIRD-PARTY.txt, which lists them. The Inter typeface's OFL text is a web file
// (licences/Inter-OFL-1.1.txt, bundled with the pages). The list of bundled libraries is taken from
// what the release build actually resolves, and the build fails on a library whose licence is not
// accounted for.
object Licences {
    val APACHE_GROUPS = listOf("androidx.", "org.jetbrains.kotlin", "org.jetbrains.kotlinx", "org.jspecify", "com.google.guava")

    fun isApache(group: String): Boolean =
        APACHE_GROUPS.any { if (it.endsWith(".")) group.startsWith(it) else group == it || group.startsWith("$it.") } ||
            group == "org.jetbrains"
}

abstract class PrepareLicenceAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val texts: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val appLicence: RegularFileProperty

    @get:Input
    abstract val modules: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

val prepareLicenceAssets = tasks.register<PrepareLicenceAssets>("prepareLicenceAssets") {
    description = "Puts the app's own and the bundled libraries' licence texts into assets/licences/."
    group = "build"
    texts.from(rootProject.fileTree("licences"))
    appLicence.set(webRootDir.resolve("LICENSE"))
    outputDir.set(layout.buildDirectory.dir("generated/licenceAssets"))
    doLast {
        val out = outputDir.get().asFile.resolve("licences")
        outputDir.get().asFile.deleteRecursively()
        out.mkdirs()
        texts.files.sortedBy { it.name }.forEach { it.copyTo(out.resolve(it.name)) }
        require(out.resolve("Apache-2.0.txt").isFile) { "android/licences/Apache-2.0.txt is missing" }
        appLicence.get().asFile.copyTo(out.resolve("StopScroll-MIT.txt"))
        val mods = modules.get()
        require(mods.isNotEmpty()) { "the release runtime classpath resolved to no libraries" }
        val unknown = mods.filter { !Licences.isApache(it.substringBefore(':')) }
        if (unknown.isNotEmpty()) {
            error(
                "These bundled libraries have no licence on record in assets/licences/. Check " +
                    "their licences and extend Licences in app/build.gradle.kts:\n  " + unknown.joinToString("\n  "),
            )
        }
        out.resolve("THIRD-PARTY.txt").writeText(
            buildString {
                append("StopScroll for Android: third-party components\n\n")
                append("The bundled pages (assets/web/) ship no third-party code. They carry the Inter typeface\n")
                append("(assets/web/fonts/inter/), under the SIL Open Font License 1.1, full text in\n")
                append("assets/web/licences/Inter-OFL-1.1.txt.\n\n")
                append("The Android app's release build depends on the libraries below, all licensed under the\n")
                append("Apache License, Version 2.0, full text in Apache-2.0.txt. R8 leaves out the parts the app\n")
                append("does not use, so some of them add no code at all.\n\n")
                mods.forEach { append("  ").append(it).append('\n') }
                append("\nStopScroll itself: MIT licence, in StopScroll-MIT.txt.\n")
            },
        )
    }
}

object ManifestRules {
    private const val PKG = "no.stormberry.stopscroll"

    /** What is wrong with a merged manifest, or nothing. */
    fun problems(xml: String): List<String> {
        val body = xml.replace(Regex("<!--[\\s\\S]*?-->"), "")
        val out = mutableListOf<String>()
        Regex("<(uses-permission(?:-sdk-23|-sdk-m)?|permission|permission-group|permission-tree)\\b[^>]*>").findAll(body).forEach {
            out += "the merged manifest declares or asks for a permission: ${it.value}"
        }
        val apps = Regex("<application\\b[^>]*>").findAll(body).toList()
        if (apps.size != 1) out += "expected one <application>, found ${apps.size}"
        apps.firstOrNull()?.let { app ->
            if (!app.value.contains("android:allowBackup=\"false\"")) out += "allowBackup is not false"
            if (app.value.contains("android:usesCleartextTraffic=\"true\"")) out += "cleartext traffic is allowed"
        }
        val services = Regex("<service\\b[^>]*>").findAll(body).toList()
        if (services.size != 1) out += "expected exactly one service (FeedService), found ${services.size}"
        Regex("<(activity|activity-alias|service|receiver|provider)\\b[^>]*>").findAll(body).forEach { m ->
            val tag = m.value
            val kind = m.groupValues[1]
            val name = Regex("android:name=\"([^\"]+)\"").find(tag)?.groupValues?.get(1) ?: "(no name)"
            if (!tag.contains("android:exported=\"true\"")) return@forEach
            val ok = (kind == "activity" && name == "$PKG.MainActivity") ||
                (kind == "service" && name == "$PKG.FeedService" && tag.contains("android:permission=\"android.permission.BIND_ACCESSIBILITY_SERVICE\""))
            if (!ok) out += "an exported $kind that is not MainActivity or the protected FeedService: $name"
        }
        return out
    }
}

abstract class CheckMergedManifest : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty
}

abstract class CheckApkPermissions : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val apkDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val expected: RegularFileProperty

    @get:Internal
    abstract val sdkDir: DirectoryProperty

    @get:Input
    abstract val packageSuffix: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty
}

androidComponents {
    val sdk = sdkComponents.sdkDirectory
    onVariants { variant ->
        val vName = variant.name
        val cap = vName.replaceFirstChar { it.uppercase() }
        val manifestCheck = tasks.register<CheckMergedManifest>("check${cap}MergedManifest") {
            description = "Fails unless the merged manifest asks for no permission, keeps allowBackup false and exports only MainActivity and the protected service."
            group = "verification"
            manifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/manifest/$vName.txt"))
            doLast {
                val problems = ManifestRules.problems(manifest.get().asFile.readText())
                if (problems.isNotEmpty()) error("The merged manifest of $vName breaks the no-permission rules:\n  " + problems.joinToString("\n  "))
                report.get().asFile.writeText("no permission asked for or declared; allowBackup false; only MainActivity and FeedService exported\n")
                logger.lifecycle("check${cap}MergedManifest: no permission; allowBackup false; only MainActivity and the protected FeedService exported")
            }
        }
        val apkCheck = tasks.register<CheckApkPermissions>("check${cap}ApkPermissions") {
            description = "Fails unless aapt's permission list of the built APK equals android/expected-permissions.txt."
            group = "verification"
            apkDir.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK))
            expected.set(rootProject.file("expected-permissions.txt"))
            sdkDir.set(sdk)
            // The package line may carry the Play edition's ".play" and the debug ".debug" suffixes;
            // everything else must equal expected-permissions.txt.
            packageSuffix.set((if (playBuild) ".play" else "") + (if (variant.buildType == "debug") ".debug" else ""))
            report.set(layout.buildDirectory.file("reports/permissions/$vName.txt"))
            doLast {
                val apk = apkDir.get().asFile.walkTopDown().singleOrNull { it.isFile && it.extension == "apk" }
                    ?: error("expected exactly one APK in ${apkDir.get().asFile}")
                val aapt = sdkDir.get().asFile.resolve("build-tools").listFiles()
                    ?.map { it.resolve("aapt") }?.filter { it.canExecute() }
                    ?.maxByOrNull { it.parentFile.name }
                    ?: error("no aapt under the SDK's build-tools")
                val proc = ProcessBuilder(aapt.path, "dump", "permissions", apk.path).redirectErrorStream(true).start()
                val got = proc.inputStream.bufferedReader().readText().trimEnd('\n')
                if (proc.waitFor() != 0) error("aapt failed: $got")
                val suffix = packageSuffix.get()
                val normalised = got.lines().joinToString("\n") {
                    if (suffix.isNotEmpty() && it == "package: no.stormberry.stopscroll$suffix") "package: no.stormberry.stopscroll" else it
                }
                val want = expected.get().asFile.readText().trimEnd('\n')
                if (normalised != want) error("${apk.name} asks for other permissions than expected-permissions.txt:\n--- expected\n$want\n--- aapt\n$got")
                report.get().asFile.writeText("$normalised\n")
                logger.lifecycle("check${cap}ApkPermissions: ${apk.name} matches expected-permissions.txt (no permissions)")
            }
        }
        tasks.matching { it.name == "package$cap" || it.name == "check" }.configureEach { dependsOn(manifestCheck) }
        tasks.matching { it.name == "assemble$cap" }.configureEach { finalizedBy(apkCheck) }
        variant.sources.assets?.addGeneratedSourceDirectory(prepareWebAssets, PrepareWebAssets::outputDir)
        variant.sources.assets?.addGeneratedSourceDirectory(prepareLicenceAssets, PrepareLicenceAssets::outputDir)
        if (variant.name == "release") {
            val resolved = variant.runtimeConfiguration.incoming.resolutionResult.rootComponent.map { rootComponent ->
                val seen = sortedSetOf<String>()
                val queue = ArrayDeque(listOf(rootComponent))
                val visited = mutableSetOf<org.gradle.api.artifacts.component.ComponentIdentifier>()
                while (queue.isNotEmpty()) {
                    val c = queue.removeFirst()
                    if (!visited.add(c.id)) continue
                    (c.id as? org.gradle.api.artifacts.component.ModuleComponentIdentifier)?.let {
                        seen += "${it.group}:${it.module}:${it.version}"
                    }
                    c.dependencies.filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
                        .forEach { queue.addLast(it.selected) }
                }
                seen.toList()
            }
            prepareLicenceAssets.configure { modules.set(resolved) }
        }
    }
}

tasks.named("preBuild") { dependsOn(verifyWebAssets) }
tasks.named("check") { dependsOn(verifyWebAssets) }

// The JVM unit tests read the real rules file and the sanitised fixtures from the repository.
tasks.withType<Test>().configureEach {
    systemProperty("stopscroll.root", webRootDir.path)
    inputs.dir(webRootDir.resolve("tests/fixtures")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rulesFile).withPathSensitivity(PathSensitivity.NONE)
    inputs.dir(webRootDir.resolve("android/app/src/main")).withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.json)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
}
