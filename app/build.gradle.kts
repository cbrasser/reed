import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.parcelize)
}

// sherpa-onnx (on-device voices) only ships as an AAR on its GitHub releases. It's fetched once,
// pinned by version and checksum, instead of living in git (38 MB).
val sherpaVersion = "1.13.8"
val sherpaSha256 = "b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471"
val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar").asFile

fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes()).joinToString("") { "%02x".format(it) }

val fetchSherpa = tasks.register("fetchSherpa") {
    description = "Downloads the pinned sherpa-onnx AAR and verifies its checksum."
    outputs.file(sherpaAar)
    doLast {
        if (sherpaAar.exists() && sha256(sherpaAar) == sherpaSha256) return@doLast
        sherpaAar.parentFile.mkdirs()
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/${sherpaAar.name}"
        val partial = File(sherpaAar.path + ".part")
        URI(url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
        check(sha256(partial) == sherpaSha256) { "Checksum mismatch for $url" }
        partial.renameTo(sherpaAar)
    }
}

fun gitOutput(vararg args: String): String? = providers.exec {
    commandLine("git", *args)
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim().ifEmpty { null }

android {
    namespace = "app.reed"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "app.reed"
        minSdk = 30
        targetSdk = 36
        // Versions come from git: the latest vX.Y.Z tag names the release (Obtainium compares it
        // with the installed versionName), the commit count keeps versionCode increasing.
        versionCode = gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
        versionName = gitOutput("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")?.removePrefix("v") ?: "0.1.0"
    }

    // Personal key, kept outside the repo. Credentials live in ~/.gradle/gradle.properties
    // (REED_KEYSTORE_FILE, REED_KEYSTORE_PASSWORD, REED_KEY_ALIAS, REED_KEY_PASSWORD).
    // Debug and release share it so an update never needs an uninstall, which would delete notes.
    val reedKeystore = providers.gradleProperty("REED_KEYSTORE_FILE").orNull?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (reedKeystore != null) {
            create("reed") {
                storeFile = reedKeystore
                storePassword = providers.gradleProperty("REED_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("REED_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("REED_KEY_PASSWORD").get()
            }
        }
    }
    val reedSigning = signingConfigs.findByName("reed") ?: signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = reedSigning
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = reedSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    packaging {
        jniLibs {
            // Reed voices run on 64-bit ARM phones only; elsewhere read-aloud uses the phone's engine.
            excludes += listOf("lib/armeabi-v7a/libsherpa-onnx-jni.so", "lib/x86/libsherpa-onnx-jni.so", "lib/x86/libonnxruntime.so", "lib/x86_64/libsherpa-onnx-jni.so")
        }
    }

    androidResources {
        noCompress += listOf("ttf")
    }
}

kotlin {
    jvmToolchain(21)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.biometric)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.timber)
    implementation(libs.okhttp)
    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.json)

    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.readium.pdfium.navigator)
    implementation(libs.readium.pdfium.document)
    implementation(libs.readium.media.tts)
    implementation(libs.media3.session)
    implementation(files(sherpaAar).builtBy(fetchSherpa))
    implementation(libs.commons.compress)
}
